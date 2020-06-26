package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class SweetBerryBushBlock extends BushBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape SAPLING_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D);
    private static final VoxelShape MID_GROWTH_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public SweetBerryBushBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(SweetBerryBushBlock.AGE, 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) == 0 ? SweetBerryBushBlock.SAPLING_SHAPE : ((Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) < 3 ? SweetBerryBushBlock.MID_GROWTH_SHAPE : super.getShape(iblockdata, iblockaccess, blockposition, voxelshapecollision));
    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) < 3;
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        int i = (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE);

        if (i < 3 && random.nextInt(Math.max(1, (int) (100.0F / worldserver.spigotConfig.sweetBerryModifier) * 5)) == 0 && worldserver.getRawBrightness(blockposition.above(), 0) >= 9) { // Spigot
            CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (BlockState) iblockdata.setValue(SweetBerryBushBlock.AGE, i + 1), 2); // CraftBukkit
        }

    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (entity instanceof LivingEntity && entity.getType() != EntityType.FOX && entity.getType() != EntityType.BEE) {
            entity.makeStuckInBlock(iblockdata, new Vec3(0.800000011920929D, 0.75D, 0.800000011920929D));
            if (!world.isClientSide && (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) > 0 && (entity.xOld != entity.getX() || entity.zOld != entity.getZ())) {
                double d0 = Math.abs(entity.getX() - entity.xOld);
                double d1 = Math.abs(entity.getZ() - entity.zOld);

                if (d0 >= 0.003000000026077032D || d1 >= 0.003000000026077032D) {
                    CraftEventFactory.blockDamage = CraftBlock.at(world, blockposition); // CraftBukkit
                    entity.hurt(DamageSource.SWEET_BERRY_BUSH, 1.0F);
                    CraftEventFactory.blockDamage = null; // CraftBukkit
                }
            }

        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        int i = (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE);
        boolean flag = i == 3;

        if (!flag && entityhuman.getItemInHand(enumhand).getItem() == Items.BONE_MEAL) {
            return InteractionResult.PASS;
        } else if (i > 1) {
            int j = 1 + world.random.nextInt(2);

            popResource(world, blockposition, new ItemStack(Items.SWEET_BERRIES, j + (flag ? 1 : 0)));
            world.playSound((Player) null, blockposition, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + world.random.nextFloat() * 0.4F);
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(SweetBerryBushBlock.AGE, 1), 2);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return super.interact(iblockdata, world, blockposition, entityhuman, enumhand, movingobjectpositionblock);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(SweetBerryBushBlock.AGE);
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        int i = Math.min(3, (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) + 1);

        worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(SweetBerryBushBlock.AGE, i), 2);
    }
}
