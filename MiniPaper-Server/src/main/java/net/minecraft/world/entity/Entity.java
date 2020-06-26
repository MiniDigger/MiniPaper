package net.minecraft.world.entity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.RewindableStream;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.spigotmc.CustomTimingsHandler; // Spigot
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
// CraftBukkit end

public abstract class Entity implements Nameable, CommandSource {

    // CraftBukkit start
    private static final int CURRENT_LEVEL = 2;
    static boolean isLevelAtLeast(CompoundTag tag, int level) {
        return tag.contains("Bukkit.updateLevel") && tag.getInt("Bukkit.updateLevel") >= level;
    }

    private CraftEntity bukkitEntity;

    public CraftEntity getBukkitEntity() {
        if (bukkitEntity == null) {
            bukkitEntity = CraftEntity.getEntity(level.getServerOH(), this);
        }
        return bukkitEntity;
    }

    @Override
    public CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return getBukkitEntity();
    }
    // CraftBukkit end

    protected static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    private static final List<ItemStack> EMPTY_LIST = Collections.emptyList();
    private static final AABB INITIAL_AABB = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static double viewScale = 1.0D;
    private final EntityType<?> type;
    private int id;
    public boolean blocksBuilding;
    public final List<Entity> passengers;
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    public boolean forcedLoading;
    public Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    private BlockPos blockPosition;
    private Vec3 deltaMovement;
    public float yRot;
    public float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb;
    protected boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier;
    public boolean removed;
    public float walkDistO;
    public float walkDist;
    public float moveDist;
    public float fallDistance;
    private float nextStep;
    private float nextFlap;
    public double xOld;
    public double yOld;
    public double zOld;
    public float maxUpStep;
    public boolean noPhysics;
    public float pushthrough;
    protected final Random random;
    public int tickCount;
    public int remainingFireTicks;
    public boolean wasTouchingWater;
    protected Object2DoubleMap<Tag<Fluid>> fluidHeight;
    protected boolean wasEyeInWater;
    @Nullable
    protected Tag<Fluid> fluidOnEyes;
    protected boolean isTouchingLava;
    public int invulnerableTime;
    protected boolean firstTick;
    public final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<net.minecraft.world.entity.Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
    public boolean inChunk;
    public int xChunk; public int getChunkX() { return xChunk; } // Paper - OBFHELPER
    public int yChunk; public int getChunkY() { return yChunk; } // Paper - OBFHELPER
    public int zChunk; public int getChunkZ() { return zChunk; } // Paper - OBFHELPER
    private boolean movedSinceLastChunkCheck;
    public long xp;
    public long yp;
    public long zp;
    public boolean noCulling;
    public boolean hasImpulse;
    public int changingDimensionDelay;
    protected boolean isInsidePortal;
    protected int portalTime;
    protected BlockPos portalEntranceBlock;
    protected Vec3 portalEntranceOffset;
    protected Direction portalEntranceForwards;
    private boolean invulnerable;
    public UUID uuid;
    protected String stringUUID;
    public boolean glowing;
    private final Set<String> tags;
    private boolean forceChunkAddition;
    private final double[] pistonDeltas;
    private long pistonDeltasGameTime;
    private EntityDimensions dimensions;
    private float eyeHeight;
    // CraftBukkit start
    public boolean persist = true;
    public boolean valid;
    public org.bukkit.projectiles.ProjectileSource projectileSource; // For projectiles only
    public boolean forceExplosionKnockback; // SPIGOT-949
    public CustomTimingsHandler tickTimer = org.bukkit.craftbukkit.SpigotTimings.getEntityTimings(this); // Spigot
    // Spigot start
    public final org.spigotmc.ActivationRange.ActivationType activationType = org.spigotmc.ActivationRange.initializeEntityActivationType(this);
    public final boolean defaultActivationState;
    public long activatedTick = Integer.MIN_VALUE;
    public void inactiveTick() { }
    // Spigot end

    public float getBukkitYaw() {
        return this.yRot;
    }

    public boolean isChunkLoaded() {
        return level.hasChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4);
    }
    // CraftBukkit end

<<<<<<< HEAD
=======
    // Paper start
    /**
     * Overriding this field will cause memory leaks.
     */
    private final boolean hardCollides;

    private static final java.util.Map<Class<? extends Entity>, Boolean> cachedOverrides = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    {
        Boolean hardCollides = cachedOverrides.get(this.getClass());
        if (hardCollides == null) {
            try {
                Object getHardCollisionBoxMethod = Entity.class.getMethod("getCollideBox"); // Toothpick - remap fix
                Object getHardCollisionBoxEntityMethod = Entity.class.getMethod("getCollideAgainstBox", Entity.class); // Toothpick - remap fix
                if (!this.getClass().getMethod("getCollideBox").equals(getHardCollisionBoxMethod)) { // Toothpick - remap fix
                    hardCollides = Boolean.TRUE;
                } else if (!this.getClass().getMethod("getCollideAgainstBox", Entity.class).equals(getHardCollisionBoxEntityMethod)) { // Toothpick - remap fix
                    hardCollides = Boolean.TRUE;
                } else {
                    hardCollides = Boolean.FALSE;
                }
                cachedOverrides.put(this.getClass(), hardCollides);
            } catch (Throwable thr) {
                // shouldn't happen, just explode
                throw new RuntimeException(thr);
            }
        }
        this.hardCollides = hardCollides.booleanValue();
    }

    public final boolean hardCollides() {
        return this.hardCollides;
    }
    // Paper end

    // Paper start - optimise entity tracking
    final org.spigotmc.TrackingRange.TrackingRangeType trackingRangeType = org.spigotmc.TrackingRange.getTrackingRangeType(this);

    public boolean isLegacyTrackingEntity = false;

    public final void setLegacyTrackingEntity(final boolean isLegacyTrackingEntity) {
        this.isLegacyTrackingEntity = isLegacyTrackingEntity;
    }

    public final com.destroystokyo.paper.util.misc.PooledLinkedHashSets.PooledObjectLinkedOpenHashSet<ServerPlayer> getPlayersInTrackRange() {
        return ((ServerLevel)this.level).getChunkSourceOH().chunkMap.playerEntityTrackerTrackMaps[this.trackingRangeType.ordinal()]
            .getObjectsInRange(MCUtil.getCoordinateKey(this));
    }
    // Paper end - optimise entity tracking

>>>>>>> Toothpick
    public Entity(EntityType<?> entitytypes, Level world) {
        this.id = Entity.ENTITY_COUNTER.incrementAndGet();
        this.passengers = Lists.newArrayList();
        this.deltaMovement = Vec3.ZERO;
        this.bb = Entity.INITIAL_AABB;
        this.stuckSpeedMultiplier = Vec3.ZERO;
        this.nextStep = 1.0F;
        this.nextFlap = 1.0F;
        this.random = new Random();
        this.remainingFireTicks = -this.getFireImmuneTicks();
        this.fluidHeight = new Object2DoubleArrayMap(2);
        this.firstTick = true;
        this.uuid = Mth.createInsecureUUID(this.random);
        this.stringUUID = this.uuid.toString();
        this.tags = Sets.newHashSet();
        this.pistonDeltas = new double[]{0.0D, 0.0D, 0.0D};
        this.type = entitytypes;
        this.level = world;
        this.dimensions = entitytypes.getDimensions();
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.setPos(0.0D, 0.0D, 0.0D);
        // Spigot start
        if (world != null) {
            this.defaultActivationState = org.spigotmc.ActivationRange.initializeEntityActivationState(this, world.spigotConfig);
        } else {
            this.defaultActivationState = false;
        }
        // Spigot end
        this.entityData = new SynchedEntityData(this);
        this.entityData.register(Entity.DATA_SHARED_FLAGS_ID, (byte) 0);
        this.entityData.register(Entity.DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        this.entityData.register(Entity.DATA_CUSTOM_NAME_VISIBLE, false);
        this.entityData.register(Entity.DATA_CUSTOM_NAME, Optional.empty());
        this.entityData.register(Entity.DATA_SILENT, false);
        this.entityData.register(Entity.DATA_NO_GRAVITY, false);
        this.entityData.register(Entity.DATA_POSE, net.minecraft.world.entity.Pose.STANDING);
        this.defineSynchedData();
        this.entityData.registrationLocked = true; // Spigot
        this.eyeHeight = this.getEyeHeight(net.minecraft.world.entity.Pose.STANDING, this.dimensions);
    }

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

    }

    public void setPacketCoordinates(double d0, double d1, double d2) {
        this.xp = ClientboundMoveEntityPacket.entityToPacket(d0);
        this.yp = ClientboundMoveEntityPacket.entityToPacket(d1);
        this.zp = ClientboundMoveEntityPacket.entityToPacket(d2);
    }

    public EntityType<?> getType() {
        return this.type;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int i) {
        this.id = i;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String s) {
        return this.tags.size() >= 1024 ? false : this.tags.add(s);
    }

    public boolean removeTag(String s) {
        return this.tags.remove(s);
    }

    public void kill() {
        this.remove();
    }

    protected abstract void defineSynchedData();

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    public boolean equals(Object object) {
        return object instanceof Entity ? ((Entity) object).id == this.id : false;
    }

    public int hashCode() {
        return this.id;
    }

    public void remove() {
        this.removed = true;
    }

    public void setPose(net.minecraft.world.entity.Pose entitypose) {
        // CraftBukkit start
        if (entitypose == this.getPose()) {
            return;
        }
        this.level.getServerOH().getPluginManager().callEvent(new EntityPoseChangeEvent(this.getBukkitEntity(), Pose.values()[entitypose.ordinal()]));
        // CraftBukkit end
        this.entityData.set(Entity.DATA_POSE, entitypose);
    }

    public net.minecraft.world.entity.Pose getPose() {
        return (net.minecraft.world.entity.Pose) this.entityData.get(Entity.DATA_POSE);
    }

    public boolean closerThan(Entity entity, double d0) {
        double d1 = entity.position.x - this.position.x;
        double d2 = entity.position.y - this.position.y;
        double d3 = entity.position.z - this.position.z;

        return d1 * d1 + d2 * d2 + d3 * d3 < d0 * d0;
    }

    public void setRot(float f, float f1) {
        // CraftBukkit start - yaw was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(f)) {
            f = 0;
        }

        if (f == Float.POSITIVE_INFINITY || f == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getServerOH().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid yaw");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite yaw (Hacking?)");
            }
            f = 0;
        }

        // pitch was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(f1)) {
            f1 = 0;
        }

        if (f1 == Float.POSITIVE_INFINITY || f1 == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getServerOH().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid pitch");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite pitch (Hacking?)");
            }
            f1 = 0;
        }
        // CraftBukkit end

        this.yRot = f % 360.0F;
        this.xRot = f1 % 360.0F;
    }

    public void setPos(double d0, double d1, double d2) {
        this.setPosRaw(d0, d1, d2);
        float f = this.dimensions.width / 2.0F;
        float f1 = this.dimensions.height;

        this.setBoundingBox(new AABB(d0 - (double) f, d1, d2 - (double) f, d0 + (double) f, d1 + (double) f1, d2 + (double) f));
        if (valid) ((ServerLevel) level).updateChunkPos(this); // CraftBukkit
    }

    protected void reapplyPosition() {
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void tick() {
        if (!this.level.isClientSide) {
            this.setSharedFlag(6, this.isGlowing());
        }

        this.baseTick();
    }

    // CraftBukkit start
    public void postTick() {
        // No clean way to break out of ticking once the entity has been copied to a new world, so instead we move the portalling later in the tick cycle
        if (!(this instanceof ServerPlayer)) {
            this.handleNetherPortal();
        }
    }
    // CraftBukkit end

    public void baseTick() {
        this.level.getProfiler().push("entityBaseTick");
        if (this.isPassenger() && this.getVehicle().removed) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            --this.boardingCooldown;
        }

        this.walkDistO = this.walkDist;
        this.xRotO = this.xRot;
        this.yRotO = this.yRot;
        if (this instanceof ServerPlayer) this.handleNetherPortal(); // CraftBukkit - // Moved up to postTick
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        if (this.level.isClientSide) {
            this.clearFire();
        } else if (this.remainingFireTicks > 0) {
            if (this.fireImmune()) {
                this.setRemainingFireTicks(this.remainingFireTicks - 4);
                if (this.remainingFireTicks < 0) {
                    this.clearFire();
                }
            } else {
                if (this.remainingFireTicks % 20 == 0) {
                    this.hurt(DamageSource.ON_FIRE, 1.0F);
                }

                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            }
        }

        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
        }

        if (this.getY() < -64.0D) {
            this.outOfWorld();
        }

        if (!this.level.isClientSide) {
            this.setSharedFlag(0, this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        this.level.getProfiler().pop();
    }

    protected void processDimensionDelay() {
        if (this.changingDimensionDelay > 0) {
            --this.changingDimensionDelay;
        }

    }

    public int getPortalWaitTime() {
        return 1;
    }

    protected void lavaHurt() {
        if (!this.fireImmune()) {
            // CraftBukkit start - Fallen in lava TODO: this event spams!
            if (this instanceof net.minecraft.world.entity.LivingEntity && remainingFireTicks <= 0) {
                // not on fire yet
                // TODO: shouldn't be sending null for the block
                org.bukkit.block.Block damager = null; // ((WorldServer) this.l).getWorld().getBlockAt(i, j, k);
                org.bukkit.entity.Entity damagee = this.getBukkitEntity();
                EntityCombustEvent combustEvent = new org.bukkit.event.entity.EntityCombustByBlockEvent(damager, damagee, 15);
                this.level.getServerOH().getPluginManager().callEvent(combustEvent);

                if (!combustEvent.isCancelled()) {
                    this.setOnFire(combustEvent.getDuration(), false);
                }
            } else {
                // This will be called every single tick the entity is in lava, so don't throw an event
                this.setOnFire(15, false);
            }
            // CraftBukkit end - we also don't throw an event unless the object in lava is living, to save on some event calls
            this.hurt(DamageSource.LAVA, 4.0F);
        }
    }

    public void setSecondsOnFire(int i) {
        // CraftBukkit start
        this.setOnFire(i, true);
    }

    public void setOnFire(int i, boolean callEvent) {
        if (callEvent) {
            EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity(), i);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            i = event.getDuration();
        }
        // CraftBukkit end
        int j = i * 20;

        if (this instanceof net.minecraft.world.entity.LivingEntity) {
            j = ProtectionEnchantment.getFireAfterDampener((net.minecraft.world.entity.LivingEntity) this, j);
        }

        if (this.remainingFireTicks < j) {
            this.setRemainingFireTicks(j);
        }

    }

    public void setRemainingFireTicks(int i) {
        this.remainingFireTicks = i;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void outOfWorld() {
        this.remove();
    }

    public boolean isFree(double d0, double d1, double d2) {
        return this.isFree(this.getBoundingBox().move(d0, d1, d2));
    }

    private boolean isFree(AABB axisalignedbb) {
        return this.level.noCollision(this, axisalignedbb) && !this.level.containsAnyLiquid(axisalignedbb);
    }

    public void setOnGround(boolean flag) {
        this.onGround = flag;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public void move(MoverType enummovetype, Vec3 vec3d) {
        org.bukkit.craftbukkit.SpigotTimings.entityMoveTimer.startTiming(); // Spigot
        if (this.noPhysics) {
            this.setBoundingBox(this.getBoundingBox().move(vec3d));
            this.setLocationFromBoundingbox();
        } else {
            if (enummovetype == MoverType.PISTON) {
                vec3d = this.limitPistonMovement(vec3d);
                if (vec3d.equals(Vec3.ZERO)) {
                    return;
                }
            }

            this.level.getProfiler().push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
                vec3d = vec3d.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }

            vec3d = this.maybeBackOffFromEdge(vec3d, enummovetype);
            Vec3 vec3d1 = this.collide(vec3d);

            if (vec3d1.lengthSqr() > 1.0E-7D) {
                this.setBoundingBox(this.getBoundingBox().move(vec3d1));
                this.setLocationFromBoundingbox();
            }

            this.level.getProfiler().pop();
            this.level.getProfiler().push("rest");
            this.horizontalCollision = !Mth.equal(vec3d.x, vec3d1.x) || !Mth.equal(vec3d.z, vec3d1.z);
            this.verticalCollision = vec3d.y != vec3d1.y;
            this.onGround = this.verticalCollision && vec3d.y < 0.0D;
            BlockPos blockposition = this.getOnPos();
            BlockState iblockdata = this.level.getType(blockposition);

            this.checkFallDamage(vec3d1.y, this.onGround, iblockdata, blockposition);
            Vec3 vec3d2 = this.getDeltaMovement();

            if (vec3d.x != vec3d1.x) {
                this.setDeltaMovement(0.0D, vec3d2.y, vec3d2.z);
            }

            if (vec3d.z != vec3d1.z) {
                this.setDeltaMovement(vec3d2.x, vec3d2.y, 0.0D);
            }

            Block block = iblockdata.getBlock();

            if (vec3d.y != vec3d1.y) {
                block.updateEntityAfterFallOn((BlockGetter) this.level, this);
            }

            // CraftBukkit start
            if (horizontalCollision && getBukkitEntity() instanceof Vehicle) {
                Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                org.bukkit.block.Block bl = this.level.getWorld().getBlockAt(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()));

                if (vec3d.x > vec3d1.x) {
                    bl = bl.getRelative(BlockFace.EAST);
                } else if (vec3d.x < vec3d1.x) {
                    bl = bl.getRelative(BlockFace.WEST);
                } else if (vec3d.z > vec3d1.z) {
                    bl = bl.getRelative(BlockFace.SOUTH);
                } else if (vec3d.z < vec3d1.z) {
                    bl = bl.getRelative(BlockFace.NORTH);
                }

                if (!bl.getType().isAir()) {
                    VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, bl);
                    level.getServerOH().getPluginManager().callEvent(event);
                }
            }
            // CraftBukkit end

            if (this.onGround && !this.isSteppingCarefully()) {
                block.stepOn(this.level, blockposition, this);
            }

            if (this.isMovementNoisy() && !this.isPassenger()) {
                double d0 = vec3d1.x;
                double d1 = vec3d1.y;
                double d2 = vec3d1.z;

                if (!block.is((Tag) BlockTags.CLIMBABLE)) {
                    d1 = 0.0D;
                }

                this.walkDist = (float) ((double) this.walkDist + (double) Mth.sqrt(getHorizontalDistanceSqr(vec3d1)) * 0.6D);
                this.moveDist = (float) ((double) this.moveDist + (double) Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 0.6D);
                if (this.moveDist > this.nextStep && !iblockdata.isAir()) {
                    this.nextStep = this.nextStep();
                    if (this.isInWater()) {
                        Entity entity = this.isVehicle() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;
                        float f = entity == this ? 0.35F : 0.4F;
                        Vec3 vec3d3 = entity.getDeltaMovement();
                        float f1 = Mth.sqrt(vec3d3.x * vec3d3.x * 0.20000000298023224D + vec3d3.y * vec3d3.y + vec3d3.z * vec3d3.z * 0.20000000298023224D) * f;

                        if (f1 > 1.0F) {
                            f1 = 1.0F;
                        }

                        this.playSwimSound(f1);
                    } else {
                        this.playStepSound(blockposition, iblockdata);
                    }
                } else if (this.moveDist > this.nextFlap && this.makeFlySound() && iblockdata.isAir()) {
                    this.nextFlap = this.playFlySound(this.moveDist);
                }
            }

            try {
                this.isTouchingLava = false;
                this.checkInsideBlocks();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Checking entity block collision");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being checked for collision");

                this.appendEntityCrashDetails(crashreportsystemdetails);
                throw new ReportedException(crashreport);
            }

            float f2 = this.getBlockSpeedFactor();

            this.setDeltaMovement(this.getDeltaMovement().multiply((double) f2, 1.0D, (double) f2));
            if (this.level.getBlockStatesIfLoaded(this.getBoundingBox().deflate(0.001D)).noneMatch((iblockdata1) -> {
                return iblockdata1.is((Tag) BlockTags.FIRE) || iblockdata1.is(Blocks.LAVA);
            }) && this.remainingFireTicks <= 0) {
                this.setRemainingFireTicks(-this.getFireImmuneTicks());
            }

            if (this.isInWaterRainOrBubble() && this.isOnFire()) {
                this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                this.setRemainingFireTicks(-this.getFireImmuneTicks());
            }

            this.level.getProfiler().pop();
        }
        org.bukkit.craftbukkit.SpigotTimings.entityMoveTimer.stopTiming(); // Spigot
    }

    protected BlockPos getOnPos() {
        int i = Mth.floor(this.position.x);
        int j = Mth.floor(this.position.y - 0.20000000298023224D);
        int k = Mth.floor(this.position.z);
        BlockPos blockposition = new BlockPos(i, j, k);

        if (this.level.getType(blockposition).isAir()) {
            BlockPos blockposition1 = blockposition.below();
            BlockState iblockdata = this.level.getType(blockposition1);
            Block block = iblockdata.getBlock();

            if (block.is((Tag) BlockTags.FENCES) || block.is((Tag) BlockTags.WALLS) || block instanceof FenceGateBlock) {
                return blockposition1;
            }
        }

        return blockposition;
    }

    protected float getBlockJumpFactor() {
        float f = this.level.getType(this.blockPosition()).getBlock().getJumpFactor();
        float f1 = this.level.getType(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();

        return (double) f == 1.0D ? f1 : f;
    }

    protected float getBlockSpeedFactor() {
        Block block = this.level.getType(this.blockPosition()).getBlock();
        float f = block.getSpeedFactor();

        return block != Blocks.WATER && block != Blocks.BUBBLE_COLUMN ? ((double) f == 1.0D ? this.level.getType(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f) : f;
    }

    protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return new BlockPos(this.position.x, this.getBoundingBox().minY - 0.5000001D, this.position.z);
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 vec3d, MoverType enummovetype) {
        return vec3d;
    }

    protected Vec3 limitPistonMovement(Vec3 vec3d) {
        if (vec3d.lengthSqr() <= 1.0E-7D) {
            return vec3d;
        } else {
            long i = this.level.getGameTime();

            if (i != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0D);
                this.pistonDeltasGameTime = i;
            }

            double d0;

            if (vec3d.x != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.X, vec3d.x);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(d0, 0.0D, 0.0D);
            } else if (vec3d.y != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.Y, vec3d.y);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(0.0D, d0, 0.0D);
            } else if (vec3d.z != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.Z, vec3d.z);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(0.0D, 0.0D, d0);
            } else {
                return Vec3.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis enumdirection_enumaxis, double d0) {
        int i = enumdirection_enumaxis.ordinal();
        double d1 = Mth.clamp(d0 + this.pistonDeltas[i], -0.51D, 0.51D);

        d0 = d1 - this.pistonDeltas[i];
        this.pistonDeltas[i] = d1;
        return d0;
    }

    private Vec3 collide(Vec3 vec3d) {
        AABB axisalignedbb = this.getBoundingBox();
        CollisionContext voxelshapecollision = CollisionContext.of(this);
        VoxelShape voxelshape = this.level.getWorldBorder().getCollisionShape();
        Stream<VoxelShape> stream = Shapes.joinIsNotEmpty(voxelshape, Shapes.create(axisalignedbb.deflate(1.0E-7D)), BooleanOp.AND) ? Stream.empty() : Stream.of(voxelshape);
        Stream<VoxelShape> stream1 = this.level.getEntityCollisions(this, axisalignedbb.expandTowards(vec3d), (entity) -> {
            return true;
        });
        RewindableStream<VoxelShape> streamaccumulator = new RewindableStream<>(Stream.concat(stream1, stream));
        Vec3 vec3d1 = vec3d.lengthSqr() == 0.0D ? vec3d : collideBoundingBoxHeuristically(this, vec3d, axisalignedbb, this.level, voxelshapecollision, streamaccumulator);
        boolean flag = vec3d.x != vec3d1.x;
        boolean flag1 = vec3d.y != vec3d1.y;
        boolean flag2 = vec3d.z != vec3d1.z;
        boolean flag3 = this.onGround || flag1 && vec3d.y < 0.0D;

        if (this.maxUpStep > 0.0F && flag3 && (flag || flag2)) {
            Vec3 vec3d2 = collideBoundingBoxHeuristically(this, new Vec3(vec3d.x, (double) this.maxUpStep, vec3d.z), axisalignedbb, this.level, voxelshapecollision, streamaccumulator);
            Vec3 vec3d3 = collideBoundingBoxHeuristically(this, new Vec3(0.0D, (double) this.maxUpStep, 0.0D), axisalignedbb.expandTowards(vec3d.x, 0.0D, vec3d.z), this.level, voxelshapecollision, streamaccumulator);

            if (vec3d3.y < (double) this.maxUpStep) {
                Vec3 vec3d4 = collideBoundingBoxHeuristically(this, new Vec3(vec3d.x, 0.0D, vec3d.z), axisalignedbb.move(vec3d3), this.level, voxelshapecollision, streamaccumulator).add(vec3d3);

                if (getHorizontalDistanceSqr(vec3d4) > getHorizontalDistanceSqr(vec3d2)) {
                    vec3d2 = vec3d4;
                }
            }

            if (getHorizontalDistanceSqr(vec3d2) > getHorizontalDistanceSqr(vec3d1)) {
                return vec3d2.add(collideBoundingBoxHeuristically(this, new Vec3(0.0D, -vec3d2.y + vec3d.y, 0.0D), axisalignedbb.move(vec3d2), this.level, voxelshapecollision, streamaccumulator));
            }
        }

        return vec3d1;
    }

    public static double getHorizontalDistanceSqr(Vec3 vec3d) {
        return vec3d.x * vec3d.x + vec3d.z * vec3d.z;
    }

    public static Vec3 collideBoundingBoxHeuristically(@Nullable Entity entity, Vec3 vec3d, AABB axisalignedbb, Level world, CollisionContext voxelshapecollision, RewindableStream<VoxelShape> streamaccumulator) {
        boolean flag = vec3d.x == 0.0D;
        boolean flag1 = vec3d.y == 0.0D;
        boolean flag2 = vec3d.z == 0.0D;

        if ((!flag || !flag1) && (!flag || !flag2) && (!flag1 || !flag2)) {
            RewindableStream<VoxelShape> streamaccumulator1 = new RewindableStream<>(Stream.concat(streamaccumulator.getStream(), world.getBlockCollisions(entity, axisalignedbb.expandTowards(vec3d))));

            return collideBoundingBoxLegacy(vec3d, axisalignedbb, streamaccumulator1);
        } else {
            return collideBoundingBox(vec3d, axisalignedbb, world, voxelshapecollision, streamaccumulator);
        }
    }

    public static Vec3 collideBoundingBoxLegacy(Vec3 vec3d, AABB axisalignedbb, RewindableStream<VoxelShape> streamaccumulator) {
        double d0 = vec3d.x;
        double d1 = vec3d.y;
        double d2 = vec3d.z;

        if (d1 != 0.0D) {
            d1 = Shapes.collide(Direction.Axis.Y, axisalignedbb, streamaccumulator.getStream(), d1);
            if (d1 != 0.0D) {
                axisalignedbb = axisalignedbb.move(0.0D, d1, 0.0D);
            }
        }

        boolean flag = Math.abs(d0) < Math.abs(d2);

        if (flag && d2 != 0.0D) {
            d2 = Shapes.collide(Direction.Axis.Z, axisalignedbb, streamaccumulator.getStream(), d2);
            if (d2 != 0.0D) {
                axisalignedbb = axisalignedbb.move(0.0D, 0.0D, d2);
            }
        }

        if (d0 != 0.0D) {
            d0 = Shapes.collide(Direction.Axis.X, axisalignedbb, streamaccumulator.getStream(), d0);
            if (!flag && d0 != 0.0D) {
                axisalignedbb = axisalignedbb.move(d0, 0.0D, 0.0D);
            }
        }

        if (!flag && d2 != 0.0D) {
            d2 = Shapes.collide(Direction.Axis.Z, axisalignedbb, streamaccumulator.getStream(), d2);
        }

        return new Vec3(d0, d1, d2);
    }

    public static Vec3 collideBoundingBox(Vec3 vec3d, AABB axisalignedbb, LevelReader iworldreader, CollisionContext voxelshapecollision, RewindableStream<VoxelShape> streamaccumulator) {
        double d0 = vec3d.x;
        double d1 = vec3d.y;
        double d2 = vec3d.z;

        if (d1 != 0.0D) {
            d1 = Shapes.collide(Direction.Axis.Y, axisalignedbb, iworldreader, d1, voxelshapecollision, streamaccumulator.getStream());
            if (d1 != 0.0D) {
                axisalignedbb = axisalignedbb.move(0.0D, d1, 0.0D);
            }
        }

        boolean flag = Math.abs(d0) < Math.abs(d2);

        if (flag && d2 != 0.0D) {
            d2 = Shapes.collide(Direction.Axis.Z, axisalignedbb, iworldreader, d2, voxelshapecollision, streamaccumulator.getStream());
            if (d2 != 0.0D) {
                axisalignedbb = axisalignedbb.move(0.0D, 0.0D, d2);
            }
        }

        if (d0 != 0.0D) {
            d0 = Shapes.collide(Direction.Axis.X, axisalignedbb, iworldreader, d0, voxelshapecollision, streamaccumulator.getStream());
            if (!flag && d0 != 0.0D) {
                axisalignedbb = axisalignedbb.move(d0, 0.0D, 0.0D);
            }
        }

        if (!flag && d2 != 0.0D) {
            d2 = Shapes.collide(Direction.Axis.Z, axisalignedbb, iworldreader, d2, voxelshapecollision, streamaccumulator.getStream());
        }

        return new Vec3(d0, d1, d2);
    }

    protected float nextStep() {
        return (float) ((int) this.moveDist + 1);
    }

    public void setLocationFromBoundingbox() {
        AABB axisalignedbb = this.getBoundingBox();

        this.setPosRaw((axisalignedbb.minX + axisalignedbb.maxX) / 2.0D, axisalignedbb.minY, (axisalignedbb.minZ + axisalignedbb.maxZ) / 2.0D);
        if (valid) ((ServerLevel) level).updateChunkPos(this); // CraftBukkit
    }

    protected SoundEvent getSoundSwim() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSoundSplash() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSoundSplashHighSpeed() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected void checkInsideBlocks() {
        AABB axisalignedbb = this.getBoundingBox();
        BlockPos blockposition = new BlockPos(axisalignedbb.minX + 0.001D, axisalignedbb.minY + 0.001D, axisalignedbb.minZ + 0.001D);
        BlockPos blockposition1 = new BlockPos(axisalignedbb.maxX - 0.001D, axisalignedbb.maxY - 0.001D, axisalignedbb.maxZ - 0.001D);
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        if (this.level.hasChunksAt(blockposition, blockposition1)) {
            for (int i = blockposition.getX(); i <= blockposition1.getX(); ++i) {
                for (int j = blockposition.getY(); j <= blockposition1.getY(); ++j) {
                    for (int k = blockposition.getZ(); k <= blockposition1.getZ(); ++k) {
                        blockposition_mutableblockposition.d(i, j, k);
                        BlockState iblockdata = this.level.getType(blockposition_mutableblockposition);

                        try {
                            iblockdata.entityInside(this.level, blockposition_mutableblockposition, this);
                            this.onInsideBlock(iblockdata);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being collided with");

                            CrashReportCategory.populateBlockDetails(crashreportsystemdetails, blockposition_mutableblockposition, iblockdata);
                            throw new ReportedException(crashreport);
                        }
                    }
                }
            }
        }

    }

    protected void onInsideBlock(BlockState iblockdata) {}

    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        if (!iblockdata.getMaterial().isLiquid()) {
            BlockState iblockdata1 = this.level.getType(blockposition.above());
            SoundType soundeffecttype = iblockdata1.is(Blocks.SNOW) ? iblockdata1.getStepSound() : iblockdata.getStepSound();

            this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
        }
    }

    protected void playSwimSound(float f) {
        this.playSound(this.getSoundSwim(), f, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected float playFlySound(float f) {
        return 0.0F;
    }

    protected boolean makeFlySound() {
        return false;
    }

    public void playSound(SoundEvent soundeffect, float f, float f1) {
        if (!this.isSilent()) {
            this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), f, f1);
        }

    }

    public boolean isSilent() {
        return (Boolean) this.entityData.get(Entity.DATA_SILENT);
    }

    public void setSilent(boolean flag) {
        this.entityData.set(Entity.DATA_SILENT, flag);
    }

    public boolean isNoGravity() {
        return (Boolean) this.entityData.get(Entity.DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean flag) {
        this.entityData.set(Entity.DATA_NO_GRAVITY, flag);
    }

    protected boolean isMovementNoisy() {
        return true;
    }

    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {
        if (flag) {
            if (this.fallDistance > 0.0F) {
                iblockdata.getBlock().fallOn(this.level, blockposition, this, this.fallDistance);
            }

            this.fallDistance = 0.0F;
        } else if (d0 < 0.0D) {
            this.fallDistance = (float) ((double) this.fallDistance - d0);
        }

    }

    @Nullable
    public AABB getCollideBox() {
        return null;
    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(float f, float f1) {
        if (this.isVehicle()) {
            Iterator iterator = this.getPassengers().iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                entity.causeFallDamage(f, f1);
            }
        }

        return false;
    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    private boolean isInRain() {
        BlockPos blockposition = this.blockPosition();

        return this.level.isRainingAt(blockposition) || this.level.isRainingAt(blockposition.offset(0.0D, (double) this.dimensions.height, 0.0D));
    }

    private boolean isInBubbleColumn() {
        return this.level.getType(this.blockPosition()).is(Blocks.BUBBLE_COLUMN);
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInWaterRainOrBubble() {
        return this.isInWater() || this.isInRain() || this.isInBubbleColumn();
    }

    public boolean isInWaterOrBubble() {
        return this.isInWater() || this.isInBubbleColumn();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger());
        }

    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        if (this.isInWater()) {
            return true;
        } else {
            double d0 = this.level.dimensionType().ultraWarm() ? 0.007D : 0.0023333333333333335D;

            return this.updateFluidHeightAndDoFluidPushing((Tag) FluidTags.LAVA, d0);
        }
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        if (this.getVehicle() instanceof Boat) {
            this.wasTouchingWater = false;
        } else if (this.updateFluidHeightAndDoFluidPushing((Tag) FluidTags.WATER, 0.014D)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.fallDistance = 0.0F;
            this.wasTouchingWater = true;
            this.clearFire();
        } else {
            this.wasTouchingWater = false;
        }

    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid((Tag) FluidTags.WATER);
        this.fluidOnEyes = null;
        double d0 = this.getEyeY() - 0.1111111119389534D;
        Vec3 vec3d = new Vec3(this.getX(), d0, this.getZ());
        Entity entity = this.getVehicle();

        if (entity instanceof Boat) {
            Boat entityboat = (Boat) entity;

            if (!entityboat.isUnderWater() && entityboat.getBoundingBox().contains(vec3d)) {
                return;
            }
        }

        BlockPos blockposition = new BlockPos(vec3d);
        FluidState fluid = this.level.getFluidState(blockposition);
        Iterator iterator = FluidTags.getWrappers().iterator();

        Tag tag;

        do {
            if (!iterator.hasNext()) {
                return;
            }

            tag = (Tag) iterator.next();
        } while (!fluid.is(tag));

        double d1 = (double) ((float) blockposition.getY() + fluid.getHeight(this.level, blockposition));

        if (d1 > d0) {
            this.fluidOnEyes = tag;
        }

    }

    protected void doWaterSplashEffect() {
        Entity entity = this.isVehicle() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 vec3d = entity.getDeltaMovement();
        float f1 = Mth.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f;

        if (f1 > 1.0F) {
            f1 = 1.0F;
        }

        if ((double) f1 < 0.25D) {
            this.playSound(this.getSoundSplash(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSoundSplashHighSpeed(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f2 = (float) Mth.floor(this.getY());

        double d0;
        double d1;
        int i;

        for (i = 0; (float) i < 1.0F + this.dimensions.width * 20.0F; ++i) {
            d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            this.level.addParticle(ParticleTypes.BUBBLE, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y - this.random.nextDouble() * 0.20000000298023224D, vec3d.z);
        }

        for (i = 0; (float) i < 1.0F + this.dimensions.width * 20.0F; ++i) {
            d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            this.level.addParticle(ParticleTypes.SPLASH, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y, vec3d.z);
        }

    }

    protected BlockState getBlockStateOn() {
        return this.level.getType(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        int i = Mth.floor(this.getX());
        int j = Mth.floor(this.getY() - 0.20000000298023224D);
        int k = Mth.floor(this.getZ());
        BlockPos blockposition = new BlockPos(i, j, k);
        BlockState iblockdata = this.level.getType(blockposition);

        if (iblockdata.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 vec3d = this.getDeltaMovement();

            this.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width, this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width, vec3d.x * -4.0D, 1.5D, vec3d.z * -4.0D);
        }

    }

    public boolean isEyeInFluid(Tag<Fluid> tag) {
        return this.fluidOnEyes == tag;
    }

    public void setInLava() {
        this.isTouchingLava = true;
    }

    public boolean isInLava() {
        return this.isTouchingLava;
    }

    public void moveRelative(float f, Vec3 vec3d) {
        Vec3 vec3d1 = getInputVector(vec3d, f, this.yRot);

        this.setDeltaMovement(this.getDeltaMovement().add(vec3d1));
    }

    private static Vec3 getInputVector(Vec3 vec3d, float f, float f1) {
        double d0 = vec3d.lengthSqr();

        if (d0 < 1.0E-7D) {
            return Vec3.ZERO;
        } else {
            Vec3 vec3d1 = (d0 > 1.0D ? vec3d.normalize() : vec3d).scale((double) f);
            float f2 = Mth.sin(f1 * 0.017453292F);
            float f3 = Mth.cos(f1 * 0.017453292F);

            return new Vec3(vec3d1.x * (double) f3 - vec3d1.z * (double) f2, vec3d1.y, vec3d1.z * (double) f3 + vec3d1.x * (double) f2);
        }
    }

    public float getBrightness() {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition(this.getX(), 0.0D, this.getZ());

        if (this.level.hasChunkAt(blockposition_mutableblockposition)) {
            blockposition_mutableblockposition.setY(Mth.floor(this.getEyeY()));
            return this.level.getBrightness(blockposition_mutableblockposition);
        } else {
            return 0.0F;
        }
    }

    public void setLevel(Level world) {
        // CraftBukkit start
        if (world == null) {
            remove();
            this.level = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
            return;
        }
        // CraftBukkit end
        this.level = world;
    }

    public void absMoveTo(double d0, double d1, double d2, float f, float f1) {
        double d3 = Mth.clamp(d0, -3.0E7D, 3.0E7D);
        double d4 = Mth.clamp(d2, -3.0E7D, 3.0E7D);

        this.xo = d3;
        this.yo = d1;
        this.zo = d4;
        this.setPos(d3, d1, d4);
        this.yRot = f % 360.0F;
        this.xRot = Mth.clamp(f1, -90.0F, 90.0F) % 360.0F;
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;
        level.getChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4); // CraftBukkit
    }

    public void moveTo(Vec3 vec3d) {
        this.moveTo(vec3d.x, vec3d.y, vec3d.z);
    }

    public void moveTo(double d0, double d1, double d2) {
        this.moveTo(d0, d1, d2, this.yRot, this.xRot);
    }

    public void moveTo(BlockPos blockposition, float f, float f1) {
        this.moveTo((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, f, f1);
    }

    public void moveTo(double d0, double d1, double d2, float f, float f1) {
        this.setPosAndOldPos(d0, d1, d2);
        this.yRot = f;
        this.xRot = f1;
        this.reapplyPosition();
    }

    public void setPosAndOldPos(double d0, double d1, double d2) {
        this.setPosRaw(d0, d1, d2);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
        this.xOld = d0;
        this.yOld = d1;
        this.zOld = d2;
    }

    public float distanceTo(Entity entity) {
        float f = (float) (this.getX() - entity.getX());
        float f1 = (float) (this.getY() - entity.getY());
        float f2 = (float) (this.getZ() - entity.getZ());

        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double distanceToSqr(double d0, double d1, double d2) {
        double d3 = this.getX() - d0;
        double d4 = this.getY() - d1;
        double d5 = this.getZ() - d2;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3 vec3d) {
        double d0 = this.getX() - vec3d.x;
        double d1 = this.getY() - vec3d.y;
        double d2 = this.getZ() - vec3d.z;

        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public void playerTouch(Player entityhuman) {}

    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                double d0 = entity.getX() - this.getX();
                double d1 = entity.getZ() - this.getZ();
                double d2 = Mth.absMax(d0, d1);

                if (d2 >= 0.009999999776482582D) {
                    d2 = (double) Mth.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;

                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= 0.05000000074505806D;
                    d1 *= 0.05000000074505806D;
                    d0 *= (double) (1.0F - this.pushthrough);
                    d1 *= (double) (1.0F - this.pushthrough);
                    if (!this.isVehicle()) {
                        this.push(-d0, 0.0D, -d1);
                    }

                    if (!entity.isVehicle()) {
                        entity.push(d0, 0.0D, d1);
                    }
                }

            }
        }
    }

    public void push(double d0, double d1, double d2) {
        this.setDeltaMovement(this.getDeltaMovement().add(d0, d1, d2));
        this.hasImpulse = true;
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            this.markHurt();
            return false;
        }
    }

    public final Vec3 getViewVector(float f) {
        return this.calculateViewVector(this.getViewXRot(f), this.getViewYRot(f));
    }

    public float getViewXRot(float f) {
        return f == 1.0F ? this.xRot : Mth.lerp(f, this.xRotO, this.xRot);
    }

    public float getViewYRot(float f) {
        return f == 1.0F ? this.yRot : Mth.lerp(f, this.yRotO, this.yRot);
    }

    protected final Vec3 calculateViewVector(float f, float f1) {
        float f2 = f * 0.017453292F;
        float f3 = -f1 * 0.017453292F;
        float f4 = Mth.cos(f3);
        float f5 = Mth.sin(f3);
        float f6 = Mth.cos(f2);
        float f7 = Mth.sin(f2);

        return new Vec3((double) (f5 * f6), (double) (-f7), (double) (f4 * f6));
    }

    public final Vec3 getUpVector(float f) {
        return this.calculateUpVector(this.getViewXRot(f), this.getViewYRot(f));
    }

    protected final Vec3 calculateUpVector(float f, float f1) {
        return this.calculateViewVector(f - 90.0F, f1);
    }

    public final Vec3 getEyePosition(float f) {
        if (f == 1.0F) {
            return new Vec3(this.getX(), this.getEyeY(), this.getZ());
        } else {
            double d0 = Mth.lerp((double) f, this.xo, this.getX());
            double d1 = Mth.lerp((double) f, this.yo, this.getY()) + (double) this.getEyeHeight();
            double d2 = Mth.lerp((double) f, this.zo, this.getZ());

            return new Vec3(d0, d1, d2);
        }
    }

    public HitResult pick(double d0, float f, boolean flag) {
        Vec3 vec3d = this.getEyePosition(f);
        Vec3 vec3d1 = this.getViewVector(f);
        Vec3 vec3d2 = vec3d.add(vec3d1.x * d0, vec3d1.y * d0, vec3d1.z * d0);

        return this.level.clip(new ClipContext(vec3d, vec3d2, ClipContext.Block.OUTLINE, flag ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    // CraftBukkit start - collidable API
    public boolean canCollideWith(Entity entity) {
        return isPushable();
    }
    // CraftBukkit end

    public void awardKillScore(Entity entity, int i, DamageSource damagesource) {
        if (entity instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer) entity, this, damagesource);
        }

    }

    public boolean saveAsPassenger(CompoundTag nbttagcompound) {
        String s = this.getEncodeId();

        if (this.persist && !this.removed && s != null) { // CraftBukkit - persist flag
            nbttagcompound.putString("id", s);
            this.saveWithoutId(nbttagcompound);
            return true;
        } else {
            return false;
        }
    }

    public boolean save(CompoundTag nbttagcompound) {
        return this.isPassenger() ? false : this.saveAsPassenger(nbttagcompound);
    }

    public CompoundTag saveWithoutId(CompoundTag nbttagcompound) {
        try {
            if (this.vehicle != null) {
                nbttagcompound.put("Pos", this.newDoubleList(this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ()));
            } else {
                nbttagcompound.put("Pos", this.newDoubleList(this.getX(), this.getY(), this.getZ()));
            }

            Vec3 vec3d = this.getDeltaMovement();

            nbttagcompound.put("Motion", this.newDoubleList(vec3d.x, vec3d.y, vec3d.z));

            // CraftBukkit start - Checking for NaN pitch/yaw and resetting to zero
            // TODO: make sure this is the best way to address this.
            if (Float.isNaN(this.yRot)) {
                this.yRot = 0;
            }

            if (Float.isNaN(this.xRot)) {
                this.xRot = 0;
            }
            // CraftBukkit end

            nbttagcompound.put("Rotation", this.newFloatList(this.yRot, this.xRot));
            nbttagcompound.putFloat("FallDistance", this.fallDistance);
            nbttagcompound.putShort("Fire", (short) this.remainingFireTicks);
            nbttagcompound.putShort("Air", (short) this.getAirSupply());
            nbttagcompound.putBoolean("OnGround", this.onGround);
            nbttagcompound.putBoolean("Invulnerable", this.invulnerable);
            nbttagcompound.putInt("PortalCooldown", this.changingDimensionDelay);
            nbttagcompound.putUUID("UUID", this.getUUID());
            // CraftBukkit start
            // PAIL: Check above UUID reads 1.8 properly, ie: UUIDMost / UUIDLeast
            nbttagcompound.putLong("WorldUUIDLeast", ((ServerLevel) this.level).getWorld().getUID().getLeastSignificantBits());
            nbttagcompound.putLong("WorldUUIDMost", ((ServerLevel) this.level).getWorld().getUID().getMostSignificantBits());
            nbttagcompound.putInt("Bukkit.updateLevel", CURRENT_LEVEL);
            nbttagcompound.putInt("Spigot.ticksLived", this.tickCount);
            // CraftBukkit end
            Component ichatbasecomponent = this.getCustomName();

            if (ichatbasecomponent != null) {
                nbttagcompound.putString("CustomName", Component.ChatSerializer.a(ichatbasecomponent));
            }

            if (this.isCustomNameVisible()) {
                nbttagcompound.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                nbttagcompound.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                nbttagcompound.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.glowing) {
                nbttagcompound.putBoolean("Glowing", this.glowing);
            }

            ListTag nbttaglist;
            Iterator iterator;

            if (!this.tags.isEmpty()) {
                nbttaglist = new ListTag();
                iterator = this.tags.iterator();

                while (iterator.hasNext()) {
                    String s = (String) iterator.next();

                    nbttaglist.add(StringTag.valueOf(s));
                }

                nbttagcompound.put("Tags", nbttaglist);
            }

            this.addAdditionalSaveData(nbttagcompound);
            if (this.isVehicle()) {
                nbttaglist = new ListTag();
                iterator = this.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();
                    CompoundTag nbttagcompound1 = new CompoundTag();

                    if (entity.saveAsPassenger(nbttagcompound1)) {
                        nbttaglist.add(nbttagcompound1);
                    }
                }

                if (!nbttaglist.isEmpty()) {
                    nbttagcompound.put("Passengers", nbttaglist);
                }
            }

            // CraftBukkit start - stores eventually existing bukkit values
            if (this.bukkitEntity != null) {
                this.bukkitEntity.storeBukkitValues(nbttagcompound);
            }
            // CraftBukkit end
            return nbttagcompound;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being saved");

            this.appendEntityCrashDetails(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    public void load(CompoundTag nbttagcompound) {
        try {
            ListTag nbttaglist = nbttagcompound.getList("Pos", 6);
            ListTag nbttaglist1 = nbttagcompound.getList("Motion", 6);
            ListTag nbttaglist2 = nbttagcompound.getList("Rotation", 5);
            double d0 = nbttaglist1.getDouble(0);
            double d1 = nbttaglist1.getDouble(1);
            double d2 = nbttaglist1.getDouble(2);

            this.setDeltaMovement(Math.abs(d0) > 10.0D ? 0.0D : d0, Math.abs(d1) > 10.0D ? 0.0D : d1, Math.abs(d2) > 10.0D ? 0.0D : d2);
            this.setPosAndOldPos(nbttaglist.getDouble(0), nbttaglist.getDouble(1), nbttaglist.getDouble(2));
            this.yRot = nbttaglist2.getFloat(0);
            this.xRot = nbttaglist2.getFloat(1);
            this.yRotO = this.yRot;
            this.xRotO = this.xRot;
            this.setYHeadRot(this.yRot);
            this.setYBodyRot(this.yRot);
            this.fallDistance = nbttagcompound.getFloat("FallDistance");
            this.remainingFireTicks = nbttagcompound.getShort("Fire");
            this.setAirSupply(nbttagcompound.getShort("Air"));
            this.onGround = nbttagcompound.getBoolean("OnGround");
            this.invulnerable = nbttagcompound.getBoolean("Invulnerable");
            this.changingDimensionDelay = nbttagcompound.getInt("PortalCooldown");
            if (nbttagcompound.hasUUID("UUID")) {
                this.uuid = nbttagcompound.getUUID("UUID");
                this.stringUUID = this.uuid.toString();
            }

            if (Double.isFinite(this.getX()) && Double.isFinite(this.getY()) && Double.isFinite(this.getZ())) {
                if (Double.isFinite((double) this.yRot) && Double.isFinite((double) this.xRot)) {
                    this.reapplyPosition();
                    this.setRot(this.yRot, this.xRot);
                    if (nbttagcompound.contains("CustomName", 8)) {
                        String s = nbttagcompound.getString("CustomName");

                        try {
                            this.setCustomName(Component.ChatSerializer.a(s));
                        } catch (Exception exception) {
                            Entity.LOGGER.warn("Failed to parse entity custom name {}", s, exception);
                        }
                    }

                    this.setCustomNameVisible(nbttagcompound.getBoolean("CustomNameVisible"));
                    this.setSilent(nbttagcompound.getBoolean("Silent"));
                    this.setNoGravity(nbttagcompound.getBoolean("NoGravity"));
                    this.setGlowing(nbttagcompound.getBoolean("Glowing"));
                    if (nbttagcompound.contains("Tags", 9)) {
                        this.tags.clear();
                        ListTag nbttaglist3 = nbttagcompound.getList("Tags", 8);
                        int i = Math.min(nbttaglist3.size(), 1024);

                        for (int j = 0; j < i; ++j) {
                            this.tags.add(nbttaglist3.getString(j));
                        }
                    }

                    this.readAdditionalSaveData(nbttagcompound);
                    if (this.repositionEntityAfterLoad()) {
                        this.reapplyPosition();
                    }

                } else {
                    throw new IllegalStateException("Entity has invalid rotation");
                }
            } else {
                throw new IllegalStateException("Entity has invalid position");
            }

            // CraftBukkit start
            if (this instanceof net.minecraft.world.entity.LivingEntity) {
                net.minecraft.world.entity.LivingEntity entity = (net.minecraft.world.entity.LivingEntity) this;

                this.tickCount = nbttagcompound.getInt("Spigot.ticksLived");

                // Reset the persistence for tamed animals
                if (entity instanceof TamableAnimal && !isLevelAtLeast(nbttagcompound, 2) && !nbttagcompound.getBoolean("PersistenceRequired")) {
                    Mob entityinsentient = (Mob) entity;
                    entityinsentient.persistenceRequired = !entityinsentient.removeWhenFarAway(0);
                }
            }
            // CraftBukkit end

            // CraftBukkit start - Reset world
            if (this instanceof ServerPlayer) {
                Server server = Bukkit.getServer();
                org.bukkit.World bworld = null;

                // TODO: Remove World related checks, replaced with WorldUID
                String worldName = nbttagcompound.getString("world");

                if (nbttagcompound.contains("WorldUUIDMost") && nbttagcompound.contains("WorldUUIDLeast")) {
                    UUID uid = new UUID(nbttagcompound.getLong("WorldUUIDMost"), nbttagcompound.getLong("WorldUUIDLeast"));
                    bworld = server.getWorld(uid);
                } else {
                    bworld = server.getWorld(worldName);
                }

                if (bworld == null) {
                    bworld = ((org.bukkit.craftbukkit.CraftServer) server).getServer().getWorldServer(Level.OVERWORLD).getWorld();
                }

                setLevel(bworld == null ? null : ((CraftWorld) bworld).getHandle());
            }
            this.getBukkitEntity().readBukkitValues(nbttagcompound);
            // CraftBukkit end

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being loaded");

            this.appendEntityCrashDetails(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    public final String getEncodeId() {
        EntityType<?> entitytypes = this.getType();
        ResourceLocation minecraftkey = EntityType.getKey(entitytypes);

        return entitytypes.canSerialize() && minecraftkey != null ? minecraftkey.toString() : null;
    }

    protected abstract void readAdditionalSaveData(CompoundTag nbttagcompound);

    protected abstract void addAdditionalSaveData(CompoundTag nbttagcompound);

    protected ListTag newDoubleList(double... adouble) {
        ListTag nbttaglist = new ListTag();
        double[] adouble1 = adouble;
        int i = adouble.length;

        for (int j = 0; j < i; ++j) {
            double d0 = adouble1[j];

            nbttaglist.add(DoubleTag.valueOf(d0));
        }

        return nbttaglist;
    }

    protected ListTag newFloatList(float... afloat) {
        ListTag nbttaglist = new ListTag();
        float[] afloat1 = afloat;
        int i = afloat.length;

        for (int j = 0; j < i; ++j) {
            float f = afloat1[j];

            nbttaglist.add(FloatTag.valueOf(f));
        }

        return nbttaglist;
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike imaterial) {
        return this.spawnAtLocation(imaterial, 0);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike imaterial, int i) {
        return this.spawnAtLocation(new ItemStack(imaterial), (float) i);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack itemstack) {
        return this.spawnAtLocation(itemstack, 0.0F);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack itemstack, float f) {
        if (itemstack.isEmpty()) {
            return null;
        } else if (this.level.isClientSide) {
            return null;
        } else {
            // CraftBukkit start - Capture drops for death event
            if (this instanceof net.minecraft.world.entity.LivingEntity && !((net.minecraft.world.entity.LivingEntity) this).forceDrops) {
                ((net.minecraft.world.entity.LivingEntity) this).drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack));
                return null;
            }
            // CraftBukkit end
            ItemEntity entityitem = new ItemEntity(this.level, this.getX(), this.getY() + (double) f, this.getZ(), itemstack);

            entityitem.setDefaultPickUpDelay();
            // CraftBukkit start
            EntityDropItemEvent event = new EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }
            // CraftBukkit end
            this.level.addFreshEntity(entityitem);
            return entityitem;
        }
    }

    public boolean isAlive() {
        return !this.removed;
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        } else {
            float f = 0.1F;
            float f1 = this.dimensions.width * 0.8F;
            AABB axisalignedbb = AABB.ofSize((double) f1, 0.10000000149011612D, (double) f1).move(this.getX(), this.getEyeY(), this.getZ());

            return this.level.getBlockCollisions(this, axisalignedbb, (iblockdata, blockposition) -> {
                return iblockdata.isSuffocating(this.level, blockposition);
            }).findAny().isPresent();
        }
    }

    public InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        return InteractionResult.PASS;
    }

    @Nullable
    public AABB getCollideAgainstBox(Entity entity) {
        return null;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public void positionRider(Entity entity) {
        this.positionRider(entity, Entity::setPos);
    }

    private void positionRider(Entity entity, Entity.MoveFunction entity_a) {
        if (this.hasPassenger(entity)) {
            double d0 = this.getY() + this.getPassengersRidingOffset() + entity.getMyRidingOffset();

            entity_a.accept(entity, this.getX(), d0, this.getZ());
        }
    }

    public double getMyRidingOffset() {
        return 0.0D;
    }

    public double getPassengersRidingOffset() {
        return (double) this.dimensions.height * 0.75D;
    }

    public boolean startRiding(Entity entity) {
        return this.startRiding(entity, false);
    }

    public boolean startRiding(Entity entity, boolean flag) {
        for (Entity entity1 = entity; entity1.vehicle != null; entity1 = entity1.vehicle) {
            if (entity1.vehicle == this) {
                return false;
            }
        }

        if (!flag && (!this.canRide(entity) || !entity.canAddPassenger(this))) {
            return false;
        } else {
            if (this.isPassenger()) {
                this.stopRiding();
            }

            this.setPose(net.minecraft.world.entity.Pose.STANDING);
            this.vehicle = entity;
            if (!this.vehicle.addPassenger(this)) this.vehicle = null; // CraftBukkit
            return true;
        }
    }

    protected boolean canRide(Entity entity) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    protected boolean canEnterPose(net.minecraft.world.entity.Pose entitypose) {
        return this.level.noCollision(this, this.getBoundingBoxForPose(entitypose).deflate(1.0E-7D));
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; --i) {
            ((Entity) this.passengers.get(i)).stopRiding();
        }

    }

    public void removeVehicle() {
        if (this.vehicle != null) {
            Entity entity = this.vehicle;

            this.vehicle = null;
            if (!entity.removePassenger(this)) this.vehicle = entity; // CraftBukkit
        }

    }

    public void stopRiding() {
        this.removeVehicle();
    }

    protected boolean addPassenger(Entity entity) { // CraftBukkit
        if (entity.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            // CraftBukkit start
            com.google.common.base.Preconditions.checkState(!entity.passengers.contains(this), "Circular entity riding! %s %s", this, entity);

            CraftEntity craft = (CraftEntity) entity.getBukkitEntity().getVehicle();
            Entity orig = craft == null ? null : craft.getHandle();
            if (getBukkitEntity() instanceof Vehicle && entity.getBukkitEntity() instanceof LivingEntity) {
                VehicleEnterEvent event = new VehicleEnterEvent(
                        (Vehicle) getBukkitEntity(),
                         entity.getBukkitEntity()
                );
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                CraftEntity craftn = (CraftEntity) entity.getBukkitEntity().getVehicle();
                Entity n = craftn == null ? null : craftn.getHandle();
                if (event.isCancelled() || n != orig) {
                    return false;
                }
            }
            // CraftBukkit end
            // Spigot start
            org.spigotmc.event.entity.EntityMountEvent event = new org.spigotmc.event.entity.EntityMountEvent(entity.getBukkitEntity(), this.getBukkitEntity());
            // Suppress during worldgen
            if (this.valid) {
                Bukkit.getPluginManager().callEvent(event);
            }
            if (event.isCancelled()) {
                return false;
            }
            // Spigot end
            if (!this.level.isClientSide && entity instanceof Player && !(this.getControllingPassenger() instanceof Player)) {
                this.passengers.add(0, entity);
            } else {
                this.passengers.add(entity);
            }

        }
        return true; // CraftBukkit
    }

    protected boolean removePassenger(Entity entity) { // CraftBukkit
        if (entity.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            // CraftBukkit start
            CraftEntity craft = (CraftEntity) entity.getBukkitEntity().getVehicle();
            Entity orig = craft == null ? null : craft.getHandle();
            if (getBukkitEntity() instanceof Vehicle && entity.getBukkitEntity() instanceof LivingEntity) {
                VehicleExitEvent event = new VehicleExitEvent(
                        (Vehicle) getBukkitEntity(),
                        (LivingEntity) entity.getBukkitEntity()
                );
                Bukkit.getPluginManager().callEvent(event);
                CraftEntity craftn = (CraftEntity) entity.getBukkitEntity().getVehicle();
                Entity n = craftn == null ? null : craftn.getHandle();
                if (event.isCancelled() || n != orig) {
                    return false;
                }
            }
            // CraftBukkit end
            // Spigot start
            org.spigotmc.event.entity.EntityDismountEvent event = new org.spigotmc.event.entity.EntityDismountEvent(entity.getBukkitEntity(), this.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }
            // Spigot end
            this.passengers.remove(entity);
            entity.boardingCooldown = 60;
        }
        return true; // CraftBukkit
    }

    protected boolean canAddPassenger(Entity entity) {
        return this.getPassengers().size() < 1;
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.xRot, this.yRot);
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.xRot, this.yRot);
    }

    public void handleInsidePortal(BlockPos blockposition) {
        if (this.changingDimensionDelay > 0) {
            this.changingDimensionDelay = this.getDimensionChangingDelay();
        } else {
            if (!this.level.isClientSide && !blockposition.equals(this.portalEntranceBlock)) {
                this.portalEntranceBlock = new BlockPos(blockposition);
                NetherPortalBlock blockportal = (NetherPortalBlock) Blocks.NETHER_PORTAL;
                BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = NetherPortalBlock.getPortalShape((LevelAccessor) this.level, this.portalEntranceBlock);
                double d0 = shapedetector_shapedetectorcollection.getForwards().getAxis() == Direction.Axis.X ? (double) shapedetector_shapedetectorcollection.getFrontTopLeft().getZ() : (double) shapedetector_shapedetectorcollection.getFrontTopLeft().getX();
                double d1 = Mth.clamp(Math.abs(Mth.inverseLerp((shapedetector_shapedetectorcollection.getForwards().getAxis() == Direction.Axis.X ? this.getZ() : this.getX()) - (double) (shapedetector_shapedetectorcollection.getForwards().getClockWise().getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1 : 0), d0, d0 - (double) shapedetector_shapedetectorcollection.getWidth())), 0.0D, 1.0D);
                double d2 = Mth.clamp(Mth.inverseLerp(this.getY() - 1.0D, (double) shapedetector_shapedetectorcollection.getFrontTopLeft().getY(), (double) (shapedetector_shapedetectorcollection.getFrontTopLeft().getY() - shapedetector_shapedetectorcollection.getHeight())), 0.0D, 1.0D);

                this.portalEntranceOffset = new Vec3(d1, d2, 0.0D);
                this.portalEntranceForwards = shapedetector_shapedetectorcollection.getForwards();
            }

            this.isInsidePortal = true;
        }
    }

    protected void handleNetherPortal() {
        if (this.level instanceof ServerLevel) {
            int i = this.getPortalWaitTime();
            ServerLevel worldserver = (ServerLevel) this.level;

            if (this.isInsidePortal) {
                MinecraftServer minecraftserver = worldserver.getServer();
                ResourceKey<Level> resourcekey = this.level.getDimensionKey() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
                ServerLevel worldserver1 = minecraftserver.getWorldServer(resourcekey);

                if (worldserver1 != null && minecraftserver.isNetherEnabled() && !this.isPassenger() && this.portalTime++ >= i) {
                    this.level.getProfiler().push("portal");
                    this.portalTime = i;
                    this.changingDimensionDelay = this.getDimensionChangingDelay();
                    // CraftBukkit start
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).a(worldserver1, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
                    } else {
                        this.changeDimension(worldserver1);
                    }
                    // CraftBukkit end
                    this.level.getProfiler().pop();
                }

                this.isInsidePortal = false;
            } else {
                if (this.portalTime > 0) {
                    this.portalTime -= 4;
                }

                if (this.portalTime < 0) {
                    this.portalTime = 0;
                }
            }

            this.processDimensionDelay();
        }
    }

    public int getDimensionChangingDelay() {
        return 300;
    }

    public Iterable<ItemStack> getHandSlots() {
        return Entity.EMPTY_LIST;
    }

    public Iterable<ItemStack> getArmorSlots() {
        return Entity.EMPTY_LIST;
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorSlots());
    }

    public void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack) {}

    public boolean isOnFire() {
        boolean flag = this.level != null && this.level.isClientSide;

        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.getPassengers().isEmpty();
    }

    public boolean rideableUnderWater() {
        return true;
    }

    public void setShiftKeyDown(boolean flag) {
        this.setSharedFlag(1, flag);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.getPose() == net.minecraft.world.entity.Pose.CROUCHING;
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean flag) {
        this.setSharedFlag(3, flag);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.getPose() == net.minecraft.world.entity.Pose.SWIMMING;
    }

    public void setSwimming(boolean flag) {
        // CraftBukkit start
        if (this.isSwimming() != flag && this instanceof net.minecraft.world.entity.LivingEntity) {
            if (CraftEventFactory.callToggleSwimEvent((net.minecraft.world.entity.LivingEntity) this, flag).isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.setSharedFlag(4, flag);
    }

    public boolean isGlowing() {
        return this.glowing || this.level.isClientSide && this.getSharedFlag(6);
    }

    public void setGlowing(boolean flag) {
        this.glowing = flag;
        if (!this.level.isClientSide) {
            this.setSharedFlag(6, this.glowing);
        }

    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    @Nullable
    public Team getTeam() {
        return this.level.getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public boolean isAlliedTo(Entity entity) {
        return this.isAlliedTo(entity.getTeam());
    }

    public boolean isAlliedTo(Team scoreboardteambase) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(scoreboardteambase) : false;
    }

    public void setInvisible(boolean flag) {
        this.setSharedFlag(5, flag);
    }

    public boolean getSharedFlag(int i) {
        return ((Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) & 1 << i) != 0;
    }

    public void setSharedFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID);

        if (flag) {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 | 1 << i));
        } else {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 & ~(1 << i)));
        }

    }

    public int getMaxAirSupply() {
        return 300;
    }

    public int getAirSupply() {
        return (Integer) this.entityData.get(Entity.DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int i) {
        // CraftBukkit start
        EntityAirChangeEvent event = new EntityAirChangeEvent(this.getBukkitEntity(), i);
        // Suppress during worldgen
        if (this.valid) {
            event.getEntity().getServer().getPluginManager().callEvent(event);
        }
        if (event.isCancelled()) {
            return;
        }
        this.entityData.set(Entity.DATA_AIR_SUPPLY_ID, event.getAmount());
        // CraftBukkit end
    }

    public void thunderHit(LightningBolt entitylightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        // CraftBukkit start
        final org.bukkit.entity.Entity thisBukkitEntity = this.getBukkitEntity();
        final org.bukkit.entity.Entity stormBukkitEntity = entitylightning.getBukkitEntity();
        final PluginManager pluginManager = Bukkit.getPluginManager();
        // CraftBukkit end

        if (this.remainingFireTicks == 0) {
            // CraftBukkit start - Call a combust event when lightning strikes
            EntityCombustByEntityEvent entityCombustEvent = new EntityCombustByEntityEvent(stormBukkitEntity, thisBukkitEntity, 8);
            pluginManager.callEvent(entityCombustEvent);
            if (!entityCombustEvent.isCancelled()) {
                this.setOnFire(entityCombustEvent.getDuration(), false);
            }
            // CraftBukkit end
        }

        // CraftBukkit start
        if (thisBukkitEntity instanceof Hanging) {
            HangingBreakByEntityEvent hangingEvent = new HangingBreakByEntityEvent((Hanging) thisBukkitEntity, stormBukkitEntity);
            pluginManager.callEvent(hangingEvent);

            if (hangingEvent.isCancelled()) {
                return;
            }
        }

        if (this.fireImmune()) {
            return;
        }
        CraftEventFactory.entityDamage = entitylightning;
        if (!this.hurt(DamageSource.LIGHTNING_BOLT, 5.0F)) {
            CraftEventFactory.entityDamage = null;
            return;
        }
        // CraftBukkit end
    }

    public void onAboveBubbleCol(boolean flag) {
        Vec3 vec3d = this.getDeltaMovement();
        double d0;

        if (flag) {
            d0 = Math.max(-0.9D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(1.8D, vec3d.y + 0.1D);
        }

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
    }

    public void onInsideBubbleColumn(boolean flag) {
        Vec3 vec3d = this.getDeltaMovement();
        double d0;

        if (flag) {
            d0 = Math.max(-0.3D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(0.7D, vec3d.y + 0.06D);
        }

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
        this.fallDistance = 0.0F;
    }

    public void killed(net.minecraft.world.entity.LivingEntity entityliving) {}

    protected void checkInBlock(double d0, double d1, double d2) {
        BlockPos blockposition = new BlockPos(d0, d1, d2);
        Vec3 vec3d = new Vec3(d0 - (double) blockposition.getX(), d1 - (double) blockposition.getY(), d2 - (double) blockposition.getZ());
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        Direction enumdirection = Direction.UP;
        double d3 = Double.MAX_VALUE;
        Direction[] aenumdirection = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection1 = aenumdirection[j];

            blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection1);
            if (!this.level.getType(blockposition_mutableblockposition).isCollisionShapeFullBlock(this.level, blockposition_mutableblockposition)) {
                double d4 = vec3d.get(enumdirection1.getAxis());
                double d5 = enumdirection1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - d4 : d4;

                if (d5 < d3) {
                    d3 = d5;
                    enumdirection = enumdirection1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = (float) enumdirection.getAxisDirection().getStep();
        Vec3 vec3d1 = this.getDeltaMovement().scale(0.75D);

        if (enumdirection.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement((double) (f1 * f), vec3d1.y, vec3d1.z);
        } else if (enumdirection.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec3d1.x, (double) (f1 * f), vec3d1.z);
        } else if (enumdirection.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec3d1.x, vec3d1.y, (double) (f1 * f));
        }

    }

    public void makeStuckInBlock(BlockState iblockdata, Vec3 vec3d) {
        this.fallDistance = 0.0F;
        this.stuckSpeedMultiplier = vec3d;
    }

    private static Component removeAction(Component ichatbasecomponent) {
        MutableComponent ichatmutablecomponent = ichatbasecomponent.plainCopy().setStyle(ichatbasecomponent.getStyle().withClickEvent((ClickEvent) null));
        Iterator iterator = ichatbasecomponent.getSiblings().iterator();

        while (iterator.hasNext()) {
            Component ichatbasecomponent1 = (Component) iterator.next();

            ichatmutablecomponent.append(removeAction(ichatbasecomponent1));
        }

        return ichatmutablecomponent;
    }

    @Override
    public Component getName() {
        Component ichatbasecomponent = this.getCustomName();

        return ichatbasecomponent != null ? removeAction(ichatbasecomponent) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float f) {}

    public void setYBodyRot(float f) {}

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity entity) {
        return false;
    }

    public String toString() {
        return String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]", this.getClass().getSimpleName(), this.getName().getString(), this.id, this.level == null ? "~NULL~" : this.level.toString(), this.getX(), this.getY(), this.getZ());
    }

    public boolean isInvulnerableTo(DamageSource damagesource) {
        return this.invulnerable && damagesource != DamageSource.OUT_OF_WORLD && !damagesource.isCreativePlayer();
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean flag) {
        this.invulnerable = flag;
    }

    public void copyPosition(Entity entity) {
        this.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.yRot, entity.xRot);
    }

    public void restoreFrom(Entity entity) {
        CompoundTag nbttagcompound = entity.saveWithoutId(new CompoundTag());

        nbttagcompound.remove("Dimension");
        this.load(nbttagcompound);
        this.changingDimensionDelay = entity.changingDimensionDelay;
        this.portalEntranceBlock = entity.portalEntranceBlock;
        this.portalEntranceOffset = entity.portalEntranceOffset;
        this.portalEntranceForwards = entity.portalEntranceForwards;
    }

    @Nullable
    public Entity changeDimension(ServerLevel worldserver) {
        // CraftBukkit start
        return teleportTo(worldserver, null);
    }

    @Nullable
    public Entity teleportTo(ServerLevel worldserver, BlockPos location) {
        // CraftBukkit end
        if (this.level instanceof ServerLevel && !this.removed) {
            this.level.getProfiler().push("changeDimension");
            // CraftBukkit start
            // this.decouple();
            if (worldserver == null){
                return null;
            }
            // CraftBukkit end
            this.level.getProfiler().push("reposition");
            Vec3 vec3d = this.getDeltaMovement();
            float f = 0.0F;
            BlockPos blockposition = location; // CraftBukkit

        if (blockposition == null) { // CraftBukkit
            if (this.level.getDimensionKey() == Level.END && worldserver.getDimensionKey() == Level.OVERWORLD) {
                // CraftBukkit start
                EntityPortalEvent event = CraftEventFactory.callEntityPortalEvent(this, worldserver, worldserver.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldserver.getSharedSpawnPos()), 0);
                if (event == null) {
                    return null;
                }
                worldserver = ((CraftWorld) event.getTo().getWorld()).getHandle();
                blockposition = new BlockPos(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
                // CraftBukkit end
            } else if (worldserver.getDimensionKey() == Level.END) {
                // CraftBukkit start
                EntityPortalEvent event = CraftEventFactory.callEntityPortalEvent(this, worldserver, ServerLevel.END_SPAWN_POINT, 0);
                if (event == null) {
                    return null;
                }
                worldserver = ((CraftWorld) event.getTo().getWorld()).getHandle();
                blockposition = new BlockPos(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
                // CraftBukkit end
            } else {
                double d0 = this.getX();
                double d1 = this.getZ();
                DimensionType dimensionmanager = this.level.dimensionType();
                DimensionType dimensionmanager1 = worldserver.dimensionType();
                double d2 = 8.0D;

                if (!dimensionmanager.shrunk() && dimensionmanager1.shrunk()) {
                    d0 /= 8.0D;
                    d1 /= 8.0D;
                } else if (dimensionmanager.shrunk() && !dimensionmanager1.shrunk()) {
                    d0 *= 8.0D;
                    d1 *= 8.0D;
                }

                // Spigot start - SPIGOT-5677, MC-114796: Fix portals generating outside world border
                double d3 = Math.max(-2.9999872E7D, worldserver.getWorldBorder().getMinX() + 16.0D);
                double d4 = Math.max(-2.9999872E7D, worldserver.getWorldBorder().getMinZ() + 16.0D);
                // Spigot end
                double d5 = Math.min(2.9999872E7D, worldserver.getWorldBorder().getMaxX() - 16.0D);
                double d6 = Math.min(2.9999872E7D, worldserver.getWorldBorder().getMaxZ() - 16.0D);

                d0 = Mth.clamp(d0, d3, d5);
                d1 = Mth.clamp(d1, d4, d6);
                Vec3 vec3d1 = this.getPortalEntranceOffset();

                blockposition = new BlockPos(d0, this.getY(), d1);
                // CraftBukkit start
                EntityPortalEvent event = CraftEventFactory.callEntityPortalEvent(this, worldserver, blockposition, 128);
                if (event == null) {
                    return null;
                }
                worldserver = ((CraftWorld) event.getTo().getWorld()).getHandle();
                blockposition = new BlockPos(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
                int searchRadius = event.getSearchRadius();
                // CraftBukkit end
                BlockPattern.PortalInfo shapedetector_shape = worldserver.getPortalForcer().findPortal(blockposition, vec3d, this.getPortalEntranceForwards(), vec3d1.x, vec3d1.y, this instanceof Player, searchRadius); // CraftBukkit - search radius

                if (shapedetector_shape == null) {
                    return null;
                }

                blockposition = new BlockPos(shapedetector_shape.pos);
                vec3d = shapedetector_shape.speed;
                f = (float) shapedetector_shape.angle;
            }
        } // CraftBukkit

            // CraftBukkit start
            this.unRide();
            // CraftBukkit end

            this.level.getProfiler().popPush("reloading");
            Entity entity = this.getType().create((Level) worldserver);

            if (entity != null) {
                entity.restoreFrom(this);
                entity.moveTo(blockposition, entity.yRot + f, entity.xRot);
                entity.setDeltaMovement(vec3d);
                worldserver.addFromAnotherDimension(entity);
                if (worldserver.getDimensionKey() == Level.END) {
                    ServerLevel.a(worldserver, this); // CraftBukkit
                }
                // CraftBukkit start - Forward the CraftEntity to the new entity
                this.getBukkitEntity().setHandle(entity);
                entity.bukkitEntity = this.getBukkitEntity();

                if (this instanceof Mob) {
                    ((Mob)this).dropLeash(true, false); // Unleash to prevent duping of leads.
                }
                // CraftBukkit end
            }

            this.removeAfterChangingDimensions();
            this.level.getProfiler().pop();
            ((ServerLevel) this.level).resetEmptyTime();
            worldserver.resetEmptyTime();
            this.level.getProfiler().pop();
            return entity;
        } else {
            return null;
        }
    }

    protected void removeAfterChangingDimensions() {
        this.removed = true;
    }

    public boolean canChangeDimensions() {
        return true;
    }

    public float getBlockExplosionResistance(Explosion explosion, BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, FluidState fluid, float f) {
        return f;
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, float f) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public Vec3 getPortalEntranceOffset() {
        return this.portalEntranceOffset;
    }

    public Direction getPortalEntranceForwards() {
        return this.portalEntranceForwards;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void appendEntityCrashDetails(CrashReportCategory crashreportsystemdetails) {
        crashreportsystemdetails.setDetail("Entity Type", () -> {
            return EntityType.getKey(this.getType()) + " (" + this.getClass().getCanonicalName() + ")";
        });
        crashreportsystemdetails.setDetail("Entity ID", (Object) this.id);
        crashreportsystemdetails.setDetail("Entity Name", () -> {
            return this.getName().getString();
        });
        crashreportsystemdetails.setDetail("Entity's Exact location", (Object) String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        crashreportsystemdetails.setDetail("Entity's Block location", (Object) CrashReportCategory.formatLocation(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ())));
        Vec3 vec3d = this.getDeltaMovement();

        crashreportsystemdetails.setDetail("Entity's Momentum", (Object) String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3d.x, vec3d.y, vec3d.z));
        crashreportsystemdetails.setDetail("Entity's Passengers", () -> {
            return this.getPassengers().toString();
        });
        crashreportsystemdetails.setDetail("Entity's Vehicle", () -> {
            return this.getVehicle().toString();
        });
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
        this.stringUUID = this.uuid.toString();
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName()).withStyle((chatmodifier) -> {
            return chatmodifier.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID());
        });
    }

    public void setCustomName(@Nullable Component ichatbasecomponent) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME, Optional.ofNullable(ichatbasecomponent));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return (Component) ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).orElse((Object) null);
    }

    @Override
    public boolean hasCustomName() {
        return ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).isPresent();
    }

    public void setCustomNameVisible(boolean flag) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME_VISIBLE, flag);
    }

    public boolean isCustomNameVisible() {
        return (Boolean) this.entityData.get(Entity.DATA_CUSTOM_NAME_VISIBLE);
    }

    public final void teleportToWithTicket(double d0, double d1, double d2) {
        if (this.level instanceof ServerLevel) {
            ChunkPos chunkcoordintpair = new ChunkPos(new BlockPos(d0, d1, d2));

            ((ServerLevel) this.level).getChunkSourceOH().addRegionTicket(TicketType.POST_TELEPORT, chunkcoordintpair, 0, this.getId());
            this.level.getChunk(chunkcoordintpair.x, chunkcoordintpair.z);
            this.teleportTo(d0, d1, d2);
        }
    }

    public void teleportTo(double d0, double d1, double d2) {
        if (this.level instanceof ServerLevel) {
            ServerLevel worldserver = (ServerLevel) this.level;

            this.moveTo(d0, d1, d2, this.yRot, this.xRot);
            this.getSelfAndPassengers().forEach((entity) -> {
                worldserver.updateChunkPos(entity);
                entity.forceChunkAddition = true;
                Iterator iterator = entity.passengers.iterator();

                while (iterator.hasNext()) {
                    Entity entity1 = (Entity) iterator.next();

                    entity.positionRider(entity1, Entity::moveTo);
                }

            });
        }
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (Entity.DATA_POSE.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

    }

    public void refreshDimensions() {
        EntityDimensions entitysize = this.dimensions;
        net.minecraft.world.entity.Pose entitypose = this.getPose();
        EntityDimensions entitysize1 = this.getDimensions(entitypose);

        this.dimensions = entitysize1;
        this.eyeHeight = this.getEyeHeight(entitypose, entitysize1);
        if (entitysize1.width < entitysize.width) {
            double d0 = (double) entitysize1.width / 2.0D;

            this.setBoundingBox(new AABB(this.getX() - d0, this.getY(), this.getZ() - d0, this.getX() + d0, this.getY() + (double) entitysize1.height, this.getZ() + d0));
        } else {
            AABB axisalignedbb = this.getBoundingBox();

            this.setBoundingBox(new AABB(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ, axisalignedbb.minX + (double) entitysize1.width, axisalignedbb.minY + (double) entitysize1.height, axisalignedbb.minZ + (double) entitysize1.width));
            if (entitysize1.width > entitysize.width && !this.firstTick && !this.level.isClientSide) {
                float f = entitysize.width - entitysize1.width;

                this.move(MoverType.SELF, new Vec3((double) f, 0.0D, (double) f));
            }

        }
    }

    public Direction getDirection() {
        return Direction.fromYRot((double) this.yRot);
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer entityplayer) {
        return true;
    }

    public AABB getBoundingBox() {
        return this.bb;
    }

    protected AABB getBoundingBoxForPose(net.minecraft.world.entity.Pose entitypose) {
        EntityDimensions entitysize = this.getDimensions(entitypose);
        float f = entitysize.width / 2.0F;
        Vec3 vec3d = new Vec3(this.getX() - (double) f, this.getY(), this.getZ() - (double) f);
        Vec3 vec3d1 = new Vec3(this.getX() + (double) f, this.getY() + (double) entitysize.height, this.getZ() + (double) f);

        return new AABB(vec3d, vec3d1);
    }

    public void setBoundingBox(AABB axisalignedbb) {
        // CraftBukkit start - block invalid bounding boxes
        double minX = axisalignedbb.minX,
                minY = axisalignedbb.minY,
                minZ = axisalignedbb.minZ,
                maxX = axisalignedbb.maxX,
                maxY = axisalignedbb.maxY,
                maxZ = axisalignedbb.maxZ;
        double len = axisalignedbb.maxX - axisalignedbb.minX;
        if (len < 0) maxX = minX;
        if (len > 64) maxX = minX + 64.0;

        len = axisalignedbb.maxY - axisalignedbb.minY;
        if (len < 0) maxY = minY;
        if (len > 64) maxY = minY + 64.0;

        len = axisalignedbb.maxZ - axisalignedbb.minZ;
        if (len < 0) maxZ = minZ;
        if (len > 64) maxZ = minZ + 64.0;
        this.bb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        // CraftBukkit end
    }

    protected float getEyeHeight(net.minecraft.world.entity.Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * 0.85F;
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public boolean setSlot(int i, ItemStack itemstack) {
        return false;
    }

    @Override
    public void sendMessage(Component ichatbasecomponent, UUID uuid) {}

    public Level getCommandSenderWorld() {
        return this.level;
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level.getServer();
    }

    public InteractionResult interactAt(Player entityhuman, Vec3 vec3d, InteractionHand enumhand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion() {
        return false;
    }

    public void doEnchantDamageEffects(net.minecraft.world.entity.LivingEntity entityliving, Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            EnchantmentHelper.doPostHurtEffects((net.minecraft.world.entity.LivingEntity) entity, (Entity) entityliving);
        }

        EnchantmentHelper.doPostDamageEffects(entityliving, entity);
    }

    public void startSeenByPlayer(ServerPlayer entityplayer) {}

    public void stopSeenByPlayer(ServerPlayer entityplayer) {}

    public float rotate(Rotation enumblockrotation) {
        float f = Mth.wrapDegrees(this.yRot);

        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    public float mirror(Mirror enumblockmirror) {
        float f = Mth.wrapDegrees(this.yRot);

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                return -f;
            case FRONT_BACK:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public boolean checkAndResetForcedChunkAdditionFlag() {
        boolean flag = this.forceChunkAddition;

        this.forceChunkAddition = false;
        return flag;
    }

    public boolean checkAndResetUpdateChunkPos() {
        boolean flag = this.movedSinceLastChunkCheck;

        this.movedSinceLastChunkCheck = false;
        return flag;
    }

    @Nullable
    public Entity getControllingPassenger() {
        return null;
    }

    public List<Entity> getPassengers() {
        return (List) (this.passengers.isEmpty() ? Collections.emptyList() : Lists.newArrayList(this.passengers));
    }

    public boolean hasPassenger(Entity entity) {
        Iterator iterator = this.getPassengers().iterator();

        Entity entity1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            entity1 = (Entity) iterator.next();
        } while (!entity1.equals(entity));

        return true;
    }

    public boolean hasPassenger(Class<? extends Entity> oclass) {
        Iterator iterator = this.getPassengers().iterator();

        Entity entity;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            entity = (Entity) iterator.next();
        } while (!oclass.isAssignableFrom(entity.getClass()));

        return true;
    }

    public Collection<Entity> getIndirectPassengers() {
        Set<Entity> set = Sets.newHashSet();
        Iterator iterator = this.getPassengers().iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            set.add(entity);
            entity.fillIndirectPassengers(false, set);
        }

        return set;
    }

    public Stream<Entity> getSelfAndPassengers() {
        return Stream.concat(Stream.of(this), this.passengers.stream().flatMap(Entity::getSelfAndPassengers));
    }

    public boolean hasOnePlayerPassenger() {
        Set<Entity> set = Sets.newHashSet();

        this.fillIndirectPassengers(true, set);
        return set.size() == 1;
    }

    private void fillIndirectPassengers(boolean flag, Set<Entity> set) {
        Entity entity;

        for (Iterator iterator = this.getPassengers().iterator(); iterator.hasNext(); entity.fillIndirectPassengers(flag, set)) {
            entity = (Entity) iterator.next();
            if (!flag || ServerPlayer.class.isAssignableFrom(entity.getClass())) {
                set.add(entity);
            }
        }

    }

    public Entity getRootVehicle() {
        Entity entity;

        for (entity = this; entity.isPassenger(); entity = entity.getVehicle()) {
            ;
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity entity) {
        return this.getRootVehicle() == entity.getRootVehicle();
    }

    public boolean isControlledByLocalInstance() {
        Entity entity = this.getControllingPassenger();

        return entity instanceof Player ? ((Player) entity).isLocalPlayer() : !this.level.isClientSide;
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double d0, double d1, float f) {
        double d2 = (d0 + d1 + 9.999999747378752E-6D) / 2.0D;
        float f1 = -Mth.sin(f * 0.017453292F);
        float f2 = Mth.cos(f * 0.017453292F);
        float f3 = Math.max(Math.abs(f1), Math.abs(f2));

        return new Vec3((double) f1 * d2 / (double) f3, 0.0D, (double) f2 * d2 / (double) f3);
    }

    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity entityliving) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    public int getFireImmuneTicks() {
        return 1;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(this, this.position(), this.getRotationVector(), this.level instanceof ServerLevel ? (ServerLevel) this.level : null, this.getPermissionLevel(), this.getName().getString(), this.getDisplayName(), this.level.getServer(), this);
    }

    protected int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int i) {
        return this.getPermissionLevel() >= i;
    }

    @Override
    public boolean acceptsSuccess() {
        return this.level.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }

    public void lookAt(EntityAnchorArgument.Anchor argumentanchor_anchor, Vec3 vec3d) {
        Vec3 vec3d1 = argumentanchor_anchor.apply(this);
        double d0 = vec3d.x - vec3d1.x;
        double d1 = vec3d.y - vec3d1.y;
        double d2 = vec3d.z - vec3d1.z;
        double d3 = (double) Mth.sqrt(d0 * d0 + d2 * d2);

        this.xRot = Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 57.2957763671875D)));
        this.yRot = Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F);
        this.setYHeadRot(this.yRot);
        this.xRotO = this.xRot;
        this.yRotO = this.yRot;
    }

    public boolean updateFluidHeightAndDoFluidPushing(Tag<Fluid> tag, double d0) {
        AABB axisalignedbb = this.getBoundingBox().deflate(0.001D);
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.minY);
        int l = Mth.ceil(axisalignedbb.maxY);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);

        if (!this.level.hasChunksAt(i, k, i1, j, l, j1)) {
            return false;
        } else {
            double d1 = 0.0D;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3 vec3d = Vec3.ZERO;
            int k1 = 0;
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        blockposition_mutableblockposition.d(l1, i2, j2);
                        FluidState fluid = this.level.getFluidState(blockposition_mutableblockposition);

                        if (fluid.is(tag)) {
                            double d2 = (double) ((float) i2 + fluid.getHeight(this.level, blockposition_mutableblockposition));

                            if (d2 >= axisalignedbb.minY) {
                                flag1 = true;
                                d1 = Math.max(d2 - axisalignedbb.minY, d1);
                                if (flag) {
                                    Vec3 vec3d1 = fluid.getFlow(this.level, blockposition_mutableblockposition);

                                    if (d1 < 0.4D) {
                                        vec3d1 = vec3d1.scale(d1);
                                    }

                                    vec3d = vec3d.add(vec3d1);
                                    ++k1;
                                }
                            }
                        }
                    }
                }
            }

            if (vec3d.length() > 0.0D) {
                if (k1 > 0) {
                    vec3d = vec3d.scale(1.0D / (double) k1);
                }

                if (!(this instanceof Player)) {
                    vec3d = vec3d.normalize();
                }

                Vec3 vec3d2 = this.getDeltaMovement();

                vec3d = vec3d.scale(d0 * 1.0D);
                double d3 = 0.003D;

                if (Math.abs(vec3d2.x) < 0.003D && Math.abs(vec3d2.z) < 0.003D && vec3d.length() < 0.0045000000000000005D) {
                    vec3d = vec3d.normalize().scale(0.0045000000000000005D);
                }

                this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
            }

            this.fluidHeight.put(tag, d1);
            return flag1;
        }
    }

    public double getFluidHeight(Tag<Fluid> tag) {
        return this.fluidHeight.getDouble(tag);
    }

    public double getFluidJumpThreshold() {
        return (double) this.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
    }

    public final float getBbWidth() {
        return this.dimensions.width;
    }

    public final float getBbHeight() {
        return this.dimensions.height;
    }

    public abstract Packet<?> getAddEntityPacket();

    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose entitypose) {
        return this.type.getDimensions();
    }

    public Vec3 position() {
        return this.position;
    }

    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 vec3d) {
        this.deltaMovement = vec3d;
    }

    public void setDeltaMovement(double d0, double d1, double d2) {
        this.setDeltaMovement(new Vec3(d0, d1, d2));
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double d0) {
        return this.position.x + (double) this.getBbWidth() * d0;
    }

    public double getRandomX(double d0) {
        return this.getX((2.0D * this.random.nextDouble() - 1.0D) * d0);
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double d0) {
        return this.position.y + (double) this.getBbHeight() * d0;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + (double) this.eyeHeight;
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double d0) {
        return this.position.z + (double) this.getBbWidth() * d0;
    }

    public double getRandomZ(double d0) {
        return this.getZ((2.0D * this.random.nextDouble() - 1.0D) * d0);
    }

    public void setPosRaw(double d0, double d1, double d2) {
        if (this.position.x != d0 || this.position.y != d1 || this.position.z != d2) {
            this.position = new Vec3(d0, d1, d2);
            int i = Mth.floor(d0);
            int j = Mth.floor(d1);
            int k = Mth.floor(d2);

            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(i, j, k);
            }

            this.movedSinceLastChunkCheck = true;
        }

    }

    public void checkDespawn() {}

    @FunctionalInterface
    public interface MoveFunction {

        void accept(Entity entity, double d0, double d1, double d2);
    }
}
