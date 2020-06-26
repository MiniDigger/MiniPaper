package net.minecraft.world.level.block.state;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BlockBehaviour {

    protected static final Direction[] UPDATE_SHAPE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};
    protected final Material material;
    protected final boolean hasCollision;
    protected final float explosionResistance;
    protected final boolean isRandomlyTicking;
    protected final SoundType soundType;
    protected final float friction;
    protected final float speedFactor;
    protected final float jumpFactor;
    protected final boolean dynamicShape;
    protected final BlockBehaviour.Info properties;
    @Nullable
    protected ResourceLocation drops;

    public BlockBehaviour(BlockBehaviour.Info blockbase_info) {
        this.material = blockbase_info.a;
        this.hasCollision = blockbase_info.c;
        this.drops = blockbase_info.m;
        this.explosionResistance = blockbase_info.f;
        this.isRandomlyTicking = blockbase_info.i;
        this.soundType = blockbase_info.d;
        this.friction = blockbase_info.j;
        this.speedFactor = blockbase_info.k;
        this.jumpFactor = blockbase_info.l;
        this.dynamicShape = blockbase_info.v;
        this.properties = blockbase_info;
    }

    @Deprecated
    public void updateIndirectNeighbourShapes(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, int i, int j) {}

    @Deprecated
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        switch (pathmode) {
            case LAND:
                return !iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition);
            case WATER:
                return iblockaccess.getFluidState(blockposition).is((Tag) FluidTags.WATER);
            case AIR:
                return !iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition);
            default:
                return false;
        }
    }

    @Deprecated
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return iblockdata;
    }

    @Deprecated
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        DebugPackets.sendNeighborsUpdatePacket(world, blockposition);
    }

    @Deprecated
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        org.spigotmc.AsyncCatcher.catchOp("block onPlace"); // Spigot
    }

    @Deprecated
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        org.spigotmc.AsyncCatcher.catchOp("block remove"); // Spigot
        if (this.isEntityBlock() && !iblockdata.is(iblockdata1.getBlock())) {
            world.removeBlockEntity(blockposition);
        }

    }

    @Deprecated
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        return InteractionResult.PASS;
    }

    @Deprecated
    public boolean triggerEvent(BlockState iblockdata, Level world, BlockPos blockposition, int i, int j) {
        return false;
    }

    @Deprecated
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    @Deprecated
    public boolean useShapeForLightOcclusion(BlockState iblockdata) {
        return false;
    }

    @Deprecated
    public boolean isPowerSource(BlockState iblockdata) {
        return false;
    }

    @Deprecated
    public PushReaction getPushReaction(BlockState iblockdata) {
        return this.material.getPushReaction();
    }

    @Deprecated
    public FluidState getFluidState(BlockState iblockdata) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Deprecated
    public boolean isComplexRedstone(BlockState iblockdata) {
        return false;
    }

    public BlockBehaviour.OffsetType getOffsetType() {
        return BlockBehaviour.OffsetType.NONE;
    }

    @Deprecated
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return iblockdata;
    }

    @Deprecated
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata;
    }

    @Deprecated
    public boolean canBeReplaced(BlockState iblockdata, BlockPlaceContext blockactioncontext) {
        return this.material.isReplaceable() && (blockactioncontext.getItemInHand().isEmpty() || blockactioncontext.getItemInHand().getItem() != this.asItem());
    }

    @Deprecated
    public boolean canBeReplaced(BlockState iblockdata, Fluid fluidtype) {
        return this.material.isReplaceable() || !this.material.isSolid();
    }

    @Deprecated
    public List<ItemStack> getDrops(BlockState iblockdata, LootContext.Builder loottableinfo_builder) {
        ResourceLocation minecraftkey = this.getLootTable();

        if (minecraftkey == BuiltInLootTables.EMPTY) {
            return Collections.emptyList();
        } else {
            LootContext loottableinfo = loottableinfo_builder.set(LootContextParams.BLOCK_STATE, iblockdata).create(LootContextParamSets.BLOCK);
            ServerLevel worldserver = loottableinfo.getLevel();
            LootTable loottable = worldserver.getServer().getLootTables().get(minecraftkey);

            return loottable.getRandomItems(loottableinfo);
        }
    }

    @Deprecated
    public VoxelShape getOcclusionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.getShape(iblockaccess, blockposition);
    }

    @Deprecated
    public VoxelShape getBlockSupportShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return this.getCollisionShape(iblockdata, iblockaccess, blockposition, CollisionContext.empty());
    }

    @Deprecated
    public VoxelShape getInteractionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return Shapes.empty();
    }

    @Deprecated
    public int getLightBlock(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.isSolidRender(iblockaccess, blockposition) ? iblockaccess.getMaxLightLevel() : (iblockdata.propagatesSkylightDown(iblockaccess, blockposition) ? 0 : 1);
    }

    @Nullable
    @Deprecated
    public MenuProvider getInventory(BlockState iblockdata, Level world, BlockPos blockposition) {
        return null;
    }

    @Deprecated
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return true;
    }

    @Deprecated
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        return 0;
    }

    @Deprecated
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return Shapes.block();
    }

    @Deprecated
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return this.hasCollision ? iblockdata.getShape(iblockaccess, blockposition) : Shapes.empty();
    }

    @Deprecated
    public VoxelShape getVisualShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return this.getCollisionShape(iblockdata, iblockaccess, blockposition, voxelshapecollision);
    }

    @Deprecated
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        this.tickAlways(iblockdata, worldserver, blockposition, random);
    }

    @Deprecated
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {}

    @Deprecated
    public float getDamage(BlockState iblockdata, Player entityhuman, BlockGetter iblockaccess, BlockPos blockposition) {
        float f = iblockdata.getDestroySpeed(iblockaccess, blockposition);

        if (f == -1.0F) {
            return 0.0F;
        } else {
            int i = entityhuman.hasBlock(iblockdata) ? 30 : 100;

            return entityhuman.getDestroySpeed(iblockdata) / f / (float) i;
        }
    }

    @Deprecated
    public void dropNaturally(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {}

    @Deprecated
    public void attack(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman) {}

    @Deprecated
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return 0;
    }

    @Deprecated
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {}

    @Deprecated
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return 0;
    }

    public final boolean isEntityBlock() {
        return this instanceof EntityBlock;
    }

    public final ResourceLocation getLootTable() {
        if (this.drops == null) {
            ResourceLocation minecraftkey = Registry.BLOCK.getKey(this.asBlock());

            this.drops = new ResourceLocation(minecraftkey.getNamespace(), "blocks/" + minecraftkey.getPath());
        }

        return this.drops;
    }

    @Deprecated
    public void onProjectileHit(Level world, BlockState iblockdata, BlockHitResult movingobjectpositionblock, Projectile iprojectile) {}

    public abstract Item asItem();

    protected abstract Block asBlock();

    public MaterialColor defaultMaterialColor() {
        return (MaterialColor) this.properties.b.apply(this.asBlock().getBlockData());
    }

    public interface StateArgumentPredicate<A> {

        boolean test(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, A a0);
    }

    public interface StatePredicate {

        boolean test(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition);
    }

    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> {

        private final int lightEmission;
        private final boolean useShapeForLightOcclusion;
        private final boolean isAir;
        private final Material material;
        private final MaterialColor materialColor;
        public final float destroySpeed;
        private final boolean requiresCorrectToolForDrops;
        private final boolean canOcclude;
        private final BlockBehaviour.StatePredicate isRedstoneConductor;
        private final BlockBehaviour.StatePredicate isSuffocating;
        private final BlockBehaviour.StatePredicate isViewBlocking;
        private final BlockBehaviour.StatePredicate hasPostProcess;
        private final BlockBehaviour.StatePredicate emissiveRendering;
        @Nullable
        protected BlockBehaviour.BlockStateBase.Cache cache;

        protected BlockStateBase(Block block, ImmutableMap<Property<?>, Comparable<?>> immutablemap, MapCodec<BlockState> mapcodec) {
            super(block, immutablemap, mapcodec);
            BlockBehaviour.Info blockbase_info = block.properties;

            this.lightEmission = blockbase_info.e.applyAsInt(this.asState());
            this.useShapeForLightOcclusion = block.useShapeForLightOcclusion(this.asState());
            this.isAir = blockbase_info.o;
            this.material = blockbase_info.a;
            this.materialColor = (MaterialColor) blockbase_info.b.apply(this.asState());
            this.destroySpeed = blockbase_info.g;
            this.requiresCorrectToolForDrops = blockbase_info.h;
            this.canOcclude = blockbase_info.n;
            this.isRedstoneConductor = blockbase_info.q;
            this.isSuffocating = blockbase_info.r;
            this.isViewBlocking = blockbase_info.s;
            this.hasPostProcess = blockbase_info.t;
            this.emissiveRendering = blockbase_info.u;
        }

        public void initCache() {
            if (!this.getBlock().hasDynamicShape()) {
                this.cache = new BlockBehaviour.BlockStateBase.Cache(this.asState());
            }

        }

        public Block getBlock() {
            return (Block) this.owner;
        }

        public Material getMaterial() {
            return this.material;
        }

        public boolean isValidSpawn(BlockGetter iblockaccess, BlockPos blockposition, EntityType<?> entitytypes) {
            return this.getBlock().properties.p.test(this.asState(), iblockaccess, blockposition, entitytypes);
        }

        public boolean propagatesSkylightDown(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.cache != null ? this.cache.propagatesSkylightDown : this.getBlock().propagatesSkylightDown(this.asState(), iblockaccess, blockposition);
        }

        public int getLightBlock(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.cache != null ? this.cache.lightBlock : this.getBlock().getLightBlock(this.asState(), iblockaccess, blockposition);
        }

        public VoxelShape getFaceOcclusionShape(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
            return this.cache != null && this.cache.occlusionShapes != null ? this.cache.occlusionShapes[enumdirection.ordinal()] : Shapes.getFaceShape(this.getOcclusionShape(iblockaccess, blockposition), enumdirection);
        }

        public VoxelShape getOcclusionShape(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.getBlock().getOcclusionShape(this.asState(), iblockaccess, blockposition);
        }

        public boolean hasLargeCollisionShape() {
            return this.cache == null || this.cache.largeCollisionShape;
        }

        public boolean useShapeForLightOcclusion() {
            return this.useShapeForLightOcclusion;
        }

        public int getLightEmission() {
            return this.lightEmission;
        }

        public boolean isAir() {
            return this.isAir;
        }

        public MaterialColor getMapColor(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.materialColor;
        }

        public BlockState rotate(Rotation enumblockrotation) {
            return this.getBlock().rotate(this.asState(), enumblockrotation);
        }

        public BlockState mirror(Mirror enumblockmirror) {
            return this.getBlock().mirror(this.asState(), enumblockmirror);
        }

        public RenderShape getRenderShape() {
            return this.getBlock().getRenderShape(this.asState());
        }

        public boolean isRedstoneConductor(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.isRedstoneConductor.test(this.asState(), iblockaccess, blockposition);
        }

        public boolean isSignalSource() {
            return this.getBlock().isPowerSource(this.asState());
        }

        public int getSignal(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
            return this.getBlock().getSignal(this.asState(), iblockaccess, blockposition, enumdirection);
        }

        public boolean hasAnalogOutputSignal() {
            return this.getBlock().isComplexRedstone(this.asState());
        }

        public int getAnalogOutputSignal(Level world, BlockPos blockposition) {
            return this.getBlock().getAnalogOutputSignal(this.asState(), world, blockposition);
        }

        public float getDestroySpeed(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.destroySpeed;
        }

        public float getDestroyProgress(Player entityhuman, BlockGetter iblockaccess, BlockPos blockposition) {
            return this.getBlock().getDamage(this.asState(), entityhuman, iblockaccess, blockposition);
        }

        public int getDirectSignal(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
            return this.getBlock().getDirectSignal(this.asState(), iblockaccess, blockposition, enumdirection);
        }

        public PushReaction getPistonPushReaction() {
            return this.getBlock().getPushReaction(this.asState());
        }

        public boolean isSolidRender(BlockGetter iblockaccess, BlockPos blockposition) {
            if (this.cache != null) {
                return this.cache.solidRender;
            } else {
                BlockState iblockdata = this.asState();

                return iblockdata.canOcclude() ? Block.isShapeFullBlock(iblockdata.getOcclusionShape(iblockaccess, blockposition)) : false;
            }
        }

        public boolean canOcclude() {
            return this.canOcclude;
        }

        public VoxelShape getShape(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.getShape(iblockaccess, blockposition, CollisionContext.empty());
        }

        public VoxelShape getShape(BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
            return this.getBlock().getShape(this.asState(), iblockaccess, blockposition, voxelshapecollision);
        }

        public VoxelShape getCollisionShape(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.cache != null ? this.cache.collisionShape : this.getCollisionShape(iblockaccess, blockposition, CollisionContext.empty());
        }

        public VoxelShape getCollisionShape(BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
            return this.getBlock().getCollisionShape(this.asState(), iblockaccess, blockposition, voxelshapecollision);
        }

        public VoxelShape getBlockSupportShape(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.getBlock().getBlockSupportShape(this.asState(), iblockaccess, blockposition);
        }

        public VoxelShape getVisualShape(BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
            return this.getBlock().getVisualShape(this.asState(), iblockaccess, blockposition, voxelshapecollision);
        }

        public VoxelShape getInteractionShape(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.getBlock().getInteractionShape(this.asState(), iblockaccess, blockposition);
        }

        public final boolean entityCanStandOn(BlockGetter iblockaccess, BlockPos blockposition, Entity entity) {
            return this.entityCanStandOnFace(iblockaccess, blockposition, entity, Direction.UP);
        }

        public final boolean entityCanStandOnFace(BlockGetter iblockaccess, BlockPos blockposition, Entity entity, Direction enumdirection) {
            return Block.isFaceFull(this.getCollisionShape(iblockaccess, blockposition, CollisionContext.of(entity)), enumdirection);
        }

        public Vec3 getOffset(BlockGetter iblockaccess, BlockPos blockposition) {
            BlockBehaviour.OffsetType blockbase_enumrandomoffset = this.getBlock().getOffsetType();

            if (blockbase_enumrandomoffset == BlockBehaviour.OffsetType.NONE) {
                return Vec3.ZERO;
            } else {
                long i = Mth.getSeed(blockposition.getX(), 0, blockposition.getZ());

                return new Vec3(((double) ((float) (i & 15L) / 15.0F) - 0.5D) * 0.5D, blockbase_enumrandomoffset == BlockBehaviour.OffsetType.XYZ ? ((double) ((float) (i >> 4 & 15L) / 15.0F) - 1.0D) * 0.2D : 0.0D, ((double) ((float) (i >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D);
            }
        }

        public boolean triggerEvent(Level world, BlockPos blockposition, int i, int j) {
            return this.getBlock().triggerEvent(this.asState(), world, blockposition, i, j);
        }

        public void neighborChanged(Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
            this.getBlock().doPhysics(this.asState(), world, blockposition, block, blockposition1, flag);
        }

        public final void updateNeighbourShapes(LevelAccessor generatoraccess, BlockPos blockposition, int i) {
            this.updateNeighbourShapes(generatoraccess, blockposition, i, 512);
        }

        public final void updateNeighbourShapes(LevelAccessor generatoraccess, BlockPos blockposition, int i, int j) {
            this.getBlock();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
            Direction[] aenumdirection = BlockBehaviour.UPDATE_SHAPE_ORDER;
            int k = aenumdirection.length;

            for (int l = 0; l < k; ++l) {
                Direction enumdirection = aenumdirection[l];

                blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection);
                BlockState iblockdata = generatoraccess.getType(blockposition_mutableblockposition);
                BlockState iblockdata1 = iblockdata.updateState(enumdirection.getOpposite(), this.asState(), generatoraccess, blockposition_mutableblockposition, blockposition);

                Block.updateOrDestroy(iblockdata, iblockdata1, generatoraccess, blockposition_mutableblockposition, i, j);
            }

        }

        public final void updateIndirectNeighbourShapes(LevelAccessor generatoraccess, BlockPos blockposition, int i) {
            this.updateIndirectNeighbourShapes(generatoraccess, blockposition, i, 512);
        }

        public void updateIndirectNeighbourShapes(LevelAccessor generatoraccess, BlockPos blockposition, int i, int j) {
            this.getBlock().updateIndirectNeighbourShapes(this.asState(), generatoraccess, blockposition, i, j);
        }

        public void onPlace(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag) {
            this.getBlock().onPlace(this.asState(), world, blockposition, iblockdata, flag);
        }

        public void remove(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag) {
            this.getBlock().remove(this.asState(), world, blockposition, iblockdata, flag);
        }

        public void tick(ServerLevel worldserver, BlockPos blockposition, Random random) {
            this.getBlock().tickAlways(this.asState(), worldserver, blockposition, random);
        }

        public void randomTick(ServerLevel worldserver, BlockPos blockposition, Random random) {
            this.getBlock().tick(this.asState(), worldserver, blockposition, random);
        }

        public void entityInside(Level world, BlockPos blockposition, Entity entity) {
            this.getBlock().entityInside(this.asState(), world, blockposition, entity);
        }

        public void spawnAfterBreak(Level world, BlockPos blockposition, ItemStack itemstack) {
            this.getBlock().dropNaturally(this.asState(), world, blockposition, itemstack);
        }

        public List<ItemStack> getDrops(LootContext.Builder loottableinfo_builder) {
            return this.getBlock().getDrops(this.asState(), loottableinfo_builder);
        }

        public InteractionResult use(Level world, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
            return this.getBlock().interact(this.asState(), world, movingobjectpositionblock.getBlockPos(), entityhuman, enumhand, movingobjectpositionblock);
        }

        public void attack(Level world, BlockPos blockposition, Player entityhuman) {
            this.getBlock().attack(this.asState(), world, blockposition, entityhuman);
        }

        public boolean isSuffocating(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.isSuffocating.test(this.asState(), iblockaccess, blockposition);
        }

        public BlockState updateState(Direction enumdirection, BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
            return this.getBlock().updateState(this.asState(), enumdirection, iblockdata, generatoraccess, blockposition, blockposition1);
        }

        public boolean isPathfindable(BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
            return this.getBlock().isPathfindable(this.asState(), iblockaccess, blockposition, pathmode);
        }

        public boolean canBeReplaced(BlockPlaceContext blockactioncontext) {
            return this.getBlock().canBeReplaced(this.asState(), blockactioncontext);
        }

        public boolean canBeReplaced(Fluid fluidtype) {
            return this.getBlock().canBeReplaced(this.asState(), fluidtype);
        }

        public boolean canSurvive(LevelReader iworldreader, BlockPos blockposition) {
            return this.getBlock().canPlace(this.asState(), iworldreader, blockposition);
        }

        public boolean hasPostProcess(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.hasPostProcess.test(this.asState(), iblockaccess, blockposition);
        }

        @Nullable
        public MenuProvider getMenuProvider(Level world, BlockPos blockposition) {
            return this.getBlock().getInventory(this.asState(), world, blockposition);
        }

        public boolean is(Tag<Block> tag) {
            return this.getBlock().is(tag);
        }

        public boolean is(Tag<Block> tag, Predicate<BlockBehaviour.BlockStateBase> predicate) {
            return this.getBlock().is(tag) && predicate.test(this);
        }

        public boolean is(Block block) {
            return this.getBlock().is(block);
        }

        public FluidState getFluidState() {
            return this.getBlock().getFluidState(this.asState());
        }

        public boolean isRandomlyTicking() {
            return this.getBlock().isTicking(this.asState());
        }

        public SoundType getStepSound() {
            return this.getBlock().getStepSound(this.asState());
        }

        public void onProjectileHit(Level world, BlockState iblockdata, BlockHitResult movingobjectpositionblock, Projectile iprojectile) {
            this.getBlock().onProjectileHit(world, iblockdata, movingobjectpositionblock, iprojectile);
        }

        public boolean isFaceSturdy(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
            return this.cache != null ? this.cache.isFaceSturdy[enumdirection.ordinal()] : Block.isFaceSturdy(this.asState(), iblockaccess, blockposition, enumdirection);
        }

        public boolean isCollisionShapeFullBlock(BlockGetter iblockaccess, BlockPos blockposition) {
            return this.cache != null ? this.cache.isCollisionShapeFullBlock : Block.isShapeFullBlock(this.getCollisionShape(iblockaccess, blockposition));
        }

        protected abstract BlockState asState();

        public boolean requiresCorrectToolForDrops() {
            return this.requiresCorrectToolForDrops;
        }

        static final class Cache {

            private static final Direction[] DIRECTIONS = Direction.values();
            protected final boolean solidRender;
            private final boolean propagatesSkylightDown;
            private final int lightBlock;
            @Nullable
            private final VoxelShape[] occlusionShapes;
            protected final VoxelShape collisionShape;
            protected final boolean largeCollisionShape;
            protected final boolean[] isFaceSturdy;
            protected final boolean isCollisionShapeFullBlock;

            private Cache(BlockState iblockdata) {
                Block block = iblockdata.getBlock();

                this.solidRender = iblockdata.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                this.propagatesSkylightDown = block.propagatesSkylightDown(iblockdata, (BlockGetter) EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                this.lightBlock = block.getLightBlock(iblockdata, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                int i;

                if (!iblockdata.canOcclude()) {
                    this.occlusionShapes = null;
                } else {
                    this.occlusionShapes = new VoxelShape[DIRECTIONS.length];
                    VoxelShape voxelshape = block.getOcclusionShape(iblockdata, (BlockGetter) EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    Direction[] aenumdirection = DIRECTIONS;

                    i = aenumdirection.length;

                    for (int j = 0; j < i; ++j) {
                        Direction enumdirection = aenumdirection[j];

                        this.occlusionShapes[enumdirection.ordinal()] = Shapes.getFaceShape(voxelshape, enumdirection);
                    }
                }

                this.collisionShape = block.getCollisionShape(iblockdata, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
                this.largeCollisionShape = Arrays.stream(Direction.Axis.values()).anyMatch((enumdirection_enumaxis) -> {
                    return this.collisionShape.min(enumdirection_enumaxis) < 0.0D || this.collisionShape.max(enumdirection_enumaxis) > 1.0D;
                });
                this.isFaceSturdy = new boolean[6];
                Direction[] aenumdirection1 = DIRECTIONS;
                int k = aenumdirection1.length;

                for (i = 0; i < k; ++i) {
                    Direction enumdirection1 = aenumdirection1[i];

                    this.isFaceSturdy[enumdirection1.ordinal()] = Block.isFaceSturdy(iblockdata, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, enumdirection1);
                }

                this.isCollisionShapeFullBlock = Block.isShapeFullBlock(iblockdata.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            }
        }
    }

    public static class Info {

        private Material a;
        private Function<BlockState, MaterialColor> b;
        private boolean c;
        private SoundType d;
        private ToIntFunction<BlockState> e;
        private float f;
        private float g;
        private boolean h;
        private boolean i;
        private float j;
        private float k;
        private float l;
        private ResourceLocation m;
        private boolean n;
        private boolean o;
        private BlockBehaviour.StateArgumentPredicate<EntityType<?>> p;
        private BlockBehaviour.StatePredicate q;
        private BlockBehaviour.StatePredicate r;
        private BlockBehaviour.StatePredicate s;
        private BlockBehaviour.StatePredicate t;
        private BlockBehaviour.StatePredicate u;
        private boolean v;

        private Info(Material material, MaterialColor materialmapcolor) {
            this(material, (iblockdata) -> {
                return materialmapcolor;
            });
        }

        private Info(Material material, Function<BlockState, MaterialColor> function) {
            this.c = true;
            this.d = SoundType.STONE;
            this.e = (iblockdata) -> {
                return 0;
            };
            this.j = 0.6F;
            this.k = 1.0F;
            this.l = 1.0F;
            this.n = true;
            this.p = (iblockdata, iblockaccess, blockposition, entitytypes) -> {
                return iblockdata.isFaceSturdy(iblockaccess, blockposition, Direction.UP) && iblockdata.getLightEmission() < 14;
            };
            this.q = (iblockdata, iblockaccess, blockposition) -> {
                return iblockdata.getMaterial().isSolidBlocking() && iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition);
            };
            this.r = (iblockdata, iblockaccess, blockposition) -> {
                return this.a.blocksMotion() && iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition);
            };
            this.s = this.r;
            this.t = (iblockdata, iblockaccess, blockposition) -> {
                return false;
            };
            this.u = (iblockdata, iblockaccess, blockposition) -> {
                return false;
            };
            this.a = material;
            this.b = function;
        }

        public static BlockBehaviour.Info a(Material material) {
            return a(material, material.getColor());
        }

        public static BlockBehaviour.Info a(Material material, DyeColor enumcolor) {
            return a(material, enumcolor.getMaterialColor());
        }

        public static BlockBehaviour.Info a(Material material, MaterialColor materialmapcolor) {
            return new BlockBehaviour.Info(material, materialmapcolor);
        }

        public static BlockBehaviour.Info a(Material material, Function<BlockState, MaterialColor> function) {
            return new BlockBehaviour.Info(material, function);
        }

        public static BlockBehaviour.Info a(BlockBehaviour blockbase) {
            BlockBehaviour.Info blockbase_info = new BlockBehaviour.Info(blockbase.material, blockbase.properties.b);

            blockbase_info.a = blockbase.properties.a;
            blockbase_info.g = blockbase.properties.g;
            blockbase_info.f = blockbase.properties.f;
            blockbase_info.c = blockbase.properties.c;
            blockbase_info.i = blockbase.properties.i;
            blockbase_info.e = blockbase.properties.e;
            blockbase_info.b = blockbase.properties.b;
            blockbase_info.d = blockbase.properties.d;
            blockbase_info.j = blockbase.properties.j;
            blockbase_info.k = blockbase.properties.k;
            blockbase_info.v = blockbase.properties.v;
            blockbase_info.n = blockbase.properties.n;
            blockbase_info.o = blockbase.properties.o;
            blockbase_info.h = blockbase.properties.h;
            return blockbase_info;
        }

        public BlockBehaviour.Info a() {
            this.c = false;
            this.n = false;
            return this;
        }

        public BlockBehaviour.Info b() {
            this.n = false;
            return this;
        }

        public BlockBehaviour.Info a(float f) {
            this.j = f;
            return this;
        }

        public BlockBehaviour.Info b(float f) {
            this.k = f;
            return this;
        }

        public BlockBehaviour.Info c(float f) {
            this.l = f;
            return this;
        }

        public BlockBehaviour.Info a(SoundType soundeffecttype) {
            this.d = soundeffecttype;
            return this;
        }

        public BlockBehaviour.Info a(ToIntFunction<BlockState> tointfunction) {
            this.e = tointfunction;
            return this;
        }

        public BlockBehaviour.Info a(float f, float f1) {
            this.g = f;
            this.f = Math.max(0.0F, f1);
            return this;
        }

        public BlockBehaviour.Info c() {
            return this.d(0.0F);
        }

        public BlockBehaviour.Info d(float f) {
            this.a(f, f);
            return this;
        }

        public BlockBehaviour.Info d() {
            this.i = true;
            return this;
        }

        public BlockBehaviour.Info e() {
            this.v = true;
            return this;
        }

        public BlockBehaviour.Info f() {
            this.m = BuiltInLootTables.EMPTY;
            return this;
        }

        public BlockBehaviour.Info a(Block block) {
            this.m = block.getLootTable();
            return this;
        }

        public BlockBehaviour.Info g() {
            this.o = true;
            return this;
        }

        public BlockBehaviour.Info a(BlockBehaviour.StateArgumentPredicate<EntityType<?>> blockbase_d) {
            this.p = blockbase_d;
            return this;
        }

        public BlockBehaviour.Info a(BlockBehaviour.StatePredicate blockbase_e) {
            this.q = blockbase_e;
            return this;
        }

        public BlockBehaviour.Info b(BlockBehaviour.StatePredicate blockbase_e) {
            this.r = blockbase_e;
            return this;
        }

        public BlockBehaviour.Info c(BlockBehaviour.StatePredicate blockbase_e) {
            this.s = blockbase_e;
            return this;
        }

        public BlockBehaviour.Info d(BlockBehaviour.StatePredicate blockbase_e) {
            this.t = blockbase_e;
            return this;
        }

        public BlockBehaviour.Info e(BlockBehaviour.StatePredicate blockbase_e) {
            this.u = blockbase_e;
            return this;
        }

        public BlockBehaviour.Info h() {
            this.h = true;
            return this;
        }
    }

    public static enum OffsetType {

        NONE, XZ, XYZ;

        private OffsetType() {}
    }
}
