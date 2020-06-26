package net.minecraft.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import jline.console.ConsoleReader;
import joptsimple.OptionSet;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.RegistryReadOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagManager;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.ContinuousProfiler;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Snooper;
import net.minecraft.world.SnooperPopulator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.saveddata.SaveDataDirtyRunnable;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.PredicateManager;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.Main;
import org.bukkit.event.server.ServerLoadEvent;
// CraftBukkit end
import org.bukkit.craftbukkit.SpigotTimings; // Spigot
import org.spigotmc.SlackActivityAccountant; // Spigot

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements SnooperPopulator, CommandSource, AutoCloseable {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final File USERID_CACHE_FILE = new File("usercache.json");
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings("Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), DataPackConfig.DEFAULT);
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final Snooper snooper = new Snooper("server", this, Util.getMillis());
    private final List<Runnable> tickables = Lists.newArrayList();
    private ContinuousProfiler continousProfiler;
    private ProfilerFiller profiler;
    private ServerConnectionListener connection;
    public final ChunkProgressListenerFactory progressListenerFactory;
    private final ServerStatus status;
    private final Random random;
    public final DataFixer fixerUpper;
    private String localIp;
    private int port;
    public final RegistryAccess.Dimension registryHolder;
    public final Map<ResourceKey<Level>, ServerLevel> levels;
    private PlayerList playerList;
    private volatile boolean running;
    private boolean stopped;
    private int tickCount;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    @Nullable
    private String motd;
    private int maxBuildHeight;
    private int playerIdleTimeout;
    public final long[] tickTimes;
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private String singleplayerName;
    private boolean isDemo;
    private String resourcePack;
    private String resourcePackHash;
    private volatile boolean isReady;
    private long lastOverloadWarning;
    private boolean delayProfilerStart;
    private boolean forceGameType;
    private final MinecraftSessionService sessionService;
    private final GameProfileRepository profileRepository;
    private final GameProfileCache profileCache;
    private long lastServerStatus;
    public final Thread serverThread;
    private long nextTickTime;
    private long delayedTasksMaxNextTickTime;
    private boolean mayHaveDelayedTasks;
    private final PackRepository<Pack> packRepository;
    private final ServerScoreboard scoreboard;
    @Nullable
    private CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents;
    private final ServerFunctionManager functionManager;
    private final FrameTimer frameTimer;
    private boolean enforceWhitelist;
    private float averageTickTime;
    public final Executor executor;
    @Nullable
    private String serverId;
    public ServerResources resources;
    private final StructureManager structureManager;
    protected WorldData worldData;

    // CraftBukkit start
    public DataPackConfig datapackconfiguration;
    public org.bukkit.craftbukkit.CraftServer server;
    public OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    public org.bukkit.command.RemoteConsoleCommandSender remoteConsole;
    public ConsoleReader reader;
    public static int currentTick = (int) (System.currentTimeMillis() / 50);
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    public File bukkitDataPackFolder;
    public Commands vanillaCommandDispatcher;
    private boolean forceTicks;
    // CraftBukkit end
    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / TPS;
    private static final int SAMPLE_INTERVAL = 100;
    public final double[] recentTps = new double[ 3 ];
    public final SlackActivityAccountant slackActivityAccountant = new SlackActivityAccountant();
    // Spigot end

    public static <S extends MinecraftServer> S spin(Function<Thread, S> function) {
        AtomicReference<S> atomicreference = new AtomicReference();
        Thread thread = new Thread(() -> {
            ((MinecraftServer) atomicreference.get()).runServer();
        }, "Server thread");

        thread.setUncaughtExceptionHandler((thread1, throwable) -> {
            MinecraftServer.LOGGER.error(throwable);
        });
        S s0 = function.apply(thread); // CraftBukkit - decompile error

        atomicreference.set(s0);
        thread.start();
        return s0;
    }

    public MinecraftServer(OptionSet options, DataPackConfig datapackconfiguration, Thread thread, RegistryAccess.Dimension iregistrycustom_dimension, LevelStorageSource.LevelStorageAccess convertable_conversionsession, WorldData savedata, PackRepository<Pack> resourcepackrepository, Proxy proxy, DataFixer datafixer, ServerResources datapackresources, MinecraftSessionService minecraftsessionservice, GameProfileRepository gameprofilerepository, GameProfileCache usercache, ChunkProgressListenerFactory worldloadlistenerfactory) {
        super("Server");
        this.continousProfiler = new ContinuousProfiler(Util.timeSource, this::getTickCount);
        this.profiler = InactiveProfiler.INSTANCE;
        this.status = new ServerStatus();
        this.random = new Random();
        this.port = -1;
        this.levels = Maps.newLinkedHashMap(); // CraftBukkit - keep order, k+v already use identity methods
        this.running = true;
        this.tickTimes = new long[100];
        this.resourcePack = "";
        this.resourcePackHash = "";
        this.nextTickTime = Util.getMillis();
        this.scoreboard = new ServerScoreboard(this);
        this.customBossEvents = new CustomBossEvents();
        this.frameTimer = new FrameTimer();
        this.registryHolder = iregistrycustom_dimension;
        this.worldData = savedata;
        this.proxy = proxy;
        this.packRepository = resourcepackrepository;
        this.resources = datapackresources;
        this.sessionService = minecraftsessionservice;
        this.profileRepository = gameprofilerepository;
        this.profileCache = usercache;
        // this.serverConnection = new ServerConnection(this); // Spigot
        this.progressListenerFactory = worldloadlistenerfactory;
        this.storageSource = convertable_conversionsession;
        this.playerDataStorage = convertable_conversionsession.createPlayerStorage();
        this.fixerUpper = datafixer;
        this.functionManager = new ServerFunctionManager(this, datapackresources.getFunctionLibrary());
        this.structureManager = new StructureManager(datapackresources.getResourceManager(), convertable_conversionsession, datafixer);
        this.serverThread = thread;
        this.executor = Util.backgroundExecutor();
        // CraftBukkit start
        this.options = options;
        this.datapackconfiguration = datapackconfiguration;
        this.vanillaCommandDispatcher = datapackresources.commands; // CraftBukkit
        // Try to see if we're actually running in a terminal, disable jline if not
        if (System.console() == null && System.getProperty("jline.terminal") == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            Main.useJline = false;
        }

        try {
            reader = new ConsoleReader(System.in, System.out);
            reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                Main.useJline = false;
                reader = new ConsoleReader(System.in, System.out);
                reader.setExpandEvents(false);
            } catch (IOException ex) {
                LOGGER.warn((String) null, ex);
            }
        }
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));
    }
    // CraftBukkit end

    private void readScoreboard(DimensionDataStorage worldpersistentdata) {
        ScoreboardSaveData persistentscoreboard = (ScoreboardSaveData) worldpersistentdata.computeIfAbsent(ScoreboardSaveData::new, "scoreboard");

        persistentscoreboard.setScoreboard((Scoreboard) this.getScoreboard());
        this.getScoreboard().addDirtyListener((Runnable) (new SaveDataDirtyRunnable(persistentscoreboard)));
    }

    protected abstract boolean initServer() throws IOException;

    public static void convertFromRegionFormatIfNeeded(LevelStorageSource.LevelStorageAccess convertable_conversionsession) {
        if (convertable_conversionsession.requiresConversion()) {
            MinecraftServer.LOGGER.info("Converting map! {}", convertable_conversionsession.getLevelId()); // CraftBukkit
            convertable_conversionsession.convert(new ProgressListener() {
                private long timeStamp = Util.getMillis();

                @Override
                public void progressStartNoAbort(Component ichatbasecomponent) {}

                @Override
                public void progressStagePercentage(int i) {
                    if (Util.getMillis() - this.timeStamp >= 1000L) {
                        this.timeStamp = Util.getMillis();
                        MinecraftServer.LOGGER.info("Converting... {}%", i);
                    }

                }

                @Override
                public void progressStage(Component ichatbasecomponent) {}
            });
        }

    }

    protected void loadWorld(String s) {
        int worldCount = 3;

        for (int worldId = 0; worldId < worldCount; ++worldId) {
            ServerLevel world;
            PrimaryLevelData worlddata;
            byte dimension = 0;
            ResourceKey<LevelStem> dimensionKey = LevelStem.OVERWORLD;

            if (worldId == 1) {
                if (isNetherEnabled()) {
                    dimension = -1;
                    dimensionKey = LevelStem.NETHER;
                } else {
                    continue;
                }
            }

            if (worldId == 2) {
                if (server.getAllowEnd()) {
                    dimension = 1;
                    dimensionKey = LevelStem.END;
                } else {
                    continue;
                }
            }

            String worldType = org.bukkit.World.Environment.getEnvironment(dimension).toString().toLowerCase();
            String name = (dimension == 0) ? s : s + "_" + worldType;
            LevelStorageSource.LevelStorageAccess worldSession;
            if (dimension == 0) {
                worldSession = this.storageSource;
            } else {
                try {
                    worldSession = LevelStorageSource.createDefault(server.getWorldContainer().toPath()).c(name, dimensionKey);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                MinecraftServer.convertFromRegionFormatIfNeeded(worldSession); // Run conversion now
            }

            org.bukkit.generator.ChunkGenerator gen = this.server.getGenerator(name);

            RegistryAccess.Dimension iregistrycustom_dimension = RegistryAccess.b();

            RegistryReadOps<Tag> registryreadops = RegistryReadOps.create((DynamicOps) NbtOps.INSTANCE, this.resources.getResourceManager(), (RegistryAccess) iregistrycustom_dimension);
            worlddata = (PrimaryLevelData) worldSession.getDataTag((DynamicOps) registryreadops, datapackconfiguration);
            if (worlddata == null) {
                LevelSettings worldsettings;
                WorldGenSettings generatorsettings;

                if (this.isDemo()) {
                    worldsettings = MinecraftServer.DEMO_SETTINGS;
                    generatorsettings = WorldGenSettings.DEMO_SETTINGS;
                } else {
                    DedicatedServerProperties dedicatedserverproperties = ((DedicatedServer) this).getProperties();

                    worldsettings = new LevelSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty, false, new GameRules(), datapackconfiguration);
                    generatorsettings = options.has("bonusChest") ? dedicatedserverproperties.worldGenSettings.withBonusChest() : dedicatedserverproperties.worldGenSettings;
                }

                worlddata = new PrimaryLevelData(worldsettings, generatorsettings, Lifecycle.stable());
            }
            worlddata.checkName(name); // CraftBukkit - Migration did not rewrite the level.dat; This forces 1.8 to take the last loaded world as respawn (in this case the end)
            if (options.has("forceUpgrade")) {
                net.minecraft.server.Main.forceUpgrade(worldSession, DataFixers.getDataFixerOH(), options.has("eraseCache"), () -> {
                    return true;
                }, worlddata.worldGenSettings().levels());
            }

            ServerLevelData iworlddataserver = worlddata;
            WorldGenSettings generatorsettings = worlddata.worldGenSettings();
            boolean flag = generatorsettings.isDebug();
            long i = generatorsettings.seed();
            long j = BiomeManager.obfuscateSeed(i);
            List<CustomSpawner> list = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(iworlddataserver));
            MappedRegistry<LevelStem> registrymaterials = generatorsettings.dimensions();
            LevelStem worlddimension = (LevelStem) registrymaterials.get(dimensionKey);
            DimensionType dimensionmanager;
            ChunkGenerator chunkgenerator;

            if (worlddimension == null) {
                dimensionmanager = DimensionType.defaultOverworld();
                chunkgenerator = WorldGenSettings.makeDefaultOverworld((new Random()).nextLong());
            } else {
                dimensionmanager = worlddimension.type();
                chunkgenerator = worlddimension.generator();
            }

            ResourceKey<DimensionType> typeKey = (ResourceKey) this.registryHolder.a().getResourceKey(dimensionmanager).orElseThrow(() -> {
                return new IllegalStateException("Unregistered dimension type: " + dimensionmanager);
            });
            ResourceKey<Level> worldKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionKey.location());

            if (worldId == 0) {
                this.worldData = worlddata;
                this.worldData.setGameType(((DedicatedServer) this).getProperties().gamemode); // From DedicatedServer.init

                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(11);

                world = new ServerLevel(this, this.executor, worldSession, iworlddataserver, worldKey, typeKey, dimensionmanager, worldloadlistener, chunkgenerator, flag, j, list, true, org.bukkit.World.Environment.getEnvironment(dimension), gen);
                DimensionDataStorage worldpersistentdata = world.getDataStorage();
                this.readScoreboard(worldpersistentdata);
                this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, world.getScoreboard());
                this.commandStorage = new CommandStorage(worldpersistentdata);
            } else {
                String dim = "DIM" + dimension;

                File newWorld = new File(new File(name), dim);
                File oldWorld = new File(new File(s), dim);
                File oldLevelDat = new File(new File(s), "level.dat"); // The data folders exist on first run as they are created in the PersistentCollection constructor above, but the level.dat won't

                if (!newWorld.isDirectory() && oldWorld.isDirectory() && oldLevelDat.isFile()) {
                    MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder required ----");
                    MinecraftServer.LOGGER.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    MinecraftServer.LOGGER.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    MinecraftServer.LOGGER.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        MinecraftServer.LOGGER.warn("A file or folder already exists at " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            MinecraftServer.LOGGER.info("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);
                            // Migrate world data too.
                            try {
                                com.google.common.io.Files.copy(oldLevelDat, new File(new File(name), "level.dat"));
                                org.apache.commons.io.FileUtils.copyDirectory(new File(new File(s), "data"), new File(new File(name), "data"));
                            } catch (IOException exception) {
                                MinecraftServer.LOGGER.warn("Unable to migrate world data.");
                            }
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            MinecraftServer.LOGGER.warn("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        MinecraftServer.LOGGER.warn("Could not create path for " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(11);
                world = new ServerLevel(this, this.executor, worldSession, iworlddataserver, worldKey, typeKey, dimensionmanager, worldloadlistener, chunkgenerator, flag, j, ImmutableList.of(), true, org.bukkit.World.Environment.getEnvironment(dimension), gen);
            }

            worlddata.setModdedInfo(this.getServerModName(), this.getModdedStatus().isPresent());
            this.initWorld(world, worlddata, worldData, worlddata.worldGenSettings());
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(world.getWorld()));

            this.levels.put(world.getDimensionKey(), world);
            this.getPlayerList().setLevel(world);

            if (worlddata.getCustomBossEvents() != null) {
                this.getCustomBossEvents().load(worlddata.getCustomBossEvents());
            }
        }
        this.forceDifficulty();
        for (ServerLevel worldserver : this.getAllLevels()) {
            this.loadSpawn(worldserver.getChunkSourceOH().chunkMap.progressListener, worldserver);
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(worldserver.getWorld()));
        }

        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        this.server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
        // CraftBukkit end

    }

    protected void forceDifficulty() {}

    // CraftBukkit start
    public void initWorld(ServerLevel worldserver, ServerLevelData iworlddataserver, WorldData saveData, WorldGenSettings generatorsettings) {
        boolean flag = generatorsettings.isDebug();
        // CraftBukkit start
        if (worldserver.generator != null) {
            worldserver.getWorld().getPopulators().addAll(worldserver.generator.getDefaultPopulators(worldserver.getWorld()));
        }
        WorldBorder worldborder = worldserver.getWorldBorder();

        worldborder.applySettings(iworlddataserver.getWorldBorder());
        if (!iworlddataserver.isInitialized()) {
            try {
                setInitialSpawn(worldserver, iworlddataserver, generatorsettings.generateBonusChest(), flag, true);
                iworlddataserver.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception initializing level");

                try {
                    worldserver.fillReportDetails(crashreport);
                } catch (Throwable throwable1) {
                    ;
                }

                throw new ReportedException(crashreport);
            }

            iworlddataserver.setInitialized(true);
        }
    }
    // CraftBukkit end

    private static void setInitialSpawn(ServerLevel worldserver, ServerLevelData iworlddataserver, boolean flag, boolean flag1, boolean flag2) {
        ChunkGenerator chunkgenerator = worldserver.getChunkSourceOH().getGenerator();

        if (!flag2) {
            iworlddataserver.setSpawn(BlockPos.ZERO.above(chunkgenerator.getSpawnHeight()));
        } else if (flag1) {
            iworlddataserver.setSpawn(BlockPos.ZERO.above());
        } else {
            BiomeSource worldchunkmanager = chunkgenerator.getWorldChunkManager();
            List<Biome> list = worldchunkmanager.getPlayerSpawnBiomes();
            Random random = new Random(worldserver.getSeed());
            BlockPos blockposition = worldchunkmanager.findBiomeHorizontal(0, worldserver.getSeaLevel(), 0, 256, list, random);
            ChunkPos chunkcoordintpair = blockposition == null ? new ChunkPos(0, 0) : new ChunkPos(blockposition);
            // CraftBukkit start
            if (worldserver.generator != null) {
                Random rand = new Random(worldserver.getSeed());
                org.bukkit.Location spawn = worldserver.generator.getFixedSpawnLocation(worldserver.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != worldserver.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + iworlddataserver.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        iworlddataserver.setSpawn(new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()));
                        return;
                    }
                }
            }
            // CraftBukkit end

            if (blockposition == null) {
                MinecraftServer.LOGGER.warn("Unable to find spawn biome");
            }

            boolean flag3 = false;
            Iterator iterator = BlockTags.VALID_SPAWN.getValues().iterator();

            while (iterator.hasNext()) {
                Block block = (Block) iterator.next();

                if (worldchunkmanager.getSurfaceBlocks().contains(block.getBlockData())) {
                    flag3 = true;
                    break;
                }
            }

            iworlddataserver.setSpawn(chunkcoordintpair.getWorldPosition().offset(8, chunkgenerator.getSpawnHeight(), 8));
            int i = 0;
            int j = 0;
            int k = 0;
            int l = -1;
            boolean flag4 = true;

            for (int i1 = 0; i1 < 1024; ++i1) {
                if (i > -16 && i <= 16 && j > -16 && j <= 16) {
                    BlockPos blockposition1 = PlayerRespawnLogic.getSpawnPosInChunk(worldserver, new ChunkPos(chunkcoordintpair.x + i, chunkcoordintpair.z + j), flag3);

                    if (blockposition1 != null) {
                        iworlddataserver.setSpawn(blockposition1);
                        break;
                    }
                }

                if (i == j || i < 0 && i == -j || i > 0 && i == 1 - j) {
                    int j1 = k;

                    k = -l;
                    l = j1;
                }

                i += k;
                j += l;
            }

            if (flag) {
                ConfiguredFeature<?, ?> worldgenfeatureconfigured = Feature.BONUS_CHEST.configured(FeatureConfiguration.NONE); // CraftBukkit - decompile error

                worldgenfeatureconfigured.place(worldserver, worldserver.getStructureManager(), chunkgenerator, worldserver.random, new BlockPos(iworlddataserver.getXSpawn(), iworlddataserver.getYSpawn(), iworlddataserver.getZSpawn()));
            }

        }
    }

    private void setupDebugLevel(WorldData savedata) {
        savedata.setDifficulty(Difficulty.PEACEFUL);
        savedata.setDifficultyLocked(true);
        ServerLevelData iworlddataserver = savedata.overworldData();

        iworlddataserver.setRaining(false);
        iworlddataserver.setThundering(false);
        iworlddataserver.setClearWeatherTime(1000000000);
        iworlddataserver.setDayTime(6000L);
        iworlddataserver.setGameType(GameType.SPECTATOR);
    }

    // CraftBukkit start
    public void loadSpawn(ChunkProgressListener worldloadlistener, ServerLevel worldserver) {
        if (!worldserver.getWorld().getKeepSpawnInMemory()) {
            return;
        }

        // WorldServer worldserver = this.D();
        this.forceTicks = true;
        // CraftBukkit end

        MinecraftServer.LOGGER.info("Preparing start region for dimension {}", worldserver.getDimensionKey().location());
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        worldloadlistener.updateSpawnPos(new ChunkPos(blockposition));
        ServerChunkCache chunkproviderserver = worldserver.getChunkSourceOH();

        chunkproviderserver.getLightEngine().setTaskPerBatch(500);
        this.nextTickTime = Util.getMillis();
        chunkproviderserver.addRegionTicket(TicketType.START, new ChunkPos(blockposition), 11, Unit.INSTANCE);

        while (chunkproviderserver.getTickingGenerated() != 441) {
            // CraftBukkit start
            // this.nextTick = SystemUtils.getMonotonicMillis() + 10L;
            this.executeModerately();
            // CraftBukkit end
        }

        // CraftBukkit start
        // this.nextTick = SystemUtils.getMonotonicMillis() + 10L;
        this.executeModerately();
        // Iterator iterator = this.worldServer.values().iterator();

        if (true) {
            ServerLevel worldserver1 = worldserver;
            ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) worldserver.getDataStorage().get(ForcedChunksSavedData::new, "chunks");
            // CraftBukkit end

            if (forcedchunk != null) {
                LongIterator longiterator = forcedchunk.getChunks().iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    ChunkPos chunkcoordintpair = new ChunkPos(i);

                    worldserver1.getChunkSourceOH().updateChunkForced(chunkcoordintpair, true);
                }
            }
        }

        // CraftBukkit start
        // this.nextTick = SystemUtils.getMonotonicMillis() + 10L;
        this.executeModerately();
        // CraftBukkit end
        worldloadlistener.stop();
        chunkproviderserver.getLightEngine().setTaskPerBatch(5);
        this.updateMobSpawningFlags();

        // CraftBukkit start
        this.forceTicks = false;
        // CraftBukkit end
    }

    protected void detectBundledResources() {
        File file = this.storageSource.getLevelPath(LevelResource.MAP_RESOURCE_FILE).toFile();

        if (file.isFile()) {
            String s = this.storageSource.getLevelId();

            try {
                this.setResourcePack("level://" + URLEncoder.encode(s, StandardCharsets.UTF_8.toString()) + "/" + "resources.zip", "");
            } catch (UnsupportedEncodingException unsupportedencodingexception) {
                MinecraftServer.LOGGER.warn("Something went wrong url encoding {}", s);
            }
        }

    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean flag, boolean flag1, boolean flag2) {
        boolean flag3 = false;

        for (Iterator iterator = this.getAllLevels().iterator(); iterator.hasNext(); flag3 = true) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (!flag) {
                MinecraftServer.LOGGER.info("Saving chunks for level '{}'/{}", worldserver, worldserver.getDimensionKey().location());
            }

            worldserver.save((ProgressListener) null, flag1, worldserver.noSave && !flag2);
        }

        // CraftBukkit start - moved to WorldServer.save
        /*
        WorldServer worldserver1 = this.D();
        IWorldDataServer iworlddataserver = this.saveData.G();

        iworlddataserver.a(worldserver1.getWorldBorder().t());
        this.saveData.setCustomBossEvents(this.getBossBattleCustomData().save());
        this.convertable.a(this.f, this.saveData, this.getPlayerList().save());
        */
        // CraftBukkit end
        return flag3;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // CraftBukkit start
    private boolean hasStopped = false;
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (stopLock) {
            return hasStopped;
        }
    }
    // CraftBukkit end

    protected void stopServer() {
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(stopLock) {
            if (hasStopped) return;
            hasStopped = true;
        }
        // CraftBukkit end
        MinecraftServer.LOGGER.info("Stopping server");
        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
        }
        // CraftBukkit end
        if (this.getConnection() != null) {
            this.getConnection().stop();
        }

        if (this.playerList != null) {
            MinecraftServer.LOGGER.info("Saving players");
            this.playerList.saveAll();
            this.playerList.removeAll();
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        MinecraftServer.LOGGER.info("Saving worlds");
        Iterator iterator = this.getAllLevels().iterator();

        ServerLevel worldserver;

        while (iterator.hasNext()) {
            worldserver = (ServerLevel) iterator.next();
            if (worldserver != null) {
                worldserver.noSave = false;
            }
        }

        this.saveAllChunks(false, true, false);
        iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            worldserver = (ServerLevel) iterator.next();
            if (worldserver != null) {
                try {
                    worldserver.close();
                } catch (IOException ioexception) {
                    MinecraftServer.LOGGER.error("Exception closing the level", ioexception);
                }
            }
        }

        if (this.snooper.isStarted()) {
            this.snooper.interrupt();
        }

        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException ioexception1) {
            MinecraftServer.LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), ioexception1);
        }
        // Spigot start
        if (org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) {
            LOGGER.info("Saving usercache.json");
            this.getProfileCache().save();
        }
        // Spigot end

    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String s) {
        this.localIp = s;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean flag) {
        this.running = false;
        if (flag) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedexception) {
                MinecraftServer.LOGGER.error("Error while shutting down", interruptedexception);
            }
        }

    }

    // Spigot Start
    private static double calcTps(double avg, double exp, double tps)
    {
        return ( avg * exp ) + ( tps * ( 1 - exp ) );
    }
    // Spigot End

    protected void runServer() {
        try {
            if (this.initServer()) {
                this.nextTickTime = Util.getMillis();
                this.status.setDescription(new TextComponent(this.motd));
                this.status.setVersion(new ServerStatus.Version(SharedConstants.getCurrentVersion().getName(), SharedConstants.getCurrentVersion().getProtocolVersion()));
                this.updateStatusIcon(this.status);

                // Spigot start
                Arrays.fill( recentTps, 20 );
                long curTime, tickSection = Util.getMillis(), tickCount = 1;
                while (this.running) {
                    long i = (curTime = Util.getMillis()) - this.nextTickTime;

                    if (i > 5000L && this.nextTickTime - this.lastOverloadWarning >= 30000L) { // CraftBukkit
                        long j = i / 50L;

                        if (server.getWarnOnOverload()) // CraftBukkit
                        MinecraftServer.LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", i, j);
                        this.nextTickTime += j * 50L;
                        this.lastOverloadWarning = this.nextTickTime;
                    }

                    if ( tickCount++ % SAMPLE_INTERVAL == 0 )
                    {
                        double currentTps = 1E3 / ( curTime - tickSection ) * SAMPLE_INTERVAL;
                        recentTps[0] = calcTps( recentTps[0], 0.92, currentTps ); // 1/exp(5sec/1min)
                        recentTps[1] = calcTps( recentTps[1], 0.9835, currentTps ); // 1/exp(5sec/5min)
                        recentTps[2] = calcTps( recentTps[2], 0.9945, currentTps ); // 1/exp(5sec/15min)
                        tickSection = curTime;
                    }
                    // Spigot end

                    MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit
                    this.nextTickTime += 50L;
                    SingleTickProfiler gameprofilertick = SingleTickProfiler.createTickProfiler("Server");

                    this.startProfilerTick(gameprofilertick);
                    this.profiler.startTick();
                    this.profiler.push("tick");
                    this.tickServer(this::haveTime);
                    this.profiler.popPush("nextTickWait");
                    this.mayHaveDelayedTasks = true;
                    this.delayedTasksMaxNextTickTime = Math.max(Util.getMillis() + 50L, this.nextTickTime);
                    this.waitUntilNextTick();
                    this.profiler.pop();
                    this.profiler.endTick();
                    this.endProfilerTick(gameprofilertick);
                    this.isReady = true;
                }
            } else {
                this.onServerCrash((CrashReport) null);
            }
        } catch (Throwable throwable) {
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
            // Spigot Start
            if ( throwable.getCause() != null )
            {
                MinecraftServer.LOGGER.error( "\tCause of unexpected exception was", throwable.getCause() );
            }
            // Spigot End
            CrashReport crashreport;

            if (throwable instanceof ReportedException) {
                crashreport = this.fillReport(((ReportedException) throwable).getReport());
            } else {
                crashreport = this.fillReport(new CrashReport("Exception in server tick loop", throwable));
            }

            File file = new File(new File(this.getServerDirectory(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");

            if (crashreport.saveToFile(file)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", file.getAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable1) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable1);
            } finally {
                org.spigotmc.WatchdogThread.doStop(); // Spigot
                // CraftBukkit start - Restore terminal to original settings
                try {
                    reader.getTerminal().restore();
                } catch (Exception ignored) {
                }
                // CraftBukkit end
                this.onServerExit();
            }

        }

    }

    private boolean haveTime() {
        // CraftBukkit start
        return this.forceTicks || this.runningTask() || Util.getMillis() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTime : this.nextTickTime);
    }

    private void executeModerately() {
        this.runAllTasks();
        java.util.concurrent.locks.LockSupport.parkNanos("executing tasks", 1000L);
    }
    // CraftBukkit end

    protected void waitUntilNextTick() {
        this.runAllTasks();
        this.managedBlock(() -> {
            return !this.haveTime();
        });
    }

    @Override
    protected TickTask wrapRunnable(Runnable runnable) {
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean shouldRun(TickTask ticktask) {
        return ticktask.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    public boolean pollTask() {
        boolean flag = this.pollTaskInternal();

        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            return true;
        } else {
            if (this.haveTime()) {
                Iterator iterator = this.getAllLevels().iterator();

                while (iterator.hasNext()) {
                    ServerLevel worldserver = (ServerLevel) iterator.next();

                    if (worldserver.getChunkSourceOH().pollTask()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    protected void c(TickTask ticktask) {
        this.getProfiler().incrementCounter("runTask");
        super.doRunTask(ticktask);
    }

    private void updateStatusIcon(ServerStatus serverping) {
        File file = this.getFile("server-icon.png");

        if (!file.exists()) {
            file = this.storageSource.getIconFile();
        }

        if (file.isFile()) {
            ByteBuf bytebuf = Unpooled.buffer();

            try {
                BufferedImage bufferedimage = ImageIO.read(file);

                Validate.validState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide", new Object[0]);
                Validate.validState(bufferedimage.getHeight() == 64, "Must be 64 pixels high", new Object[0]);
                ImageIO.write(bufferedimage, "PNG", new ByteBufOutputStream(bytebuf));
                ByteBuffer bytebuffer = Base64.getEncoder().encode(bytebuf.nioBuffer());

                serverping.setFavicon("data:image/png;base64," + StandardCharsets.UTF_8.decode(bytebuffer));
            } catch (Exception exception) {
                MinecraftServer.LOGGER.error("Couldn't load server icon", exception);
            } finally {
                bytebuf.release();
            }
        }

    }

    public File getServerDirectory() {
        return new File(".");
    }

    protected void onServerCrash(CrashReport crashreport) {}

    protected void onServerExit() {}

    protected void tickServer(BooleanSupplier booleansupplier) {
        SpigotTimings.serverTickTimer.startTiming(); // Spigot
        this.slackActivityAccountant.tickStarted(); // Spigot
        long i = Util.getNanos();

        ++this.tickCount;
        this.tickChildren(booleansupplier);
        if (i - this.lastServerStatus >= 5000000000L) {
            this.lastServerStatus = i;
            this.status.setPlayerSample(new ServerStatus.ServerPingPlayerSample(this.getMaxPlayers(), this.getPlayerCount()));
            GameProfile[] agameprofile = new GameProfile[Math.min(this.getPlayerCount(), 12)];
            int j = Mth.nextInt(this.random, 0, this.getPlayerCount() - agameprofile.length);

            for (int k = 0; k < agameprofile.length; ++k) {
                agameprofile[k] = ((ServerPlayer) this.playerList.getPlayers().get(j + k)).getGameProfile();
            }

            Collections.shuffle(Arrays.asList(agameprofile));
            this.status.b().a(agameprofile);
        }

        if (autosavePeriod > 0 && this.tickCount % autosavePeriod == 0) { // CraftBukkit
            SpigotTimings.worldSaveTimer.startTiming(); // Spigot
            MinecraftServer.LOGGER.debug("Autosave started");
            this.profiler.push("save");
            this.playerList.saveAll();
            this.saveAllChunks(true, false, false);
            this.profiler.pop();
            MinecraftServer.LOGGER.debug("Autosave finished");
            SpigotTimings.worldSaveTimer.stopTiming(); // Spigot
        }

        this.profiler.push("snooper");
        if (((DedicatedServer) this).getProperties().snooperEnabled && !this.snooper.isStarted() && this.tickCount > 100) { // Spigot
            this.snooper.start();
        }

        if (((DedicatedServer) this).getProperties().snooperEnabled && this.tickCount % 6000 == 0) { // Spigot
            this.snooper.prepare();
        }

        this.profiler.pop();
        this.profiler.push("tallying");
        long l = this.tickTimes[this.tickCount % 100] = Util.getNanos() - i;

        this.averageTickTime = this.averageTickTime * 0.8F + (float) l / 1000000.0F * 0.19999999F;
        long i1 = Util.getNanos();

        this.frameTimer.logFrameDuration(i1 - i);
        this.profiler.pop();
        org.spigotmc.WatchdogThread.tick(); // Spigot
        this.slackActivityAccountant.tickEnded(l); // Spigot
        SpigotTimings.serverTickTimer.stopTiming(); // Spigot
        org.spigotmc.CustomTimingsHandler.tick(); // Spigot
    }

    protected void tickChildren(BooleanSupplier booleansupplier) {
        SpigotTimings.schedulerTimer.startTiming(); // Spigot
        this.server.getScheduler().mainThreadHeartbeat(this.tickCount); // CraftBukkit
        SpigotTimings.schedulerTimer.stopTiming(); // Spigot
        this.profiler.push("commandFunctions");
        SpigotTimings.commandFunctionsTimer.startTiming(); // Spigot
        this.getFunctions().tick();
        SpigotTimings.commandFunctionsTimer.stopTiming(); // Spigot
        this.profiler.popPush("levels");
        Iterator iterator = this.getAllLevels().iterator();

        // CraftBukkit start
        // Run tasks that are waiting on processing
        SpigotTimings.processQueueTimer.startTiming(); // Spigot
        while (!processQueue.isEmpty()) {
            processQueue.remove().run();
        }
        SpigotTimings.processQueueTimer.stopTiming(); // Spigot

        SpigotTimings.timeUpdateTimer.startTiming(); // Spigot
        // Send time updates to everyone, it will get the right time from the world the player is in.
        if (this.tickCount % 20 == 0) {
            for (int i = 0; i < this.getPlayerList().players.size(); ++i) {
                ServerPlayer entityplayer = (ServerPlayer) this.getPlayerList().players.get(i);
                entityplayer.connection.sendPacket(new ClientboundSetTimePacket(entityplayer.level.getGameTime(), entityplayer.getPlayerTime(), entityplayer.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT))); // Add support for per player time
            }
        }
        SpigotTimings.timeUpdateTimer.stopTiming(); // Spigot

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            this.profiler.push(() -> {
                return worldserver + " " + worldserver.getDimensionKey().location();
            });
            /* Drop global time updates
            if (this.ticks % 20 == 0) {
                this.methodProfiler.enter("timeSync");
                this.playerList.a((Packet) (new PacketPlayOutUpdateTime(worldserver.getTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE))), worldserver.getDimensionKey());
                this.methodProfiler.exit();
            }
            // CraftBukkit end */

            this.profiler.push("tick");

            try {
                worldserver.timings.doTick.startTiming(); // Spigot
                worldserver.tick(booleansupplier);
                worldserver.timings.doTick.stopTiming(); // Spigot
            } catch (Throwable throwable) {
                // Spigot Start
                CrashReport crashreport;
                try {
                    crashreport = CrashReport.forThrowable(throwable, "Exception ticking world");
                } catch (Throwable t) {
                    throw new RuntimeException("Error generating crash report", t);
                }
                // Spigot End

                worldserver.fillReportDetails(crashreport);
                throw new ReportedException(crashreport);
            }

            this.profiler.pop();
            this.profiler.pop();
        }

        this.profiler.popPush("connection");
        SpigotTimings.connectionTimer.startTiming(); // Spigot
        this.getConnection().tick();
        SpigotTimings.connectionTimer.stopTiming(); // Spigot
        this.profiler.popPush("players");
        SpigotTimings.playerListTimer.startTiming(); // Spigot
        this.playerList.tick();
        SpigotTimings.playerListTimer.stopTiming(); // Spigot
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            GameTestTicker.singleton.tick();
        }

        this.profiler.popPush("server gui refresh");

        SpigotTimings.tickablesTimer.startTiming(); // Spigot
        for (int i = 0; i < this.tickables.size(); ++i) {
            ((Runnable) this.tickables.get(i)).run();
        }
        SpigotTimings.tickablesTimer.stopTiming(); // Spigot

        this.profiler.pop();
    }

    public boolean isNetherEnabled() {
        return true;
    }

    public void addTickable(Runnable runnable) {
        this.tickables.add(runnable);
    }

<<<<<<< HEAD
=======
    public static void main(final OptionSet optionset) { // CraftBukkit - replaces main(String[] astring)
        /* CraftBukkit start - Replace everything
        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("nogui");
        OptionSpec<Void> optionspec1 = optionparser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpec<Void> optionspec2 = optionparser.accepts("demo");
        OptionSpec<Void> optionspec3 = optionparser.accepts("bonusChest");
        OptionSpec<Void> optionspec4 = optionparser.accepts("forceUpgrade");
        OptionSpec<Void> optionspec5 = optionparser.accepts("eraseCache");
        OptionSpec<Void> optionspec6 = optionparser.accepts("help").forHelp();
        OptionSpec<String> optionspec7 = optionparser.accepts("singleplayer").withRequiredArg();
        OptionSpec<String> optionspec8 = optionparser.accepts("universe").withRequiredArg().defaultsTo(".", new String[0]);
        OptionSpec<String> optionspec9 = optionparser.accepts("world").withRequiredArg();
        OptionSpec<Integer> optionspec10 = optionparser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1, new Integer[0]);
        OptionSpec<String> optionspec11 = optionparser.accepts("serverId").withRequiredArg();
        NonOptionArgumentSpec nonoptionargumentspec = optionparser.nonOptions();

        try {
            OptionSet optionset = optionparser.parse(astring);

            if (optionset.has(optionspec6)) {
                optionparser.printHelpOn(System.err);
                return;
            }
            */ // CraftBukkit end

        try {
            java.nio.file.Path java_nio_file_path = Paths.get("server.properties");
            DedicatedServerSettings dedicatedserversettings = new DedicatedServerSettings(optionset); // CraftBukkit - CLI argument support

            dedicatedserversettings.forceSave();
            java.nio.file.Path java_nio_file_path1 = Paths.get("eula.txt");
            EULA eula = new EULA(java_nio_file_path1);

            if (optionset.has("initSettings")) { // CraftBukkit
                MinecraftServer.LOGGER.info("Initialized '" + java_nio_file_path.toAbsolutePath().toString() + "' and '" + java_nio_file_path1.toAbsolutePath().toString() + "'");
                return;
            }

            // Spigot Start
            boolean eulaAgreed = Boolean.getBoolean( "com.mojang.eula.agree" );
            if ( eulaAgreed )
            {
                System.err.println( "You have used the Spigot command line EULA agreement flag." );
                System.err.println( "By using this setting you are indicating your agreement to Mojang's EULA (https://account.mojang.com/documents/minecraft_eula)." );
                System.err.println( "If you do not agree to the above EULA please stop your server and remove this flag immediately." );
            }
            // Spigot End
            if (!eula.hasAgreedToEULA() && !eulaAgreed) { // Spigot
                MinecraftServer.LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
            File s = (File) optionset.valueOf("universe"); // CraftBukkit
            YggdrasilAuthenticationService yggdrasilauthenticationservice = new com.destroystokyo.paper.profile.PaperAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString()); // Paper
            MinecraftSessionService minecraftsessionservice = yggdrasilauthenticationservice.createMinecraftSessionService();
            GameProfileRepository gameprofilerepository = yggdrasilauthenticationservice.createProfileRepository();
            GameProfileCache usercache = new GameProfileCache(gameprofilerepository, new File(s, MinecraftServer.USERID_CACHE_FILE.getName()));
            // CraftBukkit start
            String s1 = (String) Optional.ofNullable(optionset.valueOf("world")).orElse(dedicatedserversettings.getProperties().levelName);
            final DedicatedServer dedicatedserver = new DedicatedServer(optionset, dedicatedserversettings, DataFixers.getDataFixerOH(), yggdrasilauthenticationservice, minecraftsessionservice, gameprofilerepository, usercache, LoggerChunkProgressListener::new, s1);

            /*
            dedicatedserver.i((String) optionset.valueOf(optionspec7));
            dedicatedserver.setPort((Integer) optionset.valueOf(optionspec10));
            dedicatedserver.e(optionset.has(optionspec2));
            dedicatedserver.f(optionset.has(optionspec3));
            dedicatedserver.setForceUpgrade(optionset.has(optionspec4));
            dedicatedserver.setEraseCache(optionset.has(optionspec5));
            dedicatedserver.c((String) optionset.valueOf(optionspec11));
            */
            boolean flag = !optionset.has("nogui") && !optionset.nonOptionArguments().contains("nogui");

            if (flag && !GraphicsEnvironment.isHeadless()) {
                dedicatedserver.showGui();
            }

            /*
            dedicatedserver.startServerThread();
            Thread thread = new Thread("Server Shutdown Thread") {
                public void run() {
                    dedicatedserver.safeShutdown(true);
                }
            };

            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(MinecraftServer.LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
            */

            if (optionset.has("port")) {
                int port = (Integer) optionset.valueOf("port");
                if (port > 0) {
                    dedicatedserver.setPort(port);
                }
            }

            if (optionset.has("universe")) {
                dedicatedserver.universe = (File) optionset.valueOf("universe");
            }

            if (optionset.has("forceUpgrade")) {
                dedicatedserver.forceUpgrade(true);
            }

            if (optionset.has("eraseCache")) {
                dedicatedserver.eraseCache(true);
            }

            Class.forName("net.minecraft.world.entity.npc.VillagerTrades");// Paper - load this sync so it won't fail later async // Toothpick - reflection fix
            dedicatedserver.serverThread.setPriority(Thread.NORM_PRIORITY+2); // Paper - boost priority
            dedicatedserver.serverThread.start();
            // CraftBukkit end
        } catch (Exception exception) {
            MinecraftServer.LOGGER.fatal("Failed to start the minecraft server", exception);
        }

    }

>>>>>>> Toothpick
    protected void setId(String s) {
        this.serverId = s;
    }

    public File getFile(String s) {
        return new File(this.getServerDirectory(), s);
    }

    public final ServerLevel overworld() {
        return (ServerLevel) this.levels.get(Level.OVERWORLD);
    }

    @Nullable
    public ServerLevel getWorldServer(ResourceKey<Level> resourcekey) {
        return (ServerLevel) this.levels.get(resourcekey);
    }

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    public String getServerModName() {
        return "Spigot"; // Spigot - Spigot > // CraftBukkit - cb > vanilla!
    }

    public CrashReport fillReport(CrashReport crashreport) {
        if (this.playerList != null) {
            crashreport.getSystemDetails().setDetail("Player Count", () -> {
                return this.playerList.getPlayerCount() + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers();
            });
        }

        crashreport.getSystemDetails().setDetail("Data Packs", () -> {
            StringBuilder stringbuilder = new StringBuilder();
            Iterator iterator = this.packRepository.getSelectedPacks().iterator();

            while (iterator.hasNext()) {
                Pack resourcepackloader = (Pack) iterator.next();

                if (stringbuilder.length() > 0) {
                    stringbuilder.append(", ");
                }

                stringbuilder.append(resourcepackloader.getId());
                if (!resourcepackloader.getCompatibility().isCompatible()) {
                    stringbuilder.append(" (incompatible)");
                }
            }

            return stringbuilder.toString();
        });
        if (this.serverId != null) {
            crashreport.getSystemDetails().setDetail("Server Id", () -> {
                return this.serverId;
            });
        }

        return crashreport;
    }

    public abstract Optional<String> getModdedStatus();

    @Override
    public void sendMessage(Component ichatbasecomponent, UUID uuid) {
        MinecraftServer.LOGGER.info(ichatbasecomponent.getString());
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int i) {
        this.port = i;
    }

    public String getSingleplayerName() {
        return this.singleplayerName;
    }

    public void setSingleplayerName(String s) {
        this.singleplayerName = s;
    }

    public boolean isSingleplayer() {
        return this.singleplayerName != null;
    }

    public void setKeyPair(KeyPair keypair) {
        this.keyPair = keypair;
    }

    public void setDifficulty(Difficulty enumdifficulty, boolean flag) {
        if (flag || !this.worldData.isDifficultyLocked()) {
            this.worldData.setDifficulty(this.worldData.isHardcore() ? Difficulty.HARD : enumdifficulty);
            this.updateMobSpawningFlags();
            this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
        }
    }

    public int getScaledTrackingDistance(int i) {
        return i;
    }

    private void updateMobSpawningFlags() {
        Iterator iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            worldserver.setSpawnSettings(this.isSpawningMonsters(), this.isSpawningAnimals());
        }

    }

    public void setDifficultyLocked(boolean flag) {
        this.worldData.setDifficultyLocked(flag);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer entityplayer) {
        LevelData worlddata = entityplayer.getLevel().getLevelData();

        entityplayer.connection.sendPacket(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
    }

    protected boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean flag) {
        this.isDemo = flag;
    }

    public String getResourcePack() {
        return this.resourcePack;
    }

    public String getResourcePackHash() {
        return this.resourcePackHash;
    }

    public void setResourcePack(String s, String s1) {
        this.resourcePack = s;
        this.resourcePackHash = s1;
    }

    @Override
    public void populateSnooper(Snooper mojangstatisticsgenerator) {
        mojangstatisticsgenerator.setDynamicData("whitelist_enabled", false);
        mojangstatisticsgenerator.setDynamicData("whitelist_count", 0);
        if (this.playerList != null) {
            mojangstatisticsgenerator.setDynamicData("players_current", this.getPlayerCount());
            mojangstatisticsgenerator.setDynamicData("players_max", this.getMaxPlayers());
            mojangstatisticsgenerator.setDynamicData("players_seen", this.playerDataStorage.getSeenPlayers().length);
        }

        mojangstatisticsgenerator.setDynamicData("uses_auth", this.onlineMode);
        mojangstatisticsgenerator.setDynamicData("gui_state", this.hasGui() ? "enabled" : "disabled");
        mojangstatisticsgenerator.setDynamicData("run_time", (Util.getMillis() - mojangstatisticsgenerator.getStartupTime()) / 60L * 1000L);
        mojangstatisticsgenerator.setDynamicData("avg_tick_ms", (int) (Mth.average(this.tickTimes) * 1.0E-6D));
        int i = 0;
        Iterator iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (worldserver != null) {
                mojangstatisticsgenerator.setDynamicData("world[" + i + "][dimension]", worldserver.getDimensionKey().location());
                mojangstatisticsgenerator.setDynamicData("world[" + i + "][mode]", this.worldData.getGameType());
                mojangstatisticsgenerator.setDynamicData("world[" + i + "][difficulty]", worldserver.getDifficulty());
                mojangstatisticsgenerator.setDynamicData("world[" + i + "][hardcore]", this.worldData.isHardcore());
                mojangstatisticsgenerator.setDynamicData("world[" + i + "][height]", this.maxBuildHeight);
                mojangstatisticsgenerator.setDynamicData("world[" + i + "][chunks_loaded]", worldserver.getChunkSourceOH().getLoadedChunksCount());
                ++i;
            }
        }

        mojangstatisticsgenerator.setDynamicData("worlds", i);
    }

    public abstract boolean isDedicatedServer();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean flag) {
        this.onlineMode = flag;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean flag) {
        this.preventProxyConnections = flag;
    }

    public boolean isSpawningAnimals() {
        return true;
    }

    public boolean areNpcsEnabled() {
        return true;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean flag) {
        this.pvp = flag;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean flag) {
        this.allowFlight = flag;
    }

    public abstract boolean isCommandBlockEnabled();

    public String getMotd() {
        return this.motd;
    }

    public void setMotd(String s) {
        this.motd = s;
    }

    public int getMaxBuildHeight() {
        return this.maxBuildHeight;
    }

    public void setMaxBuildHeight(int i) {
        this.maxBuildHeight = i;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList playerlist) {
        this.playerList = playerlist;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType enumgamemode) {
        this.worldData.setGameType(enumgamemode);
    }

    @Nullable
    public ServerConnectionListener getConnection() {
        return this.connection == null ? this.connection = new ServerConnectionListener(this) : this.connection; // Spigot
    }

    public boolean hasGui() {
        return false;
    }

    public abstract boolean publishServer(GameType enumgamemode, boolean flag, int i);

    public int getTickCount() {
        return this.tickCount;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(ServerLevel worldserver, BlockPos blockposition, Player entityhuman) {
        return false;
    }

    public void setForceGameType(boolean flag) {
        this.forceGameType = flag;
    }

    public boolean getForceGameType() {
        return this.forceGameType;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int i) {
        this.playerIdleTimeout = i;
    }

    public MinecraftSessionService getSessionService() {
        return this.sessionService;
    }

    public GameProfileRepository getProfileRepository() {
        return this.profileRepository;
    }

    public GameProfileCache getProfileCache() {
        return this.profileCache;
    }

    public ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public long getNextTickTime() {
        return this.nextTickTime;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable ServerLevel worldserver) {
        return worldserver != null ? worldserver.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    public CompletableFuture<Void> reloadResources(Collection<String> collection) {
        CompletableFuture<Void> completablefuture = CompletableFuture.supplyAsync(() -> {
            Stream<String> stream = collection.stream(); // CraftBukkit - decompile error
            PackRepository resourcepackrepository = this.packRepository;

            this.packRepository.getClass();
            return stream.map(resourcepackrepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()); // CraftBukkit - decompile error
        }, this).thenCompose((immutablelist) -> {
            return ServerResources.loadResources(immutablelist, this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED, this.getFunctionCompilationLevel(), this.executor, this);
        }).thenAcceptAsync((datapackresources) -> {
            this.resources.close();
            this.resources = datapackresources;
            this.packRepository.setSelected(collection);
            this.worldData.setDataPackConfig(getSelectedPacks(this.packRepository));
            datapackresources.updateGlobals();
            this.getPlayerList().saveAll();
            this.getPlayerList().reloadResources();
            this.functionManager.replaceLibrary(this.resources.getFunctionLibrary());
            this.structureManager.onResourceManagerReload(this.resources.getResourceManager());
        }, this);

        if (this.isSameThread()) {
            this.managedBlock(completablefuture::isDone);
        }

        return completablefuture;
    }

    public static DataPackConfig configurePackRepository(PackRepository<Pack> resourcepackrepository, DataPackConfig datapackconfiguration, boolean flag) {
        resourcepackrepository.reload();
        if (flag) {
            resourcepackrepository.setSelected((Collection) Collections.singleton("vanilla"));
            return new DataPackConfig(ImmutableList.of("vanilla"), ImmutableList.of());
        } else {
            Set<String> set = Sets.newLinkedHashSet();
            Iterator iterator = datapackconfiguration.getEnabled().iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                if (resourcepackrepository.isAvailable(s)) {
                    set.add(s);
                } else {
                    MinecraftServer.LOGGER.warn("Missing data pack {}", s);
                }
            }

            iterator = resourcepackrepository.getAvailablePacks().iterator();

            while (iterator.hasNext()) {
                Pack resourcepackloader = (Pack) iterator.next();
                String s1 = resourcepackloader.getId();

                if (!datapackconfiguration.getDisabled().contains(s1) && !set.contains(s1)) {
                    MinecraftServer.LOGGER.info("Found new data pack {}, loading it automatically", s1);
                    set.add(s1);
                }
            }

            if (set.isEmpty()) {
                MinecraftServer.LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            resourcepackrepository.setSelected((Collection) set);
            return getSelectedPacks(resourcepackrepository);
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository<?> resourcepackrepository) {
        Collection<String> collection = resourcepackrepository.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list1 = (List) resourcepackrepository.getAvailableIds().stream().filter((s) -> {
            return !collection.contains(s);
        }).collect(ImmutableList.toImmutableList());

        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers(CommandSourceStack commandlistenerwrapper) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerlist = commandlistenerwrapper.getServer().getPlayerList();
            UserWhiteList whitelist = playerlist.getWhiteList();
            List<ServerPlayer> list = Lists.newArrayList(playerlist.getPlayers());
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (!whitelist.isWhiteListed(entityplayer.getGameProfile())) {
                    entityplayer.connection.disconnect(new TranslatableComponent("multiplayer.disconnect.not_whitelisted"));
                }
            }

        }
    }

    public PackRepository<Pack> getResourcePackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel worldserver = this.overworld();

        return new CommandSourceStack(this, worldserver == null ? Vec3.ZERO : Vec3.atLowerCornerOf((Vec3i) worldserver.getSharedSpawnPos()), Vec2.ZERO, worldserver, 4, "Server", new TextComponent("Server"), this, (Entity) null);
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    public RecipeManager getRecipeManager() {
        return this.resources.getRecipeManager();
    }

    public TagManager getTags() {
        return this.resources.getTags();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public LootTables getLootTables() {
        return this.resources.getLootTables();
    }

    public PredicateManager getPredicateManager() {
        return this.resources.getPredicateManager();
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean flag) {
        this.enforceWhitelist = flag;
    }

    public float getAverageTickTime() {
        return this.averageTickTime;
    }

    public int getProfilePermissions(GameProfile gameprofile) {
        if (this.getPlayerList().isOp(gameprofile)) {
            ServerOpListEntry oplistentry = (ServerOpListEntry) this.getPlayerList().getOPs().get(gameprofile);

            return oplistentry != null ? oplistentry.getLevel() : (this.isSingleplayerOwner(gameprofile) ? 4 : (this.isSingleplayer() ? (this.getPlayerList().isAllowCheatsForAllPlayers() ? 4 : 0) : this.getOperatorUserPermissionLevel()));
        } else {
            return 0;
        }
    }

    public ProfilerFiller getProfiler() {
        return this.profiler;
    }

    public abstract boolean isSingleplayerOwner(GameProfile gameprofile);

    public void saveDebugReport(java.nio.file.Path java_nio_file_path) throws IOException {
        java.nio.file.Path java_nio_file_path1 = java_nio_file_path.resolve("levels");
        Iterator iterator = this.levels.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<ResourceKey<Level>, ServerLevel> entry = (Entry) iterator.next();
            ResourceLocation minecraftkey = ((ResourceKey) entry.getKey()).location();
            java.nio.file.Path java_nio_file_path2 = java_nio_file_path1.resolve(minecraftkey.getNamespace()).resolve(minecraftkey.getPath());

            Files.createDirectories(java_nio_file_path2);
            ((ServerLevel) entry.getValue()).saveDebugReport(java_nio_file_path2);
        }

        this.dumpGameRules(java_nio_file_path.resolve("gamerules.txt"));
        this.dumpClasspath(java_nio_file_path.resolve("classpath.txt"));
        this.dumpCrashCategory(java_nio_file_path.resolve("example_crash.txt"));
        this.dumpMiscStats(java_nio_file_path.resolve("stats.txt"));
        this.dumpThreads(java_nio_file_path.resolve("threads.txt"));
    }

    private void dumpMiscStats(java.nio.file.Path java_nio_file_path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(java_nio_file_path);
        Throwable throwable = null;

        try {
            bufferedwriter.write(String.format("pending_tasks: %d\n", this.getPendingTasksCount()));
            bufferedwriter.write(String.format("average_tick_time: %f\n", this.getAverageTickTime()));
            bufferedwriter.write(String.format("tick_times: %s\n", Arrays.toString(this.tickTimes)));
            bufferedwriter.write(String.format("queue: %s\n", Util.backgroundExecutor()));
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

    }

    private void dumpCrashCategory(java.nio.file.Path java_nio_file_path) throws IOException {
        CrashReport crashreport = new CrashReport("Server dump", new Exception("dummy"));

        this.fillReport(crashreport);
        BufferedWriter bufferedwriter = Files.newBufferedWriter(java_nio_file_path);
        Throwable throwable = null;

        try {
            bufferedwriter.write(crashreport.getFriendlyReport());
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

    }

    private void dumpGameRules(java.nio.file.Path java_nio_file_path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(java_nio_file_path);
        Throwable throwable = null;

        try {
            final List<String> list = Lists.newArrayList();
            final GameRules gamerules = this.getGameRules();

            GameRules.a(new GameRules.GameRuleVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void a(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.Type<T> gamerules_gameruledefinition) {
                    list.add(String.format("%s=%s\n", gamerules_gamerulekey.a(), gamerules.get(gamerules_gamerulekey).toString()));
                }
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                bufferedwriter.write(s);
            }
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

    }

    private void dumpClasspath(java.nio.file.Path java_nio_file_path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(java_nio_file_path);
        Throwable throwable = null;

        try {
            String s = System.getProperty("java.class.path");
            String s1 = System.getProperty("path.separator");
            Iterator iterator = Splitter.on(s1).split(s).iterator();

            while (iterator.hasNext()) {
                String s2 = (String) iterator.next();

                bufferedwriter.write(s2);
                bufferedwriter.write("\n");
            }
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

    }

    private void dumpThreads(java.nio.file.Path java_nio_file_path) throws IOException {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);

        Arrays.sort(athreadinfo, Comparator.comparing(ThreadInfo::getThreadName));
        BufferedWriter bufferedwriter = Files.newBufferedWriter(java_nio_file_path);
        Throwable throwable = null;

        try {
            ThreadInfo[] athreadinfo1 = athreadinfo;
            int i = athreadinfo.length;

            for (int j = 0; j < i; ++j) {
                ThreadInfo threadinfo = athreadinfo1[j];

                bufferedwriter.write(threadinfo.toString());
                bufferedwriter.write(10);
            }
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

    }

    // CraftBukkit start
    @Override
    public boolean isSameThread() {
        return super.isSameThread() || this.isStopped(); // CraftBukkit - MC-142590
    }

    public boolean isDebugging() {
        return false;
    }

    @Deprecated
    public static MinecraftServer getServer() {
        return (Bukkit.getServer() instanceof CraftServer) ? ((CraftServer) Bukkit.getServer()).getServer() : null;
    }
    // CraftBukkit end

    private void startProfilerTick(@Nullable SingleTickProfiler gameprofilertick) {
        if (this.delayProfilerStart) {
            this.delayProfilerStart = false;
            this.continousProfiler.enable();
        }

        this.profiler = SingleTickProfiler.decorateFiller(this.continousProfiler.getFiller(), gameprofilertick);
    }

    private void endProfilerTick(@Nullable SingleTickProfiler gameprofilertick) {
        if (gameprofilertick != null) {
            gameprofilertick.endTick();
        }

        this.profiler = this.continousProfiler.getFiller();
    }

    public boolean isProfiling() {
        return this.continousProfiler.isEnabled();
    }

    public void startProfiling() {
        this.delayProfilerStart = true;
    }

    public ProfileResults finishProfiling() {
        ProfileResults methodprofilerresults = this.continousProfiler.getResults();

        this.continousProfiler.disable();
        return methodprofilerresults;
    }

    public java.nio.file.Path getWorldPath(LevelResource savedfile) {
        return this.storageSource.getLevelPath(savedfile);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureManager getDefinedStructureManager() {
        return this.structureManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }
}
