package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftInventoryLectern;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
// CraftBukkit end

public class LecternMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryLectern inventory = new CraftInventoryLectern(this.lectern);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
    private final Container lectern;
    private final ContainerData lecternData;

    // CraftBukkit start - add player
    public LecternMenu(int i, Inventory playerinventory) {
        this(i, new SimpleContainer(1), new SimpleContainerData(1), playerinventory);
    }

    public LecternMenu(int i, Container iinventory, ContainerData icontainerproperties, Inventory playerinventory) {
        // CraftBukkit end
        super(MenuType.LECTERN, i);
        checkContainerSize(iinventory, 1);
        checkContainerDataCount(icontainerproperties, 1);
        this.lectern = iinventory;
        this.lecternData = icontainerproperties;
        this.addSlot(new Slot(iinventory, 0, 0, 0) {
            @Override
            public void setChanged() {
                super.setChanged();
                LecternMenu.this.slotsChanged(this.container);
            }
        });
        this.addDataSlots(icontainerproperties);
        player = (Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player entityhuman, int i) {
        int j;

        if (i >= 100) {
            j = i - 100;
            this.setData(0, j);
            return true;
        } else {
            switch (i) {
                case 1:
                    j = this.lecternData.get(0);
                    this.setData(0, j - 1);
                    return true;
                case 2:
                    j = this.lecternData.get(0);
                    this.setData(0, j + 1);
                    return true;
                case 3:
                    if (!entityhuman.mayBuild()) {
                        return false;
                    }

                    // CraftBukkit start - Event for taking the book
                    PlayerTakeLecternBookEvent event = new PlayerTakeLecternBookEvent(player, ((CraftInventoryLectern) getBukkitView().getTopInventory()).getHolder());
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        return false;
                    }
                    // CraftBukkit end
                    ItemStack itemstack = this.lectern.removeItemNoUpdate(0);

                    this.lectern.setChanged();
                    if (!entityhuman.inventory.add(itemstack)) {
                        entityhuman.drop(itemstack, false);
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public void setData(int i, int j) {
        super.setData(i, j);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.lectern.stillValid(entityhuman);
    }
}
