package net.minecraft.world.inventory;

import net.minecraft.recipebook.ServerPlaceSmeltingRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryFurnace;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public abstract class AbstractFurnaceMenu extends RecipeBookMenu<Container> {

    private final Container container;
    private final ContainerData data;
    protected final Level level;
    private final RecipeType<? extends AbstractCookingRecipe> recipeType;

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryFurnace inventory = new CraftInventoryFurnace((AbstractFurnaceBlockEntity) this.container);
        bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end

    protected AbstractFurnaceMenu(MenuType<?> containers, RecipeType<? extends AbstractCookingRecipe> recipes, int i, Inventory playerinventory) {
        this(containers, recipes, i, playerinventory, new SimpleContainer(3), new SimpleContainerData(4));
    }

    protected AbstractFurnaceMenu(MenuType<?> containers, RecipeType<? extends AbstractCookingRecipe> recipes, int i, Inventory playerinventory, Container iinventory, ContainerData icontainerproperties) {
        super(containers, i);
        this.recipeType = recipes;
        checkContainerSize(iinventory, 3);
        checkContainerDataCount(icontainerproperties, 4);
        this.container = iinventory;
        this.data = icontainerproperties;
        this.level = playerinventory.player.level;
        this.addSlot(new Slot(iinventory, 0, 56, 17));
        this.addSlot((Slot) (new FurnaceFuelSlot(this, iinventory, 1, 56, 53)));
        this.addSlot((Slot) (new FurnaceResultSlot(playerinventory.player, iinventory, 2, 116, 35)));
        this.player = playerinventory; // CraftBukkit - save player

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 142));
        }

        this.addDataSlots(icontainerproperties);
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents autorecipestackmanager) {
        if (this.container instanceof StackedContentsCompatible) {
            ((StackedContentsCompatible) this.container).fillStackedContents(autorecipestackmanager);
        }

    }

    @Override
    public void clearCraftingContent() {
        this.container.clearContent();
    }

    @Override
    public void handlePlacement(boolean flag, Recipe<?> irecipe, ServerPlayer entityplayer) {
        (new ServerPlaceSmeltingRecipe(this)).recipeClicked(entityplayer, irecipe, flag); // CraftBukkit - decompile error
    }

    @Override
    public boolean recipeMatches(Recipe<? super Container> irecipe) {
        return irecipe.matches(this.container, this.level);
    }

    @Override
    public int getResultSlotIndex() {
        return 2;
    }

    @Override
    public int getGridWidth() {
        return 1;
    }

    @Override
    public int getGridHeight() {
        return 1;
    }

    @Override
    public boolean stillValid(Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.container.stillValid(entityhuman);
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i == 2) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (i != 1 && i != 0) {
                if (this.canSmelt(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.isFuel(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= 3 && i < 30) {
                    if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= 30 && i < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(entityhuman, itemstack1);
        }

        return itemstack;
    }

    protected boolean canSmelt(ItemStack itemstack) {
        return this.level.getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>) this.recipeType, new SimpleContainer(new ItemStack[]{itemstack}), this.level).isPresent(); // Eclipse fail
    }

    protected boolean isFuel(ItemStack itemstack) {
        return AbstractFurnaceBlockEntity.isFuel(itemstack);
    }
}
