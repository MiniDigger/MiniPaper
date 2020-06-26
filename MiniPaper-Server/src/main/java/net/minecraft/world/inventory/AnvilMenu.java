package net.minecraft.world.inventory;

import java.util.Iterator;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class AnvilMenu extends ItemCombinerMenu {

    private static final Logger LOGGER = LogManager.getLogger();
    private int repairItemCountCost;
    public String itemName;
    public final DataSlot cost;
    // CraftBukkit start
    public int maximumRepairCost = 40;
    private CraftInventoryView bukkitEntity;
    // CraftBukkit end

    public AnvilMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public AnvilMenu(int i, Inventory playerinventory, ContainerLevelAccess containeraccess) {
        super(MenuType.ANVIL, i, playerinventory, containeraccess);
        this.cost = DataSlot.standalone();
        this.addDataSlot(this.cost);
    }

    @Override
    protected boolean isValidBlock(BlockState iblockdata) {
        return iblockdata.is((Tag) BlockTags.ANVIL);
    }

    @Override
    protected boolean mayPickup(Player entityhuman, boolean flag) {
        return (entityhuman.abilities.instabuild || entityhuman.experienceLevel >= this.cost.get()) && this.cost.get() > 0;
    }

    @Override
    protected ItemStack onTake(Player entityhuman, ItemStack itemstack) {
        if (!entityhuman.abilities.instabuild) {
            entityhuman.giveExperienceLevels(-this.cost.get());
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        if (this.repairItemCountCost > 0) {
            ItemStack itemstack1 = this.inputSlots.getItem(1);

            if (!itemstack1.isEmpty() && itemstack1.getCount() > this.repairItemCountCost) {
                itemstack1.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack1);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(0);
        this.access.execute((world, blockposition) -> {
            BlockState iblockdata = world.getType(blockposition);

            if (!entityhuman.abilities.instabuild && iblockdata.is((Tag) BlockTags.ANVIL) && entityhuman.getRandom().nextFloat() < 0.12F) {
                BlockState iblockdata1 = AnvilBlock.damage(iblockdata);

                if (iblockdata1 == null) {
                    world.removeBlock(blockposition, false);
                    world.levelEvent(1029, blockposition, 0);
                } else {
                    world.setTypeAndData(blockposition, iblockdata1, 2);
                    world.levelEvent(1030, blockposition, 0);
                }
            } else {
                world.levelEvent(1030, blockposition, 0);
            }

        });
        return itemstack;
    }

    @Override
    public void createResult() {
        ItemStack itemstack = this.inputSlots.getItem(0);

        this.cost.set(1);
        int i = 0;
        byte b0 = 0;
        byte b1 = 0;

        if (itemstack.isEmpty()) {
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(0);
        } else {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemstack1);
            int j = b0 + itemstack.getBaseRepairCost() + (itemstack2.isEmpty() ? 0 : itemstack2.getBaseRepairCost());

            this.repairItemCountCost = 0;
            if (!itemstack2.isEmpty()) {
                boolean flag = itemstack2.getItem() == Items.ENCHANTED_BOOK && !EnchantedBookItem.getEnchantments(itemstack2).isEmpty();
                int k;
                int l;
                int i1;

                if (itemstack1.isDamageableItem() && itemstack1.getItem().isValidRepairItem(itemstack, itemstack2)) {
                    k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    if (k <= 0) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(0);
                        return;
                    }

                    for (i1 = 0; k > 0 && i1 < itemstack2.getCount(); ++i1) {
                        l = itemstack1.getDamageValue() - k;
                        itemstack1.setDamageValue(l);
                        ++i;
                        k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i1;
                } else {
                    if (!flag && (itemstack1.getItem() != itemstack2.getItem() || !itemstack1.isDamageableItem())) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(0);
                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        k = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        i1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        l = i1 + itemstack1.getMaxDamage() * 12 / 100;
                        int j1 = k + l;
                        int k1 = itemstack1.getMaxDamage() - j1;

                        if (k1 < 0) {
                            k1 = 0;
                        }

                        if (k1 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(k1);
                            i += 2;
                        }
                    }

                    Map<Enchantment, Integer> map1 = EnchantmentHelper.getEnchantments(itemstack2);
                    boolean flag1 = false;
                    boolean flag2 = false;
                    Iterator iterator = map1.keySet().iterator();

                    while (iterator.hasNext()) {
                        Enchantment enchantment = (Enchantment) iterator.next();

                        if (enchantment != null) {
                            int l1 = (Integer) map.getOrDefault(enchantment, 0);
                            int i2 = (Integer) map1.get(enchantment);

                            i2 = l1 == i2 ? i2 + 1 : Math.max(i2, l1);
                            boolean flag3 = enchantment.canEnchant(itemstack);

                            if (this.player.abilities.instabuild || itemstack.getItem() == Items.ENCHANTED_BOOK) {
                                flag3 = true;
                            }

                            Iterator iterator1 = map.keySet().iterator();

                            while (iterator1.hasNext()) {
                                Enchantment enchantment1 = (Enchantment) iterator1.next();

                                if (enchantment1 != enchantment && !enchantment.isCompatibleWith(enchantment1)) {
                                    flag3 = false;
                                    ++i;
                                }
                            }

                            if (!flag3) {
                                flag2 = true;
                            } else {
                                flag1 = true;
                                if (i2 > enchantment.getMaxLevel()) {
                                    i2 = enchantment.getMaxLevel();
                                }

                                map.put(enchantment, i2);
                                int j2 = 0;

                                switch (enchantment.getRarity()) {
                                    case COMMON:
                                        j2 = 1;
                                        break;
                                    case UNCOMMON:
                                        j2 = 2;
                                        break;
                                    case RARE:
                                        j2 = 4;
                                        break;
                                    case VERY_RARE:
                                        j2 = 8;
                                }

                                if (flag) {
                                    j2 = Math.max(1, j2 / 2);
                                }

                                i += j2 * i2;
                                if (itemstack.getCount() > 1) {
                                    i = 40;
                                }
                            }
                        }
                    }

                    if (flag2 && !flag1) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(0);
                        return;
                    }
                }
            }

            if (StringUtils.isBlank(this.itemName)) {
                if (itemstack.hasCustomHoverName()) {
                    b1 = 1;
                    i += b1;
                    itemstack1.resetHoverName();
                }
            } else if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                b1 = 1;
                i += b1;
                itemstack1.setHoverName((Component) (new TextComponent(this.itemName)));
            }

            this.cost.set(j + i);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (b1 == i && b1 > 0 && this.cost.get() >= maximumRepairCost) { // CraftBukkit
                this.cost.set(maximumRepairCost - 1); // CraftBukkit
            }

            if (this.cost.get() >= maximumRepairCost && !this.player.abilities.instabuild) { // CraftBukkit
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int k2 = itemstack1.getBaseRepairCost();

                if (!itemstack2.isEmpty() && k2 < itemstack2.getBaseRepairCost()) {
                    k2 = itemstack2.getBaseRepairCost();
                }

                if (b1 != i || b1 == 0) {
                    k2 = calculateIncreasedRepairCost(k2);
                }

                itemstack1.setRepairCost(k2);
                EnchantmentHelper.setEnchantments(map, itemstack1);
            }

            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), itemstack1); // CraftBukkit
            this.broadcastChanges();
        }
    }

    public static int calculateIncreasedRepairCost(int i) {
        return i * 2 + 1;
    }

    public void setItemName(String s) {
        this.itemName = s;
        if (this.getSlot(2).hasItem()) {
            ItemStack itemstack = this.getSlot(2).getItem();

            if (StringUtils.isBlank(s)) {
                itemstack.resetHoverName();
            } else {
                itemstack.setHoverName((Component) (new TextComponent(this.itemName)));
            }
        }

        this.createResult();
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryAnvil(
                access.getLocation(), this.inputSlots, this.resultSlots, this);
        bukkitEntity = new CraftInventoryView(this.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
