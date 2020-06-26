package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ChorusFlowerBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    private final ChorusPlantBlock plant;

    protected ChorusFlowerBlock(ChorusPlantBlock blockchorusfruit, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.plant = blockchorusfruit;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ChorusFlowerBlock.AGE, 0));
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        }

    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(ChorusFlowerBlock.AGE) < 5;
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        BlockPos blockposition1 = blockposition.above();

        if (worldserver.isEmptyBlock(blockposition1) && blockposition1.getY() < 256) {
            int i = (Integer) iblockdata.getValue(ChorusFlowerBlock.AGE);

            if (i < 5) {
                boolean flag = false;
                boolean flag1 = false;
                BlockState iblockdata1 = worldserver.getType(blockposition.below());
                Block block = iblockdata1.getBlock();
                int j;

                if (block == Blocks.END_STONE) {
                    flag = true;
                } else if (block == this.plant) {
                    j = 1;

                    for (int k = 0; k < 4; ++k) {
                        Block block1 = worldserver.getType(blockposition.below(j + 1)).getBlock();

                        if (block1 != this.plant) {
                            if (block1 == Blocks.END_STONE) {
                                flag1 = true;
                            }
                            break;
                        }

                        ++j;
                    }

                    if (j < 2 || j <= random.nextInt(flag1 ? 5 : 4)) {
                        flag = true;
                    }
                } else if (iblockdata1.isAir()) {
                    flag = true;
                }

                if (flag && allNeighborsEmpty((LevelReader) worldserver, blockposition1, (Direction) null) && worldserver.isEmptyBlock(blockposition.above(2))) {
                    // CraftBukkit start - add event
                    if (CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition1, this.getBlockData().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(i)), 2)) {
                        worldserver.setTypeAndData(blockposition, this.plant.getStateForPlacement((BlockGetter) worldserver, blockposition), 2);
                        this.placeGrownFlower(worldserver, blockposition1, i);
                    }
                    // CraftBukkit end
                } else if (i < 4) {
                    j = random.nextInt(4);
                    if (flag1) {
                        ++j;
                    }

                    boolean flag2 = false;

                    for (int l = 0; l < j; ++l) {
                        Direction enumdirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos blockposition2 = blockposition.relative(enumdirection);

                        if (worldserver.isEmptyBlock(blockposition2) && worldserver.isEmptyBlock(blockposition2.below()) && allNeighborsEmpty((LevelReader) worldserver, blockposition2, enumdirection.getOpposite())) {
                            // CraftBukkit start - add event
                            if (CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition2, this.getBlockData().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(i + 1)), 2)) {
                                this.placeGrownFlower(worldserver, blockposition2, i + 1);
                                flag2 = true;
                            }
                            // CraftBukkit end
                        }
                    }

                    if (flag2) {
                        worldserver.setTypeAndData(blockposition, this.plant.getStateForPlacement((BlockGetter) worldserver, blockposition), 2);
                    } else {
                        // CraftBukkit - add event
                        if (CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, this.getBlockData().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(5)), 2)) {
                            this.placeDeadFlower((Level) worldserver, blockposition);
                        }
                        // CraftBukkit end
                    }
                } else {
                    // CraftBukkit - add event
                    if (CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, this.getBlockData().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(5)), 2)) {
                        this.placeDeadFlower((Level) worldserver, blockposition);
                    }
                    // CraftBukkit end
                }

            }
        }
    }

    private void placeGrownFlower(Level world, BlockPos blockposition, int i) {
        world.setTypeAndData(blockposition, (BlockState) this.getBlockData().setValue(ChorusFlowerBlock.AGE, i), 2);
        world.levelEvent(1033, blockposition, 0);
    }

    private void placeDeadFlower(Level world, BlockPos blockposition) {
        world.setTypeAndData(blockposition, (BlockState) this.getBlockData().setValue(ChorusFlowerBlock.AGE, 5), 2);
        world.levelEvent(1034, blockposition, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader iworldreader, BlockPos blockposition, @Nullable Direction enumdirection) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        Direction enumdirection1;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            enumdirection1 = (Direction) iterator.next();
        } while (enumdirection1 == enumdirection || iworldreader.isEmptyBlock(blockposition.relative(enumdirection1)));

        return false;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection != Direction.UP && !iblockdata.canSurvive(generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockState iblockdata1 = iworldreader.getType(blockposition.below());

        if (iblockdata1.getBlock() != this.plant && !iblockdata1.is(Blocks.END_STONE)) {
            if (!iblockdata1.isAir()) {
                return false;
            } else {
                boolean flag = false;
                Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext()) {
                    Direction enumdirection = (Direction) iterator.next();
                    BlockState iblockdata2 = iworldreader.getType(blockposition.relative(enumdirection));

                    if (iblockdata2.is((Block) this.plant)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (!iblockdata2.isAir()) {
                        return false;
                    }
                }

                return flag;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ChorusFlowerBlock.AGE);
    }

    public static void generatePlant(LevelAccessor generatoraccess, BlockPos blockposition, Random random, int i) {
        generatoraccess.setTypeAndData(blockposition, ((ChorusPlantBlock) Blocks.CHORUS_PLANT).getStateForPlacement((BlockGetter) generatoraccess, blockposition), 2);
        growTreeRecursive(generatoraccess, blockposition, random, blockposition, i, 0);
    }

    private static void growTreeRecursive(LevelAccessor generatoraccess, BlockPos blockposition, Random random, BlockPos blockposition1, int i, int j) {
        ChorusPlantBlock blockchorusfruit = (ChorusPlantBlock) Blocks.CHORUS_PLANT;
        int k = random.nextInt(4) + 1;

        if (j == 0) {
            ++k;
        }

        for (int l = 0; l < k; ++l) {
            BlockPos blockposition2 = blockposition.above(l + 1);

            if (!allNeighborsEmpty((LevelReader) generatoraccess, blockposition2, (Direction) null)) {
                return;
            }

            generatoraccess.setTypeAndData(blockposition2, blockchorusfruit.getStateForPlacement((BlockGetter) generatoraccess, blockposition2), 2);
            generatoraccess.setTypeAndData(blockposition2.below(), blockchorusfruit.getStateForPlacement((BlockGetter) generatoraccess, blockposition2.below()), 2);
        }

        boolean flag = false;

        if (j < 4) {
            int i1 = random.nextInt(4);

            if (j == 0) {
                ++i1;
            }

            for (int j1 = 0; j1 < i1; ++j1) {
                Direction enumdirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockposition3 = blockposition.above(k).relative(enumdirection);

                if (Math.abs(blockposition3.getX() - blockposition1.getX()) < i && Math.abs(blockposition3.getZ() - blockposition1.getZ()) < i && generatoraccess.isEmptyBlock(blockposition3) && generatoraccess.isEmptyBlock(blockposition3.below()) && allNeighborsEmpty((LevelReader) generatoraccess, blockposition3, enumdirection.getOpposite())) {
                    flag = true;
                    generatoraccess.setTypeAndData(blockposition3, blockchorusfruit.getStateForPlacement((BlockGetter) generatoraccess, blockposition3), 2);
                    generatoraccess.setTypeAndData(blockposition3.relative(enumdirection.getOpposite()), blockchorusfruit.getStateForPlacement((BlockGetter) generatoraccess, blockposition3.relative(enumdirection.getOpposite())), 2);
                    growTreeRecursive(generatoraccess, blockposition3, random, blockposition1, i, j + 1);
                }
            }
        }

        if (!flag) {
            generatoraccess.setTypeAndData(blockposition.above(k), (BlockState) Blocks.CHORUS_FLOWER.getBlockData().setValue(ChorusFlowerBlock.AGE, 5), 2);
        }

    }

    @Override
    public void onProjectileHit(Level world, BlockState iblockdata, BlockHitResult movingobjectpositionblock, Projectile iprojectile) {
        if (iprojectile.getType().is((Tag) EntityTypeTags.IMPACT_PROJECTILES)) {
            BlockPos blockposition = movingobjectpositionblock.getBlockPos();

            world.destroyBlock(blockposition, true, iprojectile);
        }

    }
}
