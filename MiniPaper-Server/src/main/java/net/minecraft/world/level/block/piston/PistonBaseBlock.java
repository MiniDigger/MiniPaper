package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import com.google.common.collect.ImmutableList;
import java.util.AbstractList;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
// CraftBukkit end

public class PistonBaseBlock extends DirectionalBlock {

    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 0.0D, 12.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(4.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 12.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 4.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape UP_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);
    protected static final VoxelShape DOWN_AABB = Block.box(0.0D, 4.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final boolean isSticky;

    public PistonBaseBlock(boolean flag, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PistonBaseBlock.FACING, Direction.NORTH)).setValue(PistonBaseBlock.EXTENDED, false));
        this.isSticky = flag;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        if ((Boolean) iblockdata.getValue(PistonBaseBlock.EXTENDED)) {
            switch ((Direction) iblockdata.getValue(PistonBaseBlock.FACING)) {
                case DOWN:
                    return PistonBaseBlock.DOWN_AABB;
                case UP:
                default:
                    return PistonBaseBlock.UP_AABB;
                case NORTH:
                    return PistonBaseBlock.NORTH_AABB;
                case SOUTH:
                    return PistonBaseBlock.SOUTH_AABB;
                case WEST:
                    return PistonBaseBlock.WEST_AABB;
                case EAST:
                    return PistonBaseBlock.EAST_AABB;
            }
        } else {
            return Shapes.block();
        }
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        if (!world.isClientSide) {
            this.checkIfExtend(world, blockposition, iblockdata);
        }

    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (!world.isClientSide) {
            this.checkIfExtend(world, blockposition, iblockdata);
        }

    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            if (!world.isClientSide && world.getBlockEntity(blockposition) == null) {
                this.checkIfExtend(world, blockposition, iblockdata);
            }

        }
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) ((BlockState) this.getBlockData().setValue(PistonBaseBlock.FACING, blockactioncontext.getNearestLookingDirection().getOpposite())).setValue(PistonBaseBlock.EXTENDED, false);
    }

    private void checkIfExtend(Level world, BlockPos blockposition, BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(PistonBaseBlock.FACING);
        boolean flag = this.getNeighborSignal(world, blockposition, enumdirection);

        if (flag && !(Boolean) iblockdata.getValue(PistonBaseBlock.EXTENDED)) {
            if ((new PistonStructureResolver(world, blockposition, enumdirection, true)).resolve()) {
                world.blockEvent(blockposition, this, 0, enumdirection.get3DDataValue());
            }
        } else if (!flag && (Boolean) iblockdata.getValue(PistonBaseBlock.EXTENDED)) {
            BlockPos blockposition1 = blockposition.relative(enumdirection, 2);
            BlockState iblockdata1 = world.getType(blockposition1);
            byte b0 = 1;

            if (iblockdata1.is(Blocks.MOVING_PISTON) && iblockdata1.getValue(PistonBaseBlock.FACING) == enumdirection) {
                BlockEntity tileentity = world.getBlockEntity(blockposition1);

                if (tileentity instanceof PistonMovingBlockEntity) {
                    PistonMovingBlockEntity tileentitypiston = (PistonMovingBlockEntity) tileentity;

                    if (tileentitypiston.isExtending() && (tileentitypiston.getProgress(0.0F) < 0.5F || world.getGameTime() == tileentitypiston.getLastTicked() || ((ServerLevel) world).isHandlingTick())) {
                        b0 = 2;
                    }
                }
            }

            // CraftBukkit start
            if (!this.isSticky) {
                org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                BlockPistonRetractEvent event = new BlockPistonRetractEvent(block, ImmutableList.<org.bukkit.block.Block>of(), CraftBlock.notchToBlockFace(enumdirection));
                world.getServerOH().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // PAIL: checkME - what happened to setTypeAndData?
            // CraftBukkit end
            world.blockEvent(blockposition, this, b0, enumdirection.get3DDataValue());
        }

    }

    private boolean getNeighborSignal(Level world, BlockPos blockposition, Direction enumdirection) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        int j;

        for (j = 0; j < i; ++j) {
            Direction enumdirection1 = aenumdirection[j];

            if (enumdirection1 != enumdirection && world.hasSignal(blockposition.relative(enumdirection1), enumdirection1)) {
                return true;
            }
        }

        if (world.hasSignal(blockposition, Direction.DOWN)) {
            return true;
        } else {
            BlockPos blockposition1 = blockposition.above();
            Direction[] aenumdirection1 = Direction.values();

            j = aenumdirection1.length;

            for (int k = 0; k < j; ++k) {
                Direction enumdirection2 = aenumdirection1[k];

                if (enumdirection2 != Direction.DOWN && world.hasSignal(blockposition1.relative(enumdirection2), enumdirection2)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public boolean triggerEvent(BlockState iblockdata, Level world, BlockPos blockposition, int i, int j) {
        Direction enumdirection = (Direction) iblockdata.getValue(PistonBaseBlock.FACING);

        if (!world.isClientSide) {
            boolean flag = this.getNeighborSignal(world, blockposition, enumdirection);

            if (flag && (i == 1 || i == 2)) {
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(PistonBaseBlock.EXTENDED, true), 2);
                return false;
            }

            if (!flag && i == 0) {
                return false;
            }
        }

        if (i == 0) {
            if (!this.moveBlocks(world, blockposition, enumdirection, true)) {
                return false;
            }

            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(PistonBaseBlock.EXTENDED, true), 67);
            world.playSound((Player) null, blockposition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, world.random.nextFloat() * 0.25F + 0.6F);
        } else if (i == 1 || i == 2) {
            BlockEntity tileentity = world.getBlockEntity(blockposition.relative(enumdirection));

            if (tileentity instanceof PistonMovingBlockEntity) {
                ((PistonMovingBlockEntity) tileentity).finalTick();
            }

            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.MOVING_PISTON.getBlockData().setValue(MovingPistonBlock.FACING, enumdirection)).setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);

            world.setTypeAndData(blockposition, iblockdata1, 20);
            world.setBlockEntity(blockposition, MovingPistonBlock.newMovingBlockEntity((BlockState) this.getBlockData().setValue(PistonBaseBlock.FACING, Direction.from3DDataValue(j & 7)), enumdirection, false, true));
            world.blockUpdated(blockposition, iblockdata1.getBlock());
            iblockdata1.updateNeighbourShapes(world, blockposition, 2);
            if (this.isSticky) {
                BlockPos blockposition1 = blockposition.offset(enumdirection.getStepX() * 2, enumdirection.getStepY() * 2, enumdirection.getStepZ() * 2);
                BlockState iblockdata2 = world.getType(blockposition1);
                boolean flag1 = false;

                if (iblockdata2.is(Blocks.MOVING_PISTON)) {
                    BlockEntity tileentity1 = world.getBlockEntity(blockposition1);

                    if (tileentity1 instanceof PistonMovingBlockEntity) {
                        PistonMovingBlockEntity tileentitypiston = (PistonMovingBlockEntity) tileentity1;

                        if (tileentitypiston.getDirection() == enumdirection && tileentitypiston.isExtending()) {
                            tileentitypiston.finalTick();
                            flag1 = true;
                        }
                    }
                }

                if (!flag1) {
                    if (i == 1 && !iblockdata2.isAir() && isPushable(iblockdata2, world, blockposition1, enumdirection.getOpposite(), false, enumdirection) && (iblockdata2.getPistonPushReaction() == PushReaction.NORMAL || iblockdata2.is(Blocks.PISTON) || iblockdata2.is(Blocks.STICKY_PISTON))) {
                        this.moveBlocks(world, blockposition, enumdirection, false);
                    } else {
                        world.removeBlock(blockposition.relative(enumdirection), false);
                    }
                }
            } else {
                world.removeBlock(blockposition.relative(enumdirection), false);
            }

            world.playSound((Player) null, blockposition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, world.random.nextFloat() * 0.15F + 0.6F);
        }

        return true;
    }

    public static boolean isPushable(BlockState iblockdata, Level world, BlockPos blockposition, Direction enumdirection, boolean flag, Direction enumdirection1) {
        if (!iblockdata.is(Blocks.OBSIDIAN) && !iblockdata.is(Blocks.CRYING_OBSIDIAN) && !iblockdata.is(Blocks.RESPAWN_ANCHOR)) {
            if (!world.getWorldBorder().isWithinBounds(blockposition)) {
                return false;
            } else if (blockposition.getY() >= 0 && (enumdirection != Direction.DOWN || blockposition.getY() != 0)) {
                if (blockposition.getY() <= world.getMaxBuildHeight() - 1 && (enumdirection != Direction.UP || blockposition.getY() != world.getMaxBuildHeight() - 1)) {
                    if (!iblockdata.is(Blocks.PISTON) && !iblockdata.is(Blocks.STICKY_PISTON)) {
                        if (iblockdata.getDestroySpeed(world, blockposition) == -1.0F) {
                            return false;
                        }

                        switch (iblockdata.getPistonPushReaction()) {
                            case BLOCK:
                                return false;
                            case DESTROY:
                                return flag;
                            case PUSH_ONLY:
                                return enumdirection == enumdirection1;
                        }
                    } else if ((Boolean) iblockdata.getValue(PistonBaseBlock.EXTENDED)) {
                        return false;
                    }

                    return !iblockdata.getBlock().isEntityBlock();
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean moveBlocks(Level world, BlockPos blockposition, Direction enumdirection, boolean flag) {
        BlockPos blockposition1 = blockposition.relative(enumdirection);

        if (!flag && world.getType(blockposition1).is(Blocks.PISTON_HEAD)) {
            world.setTypeAndData(blockposition1, Blocks.AIR.getBlockData(), 20);
        }

        PistonStructureResolver pistonextendschecker = new PistonStructureResolver(world, blockposition, enumdirection, flag);

        if (!pistonextendschecker.resolve()) {
            return false;
        } else {
            Map<BlockPos, BlockState> map = Maps.newHashMap();
            List<BlockPos> list = pistonextendschecker.getToPush();
            List<BlockState> list1 = Lists.newArrayList();

            for (int i = 0; i < list.size(); ++i) {
                BlockPos blockposition2 = (BlockPos) list.get(i);
                BlockState iblockdata = world.getType(blockposition2);

                list1.add(iblockdata);
                map.put(blockposition2, iblockdata);
            }

            List<BlockPos> list2 = pistonextendschecker.getToDestroy();
            BlockState[] aiblockdata = new BlockState[list.size() + list2.size()];
            Direction enumdirection1 = flag ? enumdirection : enumdirection.getOpposite();
            int j = 0;
            // CraftBukkit start
            final org.bukkit.block.Block bblock = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

            final List<BlockPos> moved = pistonextendschecker.getToPush();
            final List<BlockPos> broken = pistonextendschecker.getToDestroy();

            List<org.bukkit.block.Block> blocks = new AbstractList<org.bukkit.block.Block>() {

                @Override
                public int size() {
                    return moved.size() + broken.size();
                }

                @Override
                public org.bukkit.block.Block get(int index) {
                    if (index >= size() || index < 0) {
                        throw new ArrayIndexOutOfBoundsException(index);
                    }
                    BlockPos pos = (BlockPos) (index < moved.size() ? moved.get(index) : broken.get(index - moved.size()));
                    return bblock.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                }
            };
            org.bukkit.event.block.BlockPistonEvent event;
            if (flag) {
                event = new BlockPistonExtendEvent(bblock, blocks, CraftBlock.notchToBlockFace(enumdirection1));
            } else {
                event = new BlockPistonRetractEvent(bblock, blocks, CraftBlock.notchToBlockFace(enumdirection1));
            }
            world.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                for (BlockPos b : broken) {
                    world.notify(b, Blocks.AIR.getBlockData(), world.getType(b), 3);
                }
                for (BlockPos b : moved) {
                    world.notify(b, Blocks.AIR.getBlockData(), world.getType(b), 3);
                    b = b.relative(enumdirection1);
                    world.notify(b, Blocks.AIR.getBlockData(), world.getType(b), 3);
                }
                return false;
            }
            // CraftBukkit end

            BlockPos blockposition3;
            int k;
            BlockState iblockdata1;

            for (k = list2.size() - 1; k >= 0; --k) {
                blockposition3 = (BlockPos) list2.get(k);
                iblockdata1 = world.getType(blockposition3);
                BlockEntity tileentity = iblockdata1.getBlock().isEntityBlock() ? world.getBlockEntity(blockposition3) : null;

                dropResources(iblockdata1, world, blockposition3, tileentity);
                world.setTypeAndData(blockposition3, Blocks.AIR.getBlockData(), 18);
                aiblockdata[j++] = iblockdata1;
            }

            for (k = list.size() - 1; k >= 0; --k) {
                blockposition3 = (BlockPos) list.get(k);
                iblockdata1 = world.getType(blockposition3);
                blockposition3 = blockposition3.relative(enumdirection1);
                map.remove(blockposition3);
                world.setTypeAndData(blockposition3, (BlockState) Blocks.MOVING_PISTON.getBlockData().setValue(PistonBaseBlock.FACING, enumdirection), 68);
                world.setBlockEntity(blockposition3, MovingPistonBlock.newMovingBlockEntity((BlockState) list1.get(k), enumdirection, flag, false));
                aiblockdata[j++] = iblockdata1;
            }

            if (flag) {
                PistonType blockpropertypistontype = this.isSticky ? PistonType.STICKY : PistonType.DEFAULT;
                BlockState iblockdata2 = (BlockState) ((BlockState) Blocks.PISTON_HEAD.getBlockData().setValue(PistonHeadBlock.FACING, enumdirection)).setValue(PistonHeadBlock.TYPE, blockpropertypistontype);

                iblockdata1 = (BlockState) ((BlockState) Blocks.MOVING_PISTON.getBlockData().setValue(MovingPistonBlock.FACING, enumdirection)).setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
                map.remove(blockposition1);
                world.setTypeAndData(blockposition1, iblockdata1, 68);
                world.setBlockEntity(blockposition1, MovingPistonBlock.newMovingBlockEntity(iblockdata2, enumdirection, true, true));
            }

            BlockState iblockdata3 = Blocks.AIR.getBlockData();
            Iterator iterator = map.keySet().iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition4 = (BlockPos) iterator.next();

                world.setTypeAndData(blockposition4, iblockdata3, 82);
            }

            iterator = map.entrySet().iterator();

            BlockPos blockposition5;

            while (iterator.hasNext()) {
                Entry<BlockPos, BlockState> entry = (Entry) iterator.next();

                blockposition5 = (BlockPos) entry.getKey();
                BlockState iblockdata4 = (BlockState) entry.getValue();

                iblockdata4.updateIndirectNeighbourShapes(world, blockposition5, 2);
                iblockdata3.updateNeighbourShapes(world, blockposition5, 2);
                iblockdata3.updateIndirectNeighbourShapes(world, blockposition5, 2);
            }

            j = 0;

            int l;

            for (l = list2.size() - 1; l >= 0; --l) {
                iblockdata1 = aiblockdata[j++];
                blockposition5 = (BlockPos) list2.get(l);
                iblockdata1.updateIndirectNeighbourShapes(world, blockposition5, 2);
                world.updateNeighborsAt(blockposition5, iblockdata1.getBlock());
            }

            for (l = list.size() - 1; l >= 0; --l) {
                world.updateNeighborsAt((BlockPos) list.get(l), aiblockdata[j++].getBlock());
            }

            if (flag) {
                world.updateNeighborsAt(blockposition1, Blocks.PISTON_HEAD);
            }

            return true;
        }
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(PistonBaseBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(PistonBaseBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(PistonBaseBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(PistonBaseBlock.FACING, PistonBaseBlock.EXTENDED);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(PistonBaseBlock.EXTENDED);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
