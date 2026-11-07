package mindustry.net;

import arc.*;
import arc.func.*;
import arc.net.dns.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sends server pings over one shared UDP socket instead of one thread and one socket per ping.
 * <p>
 * The old implementation submitted every ping to a thread pool, where each ping created a socket and
 * blocked on {@code receive()} for up to two seconds. Pinging a few hundred community servers therefore
 * took several rounds of the pool, and any ping issued afterwards had to wait in the same queue.
 * <p>
 * Here every request is a single UDP packet and a small entry in {@link #pending}; a single receiver
 * thread matches responses back to their requests. The whole community list is answered within one
 * timeout period, and callbacks are always posted to the main thread.
 */
public class PingService{
    private static final Object lock = new Object();
    private static final Object sendLock = new Object();
    private static final Seq<Request> pending = new Seq<>();

    private static DatagramSocket socket;
    private static ExecutorService resolver, srvResolver;
    private static boolean initialized;

    private PingService(){
    }

    /**
     * Pings a host without blocking the caller.
     * @param valid called on the main thread with the server data when the server answers
     * @param failed called on the main thread when the server does not answer or the address cannot be resolved
     */
    public static void ping(String address, int port, Cons<Host> valid, Cons<Exception> failed){
        init();

        if(socket == null || socket.isClosed()){
            Core.app.post(() -> failed.get(new IOException("Ping socket is unavailable.")));
            return;
        }

        resolver.submit(() -> {
            InetAddress resolved;
            try{
                resolved = InetAddress.getByName(address);
            }catch(IOException e){
                Core.app.post(() -> failed.get(e));
                return;
            }

            AtomicBoolean handled = new AtomicBoolean();
            send(resolved, port, handled, valid, failed);

            // SRV records only matter for hostnames on the default port, and resolving them costs a DNS lookup,
            // so they are skipped for plain addresses and every IP-based server in the community list
            if(port == Vars.port && !isIpLiteral(address)){
                srvResolver.submit(() -> {
                    try{
                        for(var record : ArcDns.getSrvRecords("_mindustry._tcp." + address)){
                            if(handled.get()) return;
                            send(InetAddress.getByName(record.target), record.port, handled, valid, failed);
                        }
                    }catch(Exception e){
                        Log.debug("Failed to resolve SRV records for '@'", address);
                    }
                });
            }

            float timeout = Core.settings.getInt("serverbrowserpinglimit", 2000) / 1000f;
            Timer.schedule(() -> timeout(handled, failed), timeout);
        });
    }

    /** Sends one ping request and registers it for the response. */
    private static void send(InetAddress address, int port, AtomicBoolean handled, Cons<Host> valid, Cons<Exception> failed){
        //a request that is created after the ping already timed out or was answered must not linger
        if(handled.get()) return;

        Request request = new Request(address, port, handled, valid, failed);

        synchronized(lock){
            pending.add(request);
        }

        try{
            byte[] data = {-2, 1};
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            //sending is not cheap under load, but it must never be serialized with the receiver
            synchronized(sendLock){
                socket.send(packet);
            }
        }catch(IOException e){
            synchronized(lock){
                pending.remove(request, true);
            }
            if(handled.compareAndSet(false, true)){
                Core.app.post(() -> failed.get(e));
            }
        }
    }

    /** Fails the request once the timeout passed, unless a response already claimed it. */
    private static void timeout(AtomicBoolean handled, Cons<Exception> failed){
        synchronized(lock){
            for(int i = pending.size - 1; i >= 0; i--){
                if(pending.get(i).handled == handled){
                    pending.remove(i);
                }
            }
        }

        if(handled.compareAndSet(false, true)){
            Core.app.post(() -> failed.get(new SocketTimeoutException("The server did not respond.")));
        }
    }

    private static void init(){
        if(initialized) return;

        synchronized(lock){
            if(initialized) return;

            try{
                socket = new DatagramSocket();
                //a whole community list may answer within a few milliseconds, so the default buffer would drop replies
                socket.setReceiveBufferSize(1 << 20);
            }catch(IOException e){
                socket = null;
                Log.err("Unable to create the ping socket", e);
                return;
            }

            resolver = Threads.executor("Ping Service", 8);
            srvResolver = Threads.executor("Ping Service SRV", 1);
            Threads.daemon("Ping Service Receiver", PingService::receive);
            initialized = true;
        }
    }

    private static void receive(){
        DatagramPacket packet = new DatagramPacket(new byte[512], 512);

        while(true){
            try{
                socket.receive(packet);
                handle(packet);
            }catch(IOException e){
                if(socket == null || socket.isClosed()) return;
                //a single failed receive must not kill the receiver thread
            }catch(Throwable t){
                Log.err("Failed to handle a ping response", t);
            }
        }
    }

    private static void handle(DatagramPacket packet){
        Seq<Request> matched = new Seq<>();

        synchronized(lock){
            for(int i = pending.size - 1; i >= 0; i--){
                Request request = pending.get(i);
                if(request.port == packet.getPort() && request.address.equals(packet.getAddress())){
                    matched.add(request);
                    pending.remove(i);
                }
            }
        }

        if(matched.isEmpty()) return;

        String address = packet.getAddress().getHostAddress();

        //one response may satisfy several identical requests, so each one parses its own copy
        for(Request request : matched){
            if(request.handled.get()) continue;

            Host host;
            try{
                host = NetworkIO.readServerData((int)Time.timeSinceMillis(request.start), address, ByteBuffer.wrap(packet.getData()));
            }catch(Exception e){
                Log.err("Failed to parse the ping response from '@'", address, e);
                if(request.handled.compareAndSet(false, true)){
                    Core.app.post(() -> request.failed.get(e));
                }
                continue;
            }

            host.port = request.port;

            if(request.handled.compareAndSet(false, true)){
                Core.app.post(() -> request.valid.get(host));
            }
        }
    }

    /** Whether the address is a literal IP, in which case SRV records cannot exist for it. */
    private static boolean isIpLiteral(String address){
        return address.matches("\\d{1,3}(\\.\\d{1,3}){3}") || (address.indexOf(':') != -1 && address.matches("[0-9a-fA-F:%.]+"));
    }

    private static class Request{
        final InetAddress address;
        final int port;
        final AtomicBoolean handled;
        final Cons<Host> valid;
        final Cons<Exception> failed;
        final long start = Time.millis();

        Request(InetAddress address, int port, AtomicBoolean handled, Cons<Host> valid, Cons<Exception> failed){
            this.address = address;
            this.port = port;
            this.handled = handled;
            this.valid = valid;
            this.failed = failed;
        }
    }
}
