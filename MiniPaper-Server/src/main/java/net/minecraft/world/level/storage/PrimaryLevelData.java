package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SerializableUUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.resources.RegistryWriteOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
// CraftBukkit end

public class PrimaryLevelData implements ServerLevelData, WorldData {

    private static final Logger LOGGER = LogManager.getLogger();
    public LevelSettings settings;
    private final WorldGenSettings worldGenSettings;
    private final Lifecycle worldGenSettingsLifecycle;
    private int xSpawn;
    private int ySpawn;
    private int zSpawn;
    private long gameTime;
    private long dayTime;
    @Nullable
    private final DataFixer fixerUpper;
    private final int playerDataVersion;
    private boolean upgradedPlayerTag;
    @Nullable
    private CompoundTag loadedPlayerTag;
    private final int version;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private boolean initialized;
    private boolean difficultyLocked;
    private WorldBorder.Settings worldBorder;
    private CompoundTag endDragonFightData;
    @Nullable
    private CompoundTag customBossEvents;
    private int wanderingTraderSpawnDelay;
    private int wanderingTraderSpawnChance;
    @Nullable
    private UUID wanderingTraderId;
    private final Set<String> knownServerBrands;
    private boolean wasModded;
    private final TimerQueue<MinecraftServer> scheduledEvents;
    public ServerLevel world; // CraftBukkit

    private PrimaryLevelData(@Nullable DataFixer datafixer, int i, @Nullable CompoundTag nbttagcompound, boolean flag, int j, int k, int l, long i1, long j1, int k1, int l1, int i2, boolean flag1, int j2, boolean flag2, boolean flag3, boolean flag4, WorldBorder.Settings worldborder_c, int k2, int l2, @Nullable UUID uuid, LinkedHashSet<String> linkedhashset, TimerQueue<MinecraftServer> customfunctioncallbacktimerqueue, @Nullable CompoundTag nbttagcompound1, CompoundTag nbttagcompound2, LevelSettings worldsettings, WorldGenSettings generatorsettings, Lifecycle lifecycle) {
        this.fixerUpper = datafixer;
        this.wasModded = flag;
        this.xSpawn = j;
        this.ySpawn = k;
        this.zSpawn = l;
        this.gameTime = i1;
        this.dayTime = j1;
        this.version = k1;
        this.clearWeatherTime = l1;
        this.rainTime = i2;
        this.raining = flag1;
        this.thunderTime = j2;
        this.thundering = flag2;
        this.initialized = flag3;
        this.difficultyLocked = flag4;
        this.worldBorder = worldborder_c;
        this.wanderingTraderSpawnDelay = k2;
        this.wanderingTraderSpawnChance = l2;
        this.wanderingTraderId = uuid;
        this.knownServerBrands = linkedhashset;
        this.loadedPlayerTag = nbttagcompound;
        this.playerDataVersion = i;
        this.scheduledEvents = customfunctioncallbacktimerqueue;
        this.customBossEvents = nbttagcompound1;
        this.endDragonFightData = nbttagcompound2;
        this.settings = worldsettings;
        this.worldGenSettings = generatorsettings;
        this.worldGenSettingsLifecycle = lifecycle;
    }

    public PrimaryLevelData(LevelSettings worldsettings, WorldGenSettings generatorsettings, Lifecycle lifecycle) {
        this((DataFixer) null, SharedConstants.getCurrentVersion().getWorldVersion(), (CompoundTag) null, false, 0, 0, 0, 0L, 0L, 19133, 0, 0, false, 0, false, false, false, WorldBorder.DEFAULT_SETTINGS, 0, 0, (UUID) null, Sets.newLinkedHashSet(), new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS), (CompoundTag) null, new CompoundTag(), worldsettings.copy(), generatorsettings, lifecycle);
    }

    public static PrimaryLevelData parse(Dynamic<Tag> dynamic, DataFixer datafixer, int i, @Nullable CompoundTag nbttagcompound, LevelSettings worldsettings, LevelVersion levelversion, WorldGenSettings generatorsettings, Lifecycle lifecycle) {
        long j = dynamic.get("Time").asLong(0L);
        CompoundTag nbttagcompound1 = (CompoundTag) dynamic.get("DragonFight").result().map(Dynamic::getValue).orElseGet(() -> {
            return (Tag) dynamic.get("DimensionData").get("1").get("DragonFight").orElseEmptyMap().getValue();
        });

        // CraftBukkit - decompile error
        return new PrimaryLevelData(datafixer, i, nbttagcompound, dynamic.get("WasModded").asBoolean(false), dynamic.get("SpawnX").asInt(0), dynamic.get("SpawnY").asInt(0), dynamic.get("SpawnZ").asInt(0), j, dynamic.get("DayTime").asLong(j), levelversion.levelDataVersion(), dynamic.get("clearWeatherTime").asInt(0), dynamic.get("rainTime").asInt(0), dynamic.get("raining").asBoolean(false), dynamic.get("thunderTime").asInt(0), dynamic.get("thundering").asBoolean(false), dynamic.get("initialized").asBoolean(true), dynamic.get("DifficultyLocked").asBoolean(false), WorldBorder.Settings.read(dynamic, WorldBorder.DEFAULT_SETTINGS), dynamic.get("WanderingTraderSpawnDelay").asInt(0), dynamic.get("WanderingTraderSpawnChance").asInt(0), (UUID) dynamic.get("WanderingTraderId").read(SerializableUUID.CODEC).result().orElse(null), (LinkedHashSet) dynamic.get("ServerBrands").asStream().flatMap((dynamic1) -> {
            return Util.toStream(dynamic1.asString().result());
        }).collect(Collectors.toCollection(Sets::newLinkedHashSet)), new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS, dynamic.get("ScheduledEvents").asStream()), (CompoundTag) dynamic.get("CustomBossEvents").orElseEmptyMap().getValue(), nbttagcompound1, worldsettings, generatorsettings, lifecycle);
    }

    @Override
    public CompoundTag createTag(RegistryAccess iregistrycustom, @Nullable CompoundTag nbttagcompound) {
        this.updatePlayerTag();
        if (nbttagcompound == null) {
            nbttagcompound = this.loadedPlayerTag;
        }

        CompoundTag nbttagcompound1 = new CompoundTag();

        this.setTagData(iregistrycustom, nbttagcompound1, nbttagcompound);
        return nbttagcompound1;
    }

    private void setTagData(RegistryAccess iregistrycustom, CompoundTag nbttagcompound, @Nullable CompoundTag nbttagcompound1) {
        ListTag nbttaglist = new ListTag();

        this.knownServerBrands.stream().map(StringTag::valueOf).forEach(nbttaglist::add);
        nbttagcompound.put("ServerBrands", nbttaglist);
        nbttagcompound.putBoolean("WasModded", this.wasModded);
        CompoundTag nbttagcompound2 = new CompoundTag();

        nbttagcompound2.putString("Name", SharedConstants.getCurrentVersion().getName());
        nbttagcompound2.putInt("Id", SharedConstants.getCurrentVersion().getWorldVersion());
        nbttagcompound2.putBoolean("Snapshot", !SharedConstants.getCurrentVersion().isStable());
        nbttagcompound.put("Version", nbttagcompound2);
        nbttagcompound.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        RegistryWriteOps<Tag> registrywriteops = RegistryWriteOps.create(NbtOps.INSTANCE, iregistrycustom);
        DataResult<Tag> dataresult = WorldGenSettings.CODEC.encodeStart(registrywriteops, this.worldGenSettings); // CraftBukkit - decompile error
        Logger logger = PrimaryLevelData.LOGGER;

        logger.getClass();
        dataresult.resultOrPartial(Util.prefix("WorldGenSettings: ", logger::error)).ifPresent((nbtbase) -> {
            nbttagcompound.put("WorldGenSettings", nbtbase);
        });
        nbttagcompound.putInt("GameType", this.settings.gameType().getId());
        nbttagcompound.putInt("SpawnX", this.xSpawn);
        nbttagcompound.putInt("SpawnY", this.ySpawn);
        nbttagcompound.putInt("SpawnZ", this.zSpawn);
        nbttagcompound.putLong("Time", this.gameTime);
        nbttagcompound.putLong("DayTime", this.dayTime);
        nbttagcompound.putLong("LastPlayed", Util.getEpochMillis());
        nbttagcompound.putString("LevelName", this.settings.levelName());
        nbttagcompound.putInt("version", 19133);
        nbttagcompound.putInt("clearWeatherTime", this.clearWeatherTime);
        nbttagcompound.putInt("rainTime", this.rainTime);
        nbttagcompound.putBoolean("raining", this.raining);
        nbttagcompound.putInt("thunderTime", this.thunderTime);
        nbttagcompound.putBoolean("thundering", this.thundering);
        nbttagcompound.putBoolean("hardcore", this.settings.hardcore());
        nbttagcompound.putBoolean("allowCommands", this.settings.allowCommands());
        nbttagcompound.putBoolean("initialized", this.initialized);
        this.worldBorder.write(nbttagcompound);
        nbttagcompound.putByte("Difficulty", (byte) this.settings.difficulty().getId());
        nbttagcompound.putBoolean("DifficultyLocked", this.difficultyLocked);
        nbttagcompound.put("GameRules", this.settings.gameRules().createTag());
        nbttagcompound.put("DragonFight", this.endDragonFightData);
        if (nbttagcompound1 != null) {
            nbttagcompound.put("Player", nbttagcompound1);
        }

        DataPackConfig.CODEC.encodeStart(NbtOps.INSTANCE, this.settings.getDataPackConfig()).result().ifPresent((nbtbase) -> {
            nbttagcompound.put("DataPacks", nbtbase);
        });
        if (this.customBossEvents != null) {
            nbttagcompound.put("CustomBossEvents", this.customBossEvents);
        }

        nbttagcompound.put("ScheduledEvents", this.scheduledEvents.store());
        nbttagcompound.putInt("WanderingTraderSpawnDelay", this.wanderingTraderSpawnDelay);
        nbttagcompound.putInt("WanderingTraderSpawnChance", this.wanderingTraderSpawnChance);
        if (this.wanderingTraderId != null) {
            nbttagcompound.putUUID("WanderingTraderId", this.wanderingTraderId);
        }

        nbttagcompound.putString("Bukkit.Version", Bukkit.getName() + "/" + Bukkit.getVersion() + "/" + Bukkit.getBukkitVersion()); // CraftBukkit
    }

    @Override
    public int getXSpawn() {
        return this.xSpawn;
    }

    @Override
    public int getYSpawn() {
        return this.ySpawn;
    }

    @Override
    public int getZSpawn() {
        return this.zSpawn;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    private void updatePlayerTag() {
        if (!this.upgradedPlayerTag && this.loadedPlayerTag != null) {
            if (this.playerDataVersion < SharedConstants.getCurrentVersion().getWorldVersion()) {
                if (this.fixerUpper == null) {
                    throw (NullPointerException) Util.pauseInIde(new NullPointerException("Fixer Upper not set inside LevelData, and the player tag is not upgraded."));
                }

                this.loadedPlayerTag = NbtUtils.update(this.fixerUpper, DataFixTypes.PLAYER, this.loadedPlayerTag, this.playerDataVersion);
            }

            this.upgradedPlayerTag = true;
        }
    }

    @Override
    public CompoundTag getLoadedPlayerTag() {
        this.updatePlayerTag();
        return this.loadedPlayerTag;
    }

    @Override
    public void setXSpawn(int i) {
        this.xSpawn = i;
    }

    @Override
    public void setYSpawn(int i) {
        this.ySpawn = i;
    }

    @Override
    public void setZSpawn(int i) {
        this.zSpawn = i;
    }

    @Override
    public void setGameTime(long i) {
        this.gameTime = i;
    }

    @Override
    public void setDayTime(long i) {
        this.dayTime = i;
    }

    @Override
    public void setSpawn(BlockPos blockposition) {
        this.xSpawn = blockposition.getX();
        this.ySpawn = blockposition.getY();
        this.zSpawn = blockposition.getZ();
    }

    @Override
    public String getLevelName() {
        return this.settings.levelName();
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int i) {
        this.clearWeatherTime = i;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean flag) {
        // CraftBukkit start
        if (this.thundering == flag) {
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(getLevelName());
        if (world != null) {
            ThunderChangeEvent thunder = new ThunderChangeEvent(world, flag);
            Bukkit.getServer().getPluginManager().callEvent(thunder);
            if (thunder.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.thundering = flag;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int i) {
        this.thunderTime = i;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean flag) {
        // CraftBukkit start
        if (this.raining == flag) {
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(getLevelName());
        if (world != null) {
            WeatherChangeEvent weather = new WeatherChangeEvent(world, flag);
            Bukkit.getServer().getPluginManager().callEvent(weather);
            if (weather.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.raining = flag;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int i) {
        this.rainTime = i;
    }

    @Override
    public GameType getGameType() {
        return this.settings.gameType();
    }

    @Override
    public void setGameType(GameType enumgamemode) {
        this.settings = this.settings.withGameType(enumgamemode);
    }

    @Override
    public boolean isHardcore() {
        return this.settings.hardcore();
    }

    @Override
    public boolean getAllowCommands() {
        return this.settings.allowCommands();
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean flag) {
        this.initialized = flag;
    }

    @Override
    public GameRules getGameRules() {
        return this.settings.gameRules();
    }

    @Override
    public WorldBorder.Settings getWorldBorder() {
        return this.worldBorder;
    }

    @Override
    public void setWorldBorder(WorldBorder.Settings worldborder_c) {
        this.worldBorder = worldborder_c;
    }

    @Override
    public Difficulty getDifficulty() {
        return this.settings.difficulty();
    }

    @Override
    public void setDifficulty(Difficulty enumdifficulty) {
        this.settings = this.settings.withDifficulty(enumdifficulty);
        // CraftBukkit start
        ClientboundChangeDifficultyPacket packet = new ClientboundChangeDifficultyPacket(this.getDifficulty(), this.isDifficultyLocked());
        for (ServerPlayer player : (java.util.List<ServerPlayer>) (java.util.List) world.players()) {
            player.connection.sendPacket(packet);
        }
        // CraftBukkit end
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    @Override
    public void setDifficultyLocked(boolean flag) {
        this.difficultyLocked = flag;
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory crashreportsystemdetails) {
        ServerLevelData.super.fillCrashReportCategory(crashreportsystemdetails);
        WorldData.super.fillCrashReportCategory(crashreportsystemdetails);
    }

    @Override
    public WorldGenSettings worldGenSettings() {
        return this.worldGenSettings;
    }

    @Override
    public CompoundTag endDragonFightData() {
        return this.endDragonFightData;
    }

    @Override
    public void setEndDragonFightData(CompoundTag nbttagcompound) {
        this.endDragonFightData = nbttagcompound;
    }

    @Override
    public DataPackConfig getDataPackConfig() {
        return this.settings.getDataPackConfig();
    }

    @Override
    public void setDataPackConfig(DataPackConfig datapackconfiguration) {
        this.settings = this.settings.withDataPackConfig(datapackconfiguration);
    }

    @Nullable
    @Override
    public CompoundTag getCustomBossEvents() {
        return this.customBossEvents;
    }

    @Override
    public void setCustomBossEvents(@Nullable CompoundTag nbttagcompound) {
        this.customBossEvents = nbttagcompound;
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderSpawnDelay;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int i) {
        this.wanderingTraderSpawnDelay = i;
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderSpawnChance;
    }

    @Override
    public void setWanderingTraderSpawnChance(int i) {
        this.wanderingTraderSpawnChance = i;
    }

    @Override
    public void setWanderingTraderId(UUID uuid) {
        this.wanderingTraderId = uuid;
    }

    @Override
    public void setModdedInfo(String s, boolean flag) {
        this.knownServerBrands.add(s);
        this.wasModded |= flag;
    }

    @Override
    public boolean wasModded() {
        return this.wasModded;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.copyOf(this.knownServerBrands);
    }

    @Override
    public ServerLevelData overworldData() {
        return this;
    }

    // CraftBukkit start - Check if the name stored in NBT is the correct one
    public void checkName(String name) {
        if (!this.settings.levelName.equals(name)) {
            this.settings.levelName = name;
        }
    }
    // CraftBukkit end
}
