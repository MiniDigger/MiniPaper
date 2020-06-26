package net.minecraft.world.inventory;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.UpgradeRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.inventory.CraftInventoryView; // CraftBukkit

public class SmithingMenu extends ItemCombinerMenu {

    private final Level level;
    @Nullable
    private UpgradeRecipe selectedRecipe;
    private final List<UpgradeRecipe> recipes;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity;
    // CraftBukkit end

    public SmithingMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public SmithingMenu(int i, Inventory playerinventory, ContainerLevelAccess containeraccess) {
        super(MenuType.SMITHING, i, playerinventory, containeraccess);
        this.level = playerinventory.player.level;
        this.recipes = this.level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING);
    }

    @Override
    protected boolean isValidBlock(BlockState iblockdata) {
        return iblockdata.is(Blocks.SMITHING_TABLE);
    }

    @Override
    protected boolean mayPickup(Player entityhuman, boolean flag) {
        return this.selectedRecipe != null && this.selectedRecipe.matches(this.inputSlots, this.level);
    }

    @Override
    protected ItemStack onTake(Player entityhuman, ItemStack itemstack) {
        this.shrinkStackInSlot(0);
        this.shrinkStackInSlot(1);
        this.access.execute((world, blockposition) -> {
            world.levelEvent(1044, blockposition, 0);
        });
        return itemstack;
    }

    private void shrinkStackInSlot(int i) {
        ItemStack itemstack = this.inputSlots.getItem(i);

        itemstack.shrink(1);
        this.inputSlots.setItem(i, itemstack);
    }

    @Override
    public void createResult() {
        List<UpgradeRecipe> list = this.level.getRecipeManager().getRecipesFor(RecipeType.SMITHING, this.inputSlots, this.level);

        if (list.isEmpty()) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        } else {
            this.selectedRecipe = (UpgradeRecipe) list.get(0);
            ItemStack itemstack = this.selectedRecipe.assemble(this.inputSlots);

            this.resultSlots.setItem(0, itemstack);
        }

    }

    @Override
    protected boolean shouldQuickMoveToAdditionalSlot(ItemStack itemstack) {
        return this.recipes.stream().anyMatch((recipesmithing) -> {
            return recipesmithing.isAdditionIngredient(itemstack);
        });
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventorySmithing(
                this.inputSlots, this.resultSlots);
        bukkitEntity = new CraftInventoryView(this.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
