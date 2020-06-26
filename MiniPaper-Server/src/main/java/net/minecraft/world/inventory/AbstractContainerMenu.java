package net.minecraft.world.inventory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
// CraftBukkit start
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
// CraftBukkit end

public abstract class AbstractContainerMenu {

    public NonNullList<ItemStack> lastSlots = NonNullList.create();
    public List<Slot> slots = Lists.newArrayList();
    private final List<DataSlot> dataSlots = Lists.newArrayList();
    @Nullable
    private final MenuType<?> menuType;
    public final int containerId;
    private int quickcraftType = -1;
    private int quickcraftStatus;
    private final Set<Slot> quickcraftSlots = Sets.newHashSet();
    private final List<ContainerListener> containerListeners = Lists.newArrayList();
    private final Set<Player> unSynchedPlayers = Sets.newHashSet();

    // CraftBukkit start
    public boolean checkReachable = true;
    public abstract InventoryView getBukkitView();
    public void transferTo(AbstractContainerMenu other, org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        InventoryView source = this.getBukkitView(), destination = other.getBukkitView();
        ((CraftInventory) source.getTopInventory()).getInventory().onClose(player);
        ((CraftInventory) source.getBottomInventory()).getInventory().onClose(player);
        ((CraftInventory) destination.getTopInventory()).getInventory().onOpen(player);
        ((CraftInventory) destination.getBottomInventory()).getInventory().onOpen(player);
    }
    private Component title;
    public final Component getTitle() {
        Preconditions.checkState(this.title != null, "Title not set");
        return this.title;
    }
    public final void setTitle(Component title) {
        Preconditions.checkState(this.title == null, "Title already set");
        this.title = title;
    }
    // CraftBukkit end

    protected AbstractContainerMenu(@Nullable MenuType<?> containers, int i) {
        this.menuType = containers;
        this.containerId = i;
    }

    protected static boolean stillValid(ContainerLevelAccess containeraccess, Player entityhuman, Block block) {
        return (Boolean) containeraccess.evaluate((world, blockposition) -> {
            return !world.getType(blockposition).is(block) ? false : entityhuman.distanceToSqr((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D) <= 64.0D;
        }, true);
    }

    public MenuType<?> getType() {
        if (this.menuType == null) {
            throw new UnsupportedOperationException("Unable to construct this menu by type");
        } else {
            return this.menuType;
        }
    }

    protected static void checkContainerSize(Container iinventory, int i) {
        int j = iinventory.getContainerSize();

        if (j < i) {
            throw new IllegalArgumentException("Container size " + j + " is smaller than expected " + i);
        }
    }

    protected static void checkContainerDataCount(ContainerData icontainerproperties, int i) {
        int j = icontainerproperties.getCount();

        if (j < i) {
            throw new IllegalArgumentException("Container data count " + j + " is smaller than expected " + i);
        }
    }

    protected Slot addSlot(Slot slot) {
        slot.index = this.slots.size();
        this.slots.add(slot);
        this.lastSlots.add(ItemStack.EMPTY);
        return slot;
    }

    protected DataSlot addDataSlot(DataSlot containerproperty) {
        this.dataSlots.add(containerproperty);
        return containerproperty;
    }

    protected void addDataSlots(ContainerData icontainerproperties) {
        for (int i = 0; i < icontainerproperties.getCount(); ++i) {
            this.addDataSlot(DataSlot.forContainer(icontainerproperties, i));
        }

    }

    public void addSlotListener(ContainerListener icrafting) {
        if (!this.containerListeners.contains(icrafting)) {
            this.containerListeners.add(icrafting);
            icrafting.refreshContainer(this, this.getItems());
            this.broadcastChanges();
        }
    }

    public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> nonnulllist = NonNullList.create();

        for (int i = 0; i < this.slots.size(); ++i) {
            nonnulllist.add(((Slot) this.slots.get(i)).getItem());
        }

        return nonnulllist;
    }

    public void broadcastChanges() {
        int i;

        for (i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();
            ItemStack itemstack1 = (ItemStack) this.lastSlots.get(i);

            if (!ItemStack.matches(itemstack1, itemstack)) {
                ItemStack itemstack2 = itemstack.copy();

                this.lastSlots.set(i, itemstack2);
                Iterator iterator = this.containerListeners.iterator();

                while (iterator.hasNext()) {
                    ContainerListener icrafting = (ContainerListener) iterator.next();

                    icrafting.slotChanged(this, i, itemstack2);
                }
            }
        }

        for (i = 0; i < this.dataSlots.size(); ++i) {
            DataSlot containerproperty = (DataSlot) this.dataSlots.get(i);

            if (containerproperty.checkAndClearUpdateFlag()) {
                Iterator iterator1 = this.containerListeners.iterator();

                while (iterator1.hasNext()) {
                    ContainerListener icrafting1 = (ContainerListener) iterator1.next();

                    icrafting1.setContainerData(this, i, containerproperty.get());
                }
            }
        }

    }

    public boolean clickMenuButton(Player entityhuman, int i) {
        return false;
    }

    public Slot getSlot(int i) {
        return (Slot) this.slots.get(i);
    }

    public ItemStack quickMoveStack(Player entityhuman, int i) {
        Slot slot = (Slot) this.slots.get(i);

        return slot != null ? slot.getItem() : ItemStack.EMPTY;
    }

    public ItemStack clicked(int i, int j, ClickType inventoryclicktype, Player entityhuman) {
        try {
            return this.doClick(i, j, inventoryclicktype, entityhuman);
        } catch (Exception exception) {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Container click");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Click info");

            crashreportsystemdetails.setDetail("Menu Type", () -> {
                return this.menuType != null ? Registry.MENU.getKey(this.menuType).toString() : "<no type>";
            });
            crashreportsystemdetails.setDetail("Menu Class", () -> {
                return this.getClass().getCanonicalName();
            });
            crashreportsystemdetails.setDetail("Slot Count", (Object) this.slots.size());
            crashreportsystemdetails.setDetail("Slot", (Object) i);
            crashreportsystemdetails.setDetail("Button", (Object) j);
            crashreportsystemdetails.setDetail("Type", (Object) inventoryclicktype);
            throw new ReportedException(crashreport);
        }
    }

    private ItemStack doClick(int i, int j, ClickType inventoryclicktype, Player entityhuman) {
        ItemStack itemstack = ItemStack.EMPTY;
        Inventory playerinventory = entityhuman.inventory;
        ItemStack itemstack1;
        ItemStack itemstack2;
        int k;
        int l;

        if (inventoryclicktype == ClickType.QUICK_CRAFT) {
            int i1 = this.quickcraftStatus;

            this.quickcraftStatus = getQuickcraftHeader(j);
            if ((i1 != 1 || this.quickcraftStatus != 2) && i1 != this.quickcraftStatus) {
                this.resetQuickCraft();
            } else if (playerinventory.getCarried().isEmpty()) {
                this.resetQuickCraft();
            } else if (this.quickcraftStatus == 0) {
                this.quickcraftType = getQuickcraftType(j);
                if (isValidQuickcraftType(this.quickcraftType, entityhuman)) {
                    this.quickcraftStatus = 1;
                    this.quickcraftSlots.clear();
                } else {
                    this.resetQuickCraft();
                }
            } else if (this.quickcraftStatus == 1) {
                Slot slot = (Slot) this.slots.get(i);

                itemstack1 = playerinventory.getCarried();
                if (slot != null && canItemQuickReplace(slot, itemstack1, true) && slot.mayPlace(itemstack1) && (this.quickcraftType == 2 || itemstack1.getCount() > this.quickcraftSlots.size()) && this.canDragTo(slot)) {
                    this.quickcraftSlots.add(slot);
                }
            } else if (this.quickcraftStatus == 2) {
                if (!this.quickcraftSlots.isEmpty()) {
                    itemstack2 = playerinventory.getCarried().copy();
                    k = playerinventory.getCarried().getCount();
                    Iterator iterator = this.quickcraftSlots.iterator();

                    Map<Integer, ItemStack> draggedSlots = new HashMap<Integer, ItemStack>(); // CraftBukkit - Store slots from drag in map (raw slot id -> new stack)
                    while (iterator.hasNext()) {
                        Slot slot1 = (Slot) iterator.next();
                        ItemStack itemstack3 = playerinventory.getCarried();

                        if (slot1 != null && canItemQuickReplace(slot1, itemstack3, true) && slot1.mayPlace(itemstack3) && (this.quickcraftType == 2 || itemstack3.getCount() >= this.quickcraftSlots.size()) && this.canDragTo(slot1)) {
                            ItemStack itemstack4 = itemstack2.copy();
                            int j1 = slot1.hasItem() ? slot1.getItem().getCount() : 0;

                            getQuickCraftSlotCount(this.quickcraftSlots, this.quickcraftType, itemstack4, j1);
                            l = Math.min(itemstack4.getMaxStackSize(), slot1.getMaxStackSize(itemstack4));
                            if (itemstack4.getCount() > l) {
                                itemstack4.setCount(l);
                            }

                            k -= itemstack4.getCount() - j1;
                            // slot1.set(itemstack4);
                            draggedSlots.put(slot1.index, itemstack4); // CraftBukkit - Put in map instead of setting
                        }
                    }

                    // CraftBukkit start - InventoryDragEvent
                    InventoryView view = getBukkitView();
                    org.bukkit.inventory.ItemStack newcursor = CraftItemStack.asCraftMirror(itemstack2);
                    newcursor.setAmount(k);
                    Map<Integer, org.bukkit.inventory.ItemStack> eventmap = new HashMap<Integer, org.bukkit.inventory.ItemStack>();
                    for (Map.Entry<Integer, ItemStack> ditem : draggedSlots.entrySet()) {
                        eventmap.put(ditem.getKey(), CraftItemStack.asBukkitCopy(ditem.getValue()));
                    }

                    // It's essential that we set the cursor to the new value here to prevent item duplication if a plugin closes the inventory.
                    ItemStack oldCursor = playerinventory.getCarried();
                    playerinventory.setCarried(CraftItemStack.asNMSCopy(newcursor));

                    InventoryDragEvent event = new InventoryDragEvent(view, (newcursor.getType() != org.bukkit.Material.AIR ? newcursor : null), CraftItemStack.asBukkitCopy(oldCursor), this.quickcraftType == 1, eventmap);
                    entityhuman.level.getServerOH().getPluginManager().callEvent(event);

                    // Whether or not a change was made to the inventory that requires an update.
                    boolean needsUpdate = event.getResult() != Result.DEFAULT;

                    if (event.getResult() != Result.DENY) {
                        for (Map.Entry<Integer, ItemStack> dslot : draggedSlots.entrySet()) {
                            view.setItem(dslot.getKey(), CraftItemStack.asBukkitCopy(dslot.getValue()));
                        }
                        // The only time the carried item will be set to null is if the inventory is closed by the server.
                        // If the inventory is closed by the server, then the cursor items are dropped.  This is why we change the cursor early.
                        if (playerinventory.getCarried() != null) {
                            playerinventory.setCarried(CraftItemStack.asNMSCopy(event.getCursor()));
                            needsUpdate = true;
                        }
                    } else {
                        playerinventory.setCarried(oldCursor);
                    }

                    if (needsUpdate && entityhuman instanceof ServerPlayer) {
                        ((ServerPlayer) entityhuman).refreshContainer(this);
                    }
                    // CraftBukkit end
                }

                this.resetQuickCraft();
            } else {
                this.resetQuickCraft();
            }
        } else if (this.quickcraftStatus != 0) {
            this.resetQuickCraft();
        } else {
            Slot slot2;
            int k1;

            if ((inventoryclicktype == ClickType.PICKUP || inventoryclicktype == ClickType.QUICK_MOVE) && (j == 0 || j == 1)) {
                if (i == -999) {
                    if (!playerinventory.getCarried().isEmpty()) {
                        if (j == 0) {
                            // CraftBukkit start
                            ItemStack carried = playerinventory.getCarried();
                            playerinventory.setCarried(ItemStack.EMPTY);
                            entityhuman.drop(carried, true);
                            // CraftBukkit start
                        }

                        if (j == 1) {
                            entityhuman.drop(playerinventory.getCarried().split(1), true);
                        }
                    }
                } else if (inventoryclicktype == ClickType.QUICK_MOVE) {
                    if (i < 0) {
                        return ItemStack.EMPTY;
                    }

                    slot2 = (Slot) this.slots.get(i);
                    if (slot2 == null || !slot2.mayPickup(entityhuman)) {
                        return ItemStack.EMPTY;
                    }

                    for (itemstack2 = this.quickMoveStack(entityhuman, i); !itemstack2.isEmpty() && ItemStack.isSame(slot2.getItem(), itemstack2); itemstack2 = this.quickMoveStack(entityhuman, i)) {
                        itemstack = itemstack2.copy();
                    }
                } else {
                    if (i < 0) {
                        return ItemStack.EMPTY;
                    }

                    slot2 = (Slot) this.slots.get(i);
                    if (slot2 != null) {
                        itemstack2 = slot2.getItem();
                        itemstack1 = playerinventory.getCarried();
                        if (!itemstack2.isEmpty()) {
                            itemstack = itemstack2.copy();
                        }

                        if (itemstack2.isEmpty()) {
                            if (!itemstack1.isEmpty() && slot2.mayPlace(itemstack1)) {
                                k1 = j == 0 ? itemstack1.getCount() : 1;
                                if (k1 > slot2.getMaxStackSize(itemstack1)) {
                                    k1 = slot2.getMaxStackSize(itemstack1);
                                }

                                slot2.set(itemstack1.split(k1));
                            }
                        } else if (slot2.mayPickup(entityhuman)) {
                            if (itemstack1.isEmpty()) {
                                if (itemstack2.isEmpty()) {
                                    slot2.set(ItemStack.EMPTY);
                                    playerinventory.setCarried(ItemStack.EMPTY);
                                } else {
                                    k1 = j == 0 ? itemstack2.getCount() : (itemstack2.getCount() + 1) / 2;
                                    playerinventory.setCarried(slot2.remove(k1));
                                    if (itemstack2.isEmpty()) {
                                        slot2.set(ItemStack.EMPTY);
                                    }

                                    slot2.onTake(entityhuman, playerinventory.getCarried());
                                }
                            } else if (slot2.mayPlace(itemstack1)) {
                                if (consideredTheSameItem(itemstack2, itemstack1)) {
                                    k1 = j == 0 ? itemstack1.getCount() : 1;
                                    if (k1 > slot2.getMaxStackSize(itemstack1) - itemstack2.getCount()) {
                                        k1 = slot2.getMaxStackSize(itemstack1) - itemstack2.getCount();
                                    }

                                    if (k1 > itemstack1.getMaxStackSize() - itemstack2.getCount()) {
                                        k1 = itemstack1.getMaxStackSize() - itemstack2.getCount();
                                    }

                                    itemstack1.shrink(k1);
                                    itemstack2.grow(k1);
                                } else if (itemstack1.getCount() <= slot2.getMaxStackSize(itemstack1)) {
                                    slot2.set(itemstack1);
                                    playerinventory.setCarried(itemstack2);
                                }
                            } else if (itemstack1.getMaxStackSize() > 1 && consideredTheSameItem(itemstack2, itemstack1) && !itemstack2.isEmpty()) {
                                k1 = itemstack2.getCount();
                                if (k1 + itemstack1.getCount() <= itemstack1.getMaxStackSize()) {
                                    itemstack1.grow(k1);
                                    itemstack2 = slot2.remove(k1);
                                    if (itemstack2.isEmpty()) {
                                        slot2.set(ItemStack.EMPTY);
                                    }

                                    slot2.onTake(entityhuman, playerinventory.getCarried());
                                }
                            }
                        }

                        slot2.setChanged();
                        // CraftBukkit start - Make sure the client has the right slot contents
                        if (entityhuman instanceof ServerPlayer && slot2.getMaxStackSize() != 64) {
                            ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundContainerSetSlotPacket(this.containerId, slot2.index, slot2.getItem()));
                            // Updating a crafting inventory makes the client reset the result slot, have to send it again
                            if (this.getBukkitView().getType() == InventoryType.WORKBENCH || this.getBukkitView().getType() == InventoryType.CRAFTING) {
                                ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundContainerSetSlotPacket(this.containerId, 0, this.getSlot(0).getItem()));
                            }
                        }
                        // CraftBukkit end
                    }
                }
            } else if (inventoryclicktype == ClickType.SWAP) {
                slot2 = (Slot) this.slots.get(i);
                itemstack2 = playerinventory.getItem(j);
                itemstack1 = slot2.getItem();
                if (!itemstack2.isEmpty() || !itemstack1.isEmpty()) {
                    if (itemstack2.isEmpty()) {
                        if (slot2.mayPickup(entityhuman)) {
                            playerinventory.setItem(j, itemstack1);
                            slot2.onSwapCraft(itemstack1.getCount());
                            slot2.set(ItemStack.EMPTY);
                            slot2.onTake(entityhuman, itemstack1);
                        }
                    } else if (itemstack1.isEmpty()) {
                        if (slot2.mayPlace(itemstack2)) {
                            k1 = slot2.getMaxStackSize(itemstack2);
                            if (itemstack2.getCount() > k1) {
                                slot2.set(itemstack2.split(k1));
                            } else {
                                slot2.set(itemstack2);
                                playerinventory.setItem(j, ItemStack.EMPTY);
                            }
                        }
                    } else if (slot2.mayPickup(entityhuman) && slot2.mayPlace(itemstack2)) {
                        k1 = slot2.getMaxStackSize(itemstack2);
                        if (itemstack2.getCount() > k1) {
                            slot2.set(itemstack2.split(k1));
                            slot2.onTake(entityhuman, itemstack1);
                            if (!playerinventory.add(itemstack1)) {
                                entityhuman.drop(itemstack1, true);
                            }
                        } else {
                            slot2.set(itemstack2);
                            playerinventory.setItem(j, itemstack1);
                            slot2.onTake(entityhuman, itemstack1);
                        }
                    }
                }
            } else if (inventoryclicktype == ClickType.CLONE && entityhuman.abilities.instabuild && playerinventory.getCarried().isEmpty() && i >= 0) {
                slot2 = (Slot) this.slots.get(i);
                if (slot2 != null && slot2.hasItem()) {
                    itemstack2 = slot2.getItem().copy();
                    itemstack2.setCount(itemstack2.getMaxStackSize());
                    playerinventory.setCarried(itemstack2);
                }
            } else if (inventoryclicktype == ClickType.THROW && playerinventory.getCarried().isEmpty() && i >= 0) {
                slot2 = (Slot) this.slots.get(i);
                if (slot2 != null && slot2.hasItem() && slot2.mayPickup(entityhuman)) {
                    itemstack2 = slot2.remove(j == 0 ? 1 : slot2.getItem().getCount());
                    slot2.onTake(entityhuman, itemstack2);
                    entityhuman.drop(itemstack2, true);
                }
            } else if (inventoryclicktype == ClickType.PICKUP_ALL && i >= 0) {
                slot2 = (Slot) this.slots.get(i);
                itemstack2 = playerinventory.getCarried();
                if (!itemstack2.isEmpty() && (slot2 == null || !slot2.hasItem() || !slot2.mayPickup(entityhuman))) {
                    k = j == 0 ? 0 : this.slots.size() - 1;
                    k1 = j == 0 ? 1 : -1;

                    for (int l1 = 0; l1 < 2; ++l1) {
                        for (int i2 = k; i2 >= 0 && i2 < this.slots.size() && itemstack2.getCount() < itemstack2.getMaxStackSize(); i2 += k1) {
                            Slot slot3 = (Slot) this.slots.get(i2);

                            if (slot3.hasItem() && canItemQuickReplace(slot3, itemstack2, true) && slot3.mayPickup(entityhuman) && this.canTakeItemForPickAll(itemstack2, slot3)) {
                                ItemStack itemstack5 = slot3.getItem();

                                if (l1 != 0 || itemstack5.getCount() != itemstack5.getMaxStackSize()) {
                                    l = Math.min(itemstack2.getMaxStackSize() - itemstack2.getCount(), itemstack5.getCount());
                                    ItemStack itemstack6 = slot3.remove(l);

                                    itemstack2.grow(l);
                                    if (itemstack6.isEmpty()) {
                                        slot3.set(ItemStack.EMPTY);
                                    }

                                    slot3.onTake(entityhuman, itemstack6);
                                }
                            }
                        }
                    }
                }

                this.broadcastChanges();
            }
        }

        return itemstack;
    }

    public static boolean consideredTheSameItem(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.getItem() == itemstack1.getItem() && ItemStack.tagMatches(itemstack, itemstack1);
    }

    public boolean canTakeItemForPickAll(ItemStack itemstack, Slot slot) {
        return true;
    }

    public void removed(Player entityhuman) {
        Inventory playerinventory = entityhuman.inventory;

        if (!playerinventory.getCarried().isEmpty()) {
            // CraftBukkit start - SPIGOT-4556
            ItemStack carried = playerinventory.getCarried();
            playerinventory.setCarried(ItemStack.EMPTY);
            entityhuman.drop(carried, false);
            // CraftBukkit end
        }

    }

    protected void clearContainer(Player entityhuman, Level world, Container iinventory) {
        int i;

        if (entityhuman.isAlive() && (!(entityhuman instanceof ServerPlayer) || !((ServerPlayer) entityhuman).hasDisconnected())) {
            for (i = 0; i < iinventory.getContainerSize(); ++i) {
                entityhuman.inventory.placeItemBackInInventory(world, iinventory.removeItemNoUpdate(i));
            }

        } else {
            for (i = 0; i < iinventory.getContainerSize(); ++i) {
                entityhuman.drop(iinventory.removeItemNoUpdate(i), false);
            }

        }
    }

    public void slotsChanged(Container iinventory) {
        this.broadcastChanges();
    }

    public void setItem(int i, ItemStack itemstack) {
        this.getSlot(i).set(itemstack);
    }

    public void setData(int i, int j) {
        ((DataSlot) this.dataSlots.get(i)).set(j);
    }

    public boolean isSynched(Player entityhuman) {
        return !this.unSynchedPlayers.contains(entityhuman);
    }

    public void setSynched(Player entityhuman, boolean flag) {
        if (flag) {
            this.unSynchedPlayers.remove(entityhuman);
        } else {
            this.unSynchedPlayers.add(entityhuman);
        }

    }

    public abstract boolean stillValid(Player entityhuman);

    protected boolean moveItemStackTo(ItemStack itemstack, int i, int j, boolean flag) {
        boolean flag1 = false;
        int k = i;

        if (flag) {
            k = j - 1;
        }

        Slot slot;
        ItemStack itemstack1;

        if (itemstack.isStackable()) {
            while (!itemstack.isEmpty()) {
                if (flag) {
                    if (k < i) {
                        break;
                    }
                } else if (k >= j) {
                    break;
                }

                slot = (Slot) this.slots.get(k);
                itemstack1 = slot.getItem();
                if (!itemstack1.isEmpty() && consideredTheSameItem(itemstack, itemstack1)) {
                    int l = itemstack1.getCount() + itemstack.getCount();

                    if (l <= itemstack.getMaxStackSize()) {
                        itemstack.setCount(0);
                        itemstack1.setCount(l);
                        slot.setChanged();
                        flag1 = true;
                    } else if (itemstack1.getCount() < itemstack.getMaxStackSize()) {
                        itemstack.shrink(itemstack.getMaxStackSize() - itemstack1.getCount());
                        itemstack1.setCount(itemstack.getMaxStackSize());
                        slot.setChanged();
                        flag1 = true;
                    }
                }

                if (flag) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        if (!itemstack.isEmpty()) {
            if (flag) {
                k = j - 1;
            } else {
                k = i;
            }

            while (true) {
                if (flag) {
                    if (k < i) {
                        break;
                    }
                } else if (k >= j) {
                    break;
                }

                slot = (Slot) this.slots.get(k);
                itemstack1 = slot.getItem();
                if (itemstack1.isEmpty() && slot.mayPlace(itemstack)) {
                    if (itemstack.getCount() > slot.getMaxStackSize()) {
                        slot.set(itemstack.split(slot.getMaxStackSize()));
                    } else {
                        slot.set(itemstack.split(itemstack.getCount()));
                    }

                    slot.setChanged();
                    flag1 = true;
                    break;
                }

                if (flag) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        return flag1;
    }

    public static int getQuickcraftType(int i) {
        return i >> 2 & 3;
    }

    public static int getQuickcraftHeader(int i) {
        return i & 3;
    }

    public static boolean isValidQuickcraftType(int i, Player entityhuman) {
        return i == 0 ? true : (i == 1 ? true : i == 2 && entityhuman.abilities.instabuild);
    }

    protected void resetQuickCraft() {
        this.quickcraftStatus = 0;
        this.quickcraftSlots.clear();
    }

    public static boolean canItemQuickReplace(@Nullable Slot slot, ItemStack itemstack, boolean flag) {
        boolean flag1 = slot == null || !slot.hasItem();

        return !flag1 && itemstack.sameItem(slot.getItem()) && ItemStack.tagMatches(slot.getItem(), itemstack) ? slot.getItem().getCount() + (flag ? 0 : itemstack.getCount()) <= itemstack.getMaxStackSize() : flag1;
    }

    public static void getQuickCraftSlotCount(Set<Slot> set, int i, ItemStack itemstack, int j) {
        switch (i) {
            case 0:
                itemstack.setCount(Mth.floor((float) itemstack.getCount() / (float) set.size()));
                break;
            case 1:
                itemstack.setCount(1);
                break;
            case 2:
                itemstack.setCount(itemstack.getItem().getMaxStackSize());
        }

        itemstack.grow(j);
    }

    public boolean canDragTo(Slot slot) {
        return true;
    }

    public static int getRedstoneSignalFromBlockEntity(@Nullable BlockEntity tileentity) {
        return tileentity instanceof Container ? getRedstoneSignalFromContainer((Container) tileentity) : 0;
    }

    public static int getRedstoneSignalFromContainer(@Nullable Container iinventory) {
        if (iinventory == null) {
            return 0;
        } else {
            int i = 0;
            float f = 0.0F;

            for (int j = 0; j < iinventory.getContainerSize(); ++j) {
                ItemStack itemstack = iinventory.getItem(j);

                if (!itemstack.isEmpty()) {
                    f += (float) itemstack.getCount() / (float) Math.min(iinventory.getMaxStackSize(), itemstack.getMaxStackSize());
                    ++i;
                }
            }

            f /= (float) iinventory.getContainerSize();
            return Mth.floor(f * 14.0F) + (i > 0 ? 1 : 0);
        }
    }
}
