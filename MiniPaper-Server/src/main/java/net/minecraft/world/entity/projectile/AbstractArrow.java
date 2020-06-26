package net.minecraft.world.entity.projectile;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
// CraftBukkit end

public abstract class AbstractArrow extends Projectile {

    private static final EntityDataAccessor<Byte> ID_FLAGS = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PIERCE_LEVEL = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    @Nullable
    private BlockState lastState;
    public boolean inGround;
    protected int inGroundTime;
    public AbstractArrow.Pickup pickup;
    public int shakeTime;
    public int life;
    private double baseDamage;
    public int knockback;
    private SoundEvent soundEvent;
    private IntOpenHashSet piercingIgnoreEntityIds;
    private List<Entity> piercedAndKilledEntities;

    // Spigot Start
    @Override
    public void inactiveTick()
    {
        if ( this.inGround )
        {
            this.life += 1;
        }
        super.inactiveTick();
    }
    // Spigot End

    protected AbstractArrow(EntityType<? extends AbstractArrow> entitytypes, Level world) {
        super(entitytypes, world);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
        this.baseDamage = 2.0D;
        this.soundEvent = this.getDefaultHitGroundSoundEvent();
    }

    protected AbstractArrow(EntityType<? extends AbstractArrow> entitytypes, double d0, double d1, double d2, Level world) {
        this(entitytypes, world);
        this.setPos(d0, d1, d2);
    }

    protected AbstractArrow(EntityType<? extends AbstractArrow> entitytypes, LivingEntity entityliving, Level world) {
        this(entitytypes, entityliving.getX(), entityliving.getEyeY() - 0.10000000149011612D, entityliving.getZ(), world);
        this.setOwner(entityliving);
        if (entityliving instanceof Player) {
            this.pickup = AbstractArrow.Pickup.ALLOWED;
        }

    }

    public void setSoundEvent(SoundEvent soundeffect) {
        this.soundEvent = soundeffect;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(AbstractArrow.ID_FLAGS, (byte) 0);
        this.entityData.register(AbstractArrow.PIERCE_LEVEL, (byte) 0);
    }

    @Override
    public void shoot(double d0, double d1, double d2, float f, float f1) {
        super.shoot(d0, d1, d2, f, f1);
        this.life = 0;
    }

    @Override
    public void tick() {
        super.tick();
        boolean flag = this.isNoPhysics();
        Vec3 vec3d = this.getDeltaMovement();

        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            float f = Mth.sqrt(getHorizontalDistanceSqr(vec3d));

            this.yRot = (float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D);
            this.xRot = (float) (Mth.atan2(vec3d.y, (double) f) * 57.2957763671875D);
            this.yRotO = this.yRot;
            this.xRotO = this.xRot;
        }

        BlockPos blockposition = this.blockPosition();
        BlockState iblockdata = this.level.getType(blockposition);
        Vec3 vec3d1;

        if (!iblockdata.isAir() && !flag) {
            VoxelShape voxelshape = iblockdata.getCollisionShape(this.level, blockposition);

            if (!voxelshape.isEmpty()) {
                vec3d1 = this.position();
                Iterator iterator = voxelshape.toAabbs().iterator();

                while (iterator.hasNext()) {
                    AABB axisalignedbb = (AABB) iterator.next();

                    if (axisalignedbb.move(blockposition).contains(vec3d1)) {
                        this.inGround = true;
                        break;
                    }
                }
            }
        }

        if (this.shakeTime > 0) {
            --this.shakeTime;
        }

        if (this.isInWaterOrRain()) {
            this.clearFire();
        }

        if (this.inGround && !flag) {
            if (this.lastState != iblockdata && this.shouldFall()) {
                this.startFalling();
            } else if (!this.level.isClientSide) {
                this.tickDespawn();
            }

            ++this.inGroundTime;
        } else {
            this.inGroundTime = 0;
            Vec3 vec3d2 = this.position();

            vec3d1 = vec3d2.add(vec3d);
            Object object = this.level.clip(new ClipContext(vec3d2, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

            if (((HitResult) object).getType() != HitResult.Type.MISS) {
                vec3d1 = ((HitResult) object).getLocation();
            }

            while (!this.removed) {
                EntityHitResult movingobjectpositionentity = this.findHitEntity(vec3d2, vec3d1);

                if (movingobjectpositionentity != null) {
                    object = movingobjectpositionentity;
                }

                if (object != null && ((HitResult) object).getType() == HitResult.Type.ENTITY) {
                    Entity entity = ((EntityHitResult) object).getEntity();
                    Entity entity1 = this.getOwner();

                    if (entity instanceof Player && entity1 instanceof Player && !((Player) entity1).canHarmPlayer((Player) entity)) {
                        object = null;
                        movingobjectpositionentity = null;
                    }
                }

                if (object != null && !flag) {
                    this.onHit((HitResult) object);
                    this.hasImpulse = true;
                }

                if (movingobjectpositionentity == null || this.getPierceLevel() <= 0) {
                    break;
                }

                object = null;
            }

            vec3d = this.getDeltaMovement();
            double d0 = vec3d.x;
            double d1 = vec3d.y;
            double d2 = vec3d.z;

            if (this.isCritArrow()) {
                for (int i = 0; i < 4; ++i) {
                    this.level.addParticle(ParticleTypes.CRIT, this.getX() + d0 * (double) i / 4.0D, this.getY() + d1 * (double) i / 4.0D, this.getZ() + d2 * (double) i / 4.0D, -d0, -d1 + 0.2D, -d2);
                }
            }

            double d3 = this.getX() + d0;
            double d4 = this.getY() + d1;
            double d5 = this.getZ() + d2;
            float f1 = Mth.sqrt(getHorizontalDistanceSqr(vec3d));

            if (flag) {
                this.yRot = (float) (Mth.atan2(-d0, -d2) * 57.2957763671875D);
            } else {
                this.yRot = (float) (Mth.atan2(d0, d2) * 57.2957763671875D);
            }

            this.xRot = (float) (Mth.atan2(d1, (double) f1) * 57.2957763671875D);
            this.xRot = lerpRotation(this.xRotO, this.xRot);
            this.yRot = lerpRotation(this.yRotO, this.yRot);
            float f2 = 0.99F;
            float f3 = 0.05F;

            if (this.isInWater()) {
                for (int j = 0; j < 4; ++j) {
                    float f4 = 0.25F;

                    this.level.addParticle(ParticleTypes.BUBBLE, d3 - d0 * 0.25D, d4 - d1 * 0.25D, d5 - d2 * 0.25D, d0, d1, d2);
                }

                f2 = this.getWaterInertia();
            }

            this.setDeltaMovement(vec3d.scale((double) f2));
            if (!this.isNoGravity() && !flag) {
                Vec3 vec3d3 = this.getDeltaMovement();

                this.setDeltaMovement(vec3d3.x, vec3d3.y - 0.05000000074505806D, vec3d3.z);
            }

            this.setPos(d3, d4, d5);
            this.checkInsideBlocks();
        }
    }

    private boolean shouldFall() {
        return this.inGround && this.level.noCollision((new AABB(this.position(), this.position())).inflate(0.06D));
    }

    private void startFalling() {
        this.inGround = false;
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.multiply((double) (this.random.nextFloat() * 0.2F), (double) (this.random.nextFloat() * 0.2F), (double) (this.random.nextFloat() * 0.2F)));
        this.life = 0;
    }

    @Override
    public void move(MoverType enummovetype, Vec3 vec3d) {
        super.move(enummovetype, vec3d);
        if (enummovetype != MoverType.SELF && this.shouldFall()) {
            this.startFalling();
        }

    }

    protected void tickDespawn() {
        ++this.life;
        if (this.life >= ((this instanceof ThrownTrident) ? level.spigotConfig.tridentDespawnRate : level.spigotConfig.arrowDespawnRate)) { // Spigot
            this.remove();
        }

    }

    private void resetPiercedEntities() {
        if (this.piercedAndKilledEntities != null) {
            this.piercedAndKilledEntities.clear();
        }

        if (this.piercingIgnoreEntityIds != null) {
            this.piercingIgnoreEntityIds.clear();
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        Entity entity = movingobjectpositionentity.getEntity();
        float f = (float) this.getDeltaMovement().length();
        int i = Mth.ceil(Mth.clamp((double) f * this.baseDamage, 0.0D, 2.147483647E9D));

        if (this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }

            if (this.piercedAndKilledEntities == null) {
                this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
            }

            if (this.piercingIgnoreEntityIds.size() >= this.getPierceLevel() + 1) {
                this.remove();
                return;
            }

            this.piercingIgnoreEntityIds.add(entity.getId());
        }

        if (this.isCritArrow()) {
            long j = (long) this.random.nextInt(i / 2 + 2);

            i = (int) Math.min(j + (long) i, 2147483647L);
        }

        Entity entity1 = this.getOwner();
        DamageSource damagesource;

        if (entity1 == null) {
            damagesource = DamageSource.arrow(this, this);
        } else {
            damagesource = DamageSource.arrow(this, entity1);
            if (entity1 instanceof LivingEntity) {
                ((LivingEntity) entity1).setLastHurtMob(entity);
            }
        }

        boolean flag = entity.getType() == EntityType.ENDERMAN;
        int k = entity.getRemainingFireTicks();

        if (this.isOnFire() && !flag) {
            // CraftBukkit start
            EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), 5);
            org.bukkit.Bukkit.getPluginManager().callEvent(combustEvent);
            if (!combustEvent.isCancelled()) {
                entity.setOnFire(combustEvent.getDuration(), false);
            }
            // CraftBukkit end
        }

        if (entity.hurt(damagesource, (float) i)) {
            if (flag) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;

                if (!this.level.isClientSide && this.getPierceLevel() <= 0) {
                    entityliving.setArrowCount(entityliving.getArrowCount() + 1);
                }

                if (this.knockback > 0) {
                    Vec3 vec3d = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale((double) this.knockback * 0.6D);

                    if (vec3d.lengthSqr() > 0.0D) {
                        entityliving.push(vec3d.x, 0.1D, vec3d.z);
                    }
                }

                if (!this.level.isClientSide && entity1 instanceof LivingEntity) {
                    EnchantmentHelper.doPostHurtEffects(entityliving, entity1);
                    EnchantmentHelper.doPostDamageEffects((LivingEntity) entity1, (Entity) entityliving);
                }

                this.doPostHurtEffects(entityliving);
                if (entity1 != null && entityliving != entity1 && entityliving instanceof Player && entity1 instanceof ServerPlayer && !this.isSilent()) {
                    ((ServerPlayer) entity1).connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0.0F));
                }

                if (!entity.isAlive() && this.piercedAndKilledEntities != null) {
                    this.piercedAndKilledEntities.add(entityliving);
                }

                if (!this.level.isClientSide && entity1 instanceof ServerPlayer) {
                    ServerPlayer entityplayer = (ServerPlayer) entity1;

                    if (this.piercedAndKilledEntities != null && this.shotFromCrossbow()) {
                        CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(entityplayer, (Collection) this.piercedAndKilledEntities);
                    } else if (!entity.isAlive() && this.shotFromCrossbow()) {
                        CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(entityplayer, (Collection) Arrays.asList(entity));
                    }
                }
            }

            this.playSound(this.soundEvent, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
            if (this.getPierceLevel() <= 0) {
                this.remove();
            }
        } else {
            entity.setRemainingFireTicks(k);
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.1D));
            this.yRot += 180.0F;
            this.yRotO += 180.0F;
            if (!this.level.isClientSide && this.getDeltaMovement().lengthSqr() < 1.0E-7D) {
                if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }

                this.remove();
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult movingobjectpositionblock) {
        this.lastState = this.level.getType(movingobjectpositionblock.getBlockPos());
        super.onHitBlock(movingobjectpositionblock);
        Vec3 vec3d = movingobjectpositionblock.getLocation().subtract(this.getX(), this.getY(), this.getZ());

        this.setDeltaMovement(vec3d);
        Vec3 vec3d1 = vec3d.normalize().scale(0.05000000074505806D);

        this.setPosRaw(this.getX() - vec3d1.x, this.getY() - vec3d1.y, this.getZ() - vec3d1.z);
        this.playSound(this.getSoundHit(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.inGround = true;
        this.shakeTime = 7;
        this.setCritArrow(false);
        this.setPierceLevel((byte) 0);
        this.setSoundEvent(SoundEvents.ARROW_HIT);
        this.setShotFromCrossbow(false);
        this.resetPiercedEntities();
    }

    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ARROW_HIT;
    }

    protected final SoundEvent getSoundHit() {
        return this.soundEvent;
    }

    protected void doPostHurtEffects(LivingEntity entityliving) {}

    @Nullable
    protected EntityHitResult findHitEntity(Vec3 vec3d, Vec3 vec3d1) {
        return ProjectileUtil.getEntityHitResult(this.level, this, vec3d, vec3d1, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), this::canHitEntity);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && (this.piercingIgnoreEntityIds == null || !this.piercingIgnoreEntityIds.contains(entity.getId()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putShort("life", (short) this.life);
        if (this.lastState != null) {
            nbttagcompound.put("inBlockState", NbtUtils.writeBlockState(this.lastState));
        }

        nbttagcompound.putByte("shake", (byte) this.shakeTime);
        nbttagcompound.putBoolean("inGround", this.inGround);
        nbttagcompound.putByte("pickup", (byte) this.pickup.ordinal());
        nbttagcompound.putDouble("damage", this.baseDamage);
        nbttagcompound.putBoolean("crit", this.isCritArrow());
        nbttagcompound.putByte("PierceLevel", this.getPierceLevel());
        nbttagcompound.putString("SoundEvent", Registry.SOUND_EVENT.getKey(this.soundEvent).toString());
        nbttagcompound.putBoolean("ShotFromCrossbow", this.shotFromCrossbow());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.life = nbttagcompound.getShort("life");
        if (nbttagcompound.contains("inBlockState", 10)) {
            this.lastState = NbtUtils.readBlockState(nbttagcompound.getCompound("inBlockState"));
        }

        this.shakeTime = nbttagcompound.getByte("shake") & 255;
        this.inGround = nbttagcompound.getBoolean("inGround");
        if (nbttagcompound.contains("damage", 99)) {
            this.baseDamage = nbttagcompound.getDouble("damage");
        }

        if (nbttagcompound.contains("pickup", 99)) {
            this.pickup = AbstractArrow.Pickup.byOrdinal(nbttagcompound.getByte("pickup"));
        } else if (nbttagcompound.contains("player", 99)) {
            this.pickup = nbttagcompound.getBoolean("player") ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.DISALLOWED;
        }

        this.setCritArrow(nbttagcompound.getBoolean("crit"));
        this.setPierceLevel(nbttagcompound.getByte("PierceLevel"));
        if (nbttagcompound.contains("SoundEvent", 8)) {
            this.soundEvent = (SoundEvent) Registry.SOUND_EVENT.getOptional(new ResourceLocation(nbttagcompound.getString("SoundEvent"))).orElse(this.getDefaultHitGroundSoundEvent());
        }

        this.setShotFromCrossbow(nbttagcompound.getBoolean("ShotFromCrossbow"));
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);
        if (entity instanceof Player) {
            this.pickup = ((Player) entity).abilities.instabuild ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
        }

    }

    @Override
    public void playerTouch(Player entityhuman) {
        if (!this.level.isClientSide && (this.inGround || this.isNoPhysics()) && this.shakeTime <= 0) {
            // CraftBukkit start
            ItemStack itemstack = this.getPickupItem();
            if (this.pickup == Pickup.ALLOWED && !itemstack.isEmpty() && entityhuman.inventory.canHold(itemstack) > 0) {
                ItemEntity item = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), itemstack);
                PlayerPickupArrowEvent event = new PlayerPickupArrowEvent((org.bukkit.entity.Player) entityhuman.getBukkitEntity(), new org.bukkit.craftbukkit.entity.CraftItem(this.level.getServerOH(), this, item), (org.bukkit.entity.AbstractArrow) this.getBukkitEntity());
                // event.setCancelled(!entityhuman.canPickUpLoot); TODO
                this.level.getServerOH().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                itemstack = item.getItem();
            }
            boolean flag = this.pickup == AbstractArrow.Pickup.ALLOWED || this.pickup == AbstractArrow.Pickup.CREATIVE_ONLY && entityhuman.abilities.instabuild || this.isNoPhysics() && this.getOwner().getUUID() == entityhuman.getUUID();

            if (this.pickup == AbstractArrow.Pickup.ALLOWED && !entityhuman.inventory.add(itemstack)) {
                // CraftBukkit end
                flag = false;
            }

            if (flag) {
                entityhuman.take(this, 1);
                this.remove();
            }

        }
    }

    protected abstract ItemStack getPickupItem();

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    public void setBaseDamage(double d0) {
        this.baseDamage = d0;
    }

    public double getBaseDamage() {
        return this.baseDamage;
    }

    public void setKnockback(int i) {
        this.knockback = i;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected float getEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.13F;
    }

    public void setCritArrow(boolean flag) {
        this.setFlag(1, flag);
    }

    public void setPierceLevel(byte b0) {
        this.entityData.set(AbstractArrow.PIERCE_LEVEL, b0);
    }

    private void setFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        if (flag) {
            this.entityData.set(AbstractArrow.ID_FLAGS, (byte) (b0 | i));
        } else {
            this.entityData.set(AbstractArrow.ID_FLAGS, (byte) (b0 & ~i));
        }

    }

    public boolean isCritArrow() {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        return (b0 & 1) != 0;
    }

    public boolean shotFromCrossbow() {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        return (b0 & 4) != 0;
    }

    public byte getPierceLevel() {
        return (Byte) this.entityData.get(AbstractArrow.PIERCE_LEVEL);
    }

    public void setEnchantmentEffectsFromEntity(LivingEntity entityliving, float f) {
        int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER_ARROWS, entityliving);
        int j = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH_ARROWS, entityliving);

        this.setBaseDamage((double) (f * 2.0F) + this.random.nextGaussian() * 0.25D + (double) ((float) this.level.getDifficulty().getId() * 0.11F));
        if (i > 0) {
            this.setBaseDamage(this.getBaseDamage() + (double) i * 0.5D + 0.5D);
        }

        if (j > 0) {
            this.setKnockback(j);
        }

        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAMING_ARROWS, entityliving) > 0) {
            this.setSecondsOnFire(100);
        }

    }

    protected float getWaterInertia() {
        return 0.6F;
    }

    public void setNoPhysics(boolean flag) {
        this.noPhysics = flag;
        this.setFlag(2, flag);
    }

    public boolean isNoPhysics() {
        return !this.level.isClientSide ? this.noPhysics : ((Byte) this.entityData.get(AbstractArrow.ID_FLAGS) & 2) != 0;
    }

    public void setShotFromCrossbow(boolean flag) {
        this.setFlag(4, flag);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        Entity entity = this.getOwner();

        return new ClientboundAddEntityPacket(this, entity == null ? 0 : entity.getId());
    }

    public static enum Pickup {

        DISALLOWED, ALLOWED, CREATIVE_ONLY;

        private Pickup() {}

        public static AbstractArrow.Pickup byOrdinal(int i) {
            if (i < 0 || i > values().length) {
                i = 0;
            }

            return values()[i];
        }
    }
}
