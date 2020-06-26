package net.minecraft.world.level.levelgen;

import java.util.Iterator;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {

    private int nextTick;

    public PhantomSpawner() {}

    @Override
    public int tick(ServerLevel worldserver, boolean flag, boolean flag1) {
        if (!flag) {
            return 0;
        } else if (!worldserver.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        } else {
            Random random = worldserver.random;

            --this.nextTick;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick += (60 + random.nextInt(60)) * 20;
                if (worldserver.getSkyDarken() < 5 && worldserver.dimensionType().hasSkyLight()) {
                    return 0;
                } else {
                    int i = 0;
                    Iterator iterator = worldserver.players().iterator();

                    while (iterator.hasNext()) {
                        Player entityhuman = (Player) iterator.next();

                        if (!entityhuman.isSpectator()) {
                            BlockPos blockposition = entityhuman.blockPosition();

                            if (!worldserver.dimensionType().hasSkyLight() || blockposition.getY() >= worldserver.getSeaLevel() && worldserver.canSeeSky(blockposition)) {
                                DifficultyInstance difficultydamagescaler = worldserver.getDamageScaler(blockposition);

                                if (difficultydamagescaler.isHarderThan(random.nextFloat() * 3.0F)) {
                                    ServerStatsCounter serverstatisticmanager = ((ServerPlayer) entityhuman).getStats();
                                    int j = Mth.clamp(serverstatisticmanager.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                    boolean flag2 = true;

                                    if (random.nextInt(j) >= 72000) {
                                        BlockPos blockposition1 = blockposition.above(20 + random.nextInt(15)).east(-10 + random.nextInt(21)).south(-10 + random.nextInt(21));
                                        BlockState iblockdata = worldserver.getType(blockposition1);
                                        FluidState fluid = worldserver.getFluidState(blockposition1);

                                        if (NaturalSpawner.isValidEmptySpawnBlock((BlockGetter) worldserver, blockposition1, iblockdata, fluid, EntityType.PHANTOM)) {
                                            SpawnGroupData groupdataentity = null;
                                            int k = 1 + random.nextInt(difficultydamagescaler.getDifficulty().getId() + 1);

                                            for (int l = 0; l < k; ++l) {
                                                Phantom entityphantom = (Phantom) EntityType.PHANTOM.create((Level) worldserver);

                                                entityphantom.moveTo(blockposition1, 0.0F, 0.0F);
                                                groupdataentity = entityphantom.prepare(worldserver, difficultydamagescaler, MobSpawnType.NATURAL, groupdataentity, (CompoundTag) null);
                                                worldserver.addEntity(entityphantom, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
                                            }

                                            i += k;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return i;
                }
            }
        }
    }
}