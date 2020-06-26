package net.minecraft.world.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftInventoryLoom;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class LoomMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryLoom inventory = new CraftInventoryLoom(this.inputContainer, this.outputContainer);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
    private final ContainerLevelAccess access;
    private final DataSlot selectedBannerPatternIndex;
    private Runnable slotUpdateListener;
    private final Slot bannerSlot;
    private final Slot dyeSlot;
    private final Slot patternSlot;
    private final Slot resultSlot;
    private long lastSoundTime;
    private final Container inputContainer;
    private final Container outputContainer;

    public LoomMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public LoomMenu(int i, Inventory playerinventory, final ContainerLevelAccess containeraccess) {
        super(MenuType.LOOM, i);
        this.selectedBannerPatternIndex = DataSlot.standalone();
        this.slotUpdateListener = () -> {
        };
        this.inputContainer = new SimpleContainer(3) {
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotsChanged((Container) this);
                LoomMenu.this.slotUpdateListener.run();
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return containeraccess.getLocation();
            }
            // CraftBukkit end
        };
        this.outputContainer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotUpdateListener.run();
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return containeraccess.getLocation();
            }
            // CraftBukkit end
        };
        this.access = containeraccess;
        this.bannerSlot = this.addSlot(new Slot(this.inputContainer, 0, 13, 26) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.getItem() instanceof BannerItem;
            }
        });
        this.dyeSlot = this.addSlot(new Slot(this.inputContainer, 1, 33, 26) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.getItem() instanceof DyeItem;
            }
        });
        this.patternSlot = this.addSlot(new Slot(this.inputContainer, 2, 23, 45) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.getItem() instanceof BannerPatternItem;
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.outputContainer, 0, 143, 58) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return false;
            }

            @Override
            public ItemStack onTake(net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack) {
                LoomMenu.this.bannerSlot.remove(1);
                LoomMenu.this.dyeSlot.remove(1);
                if (!LoomMenu.this.bannerSlot.hasItem() || !LoomMenu.this.dyeSlot.hasItem()) {
                    LoomMenu.this.selectedBannerPatternIndex.set(0);
                }

                containeraccess.execute((world, blockposition) -> {
                    long j = world.getGameTime();

                    if (LoomMenu.this.lastSoundTime != j) {
                        world.playSound((net.minecraft.world.entity.player.Player) null, blockposition, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        LoomMenu.this.lastSoundTime = j;
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

        this.addDataSlot(this.selectedBannerPatternIndex);
        player = (Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, entityhuman, Blocks.LOOM);
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player entityhuman, int i) {
        if (i > 0 && i <= BannerPattern.AVAILABLE_PATTERNS) {
            this.selectedBannerPatternIndex.set(i);
            this.setupResultSlot();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void slotsChanged(Container iinventory) {
        ItemStack itemstack = this.bannerSlot.getItem();
        ItemStack itemstack1 = this.dyeSlot.getItem();
        ItemStack itemstack2 = this.patternSlot.getItem();
        ItemStack itemstack3 = this.resultSlot.getItem();

        if (!itemstack3.isEmpty() && (itemstack.isEmpty() || itemstack1.isEmpty() || this.selectedBannerPatternIndex.get() <= 0 || this.selectedBannerPatternIndex.get() >= BannerPattern.COUNT - BannerPattern.PATTERN_ITEM_COUNT && itemstack2.isEmpty())) {
            this.resultSlot.set(ItemStack.EMPTY);
            this.selectedBannerPatternIndex.set(0);
        } else if (!itemstack2.isEmpty() && itemstack2.getItem() instanceof BannerPatternItem) {
            CompoundTag nbttagcompound = itemstack.getOrCreateTagElement("BlockEntityTag");
            boolean flag = nbttagcompound.contains("Patterns", 9) && !itemstack.isEmpty() && nbttagcompound.getList("Patterns", 10).size() >= 6;

            if (flag) {
                this.selectedBannerPatternIndex.set(0);
            } else {
                this.selectedBannerPatternIndex.set(((BannerPatternItem) itemstack2.getItem()).getBannerPattern().ordinal());
            }
        }

        this.setupResultSlot();
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i == this.resultSlot.index) {
                if (!this.moveItemStackTo(itemstack1, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (i != this.dyeSlot.index && i != this.bannerSlot.index && i != this.patternSlot.index) {
                if (itemstack1.getItem() instanceof BannerItem) {
                    if (!this.moveItemStackTo(itemstack1, this.bannerSlot.index, this.bannerSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.getItem() instanceof DyeItem) {
                    if (!this.moveItemStackTo(itemstack1, this.dyeSlot.index, this.dyeSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.getItem() instanceof BannerPatternItem) {
                    if (!this.moveItemStackTo(itemstack1, this.patternSlot.index, this.patternSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= 4 && i < 31) {
                    if (!this.moveItemStackTo(itemstack1, 31, 40, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= 31 && i < 40 && !this.moveItemStackTo(itemstack1, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 4, 40, false)) {
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

    @Override
    public void removed(net.minecraft.world.entity.player.Player entityhuman) {
        super.removed(entityhuman);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(entityhuman, entityhuman.level, this.inputContainer);
        });
    }

    private void setupResultSlot() {
        if (this.selectedBannerPatternIndex.get() > 0) {
            ItemStack itemstack = this.bannerSlot.getItem();
            ItemStack itemstack1 = this.dyeSlot.getItem();
            ItemStack itemstack2 = ItemStack.EMPTY;

            if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
                itemstack2 = itemstack.copy();
                itemstack2.setCount(1);
                BannerPattern enumbannerpatterntype = BannerPattern.values()[this.selectedBannerPatternIndex.get()];
                DyeColor enumcolor = ((DyeItem) itemstack1.getItem()).getDyeColor();
                CompoundTag nbttagcompound = itemstack2.getOrCreateTagElement("BlockEntityTag");
                ListTag nbttaglist;

                if (nbttagcompound.contains("Patterns", 9)) {
                    nbttaglist = nbttagcompound.getList("Patterns", 10);
                    // CraftBukkit start
                    while (nbttaglist.size() > 20) {
                        nbttaglist.remove(20);
                    }
                    // CraftBukkit end
                } else {
                    nbttaglist = new ListTag();
                    nbttagcompound.put("Patterns", nbttaglist);
                }

                CompoundTag nbttagcompound1 = new CompoundTag();

                nbttagcompound1.putString("Pattern", enumbannerpatterntype.getHashname());
                nbttagcompound1.putInt("Color", enumcolor.getId());
                nbttaglist.add(nbttagcompound1);
            }

            if (!ItemStack.matches(itemstack2, this.resultSlot.getItem())) {
                this.resultSlot.set(itemstack2);
            }
        }

    }
}
