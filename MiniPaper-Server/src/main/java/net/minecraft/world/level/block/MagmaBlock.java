package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MagmaBlock extends Block {

    public MagmaBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public void stepOn(Level world, BlockPos blockposition, Entity entity) {
        if (!entity.fireImmune() && entity instanceof LivingEntity && !EnchantmentHelper.hasFrostWalker((LivingEntity) entity)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.blockDamage = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()); // CraftBukkit
            entity.hurt(DamageSource.HOT_FLOOR, 1.0F);
            org.bukkit.craftbukkit.event.CraftEventFactory.blockDamage = null; // CraftBukkit
        }

        super.stepOn(world, blockposition, entity);
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        BubbleColumnBlock.growColumn(worldserver, blockposition.above(), true);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection == Direction.UP && iblockdata1.is(Blocks.WATER)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 20);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        BlockPos blockposition1 = blockposition.above();

        if (worldserver.getFluidState(blockposition).is((Tag) FluidTags.WATER)) {
            worldserver.playSound((Player) null, blockposition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (worldserver.random.nextFloat() - worldserver.random.nextFloat()) * 0.8F);
            worldserver.sendParticles(ParticleTypes.LARGE_SMOKE, (double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.25D, (double) blockposition1.getZ() + 0.5D, 8, 0.5D, 0.25D, 0.5D, 0.0D);
        }

    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        world.getBlockTickList().scheduleTick(blockposition, this, 20);
    }
}
