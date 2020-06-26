package net.minecraft.server.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.GameRules;

public class GameRuleCommand {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = (LiteralArgumentBuilder) Commands.literal("gamerule").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        });

        GameRules.a(new GameRules.GameRuleVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void a(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.Type<T> gamerules_gameruledefinition) {
                literalargumentbuilder.then(((LiteralArgumentBuilder) Commands.literal(gamerules_gamerulekey.a()).executes((commandcontext) -> {
                    return GameRuleCommand.b((CommandSourceStack) commandcontext.getSource(), gamerules_gamerulekey);
                })).then(gamerules_gameruledefinition.createArgument("value").executes((commandcontext) -> {
                    return GameRuleCommand.b(commandcontext, gamerules_gamerulekey);
                })));
            }
        });
        com_mojang_brigadier_commanddispatcher.register(literalargumentbuilder);
    }

    private static <T extends GameRules.Value<T>> int b(CommandContext<CommandSourceStack> commandcontext, GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) commandcontext.getSource();
        T t0 = commandlistenerwrapper.getLevel().getGameRules().get(gamerules_gamerulekey); // CraftBukkit

        t0.setFromArgument(commandcontext, "value");
        commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.gamerule.set", new Object[]{gamerules_gamerulekey.a(), t0.toString()}), true);
        return t0.getCommandResult();
    }

    private static <T extends GameRules.Value<T>> int b(CommandSourceStack commandlistenerwrapper, GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        T t0 = commandlistenerwrapper.getLevel().getGameRules().get(gamerules_gamerulekey); // CraftBukkit

        commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.gamerule.query", new Object[]{gamerules_gamerulekey.a(), t0.toString()}), false);
        return t0.getCommandResult();
    }
}
