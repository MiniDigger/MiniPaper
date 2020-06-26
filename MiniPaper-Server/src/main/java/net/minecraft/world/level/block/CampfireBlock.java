package net.minecraft.world.level.block;

import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CampfireBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty SIGNAL_FIRE = BlockStateProperties.SIGNAL_FIRE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape VIRTUAL_FENCE_POST = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private final boolean spawnParticles;
    private final int fireDamage;

    public CampfireBlock(boolean flag, int i, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.spawnParticles = flag;
        this.fireDamage = i;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CampfireBlock.LIT, true)).setValue(CampfireBlock.SIGNAL_FIRE, false)).setValue(CampfireBlock.WATERLOGGED, false)).setValue(CampfireBlock.FACING, Direction.NORTH));
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof CampfireBlockEntity) {
            CampfireBlockEntity tileentitycampfire = (CampfireBlockEntity) tileentity;
            ItemStack itemstack = entityhuman.getItemInHand(enumhand);
            Optional<CampfireCookingRecipe> optional = tileentitycampfire.getCookableRecipe(itemstack);

            if (optional.isPresent()) {
                if (!world.isClientSide && tileentitycampfire.placeFood(entityhuman.abilities.instabuild ? itemstack.copy() : itemstack, ((CampfireCookingRecipe) optional.get()).getCookingTime())) {
                    entityhuman.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                    return InteractionResult.SUCCESS;
                }

                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!entity.fireImmune() && (Boolean) iblockdata.getValue(CampfireBlock.LIT) && entity instanceof LivingEntity && !EnchantmentHelper.hasFrostWalker((LivingEntity) entity)) {
            entity.hurt(DamageSource.IN_FIRE, (float) this.fireDamage);
        }

        super.entityInside(iblockdata, world, blockposition, entity);
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof CampfireBlockEntity) {
                Containers.dropContents(world, blockposition, ((CampfireBlockEntity) tileentity).getItems());
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        boolean flag = world.getFluidState(blockposition).getType() == Fluids.WATER;

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(CampfireBlock.WATERLOGGED, flag)).setValue(CampfireBlock.SIGNAL_FIRE, this.isSmokeSource(world.getType(blockposition.below())))).setValue(CampfireBlock.LIT, !flag)).setValue(CampfireBlock.FACING, blockactioncontext.getHorizontalDirection());
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if ((Boolean) iblockdata.getValue(CampfireBlock.WATERLOGGED)) {
            generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
        }

        return enumdirection == Direction.DOWN ? (BlockState) iblockdata.setValue(CampfireBlock.SIGNAL_FIRE, this.isSmokeSource(iblockdata1)) : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    private boolean isSmokeSource(BlockState iblockdata) {
        return iblockdata.is(Blocks.HAY_BLOCK);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return CampfireBlock.SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    public static void dowse(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata) {
        if (generatoraccess.isClientSide()) {
            for (int i = 0; i < 20; ++i) {
                makeParticles(generatoraccess.getLevel(), blockposition, (Boolean) iblockdata.getValue(CampfireBlock.SIGNAL_FIRE), true);
            }
        }

        BlockEntity tileentity = generatoraccess.getBlockEntity(blockposition);

        if (tileentity instanceof CampfireBlockEntity) {
            ((CampfireBlockEntity) tileentity).dowse();
        }

    }

    @Override
    public boolean place(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata, FluidState fluid) {
        if (!(Boolean) iblockdata.getValue(BlockStateProperties.WATERLOGGED) && fluid.getType() == Fluids.WATER) {
            boolean flag = (Boolean) iblockdata.getValue(CampfireBlock.LIT);

            if (flag) {
                if (!generatoraccess.isClientSide()) {
                    generatoraccess.playSound((Player) null, blockposition, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                dowse(generatoraccess, blockposition, iblockdata);
            }

            generatoraccess.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(CampfireBlock.WATERLOGGED, true)).setValue(CampfireBlock.LIT, false), 3);
            generatoraccess.getFluidTickList().scheduleTick(blockposition, fluid.getType(), fluid.getType().getTickDelay((LevelReader) generatoraccess));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState iblockdata, BlockHitResult movingobjectpositionblock, Projectile iprojectile) {
        if (!world.isClientSide && iprojectile.isOnFire()) {
            Entity entity = iprojectile.getOwner();
            boolean flag = entity == null || entity instanceof Player || world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

            if (flag && !(Boolean) iblockdata.getValue(CampfireBlock.LIT) && !(Boolean) iblockdata.getValue(CampfireBlock.WATERLOGGED)) {
                BlockPos blockposition = movingobjectpositionblock.getBlockPos();

                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, entity).isCancelled()) {
                    return;
                }
                // CraftBukkit end
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(BlockStateProperties.LIT, true), 11);
            }
        }

    }

    public static void makeParticles(Level world, BlockPos blockposition, boolean flag, boolean flag1) {
        Random random = world.getRandom();
        SimpleParticleType particletype = flag ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;

        world.addAlwaysVisibleParticle(particletype, true, (double) blockposition.getX() + 0.5D + random.nextDouble() / 3.0D * (double) (random.nextBoolean() ? 1 : -1), (double) blockposition.getY() + random.nextDouble() + random.nextDouble(), (double) blockposition.getZ() + 0.5D + random.nextDouble() / 3.0D * (double) (random.nextBoolean() ? 1 : -1), 0.0D, 0.07D, 0.0D);
        if (flag1) {
            world.addParticle(ParticleTypes.SMOKE, (double) blockposition.getX() + 0.25D + random.nextDouble() / 2.0D * (double) (random.nextBoolean() ? 1 : -1), (double) blockposition.getY() + 0.4D, (double) blockposition.getZ() + 0.25D + random.nextDouble() / 2.0D * (double) (random.nextBoolean() ? 1 : -1), 0.0D, 0.005D, 0.0D);
        }

    }

    public static boolean isSmokeyPos(Level world, BlockPos blockposition) {
        for (int i = 1; i <= 5; ++i) {
            BlockPos blockposition1 = blockposition.below(i);
            BlockState iblockdata = world.getType(blockposition1);

            if (isLitCampfire(iblockdata)) {
                return true;
            }

            boolean flag = Shapes.joinIsNotEmpty(CampfireBlock.VIRTUAL_FENCE_POST, iblockdata.getCollisionShape((BlockGetter) world, blockposition, CollisionContext.empty()), BooleanOp.AND);

            if (flag) {
                BlockState iblockdata1 = world.getType(blockposition1.below());

                return isLitCampfire(iblockdata1);
            }
        }

        return false;
    }

    public static boolean isLitCampfire(BlockState iblockdata) {
        return iblockdata.hasProperty(CampfireBlock.LIT) && iblockdata.is((Tag) BlockTags.CAMPFIRES) && (Boolean) iblockdata.getValue(CampfireBlock.LIT);
    }

    @Override
    public FluidState getFluidState(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(CampfireBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(iblockdata);
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(CampfireBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(CampfireBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(CampfireBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CampfireBlock.LIT, CampfireBlock.SIGNAL_FIRE, CampfireBlock.WATERLOGGED, CampfireBlock.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new CampfireBlockEntity();
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }

    public static boolean canLight(BlockState iblockdata) {
        return iblockdata.is((Tag) BlockTags.CAMPFIRES, (blockbase_blockdata) -> {
            return blockbase_blockdata.hasProperty(BlockStateProperties.WATERLOGGED) && blockbase_blockdata.hasProperty(BlockStateProperties.LIT);
        }) && !(Boolean) iblockdata.getValue(BlockStateProperties.WATERLOGGED) && !(Boolean) iblockdata.getValue(BlockStateProperties.LIT);
    }
}
