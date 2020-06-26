package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.hanging.HangingPlaceEvent; // CraftBukkit

public class LeadItem extends Item {

    public LeadItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        Block block = world.getType(blockposition).getBlock();

        if (block.is((Tag) BlockTags.FENCES)) {
            Player entityhuman = itemactioncontext.getPlayer();

            if (!world.isClientSide && entityhuman != null) {
                bindPlayerMobs(entityhuman, world, blockposition);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static InteractionResult bindPlayerMobs(Player entityhuman, Level world, BlockPos blockposition) {
        LeashFenceKnotEntity entityleash = null;
        boolean flag = false;
        double d0 = 7.0D;
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();
        List<Mob> list = world.getEntitiesOfClass(Mob.class, new AABB((double) i - 7.0D, (double) j - 7.0D, (double) k - 7.0D, (double) i + 7.0D, (double) j + 7.0D, (double) k + 7.0D));
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Mob entityinsentient = (Mob) iterator.next();

            if (entityinsentient.getLeashHolder() == entityhuman) {
                if (entityleash == null) {
                    entityleash = LeashFenceKnotEntity.getOrCreateKnot(world, blockposition);

                    // CraftBukkit start - fire HangingPlaceEvent
                    HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) entityleash.getBukkitEntity(), entityhuman != null ? (org.bukkit.entity.Player) entityhuman.getBukkitEntity() : null, world.getWorld().getBlockAt(i, j, k), org.bukkit.block.BlockFace.SELF);
                    world.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        entityleash.remove();
                        return InteractionResult.PASS;
                    }
                    // CraftBukkit end
                }

                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLeashEntityEvent(entityinsentient, entityleash, entityhuman).isCancelled()) {
                    continue;
                }
                // CraftBukkit end

                entityinsentient.setLeashedTo(entityleash, true);
                flag = true;
            }
        }

        return flag ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
