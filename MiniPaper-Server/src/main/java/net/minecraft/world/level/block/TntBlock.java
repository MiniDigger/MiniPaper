package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class TntBlock extends Block {

    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;

    public TntBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) this.getBlockData().setValue(TntBlock.UNSTABLE, false));
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            if (world.hasNeighborSignal(blockposition)) {
                explode(world, blockposition);
                world.removeBlock(blockposition, false);
            }

        }
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (world.hasNeighborSignal(blockposition)) {
            explode(world, blockposition);
            world.removeBlock(blockposition, false);
        }

    }

    @Override
    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!world.isClientSide() && !entityhuman.isCreative() && (Boolean) iblockdata.getValue(TntBlock.UNSTABLE)) {
            explode(world, blockposition);
        }

        super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    @Override
    public void wasExploded(Level world, BlockPos blockposition, Explosion explosion) {
        if (!world.isClientSide) {
            PrimedTnt entitytntprimed = new PrimedTnt(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, explosion.getSourceMob());

            entitytntprimed.setFuse((short) (world.random.nextInt(entitytntprimed.getLife() / 4) + entitytntprimed.getLife() / 8));
            world.addFreshEntity(entitytntprimed);
        }
    }

    public static void explode(Level world, BlockPos blockposition) {
        explode(world, blockposition, (LivingEntity) null);
    }

    private static void explode(Level world, BlockPos blockposition, @Nullable LivingEntity entityliving) {
        if (!world.isClientSide) {
            PrimedTnt entitytntprimed = new PrimedTnt(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, entityliving);

            world.addFreshEntity(entitytntprimed);
            world.playSound((Player) null, entitytntprimed.getX(), entitytntprimed.getY(), entitytntprimed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        Item item = itemstack.getItem();

        if (item != Items.FLINT_AND_STEEL && item != Items.FIRE_CHARGE) {
            return super.interact(iblockdata, world, blockposition, entityhuman, enumhand, movingobjectpositionblock);
        } else {
            explode(world, blockposition, (LivingEntity) entityhuman);
            world.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 11);
            if (!entityhuman.isCreative()) {
                if (item == Items.FLINT_AND_STEEL) {
                    itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                        entityhuman1.broadcastBreakEvent(enumhand);
                    });
                } else {
                    itemstack.shrink(1);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState iblockdata, BlockHitResult movingobjectpositionblock, Projectile iprojectile) {
        if (!world.isClientSide) {
            Entity entity = iprojectile.getOwner();

            if (iprojectile.isOnFire()) {
                BlockPos blockposition = movingobjectpositionblock.getBlockPos();
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(iprojectile, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                    return;
                }
                // CraftBukkit end

                explode(world, blockposition, entity instanceof LivingEntity ? (LivingEntity) entity : null);
                world.removeBlock(blockposition, false);
            }
        }

    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(TntBlock.UNSTABLE);
    }
}
