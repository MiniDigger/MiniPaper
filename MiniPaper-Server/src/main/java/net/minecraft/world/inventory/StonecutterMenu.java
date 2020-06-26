package net.minecraft.world.inventory;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryStonecutter;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class StonecutterMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final DataSlot selectedRecipeIndex;
    private final Level level;
    private List<StonecutterRecipe> recipes;
    private ItemStack input;
    private long lastSoundTime;
    final Slot inputSlot;
    final Slot resultSlot;
    private Runnable slotUpdateListener;
    public final Container container;
    private final ResultContainer resultContainer;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryStonecutter inventory = new CraftInventoryStonecutter(this.container, this.resultContainer);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end

    public StonecutterMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public StonecutterMenu(int i, Inventory playerinventory, final ContainerLevelAccess containeraccess) {
        super(MenuType.STONECUTTER, i);
        this.selectedRecipeIndex = DataSlot.standalone();
        this.recipes = Lists.newArrayList();
        this.input = ItemStack.EMPTY;
        this.slotUpdateListener = () -> {
        };
        this.container = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                StonecutterMenu.this.slotsChanged((Container) this);
                StonecutterMenu.this.slotUpdateListener.run();
            }
        };
        this.resultContainer = new ResultContainer();
        this.access = containeraccess;
        this.level = playerinventory.player.level;
        this.inputSlot = this.addSlot(new Slot(this.container, 0, 20, 33));
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return false;
            }

            @Override
            public ItemStack onTake(net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack) {
                ItemStack itemstack1 = StonecutterMenu.this.inputSlot.remove(1);

                if (!itemstack1.isEmpty()) {
                    StonecutterMenu.this.setupResultSlot();
                }

                itemstack.getItem().onCraftedBy(itemstack, entityhuman.level, entityhuman);
                containeraccess.execute((world, blockposition) -> {
                    long j = world.getGameTime();

                    if (StonecutterMenu.this.lastSoundTime != j) {
                        world.playSound((net.minecraft.world.entity.player.Player) null, blockposition, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        StonecutterMenu.this.lastSoundTime = j;
                    }

                });
                return super.onTake(entityhuman, itemstack);
            }
        });

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 142));
        }

        this.addDataSlot(this.selectedRecipeIndex);
        player = (Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, entityhuman, Blocks.STONECUTTER);
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player entityhuman, int i) {
        if (this.isValidRecipeIndex(i)) {
            this.selectedRecipeIndex.set(i);
            this.setupResultSlot();
        }

        return true;
    }

    private boolean isValidRecipeIndex(int i) {
        return i >= 0 && i < this.recipes.size();
    }

    @Override
    public void slotsChanged(Container iinventory) {
        ItemStack itemstack = this.inputSlot.getItem();

        if (itemstack.getItem() != this.input.getItem()) {
            this.input = itemstack.copy();
            this.setupRecipeList(iinventory, itemstack);
        }

    }

    private void setupRecipeList(Container iinventory, ItemStack itemstack) {
        this.recipes.clear();
        this.selectedRecipeIndex.set(-1);
        this.resultSlot.set(ItemStack.EMPTY);
        if (!itemstack.isEmpty()) {
            this.recipes = this.level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, iinventory, this.level);
        }

    }

    private void setupResultSlot() {
        if (!this.recipes.isEmpty() && this.isValidRecipeIndex(this.selectedRecipeIndex.get())) {
            StonecutterRecipe recipestonecutting = (StonecutterRecipe) this.recipes.get(this.selectedRecipeIndex.get());

            this.resultSlot.set(recipestonecutting.assemble(this.container));
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
        }

        this.broadcastChanges();
    }

    @Override
    public MenuType<?> getType() {
        return MenuType.STONECUTTER;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack itemstack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(itemstack, slot);
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            Item item = itemstack1.getItem();

            itemstack = itemstack1.copy();
            if (i == 1) {
                item.onCraftedBy(itemstack1, entityhuman.level, entityhuman);
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (i == 0) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.level.getRecipeManager().getRecipeFor(RecipeType.STONECUTTING, new SimpleContainer(new ItemStack[]{itemstack1}), this.level).isPresent()) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i >= 2 && i < 29) {
                if (!this.moveItemStackTo(itemstack1, 29, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i >= 29 && i < 38 && !this.moveItemStackTo(itemstack1, 2, 29, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(entityhuman, itemstack1);
            this.broadcastChanges();
        }

        return itemstack;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player entityhuman) {
        super.removed(entityhuman);
        this.resultContainer.removeItemNoUpdate(1);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(entityhuman, entityhuman.level, this.container);
        });
    }
}
