package net.minecraft.world.inventory;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryCrafting;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class InventoryMenu extends RecipeBookMenu<CraftingContainer> {

    public static final ResourceLocation BLOCK_ATLAS = new ResourceLocation("textures/atlas/blocks.png");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_HELMET = new ResourceLocation("item/empty_armor_slot_helmet");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_CHESTPLATE = new ResourceLocation("item/empty_armor_slot_chestplate");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_LEGGINGS = new ResourceLocation("item/empty_armor_slot_leggings");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_BOOTS = new ResourceLocation("item/empty_armor_slot_boots");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_SHIELD = new ResourceLocation("item/empty_armor_slot_shield");
    private static final ResourceLocation[] TEXTURE_EMPTY_SLOTS = new ResourceLocation[]{InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET};
    private static final EquipmentSlot[] SLOT_IDS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    // CraftBukkit start
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots;
    // CraftBukkit end
    public final boolean active;
    private final Player owner;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;
    // CraftBukkit end

    public InventoryMenu(Inventory playerinventory, boolean flag, Player entityhuman) {
        super((MenuType) null, 0);
        this.active = flag;
        this.owner = entityhuman;
        // CraftBukkit start
        this.resultSlots = new ResultContainer(); // CraftBukkit - moved to before InventoryCrafting construction
        this.craftSlots = new CraftingContainer(this, 2, 2, playerinventory.player); // CraftBukkit - pass player
        this.craftSlots.resultInventory = this.resultSlots; // CraftBukkit - let InventoryCrafting know about its result slot
        this.player = playerinventory; // CraftBukkit - save player
        setTitle(new TranslatableComponent("container.crafting")); // SPIGOT-4722: Allocate title for player inventory
        // CraftBukkit end
        this.addSlot((Slot) (new ResultSlot(playerinventory.player, this.craftSlots, this.resultSlots, 0, 154, 28)));

        int i;
        int j;

        for (i = 0; i < 2; ++i) {
            for (j = 0; j < 2; ++j) {
                this.addSlot(new Slot(this.craftSlots, j + i * 2, 98 + j * 18, 18 + i * 18));
            }
        }

        for (i = 0; i < 4; ++i) {
            final EquipmentSlot enumitemslot = InventoryMenu.SLOT_IDS[i];

            this.addSlot(new Slot(playerinventory, 39 - i, 8, 8 + i * 18) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack itemstack) {
                    return enumitemslot == Mob.getEquipmentSlotForItem(itemstack);
                }

                @Override
                public boolean mayPickup(Player entityhuman1) {
                    ItemStack itemstack = this.getItem();

                    return !itemstack.isEmpty() && !entityhuman1.isCreative() && EnchantmentHelper.hasBindingCurse(itemstack) ? false : super.mayPickup(entityhuman1);
                }
            });
        }

        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerinventory, j + (i + 1) * 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerinventory, i, 8 + i * 18, 142));
        }

        this.addSlot(new Slot(playerinventory, 40, 77, 62) {
        });
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents autorecipestackmanager) {
        this.craftSlots.fillStackedContents(autorecipestackmanager);
    }

    @Override
    public void clearCraftingContent() {
        this.resultSlots.clearContent();
        this.craftSlots.clearContent();
    }

    @Override
    public boolean recipeMatches(Recipe<? super CraftingContainer> irecipe) {
        return irecipe.matches(this.craftSlots, this.owner.level);
    }

    @Override
    public void slotsChanged(Container iinventory) {
        CraftingMenu.a(this.containerId, this.owner.level, this.owner, this.craftSlots, this.resultSlots, this); // CraftBukkit
    }

    @Override
    public void removed(Player entityhuman) {
        super.removed(entityhuman);
        this.resultSlots.clearContent();
        if (!entityhuman.level.isClientSide) {
            this.clearContainer(entityhuman, entityhuman.level, (Container) this.craftSlots);
        }
    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

            if (i == 0) {
                if (!this.moveItemStackTo(itemstack1, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (i >= 1 && i < 5) {
                if (!this.moveItemStackTo(itemstack1, 9, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i >= 5 && i < 9) {
                if (!this.moveItemStackTo(itemstack1, 9, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (enumitemslot.getType() == EquipmentSlot.Type.ARMOR && !((Slot) this.slots.get(8 - enumitemslot.getIndex())).hasItem()) {
                int j = 8 - enumitemslot.getIndex();

                if (!this.moveItemStackTo(itemstack1, j, j + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (enumitemslot == EquipmentSlot.OFFHAND && !((Slot) this.slots.get(45)).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 45, 46, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i >= 9 && i < 36) {
                if (!this.moveItemStackTo(itemstack1, 36, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i >= 36 && i < 45) {
                if (!this.moveItemStackTo(itemstack1, 9, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 9, 45, false)) {
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

    public CraftingContainer getCraftSlots() {
        return this.craftSlots;
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryCrafting inventory = new CraftInventoryCrafting(this.craftSlots, this.resultSlots);
        bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
