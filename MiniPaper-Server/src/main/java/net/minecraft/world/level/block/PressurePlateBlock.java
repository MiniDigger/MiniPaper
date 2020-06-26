package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit

public class PressurePlateBlock extends BasePressurePlateBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final PressurePlateBlock.Sensitivity sensitivity;

    protected PressurePlateBlock(PressurePlateBlock.Sensitivity blockpressureplatebinary_enummobtype, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PressurePlateBlock.POWERED, false));
        this.sensitivity = blockpressureplatebinary_enummobtype;
    }

    @Override
    protected int getPower(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(PressurePlateBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected BlockState setSignalForState(BlockState iblockdata, int i) {
        return (BlockState) iblockdata.setValue(PressurePlateBlock.POWERED, i > 0);
    }

    @Override
    protected void playOnSound(LevelAccessor generatoraccess, BlockPos blockposition) {
        if (this.material != Material.WOOD && this.material != Material.NETHER_WOOD) {
            generatoraccess.playSound((Player) null, blockposition, SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.3F, 0.6F);
        } else {
            generatoraccess.playSound((Player) null, blockposition, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.3F, 0.8F);
        }

    }

    @Override
    protected void playOffSound(LevelAccessor generatoraccess, BlockPos blockposition) {
        if (this.material != Material.WOOD && this.material != Material.NETHER_WOOD) {
            generatoraccess.playSound((Player) null, blockposition, SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF, SoundSource.BLOCKS, 0.3F, 0.5F);
        } else {
            generatoraccess.playSound((Player) null, blockposition, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF, SoundSource.BLOCKS, 0.3F, 0.7F);
        }

    }

    @Override
    protected int getSignalStrength(Level world, BlockPos blockposition) {
        AABB axisalignedbb = PressurePlateBlock.TOUCH_AABB.move(blockposition);
        List list;

        switch (this.sensitivity) {
            case EVERYTHING:
                list = world.getEntities((Entity) null, axisalignedbb);
                break;
            case MOBS:
                list = world.getEntitiesOfClass(LivingEntity.class, axisalignedbb);
                break;
            default:
                return 0;
        }

        if (!list.isEmpty()) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                // CraftBukkit start - Call interact event when turning on a pressure plate
                if (this.getPower(world.getType(blockposition)) == 0) {
                    org.bukkit.World bworld = world.getWorld();
                    org.bukkit.plugin.PluginManager manager = world.getServerOH().getPluginManager();
                    org.bukkit.event.Cancellable cancellable;

                    if (entity instanceof Player) {
                        cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
                    } else {
                        cancellable = new EntityInteractEvent(entity.getBukkitEntity(), bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                        manager.callEvent((EntityInteractEvent) cancellable);
                    }

                    // We only want to block turning the plate on if all events are cancelled
                    if (cancellable.isCancelled()) {
                        continue;
                    }
                }
                // CraftBukkit end

                if (!entity.isIgnoringBlockTriggers()) {
                    return 15;
                }
            }
        }

        return 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(PressurePlateBlock.POWERED);
    }

    public static enum Sensitivity {

        EVERYTHING, MOBS;

        private Sensitivity() {}
    }
}
