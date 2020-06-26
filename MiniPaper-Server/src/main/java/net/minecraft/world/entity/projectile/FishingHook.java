package net.minecraft.world.entity.projectile;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.entity.FishHook;
import org.bukkit.event.player.PlayerFishEvent;
// CraftBukkit end

public class FishingHook extends Projectile {

    private final Random syncronizedRandom;
    private boolean biting;
    private int outOfWaterTime;
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BITING = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.BOOLEAN);
    private int life;
    private int nibble;
    private int timeUntilLured;
    private int timeUntilHooked;
    private float fishAngle;
    private boolean openWater;
    private Entity hookedIn;
    private FishingHook.FishHookState currentState;
    private final int luck;
    private final int lureSpeed;

    private FishingHook(Level world, net.minecraft.world.entity.player.Player entityhuman, int i, int j) {
        super(EntityType.FISHING_BOBBER, world);
        this.syncronizedRandom = new Random();
        this.openWater = true;
        this.currentState = FishingHook.FishHookState.FLYING;
        this.noCulling = true;
        this.setOwner(entityhuman);
        entityhuman.fishing = this;
        this.luck = Math.max(0, i);
        this.lureSpeed = Math.max(0, j);
    }

    public FishingHook(net.minecraft.world.entity.player.Player entityhuman, Level world, int i, int j) {
        this(world, entityhuman, i, j);
        float f = entityhuman.xRot;
        float f1 = entityhuman.yRot;
        float f2 = Mth.cos(-f1 * 0.017453292F - 3.1415927F);
        float f3 = Mth.sin(-f1 * 0.017453292F - 3.1415927F);
        float f4 = -Mth.cos(-f * 0.017453292F);
        float f5 = Mth.sin(-f * 0.017453292F);
        double d0 = entityhuman.getX() - (double) f3 * 0.3D;
        double d1 = entityhuman.getEyeY();
        double d2 = entityhuman.getZ() - (double) f2 * 0.3D;

        this.moveTo(d0, d1, d2, f1, f);
        Vec3 vec3d = new Vec3((double) (-f3), (double) Mth.clamp(-(f5 / f4), -5.0F, 5.0F), (double) (-f2));
        double d3 = vec3d.length();

        vec3d = vec3d.multiply(0.6D / d3 + 0.5D + this.random.nextGaussian() * 0.0045D, 0.6D / d3 + 0.5D + this.random.nextGaussian() * 0.0045D, 0.6D / d3 + 0.5D + this.random.nextGaussian() * 0.0045D);
        this.setDeltaMovement(vec3d);
        this.yRot = (float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D);
        this.xRot = (float) (Mth.atan2(vec3d.y, (double) Mth.sqrt(getHorizontalDistanceSqr(vec3d))) * 57.2957763671875D);
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(FishingHook.DATA_HOOKED_ENTITY, 0);
        this.getEntityData().register(FishingHook.DATA_BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (FishingHook.DATA_HOOKED_ENTITY.equals(datawatcherobject)) {
            int i = (Integer) this.getEntityData().get(FishingHook.DATA_HOOKED_ENTITY);

            this.hookedIn = i > 0 ? this.level.getEntity(i - 1) : null;
        }

        if (FishingHook.DATA_BITING.equals(datawatcherobject)) {
            this.biting = (Boolean) this.getEntityData().get(FishingHook.DATA_BITING);
            if (this.biting) {
                this.setDeltaMovement(this.getDeltaMovement().x, (double) (-0.4F * Mth.nextFloat(this.syncronizedRandom, 0.6F, 1.0F)), this.getDeltaMovement().z);
            }
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public void tick() {
        this.syncronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level.getGameTime());
        super.tick();
        net.minecraft.world.entity.player.Player entityhuman = this.getPlayerOwner();

        if (entityhuman == null) {
            this.remove();
        } else if (this.level.isClientSide || !this.shouldStopFishing(entityhuman)) {
            if (this.onGround) {
                ++this.life;
                if (this.life >= 1200) {
                    this.remove();
                    return;
                }
            } else {
                this.life = 0;
            }

            float f = 0.0F;
            BlockPos blockposition = this.blockPosition();
            FluidState fluid = this.level.getFluidState(blockposition);

            if (fluid.is((Tag) FluidTags.WATER)) {
                f = fluid.getHeight(this.level, blockposition);
            }

            boolean flag = f > 0.0F;

            if (this.currentState == FishingHook.FishHookState.FLYING) {
                if (this.hookedIn != null) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.currentState = FishingHook.FishHookState.HOOKED_IN_ENTITY;
                    return;
                }

                if (flag) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.3D, 0.2D, 0.3D));
                    this.currentState = FishingHook.FishHookState.BOBBING;
                    return;
                }

                this.checkCollision();
            } else {
                if (this.currentState == FishingHook.FishHookState.HOOKED_IN_ENTITY) {
                    if (this.hookedIn != null) {
                        if (this.hookedIn.removed) {
                            this.hookedIn = null;
                            this.currentState = FishingHook.FishHookState.FLYING;
                        } else {
                            this.setPos(this.hookedIn.getX(), this.hookedIn.getY(0.8D), this.hookedIn.getZ());
                        }
                    }

                    return;
                }

                if (this.currentState == FishingHook.FishHookState.BOBBING) {
                    Vec3 vec3d = this.getDeltaMovement();
                    double d0 = this.getY() + vec3d.y - (double) blockposition.getY() - (double) f;

                    if (Math.abs(d0) < 0.01D) {
                        d0 += Math.signum(d0) * 0.1D;
                    }

                    this.setDeltaMovement(vec3d.x * 0.9D, vec3d.y - d0 * (double) this.random.nextFloat() * 0.2D, vec3d.z * 0.9D);
                    if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
                        this.openWater = true;
                    } else {
                        this.openWater = this.openWater && this.outOfWaterTime < 10 && this.calculateOpenWater(blockposition);
                    }

                    if (flag) {
                        this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
                        if (this.biting) {
                            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.1D * (double) this.syncronizedRandom.nextFloat() * (double) this.syncronizedRandom.nextFloat(), 0.0D));
                        }

                        if (!this.level.isClientSide) {
                            this.catchingFish(blockposition);
                        }
                    } else {
                        this.outOfWaterTime = Math.min(10, this.outOfWaterTime + 1);
                    }
                }
            }

            if (!fluid.is((Tag) FluidTags.WATER)) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
            this.updateRotation();
            if (this.currentState == FishingHook.FishHookState.FLYING && (this.onGround || this.horizontalCollision)) {
                this.setDeltaMovement(Vec3.ZERO);
            }

            double d1 = 0.92D;

            this.setDeltaMovement(this.getDeltaMovement().scale(0.92D));
            this.reapplyPosition();
        }
    }

    private boolean shouldStopFishing(net.minecraft.world.entity.player.Player entityhuman) {
        ItemStack itemstack = entityhuman.getMainHandItem();
        ItemStack itemstack1 = entityhuman.getOffhandItem();
        boolean flag = itemstack.getItem() == Items.FISHING_ROD;
        boolean flag1 = itemstack1.getItem() == Items.FISHING_ROD;

        if (!entityhuman.removed && entityhuman.isAlive() && (flag || flag1) && this.distanceToSqr(entityhuman) <= 1024.0D) {
            return false;
        } else {
            this.remove();
            return true;
        }
    }

    private void checkCollision() {
        HitResult movingobjectposition = ProjectileUtil.getHitResult(this, this::canHitEntity, ClipContext.Block.COLLIDER);

        this.onHit(movingobjectposition);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) || entity.isAlive() && entity instanceof ItemEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        if (!this.level.isClientSide) {
            this.hookedIn = movingobjectpositionentity.getEntity();
            this.setHookedEntity();
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult movingobjectpositionblock) {
        super.onHitBlock(movingobjectpositionblock);
        this.setDeltaMovement(this.getDeltaMovement().normalize().scale(movingobjectpositionblock.distanceTo((Entity) this)));
    }

    private void setHookedEntity() {
        this.getEntityData().set(FishingHook.DATA_HOOKED_ENTITY, this.hookedIn.getId() + 1);
    }

    private void catchingFish(BlockPos blockposition) {
        ServerLevel worldserver = (ServerLevel) this.level;
        int i = 1;
        BlockPos blockposition1 = blockposition.above();

        if (this.random.nextFloat() < 0.25F && this.level.isRainingAt(blockposition1)) {
            ++i;
        }

        if (this.random.nextFloat() < 0.5F && !this.level.canSeeSky(blockposition1)) {
            --i;
        }

        if (this.nibble > 0) {
            --this.nibble;
            if (this.nibble <= 0) {
                this.timeUntilLured = 0;
                this.timeUntilHooked = 0;
                this.getEntityData().set(FishingHook.DATA_BITING, false);
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) this.getPlayerOwner().getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.FAILED_ATTEMPT);
                this.level.getServerOH().getPluginManager().callEvent(playerFishEvent);
                // CraftBukkit end
            }
        } else {
            float f;
            float f1;
            float f2;
            double d0;
            double d1;
            double d2;
            BlockState iblockdata;

            if (this.timeUntilHooked > 0) {
                this.timeUntilHooked -= i;
                if (this.timeUntilHooked > 0) {
                    this.fishAngle = (float) ((double) this.fishAngle + this.random.nextGaussian() * 4.0D);
                    f = this.fishAngle * 0.017453292F;
                    f1 = Mth.sin(f);
                    f2 = Mth.cos(f);
                    d0 = this.getX() + (double) (f1 * (float) this.timeUntilHooked * 0.1F);
                    d1 = (double) ((float) Mth.floor(this.getY()) + 1.0F);
                    d2 = this.getZ() + (double) (f2 * (float) this.timeUntilHooked * 0.1F);
                    iblockdata = worldserver.getType(new BlockPos(d0, d1 - 1.0D, d2));
                    if (iblockdata.is(Blocks.WATER)) {
                        if (this.random.nextFloat() < 0.15F) {
                            worldserver.sendParticles(ParticleTypes.BUBBLE, d0, d1 - 0.10000000149011612D, d2, 1, (double) f1, 0.1D, (double) f2, 0.0D);
                        }

                        float f3 = f1 * 0.04F;
                        float f4 = f2 * 0.04F;

                        worldserver.sendParticles(ParticleTypes.FISHING, d0, d1, d2, 0, (double) f4, 0.01D, (double) (-f3), 1.0D);
                        worldserver.sendParticles(ParticleTypes.FISHING, d0, d1, d2, 0, (double) (-f4), 0.01D, (double) f3, 1.0D);
                    }
                } else {
                    // CraftBukkit start
                    PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) this.getPlayerOwner().getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.BITE);
                    this.level.getServerOH().getPluginManager().callEvent(playerFishEvent);
                    if (playerFishEvent.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                    double d3 = this.getY() + 0.5D;

                    worldserver.sendParticles(ParticleTypes.BUBBLE, this.getX(), d3, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), (double) this.getBbWidth(), 0.0D, (double) this.getBbWidth(), 0.20000000298023224D);
                    worldserver.sendParticles(ParticleTypes.FISHING, this.getX(), d3, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), (double) this.getBbWidth(), 0.0D, (double) this.getBbWidth(), 0.20000000298023224D);
                    this.nibble = Mth.nextInt(this.random, 20, 40);
                    this.getEntityData().set(FishingHook.DATA_BITING, true);
                }
            } else if (this.timeUntilLured > 0) {
                this.timeUntilLured -= i;
                f = 0.15F;
                if (this.timeUntilLured < 20) {
                    f = (float) ((double) f + (double) (20 - this.timeUntilLured) * 0.05D);
                } else if (this.timeUntilLured < 40) {
                    f = (float) ((double) f + (double) (40 - this.timeUntilLured) * 0.02D);
                } else if (this.timeUntilLured < 60) {
                    f = (float) ((double) f + (double) (60 - this.timeUntilLured) * 0.01D);
                }

                if (this.random.nextFloat() < f) {
                    f1 = Mth.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                    f2 = Mth.nextFloat(this.random, 25.0F, 60.0F);
                    d0 = this.getX() + (double) (Mth.sin(f1) * f2 * 0.1F);
                    d1 = (double) ((float) Mth.floor(this.getY()) + 1.0F);
                    d2 = this.getZ() + (double) (Mth.cos(f1) * f2 * 0.1F);
                    iblockdata = worldserver.getType(new BlockPos(d0, d1 - 1.0D, d2));
                    if (iblockdata.is(Blocks.WATER)) {
                        worldserver.sendParticles(ParticleTypes.SPLASH, d0, d1, d2, 2 + this.random.nextInt(2), 0.10000000149011612D, 0.0D, 0.10000000149011612D, 0.0D);
                    }
                }

                if (this.timeUntilLured <= 0) {
                    this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                    this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
                }
            } else {
                this.timeUntilLured = Mth.nextInt(this.random, 100, 600);
                this.timeUntilLured -= this.lureSpeed * 20 * 5;
            }
        }

    }

    private boolean calculateOpenWater(BlockPos blockposition) {
        FishingHook.OpenWaterType entityfishinghook_waterposition = FishingHook.OpenWaterType.INVALID;

        for (int i = -1; i <= 2; ++i) {
            FishingHook.OpenWaterType entityfishinghook_waterposition1 = this.getOpenWaterTypeForArea(blockposition.offset(-2, i, -2), blockposition.offset(2, i, 2));

            switch (entityfishinghook_waterposition1) {
                case INVALID:
                    return false;
                case ABOVE_WATER:
                    if (entityfishinghook_waterposition == FishingHook.OpenWaterType.INVALID) {
                        return false;
                    }
                    break;
                case INSIDE_WATER:
                    if (entityfishinghook_waterposition == FishingHook.OpenWaterType.ABOVE_WATER) {
                        return false;
                    }
            }

            entityfishinghook_waterposition = entityfishinghook_waterposition1;
        }

        return true;
    }

    private FishingHook.OpenWaterType getOpenWaterTypeForArea(BlockPos blockposition, BlockPos blockposition1) {
        return (FishingHook.OpenWaterType) BlockPos.betweenClosedStream(blockposition, blockposition1).map(this::getOpenWaterTypeForBlock).reduce((entityfishinghook_waterposition, entityfishinghook_waterposition1) -> {
            return entityfishinghook_waterposition == entityfishinghook_waterposition1 ? entityfishinghook_waterposition : FishingHook.OpenWaterType.INVALID;
        }).orElse(FishingHook.OpenWaterType.INVALID);
    }

    private FishingHook.OpenWaterType getOpenWaterTypeForBlock(BlockPos blockposition) {
        BlockState iblockdata = this.level.getType(blockposition);

        if (!iblockdata.isAir() && !iblockdata.is(Blocks.LILY_PAD)) {
            FluidState fluid = iblockdata.getFluidState();

            return fluid.is((Tag) FluidTags.WATER) && fluid.isSource() && iblockdata.getCollisionShape(this.level, blockposition).isEmpty() ? FishingHook.OpenWaterType.INSIDE_WATER : FishingHook.OpenWaterType.INVALID;
        } else {
            return FishingHook.OpenWaterType.ABOVE_WATER;
        }
    }

    public boolean isOpenWaterFishing() {
        return this.openWater;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {}

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {}

    public int retrieve(ItemStack itemstack) {
        net.minecraft.world.entity.player.Player entityhuman = this.getPlayerOwner();

        if (!this.level.isClientSide && entityhuman != null) {
            int i = 0;

            if (this.hookedIn != null) {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), this.hookedIn.getBukkitEntity(), (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.CAUGHT_ENTITY);
                this.level.getServerOH().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
                // CraftBukkit end
                this.bringInHookedEntity();
                CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer) entityhuman, itemstack, this, (Collection) Collections.emptyList());
                this.level.broadcastEntityEvent(this, (byte) 31);
                i = this.hookedIn instanceof ItemEntity ? 3 : 5;
            } else if (this.nibble > 0) {
                LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.level)).set(LootContextParams.BLOCK_POS, this.blockPosition()).set(LootContextParams.TOOL, itemstack).set(LootContextParams.THIS_ENTITY, this).withRandom(this.random).withLuck((float) this.luck + entityhuman.getLuck());
                LootTable loottable = this.level.getServer().getLootTables().get(BuiltInLootTables.FISHING);
                List<ItemStack> list = loottable.getRandomItems(loottableinfo_builder.create(LootContextParamSets.FISHING));

                CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer) entityhuman, itemstack, this, (Collection) list);
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    ItemStack itemstack1 = (ItemStack) iterator.next();
                    ItemEntity entityitem = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), itemstack1);
                    // CraftBukkit start
                    PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), entityitem.getBukkitEntity(), (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.CAUGHT_FISH);
                    playerFishEvent.setExpToDrop(this.random.nextInt(6) + 1);
                    this.level.getServerOH().getPluginManager().callEvent(playerFishEvent);

                    if (playerFishEvent.isCancelled()) {
                        return 0;
                    }
                    // CraftBukkit end
                    double d0 = entityhuman.getX() - this.getX();
                    double d1 = entityhuman.getY() - this.getY();
                    double d2 = entityhuman.getZ() - this.getZ();
                    double d3 = 0.1D;

                    entityitem.setDeltaMovement(d0 * 0.1D, d1 * 0.1D + Math.sqrt(Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2)) * 0.08D, d2 * 0.1D);
                    this.level.addFreshEntity(entityitem);
                    // CraftBukkit start - this.random.nextInt(6) + 1 -> playerFishEvent.getExpToDrop()
                    if (playerFishEvent.getExpToDrop() > 0) {
                        entityhuman.level.addFreshEntity(new ExperienceOrb(entityhuman.level, entityhuman.getX(), entityhuman.getY() + 0.5D, entityhuman.getZ() + 0.5D, playerFishEvent.getExpToDrop()));
                    }
                    // CraftBukkit end
                    if (itemstack1.getItem().is((Tag) ItemTags.FISHES)) {
                        entityhuman.awardStat(Stats.FISH_CAUGHT, 1);
                    }
                }

                i = 1;
            }

            if (this.onGround) {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.IN_GROUND);
                this.level.getServerOH().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
                // CraftBukkit end
                i = 2;
            }
            // CraftBukkit start
            if (i == 0) {
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.REEL_IN);
                this.level.getServerOH().getPluginManager().callEvent(playerFishEvent);
                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
            }
            // CraftBukkit end

            this.remove();
            return i;
        } else {
            return 0;
        }
    }

    protected void bringInHookedEntity() {
        Entity entity = this.getOwner();

        if (entity != null) {
            Vec3 vec3d = (new Vec3(entity.getX() - this.getX(), entity.getY() - this.getY(), entity.getZ() - this.getZ())).scale(0.1D);

            this.hookedIn.setDeltaMovement(this.hookedIn.getDeltaMovement().add(vec3d));
        }
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    public void remove() {
        super.remove();
        net.minecraft.world.entity.player.Player entityhuman = this.getPlayerOwner();

        if (entityhuman != null) {
            entityhuman.fishing = null;
        }

    }

    @Nullable
    public net.minecraft.world.entity.player.Player getPlayerOwner() {
        Entity entity = this.getOwner();

        return entity instanceof net.minecraft.world.entity.player.Player ? (net.minecraft.world.entity.player.Player) entity : null;
    }

    @Nullable
    public Entity getHookedIn() {
        return this.hookedIn;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        Entity entity = this.getOwner();

        return new ClientboundAddEntityPacket(this, entity == null ? this.getId() : entity.getId());
    }

    static enum OpenWaterType {

        ABOVE_WATER, INSIDE_WATER, INVALID;

        private OpenWaterType() {}
    }

    static enum FishHookState {

        FLYING, HOOKED_IN_ENTITY, BOBBING;

        private FishHookState() {}
    }
}
