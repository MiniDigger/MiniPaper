package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockFromToEvent; // CraftBukkit

public class DragonEggBlock extends FallingBlock {

    protected static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public DragonEggBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return DragonEggBlock.SHAPE;
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        this.teleport(iblockdata, world, blockposition);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public void attack(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman) {
        this.teleport(iblockdata, world, blockposition);
    }

    private void teleport(BlockState iblockdata, Level world, BlockPos blockposition) {
        for (int i = 0; i < 1000; ++i) {
            BlockPos blockposition1 = blockposition.offset(world.random.nextInt(16) - world.random.nextInt(16), world.random.nextInt(8) - world.random.nextInt(8), world.random.nextInt(16) - world.random.nextInt(16));

            if (world.getType(blockposition1).isAir()) {
                // CraftBukkit start
                org.bukkit.block.Block from = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                org.bukkit.block.Block to = world.getWorld().getBlockAt(blockposition1.getX(), blockposition1.getY(), blockposition1.getZ());
                BlockFromToEvent event = new BlockFromToEvent(from, to);
                org.bukkit.Bukkit.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }

                blockposition1 = new BlockPos(event.getToBlock().getX(), event.getToBlock().getY(), event.getToBlock().getZ());
                // CraftBukkit end
                if (world.isClientSide) {
                    for (int j = 0; j < 128; ++j) {
                        double d0 = world.random.nextDouble();
                        float f = (world.random.nextFloat() - 0.5F) * 0.2F;
                        float f1 = (world.random.nextFloat() - 0.5F) * 0.2F;
                        float f2 = (world.random.nextFloat() - 0.5F) * 0.2F;
                        double d1 = Mth.lerp(d0, (double) blockposition1.getX(), (double) blockposition.getX()) + (world.random.nextDouble() - 0.5D) + 0.5D;
                        double d2 = Mth.lerp(d0, (double) blockposition1.getY(), (double) blockposition.getY()) + world.random.nextDouble() - 0.5D;
                        double d3 = Mth.lerp(d0, (double) blockposition1.getZ(), (double) blockposition.getZ()) + (world.random.nextDouble() - 0.5D) + 0.5D;

                        world.addParticle(ParticleTypes.PORTAL, d1, d2, d3, (double) f, (double) f1, (double) f2);
                    }
                } else {
                    world.setTypeAndData(blockposition1, iblockdata, 2);
                    world.removeBlock(blockposition, false);
                }

                return;
            }
        }

    }

    @Override
    protected int getDelayAfterPlace() {
        return 5;
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
