package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.ClientSideMerchant;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.bukkit.craftbukkit.inventory.CraftInventoryView; // CraftBukkit

public class MerchantMenu extends AbstractContainerMenu {

    private final Merchant trader;
    private final MerchantContainer tradeContainer;

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity == null) {
            bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), new org.bukkit.craftbukkit.inventory.CraftInventoryMerchant(trader, tradeContainer), this);
        }
        return bukkitEntity;
    }
    // CraftBukkit end

    public MerchantMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, new ClientSideMerchant(playerinventory.player));
    }

    public MerchantMenu(int i, Inventory playerinventory, Merchant imerchant) {
        super(MenuType.MERCHANT, i);
        this.trader = imerchant;
        this.tradeContainer = new MerchantContainer(imerchant);
        this.addSlot(new Slot(this.tradeContainer, 0, 136, 37));
        this.addSlot(new Slot(this.tradeContainer, 1, 162, 37));
        this.addSlot((Slot) (new MerchantResultSlot(playerinventory.player, imerchant, this.tradeContainer, 2, 220, 37)));
        this.player = playerinventory; // CraftBukkit - save player

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 108 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 108 + j * 18, 142));
        }

    }

    @Override
    public void slotsChanged(Container iinventory) {
        this.tradeContainer.updateSellItem();
        super.slotsChanged(iinventory);
    }

    public void setSelectionHint(int i) {
        this.tradeContainer.setSelectionHint(i);
    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.trader.getTradingPlayer() == entityhuman;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack itemstack, Slot slot) {
        return false;
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
                this.playTradeSound();
            } else if (i != 0 && i != 1) {
                if (i >= 3 && i < 30) {
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

    private void playTradeSound() {
        if (!this.trader.getLevel().isClientSide && this.trader instanceof Entity) { // CraftBukkit - SPIGOT-5035
            Entity entity = (Entity) this.trader;

            this.trader.getLevel().playLocalSound(entity.getX(), entity.getY(), entity.getZ(), this.trader.getTradeSound(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        }

    }

    @Override
    public void removed(Player entityhuman) {
        super.removed(entityhuman);
        this.trader.setTradingPlayer((Player) null);
        if (!this.trader.getLevel().isClientSide) {
            if (entityhuman.isAlive() && (!(entityhuman instanceof ServerPlayer) || !((ServerPlayer) entityhuman).hasDisconnected())) {
                entityhuman.inventory.placeItemBackInInventory(entityhuman.level, this.tradeContainer.removeItemNoUpdate(0));
                entityhuman.inventory.placeItemBackInInventory(entityhuman.level, this.tradeContainer.removeItemNoUpdate(1));
            } else {
                ItemStack itemstack = this.tradeContainer.removeItemNoUpdate(0);

                if (!itemstack.isEmpty()) {
                    entityhuman.drop(itemstack, false);
                }

                itemstack = this.tradeContainer.removeItemNoUpdate(1);
                if (!itemstack.isEmpty()) {
                    entityhuman.drop(itemstack, false);
                }
            }

        }
    }

    public void tryMoveItems(int i) {
        if (this.getOffers().size() > i) {
            ItemStack itemstack = this.tradeContainer.getItem(0);

            if (!itemstack.isEmpty()) {
                if (!this.moveItemStackTo(itemstack, 3, 39, true)) {
                    return;
                }

                this.tradeContainer.setItem(0, itemstack);
            }

            ItemStack itemstack1 = this.tradeContainer.getItem(1);

            if (!itemstack1.isEmpty()) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return;
                }

                this.tradeContainer.setItem(1, itemstack1);
            }

            if (this.tradeContainer.getItem(0).isEmpty() && this.tradeContainer.getItem(1).isEmpty()) {
                ItemStack itemstack2 = ((MerchantOffer) this.getOffers().get(i)).getCostA();

                this.moveFromInventoryToPaymentSlot(0, itemstack2);
                ItemStack itemstack3 = ((MerchantOffer) this.getOffers().get(i)).getCostB();

                this.moveFromInventoryToPaymentSlot(1, itemstack3);
            }

        }
    }

    private void moveFromInventoryToPaymentSlot(int i, ItemStack itemstack) {
        if (!itemstack.isEmpty()) {
            for (int j = 3; j < 39; ++j) {
                ItemStack itemstack1 = ((Slot) this.slots.get(j)).getItem();

                if (!itemstack1.isEmpty() && this.isSameItem(itemstack, itemstack1)) {
                    ItemStack itemstack2 = this.tradeContainer.getItem(i);
                    int k = itemstack2.isEmpty() ? 0 : itemstack2.getCount();
                    int l = Math.min(itemstack.getMaxStackSize() - k, itemstack1.getCount());
                    ItemStack itemstack3 = itemstack1.copy();
                    int i1 = k + l;

                    itemstack1.shrink(l);
                    itemstack3.setCount(i1);
                    this.tradeContainer.setItem(i, itemstack3);
                    if (i1 >= itemstack.getMaxStackSize()) {
                        break;
                    }
                }
            }
        }

    }

    private boolean isSameItem(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.getItem() == itemstack1.getItem() && ItemStack.tagMatches(itemstack, itemstack1);
    }

    public MerchantOffers getOffers() {
        return this.trader.getOffers();
    }
}
