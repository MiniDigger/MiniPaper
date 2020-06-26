package net.minecraft.world.level.block;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BedBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    protected static final VoxelShape BASE = Block.box(0.0D, 3.0D, 0.0D, 16.0D, 9.0D, 16.0D);
    protected static final VoxelShape LEG_NORTH_WEST = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 3.0D, 3.0D);
    protected static final VoxelShape LEG_SOUTH_WEST = Block.box(0.0D, 0.0D, 13.0D, 3.0D, 3.0D, 16.0D);
    protected static final VoxelShape LEG_NORTH_EAST = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 3.0D, 3.0D);
    protected static final VoxelShape LEG_SOUTH_EAST = Block.box(13.0D, 0.0D, 13.0D, 16.0D, 3.0D, 16.0D);
    protected static final VoxelShape NORTH_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_NORTH_WEST, BedBlock.LEG_NORTH_EAST);
    protected static final VoxelShape SOUTH_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_SOUTH_WEST, BedBlock.LEG_SOUTH_EAST);
    protected static final VoxelShape WEST_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_NORTH_WEST, BedBlock.LEG_SOUTH_WEST);
    protected static final VoxelShape EAST_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_NORTH_EAST, BedBlock.LEG_SOUTH_EAST);
    private final DyeColor color;

    public BedBlock(DyeColor enumcolor, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.color = enumcolor;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BedBlock.PART, BedPart.FOOT)).setValue(BedBlock.OCCUPIED, false));
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (world.isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            if (iblockdata.getValue(BedBlock.PART) != BedPart.HEAD) {
                blockposition = blockposition.relative((Direction) iblockdata.getValue(BedBlock.FACING));
                iblockdata = world.getType(blockposition);
                if (!iblockdata.is((Block) this)) {
                    return InteractionResult.CONSUME;
                }
            }

            // CraftBukkit - moved world and biome check into EntityHuman
            if (false && !canSetSpawn(world)) {
                world.removeBlock(blockposition, false);
                BlockPos blockposition1 = blockposition.relative(((Direction) iblockdata.getValue(BedBlock.FACING)).getOpposite());

                if (world.getType(blockposition1).is((Block) this)) {
                    world.removeBlock(blockposition1, false);
                }

                world.createExplosion((Entity) null, DamageSource.badRespawnPointExplosion(), (ExplosionDamageCalculator) null, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, 5.0F, true, Explosion.BlockInteraction.DESTROY);
                return InteractionResult.SUCCESS;
            } else if ((Boolean) iblockdata.getValue(BedBlock.OCCUPIED)) {
                if (!this.kickVillagerOutOfBed(world, blockposition)) {
                    entityhuman.displayClientMessage((Component) (new TranslatableComponent("block.minecraft.bed.occupied")), true);
                }

                return InteractionResult.SUCCESS;
            } else {
                // CraftBukkit start
                BlockState finaliblockdata = iblockdata;
                BlockPos finalblockposition = blockposition;
                // CraftBukkit end
                entityhuman.startSleepInBed(blockposition).ifLeft((entityhuman_enumbedresult) -> {
                    // CraftBukkit start - handling bed explosion from below here
                    if (entityhuman_enumbedresult == Player.BedSleepingProblem.NOT_POSSIBLE_HERE) {
                        this.explodeBed(finaliblockdata, world, finalblockposition);
                    } else
                    // CraftBukkit end
                    if (entityhuman_enumbedresult != null) {
                        entityhuman.displayClientMessage(entityhuman_enumbedresult.getMessage(), true);
                    }

                });
                return InteractionResult.SUCCESS;
            }
        }
    }

    // CraftBukkit start
    private InteractionResult explodeBed(BlockState iblockdata, Level world, BlockPos blockposition) {
        {
            {
                world.removeBlock(blockposition, false);
                BlockPos blockposition1 = blockposition.relative(((Direction) iblockdata.getValue(BedBlock.FACING)).getOpposite());

                if (world.getType(blockposition1).getBlock() == this) {
                    world.removeBlock(blockposition1, false);
                }

                world.createExplosion((Entity) null, DamageSource.badRespawnPointExplosion(), (ExplosionDamageCalculator) null, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, 5.0F, true, Explosion.BlockInteraction.DESTROY);
                return InteractionResult.SUCCESS;
            }
        }
    }
    // CraftBukkit end

    public static boolean canSetSpawn(Level world) {
        // CraftBukkit - moved world and biome check into EntityHuman
        return true || world.dimensionType().bedWorks();
    }

    private boolean kickVillagerOutOfBed(Level world, BlockPos blockposition) {
        List<Villager> list = world.getEntitiesOfClass(Villager.class, new AABB(blockposition), LivingEntity::isSleeping);

        if (list.isEmpty()) {
            return false;
        } else {
            ((Villager) list.get(0)).stopSleeping();
            return true;
        }
    }

    @Override
    public void fallOn(Level world, BlockPos blockposition, Entity entity, float f) {
        super.fallOn(world, blockposition, entity, f * 0.5F);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter iblockaccess, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(iblockaccess, entity);
        } else {
            this.bounceUp(entity);
        }

    }

    private void bounceUp(Entity entity) {
        Vec3 vec3d = entity.getDeltaMovement();

        if (vec3d.y < 0.0D) {
            double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;

            entity.setDeltaMovement(vec3d.x, -vec3d.y * 0.6600000262260437D * d0, vec3d.z);
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection == getNeighbourDirection((BedPart) iblockdata.getValue(BedBlock.PART), (Direction) iblockdata.getValue(BedBlock.FACING)) ? (iblockdata1.is((Block) this) && iblockdata1.getValue(BedBlock.PART) != iblockdata.getValue(BedBlock.PART) ? (BlockState) iblockdata.setValue(BedBlock.OCCUPIED, iblockdata1.getValue(BedBlock.OCCUPIED)) : Blocks.AIR.getBlockData()) : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    private static Direction getNeighbourDirection(BedPart blockpropertybedpart, Direction enumdirection) {
        return blockpropertybedpart == BedPart.FOOT ? enumdirection : enumdirection.getOpposite();
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!world.isClientSide && entityhuman.isCreative()) {
            BedPart blockpropertybedpart = (BedPart) iblockdata.getValue(BedBlock.PART);

            if (blockpropertybedpart == BedPart.FOOT) {
                BlockPos blockposition1 = blockposition.relative(getNeighbourDirection(blockpropertybedpart, (Direction) iblockdata.getValue(BedBlock.FACING)));
                BlockState iblockdata1 = world.getType(blockposition1);

                if (iblockdata1.getBlock() == this && iblockdata1.getValue(BedBlock.PART) == BedPart.HEAD) {
                    world.setTypeAndData(blockposition1, Blocks.AIR.getBlockData(), 35);
                    world.levelEvent(entityhuman, 2001, blockposition1, Block.getCombinedId(iblockdata1));
                }
            }
        }

        super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        Direction enumdirection = blockactioncontext.getHorizontalDirection();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        BlockPos blockposition1 = blockposition.relative(enumdirection);

        return blockactioncontext.getLevel().getType(blockposition1).canBeReplaced(blockactioncontext) ? (BlockState) this.getBlockData().setValue(BedBlock.FACING, enumdirection) : null;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        Direction enumdirection = getConnectedDirection(iblockdata).getOpposite();

        switch (enumdirection) {
            case NORTH:
                return BedBlock.NORTH_SHAPE;
            case SOUTH:
                return BedBlock.SOUTH_SHAPE;
            case WEST:
                return BedBlock.WEST_SHAPE;
            default:
                return BedBlock.EAST_SHAPE;
        }
    }

    public static Direction getConnectedDirection(BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(BedBlock.FACING);

        return iblockdata.getValue(BedBlock.PART) == BedPart.HEAD ? enumdirection.getOpposite() : enumdirection;
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> entitytypes, LevelReader iworldreader, BlockPos blockposition, int i) {
        Direction enumdirection = (Direction) iworldreader.getType(blockposition).getValue(BedBlock.FACING);
        int j = blockposition.getX();
        int k = blockposition.getY();
        int l = blockposition.getZ();

        for (int i1 = 0; i1 <= 1; ++i1) {
            int j1 = j - enumdirection.getStepX() * i1 - 1;
            int k1 = l - enumdirection.getStepZ() * i1 - 1;
            int l1 = j1 + 2;
            int i2 = k1 + 2;

            for (int j2 = j1; j2 <= l1; ++j2) {
                for (int k2 = k1; k2 <= i2; ++k2) {
                    BlockPos blockposition1 = new BlockPos(j2, k, k2);
                    Optional<Vec3> optional = getStandingLocationAtOrBelow(entitytypes, iworldreader, blockposition1);

                    if (optional.isPresent()) {
                        if (i <= 0) {
                            return optional;
                        }

                        --i;
                    }
                }
            }
        }

        return Optional.empty();
    }

    public static Optional<Vec3> getStandingLocationAtOrBelow(EntityType<?> entitytypes, LevelReader iworldreader, BlockPos blockposition) {
        VoxelShape voxelshape = iworldreader.getType(blockposition).getCollisionShape(iworldreader, blockposition);

        if (voxelshape.max(Direction.Axis.Y) > 0.4375D) {
            return Optional.empty();
        } else {
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = blockposition.i();

            while (blockposition_mutableblockposition.getY() >= 0 && blockposition.getY() - blockposition_mutableblockposition.getY() <= 2 && iworldreader.getType(blockposition_mutableblockposition).getCollisionShape(iworldreader, blockposition_mutableblockposition).isEmpty()) {
                blockposition_mutableblockposition.c(Direction.DOWN);
            }

            VoxelShape voxelshape1 = iworldreader.getType(blockposition_mutableblockposition).getCollisionShape(iworldreader, blockposition_mutableblockposition);

            if (voxelshape1.isEmpty()) {
                return Optional.empty();
            } else {
                double d0 = (double) blockposition_mutableblockposition.getY() + voxelshape1.max(Direction.Axis.Y) + 2.0E-7D;

                if ((double) blockposition.getY() - d0 > 2.0D) {
                    return Optional.empty();
                } else {
                    Vec3 vec3d = new Vec3((double) blockposition_mutableblockposition.getX() + 0.5D, d0, (double) blockposition_mutableblockposition.getZ() + 0.5D);
                    AABB axisalignedbb = entitytypes.getAABB(vec3d.x, vec3d.y, vec3d.z);

                    if (iworldreader.noCollision(axisalignedbb)) {
                        Stream<BlockState> stream = iworldreader.getBlockStates(axisalignedbb.expandTowards(0.0D, -0.20000000298023224D, 0.0D)); // CraftBukkit - decompile error

                        entitytypes.getClass();
                        if (stream.noneMatch(entitytypes::isBlockDangerous)) {
                            return Optional.of(vec3d);
                        }
                    }

                    return Optional.empty();
                }
            }
        }
    }

    @Override
    public PushReaction getPushReaction(BlockState iblockdata) {
        return PushReaction.DESTROY;
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(BedBlock.FACING, BedBlock.PART, BedBlock.OCCUPIED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new BedBlockEntity(this.color);
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, @Nullable LivingEntity entityliving, ItemStack itemstack) {
        super.postPlace(world, blockposition, iblockdata, entityliving, itemstack);
        if (!world.isClientSide) {
            BlockPos blockposition1 = blockposition.relative((Direction) iblockdata.getValue(BedBlock.FACING));

            world.setTypeAndData(blockposition1, (BlockState) iblockdata.setValue(BedBlock.PART, BedPart.HEAD), 3);
            world.blockUpdated(blockposition, Blocks.AIR);
            iblockdata.updateNeighbourShapes(world, blockposition, 3);
        }

    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
