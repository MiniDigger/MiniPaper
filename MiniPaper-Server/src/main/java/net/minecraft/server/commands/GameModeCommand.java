package net.minecraft.server.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

public class GameModeCommand {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = (LiteralArgumentBuilder) Commands.literal("gamemode").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        });
        GameType[] aenumgamemode = GameType.values();
        int i = aenumgamemode.length;

        for (int j = 0; j < i; ++j) {
            GameType enumgamemode = aenumgamemode[j];

            if (enumgamemode != GameType.NOT_SET) {
                literalargumentbuilder.then(((LiteralArgumentBuilder) Commands.literal(enumgamemode.getName()).executes((commandcontext) -> {
                    return setMode(commandcontext, (Collection) Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()), enumgamemode);
                })).then(Commands.argument("target", (ArgumentType) EntityArgument.players()).executes((commandcontext) -> {
                    return setMode(commandcontext, EntityArgument.getPlayers(commandcontext, "target"), enumgamemode);
                })));
            }
        }

        com_mojang_brigadier_commanddispatcher.register(literalargumentbuilder);
    }

    private static void logGamemodeChange(CommandSourceStack commandlistenerwrapper, ServerPlayer entityplayer, GameType enumgamemode) {
        TranslatableComponent chatmessage = new TranslatableComponent("gameMode." + enumgamemode.getName());

        if (commandlistenerwrapper.getEntity() == entityplayer) {
            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.gamemode.success.self", new Object[]{chatmessage}), true);
        } else {
            if (commandlistenerwrapper.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
                entityplayer.sendMessage(new TranslatableComponent("gameMode.changed", new Object[]{chatmessage}), Util.NIL_UUID);
            }

            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.gamemode.success.other", new Object[]{entityplayer.getDisplayName(), chatmessage}), true);
        }

    }

    private static int setMode(CommandContext<CommandSourceStack> commandcontext, Collection<ServerPlayer> collection, GameType enumgamemode) {
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.gameMode.getGameModeForPlayer() != enumgamemode) {
                entityplayer.setGameMode(enumgamemode);
                // CraftBukkit start - handle event cancelling the change
                if (entityplayer.gameMode.getGameModeForPlayer() != enumgamemode) {
                    commandcontext.getSource().sendFailure(new TextComponent("Failed to set the gamemode of '" + entityplayer.getScoreboardName() + "'"));
                    continue;
                }
                // CraftBukkit end
                logGamemodeChange((CommandSourceStack) commandcontext.getSource(), entityplayer, enumgamemode);
                ++i;
            }
        }

        return i;
    }
}
