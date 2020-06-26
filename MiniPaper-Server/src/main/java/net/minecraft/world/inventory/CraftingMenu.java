package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryCrafting;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class CraftingMenu extends RecipeBookMenu<CraftingContainer> {

    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots;
    public final ContainerLevelAccess access;
    private final Player player;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory craftplayer;
    // CraftBukkit end

    public CraftingMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public CraftingMenu(int i, Inventory playerinventory, ContainerLevelAccess containeraccess) {
        super(MenuType.CRAFTING, i);
        // CraftBukkit start - Switched order of IInventory construction and stored player
        this.resultSlots = new ResultContainer();
        this.craftSlots = new CraftingContainer(this, 3, 3, playerinventory.player); // CraftBukkit - pass player
        this.craftSlots.resultInventory = this.resultSlots;
        this.craftplayer = playerinventory;
        // CraftBukkit end
        this.access = containeraccess;
        this.player = playerinventory.player;
        this.addSlot((Slot) (new ResultSlot(playerinventory.player, this.craftSlots, this.resultSlots, 0, 124, 35)));

        int j;
        int k;

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 3; ++k) {
                this.addSlot(new Slot(this.craftSlots, k + j * 3, 30 + k * 18, 17 + j * 18));
            }
        }

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 142));
        }

    }

    protected static void a(int i, Level world, Player entityhuman, CraftingContainer inventorycrafting, ResultContainer inventorycraftresult, AbstractContainerMenu container) { // CraftBukkit
        if (!world.isClientSide) {
            ServerPlayer entityplayer = (ServerPlayer) entityhuman;
            ItemStack itemstack = ItemStack.EMPTY;
            Optional<CraftingRecipe> optional = world.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, inventorycrafting, world);

            if (optional.isPresent()) {
                CraftingRecipe recipecrafting = (CraftingRecipe) optional.get();

                if (inventorycraftresult.setRecipeUsed(world, entityplayer, recipecrafting)) {
                    itemstack = recipecrafting.assemble(inventorycrafting);
                }
            }
            itemstack = org.bukkit.craftbukkit.event.CraftEventFactory.callPreCraftEvent(inventorycrafting, inventorycraftresult, itemstack, container.getBukkitView(), false); // CraftBukkit

            inventorycraftresult.setItem(0, itemstack);
            entityplayer.connection.sendPacket(new ClientboundContainerSetSlotPacket(i, 0, itemstack));
        }
    }

    @Override
    public void slotsChanged(Container iinventory) {
        this.access.execute((world, blockposition) -> {
            a(this.containerId, world, this.player, this.craftSlots, this.resultSlots, this); // CraftBukkit
        });
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents autorecipestackmanager) {
        this.craftSlots.fillStackedContents(autorecipestackmanager);
    }

    @Override
    public void clearCraftingContent() {
        this.craftSlots.clearContent();
        this.resultSlots.clearContent();
    }

    @Override
    public boolean recipeMatches(Recipe<? super CraftingContainer> irecipe) {
        return irecipe.matches(this.craftSlots, this.player.level);
    }

    @Override
    public void removed(Player entityhuman) {
        super.removed(entityhuman);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(entityhuman, world, (Container) this.craftSlots);
        });
    }

    @Override
    public boolean stillValid(Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, entityhuman, Blocks.CRAFTING_TABLE);
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i == 0) {
                this.access.execute((world, blockposition) -> {
                    itemstack1.getItem().onCraftedBy(itemstack1, world, entityhuman);
                });
                if (!this.moveItemStackTo(itemstack1, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (i >= 10 && i < 46) {
                if (!this.moveItemStackTo(itemstack1, 1, 10, false)) {
                    if (i < 37) {
                        if (!this.moveItemStackTo(itemstack1, 37, 46, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(itemstack1, 10, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(itemstack1, 10, 46, false)) {
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

            ItemStack itemstack2 = slot.onTake(entityhuman, itemstack1);

            if (i == 0) {
                entityhuman.drop(itemstack2, false);
            }
        }

        return itemstack;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack itemstack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(itemstack, slot);
    }

    @Override
    public int getResultSlotIndex() {
        return 0;
    }

    @Override
    public int getGridWidth() {
        return this.craftSlots.getWidth();
    }

    @Override
    public int getGridHeight() {
        return this.craftSlots.getHeight();
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryCrafting inventory = new CraftInventoryCrafting(this.craftSlots, this.resultSlots);
        bukkitEntity = new CraftInventoryView(this.craftplayer.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
