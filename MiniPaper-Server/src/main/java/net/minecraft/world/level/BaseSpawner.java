package net.minecraft.world.level;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.util.WeighedRandom;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BaseSpawner {

    private static final Logger LOGGER = LogManager.getLogger();
    public int spawnDelay = 20;
    public final List<SpawnData> spawnPotentials = Lists.newArrayList();
    public SpawnData nextSpawnData = new SpawnData();
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    @Nullable
    private Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;

    public BaseSpawner() {}

    @Nullable
    public ResourceLocation getEntityId() {
        String s = this.nextSpawnData.getTag().getString("id");

        try {
            return StringUtil.isNullOrEmpty(s) ? null : new ResourceLocation(s);
        } catch (ResourceLocationException resourcekeyinvalidexception) {
            BlockPos blockposition = this.getPos();

            BaseSpawner.LOGGER.warn("Invalid entity id '{}' at spawner {}:[{},{},{}]", s, this.getLevel().getDimensionKey().location(), blockposition.getX(), blockposition.getY(), blockposition.getZ());
            return null;
        }
    }

    public void setEntityId(EntityType<?> entitytypes) {
        this.nextSpawnData.getTag().putString("id", Registry.ENTITY_TYPE.getKey(entitytypes).toString());
        this.spawnPotentials.clear(); // CraftBukkit - SPIGOT-3496, MC-92282
    }

    private boolean isNearPlayer() {
        BlockPos blockposition = this.getPos();

        return this.getLevel().hasNearbyAlivePlayer((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, (double) this.requiredPlayerRange);
    }

    public void tick() {
        if (!this.isNearPlayer()) {
            this.oSpin = this.spin;
        } else {
            Level world = this.getLevel();
            BlockPos blockposition = this.getPos();

            if (world.isClientSide) {
                double d0 = (double) blockposition.getX() + world.random.nextDouble();
                double d1 = (double) blockposition.getY() + world.random.nextDouble();
                double d2 = (double) blockposition.getZ() + world.random.nextDouble();

                world.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                world.addParticle(ParticleTypes.FLAME, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                if (this.spawnDelay > 0) {
                    --this.spawnDelay;
                }

                this.oSpin = this.spin;
                this.spin = (this.spin + (double) (1000.0F / ((float) this.spawnDelay + 200.0F))) % 360.0D;
            } else {
                if (this.spawnDelay == -1) {
                    this.delay();
                }

                if (this.spawnDelay > 0) {
                    --this.spawnDelay;
                    return;
                }

                boolean flag = false;

                for (int i = 0; i < this.spawnCount; ++i) {
                    CompoundTag nbttagcompound = this.nextSpawnData.getTag();
                    Optional<EntityType<?>> optional = EntityType.by(nbttagcompound);

                    if (!optional.isPresent()) {
                        this.delay();
                        return;
                    }

                    ListTag nbttaglist = nbttagcompound.getList("Pos", 6);
                    int j = nbttaglist.size();
                    double d3 = j >= 1 ? nbttaglist.getDouble(0) : (double) blockposition.getX() + (world.random.nextDouble() - world.random.nextDouble()) * (double) this.spawnRange + 0.5D;
                    double d4 = j >= 2 ? nbttaglist.getDouble(1) : (double) (blockposition.getY() + world.random.nextInt(3) - 1);
                    double d5 = j >= 3 ? nbttaglist.getDouble(2) : (double) blockposition.getZ() + (world.random.nextDouble() - world.random.nextDouble()) * (double) this.spawnRange + 0.5D;

                    if (world.noCollision(((EntityType) optional.get()).getAABB(d3, d4, d5)) && SpawnPlacements.checkSpawnRules((EntityType) optional.get(), world.getLevel(), MobSpawnType.SPAWNER, new BlockPos(d3, d4, d5), world.getRandom())) {
                        Entity entity = EntityType.loadEntityRecursive(nbttagcompound, world, (entity1) -> {
                            entity1.moveTo(d3, d4, d5, entity1.yRot, entity1.xRot);
                            return entity1;
                        });

                        if (entity == null) {
                            this.delay();
                            return;
                        }

                        int k = world.getEntitiesOfClass(entity.getClass(), (new AABB((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), (double) (blockposition.getX() + 1), (double) (blockposition.getY() + 1), (double) (blockposition.getZ() + 1))).inflate((double) this.spawnRange)).size();

                        if (k >= this.maxNearbyEntities) {
                            this.delay();
                            return;
                        }

                        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), world.random.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob) {
                            Mob entityinsentient = (Mob) entity;

                            if (!entityinsentient.checkSpawnRules((LevelAccessor) world, MobSpawnType.SPAWNER) || !entityinsentient.checkSpawnObstruction((LevelReader) world)) {
                                continue;
                            }

                            if (this.nextSpawnData.getTag().size() == 1 && this.nextSpawnData.getTag().contains("id", 8)) {
                                ((Mob) entity).prepare(world, world.getDamageScaler(entity.blockPosition()), MobSpawnType.SPAWNER, (SpawnGroupData) null, (CompoundTag) null);
                            }
                            // Spigot Start
                            if ( entityinsentient.level.spigotConfig.nerfSpawnerMobs )
                            {
                                entityinsentient.aware = false;
                            }
                            // Spigot End
                        }
                        // Spigot Start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callSpawnerSpawnEvent(entity, blockposition).isCancelled()) {
                            Entity vehicle = entity.getVehicle();
                            if (vehicle != null) {
                                vehicle.removed = true;
                            }
                            for (Entity passenger : entity.getIndirectPassengers()) {
                                passenger.removed = true;
                            }
                            continue;
                        }
                        // Spigot End

                        this.addWithPassengers(entity);
                        world.levelEvent(2004, blockposition, 0);
                        if (entity instanceof Mob) {
                            ((Mob) entity).spawnAnim();
                        }

                        flag = true;
                    }
                }

                if (flag) {
                    this.delay();
                }
            }

        }
    }

    private void addWithPassengers(Entity entity) {
        if (this.getLevel().addEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER)) { // CraftBukkit
            Iterator iterator = entity.getPassengers().iterator();

            while (iterator.hasNext()) {
                Entity entity1 = (Entity) iterator.next();

                this.addWithPassengers(entity1);
            }

        }
    }

    private void delay() {
        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            int i = this.maxSpawnDelay - this.minSpawnDelay;

            this.spawnDelay = this.minSpawnDelay + this.getLevel().random.nextInt(i);
        }

        if (!this.spawnPotentials.isEmpty()) {
            this.setNextSpawnData((SpawnData) WeighedRandom.a(this.getLevel().random, this.spawnPotentials));
        }

        this.broadcastEvent(1);
    }

    public void load(CompoundTag nbttagcompound) {
        this.spawnDelay = nbttagcompound.getShort("Delay");
        this.spawnPotentials.clear();
        if (nbttagcompound.contains("SpawnPotentials", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("SpawnPotentials", 10);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                this.spawnPotentials.add(new SpawnData(nbttaglist.getCompound(i)));
            }
        }

        if (nbttagcompound.contains("SpawnData", 10)) {
            this.setNextSpawnData(new SpawnData(1, nbttagcompound.getCompound("SpawnData")));
        } else if (!this.spawnPotentials.isEmpty()) {
            this.setNextSpawnData((SpawnData) WeighedRandom.a(this.getLevel().random, this.spawnPotentials));
        }

        if (nbttagcompound.contains("MinSpawnDelay", 99)) {
            this.minSpawnDelay = nbttagcompound.getShort("MinSpawnDelay");
            this.maxSpawnDelay = nbttagcompound.getShort("MaxSpawnDelay");
            this.spawnCount = nbttagcompound.getShort("SpawnCount");
        }

        if (nbttagcompound.contains("MaxNearbyEntities", 99)) {
            this.maxNearbyEntities = nbttagcompound.getShort("MaxNearbyEntities");
            this.requiredPlayerRange = nbttagcompound.getShort("RequiredPlayerRange");
        }

        if (nbttagcompound.contains("SpawnRange", 99)) {
            this.spawnRange = nbttagcompound.getShort("SpawnRange");
        }

        if (this.getLevel() != null) {
            this.displayEntity = null;
        }

    }

    public CompoundTag save(CompoundTag nbttagcompound) {
        ResourceLocation minecraftkey = this.getEntityId();

        if (minecraftkey == null) {
            return nbttagcompound;
        } else {
            nbttagcompound.putShort("Delay", (short) this.spawnDelay);
            nbttagcompound.putShort("MinSpawnDelay", (short) this.minSpawnDelay);
            nbttagcompound.putShort("MaxSpawnDelay", (short) this.maxSpawnDelay);
            nbttagcompound.putShort("SpawnCount", (short) this.spawnCount);
            nbttagcompound.putShort("MaxNearbyEntities", (short) this.maxNearbyEntities);
            nbttagcompound.putShort("RequiredPlayerRange", (short) this.requiredPlayerRange);
            nbttagcompound.putShort("SpawnRange", (short) this.spawnRange);
            nbttagcompound.put("SpawnData", this.nextSpawnData.getTag().copy());
            ListTag nbttaglist = new ListTag();

            if (this.spawnPotentials.isEmpty()) {
                nbttaglist.add(this.nextSpawnData.save());
            } else {
                Iterator iterator = this.spawnPotentials.iterator();

                while (iterator.hasNext()) {
                    SpawnData mobspawnerdata = (SpawnData) iterator.next();

                    nbttaglist.add(mobspawnerdata.save());
                }
            }

            nbttagcompound.put("SpawnPotentials", nbttaglist);
            return nbttagcompound;
        }
    }

    public boolean onEventTriggered(int i) {
        if (i == 1 && this.getLevel().isClientSide) {
            this.spawnDelay = this.minSpawnDelay;
            return true;
        } else {
            return false;
        }
    }

    public void setNextSpawnData(SpawnData mobspawnerdata) {
        this.nextSpawnData = mobspawnerdata;
    }

    public abstract void broadcastEvent(int i);

    public abstract Level getLevel();

    public abstract BlockPos getPos();
}
