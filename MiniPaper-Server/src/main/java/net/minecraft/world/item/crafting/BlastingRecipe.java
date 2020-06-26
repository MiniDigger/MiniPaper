package net.minecraft.world.item.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftBlastingRecipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class BlastingRecipe extends AbstractCookingRecipe {

    public BlastingRecipe(ResourceLocation minecraftkey, String s, Ingredient recipeitemstack, ItemStack itemstack, float f, int i) {
        super(RecipeType.BLASTING, minecraftkey, s, recipeitemstack, itemstack, f, i);
    }

    @Override
    public RecipeSerializer<?> getRecipeSerializer() {
        return RecipeSerializer.BLASTING_RECIPE;
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe() {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftBlastingRecipe recipe = new CraftBlastingRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
        recipe.setGroup(this.group);

        return recipe;
    }
    // CraftBukkit end
}
