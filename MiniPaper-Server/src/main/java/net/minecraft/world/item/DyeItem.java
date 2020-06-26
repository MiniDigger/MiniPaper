package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import org.bukkit.event.entity.SheepDyeWoolEvent; // CraftBukkit

public class DyeItem extends Item {

    private static final Map<DyeColor, DyeItem> ITEM_BY_COLOR = Maps.newEnumMap(DyeColor.class);
    private final DyeColor dyeColor;

    public DyeItem(DyeColor enumcolor, Item.Info item_info) {
        super(item_info);
        this.dyeColor = enumcolor;
        DyeItem.ITEM_BY_COLOR.put(enumcolor, this);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack itemstack, Player entityhuman, LivingEntity entityliving, InteractionHand enumhand) {
        if (entityliving instanceof Sheep) {
            Sheep entitysheep = (Sheep) entityliving;

            if (entitysheep.isAlive() && !entitysheep.isSheared() && entitysheep.getColor() != this.dyeColor) {
                if (!entityhuman.level.isClientSide) {
                    // CraftBukkit start
                    byte bColor = (byte) this.dyeColor.getId();
                    SheepDyeWoolEvent event = new SheepDyeWoolEvent((org.bukkit.entity.Sheep) entitysheep.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData(bColor));
                    entitysheep.level.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return InteractionResult.PASS;
                    }

                    entitysheep.setColor(DyeColor.byId((byte) event.getColor().getWoolData()));
                    // CraftBukkit end
                    itemstack.shrink(1);
                }

                return InteractionResult.sidedSuccess(entityhuman.level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    public DyeColor getDyeColor() {
        return this.dyeColor;
    }

    public static DyeItem byColor(DyeColor enumcolor) {
        return (DyeItem) DyeItem.ITEM_BY_COLOR.get(enumcolor);
    }
}
