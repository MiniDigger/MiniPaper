package net.minecraft.world.inventory;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class GrindstoneMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryGrindstone inventory = new CraftInventoryGrindstone(this.repairSlots, this.resultSlots);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
    private final Container resultSlots;
    private final Container repairSlots;
    private final ContainerLevelAccess access;

    public GrindstoneMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int i, Inventory playerinventory, final ContainerLevelAccess containeraccess) {
        super(MenuType.GRINDSTONE, i);
        this.resultSlots = new ResultContainer();
        this.repairSlots = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                GrindstoneMenu.this.slotsChanged((Container) this);
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return containeraccess.getLocation();
            }
            // CraftBukkit end
        };
        this.access = containeraccess;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.isDamageableItem() || itemstack.getItem() == Items.ENCHANTED_BOOK || itemstack.isEnchanted();
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.isDamageableItem() || itemstack.getItem() == Items.ENCHANTED_BOOK || itemstack.isEnchanted();
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return false;
            }

            @Override
            public ItemStack onTake(net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack) {
                containeraccess.execute((world, blockposition) -> {
                    int j = this.getExperienceAmount(world);

                    while (j > 0) {
                        int k = ExperienceOrb.getExperienceValue(j);

                        j -= k;
                        world.addFreshEntity(new ExperienceOrb(world, (double) blockposition.getX(), (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, k));
                    }

                    world.levelEvent(1042, blockposition, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
                return itemstack;
            }

            private int getExperienceAmount(Level world) {
                byte b0 = 0;
                int j = b0 + this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));

                j += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (j > 0) {
                    int k = (int) Math.ceil((double) j / 2.0D);

                    return k + world.random.nextInt(k);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(ItemStack itemstack) {
                int j = 0;
                Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemstack);
                Iterator iterator = map.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<Enchantment, Integer> entry = (Entry) iterator.next();
                    Enchantment enchantment = (Enchantment) entry.getKey();
                    Integer integer = (Integer) entry.getValue();

                    if (!enchantment.isCurse()) {
                        j += enchantment.getMinCost(integer);
                    }
                }

                return j;
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
    public void slotsChanged(Container iinventory) {
        super.slotsChanged(iinventory);
        if (iinventory == this.repairSlots) {
            this.createResult();
        }

    }

    private void createResult() {
        ItemStack itemstack = this.repairSlots.getItem(0);
        ItemStack itemstack1 = this.repairSlots.getItem(1);
        boolean flag = !itemstack.isEmpty() || !itemstack1.isEmpty();
        boolean flag1 = !itemstack.isEmpty() && !itemstack1.isEmpty();

        if (flag) {
            boolean flag2 = !itemstack.isEmpty() && itemstack.getItem() != Items.ENCHANTED_BOOK && !itemstack.isEnchanted() || !itemstack1.isEmpty() && itemstack1.getItem() != Items.ENCHANTED_BOOK && !itemstack1.isEnchanted();

            if (itemstack.getCount() > 1 || itemstack1.getCount() > 1 || !flag1 && flag2) {
                this.resultSlots.setItem(0, ItemStack.EMPTY);
                this.broadcastChanges();
                return;
            }

            byte b0 = 1;
            int i;
            ItemStack itemstack2;

            if (flag1) {
                if (itemstack.getItem() != itemstack1.getItem()) {
                    this.resultSlots.setItem(0, ItemStack.EMPTY);
                    this.broadcastChanges();
                    return;
                }

                Item item = itemstack.getItem();
                int j = item.getMaxDamage() - itemstack.getDamageValue();
                int k = item.getMaxDamage() - itemstack1.getDamageValue();
                int l = j + k + item.getMaxDamage() * 5 / 100;

                i = Math.max(item.getMaxDamage() - l, 0);
                itemstack2 = this.mergeEnchants(itemstack, itemstack1);
                if (!itemstack2.isDamageableItem()) {
                    if (!ItemStack.matches(itemstack, itemstack1)) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.broadcastChanges();
                        return;
                    }

                    b0 = 2;
                }
            } else {
                boolean flag3 = !itemstack.isEmpty();

                i = flag3 ? itemstack.getDamageValue() : itemstack1.getDamageValue();
                itemstack2 = flag3 ? itemstack : itemstack1;
            }

            this.resultSlots.setItem(0, this.removeNonCurses(itemstack2, i, b0));
        } else {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        }

        this.broadcastChanges();
    }

    private ItemStack mergeEnchants(ItemStack itemstack, ItemStack itemstack1) {
        ItemStack itemstack2 = itemstack.copy();
        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemstack1);
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Enchantment, Integer> entry = (Entry) iterator.next();
            Enchantment enchantment = (Enchantment) entry.getKey();

            if (!enchantment.isCurse() || EnchantmentHelper.getItemEnchantmentLevel(enchantment, itemstack2) == 0) {
                itemstack2.enchant(enchantment, (Integer) entry.getValue());
            }
        }

        return itemstack2;
    }

    private ItemStack removeNonCurses(ItemStack itemstack, int i, int j) {
        ItemStack itemstack1 = itemstack.copy();

        itemstack1.removeTagKey("Enchantments");
        itemstack1.removeTagKey("StoredEnchantments");
        if (i > 0) {
            itemstack1.setDamageValue(i);
        } else {
            itemstack1.removeTagKey("Damage");
        }

        itemstack1.setCount(j);
        Map<Enchantment, Integer> map = (Map) EnchantmentHelper.getEnchantments(itemstack).entrySet().stream().filter((entry) -> {
            return ((Enchantment) entry.getKey()).isCurse();
        }).collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        EnchantmentHelper.setEnchantments(map, itemstack1);
        itemstack1.setRepairCost(0);
        if (itemstack1.getItem() == Items.ENCHANTED_BOOK && map.size() == 0) {
            itemstack1 = new ItemStack(Items.BOOK);
            if (itemstack.hasCustomHoverName()) {
                itemstack1.setHoverName(itemstack.getHoverName());
            }
        }

        for (int k = 0; k < map.size(); ++k) {
            itemstack1.setRepairCost(AnvilMenu.calculateIncreasedRepairCost(itemstack1.getBaseRepairCost()));
        }

        return itemstack1;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player entityhuman) {
        super.removed(entityhuman);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(entityhuman, world, this.repairSlots);
        });
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, entityhuman, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            ItemStack itemstack2 = this.repairSlots.getItem(0);
            ItemStack itemstack3 = this.repairSlots.getItem(1);

            if (i == 2) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (i != 0 && i != 1) {
                if (!itemstack2.isEmpty() && !itemstack3.isEmpty()) {
                    if (i >= 3 && i < 30) {
                        if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (i >= 30 && i < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 0, 2, false)) {
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
}
