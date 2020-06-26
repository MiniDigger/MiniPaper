package net.minecraft.world.entity.vehicle;

import com.google.common.collect.UnmodifiableIterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
// CraftBukkit end

public class Boat extends Entity {

    private static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ID_PADDLE_LEFT = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ID_PADDLE_RIGHT = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ID_BUBBLE_TIME = SynchedEntityData.defineId(Boat.class, EntityDataSerializers.INT);
    private final float[] paddlePositions;
    private float invFriction;
    private float outOfControlTicks;
    private float deltaRotation;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputUp;
    private boolean inputDown;
    private double waterLevel;
    private float landFriction;
    private Boat.Status status;
    private Boat.Status oldStatus;
    private double lastYd;
    private boolean isAboveBubbleColumn;
    private boolean bubbleColumnDirectionIsDown;
    private float bubbleMultiplier;
    private float bubbleAngle;
    private float bubbleAngleO;

    // CraftBukkit start
    // PAIL: Some of these haven't worked since a few updates, and since 1.9 they are less and less applicable.
    public double maxSpeed = 0.4D;
    public double occupiedDeceleration = 0.2D;
    public double unoccupiedDeceleration = -1;
    public boolean landBoats = false;
    // CraftBukkit end

    public Boat(EntityType<? extends Boat> entitytypes, Level world) {
        super(entitytypes, world);
        this.paddlePositions = new float[2];
        this.blocksBuilding = true;
    }

    public Boat(Level world, double d0, double d1, double d2) {
        this(EntityType.BOAT, world);
        this.setPos(d0, d1, d2);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
    }

    @Override
    protected float getEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height;
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(Boat.DATA_ID_HURT, 0);
        this.entityData.register(Boat.DATA_ID_HURTDIR, 1);
        this.entityData.register(Boat.DATA_ID_DAMAGE, 0.0F);
        this.entityData.register(Boat.DATA_ID_TYPE, Boat.Type.OAK.ordinal());
        this.entityData.register(Boat.DATA_ID_PADDLE_LEFT, false);
        this.entityData.register(Boat.DATA_ID_PADDLE_RIGHT, false);
        this.entityData.register(Boat.DATA_ID_BUBBLE_TIME, 0);
    }

    @Nullable
    @Override
    public AABB getCollideAgainstBox(Entity entity) {
        return entity.isPushable() ? entity.getBoundingBox() : null;
    }

    @Nullable
    @Override
    public AABB getCollideBox() {
        return this.getBoundingBox();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public double getPassengersRidingOffset() {
        return -0.1D;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (!this.level.isClientSide && !this.removed) {
            // CraftBukkit start
            Vehicle vehicle = (Vehicle) this.getBukkitEntity();
            org.bukkit.entity.Entity attacker = (damagesource.getEntity() == null) ? null : damagesource.getEntity().getBukkitEntity();

            VehicleDamageEvent event = new VehicleDamageEvent(vehicle, attacker, (double) f);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }
            // f = event.getDamage(); // TODO Why don't we do this?
            // CraftBukkit end

            this.setHurtDir(-this.getHurtDir());
            this.setHurtTime(10);
            this.setDamage(this.getDamage() + f * 10.0F);
            this.markHurt();
            boolean flag = damagesource.getEntity() instanceof Player && ((Player) damagesource.getEntity()).abilities.instabuild;

            if (flag || this.getDamage() > 40.0F) {
                // CraftBukkit start
                VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, attacker);
                this.level.getServerOH().getPluginManager().callEvent(destroyEvent);

                if (destroyEvent.isCancelled()) {
                    this.setDamage(40F); // Maximize damage so this doesn't get triggered again right away
                    return true;
                }
                // CraftBukkit end
                if (!flag && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    this.spawnAtLocation((ItemLike) this.getDropItem());
                }

                this.remove();
            }

            return true;
        } else {
            return true;
        }
    }

    @Override
    public void onAboveBubbleCol(boolean flag) {
        if (!this.level.isClientSide) {
            this.isAboveBubbleColumn = true;
            this.bubbleColumnDirectionIsDown = flag;
            if (this.getBubbleTime() == 0) {
                this.setBubbleTime(60);
            }
        }

        this.level.addParticle(ParticleTypes.SPLASH, this.getX() + (double) this.random.nextFloat(), this.getY() + 0.7D, this.getZ() + (double) this.random.nextFloat(), 0.0D, 0.0D, 0.0D);
        if (this.random.nextInt(20) == 0) {
            this.level.playLocalSound(this.getX(), this.getY(), this.getZ(), this.getSoundSplash(), this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat(), false);
        }

    }

    @Override
    public void push(Entity entity) {
        if (entity instanceof Boat) {
            if (entity.getBoundingBox().minY < this.getBoundingBox().maxY) {
                // CraftBukkit start
                if (!this.isPassengerOfSameVehicle(entity)) {
                    VehicleEntityCollisionEvent event = new VehicleEntityCollisionEvent((Vehicle) this.getBukkitEntity(), entity.getBukkitEntity());
                    this.level.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                }
                // CraftBukkit end
                super.push(entity);
            }
        } else if (entity.getBoundingBox().minY <= this.getBoundingBox().minY) {
            // CraftBukkit start
            if (!this.isPassengerOfSameVehicle(entity)) {
                VehicleEntityCollisionEvent event = new VehicleEntityCollisionEvent((Vehicle) this.getBukkitEntity(), entity.getBukkitEntity());
                this.level.getServerOH().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // CraftBukkit end
            super.push(entity);
        }

    }

    public Item getDropItem() {
        switch (this.getTypeOH()) {
            case OAK:
            default:
                return Items.OAK_BOAT;
            case SPRUCE:
                return Items.SPRUCE_BOAT;
            case BIRCH:
                return Items.BIRCH_BOAT;
            case JUNGLE:
                return Items.JUNGLE_BOAT;
            case ACACIA:
                return Items.ACACIA_BOAT;
            case DARK_OAK:
                return Items.DARK_OAK_BOAT;
        }
    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }

    @Override
    public Direction getMotionDirection() {
        return this.getDirection().getClockWise();
    }

    private Location lastLocation; // CraftBukkit
    @Override
    public void tick() {
        this.oldStatus = this.status;
        this.status = this.getStatus();
        if (this.status != Boat.Status.UNDER_WATER && this.status != Boat.Status.UNDER_FLOWING_WATER) {
            this.outOfControlTicks = 0.0F;
        } else {
            ++this.outOfControlTicks;
        }

        if (!this.level.isClientSide && this.outOfControlTicks >= 60.0F) {
            this.ejectPassengers();
        }

        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        super.tick();
        this.tickLerp();
        if (this.isControlledByLocalInstance()) {
            if (this.getPassengers().isEmpty() || !(this.getPassengers().get(0) instanceof Player)) {
                this.setPaddleState(false, false);
            }

            this.floatBoat();
            if (this.level.isClientSide) {
                this.controlBoat();
                this.level.sendPacketToServer((Packet) (new ServerboundPaddleBoatPacket(this.getPaddleState(0), this.getPaddleState(1))));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

        // CraftBukkit start
        org.bukkit.Server server = this.level.getServerOH();
        org.bukkit.World bworld = this.level.getWorld();

        Location to = new Location(bworld, this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
        Vehicle vehicle = (Vehicle) this.getBukkitEntity();

        server.getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleUpdateEvent(vehicle));

        if (lastLocation != null && !lastLocation.equals(to)) {
            VehicleMoveEvent event = new VehicleMoveEvent(vehicle, lastLocation, to);
            server.getPluginManager().callEvent(event);
        }
        lastLocation = vehicle.getLocation();
        // CraftBukkit end

        this.tickBubbleColumn();

        for (int i = 0; i <= 1; ++i) {
            if (this.getPaddleState(i)) {
                if (!this.isSilent() && (double) (this.paddlePositions[i] % 6.2831855F) <= 0.7853981852531433D && ((double) this.paddlePositions[i] + 0.39269909262657166D) % 6.2831854820251465D >= 0.7853981852531433D) {
                    SoundEvent soundeffect = this.getPaddleSound();

                    if (soundeffect != null) {
                        Vec3 vec3d = this.getViewVector(1.0F);
                        double d0 = i == 1 ? -vec3d.z : vec3d.z;
                        double d1 = i == 1 ? vec3d.x : -vec3d.x;

                        this.level.playSound((Player) null, this.getX() + d0, this.getY(), this.getZ() + d1, soundeffect, this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat());
                    }
                }

                this.paddlePositions[i] = (float) ((double) this.paddlePositions[i] + 0.39269909262657166D);
            } else {
                this.paddlePositions[i] = 0.0F;
            }
        }

        this.checkInsideBlocks();
        List<Entity> list = this.level.getEntities(this, this.getBoundingBox().inflate(0.20000000298023224D, -0.009999999776482582D, 0.20000000298023224D), EntitySelector.pushableBy(this));

        if (!list.isEmpty()) {
            boolean flag = !this.level.isClientSide && !(this.getControllingPassenger() instanceof Player);

            for (int j = 0; j < list.size(); ++j) {
                Entity entity = (Entity) list.get(j);

                if (!entity.hasPassenger(this)) {
                    if (flag && this.getPassengers().size() < 2 && !entity.isPassenger() && entity.getBbWidth() < this.getBbWidth() && entity instanceof LivingEntity && !(entity instanceof WaterAnimal) && !(entity instanceof Player)) {
                        entity.startRiding(this);
                    } else {
                        this.push(entity);
                    }
                }
            }
        }

    }

    private void tickBubbleColumn() {
        int i;

        if (this.level.isClientSide) {
            i = this.getBubbleTime();
            if (i > 0) {
                this.bubbleMultiplier += 0.05F;
            } else {
                this.bubbleMultiplier -= 0.1F;
            }

            this.bubbleMultiplier = Mth.clamp(this.bubbleMultiplier, 0.0F, 1.0F);
            this.bubbleAngleO = this.bubbleAngle;
            this.bubbleAngle = 10.0F * (float) Math.sin((double) (0.5F * (float) this.level.getGameTime())) * this.bubbleMultiplier;
        } else {
            if (!this.isAboveBubbleColumn) {
                this.setBubbleTime(0);
            }

            i = this.getBubbleTime();
            if (i > 0) {
                --i;
                this.setBubbleTime(i);
                int j = 60 - i - 1;

                if (j > 0 && i == 0) {
                    this.setBubbleTime(0);
                    Vec3 vec3d = this.getDeltaMovement();

                    if (this.bubbleColumnDirectionIsDown) {
                        this.setDeltaMovement(vec3d.add(0.0D, -0.7D, 0.0D));
                        this.ejectPassengers();
                    } else {
                        this.setDeltaMovement(vec3d.x, this.hasPassenger(Player.class) ? 2.7D : 0.6D, vec3d.z);
                    }
                }

                this.isAboveBubbleColumn = false;
            }
        }

    }

    @Nullable
    protected SoundEvent getPaddleSound() {
        switch (this.getStatus()) {
            case IN_WATER:
            case UNDER_WATER:
            case UNDER_FLOWING_WATER:
                return SoundEvents.BOAT_PADDLE_WATER;
            case ON_LAND:
                return SoundEvents.BOAT_PADDLE_LAND;
            case IN_AIR:
            default:
                return null;
        }
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.setPacketCoordinates(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            double d0 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
            double d1 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
            double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
            double d3 = Mth.wrapDegrees(this.lerpYRot - (double) this.yRot);

            this.yRot = (float) ((double) this.yRot + d3 / (double) this.lerpSteps);
            this.xRot = (float) ((double) this.xRot + (this.lerpXRot - (double) this.xRot) / (double) this.lerpSteps);
            --this.lerpSteps;
            this.setPos(d0, d1, d2);
            this.setRot(this.yRot, this.xRot);
        }
    }

    public void setPaddleState(boolean flag, boolean flag1) {
        this.entityData.set(Boat.DATA_ID_PADDLE_LEFT, flag);
        this.entityData.set(Boat.DATA_ID_PADDLE_RIGHT, flag1);
    }

    private Boat.Status getStatus() {
        Boat.Status entityboat_enumstatus = this.isUnderwater();

        if (entityboat_enumstatus != null) {
            this.waterLevel = this.getBoundingBox().maxY;
            return entityboat_enumstatus;
        } else if (this.checkInWater()) {
            return Boat.Status.IN_WATER;
        } else {
            float f = this.getGroundFriction();

            if (f > 0.0F) {
                this.landFriction = f;
                return Boat.Status.ON_LAND;
            } else {
                return Boat.Status.IN_AIR;
            }
        }
    }

    public float getWaterLevelAbove() {
        AABB axisalignedbb = this.getBoundingBox();
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.maxY);
        int l = Mth.ceil(axisalignedbb.maxY - this.lastYd);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        int k1 = k;

        while (k1 < l) {
            float f = 0.0F;
            int l1 = i;

            label35:
            while (true) {
                if (l1 < j) {
                    int i2 = i1;

                    while (true) {
                        if (i2 >= j1) {
                            ++l1;
                            continue label35;
                        }

                        blockposition_mutableblockposition.d(l1, k1, i2);
                        FluidState fluid = this.level.getFluidState(blockposition_mutableblockposition);

                        if (fluid.is((Tag) FluidTags.WATER)) {
                            f = Math.max(f, fluid.getHeight(this.level, blockposition_mutableblockposition));
                        }

                        if (f >= 1.0F) {
                            break;
                        }

                        ++i2;
                    }
                } else if (f < 1.0F) {
                    return (float) blockposition_mutableblockposition.getY() + f;
                }

                ++k1;
                break;
            }
        }

        return (float) (l + 1);
    }

    public float getGroundFriction() {
        AABB axisalignedbb = this.getBoundingBox();
        AABB axisalignedbb1 = new AABB(axisalignedbb.minX, axisalignedbb.minY - 0.001D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
        int i = Mth.floor(axisalignedbb1.minX) - 1;
        int j = Mth.ceil(axisalignedbb1.maxX) + 1;
        int k = Mth.floor(axisalignedbb1.minY) - 1;
        int l = Mth.ceil(axisalignedbb1.maxY) + 1;
        int i1 = Mth.floor(axisalignedbb1.minZ) - 1;
        int j1 = Mth.ceil(axisalignedbb1.maxZ) + 1;
        VoxelShape voxelshape = Shapes.create(axisalignedbb1);
        float f = 0.0F;
        int k1 = 0;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        for (int l1 = i; l1 < j; ++l1) {
            for (int i2 = i1; i2 < j1; ++i2) {
                int j2 = (l1 != i && l1 != j - 1 ? 0 : 1) + (i2 != i1 && i2 != j1 - 1 ? 0 : 1);

                if (j2 != 2) {
                    for (int k2 = k; k2 < l; ++k2) {
                        if (j2 <= 0 || k2 != k && k2 != l - 1) {
                            blockposition_mutableblockposition.d(l1, k2, i2);
                            BlockState iblockdata = this.level.getType(blockposition_mutableblockposition);

                            if (!(iblockdata.getBlock() instanceof WaterlilyBlock) && Shapes.joinIsNotEmpty(iblockdata.getCollisionShape(this.level, blockposition_mutableblockposition).move((double) l1, (double) k2, (double) i2), voxelshape, BooleanOp.AND)) {
                                f += iblockdata.getBlock().getFriction();
                                ++k1;
                            }
                        }
                    }
                }
            }
        }

        return f / (float) k1;
    }

    private boolean checkInWater() {
        AABB axisalignedbb = this.getBoundingBox();
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.minY);
        int l = Mth.ceil(axisalignedbb.minY + 0.001D);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        boolean flag = false;

        this.waterLevel = Double.MIN_VALUE;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    blockposition_mutableblockposition.d(k1, l1, i2);
                    FluidState fluid = this.level.getFluidState(blockposition_mutableblockposition);

                    if (fluid.is((Tag) FluidTags.WATER)) {
                        float f = (float) l1 + fluid.getHeight(this.level, blockposition_mutableblockposition);

                        this.waterLevel = Math.max((double) f, this.waterLevel);
                        flag |= axisalignedbb.minY < (double) f;
                    }
                }
            }
        }

        return flag;
    }

    @Nullable
    private Boat.Status isUnderwater() {
        AABB axisalignedbb = this.getBoundingBox();
        double d0 = axisalignedbb.maxY + 0.001D;
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.maxY);
        int l = Mth.ceil(d0);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        boolean flag = false;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    blockposition_mutableblockposition.d(k1, l1, i2);
                    FluidState fluid = this.level.getFluidState(blockposition_mutableblockposition);

                    if (fluid.is((Tag) FluidTags.WATER) && d0 < (double) ((float) blockposition_mutableblockposition.getY() + fluid.getHeight(this.level, blockposition_mutableblockposition))) {
                        if (!fluid.isSource()) {
                            return Boat.Status.UNDER_FLOWING_WATER;
                        }

                        flag = true;
                    }
                }
            }
        }

        return flag ? Boat.Status.UNDER_WATER : null;
    }

    private void floatBoat() {
        double d0 = -0.03999999910593033D;
        double d1 = this.isNoGravity() ? 0.0D : -0.03999999910593033D;
        double d2 = 0.0D;

        this.invFriction = 0.05F;
        if (this.oldStatus == Boat.Status.IN_AIR && this.status != Boat.Status.IN_AIR && this.status != Boat.Status.ON_LAND) {
            this.waterLevel = this.getY(1.0D);
            this.setPos(this.getX(), (double) (this.getWaterLevelAbove() - this.getBbHeight()) + 0.101D, this.getZ());
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            this.lastYd = 0.0D;
            this.status = Boat.Status.IN_WATER;
        } else {
            if (this.status == Boat.Status.IN_WATER) {
                d2 = (this.waterLevel - this.getY()) / (double) this.getBbHeight();
                this.invFriction = 0.9F;
            } else if (this.status == Boat.Status.UNDER_FLOWING_WATER) {
                d1 = -7.0E-4D;
                this.invFriction = 0.9F;
            } else if (this.status == Boat.Status.UNDER_WATER) {
                d2 = 0.009999999776482582D;
                this.invFriction = 0.45F;
            } else if (this.status == Boat.Status.IN_AIR) {
                this.invFriction = 0.9F;
            } else if (this.status == Boat.Status.ON_LAND) {
                this.invFriction = this.landFriction;
                if (this.getControllingPassenger() instanceof Player) {
                    this.landFriction /= 2.0F;
                }
            }

            Vec3 vec3d = this.getDeltaMovement();

            this.setDeltaMovement(vec3d.x * (double) this.invFriction, vec3d.y + d1, vec3d.z * (double) this.invFriction);
            this.deltaRotation *= this.invFriction;
            if (d2 > 0.0D) {
                Vec3 vec3d1 = this.getDeltaMovement();

                this.setDeltaMovement(vec3d1.x, (vec3d1.y + d2 * 0.06153846016296973D) * 0.75D, vec3d1.z);
            }
        }

    }

    private void controlBoat() {
        if (this.isVehicle()) {
            float f = 0.0F;

            if (this.inputLeft) {
                --this.deltaRotation;
            }

            if (this.inputRight) {
                ++this.deltaRotation;
            }

            if (this.inputRight != this.inputLeft && !this.inputUp && !this.inputDown) {
                f += 0.005F;
            }

            this.yRot += this.deltaRotation;
            if (this.inputUp) {
                f += 0.04F;
            }

            if (this.inputDown) {
                f -= 0.005F;
            }

            this.setDeltaMovement(this.getDeltaMovement().add((double) (Mth.sin(-this.yRot * 0.017453292F) * f), 0.0D, (double) (Mth.cos(this.yRot * 0.017453292F) * f)));
            this.setPaddleState(this.inputRight && !this.inputLeft || this.inputUp, this.inputLeft && !this.inputRight || this.inputUp);
        }
    }

    @Override
    public void positionRider(Entity entity) {
        if (this.hasPassenger(entity)) {
            float f = 0.0F;
            float f1 = (float) ((this.removed ? 0.009999999776482582D : this.getPassengersRidingOffset()) + entity.getMyRidingOffset());

            if (this.getPassengers().size() > 1) {
                int i = this.getPassengers().indexOf(entity);

                if (i == 0) {
                    f = 0.2F;
                } else {
                    f = -0.6F;
                }

                if (entity instanceof Animal) {
                    f = (float) ((double) f + 0.2D);
                }
            }

            Vec3 vec3d = (new Vec3((double) f, 0.0D, 0.0D)).yRot(-this.yRot * 0.017453292F - 1.5707964F);

            entity.setPos(this.getX() + vec3d.x, this.getY() + (double) f1, this.getZ() + vec3d.z);
            entity.yRot += this.deltaRotation;
            entity.setYHeadRot(entity.getYHeadRot() + this.deltaRotation);
            this.clampRotation(entity);
            if (entity instanceof Animal && this.getPassengers().size() > 1) {
                int j = entity.getId() % 2 == 0 ? 90 : 270;

                entity.setYBodyRot(((Animal) entity).yBodyRot + (float) j);
                entity.setYHeadRot(entity.getYHeadRot() + (float) j);
            }

        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity entityliving) {
        Vec3 vec3d = getCollisionHorizontalEscapeVector((double) (this.getBbWidth() * Mth.SQRT_OF_TWO), (double) entityliving.getBbWidth(), this.yRot);
        double d0 = this.getX() + vec3d.x;
        double d1 = this.getZ() + vec3d.z;
        BlockPos blockposition = new BlockPos(d0, this.getBoundingBox().maxY, d1);
        BlockPos blockposition1 = blockposition.below();

        if (!this.level.isWaterAt(blockposition1)) {
            UnmodifiableIterator unmodifiableiterator = entityliving.getDismountPoses().iterator();

            while (unmodifiableiterator.hasNext()) {
                Pose entitypose = (Pose) unmodifiableiterator.next();
                AABB axisalignedbb = entityliving.getLocalBoundsForPose(entitypose);
                double d2 = this.level.getRelativeFloorHeight(blockposition);

                if (DismountHelper.isFloorValid(d2)) {
                    Vec3 vec3d1 = new Vec3(d0, (double) blockposition.getY() + d2, d1);

                    if (DismountHelper.canDismountTo(this.level, entityliving, axisalignedbb.move(vec3d1))) {
                        entityliving.setPose(entitypose);
                        return vec3d1;
                    }
                }

                double d3 = this.level.getRelativeFloorHeight(blockposition1);

                if (DismountHelper.isFloorValid(d3)) {
                    Vec3 vec3d2 = new Vec3(d0, (double) blockposition1.getY() + d3, d1);

                    if (DismountHelper.canDismountTo(this.level, entityliving, axisalignedbb.move(vec3d2))) {
                        entityliving.setPose(entitypose);
                        return vec3d2;
                    }
                }
            }
        }

        return super.getDismountLocationForPassenger(entityliving);
    }

    protected void clampRotation(Entity entity) {
        entity.setYBodyRot(this.yRot);
        float f = Mth.wrapDegrees(entity.yRot - this.yRot);
        float f1 = Mth.clamp(f, -105.0F, 105.0F);

        entity.yRotO += f1 - f;
        entity.yRot += f1 - f;
        entity.setYHeadRot(entity.yRot);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putString("Type", this.getTypeOH().getName());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        if (nbttagcompound.contains("Type", 8)) {
            this.setType(Boat.Type.byName(nbttagcompound.getString("Type")));
        }

    }

    @Override
    public InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        return entityhuman.isSecondaryUseActive() ? InteractionResult.PASS : (this.outOfControlTicks < 60.0F ? (!this.level.isClientSide ? (entityhuman.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS) : InteractionResult.SUCCESS) : InteractionResult.PASS);
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {
        this.lastYd = this.getDeltaMovement().y;
        if (!this.isPassenger()) {
            if (flag) {
                if (this.fallDistance > 3.0F) {
                    if (this.status != Boat.Status.ON_LAND) {
                        this.fallDistance = 0.0F;
                        return;
                    }

                    this.causeFallDamage(this.fallDistance, 1.0F);
                    if (!this.level.isClientSide && !this.removed) {
                    // CraftBukkit start
                    Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                    VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, null);
                    this.level.getServerOH().getPluginManager().callEvent(destroyEvent);
                    if (!destroyEvent.isCancelled()) {
                        this.remove();
                        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                            int i;

                            for (i = 0; i < 3; ++i) {
                                this.spawnAtLocation((ItemLike) this.getTypeOH().getPlanks());
                            }

                            for (i = 0; i < 2; ++i) {
                                this.spawnAtLocation((ItemLike) Items.STICK);
                            }
                        }
                    }
                    } // CraftBukkit end
                }

                this.fallDistance = 0.0F;
            } else if (!this.level.getFluidState(this.blockPosition().below()).is((Tag) FluidTags.WATER) && d0 < 0.0D) {
                this.fallDistance = (float) ((double) this.fallDistance - d0);
            }

        }
    }

    public boolean getPaddleState(int i) {
        return (Boolean) this.entityData.get(i == 0 ? Boat.DATA_ID_PADDLE_LEFT : Boat.DATA_ID_PADDLE_RIGHT) && this.getControllingPassenger() != null;
    }

    public void setDamage(float f) {
        this.entityData.set(Boat.DATA_ID_DAMAGE, f);
    }

    public float getDamage() {
        return (Float) this.entityData.get(Boat.DATA_ID_DAMAGE);
    }

    public void setHurtTime(int i) {
        this.entityData.set(Boat.DATA_ID_HURT, i);
    }

    public int getHurtTime() {
        return (Integer) this.entityData.get(Boat.DATA_ID_HURT);
    }

    private void setBubbleTime(int i) {
        this.entityData.set(Boat.DATA_ID_BUBBLE_TIME, i);
    }

    private int getBubbleTime() {
        return (Integer) this.entityData.get(Boat.DATA_ID_BUBBLE_TIME);
    }

    public void setHurtDir(int i) {
        this.entityData.set(Boat.DATA_ID_HURTDIR, i);
    }

    public int getHurtDir() {
        return (Integer) this.entityData.get(Boat.DATA_ID_HURTDIR);
    }

    public void setType(Boat.Type entityboat_enumboattype) {
        this.entityData.set(Boat.DATA_ID_TYPE, entityboat_enumboattype.ordinal());
    }

    public Boat.Type getTypeOH() {
        return Boat.Type.byId((Integer) this.entityData.get(Boat.DATA_ID_TYPE));
    }

    @Override
    protected boolean canAddPassenger(Entity entity) {
        return this.getPassengers().size() < 2 && !this.isEyeInFluid((Tag) FluidTags.WATER);
    }

    @Nullable
    @Override
    public Entity getControllingPassenger() {
        List<Entity> list = this.getPassengers();

        return list.isEmpty() ? null : (Entity) list.get(0);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean isUnderWater() {
        return this.status == Boat.Status.UNDER_WATER || this.status == Boat.Status.UNDER_FLOWING_WATER;
    }

    public static enum Type {

        OAK(Blocks.OAK_PLANKS, "oak"), SPRUCE(Blocks.SPRUCE_PLANKS, "spruce"), BIRCH(Blocks.BIRCH_PLANKS, "birch"), JUNGLE(Blocks.JUNGLE_PLANKS, "jungle"), ACACIA(Blocks.ACACIA_PLANKS, "acacia"), DARK_OAK(Blocks.DARK_OAK_PLANKS, "dark_oak");

        private final String name;
        private final Block planks;

        private Type(Block block, String s) {
            this.name = s;
            this.planks = block;
        }

        public String getName() {
            return this.name;
        }

        public Block getPlanks() {
            return this.planks;
        }

        public String toString() {
            return this.name;
        }

        public static Boat.Type byId(int i) {
            Boat.Type[] aentityboat_enumboattype = values();

            if (i < 0 || i >= aentityboat_enumboattype.length) {
                i = 0;
            }

            return aentityboat_enumboattype[i];
        }

        public static Boat.Type byName(String s) {
            Boat.Type[] aentityboat_enumboattype = values();

            for (int i = 0; i < aentityboat_enumboattype.length; ++i) {
                if (aentityboat_enumboattype[i].getName().equals(s)) {
                    return aentityboat_enumboattype[i];
                }
            }

            return aentityboat_enumboattype[0];
        }
    }

    public static enum Status {

        IN_WATER, UNDER_WATER, UNDER_FLOWING_WATER, ON_LAND, IN_AIR;

        private Status() {}
    }
}
