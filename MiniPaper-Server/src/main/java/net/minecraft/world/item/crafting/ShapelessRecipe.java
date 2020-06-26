package net.minecraft.world.item.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Iterator;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapelessRecipe;
// CraftBukkit end

public class ShapelessRecipe implements CraftingRecipe {

    private final ResourceLocation id;
    private final String group;
    private final ItemStack result;
    private final NonNullList<Ingredient> ingredients;

    public ShapelessRecipe(ResourceLocation minecraftkey, String s, ItemStack itemstack, NonNullList<Ingredient> nonnulllist) {
        this.id = minecraftkey;
        this.group = s;
        this.result = itemstack;
        this.ingredients = nonnulllist;
    }

    // CraftBukkit start
    @SuppressWarnings("unchecked")
    public org.bukkit.inventory.ShapelessRecipe toBukkitRecipe() {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapelessRecipe recipe = new CraftShapelessRecipe(result, this);
        recipe.setGroup(this.group);

        for (Ingredient list : this.ingredients) {
            recipe.addIngredient(CraftRecipe.toBukkit(list));
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
        return RecipeSerializer.SHAPELESS_RECIPE;
    }

    @Override
    public ItemStack getResultItem() {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    public boolean matches(CraftingContainer inventorycrafting, Level world) {
        StackedContents autorecipestackmanager = new StackedContents();
        int i = 0;

        for (int j = 0; j < inventorycrafting.getContainerSize(); ++j) {
            ItemStack itemstack = inventorycrafting.getItem(j);

            if (!itemstack.isEmpty()) {
                ++i;
                autorecipestackmanager.accountStack(itemstack, 1);
            }
        }

        return i == this.ingredients.size() && autorecipestackmanager.canCraft(this, (IntList) null);
    }

    public ItemStack assemble(CraftingContainer inventorycrafting) {
        return this.result.copy();
    }

    public static class Serializer implements RecipeSerializer<ShapelessRecipe> {

        public Serializer() {}

        @Override
        public ShapelessRecipe fromJson(ResourceLocation minecraftkey, JsonObject jsonobject) {
            String s = GsonHelper.getAsString(jsonobject, "group", "");
            NonNullList<Ingredient> nonnulllist = itemsFromJson(GsonHelper.getAsJsonArray(jsonobject, "ingredients"));

            if (nonnulllist.isEmpty()) {
                throw new JsonParseException("No ingredients for shapeless recipe");
            } else if (nonnulllist.size() > 9) {
                throw new JsonParseException("Too many ingredients for shapeless recipe");
            } else {
                ItemStack itemstack = ShapedRecipe.itemFromJson(GsonHelper.getAsJsonObject(jsonobject, "result"));

                return new ShapelessRecipe(minecraftkey, s, itemstack, nonnulllist);
            }
        }

        private static NonNullList<Ingredient> itemsFromJson(JsonArray jsonarray) {
            NonNullList<Ingredient> nonnulllist = NonNullList.create();

            for (int i = 0; i < jsonarray.size(); ++i) {
                Ingredient recipeitemstack = Ingredient.fromJson(jsonarray.get(i));

                if (!recipeitemstack.isEmpty()) {
                    nonnulllist.add(recipeitemstack);
                }
            }

            return nonnulllist;
        }

        @Override
        public ShapelessRecipe fromNetwork(ResourceLocation minecraftkey, FriendlyByteBuf packetdataserializer) {
            String s = packetdataserializer.readUtf(32767);
            int i = packetdataserializer.readVarInt();
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i, Ingredient.EMPTY);

            for (int j = 0; j < nonnulllist.size(); ++j) {
                nonnulllist.set(j, Ingredient.fromNetwork(packetdataserializer));
            }

            ItemStack itemstack = packetdataserializer.readItem();

            return new ShapelessRecipe(minecraftkey, s, itemstack, nonnulllist);
        }

        public void toNetwork(FriendlyByteBuf packetdataserializer, ShapelessRecipe shapelessrecipes) {
            packetdataserializer.writeUtf(shapelessrecipes.group);
            packetdataserializer.writeVarInt(shapelessrecipes.ingredients.size());
            Iterator iterator = shapelessrecipes.ingredients.iterator();

            while (iterator.hasNext()) {
                Ingredient recipeitemstack = (Ingredient) iterator.next();

                recipeitemstack.toNetwork(packetdataserializer);
            }

            packetdataserializer.writeItem(shapelessrecipes.result);
        }
    }
}
