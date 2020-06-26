package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import java.util.Set;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class LootItemRandomChanceWithLootingCondition implements LootItemCondition {

    private final float percent;
    private final float lootingMultiplier;

    private LootItemRandomChanceWithLootingCondition(float f, float f1) {
        this.percent = f;
        this.lootingMultiplier = f1;
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.RANDOM_CHANCE_WITH_LOOTING;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.KILLER_ENTITY);
    }

    public boolean test(LootContext loottableinfo) {
        Entity entity = (Entity) loottableinfo.getContextParameter(LootContextParams.KILLER_ENTITY);
        int i = 0;

        if (entity instanceof LivingEntity) {
            i = EnchantmentHelper.getMobLooting((LivingEntity) entity);
        }
        // CraftBukkit start - only use lootingModifier if set by Bukkit
        if (loottableinfo.hasContextParameter(LootContextParams.LOOTING_MOD)) {
            i = loottableinfo.getContextParameter(LootContextParams.LOOTING_MOD);
        }
        // CraftBukkit end

        return loottableinfo.getRandom().nextFloat() < this.percent + (float) i * this.lootingMultiplier;
    }

    public static LootItemCondition.Builder randomChanceAndLootingBoost(float f, float f1) {
        return () -> {
            return new LootItemRandomChanceWithLootingCondition(f, f1);
        };
    }

    public static class Serializer implements net.minecraft.world.level.storage.loot.Serializer<LootItemRandomChanceWithLootingCondition> {

        public Serializer() {}

        public void serialize(JsonObject jsonobject, LootItemRandomChanceWithLootingCondition lootitemconditionrandomchancewithlooting, JsonSerializationContext jsonserializationcontext) {
            jsonobject.addProperty("chance", lootitemconditionrandomchancewithlooting.percent);
            jsonobject.addProperty("looting_multiplier", lootitemconditionrandomchancewithlooting.lootingMultiplier);
        }

        @Override
        public LootItemRandomChanceWithLootingCondition a(JsonObject jsonobject, JsonDeserializationContext jsondeserializationcontext) {
            return new LootItemRandomChanceWithLootingCondition(GsonHelper.getAsFloat(jsonobject, "chance"), GsonHelper.getAsFloat(jsonobject, "looting_multiplier"));
        }
    }
}
