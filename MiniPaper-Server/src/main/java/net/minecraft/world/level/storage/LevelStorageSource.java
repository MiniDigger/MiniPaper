package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LevelStorageSource {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter FORMATTER = (new DateTimeFormatterBuilder()).appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-').appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-').appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('_').appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral('-').appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral('-').appendValue(ChronoField.SECOND_OF_MINUTE, 2).toFormatter();
    private static final ImmutableList<String> OLD_SETTINGS_KEYS = ImmutableList.of("RandomSeed", "generatorName", "generatorOptions", "generatorVersion", "legacy_custom_options", "MapFeatures", "BonusChest");
    public final java.nio.file.Path baseDir;
    private final java.nio.file.Path backupDir;
    private final DataFixer fixerUpper;

    public LevelStorageSource(java.nio.file.Path java_nio_file_path, java.nio.file.Path java_nio_file_path1, DataFixer datafixer) {
        this.fixerUpper = datafixer;

        try {
            Files.createDirectories(Files.exists(java_nio_file_path, new LinkOption[0]) ? java_nio_file_path.toRealPath() : java_nio_file_path);
        } catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        }

        this.baseDir = java_nio_file_path;
        this.backupDir = java_nio_file_path1;
    }

    public static LevelStorageSource createDefault(java.nio.file.Path java_nio_file_path) {
        return new LevelStorageSource(java_nio_file_path, java_nio_file_path.resolve("../backups"), DataFixers.getDataFixerOH());
    }

    private static Pair<WorldGenSettings, Lifecycle> readWorldGenSettings(Dynamic<?> dynamic, DataFixer datafixer, int i) {
        Dynamic<?> dynamic1 = dynamic.get("WorldGenSettings").orElseEmptyMap();
        UnmodifiableIterator unmodifiableiterator = LevelStorageSource.OLD_SETTINGS_KEYS.iterator();

        while (unmodifiableiterator.hasNext()) {
            String s = (String) unmodifiableiterator.next();
            Optional<? extends Dynamic<?>> optional = dynamic.get(s).result();

            if (optional.isPresent()) {
                dynamic1 = dynamic1.set(s, (Dynamic) optional.get());
            }
        }

        Dynamic<?> dynamic2 = datafixer.update(References.WORLD_GEN_SETTINGS, dynamic1, i, SharedConstants.getCurrentVersion().getWorldVersion());
        DataResult<WorldGenSettings> dataresult = WorldGenSettings.CODEC.parse(dynamic2);
        Logger logger = LevelStorageSource.LOGGER;

        logger.getClass();
        return Pair.of(dataresult.resultOrPartial(Util.prefix("WorldGenSettings: ", logger::error)).orElseGet(WorldGenSettings::makeDefault), dataresult.lifecycle());
    }

    private static DataPackConfig readDataPackConfig(Dynamic<?> dynamic) {
        DataResult dataresult = DataPackConfig.CODEC.parse(dynamic);
        Logger logger = LevelStorageSource.LOGGER;

        logger.getClass();
        return (DataPackConfig) dataresult.resultOrPartial(logger::error).orElse(DataPackConfig.DEFAULT);
    }

    private int getStorageVersion() {
        return 19133;
    }

    @Nullable
    private <T> T readLevelData(File file, BiFunction<File, DataFixer, T> bifunction) {
        if (!file.exists()) {
            return null;
        } else {
            File file1 = new File(file, "level.dat");

            if (file1.exists()) {
                T t0 = bifunction.apply(file1, this.fixerUpper);

                if (t0 != null) {
                    return t0;
                }
            }

            file1 = new File(file, "level.dat_old");
            return file1.exists() ? bifunction.apply(file1, this.fixerUpper) : null;
        }
    }

    @Nullable
    private static DataPackConfig getDataPacks(File file, DataFixer datafixer) {
        try {
            CompoundTag nbttagcompound = NbtIo.readCompressed((InputStream) (new FileInputStream(file)));
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");

            nbttagcompound1.remove("Player");
            int i = nbttagcompound1.contains("DataVersion", 99) ? nbttagcompound1.getInt("DataVersion") : -1;
            Dynamic<Tag> dynamic = datafixer.update(DataFixTypes.LEVEL.getType(), new Dynamic(NbtOps.INSTANCE, nbttagcompound1), i, SharedConstants.getCurrentVersion().getWorldVersion());

            return (DataPackConfig) dynamic.get("DataPacks").result().map(LevelStorageSource::readDataPackConfig).orElse(DataPackConfig.DEFAULT);
        } catch (Exception exception) {
            LevelStorageSource.LOGGER.error("Exception reading {}", file, exception);
            return null;
        }
    }

    private static BiFunction<File, DataFixer, PrimaryLevelData> getLevelData(DynamicOps<Tag> dynamicops, DataPackConfig datapackconfiguration) {
        return (file, datafixer) -> {
            try {
                CompoundTag nbttagcompound = NbtIo.readCompressed((InputStream) (new FileInputStream(file)));
                CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");
                CompoundTag nbttagcompound2 = nbttagcompound1.contains("Player", 10) ? nbttagcompound1.getCompound("Player") : null;

                nbttagcompound1.remove("Player");
                int i = nbttagcompound1.contains("DataVersion", 99) ? nbttagcompound1.getInt("DataVersion") : -1;
                Dynamic<Tag> dynamic = datafixer.update(DataFixTypes.LEVEL.getType(), new Dynamic(dynamicops, nbttagcompound1), i, SharedConstants.getCurrentVersion().getWorldVersion());
                Pair<WorldGenSettings, Lifecycle> pair = readWorldGenSettings(dynamic, datafixer, i);
                LevelVersion levelversion = LevelVersion.parse(dynamic);
                LevelSettings worldsettings = LevelSettings.parse(dynamic, datapackconfiguration);

                return PrimaryLevelData.parse(dynamic, datafixer, i, nbttagcompound2, worldsettings, levelversion, (WorldGenSettings) pair.getFirst(), (Lifecycle) pair.getSecond());
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Exception reading {}", file, exception);
                return null;
            }
        };
    }

    private BiFunction<File, DataFixer, LevelSummary> levelSummaryReader(File file, boolean flag) {
        return (file1, datafixer) -> {
            try {
                CompoundTag nbttagcompound = NbtIo.readCompressed((InputStream) (new FileInputStream(file1)));
                CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");

                nbttagcompound1.remove("Player");
                int i = nbttagcompound1.contains("DataVersion", 99) ? nbttagcompound1.getInt("DataVersion") : -1;
                Dynamic<Tag> dynamic = datafixer.update(DataFixTypes.LEVEL.getType(), new Dynamic(NbtOps.INSTANCE, nbttagcompound1), i, SharedConstants.getCurrentVersion().getWorldVersion());
                LevelVersion levelversion = LevelVersion.parse(dynamic);
                int j = levelversion.levelDataVersion();

                if (j != 19132 && j != 19133) {
                    return null;
                } else {
                    boolean flag1 = j != this.getStorageVersion();
                    File file2 = new File(file, "icon.png");
                    DataPackConfig datapackconfiguration = (DataPackConfig) dynamic.get("DataPacks").result().map(LevelStorageSource::readDataPackConfig).orElse(DataPackConfig.DEFAULT);
                    LevelSettings worldsettings = LevelSettings.parse(dynamic, datapackconfiguration);

                    return new LevelSummary(worldsettings, levelversion, file.getName(), flag1, flag, file2);
                }
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Exception reading {}", file1, exception);
                return null;
            }
        };
    }

    // CraftBukkit start
    public LevelStorageSource.LevelStorageAccess c(String s, ResourceKey<LevelStem> dimensionType) throws IOException {
        return new LevelStorageSource.LevelStorageAccess(s, dimensionType);
        // CraftBukkit end
    }

    public class LevelStorageAccess implements AutoCloseable {

        private final DirectoryLock lock;
        public final java.nio.file.Path levelPath;
        private final String levelId;
        private final Map<LevelResource, java.nio.file.Path> resources = Maps.newHashMap();
        // CraftBukkit start
        private final ResourceKey<LevelStem> dimensionType;

        public LevelStorageAccess(String s, ResourceKey<LevelStem> dimensionType) throws IOException {
            this.dimensionType = dimensionType;
            // CraftBukkit end
            this.levelId = s;
            this.levelPath = LevelStorageSource.this.baseDir.resolve(s);
            this.lock = DirectoryLock.create(this.levelPath);
        }

        public String getLevelId() {
            return this.levelId;
        }

        public java.nio.file.Path getLevelPath(LevelResource savedfile) {
            return (java.nio.file.Path) this.resources.computeIfAbsent(savedfile, (savedfile1) -> {
                return this.levelPath.resolve(savedfile1.getId());
            });
        }

        public File getDimensionPath(ResourceKey<Level> resourcekey) {
            // CraftBukkit start
            return this.getFolder(this.levelPath.toFile());
        }

        private File getFolder(File file) {
            if (dimensionType == LevelStem.OVERWORLD) {
                return file;
            } else if (dimensionType == LevelStem.NETHER) {
                return new File(file, "DIM-1");
            } else if (dimensionType == LevelStem.END) {
                return new File(file, "DIM1");
            } else {
                throw new IllegalArgumentException("Unknwon dimension " + this.dimensionType);
            }
        }
        // CraftBukkit end

        private void checkLock() {
            if (!this.lock.isValid()) {
                throw new IllegalStateException("Lock is no longer valid");
            }
        }

        public PlayerDataStorage createPlayerStorage() {
            this.checkLock();
            return new PlayerDataStorage(this, LevelStorageSource.this.fixerUpper);
        }

        public boolean requiresConversion() {
            LevelSummary worldinfo = this.getSummary();

            return worldinfo != null && worldinfo.levelVersion().levelDataVersion() != LevelStorageSource.this.getStorageVersion();
        }

        public boolean convert(ProgressListener iprogressupdate) {
            this.checkLock();
            return McRegionUpgrader.convertLevel(this, iprogressupdate);
        }

        @Nullable
        public LevelSummary getSummary() {
            this.checkLock();
            return (LevelSummary) LevelStorageSource.this.readLevelData(this.levelPath.toFile(), LevelStorageSource.this.levelSummaryReader(this.levelPath.toFile(), false));
        }

        @Nullable
        public WorldData getDataTag(DynamicOps<Tag> dynamicops, DataPackConfig datapackconfiguration) {
            this.checkLock();
            return (WorldData) LevelStorageSource.this.readLevelData(this.levelPath.toFile(), LevelStorageSource.getLevelData(dynamicops, datapackconfiguration));
        }

        @Nullable
        public DataPackConfig getDataPacks() {
            this.checkLock();
            return (DataPackConfig) LevelStorageSource.this.readLevelData(this.levelPath.toFile(), (file, datafixer) -> {
                return LevelStorageSource.getDataPacks(file, datafixer);
            });
        }

        public void saveDataTag(RegistryAccess iregistrycustom, WorldData savedata) {
            this.saveDataTag(iregistrycustom, savedata, (CompoundTag) null);
        }

        public void saveDataTag(RegistryAccess iregistrycustom, WorldData savedata, @Nullable CompoundTag nbttagcompound) {
            File file = this.levelPath.toFile();
            CompoundTag nbttagcompound1 = savedata.createTag(iregistrycustom, nbttagcompound);
            CompoundTag nbttagcompound2 = new CompoundTag();

            nbttagcompound2.put("Data", nbttagcompound1);

            try {
                File file1 = File.createTempFile("level", ".dat", file);

                NbtIo.writeCompressed(nbttagcompound2, (OutputStream) (new FileOutputStream(file1)));
                File file2 = new File(file, "level.dat_old");
                File file3 = new File(file, "level.dat");

                Util.safeReplaceFile(file3, file1, file2);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to save level {}", file, exception);
            }

        }

        public File getIconFile() {
            this.checkLock();
            return this.levelPath.resolve("icon.png").toFile();
        }

        public void close() throws IOException {
            this.lock.close();
        }
    }
}
