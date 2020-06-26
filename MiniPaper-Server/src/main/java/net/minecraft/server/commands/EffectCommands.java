package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MobEffectArgument;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EffectCommands {

    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(new TranslatableComponent("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(new TranslatableComponent("commands.effect.clear.everything.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(new TranslatableComponent("commands.effect.clear.specific.failed"));

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher) {
        com_mojang_brigadier_commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("effect").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((LiteralArgumentBuilder) Commands.literal("clear").executes((commandcontext) -> {
            return clearEffects((CommandSourceStack) commandcontext.getSource(), ImmutableList.of(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()));
        })).then(((RequiredArgumentBuilder) Commands.argument("targets", (ArgumentType) EntityArgument.entities()).executes((commandcontext) -> {
            return clearEffects((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"));
        })).then(Commands.argument("effect", (ArgumentType) MobEffectArgument.effect()).executes((commandcontext) -> {
            return clearEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), MobEffectArgument.getEffect(commandcontext, "effect"));
        }))))).then(Commands.literal("give").then(Commands.argument("targets", (ArgumentType) EntityArgument.entities()).then(((RequiredArgumentBuilder) Commands.argument("effect", (ArgumentType) MobEffectArgument.effect()).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), MobEffectArgument.getEffect(commandcontext, "effect"), (Integer) null, 0, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("seconds", (ArgumentType) IntegerArgumentType.integer(1, 1000000)).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), MobEffectArgument.getEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), 0, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("amplifier", (ArgumentType) IntegerArgumentType.integer(0, 255)).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), MobEffectArgument.getEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), IntegerArgumentType.getInteger(commandcontext, "amplifier"), true);
        })).then(Commands.argument("hideParticles", (ArgumentType) BoolArgumentType.bool()).executes((commandcontext) -> {
            return giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), MobEffectArgument.getEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), IntegerArgumentType.getInteger(commandcontext, "amplifier"), !BoolArgumentType.getBool(commandcontext, "hideParticles"));
        }))))))));
    }

    private static int giveEffect(CommandSourceStack commandlistenerwrapper, Collection<? extends Entity> collection, MobEffect mobeffectlist, @Nullable Integer integer, int i, boolean flag) throws CommandSyntaxException {
        int j = 0;
        int k;

        if (integer != null) {
            if (mobeffectlist.isInstantenous()) {
                k = integer;
            } else {
                k = integer * 20;
            }
        } else if (mobeffectlist.isInstantenous()) {
            k = 1;
        } else {
            k = 600;
        }

        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) {
                MobEffectInstance mobeffect = new MobEffectInstance(mobeffectlist, k, i, false, flag);

                if (((LivingEntity) entity).addEffect(mobeffect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND)) { // CraftBukkit
                    ++j;
                }
            }
        }

        if (j == 0) {
            throw EffectCommands.ERROR_GIVE_FAILED.create();
        } else {
            if (collection.size() == 1) {
                commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.effect.give.success.single", new Object[]{mobeffectlist.getDisplayName(), ((Entity) collection.iterator().next()).getDisplayName(), k / 20}), true);
            } else {
                commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.effect.give.success.multiple", new Object[]{mobeffectlist.getDisplayName(), collection.size(), k / 20}), true);
            }

            return j;
        }
    }

    private static int clearEffects(CommandSourceStack commandlistenerwrapper, Collection<? extends Entity> collection) throws CommandSyntaxException {
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity && ((LivingEntity) entity).removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND)) { // CraftBukkit
                ++i;
            }
        }

        if (i == 0) {
            throw EffectCommands.ERROR_CLEAR_EVERYTHING_FAILED.create();
        } else {
            if (collection.size() == 1) {
                commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.effect.clear.everything.success.single", new Object[]{((Entity) collection.iterator().next()).getDisplayName()}), true);
            } else {
                commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.effect.clear.everything.success.multiple", new Object[]{collection.size()}), true);
            }

            return i;
        }
    }

    private static int clearEffect(CommandSourceStack commandlistenerwrapper, Collection<? extends Entity> collection, MobEffect mobeffectlist) throws CommandSyntaxException {
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity && ((LivingEntity) entity).removeEffect(mobeffectlist, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND)) { // CraftBukkit
                ++i;
            }
        }

        if (i == 0) {
            throw EffectCommands.ERROR_CLEAR_SPECIFIC_FAILED.create();
        } else {
            if (collection.size() == 1) {
                commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.effect.clear.specific.success.single", new Object[]{mobeffectlist.getDisplayName(), ((Entity) collection.iterator().next()).getDisplayName()}), true);
            } else {
                commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.effect.clear.specific.success.multiple", new Object[]{mobeffectlist.getDisplayName(), collection.size()}), true);
            }

            return i;
        }
    }
}
