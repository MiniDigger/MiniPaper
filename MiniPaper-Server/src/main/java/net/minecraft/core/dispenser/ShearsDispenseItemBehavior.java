package net.minecraft.core.dispenser;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class ShearsDispenseItemBehavior extends OptionalDispenseItemBehavior {

    public ShearsDispenseItemBehavior() {}

    @Override
    protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
        Level world = isourceblock.getLevel();
        // CraftBukkit start
        org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

        BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
        if (!DispenserBlock.eventFired) {
            world.getServerOH().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            return itemstack;
        }

        if (!event.getItem().equals(craftItem)) {
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                idispensebehavior.dispense(isourceblock, eventStack);
                return itemstack;
            }
        }
        // CraftBukkit end

        if (!world.isClientSide()) {
            BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));

            this.setSuccess(tryShearBeehive((ServerLevel) world, blockposition) || b((ServerLevel) world, blockposition, bukkitBlock, craftItem)); // CraftBukkit
            if (this.isSuccess() && itemstack.hurt(1, world.getRandom(), (ServerPlayer) null)) {
                itemstack.setCount(0);
            }
        }

        return itemstack;
    }

    private static boolean tryShearBeehive(ServerLevel worldserver, BlockPos blockposition) {
        BlockState iblockdata = worldserver.getType(blockposition);

        if (iblockdata.is((Tag) BlockTags.BEEHIVES)) {
            int i = (Integer) iblockdata.getValue(BeehiveBlock.HONEY_LEVEL);

            if (i >= 5) {
                worldserver.playSound((Player) null, blockposition, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                BeehiveBlock.dropHoneycomb((Level) worldserver, blockposition);
                ((BeehiveBlock) iblockdata.getBlock()).releaseBeesAndResetHoneyLevel(worldserver, iblockdata, blockposition, (Player) null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                return true;
            }
        }

        return false;
    }

    private static boolean b(ServerLevel worldserver, BlockPos blockposition, org.bukkit.block.Block bukkitBlock, CraftItemStack craftItem) { // CraftBukkit - add args
        List<LivingEntity> list = worldserver.getEntitiesOfClass(LivingEntity.class, new AABB(blockposition), EntitySelector.NO_SPECTATORS);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            LivingEntity entityliving = (LivingEntity) iterator.next();

            if (entityliving instanceof Shearable) {
                Shearable ishearable = (Shearable) entityliving;

                if (ishearable.readyForShearing()) {
                    // CraftBukkit start
                    if (CraftEventFactory.callBlockShearEntityEvent(entityliving, bukkitBlock, craftItem).isCancelled()) {
                        continue;
                    }
                    // CraftBukkit end
                    ishearable.shear(SoundSource.BLOCKS);
                    return true;
                }
            }
        }

        return false;
    }
}
