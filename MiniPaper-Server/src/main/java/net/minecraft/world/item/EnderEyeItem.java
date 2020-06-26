package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class EnderEyeItem extends Item {

    public EnderEyeItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockState iblockdata = world.getType(blockposition);

        if (iblockdata.is(Blocks.END_PORTAL_FRAME) && !(Boolean) iblockdata.getValue(EndPortalFrameBlock.HAS_EYE)) {
            if (world.isClientSide) {
                return InteractionResult.SUCCESS;
            } else {
                BlockState iblockdata1 = (BlockState) iblockdata.setValue(EndPortalFrameBlock.HAS_EYE, true);

                Block.pushEntitiesUp(iblockdata, iblockdata1, world, blockposition);
                world.setTypeAndData(blockposition, iblockdata1, 2);
                world.updateNeighbourForOutputSignal(blockposition, Blocks.END_PORTAL_FRAME);
                itemactioncontext.getItemInHand().shrink(1);
                world.levelEvent(1503, blockposition, 0);
                BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = EndPortalFrameBlock.getOrCreatePortalShape().find(world, blockposition);

                if (shapedetector_shapedetectorcollection != null) {
                    BlockPos blockposition1 = shapedetector_shapedetectorcollection.getFrontTopLeft().offset(-3, 0, -3);

                    for (int i = 0; i < 3; ++i) {
                        for (int j = 0; j < 3; ++j) {
                            world.setTypeAndData(blockposition1.offset(i, 0, j), Blocks.END_PORTAL.getBlockData(), 2);
                        }
                    }

                    // CraftBukkit start - Use relative location for far away sounds
                    // world.b(1038, blockposition1.b(1, 0, 1), 0);
                    int viewDistance = world.getServerOH().getViewDistance() * 16;
                    BlockPos soundPos = blockposition1.offset(1, 0, 1);
                    for (ServerPlayer player : world.getServerOH().getServer().getPlayerList().players) {
                        double deltaX = soundPos.getX() - player.getX();
                        double deltaZ = soundPos.getZ() - player.getZ();
                        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                        if (world.spigotConfig.endPortalSoundRadius > 0 && distanceSquared > world.spigotConfig.endPortalSoundRadius * world.spigotConfig.endPortalSoundRadius) continue; // Spigot
                        if (distanceSquared > viewDistance * viewDistance) {
                            double deltaLength = Math.sqrt(distanceSquared);
                            double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                            double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                            player.connection.sendPacket(new ClientboundLevelEventPacket(1038, new BlockPos((int) relativeX, (int) soundPos.getY(), (int) relativeZ), 0, true));
                        } else {
                            player.connection.sendPacket(new ClientboundLevelEventPacket(1038, soundPos, 0, true));
                        }
                    }
                    // CraftBukkit end
                }

                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        BlockHitResult movingobjectpositionblock = getPlayerPOVHitResult(world, entityhuman, ClipContext.Fluid.NONE);

        if (movingobjectpositionblock.getType() == HitResult.Type.BLOCK && world.getType(((BlockHitResult) movingobjectpositionblock).getBlockPos()).is(Blocks.END_PORTAL_FRAME)) {
            return InteractionResultHolder.pass(itemstack);
        } else {
            entityhuman.startUsingItem(enumhand);
            if (world instanceof ServerLevel) {
                BlockPos blockposition = ((ServerLevel) world).getChunkSourceOH().getGenerator().findNearestMapFeature((ServerLevel) world, StructureFeature.STRONGHOLD, entityhuman.blockPosition(), 100, false);

                if (blockposition != null) {
                    EyeOfEnder entityendersignal = new EyeOfEnder(world, entityhuman.getX(), entityhuman.getY(0.5D), entityhuman.getZ());

                    entityendersignal.setItem(itemstack);
                    entityendersignal.signalTo(blockposition);
                    // CraftBukkit start
                    if (!world.addFreshEntity(entityendersignal)) {
                        return new InteractionResultHolder(InteractionResult.FAIL, itemstack);
                    }
                    // CraftBukkit end
                    if (entityhuman instanceof ServerPlayer) {
                        CriteriaTriggers.USED_ENDER_EYE.trigger((ServerPlayer) entityhuman, blockposition);
                    }

                    world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, 0.5F, 0.4F / (EnderEyeItem.random.nextFloat() * 0.4F + 0.8F));
                    world.levelEvent((Player) null, 1003, entityhuman.blockPosition(), 0);
                    if (!entityhuman.abilities.instabuild) {
                        itemstack.shrink(1);
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(this));
                    entityhuman.swing(enumhand, true);
                    return InteractionResultHolder.success(itemstack);
                }
            }

            return InteractionResultHolder.consume(itemstack);
        }
    }
}
