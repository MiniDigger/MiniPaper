package net.minecraft.world.level.block;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
// CraftBukkit end

public class FireBlock extends BaseFireBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != Direction.DOWN;
    }).collect(Util.toMap());
    private final Object2IntMap<Block> flameOdds = new Object2IntOpenHashMap();
    private final Object2IntMap<Block> burnOdds = new Object2IntOpenHashMap();

    public FireBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info, 1.0F);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(FireBlock.AGE, 0)).setValue(FireBlock.NORTH, false)).setValue(FireBlock.EAST, false)).setValue(FireBlock.SOUTH, false)).setValue(FireBlock.WEST, false)).setValue(FireBlock.UP, false));
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        // CraftBukkit start
        if (!this.canPlace(iblockdata, generatoraccess, blockposition)) {
            CraftBlockState blockState = CraftBlockState.getBlockState(generatoraccess, blockposition);
            blockState.setData(Blocks.AIR.getBlockData());

            BlockFadeEvent event = new BlockFadeEvent(blockState.getBlock(), blockState);
            generatoraccess.getLevel().getServer().server.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }
        return this.getStateWithAge(generatoraccess, blockposition, (Integer) iblockdata.getValue(FireBlock.AGE));
        // CraftBukkit end
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        VoxelShape voxelshape = Shapes.empty();

        if ((Boolean) iblockdata.getValue(FireBlock.UP)) {
            voxelshape = FireBlock.UP_AABB;
        }

        if ((Boolean) iblockdata.getValue(FireBlock.WEST)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.WEST_AABB);
        }

        if ((Boolean) iblockdata.getValue(FireBlock.EAST)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.EAST_AABB);
        }

        if ((Boolean) iblockdata.getValue(FireBlock.NORTH)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.NORTH_AABB);
        }

        if ((Boolean) iblockdata.getValue(FireBlock.SOUTH)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.SOUTH_AABB);
        }

        return voxelshape == Shapes.empty() ? FireBlock.DOWN_AABB : voxelshape;
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return this.getPlacedState(blockactioncontext.getLevel(), blockactioncontext.getClickedPos());
    }

    protected BlockState getPlacedState(BlockGetter iblockaccess, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();
        BlockState iblockdata = iblockaccess.getType(blockposition1);

        if (!this.canBurn(iblockdata) && !iblockdata.isFaceSturdy(iblockaccess, blockposition1, Direction.UP)) {
            BlockState iblockdata1 = this.getBlockData();
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];
                BooleanProperty blockstateboolean = (BooleanProperty) FireBlock.PROPERTY_BY_DIRECTION.get(enumdirection);

                if (blockstateboolean != null) {
                    iblockdata1 = (BlockState) iblockdata1.setValue(blockstateboolean, this.canBurn(iblockaccess.getType(blockposition.relative(enumdirection))));
                }
            }

            return iblockdata1;
        } else {
            return this.getBlockData();
        }
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();

        return iworldreader.getType(blockposition1).isFaceSturdy(iworldreader, blockposition1, Direction.UP) || this.isValidFireLocation(iworldreader, blockposition);
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        worldserver.getBlockTickList().scheduleTick(blockposition, this, getFireTickDelay(worldserver.random));
        if (worldserver.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            if (!iblockdata.canSurvive(worldserver, blockposition)) {
                fireExtinguished(worldserver, blockposition); // CraftBukkit - invalid place location
            }

            BlockState iblockdata1 = worldserver.getType(blockposition.below());
            boolean flag = iblockdata1.is(worldserver.dimensionType().infiniburn());
            int i = (Integer) iblockdata.getValue(FireBlock.AGE);

            if (!flag && worldserver.isRaining() && this.isNearRain((Level) worldserver, blockposition) && random.nextFloat() < 0.2F + (float) i * 0.03F) {
                fireExtinguished(worldserver, blockposition); // CraftBukkit - extinguished by rain
            } else {
                int j = Math.min(15, i + random.nextInt(3) / 2);

                if (i != j) {
                    iblockdata = (BlockState) iblockdata.setValue(FireBlock.AGE, j);
                    worldserver.setTypeAndData(blockposition, iblockdata, 4);
                }

                if (!flag) {
                    if (!this.isValidFireLocation(worldserver, blockposition)) {
                        BlockPos blockposition1 = blockposition.below();

                        if (!worldserver.getType(blockposition1).isFaceSturdy(worldserver, blockposition1, Direction.UP) || i > 3) {
                            fireExtinguished(worldserver, blockposition); // CraftBukkit
                        }

                        return;
                    }

                    if (i == 15 && random.nextInt(4) == 0 && !this.canBurn(worldserver.getType(blockposition.below()))) {
                        fireExtinguished(worldserver, blockposition); // CraftBukkit
                        return;
                    }
                }

                boolean flag1 = worldserver.isHumidAt(blockposition);
                int k = flag1 ? -50 : 0;

                // CraftBukkit start - add source blockposition to burn calls
                this.trySpread(worldserver, blockposition.east(), 300 + k, random, i, blockposition);
                this.trySpread(worldserver, blockposition.west(), 300 + k, random, i, blockposition);
                this.trySpread(worldserver, blockposition.below(), 250 + k, random, i, blockposition);
                this.trySpread(worldserver, blockposition.above(), 250 + k, random, i, blockposition);
                this.trySpread(worldserver, blockposition.north(), 300 + k, random, i, blockposition);
                this.trySpread(worldserver, blockposition.south(), 300 + k, random, i, blockposition);
                // CraftBukkit end
                BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

                for (int l = -1; l <= 1; ++l) {
                    for (int i1 = -1; i1 <= 1; ++i1) {
                        for (int j1 = -1; j1 <= 4; ++j1) {
                            if (l != 0 || j1 != 0 || i1 != 0) {
                                int k1 = 100;

                                if (j1 > 1) {
                                    k1 += (j1 - 1) * 100;
                                }

                                blockposition_mutableblockposition.a((Vec3i) blockposition, l, j1, i1);
                                int l1 = this.getFireOdds((LevelReader) worldserver, (BlockPos) blockposition_mutableblockposition);

                                if (l1 > 0) {
                                    int i2 = (l1 + 40 + worldserver.getDifficulty().getId() * 7) / (i + 30);

                                    if (flag1) {
                                        i2 /= 2;
                                    }

                                    if (i2 > 0 && random.nextInt(k1) <= i2 && (!worldserver.isRaining() || !this.isNearRain((Level) worldserver, (BlockPos) blockposition_mutableblockposition))) {
                                        int j2 = Math.min(15, i + random.nextInt(5) / 4);

                                        // CraftBukkit start - Call to stop spread of fire
                                        if (worldserver.getType(blockposition_mutableblockposition).getBlock() != Blocks.FIRE) {
                                            if (CraftEventFactory.callBlockIgniteEvent(worldserver, blockposition_mutableblockposition, blockposition).isCancelled()) {
                                                continue;
                                            }

                                            CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition_mutableblockposition, this.getStateWithAge(worldserver, blockposition_mutableblockposition, j2), 3); // CraftBukkit
                                        }
                                        // CraftBukkit end
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    protected boolean isNearRain(Level world, BlockPos blockposition) {
        return world.isRainingAt(blockposition) || world.isRainingAt(blockposition.west()) || world.isRainingAt(blockposition.east()) || world.isRainingAt(blockposition.north()) || world.isRainingAt(blockposition.south());
    }

    private int getBurnChance(BlockState iblockdata) {
        return iblockdata.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) iblockdata.getValue(BlockStateProperties.WATERLOGGED) ? 0 : this.burnOdds.getInt(iblockdata.getBlock());
    }

    private int getFlameChance(BlockState iblockdata) {
        return iblockdata.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) iblockdata.getValue(BlockStateProperties.WATERLOGGED) ? 0 : this.flameOdds.getInt(iblockdata.getBlock());
    }

    private void trySpread(Level world, BlockPos blockposition, int i, Random random, int j, BlockPos sourceposition) { // CraftBukkit add sourceposition
        int k = this.getBurnChance(world.getType(blockposition));

        if (random.nextInt(i) < k) {
            BlockState iblockdata = world.getType(blockposition);

            // CraftBukkit start
            org.bukkit.block.Block theBlock = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            org.bukkit.block.Block sourceBlock = world.getWorld().getBlockAt(sourceposition.getX(), sourceposition.getY(), sourceposition.getZ());

            BlockBurnEvent event = new BlockBurnEvent(theBlock, sourceBlock);
            world.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }
            // CraftBukkit end

            if (random.nextInt(j + 10) < 5 && !world.isRainingAt(blockposition)) {
                int l = Math.min(j + random.nextInt(5) / 4, 15);

                world.setTypeAndData(blockposition, this.getStateWithAge(world, blockposition, l), 3);
            } else {
                world.removeBlock(blockposition, false);
            }

            Block block = iblockdata.getBlock();

            if (block instanceof TntBlock) {
                TntBlock blocktnt = (TntBlock) block;

                TntBlock.explode(world, blockposition);
            }
        }

    }

    private BlockState getStateWithAge(LevelAccessor generatoraccess, BlockPos blockposition, int i) {
        BlockState iblockdata = getState((BlockGetter) generatoraccess, blockposition);

        return iblockdata.is(Blocks.FIRE) ? (BlockState) iblockdata.setValue(FireBlock.AGE, i) : iblockdata;
    }

    private boolean isValidFireLocation(BlockGetter iblockaccess, BlockPos blockposition) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (this.canBurn(iblockaccess.getType(blockposition.relative(enumdirection)))) {
                return true;
            }
        }

        return false;
    }

    private int getFireOdds(LevelReader iworldreader, BlockPos blockposition) {
        if (!iworldreader.isEmptyBlock(blockposition)) {
            return 0;
        } else {
            int i = 0;
            Direction[] aenumdirection = Direction.values();
            int j = aenumdirection.length;

            for (int k = 0; k < j; ++k) {
                Direction enumdirection = aenumdirection[k];
                BlockState iblockdata = iworldreader.getType(blockposition.relative(enumdirection));

                i = Math.max(this.getFlameChance(iblockdata), i);
            }

            return i;
        }
    }

    @Override
    protected boolean canBurn(BlockState iblockdata) {
        return this.getFlameChance(iblockdata) > 0;
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        super.onPlace(iblockdata, world, blockposition, iblockdata1, flag);
        world.getBlockTickList().scheduleTick(blockposition, this, getFireTickDelay(world.random));
    }

    private static int getFireTickDelay(Random random) {
        return 30 + random.nextInt(10);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(FireBlock.AGE, FireBlock.NORTH, FireBlock.EAST, FireBlock.SOUTH, FireBlock.WEST, FireBlock.UP);
    }

    private void setFlammable(Block block, int i, int j) {
        this.flameOdds.put(block, i);
        this.burnOdds.put(block, j);
    }

    public static void bootStrap() {
        FireBlock blockfire = (FireBlock) Blocks.FIRE;

        blockfire.setFlammable(Blocks.OAK_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.OAK_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.OAK_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.OAK_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.OAK_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.SPRUCE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.BIRCH_LOG, 5, 5);
        blockfire.setFlammable(Blocks.JUNGLE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.ACACIA_LOG, 5, 5);
        blockfire.setFlammable(Blocks.DARK_OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_SPRUCE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_BIRCH_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_JUNGLE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_ACACIA_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_DARK_OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_SPRUCE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_BIRCH_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_JUNGLE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_ACACIA_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_DARK_OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.SPRUCE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.BIRCH_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.JUNGLE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.ACACIA_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.DARK_OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.OAK_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.SPRUCE_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.BIRCH_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.JUNGLE_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.ACACIA_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.DARK_OAK_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.BOOKSHELF, 30, 20);
        blockfire.setFlammable(Blocks.TNT, 15, 100);
        blockfire.setFlammable(Blocks.GRASS, 60, 100);
        blockfire.setFlammable(Blocks.FERN, 60, 100);
        blockfire.setFlammable(Blocks.DEAD_BUSH, 60, 100);
        blockfire.setFlammable(Blocks.SUNFLOWER, 60, 100);
        blockfire.setFlammable(Blocks.LILAC, 60, 100);
        blockfire.setFlammable(Blocks.ROSE_BUSH, 60, 100);
        blockfire.setFlammable(Blocks.PEONY, 60, 100);
        blockfire.setFlammable(Blocks.TALL_GRASS, 60, 100);
        blockfire.setFlammable(Blocks.LARGE_FERN, 60, 100);
        blockfire.setFlammable(Blocks.DANDELION, 60, 100);
        blockfire.setFlammable(Blocks.POPPY, 60, 100);
        blockfire.setFlammable(Blocks.BLUE_ORCHID, 60, 100);
        blockfire.setFlammable(Blocks.ALLIUM, 60, 100);
        blockfire.setFlammable(Blocks.AZURE_BLUET, 60, 100);
        blockfire.setFlammable(Blocks.RED_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.ORANGE_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.WHITE_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.PINK_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.OXEYE_DAISY, 60, 100);
        blockfire.setFlammable(Blocks.CORNFLOWER, 60, 100);
        blockfire.setFlammable(Blocks.LILY_OF_THE_VALLEY, 60, 100);
        blockfire.setFlammable(Blocks.WITHER_ROSE, 60, 100);
        blockfire.setFlammable(Blocks.WHITE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.ORANGE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.MAGENTA_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.LIGHT_BLUE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.YELLOW_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.LIME_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.PINK_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.GRAY_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.LIGHT_GRAY_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.CYAN_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.PURPLE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.BLUE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.BROWN_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.GREEN_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.RED_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.BLACK_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.VINE, 15, 100);
        blockfire.setFlammable(Blocks.COAL_BLOCK, 5, 5);
        blockfire.setFlammable(Blocks.HAY_BLOCK, 60, 20);
        blockfire.setFlammable(Blocks.TARGET, 15, 20);
        blockfire.setFlammable(Blocks.WHITE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.ORANGE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.MAGENTA_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.LIGHT_BLUE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.YELLOW_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.LIME_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.PINK_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.GRAY_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.LIGHT_GRAY_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.CYAN_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.PURPLE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.BLUE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.BROWN_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.GREEN_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.RED_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.BLACK_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.DRIED_KELP_BLOCK, 30, 60);
        blockfire.setFlammable(Blocks.BAMBOO, 60, 60);
        blockfire.setFlammable(Blocks.SCAFFOLDING, 60, 60);
        blockfire.setFlammable(Blocks.LECTERN, 30, 20);
        blockfire.setFlammable(Blocks.COMPOSTER, 5, 20);
        blockfire.setFlammable(Blocks.SWEET_BERRY_BUSH, 60, 100);
        blockfire.setFlammable(Blocks.BEEHIVE, 5, 20);
        blockfire.setFlammable(Blocks.BEE_NEST, 30, 20);
    }
}
