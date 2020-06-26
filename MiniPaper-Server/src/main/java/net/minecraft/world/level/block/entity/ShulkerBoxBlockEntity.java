package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class ShulkerBoxBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, TickableBlockEntity {

    private static final int[] SLOTS = IntStream.range(0, 27).toArray();
    private NonNullList<ItemStack> itemStacks;
    private int openCount;
    private ShulkerBoxBlockEntity.AnimationStatus animationStatus;
    private float progress;
    private float progressOld;
    @Nullable
    private DyeColor color;
    private boolean loadColorFromBlock;

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

    public ShulkerBoxBlockEntity(@Nullable DyeColor enumcolor) {
        super(BlockEntityType.SHULKER_BOX);
        this.itemStacks = NonNullList.withSize(27, ItemStack.EMPTY);
        this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
        this.color = enumcolor;
    }

    public ShulkerBoxBlockEntity() {
        this((DyeColor) null);
        this.loadColorFromBlock = true;
    }

    @Override
    public void tick() {
        this.updateAnimation();
        if (this.animationStatus == ShulkerBoxBlockEntity.AnimationStatus.OPENING || this.animationStatus == ShulkerBoxBlockEntity.AnimationStatus.CLOSING) {
            this.moveCollidedEntities();
        }

    }

    protected void updateAnimation() {
        this.progressOld = this.progress;
        switch (this.animationStatus) {
            case CLOSED:
                this.progress = 0.0F;
                break;
            case OPENING:
                this.progress += 0.1F;
                if (this.progress >= 1.0F) {
                    this.moveCollidedEntities();
                    this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.OPENED;
                    this.progress = 1.0F;
                    this.doNeighborUpdates();
                }
                break;
            case CLOSING:
                this.progress -= 0.1F;
                if (this.progress <= 0.0F) {
                    this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
                    this.progress = 0.0F;
                    this.doNeighborUpdates();
                }
                break;
            case OPENED:
                this.progress = 1.0F;
        }

    }

    public ShulkerBoxBlockEntity.AnimationStatus getAnimationStatus() {
        return this.animationStatus;
    }

    public AABB getBoundingBox(BlockState iblockdata) {
        return this.getBoundingBox((Direction) iblockdata.getValue(ShulkerBoxBlock.FACING));
    }

    public AABB getBoundingBox(Direction enumdirection) {
        float f = this.getProgress(1.0F);

        return Shapes.block().bounds().expandTowards((double) (0.5F * f * (float) enumdirection.getStepX()), (double) (0.5F * f * (float) enumdirection.getStepY()), (double) (0.5F * f * (float) enumdirection.getStepZ()));
    }

    private AABB getTopBoundingBox(Direction enumdirection) {
        Direction enumdirection1 = enumdirection.getOpposite();

        return this.getBoundingBox(enumdirection).contract((double) enumdirection1.getStepX(), (double) enumdirection1.getStepY(), (double) enumdirection1.getStepZ());
    }

    private void moveCollidedEntities() {
        BlockState iblockdata = this.level.getType(this.getBlockPos());

        if (iblockdata.getBlock() instanceof ShulkerBoxBlock) {
            Direction enumdirection = (Direction) iblockdata.getValue(ShulkerBoxBlock.FACING);
            AABB axisalignedbb = this.getTopBoundingBox(enumdirection).move(this.worldPosition);
            List<Entity> list = this.level.getEntities((Entity) null, axisalignedbb);

            if (!list.isEmpty()) {
                for (int i = 0; i < list.size(); ++i) {
                    Entity entity = (Entity) list.get(i);

                    if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
                        double d0 = 0.0D;
                        double d1 = 0.0D;
                        double d2 = 0.0D;
                        AABB axisalignedbb1 = entity.getBoundingBox();

                        switch (enumdirection.getAxis()) {
                            case X:
                                if (enumdirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    d0 = axisalignedbb.maxX - axisalignedbb1.minX;
                                } else {
                                    d0 = axisalignedbb1.maxX - axisalignedbb.minX;
                                }

                                d0 += 0.01D;
                                break;
                            case Y:
                                if (enumdirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    d1 = axisalignedbb.maxY - axisalignedbb1.minY;
                                } else {
                                    d1 = axisalignedbb1.maxY - axisalignedbb.minY;
                                }

                                d1 += 0.01D;
                                break;
                            case Z:
                                if (enumdirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    d2 = axisalignedbb.maxZ - axisalignedbb1.minZ;
                                } else {
                                    d2 = axisalignedbb1.maxZ - axisalignedbb.minZ;
                                }

                                d2 += 0.01D;
                        }

                        entity.move(MoverType.SHULKER_BOX, new Vec3(d0 * (double) enumdirection.getStepX(), d1 * (double) enumdirection.getStepY(), d2 * (double) enumdirection.getStepZ()));
                    }
                }

            }
        }
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean triggerEvent(int i, int j) {
        if (i == 1) {
            this.openCount = j;
            if (j == 0) {
                this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSING;
                this.doNeighborUpdates();
            }

            if (j == 1) {
                this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.OPENING;
                this.doNeighborUpdates();
            }

            return true;
        } else {
            return super.triggerEvent(i, j);
        }
    }

    private void doNeighborUpdates() {
        this.getBlock().updateNeighbourShapes(this.getLevel(), this.getBlockPos(), 3);
    }

    @Override
    public void startOpen(Player entityhuman) {
        if (!entityhuman.isSpectator()) {
            if (this.openCount < 0) {
                this.openCount = 0;
            }

            ++this.openCount;
            this.level.blockEvent(this.worldPosition, this.getBlock().getBlock(), 1, this.openCount);
            if (this.openCount == 1) {
                this.level.playSound((Player) null, this.worldPosition, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }
        }

    }

    @Override
    public void stopOpen(Player entityhuman) {
        if (!entityhuman.isSpectator()) {
            --this.openCount;
            this.level.blockEvent(this.worldPosition, this.getBlock().getBlock(), 1, this.openCount);
            if (this.openCount <= 0) {
                this.level.playSound((Player) null, this.worldPosition, SoundEvents.SHULKER_BOX_CLOSE, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }
        }

    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("container.shulkerBox");
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.loadFromTag(nbttagcompound);
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        return this.saveToTag(nbttagcompound);
    }

    public void loadFromTag(CompoundTag nbttagcompound) {
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbttagcompound) && nbttagcompound.contains("Items", 9)) {
            ContainerHelper.loadAllItems(nbttagcompound, this.itemStacks);
        }

    }

    public CompoundTag saveToTag(CompoundTag nbttagcompound) {
        if (!this.trySaveLootTable(nbttagcompound)) {
            ContainerHelper.saveAllItems(nbttagcompound, this.itemStacks, false);
        }

        return nbttagcompound;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.itemStacks;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.itemStacks = nonnulllist;
    }

    @Override
    public int[] getSlotsForFace(Direction enumdirection) {
        return ShulkerBoxBlockEntity.SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable Direction enumdirection) {
        return !(Block.byItem(itemstack.getItem()) instanceof ShulkerBoxBlock);
    }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack itemstack, Direction enumdirection) {
        return true;
    }

    public float getProgress(float f) {
        return Mth.lerp(f, this.progressOld, this.progress);
    }

    @Override
    protected AbstractContainerMenu createMenu(int i, Inventory playerinventory) {
        return new ShulkerBoxMenu(i, playerinventory, this);
    }

    public boolean isClosed() {
        return this.animationStatus == ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
    }

    public static enum AnimationStatus {

        CLOSED, OPENING, OPENED, CLOSING;

        private AnimationStatus() {}
    }
}
