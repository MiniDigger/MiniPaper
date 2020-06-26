package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.CauldronLevelChangeEvent; // CraftBukkit

public class CauldronBlock extends Block {

    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
    private static final VoxelShape INSIDE = box(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    protected static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Shapes.or(box(0.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D), box(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 16.0D), box(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D), CauldronBlock.INSIDE), BooleanOp.ONLY_FIRST);

    public CauldronBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CauldronBlock.LEVEL, 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return CauldronBlock.SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return CauldronBlock.INSIDE;
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        int i = (Integer) iblockdata.getValue(CauldronBlock.LEVEL);
        float f = (float) blockposition.getY() + (6.0F + (float) (3 * i)) / 16.0F;

        if (!world.isClientSide && entity.isOnFire() && i > 0 && entity.getY() <= (double) f) {
            // CraftBukkit start
            if (!this.changeLevel(world, blockposition, iblockdata, i - 1, entity, CauldronLevelChangeEvent.ChangeReason.EXTINGUISH)) {
                return;
            }
            entity.clearFire();
            // this.a(world, blockposition, iblockdata, i - 1);
            // CraftBukkit end
        }

    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.isEmpty()) {
            return InteractionResult.PASS;
        } else {
            int i = (Integer) iblockdata.getValue(CauldronBlock.LEVEL);
            Item item = itemstack.getItem();

            if (item == Items.WATER_BUCKET) {
                if (i < 3 && !world.isClientSide) {
                    // CraftBukkit start
                    if (!this.changeLevel(world, blockposition, iblockdata, 3, entityhuman, CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY)) {
                        return InteractionResult.SUCCESS;
                    }
                    if (!entityhuman.abilities.instabuild) {
                        entityhuman.setItemInHand(enumhand, new ItemStack(Items.BUCKET));
                    }

                    entityhuman.awardStat(Stats.FILL_CAULDRON);
                    // this.a(world, blockposition, iblockdata, 3);
                    // CraftBukkit end
                    world.playSound((Player) null, blockposition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            } else if (item == Items.BUCKET) {
                if (i == 3 && !world.isClientSide) {
                    // CraftBukkit start
                    if (!this.changeLevel(world, blockposition, iblockdata, 0, entityhuman, CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL)) {
                        return InteractionResult.SUCCESS;
                    }
                    if (!entityhuman.abilities.instabuild) {
                        itemstack.shrink(1);
                        if (itemstack.isEmpty()) {
                            entityhuman.setItemInHand(enumhand, new ItemStack(Items.WATER_BUCKET));
                        } else if (!entityhuman.inventory.add(new ItemStack(Items.WATER_BUCKET))) {
                            entityhuman.drop(new ItemStack(Items.WATER_BUCKET), false);
                        }
                    }

                    entityhuman.awardStat(Stats.USE_CAULDRON);
                    // this.a(world, blockposition, iblockdata, 0);
                    // CraftBukkit end
                    world.playSound((Player) null, blockposition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                ItemStack itemstack1;

                if (item == Items.GLASS_BOTTLE) {
                    if (i > 0 && !world.isClientSide) {
                        // CraftBukkit start
                        if (!this.changeLevel(world, blockposition, iblockdata, i - 1, entityhuman, CauldronLevelChangeEvent.ChangeReason.BOTTLE_FILL)) {
                            return InteractionResult.SUCCESS;
                        }
                        if (!entityhuman.abilities.instabuild) {
                            itemstack1 = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
                            entityhuman.awardStat(Stats.USE_CAULDRON);
                            itemstack.shrink(1);
                            if (itemstack.isEmpty()) {
                                entityhuman.setItemInHand(enumhand, itemstack1);
                            } else if (!entityhuman.inventory.add(itemstack1)) {
                                entityhuman.drop(itemstack1, false);
                            } else if (entityhuman instanceof ServerPlayer) {
                                ((ServerPlayer) entityhuman).refreshContainer(entityhuman.inventoryMenu);
                            }
                        }

                        world.playSound((Player) null, blockposition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                        // this.a(world, blockposition, iblockdata, i - 1);
                        // CraftBukkit end
                    }

                    return InteractionResult.sidedSuccess(world.isClientSide);
                } else if (item == Items.POTION && PotionUtils.getPotion(itemstack) == Potions.WATER) {
                    if (i < 3 && !world.isClientSide) {
                        // CraftBukkit start
                        if (!this.changeLevel(world, blockposition, iblockdata, i + 1, entityhuman, CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY)) {
                            return InteractionResult.SUCCESS;
                        }
                        if (!entityhuman.abilities.instabuild) {
                            itemstack1 = new ItemStack(Items.GLASS_BOTTLE);
                            entityhuman.awardStat(Stats.USE_CAULDRON);
                            entityhuman.setItemInHand(enumhand, itemstack1);
                            if (entityhuman instanceof ServerPlayer) {
                                ((ServerPlayer) entityhuman).refreshContainer(entityhuman.inventoryMenu);
                            }
                        }

                        world.playSound((Player) null, blockposition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        // this.a(world, blockposition, iblockdata, i + 1);
                        // CraftBukkit end
                    }

                    return InteractionResult.sidedSuccess(world.isClientSide);
                } else {
                    if (i > 0 && item instanceof DyeableLeatherItem) {
                        DyeableLeatherItem idyeable = (DyeableLeatherItem) item;

                        if (idyeable.hasCustomColor(itemstack) && !world.isClientSide) {
                            // CraftBukkit start
                            if (!this.changeLevel(world, blockposition, iblockdata, i - 1, entityhuman, CauldronLevelChangeEvent.ChangeReason.ARMOR_WASH)) {
                                return InteractionResult.SUCCESS;
                            }
                            idyeable.clearColor(itemstack);
                            // this.a(world, blockposition, iblockdata, i - 1);
                            // CraftBukkit end
                            entityhuman.awardStat(Stats.CLEAN_ARMOR);
                            return InteractionResult.SUCCESS;
                        }
                    }

                    if (i > 0 && item instanceof BannerItem) {
                        if (BannerBlockEntity.getPatternCount(itemstack) > 0 && !world.isClientSide) {
                            // CraftBukkit start
                            if (!this.changeLevel(world, blockposition, iblockdata, i - 1, entityhuman, CauldronLevelChangeEvent.ChangeReason.BANNER_WASH)) {
                                return InteractionResult.SUCCESS;
                            }
                            itemstack1 = itemstack.copy();
                            itemstack1.setCount(1);
                            BannerBlockEntity.removeLastPattern(itemstack1);
                            entityhuman.awardStat(Stats.CLEAN_BANNER);
                            if (!entityhuman.abilities.instabuild) {
                                itemstack.shrink(1);
                                // this.a(world, blockposition, iblockdata, i - 1);
                                // CraftBukkit end
                            }

                            if (itemstack.isEmpty()) {
                                entityhuman.setItemInHand(enumhand, itemstack1);
                            } else if (!entityhuman.inventory.add(itemstack1)) {
                                entityhuman.drop(itemstack1, false);
                            } else if (entityhuman instanceof ServerPlayer) {
                                ((ServerPlayer) entityhuman).refreshContainer(entityhuman.inventoryMenu);
                            }
                        }

                        return InteractionResult.sidedSuccess(world.isClientSide);
                    } else if (i > 0 && item instanceof BlockItem) {
                        Block block = ((BlockItem) item).getBlock();

                        if (block instanceof ShulkerBoxBlock && !world.isClientSide()) {
                            ItemStack itemstack2 = new ItemStack(Blocks.SHULKER_BOX, 1);

                            if (itemstack.hasTag()) {
                                itemstack2.setTag(itemstack.getTag().copy());
                            }

                            entityhuman.setItemInHand(enumhand, itemstack2);
                            this.setWaterLevel(world, blockposition, iblockdata, i - 1);
                            entityhuman.awardStat(Stats.CLEAN_SHULKER_BOX);
                            return InteractionResult.SUCCESS;
                        } else {
                            return InteractionResult.CONSUME;
                        }
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    // CraftBukkit start
    public void setWaterLevel(Level world, BlockPos blockposition, BlockState iblockdata, int i) {
        this.changeLevel(world, blockposition, iblockdata, i, null, CauldronLevelChangeEvent.ChangeReason.UNKNOWN);
    }

    private boolean changeLevel(Level world, BlockPos blockposition, BlockState iblockdata, int i, Entity entity, CauldronLevelChangeEvent.ChangeReason reason) {
        int newLevel = Integer.valueOf(Mth.clamp(i, 0, 3));
        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()),
                (entity == null) ? null : entity.getBukkitEntity(), reason, iblockdata.getValue(CauldronBlock.LEVEL), newLevel
        );
        world.getServerOH().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(CauldronBlock.LEVEL, event.getNewLevel()), 2);
        world.updateNeighbourForOutputSignal(blockposition, this);
        return true;
        // CraftBukkit end
    }

    @Override
    public void handleRain(Level world, BlockPos blockposition) {
        if (world.random.nextInt(20) == 1) {
            float f = world.getBiome(blockposition).getTemperature(blockposition);

            if (f >= 0.15F) {
                BlockState iblockdata = world.getType(blockposition);

                if ((Integer) iblockdata.getValue(CauldronBlock.LEVEL) < 3) {
                    this.setWaterLevel(world, blockposition, (BlockState) iblockdata.cycle((Property) CauldronBlock.LEVEL), 2); // CraftBukkit
                }

            }
        }
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        return (Integer) iblockdata.getValue(CauldronBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CauldronBlock.LEVEL);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
