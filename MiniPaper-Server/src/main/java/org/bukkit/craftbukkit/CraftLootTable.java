package org.bukkit.craftbukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;

public class CraftLootTable implements org.bukkit.loot.LootTable {

    private final LootTable handle;
    private final NamespacedKey key;

    public CraftLootTable(NamespacedKey key, LootTable handle) {
        this.handle = handle;
        this.key = key;
    }

    public LootTable getHandle() {
        return handle;
    }

    @Override
    public Collection<ItemStack> populateLoot(Random random, LootContext context) {
        net.minecraft.world.level.storage.loot.LootContext nmsContext = convertContext(context);
        List<net.minecraft.world.item.ItemStack> nmsItems = handle.getRandomItems(nmsContext);
        Collection<ItemStack> bukkit = new ArrayList<>(nmsItems.size());

        for (net.minecraft.world.item.ItemStack item : nmsItems) {
            if (item.isEmpty()) {
                continue;
            }
            bukkit.add(CraftItemStack.asBukkitCopy(item));
        }

        return bukkit;
    }

    @Override
    public void fillInventory(Inventory inventory, Random random, LootContext context) {
        net.minecraft.world.level.storage.loot.LootContext nmsContext = convertContext(context);
        CraftInventory craftInventory = (CraftInventory) inventory;
        Container handle = craftInventory.getInventory();

        // TODO: When events are added, call event here w/ custom reason?
        getHandle().fill(handle, nmsContext);
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    private net.minecraft.world.level.storage.loot.LootContext convertContext(LootContext context) {
        Location loc = context.getLocation();
        ServerLevel handle = ((CraftWorld) loc.getWorld()).getHandle();

        net.minecraft.world.level.storage.loot.LootContext.Builder builder = new net.minecraft.world.level.storage.loot.LootContext.Builder(handle);
        if (getHandle() != LootTable.EMPTY) {
            // builder.luck(context.getLuck());

            if (context.getLootedEntity() != null) {
                Entity nmsLootedEntity = ((CraftEntity) context.getLootedEntity()).getHandle();
                builder.set(LootContextParams.THIS_ENTITY, nmsLootedEntity);
                builder.set(LootContextParams.DAMAGE_SOURCE, DamageSource.GENERIC);
                builder.set(LootContextParams.BLOCK_POS, nmsLootedEntity.blockPosition());
            }

            if (context.getKiller() != null) {
                Player nmsKiller = ((CraftHumanEntity) context.getKiller()).getHandle();
                builder.set(LootContextParams.KILLER_ENTITY, nmsKiller);
                // If there is a player killer, damage source should reflect that in case loot tables use that information
                builder.set(LootContextParams.DAMAGE_SOURCE, DamageSource.playerAttack(nmsKiller));
                builder.set(LootContextParams.LAST_DAMAGE_PLAYER, nmsKiller); // SPIGOT-5603 - Set minecraft:killed_by_player
            }

            // SPIGOT-5603 - Use LootContext#lootingModifier
            if (context.getLootingModifier() != LootContext.DEFAULT_LOOT_MODIFIER) {
                builder.set(LootContextParams.LOOTING_MOD, context.getLootingModifier());
            }
        }

        // SPIGOT-5603 - Avoid IllegalArgumentException in LootTableInfo#build()
        LootContextParamSet.Builder nmsBuilder = new LootContextParamSet.Builder();
        for (LootContextParam<?> param : getHandle().getParamSet().getRequired()) {
            nmsBuilder.addRequired(param);
        }
        for (LootContextParam<?> param : getHandle().getParamSet().getAllowed()) {
            if (!getHandle().getParamSet().getRequired().contains(param)) {
                nmsBuilder.addOptional(param);
            }
        }
        nmsBuilder.addOptional(LootContextParams.LOOTING_MOD);

        return builder.create(nmsBuilder.build());
    }

    public static LootContext convertContext(net.minecraft.world.level.storage.loot.LootContext info) {
        BlockPos position = info.getContextParameter(LootContextParams.BLOCK_POS);
        Location location = new Location(info.getLevel().getWorld(), position.getX(), position.getY(), position.getZ());
        LootContext.Builder contextBuilder = new LootContext.Builder(location);

        if (info.hasContextParameter(LootContextParams.KILLER_ENTITY)) {
            CraftEntity killer = info.getContextParameter(LootContextParams.KILLER_ENTITY).getBukkitEntity();
            if (killer instanceof CraftHumanEntity) {
                contextBuilder.killer((CraftHumanEntity) killer);
            }
        }

        if (info.hasContextParameter(LootContextParams.THIS_ENTITY)) {
            contextBuilder.lootedEntity(info.getContextParameter(LootContextParams.THIS_ENTITY).getBukkitEntity());
        }

        if (info.hasContextParameter(LootContextParams.LOOTING_MOD)) {
            contextBuilder.lootingModifier(info.getContextParameter(LootContextParams.LOOTING_MOD));
        }

        contextBuilder.luck(info.getLuck());
        return contextBuilder.build();
    }

    @Override
    public String toString() {
        return getKey().toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof org.bukkit.loot.LootTable)) {
            return false;
        }

        org.bukkit.loot.LootTable table = (org.bukkit.loot.LootTable) obj;
        return table.getKey().equals(this.getKey());
    }
}
