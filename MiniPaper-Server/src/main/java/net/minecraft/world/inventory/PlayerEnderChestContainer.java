package net.minecraft.world.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.bukkit.Location;
import org.bukkit.inventory.InventoryHolder;

public class PlayerEnderChestContainer extends SimpleContainer {

    private EnderChestBlockEntity activeChest;
    // CraftBukkit start
    private final Player owner;

    public InventoryHolder getBukkitOwner() {
        return owner.getBukkitEntity();
    }

    @Override
    public Location getLocation() {
        return new Location(this.activeChest.getLevel().getWorld(), this.activeChest.getBlockPos().getX(), this.activeChest.getBlockPos().getY(), this.activeChest.getBlockPos().getZ());
    }

    public PlayerEnderChestContainer(Player owner) {
        super(27);
        this.owner = owner;
        // CraftBukkit end
    }

    public void setActiveChest(EnderChestBlockEntity tileentityenderchest) {
        this.activeChest = tileentityenderchest;
    }

    @Override
    public void fromTag(ListTag nbttaglist) {
        int i;

        for (i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, ItemStack.EMPTY);
        }

        for (i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound = nbttaglist.getCompound(i);
            int j = nbttagcompound.getByte("Slot") & 255;

            if (j >= 0 && j < this.getContainerSize()) {
                this.setItem(j, ItemStack.of(nbttagcompound));
            }
        }

    }

    @Override
    public ListTag createTag() {
        ListTag nbttaglist = new ListTag();

        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                CompoundTag nbttagcompound = new CompoundTag();

                nbttagcompound.putByte("Slot", (byte) i);
                itemstack.save(nbttagcompound);
                nbttaglist.add(nbttagcompound);
            }
        }

        return nbttaglist;
    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.activeChest != null && !this.activeChest.stillValid(entityhuman) ? false : super.stillValid(entityhuman);
    }

    @Override
    public void startOpen(Player entityhuman) {
        if (this.activeChest != null) {
            this.activeChest.startOpen();
        }

        super.startOpen(entityhuman);
    }

    @Override
    public void stopOpen(Player entityhuman) {
        if (this.activeChest != null) {
            this.activeChest.stopOpen();
        }

        super.stopOpen(entityhuman);
        this.activeChest = null;
    }
}
