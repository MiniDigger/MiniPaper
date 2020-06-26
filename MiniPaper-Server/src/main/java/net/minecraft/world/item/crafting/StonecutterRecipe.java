package net.minecraft.world.item.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftStonecuttingRecipe;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class StonecutterRecipe extends SingleItemRecipe {

    public StonecutterRecipe(ResourceLocation minecraftkey, String s, Ingredient recipeitemstack, ItemStack itemstack) {
        super(RecipeType.STONECUTTING, RecipeSerializer.STONECUTTER, minecraftkey, s, recipeitemstack, itemstack);
    }

    @Override
    public boolean matches(Container iinventory, Level world) {
        return this.ingredient.test(iinventory.getItem(0));
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe() {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftStonecuttingRecipe recipe = new CraftStonecuttingRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient));
        recipe.setGroup(this.group);

        return recipe;
    }
    // CraftBukkit end
}
