package net.minecraft.world.inventory;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftInventoryCartography;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class CartographyTableMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryCartography inventory = new CraftInventoryCartography(this.container, this.resultContainer);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
    private final ContainerLevelAccess access;
    private boolean quickMoved;
    private long lastSoundTime;
    public final Container container;
    private final ResultContainer resultContainer;

    public CartographyTableMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public CartographyTableMenu(int i, Inventory playerinventory, final ContainerLevelAccess containeraccess) {
        super(MenuType.CARTOGRAPHY_TABLE, i);
        this.container = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                CartographyTableMenu.this.slotsChanged((Container) this);
                super.setChanged();
            }
        };
        this.resultContainer = new ResultContainer() {
            @Override
            public void setChanged() {
                CartographyTableMenu.this.slotsChanged((Container) this);
                super.setChanged();
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return containeraccess.getLocation();
            }
            // CraftBukkit end
        };
        this.access = containeraccess;
        this.addSlot(new Slot(this.container, 0, 15, 15) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.getItem() == Items.FILLED_MAP;
            }
        });
        this.addSlot(new Slot(this.container, 1, 15, 52) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                Item item = itemstack.getItem();

                return item == Items.PAPER || item == Items.MAP || item == Items.GLASS_PANE;
            }
        });
        this.addSlot(new Slot(this.resultContainer, 2, 145, 39) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return false;
            }

            @Override
            public ItemStack remove(int j) {
                ItemStack itemstack = super.remove(j);
                ItemStack itemstack1 = (ItemStack) containeraccess.evaluate((world, blockposition) -> {
                    if (!CartographyTableMenu.this.quickMoved && CartographyTableMenu.this.container.getItem(1).getItem() == Items.GLASS_PANE) {
                        ItemStack itemstack2 = MapItem.lockMap(world, CartographyTableMenu.this.container.getItem(0));

                        if (itemstack2 != null) {
                            itemstack2.setCount(1);
                            return itemstack2;
                        }
                    }

                    return itemstack;
                }).orElse(itemstack);

                CartographyTableMenu.this.container.removeItem(0, 1);
                CartographyTableMenu.this.container.removeItem(1, 1);
                return itemstack1;
            }

            @Override
            protected void onQuickCraft(ItemStack itemstack, int j) {
                this.remove(j);
                super.onQuickCraft(itemstack, j);
            }

            @Override
            public ItemStack onTake(net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack) {
                itemstack.getItem().onCraftedBy(itemstack, entityhuman.level, entityhuman);
                containeraccess.execute((world, blockposition) -> {
                    long j = world.getGameTime();

                    if (CartographyTableMenu.this.lastSoundTime != j) {
                        world.playSound((net.minecraft.world.entity.player.Player) null, blockposition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        CartographyTableMenu.this.lastSoundTime = j;
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

        player = (Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, entityhuman, Blocks.CARTOGRAPHY_TABLE);
    }

    @Override
    public void slotsChanged(Container iinventory) {
        ItemStack itemstack = this.container.getItem(0);
        ItemStack itemstack1 = this.container.getItem(1);
        ItemStack itemstack2 = this.resultContainer.getItem(2);

        if (!itemstack2.isEmpty() && (itemstack.isEmpty() || itemstack1.isEmpty())) {
            this.resultContainer.removeItemNoUpdate(2);
        } else if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
            this.setupResultSlot(itemstack, itemstack1, itemstack2);
        }

    }

    private void setupResultSlot(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2) {
        this.access.execute((world, blockposition) -> {
            Item item = itemstack1.getItem();
            MapItemSavedData worldmap = MapItem.getSavedData(itemstack, world);

            if (worldmap != null) {
                ItemStack itemstack3;

                if (item == Items.PAPER && !worldmap.locked && worldmap.scale < 4) {
                    itemstack3 = itemstack.copy();
                    itemstack3.setCount(1);
                    itemstack3.getOrCreateTag().putInt("map_scale_direction", 1);
                    this.broadcastChanges();
                } else if (item == Items.GLASS_PANE && !worldmap.locked) {
                    itemstack3 = itemstack.copy();
                    itemstack3.setCount(1);
                    this.broadcastChanges();
                } else {
                    if (item != Items.MAP) {
                        this.resultContainer.removeItemNoUpdate(2);
                        this.broadcastChanges();
                        return;
                    }

                    itemstack3 = itemstack.copy();
                    itemstack3.setCount(2);
                    this.broadcastChanges();
                }

                if (!ItemStack.matches(itemstack3, itemstack2)) {
                    this.resultContainer.setItem(2, itemstack3);
                    this.broadcastChanges();
                }

            }
        });
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
            ItemStack itemstack2 = itemstack1;
            Item item = itemstack1.getItem();

            itemstack = itemstack1.copy();
            if (i == 2) {
                if (this.container.getItem(1).getItem() == Items.GLASS_PANE) {
                    itemstack2 = (ItemStack) this.access.evaluate((world, blockposition) -> {
                        ItemStack itemstack3 = MapItem.lockMap(world, this.container.getItem(0));

                        if (itemstack3 != null) {
                            itemstack3.setCount(1);
                            return itemstack3;
                        } else {
                            return itemstack1;
                        }
                    }).orElse(itemstack1);
                }

                item.onCraftedBy(itemstack2, entityhuman.level, entityhuman);
                if (!this.moveItemStackTo(itemstack2, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack2, itemstack);
            } else if (i != 1 && i != 0) {
                if (item == Items.FILLED_MAP) {
                    if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (item != Items.PAPER && item != Items.MAP && item != Items.GLASS_PANE) {
                    if (i >= 3 && i < 30) {
                        if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (i >= 30 && i < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack2.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (itemstack2.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            this.quickMoved = true;
            slot.onTake(entityhuman, itemstack2);
            this.quickMoved = false;
            this.broadcastChanges();
        }

        return itemstack;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player entityhuman) {
        super.removed(entityhuman);
        this.resultContainer.removeItemNoUpdate(2);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(entityhuman, entityhuman.level, this.container);
        });
    }
}
