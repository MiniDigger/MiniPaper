package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.Wearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockMaterialPredicate;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
// CraftBukkit start
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock implements Wearable {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    @Nullable
    private BlockPattern snowGolemBase;
    @Nullable
    private BlockPattern snowGolemFull;
    @Nullable
    private BlockPattern ironGolemBase;
    @Nullable
    private BlockPattern ironGolemFull;
    private static final Predicate<BlockState> PUMPKINS_PREDICATE = (iblockdata) -> {
        return iblockdata != null && (iblockdata.is(Blocks.CARVED_PUMPKIN) || iblockdata.is(Blocks.JACK_O_LANTERN));
    };

    protected CarvedPumpkinBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CarvedPumpkinBlock.FACING, Direction.NORTH));
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            this.trySpawnGolem(world, blockposition);
        }
    }

    public boolean canSpawnGolem(LevelReader iworldreader, BlockPos blockposition) {
        return this.getOrCreateSnowGolemBase().find(iworldreader, blockposition) != null || this.getOrCreateIronGolemBase().find(iworldreader, blockposition) != null;
    }

    private void trySpawnGolem(Level world, BlockPos blockposition) {
        BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = this.getOrCreateSnowGolemFull().find(world, blockposition);
        int i;
        Iterator iterator;
        ServerPlayer entityplayer;
        int j;

        BlockStateListPopulator blockList = new BlockStateListPopulator(world); // CraftBukkit - Use BlockStateListPopulator
        if (shapedetector_shapedetectorcollection != null) {
            for (i = 0; i < this.getOrCreateSnowGolemFull().getHeight(); ++i) {
                BlockInWorld shapedetectorblock = shapedetector_shapedetectorcollection.getBlock(0, i, 0);

                blockList.setTypeAndData(shapedetectorblock.getPos(), Blocks.AIR.getBlockData(), 2); // CraftBukkit
                // world.triggerEffect(2001, shapedetectorblock.getPosition(), Block.getCombinedId(shapedetectorblock.a())); // CraftBukkit
            }

            SnowGolem entitysnowman = (SnowGolem) EntityType.SNOW_GOLEM.create(world);
            BlockPos blockposition1 = shapedetector_shapedetectorcollection.getBlock(0, 2, 0).getPos();

            entitysnowman.moveTo((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.05D, (double) blockposition1.getZ() + 0.5D, 0.0F, 0.0F);
            // CraftBukkit start
            if (!world.addEntity(entitysnowman, SpawnReason.BUILD_SNOWMAN)) {
                return;
            }
            for (BlockPos pos : blockList.getBlocks()) {
                world.levelEvent(2001, pos, Block.getCombinedId(world.getType(pos)));
            }
            blockList.updateList();
            // CraftBukkit end
            iterator = world.getEntitiesOfClass(ServerPlayer.class, entitysnowman.getBoundingBox().inflate(5.0D)).iterator();

            while (iterator.hasNext()) {
                entityplayer = (ServerPlayer) iterator.next();
                CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayer, (Entity) entitysnowman);
            }

            for (j = 0; j < this.getOrCreateSnowGolemFull().getHeight(); ++j) {
                BlockInWorld shapedetectorblock1 = shapedetector_shapedetectorcollection.getBlock(0, j, 0);

                world.blockUpdated(shapedetectorblock1.getPos(), Blocks.AIR);
            }
        } else {
            shapedetector_shapedetectorcollection = this.getOrCreateIronGolemFull().find(world, blockposition);
            if (shapedetector_shapedetectorcollection != null) {
                for (i = 0; i < this.getOrCreateIronGolemFull().getWidth(); ++i) {
                    for (int k = 0; k < this.getOrCreateIronGolemFull().getHeight(); ++k) {
                        BlockInWorld shapedetectorblock2 = shapedetector_shapedetectorcollection.getBlock(i, k, 0);

                        blockList.setTypeAndData(shapedetectorblock2.getPos(), Blocks.AIR.getBlockData(), 2); // CraftBukkit
                        // world.triggerEffect(2001, shapedetectorblock2.getPosition(), Block.getCombinedId(shapedetectorblock2.a())); // CraftBukkit
                    }
                }

                BlockPos blockposition2 = shapedetector_shapedetectorcollection.getBlock(1, 2, 0).getPos();
                IronGolem entityirongolem = (IronGolem) EntityType.IRON_GOLEM.create(world);

                entityirongolem.setPlayerCreated(true);
                entityirongolem.moveTo((double) blockposition2.getX() + 0.5D, (double) blockposition2.getY() + 0.05D, (double) blockposition2.getZ() + 0.5D, 0.0F, 0.0F);
                // CraftBukkit start
                if (!world.addEntity(entityirongolem, SpawnReason.BUILD_IRONGOLEM)) {
                    return;
                }
                for (BlockPos pos : blockList.getBlocks()) {
                    world.levelEvent(2001, pos, Block.getCombinedId(world.getType(pos)));
                }
                blockList.updateList();
                // CraftBukkit end
                iterator = world.getEntitiesOfClass(ServerPlayer.class, entityirongolem.getBoundingBox().inflate(5.0D)).iterator();

                while (iterator.hasNext()) {
                    entityplayer = (ServerPlayer) iterator.next();
                    CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayer, (Entity) entityirongolem);
                }

                for (j = 0; j < this.getOrCreateIronGolemFull().getWidth(); ++j) {
                    for (int l = 0; l < this.getOrCreateIronGolemFull().getHeight(); ++l) {
                        BlockInWorld shapedetectorblock3 = shapedetector_shapedetectorcollection.getBlock(j, l, 0);

                        world.blockUpdated(shapedetectorblock3.getPos(), Blocks.AIR);
                    }
                }
            }
        }

    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(CarvedPumpkinBlock.FACING, blockactioncontext.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CarvedPumpkinBlock.FACING);
    }

    private BlockPattern getOrCreateSnowGolemBase() {
        if (this.snowGolemBase == null) {
            this.snowGolemBase = BlockPatternBuilder.start().aisle(" ", "#", "#").where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK))).build();
        }

        return this.snowGolemBase;
    }

    private BlockPattern getOrCreateSnowGolemFull() {
        if (this.snowGolemFull == null) {
            this.snowGolemFull = BlockPatternBuilder.start().aisle("^", "#", "#").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK))).build();
        }

        return this.snowGolemFull;
    }

    private BlockPattern getOrCreateIronGolemBase() {
        if (this.ironGolemBase == null) {
            this.ironGolemBase = BlockPatternBuilder.start().aisle("~ ~", "###", "~#~").where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK))).where('~', BlockInWorld.hasState(BlockMaterialPredicate.forMaterial(Material.AIR))).build();
        }

        return this.ironGolemBase;
    }

    private BlockPattern getOrCreateIronGolemFull() {
        if (this.ironGolemFull == null) {
            this.ironGolemFull = BlockPatternBuilder.start().aisle("~^~", "###", "~#~").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK))).where('~', BlockInWorld.hasState(BlockMaterialPredicate.forMaterial(Material.AIR))).build();
        }

        return this.ironGolemFull;
    }
}
