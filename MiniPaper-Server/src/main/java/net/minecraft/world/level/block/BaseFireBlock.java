package net.minecraft.world.level.block;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {

    private final float fireDamage;
    protected static final VoxelShape UP_AABB = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape DOWN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    protected static final VoxelShape EAST_AABB = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);

    public BaseFireBlock(BlockBehaviour.Info blockbase_info, float f) {
        super(blockbase_info);
        this.fireDamage = f;
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return getState((BlockGetter) blockactioncontext.getLevel(), blockactioncontext.getClickedPos());
    }

    public static BlockState getState(BlockGetter iblockaccess, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();
        BlockState iblockdata = iblockaccess.getType(blockposition1);

        return SoulFireBlock.canSurviveOnBlock(iblockdata.getBlock()) ? Blocks.SOUL_FIRE.getBlockData() : ((FireBlock) Blocks.FIRE).getPlacedState(iblockaccess, blockposition);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return BaseFireBlock.DOWN_AABB;
    }

    protected abstract boolean canBurn(BlockState iblockdata);

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!entity.fireImmune()) {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            if (entity.getRemainingFireTicks() == 0) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityCombustEvent event = new org.bukkit.event.entity.EntityCombustByBlockEvent(org.bukkit.craftbukkit.block.CraftBlock.at(world, blockposition), entity.getBukkitEntity(), 8);
                world.getServerOH().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.setOnFire(event.getDuration(), false);
                }
                // CraftBukkit end
            }

            entity.hurt(DamageSource.IN_FIRE, this.fireDamage);
        }

        super.entityInside(iblockdata, world, blockposition, entity);
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            // CraftBukkit - getTypeKey()
            if (world.getTypeKey()!= DimensionType.OVERWORLD_LOCATION && world.getTypeKey()!= DimensionType.NETHER_LOCATION || !NetherPortalBlock.trySpawnPortal((LevelAccessor) world, blockposition)) {
                if (!iblockdata.canSurvive(world, blockposition)) {
                    fireExtinguished(world, blockposition); // CraftBukkit - fuel block broke
                }

            }
        }
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!world.isClientSide()) {
            world.levelEvent((Player) null, 1009, blockposition, 0);
        }

    }

    public static boolean canBePlacedAt(LevelAccessor generatoraccess, BlockPos blockposition) {
        BlockState iblockdata = generatoraccess.getType(blockposition);
        BlockState iblockdata1 = getState((BlockGetter) generatoraccess, blockposition);

        return iblockdata.isAir() && (iblockdata1.canSurvive(generatoraccess, blockposition) || isPortal(generatoraccess, blockposition));
    }

    private static boolean isPortal(LevelAccessor generatoraccess, BlockPos blockposition) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        Direction enumdirection;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            enumdirection = (Direction) iterator.next();
        } while (!generatoraccess.getType(blockposition.relative(enumdirection)).is(Blocks.OBSIDIAN) || NetherPortalBlock.isPortal(generatoraccess, blockposition) == null);

        return true;
    }

    // CraftBukkit start
    protected void fireExtinguished(LevelAccessor world, BlockPos position) {
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, position, Blocks.AIR.getBlockData()).isCancelled()) {
            world.removeBlock(position, false);
        }
    }
    // CraftBukkit end
}
