package net.minecraft.world.item.trading;

import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface Merchant {

    void setTradingPlayer(@Nullable Player entityhuman);

    @Nullable
    Player getTradingPlayer();

    MerchantOffers getOffers();

    void notifyTrade(MerchantOffer merchantrecipe);

    void notifyTradeUpdated(ItemStack itemstack);

    Level getLevel();

    int getVillagerXp();

    void overrideXp(int i);

    boolean showProgressBar();

    SoundEvent getTradeSound();

    default boolean canRestock() {
        return false;
    }

    default void openTradingScreen(Player entityhuman, Component ichatbasecomponent, int i) {
        OptionalInt optionalint = entityhuman.openMenu(new SimpleMenuProvider((j, playerinventory, entityhuman1) -> {
            return new MerchantMenu(j, playerinventory, this);
        }, ichatbasecomponent));

        if (optionalint.isPresent()) {
            MerchantOffers merchantrecipelist = this.getOffers();

            if (!merchantrecipelist.isEmpty()) {
                entityhuman.openTrade(optionalint.getAsInt(), merchantrecipelist, i, this.getVillagerXp(), this.showProgressBar(), this.canRestock());
            }
        }

    }

    org.bukkit.craftbukkit.inventory.CraftMerchant getCraftMerchant(); // CraftBukkit
}
