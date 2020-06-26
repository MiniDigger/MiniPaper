package net.minecraft.world.level.block;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.inventory.CraftBlockInventoryHolder; // CraftBukkit

public class ComposterBlock extends Block implements WorldlyContainerHolder {

    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_COMPOSTER;
    public static final Object2FloatMap<ItemLike> COMPOSTABLES = new Object2FloatOpenHashMap();
    private static final VoxelShape OUTER_SHAPE = Shapes.block();
    private static final VoxelShape[] SHAPES = (VoxelShape[]) Util.make((new VoxelShape[9]), (avoxelshape) -> { // CraftBukkit - decompile error
        for (int i = 0; i < 8; ++i) {
            avoxelshape[i] = Shapes.join(ComposterBlock.OUTER_SHAPE, Block.box(2.0D, (double) Math.max(2, 1 + i * 2), 2.0D, 14.0D, 16.0D, 14.0D), BooleanOp.ONLY_FIRST);
        }

        avoxelshape[8] = avoxelshape[7];
    });

    public static void bootStrap() {
        ComposterBlock.COMPOSTABLES.defaultReturnValue(-1.0F);
        float f = 0.3F;
        float f1 = 0.5F;
        float f2 = 0.65F;
        float f3 = 0.85F;
        float f4 = 1.0F;

        add(0.3F, Items.JUNGLE_LEAVES);
        add(0.3F, Items.OAK_LEAVES);
        add(0.3F, Items.SPRUCE_LEAVES);
        add(0.3F, Items.DARK_OAK_LEAVES);
        add(0.3F, Items.ACACIA_LEAVES);
        add(0.3F, Items.BIRCH_LEAVES);
        add(0.3F, Items.OAK_SAPLING);
        add(0.3F, Items.SPRUCE_SAPLING);
        add(0.3F, Items.BIRCH_SAPLING);
        add(0.3F, Items.JUNGLE_SAPLING);
        add(0.3F, Items.ACACIA_SAPLING);
        add(0.3F, Items.DARK_OAK_SAPLING);
        add(0.3F, Items.BEETROOT_SEEDS);
        add(0.3F, Items.DRIED_KELP);
        add(0.3F, Items.GRASS);
        add(0.3F, Items.KELP);
        add(0.3F, Items.MELON_SEEDS);
        add(0.3F, Items.PUMPKIN_SEEDS);
        add(0.3F, Items.SEAGRASS);
        add(0.3F, Items.SWEET_BERRIES);
        add(0.3F, Items.WHEAT_SEEDS);
        add(0.5F, Items.DRIED_KELP_BLOCK);
        add(0.5F, Items.TALL_GRASS);
        add(0.5F, Items.CACTUS);
        add(0.5F, Items.SUGAR_CANE);
        add(0.5F, Items.VINE);
        add(0.5F, Items.NETHER_SPROUTS);
        add(0.5F, Items.WEEPING_VINES);
        add(0.5F, Items.TWISTING_VINES);
        add(0.5F, Items.MELON_SLICE);
        add(0.65F, Items.SEA_PICKLE);
        add(0.65F, Items.LILY_PAD);
        add(0.65F, Items.PUMPKIN);
        add(0.65F, Items.CARVED_PUMPKIN);
        add(0.65F, Items.MELON);
        add(0.65F, Items.APPLE);
        add(0.65F, Items.BEETROOT);
        add(0.65F, Items.CARROT);
        add(0.65F, Items.COCOA_BEANS);
        add(0.65F, Items.POTATO);
        add(0.65F, Items.WHEAT);
        add(0.65F, Items.BROWN_MUSHROOM);
        add(0.65F, Items.RED_MUSHROOM);
        add(0.65F, Items.MUSHROOM_STEM);
        add(0.65F, Items.CRIMSON_FUNGUS);
        add(0.65F, Items.WARPED_FUNGUS);
        add(0.65F, Items.NETHER_WART);
        add(0.65F, Items.CRIMSON_ROOTS);
        add(0.65F, Items.WARPED_ROOTS);
        add(0.65F, Items.SHROOMLIGHT);
        add(0.65F, Items.DANDELION);
        add(0.65F, Items.POPPY);
        add(0.65F, Items.BLUE_ORCHID);
        add(0.65F, Items.ALLIUM);
        add(0.65F, Items.AZURE_BLUET);
        add(0.65F, Items.RED_TULIP);
        add(0.65F, Items.ORANGE_TULIP);
        add(0.65F, Items.WHITE_TULIP);
        add(0.65F, Items.PINK_TULIP);
        add(0.65F, Items.OXEYE_DAISY);
        add(0.65F, Items.CORNFLOWER);
        add(0.65F, Items.LILY_OF_THE_VALLEY);
        add(0.65F, Items.WITHER_ROSE);
        add(0.65F, Items.FERN);
        add(0.65F, Items.SUNFLOWER);
        add(0.65F, Items.LILAC);
        add(0.65F, Items.ROSE_BUSH);
        add(0.65F, Items.PEONY);
        add(0.65F, Items.LARGE_FERN);
        add(0.85F, Items.HAY_BLOCK);
        add(0.85F, Items.BROWN_MUSHROOM_BLOCK);
        add(0.85F, Items.RED_MUSHROOM_BLOCK);
        add(0.85F, Items.NETHER_WART_BLOCK);
        add(0.85F, Items.WARPED_WART_BLOCK);
        add(0.85F, Items.BREAD);
        add(0.85F, Items.BAKED_POTATO);
        add(0.85F, Items.COOKIE);
        add(1.0F, Items.CAKE);
        add(1.0F, Items.PUMPKIN_PIE);
    }

    private static void add(float f, ItemLike imaterial) {
        ComposterBlock.COMPOSTABLES.put(imaterial.asItem(), f);
    }

    public ComposterBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ComposterBlock.LEVEL, 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return ComposterBlock.SHAPES[(Integer) iblockdata.getValue(ComposterBlock.LEVEL)];
    }

    @Override
    public VoxelShape getInteractionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return ComposterBlock.OUTER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return ComposterBlock.SHAPES[0];
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if ((Integer) iblockdata.getValue(ComposterBlock.LEVEL) == 7) {
            world.getBlockTickList().scheduleTick(blockposition, iblockdata.getBlock(), 20);
        }

    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        int i = (Integer) iblockdata.getValue(ComposterBlock.LEVEL);
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (i < 8 && ComposterBlock.COMPOSTABLES.containsKey(itemstack.getItem())) {
            if (i < 7 && !world.isClientSide) {
                BlockState iblockdata1 = addItem(iblockdata, (LevelAccessor) world, blockposition, itemstack);

                world.levelEvent(1500, blockposition, iblockdata != iblockdata1 ? 1 : 0);
                if (!entityhuman.abilities.instabuild) {
                    itemstack.shrink(1);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else if (i == 8) {
            extractProduce(iblockdata, world, blockposition);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static BlockState insertItem(BlockState iblockdata, ServerLevel worldserver, ItemStack itemstack, BlockPos blockposition) {
        int i = (Integer) iblockdata.getValue(ComposterBlock.LEVEL);

        if (i < 7 && ComposterBlock.COMPOSTABLES.containsKey(itemstack.getItem())) {
            BlockState iblockdata1 = addItem(iblockdata, (LevelAccessor) worldserver, blockposition, itemstack);

            itemstack.shrink(1);
            worldserver.levelEvent(1500, blockposition, iblockdata != iblockdata1 ? 1 : 0);
            return iblockdata1;
        } else {
            return iblockdata;
        }
    }

    public static BlockState extractProduce(BlockState iblockdata, Level world, BlockPos blockposition) {
        if (!world.isClientSide) {
            float f = 0.7F;
            double d0 = (double) (world.random.nextFloat() * 0.7F) + 0.15000000596046448D;
            double d1 = (double) (world.random.nextFloat() * 0.7F) + 0.06000000238418579D + 0.6D;
            double d2 = (double) (world.random.nextFloat() * 0.7F) + 0.15000000596046448D;
            ItemEntity entityitem = new ItemEntity(world, (double) blockposition.getX() + d0, (double) blockposition.getY() + d1, (double) blockposition.getZ() + d2, new ItemStack(Items.BONE_MEAL));

            entityitem.setDefaultPickUpDelay();
            world.addFreshEntity(entityitem);
        }

        BlockState iblockdata1 = empty(iblockdata, (LevelAccessor) world, blockposition);

        world.playSound((Player) null, blockposition, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return iblockdata1;
    }

    private static BlockState empty(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition) {
        BlockState iblockdata1 = (BlockState) iblockdata.setValue(ComposterBlock.LEVEL, 0);

        generatoraccess.setTypeAndData(blockposition, iblockdata1, 3);
        return iblockdata1;
    }

    private static BlockState addItem(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, ItemStack itemstack) {
        int i = (Integer) iblockdata.getValue(ComposterBlock.LEVEL);
        float f = ComposterBlock.COMPOSTABLES.getFloat(itemstack.getItem());

        if ((i != 0 || f <= 0.0F) && generatoraccess.getRandom().nextDouble() >= (double) f) {
            return iblockdata;
        } else {
            int j = i + 1;
            BlockState iblockdata1 = (BlockState) iblockdata.setValue(ComposterBlock.LEVEL, j);

            generatoraccess.setTypeAndData(blockposition, iblockdata1, 3);
            if (j == 7) {
                generatoraccess.getBlockTickList().scheduleTick(blockposition, iblockdata.getBlock(), 20);
            }

            return iblockdata1;
        }
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Integer) iblockdata.getValue(ComposterBlock.LEVEL) == 7) {
            worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.cycle((Property) ComposterBlock.LEVEL), 3);
            worldserver.playSound((Player) null, blockposition, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        return (Integer) iblockdata.getValue(ComposterBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ComposterBlock.LEVEL);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }

    @Override
    public WorldlyContainer getContainer(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition) {
        int i = (Integer) iblockdata.getValue(ComposterBlock.LEVEL);

        // CraftBukkit - empty generatoraccess, blockposition
        return (WorldlyContainer) (i == 8 ? new ComposterBlock.OutputContainer(iblockdata, generatoraccess, blockposition, new ItemStack(Items.BONE_MEAL)) : (i < 7 ? new ComposterBlock.ContainerInput(iblockdata, generatoraccess, blockposition) : new ComposterBlock.ContainerEmpty(generatoraccess, blockposition)));
    }

    static class ContainerInput extends SimpleContainer implements WorldlyContainer {

        private final BlockState a;
        private final LevelAccessor b;
        private final BlockPos c;
        private boolean d;

        public ContainerInput(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition) {
            super(1);
            this.bukkitOwner = new CraftBlockInventoryHolder(generatoraccess, blockposition, this); // CraftBukkit
            this.a = iblockdata;
            this.b = generatoraccess;
            this.c = blockposition;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction enumdirection) {
            return enumdirection == Direction.UP ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable Direction enumdirection) {
            return !this.d && enumdirection == Direction.UP && ComposterBlock.COMPOSTABLES.containsKey(itemstack.getItem());
        }

        @Override
        public boolean canTakeItemThroughFace(int i, ItemStack itemstack, Direction enumdirection) {
            return false;
        }

        @Override
        public void setChanged() {
            ItemStack itemstack = this.getItem(0);

            if (!itemstack.isEmpty()) {
                this.d = true;
                BlockState iblockdata = ComposterBlock.addItem(this.a, this.b, this.c, itemstack);

                this.b.levelEvent(1500, this.c, iblockdata != this.a ? 1 : 0);
                this.removeItemNoUpdate(0);
            }

        }
    }

    static class OutputContainer extends SimpleContainer implements WorldlyContainer {

        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public OutputContainer(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, ItemStack itemstack) {
            super(itemstack);
            this.state = iblockdata;
            this.level = generatoraccess;
            this.pos = blockposition;
            this.bukkitOwner = new CraftBlockInventoryHolder(generatoraccess, blockposition, this); // CraftBukkit
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction enumdirection) {
            return enumdirection == Direction.DOWN ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable Direction enumdirection) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int i, ItemStack itemstack, Direction enumdirection) {
            return !this.changed && enumdirection == Direction.DOWN && itemstack.getItem() == Items.BONE_MEAL;
        }

        @Override
        public void setChanged() {
            // CraftBukkit start - allow putting items back (eg cancelled InventoryMoveItemEvent)
            if (this.isEmpty()) {
                ComposterBlock.empty(this.state, this.level, this.pos);
                this.changed = true;
            } else {
                this.level.setTypeAndData(this.pos, this.state, 3);
                this.changed = false;
            }
            // CraftBukkit end
        }
    }

    static class ContainerEmpty extends SimpleContainer implements WorldlyContainer {

        public ContainerEmpty(LevelAccessor generatoraccess, BlockPos blockposition) { // CraftBukkit
            super(0);
            this.bukkitOwner = new CraftBlockInventoryHolder(generatoraccess, blockposition, this); // CraftBukkit
        }

        @Override
        public int[] getSlotsForFace(Direction enumdirection) {
            return new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable Direction enumdirection) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int i, ItemStack itemstack, Direction enumdirection) {
            return false;
        }
    }
}
