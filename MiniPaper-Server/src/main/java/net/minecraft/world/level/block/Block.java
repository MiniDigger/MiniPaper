package net.minecraft.world.level.block;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.IdMapper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Block extends BlockBehaviour implements ItemLike {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final IdMapper<BlockState> BLOCK_STATE_REGISTRY = new IdMapper<>();
    public static final LoadingCache<VoxelShape, Boolean> SHAPE_FULL_BLOCK_CACHE = CacheBuilder.newBuilder().maximumSize(512L).weakKeys().build(new CacheLoader<VoxelShape, Boolean>() {
        public Boolean load(VoxelShape voxelshape) {
            return !Shapes.joinIsNotEmpty(Shapes.block(), voxelshape, BooleanOp.NOT_SAME);
        }
    });
    public static final VoxelShape RIGID_SUPPORT_SHAPE = Shapes.join(Shapes.block(), box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D), BooleanOp.ONLY_FIRST);
    public static final VoxelShape CENTER_SUPPORT_SHAPE = box(7.0D, 0.0D, 7.0D, 9.0D, 10.0D, 9.0D);
    public final StateDefinition<Block, BlockState> stateDefinition;
    public BlockState defaultBlockState;
    @Nullable
    public String descriptionId;
    @Nullable
    public Item item;
    public static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(2048, 0.25F) {
            protected void rehash(int i) {}
        };

        object2bytelinkedopenhashmap.defaultReturnValue((byte) 127);
        return object2bytelinkedopenhashmap;
    });

    public static int getCombinedId(@Nullable BlockState iblockdata) {
        if (iblockdata == null) {
            return 0;
        } else {
            int i = Block.BLOCK_STATE_REGISTRY.getId(iblockdata);

            return i == -1 ? 0 : i;
        }
    }

    public static BlockState getByCombinedId(int i) {
        BlockState iblockdata = (BlockState) Block.BLOCK_STATE_REGISTRY.byId(i);

        return iblockdata == null ? Blocks.AIR.getBlockData() : iblockdata;
    }

    public static Block byItem(@Nullable Item item) {
        return item instanceof BlockItem ? ((BlockItem) item).getBlock() : Blocks.AIR;
    }

    public static BlockState pushEntitiesUp(BlockState iblockdata, BlockState iblockdata1, Level world, BlockPos blockposition) {
        VoxelShape voxelshape = Shapes.joinUnoptimized(iblockdata.getCollisionShape(world, blockposition), iblockdata1.getCollisionShape(world, blockposition), BooleanOp.ONLY_SECOND).move((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
        List<Entity> list = world.getEntities((Entity) null, voxelshape.bounds());
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();
            double d0 = Shapes.collide(Direction.Axis.Y, entity.getBoundingBox().move(0.0D, 1.0D, 0.0D), Stream.of(voxelshape), -1.0D);

            entity.teleportTo(entity.getX(), entity.getY() + 1.0D + d0, entity.getZ());
        }

        return iblockdata1;
    }

    public static VoxelShape box(double d0, double d1, double d2, double d3, double d4, double d5) {
        return Shapes.box(d0 / 16.0D, d1 / 16.0D, d2 / 16.0D, d3 / 16.0D, d4 / 16.0D, d5 / 16.0D);
    }

    public boolean is(Tag<Block> tag) {
        return tag.contains(this);
    }

    public boolean is(Block block) {
        return this == block;
    }

    public static BlockState updateFromNeighbourShapes(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition) {
        BlockState iblockdata1 = iblockdata;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        Direction[] aenumdirection = Block.UPDATE_SHAPE_ORDER;
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection);
            iblockdata1 = iblockdata1.updateState(enumdirection, generatoraccess.getType(blockposition_mutableblockposition), generatoraccess, blockposition, blockposition_mutableblockposition);
        }

        return iblockdata1;
    }

    public static void updateOrDestroy(BlockState iblockdata, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, int i) {
        updateOrDestroy(iblockdata, iblockdata1, generatoraccess, blockposition, i, 512);
    }

    public static void updateOrDestroy(BlockState iblockdata, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, int i, int j) {
        if (iblockdata1 != iblockdata) {
            if (iblockdata1.isAir()) {
                if (!generatoraccess.isClientSide()) {
                    generatoraccess.destroyBlock(blockposition, (i & 32) == 0, (Entity) null, j);
                }
            } else {
                generatoraccess.setBlock(blockposition, iblockdata1, i & -33, j);
            }
        }

    }

    public Block(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        StateDefinition.Builder<Block, BlockState> blockstatelist_a = new StateDefinition.Builder<>(this);

        this.createBlockStateDefinition(blockstatelist_a);
        this.stateDefinition = blockstatelist_a.create(Block::getBlockData, BlockState::new);
        this.registerDefaultState((BlockState) this.stateDefinition.any());
    }

    public static boolean isExceptionForConnection(Block block) {
        return block instanceof LeavesBlock || block == Blocks.BARRIER || block == Blocks.CARVED_PUMPKIN || block == Blocks.JACK_O_LANTERN || block == Blocks.MELON || block == Blocks.PUMPKIN || block.is((Tag) BlockTags.SHULKER_BOXES);
    }

    public boolean isTicking(BlockState iblockdata) {
        return this.isRandomlyTicking;
    }

    public static boolean canSupportRigidBlock(BlockGetter iblockaccess, BlockPos blockposition) {
        BlockState iblockdata = iblockaccess.getType(blockposition);

        return iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition) && iblockdata.isFaceSturdy(iblockaccess, blockposition, Direction.UP) || !Shapes.joinIsNotEmpty(iblockdata.getBlockSupportShape(iblockaccess, blockposition).getFaceShape(Direction.UP), Block.RIGID_SUPPORT_SHAPE, BooleanOp.ONLY_SECOND);
    }

    public static boolean canSupportCenter(LevelReader iworldreader, BlockPos blockposition, Direction enumdirection) {
        BlockState iblockdata = iworldreader.getType(blockposition);

        return enumdirection == Direction.DOWN && iblockdata.is((Tag) BlockTags.UNSTABLE_BOTTOM_CENTER) ? false : !Shapes.joinIsNotEmpty(iblockdata.getBlockSupportShape(iworldreader, blockposition).getFaceShape(enumdirection), Block.CENTER_SUPPORT_SHAPE, BooleanOp.ONLY_SECOND);
    }

    public static boolean isFaceSturdy(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return isFaceFull(iblockdata.getBlockSupportShape(iblockaccess, blockposition), enumdirection);
    }

    public static boolean isFaceFull(VoxelShape voxelshape, Direction enumdirection) {
        VoxelShape voxelshape1 = voxelshape.getFaceShape(enumdirection);

        return isShapeFullBlock(voxelshape1);
    }

    public static boolean isShapeFullBlock(VoxelShape voxelshape) {
        return (Boolean) Block.SHAPE_FULL_BLOCK_CACHE.getUnchecked(voxelshape);
    }

    public boolean propagatesSkylightDown(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return !isShapeFullBlock(iblockdata.getShape(iblockaccess, blockposition)) && iblockdata.getFluidState().isEmpty();
    }

    public void postBreak(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata) {}

    public static List<ItemStack> getDrops(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, @Nullable BlockEntity tileentity) {
        LootContext.Builder loottableinfo_builder = (new LootContext.Builder(worldserver)).withRandom(worldserver.random).set(LootContextParams.BLOCK_POS, blockposition).set(LootContextParams.TOOL, ItemStack.EMPTY).setOptional(LootContextParams.BLOCK_ENTITY, tileentity);

        return iblockdata.getDrops(loottableinfo_builder);
    }

    public static List<ItemStack> getDrops(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, @Nullable BlockEntity tileentity, @Nullable Entity entity, ItemStack itemstack) {
        LootContext.Builder loottableinfo_builder = (new LootContext.Builder(worldserver)).withRandom(worldserver.random).set(LootContextParams.BLOCK_POS, blockposition).set(LootContextParams.TOOL, itemstack).setOptional(LootContextParams.THIS_ENTITY, entity).setOptional(LootContextParams.BLOCK_ENTITY, tileentity);

        return iblockdata.getDrops(loottableinfo_builder);
    }

    public static void dropResources(BlockState iblockdata, Level world, BlockPos blockposition) {
        if (world instanceof ServerLevel) {
            getDrops(iblockdata, (ServerLevel) world, blockposition, (BlockEntity) null).forEach((itemstack) -> {
                popResource(world, blockposition, itemstack);
            });
        }

        iblockdata.spawnAfterBreak(world, blockposition, ItemStack.EMPTY);
    }

    public static void dropResources(BlockState iblockdata, Level world, BlockPos blockposition, @Nullable BlockEntity tileentity) {
        if (world instanceof ServerLevel) {
            getDrops(iblockdata, (ServerLevel) world, blockposition, tileentity).forEach((itemstack) -> {
                popResource(world, blockposition, itemstack);
            });
        }

        iblockdata.spawnAfterBreak(world, blockposition, ItemStack.EMPTY);
    }

    public static void dropItems(BlockState iblockdata, Level world, BlockPos blockposition, @Nullable BlockEntity tileentity, Entity entity, ItemStack itemstack) {
        if (world instanceof ServerLevel) {
            getDrops(iblockdata, (ServerLevel) world, blockposition, tileentity, entity, itemstack).forEach((itemstack1) -> {
                popResource(world, blockposition, itemstack1);
            });
        }

        iblockdata.spawnAfterBreak(world, blockposition, itemstack);
    }

    public static void popResource(Level world, BlockPos blockposition, ItemStack itemstack) {
        if (!world.isClientSide && !itemstack.isEmpty() && world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            float f = 0.5F;
            double d0 = (double) (world.random.nextFloat() * 0.5F) + 0.25D;
            double d1 = (double) (world.random.nextFloat() * 0.5F) + 0.25D;
            double d2 = (double) (world.random.nextFloat() * 0.5F) + 0.25D;
            ItemEntity entityitem = new ItemEntity(world, (double) blockposition.getX() + d0, (double) blockposition.getY() + d1, (double) blockposition.getZ() + d2, itemstack);

            entityitem.setDefaultPickUpDelay();
            // CraftBukkit start
            if (world.captureDrops != null) {
                world.captureDrops.add(entityitem);
            } else {
                world.addFreshEntity(entityitem);
            }
            // CraftBukkit end
        }
    }

    protected void popExperience(Level world, BlockPos blockposition, int i) {
        if (!world.isClientSide && world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            while (i > 0) {
                int j = ExperienceOrb.getExperienceValue(i);

                i -= j;
                world.addFreshEntity(new ExperienceOrb(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, j));
            }
        }

    }

    public float getExplosionResistance() {
        return this.explosionResistance;
    }

    public void wasExploded(Level world, BlockPos blockposition, Explosion explosion) {}

    public void stepOn(Level world, BlockPos blockposition, Entity entity) {}

    @Nullable
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return this.getBlockData();
    }

    public void playerDestroy(Level world, Player entityhuman, BlockPos blockposition, BlockState iblockdata, @Nullable BlockEntity tileentity, ItemStack itemstack) {
        entityhuman.awardStat(Stats.BLOCK_MINED.get(this));
        entityhuman.causeFoodExhaustion(0.005F);
        dropItems(iblockdata, world, blockposition, tileentity, entityhuman, itemstack);
    }

    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, @Nullable LivingEntity entityliving, ItemStack itemstack) {}

    public boolean isPossibleToRespawnInThis() {
        return !this.material.isSolid() && !this.material.isLiquid();
    }

    public String getDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("block", Registry.BLOCK.getKey(this));
        }

        return this.descriptionId;
    }

    public void fallOn(Level world, BlockPos blockposition, Entity entity, float f) {
        entity.causeFallDamage(f, 1.0F);
    }

    public void updateEntityAfterFallOn(BlockGetter iblockaccess, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
    }

    public void fillItemCategory(CreativeModeTab creativemodetab, NonNullList<ItemStack> nonnulllist) {
        nonnulllist.add(new ItemStack(this));
    }

    public float getFriction() {
        return this.friction;
    }

    public float getSpeedFactor() {
        return this.speedFactor;
    }

    public float getJumpFactor() {
        return this.jumpFactor;
    }

    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        world.levelEvent(entityhuman, 2001, blockposition, getCombinedId(iblockdata));
        if (this.is((Tag) BlockTags.GUARDED_BY_PIGLINS)) {
            PiglinAi.angerNearbyPiglins(entityhuman, false);
        }

    }

    public void handleRain(Level world, BlockPos blockposition) {}

    public boolean dropFromExplosion(Explosion explosion) {
        return true;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {}

    public StateDefinition<Block, BlockState> getStateDefinition() {
        return this.stateDefinition;
    }

    protected final void registerDefaultState(BlockState iblockdata) {
        this.defaultBlockState = iblockdata;
    }

    public final BlockState getBlockData() {
        return this.defaultBlockState;
    }

    public SoundType getStepSound(BlockState iblockdata) {
        return this.soundType;
    }

    @Override
    public Item asItem() {
        if (this.item == null) {
            this.item = Item.byBlock(this);
        }

        return this.item;
    }

    public boolean hasDynamicShape() {
        return this.dynamicShape;
    }

    public String toString() {
        return "Block{" + Registry.BLOCK.getKey(this) + "}";
    }

    @Override
    protected Block asBlock() {
        return this;
    }

    // CraftBukkit start
    public int getExpDrop(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        return 0;
    }
    // CraftBukkit end

    // Spigot start
    public static float range(float min, float value, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
    // Spigot end

    public static final class BlockStatePairKey {

        private final BlockState first;
        private final BlockState second;
        private final Direction direction;

        public BlockStatePairKey(BlockState iblockdata, BlockState iblockdata1, Direction enumdirection) {
            this.first = iblockdata;
            this.second = iblockdata1;
            this.direction = enumdirection;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (!(object instanceof Block.BlockStatePairKey)) {
                return false;
            } else {
                Block.BlockStatePairKey block_a = (Block.BlockStatePairKey) object;

                return this.first == block_a.first && this.second == block_a.second && this.direction == block_a.direction;
            }
        }

        public int hashCode() {
            int i = this.first.hashCode();

            i = 31 * i + this.second.hashCode();
            i = 31 * i + this.direction.hashCode();
            return i;
        }
    }
}
