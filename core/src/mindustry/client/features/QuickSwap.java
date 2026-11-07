package mindustry.client.features;

import arc.*;
import mindustry.content.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * Swaps conveyors and liquid conduits for their bridge counterpart while shift is held.
 * <p>
 * While shift is down and a supported block is selected, the selection is temporarily replaced with the
 * matching bridge; releasing shift restores the original block. This never touches the hotbar's saved
 * selection, so the category memory keeps the real block.
 */
public class QuickSwap{
    private static Block from, to;

    /** Called from the client's per-frame update. */
    public static void update(){
        boolean enabled = Core.settings.getBool("quickconveyorswap", true) && state.isPlaying() && !state.rules.editor;

        if(from != null){
            if(!enabled || !Core.input.shift()){
                //restore on release, on disable and on leaving the game; a manual selection change wins over the swap
                if(control.input.block == to) control.input.block = from;
                from = null;
                to = null;
                return;
            }

            //still swapped: nothing to do, unless the hand was changed to something else
            if(control.input.block == to) return;

            from = null;
            to = null;
        }

        if(!enabled || !Core.input.shift()) return;

        Block selected = control.input.block;
        Block bridge = bridgeFor(selected);

        if(bridge != null && bridge.unlockedNow()){
            from = selected;
            to = bridge;
            control.input.block = bridge;
        }
    }

    /** @return the bridge counterpart of the given block, or null if it has none. */
    private static Block bridgeFor(Block selected){
        if(selected == Blocks.conveyor) return Blocks.itemBridge;
        if(selected == Blocks.titaniumConveyor) return Blocks.phaseConveyor;
        //liquid conduits
        if(selected == Blocks.conduit) return Blocks.bridgeConduit;
        if(selected == Blocks.pulseConduit) return Blocks.phaseConduit;
        if(selected == Blocks.reinforcedConduit) return Blocks.reinforcedBridgeConduit;
        return null;
    }
}
