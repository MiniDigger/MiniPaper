package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;

    protected WeightedPressurePlateBlock(int i, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(WeightedPressurePlateBlock.POWER, 0));
        this.maxWeight = i;
    }

    @Override
    protected int getSignalStrength(Level world, BlockPos blockposition) {
        // CraftBukkit start
        // int i = Math.min(world.a(Entity.class, BlockPressurePlateWeighted.c.a(blockposition)).size(), this.weight);
        int i = 0;
        java.util.Iterator iterator = world.getEntitiesOfClass(Entity.class, WeightedPressurePlateBlock.TOUCH_AABB.move(blockposition)).iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            org.bukkit.event.Cancellable cancellable;

            if (entity instanceof Player) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                world.getServerOH().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            // We only want to block turning the plate on if all events are cancelled
            if (!cancellable.isCancelled()) {
                i++;
            }
        }

        i = Math.min(i, this.maxWeight);
        // CraftBukkit end

        if (i > 0) {
            float f = (float) Math.min(this.maxWeight, i) / (float) this.maxWeight;

            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    @Override
    protected void playOnSound(LevelAccessor generatoraccess, BlockPos blockposition) {
        generatoraccess.playSound((Player) null, blockposition, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.3F, 0.90000004F);
    }

    @Override
    protected void playOffSound(LevelAccessor generatoraccess, BlockPos blockposition) {
        generatoraccess.playSound((Player) null, blockposition, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundSource.BLOCKS, 0.3F, 0.75F);
    }

    @Override
    protected int getPower(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(WeightedPressurePlateBlock.POWER);
    }

    @Override
    protected BlockState setSignalForState(BlockState iblockdata, int i) {
        return (BlockState) iblockdata.setValue(WeightedPressurePlateBlock.POWER, i);
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(WeightedPressurePlateBlock.POWER);
    }
}
