package net.minecraft.commands.arguments.blocks;

import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockStateParser {

    public static final SimpleCommandExceptionType ERROR_NO_TAGS_ALLOWED = new SimpleCommandExceptionType(new TranslatableComponent("argument.block.tag.disallowed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_BLOCK = new DynamicCommandExceptionType((object) -> {
        return new TranslatableComponent("argument.block.id.invalid", new Object[]{object});
    });
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_PROPERTY = new Dynamic2CommandExceptionType((object, object1) -> {
        return new TranslatableComponent("argument.block.property.unknown", new Object[]{object, object1});
    });
    public static final Dynamic2CommandExceptionType ERROR_DUPLICATE_PROPERTY = new Dynamic2CommandExceptionType((object, object1) -> {
        return new TranslatableComponent("argument.block.property.duplicate", new Object[]{object1, object});
    });
    public static final Dynamic3CommandExceptionType ERROR_INVALID_VALUE = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return new TranslatableComponent("argument.block.property.invalid", new Object[]{object, object2, object1});
    });
    public static final Dynamic2CommandExceptionType ERROR_EXPECTED_VALUE = new Dynamic2CommandExceptionType((object, object1) -> {
        return new TranslatableComponent("argument.block.property.novalue", new Object[]{object, object1});
    });
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_PROPERTIES = new SimpleCommandExceptionType(new TranslatableComponent("argument.block.property.unclosed"));
    private static final BiFunction<SuggestionsBuilder, TagCollection<Block>, CompletableFuture<Suggestions>> SUGGEST_NOTHING = (suggestionsbuilder, tags) -> {
        return suggestionsbuilder.buildFuture();
    };
    private final StringReader reader;
    private final boolean forTesting;
    private final Map<Property<?>, Comparable<?>> properties = Maps.newLinkedHashMap(); // CraftBukkit - stable
    private final Map<String, String> vagueProperties = Maps.newHashMap();
    private ResourceLocation id = new ResourceLocation("");
    private StateDefinition<Block, BlockState> definition;
    private BlockState state;
    @Nullable
    private CompoundTag nbt;
    private ResourceLocation tag = new ResourceLocation("");
    private int tagCursor;
    private BiFunction<SuggestionsBuilder, TagCollection<Block>, CompletableFuture<Suggestions>> suggestions;

    public BlockStateParser(StringReader stringreader, boolean flag) {
        this.suggestions = BlockStateParser.SUGGEST_NOTHING;
        this.reader = stringreader;
        this.forTesting = flag;
    }

    public Map<Property<?>, Comparable<?>> getProperties() {
        return this.properties;
    }

    @Nullable
    public BlockState getBlockData() {
        return this.state;
    }

    @Nullable
    public CompoundTag getNbt() {
        return this.nbt;
    }

    @Nullable
    public ResourceLocation getTag() {
        return this.tag;
    }

    public BlockStateParser parse(boolean flag) throws CommandSyntaxException {
        this.suggestions = this::suggestBlockIdOrTag;
        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.readTag();
            this.suggestions = this::suggestOpenVaguePropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readVagueProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        } else {
            this.readBlock();
            this.suggestions = this::suggestOpenPropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        }

        if (flag && this.reader.canRead() && this.reader.peek() == '{') {
            this.suggestions = BlockStateParser.SUGGEST_NOTHING;
            this.readNbt();
        }

        return this;
    }

    private CompletableFuture<Suggestions> suggestPropertyNameOrEnd(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty()) {
            suggestionsbuilder.suggest(String.valueOf(']'));
        }

        return this.suggestPropertyName(suggestionsbuilder, tags);
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyNameOrEnd(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty()) {
            suggestionsbuilder.suggest(String.valueOf(']'));
        }

        return this.suggestVaguePropertyName(suggestionsbuilder, tags);
    }

    private CompletableFuture<Suggestions> suggestPropertyName(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
        Iterator iterator = this.state.getProperties().iterator();

        while (iterator.hasNext()) {
            Property<?> iblockstate = (Property) iterator.next();

            if (!this.properties.containsKey(iblockstate) && iblockstate.getName().startsWith(s)) {
                suggestionsbuilder.suggest(iblockstate.getName() + '=');
            }
        }

        return suggestionsbuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyName(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);

        if (this.tag != null && !this.tag.getPath().isEmpty()) {
            Tag<Block> tag = tags.getTag(this.tag);

            if (tag != null) {
                Iterator iterator = tag.getValues().iterator();

                while (iterator.hasNext()) {
                    Block block = (Block) iterator.next();
                    Iterator iterator1 = block.getStateDefinition().getProperties().iterator();

                    while (iterator1.hasNext()) {
                        Property<?> iblockstate = (Property) iterator1.next();

                        if (!this.vagueProperties.containsKey(iblockstate.getName()) && iblockstate.getName().startsWith(s)) {
                            suggestionsbuilder.suggest(iblockstate.getName() + '=');
                        }
                    }
                }
            }
        }

        return suggestionsbuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenNbt(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty() && this.hasBlockEntity(tags)) {
            suggestionsbuilder.suggest(String.valueOf('{'));
        }

        return suggestionsbuilder.buildFuture();
    }

    private boolean hasBlockEntity(TagCollection<Block> tags) {
        if (this.state != null) {
            return this.state.getBlock().isEntityBlock();
        } else {
            if (this.tag != null) {
                Tag<Block> tag = tags.getTag(this.tag);

                if (tag != null) {
                    Iterator iterator = tag.getValues().iterator();

                    while (iterator.hasNext()) {
                        Block block = (Block) iterator.next();

                        if (block.isEntityBlock()) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty()) {
            suggestionsbuilder.suggest(String.valueOf('='));
        }

        return suggestionsbuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestNextPropertyOrEnd(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty()) {
            suggestionsbuilder.suggest(String.valueOf(']'));
        }

        if (suggestionsbuilder.getRemaining().isEmpty() && this.properties.size() < this.state.getProperties().size()) {
            suggestionsbuilder.suggest(String.valueOf(','));
        }

        return suggestionsbuilder.buildFuture();
    }

    private static <T extends Comparable<T>> SuggestionsBuilder addSuggestions(SuggestionsBuilder suggestionsbuilder, Property<T> iblockstate) {
        Iterator iterator = iblockstate.getPossibleValues().iterator();

        while (iterator.hasNext()) {
            T t0 = (T) iterator.next(); // CraftBukkit - decompile error

            if (t0 instanceof Integer) {
                suggestionsbuilder.suggest((Integer) t0);
            } else {
                suggestionsbuilder.suggest(iblockstate.getName(t0));
            }
        }

        return suggestionsbuilder;
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyValue(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags, String s) {
        boolean flag = false;

        if (this.tag != null && !this.tag.getPath().isEmpty()) {
            Tag<Block> tag = tags.getTag(this.tag);

            if (tag != null) {
                Iterator iterator = tag.getValues().iterator();

                while (iterator.hasNext()) {
                    Block block = (Block) iterator.next();
                    Property<?> iblockstate = block.getStateDefinition().getProperty(s);

                    if (iblockstate != null) {
                        addSuggestions(suggestionsbuilder, iblockstate);
                    }

                    if (!flag) {
                        Iterator iterator1 = block.getStateDefinition().getProperties().iterator();

                        while (iterator1.hasNext()) {
                            Property<?> iblockstate1 = (Property) iterator1.next();

                            if (!this.vagueProperties.containsKey(iblockstate1.getName())) {
                                flag = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (flag) {
            suggestionsbuilder.suggest(String.valueOf(','));
        }

        suggestionsbuilder.suggest(String.valueOf(']'));
        return suggestionsbuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenVaguePropertiesOrNbt(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty()) {
            Tag<Block> tag = tags.getTag(this.tag);

            if (tag != null) {
                boolean flag = false;
                boolean flag1 = false;
                Iterator iterator = tag.getValues().iterator();

                while (iterator.hasNext()) {
                    Block block = (Block) iterator.next();

                    flag |= !block.getStateDefinition().getProperties().isEmpty();
                    flag1 |= block.isEntityBlock();
                    if (flag && flag1) {
                        break;
                    }
                }

                if (flag) {
                    suggestionsbuilder.suggest(String.valueOf('['));
                }

                if (flag1) {
                    suggestionsbuilder.suggest(String.valueOf('{'));
                }
            }
        }

        return this.suggestTag(suggestionsbuilder, tags);
    }

    private CompletableFuture<Suggestions> suggestOpenPropertiesOrNbt(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (suggestionsbuilder.getRemaining().isEmpty()) {
            if (!this.state.getBlock().getStateDefinition().getProperties().isEmpty()) {
                suggestionsbuilder.suggest(String.valueOf('['));
            }

            if (this.state.getBlock().isEntityBlock()) {
                suggestionsbuilder.suggest(String.valueOf('{'));
            }
        }

        return suggestionsbuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTag(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        return SharedSuggestionProvider.suggestResource((Iterable) tags.getAvailableTags(), suggestionsbuilder.createOffset(this.tagCursor).add(suggestionsbuilder));
    }

    private CompletableFuture<Suggestions> suggestBlockIdOrTag(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        if (this.forTesting) {
            SharedSuggestionProvider.suggestResource((Iterable) tags.getAvailableTags(), suggestionsbuilder, String.valueOf('#'));
        }

        SharedSuggestionProvider.suggestResource((Iterable) Registry.BLOCK.keySet(), suggestionsbuilder);
        return suggestionsbuilder.buildFuture();
    }

    public void readBlock() throws CommandSyntaxException {
        int i = this.reader.getCursor();

        this.id = ResourceLocation.read(this.reader);
        Block block = (Block) Registry.BLOCK.getOptional(this.id).orElseThrow(() -> {
            this.reader.setCursor(i);
            return BlockStateParser.ERROR_UNKNOWN_BLOCK.createWithContext(this.reader, this.id.toString());
        });

        this.definition = block.getStateDefinition();
        this.state = block.getBlockData();
    }

    public void readTag() throws CommandSyntaxException {
        if (!this.forTesting) {
            throw BlockStateParser.ERROR_NO_TAGS_ALLOWED.create();
        } else {
            this.suggestions = this::suggestTag;
            this.reader.expect('#');
            this.tagCursor = this.reader.getCursor();
            this.tag = ResourceLocation.read(this.reader);
        }
    }

    public void readProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestPropertyNameOrEnd;
        this.reader.skipWhitespace();

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                int i = this.reader.getCursor();
                String s = this.reader.readString();
                Property<?> iblockstate = this.definition.getProperty(s);

                if (iblockstate == null) {
                    this.reader.setCursor(i);
                    throw BlockStateParser.ERROR_UNKNOWN_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
                }

                if (this.properties.containsKey(iblockstate)) {
                    this.reader.setCursor(i);
                    throw BlockStateParser.ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skipWhitespace();
                this.suggestions = this::suggestEquals;
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    throw BlockStateParser.ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.suggestions = (suggestionsbuilder, tags) -> {
                    return addSuggestions(suggestionsbuilder, iblockstate).buildFuture();
                };
                int j = this.reader.getCursor();

                this.setValue(iblockstate, this.reader.readString(), j);
                this.suggestions = this::suggestNextPropertyOrEnd;
                this.reader.skipWhitespace();
                if (!this.reader.canRead()) {
                    continue;
                }

                if (this.reader.peek() == ',') {
                    this.reader.skip();
                    this.suggestions = this::suggestPropertyName;
                    continue;
                }

                if (this.reader.peek() != ']') {
                    throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip();
                return;
            }

            throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    public void readVagueProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestVaguePropertyNameOrEnd;
        int i = -1;

        this.reader.skipWhitespace();

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                int j = this.reader.getCursor();
                String s = this.reader.readString();

                if (this.vagueProperties.containsKey(s)) {
                    this.reader.setCursor(j);
                    throw BlockStateParser.ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skipWhitespace();
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    this.reader.setCursor(j);
                    throw BlockStateParser.ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.suggestions = (suggestionsbuilder, tags) -> {
                    return this.suggestVaguePropertyValue(suggestionsbuilder, tags, s);
                };
                i = this.reader.getCursor();
                String s1 = this.reader.readString();

                this.vagueProperties.put(s, s1);
                this.reader.skipWhitespace();
                if (!this.reader.canRead()) {
                    continue;
                }

                i = -1;
                if (this.reader.peek() == ',') {
                    this.reader.skip();
                    this.suggestions = this::suggestVaguePropertyName;
                    continue;
                }

                if (this.reader.peek() != ']') {
                    throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip();
                return;
            }

            if (i >= 0) {
                this.reader.setCursor(i);
            }

            throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    public void readNbt() throws CommandSyntaxException {
        this.nbt = (new TagParser(this.reader)).readStruct();
    }

    private <T extends Comparable<T>> void setValue(Property<T> iblockstate, String s, int i) throws CommandSyntaxException {
        Optional<T> optional = iblockstate.getValue(s);

        if (optional.isPresent()) {
            this.state = (BlockState) this.state.setValue(iblockstate, (T) optional.get()); // CraftBukkit - decompile error
            this.properties.put(iblockstate, optional.get());
        } else {
            this.reader.setCursor(i);
            throw BlockStateParser.ERROR_INVALID_VALUE.createWithContext(this.reader, this.id.toString(), iblockstate.getName(), s);
        }
    }

    public static String serialize(BlockState iblockdata) {
        StringBuilder stringbuilder = new StringBuilder(Registry.BLOCK.getKey(iblockdata.getBlock()).toString());

        if (!iblockdata.getProperties().isEmpty()) {
            stringbuilder.append('[');
            boolean flag = false;

            for (UnmodifiableIterator unmodifiableiterator = iblockdata.getValues().entrySet().iterator(); unmodifiableiterator.hasNext(); flag = true) {
                Entry<Property<?>, Comparable<?>> entry = (Entry) unmodifiableiterator.next();

                if (flag) {
                    stringbuilder.append(',');
                }

                appendProperty(stringbuilder, (Property) entry.getKey(), (Comparable) entry.getValue());
            }

            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    private static <T extends Comparable<T>> void appendProperty(StringBuilder stringbuilder, Property<T> iblockstate, Comparable<?> comparable) {
        stringbuilder.append(iblockstate.getName());
        stringbuilder.append('=');
        stringbuilder.append(iblockstate.getName((T) comparable)); // CraftBukkit - decompile error
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder suggestionsbuilder, TagCollection<Block> tags) {
        return (CompletableFuture) this.suggestions.apply(suggestionsbuilder.createOffset(this.reader.getCursor()), tags);
    }

    public Map<String, String> getVagueProperties() {
        return this.vagueProperties;
    }
}
