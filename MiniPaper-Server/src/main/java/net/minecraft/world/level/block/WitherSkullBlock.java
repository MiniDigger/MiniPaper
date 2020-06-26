package net.minecraft.world.level.block;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockMaterialPredicate;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.material.Material;
// CraftBukkit start
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public class WitherSkullBlock extends SkullBlock {

    @Nullable
    private static BlockPattern witherPatternFull;
    @Nullable
    private static BlockPattern witherPatternBase;

    protected WitherSkullBlock(BlockBehaviour.Info blockbase_info) {
        super(SkullBlock.Types.WITHER_SKELETON, blockbase_info);
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, @Nullable LivingEntity entityliving, ItemStack itemstack) {
        super.postPlace(world, blockposition, iblockdata, entityliving, itemstack);
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof SkullBlockEntity) {
            checkSpawn(world, blockposition, (SkullBlockEntity) tileentity);
        }

    }

    public static void checkSpawn(Level world, BlockPos blockposition, SkullBlockEntity tileentityskull) {
        if (world.captureBlockStates) return; // CraftBukkit
        if (!world.isClientSide) {
            BlockState iblockdata = tileentityskull.getBlock();
            boolean flag = iblockdata.is(Blocks.WITHER_SKELETON_SKULL) || iblockdata.is(Blocks.WITHER_SKELETON_WALL_SKULL);

            if (flag && blockposition.getY() >= 0 && world.getDifficulty() != Difficulty.PEACEFUL) {
                BlockPattern shapedetector = getOrCreateWitherFull();
                BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = shapedetector.find(world, blockposition);

                if (shapedetector_shapedetectorcollection != null) {
                    // CraftBukkit start - Use BlockStateListPopulator
                    BlockStateListPopulator blockList = new BlockStateListPopulator(world);
                    for (int i = 0; i < shapedetector.getWidth(); ++i) {
                        for (int j = 0; j < shapedetector.getHeight(); ++j) {
                            BlockInWorld shapedetectorblock = shapedetector_shapedetectorcollection.getBlock(i, j, 0);

                            blockList.setTypeAndData(shapedetectorblock.getPos(), Blocks.AIR.getBlockData(), 2); // CraftBukkit
                            // world.triggerEffect(2001, shapedetectorblock.getPosition(), Block.getCombinedId(shapedetectorblock.a())); // CraftBukkit
                        }
                    }

                    WitherBoss entitywither = (WitherBoss) EntityType.WITHER.create(world);
                    BlockPos blockposition1 = shapedetector_shapedetectorcollection.getBlock(1, 2, 0).getPos();

                    entitywither.moveTo((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.55D, (double) blockposition1.getZ() + 0.5D, shapedetector_shapedetectorcollection.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F, 0.0F);
                    entitywither.yBodyRot = shapedetector_shapedetectorcollection.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
                    entitywither.makeInvulnerable();
                    // CraftBukkit start
                    if (!world.addEntity(entitywither, SpawnReason.BUILD_WITHER)) {
                        return;
                    }
                    for (BlockPos pos : blockList.getBlocks()) {
                        world.levelEvent(2001, pos, Block.getCombinedId(world.getType(pos)));
                    }
                    blockList.updateList();
                    // CraftBukkit end
                    Iterator iterator = world.getEntitiesOfClass(ServerPlayer.class, entitywither.getBoundingBox().inflate(50.0D)).iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                        CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayer, (Entity) entitywither);
                    }

                    // world.addEntity(entitywither); // CraftBukkit - moved up

                    for (int k = 0; k < shapedetector.getWidth(); ++k) {
                        for (int l = 0; l < shapedetector.getHeight(); ++l) {
                            world.blockUpdated(shapedetector_shapedetectorcollection.getBlock(k, l, 0).getPos(), Blocks.AIR);
                        }
                    }

                }
            }
        }
    }

    public static boolean canSpawnMob(Level world, BlockPos blockposition, ItemStack itemstack) {
        return itemstack.getItem() == Items.WITHER_SKELETON_SKULL && blockposition.getY() >= 2 && world.getDifficulty() != Difficulty.PEACEFUL && !world.isClientSide ? getOrCreateWitherBase().find(world, blockposition) != null : false;
    }

    private static BlockPattern getOrCreateWitherFull() {
        if (WitherSkullBlock.witherPatternFull == null) {
            WitherSkullBlock.witherPatternFull = BlockPatternBuilder.start().aisle("^^^", "###", "~#~").where('#', (shapedetectorblock) -> {
                return shapedetectorblock.getState().is((Tag) BlockTags.WITHER_SUMMON_BASE_BLOCKS);
            }).where('^', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_SKULL).or(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_WALL_SKULL)))).where('~', BlockInWorld.hasState(BlockMaterialPredicate.forMaterial(Material.AIR))).build();
        }

        return WitherSkullBlock.witherPatternFull;
    }

    private static BlockPattern getOrCreateWitherBase() {
        if (WitherSkullBlock.witherPatternBase == null) {
            WitherSkullBlock.witherPatternBase = BlockPatternBuilder.start().aisle("   ", "###", "~#~").where('#', (shapedetectorblock) -> {
                return shapedetectorblock.getState().is((Tag) BlockTags.WITHER_SUMMON_BASE_BLOCKS);
            }).where('~', BlockInWorld.hasState(BlockMaterialPredicate.forMaterial(Material.AIR))).build();
        }

        return WitherSkullBlock.witherPatternBase;
    }
}
