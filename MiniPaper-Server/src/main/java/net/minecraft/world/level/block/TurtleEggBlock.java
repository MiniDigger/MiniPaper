package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.block.CraftBlock;

// CraftBukkit start
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class TurtleEggBlock extends Block {

    private static final VoxelShape ONE_EGG_AABB = Block.box(3.0D, 0.0D, 3.0D, 12.0D, 7.0D, 12.0D);
    private static final VoxelShape MULTIPLE_EGGS_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    public static final IntegerProperty EGGS = BlockStateProperties.EGGS;

    public TurtleEggBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TurtleEggBlock.HATCH, 0)).setValue(TurtleEggBlock.EGGS, 1));
    }

    @Override
    public void stepOn(Level world, BlockPos blockposition, Entity entity) {
        this.destroyEgg(world, blockposition, entity, 100);
        super.stepOn(world, blockposition, entity);
    }

    @Override
    public void fallOn(Level world, BlockPos blockposition, Entity entity, float f) {
        if (!(entity instanceof Zombie)) {
            this.destroyEgg(world, blockposition, entity, 3);
        }

        super.fallOn(world, blockposition, entity, f);
    }

    private void destroyEgg(Level world, BlockPos blockposition, Entity entity, int i) {
        if (this.canDestroyEgg(world, entity)) {
            if (!world.isClientSide && world.random.nextInt(i) == 0) {
                BlockState iblockdata = world.getType(blockposition);

                if (iblockdata.is(Blocks.TURTLE_EGG)) {
                    // CraftBukkit start - Step on eggs
                    org.bukkit.event.Cancellable cancellable;
                    if (entity instanceof Player) {
                        cancellable = CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
                    } else {
                        cancellable = new EntityInteractEvent(entity.getBukkitEntity(), CraftBlock.at(world, blockposition));
                        world.getServerOH().getPluginManager().callEvent((EntityInteractEvent) cancellable);
                    }

                    if (cancellable.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.decreaseEggs(world, blockposition, iblockdata);
                }
            }

        }
    }

    private void decreaseEggs(Level world, BlockPos blockposition, BlockState iblockdata) {
        world.playSound((Player) null, blockposition, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + world.random.nextFloat() * 0.2F);
        int i = (Integer) iblockdata.getValue(TurtleEggBlock.EGGS);

        if (i <= 1) {
            world.destroyBlock(blockposition, false);
        } else {
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(TurtleEggBlock.EGGS, i - 1), 2);
            world.levelEvent(2001, blockposition, Block.getCombinedId(iblockdata));
        }

    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (this.shouldUpdateHatchLevel((Level) worldserver) && onSand((BlockGetter) worldserver, blockposition)) {
            int i = (Integer) iblockdata.getValue(TurtleEggBlock.HATCH);

            if (i < 2) {
                // CraftBukkit start - Call BlockGrowEvent
                if (!CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, iblockdata.setValue(TurtleEggBlock.HATCH, i + 1), 2)) {
                    return;
                }
                // CraftBukkit end
                worldserver.playSound((Player) null, blockposition, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                // worldserver.setTypeAndData(blockposition, (IBlockData) iblockdata.set(BlockTurtleEgg.a, i + 1), 2); // CraftBukkit - handled above
            } else {
                // CraftBukkit start - Call BlockFadeEvent
                if (CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                    return;
                }
                // CraftBukkit end
                worldserver.playSound((Player) null, blockposition, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                worldserver.removeBlock(blockposition, false);

                for (int j = 0; j < (Integer) iblockdata.getValue(TurtleEggBlock.EGGS); ++j) {
                    worldserver.levelEvent(2001, blockposition, Block.getCombinedId(iblockdata));
                    Turtle entityturtle = (Turtle) EntityType.TURTLE.create((Level) worldserver);

                    entityturtle.setAge(-24000);
                    entityturtle.setHomePos(blockposition);
                    entityturtle.moveTo((double) blockposition.getX() + 0.3D + (double) j * 0.2D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.3D, 0.0F, 0.0F);
                    worldserver.addEntity(entityturtle, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // CraftBukkit
                }
            }
        }

    }

    public static boolean onSand(BlockGetter iblockaccess, BlockPos blockposition) {
        return isSand(iblockaccess, blockposition.below());
    }

    public static boolean isSand(BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockaccess.getType(blockposition).is((Tag) BlockTags.SAND);
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (onSand((BlockGetter) world, blockposition) && !world.isClientSide) {
            world.levelEvent(2005, blockposition, 0);
        }

    }

    private boolean shouldUpdateHatchLevel(Level world) {
        float f = world.getTimeOfDay(1.0F);

        return (double) f < 0.69D && (double) f > 0.65D ? true : world.random.nextInt(500) == 0;
    }

    @Override
    public void playerDestroy(Level world, Player entityhuman, BlockPos blockposition, BlockState iblockdata, @Nullable BlockEntity tileentity, ItemStack itemstack) {
        super.playerDestroy(world, entityhuman, blockposition, iblockdata, tileentity, itemstack);
        this.decreaseEggs(world, blockposition, iblockdata);
    }

    @Override
    public boolean canBeReplaced(BlockState iblockdata, BlockPlaceContext blockactioncontext) {
        return blockactioncontext.getItemInHand().getItem() == this.asItem() && (Integer) iblockdata.getValue(TurtleEggBlock.EGGS) < 4 ? true : super.canBeReplaced(iblockdata, blockactioncontext);
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos());

        return iblockdata.is((Block) this) ? (BlockState) iblockdata.setValue(TurtleEggBlock.EGGS, Math.min(4, (Integer) iblockdata.getValue(TurtleEggBlock.EGGS) + 1)) : super.getPlacedState(blockactioncontext);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return (Integer) iblockdata.getValue(TurtleEggBlock.EGGS) > 1 ? TurtleEggBlock.MULTIPLE_EGGS_AABB : TurtleEggBlock.ONE_EGG_AABB;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(TurtleEggBlock.HATCH, TurtleEggBlock.EGGS);
    }

    private boolean canDestroyEgg(Level world, Entity entity) {
        return !(entity instanceof Turtle) && !(entity instanceof Bat) ? (!(entity instanceof LivingEntity) ? false : entity instanceof Player || world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) : false;
    }
}
