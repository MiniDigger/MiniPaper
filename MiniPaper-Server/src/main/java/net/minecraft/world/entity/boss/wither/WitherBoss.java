package net.minecraft.world.entity.boss.wither;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class WitherBoss extends Monster implements RangedAttackMob {

    private static final EntityDataAccessor<Integer> DATA_TARGET_A = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_B = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_C = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<Integer>> DATA_TARGETS = ImmutableList.of(WitherBoss.DATA_TARGET_A, WitherBoss.DATA_TARGET_B, WitherBoss.DATA_TARGET_C);
    private static final EntityDataAccessor<Integer> DATA_ID_INV = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private final float[] xRotHeads = new float[2];
    private final float[] yRotHeads = new float[2];
    private final float[] xRotOHeads = new float[2];
    private final float[] yRotOHeads = new float[2];
    private final int[] nextHeadUpdate = new int[2];
    private final int[] idleHeadUpdates = new int[2];
    private int destroyBlocksTick;
    public final ServerBossEvent bossEvent;
    private static final Predicate<LivingEntity> LIVING_ENTITY_SELECTOR = (entityliving) -> {
        return entityliving.getMobType() != MobType.UNDEAD && entityliving.attackable();
    };
    private static final TargetingConditions TARGETING_CONDITIONS = (new TargetingConditions()).range(20.0D).selector(WitherBoss.LIVING_ENTITY_SELECTOR);

    public WitherBoss(EntityType<? extends WitherBoss> entitytypes, Level world) {
        super(entitytypes, world);
        this.bossEvent = (ServerBossEvent) (new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS)).setDarkenScreen(true);
        this.setHealth(this.getMaxHealth());
        this.getNavigation().setCanFloat(true);
        this.xpReward = 50;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new WitherBoss.WitherDoNothingGoal());
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 40, 20.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 0, false, false, WitherBoss.LIVING_ENTITY_SELECTOR));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(WitherBoss.DATA_TARGET_A, 0);
        this.entityData.register(WitherBoss.DATA_TARGET_B, 0);
        this.entityData.register(WitherBoss.DATA_TARGET_C, 0);
        this.entityData.register(WitherBoss.DATA_ID_INV, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Invul", this.getInvulnerableTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setInvulnerableTicks(nbttagcompound.getInt("Invul"));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }

    }

    @Override
    public void setCustomName(@Nullable Component ichatbasecomponent) {
        super.setCustomName(ichatbasecomponent);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public void aiStep() {
        Vec3 vec3d = this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D);

        if (!this.level.isClientSide && this.getAlternativeTarget(0) > 0) {
            Entity entity = this.level.getEntity(this.getAlternativeTarget(0));

            if (entity != null) {
                double d0 = vec3d.y;

                if (this.getY() < entity.getY() || !this.isPowered() && this.getY() < entity.getY() + 5.0D) {
                    d0 = Math.max(0.0D, d0);
                    d0 += 0.3D - d0 * 0.6000000238418579D;
                }

                vec3d = new Vec3(vec3d.x, d0, vec3d.z);
                Vec3 vec3d1 = new Vec3(entity.getX() - this.getX(), 0.0D, entity.getZ() - this.getZ());

                if (getHorizontalDistanceSqr(vec3d1) > 9.0D) {
                    Vec3 vec3d2 = vec3d1.normalize();

                    vec3d = vec3d.add(vec3d2.x * 0.3D - vec3d.x * 0.6D, 0.0D, vec3d2.z * 0.3D - vec3d.z * 0.6D);
                }
            }
        }

        this.setDeltaMovement(vec3d);
        if (getHorizontalDistanceSqr(vec3d) > 0.05D) {
            this.yRot = (float) Mth.atan2(vec3d.z, vec3d.x) * 57.295776F - 90.0F;
        }

        super.aiStep();

        int i;

        for (i = 0; i < 2; ++i) {
            this.yRotOHeads[i] = this.yRotHeads[i];
            this.xRotOHeads[i] = this.xRotHeads[i];
        }

        int j;

        for (i = 0; i < 2; ++i) {
            j = this.getAlternativeTarget(i + 1);
            Entity entity1 = null;

            if (j > 0) {
                entity1 = this.level.getEntity(j);
            }

            if (entity1 != null) {
                double d1 = this.getHeadX(i + 1);
                double d2 = this.getHeadY(i + 1);
                double d3 = this.getHeadZ(i + 1);
                double d4 = entity1.getX() - d1;
                double d5 = entity1.getEyeY() - d2;
                double d6 = entity1.getZ() - d3;
                double d7 = (double) Mth.sqrt(d4 * d4 + d6 * d6);
                float f = (float) (Mth.atan2(d6, d4) * 57.2957763671875D) - 90.0F;
                float f1 = (float) (-(Mth.atan2(d5, d7) * 57.2957763671875D));

                this.xRotHeads[i] = this.rotlerp(this.xRotHeads[i], f1, 40.0F);
                this.yRotHeads[i] = this.rotlerp(this.yRotHeads[i], f, 10.0F);
            } else {
                this.yRotHeads[i] = this.rotlerp(this.yRotHeads[i], this.yBodyRot, 10.0F);
            }
        }

        boolean flag = this.isPowered();

        for (j = 0; j < 3; ++j) {
            double d8 = this.getHeadX(j);
            double d9 = this.getHeadY(j);
            double d10 = this.getHeadZ(j);

            this.level.addParticle(ParticleTypes.SMOKE, d8 + this.random.nextGaussian() * 0.30000001192092896D, d9 + this.random.nextGaussian() * 0.30000001192092896D, d10 + this.random.nextGaussian() * 0.30000001192092896D, 0.0D, 0.0D, 0.0D);
            if (flag && this.level.random.nextInt(4) == 0) {
                this.level.addParticle(ParticleTypes.ENTITY_EFFECT, d8 + this.random.nextGaussian() * 0.30000001192092896D, d9 + this.random.nextGaussian() * 0.30000001192092896D, d10 + this.random.nextGaussian() * 0.30000001192092896D, 0.699999988079071D, 0.699999988079071D, 0.5D);
            }
        }

        if (this.getInvulnerableTicks() > 0) {
            for (j = 0; j < 3; ++j) {
                this.level.addParticle(ParticleTypes.ENTITY_EFFECT, this.getX() + this.random.nextGaussian(), this.getY() + (double) (this.random.nextFloat() * 3.3F), this.getZ() + this.random.nextGaussian(), 0.699999988079071D, 0.699999988079071D, 0.8999999761581421D);
            }
        }

    }

    @Override
    protected void customServerAiStep() {
        int i;

        if (this.getInvulnerableTicks() > 0) {
            i = this.getInvulnerableTicks() - 1;
            if (i <= 0) {
                Explosion.BlockInteraction explosion_effect = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.NONE;
                // CraftBukkit start
                // this.world.createExplosion(this, this.locX(), this.getHeadY(), this.locZ(), 7.0F, false, explosion_effect);
                ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), 7.0F, false);
                this.level.getServerOH().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    this.level.explode(this, this.getX(), this.getEyeY(), this.getZ(), event.getRadius(), event.getFire(), explosion_effect);
                }
                // CraftBukkit end

                if (!this.isSilent()) {
                    // CraftBukkit start - Use relative location for far away sounds
                    // this.world.b(1023, new BlockPosition(this), 0);
                    int viewDistance = ((ServerLevel) this.level).getServerOH().getViewDistance() * 16;
                    for (ServerPlayer player : (List<ServerPlayer>) MinecraftServer.getServer().getPlayerList().players) {
                        double deltaX = this.getX() - player.getX();
                        double deltaZ = this.getZ() - player.getZ();
                        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                        if ( level.spigotConfig.witherSpawnSoundRadius > 0 && distanceSquared > level.spigotConfig.witherSpawnSoundRadius * level.spigotConfig.witherSpawnSoundRadius ) continue; // Spigot
                        if (distanceSquared > viewDistance * viewDistance) {
                            double deltaLength = Math.sqrt(distanceSquared);
                            double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                            double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                            player.connection.sendPacket(new ClientboundLevelEventPacket(1023, new BlockPos((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                        } else {
                            player.connection.sendPacket(new ClientboundLevelEventPacket(1023, this.blockPosition(), 0, true));
                        }
                    }
                    // CraftBukkit end
                }
            }

            this.setInvulnerableTicks(i);
            if (this.tickCount % 10 == 0) {
                this.heal(10.0F, EntityRegainHealthEvent.RegainReason.WITHER_SPAWN); // CraftBukkit
            }

        } else {
            super.customServerAiStep();

            int j;

            for (i = 1; i < 3; ++i) {
                if (this.tickCount >= this.nextHeadUpdate[i - 1]) {
                    this.nextHeadUpdate[i - 1] = this.tickCount + 10 + this.random.nextInt(10);
                    if (this.level.getDifficulty() == Difficulty.NORMAL || this.level.getDifficulty() == Difficulty.HARD) {
                        int k = i - 1;
                        int l = this.idleHeadUpdates[i - 1];

                        this.idleHeadUpdates[k] = this.idleHeadUpdates[i - 1] + 1;
                        if (l > 15) {
                            float f = 10.0F;
                            float f1 = 5.0F;
                            double d0 = Mth.nextDouble(this.random, this.getX() - 10.0D, this.getX() + 10.0D);
                            double d1 = Mth.nextDouble(this.random, this.getY() - 5.0D, this.getY() + 5.0D);
                            double d2 = Mth.nextDouble(this.random, this.getZ() - 10.0D, this.getZ() + 10.0D);

                            this.performRangedAttack(i + 1, d0, d1, d2, true);
                            this.idleHeadUpdates[i - 1] = 0;
                        }
                    }

                    j = this.getAlternativeTarget(i);
                    if (j > 0) {
                        Entity entity = this.level.getEntity(j);

                        if (entity != null && entity.isAlive() && this.distanceToSqr(entity) <= 900.0D && this.canSee(entity)) {
                            if (entity instanceof Player && ((Player) entity).abilities.invulnerable) {
                                this.setAlternativeTarget(i, 0);
                            } else {
                                this.performRangedAttack(i + 1, (LivingEntity) entity);
                                this.nextHeadUpdate[i - 1] = this.tickCount + 40 + this.random.nextInt(20);
                                this.idleHeadUpdates[i - 1] = 0;
                            }
                        } else {
                            this.setAlternativeTarget(i, 0);
                        }
                    } else {
                        List<LivingEntity> list = this.level.getNearbyEntities(LivingEntity.class, WitherBoss.TARGETING_CONDITIONS, this, this.getBoundingBox().inflate(20.0D, 8.0D, 20.0D));

                        for (int i1 = 0; i1 < 10 && !list.isEmpty(); ++i1) {
                            LivingEntity entityliving = (LivingEntity) list.get(this.random.nextInt(list.size()));

                            if (entityliving != this && entityliving.isAlive() && this.canSee(entityliving)) {
                                if (entityliving instanceof Player) {
                                    if (!((Player) entityliving).abilities.invulnerable) {
                                        if (CraftEventFactory.callEntityTargetLivingEvent(this, entityliving, EntityTargetEvent.TargetReason.CLOSEST_PLAYER).isCancelled()) continue; // CraftBukkit
                                        this.setAlternativeTarget(i, entityliving.getId());
                                    }
                                } else {
                                    if (CraftEventFactory.callEntityTargetLivingEvent(this, entityliving, EntityTargetEvent.TargetReason.CLOSEST_ENTITY).isCancelled()) continue; // CraftBukkit
                                    this.setAlternativeTarget(i, entityliving.getId());
                                }
                                break;
                            }

                            list.remove(entityliving);
                        }
                    }
                }
            }

            if (this.getTarget() != null) {
                this.setAlternativeTarget(0, this.getTarget().getId());
            } else {
                this.setAlternativeTarget(0, 0);
            }

            if (this.destroyBlocksTick > 0) {
                --this.destroyBlocksTick;
                if (this.destroyBlocksTick == 0 && this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    i = Mth.floor(this.getY());
                    j = Mth.floor(this.getX());
                    int j1 = Mth.floor(this.getZ());
                    boolean flag = false;

                    for (int k1 = -1; k1 <= 1; ++k1) {
                        for (int l1 = -1; l1 <= 1; ++l1) {
                            for (int i2 = 0; i2 <= 3; ++i2) {
                                int j2 = j + k1;
                                int k2 = i + i2;
                                int l2 = j1 + l1;
                                BlockPos blockposition = new BlockPos(j2, k2, l2);
                                BlockState iblockdata = this.level.getType(blockposition);

                                if (canDestroy(iblockdata)) {
                                    // CraftBukkit start
                                    if (CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                                        continue;
                                    }
                                    // CraftBukkit end
                                    flag = this.level.destroyBlock(blockposition, true, this) || flag;
                                }
                            }
                        }
                    }

                    if (flag) {
                        this.level.levelEvent((Player) null, 1022, this.blockPosition(), 0);
                    }
                }
            }

            if (this.tickCount % 20 == 0) {
                this.heal(1.0F, EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
            }

            this.bossEvent.setPercent(this.getHealth() / this.getMaxHealth());
        }
    }

    public static boolean canDestroy(BlockState iblockdata) {
        return !iblockdata.isAir() && !BlockTags.WITHER_IMMUNE.contains(iblockdata.getBlock());
    }

    public void makeInvulnerable() {
        this.setInvulnerableTicks(220);
        this.setHealth(this.getMaxHealth() / 3.0F);
    }

    @Override
    public void makeStuckInBlock(BlockState iblockdata, Vec3 vec3d) {}

    @Override
    public void startSeenByPlayer(ServerPlayer entityplayer) {
        super.startSeenByPlayer(entityplayer);
        this.bossEvent.addPlayer(entityplayer);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer entityplayer) {
        super.stopSeenByPlayer(entityplayer);
        this.bossEvent.removePlayer(entityplayer);
    }

    private double getHeadX(int i) {
        if (i <= 0) {
            return this.getX();
        } else {
            float f = (this.yBodyRot + (float) (180 * (i - 1))) * 0.017453292F;
            float f1 = Mth.cos(f);

            return this.getX() + (double) f1 * 1.3D;
        }
    }

    private double getHeadY(int i) {
        return i <= 0 ? this.getY() + 3.0D : this.getY() + 2.2D;
    }

    private double getHeadZ(int i) {
        if (i <= 0) {
            return this.getZ();
        } else {
            float f = (this.yBodyRot + (float) (180 * (i - 1))) * 0.017453292F;
            float f1 = Mth.sin(f);

            return this.getZ() + (double) f1 * 1.3D;
        }
    }

    private float rotlerp(float f, float f1, float f2) {
        float f3 = Mth.wrapDegrees(f1 - f);

        if (f3 > f2) {
            f3 = f2;
        }

        if (f3 < -f2) {
            f3 = -f2;
        }

        return f + f3;
    }

    private void performRangedAttack(int i, LivingEntity entityliving) {
        this.performRangedAttack(i, entityliving.getX(), entityliving.getY() + (double) entityliving.getEyeHeight() * 0.5D, entityliving.getZ(), i == 0 && this.random.nextFloat() < 0.001F);
    }

    private void performRangedAttack(int i, double d0, double d1, double d2, boolean flag) {
        if (!this.isSilent()) {
            this.level.levelEvent((Player) null, 1024, this.blockPosition(), 0);
        }

        double d3 = this.getHeadX(i);
        double d4 = this.getHeadY(i);
        double d5 = this.getHeadZ(i);
        double d6 = d0 - d3;
        double d7 = d1 - d4;
        double d8 = d2 - d5;
        WitherSkull entitywitherskull = new WitherSkull(this.level, this, d6, d7, d8);

        entitywitherskull.setOwner(this);
        if (flag) {
            entitywitherskull.setDangerous(true);
        }

        entitywitherskull.setPosRaw(d3, d4, d5);
        this.level.addFreshEntity(entitywitherskull);
    }

    @Override
    public void performRangedAttack(LivingEntity entityliving, float f) {
        this.performRangedAttack(0, entityliving);
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (damagesource != DamageSource.DROWN && !(damagesource.getEntity() instanceof WitherBoss)) {
            if (this.getInvulnerableTicks() > 0 && damagesource != DamageSource.OUT_OF_WORLD) {
                return false;
            } else {
                Entity entity;

                if (this.isPowered()) {
                    entity = damagesource.getDirectEntity();
                    if (entity instanceof AbstractArrow) {
                        return false;
                    }
                }

                entity = damagesource.getEntity();
                if (entity != null && !(entity instanceof Player) && entity instanceof LivingEntity && ((LivingEntity) entity).getMobType() == this.getMobType()) {
                    return false;
                } else {
                    if (this.destroyBlocksTick <= 0) {
                        this.destroyBlocksTick = 20;
                    }

                    for (int i = 0; i < this.idleHeadUpdates.length; ++i) {
                        this.idleHeadUpdates[i] += 3;
                    }

                    return super.hurt(damagesource, f);
                }
            }
        } else {
            return false;
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damagesource, int i, boolean flag) {
        super.dropCustomDeathLoot(damagesource, i, flag);
        ItemEntity entityitem = this.spawnAtLocation((ItemLike) Items.NETHER_STAR);

        if (entityitem != null) {
            entityitem.setExtendedLifetime();
        }

    }

    @Override
    public void checkDespawn() {
        if (this.level.getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.remove();
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        return false;
    }

    @Override
    public boolean addEffect(MobEffectInstance mobeffect) {
        return false;
    }

    public static AttributeSupplier.Builder eL() {
        return Monster.eS().a(Attributes.MAX_HEALTH, 300.0D).a(Attributes.MOVEMENT_SPEED, 0.6000000238418579D).a(Attributes.FOLLOW_RANGE, 40.0D).a(Attributes.ARMOR, 4.0D);
    }

    public int getInvulnerableTicks() {
        return (Integer) this.entityData.get(WitherBoss.DATA_ID_INV);
    }

    public void setInvulnerableTicks(int i) {
        this.entityData.set(WitherBoss.DATA_ID_INV, i);
    }

    public int getAlternativeTarget(int i) {
        return (Integer) this.entityData.get((EntityDataAccessor) WitherBoss.DATA_TARGETS.get(i));
    }

    public void setAlternativeTarget(int i, int j) {
        this.entityData.set((EntityDataAccessor) WitherBoss.DATA_TARGETS.get(i), j);
    }

    public boolean isPowered() {
        return this.getHealth() <= this.getMaxHealth() / 2.0F;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance mobeffect) {
        return mobeffect.getEffect() == MobEffects.WITHER ? false : super.canBeAffected(mobeffect);
    }

    class WitherDoNothingGoal extends Goal {

        public WitherDoNothingGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return WitherBoss.this.getInvulnerableTicks() > 0;
        }
    }
}
