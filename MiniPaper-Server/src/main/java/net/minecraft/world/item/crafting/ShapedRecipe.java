package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import java.util.ArrayList;
import java.util.List;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.inventory.RecipeChoice;
// CraftBukkit end

public class ShapedRecipe implements CraftingRecipe {

    private final int width;
    private final int height;
    private final NonNullList<Ingredient> recipeItems;
    private final ItemStack result;
    private final ResourceLocation id;
    private final String group;

    public ShapedRecipe(ResourceLocation minecraftkey, String s, int i, int j, NonNullList<Ingredient> nonnulllist, ItemStack itemstack) {
        this.id = minecraftkey;
        this.group = s;
        this.width = i;
        this.height = j;
        this.recipeItems = nonnulllist;
        this.result = itemstack;
    }

    // CraftBukkit start
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe() {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapedRecipe recipe = new CraftShapedRecipe(result, this);
        recipe.setGroup(this.group);

        switch (this.height) {
        case 1:
            switch (this.width) {
            case 1:
                recipe.shape("a");
                break;
            case 2:
                recipe.shape("ab");
                break;
            case 3:
                recipe.shape("abc");
                break;
            }
            break;
        case 2:
            switch (this.width) {
            case 1:
                recipe.shape("a","b");
                break;
            case 2:
                recipe.shape("ab","cd");
                break;
            case 3:
                recipe.shape("abc","def");
                break;
            }
            break;
        case 3:
            switch (this.width) {
            case 1:
                recipe.shape("a","b","c");
                break;
            case 2:
                recipe.shape("ab","cd","ef");
                break;
            case 3:
                recipe.shape("abc","def","ghi");
                break;
            }
            break;
        }
        char c = 'a';
        for (Ingredient list : this.recipeItems) {
            RecipeChoice choice = CraftRecipe.toBukkit(list);
            if (choice != null) {
                recipe.setIngredient(c, choice);
            }

            c++;
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getRecipeSerializer() {
        return RecipeSerializer.SHAPED_RECIPE;
    }

    @Override
    public ItemStack getResultItem() {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.recipeItems;
    }

    public boolean matches(CraftingContainer inventorycrafting, Level world) {
        for (int i = 0; i <= inventorycrafting.getWidth() - this.width; ++i) {
            for (int j = 0; j <= inventorycrafting.getHeight() - this.height; ++j) {
                if (this.matches(inventorycrafting, i, j, true)) {
                    return true;
                }

                if (this.matches(inventorycrafting, i, j, false)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matches(CraftingContainer inventorycrafting, int i, int j, boolean flag) {
        for (int k = 0; k < inventorycrafting.getWidth(); ++k) {
            for (int l = 0; l < inventorycrafting.getHeight(); ++l) {
                int i1 = k - i;
                int j1 = l - j;
                Ingredient recipeitemstack = Ingredient.EMPTY;

                if (i1 >= 0 && j1 >= 0 && i1 < this.width && j1 < this.height) {
                    if (flag) {
                        recipeitemstack = (Ingredient) this.recipeItems.get(this.width - i1 - 1 + j1 * this.width);
                    } else {
                        recipeitemstack = (Ingredient) this.recipeItems.get(i1 + j1 * this.width);
                    }
                }

                if (!recipeitemstack.test(inventorycrafting.getItem(k + l * inventorycrafting.getWidth()))) {
                    return false;
                }
            }
        }

        return true;
    }

    public ItemStack assemble(CraftingContainer inventorycrafting) {
        return this.getResultItem().copy();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    private static NonNullList<Ingredient> dissolvePattern(String[] astring, Map<String, Ingredient> map, int i, int j) {
        NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i * j, Ingredient.EMPTY);
        Set<String> set = Sets.newHashSet(map.keySet());

        set.remove(" ");

        for (int k = 0; k < astring.length; ++k) {
            for (int l = 0; l < astring[k].length(); ++l) {
                String s = astring[k].substring(l, l + 1);
                Ingredient recipeitemstack = (Ingredient) map.get(s);

                if (recipeitemstack == null) {
                    throw new JsonSyntaxException("Pattern references symbol '" + s + "' but it's not defined in the key");
                }

                set.remove(s);
                nonnulllist.set(l + i * k, recipeitemstack);
            }
        }

        if (!set.isEmpty()) {
            throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + set);
        } else {
            return nonnulllist;
        }
    }

    @VisibleForTesting
    static String[] shrink(String... astring) {
        int i = Integer.MAX_VALUE;
        int j = 0;
        int k = 0;
        int l = 0;

        for (int i1 = 0; i1 < astring.length; ++i1) {
            String s = astring[i1];

            i = Math.min(i, firstNonSpace(s));
            int j1 = lastNonSpace(s);

            j = Math.max(j, j1);
            if (j1 < 0) {
                if (k == i1) {
                    ++k;
                }

                ++l;
            } else {
                l = 0;
            }
        }

        if (astring.length == l) {
            return new String[0];
        } else {
            String[] astring1 = new String[astring.length - l - k];

            for (int k1 = 0; k1 < astring1.length; ++k1) {
                astring1[k1] = astring[k1 + k].substring(i, j + 1);
            }

            return astring1;
        }
    }

    private static int firstNonSpace(String s) {
        int i;

        for (i = 0; i < s.length() && s.charAt(i) == ' '; ++i) {
            ;
        }

        return i;
    }

    private static int lastNonSpace(String s) {
        int i;

        for (i = s.length() - 1; i >= 0 && s.charAt(i) == ' '; --i) {
            ;
        }

        return i;
    }

    private static String[] patternFromJson(JsonArray jsonarray) {
        String[] astring = new String[jsonarray.size()];

        if (astring.length > 3) {
            throw new JsonSyntaxException("Invalid pattern: too many rows, 3 is maximum");
        } else if (astring.length == 0) {
            throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
        } else {
            for (int i = 0; i < astring.length; ++i) {
                String s = GsonHelper.convertToString(jsonarray.get(i), "pattern[" + i + "]");

                if (s.length() > 3) {
                    throw new JsonSyntaxException("Invalid pattern: too many columns, 3 is maximum");
                }

                if (i > 0 && astring[0].length() != s.length()) {
                    throw new JsonSyntaxException("Invalid pattern: each row must be the same width");
                }

                astring[i] = s;
            }

            return astring;
        }
    }

    private static Map<String, Ingredient> keyFromJson(JsonObject jsonobject) {
        Map<String, Ingredient> map = Maps.newHashMap();
        Iterator iterator = jsonobject.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, JsonElement> entry = (Entry) iterator.next();

            if (((String) entry.getKey()).length() != 1) {
                throw new JsonSyntaxException("Invalid key entry: '" + (String) entry.getKey() + "' is an invalid symbol (must be 1 character only).");
            }

            if (" ".equals(entry.getKey())) {
                throw new JsonSyntaxException("Invalid key entry: ' ' is a reserved symbol.");
            }

            map.put(entry.getKey(), Ingredient.fromJson((JsonElement) entry.getValue()));
        }

        map.put(" ", Ingredient.EMPTY);
        return map;
    }

    public static ItemStack itemFromJson(JsonObject jsonobject) {
        String s = GsonHelper.getAsString(jsonobject, "item");
        Item item = (Item) Registry.ITEM.getOptional(new ResourceLocation(s)).orElseThrow(() -> {
            return new JsonSyntaxException("Unknown item '" + s + "'");
        });

        if (jsonobject.has("data")) {
            throw new JsonParseException("Disallowed data tag found");
        } else {
            int i = GsonHelper.getAsInt(jsonobject, "count", (int) 1);

            return new ItemStack(item, i);
        }
    }

    public static class Serializer implements RecipeSerializer<ShapedRecipe> {

        public Serializer() {}

        @Override
        public ShapedRecipe fromJson(ResourceLocation minecraftkey, JsonObject jsonobject) {
            String s = GsonHelper.getAsString(jsonobject, "group", "");
            Map<String, Ingredient> map = ShapedRecipe.keyFromJson(GsonHelper.getAsJsonObject(jsonobject, "key"));
            String[] astring = ShapedRecipe.shrink(ShapedRecipe.patternFromJson(GsonHelper.getAsJsonArray(jsonobject, "pattern")));
            int i = astring[0].length();
            int j = astring.length;
            NonNullList<Ingredient> nonnulllist = ShapedRecipe.dissolvePattern(astring, map, i, j);
            ItemStack itemstack = ShapedRecipe.itemFromJson(GsonHelper.getAsJsonObject(jsonobject, "result"));

            return new ShapedRecipe(minecraftkey, s, i, j, nonnulllist, itemstack);
        }

        @Override
        public ShapedRecipe fromNetwork(ResourceLocation minecraftkey, FriendlyByteBuf packetdataserializer) {
            int i = packetdataserializer.readVarInt();
            int j = packetdataserializer.readVarInt();
            String s = packetdataserializer.readUtf(32767);
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i * j, Ingredient.EMPTY);

            for (int k = 0; k < nonnulllist.size(); ++k) {
                nonnulllist.set(k, Ingredient.fromNetwork(packetdataserializer));
            }

            ItemStack itemstack = packetdataserializer.readItem();

            return new ShapedRecipe(minecraftkey, s, i, j, nonnulllist, itemstack);
        }

        public void toNetwork(FriendlyByteBuf packetdataserializer, ShapedRecipe shapedrecipes) {
            packetdataserializer.writeVarInt(shapedrecipes.width);
            packetdataserializer.writeVarInt(shapedrecipes.height);
            packetdataserializer.writeUtf(shapedrecipes.group);
            Iterator iterator = shapedrecipes.recipeItems.iterator();

            while (iterator.hasNext()) {
                Ingredient recipeitemstack = (Ingredient) iterator.next();

                recipeitemstack.toNetwork(packetdataserializer);
            }

            packetdataserializer.writeItem(shapedrecipes.result);
        }
    }
}
