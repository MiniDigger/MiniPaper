package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
// CraftBukkit end

public class BucketItem extends Item {

    public final Fluid content;

    public BucketItem(Fluid fluidtype, Item.Info item_info) {
        super(item_info);
        this.content = fluidtype;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        BlockHitResult movingobjectpositionblock = getPlayerPOVHitResult(world, entityhuman, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);

        if (movingobjectpositionblock.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else if (movingobjectpositionblock.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        } else {
            BlockHitResult movingobjectpositionblock1 = (BlockHitResult) movingobjectpositionblock;
            BlockPos blockposition = movingobjectpositionblock1.getBlockPos();
            Direction enumdirection = movingobjectpositionblock1.getDirection();
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (world.mayInteract(entityhuman, blockposition) && entityhuman.mayUseItemAt(blockposition1, enumdirection, itemstack)) {
                BlockState iblockdata;

                if (this.content == Fluids.EMPTY) {
                    iblockdata = world.getType(blockposition);
                    if (iblockdata.getBlock() instanceof BucketPickup) {
                        // CraftBukkit start
                        Fluid dummyFluid = ((BucketPickup) iblockdata.getBlock()).removeFluid(DummyGeneratorAccess.INSTANCE, blockposition, iblockdata);
                        PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent((ServerLevel) world, entityhuman, blockposition, blockposition, movingobjectpositionblock.getDirection(), itemstack, dummyFluid.getBucket());

                        if (event.isCancelled()) {
                            ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundBlockUpdatePacket(world, blockposition)); // SPIGOT-5163 (see PlayerInteractManager)
                            ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory(); // SPIGOT-4541
                            return new InteractionResultHolder(InteractionResult.FAIL, itemstack);
                        }
                        // CraftBukkit end
                        Fluid fluidtype = ((BucketPickup) iblockdata.getBlock()).removeFluid(world, blockposition, iblockdata);

                        if (fluidtype != Fluids.EMPTY) {
                            entityhuman.awardStat(Stats.ITEM_USED.get(this));
                            entityhuman.playSound(fluidtype.is((Tag) FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
                            ItemStack itemstack1 = ItemUtils.createBucketResult(itemstack, entityhuman, CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit

                            if (!world.isClientSide) {
                                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) entityhuman, new ItemStack(fluidtype.getBucket()));
                            }

                            return InteractionResultHolder.sidedSuccess(itemstack1, world.isClientSide());
                        }
                    }

                    return InteractionResultHolder.fail(itemstack);
                } else {
                    iblockdata = world.getType(blockposition);
                    BlockPos blockposition2 = iblockdata.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockposition : blockposition1;

                    if (this.a(entityhuman, world, blockposition2, movingobjectpositionblock1, movingobjectpositionblock1.getDirection(), blockposition, itemstack)) { // CraftBukkit
                        this.checkExtraContent(world, itemstack, blockposition2);
                        if (entityhuman instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) entityhuman, blockposition2, itemstack);
                        }

                        entityhuman.awardStat(Stats.ITEM_USED.get(this));
                        return InteractionResultHolder.sidedSuccess(this.getEmptySuccessItem(itemstack, entityhuman), world.isClientSide());
                    } else {
                        return InteractionResultHolder.fail(itemstack);
                    }
                }
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        }
    }

    protected ItemStack getEmptySuccessItem(ItemStack itemstack, Player entityhuman) {
        return !entityhuman.abilities.instabuild ? new ItemStack(Items.BUCKET) : itemstack;
    }

    public void checkExtraContent(Level world, ItemStack itemstack, BlockPos blockposition) {}

    public boolean emptyBucket(@Nullable Player entityhuman, Level world, BlockPos blockposition, @Nullable BlockHitResult movingobjectpositionblock) {
        return a(entityhuman, world, blockposition, movingobjectpositionblock, null, null, null);
    }

    public boolean a(Player entityhuman, Level world, BlockPos blockposition, @Nullable BlockHitResult movingobjectpositionblock, Direction enumdirection, BlockPos clicked, ItemStack itemstack) {
        // CraftBukkit end
        if (!(this.content instanceof FlowingFluid)) {
            return false;
        } else {
            BlockState iblockdata = world.getType(blockposition);
            Block block = iblockdata.getBlock();
            Material material = iblockdata.getMaterial();
            boolean flag = iblockdata.canBeReplaced(this.content);
            boolean flag1 = iblockdata.isAir() || flag || block instanceof LiquidBlockContainer && ((LiquidBlockContainer) block).canPlace(world, blockposition, iblockdata, this.content);

            // CraftBukkit start
            if (flag1 && entityhuman != null) {
                PlayerBucketEmptyEvent event = CraftEventFactory.callPlayerBucketEmptyEvent((ServerLevel) world, entityhuman, blockposition, clicked, enumdirection, itemstack);
                if (event.isCancelled()) {
                    ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundBlockUpdatePacket(world, blockposition)); // SPIGOT-4238: needed when looking through entity
                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory(); // SPIGOT-4541
                    return false;
                }
            }
            // CraftBukkit end
            if (!flag1) {
                return movingobjectpositionblock != null && this.emptyBucket(entityhuman, world, movingobjectpositionblock.getBlockPos().relative(movingobjectpositionblock.getDirection()), (BlockHitResult) null);
            } else if (world.dimensionType().ultraWarm() && this.content.is((Tag) FluidTags.WATER)) {
                int i = blockposition.getX();
                int j = blockposition.getY();
                int k = blockposition.getZ();

                world.playSound(entityhuman, blockposition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);

                for (int l = 0; l < 8; ++l) {
                    world.addParticle(ParticleTypes.LARGE_SMOKE, (double) i + Math.random(), (double) j + Math.random(), (double) k + Math.random(), 0.0D, 0.0D, 0.0D);
                }

                return true;
            } else if (block instanceof LiquidBlockContainer && this.content == Fluids.WATER) {
                ((LiquidBlockContainer) block).place(world, blockposition, iblockdata, ((FlowingFluid) this.content).getSource(false));
                this.playEmptySound(entityhuman, (LevelAccessor) world, blockposition);
                return true;
            } else {
                if (!world.isClientSide && flag && !material.isLiquid()) {
                    world.destroyBlock(blockposition, true);
                }

                if (!world.setTypeAndData(blockposition, this.content.defaultFluidState().getBlockData(), 11) && !iblockdata.getFluidState().isSource()) {
                    return false;
                } else {
                    this.playEmptySound(entityhuman, (LevelAccessor) world, blockposition);
                    return true;
                }
            }
        }
    }

    protected void playEmptySound(@Nullable Player entityhuman, LevelAccessor generatoraccess, BlockPos blockposition) {
        SoundEvent soundeffect = this.content.is((Tag) FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;

        generatoraccess.playSound(entityhuman, blockposition, soundeffect, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
