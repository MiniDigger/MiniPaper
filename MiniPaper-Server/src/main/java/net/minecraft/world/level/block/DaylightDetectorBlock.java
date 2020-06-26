package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DaylightDetectorBlock extends BaseEntityBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    public DaylightDetectorBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DaylightDetectorBlock.POWER, 0)).setValue(DaylightDetectorBlock.INVERTED, false));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return DaylightDetectorBlock.SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Integer) iblockdata.getValue(DaylightDetectorBlock.POWER);
    }

    public static void updateSignalStrength(BlockState iblockdata, Level world, BlockPos blockposition) {
        if (world.dimensionType().hasSkyLight()) {
            int i = world.getBrightness(LightLayer.SKY, blockposition) - world.getSkyDarken();
            float f = world.getSunAngle(1.0F);
            boolean flag = (Boolean) iblockdata.getValue(DaylightDetectorBlock.INVERTED);

            if (flag) {
                i = 15 - i;
            } else if (i > 0) {
                float f1 = f < 3.1415927F ? 0.0F : 6.2831855F;

                f += (f1 - f) * 0.2F;
                i = Math.round((float) i * Mth.cos(f));
            }

            i = Mth.clamp(i, 0, 15);
            if ((Integer) iblockdata.getValue(DaylightDetectorBlock.POWER) != i) {
                i = org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, blockposition, ((Integer) iblockdata.getValue(POWER)), i).getNewCurrent(); // CraftBukkit - Call BlockRedstoneEvent
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(DaylightDetectorBlock.POWER, i), 3);
            }

        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (entityhuman.mayBuild()) {
            if (world.isClientSide) {
                return InteractionResult.SUCCESS;
            } else {
                BlockState iblockdata1 = (BlockState) iblockdata.cycle((Property) DaylightDetectorBlock.INVERTED);

                world.setTypeAndData(blockposition, iblockdata1, 4);
                updateSignalStrength(iblockdata1, world, blockposition);
                return InteractionResult.CONSUME;
            }
        } else {
            return super.interact(iblockdata, world, blockposition, entityhuman, enumhand, movingobjectpositionblock);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new DaylightDetectorBlockEntity();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(DaylightDetectorBlock.POWER, DaylightDetectorBlock.INVERTED);
    }
}
