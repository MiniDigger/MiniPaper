package net.minecraft.server.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Iterator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.event.world.TimeSkipEvent;
// CrafBukkit end

public class TimeCommand {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher) {
        com_mojang_brigadier_commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("time").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("set").then(Commands.literal("day").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 1000);
        }))).then(Commands.literal("noon").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 6000);
        }))).then(Commands.literal("night").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 13000);
        }))).then(Commands.literal("midnight").executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), 18000);
        }))).then(Commands.argument("time", (ArgumentType) TimeArgument.time()).executes((commandcontext) -> {
            return setTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(Commands.literal("add").then(Commands.argument("time", (ArgumentType) TimeArgument.time()).executes((commandcontext) -> {
            return addTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("query").then(Commands.literal("daytime").executes((commandcontext) -> {
            return queryTime((CommandSourceStack) commandcontext.getSource(), getDayTime(((CommandSourceStack) commandcontext.getSource()).getLevel()));
        }))).then(Commands.literal("gametime").executes((commandcontext) -> {
            return queryTime((CommandSourceStack) commandcontext.getSource(), (int) (((CommandSourceStack) commandcontext.getSource()).getLevel().getGameTime() % 2147483647L));
        }))).then(Commands.literal("day").executes((commandcontext) -> {
            return queryTime((CommandSourceStack) commandcontext.getSource(), (int) (((CommandSourceStack) commandcontext.getSource()).getLevel().getDayTime() / 24000L % 2147483647L));
        }))));
    }

    private static int getDayTime(ServerLevel worldserver) {
        return (int) (worldserver.getDayTime() % 24000L);
    }

    private static int queryTime(CommandSourceStack commandlistenerwrapper, int i) {
        commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.time.query", new Object[]{i}), false);
        return i;
    }

    public static int setTime(CommandSourceStack commandlistenerwrapper, int i) {
        Iterator iterator = commandlistenerwrapper.getServer().getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            // CraftBukkit start
            TimeSkipEvent event = new TimeSkipEvent(worldserver.getWorld(), TimeSkipEvent.SkipReason.COMMAND, i - worldserver.getDayTime());
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                worldserver.setDayTime((long) worldserver.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
        }

        commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.time.set", new Object[]{i}), true);
        return getDayTime(commandlistenerwrapper.getLevel());
    }

    public static int addTime(CommandSourceStack commandlistenerwrapper, int i) {
        Iterator iterator = commandlistenerwrapper.getServer().getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            // CraftBukkit start
            TimeSkipEvent event = new TimeSkipEvent(worldserver.getWorld(), TimeSkipEvent.SkipReason.COMMAND, i);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                worldserver.setDayTime(worldserver.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
        }

        int j = getDayTime(commandlistenerwrapper.getLevel());

        commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.time.set", new Object[]{j}), true);
        return j;
    }
}
