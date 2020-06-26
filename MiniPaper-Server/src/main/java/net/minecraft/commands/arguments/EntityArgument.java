package net.minecraft.commands.arguments;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class EntityArgument implements ArgumentType<EntitySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "@e", "@e[type=foo]", "dd12be42-52a9-4a91-a8a1-11c01849e498");
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_ENTITY = new SimpleCommandExceptionType(new TranslatableComponent("argument.entity.toomany"));
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER = new SimpleCommandExceptionType(new TranslatableComponent("argument.player.toomany"));
    public static final SimpleCommandExceptionType ERROR_ONLY_PLAYERS_ALLOWED = new SimpleCommandExceptionType(new TranslatableComponent("argument.player.entities"));
    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND = new SimpleCommandExceptionType(new TranslatableComponent("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND = new SimpleCommandExceptionType(new TranslatableComponent("argument.entity.notfound.player"));
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(new TranslatableComponent("argument.entity.selector.not_allowed"));
    private final boolean single;
    private final boolean playersOnly;

    protected EntityArgument(boolean flag, boolean flag1) {
        this.single = flag;
        this.playersOnly = flag1;
    }

    public static EntityArgument entity() {
        return new EntityArgument(true, false);
    }

    public static Entity getEntity(CommandContext<CommandSourceStack> commandcontext, String s) throws CommandSyntaxException {
        return ((EntitySelector) commandcontext.getArgument(s, EntitySelector.class)).findSingleEntity((CommandSourceStack) commandcontext.getSource());
    }

    public static EntityArgument entities() {
        return new EntityArgument(false, false);
    }

    public static Collection<? extends Entity> getEntities(CommandContext<CommandSourceStack> commandcontext, String s) throws CommandSyntaxException {
        Collection<? extends Entity> collection = getOptionalEntities(commandcontext, s);

        if (collection.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else {
            return collection;
        }
    }

    public static Collection<? extends Entity> getOptionalEntities(CommandContext<CommandSourceStack> commandcontext, String s) throws CommandSyntaxException {
        return ((EntitySelector) commandcontext.getArgument(s, EntitySelector.class)).findEntities((CommandSourceStack) commandcontext.getSource());
    }

    public static Collection<ServerPlayer> getOptionalPlayers(CommandContext<CommandSourceStack> commandcontext, String s) throws CommandSyntaxException {
        return ((EntitySelector) commandcontext.getArgument(s, EntitySelector.class)).findPlayers((CommandSourceStack) commandcontext.getSource());
    }

    public static EntityArgument player() {
        return new EntityArgument(true, true);
    }

    public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> commandcontext, String s) throws CommandSyntaxException {
        return ((EntitySelector) commandcontext.getArgument(s, EntitySelector.class)).findSinglePlayer((CommandSourceStack) commandcontext.getSource());
    }

    public static EntityArgument players() {
        return new EntityArgument(false, true);
    }

    public static Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> commandcontext, String s) throws CommandSyntaxException {
        List<ServerPlayer> list = ((EntitySelector) commandcontext.getArgument(s, EntitySelector.class)).findPlayers((CommandSourceStack) commandcontext.getSource());

        if (list.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return list;
        }
    }

    public EntitySelector parse(StringReader stringreader) throws CommandSyntaxException {
        // CraftBukkit start
        return parse(stringreader, false);
    }

    public EntitySelector parse(StringReader stringreader, boolean overridePermissions) throws CommandSyntaxException {
        // CraftBukkit end
        boolean flag = false;
        EntitySelectorParser argumentparserselector = new EntitySelectorParser(stringreader);
        EntitySelector entityselector = argumentparserselector.parse(overridePermissions); // CraftBukkit

        if (entityselector.getMaxResults() > 1 && this.single) {
            if (this.playersOnly) {
                stringreader.setCursor(0);
                throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.createWithContext(stringreader);
            } else {
                stringreader.setCursor(0);
                throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(stringreader);
            }
        } else if (entityselector.includesEntities() && this.playersOnly && !entityselector.isSelfSelector()) {
            stringreader.setCursor(0);
            throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(stringreader);
        } else {
            return entityselector;
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandcontext, SuggestionsBuilder suggestionsbuilder) {
        if (commandcontext.getSource() instanceof SharedSuggestionProvider) {
            StringReader stringreader = new StringReader(suggestionsbuilder.getInput());

            stringreader.setCursor(suggestionsbuilder.getStart());
            SharedSuggestionProvider icompletionprovider = (SharedSuggestionProvider) commandcontext.getSource();
            EntitySelectorParser argumentparserselector = new EntitySelectorParser(stringreader, icompletionprovider.hasPermission(2));

            try {
                argumentparserselector.parse();
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }

            return argumentparserselector.fillSuggestions(suggestionsbuilder, (suggestionsbuilder1) -> {
                Collection<String> collection = icompletionprovider.getOnlinePlayerNames();
                Iterable<String> iterable = this.playersOnly ? collection : Iterables.concat(collection, icompletionprovider.getSelectedEntities());

                SharedSuggestionProvider.suggest((Iterable) iterable, suggestionsbuilder1);
            });
        } else {
            return Suggestions.empty();
        }
    }

    public Collection<String> getExamples() {
        return EntityArgument.EXAMPLES;
    }

    public static class Serializer implements ArgumentSerializer<EntityArgument> {

        public Serializer() {}

        public void serializeToNetwork(EntityArgument argumententity, FriendlyByteBuf packetdataserializer) {
            byte b0 = 0;

            if (argumententity.single) {
                b0 = (byte) (b0 | 1);
            }

            if (argumententity.playersOnly) {
                b0 = (byte) (b0 | 2);
            }

            packetdataserializer.writeByte(b0);
        }

        @Override
        public EntityArgument deserializeFromNetwork(FriendlyByteBuf packetdataserializer) {
            byte b0 = packetdataserializer.readByte();

            return new EntityArgument((b0 & 1) != 0, (b0 & 2) != 0);
        }

        public void serializeToJson(EntityArgument argumententity, JsonObject jsonobject) {
            jsonobject.addProperty("amount", argumententity.single ? "single" : "multiple");
            jsonobject.addProperty("type", argumententity.playersOnly ? "players" : "entities");
        }
    }
}
