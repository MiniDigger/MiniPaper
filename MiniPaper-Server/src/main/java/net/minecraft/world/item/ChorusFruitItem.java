package net.minecraft.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class ChorusFruitItem extends Item {

    public ChorusFruitItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entityliving) {
        ItemStack itemstack1 = super.finishUsingItem(itemstack, world, entityliving);

        if (!world.isClientSide) {
            double d0 = entityliving.getX();
            double d1 = entityliving.getY();
            double d2 = entityliving.getZ();

            for (int i = 0; i < 16; ++i) {
                double d3 = entityliving.getX() + (entityliving.getRandom().nextDouble() - 0.5D) * 16.0D;
                double d4 = Mth.clamp(entityliving.getY() + (double) (entityliving.getRandom().nextInt(16) - 8), 0.0D, (double) (world.getHeight() - 1));
                double d5 = entityliving.getZ() + (entityliving.getRandom().nextDouble() - 0.5D) * 16.0D;

                // CraftBukkit start
                if (entityliving instanceof ServerPlayer) {
                    Player player = ((ServerPlayer) entityliving).getBukkitEntity();
                    PlayerTeleportEvent teleEvent = new PlayerTeleportEvent(player, player.getLocation(), new Location(player.getWorld(), d3, d4, d5), PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);
                    world.getServerOH().getPluginManager().callEvent(teleEvent);
                    if (teleEvent.isCancelled()) {
                        break;
                    }
                    d3 = teleEvent.getTo().getX();
                    d4 = teleEvent.getTo().getY();
                    d5 = teleEvent.getTo().getZ();
                }
                // CraftBukkit end

                if (entityliving.isPassenger()) {
                    entityliving.stopRiding();
                }

                if (entityliving.randomTeleport(d3, d4, d5, true)) {
                    SoundEvent soundeffect = entityliving instanceof Fox ? SoundEvents.FOX_TELEPORT : SoundEvents.CHORUS_FRUIT_TELEPORT;

                    world.playSound((net.minecraft.world.entity.player.Player) null, d0, d1, d2, soundeffect, SoundSource.PLAYERS, 1.0F, 1.0F);
                    entityliving.playSound(soundeffect, 1.0F, 1.0F);
                    break;
                }
            }

            if (entityliving instanceof net.minecraft.world.entity.player.Player) {
                ((net.minecraft.world.entity.player.Player) entityliving).getCooldowns().addCooldown(this, 20);
            }
        }

        return itemstack1;
    }
}
