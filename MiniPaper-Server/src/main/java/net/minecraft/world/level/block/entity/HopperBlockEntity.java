package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
// CraftBukkit end

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper, TickableBlockEntity {

    private NonNullList<ItemStack> items;
    private int cooldownTime;
    private long tickedGameTime;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.items;
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

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    public HopperBlockEntity() {
        super(BlockEntityType.HOPPER);
        this.items = NonNullList.withSize(5, ItemStack.EMPTY);
        this.cooldownTime = -1;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbttagcompound)) {
            ContainerHelper.loadAllItems(nbttagcompound, this.items);
        }

        this.cooldownTime = nbttagcompound.getInt("TransferCooldown");
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (!this.trySaveLootTable(nbttagcompound)) {
            ContainerHelper.saveAllItems(nbttagcompound, this.items);
        }

        nbttagcompound.putInt("TransferCooldown", this.cooldownTime);
        return nbttagcompound;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        this.unpackLootTable((Player) null);
        return ContainerHelper.removeItem(this.getItems(), i, j);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.unpackLootTable((Player) null);
        this.getItems().set(i, itemstack);
        if (itemstack.getCount() > this.getMaxStackSize()) {
            itemstack.setCount(this.getMaxStackSize());
        }

    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("container.hopper");
    }

    @Override
    public void tick() {
        if (this.level != null && !this.level.isClientSide) {
            --this.cooldownTime;
            this.tickedGameTime = this.level.getGameTime();
            if (!this.isOnCooldown()) {
                this.setCooldown(0);
                // Spigot start
                boolean result = this.tryMoveItems(() -> {
                    return suckInItems((Hopper) this);
                });
                if (!result && this.level.spigotConfig.hopperCheck > 1) {
                    this.setCooldown(this.level.spigotConfig.hopperCheck);
                }
                // Spigot end
            }

        }
    }

    private boolean tryMoveItems(Supplier<Boolean> supplier) {
        if (this.level != null && !this.level.isClientSide) {
            if (!this.isOnCooldown() && (Boolean) this.getBlock().getValue(HopperBlock.ENABLED)) {
                boolean flag = false;

                if (!this.isEmpty()) {
                    flag = this.ejectItems();
                }

                if (!this.inventoryFull()) {
                    flag |= (Boolean) supplier.get();
                }

                if (flag) {
                    this.setCooldown(level.spigotConfig.hopperTransfer); // Spigot
                    this.setChanged();
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean inventoryFull() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (!itemstack.isEmpty() && itemstack.getCount() == itemstack.getMaxStackSize());

        return false;
    }

    private boolean ejectItems() {
        Container iinventory = this.getAttachedContainer();

        if (iinventory == null) {
            return false;
        } else {
            Direction enumdirection = ((Direction) this.getBlock().getValue(HopperBlock.FACING)).getOpposite();

            if (this.isFullContainer(iinventory, enumdirection)) {
                return false;
            } else {
                for (int i = 0; i < this.getContainerSize(); ++i) {
                    if (!this.getItem(i).isEmpty()) {
                        ItemStack itemstack = this.getItem(i).copy();
                        // ItemStack itemstack1 = addItem(this, iinventory, this.splitStack(i, 1), enumdirection);

                        // CraftBukkit start - Call event when pushing items into other inventories
                        CraftItemStack oitemstack = CraftItemStack.asCraftMirror(this.removeItem(i, level.spigotConfig.hopperAmount)); // Spigot

                        Inventory destinationInventory;
                        // Have to special case large chests as they work oddly
                        if (iinventory instanceof CompoundContainer) {
                            destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
                        } else {
                            destinationInventory = iinventory.getOwner().getInventory();
                        }

                        InventoryMoveItemEvent event = new InventoryMoveItemEvent(this.getOwner().getInventory(), oitemstack.clone(), destinationInventory, true);
                        this.getLevel().getServerOH().getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            this.setItem(i, itemstack);
                            this.setCooldown(level.spigotConfig.hopperTransfer); // Spigot
                            return false;
                        }
                        int origCount = event.getItem().getAmount(); // Spigot
                        ItemStack itemstack1 = addItem(this, iinventory, CraftItemStack.asNMSCopy(event.getItem()), enumdirection);
                        // CraftBukkit end

                        if (itemstack1.isEmpty()) {
                            iinventory.setChanged();
                            return true;
                        }

                        itemstack.shrink(origCount - itemstack1.getCount()); // Spigot
                        this.setItem(i, itemstack);
                    }
                }

                return false;
            }
        }
    }

    private static IntStream getSlots(Container iinventory, Direction enumdirection) {
        return iinventory instanceof WorldlyContainer ? IntStream.of(((WorldlyContainer) iinventory).getSlotsForFace(enumdirection)) : IntStream.range(0, iinventory.getContainerSize());
    }

    private boolean isFullContainer(Container iinventory, Direction enumdirection) {
        return getSlots(iinventory, enumdirection).allMatch((i) -> {
            ItemStack itemstack = iinventory.getItem(i);

            return itemstack.getCount() >= itemstack.getMaxStackSize();
        });
    }

    private static boolean isEmptyContainer(Container iinventory, Direction enumdirection) {
        return getSlots(iinventory, enumdirection).allMatch((i) -> {
            return iinventory.getItem(i).isEmpty();
        });
    }

    public static boolean suckInItems(Hopper ihopper) {
        Container iinventory = getSourceContainer(ihopper);

        if (iinventory != null) {
            Direction enumdirection = Direction.DOWN;

            return isEmptyContainer(iinventory, enumdirection) ? false : getSlots(iinventory, enumdirection).anyMatch((i) -> {
                return tryTakeInItemFromSlot(ihopper, iinventory, i, enumdirection);
            });
        } else {
            Iterator iterator = getItemsAtAndAbove(ihopper).iterator();

            ItemEntity entityitem;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                entityitem = (ItemEntity) iterator.next();
            } while (!addItem((Container) ihopper, entityitem));

            return true;
        }
    }

    private static boolean tryTakeInItemFromSlot(Hopper ihopper, Container iinventory, int i, Direction enumdirection) {
        ItemStack itemstack = iinventory.getItem(i);

        if (!itemstack.isEmpty() && canTakeItemFromContainer(iinventory, itemstack, i, enumdirection)) {
            ItemStack itemstack1 = itemstack.copy();
            // ItemStack itemstack2 = addItem(iinventory, ihopper, iinventory.splitStack(i, 1), (EnumDirection) null);
            // CraftBukkit start - Call event on collection of items from inventories into the hopper
            CraftItemStack oitemstack = CraftItemStack.asCraftMirror(iinventory.removeItem(i, ihopper.getLevel().spigotConfig.hopperAmount)); // Spigot

            Inventory sourceInventory;
            // Have to special case large chests as they work oddly
            if (iinventory instanceof CompoundContainer) {
                sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
            } else {
                sourceInventory = iinventory.getOwner().getInventory();
            }

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(sourceInventory, oitemstack.clone(), ihopper.getOwner().getInventory(), false);

            ihopper.getLevel().getServerOH().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                iinventory.setItem(i, itemstack1);

                if (ihopper instanceof HopperBlockEntity) {
                    ((HopperBlockEntity) ihopper).setCooldown(ihopper.getLevel().spigotConfig.hopperTransfer); // Spigot
                } else if (ihopper instanceof MinecartHopper) {
                    ((MinecartHopper) ihopper).setCooldown(ihopper.getLevel().spigotConfig.hopperTransfer / 2); // Spigot
                }
                return false;
            }
            int origCount = event.getItem().getAmount(); // Spigot
            ItemStack itemstack2 = addItem(iinventory, ihopper, CraftItemStack.asNMSCopy(event.getItem()), null);
            // CraftBukkit end

            if (itemstack2.isEmpty()) {
                iinventory.setChanged();
                return true;
            }

            itemstack1.shrink(origCount - itemstack2.getCount()); // Spigot
            iinventory.setItem(i, itemstack1);
        }

        return false;
    }

    public static boolean addItem(Container iinventory, ItemEntity entityitem) {
        boolean flag = false;
        // CraftBukkit start
        InventoryPickupItemEvent event = new InventoryPickupItemEvent(iinventory.getOwner().getInventory(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
        entityitem.level.getServerOH().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        // CraftBukkit end
        ItemStack itemstack = entityitem.getItem().copy();
        ItemStack itemstack1 = addItem((Container) null, iinventory, itemstack, (Direction) null);

        if (itemstack1.isEmpty()) {
            flag = true;
            entityitem.remove();
        } else {
            entityitem.setItem(itemstack1);
        }

        return flag;
    }

    public static ItemStack addItem(@Nullable Container iinventory, Container iinventory1, ItemStack itemstack, @Nullable Direction enumdirection) {
        if (iinventory1 instanceof WorldlyContainer && enumdirection != null) {
            WorldlyContainer iworldinventory = (WorldlyContainer) iinventory1;
            int[] aint = iworldinventory.getSlotsForFace(enumdirection);

            for (int i = 0; i < aint.length && !itemstack.isEmpty(); ++i) {
                itemstack = tryMoveInItem(iinventory, iinventory1, itemstack, aint[i], enumdirection);
            }
        } else {
            int j = iinventory1.getContainerSize();

            for (int k = 0; k < j && !itemstack.isEmpty(); ++k) {
                itemstack = tryMoveInItem(iinventory, iinventory1, itemstack, k, enumdirection);
            }
        }

        return itemstack;
    }

    private static boolean canPlaceItemInContainer(Container iinventory, ItemStack itemstack, int i, @Nullable Direction enumdirection) {
        return !iinventory.canPlaceItem(i, itemstack) ? false : !(iinventory instanceof WorldlyContainer) || ((WorldlyContainer) iinventory).canPlaceItemThroughFace(i, itemstack, enumdirection);
    }

    private static boolean canTakeItemFromContainer(Container iinventory, ItemStack itemstack, int i, Direction enumdirection) {
        return !(iinventory instanceof WorldlyContainer) || ((WorldlyContainer) iinventory).canTakeItemThroughFace(i, itemstack, enumdirection);
    }

    private static ItemStack tryMoveInItem(@Nullable Container iinventory, Container iinventory1, ItemStack itemstack, int i, @Nullable Direction enumdirection) {
        ItemStack itemstack1 = iinventory1.getItem(i);

        if (canPlaceItemInContainer(iinventory1, itemstack, i, enumdirection)) {
            boolean flag = false;
            boolean flag1 = iinventory1.isEmpty();

            if (itemstack1.isEmpty()) {
                iinventory1.setItem(i, itemstack);
                itemstack = ItemStack.EMPTY;
                flag = true;
            } else if (canMergeItems(itemstack1, itemstack)) {
                int j = itemstack.getMaxStackSize() - itemstack1.getCount();
                int k = Math.min(itemstack.getCount(), j);

                itemstack.shrink(k);
                itemstack1.grow(k);
                flag = k > 0;
            }

            if (flag) {
                if (flag1 && iinventory1 instanceof HopperBlockEntity) {
                    HopperBlockEntity tileentityhopper = (HopperBlockEntity) iinventory1;

                    if (!tileentityhopper.isOnCustomCooldown()) {
                        byte b0 = 0;

                        if (iinventory instanceof HopperBlockEntity) {
                            HopperBlockEntity tileentityhopper1 = (HopperBlockEntity) iinventory;

                            if (tileentityhopper.tickedGameTime >= tileentityhopper1.tickedGameTime) {
                                b0 = 1;
                            }
                        }

                        tileentityhopper.setCooldown(tileentityhopper.level.spigotConfig.hopperTransfer - b0); // Spigot
                    }
                }

                iinventory1.setChanged();
            }
        }

        return itemstack;
    }

    @Nullable
    private Container getAttachedContainer() {
        Direction enumdirection = (Direction) this.getBlock().getValue(HopperBlock.FACING);

        return getContainerAt(this.getLevel(), this.worldPosition.relative(enumdirection));
    }

    @Nullable
    public static Container getSourceContainer(Hopper ihopper) {
        return getContainerAt(ihopper.getLevel(), ihopper.getLevelX(), ihopper.getLevelY() + 1.0D, ihopper.getLevelZ());
    }

    public static List<ItemEntity> getItemsAtAndAbove(Hopper ihopper) {
        return (List) ihopper.getSuckShape().toAabbs().stream().flatMap((axisalignedbb) -> {
            return ihopper.getLevel().getEntitiesOfClass(ItemEntity.class, axisalignedbb.move(ihopper.getLevelX() - 0.5D, ihopper.getLevelY() - 0.5D, ihopper.getLevelZ() - 0.5D), EntitySelector.ENTITY_STILL_ALIVE).stream();
        }).collect(Collectors.toList());
    }

    @Nullable
    public static Container getContainerAt(Level world, BlockPos blockposition) {
        return getContainerAt(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D);
    }

    @Nullable
    public static Container getContainerAt(Level world, double d0, double d1, double d2) {
        Object object = null;
        BlockPos blockposition = new BlockPos(d0, d1, d2);
        if ( !world.hasChunkAt( blockposition ) ) return null; // Spigot
        BlockState iblockdata = world.getType(blockposition);
        Block block = iblockdata.getBlock();

        if (block instanceof WorldlyContainerHolder) {
            object = ((WorldlyContainerHolder) block).getContainer(iblockdata, world, blockposition);
        } else if (block.isEntityBlock()) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof Container) {
                object = (Container) tileentity;
                if (object instanceof ChestBlockEntity && block instanceof ChestBlock) {
                    object = ChestBlock.getInventory((ChestBlock) block, iblockdata, world, blockposition, true);
                }
            }
        }

        if (object == null) {
            List<Entity> list = world.getEntities((Entity) null, new AABB(d0 - 0.5D, d1 - 0.5D, d2 - 0.5D, d0 + 0.5D, d1 + 0.5D, d2 + 0.5D), EntitySelector.CONTAINER_ENTITY_SELECTOR);

            if (!list.isEmpty()) {
                object = (Container) list.get(world.random.nextInt(list.size()));
            }
        }

        return (Container) object;
    }

    private static boolean canMergeItems(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.getItem() != itemstack1.getItem() ? false : (itemstack.getDamageValue() != itemstack1.getDamageValue() ? false : (itemstack.getCount() > itemstack.getMaxStackSize() ? false : ItemStack.tagMatches(itemstack, itemstack1)));
    }

    @Override
    public double getLevelX() {
        return (double) this.worldPosition.getX() + 0.5D;
    }

    @Override
    public double getLevelY() {
        return (double) this.worldPosition.getY() + 0.5D;
    }

    @Override
    public double getLevelZ() {
        return (double) this.worldPosition.getZ() + 0.5D;
    }

    private void setCooldown(int i) {
        this.cooldownTime = i;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.items = nonnulllist;
    }

    public void entityInside(Entity entity) {
        if (entity instanceof ItemEntity) {
            BlockPos blockposition = this.getBlockPos();

            if (Shapes.joinIsNotEmpty(Shapes.create(entity.getBoundingBox().move((double) (-blockposition.getX()), (double) (-blockposition.getY()), (double) (-blockposition.getZ()))), this.getSuckShape(), BooleanOp.AND)) {
                this.tryMoveItems(() -> {
                    return addItem((Container) this, (ItemEntity) entity);
                });
            }
        }

    }

    @Override
    protected AbstractContainerMenu createMenu(int i, net.minecraft.world.entity.player.Inventory playerinventory) {
        return new HopperMenu(i, playerinventory, this);
    }
}
