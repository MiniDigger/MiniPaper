package net.minecraft.world.level.material;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public abstract class LavaFluid extends FlowingFluid {

    public LavaFluid() {}

    @Override
    public Fluid getFlowing() {
        return Fluids.FLOWING_LAVA;
    }

    @Override
    public Fluid getSource() {
        return Fluids.LAVA;
    }

    @Override
    public Item getBucket() {
        return Items.LAVA_BUCKET;
    }

    @Override
    public void randomTick(Level world, BlockPos blockposition, FluidState fluid, Random random) {
        if (world.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            int i = random.nextInt(3);

            if (i > 0) {
                BlockPos blockposition1 = blockposition;

                for (int j = 0; j < i; ++j) {
                    blockposition1 = blockposition1.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                    if (!world.isLoaded(blockposition1)) {
                        return;
                    }

                    BlockState iblockdata = world.getType(blockposition1);

                    if (iblockdata.isAir()) {
                        if (this.hasFlammableNeighbours((LevelReader) world, blockposition1)) {
                            // CraftBukkit start - Prevent lava putting something on fire
                            if (world.getType(blockposition1).getBlock() != Blocks.FIRE) {
                                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition1, blockposition).isCancelled()) {
                                    continue;
                                }
                            }
                            // CraftBukkit end
                            world.setTypeUpdate(blockposition1, BaseFireBlock.getState((BlockGetter) world, blockposition1));
                            return;
                        }
                    } else if (iblockdata.getMaterial().blocksMotion()) {
                        return;
                    }
                }
            } else {
                for (int k = 0; k < 3; ++k) {
                    BlockPos blockposition2 = blockposition.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);

                    if (!world.isLoaded(blockposition2)) {
                        return;
                    }

                    if (world.isEmptyBlock(blockposition2.above()) && this.isFlammable(world, blockposition2)) {
                        // CraftBukkit start - Prevent lava putting something on fire
                        BlockPos up = blockposition2.above();
                        if (world.getType(up).getBlock() != Blocks.FIRE) {
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, up, blockposition).isCancelled()) {
                                continue;
                            }
                        }
                        // CraftBukkit end
                        world.setTypeUpdate(blockposition2.above(), BaseFireBlock.getState((BlockGetter) world, blockposition2));
                    }
                }
            }

        }
    }

    private boolean hasFlammableNeighbours(LevelReader iworldreader, BlockPos blockposition) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (this.isFlammable(iworldreader, blockposition.relative(enumdirection))) {
                return true;
            }
        }

        return false;
    }

    private boolean isFlammable(LevelReader iworldreader, BlockPos blockposition) {
        return blockposition.getY() >= 0 && blockposition.getY() < 256 && !iworldreader.hasChunkAt(blockposition) ? false : iworldreader.getType(blockposition).getMaterial().isFlammable();
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata) {
        this.fizz(generatoraccess, blockposition);
    }

    @Override
    public int getSlopeFindDistance(LevelReader iworldreader) {
        return iworldreader.dimensionType().ultraWarm() ? 4 : 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState fluid) {
        return (BlockState) Blocks.LAVA.getBlockData().setValue(LiquidBlock.LEVEL, getLegacyLevel(fluid));
    }

    @Override
    public boolean isSame(Fluid fluidtype) {
        return fluidtype == Fluids.LAVA || fluidtype == Fluids.FLOWING_LAVA;
    }

    @Override
    public int getDropOff(LevelReader iworldreader) {
        return iworldreader.dimensionType().ultraWarm() ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState fluid, BlockGetter iblockaccess, BlockPos blockposition, Fluid fluidtype, Direction enumdirection) {
        return fluid.getHeight(iblockaccess, blockposition) >= 0.44444445F && fluidtype.is((Tag) FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader iworldreader) {
        return iworldreader.dimensionType().ultraWarm() ? 10 : 30;
    }

    @Override
    public int getSpreadDelay(Level world, BlockPos blockposition, FluidState fluid, FluidState fluid1) {
        int i = this.getTickDelay((LevelReader) world);

        if (!fluid.isEmpty() && !fluid1.isEmpty() && !(Boolean) fluid.getValue(LavaFluid.FALLING) && !(Boolean) fluid1.getValue(LavaFluid.FALLING) && fluid1.getHeight(world, blockposition) > fluid.getHeight(world, blockposition) && world.getRandom().nextInt(4) != 0) {
            i *= 4;
        }

        return i;
    }

    private void fizz(LevelAccessor generatoraccess, BlockPos blockposition) {
        generatoraccess.levelEvent(1501, blockposition, 0);
    }

    @Override
    protected boolean canConvertToSource() {
        return false;
    }

    @Override
    protected void spreadTo(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata, Direction enumdirection, FluidState fluid) {
        if (enumdirection == Direction.DOWN) {
            FluidState fluid1 = generatoraccess.getFluidState(blockposition);

            if (this.is((Tag) FluidTags.LAVA) && fluid1.is((Tag) FluidTags.WATER)) {
                if (iblockdata.getBlock() instanceof LiquidBlock) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(generatoraccess.getLevel(), blockposition, Blocks.STONE.getBlockData(), 3)) {
                        return;
                    }
                    // CraftBukkit end
                }

                this.fizz(generatoraccess, blockposition);
                return;
            }
        }

        super.spreadTo(generatoraccess, blockposition, iblockdata, enumdirection, fluid);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    public static class Flowing extends LavaFluid {

        public Flowing() {}

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> blockstatelist_a) {
            super.createFluidStateDefinition(blockstatelist_a);
            blockstatelist_a.add(LavaFluid.Flowing.LEVEL);
        }

        @Override
        public int getAmount(FluidState fluid) {
            return (Integer) fluid.getValue(LavaFluid.Flowing.LEVEL);
        }

        @Override
        public boolean isSource(FluidState fluid) {
            return false;
        }
    }

    public static class Source extends LavaFluid {

        public Source() {}

        @Override
        public int getAmount(FluidState fluid) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState fluid) {
            return true;
        }
    }
}
