package net.minecraft.server.network;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundAddMobPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerAckPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerAckPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.LazyPlayerSet;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.util.NumberConversions;
// CraftBukkit end

public class ServerGamePacketListenerImpl implements ServerGamePacketListener {

    private static final Logger LOGGER = LogManager.getLogger();
    public final Connection connection;
    private final MinecraftServer server;
    public ServerPlayer player;
    private int tickCount;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    // CraftBukkit start - multithreaded fields
    private volatile int chatSpamTickCount;
<<<<<<< HEAD
    private static final AtomicIntegerFieldUpdater chatSpamField = AtomicIntegerFieldUpdater.newUpdater(ServerGamePacketListenerImpl.class, "chatThrottle");
=======
    private static final AtomicIntegerFieldUpdater chatSpamField = AtomicIntegerFieldUpdater.newUpdater(ServerGamePacketListenerImpl.class, "chatSpamTickCount"); // Toothpick - remap fix
    private final java.util.concurrent.atomic.AtomicInteger tabSpamLimiter = new java.util.concurrent.atomic.AtomicInteger(); // Paper - configurable tab spam limits
>>>>>>> Toothpick
    // CraftBukkit end
    private int dropSpamTickCount;
    private final Int2ShortMap expectedAcks = new Int2ShortOpenHashMap();
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    private Entity lastVehicle;
    private double vehicleFirstGoodX;
    private double vehicleFirstGoodY;
    private double vehicleFirstGoodZ;
    private double vehicleLastGoodX;
    private double vehicleLastGoodY;
    private double vehicleLastGoodZ;
    private Vec3 awaitingPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;

    public ServerGamePacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager, ServerPlayer entityplayer) {
        this.server = minecraftserver;
        this.connection = networkmanager;
        networkmanager.setPacketListener(this);
        this.player = entityplayer;
        entityplayer.connection = this;

        // CraftBukkit start - add fields and methods
        this.craftServer = minecraftserver.server;
    }

    private final org.bukkit.craftbukkit.CraftServer craftServer;
    public boolean processedDisconnect;
    private int lastTick = MinecraftServer.currentTick;
    private int allowedPlayerTicks = 1;
    private int lastDropTick = MinecraftServer.currentTick;
    private int lastBookTick  = MinecraftServer.currentTick;
    private int dropCount = 0;
    private static final int SURVIVAL_PLACE_DISTANCE_SQUARED = 6 * 6;
    private static final int CREATIVE_PLACE_DISTANCE_SQUARED = 7 * 7;

    // Get position of last block hit for BlockDamageLevel.STOPPED
    private double lastPosX = Double.MAX_VALUE;
    private double lastPosY = Double.MAX_VALUE;
    private double lastPosZ = Double.MAX_VALUE;
    private float lastPitch = Float.MAX_VALUE;
    private float lastYaw = Float.MAX_VALUE;
    private boolean justTeleported = false;
    private boolean hasMoved; // Spigot

    public CraftPlayer getPlayer() {
        return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
    }
    // CraftBukkit end

    public void tick() {
        org.bukkit.craftbukkit.SpigotTimings.playerConnectionTimer.startTiming(); // Spigot
        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        this.player.absMoveTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.yRot, this.player.xRot);
        ++this.tickCount;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping()) {
            if (++this.aboveGroundTickCount > 80) {
                ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked for floating too long!", this.player.getName().getString());
                this.disconnect(new TranslatableComponent("multiplayer.disconnect.flying"));
                return;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
            if (this.clientVehicleIsFloating && this.player.getRootVehicle().getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > 80) {
                    ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getName().getString());
                    this.disconnect(new TranslatableComponent("multiplayer.disconnect.flying"));
                    return;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        this.server.getProfiler().push("keepAlive");
        long i = Util.getMillis();

        if (i - this.keepAliveTime >= 25000L) { // CraftBukkit
            if (this.keepAlivePending) {
                this.disconnect(new TranslatableComponent("disconnect.timeout"));
            } else {
                this.keepAlivePending = true;
                this.keepAliveTime = i;
                this.keepAliveChallenge = i;
                this.sendPacket(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        this.server.getProfiler().pop();
        // CraftBukkit start
        for (int spam; (spam = this.chatSpamTickCount) > 0 && !chatSpamField.compareAndSet(this, spam, spam - 1); ) ;
        /* Use thread-safe field access instead
        if (this.chatThrottle > 0) {
            --this.chatThrottle;
        }
        */
        // CraftBukkit end

        if (this.dropSpamTickCount > 0) {
            --this.dropSpamTickCount;
        }

        if (this.player.getLastActionTime() > 0L && this.server.getPlayerIdleTimeout() > 0 && Util.getMillis() - this.player.getLastActionTime() > (long) (this.server.getPlayerIdleTimeout() * 1000 * 60)) {
            this.player.resetLastActionTime(); // CraftBukkit - SPIGOT-854
            this.disconnect(new TranslatableComponent("multiplayer.disconnect.idling"));
        }
        org.bukkit.craftbukkit.SpigotTimings.playerConnectionTimer.stopTiming(); // Spigot

    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
    }

    @Override
    public Connection getConnection() {
        return this.connection;
    }

    private boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.player.getGameProfile());
    }

    // CraftBukkit start
    @Deprecated
    public void disconnect(Component ichatbasecomponent) {
        disconnect(CraftChatMessage.fromComponent(ichatbasecomponent));
    }
    // CraftBukkit end

    public void disconnect(String s) {
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        String leaveMessage = ChatFormatting.YELLOW + this.player.getScoreboardName() + " left the game.";

        PlayerKickEvent event = new PlayerKickEvent(this.craftServer.getPlayer(this.player), s, leaveMessage);

        if (this.craftServer.getServer().isRunning()) {
            this.craftServer.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        // Send the possibly modified leave message
        s = event.getReason();
        // CraftBukkit end
        final TextComponent ichatbasecomponent = new TextComponent(s);

        this.connection.sendPacketOH(new ClientboundDisconnectPacket(ichatbasecomponent), (future) -> {
            this.connection.disconnect(ichatbasecomponent);
        });
        this.onDisconnect(ichatbasecomponent); // CraftBukkit - fire quit instantly
        this.connection.setReadOnly();
        MinecraftServer minecraftserver = this.server;
        Connection networkmanager = this.connection;

        this.connection.getClass();
        // CraftBukkit - Don't wait
        minecraftserver.wrapRunnable(networkmanager::handleDisconnection);
    }

    @Override
    public void handlePlayerInput(ServerboundPlayerInputPacket packetplayinsteervehicle) {
        PacketUtils.ensureMainThread(packetplayinsteervehicle, this, this.player.getLevel());
        this.player.setPlayerInput(packetplayinsteervehicle.getXxa(), packetplayinsteervehicle.getZza(), packetplayinsteervehicle.isJumping(), packetplayinsteervehicle.isShiftKeyDown());
    }

    private static boolean containsInvalidValues(ServerboundMovePlayerPacket packetplayinflying) {
        return Doubles.isFinite(packetplayinflying.getX(0.0D)) && Doubles.isFinite(packetplayinflying.getY(0.0D)) && Doubles.isFinite(packetplayinflying.getZ(0.0D)) && Floats.isFinite(packetplayinflying.getXRot(0.0F)) && Floats.isFinite(packetplayinflying.getYRot(0.0F)) ? Math.abs(packetplayinflying.getX(0.0D)) > 3.0E7D || Math.abs(packetplayinflying.getY(0.0D)) > 3.0E7D || Math.abs(packetplayinflying.getZ(0.0D)) > 3.0E7D : true;
    }

    private static boolean containsInvalidValues(ServerboundMoveVehiclePacket packetplayinvehiclemove) {
        return !Doubles.isFinite(packetplayinvehiclemove.getX()) || !Doubles.isFinite(packetplayinvehiclemove.getY()) || !Doubles.isFinite(packetplayinvehiclemove.getZ()) || !Floats.isFinite(packetplayinvehiclemove.getXRot()) || !Floats.isFinite(packetplayinvehiclemove.getYRot());
    }

    @Override
    public void handleMoveVehicle(ServerboundMoveVehiclePacket packetplayinvehiclemove) {
        PacketUtils.ensureMainThread(packetplayinvehiclemove, this, this.player.getLevel());
        if (containsInvalidValues(packetplayinvehiclemove)) {
            this.disconnect(new TranslatableComponent("multiplayer.disconnect.invalid_vehicle_movement"));
        } else {
            Entity entity = this.player.getRootVehicle();

            if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
                ServerLevel worldserver = this.player.getLevel();
                double d0 = entity.getX();
                double d1 = entity.getY();
                double d2 = entity.getZ();
                double d3 = packetplayinvehiclemove.getX();
                double d4 = packetplayinvehiclemove.getY();
                double d5 = packetplayinvehiclemove.getZ();
                float f = packetplayinvehiclemove.getYRot();
                float f1 = packetplayinvehiclemove.getXRot();
                double d6 = d3 - this.vehicleFirstGoodX;
                double d7 = d4 - this.vehicleFirstGoodY;
                double d8 = d5 - this.vehicleFirstGoodZ;
                double d9 = entity.getDeltaMovement().lengthSqr();
                double d10 = d6 * d6 + d7 * d7 + d8 * d8;


                // CraftBukkit start - handle custom speeds and skipped ticks
                this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                this.lastTick = (int) (System.currentTimeMillis() / 50);

                ++this.receivedMovePacketCount;
                int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                    ServerGamePacketListenerImpl.LOGGER.debug(this.player.getScoreboardName() + " is sending move packets too frequently (" + i + " packets since last tick)");
                    i = 1;
                }

                if (d10 > 0) {
                    allowedPlayerTicks -= 1;
                } else {
                    allowedPlayerTicks = 20;
                }
                double speed;
                if (player.abilities.flying) {
                    speed = player.abilities.flyingSpeed * 20f;
                } else {
                    speed = player.abilities.walkingSpeed * 10f;
                }
                speed *= 2f; // TODO: Get the speed of the vehicle instead of the player

                if (d10 - d9 > Math.max(100.0D, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                // CraftBukkit end
                    ServerGamePacketListenerImpl.LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getName().getString(), this.player.getName().getString(), d6, d7, d8);
                    this.connection.sendPacket(new ClientboundMoveVehiclePacket(entity));
                    return;
                }

                boolean flag = worldserver.noCollision(entity, entity.getBoundingBox().deflate(0.0625D));

                d6 = d3 - this.vehicleLastGoodX;
                d7 = d4 - this.vehicleLastGoodY - 1.0E-6D;
                d8 = d5 - this.vehicleLastGoodZ;
                entity.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                double d11 = d7;

                d6 = d3 - entity.getX();
                d7 = d4 - entity.getY();
                if (d7 > -0.5D || d7 < 0.5D) {
                    d7 = 0.0D;
                }

                d8 = d5 - entity.getZ();
                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                boolean flag1 = false;

                if (d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold) { // Spigot
                    flag1 = true;
                    ServerGamePacketListenerImpl.LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", entity.getName().getString(), this.player.getName().getString(), Math.sqrt(d10));
                }
                Location curPos = this.getPlayer().getLocation(); // Spigot

                entity.absMoveTo(d3, d4, d5, f, f1);
                player.absMoveTo(d3, d4, d5, this.player.yRot, this.player.xRot); // CraftBukkit
                boolean flag2 = worldserver.noCollision(entity, entity.getBoundingBox().deflate(0.0625D));

                if (flag && (flag1 || !flag2)) {
                    entity.absMoveTo(d0, d1, d2, f, f1);
                    player.absMoveTo(d0, d1, d2, this.player.yRot, this.player.xRot); // CraftBukkit
                    this.connection.sendPacket(new ClientboundMoveVehiclePacket(entity));
                    return;
                }

                // CraftBukkit start - fire PlayerMoveEvent
                Player player = this.getPlayer();
                // Spigot Start
                if ( !hasMoved )
                {
                    lastPosX = curPos.getX();
                    lastPosY = curPos.getY();
                    lastPosZ = curPos.getZ();
                    lastYaw = curPos.getYaw();
                    lastPitch = curPos.getPitch();
                    hasMoved = true;
                }
                // Spigot End
                Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                // If the packet contains movement information then we update the To location with the correct XYZ.
                to.setX(packetplayinvehiclemove.getX());
                to.setY(packetplayinvehiclemove.getY());
                to.setZ(packetplayinvehiclemove.getZ());


                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                to.setYaw(packetplayinvehiclemove.getYRot());
                to.setPitch(packetplayinvehiclemove.getXRot());

                // Prevent 40 event-calls for less than a single pixel of movement >.>
                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                    this.lastPosX = to.getX();
                    this.lastPosY = to.getY();
                    this.lastPosZ = to.getZ();
                    this.lastYaw = to.getYaw();
                    this.lastPitch = to.getPitch();

                    // Skip the first time we do this
                    if (true) { // Spigot - don't skip any move events
                        Location oldTo = to.clone();
                        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                        this.craftServer.getPluginManager().callEvent(event);

                        // If the event is cancelled we move the player back to their old location.
                        if (event.isCancelled()) {
                            teleport(from);
                            return;
                        }

                        // If a Plugin has changed the To destination then we teleport the Player
                        // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                        // We only do this if the Event was not cancelled.
                        if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                            this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                            return;
                        }

                        // Check to see if the Players Location has some how changed during the call of the event.
                        // This can happen due to a plugin teleporting the player instead of using .setTo()
                        if (!from.equals(this.getPlayer().getLocation()) && this.justTeleported) {
                            this.justTeleported = false;
                            return;
                        }
                    }
                }
                // CraftBukkit end

                this.player.getLevel().getChunkSourceOH().move(this.player);
                this.player.checkMovementStatistics(this.player.getX() - d0, this.player.getY() - d1, this.player.getZ() - d2);
                this.clientVehicleIsFloating = d11 >= -0.03125D && !this.server.isFlightAllowed() && this.noBlocksAround(entity);
                this.vehicleLastGoodX = entity.getX();
                this.vehicleLastGoodY = entity.getY();
                this.vehicleLastGoodZ = entity.getZ();
            }

        }
    }

    private boolean noBlocksAround(Entity entity) {
        return entity.level.getBlockStates(entity.getBoundingBox().inflate(0.0625D).expandTowards(0.0D, -0.55D, 0.0D)).allMatch(BlockBehaviour.BlockStateBase::isAir);
    }

    @Override
    public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packetplayinteleportaccept) {
        PacketUtils.ensureMainThread(packetplayinteleportaccept, this, this.player.getLevel());
        if (packetplayinteleportaccept.getId() == this.awaitingTeleport && this.awaitingPositionFromClient != null) { // CraftBukkit
            this.player.absMoveTo(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.yRot, this.player.xRot);
            this.lastGoodX = this.awaitingPositionFromClient.x;
            this.lastGoodY = this.awaitingPositionFromClient.y;
            this.lastGoodZ = this.awaitingPositionFromClient.z;
            if (this.player.isChangingDimension()) {
                this.player.hasChangedDimension();
            }

            this.awaitingPositionFromClient = null;
            this.player.getLevel().getChunkSourceOH().move(this.player); // CraftBukkit
        }

    }

    @Override
    public void handleRecipeBookUpdatePacket(ServerboundRecipeBookUpdatePacket packetplayinrecipedisplayed) {
        PacketUtils.ensureMainThread(packetplayinrecipedisplayed, this, this.player.getLevel());
        if (packetplayinrecipedisplayed.getPurpose() == ServerboundRecipeBookUpdatePacket.Purpose.SHOWN) {
            Optional<? extends Recipe<?>> optional = this.server.getRecipeManager().byKey(packetplayinrecipedisplayed.getRecipe()); // CraftBukkit - decompile error
            ServerRecipeBook recipebookserver = this.player.getRecipeBook();

            optional.ifPresent(recipebookserver::removeHighlight);
        } else if (packetplayinrecipedisplayed.getPurpose() == ServerboundRecipeBookUpdatePacket.Purpose.SETTINGS) {
            this.player.getRecipeBook().setGuiOpen(packetplayinrecipedisplayed.isGuiOpen());
            this.player.getRecipeBook().setFilteringCraftable(packetplayinrecipedisplayed.isFilteringCraftable());
            this.player.getRecipeBook().setFurnaceGuiOpen(packetplayinrecipedisplayed.isFurnaceGuiOpen());
            this.player.getRecipeBook().setFurnaceFilteringCraftable(packetplayinrecipedisplayed.isFurnaceFilteringCraftable());
            this.player.getRecipeBook().setBlastingFurnaceGuiOpen(packetplayinrecipedisplayed.isBlastFurnaceGuiOpen());
            this.player.getRecipeBook().setBlastingFurnaceFilteringCraftable(packetplayinrecipedisplayed.isBlastFurnaceFilteringCraftable());
            this.player.getRecipeBook().setSmokerGuiOpen(packetplayinrecipedisplayed.isSmokerGuiOpen());
            this.player.getRecipeBook().setSmokerFilteringCraftable(packetplayinrecipedisplayed.isSmokerFilteringCraftable());
        }

    }

    @Override
    public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packetplayinadvancements) {
        PacketUtils.ensureMainThread(packetplayinadvancements, this, this.player.getLevel());
        if (packetplayinadvancements.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
            ResourceLocation minecraftkey = packetplayinadvancements.getTab();
            Advancement advancement = this.server.getAdvancements().getAdvancement(minecraftkey);

            if (advancement != null) {
                this.player.getAdvancements().setSelectedTab(advancement);
            }
        }

    }

    @Override
    public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packetplayintabcomplete) {
        PacketUtils.ensureMainThread(packetplayintabcomplete, this, this.player.getLevel());
        // CraftBukkit start
        if (chatSpamField.addAndGet(this, 1) > 500 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) {
            this.disconnect(new TranslatableComponent("disconnect.spam", new Object[0]));
            return;
        }
        // CraftBukkit end
        StringReader stringreader = new StringReader(packetplayintabcomplete.getCommand());

        if (stringreader.canRead() && stringreader.peek() == '/') {
            stringreader.skip();
        }

        ParseResults<CommandSourceStack> parseresults = this.server.getCommands().getDispatcher().parse(stringreader, this.player.createCommandSourceStack());

        this.server.getCommands().getDispatcher().getCompletionSuggestions(parseresults).thenAccept((suggestions) -> {
            if (suggestions.isEmpty()) return; // CraftBukkit - don't send through empty suggestions - prevents [<args>] from showing for plugins with nothing more to offer
            this.connection.sendPacket(new ClientboundCommandSuggestionsPacket(packetplayintabcomplete.getId(), suggestions));
        });
    }

    @Override
    public void handleSetCommandBlock(ServerboundSetCommandBlockPacket packetplayinsetcommandblock) {
        PacketUtils.ensureMainThread(packetplayinsetcommandblock, this, this.player.getLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendMessage(new TranslatableComponent("advMode.notEnabled"), Util.NIL_UUID);
        } else if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendMessage(new TranslatableComponent("advMode.notAllowed"), Util.NIL_UUID);
        } else {
            BaseCommandBlock commandblocklistenerabstract = null;
            CommandBlockEntity tileentitycommand = null;
            BlockPos blockposition = packetplayinsetcommandblock.getPos();
            BlockEntity tileentity = this.player.level.getBlockEntity(blockposition);

            if (tileentity instanceof CommandBlockEntity) {
                tileentitycommand = (CommandBlockEntity) tileentity;
                commandblocklistenerabstract = tileentitycommand.getCommandBlock();
            }

            String s = packetplayinsetcommandblock.getCommand();
            boolean flag = packetplayinsetcommandblock.isTrackOutput();

            if (commandblocklistenerabstract != null) {
                CommandBlockEntity.Mode tileentitycommand_type = tileentitycommand.getMode();
                Direction enumdirection = (Direction) this.player.level.getType(blockposition).getValue(CommandBlock.FACING);
                BlockState iblockdata;

                switch (packetplayinsetcommandblock.getMode()) {
                    case SEQUENCE:
                        iblockdata = Blocks.CHAIN_COMMAND_BLOCK.getBlockData();
                        this.player.level.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(CommandBlock.FACING, enumdirection)).setValue(CommandBlock.CONDITIONAL, packetplayinsetcommandblock.isConditional()), 2);
                        break;
                    case AUTO:
                        iblockdata = Blocks.REPEATING_COMMAND_BLOCK.getBlockData();
                        this.player.level.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(CommandBlock.FACING, enumdirection)).setValue(CommandBlock.CONDITIONAL, packetplayinsetcommandblock.isConditional()), 2);
                        break;
                    case REDSTONE:
                    default:
                        iblockdata = Blocks.COMMAND_BLOCK.getBlockData();
                        this.player.level.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(CommandBlock.FACING, enumdirection)).setValue(CommandBlock.CONDITIONAL, packetplayinsetcommandblock.isConditional()), 2);
                }

                tileentity.clearRemoved();
                this.player.level.setBlockEntity(blockposition, tileentity);
                commandblocklistenerabstract.setCommand(s);
                commandblocklistenerabstract.setTrackOutput(flag);
                if (!flag) {
                    commandblocklistenerabstract.setLastOutput((Component) null);
                }

                tileentitycommand.setAutomatic(packetplayinsetcommandblock.isAutomatic());
                if (tileentitycommand_type != packetplayinsetcommandblock.getMode()) {
                    tileentitycommand.onModeSwitch();
                }

                commandblocklistenerabstract.onUpdated();
                if (!StringUtil.isNullOrEmpty(s)) {
                    this.player.sendMessage(new TranslatableComponent("advMode.setCommand.success", new Object[]{s}), Util.NIL_UUID);
                }
            }

        }
    }

    @Override
    public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packetplayinsetcommandminecart) {
        PacketUtils.ensureMainThread(packetplayinsetcommandminecart, this, this.player.getLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendMessage(new TranslatableComponent("advMode.notEnabled"), Util.NIL_UUID);
        } else if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendMessage(new TranslatableComponent("advMode.notAllowed"), Util.NIL_UUID);
        } else {
            BaseCommandBlock commandblocklistenerabstract = packetplayinsetcommandminecart.getCommandBlock(this.player.level);

            if (commandblocklistenerabstract != null) {
                commandblocklistenerabstract.setCommand(packetplayinsetcommandminecart.getCommand());
                commandblocklistenerabstract.setTrackOutput(packetplayinsetcommandminecart.isTrackOutput());
                if (!packetplayinsetcommandminecart.isTrackOutput()) {
                    commandblocklistenerabstract.setLastOutput((Component) null);
                }

                commandblocklistenerabstract.onUpdated();
                this.player.sendMessage(new TranslatableComponent("advMode.setCommand.success", new Object[]{packetplayinsetcommandminecart.getCommand()}), Util.NIL_UUID);
            }

        }
    }

    @Override
    public void handlePickItem(ServerboundPickItemPacket packetplayinpickitem) {
        PacketUtils.ensureMainThread(packetplayinpickitem, this, this.player.getLevel());
        this.player.inventory.pickSlot(packetplayinpickitem.getSlot());
        this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(-2, this.player.inventory.selected, this.player.inventory.getItem(this.player.inventory.selected)));
        this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(-2, packetplayinpickitem.getSlot(), this.player.inventory.getItem(packetplayinpickitem.getSlot())));
        this.player.connection.sendPacket(new ClientboundSetCarriedItemPacket(this.player.inventory.selected));
    }

    @Override
    public void handleRenameItem(ServerboundRenameItemPacket packetplayinitemname) {
        PacketUtils.ensureMainThread(packetplayinitemname, this, this.player.getLevel());
        if (this.player.containerMenu instanceof AnvilMenu) {
            AnvilMenu containeranvil = (AnvilMenu) this.player.containerMenu;
            String s = SharedConstants.filterText(packetplayinitemname.getName());

            if (s.length() <= 35) {
                containeranvil.setItemName(s);
            }
        }

    }

    @Override
    public void handleSetBeaconPacket(ServerboundSetBeaconPacket packetplayinbeacon) {
        PacketUtils.ensureMainThread(packetplayinbeacon, this, this.player.getLevel());
        if (this.player.containerMenu instanceof BeaconMenu) {
            ((BeaconMenu) this.player.containerMenu).updateEffects(packetplayinbeacon.getPrimary(), packetplayinbeacon.getSecondary());
        }

    }

    @Override
    public void handleSetStructureBlock(ServerboundSetStructureBlockPacket packetplayinstruct) {
        PacketUtils.ensureMainThread(packetplayinstruct, this, this.player.getLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockposition = packetplayinstruct.getPos();
            BlockState iblockdata = this.player.level.getType(blockposition);
            BlockEntity tileentity = this.player.level.getBlockEntity(blockposition);

            if (tileentity instanceof StructureBlockEntity) {
                StructureBlockEntity tileentitystructure = (StructureBlockEntity) tileentity;

                tileentitystructure.setMode(packetplayinstruct.getMode());
                tileentitystructure.setStructureName(packetplayinstruct.getName());
                tileentitystructure.setStructurePos(packetplayinstruct.getOffset());
                tileentitystructure.setStructureSize(packetplayinstruct.getSize());
                tileentitystructure.setMirror(packetplayinstruct.getMirror());
                tileentitystructure.setRotation(packetplayinstruct.getRotation());
                tileentitystructure.setMetaData(packetplayinstruct.getData());
                tileentitystructure.setIgnoreEntities(packetplayinstruct.isIgnoreEntities());
                tileentitystructure.setShowAir(packetplayinstruct.isShowAir());
                tileentitystructure.setShowBoundingBox(packetplayinstruct.isShowBoundingBox());
                tileentitystructure.setIntegrity(packetplayinstruct.getIntegrity());
                tileentitystructure.setSeed(packetplayinstruct.getSeed());
                if (tileentitystructure.hasStructureName()) {
                    String s = tileentitystructure.getStructureName();

                    if (packetplayinstruct.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                        if (tileentitystructure.saveStructure()) {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.save_success", new Object[]{s})), false);
                        } else {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.save_failure", new Object[]{s})), false);
                        }
                    } else if (packetplayinstruct.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                        if (!tileentitystructure.isStructureLoadable()) {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.load_not_found", new Object[]{s})), false);
                        } else if (tileentitystructure.loadStructure()) {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.load_success", new Object[]{s})), false);
                        } else {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.load_prepare", new Object[]{s})), false);
                        }
                    } else if (packetplayinstruct.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                        if (tileentitystructure.detectSize()) {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.size_success", new Object[]{s})), false);
                        } else {
                            this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.size_failure")), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage((Component) (new TranslatableComponent("structure_block.invalid_structure_name", new Object[]{packetplayinstruct.getName()})), false);
                }

                tileentitystructure.setChanged();
                this.player.level.notify(blockposition, iblockdata, iblockdata, 3);
            }

        }
    }

    @Override
    public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packetplayinsetjigsaw) {
        PacketUtils.ensureMainThread(packetplayinsetjigsaw, this, this.player.getLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockposition = packetplayinsetjigsaw.getPos();
            BlockState iblockdata = this.player.level.getType(blockposition);
            BlockEntity tileentity = this.player.level.getBlockEntity(blockposition);

            if (tileentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity tileentityjigsaw = (JigsawBlockEntity) tileentity;

                tileentityjigsaw.setName(packetplayinsetjigsaw.getName());
                tileentityjigsaw.setTarget(packetplayinsetjigsaw.getTarget());
                tileentityjigsaw.setPool(packetplayinsetjigsaw.getPool());
                tileentityjigsaw.setFinalState(packetplayinsetjigsaw.getFinalState());
                tileentityjigsaw.setJoint(packetplayinsetjigsaw.getJoint());
                tileentityjigsaw.setChanged();
                this.player.level.notify(blockposition, iblockdata, iblockdata, 3);
            }

        }
    }

    @Override
    public void handleJigsawGenerate(ServerboundJigsawGeneratePacket packetplayinjigsawgenerate) {
        PacketUtils.ensureMainThread(packetplayinjigsawgenerate, this, this.player.getLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockposition = packetplayinjigsawgenerate.getPos();
            BlockEntity tileentity = this.player.level.getBlockEntity(blockposition);

            if (tileentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity tileentityjigsaw = (JigsawBlockEntity) tileentity;

                tileentityjigsaw.generate(this.player.getLevel(), packetplayinjigsawgenerate.levels(), packetplayinjigsawgenerate.keepJigsaws());
            }

        }
    }

    @Override
    public void handleSelectTrade(ServerboundSelectTradePacket packetplayintrsel) {
        PacketUtils.ensureMainThread(packetplayintrsel, this, this.player.getLevel());
        int i = packetplayintrsel.getItem();
        AbstractContainerMenu container = this.player.containerMenu;

        if (container instanceof MerchantMenu) {
            MerchantMenu containermerchant = (MerchantMenu) container;
            CraftEventFactory.callTradeSelectEvent(this.player, i, containermerchant); // CraftBukkit

            containermerchant.setSelectionHint(i);
            containermerchant.tryMoveItems(i);
        }

    }

    @Override
    public void handleEditBook(ServerboundEditBookPacket packetplayinbedit) {
        PacketUtils.ensureMainThread(packetplayinbedit, this, this.player.getLevel());
        // CraftBukkit start
        if (this.lastBookTick + 20 > MinecraftServer.currentTick) {
            this.disconnect("Book edited too quickly!");
            return;
        }
        this.lastBookTick = MinecraftServer.currentTick;
        net.minecraft.world.entity.EquipmentSlot enumitemslot = packetplayinbedit.getHand() == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND;
        // CraftBukkit end
        ItemStack itemstack = packetplayinbedit.getBook();

        if (!itemstack.isEmpty()) {
            if (WritableBookItem.makeSureTagIsValid(itemstack.getTag())) {
                ItemStack itemstack1 = this.player.getItemInHand(packetplayinbedit.getHand());

                if (itemstack.getItem() == Items.WRITABLE_BOOK && itemstack1.getItem() == Items.WRITABLE_BOOK) {
                    if (packetplayinbedit.isSigning()) {
                        ItemStack itemstack2 = new ItemStack(Items.WRITTEN_BOOK);
                        CompoundTag nbttagcompound = itemstack1.getTag();

                        if (nbttagcompound != null) {
                            itemstack2.setTag(nbttagcompound.copy());
                        }

                        itemstack2.addTagElement("author", (Tag) StringTag.valueOf(this.player.getName().getString()));
                        itemstack2.addTagElement("title", (Tag) StringTag.valueOf(itemstack.getTag().getString("title")));
                        ListTag nbttaglist = itemstack.getTag().getList("pages", 8);

                        for (int i = 0; i < nbttaglist.size(); ++i) {
                            String s = nbttaglist.getString(i);
                            TextComponent chatcomponenttext = new TextComponent(s);

                            s = Component.ChatSerializer.a((Component) chatcomponenttext);
                            nbttaglist.set(i, (Tag) StringTag.valueOf(s));
                        }

                        itemstack2.addTagElement("pages", (Tag) nbttaglist);
                        this.player.setItemInHand(packetplayinbedit.getHand(), CraftEventFactory.handleEditBookEvent(player, enumitemslot, itemstack1, itemstack2)); // CraftBukkit
                    } else {
                        ItemStack old = itemstack1.copy(); // CraftBukkit
                        itemstack1.addTagElement("pages", (Tag) itemstack.getTag().getList("pages", 8));
                        CraftEventFactory.handleEditBookEvent(player, enumitemslot, old, itemstack1); // CraftBukkit
                    }
                }

            }
        }
    }

    @Override
    public void handleEntityTagQuery(ServerboundEntityTagQuery packetplayinentitynbtquery) {
        PacketUtils.ensureMainThread(packetplayinentitynbtquery, this, this.player.getLevel());
        if (this.player.hasPermissions(2)) {
            Entity entity = this.player.getLevel().getEntity(packetplayinentitynbtquery.getEntityId());

            if (entity != null) {
                CompoundTag nbttagcompound = entity.saveWithoutId(new CompoundTag());

                this.player.connection.sendPacket(new ClientboundTagQueryPacket(packetplayinentitynbtquery.getTransactionId(), nbttagcompound));
            }

        }
    }

    @Override
    public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQuery packetplayintilenbtquery) {
        PacketUtils.ensureMainThread(packetplayintilenbtquery, this, this.player.getLevel());
        if (this.player.hasPermissions(2)) {
            BlockEntity tileentity = this.player.getLevel().getBlockEntity(packetplayintilenbtquery.getPos());
            CompoundTag nbttagcompound = tileentity != null ? tileentity.save(new CompoundTag()) : null;

            this.player.connection.sendPacket(new ClientboundTagQueryPacket(packetplayintilenbtquery.getTransactionId(), nbttagcompound));
        }
    }

    @Override
    public void handleMovePlayer(ServerboundMovePlayerPacket packetplayinflying) {
        PacketUtils.ensureMainThread(packetplayinflying, this, this.player.getLevel());
        if (containsInvalidValues(packetplayinflying)) {
            this.disconnect(new TranslatableComponent("multiplayer.disconnect.invalid_player_movement"));
        } else {
            ServerLevel worldserver = this.player.getLevel();

            if (!this.player.wonGame && !this.player.isImmobile()) { // CraftBukkit
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (this.awaitingPositionFromClient != null) {
                    if (this.tickCount - this.awaitingTeleportTime > 20) {
                        this.awaitingTeleportTime = this.tickCount;
                        this.teleport(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.yRot, this.player.xRot);
                    }
                    this.allowedPlayerTicks = 20; // CraftBukkit
                } else {
                    this.awaitingTeleportTime = this.tickCount;
                    if (this.player.isPassenger()) {
                        this.player.absMoveTo(this.player.getX(), this.player.getY(), this.player.getZ(), packetplayinflying.getYRot(this.player.yRot), packetplayinflying.getXRot(this.player.xRot));
                        this.player.getLevel().getChunkSourceOH().move(this.player);
                        this.allowedPlayerTicks = 20; // CraftBukkit
                    } else {
                        // CraftBukkit - Make sure the move is valid but then reset it for plugins to modify
                        double prevX = player.getX();
                        double prevY = player.getY();
                        double prevZ = player.getZ();
                        float prevYaw = player.yRot;
                        float prevPitch = player.xRot;
                        // CraftBukkit end
                        double d0 = this.player.getX();
                        double d1 = this.player.getY();
                        double d2 = this.player.getZ();
                        double d3 = this.player.getY();
                        double d4 = packetplayinflying.getX(this.player.getX());
                        double d5 = packetplayinflying.getY(this.player.getY());
                        double d6 = packetplayinflying.getZ(this.player.getZ());
                        float f = packetplayinflying.getYRot(this.player.yRot);
                        float f1 = packetplayinflying.getXRot(this.player.xRot);
                        double d7 = d4 - this.firstGoodX;
                        double d8 = d5 - this.firstGoodY;
                        double d9 = d6 - this.firstGoodZ;
                        double d10 = this.player.getDeltaMovement().lengthSqr();
                        double d11 = d7 * d7 + d8 * d8 + d9 * d9;

                        if (this.player.isSleeping()) {
                            if (d11 > 1.0D) {
                                this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), packetplayinflying.getYRot(this.player.yRot), packetplayinflying.getXRot(this.player.xRot));
                            }

                        } else {
                            ++this.receivedMovePacketCount;
                            int i = this.receivedMovePacketCount - this.knownMovePacketCount;

                            // CraftBukkit start - handle custom speeds and skipped ticks
                            this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                            this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                            this.lastTick = (int) (System.currentTimeMillis() / 50);

                            if (i > Math.max(this.allowedPlayerTicks, 5)) {
                                ServerGamePacketListenerImpl.LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                                i = 1;
                            }

                            if (packetplayinflying.hasRot || d11 > 0) {
                                allowedPlayerTicks -= 1;
                            } else {
                                allowedPlayerTicks = 20;
                            }
                            double speed;
                            if (player.abilities.flying) {
                                speed = player.abilities.flyingSpeed * 20f;
                            } else {
                                speed = player.abilities.walkingSpeed * 10f;
                            }

                            if (!this.player.isChangingDimension() && (!this.player.getLevel().getGameRules().getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK) || !this.player.isFallFlying())) {
                                float f2 = this.player.isFallFlying() ? 300.0F : 100.0F;

                                if (d11 - d10 > Math.max(f2, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                                // CraftBukkit end
                                    ServerGamePacketListenerImpl.LOGGER.warn("{} moved too quickly! {},{},{}", this.player.getName().getString(), d7, d8, d9);
                                    this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.yRot, this.player.xRot);
                                    return;
                                }
                            }

                            AABB axisalignedbb = this.player.getBoundingBox();

                            d7 = d4 - this.lastGoodX;
                            d8 = d5 - this.lastGoodY;
                            d9 = d6 - this.lastGoodZ;
                            boolean flag = d8 > 0.0D;

                            if (this.player.isOnGround() && !packetplayinflying.isOnGround() && flag) {
                                this.player.jumpFromGround();
                            }

                            this.player.move(MoverType.PLAYER, new Vec3(d7, d8, d9));
                            double d12 = d8;

                            d7 = d4 - this.player.getX();
                            d8 = d5 - this.player.getY();
                            if (d8 > -0.5D || d8 < 0.5D) {
                                d8 = 0.0D;
                            }

                            d9 = d6 - this.player.getZ();
                            d11 = d7 * d7 + d8 * d8 + d9 * d9;
                            boolean flag1 = false;

                            if (!this.player.isChangingDimension() && d11 > org.spigotmc.SpigotConfig.movedWronglyThreshold && !this.player.isSleeping() && !this.player.gameMode.isCreative() && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) { // Spigot
                                flag1 = true;
                                ServerGamePacketListenerImpl.LOGGER.warn("{} moved wrongly!", this.player.getName().getString());
                            }

                            this.player.absMoveTo(d4, d5, d6, f, f1);
                            if (!this.player.noPhysics && !this.player.isSleeping() && (flag1 && worldserver.noCollision(this.player, axisalignedbb) || this.isPlayerCollidingWithAnythingNew((LevelReader) worldserver, axisalignedbb))) {
                                this.teleport(d0, d1, d2, f, f1);
                            } else {
                                // CraftBukkit start - fire PlayerMoveEvent
                                // Rest to old location first
                                this.player.absMoveTo(prevX, prevY, prevZ, prevYaw, prevPitch);

                                Player player = this.getPlayer();
                                Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (packetplayinflying.hasPos) {
                                    to.setX(packetplayinflying.x);
                                    to.setY(packetplayinflying.y);
                                    to.setZ(packetplayinflying.z);
                                }

                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (packetplayinflying.hasRot) {
                                    to.setYaw(packetplayinflying.yRot);
                                    to.setPitch(packetplayinflying.xRot);
                                }

                                // Prevent 40 event-calls for less than a single pixel of movement >.>
                                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                                    this.lastPosX = to.getX();
                                    this.lastPosY = to.getY();
                                    this.lastPosZ = to.getZ();
                                    this.lastYaw = to.getYaw();
                                    this.lastPitch = to.getPitch();

                                    // Skip the first time we do this
                                    if (from.getX() != Double.MAX_VALUE) {
                                        Location oldTo = to.clone();
                                        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                                        this.craftServer.getPluginManager().callEvent(event);

                                        // If the event is cancelled we move the player back to their old location.
                                        if (event.isCancelled()) {
                                            teleport(from);
                                            return;
                                        }

                                        // If a Plugin has changed the To destination then we teleport the Player
                                        // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                                        // We only do this if the Event was not cancelled.
                                        if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                                            this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                                            return;
                                        }

                                        // Check to see if the Players Location has some how changed during the call of the event.
                                        // This can happen due to a plugin teleporting the player instead of using .setTo()
                                        if (!from.equals(this.getPlayer().getLocation()) && this.justTeleported) {
                                            this.justTeleported = false;
                                            return;
                                        }
                                    }
                                }
                                this.player.absMoveTo(d4, d5, d6, f, f1); // Copied from above

                                // MC-135989, SPIGOT-5564: isRiptiding
                                this.clientIsFloating = d12 >= -0.03125D && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && !this.server.isFlightAllowed() && !this.player.abilities.mayfly && !this.player.hasEffect(MobEffects.LEVITATION) && !this.player.isFallFlying() && this.noBlocksAround((Entity) this.player) && !this.player.isAutoSpinAttack();
                                // CraftBukkit end
                                this.player.getLevel().getChunkSourceOH().move(this.player);
                                this.player.doCheckFallDamage(this.player.getY() - d3, packetplayinflying.isOnGround());
                                this.player.setOnGround(packetplayinflying.isOnGround());
                                if (flag) {
                                    this.player.fallDistance = 0.0F;
                                }

                                this.player.checkMovementStatistics(this.player.getX() - d0, this.player.getY() - d1, this.player.getZ() - d2);
                                this.lastGoodX = this.player.getX();
                                this.lastGoodY = this.player.getY();
                                this.lastGoodZ = this.player.getZ();
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isPlayerCollidingWithAnythingNew(LevelReader iworldreader, AABB axisalignedbb) {
        Stream<VoxelShape> stream = iworldreader.getCollisions(this.player, this.player.getBoundingBox().deflate(9.999999747378752E-6D), (entity) -> {
            return true;
        });
        VoxelShape voxelshape = Shapes.create(axisalignedbb.deflate(9.999999747378752E-6D));

        return stream.anyMatch((voxelshape1) -> {
            return !Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.AND);
        });
    }

    public void teleport(double d0, double d1, double d2, float f, float f1) {
        this.teleport(d0, d1, d2, f, f1, Collections.<ClientboundPlayerPositionPacket.RelativeArgument>emptySet());
    }

    // CraftBukkit start - Delegate to teleport(Location)
    public void a(double d0, double d1, double d2, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
        this.a(d0, d1, d2, f, f1, Collections.<ClientboundPlayerPositionPacket.RelativeArgument>emptySet(), cause);
    }

    public void teleport(double d0, double d1, double d2, float f, float f1, Set<ClientboundPlayerPositionPacket.RelativeArgument> set) {
        this.a(d0, d1, d2, f, f1, set, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public void a(double d0, double d1, double d2, float f, float f1, Set<ClientboundPlayerPositionPacket.RelativeArgument> set, PlayerTeleportEvent.TeleportCause cause) {
        Player player = this.getPlayer();
        Location from = player.getLocation();

        double x = d0;
        double y = d1;
        double z = d2;
        float yaw = f;
        float pitch = f1;

        Location to = new Location(this.getPlayer().getWorld(), x, y, z, yaw, pitch);
        // SPIGOT-5171: Triggered on join
        if (from.equals(to)) {
            this.internalTeleport(d0, d1, d2, f, f1, set);
            return;
        }

        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.clone(), to.clone(), cause);
        this.craftServer.getPluginManager().callEvent(event);

        if (event.isCancelled() || !to.equals(event.getTo())) {
            set.clear(); // Can't relative teleport
            to = event.isCancelled() ? event.getFrom() : event.getTo();
            d0 = to.getX();
            d1 = to.getY();
            d2 = to.getZ();
            f = to.getYaw();
            f1 = to.getPitch();
        }

        this.internalTeleport(d0, d1, d2, f, f1, set);
    }

    public void teleport(Location dest) {
        internalTeleport(dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch(), Collections.<ClientboundPlayerPositionPacket.RelativeArgument>emptySet());
    }

    private void internalTeleport(double d0, double d1, double d2, float f, float f1, Set<ClientboundPlayerPositionPacket.RelativeArgument> set) {
        // CraftBukkit start
        if (Float.isNaN(f)) {
            f = 0;
        }
        if (Float.isNaN(f1)) {
            f1 = 0;
        }

        this.justTeleported = true;
        // CraftBukkit end
        double d3 = set.contains(ClientboundPlayerPositionPacket.RelativeArgument.X) ? this.player.getX() : 0.0D;
        double d4 = set.contains(ClientboundPlayerPositionPacket.RelativeArgument.Y) ? this.player.getY() : 0.0D;
        double d5 = set.contains(ClientboundPlayerPositionPacket.RelativeArgument.Z) ? this.player.getZ() : 0.0D;
        float f2 = set.contains(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT) ? this.player.yRot : 0.0F;
        float f3 = set.contains(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT) ? this.player.xRot : 0.0F;

        this.awaitingPositionFromClient = new Vec3(d0, d1, d2);
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        // CraftBukkit start - update last location
        this.lastPosX = this.awaitingPositionFromClient.x;
        this.lastPosY = this.awaitingPositionFromClient.y;
        this.lastPosZ = this.awaitingPositionFromClient.z;
        this.lastYaw = f;
        this.lastPitch = f1;
        // CraftBukkit end

        this.awaitingTeleportTime = this.tickCount;
        this.player.absMoveTo(d0, d1, d2, f, f1);
        this.player.connection.sendPacket(new ClientboundPlayerPositionPacket(d0 - d3, d1 - d4, d2 - d5, f - f2, f1 - f3, set, this.awaitingTeleport));
    }

    @Override
    public void handlePlayerAction(ServerboundPlayerActionPacket packetplayinblockdig) {
        PacketUtils.ensureMainThread(packetplayinblockdig, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        BlockPos blockposition = packetplayinblockdig.getPos();

        this.player.resetLastActionTime();
        ServerboundPlayerActionPacket.Action packetplayinblockdig_enumplayerdigtype = packetplayinblockdig.getAction();

        switch (packetplayinblockdig_enumplayerdigtype) {
            case SWAP_ITEM_WITH_OFFHAND:
                if (!this.player.isSpectator()) {
                    ItemStack itemstack = this.player.getItemInHand(InteractionHand.OFF_HAND);

                    // CraftBukkit start - inspiration taken from DispenserRegistry (See SpigotCraft#394)
                    CraftItemStack mainHand = CraftItemStack.asCraftMirror(itemstack);
                    CraftItemStack offHand = CraftItemStack.asCraftMirror(this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    PlayerSwapHandItemsEvent swapItemsEvent = new PlayerSwapHandItemsEvent(getPlayer(), mainHand.clone(), offHand.clone());
                    this.craftServer.getPluginManager().callEvent(swapItemsEvent);
                    if (swapItemsEvent.isCancelled()) {
                        return;
                    }
                    if (swapItemsEvent.getOffHandItem().equals(offHand)) {
                        this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    } else {
                        this.player.setItemInHand(InteractionHand.OFF_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getOffHandItem()));
                    }
                    if (swapItemsEvent.getMainHandItem().equals(mainHand)) {
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, itemstack);
                    } else {
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getMainHandItem()));
                    }
                    // CraftBukkit end
                    this.player.stopUsingItem();
                }

                return;
            case DROP_ITEM:
                if (!this.player.isSpectator()) {
                    // limit how quickly items can be dropped
                    // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
                    if (this.lastDropTick != MinecraftServer.currentTick) {
                        this.dropCount = 0;
                        this.lastDropTick = MinecraftServer.currentTick;
                    } else {
                        // Else we increment the drop count and check the amount.
                        this.dropCount++;
                        if (this.dropCount >= 20) {
                            LOGGER.warn(this.player.getScoreboardName() + " dropped their items too quickly!");
                            this.disconnect("You dropped your items too quickly (Hacking?)");
                            return;
                        }
                    }
                    // CraftBukkit end
                    this.player.drop(false);
                }

                return;
            case DROP_ALL_ITEMS:
                if (!this.player.isSpectator()) {
                    this.player.drop(true);
                }

                return;
            case RELEASE_USE_ITEM:
                this.player.releaseUsingItem();
                return;
            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
                this.player.gameMode.handleBlockBreakAction(blockposition, packetplayinblockdig_enumplayerdigtype, packetplayinblockdig.getDirection(), this.server.getMaxBuildHeight());
                return;
            default:
                throw new IllegalArgumentException("Invalid player action");
        }
    }

    private static boolean wasBlockPlacementAttempt(ServerPlayer entityplayer, ItemStack itemstack) {
        if (itemstack.isEmpty()) {
            return false;
        } else {
            Item item = itemstack.getItem();

            return (item instanceof BlockItem || item instanceof BucketItem) && !entityplayer.getCooldowns().isOnCooldown(item);
        }
    }

    // Spigot start - limit place/interactions
    private int limitedPackets;
    private long lastLimitedPacket = -1;

    private boolean checkLimit(long timestamp) {
        if (lastLimitedPacket != -1 && timestamp - lastLimitedPacket < 30 && limitedPackets++ >= 4) {
            return false;
        }

        if (lastLimitedPacket == -1 || timestamp - lastLimitedPacket >= 30) {
            lastLimitedPacket = timestamp;
            limitedPackets = 0;
            return true;
        }

        return true;
    }
    // Spigot end

    @Override
    public void handleUseItemOn(ServerboundUseItemOnPacket packetplayinuseitem) {
        PacketUtils.ensureMainThread(packetplayinuseitem, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!checkLimit(packetplayinuseitem.timestamp)) return; // Spigot - check limit
        ServerLevel worldserver = this.player.getLevel();
        InteractionHand enumhand = packetplayinuseitem.getHand();
        ItemStack itemstack = this.player.getItemInHand(enumhand);
        BlockHitResult movingobjectpositionblock = packetplayinuseitem.getHitResult();
        BlockPos blockposition = movingobjectpositionblock.getBlockPos();
        Direction enumdirection = movingobjectpositionblock.getDirection();

        this.player.resetLastActionTime();
        if (blockposition.getY() < this.server.getMaxBuildHeight()) {
            if (this.awaitingPositionFromClient == null && this.player.distanceToSqr((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D) < 64.0D && worldserver.mayInteract((net.minecraft.world.entity.player.Player) this.player, blockposition)) {
                // CraftBukkit start - Check if we can actually do something over this large a distance
                Location eyeLoc = this.getPlayer().getEyeLocation();
                double reachDistance = NumberConversions.square(eyeLoc.getX() - blockposition.getX()) + NumberConversions.square(eyeLoc.getY() - blockposition.getY()) + NumberConversions.square(eyeLoc.getZ() - blockposition.getZ());
                if (reachDistance > (this.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE ? CREATIVE_PLACE_DISTANCE_SQUARED : SURVIVAL_PLACE_DISTANCE_SQUARED)) {
                    return;
                }
                this.player.stopUsingItem(); // SPIGOT-4706
                // CraftBukkit end
                InteractionResult enuminteractionresult = this.player.gameMode.useItemOn(this.player, worldserver, itemstack, enumhand, movingobjectpositionblock);

                if (enumdirection == Direction.UP && !enuminteractionresult.consumesAction() && blockposition.getY() >= this.server.getMaxBuildHeight() - 1 && wasBlockPlacementAttempt(this.player, itemstack)) {
                    MutableComponent ichatmutablecomponent = (new TranslatableComponent("build.tooHigh", new Object[]{this.server.getMaxBuildHeight()})).withStyle(ChatFormatting.RED);

                    this.player.connection.sendPacket(new ClientboundChatPacket(ichatmutablecomponent, ChatType.GAME_INFO, Util.NIL_UUID));
                } else if (enuminteractionresult.shouldSwing()) {
                    this.player.swing(enumhand, true);
                }
            }
        } else {
            MutableComponent ichatmutablecomponent1 = (new TranslatableComponent("build.tooHigh", new Object[]{this.server.getMaxBuildHeight()})).withStyle(ChatFormatting.RED);

            this.player.connection.sendPacket(new ClientboundChatPacket(ichatmutablecomponent1, ChatType.GAME_INFO, Util.NIL_UUID));
        }

        this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(worldserver, blockposition));
        this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(worldserver, blockposition.relative(enumdirection)));
    }

    @Override
    public void handleUseItem(ServerboundUseItemPacket packetplayinblockplace) {
        PacketUtils.ensureMainThread(packetplayinblockplace, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!checkLimit(packetplayinblockplace.timestamp)) return; // Spigot - check limit
        ServerLevel worldserver = this.player.getLevel();
        InteractionHand enumhand = packetplayinblockplace.getHand();
        ItemStack itemstack = this.player.getItemInHand(enumhand);

        this.player.resetLastActionTime();
        if (!itemstack.isEmpty()) {
            // CraftBukkit start
            // Raytrace to look for 'rogue armswings'
            float f1 = this.player.xRot;
            float f2 = this.player.yRot;
            double d0 = this.player.getX();
            double d1 = this.player.getY() + (double) this.player.getEyeHeight();
            double d2 = this.player.getZ();
            Vec3 vec3d = new Vec3(d0, d1, d2);

            float f3 = Mth.cos(-f2 * 0.017453292F - 3.1415927F);
            float f4 = Mth.sin(-f2 * 0.017453292F - 3.1415927F);
            float f5 = -Mth.cos(-f1 * 0.017453292F);
            float f6 = Mth.sin(-f1 * 0.017453292F);
            float f7 = f4 * f5;
            float f8 = f3 * f5;
            double d3 = player.gameMode.getGameModeForPlayer()== GameType.CREATIVE ? 5.0D : 4.5D;
            Vec3 vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
            HitResult movingobjectposition = this.player.level.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

            boolean cancelled;
            if (movingobjectposition == null || movingobjectposition.getType() != HitResult.Type.BLOCK) {
                org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemstack, enumhand);
                cancelled = event.useItemInHand() == Event.Result.DENY;
            } else {
                if (player.gameMode.firedInteract) {
                    player.gameMode.firedInteract = false;
                    cancelled = player.gameMode.interactResult;
                } else {
                    BlockHitResult movingobjectpositionblock = (BlockHitResult) movingobjectposition;
                    org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), itemstack, true, enumhand);
                    cancelled = event.useItemInHand() == Event.Result.DENY;
                }
            }

            if (cancelled) {
                this.player.getBukkitEntity().updateInventory(); // SPIGOT-2524
                return;
            }
            InteractionResult enuminteractionresult = this.player.gameMode.useItem(this.player, worldserver, itemstack, enumhand);

            if (enuminteractionresult.shouldSwing()) {
                this.player.swing(enumhand, true);
            }

        }
    }

    @Override
    public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packetplayinspectate) {
        PacketUtils.ensureMainThread(packetplayinspectate, this, this.player.getLevel());
        if (this.player.isSpectator()) {
            Iterator iterator = this.server.getAllLevels().iterator();

            while (iterator.hasNext()) {
                ServerLevel worldserver = (ServerLevel) iterator.next();
                Entity entity = packetplayinspectate.getEntity(worldserver);

                if (entity != null) {
                    this.player.a(worldserver, entity.getX(), entity.getY(), entity.getZ(), entity.yRot, entity.xRot, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE); // CraftBukkit
                    return;
                }
            }
        }

    }

    @Override
    // CraftBukkit start
    public void handleResourcePackResponse(ServerboundResourcePackPacket packetplayinresourcepackstatus) {
        PacketUtils.ensureMainThread(packetplayinresourcepackstatus, this, this.player.getLevel());
        this.craftServer.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(getPlayer(), PlayerResourcePackStatusEvent.Status.values()[packetplayinresourcepackstatus.action.ordinal()]));
    }
    // CraftBukkit end

    @Override
    public void handlePaddleBoat(ServerboundPaddleBoatPacket packetplayinboatmove) {
        PacketUtils.ensureMainThread(packetplayinboatmove, this, this.player.getLevel());
        Entity entity = this.player.getVehicle();

        if (entity instanceof Boat) {
            ((Boat) entity).setPaddleState(packetplayinboatmove.getLeft(), packetplayinboatmove.getRight());
        }

    }

    @Override
    public void onDisconnect(Component ichatbasecomponent) {
        // CraftBukkit start - Rarely it would send a disconnect line twice
        if (this.processedDisconnect) {
            return;
        } else {
            this.processedDisconnect = true;
        }
        // CraftBukkit end
        ServerGamePacketListenerImpl.LOGGER.info("{} lost connection: {}", this.player.getName().getString(), ichatbasecomponent.getString());
        // CraftBukkit start - Replace vanilla quit message handling with our own.
        /*
        this.minecraftServer.invalidatePingSample();
        this.minecraftServer.getPlayerList().sendMessage((new ChatMessage("multiplayer.player.left", new Object[]{this.player.getScoreboardDisplayName()})).a(EnumChatFormat.YELLOW), ChatMessageType.SYSTEM, SystemUtils.b);
        */

        this.player.disconnect();
        String quitMessage = this.server.getPlayerList().disconnect(this.player);
        if ((quitMessage != null) && (quitMessage.length() > 0)) {
            this.server.getPlayerList().sendMessage(CraftChatMessage.fromString(quitMessage));
        }
        // CraftBukkit end
        if (this.isSingleplayerOwner()) {
            ServerGamePacketListenerImpl.LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }

    }

    public void sendPacket(Packet<?> packet) {
        this.send(packet, (GenericFutureListener) null);
    }

    public void send(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
        if (packet instanceof ClientboundChatPacket) {
            ClientboundChatPacket packetplayoutchat = (ClientboundChatPacket) packet;
            ChatVisiblity enumchatvisibility = this.player.getChatVisibility();

            if (enumchatvisibility == ChatVisiblity.HIDDEN && packetplayoutchat.getType() != ChatType.GAME_INFO) {
                return;
            }

            if (enumchatvisibility == ChatVisiblity.SYSTEM && !packetplayoutchat.isSystem()) {
                return;
            }
        }

        // CraftBukkit start
        if (packet == null || this.processedDisconnect) { // Spigot
            return;
        } else if (packet instanceof ClientboundSetDefaultSpawnPositionPacket) {
            ClientboundSetDefaultSpawnPositionPacket packet6 = (ClientboundSetDefaultSpawnPositionPacket) packet;
            this.player.compassTarget = new Location(this.getPlayer().getWorld(), packet6.pos.getX(), packet6.pos.getY(), packet6.pos.getZ());
        }
        // CraftBukkit end

        try {
            this.connection.sendPacketOH(packet, genericfuturelistener);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Packet being sent");

            crashreportsystemdetails.setDetail("Packet class", () -> {
                return packet.getClass().getCanonicalName();
            });
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public void handleSetCarriedItem(ServerboundSetCarriedItemPacket packetplayinhelditemslot) {
        PacketUtils.ensureMainThread(packetplayinhelditemslot, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (packetplayinhelditemslot.getSlot() >= 0 && packetplayinhelditemslot.getSlot() < Inventory.getSelectionSize()) {
            PlayerItemHeldEvent event = new PlayerItemHeldEvent(this.getPlayer(), this.player.inventory.selected, packetplayinhelditemslot.getSlot());
            this.craftServer.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.sendPacket(new ClientboundSetCarriedItemPacket(this.player.inventory.selected));
                this.player.resetLastActionTime();
                return;
            }
            // CraftBukkit end
            if (this.player.inventory.selected != packetplayinhelditemslot.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.inventory.selected = packetplayinhelditemslot.getSlot();
            this.player.resetLastActionTime();
        } else {
            ServerGamePacketListenerImpl.LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect("Invalid hotbar selection (Hacking?)"); // CraftBukkit
        }
    }

    @Override
    public void handleChat(ServerboundChatPacket packetplayinchat) {
        // CraftBukkit start - async chat
        // SPIGOT-3638
        if (this.server.isStopped()) {
            return;
        }

        boolean isSync = packetplayinchat.getMessage().startsWith("/");
        if (packetplayinchat.getMessage().startsWith("/")) {
            PacketUtils.ensureMainThread(packetplayinchat, this, this.player.getLevel());
        }
        // CraftBukkit end
        if (this.player.removed || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) { // CraftBukkit - dead men tell no tales
            this.sendPacket(new ClientboundChatPacket((new TranslatableComponent("chat.cannotSend")).withStyle(ChatFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID));
        } else {
            this.player.resetLastActionTime();
            String s = StringUtils.normalizeSpace(packetplayinchat.getMessage());

            for (int i = 0; i < s.length(); ++i) {
                if (!SharedConstants.isAllowedChatCharacter(s.charAt(i))) {
                    // CraftBukkit start - threadsafety
                    if (!isSync) {
                        Waitable waitable = new Waitable() {
                            @Override
                            protected Object evaluate() {
                                ServerGamePacketListenerImpl.this.disconnect(new TranslatableComponent("multiplayer.disconnect.illegal_characters"));
                                return null;
                            }
                        };

                        this.server.processQueue.add(waitable);

                        try {
                            waitable.get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        this.disconnect(new TranslatableComponent("multiplayer.disconnect.illegal_characters"));
                    }
                    // CraftBukkit end
                    return;
                }
            }

            // CraftBukkit start
            if (isSync) {
                try {
                    this.server.server.playerCommandState = true;
                    this.handleCommand(s);
                } finally {
                    this.server.server.playerCommandState = false;
                }
            } else if (s.isEmpty()) {
                LOGGER.warn(this.player.getScoreboardName() + " tried to send an empty message");
            } else if (getPlayer().isConversing()) {
                final String conversationInput = s;
                this.server.processQueue.add(new Runnable() {
                    @Override
                    public void run() {
                        getPlayer().acceptConversationInput(conversationInput);
                    }
                });
            } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) { // Re-add "Command Only" flag check
                this.sendPacket(new ClientboundChatPacket((new TranslatableComponent("chat.cannotSend")).withStyle(ChatFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID));
            } else if (true) {
                this.chat(s, true);
                // CraftBukkit end - the below is for reference. :)
            } else {
                TranslatableComponent chatmessage = new TranslatableComponent("chat.type.text", new Object[]{this.player.getDisplayName(), s});

                this.server.getPlayerList().broadcastMessage(chatmessage, ChatType.CHAT, this.player.getUUID());
            }

            // Spigot start - spam exclusions
            boolean counted = true;
            for ( String exclude : org.spigotmc.SpigotConfig.spamExclusions )
            {
                if ( exclude != null && s.startsWith( exclude ) )
                {
                    counted = false;
                    break;
                }
            }
            // Spigot end
            // CraftBukkit start - replaced with thread safe throttle
            // this.chatThrottle += 20;
            if (counted && chatSpamField.addAndGet(this, 20) > 200 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) { // Spigot
                if (!isSync) {
                    Waitable waitable = new Waitable() {
                        @Override
                        protected Object evaluate() {
                            ServerGamePacketListenerImpl.this.disconnect(new TranslatableComponent("disconnect.spam"));
                            return null;
                        }
                    };

                    this.server.processQueue.add(waitable);

                    try {
                        waitable.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    this.disconnect(new TranslatableComponent("disconnect.spam"));
                }
                // CraftBukkit end
            }

        }
    }

    // CraftBukkit start - add method
    public void chat(String s, boolean async) {
        if (s.isEmpty() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }

        if (!async && s.startsWith("/")) {
            this.handleCommand(s);
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) {
            // Do nothing, this is coming from a plugin
        } else {
            Player player = this.getPlayer();
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, player, s, new LazyPlayerSet(server));
            this.craftServer.getPluginManager().callEvent(event);

            if (PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
                // Evil plugins still listening to deprecated event
                final PlayerChatEvent queueEvent = new PlayerChatEvent(player, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());
                Waitable waitable = new Waitable() {
                    @Override
                    protected Object evaluate() {
                        org.bukkit.Bukkit.getPluginManager().callEvent(queueEvent);

                        if (queueEvent.isCancelled()) {
                            return null;
                        }

                        String message = String.format(queueEvent.getFormat(), queueEvent.getPlayer().getDisplayName(), queueEvent.getMessage());
                        ServerGamePacketListenerImpl.this.server.console.sendMessage(message);
                        if (((LazyPlayerSet) queueEvent.getRecipients()).isLazy()) {
                            for (Object player : ServerGamePacketListenerImpl.this.server.getPlayerList().players) {
                                ((ServerPlayer) player).sendMessage(CraftChatMessage.fromString(message));
                            }
                        } else {
                            for (Player player : queueEvent.getRecipients()) {
                                player.sendMessage(message);
                            }
                        }
                        return null;
                    }};
                if (async) {
                    server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }
                try {
                    waitable.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // This is proper habit for java. If we aren't handling it, pass it on!
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            } else {
                if (event.isCancelled()) {
                    return;
                }

                s = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
                server.console.sendMessage(s);
                if (((LazyPlayerSet) event.getRecipients()).isLazy()) {
                    for (Object recipient : server.getPlayerList().players) {
                        ((ServerPlayer) recipient).sendMessage(CraftChatMessage.fromString(s));
                    }
                } else {
                    for (Player recipient : event.getRecipients()) {
                        recipient.sendMessage(s);
                    }
                }
            }
        }
    }
    // CraftBukkit end

    private void handleCommand(String s) {
        org.bukkit.craftbukkit.SpigotTimings.playerCommandTimer.startTiming(); // Spigot
        // CraftBukkit start - whole method
        if ( org.spigotmc.SpigotConfig.logCommands ) // Spigot
        this.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + s);

        CraftPlayer player = this.getPlayer();

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, s, new LazyPlayerSet(server));
        this.craftServer.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            org.bukkit.craftbukkit.SpigotTimings.playerCommandTimer.stopTiming(); // Spigot
            return;
        }

        try {
            if (this.craftServer.dispatchCommand(event.getPlayer(), event.getMessage().substring(1))) {
                return;
            }
        } catch (org.bukkit.command.CommandException ex) {
            player.sendMessage(org.bukkit.ChatColor.RED + "An internal error occurred while attempting to perform this command");
            java.util.logging.Logger.getLogger(ServerGamePacketListenerImpl.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            return;
        } finally {
            org.bukkit.craftbukkit.SpigotTimings.playerCommandTimer.stopTiming(); // Spigot
        }
        // this.minecraftServer.getCommandDispatcher().a(this.player.getCommandListener(), s);
        // CraftBukkit end
    }

    @Override
    public void handleAnimate(ServerboundSwingPacket packetplayinarmanimation) {
        PacketUtils.ensureMainThread(packetplayinarmanimation, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        // CraftBukkit start - Raytrace to look for 'rogue armswings'
        float f1 = this.player.xRot;
        float f2 = this.player.yRot;
        double d0 = this.player.getX();
        double d1 = this.player.getY() + (double) this.player.getEyeHeight();
        double d2 = this.player.getZ();
        Vec3 vec3d = new Vec3(d0, d1, d2);

        float f3 = Mth.cos(-f2 * 0.017453292F - 3.1415927F);
        float f4 = Mth.sin(-f2 * 0.017453292F - 3.1415927F);
        float f5 = -Mth.cos(-f1 * 0.017453292F);
        float f6 = Mth.sin(-f1 * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = player.gameMode.getGameModeForPlayer()== GameType.CREATIVE ? 5.0D : 4.5D;
        Vec3 vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
        HitResult movingobjectposition = this.player.level.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (movingobjectposition == null || movingobjectposition.getType() != HitResult.Type.BLOCK) {
            CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.inventory.getSelected(), InteractionHand.MAIN_HAND);
        }

        // Arm swing animation
        PlayerAnimationEvent event = new PlayerAnimationEvent(this.getPlayer());
        this.craftServer.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;
        // CraftBukkit end
        this.player.swing(packetplayinarmanimation.getHand());
    }

    @Override
    public void handlePlayerCommand(ServerboundPlayerCommandPacket packetplayinentityaction) {
        PacketUtils.ensureMainThread(packetplayinentityaction, this, this.player.getLevel());
        // CraftBukkit start
        if (this.player.removed) return;
        switch (packetplayinentityaction.getAction()) {
            case PRESS_SHIFT_KEY:
            case RELEASE_SHIFT_KEY:
                PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getPlayer(), packetplayinentityaction.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY);
                this.craftServer.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                break;
            case START_SPRINTING:
            case STOP_SPRINTING:
                PlayerToggleSprintEvent e2 = new PlayerToggleSprintEvent(this.getPlayer(), packetplayinentityaction.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING);
                this.craftServer.getPluginManager().callEvent(e2);

                if (e2.isCancelled()) {
                    return;
                }
                break;
        }
        // CraftBukkit end
        this.player.resetLastActionTime();
        PlayerRideableJumping ijumpable;

        switch (packetplayinentityaction.getAction()) {
            case PRESS_SHIFT_KEY:
                this.player.setShiftKeyDown(true);
                break;
            case RELEASE_SHIFT_KEY:
                this.player.setShiftKeyDown(false);
                break;
            case START_SPRINTING:
                this.player.setSprinting(true);
                break;
            case STOP_SPRINTING:
                this.player.setSprinting(false);
                break;
            case STOP_SLEEPING:
                if (this.player.isSleeping()) {
                    this.player.stopSleepInBed(false, true);
                    this.awaitingPositionFromClient = this.player.position();
                }
                break;
            case START_RIDING_JUMP:
                if (this.player.getVehicle() instanceof PlayerRideableJumping) {
                    ijumpable = (PlayerRideableJumping) this.player.getVehicle();
                    int i = packetplayinentityaction.getData();

                    if (ijumpable.canJump() && i > 0) {
                        ijumpable.handleStartJump(i);
                    }
                }
                break;
            case STOP_RIDING_JUMP:
                if (this.player.getVehicle() instanceof PlayerRideableJumping) {
                    ijumpable = (PlayerRideableJumping) this.player.getVehicle();
                    ijumpable.handleStopJump();
                }
                break;
            case OPEN_INVENTORY:
                if (this.player.getVehicle() instanceof AbstractHorse) {
                    ((AbstractHorse) this.player.getVehicle()).openInventory((net.minecraft.world.entity.player.Player) this.player);
                }
                break;
            case START_FALL_FLYING:
                if (!this.player.tryToStartFallFlying()) {
                    this.player.stopFallFlying();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid client command!");
        }

    }

    @Override
    public void handleInteract(ServerboundInteractPacket packetplayinuseentity) {
        PacketUtils.ensureMainThread(packetplayinuseentity, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        ServerLevel worldserver = this.player.getLevel();
        Entity entity = packetplayinuseentity.getTarget((Level) worldserver);
        // Spigot Start
        if ( entity == player && !player.isSpectator() )
        {
            disconnect( "Cannot interact with self!" );
            return;
        }
        // Spigot End

        this.player.resetLastActionTime();
        this.player.setShiftKeyDown(packetplayinuseentity.isUsingSecondaryAction());
        if (entity != null) {
            double d0 = 36.0D;

            if (this.player.distanceToSqr(entity) < 36.0D) {
                InteractionHand enumhand = packetplayinuseentity.getHand();
                Optional<InteractionResult> optional = Optional.empty();

                ItemStack itemInHand = this.player.getItemInHand(packetplayinuseentity.getHand() == null ? InteractionHand.MAIN_HAND : packetplayinuseentity.getHand()); // CraftBukkit

                if (packetplayinuseentity.getAction() == ServerboundInteractPacket.Action.INTERACT
                        || packetplayinuseentity.getAction() == ServerboundInteractPacket.Action.INTERACT_AT) {
                    // CraftBukkit start
                    boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && entity instanceof Mob;
                    Item origItem = this.player.inventory.getSelected() == null ? null : this.player.inventory.getSelected().getItem();
                    PlayerInteractEntityEvent event;
                    if (packetplayinuseentity.getAction() == ServerboundInteractPacket.Action.INTERACT) {
                        event = new PlayerInteractEntityEvent((Player) this.getPlayer(), entity.getBukkitEntity(), (packetplayinuseentity.getHand() == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                    } else {
                        Vec3 target = packetplayinuseentity.getLocation();
                        event = new PlayerInteractAtEntityEvent((Player) this.getPlayer(), entity.getBukkitEntity(), new org.bukkit.util.Vector(target.x, target.y, target.z), (packetplayinuseentity.getHand() == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                    }
                    this.craftServer.getPluginManager().callEvent(event);

                    // Fish bucket - SPIGOT-4048
                    if ((entity instanceof AbstractFish && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || this.player.inventory.getSelected() == null || this.player.inventory.getSelected().getItem() != origItem)) {
                        this.sendPacket(new ClientboundAddMobPacket((AbstractFish) entity));
                        this.player.refreshContainer(this.player.containerMenu);
                    }

                    if (triggerLeashUpdate && (event.isCancelled() || this.player.inventory.getSelected() == null || this.player.inventory.getSelected().getItem() != origItem)) {
                        // Refresh the current leash state
                        this.sendPacket(new ClientboundSetEntityLinkPacket(entity, ((Mob) entity).getLeashHolder()));
                    }

                    if (event.isCancelled() || this.player.inventory.getSelected() == null || this.player.inventory.getSelected().getItem() != origItem) {
                        // Refresh the current entity metadata
                        this.sendPacket(new ClientboundSetEntityDataPacket(entity.getId(), entity.entityData, true));
                    }

                    if (event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                }

                if (packetplayinuseentity.getAction() == ServerboundInteractPacket.Action.INTERACT) {
                    optional = Optional.of(this.player.interactOn(entity, enumhand));
                    // CraftBukkit start
                    if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                        this.player.refreshContainer(this.player.containerMenu);
                    }
                    // CraftBukkit end
                } else if (packetplayinuseentity.getAction() == ServerboundInteractPacket.Action.INTERACT_AT) {
                    optional = Optional.of(entity.interactAt((net.minecraft.world.entity.player.Player) this.player, packetplayinuseentity.getLocation(), enumhand));
                    // CraftBukkit start
                    if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                        this.player.refreshContainer(this.player.containerMenu);
                    }
                    // CraftBukkit end
                } else if (packetplayinuseentity.getAction() == ServerboundInteractPacket.Action.ATTACK) {
                    if (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof AbstractArrow || (entity == this.player && !player.isSpectator())) { // CraftBukkit
                        this.disconnect(new TranslatableComponent("multiplayer.disconnect.invalid_entity_attacked"));
                        ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to attack an invalid entity", this.player.getName().getString());
                        return;
                    }

                    this.player.attack(entity);

                    // CraftBukkit start
                    if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                        this.player.refreshContainer(this.player.containerMenu);
                    }
                    // CraftBukkit end
                }

                if (optional.isPresent() && ((InteractionResult) optional.get()).consumesAction()) {
                    CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(this.player, this.player.getItemInHand(enumhand), entity);
                    if (((InteractionResult) optional.get()).shouldSwing()) {
                        this.player.swing(enumhand, true);
                    }
                }
            }
        }

    }

    @Override
    public void handleClientCommand(ServerboundClientCommandPacket packetplayinclientcommand) {
        PacketUtils.ensureMainThread(packetplayinclientcommand, this, this.player.getLevel());
        this.player.resetLastActionTime();
        ServerboundClientCommandPacket.Action packetplayinclientcommand_enumclientcommand = packetplayinclientcommand.getAction();

        switch (packetplayinclientcommand_enumclientcommand) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, true);
                    CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    this.player = this.server.getPlayerList().respawn(this.player, false);
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(GameType.SPECTATOR);
                        ((GameRules.BooleanValue) this.player.getLevel().getGameRules().get(GameRules.RULE_SPECTATORSGENERATECHUNKS)).a(false, this.server);
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }

    }

    @Override
    public void handleContainerClose(ServerboundContainerClosePacket packetplayinclosewindow) {
        PacketUtils.ensureMainThread(packetplayinclosewindow, this, this.player.getLevel());

        if (this.player.isImmobile()) return; // CraftBukkit
        CraftEventFactory.handleInventoryCloseEvent(this.player); // CraftBukkit

        this.player.doCloseContainer();
    }

    @Override
    public void handleContainerClick(ServerboundContainerClickPacket packetplayinwindowclick) {
        PacketUtils.ensureMainThread(packetplayinwindowclick, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packetplayinwindowclick.getContainerId() && this.player.containerMenu.isSynched(this.player) && this.player.containerMenu.stillValid(this.player)) { // CraftBukkit
            boolean cancelled = this.player.isSpectator(); // CraftBukkit - see below if
            if (false/*this.player.isSpectator()*/) { // CraftBukkit
                NonNullList<ItemStack> nonnulllist = NonNullList.create();

                for (int i = 0; i < this.player.containerMenu.slots.size(); ++i) {
                    nonnulllist.add(((Slot) this.player.containerMenu.slots.get(i)).getItem());
                }

                this.player.refreshContainer(this.player.containerMenu, nonnulllist);
            } else {
                // CraftBukkit start - Call InventoryClickEvent
                if (packetplayinwindowclick.getSlotNum() < -1 && packetplayinwindowclick.getSlotNum() != -999) {
                    return;
                }

                InventoryView inventory = this.player.containerMenu.getBukkitView();
                SlotType type = inventory.getSlotType(packetplayinwindowclick.getSlotNum());

                InventoryClickEvent event;
                ClickType click = ClickType.UNKNOWN;
                InventoryAction action = InventoryAction.UNKNOWN;

                ItemStack itemstack = ItemStack.EMPTY;

                switch (packetplayinwindowclick.getClickType()) {
                    case PICKUP:
                        if (packetplayinwindowclick.getButtonNum() == 0) {
                            click = ClickType.LEFT;
                        } else if (packetplayinwindowclick.getButtonNum() == 1) {
                            click = ClickType.RIGHT;
                        }
                        if (packetplayinwindowclick.getButtonNum() == 0 || packetplayinwindowclick.getButtonNum() == 1) {
                            action = InventoryAction.NOTHING; // Don't want to repeat ourselves
                            if (packetplayinwindowclick.getSlotNum() == -999) {
                                if (!player.inventory.getCarried().isEmpty()) {
                                    action = packetplayinwindowclick.getButtonNum() == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                                }
                            } else if (packetplayinwindowclick.getSlotNum() < 0)  {
                                action = InventoryAction.NOTHING;
                            } else {
                                Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum());
                                if (slot != null) {
                                    ItemStack clickedItem = slot.getItem();
                                    ItemStack cursor = player.inventory.getCarried();
                                    if (clickedItem.isEmpty()) {
                                        if (!cursor.isEmpty()) {
                                            action = packetplayinwindowclick.getButtonNum() == 0 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                                        }
                                    } else if (slot.mayPickup(player)) {
                                        if (cursor.isEmpty()) {
                                            action = packetplayinwindowclick.getButtonNum() == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                                        } else if (slot.mayPlace(cursor)) {
                                            if (clickedItem.sameItem(cursor) && ItemStack.tagMatches(clickedItem, cursor)) {
                                                int toPlace = packetplayinwindowclick.getButtonNum() == 0 ? cursor.getCount() : 1;
                                                toPlace = Math.min(toPlace, clickedItem.getMaxStackSize() - clickedItem.getCount());
                                                toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clickedItem.getCount());
                                                if (toPlace == 1) {
                                                    action = InventoryAction.PLACE_ONE;
                                                } else if (toPlace == cursor.getCount()) {
                                                    action = InventoryAction.PLACE_ALL;
                                                } else if (toPlace < 0) {
                                                    action = toPlace != -1 ? InventoryAction.PICKUP_SOME : InventoryAction.PICKUP_ONE; // this happens with oversized stacks
                                                } else if (toPlace != 0) {
                                                    action = InventoryAction.PLACE_SOME;
                                                }
                                            } else if (cursor.getCount() <= slot.getMaxStackSize()) {
                                                action = InventoryAction.SWAP_WITH_CURSOR;
                                            }
                                        } else if (cursor.getItem() == clickedItem.getItem() && ItemStack.tagMatches(cursor, clickedItem)) {
                                            if (clickedItem.getCount() >= 0) {
                                                if (clickedItem.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                                                    // As of 1.5, this is result slots only
                                                    action = InventoryAction.PICKUP_ALL;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    // TODO check on updates
                    case QUICK_MOVE:
                        if (packetplayinwindowclick.getButtonNum() == 0) {
                            click = ClickType.SHIFT_LEFT;
                        } else if (packetplayinwindowclick.getButtonNum() == 1) {
                            click = ClickType.SHIFT_RIGHT;
                        }
                        if (packetplayinwindowclick.getButtonNum() == 0 || packetplayinwindowclick.getButtonNum() == 1) {
                            if (packetplayinwindowclick.getSlotNum() < 0) {
                                action = InventoryAction.NOTHING;
                            } else {
                                Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum());
                                if (slot != null && slot.mayPickup(this.player) && slot.hasItem()) {
                                    action = InventoryAction.MOVE_TO_OTHER_INVENTORY;
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            }
                        }
                        break;
                    case SWAP:
                        if (packetplayinwindowclick.getButtonNum() >= 0 && packetplayinwindowclick.getButtonNum() < 9) {
                            click = ClickType.NUMBER_KEY;
                            Slot clickedSlot = this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum());
                            if (clickedSlot.mayPickup(player)) {
                                ItemStack hotbar = this.player.inventory.getItem(packetplayinwindowclick.getButtonNum());
                                boolean canCleanSwap = hotbar.isEmpty() || (clickedSlot.container == player.inventory && clickedSlot.mayPlace(hotbar)); // the slot will accept the hotbar item
                                if (clickedSlot.hasItem()) {
                                    if (canCleanSwap) {
                                        action = InventoryAction.HOTBAR_SWAP;
                                    } else {
                                        action = InventoryAction.HOTBAR_MOVE_AND_READD;
                                    }
                                } else if (!clickedSlot.hasItem() && !hotbar.isEmpty() && clickedSlot.mayPlace(hotbar)) {
                                    action = InventoryAction.HOTBAR_SWAP;
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            } else {
                                action = InventoryAction.NOTHING;
                            }
                        }
                        break;
                    case CLONE:
                        if (packetplayinwindowclick.getButtonNum() == 2) {
                            click = ClickType.MIDDLE;
                            if (packetplayinwindowclick.getSlotNum() < 0) {
                                action = InventoryAction.NOTHING;
                            } else {
                                Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum());
                                if (slot != null && slot.hasItem() && player.abilities.instabuild && player.inventory.getCarried().isEmpty()) {
                                    action = InventoryAction.CLONE_STACK;
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            }
                        } else {
                            click = ClickType.UNKNOWN;
                            action = InventoryAction.UNKNOWN;
                        }
                        break;
                    case THROW:
                        if (packetplayinwindowclick.getSlotNum() >= 0) {
                            if (packetplayinwindowclick.getButtonNum() == 0) {
                                click = ClickType.DROP;
                                Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum());
                                if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                    action = InventoryAction.DROP_ONE_SLOT;
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            } else if (packetplayinwindowclick.getButtonNum() == 1) {
                                click = ClickType.CONTROL_DROP;
                                Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum());
                                if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                    action = InventoryAction.DROP_ALL_SLOT;
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            }
                        } else {
                            // Sane default (because this happens when they are holding nothing. Don't ask why.)
                            click = ClickType.LEFT;
                            if (packetplayinwindowclick.getButtonNum() == 1) {
                                click = ClickType.RIGHT;
                            }
                            action = InventoryAction.NOTHING;
                        }
                        break;
                    case QUICK_CRAFT:
                        itemstack = this.player.containerMenu.clicked(packetplayinwindowclick.getSlotNum(), packetplayinwindowclick.getButtonNum(), packetplayinwindowclick.getClickType(), this.player);
                        break;
                    case PICKUP_ALL:
                        click = ClickType.DOUBLE_CLICK;
                        action = InventoryAction.NOTHING;
                        if (packetplayinwindowclick.getSlotNum() >= 0 && !this.player.inventory.getCarried().isEmpty()) {
                            ItemStack cursor = this.player.inventory.getCarried();
                            action = InventoryAction.NOTHING;
                            // Quick check for if we have any of the item
                            if (inventory.getTopInventory().contains(CraftMagicNumbers.getMaterial(cursor.getItem())) || inventory.getBottomInventory().contains(CraftMagicNumbers.getMaterial(cursor.getItem()))) {
                                action = InventoryAction.COLLECT_TO_CURSOR;
                            }
                        }
                        break;
                    default:
                        break;
                }

                if (packetplayinwindowclick.getClickType() != net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                    if (click == ClickType.NUMBER_KEY) {
                        event = new InventoryClickEvent(inventory, type, packetplayinwindowclick.getSlotNum(), click, action, packetplayinwindowclick.getButtonNum());
                    } else {
                        event = new InventoryClickEvent(inventory, type, packetplayinwindowclick.getSlotNum(), click, action);
                    }

                    org.bukkit.inventory.Inventory top = inventory.getTopInventory();
                    if (packetplayinwindowclick.getSlotNum() == 0 && top instanceof CraftingInventory) {
                        org.bukkit.inventory.Recipe recipe = ((CraftingInventory) top).getRecipe();
                        if (recipe != null) {
                            if (click == ClickType.NUMBER_KEY) {
                                event = new CraftItemEvent(recipe, inventory, type, packetplayinwindowclick.getSlotNum(), click, action, packetplayinwindowclick.getButtonNum());
                            } else {
                                event = new CraftItemEvent(recipe, inventory, type, packetplayinwindowclick.getSlotNum(), click, action);
                            }
                        }
                    }

                    event.setCancelled(cancelled);
                    AbstractContainerMenu oldContainer = this.player.containerMenu; // SPIGOT-1224
                    craftServer.getPluginManager().callEvent(event);
                    if (this.player.containerMenu != oldContainer) {
                        return;
                    }

                    switch (event.getResult()) {
                        case ALLOW:
                        case DEFAULT:
                            itemstack = this.player.containerMenu.clicked(packetplayinwindowclick.getSlotNum(), packetplayinwindowclick.getButtonNum(), packetplayinwindowclick.getClickType(), this.player);
                            break;
                        case DENY:
                            /* Needs enum constructor in InventoryAction
                            if (action.modifiesOtherSlots()) {

                            } else {
                                if (action.modifiesCursor()) {
                                    this.player.playerConnection.sendPacket(new Packet103SetSlot(-1, -1, this.player.inventory.getCarried()));
                                }
                                if (action.modifiesClicked()) {
                                    this.player.playerConnection.sendPacket(new Packet103SetSlot(this.player.activeContainer.windowId, packet102windowclick.slot, this.player.activeContainer.getSlot(packet102windowclick.slot).getItem()));
                                }
                            }*/
                            switch (action) {
                                // Modified other slots
                                case PICKUP_ALL:
                                case MOVE_TO_OTHER_INVENTORY:
                                case HOTBAR_MOVE_AND_READD:
                                case HOTBAR_SWAP:
                                case COLLECT_TO_CURSOR:
                                case UNKNOWN:
                                    this.player.refreshContainer(this.player.containerMenu);
                                    break;
                                // Modified cursor and clicked
                                case PICKUP_SOME:
                                case PICKUP_HALF:
                                case PICKUP_ONE:
                                case PLACE_ALL:
                                case PLACE_SOME:
                                case PLACE_ONE:
                                case SWAP_WITH_CURSOR:
                                    this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventory.getCarried()));
                                    this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, packetplayinwindowclick.getSlotNum(), this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum()).getItem()));
                                    break;
                                // Modified clicked only
                                case DROP_ALL_SLOT:
                                case DROP_ONE_SLOT:
                                    this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, packetplayinwindowclick.getSlotNum(), this.player.containerMenu.getSlot(packetplayinwindowclick.getSlotNum()).getItem()));
                                    break;
                                // Modified cursor only
                                case DROP_ALL_CURSOR:
                                case DROP_ONE_CURSOR:
                                case CLONE_STACK:
                                    this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventory.getCarried()));
                                    break;
                                // Nothing
                                case NOTHING:
                                    break;
                            }
                            return;
                    }

                    if (event instanceof CraftItemEvent) {
                        // Need to update the inventory on crafting to
                        // correctly support custom recipes
                        player.refreshContainer(player.containerMenu);
                    }
                }
                // CraftBukkit end
                if (ItemStack.matches(packetplayinwindowclick.getItem(), itemstack)) {
                    this.player.connection.sendPacket(new ClientboundContainerAckPacket(packetplayinwindowclick.getContainerId(), packetplayinwindowclick.getUid(), true));
                    this.player.ignoreSlotUpdateHack = true;
                    this.player.containerMenu.broadcastChanges();
                    this.player.broadcastCarriedItem();
                    this.player.ignoreSlotUpdateHack = false;
                } else {
                    this.expectedAcks.put(this.player.containerMenu.containerId, packetplayinwindowclick.getUid());
                    this.player.connection.sendPacket(new ClientboundContainerAckPacket(packetplayinwindowclick.getContainerId(), packetplayinwindowclick.getUid(), false));
                    this.player.containerMenu.setSynched(this.player, false);
                    NonNullList<ItemStack> nonnulllist1 = NonNullList.create();

                    for (int j = 0; j < this.player.containerMenu.slots.size(); ++j) {
                        ItemStack itemstack1 = ((Slot) this.player.containerMenu.slots.get(j)).getItem();

                        nonnulllist1.add(itemstack1.isEmpty() ? ItemStack.EMPTY : itemstack1);
                    }

                    this.player.refreshContainer(this.player.containerMenu, nonnulllist1);
                }
            }
        }

    }

    @Override
    public void handlePlaceRecipe(ServerboundPlaceRecipePacket packetplayinautorecipe) {
        PacketUtils.ensureMainThread(packetplayinautorecipe, this, this.player.getLevel());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator() && this.player.containerMenu.containerId == packetplayinautorecipe.getContainerId() && this.player.containerMenu.isSynched(this.player) && this.player.containerMenu instanceof RecipeBookMenu) {
            this.server.getRecipeManager().byKey(packetplayinautorecipe.getRecipe()).ifPresent((irecipe) -> {
                ((RecipeBookMenu) this.player.containerMenu).handlePlacement(packetplayinautorecipe.isShiftDown(), irecipe, this.player);
            });
        }
    }

    @Override
    public void handleContainerButtonClick(ServerboundContainerButtonClickPacket packetplayinenchantitem) {
        PacketUtils.ensureMainThread(packetplayinenchantitem, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packetplayinenchantitem.getContainerId() && this.player.containerMenu.isSynched(this.player) && !this.player.isSpectator()) {
            this.player.containerMenu.clickMenuButton((net.minecraft.world.entity.player.Player) this.player, packetplayinenchantitem.getButtonId());
            this.player.containerMenu.broadcastChanges();
        }

    }

    @Override
    public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packetplayinsetcreativeslot) {
        PacketUtils.ensureMainThread(packetplayinsetcreativeslot, this, this.player.getLevel());
        if (this.player.gameMode.isCreative()) {
            boolean flag = packetplayinsetcreativeslot.getSlotNum() < 0;
            ItemStack itemstack = packetplayinsetcreativeslot.getItem();
            CompoundTag nbttagcompound = itemstack.getTagElement("BlockEntityTag");

            if (!itemstack.isEmpty() && nbttagcompound != null && nbttagcompound.contains("x") && nbttagcompound.contains("y") && nbttagcompound.contains("z") && this.player.getBukkitEntity().hasPermission("minecraft.nbt.copy")) { // Spigot
                BlockPos blockposition = new BlockPos(nbttagcompound.getInt("x"), nbttagcompound.getInt("y"), nbttagcompound.getInt("z"));
                BlockEntity tileentity = this.player.level.getBlockEntity(blockposition);

                if (tileentity != null) {
                    CompoundTag nbttagcompound1 = tileentity.save(new CompoundTag());

                    nbttagcompound1.remove("x");
                    nbttagcompound1.remove("y");
                    nbttagcompound1.remove("z");
                    itemstack.addTagElement("BlockEntityTag", (Tag) nbttagcompound1);
                }
            }

            boolean flag1 = packetplayinsetcreativeslot.getSlotNum() >= 1 && packetplayinsetcreativeslot.getSlotNum() <= 45;
            boolean flag2 = itemstack.isEmpty() || itemstack.getDamageValue() >= 0 && itemstack.getCount() <= 64 && !itemstack.isEmpty();
            if (flag || (flag1 && !ItemStack.matches(this.player.inventoryMenu.getSlot(packetplayinsetcreativeslot.getSlotNum()).getItem(), packetplayinsetcreativeslot.getItem()))) { // Insist on valid slot
                // CraftBukkit start - Call click event
                InventoryView inventory = this.player.inventoryMenu.getBukkitView();
                org.bukkit.inventory.ItemStack item = CraftItemStack.asBukkitCopy(packetplayinsetcreativeslot.getItem());

                SlotType type = SlotType.QUICKBAR;
                if (flag) {
                    type = SlotType.OUTSIDE;
                } else if (packetplayinsetcreativeslot.getSlotNum() < 36) {
                    if (packetplayinsetcreativeslot.getSlotNum() >= 5 && packetplayinsetcreativeslot.getSlotNum() < 9) {
                        type = SlotType.ARMOR;
                    } else {
                        type = SlotType.CONTAINER;
                    }
                }
                InventoryCreativeEvent event = new InventoryCreativeEvent(inventory, type, flag ? -999 : packetplayinsetcreativeslot.getSlotNum(), item);
                craftServer.getPluginManager().callEvent(event);

                itemstack = CraftItemStack.asNMSCopy(event.getCursor());

                switch (event.getResult()) {
                case ALLOW:
                    // Plugin cleared the id / stacksize checks
                    flag2 = true;
                    break;
                case DEFAULT:
                    break;
                case DENY:
                    // Reset the slot
                    if (packetplayinsetcreativeslot.getSlotNum() >= 0) {
                        this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(this.player.inventoryMenu.containerId, packetplayinsetcreativeslot.getSlotNum(), this.player.inventoryMenu.getSlot(packetplayinsetcreativeslot.getSlotNum()).getItem()));
                        this.player.connection.sendPacket(new ClientboundContainerSetSlotPacket(-1, -1, ItemStack.EMPTY));
                    }
                    return;
                }
            }
            // CraftBukkit end

            if (flag1 && flag2) {
                if (itemstack.isEmpty()) {
                    this.player.inventoryMenu.setItem(packetplayinsetcreativeslot.getSlotNum(), ItemStack.EMPTY);
                } else {
                    this.player.inventoryMenu.setItem(packetplayinsetcreativeslot.getSlotNum(), itemstack);
                }

                this.player.inventoryMenu.setSynched(this.player, true);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2 && this.dropSpamTickCount < 200) {
                this.dropSpamTickCount += 20;
                this.player.drop(itemstack, true);
            }
        }

    }

    @Override
    public void handleContainerAck(ServerboundContainerAckPacket packetplayintransaction) {
        PacketUtils.ensureMainThread(packetplayintransaction, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        int i = this.player.containerMenu.containerId;

        if (i == packetplayintransaction.getContainerId() && this.expectedAcks.getOrDefault(i, (short) (packetplayintransaction.getUid() + 1)) == packetplayintransaction.getUid() && !this.player.containerMenu.isSynched(this.player) && !this.player.isSpectator()) {
            this.player.containerMenu.setSynched(this.player, true);
        }

    }

    @Override
    public void handleSignUpdate(ServerboundSignUpdatePacket packetplayinupdatesign) {
        PacketUtils.ensureMainThread(packetplayinupdatesign, this, this.player.getLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        ServerLevel worldserver = this.player.getLevel();
        BlockPos blockposition = packetplayinupdatesign.getPos();

        if (worldserver.hasChunkAt(blockposition)) {
            BlockState iblockdata = worldserver.getType(blockposition);
            BlockEntity tileentity = worldserver.getBlockEntity(blockposition);

            if (!(tileentity instanceof SignBlockEntity)) {
                return;
            }

            SignBlockEntity tileentitysign = (SignBlockEntity) tileentity;

            if (!tileentitysign.isEditable() || tileentitysign.getPlayerWhoMayEdit() != this.player) {
                ServerGamePacketListenerImpl.LOGGER.warn("Player {} just tried to change non-editable sign", this.player.getName().getString());
                this.sendPacket(tileentity.getUpdatePacket()); // CraftBukkit
                return;
            }

            String[] astring = packetplayinupdatesign.getLines();

            // CraftBukkit start
            Player player = this.craftServer.getPlayer(this.player);
            int x = packetplayinupdatesign.getPos().getX();
            int y = packetplayinupdatesign.getPos().getY();
            int z = packetplayinupdatesign.getPos().getZ();
            String[] lines = new String[4];

            for (int i = 0; i < astring.length; ++i) {
                lines[i] = ChatFormatting.stripFormatting(new TextComponent(ChatFormatting.stripFormatting(astring[i])).getString());
            }
            SignChangeEvent event = new SignChangeEvent((org.bukkit.craftbukkit.block.CraftBlock) player.getWorld().getBlockAt(x, y, z), this.craftServer.getPlayer(this.player), lines);
            this.craftServer.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                System.arraycopy(org.bukkit.craftbukkit.block.CraftSign.sanitizeLines(event.getLines()), 0, tileentitysign.messages, 0, 4);
                tileentitysign.isEditable = false;
             }
            // CraftBukkit end

            tileentitysign.setChanged();
            worldserver.notify(blockposition, iblockdata, iblockdata, 3);
        }

    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packetplayinkeepalive) {
        PacketUtils.ensureMainThread(packetplayinkeepalive, this, this.player.getLevel()); // CraftBukkit
        if (this.keepAlivePending && packetplayinkeepalive.getId() == this.keepAliveChallenge) {
            int i = (int) (Util.getMillis() - this.keepAliveTime);

            this.player.latency = (this.player.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(new TranslatableComponent("disconnect.timeout"));
        }

    }

    @Override
    public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packetplayinabilities) {
        PacketUtils.ensureMainThread(packetplayinabilities, this, this.player.getLevel());
        // CraftBukkit start
        if (this.player.abilities.mayfly && this.player.abilities.flying != packetplayinabilities.isFlying()) {
            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(this.craftServer.getPlayer(this.player), packetplayinabilities.isFlying());
            this.craftServer.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.player.abilities.flying = packetplayinabilities.isFlying(); // Actually set the player's flying status
            } else {
                this.player.onUpdateAbilities(); // Tell the player their ability was reverted
            }
        }
        // CraftBukkit end
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packetplayinsettings) {
        PacketUtils.ensureMainThread(packetplayinsettings, this, this.player.getLevel());
        this.player.updateOptions(packetplayinsettings);
    }

    // CraftBukkit start
    private static final ResourceLocation CUSTOM_REGISTER = new ResourceLocation("register");
    private static final ResourceLocation CUSTOM_UNREGISTER = new ResourceLocation("unregister");

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packetplayincustompayload) {
        PacketUtils.ensureMainThread(packetplayincustompayload, this, this.player.getLevel());
        if (packetplayincustompayload.identifier.equals(CUSTOM_REGISTER)) {
            try {
                String channels = packetplayincustompayload.data.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    getPlayer().addChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t register custom payload", ex);
                this.disconnect("Invalid payload REGISTER!");
            }
        } else if (packetplayincustompayload.identifier.equals(CUSTOM_UNREGISTER)) {
            try {
                String channels = packetplayincustompayload.data.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    getPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t unregister custom payload", ex);
                this.disconnect("Invalid payload UNREGISTER!");
            }
        } else {
            try {
                byte[] data = new byte[packetplayincustompayload.data.readableBytes()];
                packetplayincustompayload.data.readBytes(data);
                craftServer.getMessenger().dispatchIncomingMessage(player.getBukkitEntity(), packetplayincustompayload.identifier.toString(), data);
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t dispatch custom payload", ex);
                this.disconnect("Invalid custom payload!");
            }
        }

    }

    public final boolean isDisconnected() {
        return !this.player.joining && !this.connection.isConnected();
    }
    // CraftBukkit end

    @Override
    public void handleChangeDifficulty(ServerboundChangeDifficultyPacket packetplayindifficultychange) {
        PacketUtils.ensureMainThread(packetplayindifficultychange, this, this.player.getLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficulty(packetplayindifficultychange.getDifficulty(), false);
        }
    }

    @Override
    public void handleLockDifficulty(ServerboundLockDifficultyPacket packetplayindifficultylock) {
        PacketUtils.ensureMainThread(packetplayindifficultylock, this, this.player.getLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(packetplayindifficultylock.isLocked());
        }
    }
}
