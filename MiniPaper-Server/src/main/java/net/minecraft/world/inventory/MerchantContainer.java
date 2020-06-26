package net.minecraft.world.inventory;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftAbstractVillager;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class MerchantContainer implements Container {

    private final Merchant merchant;
    private final NonNullList<ItemStack> itemStacks;
    @Nullable
    private MerchantOffer activeOffer;
    public int selectionHint;
    private int futureXp;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.itemStacks;
    }

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
        merchant.setTradingPlayer((Player) null); // SPIGOT-4860
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int i) {
        maxStack = i;
    }

    public org.bukkit.inventory.InventoryHolder getOwner() {
        return (merchant instanceof AbstractVillager) ? (CraftAbstractVillager) ((AbstractVillager) this.merchant).getBukkitEntity() : null;
    }

    @Override
    public Location getLocation() {
        return (merchant instanceof Villager) ? ((Villager) this.merchant).getBukkitEntity().getLocation() : null;
    }
    // CraftBukkit end

    public MerchantContainer(Merchant imerchant) {
        this.itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);
        this.merchant = imerchant;
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.itemStacks.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getItem(int i) {
        return (ItemStack) this.itemStacks.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        ItemStack itemstack = (ItemStack) this.itemStacks.get(i);

        if (i == 2 && !itemstack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, i, itemstack.getCount());
        } else {
            ItemStack itemstack1 = ContainerHelper.removeItem(this.itemStacks, i, j);

            if (!itemstack1.isEmpty() && this.isPaymentSlot(i)) {
                this.updateSellItem();
            }

            return itemstack1;
        }
    }

    private boolean isPaymentSlot(int i) {
        return i == 0 || i == 1;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(this.itemStacks, i);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.itemStacks.set(i, itemstack);
        if (!itemstack.isEmpty() && itemstack.getCount() > this.getMaxStackSize()) {
            itemstack.setCount(this.getMaxStackSize());
        }

        if (this.isPaymentSlot(i)) {
            this.updateSellItem();
        }

    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.merchant.getTradingPlayer() == entityhuman;
    }

    @Override
    public void setChanged() {
        this.updateSellItem();
    }

    public void updateSellItem() {
        this.activeOffer = null;
        ItemStack itemstack;
        ItemStack itemstack1;

        if (((ItemStack) this.itemStacks.get(0)).isEmpty()) {
            itemstack = (ItemStack) this.itemStacks.get(1);
            itemstack1 = ItemStack.EMPTY;
        } else {
            itemstack = (ItemStack) this.itemStacks.get(0);
            itemstack1 = (ItemStack) this.itemStacks.get(1);
        }

        if (itemstack.isEmpty()) {
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        } else {
            MerchantOffers merchantrecipelist = this.merchant.getOffers();

            if (!merchantrecipelist.isEmpty()) {
                MerchantOffer merchantrecipe = merchantrecipelist.getRecipeFor(itemstack, itemstack1, this.selectionHint);

                if (merchantrecipe == null || merchantrecipe.isOutOfStock()) {
                    this.activeOffer = merchantrecipe;
                    merchantrecipe = merchantrecipelist.getRecipeFor(itemstack1, itemstack, this.selectionHint);
                }

                if (merchantrecipe != null && !merchantrecipe.isOutOfStock()) {
                    this.activeOffer = merchantrecipe;
                    this.setItem(2, merchantrecipe.assemble());
                    this.futureXp = merchantrecipe.getXp();
                } else {
                    this.setItem(2, ItemStack.EMPTY);
                    this.futureXp = 0;
                }
            }

            this.merchant.notifyTradeUpdated(this.getItem(2));
        }
    }

    @Nullable
    public MerchantOffer getActiveOffer() {
        return this.activeOffer;
    }

    public void setSelectionHint(int i) {
        this.selectionHint = i;
        this.updateSellItem();
    }

    @Override
    public void clearContent() {
        this.itemStacks.clear();
    }
}
