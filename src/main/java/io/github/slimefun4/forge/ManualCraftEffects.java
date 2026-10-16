package io.github.slimefun4.forge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** RC-37's four animation steps, using vanilla sound and particle packets. */
final class ManualCraftEffects {
    static void immediate(ServerLevel level,String station,BlockPos at) {
        switch(station) {
            case "enhanced_crafting_table", "grind_stone" -> sound(level,at,SoundEvents.WOODEN_BUTTON_CLICK_ON);
            case "ore_crusher" -> level.levelEvent(2001,at,Block.getId(Blocks.STONE.defaultBlockState()));
            case "juicer" -> { sound(level,at,SoundEvents.PLAYER_SPLASH); level.levelEvent(2001,at,Block.getId(Blocks.HAY_BLOCK.defaultBlockState())); }
            case "ore_washer" -> {
                level.levelEvent(2001,at,Block.getId(Blocks.WATER.defaultBlockState()));
                sound(level,at,SoundEvents.PLAYER_SPLASH);
            }
            case "smeltery" -> { sound(level,at,SoundEvents.LAVA_POP); level.levelEvent(2004,at,1); }
            case "makeshift_smeltery" -> level.levelEvent(2001,at.below(2),Block.getId(Blocks.FIRE.defaultBlockState()));
            default -> throw new IllegalArgumentException("Unknown immediate station: "+station);
        }
    }
    static void play(ServerLevel level,String station,BlockPos at,UUID actor,int step) {
        switch(station) {
            case "compressor" -> {
                var player=level.getServer().getPlayerList().getPlayer(actor);
                if(step<3 && player!=null) {
                    var sound=step==1?SoundEvents.PISTON_CONTRACT:SoundEvents.PISTON_EXTEND;
                    player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),SoundSource.PLAYERS,
                        player.getX(),player.getEyeY(),player.getZ(),1,1,level.random.nextLong()));
                }
            }
            case "pressure_chamber" -> {
                sound(level,at,SoundEvents.TNT_PRIMED);
                for(int i=0;i<3;i++) level.levelEvent(2000,at.above(),Direction.UP.get3DDataValue());
                sound(level,at,step<3?SoundEvents.TNT_PRIMED:SoundEvents.ARROW_HIT_PLAYER);
            }
            case "armor_forge" -> sound(level,at,step<3?SoundEvents.ARROW_HIT_PLAYER:SoundEvents.ANVIL_USE);
            case "magic_workbench" -> {
                level.levelEvent(2004,at,1);
                level.levelEvent(2003,at,1);
                sound(level,at,step<3?SoundEvents.WOODEN_BUTTON_CLICK_ON:SoundEvents.ARROW_HIT_PLAYER);
            }
            default -> throw new IllegalArgumentException("Unknown delayed station: "+station);
        }
    }
    private static void sound(ServerLevel level,BlockPos pos,SoundEvent sound) {
        level.playSound(null,pos.getX(),pos.getY(),pos.getZ(),sound,SoundSource.BLOCKS,1,1);
    }
    private ManualCraftEffects() {}
}
