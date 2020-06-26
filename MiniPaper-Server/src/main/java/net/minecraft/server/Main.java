package net.minecraft.server;

import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger();

    public Main() {}

    public static void main(final OptionSet optionset) { // CraftBukkit - replaces main(String[] astring)
        /* CraftBukkit start - Replace everything
        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("nogui");
        OptionSpec<Void> optionspec1 = optionparser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpec<Void> optionspec2 = optionparser.accepts("demo");
        OptionSpec<Void> optionspec3 = optionparser.accepts("bonusChest");
        OptionSpec<Void> optionspec4 = optionparser.accepts("forceUpgrade");
        OptionSpec<Void> optionspec5 = optionparser.accepts("eraseCache");
        OptionSpec<Void> optionspec6 = optionparser.accepts("safeMode", "Loads level with vanilla datapack only");
        OptionSpec<Void> optionspec7 = optionparser.accepts("help").forHelp();
        OptionSpec<String> optionspec8 = optionparser.accepts("singleplayer").withRequiredArg();
        OptionSpec<String> optionspec9 = optionparser.accepts("universe").withRequiredArg().defaultsTo(".", new String[0]);
        OptionSpec<String> optionspec10 = optionparser.accepts("world").withRequiredArg();
        OptionSpec<Integer> optionspec11 = optionparser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1, new Integer[0]);
        OptionSpec<String> optionspec12 = optionparser.accepts("serverId").withRequiredArg();
        NonOptionArgumentSpec nonoptionargumentspec = optionparser.nonOptions();

        try {
            OptionSet optionset = optionparser.parse(astring);

            if (optionset.has(optionspec7)) {
                optionparser.printHelpOn(System.err);
                return;
            }
            */ // CraftBukkit end

        try {
            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            java.nio.file.Path java_nio_file_path = Paths.get("server.properties");
            DedicatedServerSettings dedicatedserversettings = new DedicatedServerSettings(optionset); // CraftBukkit - CLI argument support

            dedicatedserversettings.forceSave();
            java.nio.file.Path java_nio_file_path1 = Paths.get("eula.txt");
            Eula eula = new Eula(java_nio_file_path1);

            if (optionset.has("initSettings")) { // CraftBukkit
                Main.LOGGER.info("Initialized '{}' and '{}'", java_nio_file_path.toAbsolutePath(), java_nio_file_path1.toAbsolutePath());
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
                Main.LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            File file = (File) optionset.valueOf("universe"); // CraftBukkit
            YggdrasilAuthenticationService yggdrasilauthenticationservice = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
            MinecraftSessionService minecraftsessionservice = yggdrasilauthenticationservice.createMinecraftSessionService();
            GameProfileRepository gameprofilerepository = yggdrasilauthenticationservice.createProfileRepository();
            GameProfileCache usercache = new GameProfileCache(gameprofilerepository, new File(file, MinecraftServer.USERID_CACHE_FILE.getName()));
            // CraftBukkit start
            String s = (String) Optional.ofNullable(optionset.valueOf("world")).orElse(dedicatedserversettings.getProperties().levelName);
            LevelStorageSource convertable = LevelStorageSource.createDefault(file.toPath());
            LevelStorageSource.LevelStorageAccess convertable_conversionsession = convertable.c(s, LevelStem.OVERWORLD);

            MinecraftServer.convertFromRegionFormatIfNeeded(convertable_conversionsession);
            DataPackConfig datapackconfiguration = convertable_conversionsession.getDataPacks();
            boolean flag = optionset.has("safeMode");

            if (flag) {
                Main.LOGGER.warn("Safe mode active, only vanilla datapack will be loaded");
            }

            PackRepository<Pack> resourcepackrepository = new PackRepository<>(Pack::new, new RepositorySource[]{new ServerPacksSource(), new FolderRepositorySource(convertable_conversionsession.getLevelPath(LevelResource.DATAPACK_DIR).toFile(), PackSource.WORLD)});
            DataPackConfig datapackconfiguration1 = MinecraftServer.configurePackRepository(resourcepackrepository, datapackconfiguration == null ? DataPackConfig.DEFAULT : datapackconfiguration, flag);
            CompletableFuture completablefuture = ServerResources.loadResources(resourcepackrepository.openAllSelected(), Commands.CommandSelection.DEDICATED, dedicatedserversettings.getProperties().functionPermissionLevel, Util.backgroundExecutor(), Runnable::run);

            ServerResources datapackresources;

            try {
                datapackresources = (ServerResources) completablefuture.get();
            } catch (Exception exception) {
                Main.LOGGER.warn("Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", exception);
                resourcepackrepository.close();
                return;
            }

            datapackresources.updateGlobals();
            RegistryAccess.Dimension iregistrycustom_dimension = RegistryAccess.b();
            /*
            RegistryReadOps<NBTBase> registryreadops = RegistryReadOps.a((DynamicOps) DynamicOpsNBT.a, datapackresources.h(), (IRegistryCustom) iregistrycustom_dimension);
            Object object = convertable_conversionsession.a((DynamicOps) registryreadops, datapackconfiguration1);

            if (object == null) {
                WorldSettings worldsettings;
                GeneratorSettings generatorsettings;

                if (optionset.has(optionspec2)) {
                    worldsettings = MinecraftServer.c;
                    generatorsettings = GeneratorSettings.b;
                } else {
                    DedicatedServerProperties dedicatedserverproperties = dedicatedserversettings.getProperties();

                    worldsettings = new WorldSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty, false, new GameRules(), datapackconfiguration1);
                    generatorsettings = optionset.has(optionspec3) ? dedicatedserverproperties.generatorSettings.k() : dedicatedserverproperties.generatorSettings;
                }

                object = new WorldDataServer(worldsettings, generatorsettings, Lifecycle.stable());
            }

            if (optionset.has(optionspec4)) {
                convertWorld(convertable_conversionsession, DataConverterRegistry.a(), optionset.has(optionspec5), () -> {
                    return true;
                }, ((SaveData) object).getGeneratorSettings().g());
            }

            convertable_conversionsession.a((IRegistryCustom) iregistrycustom_dimension, (SaveData) object);
            */
            final DedicatedServer dedicatedserver = (DedicatedServer) MinecraftServer.spin((thread) -> {
                DedicatedServer dedicatedserver1 = new DedicatedServer(optionset, datapackconfiguration1, thread, iregistrycustom_dimension, convertable_conversionsession, resourcepackrepository, datapackresources, null, dedicatedserversettings, DataFixers.getDataFixerOH(), minecraftsessionservice, gameprofilerepository, usercache, LoggerChunkProgressListener::new);

                /*
                dedicatedserver1.d((String) optionset.valueOf(optionspec8));
                dedicatedserver1.setPort((Integer) optionset.valueOf(optionspec11));
                dedicatedserver1.c(optionset.has(optionspec2));
                dedicatedserver1.b((String) optionset.valueOf(optionspec12));
                */
                boolean flag1 = !optionset.has("nogui") && !optionset.nonOptionArguments().contains("nogui");

                if (flag1 && !GraphicsEnvironment.isHeadless()) {
                    dedicatedserver1.showGui();
                }

                if (optionset.has("port")) {
                    int port = (Integer) optionset.valueOf("port");
                    if (port > 0) {
                        dedicatedserver1.setPort(port);
                    }
                }

                return dedicatedserver1;
            });
            /* CraftBukkit start
            Thread thread = new Thread("Server Shutdown Thread") {
                public void run() {
                    dedicatedserver.safeShutdown(true);
                }
            };

            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(Main.LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
            */ // CraftBukkit end
        } catch (Exception exception1) {
            Main.LOGGER.fatal("Failed to start the minecraft server", exception1);
        }

    }

    public static void forceUpgrade(LevelStorageSource.LevelStorageAccess convertable_conversionsession, DataFixer datafixer, boolean flag, BooleanSupplier booleansupplier, ImmutableSet<ResourceKey<Level>> immutableset) {
        Main.LOGGER.info("Forcing world upgrade! {}", convertable_conversionsession.getLevelId()); // CraftBukkit
        WorldUpgrader worldupgrader = new WorldUpgrader(convertable_conversionsession, datafixer, immutableset, flag);
        Component ichatbasecomponent = null;

        while (!worldupgrader.isFinished()) {
            Component ichatbasecomponent1 = worldupgrader.getStatus();

            if (ichatbasecomponent != ichatbasecomponent1) {
                ichatbasecomponent = ichatbasecomponent1;
                Main.LOGGER.info(worldupgrader.getStatus().getString());
            }

            int i = worldupgrader.getTotalChunks();

            if (i > 0) {
                int j = worldupgrader.getConverted() + worldupgrader.getSkipped();

                Main.LOGGER.info("{}% completed ({} / {} chunks)...", Mth.floor((float) j / (float) i * 100.0F), j, i);
            }

            if (!booleansupplier.getAsBoolean()) {
                worldupgrader.cancel();
            } else {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interruptedexception) {
                    ;
                }
            }
        }

    }
}
