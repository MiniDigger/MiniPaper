package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import java.util.Set;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.RandomValueBounds;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LootingEnchantFunction extends LootItemConditionalFunction {

    private final RandomValueBounds value;
    private final int limit;

    private LootingEnchantFunction(LootItemCondition[] alootitemcondition, RandomValueBounds lootvaluebounds, int i) {
        super(alootitemcondition);
        this.value = lootvaluebounds;
        this.limit = i;
    }

    @Override
    public LootItemFunctionType getType() {
        return LootItemFunctions.LOOTING_ENCHANT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.KILLER_ENTITY);
    }

    private boolean hasLimit() {
        return this.limit > 0;
    }

    @Override
    public ItemStack run(ItemStack itemstack, LootContext loottableinfo) {
        Entity entity = (Entity) loottableinfo.getContextParameter(LootContextParams.KILLER_ENTITY);

        if (entity instanceof LivingEntity) {
            int i = EnchantmentHelper.getMobLooting((LivingEntity) entity);
            // CraftBukkit start - use lootingModifier if set by plugin
            if (loottableinfo.hasContextParameter(LootContextParams.LOOTING_MOD)) {
                i = loottableinfo.getContextParameter(LootContextParams.LOOTING_MOD);
            }
            // CraftBukkit end

            if (i <= 0) { // CraftBukkit - account for possible negative looting values from Bukkit
                return itemstack;
            }

            float f = (float) i * this.value.getFloat(loottableinfo.getRandom());

            itemstack.grow(Math.round(f));
            if (this.hasLimit() && itemstack.getCount() > this.limit) {
                itemstack.setCount(this.limit);
            }
        }

        return itemstack;
    }

    public static LootingEnchantFunction.Builder lootingMultiplier(RandomValueBounds lootvaluebounds) {
        return new LootingEnchantFunction.Builder(lootvaluebounds);
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<LootingEnchantFunction> {

        public Serializer() {}

        public void serialize(JsonObject jsonobject, LootingEnchantFunction lootenchantfunction, JsonSerializationContext jsonserializationcontext) {
            super.serialize(jsonobject, lootenchantfunction, jsonserializationcontext); // CraftBukkit - decompile error
            jsonobject.add("count", jsonserializationcontext.serialize(lootenchantfunction.value));
            if (lootenchantfunction.hasLimit()) {
                jsonobject.add("limit", jsonserializationcontext.serialize(lootenchantfunction.limit));
            }

        }

        @Override
        public LootingEnchantFunction deserialize(JsonObject jsonobject, JsonDeserializationContext jsondeserializationcontext, LootItemCondition[] alootitemcondition) {
            int i = GsonHelper.getAsInt(jsonobject, "limit", (int) 0);

            return new LootingEnchantFunction(alootitemcondition, (RandomValueBounds) GsonHelper.getAsObject(jsonobject, "count", jsondeserializationcontext, RandomValueBounds.class), i);
        }
    }

    public static class Builder extends LootItemConditionalFunction.Builder<LootingEnchantFunction.Builder> {

        private final RandomValueBounds count;
        private int limit = 0;

        public Builder(RandomValueBounds lootvaluebounds) {
            this.count = lootvaluebounds;
        }

        @Override
        protected LootingEnchantFunction.Builder getThis() {
            return this;
        }

        public LootingEnchantFunction.Builder setLimit(int i) {
            this.limit = i;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new LootingEnchantFunction(this.getConditions(), this.count, this.limit);
        }
    }
}
