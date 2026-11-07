package mindustry.client.features;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import mindustry.game.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * Shows the wave countdown and the units of the next wave at enemy spawn points.
 * <p>
 * The spawn radius is the per-map rule that the game itself uses ({@link Rules#dropZoneRadius}); air units
 * are shown at the position they actually spawn at, which is computed exactly like {@code WaveSpawner}.
 */
public class SpawnInfoOverlay{
    private static final float unitSize = 20f, rowHeight = 22f;

    /** Called from the world overlay renderer. */
    public static void draw(){
        Seq<Tile> spawns = spawner.getSpawns();

        if(spawns.isEmpty() || !state.isPlaying() || state.rules.editor || !state.rules.waves) return;

        if(Core.settings.getBool("alwaysshowspawninfo", false)){
            for(Tile tile : spawns){
                drawSpawn(tile);
            }
            return;
        }

        //hover mode: only the nearest spawn circle under the cursor is shown
        if(Core.scene.hasMouse()) return;

        Tile hovered = null;
        float best = state.rules.dropZoneRadius;
        for(Tile tile : spawns){
            float dst = Mathf.dst(tile.worldx(), tile.worldy(), Core.input.mouseWorldX(), Core.input.mouseWorldY());
            if(dst <= best){
                best = dst;
                hovered = tile;
            }
        }

        if(hovered != null){
            drawSpawn(hovered);
        }
    }

    private static void drawSpawn(Tile tile){
        float x = tile.worldx(), y = tile.worldy();

        //a static timer is meaningless on maps where waves are triggered manually
        if(state.rules.waveTimer){
            drawCountdown(x, y + 40f);
        }

        float currentY = y + 18f;
        for(SpawnGroup group : state.rules.spawns){
            if(group.type == null || group.type.flying || !group.canSpawn(tile.pos())) continue;

            int count = group.getSpawned(state.wave - 1);
            if(count <= 0) continue;

            drawUnit(group.type, count, x, currentY);
            currentY -= rowHeight;
        }

        drawFlyers(tile, currentY);
    }

    private static void drawCountdown(float x, float y){
        int seconds = Math.max(0, (int)(state.wavetime / 60f));
        String text = (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
        Drawf.text(text, x, y, Color.white, 1f);
    }

    private static void drawFlyers(Tile tile, float startY){
        boolean any = false;
        for(SpawnGroup group : state.rules.spawns){
            if(group.type != null && group.type.flying && group.canSpawn(tile.pos()) && group.getSpawned(state.wave - 1) > 0){
                any = true;
                break;
            }
        }

        if(!any) return;

        float x, y;
        if(state.rules.airUseSpawns){
            //air units spawn on the spawn point itself, so the list continues under the ground units
            x = tile.worldx();
            y = startY;
        }else{
            //same projection the wave spawner uses: from the map center through the spawn point to the border
            float angle = Angles.angle(world.width() / 2f, world.height() / 2f, tile.x, tile.y);
            float trns = Math.max(world.width(), world.height()) * Mathf.sqrt2 * tilesize;
            x = Mathf.clamp(world.width() * tilesize / 2f + Angles.trnsx(angle, trns), 0, world.width() * tilesize);
            y = Mathf.clamp(world.height() * tilesize / 2f + Angles.trnsy(angle, trns), 0, world.height() * tilesize) + 18f;
        }

        for(SpawnGroup group : state.rules.spawns){
            if(group.type == null || !group.type.flying || !group.canSpawn(tile.pos())) continue;

            int count = group.getSpawned(state.wave - 1);
            if(count <= 0) continue;

            drawUnit(group.type, count, x, y);
            y -= rowHeight;
        }
    }

    private static void drawUnit(UnitType type, int count, float x, float y){
        Draw.color(Color.white);
        Draw.rect(type.uiIcon, x - unitSize * 0.6f, y, unitSize, unitSize);
        Drawf.text("x" + count, x + unitSize * 0.8f, y, Color.white, 0.85f);
    }
}
