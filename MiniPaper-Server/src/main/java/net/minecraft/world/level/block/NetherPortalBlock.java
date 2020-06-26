package net.minecraft.world.level.block;

import com.google.common.cache.LoadingCache;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.world.PortalCreateEvent;
// CraftBukkit end

public class NetherPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public NetherPortalBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        switch ((Direction.Axis) iblockdata.getValue(NetherPortalBlock.AXIS)) {
            case Z:
                return NetherPortalBlock.Z_AXIS_AABB;
            case X:
            default:
                return NetherPortalBlock.X_AXIS_AABB;
        }
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.spigotConfig.enableZombiePigmenPortalSpawns && worldserver.dimensionType().natural() && worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && random.nextInt(2000) < worldserver.getDifficulty().getId()) { // Spigot
            while (worldserver.getType(blockposition).is((Block) this)) {
                blockposition = blockposition.below();
            }

            if (worldserver.getType(blockposition).isValidSpawn((BlockGetter) worldserver, blockposition, EntityType.ZOMBIFIED_PIGLIN)) {
                // CraftBukkit - set spawn reason to NETHER_PORTAL
                Entity entity = EntityType.ZOMBIFIED_PIGLIN.spawnCreature(worldserver, (CompoundTag) null, (Component) null, (Player) null, blockposition.above(), MobSpawnType.STRUCTURE, false, false, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NETHER_PORTAL);

                if (entity != null) {
                    entity.changingDimensionDelay = entity.getDimensionChangingDelay();
                }
            }
        }

    }

    public static boolean trySpawnPortal(LevelAccessor generatoraccess, BlockPos blockposition) {
        NetherPortalBlock.PortalShape blockportal_shape = isPortal(generatoraccess, blockposition);

        if (blockportal_shape != null) {
            // CraftBukkit start - return portalcreator
            return blockportal_shape.createPortal();
            // return true;
            // CraftBukkit end
        } else {
            return false;
        }
    }

    @Nullable
    public static NetherPortalBlock.PortalShape isPortal(LevelAccessor generatoraccess, BlockPos blockposition) {
        NetherPortalBlock.PortalShape blockportal_shape = new NetherPortalBlock.PortalShape(generatoraccess, blockposition, Direction.Axis.X);

        if (blockportal_shape.isValid() && blockportal_shape.numPortalBlocks == 0) {
            return blockportal_shape;
        } else {
            NetherPortalBlock.PortalShape blockportal_shape1 = new NetherPortalBlock.PortalShape(generatoraccess, blockposition, Direction.Axis.Z);

            return blockportal_shape1.isValid() && blockportal_shape1.numPortalBlocks == 0 ? blockportal_shape1 : null;
        }
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
        Direction.Axis enumdirection_enumaxis1 = (Direction.Axis) iblockdata.getValue(NetherPortalBlock.AXIS);
        boolean flag = enumdirection_enumaxis1 != enumdirection_enumaxis && enumdirection_enumaxis.isHorizontal();

        return !flag && !iblockdata1.is((Block) this) && !(new NetherPortalBlock.PortalShape(generatoraccess, blockposition, enumdirection_enumaxis1)).isComplete() ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!entity.isPassenger() && !entity.isVehicle() && entity.canChangeDimensions()) {
            // CraftBukkit start - Entity in portal
            EntityPortalEnterEvent event = new EntityPortalEnterEvent(entity.getBukkitEntity(), new org.bukkit.Location(world.getWorld(), blockposition.getX(), blockposition.getY(), blockposition.getZ()));
            world.getServerOH().getPluginManager().callEvent(event);
            // CraftBukkit end
            entity.handleInsidePortal(blockposition);
        }

    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                switch ((Direction.Axis) iblockdata.getValue(NetherPortalBlock.AXIS)) {
                    case Z:
                        return (BlockState) iblockdata.setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
                    case X:
                        return (BlockState) iblockdata.setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
                    default:
                        return iblockdata;
                }
            default:
                return iblockdata;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(NetherPortalBlock.AXIS);
    }

    public static BlockPattern.BlockPatternMatch getPortalShape(LevelAccessor generatoraccess, BlockPos blockposition) {
        Direction.Axis enumdirection_enumaxis = Direction.Axis.Z;
        NetherPortalBlock.PortalShape blockportal_shape = new NetherPortalBlock.PortalShape(generatoraccess, blockposition, Direction.Axis.X);
        LoadingCache<BlockPos, BlockInWorld> loadingcache = BlockPattern.createLevelCache(generatoraccess, true);

        if (!blockportal_shape.isValid()) {
            enumdirection_enumaxis = Direction.Axis.X;
            blockportal_shape = new NetherPortalBlock.PortalShape(generatoraccess, blockposition, Direction.Axis.Z);
        }

        if (!blockportal_shape.isValid()) {
            return new BlockPattern.BlockPatternMatch(blockposition, Direction.NORTH, Direction.UP, loadingcache, 1, 1, 1);
        } else {
            int[] aint = new int[Direction.AxisDirection.values().length];
            Direction enumdirection = blockportal_shape.rightDir.getCounterClockWise();
            BlockPos blockposition1 = blockportal_shape.bottomLeft.above(blockportal_shape.getHeight() - 1);
            Direction.AxisDirection[] aenumdirection_enumaxisdirection = Direction.AxisDirection.values();
            int i = aenumdirection_enumaxisdirection.length;

            int j;

            for (j = 0; j < i; ++j) {
                Direction.AxisDirection enumdirection_enumaxisdirection = aenumdirection_enumaxisdirection[j];
                BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = new BlockPattern.BlockPatternMatch(enumdirection.getAxisDirection() == enumdirection_enumaxisdirection ? blockposition1 : blockposition1.relative(blockportal_shape.rightDir, blockportal_shape.getWidth() - 1), Direction.get(enumdirection_enumaxisdirection, enumdirection_enumaxis), Direction.UP, loadingcache, blockportal_shape.getWidth(), blockportal_shape.getHeight(), 1);

                for (int k = 0; k < blockportal_shape.getWidth(); ++k) {
                    for (int l = 0; l < blockportal_shape.getHeight(); ++l) {
                        BlockInWorld shapedetectorblock = shapedetector_shapedetectorcollection.getBlock(k, l, 1);

                        if (!shapedetectorblock.getState().isAir()) {
                            ++aint[enumdirection_enumaxisdirection.ordinal()];
                        }
                    }
                }
            }

            Direction.AxisDirection enumdirection_enumaxisdirection1 = Direction.AxisDirection.POSITIVE;
            Direction.AxisDirection[] aenumdirection_enumaxisdirection1 = Direction.AxisDirection.values();

            j = aenumdirection_enumaxisdirection1.length;

            for (int i1 = 0; i1 < j; ++i1) {
                Direction.AxisDirection enumdirection_enumaxisdirection2 = aenumdirection_enumaxisdirection1[i1];

                if (aint[enumdirection_enumaxisdirection2.ordinal()] < aint[enumdirection_enumaxisdirection1.ordinal()]) {
                    enumdirection_enumaxisdirection1 = enumdirection_enumaxisdirection2;
                }
            }

            return new BlockPattern.BlockPatternMatch(enumdirection.getAxisDirection() == enumdirection_enumaxisdirection1 ? blockposition1 : blockposition1.relative(blockportal_shape.rightDir, blockportal_shape.getWidth() - 1), Direction.get(enumdirection_enumaxisdirection1, enumdirection_enumaxis), Direction.UP, loadingcache, blockportal_shape.getWidth(), blockportal_shape.getHeight(), 1);
        }
    }

    public static class PortalShape {

        private final LevelAccessor level;
        private final Direction.Axis axis;
        private final Direction rightDir;
        private final Direction leftDir;
        private int numPortalBlocks;
        @Nullable
        private BlockPos bottomLeft;
        private int height;
        private int width;
        java.util.List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<org.bukkit.block.BlockState>(); // CraftBukkit - add field

        public PortalShape(LevelAccessor generatoraccess, BlockPos blockposition, Direction.Axis enumdirection_enumaxis) {
            this.level = generatoraccess;
            this.axis = enumdirection_enumaxis;
            if (enumdirection_enumaxis == Direction.Axis.X) {
                this.leftDir = Direction.EAST;
                this.rightDir = Direction.WEST;
            } else {
                this.leftDir = Direction.NORTH;
                this.rightDir = Direction.SOUTH;
            }

            for (BlockPos blockposition1 = blockposition; blockposition.getY() > blockposition1.getY() - 21 && blockposition.getY() > 0 && this.isEmpty(generatoraccess.getType(blockposition.below())); blockposition = blockposition.below()) {
                ;
            }

            int i = this.getDistanceUntilEdge(blockposition, this.leftDir) - 1;

            if (i >= 0) {
                this.bottomLeft = blockposition.relative(this.leftDir, i);
                this.width = this.getDistanceUntilEdge(this.bottomLeft, this.rightDir);
                if (this.width < 2 || this.width > 21) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            }

            if (this.bottomLeft != null) {
                this.height = this.calculatePortalHeight();
            }

        }

        protected int getDistanceUntilEdge(BlockPos blockposition, Direction enumdirection) {
            int i;

            for (i = 0; i < 22; ++i) {
                BlockPos blockposition1 = blockposition.relative(enumdirection, i);

                if (!this.isEmpty(this.level.getType(blockposition1)) || !this.level.getType(blockposition1.below()).is(Blocks.OBSIDIAN)) {
                    break;
                }
            }

            return this.level.getType(blockposition.relative(enumdirection, i)).is(Blocks.OBSIDIAN) ? i : 0;
        }

        public int getHeight() {
            return this.height;
        }

        public int getWidth() {
            return this.width;
        }

        protected int calculatePortalHeight() {
            // CraftBukkit start
            this.blocks.clear();
            // CraftBukkit end
            int i;

            label56:
            for (this.height = 0; this.height < 21; ++this.height) {
                for (i = 0; i < this.width; ++i) {
                    BlockPos blockposition = this.bottomLeft.relative(this.rightDir, i).above(this.height);
                    BlockState iblockdata = this.level.getType(blockposition);

                    if (!this.isEmpty(iblockdata)) {
                        break label56;
                    }

                    if (iblockdata.is(Blocks.NETHER_PORTAL)) {
                        ++this.numPortalBlocks;
                    }

                    if (i == 0) {
                        if (!this.level.getType(blockposition.relative(this.leftDir)).is(Blocks.OBSIDIAN)) {
                            break label56;
                            // CraftBukkit start - add the block to our list
                        } else {
                            BlockPos pos = blockposition.relative(this.leftDir);
                            blocks.add(CraftBlock.at(this.level, pos).getState());
                            // CraftBukkit end
                        }
                    } else if (i == this.width - 1 && !this.level.getType(blockposition.relative(this.rightDir)).is(Blocks.OBSIDIAN)) {
                        break label56;
                        // CraftBukkit start - add the block to our list
                    } else {
                        BlockPos pos = blockposition.relative(this.rightDir);
                        blocks.add(CraftBlock.at(this.level, pos).getState());
                        // CraftBukkit end
                    }
                }
            }

            for (i = 0; i < this.width; ++i) {
                if (!this.level.getType(this.bottomLeft.relative(this.rightDir, i).above(this.height)).is(Blocks.OBSIDIAN)) {
                    this.height = 0;
                    break;
                    // CraftBukkit start - add the block to our list
                } else {
                    BlockPos pos = this.bottomLeft.relative(this.rightDir, i).above(this.height);
                    blocks.add(CraftBlock.at(this.level, pos).getState());
                    // CraftBukkit end
                }
            }

            if (this.height <= 21 && this.height >= 3) {
                return this.height;
            } else {
                this.bottomLeft = null;
                this.width = 0;
                this.height = 0;
                return 0;
            }
        }

        protected boolean isEmpty(BlockState iblockdata) {
            return iblockdata.isAir() || iblockdata.is((Tag) BlockTags.FIRE) || iblockdata.is(Blocks.NETHER_PORTAL);
        }

        public boolean isValid() {
            return this.bottomLeft != null && this.width >= 2 && this.width <= 21 && this.height >= 3 && this.height <= 21;
        }

        // CraftBukkit start - return boolean
        public boolean createPortal() {
            org.bukkit.World bworld = this.level.getLevel().getWorld();

            // Copy below for loop
            for (int i = 0; i < this.width; ++i) {
                BlockPos blockposition = this.bottomLeft.relative(this.rightDir, i);

                for (int j = 0; j < this.height; ++j) {
                    BlockPos pos = blockposition.above(j);
                    CraftBlockState state = CraftBlockState.getBlockState(this.level.getLevel(), pos, 18);
                    state.setData((BlockState) Blocks.NETHER_PORTAL.getBlockData().setValue(NetherPortalBlock.AXIS, this.axis));
                    blocks.add(state);
                }
            }

            PortalCreateEvent event = new PortalCreateEvent(blocks, bworld, null, PortalCreateEvent.CreateReason.FIRE);
            this.level.getLevel().getServer().server.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end
            for (int i = 0; i < this.width; ++i) {
                BlockPos blockposition = this.bottomLeft.relative(this.rightDir, i);

                for (int j = 0; j < this.height; ++j) {
                    this.level.setTypeAndData(blockposition.above(j), (BlockState) Blocks.NETHER_PORTAL.getBlockData().setValue(NetherPortalBlock.AXIS, this.axis), 18);
                }
            }

            return true; // CraftBukkit
        }

        private boolean hasAllPortalBlocks() {
            return this.numPortalBlocks >= this.width * this.height;
        }

        public boolean isComplete() {
            return this.isValid() && this.hasAllPortalBlocks();
        }
    }
}
