package net.minecraft.world.level.levelgen;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public class PatrolSpawner implements CustomSpawner {

    private int nextTick;

    public PatrolSpawner() {}

    @Override
    public int tick(ServerLevel worldserver, boolean flag, boolean flag1) {
        if (!flag) {
            return 0;
        } else if (!worldserver.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)) {
            return 0;
        } else {
            Random random = worldserver.random;

            --this.nextTick;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick += 12000 + random.nextInt(1200);
                long i = worldserver.getDayTime() / 24000L;

                if (i >= 5L && worldserver.isDay()) {
                    if (random.nextInt(5) != 0) {
                        return 0;
                    } else {
                        int j = worldserver.players().size();

                        if (j < 1) {
                            return 0;
                        } else {
                            Player entityhuman = (Player) worldserver.players().get(random.nextInt(j));

                            if (entityhuman.isSpectator()) {
                                return 0;
                            } else if (worldserver.isCloseToVillage(entityhuman.blockPosition(), 2)) {
                                return 0;
                            } else {
                                int k = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
                                int l = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
                                BlockPos.MutableBlockPosition blockposition_mutableblockposition = entityhuman.blockPosition().i().e(k, 0, l);

                                if (!worldserver.hasChunksAt(blockposition_mutableblockposition.getX() - 10, blockposition_mutableblockposition.getY() - 10, blockposition_mutableblockposition.getZ() - 10, blockposition_mutableblockposition.getX() + 10, blockposition_mutableblockposition.getY() + 10, blockposition_mutableblockposition.getZ() + 10)) {
                                    return 0;
                                } else {
                                    Biome biomebase = worldserver.getBiome(blockposition_mutableblockposition);
                                    Biome.BiomeCategory biomebase_geography = biomebase.getBiomeCategory();

                                    if (biomebase_geography == Biome.BiomeCategory.MUSHROOM) {
                                        return 0;
                                    } else {
                                        int i1 = 0;
                                        int j1 = (int) Math.ceil((double) worldserver.getDamageScaler(blockposition_mutableblockposition).getEffectiveDifficulty()) + 1;

                                        for (int k1 = 0; k1 < j1; ++k1) {
                                            ++i1;
                                            blockposition_mutableblockposition.setY(worldserver.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockposition_mutableblockposition).getY());
                                            if (k1 == 0) {
                                                if (!this.spawnPatrolMember(worldserver, blockposition_mutableblockposition, random, true)) {
                                                    break;
                                                }
                                            } else {
                                                this.spawnPatrolMember(worldserver, blockposition_mutableblockposition, random, false);
                                            }

                                            blockposition_mutableblockposition.setX(blockposition_mutableblockposition.getX() + random.nextInt(5) - random.nextInt(5));
                                            blockposition_mutableblockposition.setZ(blockposition_mutableblockposition.getZ() + random.nextInt(5) - random.nextInt(5));
                                        }

                                        return i1;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    return 0;
                }
            }
        }
    }

    private boolean spawnPatrolMember(Level world, BlockPos blockposition, Random random, boolean flag) {
        BlockState iblockdata = world.getType(blockposition);

        if (!NaturalSpawner.isValidEmptySpawnBlock((BlockGetter) world, blockposition, iblockdata, iblockdata.getFluidState(), EntityType.PILLAGER)) {
            return false;
        } else if (!PatrollingMonster.checkPatrollingMonsterSpawnRules(EntityType.PILLAGER, world, MobSpawnType.PATROL, blockposition, random)) {
            return false;
        } else {
            PatrollingMonster entitymonsterpatrolling = (PatrollingMonster) EntityType.PILLAGER.create(world);

            if (entitymonsterpatrolling != null) {
                if (flag) {
                    entitymonsterpatrolling.setPatrolLeader(true);
                    entitymonsterpatrolling.findPatrolTarget();
                }

                entitymonsterpatrolling.setPos((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                entitymonsterpatrolling.prepare(world, world.getDamageScaler(blockposition), MobSpawnType.PATROL, (SpawnGroupData) null, (CompoundTag) null);
                world.addEntity(entitymonsterpatrolling, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL); // CraftBukkit
                return true;
            } else {
                return false;
            }
        }
    }
}
