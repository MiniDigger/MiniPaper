package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class Inventory implements Container, Nameable {

    public final NonNullList<ItemStack> items;
    public final NonNullList<ItemStack> armor;
    public final NonNullList<ItemStack> offhand;
    private final List<NonNullList<ItemStack>> compartments;
    public int selected;
    public final Player player;
    private ItemStack carried;
    private int timesChanged;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        List<ItemStack> combined = new ArrayList<ItemStack>(items.size() + armor.size() + offhand.size());
        for (List<net.minecraft.world.item.ItemStack> sub : this.compartments) {
            combined.addAll(sub);
        }

        return combined;
    }

    public List<ItemStack> getArmorContents() {
        return this.armor;
    }

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.player.getBukkitEntity();
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }

    @Override
    public Location getLocation() {
        return player.getBukkitEntity().getLocation();
    }
    // CraftBukkit end

    public Inventory(Player entityhuman) {
        this.items = NonNullList.withSize(36, ItemStack.EMPTY);
        this.armor = NonNullList.withSize(4, ItemStack.EMPTY);
        this.offhand = NonNullList.withSize(1, ItemStack.EMPTY);
        this.compartments = ImmutableList.of(this.items, this.armor, this.offhand);
        this.carried = ItemStack.EMPTY;
        this.player = entityhuman;
    }

    public ItemStack getSelected() {
        return isHotbarSlot(this.selected) ? (ItemStack) this.items.get(this.selected) : ItemStack.EMPTY;
    }

    public static int getSelectionSize() {
        return 9;
    }

    private boolean hasRemainingSpaceForItem(ItemStack itemstack, ItemStack itemstack1) {
        return !itemstack.isEmpty() && this.isSameItem(itemstack, itemstack1) && itemstack.isStackable() && itemstack.getCount() < itemstack.getMaxStackSize() && itemstack.getCount() < this.getMaxStackSize();
    }

    private boolean isSameItem(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.getItem() == itemstack1.getItem() && ItemStack.tagMatches(itemstack, itemstack1);
    }

    // CraftBukkit start - Watch method above! :D
    public int canHold(ItemStack itemstack) {
        int remains = itemstack.getCount();
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.getItem(i);
            if (itemstack1.isEmpty()) return itemstack.getCount();

            if (this.hasRemainingSpaceForItem(itemstack1, itemstack)) {
                remains -= (itemstack1.getMaxStackSize() < this.getMaxStackSize() ? itemstack1.getMaxStackSize() : this.getMaxStackSize()) - itemstack1.getCount();
            }
            if (remains <= 0) return itemstack.getCount();
        }
        ItemStack offhandItemStack = this.getItem(this.items.size() + this.armor.size());
        if (this.hasRemainingSpaceForItem(offhandItemStack, itemstack)) {
            remains -= (offhandItemStack.getMaxStackSize() < this.getMaxStackSize() ? offhandItemStack.getMaxStackSize() : this.getMaxStackSize()) - offhandItemStack.getCount();
        }
        if (remains <= 0) return itemstack.getCount();

        return itemstack.getCount() - remains;
    }
    // CraftBukkit end

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public void pickSlot(int i) {
        this.selected = this.getSuitableHotbarSlot();
        ItemStack itemstack = (ItemStack) this.items.get(this.selected);

        this.items.set(this.selected, this.items.get(i));
        this.items.set(i, itemstack);
    }

    public static boolean isHotbarSlot(int i) {
        return i >= 0 && i < 9;
    }

    public int findSlotMatchingUnusedItem(ItemStack itemstack) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = (ItemStack) this.items.get(i);

            if (!((ItemStack) this.items.get(i)).isEmpty() && this.isSameItem(itemstack, (ItemStack) this.items.get(i)) && !((ItemStack) this.items.get(i)).isDamaged() && !itemstack1.isEnchanted() && !itemstack1.hasCustomHoverName()) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        int i;
        int j;

        for (j = 0; j < 9; ++j) {
            i = (this.selected + j) % 9;
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                return i;
            }
        }

        for (j = 0; j < 9; ++j) {
            i = (this.selected + j) % 9;
            if (!((ItemStack) this.items.get(i)).isEnchanted()) {
                return i;
            }
        }

        return this.selected;
    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> predicate, int i, Container iinventory) {
        byte b0 = 0;
        boolean flag = i == 0;
        int j = b0 + ContainerHelper.clearOrCountMatchingItems((Container) this, predicate, i - b0, flag);

        j += ContainerHelper.clearOrCountMatchingItems(iinventory, predicate, i - j, flag);
        j += ContainerHelper.clearOrCountMatchingItems(this.carried, predicate, i - j, flag);
        if (this.carried.isEmpty()) {
            this.carried = ItemStack.EMPTY;
        }

        return j;
    }

    private int addResource(ItemStack itemstack) {
        int i = this.getSlotWithRemainingSpace(itemstack);

        if (i == -1) {
            i = this.getFreeSlot();
        }

        return i == -1 ? itemstack.getCount() : this.addResource(i, itemstack);
    }

    private int addResource(int i, ItemStack itemstack) {
        Item item = itemstack.getItem();
        int j = itemstack.getCount();
        ItemStack itemstack1 = this.getItem(i);

        if (itemstack1.isEmpty()) {
            itemstack1 = new ItemStack(item, 0);
            if (itemstack.hasTag()) {
                itemstack1.setTag(itemstack.getTag().copy());
            }

            this.setItem(i, itemstack1);
        }

        int k = j;

        if (j > itemstack1.getMaxStackSize() - itemstack1.getCount()) {
            k = itemstack1.getMaxStackSize() - itemstack1.getCount();
        }

        if (k > this.getMaxStackSize() - itemstack1.getCount()) {
            k = this.getMaxStackSize() - itemstack1.getCount();
        }

        if (k == 0) {
            return j;
        } else {
            j -= k;
            itemstack1.grow(k);
            itemstack1.setPopTime(5);
            return j;
        }
    }

    public int getSlotWithRemainingSpace(ItemStack itemstack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), itemstack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), itemstack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); ++i) {
                if (this.hasRemainingSpaceForItem((ItemStack) this.items.get(i), itemstack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            NonNullList<ItemStack> nonnulllist = (NonNullList) iterator.next();

            for (int i = 0; i < nonnulllist.size(); ++i) {
                if (!((ItemStack) nonnulllist.get(i)).isEmpty()) {
                    ((ItemStack) nonnulllist.get(i)).inventoryTick(this.player.level, this.player, i, this.selected == i);
                }
            }
        }

    }

    public boolean add(ItemStack itemstack) {
        return this.add(-1, itemstack);
    }

    public boolean add(int i, ItemStack itemstack) {
        if (itemstack.isEmpty()) {
            return false;
        } else {
            try {
                if (itemstack.isDamaged()) {
                    if (i == -1) {
                        i = this.getFreeSlot();
                    }

                    if (i >= 0) {
                        this.items.set(i, itemstack.copy());
                        ((ItemStack) this.items.get(i)).setPopTime(5);
                        itemstack.setCount(0);
                        return true;
                    } else if (this.player.abilities.instabuild) {
                        itemstack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int j;

                    do {
                        j = itemstack.getCount();
                        if (i == -1) {
                            itemstack.setCount(this.addResource(itemstack));
                        } else {
                            itemstack.setCount(this.addResource(i, itemstack));
                        }
                    } while (!itemstack.isEmpty() && itemstack.getCount() < j);

                    if (itemstack.getCount() == j && this.player.abilities.instabuild) {
                        itemstack.setCount(0);
                        return true;
                    } else {
                        return itemstack.getCount() < j;
                    }
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Adding item to inventory");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Item being added");

                crashreportsystemdetails.setDetail("Item ID", (Object) Item.getId(itemstack.getItem()));
                crashreportsystemdetails.setDetail("Item data", (Object) itemstack.getDamageValue());
                crashreportsystemdetails.setDetail("Item name", () -> {
                    return itemstack.getHoverName().getString();
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    public void placeItemBackInInventory(Level world, ItemStack itemstack) {
        if (!world.isClientSide) {
            while (!itemstack.isEmpty()) {
                int i = this.getSlotWithRemainingSpace(itemstack);

                if (i == -1) {
                    i = this.getFreeSlot();
                }

                if (i == -1) {
                    this.player.drop(itemstack, false);
                    break;
                }

                int j = itemstack.getMaxStackSize() - this.getItem(i).getCount();

                if (this.add(i, itemstack.split(j))) {
                    ((ServerPlayer) this.player).connection.sendPacket(new ClientboundContainerSetSlotPacket(-2, i, this.getItem(i)));
                }
            }

        }
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        List<ItemStack> list = null;

        NonNullList nonnulllist;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); i -= nonnulllist.size()) {
            nonnulllist = (NonNullList) iterator.next();
            if (i < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }
        }

        return list != null && !((ItemStack) list.get(i)).isEmpty() ? ContainerHelper.removeItem(list, i, j) : ItemStack.EMPTY;
    }

    public void removeItem(ItemStack itemstack) {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            NonNullList<ItemStack> nonnulllist = (NonNullList) iterator.next();

            for (int i = 0; i < nonnulllist.size(); ++i) {
                if (nonnulllist.get(i) == itemstack) {
                    nonnulllist.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }

    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        NonNullList<ItemStack> nonnulllist = null;

        NonNullList nonnulllist1;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); i -= nonnulllist1.size()) {
            nonnulllist1 = (NonNullList) iterator.next();
            if (i < nonnulllist1.size()) {
                nonnulllist = nonnulllist1;
                break;
            }
        }

        if (nonnulllist != null && !((ItemStack) nonnulllist.get(i)).isEmpty()) {
            ItemStack itemstack = (ItemStack) nonnulllist.get(i);

            nonnulllist.set(i, ItemStack.EMPTY);
            return itemstack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        NonNullList<ItemStack> nonnulllist = null;

        NonNullList nonnulllist1;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); i -= nonnulllist1.size()) {
            nonnulllist1 = (NonNullList) iterator.next();
            if (i < nonnulllist1.size()) {
                nonnulllist = nonnulllist1;
                break;
            }
        }

        if (nonnulllist != null) {
            nonnulllist.set(i, itemstack);
        }

    }

    public float getDestroySpeed(BlockState iblockdata) {
        return ((ItemStack) this.items.get(this.selected)).getDestroySpeed(iblockdata);
    }

    public ListTag save(ListTag nbttaglist) {
        CompoundTag nbttagcompound;
        int i;

        for (i = 0; i < this.items.size(); ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty()) {
                nbttagcompound = new CompoundTag();
                nbttagcompound.putByte("Slot", (byte) i);
                ((ItemStack) this.items.get(i)).save(nbttagcompound);
                nbttaglist.add(nbttagcompound);
            }
        }

        for (i = 0; i < this.armor.size(); ++i) {
            if (!((ItemStack) this.armor.get(i)).isEmpty()) {
                nbttagcompound = new CompoundTag();
                nbttagcompound.putByte("Slot", (byte) (i + 100));
                ((ItemStack) this.armor.get(i)).save(nbttagcompound);
                nbttaglist.add(nbttagcompound);
            }
        }

        for (i = 0; i < this.offhand.size(); ++i) {
            if (!((ItemStack) this.offhand.get(i)).isEmpty()) {
                nbttagcompound = new CompoundTag();
                nbttagcompound.putByte("Slot", (byte) (i + 150));
                ((ItemStack) this.offhand.get(i)).save(nbttagcompound);
                nbttaglist.add(nbttagcompound);
            }
        }

        return nbttaglist;
    }

    public void load(ListTag nbttaglist) {
        this.items.clear();
        this.armor.clear();
        this.offhand.clear();

        for (int i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound = nbttaglist.getCompound(i);
            int j = nbttagcompound.getByte("Slot") & 255;
            ItemStack itemstack = ItemStack.of(nbttagcompound);

            if (!itemstack.isEmpty()) {
                if (j >= 0 && j < this.items.size()) {
                    this.items.set(j, itemstack);
                } else if (j >= 100 && j < this.armor.size() + 100) {
                    this.armor.set(j - 100, itemstack);
                } else if (j >= 150 && j < this.offhand.size() + 150) {
                    this.offhand.set(j - 150, itemstack);
                }
            }
        }

    }

    @Override
    public int getContainerSize() {
        return this.items.size() + this.armor.size() + this.offhand.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                iterator = this.armor.iterator();

                do {
                    if (!iterator.hasNext()) {
                        iterator = this.offhand.iterator();

                        do {
                            if (!iterator.hasNext()) {
                                return true;
                            }

                            itemstack = (ItemStack) iterator.next();
                        } while (itemstack.isEmpty());

                        return false;
                    }

                    itemstack = (ItemStack) iterator.next();
                } while (itemstack.isEmpty());

                return false;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getItem(int i) {
        List<ItemStack> list = null;

        NonNullList nonnulllist;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); i -= nonnulllist.size()) {
            nonnulllist = (NonNullList) iterator.next();
            if (i < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }
        }

        return list == null ? ItemStack.EMPTY : (ItemStack) list.get(i);
    }

    @Override
    public Component getName() {
        return new TranslatableComponent("container.inventory");
    }

    public void hurtArmor(DamageSource damagesource, float f) {
        if (f > 0.0F) {
            f /= 4.0F;
            if (f < 1.0F) {
                f = 1.0F;
            }

            for (int i = 0; i < this.armor.size(); ++i) {
                ItemStack itemstack = (ItemStack) this.armor.get(i);

                if ((!damagesource.isFire() || !itemstack.getItem().isFireResistant()) && itemstack.getItem() instanceof ArmorItem) {
                    int finalI = i; // CraftBukkit - decompile error
                    itemstack.hurtAndBreak((int) f, this.player, (entityhuman) -> {
                        entityhuman.broadcastBreakEvent(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, finalI)); // CraftBukkit - decompile error
                    });
                }
            }

        }
    }

    public void dropAll() {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();

            for (int i = 0; i < list.size(); ++i) {
                ItemStack itemstack = (ItemStack) list.get(i);

                if (!itemstack.isEmpty()) {
                    this.player.drop(itemstack, true, false);
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }

    }

    @Override
    public void setChanged() {
        ++this.timesChanged;
    }

    public void setCarried(ItemStack itemstack) {
        this.carried = itemstack;
    }

    public ItemStack getCarried() {
        // CraftBukkit start
        if (this.carried.isEmpty()) {
            this.setCarried(ItemStack.EMPTY);
        }
        // CraftBukkit end
        return this.carried;
    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.player.removed ? false : entityhuman.distanceToSqr((Entity) this.player) <= 64.0D;
    }

    public boolean contains(ItemStack itemstack) {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                ItemStack itemstack1 = (ItemStack) iterator1.next();

                if (!itemstack1.isEmpty() && itemstack1.sameItem(itemstack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void replaceWith(Inventory playerinventory) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, playerinventory.getItem(i));
        }

        this.selected = playerinventory.selected;
    }

    @Override
    public void clearContent() {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();

            list.clear();
        }

    }

    public void fillStackedContents(StackedContents autorecipestackmanager) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            autorecipestackmanager.accountSimpleStack(itemstack);
        }

    }
}
