package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
// CraftBukkit end

public abstract class PlayerList {

    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<ServerPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
    private final UserBanList bans;
    private final IpBanList ipBans;
    private final ServerOpList ops;
    private final UserWhiteList whitelist;
    // CraftBukkit start
    // private final Map<UUID, ServerStatisticManager> o;
    // private final Map<UUID, AdvancementDataPlayer> p;
    // CraftBukkit end
    public final PlayerDataStorage playerIo;
    private boolean doWhiteList;
    private final RegistryAccess.Dimension registryHolder;
    protected final int maxPlayers;
    private int viewDistance;
    private GameType overrideGameMode;
    private boolean allowCheatsForAllPlayers;
    private int sendAllPlayerInfoIn;

    // CraftBukkit start
    private CraftServer cserver;
    private final Map<String,ServerPlayer> playersByName = new java.util.HashMap<>();

    public PlayerList(MinecraftServer minecraftserver, RegistryAccess.Dimension iregistrycustom_dimension, PlayerDataStorage worldnbtstorage, int i) {
        this.cserver = minecraftserver.server = new CraftServer((DedicatedServer) minecraftserver, this);
        minecraftserver.console = org.bukkit.craftbukkit.command.ColouredConsoleSender.getInstance();
        minecraftserver.reader.addCompleter(new org.bukkit.craftbukkit.command.ConsoleCommandCompleter(minecraftserver.server));
        // CraftBukkit end

        this.bans = new UserBanList(PlayerList.USERBANLIST_FILE);
        this.ipBans = new IpBanList(PlayerList.IPBANLIST_FILE);
        this.ops = new ServerOpList(PlayerList.OPLIST_FILE);
        this.whitelist = new UserWhiteList(PlayerList.WHITELIST_FILE);
        // CraftBukkit start
        // this.o = Maps.newHashMap();
        // this.p = Maps.newHashMap();
        // CraftBukkit end
        this.server = minecraftserver;
        this.registryHolder = iregistrycustom_dimension;
        this.maxPlayers = i;
        this.playerIo = worldnbtstorage;
    }

    public void placeNewPlayer(Connection networkmanager, ServerPlayer entityplayer) {
        GameProfile gameprofile = entityplayer.getGameProfile();
        GameProfileCache usercache = this.server.getProfileCache();
        GameProfile gameprofile1 = usercache.get(gameprofile.getId());
        String s = gameprofile1 == null ? gameprofile.getName() : gameprofile1.getName();

        usercache.add(gameprofile);
        CompoundTag nbttagcompound = this.load(entityplayer);
        ResourceKey resourcekey;
        // CraftBukkit start - Better rename detection
        if (nbttagcompound != null && nbttagcompound.contains("bukkit")) {
            CompoundTag bukkit = nbttagcompound.getCompound("bukkit");
            s = bukkit.contains("lastKnownName", 8) ? bukkit.getString("lastKnownName") : s;
        }
        // CraftBukkit end

        if (nbttagcompound != null) {
            DataResult dataresult = DimensionType.parseLegacy(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("Dimension")));
            Logger logger = PlayerList.LOGGER;

            logger.getClass();
            resourcekey = (ResourceKey) dataresult.resultOrPartial(logger::error).orElse(Level.OVERWORLD);
        } else {
            resourcekey = Level.OVERWORLD;
        }

        ResourceKey<Level> resourcekey1 = resourcekey;
        ServerLevel worldserver = this.server.getWorldServer(resourcekey1);
        ServerLevel worldserver1;

        if (worldserver == null) {
            PlayerList.LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey1);
            worldserver1 = this.server.overworld();
        } else {
            worldserver1 = worldserver;
        }

        entityplayer.setLevel(worldserver1);
        entityplayer.gameMode.setLevel((ServerLevel) entityplayer.level);
        String s1 = "local";

        if (networkmanager.getRemoteAddress() != null) {
            s1 = networkmanager.getRemoteAddress().toString();
        }

        // Spigot start - spawn location event
        Player bukkitPlayer = entityplayer.getBukkitEntity();
        PlayerSpawnLocationEvent ev = new PlayerSpawnLocationEvent(bukkitPlayer, bukkitPlayer.getLocation());
        Bukkit.getPluginManager().callEvent(ev);

        Location loc = ev.getSpawnLocation();
        worldserver = ((CraftWorld) loc.getWorld()).getHandle();

        entityplayer.setLevel(worldserver);
        entityplayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        entityplayer.setRot(loc.getYaw(), loc.getPitch());
        // Spigot end

        // CraftBukkit - Moved message to after join
        // PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", entityplayer.getDisplayName().getString(), s1, entityplayer.getId(), entityplayer.locX(), entityplayer.locY(), entityplayer.locZ());
        LevelData worlddata = worldserver1.getLevelData();

        this.updatePlayerGameMode(entityplayer, (ServerPlayer) null, worldserver1);
        ServerGamePacketListenerImpl playerconnection = new ServerGamePacketListenerImpl(this.server, networkmanager, entityplayer);
        GameRules gamerules = worldserver1.getGameRules();
        boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);

        // Spigot - view distance
        playerconnection.sendPacket(new ClientboundLoginPacket(entityplayer.getId(), entityplayer.gameMode.getGameModeForPlayer(), entityplayer.gameMode.getPreviousGameModeForPlayer(), BiomeManager.obfuscateSeed(worldserver1.getSeed()), worlddata.isHardcore(), this.server.levelKeys(), this.registryHolder, worldserver1.getTypeKey(), worldserver1.getDimensionKey(), this.getMaxPlayers(), worldserver.spigotConfig.viewDistance, flag1, !flag, worldserver1.isDebug(), worldserver1.isFlat()));
        entityplayer.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
        playerconnection.sendPacket(new ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.BRAND, (new FriendlyByteBuf(Unpooled.buffer())).writeUtf(this.getServer().getServerModName())));
        playerconnection.sendPacket(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        playerconnection.sendPacket(new ClientboundPlayerAbilitiesPacket(entityplayer.abilities));
        playerconnection.sendPacket(new ClientboundSetCarriedItemPacket(entityplayer.inventory.selected));
        playerconnection.sendPacket(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes()));
        playerconnection.sendPacket(new ClientboundUpdateTagsPacket(this.server.getTags()));
        this.sendPlayerPermissionLevel(entityplayer);
        entityplayer.getStats().markAllDirty();
        entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
        this.updateEntireScoreboard(worldserver1.getScoreboard(), entityplayer);
        this.server.invalidateStatus();
        TranslatableComponent chatmessage;

        if (entityplayer.getGameProfile().getName().equalsIgnoreCase(s)) {
            chatmessage = new TranslatableComponent("multiplayer.player.joined", new Object[]{entityplayer.getDisplayName()});
        } else {
            chatmessage = new TranslatableComponent("multiplayer.player.joined.renamed", new Object[]{entityplayer.getDisplayName(), s});
        }
        // CraftBukkit start
        chatmessage.withStyle(ChatFormatting.YELLOW);
        String joinMessage = CraftChatMessage.fromComponent(chatmessage);

        playerconnection.teleport(entityplayer.getX(), entityplayer.getY(), entityplayer.getZ(), entityplayer.yRot, entityplayer.xRot);
        this.players.add(entityplayer);
        this.playersByName.put(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT), entityplayer); // Spigot
        this.playersByUUID.put(entityplayer.getUUID(), entityplayer);
        // this.sendAll(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, new EntityPlayer[]{entityplayer})); // CraftBukkit - replaced with loop below

        // CraftBukkit start
        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(cserver.getPlayer(entityplayer), joinMessage);
        cserver.getPluginManager().callEvent(playerJoinEvent);

        if (!entityplayer.connection.connection.isConnected()) {
            return;
        }

        joinMessage = playerJoinEvent.getJoinMessage();

        if (joinMessage != null && joinMessage.length() > 0) {
            for (Component line : org.bukkit.craftbukkit.util.CraftChatMessage.fromString(joinMessage)) {
                server.getPlayerList().sendAll(new ClientboundChatPacket(line, ChatType.SYSTEM, Util.NIL_UUID));
            }
        }
        // CraftBukkit end

        // CraftBukkit start - sendAll above replaced with this loop
        ClientboundPlayerInfoPacket packet = new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, entityplayer);

        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer1 = (ServerPlayer) this.players.get(i);

            if (entityplayer1.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer1.connection.sendPacket(packet);
            }

            if (!entityplayer.getBukkitEntity().canSee(entityplayer1.getBukkitEntity())) {
                continue;
            }

            entityplayer.connection.sendPacket(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, new ServerPlayer[] { entityplayer1}));
        }
        entityplayer.sentListPacket = true;
        // CraftBukkit end

        entityplayer.connection.sendPacket(new ClientboundSetEntityDataPacket(entityplayer.getId(), entityplayer.entityData, true)); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (entityplayer.level == worldserver1 && !worldserver1.players().contains(entityplayer)) {
            worldserver1.addNewPlayer(entityplayer);
            this.server.getCustomBossEvents().onPlayerConnect(entityplayer);
        }

        worldserver1 = entityplayer.getLevel();  // CraftBukkit - Update in case join event changed it
        // CraftBukkit end
        this.sendLevelInfo(entityplayer, worldserver1);
        if (!this.server.getResourcePack().isEmpty()) {
            entityplayer.sendTexturePack(this.server.getResourcePack(), this.server.getResourcePackHash());
        }

        Iterator iterator = entityplayer.getActiveEffects().iterator();

        while (iterator.hasNext()) {
            MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

            playerconnection.sendPacket(new ClientboundUpdateMobEffectPacket(entityplayer.getId(), mobeffect));
        }

        if (nbttagcompound != null && nbttagcompound.contains("RootVehicle", 10)) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("RootVehicle");
            // CraftBukkit start
            ServerLevel finalWorldServer = worldserver1;
            Entity entity = EntityType.loadEntityRecursive(nbttagcompound1.getCompound("Entity"), finalWorldServer, (entity1) -> {
                return !finalWorldServer.addWithUUID(entity1) ? null : entity1;
                // CraftBukkit end
            });

            if (entity != null) {
                UUID uuid;

                if (nbttagcompound1.hasUUID("Attach")) {
                    uuid = nbttagcompound1.getUUID("Attach");
                } else {
                    uuid = null;
                }

                Iterator iterator1;
                Entity entity1;

                if (entity.getUUID().equals(uuid)) {
                    entityplayer.startRiding(entity, true);
                } else {
                    iterator1 = entity.getIndirectPassengers().iterator();

                    while (iterator1.hasNext()) {
                        entity1 = (Entity) iterator1.next();
                        if (entity1.getUUID().equals(uuid)) {
                            entityplayer.startRiding(entity1, true);
                            break;
                        }
                    }
                }

                if (!entityplayer.isPassenger()) {
                    PlayerList.LOGGER.warn("Couldn't reattach entity to player");
                    worldserver1.despawn(entity);
                    iterator1 = entity.getIndirectPassengers().iterator();

                    while (iterator1.hasNext()) {
                        entity1 = (Entity) iterator1.next();
                        worldserver1.despawn(entity1);
                    }
                }
            }
        }

        entityplayer.initMenu();
        // CraftBukkit - Moved from above, added world
        PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", entityplayer.getName().getString(), s1, entityplayer.getId(), worldserver1.serverLevelData.getLevelName(), entityplayer.getX(), entityplayer.getY(), entityplayer.getZ());
    }

    public void updateEntireScoreboard(ServerScoreboard scoreboardserver, ServerPlayer entityplayer) {
        Set<Objective> set = Sets.newHashSet();
        Iterator iterator = scoreboardserver.getPlayerTeams().iterator();

        while (iterator.hasNext()) {
            PlayerTeam scoreboardteam = (PlayerTeam) iterator.next();

            entityplayer.connection.sendPacket(new ClientboundSetPlayerTeamPacket(scoreboardteam, 0));
        }

        for (int i = 0; i < 19; ++i) {
            Objective scoreboardobjective = scoreboardserver.getDisplayObjective(i);

            if (scoreboardobjective != null && !set.contains(scoreboardobjective)) {
                List<Packet<?>> list = scoreboardserver.getStartTrackingPackets(scoreboardobjective);
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    Packet<?> packet = (Packet) iterator1.next();

                    entityplayer.connection.sendPacket(packet);
                }

                set.add(scoreboardobjective);
            }
        }

    }

    public void setLevel(ServerLevel worldserver) {
        if (playerIo != null) return; // CraftBukkit
        worldserver.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder worldborder, double d0) {
                PlayerList.this.sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_SIZE), worldborder.world);
            }

            @Override
            public void onBorderSizeLerping(WorldBorder worldborder, double d0, double d1, long i) {
                PlayerList.this.sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.LERP_SIZE), worldborder.world);
            }

            @Override
            public void onBorderCenterSet(WorldBorder worldborder, double d0, double d1) {
                PlayerList.this.sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_CENTER), worldborder.world);
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder worldborder, int i) {
                PlayerList.this.sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_WARNING_TIME), worldborder.world);
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder worldborder, int i) {
                PlayerList.this.sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_WARNING_BLOCKS), worldborder.world);
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder worldborder, double d0) {}

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder worldborder, double d0) {}
        });
    }

    @Nullable
    public CompoundTag load(ServerPlayer entityplayer) {
        CompoundTag nbttagcompound = this.server.getWorldData().getLoadedPlayerTag();
        CompoundTag nbttagcompound1;

        if (entityplayer.getName().getString().equals(this.server.getSingleplayerName()) && nbttagcompound != null) {
            nbttagcompound1 = nbttagcompound;
            entityplayer.load(nbttagcompound);
            PlayerList.LOGGER.debug("loading single player");
        } else {
            nbttagcompound1 = this.playerIo.load(entityplayer);
        }

        return nbttagcompound1;
    }

    protected void save(ServerPlayer entityplayer) {
        if (!entityplayer.getBukkitEntity().isPersistent()) return; // CraftBukkit
        this.playerIo.save(entityplayer);
        ServerStatsCounter serverstatisticmanager = (ServerStatsCounter) entityplayer.getStats(); // CraftBukkit

        if (serverstatisticmanager != null) {
            serverstatisticmanager.save();
        }

        PlayerAdvancements advancementdataplayer = (PlayerAdvancements) entityplayer.getAdvancements(); // CraftBukkit

        if (advancementdataplayer != null) {
            advancementdataplayer.save();
        }

    }

    public String disconnect(ServerPlayer entityplayer) { // CraftBukkit - return string
        ServerLevel worldserver = entityplayer.getLevel();

        entityplayer.awardStat(Stats.LEAVE_GAME);

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        entityplayer.closeContainer();

        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(cserver.getPlayer(entityplayer), "\u00A7e" + entityplayer.getScoreboardName() + " left the game");
        cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        entityplayer.doTick(); // SPIGOT-924
        // CraftBukkit end

        this.save(entityplayer);
        if (entityplayer.isPassenger()) {
            Entity entity = entityplayer.getRootVehicle();

            if (entity.hasOnePlayerPassenger()) {
                PlayerList.LOGGER.debug("Removing player mount");
                entityplayer.stopRiding();
                worldserver.despawn(entity);
                entity.removed = true;

                Entity entity1;

                for (Iterator iterator = entity.getIndirectPassengers().iterator(); iterator.hasNext(); entity1.removed = true) {
                    entity1 = (Entity) iterator.next();
                    worldserver.despawn(entity1);
                }

                worldserver.getChunk(entityplayer.xChunk, entityplayer.zChunk).markUnsaved();
            }
        }

        entityplayer.unRide();
        worldserver.removePlayerImmediately(entityplayer);
        entityplayer.getAdvancements().stopListening();
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        this.server.getCustomBossEvents().onPlayerDisconnect(entityplayer);
        UUID uuid = entityplayer.getUUID();
        ServerPlayer entityplayer1 = (ServerPlayer) this.playersByUUID.get(uuid);

        if (entityplayer1 == entityplayer) {
            this.playersByUUID.remove(uuid);
            // CraftBukkit start
            // this.o.remove(uuid);
            // this.p.remove(uuid);
            // CraftBukkit end
        }

        // CraftBukkit start
        // this.sendAll(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, new EntityPlayer[]{entityplayer}));
        ClientboundPlayerInfoPacket packet = new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, entityplayer);
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer entityplayer2 = (ServerPlayer) this.players.get(i);

            if (entityplayer2.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer2.connection.sendPacket(packet);
            } else {
                entityplayer2.getBukkitEntity().removeDisconnectingPlayer(entityplayer.getBukkitEntity());
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());
        // CraftBukkit end

        return playerQuitEvent.getQuitMessage(); // CraftBukkit
    }

    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public ServerPlayer attemptLogin(ServerLoginPacketListenerImpl loginlistener, GameProfile gameprofile, String hostname) {
        TranslatableComponent chatmessage;

        // Moved from processLogin
        UUID uuid = net.minecraft.world.entity.player.Player.createPlayerUUID(gameprofile);
        List<ServerPlayer> list = Lists.newArrayList();

        ServerPlayer entityplayer;

        for (int i = 0; i < this.players.size(); ++i) {
            entityplayer = (ServerPlayer) this.players.get(i);
            if (entityplayer.getUUID().equals(uuid)) {
                list.add(entityplayer);
            }
        }

        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            save(entityplayer); // CraftBukkit - Force the player's inventory to be saved
            entityplayer.connection.disconnect(new TranslatableComponent("multiplayer.disconnect.duplicate_login", new Object[0]));
        }

        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        SocketAddress socketaddress = loginlistener.connection.getRemoteAddress();

        ServerPlayer entity = new ServerPlayer(this.server, this.server.getWorldServer(Level.OVERWORLD), gameprofile, new ServerPlayerGameMode(this.server.getWorldServer(Level.OVERWORLD)));
        Player player = entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, hostname, ((java.net.InetSocketAddress) socketaddress).getAddress(), ((java.net.InetSocketAddress) loginlistener.connection.getRawAddress()).getAddress());

        if (getBans().isBanned(gameprofile) && !getBans().get(gameprofile).hasExpired()) {
            UserBanListEntry gameprofilebanentry = (UserBanListEntry) this.bans.get(gameprofile);

            chatmessage = new TranslatableComponent("multiplayer.disconnect.banned.reason", new Object[]{gameprofilebanentry.getReason()});
            if (gameprofilebanentry.getExpires() != null) {
                chatmessage.append(new TranslatableComponent("multiplayer.disconnect.banned.expiration", new Object[]{PlayerList.BAN_DATE_FORMAT.format(gameprofilebanentry.getExpires())}));
            }

            // return chatmessage;
            if (!gameprofilebanentry.hasExpired()) event.disallow(PlayerLoginEvent.Result.KICK_BANNED, CraftChatMessage.fromComponent(chatmessage)); // Spigot
        } else if (!this.isWhiteListed(gameprofile)) {
            chatmessage = new TranslatableComponent("multiplayer.disconnect.not_whitelisted");
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, org.spigotmc.SpigotConfig.whitelistMessage); // Spigot
        } else if (getIpBans().isBanned(socketaddress) && !getIpBans().get(socketaddress).hasExpired()) {
            IpBanListEntry ipbanentry = this.ipBans.get(socketaddress);

            chatmessage = new TranslatableComponent("multiplayer.disconnect.banned_ip.reason", new Object[]{ipbanentry.getReason()});
            if (ipbanentry.getExpires() != null) {
                chatmessage.append(new TranslatableComponent("multiplayer.disconnect.banned_ip.expiration", new Object[]{PlayerList.BAN_DATE_FORMAT.format(ipbanentry.getExpires())}));
            }

            // return chatmessage;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, CraftChatMessage.fromComponent(chatmessage));
        } else {
            // return this.players.size() >= this.maxPlayers && !this.f(gameprofile) ? new ChatMessage("multiplayer.disconnect.server_full") : null;
            if (this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile)) {
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, org.spigotmc.SpigotConfig.serverFullMessage); // Spigot
            }
        }

        cserver.getPluginManager().callEvent(event);
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(event.getKickMessage());
            return null;
        }
        return entity;
    }

    public ServerPlayer processLogin(GameProfile gameprofile, ServerPlayer player) { // CraftBukkit - added EntityPlayer
        /* CraftBukkit startMoved up
        UUID uuid = EntityHuman.a(gameprofile);
        List<EntityPlayer> list = Lists.newArrayList();

        for (int i = 0; i < this.players.size(); ++i) {
            EntityPlayer entityplayer = (EntityPlayer) this.players.get(i);

            if (entityplayer.getUniqueID().equals(uuid)) {
                list.add(entityplayer);
            }
        }

        EntityPlayer entityplayer1 = (EntityPlayer) this.j.get(gameprofile.getId());

        if (entityplayer1 != null && !list.contains(entityplayer1)) {
            list.add(entityplayer1);
        }

        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer2 = (EntityPlayer) iterator.next();

            entityplayer2.playerConnection.disconnect(new ChatMessage("multiplayer.disconnect.duplicate_login"));
        }

        WorldServer worldserver = this.server.D();
        Object object;

        if (this.server.isDemoMode()) {
            object = new DemoPlayerInteractManager(worldserver);
        } else {
            object = new PlayerInteractManager(worldserver);
        }

        return new EntityPlayer(this.server, worldserver, gameprofile, (PlayerInteractManager) object);
        */
        return player;
        // CraftBukkit end
    }

    // CraftBukkit start
    public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag) {
        return this.moveToWorld(entityplayer, this.server.getWorldServer(entityplayer.getSpawnDimension()), flag, null, true);
    }

    public ServerPlayer moveToWorld(ServerPlayer entityplayer, ServerLevel worldserver, boolean flag, Location location, boolean avoidSuffocation) {
        entityplayer.stopRiding(); // CraftBukkit
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        entityplayer.getLevel().removePlayerImmediately(entityplayer);
        BlockPos blockposition = entityplayer.getRespawnPosition();
        boolean flag1 = entityplayer.isRespawnForced();
        /* CraftBukkit start
        WorldServer worldserver = this.server.getWorldServer(entityplayer.getSpawnDimension());
        Optional optional;

        if (worldserver != null && blockposition != null) {
            optional = EntityHuman.getBed(worldserver, blockposition, flag1, flag);
        } else {
            optional = Optional.empty();
        }

        WorldServer worldserver1 = worldserver != null && optional.isPresent() ? worldserver : this.server.D();
        Object object;

        if (this.server.isDemoMode()) {
            object = new DemoPlayerInteractManager(worldserver1);
        } else {
            object = new PlayerInteractManager(worldserver1);
        }

        EntityPlayer entityplayer1 = new EntityPlayer(this.server, worldserver1, entityplayer.getProfile(), (PlayerInteractManager) object);
        // */
        ServerPlayer entityplayer1 = entityplayer;
        org.bukkit.World fromWorld = entityplayer.getBukkitEntity().getWorld();
        entityplayer.wonGame = false;
        // CraftBukkit end

        entityplayer1.connection = entityplayer.connection;
        entityplayer1.restoreFrom(entityplayer, flag);
        entityplayer1.setId(entityplayer.getId());
        entityplayer1.setMainArm(entityplayer.getMainArm());
        Iterator iterator = entityplayer.getTags().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            entityplayer1.addTag(s);
        }

        // this.a(entityplayer1, entityplayer, worldserver1); // CraftBukkit - removed
        boolean flag2 = false;

        // CraftBukkit start - fire PlayerRespawnEvent
        if (location == null) {
            boolean isBedSpawn = false;
            ServerLevel worldserver1 = this.server.getWorldServer(entityplayer.getSpawnDimension());
            if (worldserver1 != null) {
                Optional optional;

                if (blockposition != null) {
                    optional = net.minecraft.world.entity.player.Player.findRespawnPositionAndUseSpawnBlock(worldserver1, blockposition, flag1, flag);
                } else {
                    optional = Optional.empty();
                }

                if (optional.isPresent()) {
                    Vec3 vec3d = (Vec3) optional.get();

                    isBedSpawn = true;
                    location = new Location(worldserver1.getWorld(), vec3d.x, vec3d.y, vec3d.z);

                    entityplayer1.setRespawnPosition(worldserver1.getDimensionKey(), blockposition, flag1, false);
                    flag2 = !flag && worldserver1.getType(blockposition).getBlock() instanceof RespawnAnchorBlock;
                } else if (blockposition != null) {
                    entityplayer1.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
                }
            }

            if (location == null) {
                worldserver1 = this.server.getWorldServer(Level.OVERWORLD);
                blockposition = entityplayer1.getSpawnPoint(worldserver1);
                location = new Location(worldserver1.getWorld(), (double) ((float) blockposition.getX() + 0.5F), (double) ((float) blockposition.getY() + 0.1F), (double) ((float) blockposition.getZ() + 0.5F));
            }

            Player respawnPlayer = cserver.getPlayer(entityplayer1);
            PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn);
            cserver.getPluginManager().callEvent(respawnEvent);
            // Spigot Start
            if (entityplayer.connection.isDisconnected()) {
                return entityplayer;
            }
            // Spigot End

            location = respawnEvent.getRespawnLocation();
            if (!flag) entityplayer.reset(); // SPIGOT-4785
        } else {
            location.setWorld(worldserver.getWorld());
        }
        ServerLevel worldserver1 = ((CraftWorld) location.getWorld()).getHandle();
        entityplayer1.forceSetPositionRotation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        // CraftBukkit end

        while (avoidSuffocation && !worldserver1.noCollision(entityplayer1) && entityplayer1.getY() < 256.0D) {
            entityplayer1.setPos(entityplayer1.getX(), entityplayer1.getY() + 1.0D, entityplayer1.getZ());
        }
        // CraftBukkit start
        LevelData worlddata = worldserver1.getLevelData();
        entityplayer1.connection.sendPacket(new ClientboundRespawnPacket(worldserver1.getTypeKey(), worldserver1.getDimensionKey(), BiomeManager.obfuscateSeed(worldserver1.getSeed()), entityplayer1.gameMode.getGameModeForPlayer(), entityplayer1.gameMode.getPreviousGameModeForPlayer(), worldserver1.isDebug(), worldserver1.isFlat(), flag));
        entityplayer1.connection.sendPacket(new ClientboundSetChunkCacheRadiusPacket(worldserver1.spigotConfig.viewDistance)); // Spigot
        entityplayer1.setLevel(worldserver1);
        entityplayer1.removed = false;
        entityplayer1.connection.teleport(new Location(worldserver1.getWorld(), entityplayer1.getX(), entityplayer1.getY(), entityplayer1.getZ(), entityplayer1.yRot, entityplayer1.xRot));
        entityplayer1.setShiftKeyDown(false);

        // entityplayer1.playerConnection.a(entityplayer1.locX(), entityplayer1.locY(), entityplayer1.locZ(), entityplayer1.yaw, entityplayer1.pitch);
        entityplayer1.connection.sendPacket(new ClientboundSetDefaultSpawnPositionPacket(worldserver1.getSharedSpawnPos()));
        entityplayer1.connection.sendPacket(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        entityplayer1.connection.sendPacket(new ClientboundSetExperiencePacket(entityplayer1.experienceProgress, entityplayer1.totalExperience, entityplayer1.experienceLevel));
        this.sendLevelInfo(entityplayer1, worldserver1);
        this.sendPlayerPermissionLevel(entityplayer1);
        if (!entityplayer.connection.isDisconnected()) {
            worldserver1.addRespawnedPlayer(entityplayer1);
            this.players.add(entityplayer1);
            this.playersByName.put(entityplayer1.getScoreboardName().toLowerCase(java.util.Locale.ROOT), entityplayer1); // Spigot
            this.playersByUUID.put(entityplayer1.getUUID(), entityplayer1);
        }
        // entityplayer1.syncInventory();
        entityplayer1.setHealth(entityplayer1.getHealth());
        if (flag2) {
            entityplayer1.connection.sendPacket(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 1.0F, 1.0F));
        }
        // Added from changeDimension
        sendAllPlayerInfo(entityplayer); // Update health, etc...
        entityplayer.onUpdateAbilities();
        for (Object o1 : entityplayer.getActiveEffects()) {
            MobEffectInstance mobEffect = (MobEffectInstance) o1;
            entityplayer.connection.sendPacket(new ClientboundUpdateMobEffectPacket(entityplayer.getId(), mobEffect));
        }

        // Fire advancement trigger
        entityplayer.triggerDimensionChangeTriggers(((CraftWorld) fromWorld).getHandle());

        // Don't fire on respawn
        if (fromWorld != location.getWorld()) {
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(entityplayer.getBukkitEntity(), fromWorld);
            server.server.getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (entityplayer.connection.isDisconnected()) {
            this.save(entityplayer);
        }
        // CraftBukkit end
        return entityplayer1;
    }

    public void sendPlayerPermissionLevel(ServerPlayer entityplayer) {
        GameProfile gameprofile = entityplayer.getGameProfile();
        int i = this.server.getProfilePermissions(gameprofile);

        this.sendPlayerPermissionLevel(entityplayer, i);
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            for (int i = 0; i < this.players.size(); ++i) {
                final ServerPlayer target = (ServerPlayer) this.players.get(i);

                target.connection.sendPacket(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.UPDATE_LATENCY, Iterables.filter(this.players, new Predicate<ServerPlayer>() {
                    @Override
                    public boolean apply(ServerPlayer input) {
                        return target.getBukkitEntity().canSee(input.getBukkitEntity());
                    }
                })));
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }

    }

    public void sendAll(Packet<?> packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            ((ServerPlayer) this.players.get(i)).connection.sendPacket(packet);
        }

    }

    // CraftBukkit start - add a world/entity limited version
    public void sendAll(Packet packet, net.minecraft.world.entity.player.Player entityhuman) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer =  this.players.get(i);
            if (entityhuman != null && entityhuman instanceof ServerPlayer && !entityplayer.getBukkitEntity().canSee(((ServerPlayer) entityhuman).getBukkitEntity())) {
                continue;
            }
            ((ServerPlayer) this.players.get(i)).connection.sendPacket(packet);
        }
    }

    public void sendAll(Packet packet, Level world) {
        for (int i = 0; i < world.players().size(); ++i) {
            ((ServerPlayer) world.players().get(i)).connection.sendPacket(packet);
        }

    }
    // CraftBukkit end

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> resourcekey) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer = (ServerPlayer) this.players.get(i);

            if (entityplayer.level.getDimensionKey() == resourcekey) {
                entityplayer.connection.sendPacket(packet);
            }
        }

    }

    public void broadcastToTeam(net.minecraft.world.entity.player.Player entityhuman, Component ichatbasecomponent) {
        Team scoreboardteambase = entityhuman.getTeam();

        if (scoreboardteambase != null) {
            Collection<String> collection = scoreboardteambase.getPlayers();
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                ServerPlayer entityplayer = this.getPlayerByName(s);

                if (entityplayer != null && entityplayer != entityhuman) {
                    entityplayer.sendMessage(ichatbasecomponent, entityhuman.getUUID());
                }
            }

        }
    }

    public void broadcastToAllExceptTeam(net.minecraft.world.entity.player.Player entityhuman, Component ichatbasecomponent) {
        Team scoreboardteambase = entityhuman.getTeam();

        if (scoreboardteambase == null) {
            this.broadcastMessage(ichatbasecomponent, ChatType.SYSTEM, entityhuman.getUUID());
        } else {
            for (int i = 0; i < this.players.size(); ++i) {
                ServerPlayer entityplayer = (ServerPlayer) this.players.get(i);

                if (entityplayer.getTeam() != scoreboardteambase) {
                    entityplayer.sendMessage(ichatbasecomponent, entityhuman.getUUID());
                }
            }

        }
    }

    public String[] getPlayerNamesArray() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            astring[i] = ((ServerPlayer) this.players.get(i)).getGameProfile().getName();
        }

        return astring;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile gameprofile) {
        this.ops.add(new ServerOpListEntry(gameprofile, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(gameprofile)));
        ServerPlayer entityplayer = this.getPlayer(gameprofile.getId());

        if (entityplayer != null) {
            this.sendPlayerPermissionLevel(entityplayer);
        }

    }

    public void deop(GameProfile gameprofile) {
        this.ops.remove(gameprofile);
        ServerPlayer entityplayer = this.getPlayer(gameprofile.getId());

        if (entityplayer != null) {
            this.sendPlayerPermissionLevel(entityplayer);
        }

    }

    private void sendPlayerPermissionLevel(ServerPlayer entityplayer, int i) {
        if (entityplayer.connection != null) {
            byte b0;

            if (i <= 0) {
                b0 = 24;
            } else if (i >= 4) {
                b0 = 28;
            } else {
                b0 = (byte) (24 + i);
            }

            entityplayer.connection.sendPacket(new ClientboundEntityEventPacket(entityplayer, b0));
        }

        entityplayer.getBukkitEntity().recalculatePermissions(); // CraftBukkit
        this.server.getCommands().sendCommands(entityplayer);
    }

    public boolean isWhiteListed(GameProfile gameprofile) {
        return !this.doWhiteList || this.ops.contains(gameprofile) || this.whitelist.contains(gameprofile);
    }

    public boolean isOp(GameProfile gameprofile) {
        return this.ops.contains(gameprofile) || this.server.isSingleplayerOwner(gameprofile) && this.server.getWorldData().getAllowCommands() || this.allowCheatsForAllPlayers;
    }

    @Nullable
    public ServerPlayer getPlayerByName(String s) {
        return this.playersByName.get(s.toLowerCase(java.util.Locale.ROOT)); // Spigot
    }

    public void sendPacketNearby(@Nullable net.minecraft.world.entity.player.Player entityhuman, double d0, double d1, double d2, double d3, ResourceKey<Level> resourcekey, Packet<?> packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer = (ServerPlayer) this.players.get(i);

            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (entityhuman != null && entityhuman instanceof ServerPlayer && !entityplayer.getBukkitEntity().canSee(((ServerPlayer) entityhuman).getBukkitEntity())) {
               continue;
            }
            // CraftBukkit end

            if (entityplayer != entityhuman && entityplayer.level.getDimensionKey() == resourcekey) {
                double d4 = d0 - entityplayer.getX();
                double d5 = d1 - entityplayer.getY();
                double d6 = d2 - entityplayer.getZ();

                if (d4 * d4 + d5 * d5 + d6 * d6 < d3 * d3) {
                    entityplayer.connection.sendPacket(packet);
                }
            }
        }

    }

    public void saveAll() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.save((ServerPlayer) this.players.get(i));
        }

    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOPs() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {}

    public void sendLevelInfo(ServerPlayer entityplayer, ServerLevel worldserver) {
        WorldBorder worldborder = entityplayer.level.getWorldBorder(); // CraftBukkit

        entityplayer.connection.sendPacket(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.INITIALIZE));
        entityplayer.connection.sendPacket(new ClientboundSetTimePacket(worldserver.getGameTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        entityplayer.connection.sendPacket(new ClientboundSetDefaultSpawnPositionPacket(worldserver.getSharedSpawnPos()));
        if (worldserver.isRaining()) {
            // CraftBukkit start - handle player weather
            // entityplayer.playerConnection.sendPacket(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.b, 0.0F));
            // entityplayer.playerConnection.sendPacket(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.h, worldserver.d(1.0F)));
            // entityplayer.playerConnection.sendPacket(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.i, worldserver.b(1.0F)));
            entityplayer.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            entityplayer.updateWeather(-worldserver.rainLevel, worldserver.rainLevel, -worldserver.thunderLevel, worldserver.thunderLevel);
            // CraftBukkit end
        }

    }

    public void sendAllPlayerInfo(ServerPlayer entityplayer) {
        entityplayer.refreshContainer(entityplayer.inventoryMenu);
        // entityplayer.triggerHealthUpdate();
        entityplayer.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        entityplayer.connection.sendPacket(new ClientboundSetCarriedItemPacket(entityplayer.inventory.selected));
        // CraftBukkit start - from GameRules
        int i = entityplayer.level.getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
        entityplayer.connection.sendPacket(new ClientboundEntityEventPacket(entityplayer, (byte) i));
        float immediateRespawn = entityplayer.level.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        entityplayer.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean flag) {
        this.doWhiteList = flag;
    }

    public List<ServerPlayer> getPlayersWithAddress(String s) {
        List<ServerPlayer> list = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.getIpAddress().equals(s)) {
                list.add(entityplayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public CompoundTag getSingleplayerData() {
        return null;
    }

    private void updatePlayerGameMode(ServerPlayer entityplayer, @Nullable ServerPlayer entityplayer1, ServerLevel worldserver) {
        if (entityplayer1 != null) {
            entityplayer.gameMode.setGameModeForPlayer(entityplayer1.gameMode.getGameModeForPlayer(), entityplayer1.gameMode.getPreviousGameModeForPlayer());
        } else if (this.overrideGameMode != null) {
            entityplayer.gameMode.setGameModeForPlayer(this.overrideGameMode, GameType.NOT_SET);
        }

        entityplayer.gameMode.updateGameMode(worldserver.getServer().getWorldData().getGameType());
    }

    public void removeAll() {
        // CraftBukkit start - disconnect safely
        for (ServerPlayer player : this.players) {
            player.connection.disconnect(this.server.server.getShutdownMessage()); // CraftBukkit - add custom shutdown message
        }
        // CraftBukkit end

    }

    // CraftBukkit start
    public void sendMessage(Component[] iChatBaseComponents) {
        for (Component component : iChatBaseComponents) {
            broadcastMessage(component, ChatType.SYSTEM, Util.NIL_UUID);
        }
    }
    // CraftBukkit end

    public void broadcastMessage(Component ichatbasecomponent, ChatType chatmessagetype, UUID uuid) {
        this.server.sendMessage(ichatbasecomponent, uuid);
        // CraftBukkit start - we run this through our processor first so we can get web links etc
        this.sendAll(new ClientboundChatPacket(CraftChatMessage.fixComponent(ichatbasecomponent), chatmessagetype, uuid));
        // CraftBukkit end
    }

    public void sendMessage(Component ichatbasecomponent) {
        this.broadcastMessage(ichatbasecomponent, ChatType.SYSTEM, Util.NIL_UUID);
    }

    // CraftBukkit start
    public ServerStatsCounter getStatisticManager(ServerPlayer entityhuman) {
        ServerStatsCounter serverstatisticmanager = entityhuman.getStats();
        return serverstatisticmanager == null ? getStatisticManager(entityhuman.getUUID(), entityhuman.getName().getString()) : serverstatisticmanager;
    }

    public ServerStatsCounter getStatisticManager(UUID uuid, String displayName) {
        ServerPlayer entityhuman = this.getPlayer(uuid);
        ServerStatsCounter serverstatisticmanager = entityhuman == null ? null : (ServerStatsCounter) entityhuman.getStats();
        // CraftBukkit end

        if (serverstatisticmanager == null) {
            File file = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
            File file1 = new File(file, uuid + ".json");

            if (!file1.exists()) {
                File file2 = new File(file, displayName + ".json"); // CraftBukkit

                if (file2.exists() && file2.isFile()) {
                    file2.renameTo(file1);
                }
            }

            serverstatisticmanager = new ServerStatsCounter(this.server, file1);
            // this.o.put(uuid, serverstatisticmanager); // CraftBukkit
        }

        return serverstatisticmanager;
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer entityplayer) {
        UUID uuid = entityplayer.getUUID();
        PlayerAdvancements advancementdataplayer = (PlayerAdvancements) entityplayer.getAdvancements(); // CraftBukkit

        if (advancementdataplayer == null) {
            File file = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).toFile();
            File file1 = new File(file, uuid + ".json");

            advancementdataplayer = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), file1, entityplayer);
            // this.p.put(uuid, advancementdataplayer); // CraftBukkit
        }

        advancementdataplayer.setPlayer(entityplayer);
        return advancementdataplayer;
    }

    public void setViewDistance(int i) {
        this.viewDistance = i;
        this.sendAll(new ClientboundSetChunkCacheRadiusPacket(i));
        Iterator iterator = this.server.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (worldserver != null) {
                worldserver.getChunkSourceOH().setViewDistance(i);
            }
        }

    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    @Nullable
    public ServerPlayer getPlayer(UUID uuid) {
        return (ServerPlayer) this.playersByUUID.get(uuid);
    }

    public boolean canBypassPlayerLimit(GameProfile gameprofile) {
        return false;
    }

    public void reloadResources() {
        // CraftBukkit start
        /*Iterator iterator = this.p.values().iterator();

        while (iterator.hasNext()) {
            AdvancementDataPlayer advancementdataplayer = (AdvancementDataPlayer) iterator.next();

            advancementdataplayer.a(this.server.getAdvancementData());
        }*/

        for (ServerPlayer player : players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
        }
        // CraftBukkit end

        this.sendAll(new ClientboundUpdateTagsPacket(this.server.getTags()));
        ClientboundUpdateRecipesPacket packetplayoutrecipeupdate = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes());
        Iterator iterator1 = this.players.iterator();

        while (iterator1.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

            entityplayer.connection.sendPacket(packetplayoutrecipeupdate);
            entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
        }

    }

    public boolean isAllowCheatsForAllPlayers() {
        return this.allowCheatsForAllPlayers;
    }
}
