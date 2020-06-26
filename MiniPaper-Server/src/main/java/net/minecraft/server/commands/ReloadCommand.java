package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import java.util.Iterator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.WorldData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReloadCommand {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void reloadPacks(Collection<String> collection, CommandSourceStack commandlistenerwrapper) {
        commandlistenerwrapper.getServer().reloadResources(collection).exceptionally((throwable) -> {
            ReloadCommand.LOGGER.warn("Failed to execute reload", throwable);
            commandlistenerwrapper.sendFailure(new TranslatableComponent("commands.reload.failure"));
            return null;
        });
    }

    private static Collection<String> discoverNewPacks(PackRepository<?> resourcepackrepository, WorldData savedata, Collection<String> collection) {
        resourcepackrepository.reload();
        Collection<String> collection1 = Lists.newArrayList(collection);
        Collection<String> collection2 = savedata.getDataPackConfig().getDisabled();
        Iterator iterator = resourcepackrepository.getAvailableIds().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            if (!collection2.contains(s) && !collection1.contains(s)) {
                collection1.add(s);
            }
        }

        return collection1;
    }

    // CraftBukkit start
    public static void reload(MinecraftServer minecraftserver) {
        PackRepository<?> resourcepackrepository = minecraftserver.getResourcePackRepository();
        WorldData savedata = minecraftserver.getWorldData();
        Collection<String> collection = resourcepackrepository.getSelectedIds();
        Collection<String> collection1 = discoverNewPacks(resourcepackrepository, savedata, collection);
        minecraftserver.reloadResources(collection1);
    }
    // CraftBukkit end

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher) {
        com_mojang_brigadier_commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("reload").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).executes((commandcontext) -> {
            CommandSourceStack commandlistenerwrapper = (CommandSourceStack) commandcontext.getSource();
            MinecraftServer minecraftserver = commandlistenerwrapper.getServer();
            PackRepository<?> resourcepackrepository = minecraftserver.getResourcePackRepository();
            WorldData savedata = minecraftserver.getWorldData();
            Collection<String> collection = resourcepackrepository.getSelectedIds();
            Collection<String> collection1 = discoverNewPacks(resourcepackrepository, savedata, collection);

            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.reload.success"), true);
            reloadPacks(collection1, commandlistenerwrapper);
            return 0;
        }));
    }
}
