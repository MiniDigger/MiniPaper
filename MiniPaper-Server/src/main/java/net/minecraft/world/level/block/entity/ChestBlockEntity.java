package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class ChestBlockEntity extends RandomizableContainerBlockEntity implements TickableBlockEntity {

    private NonNullList<ItemStack> items;
    protected float openness;
    protected float oOpenness;
    protected int openCount;
    private int tickInterval;

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

    protected ChestBlockEntity(BlockEntityType<?> tileentitytypes) {
        super(tileentitytypes);
        this.items = NonNullList.withSize(27, ItemStack.EMPTY);
    }

    public ChestBlockEntity() {
        this(BlockEntityType.CHEST);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("container.chest");
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbttagcompound)) {
            ContainerHelper.loadAllItems(nbttagcompound, this.items);
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (!this.trySaveLootTable(nbttagcompound)) {
            ContainerHelper.saveAllItems(nbttagcompound, this.items);
        }

        return nbttagcompound;
    }

    @Override
    public void tick() {
        int i = this.worldPosition.getX();
        int j = this.worldPosition.getY();
        int k = this.worldPosition.getZ();

        ++this.tickInterval;
        this.openCount = getOpenCount(this.level, this, this.tickInterval, i, j, k, this.openCount);
        this.oOpenness = this.openness;
        float f = 0.1F;

        if (this.openCount > 0 && this.openness == 0.0F) {
            this.playSound(SoundEvents.CHEST_OPEN);
        }

        if (this.openCount == 0 && this.openness > 0.0F || this.openCount > 0 && this.openness < 1.0F) {
            float f1 = this.openness;

            if (this.openCount > 0) {
                this.openness += 0.1F;
            } else {
                this.openness -= 0.1F;
            }

            if (this.openness > 1.0F) {
                this.openness = 1.0F;
            }

            float f2 = 0.5F;

            if (this.openness < 0.5F && f1 >= 0.5F) {
                this.playSound(SoundEvents.CHEST_CLOSE);
            }

            if (this.openness < 0.0F) {
                this.openness = 0.0F;
            }
        }

    }

    public static int getOpenCount(Level world, BaseContainerBlockEntity tileentitycontainer, int i, int j, int k, int l, int i1) {
        if (!world.isClientSide && i1 != 0 && (i + j + k + l) % 200 == 0) {
            i1 = getOpenCount(world, tileentitycontainer, j, k, l);
        }

        return i1;
    }

    public static int getOpenCount(Level world, BaseContainerBlockEntity tileentitycontainer, int i, int j, int k) {
        int l = 0;
        float f = 5.0F;
        List<Player> list = world.getEntitiesOfClass(Player.class, new AABB((double) ((float) i - 5.0F), (double) ((float) j - 5.0F), (double) ((float) k - 5.0F), (double) ((float) (i + 1) + 5.0F), (double) ((float) (j + 1) + 5.0F), (double) ((float) (k + 1) + 5.0F)));
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Player entityhuman = (Player) iterator.next();

            if (entityhuman.containerMenu instanceof ChestMenu) {
                Container iinventory = ((ChestMenu) entityhuman.containerMenu).getContainer();

                if (iinventory == tileentitycontainer || iinventory instanceof CompoundContainer && ((CompoundContainer) iinventory).contains((Container) tileentitycontainer)) {
                    ++l;
                }
            }
        }

        return l;
    }

    private void playSound(SoundEvent soundeffect) {
        ChestType blockpropertychesttype = (ChestType) this.getBlock().getValue(ChestBlock.TYPE);

        if (blockpropertychesttype != ChestType.LEFT) {
            double d0 = (double) this.worldPosition.getX() + 0.5D;
            double d1 = (double) this.worldPosition.getY() + 0.5D;
            double d2 = (double) this.worldPosition.getZ() + 0.5D;

            if (blockpropertychesttype == ChestType.RIGHT) {
                Direction enumdirection = ChestBlock.getConnectedDirection(this.getBlock());

                d0 += (double) enumdirection.getStepX() * 0.5D;
                d2 += (double) enumdirection.getStepZ() * 0.5D;
            }

            this.level.playSound((Player) null, d0, d1, d2, soundeffect, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public boolean triggerEvent(int i, int j) {
        if (i == 1) {
            this.openCount = j;
            return true;
        } else {
            return super.triggerEvent(i, j);
        }
    }

    @Override
    public void startOpen(Player entityhuman) {
        if (!entityhuman.isSpectator()) {
            if (this.openCount < 0) {
                this.openCount = 0;
            }
            int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added

            ++this.openCount;
            if (this.level == null) return; // CraftBukkit

            // CraftBukkit start - Call redstone event
            if (this.getBlock().getBlock() == Blocks.TRAPPED_CHEST) {
                int newPower = Math.max(0, Math.min(15, this.openCount));

                if (oldPower != newPower) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, worldPosition, oldPower, newPower);
                }
            }
            // CraftBukkit end
            this.signalOpenCount();
        }

    }

    @Override
    public void stopOpen(Player entityhuman) {
        if (!entityhuman.isSpectator()) {
            int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
            --this.openCount;

            // CraftBukkit start - Call redstone event
            if (this.getBlock().getBlock() == Blocks.TRAPPED_CHEST) {
                int newPower = Math.max(0, Math.min(15, this.openCount));

                if (oldPower != newPower) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, worldPosition, oldPower, newPower);
                }
            }
            // CraftBukkit end
            this.signalOpenCount();
        }

    }

    protected void signalOpenCount() {
        Block block = this.getBlock().getBlock();

        if (block instanceof ChestBlock) {
            this.level.blockEvent(this.worldPosition, block, 1, this.openCount);
            this.level.updateNeighborsAt(this.worldPosition, block);
        }

    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.items = nonnulllist;
    }

    public static int getOpenCount(BlockGetter iblockaccess, BlockPos blockposition) {
        BlockState iblockdata = iblockaccess.getType(blockposition);

        if (iblockdata.getBlock().isEntityBlock()) {
            BlockEntity tileentity = iblockaccess.getBlockEntity(blockposition);

            if (tileentity instanceof ChestBlockEntity) {
                return ((ChestBlockEntity) tileentity).openCount;
            }
        }

        return 0;
    }

    public static void swapContents(ChestBlockEntity tileentitychest, ChestBlockEntity tileentitychest1) {
        NonNullList<ItemStack> nonnulllist = tileentitychest.getItems();

        tileentitychest.setItems(tileentitychest1.getItems());
        tileentitychest1.setItems(nonnulllist);
    }

    @Override
    protected AbstractContainerMenu createMenu(int i, Inventory playerinventory) {
        return ChestMenu.threeRows(i, playerinventory, this);
    }

    // CraftBukkit start
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }
    // CraftBukkit end
}
