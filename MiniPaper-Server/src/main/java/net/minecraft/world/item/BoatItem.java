package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BoatItem extends Item {

    private static final Predicate<Entity> ENTITY_PREDICATE = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);
    private final Boat.Type type;

    public BoatItem(Boat.Type entityboat_enumboattype, Item.Info item_info) {
        super(item_info);
        this.type = entityboat_enumboattype;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        BlockHitResult movingobjectpositionblock = getPlayerPOVHitResult(world, entityhuman, ClipContext.Fluid.ANY);

        if (movingobjectpositionblock.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else {
            Vec3 vec3d = entityhuman.getViewVector(1.0F);
            double d0 = 5.0D;
            List<Entity> list = world.getEntities(entityhuman, entityhuman.getBoundingBox().expandTowards(vec3d.scale(5.0D)).inflate(1.0D), BoatItem.ENTITY_PREDICATE);

            if (!list.isEmpty()) {
                Vec3 vec3d1 = entityhuman.getEyePosition(1.0F);
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();
                    AABB axisalignedbb = entity.getBoundingBox().inflate((double) entity.getPickRadius());

                    if (axisalignedbb.contains(vec3d1)) {
                        return InteractionResultHolder.pass(itemstack);
                    }
                }
            }

            if (movingobjectpositionblock.getType() == HitResult.Type.BLOCK) {
                // CraftBukkit start - Boat placement
                org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(entityhuman, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), itemstack, enumhand);

                if (event.isCancelled()) {
                    return new InteractionResultHolder(InteractionResult.PASS, itemstack);
                }
                // CraftBukkit end
                Boat entityboat = new Boat(world, movingobjectpositionblock.getLocation().x, movingobjectpositionblock.getLocation().y, movingobjectpositionblock.getLocation().z);

                entityboat.setType(this.type);
                entityboat.yRot = entityhuman.yRot;
                if (!world.noCollision(entityboat, entityboat.getBoundingBox().inflate(-0.1D))) {
                    return InteractionResultHolder.fail(itemstack);
                } else {
                    if (!world.isClientSide) {
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(world, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), entityhuman, entityboat).isCancelled()) {
                            return InteractionResultHolder.fail(itemstack);
                        }

                        if (!world.addFreshEntity(entityboat)) {
                            return new InteractionResultHolder(InteractionResult.PASS, itemstack);
                        }
                        // CraftBukkit end
                        if (!entityhuman.abilities.instabuild) {
                            itemstack.shrink(1);
                        }
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(this));
                    return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
                }
            } else {
                return InteractionResultHolder.pass(itemstack);
            }
        }
    }
}
