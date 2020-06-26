package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public abstract class AgableMob extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(AgableMob.class, EntityDataSerializers.BOOLEAN);
    protected int age;
    protected int forcedAge;
    protected int forcedAgeTimer;
    public boolean ageLocked; // CraftBukkit

    protected AgableMob(EntityType<? extends AgableMob> entitytypes, Level world) {
        super(entitytypes, world);
    }

    // Spigot start
    @Override
    public void inactiveTick()
    {
        super.inactiveTick();
        if ( this.level.isClientSide || this.ageLocked )
        { // CraftBukkit
            this.refreshDimensions();
        } else
        {
            int i = this.getAge();

            if ( i < 0 )
            {
                ++i;
                this.setAge( i );
            } else if ( i > 0 )
            {
                --i;
                this.setAge( i );
            }
        }
    }
    // Spigot end

    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
        }

        AgableMob.AgableMobGroupData entityageable_a = (AgableMob.AgableMobGroupData) groupdataentity;

        if (entityageable_a.isShouldSpawnBaby() && entityageable_a.getGroupSize() > 0 && this.random.nextFloat() <= entityageable_a.getBabySpawnChance()) {
            this.setAge(-24000);
        }

        entityageable_a.increaseGroupSizeByOne();
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    @Nullable
    public abstract AgableMob getBreedOffspring(AgableMob entityageable);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(AgableMob.DATA_BABY_ID, false);
    }

    public boolean canBreed() {
        return false;
    }

    public int getAge() {
        return this.level.isClientSide ? ((Boolean) this.entityData.get(AgableMob.DATA_BABY_ID) ? -1 : 1) : this.age;
    }

    public void ageUp(int i, boolean flag) {
        int j = this.getAge();
        int k = j;

        j += i * 20;
        if (j > 0) {
            j = 0;
        }

        int l = j - k;

        this.setAge(j);
        if (flag) {
            this.forcedAge += l;
            if (this.forcedAgeTimer == 0) {
                this.forcedAgeTimer = 40;
            }
        }

        if (this.getAge() == 0) {
            this.setAge(this.forcedAge);
        }

    }

    public void ageUp(int i) {
        this.ageUp(i, false);
    }

    public void setAge(int i) {
        int j = this.age;

        this.age = i;
        if (j < 0 && i >= 0 || j >= 0 && i < 0) {
            this.entityData.set(AgableMob.DATA_BABY_ID, i < 0);
            this.ageBoundaryReached();
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Age", this.getAge());
        nbttagcompound.putInt("ForcedAge", this.forcedAge);
        nbttagcompound.putBoolean("AgeLocked", this.ageLocked); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setAge(nbttagcompound.getInt("Age"));
        this.forcedAge = nbttagcompound.getInt("ForcedAge");
        this.ageLocked = nbttagcompound.getBoolean("AgeLocked"); // CraftBukkit
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (AgableMob.DATA_BABY_ID.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level.isClientSide || ageLocked) { // CraftBukkit
            if (this.forcedAgeTimer > 0) {
                if (this.forcedAgeTimer % 4 == 0) {
                    this.level.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
                }

                --this.forcedAgeTimer;
            }
        } else if (this.isAlive()) {
            int i = this.getAge();

            if (i < 0) {
                ++i;
                this.setAge(i);
            } else if (i > 0) {
                --i;
                this.setAge(i);
            }
        }

    }

    protected void ageBoundaryReached() {}

    @Override
    public boolean isBaby() {
        return this.getAge() < 0;
    }

    @Override
    public void setBaby(boolean flag) {
        this.setAge(flag ? -24000 : 0);
    }

    public static class AgableMobGroupData implements SpawnGroupData {

        private int groupSize;
        private boolean shouldSpawnBaby = true;
        private float babySpawnChance = 0.05F;

        public AgableMobGroupData() {}

        public int getGroupSize() {
            return this.groupSize;
        }

        public void increaseGroupSizeByOne() {
            ++this.groupSize;
        }

        public boolean isShouldSpawnBaby() {
            return this.shouldSpawnBaby;
        }

        public void setShouldSpawnBaby(boolean flag) {
            this.shouldSpawnBaby = flag;
        }

        public float getBabySpawnChance() {
            return this.babySpawnChance;
        }

        public void setBabySpawnChance(float f) {
            this.babySpawnChance = f;
        }
    }
}
