package net.minecraft.world.entity.vehicle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.util.Pair;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.util.Vector;
// CraftBukkit end

public abstract class AbstractMinecart extends Entity {

    private static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_BLOCK = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_OFFSET = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ID_CUSTOM_DISPLAY = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
    private static final ImmutableMap<Pose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(Pose.STANDING, ImmutableList.of(0, 1, -1), Pose.CROUCHING, ImmutableList.of(0, 1, -1), Pose.SWIMMING, ImmutableList.of(0, 1));
    private boolean flipped;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> EXITS = (Map) Util.make(Maps.newEnumMap(RailShape.class), (enummap) -> { // CraftBukkit - decompile error
        Vec3i baseblockposition = Direction.WEST.getNormal();
        Vec3i baseblockposition1 = Direction.EAST.getNormal();
        Vec3i baseblockposition2 = Direction.NORTH.getNormal();
        Vec3i baseblockposition3 = Direction.SOUTH.getNormal();
        Vec3i baseblockposition4 = baseblockposition.below();
        Vec3i baseblockposition5 = baseblockposition1.below();
        Vec3i baseblockposition6 = baseblockposition2.below();
        Vec3i baseblockposition7 = baseblockposition3.below();

        enummap.put(RailShape.NORTH_SOUTH, Pair.of(baseblockposition2, baseblockposition3));
        enummap.put(RailShape.EAST_WEST, Pair.of(baseblockposition, baseblockposition1));
        enummap.put(RailShape.ASCENDING_EAST, Pair.of(baseblockposition4, baseblockposition1));
        enummap.put(RailShape.ASCENDING_WEST, Pair.of(baseblockposition, baseblockposition5));
        enummap.put(RailShape.ASCENDING_NORTH, Pair.of(baseblockposition2, baseblockposition7));
        enummap.put(RailShape.ASCENDING_SOUTH, Pair.of(baseblockposition6, baseblockposition3));
        enummap.put(RailShape.SOUTH_EAST, Pair.of(baseblockposition3, baseblockposition1));
        enummap.put(RailShape.SOUTH_WEST, Pair.of(baseblockposition3, baseblockposition));
        enummap.put(RailShape.NORTH_WEST, Pair.of(baseblockposition2, baseblockposition));
        enummap.put(RailShape.NORTH_EAST, Pair.of(baseblockposition2, baseblockposition1));
    });
    private int lSteps;
    private double lx;
    private double ly;
    private double lz;
    private double lyr;
    private double lxr;

    // CraftBukkit start
    public boolean slowWhenEmpty = true;
    private double derailedX = 0.5;
    private double derailedY = 0.5;
    private double derailedZ = 0.5;
    private double flyingX = 0.95;
    private double flyingY = 0.95;
    private double flyingZ = 0.95;
    public double maxSpeed = 0.4D;
    // CraftBukkit end

    protected AbstractMinecart(EntityType<?> entitytypes, Level world) {
        super(entitytypes, world);
        this.blocksBuilding = true;
    }

    protected AbstractMinecart(EntityType<?> entitytypes, Level world, double d0, double d1, double d2) {
        this(entitytypes, world);
        this.setPos(d0, d1, d2);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
    }

    public static AbstractMinecart createMinecart(Level world, double d0, double d1, double d2, AbstractMinecart.Type entityminecartabstract_enumminecarttype) {
        return (AbstractMinecart) (entityminecartabstract_enumminecarttype == AbstractMinecart.Type.CHEST ? new MinecartChest(world, d0, d1, d2) : (entityminecartabstract_enumminecarttype == AbstractMinecart.Type.FURNACE ? new MinecartFurnace(world, d0, d1, d2) : (entityminecartabstract_enumminecarttype == AbstractMinecart.Type.TNT ? new MinecartTNT(world, d0, d1, d2) : (entityminecartabstract_enumminecarttype == AbstractMinecart.Type.SPAWNER ? new MinecartSpawner(world, d0, d1, d2) : (entityminecartabstract_enumminecarttype == AbstractMinecart.Type.HOPPER ? new MinecartHopper(world, d0, d1, d2) : (entityminecartabstract_enumminecarttype == AbstractMinecart.Type.COMMAND_BLOCK ? new MinecartCommandBlock(world, d0, d1, d2) : new Minecart(world, d0, d1, d2)))))));
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(AbstractMinecart.DATA_ID_HURT, 0);
        this.entityData.register(AbstractMinecart.DATA_ID_HURTDIR, 1);
        this.entityData.register(AbstractMinecart.DATA_ID_DAMAGE, 0.0F);
        this.entityData.register(AbstractMinecart.DATA_ID_DISPLAY_BLOCK, Block.getCombinedId(Blocks.AIR.getBlockData()));
        this.entityData.register(AbstractMinecart.DATA_ID_DISPLAY_OFFSET, 6);
        this.entityData.register(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY, false);
    }

    @Nullable
    @Override
    public AABB getCollideAgainstBox(Entity entity) {
        return entity.isPushable() ? entity.getBoundingBox() : null;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity entityliving) {
        Direction enumdirection = this.getMotionDirection();

        if (enumdirection.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(entityliving);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(enumdirection);
            BlockPos blockposition = this.blockPosition();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
            ImmutableList<Pose> immutablelist = entityliving.getDismountPoses();
            UnmodifiableIterator unmodifiableiterator = immutablelist.iterator();

            while (unmodifiableiterator.hasNext()) {
                Pose entitypose = (Pose) unmodifiableiterator.next();
                EntityDimensions entitysize = entityliving.getDimensions(entitypose);
                float f = Math.min(entitysize.width, 1.0F) / 2.0F;
                UnmodifiableIterator unmodifiableiterator1 = ((ImmutableList) AbstractMinecart.POSE_DISMOUNT_HEIGHTS.get(entitypose)).iterator();

                while (unmodifiableiterator1.hasNext()) {
                    int i = (Integer) unmodifiableiterator1.next();
                    int[][] aint1 = aint;
                    int j = aint.length;

                    for (int k = 0; k < j; ++k) {
                        int[] aint2 = aint1[k];

                        blockposition_mutableblockposition.d(blockposition.getX() + aint2[0], blockposition.getY() + i, blockposition.getZ() + aint2[1]);
                        double d0 = this.level.getRelativeFloorHeight(blockposition_mutableblockposition, (iblockdata) -> {
                            return iblockdata.is((Tag) BlockTags.CLIMBABLE) ? true : iblockdata.getBlock() instanceof TrapDoorBlock && (Boolean) iblockdata.getValue(TrapDoorBlock.OPEN);
                        });

                        if (DismountHelper.isFloorValid(d0)) {
                            AABB axisalignedbb = new AABB((double) (-f), d0, (double) (-f), (double) f, d0 + (double) entitysize.height, (double) f);
                            Vec3 vec3d = Vec3.upFromBottomCenterOf((Vec3i) blockposition_mutableblockposition, d0);

                            if (DismountHelper.canDismountTo(this.level, entityliving, axisalignedbb.move(vec3d))) {
                                entityliving.setPose(entitypose);
                                return vec3d;
                            }
                        }
                    }
                }
            }

            double d1 = this.getBoundingBox().maxY;

            blockposition_mutableblockposition.c((double) blockposition.getX(), d1, (double) blockposition.getZ());
            UnmodifiableIterator unmodifiableiterator2 = immutablelist.iterator();

            while (unmodifiableiterator2.hasNext()) {
                Pose entitypose1 = (Pose) unmodifiableiterator2.next();
                double d2 = (double) entityliving.getDimensions(entitypose1).height;
                double d3 = (double) blockposition_mutableblockposition.getY() + this.level.getRelativeCeilingHeight(blockposition_mutableblockposition, d1 - (double) blockposition_mutableblockposition.getY() + d2);

                if (d1 + d2 <= d3) {
                    entityliving.setPose(entitypose1);
                    break;
                }
            }

            return super.getDismountLocationForPassenger(entityliving);
        }
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (!this.level.isClientSide && !this.removed) {
            if (this.isInvulnerableTo(damagesource)) {
                return false;
            } else {
                // CraftBukkit start - fire VehicleDamageEvent
                Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                org.bukkit.entity.Entity passenger = (damagesource.getEntity() == null) ? null : damagesource.getEntity().getBukkitEntity();

                VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, f);
                this.level.getServerOH().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return false;
                }

                f = (float) event.getDamage();
                // CraftBukkit end
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.markHurt();
                this.setDamage(this.getDamage() + f * 10.0F);
                boolean flag = damagesource.getEntity() instanceof Player && ((Player) damagesource.getEntity()).abilities.instabuild;

                if (flag || this.getDamage() > 40.0F) {
                    // CraftBukkit start
                    VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
                    this.level.getServerOH().getPluginManager().callEvent(destroyEvent);

                    if (destroyEvent.isCancelled()) {
                        this.setDamage(40); // Maximize damage so this doesn't get triggered again right away
                        return true;
                    }
                    // CraftBukkit end
                    this.ejectPassengers();
                    if (flag && !this.hasCustomName()) {
                        this.remove();
                    } else {
                        this.destroy(damagesource);
                    }
                }

                return true;
            }
        } else {
            return true;
        }
    }

    @Override
    protected float getBlockSpeedFactor() {
        BlockState iblockdata = this.level.getType(this.blockPosition());

        return iblockdata.is((Tag) BlockTags.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    public void destroy(DamageSource damagesource) {
        this.remove();
        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack itemstack = new ItemStack(Items.MINECART);

            if (this.hasCustomName()) {
                itemstack.setHoverName(this.getCustomName());
            }

            this.spawnAtLocation(itemstack);
        }

    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }

    private static Pair<Vec3i, Vec3i> exits(RailShape blockpropertytrackposition) {
        return (Pair) AbstractMinecart.EXITS.get(blockpropertytrackposition);
    }

    @Override
    public Direction getMotionDirection() {
        return this.flipped ? this.getDirection().getOpposite().getClockWise() : this.getDirection().getClockWise();
    }

    @Override
    public void tick() {
        // CraftBukkit start
        double prevX = this.getX();
        double prevY = this.getY();
        double prevZ = this.getZ();
        float prevYaw = this.yRot;
        float prevPitch = this.xRot;
        // CraftBukkit end

        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        if (this.getY() < -64.0D) {
            this.outOfWorld();
        }

        // this.doPortalTick(); // CraftBukkit - handled in postTick
        if (this.level.isClientSide) {
            if (this.lSteps > 0) {
                double d0 = this.getX() + (this.lx - this.getX()) / (double) this.lSteps;
                double d1 = this.getY() + (this.ly - this.getY()) / (double) this.lSteps;
                double d2 = this.getZ() + (this.lz - this.getZ()) / (double) this.lSteps;
                double d3 = Mth.wrapDegrees(this.lyr - (double) this.yRot);

                this.yRot = (float) ((double) this.yRot + d3 / (double) this.lSteps);
                this.xRot = (float) ((double) this.xRot + (this.lxr - (double) this.xRot) / (double) this.lSteps);
                --this.lSteps;
                this.setPos(d0, d1, d2);
                this.setRot(this.yRot, this.xRot);
            } else {
                this.reapplyPosition();
                this.setRot(this.yRot, this.xRot);
            }

        } else {
            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY());
            int k = Mth.floor(this.getZ());

            if (this.level.getType(new BlockPos(i, j - 1, k)).is((Tag) BlockTags.RAILS)) {
                --j;
            }

            BlockPos blockposition = new BlockPos(i, j, k);
            BlockState iblockdata = this.level.getType(blockposition);

            if (BaseRailBlock.isRail(iblockdata)) {
                this.moveAlongTrack(blockposition, iblockdata);
                if (iblockdata.is(Blocks.ACTIVATOR_RAIL)) {
                    this.activateMinecart(i, j, k, (Boolean) iblockdata.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.comeOffTrack();
            }

            this.checkInsideBlocks();
            this.xRot = 0.0F;
            double d4 = this.xo - this.getX();
            double d5 = this.zo - this.getZ();

            if (d4 * d4 + d5 * d5 > 0.001D) {
                this.yRot = (float) (Mth.atan2(d5, d4) * 180.0D / 3.141592653589793D);
                if (this.flipped) {
                    this.yRot += 180.0F;
                }
            }

            double d6 = (double) Mth.wrapDegrees(this.yRot - this.yRotO);

            if (d6 < -170.0D || d6 >= 170.0D) {
                this.yRot += 180.0F;
                this.flipped = !this.flipped;
            }

            this.setRot(this.yRot, this.xRot);
            // CraftBukkit start
            org.bukkit.World bworld = this.level.getWorld();
            Location from = new Location(bworld, prevX, prevY, prevZ, prevYaw, prevPitch);
            Location to = new Location(bworld, this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
            Vehicle vehicle = (Vehicle) this.getBukkitEntity();

            this.level.getServerOH().getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleUpdateEvent(vehicle));

            if (!from.equals(to)) {
                this.level.getServerOH().getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleMoveEvent(vehicle, from, to));
            }
            // CraftBukkit end
            if (this.getMinecartType() == AbstractMinecart.Type.RIDEABLE && getHorizontalDistanceSqr(this.getDeltaMovement()) > 0.01D) {
                List<Entity> list = this.level.getEntities(this, this.getBoundingBox().inflate(0.20000000298023224D, 0.0D, 0.20000000298023224D), EntitySelector.pushableBy(this));

                if (!list.isEmpty()) {
                    for (int l = 0; l < list.size(); ++l) {
                        Entity entity = (Entity) list.get(l);

                        if (!(entity instanceof Player) && !(entity instanceof IronGolem) && !(entity instanceof AbstractMinecart) && !this.isVehicle() && !entity.isPassenger()) {
                            // CraftBukkit start
                            VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(vehicle, entity.getBukkitEntity());
                            this.level.getServerOH().getPluginManager().callEvent(collisionEvent);

                            if (collisionEvent.isCancelled()) {
                                continue;
                            }
                            // CraftBukkit end
                            entity.startRiding(this);
                        } else {
                            // CraftBukkit start
                            if (!this.isPassengerOfSameVehicle(entity)) {
                                VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(vehicle, entity.getBukkitEntity());
                                this.level.getServerOH().getPluginManager().callEvent(collisionEvent);

                                if (collisionEvent.isCancelled()) {
                                    continue;
                                }
                            }
                            // CraftBukkit end
                            entity.push(this);
                        }
                    }
                }
            } else {
                Iterator iterator = this.level.getEntities(this, this.getBoundingBox().inflate(0.20000000298023224D, 0.0D, 0.20000000298023224D)).iterator();

                while (iterator.hasNext()) {
                    Entity entity1 = (Entity) iterator.next();

                    if (!this.hasPassenger(entity1) && entity1.isPushable() && entity1 instanceof AbstractMinecart) {
                        // CraftBukkit start
                        VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(vehicle, entity1.getBukkitEntity());
                        this.level.getServerOH().getPluginManager().callEvent(collisionEvent);

                        if (collisionEvent.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end
                        entity1.push(this);
                    }
                }
            }

            this.updateInWaterStateAndDoFluidPushing();
        }
    }

    protected double getMaxSpeed() {
        return this.maxSpeed; // CraftBukkit
    }

    public void activateMinecart(int i, int j, int k, boolean flag) {}

    protected void comeOffTrack() {
        double d0 = this.getMaxSpeed();
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(Mth.clamp(vec3d.x, -d0, d0), vec3d.y, Mth.clamp(vec3d.z, -d0, d0));
        if (this.onGround) {
            // CraftBukkit start - replace magic numbers with our variables
            this.setDeltaMovement(new Vec3(this.getDeltaMovement().x * this.derailedX, this.getDeltaMovement().y * this.derailedY, this.getDeltaMovement().z * this.derailedZ));
            // CraftBukkit end
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround) {
            // CraftBukkit start - replace magic numbers with our variables
            this.setDeltaMovement(new Vec3(this.getDeltaMovement().x * this.flyingX, this.getDeltaMovement().y * this.flyingY, this.getDeltaMovement().z * this.flyingZ));
            // CraftBukkit end
        }

    }

    protected void moveAlongTrack(BlockPos blockposition, BlockState iblockdata) {
        this.fallDistance = 0.0F;
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        Vec3 vec3d = this.getPos(d0, d1, d2);

        d1 = (double) blockposition.getY();
        boolean flag = false;
        boolean flag1 = false;
        BaseRailBlock blockminecarttrackabstract = (BaseRailBlock) iblockdata.getBlock();

        if (blockminecarttrackabstract == Blocks.POWERED_RAIL) {
            flag = (Boolean) iblockdata.getValue(PoweredRailBlock.POWERED);
            flag1 = !flag;
        }

        double d3 = 0.0078125D;
        Vec3 vec3d1 = this.getDeltaMovement();
        RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(blockminecarttrackabstract.getShapeProperty());

        switch (blockpropertytrackposition) {
            case ASCENDING_EAST:
                this.setDeltaMovement(vec3d1.add(-0.0078125D, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_WEST:
                this.setDeltaMovement(vec3d1.add(0.0078125D, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_NORTH:
                this.setDeltaMovement(vec3d1.add(0.0D, 0.0D, 0.0078125D));
                ++d1;
                break;
            case ASCENDING_SOUTH:
                this.setDeltaMovement(vec3d1.add(0.0D, 0.0D, -0.0078125D));
                ++d1;
        }

        vec3d1 = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> pair = exits(blockpropertytrackposition);
        Vec3i baseblockposition = (Vec3i) pair.getFirst();
        Vec3i baseblockposition1 = (Vec3i) pair.getSecond();
        double d4 = (double) (baseblockposition1.getX() - baseblockposition.getX());
        double d5 = (double) (baseblockposition1.getZ() - baseblockposition.getZ());
        double d6 = Math.sqrt(d4 * d4 + d5 * d5);
        double d7 = vec3d1.x * d4 + vec3d1.z * d5;

        if (d7 < 0.0D) {
            d4 = -d4;
            d5 = -d5;
        }

        double d8 = Math.min(2.0D, Math.sqrt(getHorizontalDistanceSqr(vec3d1)));

        vec3d1 = new Vec3(d8 * d4 / d6, vec3d1.y, d8 * d5 / d6);
        this.setDeltaMovement(vec3d1);
        Entity entity = this.getPassengers().isEmpty() ? null : (Entity) this.getPassengers().get(0);

        if (entity instanceof Player) {
            Vec3 vec3d2 = entity.getDeltaMovement();
            double d9 = getHorizontalDistanceSqr(vec3d2);
            double d10 = getHorizontalDistanceSqr(this.getDeltaMovement());

            if (d9 > 1.0E-4D && d10 < 0.01D) {
                this.setDeltaMovement(this.getDeltaMovement().add(vec3d2.x * 0.1D, 0.0D, vec3d2.z * 0.1D));
                flag1 = false;
            }
        }

        double d11;

        if (flag1) {
            d11 = Math.sqrt(getHorizontalDistanceSqr(this.getDeltaMovement()));
            if (d11 < 0.03D) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
            }
        }

        d11 = (double) blockposition.getX() + 0.5D + (double) baseblockposition.getX() * 0.5D;
        double d12 = (double) blockposition.getZ() + 0.5D + (double) baseblockposition.getZ() * 0.5D;
        double d13 = (double) blockposition.getX() + 0.5D + (double) baseblockposition1.getX() * 0.5D;
        double d14 = (double) blockposition.getZ() + 0.5D + (double) baseblockposition1.getZ() * 0.5D;

        d4 = d13 - d11;
        d5 = d14 - d12;
        double d15;
        double d16;
        double d17;

        if (d4 == 0.0D) {
            d15 = d2 - (double) blockposition.getZ();
        } else if (d5 == 0.0D) {
            d15 = d0 - (double) blockposition.getX();
        } else {
            d16 = d0 - d11;
            d17 = d2 - d12;
            d15 = (d16 * d4 + d17 * d5) * 2.0D;
        }

        d0 = d11 + d4 * d15;
        d2 = d12 + d5 * d15;
        this.setPos(d0, d1, d2);
        d16 = this.isVehicle() ? 0.75D : 1.0D;
        d17 = this.getMaxSpeed();
        vec3d1 = this.getDeltaMovement();
        this.move(MoverType.SELF, new Vec3(Mth.clamp(d16 * vec3d1.x, -d17, d17), 0.0D, Mth.clamp(d16 * vec3d1.z, -d17, d17)));
        if (baseblockposition.getY() != 0 && Mth.floor(this.getX()) - blockposition.getX() == baseblockposition.getX() && Mth.floor(this.getZ()) - blockposition.getZ() == baseblockposition.getZ()) {
            this.setPos(this.getX(), this.getY() + (double) baseblockposition.getY(), this.getZ());
        } else if (baseblockposition1.getY() != 0 && Mth.floor(this.getX()) - blockposition.getX() == baseblockposition1.getX() && Mth.floor(this.getZ()) - blockposition.getZ() == baseblockposition1.getZ()) {
            this.setPos(this.getX(), this.getY() + (double) baseblockposition1.getY(), this.getZ());
        }

        this.applyNaturalSlowdown();
        Vec3 vec3d3 = this.getPos(this.getX(), this.getY(), this.getZ());
        Vec3 vec3d4;
        double d18;

        if (vec3d3 != null && vec3d != null) {
            double d19 = (vec3d.y - vec3d3.y) * 0.05D;

            vec3d4 = this.getDeltaMovement();
            d18 = Math.sqrt(getHorizontalDistanceSqr(vec3d4));
            if (d18 > 0.0D) {
                this.setDeltaMovement(vec3d4.multiply((d18 + d19) / d18, 1.0D, (d18 + d19) / d18));
            }

            this.setPos(this.getX(), vec3d3.y, this.getZ());
        }

        int i = Mth.floor(this.getX());
        int j = Mth.floor(this.getZ());

        if (i != blockposition.getX() || j != blockposition.getZ()) {
            vec3d4 = this.getDeltaMovement();
            d18 = Math.sqrt(getHorizontalDistanceSqr(vec3d4));
            this.setDeltaMovement(d18 * (double) (i - blockposition.getX()), vec3d4.y, d18 * (double) (j - blockposition.getZ()));
        }

        if (flag) {
            vec3d4 = this.getDeltaMovement();
            d18 = Math.sqrt(getHorizontalDistanceSqr(vec3d4));
            if (d18 > 0.01D) {
                double d20 = 0.06D;

                this.setDeltaMovement(vec3d4.add(vec3d4.x / d18 * 0.06D, 0.0D, vec3d4.z / d18 * 0.06D));
            } else {
                Vec3 vec3d5 = this.getDeltaMovement();
                double d21 = vec3d5.x;
                double d22 = vec3d5.z;

                if (blockpropertytrackposition == RailShape.EAST_WEST) {
                    if (this.isRedstoneConductor(blockposition.west())) {
                        d21 = 0.02D;
                    } else if (this.isRedstoneConductor(blockposition.east())) {
                        d21 = -0.02D;
                    }
                } else {
                    if (blockpropertytrackposition != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.isRedstoneConductor(blockposition.north())) {
                        d22 = 0.02D;
                    } else if (this.isRedstoneConductor(blockposition.south())) {
                        d22 = -0.02D;
                    }
                }

                this.setDeltaMovement(d21, vec3d5.y, d22);
            }
        }

    }

    private boolean isRedstoneConductor(BlockPos blockposition) {
        return this.level.getType(blockposition).isRedstoneConductor(this.level, blockposition);
    }

    protected void applyNaturalSlowdown() {
        double d0 = this.isVehicle() || !this.slowWhenEmpty ? 0.997D : 0.96D; // CraftBukkit - add !this.slowWhenEmpty

        this.setDeltaMovement(this.getDeltaMovement().multiply(d0, 0.0D, d0));
    }

    @Nullable
    public Vec3 getPos(double d0, double d1, double d2) {
        int i = Mth.floor(d0);
        int j = Mth.floor(d1);
        int k = Mth.floor(d2);

        if (this.level.getType(new BlockPos(i, j - 1, k)).is((Tag) BlockTags.RAILS)) {
            --j;
        }

        BlockState iblockdata = this.level.getType(new BlockPos(i, j, k));

        if (BaseRailBlock.isRail(iblockdata)) {
            RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = exits(blockpropertytrackposition);
            Vec3i baseblockposition = (Vec3i) pair.getFirst();
            Vec3i baseblockposition1 = (Vec3i) pair.getSecond();
            double d3 = (double) i + 0.5D + (double) baseblockposition.getX() * 0.5D;
            double d4 = (double) j + 0.0625D + (double) baseblockposition.getY() * 0.5D;
            double d5 = (double) k + 0.5D + (double) baseblockposition.getZ() * 0.5D;
            double d6 = (double) i + 0.5D + (double) baseblockposition1.getX() * 0.5D;
            double d7 = (double) j + 0.0625D + (double) baseblockposition1.getY() * 0.5D;
            double d8 = (double) k + 0.5D + (double) baseblockposition1.getZ() * 0.5D;
            double d9 = d6 - d3;
            double d10 = (d7 - d4) * 2.0D;
            double d11 = d8 - d5;
            double d12;

            if (d9 == 0.0D) {
                d12 = d2 - (double) k;
            } else if (d11 == 0.0D) {
                d12 = d0 - (double) i;
            } else {
                double d13 = d0 - d3;
                double d14 = d2 - d5;

                d12 = (d13 * d9 + d14 * d11) * 2.0D;
            }

            d0 = d3 + d9 * d12;
            d1 = d4 + d10 * d12;
            d2 = d5 + d11 * d12;
            if (d10 < 0.0D) {
                ++d1;
            } else if (d10 > 0.0D) {
                d1 += 0.5D;
            }

            return new Vec3(d0, d1, d2);
        } else {
            return null;
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        if (nbttagcompound.getBoolean("CustomDisplayTile")) {
            this.setDisplayBlock(NbtUtils.readBlockState(nbttagcompound.getCompound("DisplayState")));
            this.setDisplayOffset(nbttagcompound.getInt("DisplayOffset"));
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        if (this.hasCustomDisplay()) {
            nbttagcompound.putBoolean("CustomDisplayTile", true);
            nbttagcompound.put("DisplayState", NbtUtils.writeBlockState(this.getDisplayBlock()));
            nbttagcompound.putInt("DisplayOffset", this.getDisplayOffset());
        }

    }

    @Override
    public void push(Entity entity) {
        if (!this.level.isClientSide) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (!this.hasPassenger(entity)) {
                    // CraftBukkit start
                    VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent((Vehicle) this.getBukkitEntity(), entity.getBukkitEntity());
                    this.level.getServerOH().getPluginManager().callEvent(collisionEvent);

                    if (collisionEvent.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    double d0 = entity.getX() - this.getX();
                    double d1 = entity.getZ() - this.getZ();
                    double d2 = d0 * d0 + d1 * d1;

                    if (d2 >= 9.999999747378752E-5D) {
                        d2 = (double) Mth.sqrt(d2);
                        d0 /= d2;
                        d1 /= d2;
                        double d3 = 1.0D / d2;

                        if (d3 > 1.0D) {
                            d3 = 1.0D;
                        }

                        d0 *= d3;
                        d1 *= d3;
                        d0 *= 0.10000000149011612D;
                        d1 *= 0.10000000149011612D;
                        d0 *= (double) (1.0F - this.pushthrough);
                        d1 *= (double) (1.0F - this.pushthrough);
                        d0 *= 0.5D;
                        d1 *= 0.5D;
                        if (entity instanceof AbstractMinecart) {
                            double d4 = entity.getX() - this.getX();
                            double d5 = entity.getZ() - this.getZ();
                            Vec3 vec3d = (new Vec3(d4, 0.0D, d5)).normalize();
                            Vec3 vec3d1 = (new Vec3((double) Mth.cos(this.yRot * 0.017453292F), 0.0D, (double) Mth.sin(this.yRot * 0.017453292F))).normalize();
                            double d6 = Math.abs(vec3d.dot(vec3d1));

                            if (d6 < 0.800000011920929D) {
                                return;
                            }

                            Vec3 vec3d2 = this.getDeltaMovement();
                            Vec3 vec3d3 = entity.getDeltaMovement();

                            if (((AbstractMinecart) entity).getMinecartType() == AbstractMinecart.Type.FURNACE && this.getMinecartType() != AbstractMinecart.Type.FURNACE) {
                                this.setDeltaMovement(vec3d2.multiply(0.2D, 1.0D, 0.2D));
                                this.push(vec3d3.x - d0, 0.0D, vec3d3.z - d1);
                                entity.setDeltaMovement(vec3d3.multiply(0.95D, 1.0D, 0.95D));
                            } else if (((AbstractMinecart) entity).getMinecartType() != AbstractMinecart.Type.FURNACE && this.getMinecartType() == AbstractMinecart.Type.FURNACE) {
                                entity.setDeltaMovement(vec3d3.multiply(0.2D, 1.0D, 0.2D));
                                entity.push(vec3d2.x + d0, 0.0D, vec3d2.z + d1);
                                this.setDeltaMovement(vec3d2.multiply(0.95D, 1.0D, 0.95D));
                            } else {
                                double d7 = (vec3d3.x + vec3d2.x) / 2.0D;
                                double d8 = (vec3d3.z + vec3d2.z) / 2.0D;

                                this.setDeltaMovement(vec3d2.multiply(0.2D, 1.0D, 0.2D));
                                this.push(d7 - d0, 0.0D, d8 - d1);
                                entity.setDeltaMovement(vec3d3.multiply(0.2D, 1.0D, 0.2D));
                                entity.push(d7 + d0, 0.0D, d8 + d1);
                            }
                        } else {
                            this.push(-d0, 0.0D, -d1);
                            entity.push(d0 / 4.0D, 0.0D, d1 / 4.0D);
                        }
                    }

                }
            }
        }
    }

    public void setDamage(float f) {
        this.entityData.set(AbstractMinecart.DATA_ID_DAMAGE, f);
    }

    public float getDamage() {
        return (Float) this.entityData.get(AbstractMinecart.DATA_ID_DAMAGE);
    }

    public void setHurtTime(int i) {
        this.entityData.set(AbstractMinecart.DATA_ID_HURT, i);
    }

    public int getHurtTime() {
        return (Integer) this.entityData.get(AbstractMinecart.DATA_ID_HURT);
    }

    public void setHurtDir(int i) {
        this.entityData.set(AbstractMinecart.DATA_ID_HURTDIR, i);
    }

    public int getHurtDir() {
        return (Integer) this.entityData.get(AbstractMinecart.DATA_ID_HURTDIR);
    }

    public abstract AbstractMinecart.Type getMinecartType();

    public BlockState getDisplayBlock() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayBlockState() : Block.getByCombinedId((Integer) this.getEntityData().get(AbstractMinecart.DATA_ID_DISPLAY_BLOCK));
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.AIR.getBlockData();
    }

    public int getDisplayOffset() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayOffset() : (Integer) this.getEntityData().get(AbstractMinecart.DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setDisplayBlock(BlockState iblockdata) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_DISPLAY_BLOCK, Block.getCombinedId(iblockdata));
        this.setCustomDisplay(true);
    }

    public void setDisplayOffset(int i) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_DISPLAY_OFFSET, i);
        this.setCustomDisplay(true);
    }

    public boolean hasCustomDisplay() {
        return (Boolean) this.getEntityData().get(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY);
    }

    public void setCustomDisplay(boolean flag) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY, flag);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public static enum Type {

        RIDEABLE, CHEST, FURNACE, TNT, SPAWNER, HOPPER, COMMAND_BLOCK;

        private Type() {}
    }

    // CraftBukkit start - Methods for getting and setting flying and derailed velocity modifiers
    public Vector getFlyingVelocityMod() {
        return new Vector(flyingX, flyingY, flyingZ);
    }

    public void setFlyingVelocityMod(Vector flying) {
        flyingX = flying.getX();
        flyingY = flying.getY();
        flyingZ = flying.getZ();
    }

    public Vector getDerailedVelocityMod() {
        return new Vector(derailedX, derailedY, derailedZ);
    }

    public void setDerailedVelocityMod(Vector derailed) {
        derailedX = derailed.getX();
        derailedY = derailed.getY();
        derailedZ = derailed.getZ();
    }
    // CraftBukkit end
}
