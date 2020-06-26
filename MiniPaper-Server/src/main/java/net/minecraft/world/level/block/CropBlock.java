package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class CropBlock extends BushBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)};

    protected CropBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(this.getAgeProperty(), 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return CropBlock.SHAPE_BY_AGE[(Integer) iblockdata.getValue(this.getAgeProperty())];
    }

    @Override
    protected boolean mayPlaceOn(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.is(Blocks.FARMLAND);
    }

    public IntegerProperty getAgeProperty() {
        return CropBlock.AGE;
    }

    public int getMaxAge() {
        return 7;
    }

    protected int getAge(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(this.getAgeProperty());
    }

    public BlockState setAge(int i) {
        return (BlockState) this.getBlockData().setValue(this.getAgeProperty(), i);
    }

    public boolean isRipe(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(this.getAgeProperty()) >= this.getMaxAge();
    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return !this.isRipe(iblockdata);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.getRawBrightness(blockposition, 0) >= 9) {
            int i = this.getAge(iblockdata);

            if (i < this.getMaxAge()) {
                float f = getGrowthSpeed((Block) this, (BlockGetter) worldserver, blockposition);

                // Spigot start
                int modifier;
                if (this == Blocks.BEETROOTS) {
                    modifier = worldserver.spigotConfig.beetrootModifier;
                } else if (this == Blocks.CARROTS) {
                    modifier = worldserver.spigotConfig.carrotModifier;
                } else if (this == Blocks.POTATOES) {
                    modifier = worldserver.spigotConfig.potatoModifier;
                } else {
                    modifier = worldserver.spigotConfig.wheatModifier;
                }

                if (random.nextInt((int) ((100.0F / modifier) * (25.0F / f)) + 1) == 0) {
                    // Spigot end
                    CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, this.setAge(i + 1), 2); // CraftBukkit
                }
            }
        }

    }

    public void growCrops(Level world, BlockPos blockposition, BlockState iblockdata) {
        int i = this.getAge(iblockdata) + this.getBonemealAgeIncrease(world);
        int j = this.getMaxAge();

        if (i > j) {
            i = j;
        }

        CraftEventFactory.handleBlockGrowEvent(world, blockposition, this.setAge(i), 2); // CraftBukkit
    }

    protected int getBonemealAgeIncrease(Level world) {
        return Mth.nextInt(world.random, 2, 5);
    }

    protected static float getGrowthSpeed(Block block, BlockGetter iblockaccess, BlockPos blockposition) {
        float f = 1.0F;
        BlockPos blockposition1 = blockposition.below();

        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                float f1 = 0.0F;
                BlockState iblockdata = iblockaccess.getType(blockposition1.offset(i, 0, j));

                if (iblockdata.is(Blocks.FARMLAND)) {
                    f1 = 1.0F;
                    if ((Integer) iblockdata.getValue(FarmBlock.MOISTURE) > 0) {
                        f1 = 3.0F;
                    }
                }

                if (i != 0 || j != 0) {
                    f1 /= 4.0F;
                }

                f += f1;
            }
        }

        BlockPos blockposition2 = blockposition.north();
        BlockPos blockposition3 = blockposition.south();
        BlockPos blockposition4 = blockposition.west();
        BlockPos blockposition5 = blockposition.east();
        boolean flag = block == iblockaccess.getType(blockposition4).getBlock() || block == iblockaccess.getType(blockposition5).getBlock();
        boolean flag1 = block == iblockaccess.getType(blockposition2).getBlock() || block == iblockaccess.getType(blockposition3).getBlock();

        if (flag && flag1) {
            f /= 2.0F;
        } else {
            boolean flag2 = block == iblockaccess.getType(blockposition4.north()).getBlock() || block == iblockaccess.getType(blockposition5.north()).getBlock() || block == iblockaccess.getType(blockposition5.south()).getBlock() || block == iblockaccess.getType(blockposition4.south()).getBlock();

            if (flag2) {
                f /= 2.0F;
            }
        }

        return f;
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return (iworldreader.getRawBrightness(blockposition, 0) >= 8 || iworldreader.canSeeSky(blockposition)) && super.canPlace(iblockdata, iworldreader, blockposition);
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (entity instanceof Ravager && !CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, Blocks.AIR.getBlockData(), !world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)).isCancelled()) { // CraftBukkit
            world.destroyBlock(blockposition, true, entity);
        }

        super.entityInside(iblockdata, world, blockposition, entity);
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return !this.isRipe(iblockdata);
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        this.growCrops((Level) worldserver, blockposition, iblockdata);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CropBlock.AGE);
    }
}
