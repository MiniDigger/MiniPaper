package net.minecraft.world.item;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ArmorStandItem extends Item {

    public ArmorStandItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Direction enumdirection = itemactioncontext.getClickedFace();

        if (enumdirection == Direction.DOWN) {
            return InteractionResult.FAIL;
        } else {
            Level world = itemactioncontext.getLevel();
            BlockPlaceContext blockactioncontext = new BlockPlaceContext(itemactioncontext);
            BlockPos blockposition = blockactioncontext.getClickedPos();
            ItemStack itemstack = itemactioncontext.getItemInHand();
            ArmorStand entityarmorstand = (ArmorStand) EntityType.ARMOR_STAND.create(world, itemstack.getTag(), (Component) null, itemactioncontext.getPlayer(), blockposition, MobSpawnType.SPAWN_EGG, true, true);

            if (world.noCollision(entityarmorstand) && world.getEntities(entityarmorstand, entityarmorstand.getBoundingBox()).isEmpty()) {
                if (!world.isClientSide) {
                    float f = (float) Mth.floor((Mth.wrapDegrees(itemactioncontext.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;

                    entityarmorstand.moveTo(entityarmorstand.getX(), entityarmorstand.getY(), entityarmorstand.getZ(), f, 0.0F);
                    this.randomizePose(entityarmorstand, world.random);
                    // CraftBukkit start
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(itemactioncontext, entityarmorstand).isCancelled()) {
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    world.addFreshEntity(entityarmorstand);
                    world.playSound((Player) null, entityarmorstand.getX(), entityarmorstand.getY(), entityarmorstand.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
                }

                itemstack.shrink(1);
                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.FAIL;
            }
        }
    }

    private void randomizePose(ArmorStand entityarmorstand, Random random) {
        Rotations vector3f = entityarmorstand.getHeadPose();
        float f = random.nextFloat() * 5.0F;
        float f1 = random.nextFloat() * 20.0F - 10.0F;
        Rotations vector3f1 = new Rotations(vector3f.getX() + f, vector3f.getY() + f1, vector3f.getZ());

        entityarmorstand.setHeadPose(vector3f1);
        vector3f = entityarmorstand.getBodyPose();
        f = random.nextFloat() * 10.0F - 5.0F;
        vector3f1 = new Rotations(vector3f.getX(), vector3f.getY() + f, vector3f.getZ());
        entityarmorstand.setBodyPose(vector3f1);
    }
}
