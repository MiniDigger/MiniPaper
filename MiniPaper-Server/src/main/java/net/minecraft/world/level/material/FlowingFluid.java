package net.minecraft.world.level.material;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
// CraftBukkit end

public abstract class FlowingFluid extends Fluid {

    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_FLOWING;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(200) {
            protected void rehash(int i) {}
        };

        object2bytelinkedopenhashmap.defaultReturnValue((byte) 127);
        return object2bytelinkedopenhashmap;
    });
    private final Map<FluidState, VoxelShape> shapes = Maps.newIdentityHashMap();

    public FlowingFluid() {}

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> blockstatelist_a) {
        blockstatelist_a.add(FlowingFluid.FALLING);
    }

    @Override
    public Vec3 getFlow(BlockGetter iblockaccess, BlockPos blockposition, FluidState fluid) {
        double d0 = 0.0D;
        double d1 = 0.0D;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();

            blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection);
            FluidState fluid1 = iblockaccess.getFluidState(blockposition_mutableblockposition);

            if (this.affectsFlow(fluid1)) {
                float f = fluid1.getOwnHeight();
                float f1 = 0.0F;

                if (f == 0.0F) {
                    if (!iblockaccess.getType(blockposition_mutableblockposition).getMaterial().blocksMotion()) {
                        BlockPos blockposition1 = blockposition_mutableblockposition.below();
                        FluidState fluid2 = iblockaccess.getFluidState(blockposition1);

                        if (this.affectsFlow(fluid2)) {
                            f = fluid2.getOwnHeight();
                            if (f > 0.0F) {
                                f1 = fluid.getOwnHeight() - (f - 0.8888889F);
                            }
                        }
                    }
                } else if (f > 0.0F) {
                    f1 = fluid.getOwnHeight() - f;
                }

                if (f1 != 0.0F) {
                    d0 += (double) ((float) enumdirection.getStepX() * f1);
                    d1 += (double) ((float) enumdirection.getStepZ() * f1);
                }
            }
        }

        Vec3 vec3d = new Vec3(d0, 0.0D, d1);

        if ((Boolean) fluid.getValue(FlowingFluid.FALLING)) {
            Iterator iterator1 = Direction.Plane.HORIZONTAL.iterator();

            while (iterator1.hasNext()) {
                Direction enumdirection1 = (Direction) iterator1.next();

                blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection1);
                if (this.isSolidFace(iblockaccess, (BlockPos) blockposition_mutableblockposition, enumdirection1) || this.isSolidFace(iblockaccess, blockposition_mutableblockposition.above(), enumdirection1)) {
                    vec3d = vec3d.normalize().add(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }

        return vec3d.normalize();
    }

    private boolean affectsFlow(FluidState fluid) {
        return fluid.isEmpty() || fluid.getType().isSame((Fluid) this);
    }

    protected boolean isSolidFace(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        BlockState iblockdata = iblockaccess.getType(blockposition);
        FluidState fluid = iblockaccess.getFluidState(blockposition);

        return fluid.getType().isSame((Fluid) this) ? false : (enumdirection == Direction.UP ? true : (iblockdata.getMaterial() == Material.ICE ? false : iblockdata.isFaceSturdy(iblockaccess, blockposition, enumdirection)));
    }

    protected void spread(LevelAccessor generatoraccess, BlockPos blockposition, FluidState fluid) {
        if (!fluid.isEmpty()) {
            BlockState iblockdata = generatoraccess.getType(blockposition);
            BlockPos blockposition1 = blockposition.below();
            BlockState iblockdata1 = generatoraccess.getType(blockposition1);
            FluidState fluid1 = this.getNewLiquid((LevelReader) generatoraccess, blockposition1, iblockdata1);

            if (this.canSpreadTo(generatoraccess, blockposition, iblockdata, Direction.DOWN, blockposition1, iblockdata1, generatoraccess.getFluidState(blockposition1), fluid1.getType())) {
                // CraftBukkit start
                org.bukkit.block.Block source = CraftBlock.at(generatoraccess, blockposition);
                BlockFromToEvent event = new BlockFromToEvent(source, BlockFace.DOWN);
                generatoraccess.getLevel().getServerOH().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.spreadTo(generatoraccess, blockposition1, iblockdata1, Direction.DOWN, fluid1);
                if (this.sourceNeighborCount((LevelReader) generatoraccess, blockposition) >= 3) {
                    this.spreadToSides(generatoraccess, blockposition, fluid, iblockdata);
                }
            } else if (fluid.isSource() || !this.isWaterHole((BlockGetter) generatoraccess, fluid1.getType(), blockposition, iblockdata, blockposition1, iblockdata1)) {
                this.spreadToSides(generatoraccess, blockposition, fluid, iblockdata);
            }

        }
    }

    private void spreadToSides(LevelAccessor generatoraccess, BlockPos blockposition, FluidState fluid, BlockState iblockdata) {
        int i = fluid.getAmount() - this.getDropOff((LevelReader) generatoraccess);

        if ((Boolean) fluid.getValue(FlowingFluid.FALLING)) {
            i = 7;
        }

        if (i > 0) {
            Map<Direction, FluidState> map = this.getSpread((LevelReader) generatoraccess, blockposition, iblockdata);
            Iterator iterator = map.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Direction, FluidState> entry = (Entry) iterator.next();
                Direction enumdirection = (Direction) entry.getKey();
                FluidState fluid1 = (FluidState) entry.getValue();
                BlockPos blockposition1 = blockposition.relative(enumdirection);
                BlockState iblockdata1 = generatoraccess.getType(blockposition1);

                if (this.canSpreadTo(generatoraccess, blockposition, iblockdata, enumdirection, blockposition1, iblockdata1, generatoraccess.getFluidState(blockposition1), fluid1.getType())) {
                    // CraftBukkit start
                    org.bukkit.block.Block source = CraftBlock.at(generatoraccess, blockposition);
                    BlockFromToEvent event = new BlockFromToEvent(source, org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(enumdirection));
                    generatoraccess.getLevel().getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        continue;
                    }
                    // CraftBukkit end
                    this.spreadTo(generatoraccess, blockposition1, iblockdata1, enumdirection, fluid1);
                }
            }

        }
    }

    protected FluidState getNewLiquid(LevelReader iworldreader, BlockPos blockposition, BlockState iblockdata) {
        int i = 0;
        int j = 0;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            BlockState iblockdata1 = iworldreader.getType(blockposition1);
            FluidState fluid = iblockdata1.getFluidState();

            if (fluid.getType().isSame((Fluid) this) && this.canPassThroughWall(enumdirection, (BlockGetter) iworldreader, blockposition, iblockdata, blockposition1, iblockdata1)) {
                if (fluid.isSource()) {
                    ++j;
                }

                i = Math.max(i, fluid.getAmount());
            }
        }

        if (this.canConvertToSource() && j >= 2) {
            BlockState iblockdata2 = iworldreader.getType(blockposition.below());
            FluidState fluid1 = iblockdata2.getFluidState();

            if (iblockdata2.getMaterial().isSolid() || this.isSourceBlockOfThisType(fluid1)) {
                return this.getSource(false);
            }
        }

        BlockPos blockposition2 = blockposition.above();
        BlockState iblockdata3 = iworldreader.getType(blockposition2);
        FluidState fluid2 = iblockdata3.getFluidState();

        if (!fluid2.isEmpty() && fluid2.getType().isSame((Fluid) this) && this.canPassThroughWall(Direction.UP, (BlockGetter) iworldreader, blockposition, iblockdata, blockposition2, iblockdata3)) {
            return this.getFlowing(8, true);
        } else {
            int k = i - this.getDropOff(iworldreader);

            return k <= 0 ? Fluids.EMPTY.defaultFluidState() : this.getFlowing(k, false);
        }
    }

    private boolean canPassThroughWall(Direction enumdirection, BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, BlockPos blockposition1, BlockState iblockdata1) {
        Object2ByteLinkedOpenHashMap object2bytelinkedopenhashmap;

        if (!iblockdata.getBlock().hasDynamicShape() && !iblockdata1.getBlock().hasDynamicShape()) {
            object2bytelinkedopenhashmap = (Object2ByteLinkedOpenHashMap) FlowingFluid.OCCLUSION_CACHE.get();
        } else {
            object2bytelinkedopenhashmap = null;
        }

        Block.BlockStatePairKey block_a;

        if (object2bytelinkedopenhashmap != null) {
            block_a = new Block.BlockStatePairKey(iblockdata, iblockdata1, enumdirection);
            byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(block_a);

            if (b0 != 127) {
                return b0 != 0;
            }
        } else {
            block_a = null;
        }

        VoxelShape voxelshape = iblockdata.getCollisionShape(iblockaccess, blockposition);
        VoxelShape voxelshape1 = iblockdata1.getCollisionShape(iblockaccess, blockposition1);
        boolean flag = !Shapes.mergedFaceOccludes(voxelshape, voxelshape1, enumdirection);

        if (object2bytelinkedopenhashmap != null) {
            if (object2bytelinkedopenhashmap.size() == 200) {
                object2bytelinkedopenhashmap.removeLastByte();
            }

            object2bytelinkedopenhashmap.putAndMoveToFirst(block_a, (byte) (flag ? 1 : 0));
        }

        return flag;
    }

    public abstract Fluid getFlowing();

    public FluidState getFlowing(int i, boolean flag) {
        return (FluidState) ((FluidState) this.getFlowing().defaultFluidState().setValue(FlowingFluid.LEVEL, i)).setValue(FlowingFluid.FALLING, flag);
    }

    public abstract Fluid getSource();

    public FluidState getSource(boolean flag) {
        return (FluidState) this.getSource().defaultFluidState().setValue(FlowingFluid.FALLING, flag);
    }

    protected abstract boolean canConvertToSource();

    protected void spreadTo(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata, Direction enumdirection, FluidState fluid) {
        if (iblockdata.getBlock() instanceof LiquidBlockContainer) {
            ((LiquidBlockContainer) iblockdata.getBlock()).place(generatoraccess, blockposition, iblockdata, fluid);
        } else {
            if (!iblockdata.isAir()) {
                this.beforeDestroyingBlock(generatoraccess, blockposition, iblockdata);
            }

            generatoraccess.setTypeAndData(blockposition, fluid.getBlockData(), 3);
        }

    }

    protected abstract void beforeDestroyingBlock(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata);

    private static short getCacheKey(BlockPos blockposition, BlockPos blockposition1) {
        int i = blockposition1.getX() - blockposition.getX();
        int j = blockposition1.getZ() - blockposition.getZ();

        return (short) ((i + 128 & 255) << 8 | j + 128 & 255);
    }

    protected int getSlopeDistance(LevelReader iworldreader, BlockPos blockposition, int i, Direction enumdirection, BlockState iblockdata, BlockPos blockposition1, Short2ObjectMap<Pair<BlockState, FluidState>> short2objectmap, Short2BooleanMap short2booleanmap) {
        int j = 1000;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection1 = (Direction) iterator.next();

            if (enumdirection1 != enumdirection) {
                BlockPos blockposition2 = blockposition.relative(enumdirection1);
                short short0 = getCacheKey(blockposition1, blockposition2);
                Pair<BlockState, FluidState> pair = (Pair) short2objectmap.computeIfAbsent(short0, (k) -> {
                    BlockState iblockdata1 = iworldreader.getType(blockposition2);

                    return Pair.of(iblockdata1, iblockdata1.getFluidState());
                });
                BlockState iblockdata1 = (BlockState) pair.getFirst();
                FluidState fluid = (FluidState) pair.getSecond();

                if (this.canPassThrough(iworldreader, this.getFlowing(), blockposition, iblockdata, enumdirection1, blockposition2, iblockdata1, fluid)) {
                    boolean flag = short2booleanmap.computeIfAbsent(short0, (k) -> {
                        BlockPos blockposition3 = blockposition2.below();
                        BlockState iblockdata2 = iworldreader.getType(blockposition3);

                        return this.isWaterHole((BlockGetter) iworldreader, this.getFlowing(), blockposition2, iblockdata1, blockposition3, iblockdata2);
                    });

                    if (flag) {
                        return i;
                    }

                    if (i < this.getSlopeFindDistance(iworldreader)) {
                        int k = this.getSlopeDistance(iworldreader, blockposition2, i + 1, enumdirection1.getOpposite(), iblockdata1, blockposition1, short2objectmap, short2booleanmap);

                        if (k < j) {
                            j = k;
                        }
                    }
                }
            }
        }

        return j;
    }

    private boolean isWaterHole(BlockGetter iblockaccess, Fluid fluidtype, BlockPos blockposition, BlockState iblockdata, BlockPos blockposition1, BlockState iblockdata1) {
        return !this.canPassThroughWall(Direction.DOWN, iblockaccess, blockposition, iblockdata, blockposition1, iblockdata1) ? false : (iblockdata1.getFluidState().getType().isSame((Fluid) this) ? true : this.canHoldFluid(iblockaccess, blockposition1, iblockdata1, fluidtype));
    }

    private boolean canPassThrough(BlockGetter iblockaccess, Fluid fluidtype, BlockPos blockposition, BlockState iblockdata, Direction enumdirection, BlockPos blockposition1, BlockState iblockdata1, FluidState fluid) {
        return !this.isSourceBlockOfThisType(fluid) && this.canPassThroughWall(enumdirection, iblockaccess, blockposition, iblockdata, blockposition1, iblockdata1) && this.canHoldFluid(iblockaccess, blockposition1, iblockdata1, fluidtype);
    }

    private boolean isSourceBlockOfThisType(FluidState fluid) {
        return fluid.getType().isSame((Fluid) this) && fluid.isSource();
    }

    protected abstract int getSlopeFindDistance(LevelReader iworldreader);

    private int sourceNeighborCount(LevelReader iworldreader, BlockPos blockposition) {
        int i = 0;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            FluidState fluid = iworldreader.getFluidState(blockposition1);

            if (this.isSourceBlockOfThisType(fluid)) {
                ++i;
            }
        }

        return i;
    }

    protected Map<Direction, FluidState> getSpread(LevelReader iworldreader, BlockPos blockposition, BlockState iblockdata) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        Short2ObjectMap<Pair<BlockState, FluidState>> short2objectmap = new Short2ObjectOpenHashMap();
        Short2BooleanOpenHashMap short2booleanopenhashmap = new Short2BooleanOpenHashMap();
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            short short0 = getCacheKey(blockposition, blockposition1);
            Pair<BlockState, FluidState> pair = (Pair) short2objectmap.computeIfAbsent(short0, (j) -> {
                BlockState iblockdata1 = iworldreader.getType(blockposition1);

                return Pair.of(iblockdata1, iblockdata1.getFluidState());
            });
            BlockState iblockdata1 = (BlockState) pair.getFirst();
            FluidState fluid = (FluidState) pair.getSecond();
            FluidState fluid1 = this.getNewLiquid(iworldreader, blockposition1, iblockdata1);

            if (this.canPassThrough(iworldreader, fluid1.getType(), blockposition, iblockdata, enumdirection, blockposition1, iblockdata1, fluid)) {
                BlockPos blockposition2 = blockposition1.below();
                boolean flag = short2booleanopenhashmap.computeIfAbsent(short0, (j) -> {
                    BlockState iblockdata2 = iworldreader.getType(blockposition2);

                    return this.isWaterHole((BlockGetter) iworldreader, this.getFlowing(), blockposition1, iblockdata1, blockposition2, iblockdata2);
                });
                int j;

                if (flag) {
                    j = 0;
                } else {
                    j = this.getSlopeDistance(iworldreader, blockposition1, 1, enumdirection.getOpposite(), iblockdata1, blockposition, short2objectmap, short2booleanopenhashmap);
                }

                if (j < i) {
                    map.clear();
                }

                if (j <= i) {
                    map.put(enumdirection, fluid1);
                    i = j;
                }
            }
        }

        return map;
    }

    private boolean canHoldFluid(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, Fluid fluidtype) {
        Block block = iblockdata.getBlock();

        if (block instanceof LiquidBlockContainer) {
            return ((LiquidBlockContainer) block).canPlace(iblockaccess, blockposition, iblockdata, fluidtype);
        } else if (!(block instanceof DoorBlock) && !block.is((Tag) BlockTags.SIGNS) && block != Blocks.LADDER && block != Blocks.SUGAR_CANE && block != Blocks.BUBBLE_COLUMN) {
            Material material = iblockdata.getMaterial();

            return material != Material.PORTAL && material != Material.STRUCTURAL_AIR && material != Material.WATER_PLANT && material != Material.REPLACEABLE_WATER_PLANT ? !material.blocksMotion() : false;
        } else {
            return false;
        }
    }

    protected boolean canSpreadTo(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, Direction enumdirection, BlockPos blockposition1, BlockState iblockdata1, FluidState fluid, Fluid fluidtype) {
        return fluid.canBeReplacedWith(iblockaccess, blockposition1, fluidtype, enumdirection) && this.canPassThroughWall(enumdirection, iblockaccess, blockposition, iblockdata, blockposition1, iblockdata1) && this.canHoldFluid(iblockaccess, blockposition1, iblockdata1, fluidtype);
    }

    protected abstract int getDropOff(LevelReader iworldreader);

    protected int getSpreadDelay(Level world, BlockPos blockposition, FluidState fluid, FluidState fluid1) {
        return this.getTickDelay((LevelReader) world);
    }

    @Override
    public void tick(Level world, BlockPos blockposition, FluidState fluid) {
        if (!fluid.isSource()) {
            FluidState fluid1 = this.getNewLiquid((LevelReader) world, blockposition, world.getType(blockposition));
            int i = this.getSpreadDelay(world, blockposition, fluid, fluid1);

            if (fluid1.isEmpty()) {
                fluid = fluid1;
                // CraftBukkit start
                FluidLevelChangeEvent event = CraftEventFactory.callFluidLevelChangeEvent(world, blockposition, Blocks.AIR.getBlockData());
                if (event.isCancelled()) {
                    return;
                }
                world.setTypeAndData(blockposition, ((CraftBlockData) event.getNewData()).getState(), 3);
                // CraftBukkit end
            } else if (!fluid1.equals(fluid)) {
                fluid = fluid1;
                BlockState iblockdata = fluid1.getBlockData();
                // CraftBukkit start
                FluidLevelChangeEvent event = CraftEventFactory.callFluidLevelChangeEvent(world, blockposition, iblockdata);
                if (event.isCancelled()) {
                    return;
                }
                world.setTypeAndData(blockposition, ((CraftBlockData) event.getNewData()).getState(), 2);
                // CraftBukkit end
                world.getFluidTickList().scheduleTick(blockposition, fluid1.getType(), i);
                world.updateNeighborsAt(blockposition, iblockdata.getBlock());
            }
        }

        this.spread((LevelAccessor) world, blockposition, fluid);
    }

    protected static int getLegacyLevel(FluidState fluid) {
        return fluid.isSource() ? 0 : 8 - Math.min(fluid.getAmount(), 8) + ((Boolean) fluid.getValue(FlowingFluid.FALLING) ? 8 : 0);
    }

    private static boolean hasSameAbove(FluidState fluid, BlockGetter iblockaccess, BlockPos blockposition) {
        return fluid.getType().isSame(iblockaccess.getFluidState(blockposition.above()).getType());
    }

    @Override
    public float getHeight(FluidState fluid, BlockGetter iblockaccess, BlockPos blockposition) {
        return hasSameAbove(fluid, iblockaccess, blockposition) ? 1.0F : fluid.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState fluid) {
        return (float) fluid.getAmount() / 9.0F;
    }

    @Override
    public VoxelShape getShape(FluidState fluid, BlockGetter iblockaccess, BlockPos blockposition) {
        return fluid.getAmount() == 9 && hasSameAbove(fluid, iblockaccess, blockposition) ? Shapes.block() : (VoxelShape) this.shapes.computeIfAbsent(fluid, (fluid1) -> {
            return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, (double) fluid1.getHeight(iblockaccess, blockposition), 1.0D);
        });
    }
}
