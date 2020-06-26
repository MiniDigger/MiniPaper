package net.minecraft.world.entity.ai.village;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class VillageSiege implements CustomSpawner {

    private boolean hasSetupSiege;
    private VillageSiege.State siegeState;
    private int zombiesToSpawn;
    private int nextSpawnTime;
    private int spawnX;
    private int spawnY;
    private int spawnZ;

    public VillageSiege() {
        this.siegeState = VillageSiege.State.SIEGE_DONE;
    }

    @Override
    public int tick(ServerLevel worldserver, boolean flag, boolean flag1) {
        if (!worldserver.isDay() && flag) {
            float f = worldserver.getTimeOfDay(0.0F);

            if ((double) f == 0.5D) {
                this.siegeState = worldserver.random.nextInt(10) == 0 ? VillageSiege.State.SIEGE_TONIGHT : VillageSiege.State.SIEGE_DONE;
            }

            if (this.siegeState == VillageSiege.State.SIEGE_DONE) {
                return 0;
            } else {
                if (!this.hasSetupSiege) {
                    if (!this.tryToSetupSiege(worldserver)) {
                        return 0;
                    }

                    this.hasSetupSiege = true;
                }

                if (this.nextSpawnTime > 0) {
                    --this.nextSpawnTime;
                    return 0;
                } else {
                    this.nextSpawnTime = 2;
                    if (this.zombiesToSpawn > 0) {
                        this.trySpawn(worldserver);
                        --this.zombiesToSpawn;
                    } else {
                        this.siegeState = VillageSiege.State.SIEGE_DONE;
                    }

                    return 1;
                }
            }
        } else {
            this.siegeState = VillageSiege.State.SIEGE_DONE;
            this.hasSetupSiege = false;
            return 0;
        }
    }

    private boolean tryToSetupSiege(ServerLevel worldserver) {
        Iterator iterator = worldserver.players().iterator();

        while (iterator.hasNext()) {
            Player entityhuman = (Player) iterator.next();

            if (!entityhuman.isSpectator()) {
                BlockPos blockposition = entityhuman.blockPosition();

                if (worldserver.isVillage(blockposition) && worldserver.getBiome(blockposition).getBiomeCategory() != Biome.BiomeCategory.MUSHROOM) {
                    for (int i = 0; i < 10; ++i) {
                        float f = worldserver.random.nextFloat() * 6.2831855F;

                        this.spawnX = blockposition.getX() + Mth.floor(Mth.cos(f) * 32.0F);
                        this.spawnY = blockposition.getY();
                        this.spawnZ = blockposition.getZ() + Mth.floor(Mth.sin(f) * 32.0F);
                        if (this.findRandomSpawnPos(worldserver, new BlockPos(this.spawnX, this.spawnY, this.spawnZ)) != null) {
                            this.nextSpawnTime = 0;
                            this.zombiesToSpawn = 20;
                            break;
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    private void trySpawn(ServerLevel worldserver) {
        Vec3 vec3d = this.findRandomSpawnPos(worldserver, new BlockPos(this.spawnX, this.spawnY, this.spawnZ));

        if (vec3d != null) {
            Zombie entityzombie;

            try {
                entityzombie = new Zombie(worldserver);
                entityzombie.prepare(worldserver, worldserver.getDamageScaler(entityzombie.blockPosition()), MobSpawnType.EVENT, (SpawnGroupData) null, (CompoundTag) null);
            } catch (Exception exception) {
                exception.printStackTrace();
                return;
            }

            entityzombie.moveTo(vec3d.x, vec3d.y, vec3d.z, worldserver.random.nextFloat() * 360.0F, 0.0F);
            worldserver.addEntity(entityzombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION); // CraftBukkit
        }
    }

    @Nullable
    private Vec3 findRandomSpawnPos(ServerLevel worldserver, BlockPos blockposition) {
        for (int i = 0; i < 10; ++i) {
            int j = blockposition.getX() + worldserver.random.nextInt(16) - 8;
            int k = blockposition.getZ() + worldserver.random.nextInt(16) - 8;
            int l = worldserver.getHeight(Heightmap.Types.WORLD_SURFACE, j, k);
            BlockPos blockposition1 = new BlockPos(j, l, k);

            if (worldserver.isVillage(blockposition1) && Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, worldserver, MobSpawnType.EVENT, blockposition1, worldserver.random)) {
                return Vec3.atBottomCenterOf((Vec3i) blockposition1);
            }
        }

        return null;
    }

    static enum State {

        SIEGE_CAN_ACTIVATE, SIEGE_TONIGHT, SIEGE_DONE;

        private State() {}
    }
}
