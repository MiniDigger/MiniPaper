package net.minecraft.world.level.block;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public abstract class ButtonBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    protected static final VoxelShape CEILING_AABB_X = Block.box(6.0D, 14.0D, 5.0D, 10.0D, 16.0D, 11.0D);
    protected static final VoxelShape CEILING_AABB_Z = Block.box(5.0D, 14.0D, 6.0D, 11.0D, 16.0D, 10.0D);
    protected static final VoxelShape FLOOR_AABB_X = Block.box(6.0D, 0.0D, 5.0D, 10.0D, 2.0D, 11.0D);
    protected static final VoxelShape FLOOR_AABB_Z = Block.box(5.0D, 0.0D, 6.0D, 11.0D, 2.0D, 10.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(5.0D, 6.0D, 14.0D, 11.0D, 10.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0D, 6.0D, 0.0D, 11.0D, 10.0D, 2.0D);
    protected static final VoxelShape WEST_AABB = Block.box(14.0D, 6.0D, 5.0D, 16.0D, 10.0D, 11.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 6.0D, 5.0D, 2.0D, 10.0D, 11.0D);
    protected static final VoxelShape PRESSED_CEILING_AABB_X = Block.box(6.0D, 15.0D, 5.0D, 10.0D, 16.0D, 11.0D);
    protected static final VoxelShape PRESSED_CEILING_AABB_Z = Block.box(5.0D, 15.0D, 6.0D, 11.0D, 16.0D, 10.0D);
    protected static final VoxelShape PRESSED_FLOOR_AABB_X = Block.box(6.0D, 0.0D, 5.0D, 10.0D, 1.0D, 11.0D);
    protected static final VoxelShape PRESSED_FLOOR_AABB_Z = Block.box(5.0D, 0.0D, 6.0D, 11.0D, 1.0D, 10.0D);
    protected static final VoxelShape PRESSED_NORTH_AABB = Block.box(5.0D, 6.0D, 15.0D, 11.0D, 10.0D, 16.0D);
    protected static final VoxelShape PRESSED_SOUTH_AABB = Block.box(5.0D, 6.0D, 0.0D, 11.0D, 10.0D, 1.0D);
    protected static final VoxelShape PRESSED_WEST_AABB = Block.box(15.0D, 6.0D, 5.0D, 16.0D, 10.0D, 11.0D);
    protected static final VoxelShape PRESSED_EAST_AABB = Block.box(0.0D, 6.0D, 5.0D, 1.0D, 10.0D, 11.0D);
    private final boolean sensitive;

    protected ButtonBlock(boolean flag, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ButtonBlock.FACING, Direction.NORTH)).setValue(ButtonBlock.POWERED, false)).setValue(ButtonBlock.FACE, AttachFace.WALL));
        this.sensitive = flag;
    }

    private int getPressDuration() {
        return this.sensitive ? 30 : 20;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        Direction enumdirection = (Direction) iblockdata.getValue(ButtonBlock.FACING);
        boolean flag = (Boolean) iblockdata.getValue(ButtonBlock.POWERED);

        switch ((AttachFace) iblockdata.getValue(ButtonBlock.FACE)) {
            case FLOOR:
                if (enumdirection.getAxis() == Direction.Axis.X) {
                    return flag ? ButtonBlock.PRESSED_FLOOR_AABB_X : ButtonBlock.FLOOR_AABB_X;
                }

                return flag ? ButtonBlock.PRESSED_FLOOR_AABB_Z : ButtonBlock.FLOOR_AABB_Z;
            case WALL:
                switch (enumdirection) {
                    case EAST:
                        return flag ? ButtonBlock.PRESSED_EAST_AABB : ButtonBlock.EAST_AABB;
                    case WEST:
                        return flag ? ButtonBlock.PRESSED_WEST_AABB : ButtonBlock.WEST_AABB;
                    case SOUTH:
                        return flag ? ButtonBlock.PRESSED_SOUTH_AABB : ButtonBlock.SOUTH_AABB;
                    case NORTH:
                    default:
                        return flag ? ButtonBlock.PRESSED_NORTH_AABB : ButtonBlock.NORTH_AABB;
                }
            case CEILING:
            default:
                return enumdirection.getAxis() == Direction.Axis.X ? (flag ? ButtonBlock.PRESSED_CEILING_AABB_X : ButtonBlock.CEILING_AABB_X) : (flag ? ButtonBlock.PRESSED_CEILING_AABB_Z : ButtonBlock.CEILING_AABB_Z);
        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if ((Boolean) iblockdata.getValue(ButtonBlock.POWERED)) {
            return InteractionResult.CONSUME;
        } else {
            // CraftBukkit start
            boolean powered = ((Boolean) iblockdata.getValue(POWERED));
            org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getServerOH().getPluginManager().callEvent(eventRedstone);

            if ((eventRedstone.getNewCurrent() > 0) != (!powered)) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end
            this.press(iblockdata, world, blockposition);
            this.playSound(entityhuman, world, blockposition, true);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    public void press(BlockState iblockdata, Level world, BlockPos blockposition) {
        world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ButtonBlock.POWERED, true), 3);
        this.updateNeighbours(iblockdata, world, blockposition);
        world.getBlockTickList().scheduleTick(blockposition, this, this.getPressDuration());
    }

    protected void playSound(@Nullable Player entityhuman, LevelAccessor generatoraccess, BlockPos blockposition, boolean flag) {
        generatoraccess.playSound(flag ? entityhuman : null, blockposition, this.getSound(flag), SoundSource.BLOCKS, 0.3F, flag ? 0.6F : 0.5F);
    }

    protected abstract SoundEvent getSound(boolean flag);

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            if ((Boolean) iblockdata.getValue(ButtonBlock.POWERED)) {
                this.updateNeighbours(iblockdata, world, blockposition);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(ButtonBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(ButtonBlock.POWERED) && getConnectedDirection(iblockdata) == enumdirection ? 15 : 0;
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Boolean) iblockdata.getValue(ButtonBlock.POWERED)) {
            if (this.sensitive) {
                this.checkPressed(iblockdata, (Level) worldserver, blockposition);
            } else {
                // CraftBukkit start
                org.bukkit.block.Block block = worldserver.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

                BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, 15, 0);
                worldserver.getServerOH().getPluginManager().callEvent(eventRedstone);

                if (eventRedstone.getNewCurrent() > 0) {
                    return;
                }
                // CraftBukkit end
                worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ButtonBlock.POWERED, false), 3);
                this.updateNeighbours(iblockdata, (Level) worldserver, blockposition);
                this.playSound((Player) null, worldserver, blockposition, false);
            }

        }
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!world.isClientSide && this.sensitive && !(Boolean) iblockdata.getValue(ButtonBlock.POWERED)) {
            this.checkPressed(iblockdata, world, blockposition);
        }
    }

    private void checkPressed(BlockState iblockdata, Level world, BlockPos blockposition) {
        List<? extends Entity> list = world.getEntitiesOfClass(AbstractArrow.class, iblockdata.getShape(world, blockposition).bounds().move(blockposition));
        boolean flag = !list.isEmpty();
        boolean flag1 = (Boolean) iblockdata.getValue(ButtonBlock.POWERED);

        // CraftBukkit start - Call interact event when arrows turn on wooden buttons
        if (flag1 != flag && flag) {
            org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            boolean allowed = false;

            // If all of the events are cancelled block the button press, else allow
            for (Object object : list) {
                if (object != null) {
                    EntityInteractEvent event = new EntityInteractEvent(((Entity) object).getBukkitEntity(), block);
                    world.getServerOH().getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) {
                return;
            }
        }
        // CraftBukkit end

        if (flag != flag1) {
            // CraftBukkit start
            boolean powered = flag1;
            org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getServerOH().getPluginManager().callEvent(eventRedstone);

            if ((flag && eventRedstone.getNewCurrent() <= 0) || (!flag && eventRedstone.getNewCurrent() > 0)) {
                return;
            }
            // CraftBukkit end
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ButtonBlock.POWERED, flag), 3);
            this.updateNeighbours(iblockdata, world, blockposition);
            this.playSound((Player) null, world, blockposition, flag);
        }

        if (flag) {
            world.getBlockTickList().scheduleTick(new BlockPos(blockposition), this, this.getPressDuration());
        }

    }

    private void updateNeighbours(BlockState iblockdata, Level world, BlockPos blockposition) {
        world.updateNeighborsAt(blockposition, this);
        world.updateNeighborsAt(blockposition.relative(getConnectedDirection(iblockdata).getOpposite()), this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ButtonBlock.FACING, ButtonBlock.POWERED, ButtonBlock.FACE);
    }
}
