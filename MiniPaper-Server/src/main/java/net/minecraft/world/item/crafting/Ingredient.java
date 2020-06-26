package net.minecraft.world.item.crafting;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.SerializationTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class Ingredient implements Predicate<ItemStack> {

    public static final Ingredient EMPTY = new Ingredient(Stream.empty());
    private final Ingredient.Value[] values;
    public ItemStack[] itemStacks;
    private IntList stackingIds;
    public boolean exact; // CraftBukkit

    public Ingredient(Stream<? extends Ingredient.Value> stream) {
        this.values = (Ingredient.Value[]) stream.toArray((i) -> {
            return new Ingredient.Value[i];
        });
    }

    public void dissolve() {
        if (this.itemStacks == null) {
            this.itemStacks = (ItemStack[]) Arrays.stream(this.values).flatMap((recipeitemstack_provider) -> {
                return recipeitemstack_provider.getItems().stream();
            }).distinct().toArray((i) -> {
                return new ItemStack[i];
            });
        }

    }

    public boolean test(@Nullable ItemStack itemstack) {
        if (itemstack == null) {
            return false;
        } else {
            this.dissolve();
            if (this.itemStacks.length == 0) {
                return itemstack.isEmpty();
            } else {
                ItemStack[] aitemstack = this.itemStacks;
                int i = aitemstack.length;

                for (int j = 0; j < i; ++j) {
                    ItemStack itemstack1 = aitemstack[j];

                    // CraftBukkit start
                    if (exact) {
                        if (itemstack1.getItem() == itemstack.getItem() && ItemStack.tagMatches(itemstack, itemstack1)) {
                            return true;
                        }

                        continue;
                    }
                    // CraftBukkit end
                    if (itemstack1.getItem() == itemstack.getItem()) {
                        return true;
                    }
                }

                return false;
            }
        }
    }

    public IntList getStackingIds() {
        if (this.stackingIds == null) {
            this.dissolve();
            this.stackingIds = new IntArrayList(this.itemStacks.length);
            ItemStack[] aitemstack = this.itemStacks;
            int i = aitemstack.length;

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = aitemstack[j];

                this.stackingIds.add(StackedContents.getStackingIndex(itemstack));
            }

            this.stackingIds.sort(IntComparators.NATURAL_COMPARATOR);
        }

        return this.stackingIds;
    }

    public void toNetwork(FriendlyByteBuf packetdataserializer) {
        this.dissolve();
        packetdataserializer.writeVarInt(this.itemStacks.length);

        for (int i = 0; i < this.itemStacks.length; ++i) {
            packetdataserializer.writeItem(this.itemStacks[i]);
        }

    }

    public JsonElement toJson() {
        if (this.values.length == 1) {
            return this.values[0].serialize();
        } else {
            JsonArray jsonarray = new JsonArray();
            Ingredient.Value[] arecipeitemstack_provider = this.values;
            int i = arecipeitemstack_provider.length;

            for (int j = 0; j < i; ++j) {
                Ingredient.Value recipeitemstack_provider = arecipeitemstack_provider[j];

                jsonarray.add(recipeitemstack_provider.serialize());
            }

            return jsonarray;
        }
    }

    public boolean isEmpty() {
        return this.values.length == 0 && (this.itemStacks == null || this.itemStacks.length == 0) && (this.stackingIds == null || this.stackingIds.isEmpty());
    }

    private static Ingredient fromValues(Stream<? extends Ingredient.Value> stream) {
        Ingredient recipeitemstack = new Ingredient(stream);

        return recipeitemstack.values.length == 0 ? Ingredient.EMPTY : recipeitemstack;
    }

    public static Ingredient of(ItemLike... aimaterial) {
        return of(Arrays.stream(aimaterial).map(ItemStack::new));
    }

    public static Ingredient of(Stream<ItemStack> stream) {
        return fromValues(stream.filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).map((itemstack) -> {
            return new Ingredient.ItemValue(itemstack);
        }));
    }

    public static Ingredient of(Tag<Item> tag) {
        return fromValues(Stream.of(new Ingredient.TagValue(tag)));
    }

    public static Ingredient fromNetwork(FriendlyByteBuf packetdataserializer) {
        int i = packetdataserializer.readVarInt();

        return fromValues(Stream.generate(() -> {
            return new Ingredient.ItemValue(packetdataserializer.readItem());
        }).limit((long) i));
    }

    public static Ingredient fromJson(@Nullable JsonElement jsonelement) {
        if (jsonelement != null && !jsonelement.isJsonNull()) {
            if (jsonelement.isJsonObject()) {
                return fromValues(Stream.of(a(jsonelement.getAsJsonObject())));
            } else if (jsonelement.isJsonArray()) {
                JsonArray jsonarray = jsonelement.getAsJsonArray();

                if (jsonarray.size() == 0) {
                    throw new JsonSyntaxException("Item array cannot be empty, at least one item must be defined");
                } else {
                    return fromValues(StreamSupport.stream(jsonarray.spliterator(), false).map((jsonelement1) -> {
                        return a(GsonHelper.convertToJsonObject(jsonelement1, "item"));
                    }));
                }
            } else {
                throw new JsonSyntaxException("Expected item to be object or array of objects");
            }
        } else {
            throw new JsonSyntaxException("Item cannot be null");
        }
    }

    private static Ingredient.Value a(JsonObject jsonobject) {
        if (jsonobject.has("item") && jsonobject.has("tag")) {
            throw new JsonParseException("An ingredient entry is either a tag or an item, not both");
        } else {
            ResourceLocation minecraftkey;

            if (jsonobject.has("item")) {
                minecraftkey = new ResourceLocation(GsonHelper.getAsString(jsonobject, "item"));
                Item item = (Item) Registry.ITEM.getOptional(minecraftkey).orElseThrow(() -> {
                    return new JsonSyntaxException("Unknown item '" + minecraftkey + "'");
                });

                return new Ingredient.ItemValue(new ItemStack(item));
            } else if (jsonobject.has("tag")) {
                minecraftkey = new ResourceLocation(GsonHelper.getAsString(jsonobject, "tag"));
                Tag<Item> tag = SerializationTags.getInstance().getItems().getTag(minecraftkey);

                if (tag == null) {
                    throw new JsonSyntaxException("Unknown item tag '" + minecraftkey + "'");
                } else {
                    return new Ingredient.TagValue(tag);
                }
            } else {
                throw new JsonParseException("An ingredient entry needs either a tag or an item");
            }
        }
    }

    static class TagValue implements Ingredient.Value {

        private final Tag<Item> tag;

        private TagValue(Tag<Item> tag) {
            this.tag = tag;
        }

        @Override
        public Collection<ItemStack> getItems() {
            List<ItemStack> list = Lists.newArrayList();
            Iterator iterator = this.tag.getValues().iterator();

            while (iterator.hasNext()) {
                Item item = (Item) iterator.next();

                list.add(new ItemStack(item));
            }

            return list;
        }

        @Override
        public JsonObject serialize() {
            JsonObject jsonobject = new JsonObject();

            jsonobject.addProperty("tag", SerializationTags.getInstance().getItems().getIdOrThrow(this.tag).toString());
            return jsonobject;
        }
    }

    public static class ItemValue implements Ingredient.Value {

        private final ItemStack item;

        public ItemValue(ItemStack itemstack) {
            this.item = itemstack;
        }

        @Override
        public Collection<ItemStack> getItems() {
            return Collections.singleton(this.item);
        }

        @Override
        public JsonObject serialize() {
            JsonObject jsonobject = new JsonObject();

            jsonobject.addProperty("item", Registry.ITEM.getKey(this.item.getItem()).toString());
            return jsonobject;
        }
    }

    public interface Value {

        Collection<ItemStack> getItems();

        JsonObject serialize();
    }
}
