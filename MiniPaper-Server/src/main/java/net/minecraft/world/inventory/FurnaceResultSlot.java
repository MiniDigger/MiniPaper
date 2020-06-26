package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class FurnaceResultSlot extends Slot {

    private final Player player;
    private int removeCount;

    public FurnaceResultSlot(Player entityhuman, Container iinventory, int i, int j, int k) {
        super(iinventory, i, j, k);
        this.player = entityhuman;
    }

    @Override
    public boolean mayPlace(ItemStack itemstack) {
        return false;
    }

    @Override
    public ItemStack remove(int i) {
        if (this.hasItem()) {
            this.removeCount += Math.min(i, this.getItem().getCount());
        }

        return super.remove(i);
    }

    @Override
    public ItemStack onTake(Player entityhuman, ItemStack itemstack) {
        this.checkTakeAchievements(itemstack);
        super.onTake(entityhuman, itemstack);
        return itemstack;
    }

    @Override
    protected void onQuickCraft(ItemStack itemstack, int i) {
        this.removeCount += i;
        this.checkTakeAchievements(itemstack);
    }

    @Override
    protected void checkTakeAchievements(ItemStack itemstack) {
        itemstack.onCraftedBy(this.player.level, this.player, this.removeCount);
        if (!this.player.level.isClientSide && this.container instanceof AbstractFurnaceBlockEntity) {
            ((AbstractFurnaceBlockEntity) this.container).d(this.player, itemstack, this.removeCount); // CraftBukkit
        }

        this.removeCount = 0;
    }
}
