package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.Lists;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditions;

public abstract class LootPoolEntryContainer implements ComposableEntryContainer {

    protected final LootItemCondition[] conditions;
    private final Predicate<LootContext> compositeCondition;

    protected LootPoolEntryContainer(LootItemCondition[] alootitemcondition) {
        this.conditions = alootitemcondition;
        this.compositeCondition = LootItemConditions.andConditions((Predicate[]) alootitemcondition);
    }

    public void validate(ValidationContext lootcollector) {
        for (int i = 0; i < this.conditions.length; ++i) {
            this.conditions[i].validate(lootcollector.forChild(".condition[" + i + "]"));
        }

    }

    protected final boolean canRun(LootContext loottableinfo) {
        return this.compositeCondition.test(loottableinfo);
    }

    public abstract LootPoolEntryType getType();

    public abstract static class Serializer<T extends LootPoolEntryContainer> implements net.minecraft.world.level.storage.loot.Serializer<T> {

        public Serializer() {}

        // CraftBukkit start
        @Override
        public final void a(JsonObject jsonobject, T t0, JsonSerializationContext jsonserializationcontext) {
            if (!org.apache.commons.lang3.ArrayUtils.isEmpty(t0.conditions)) {
                jsonobject.add("conditions", jsonserializationcontext.serialize(t0.conditions));
            }

            this.serializeCustom(jsonobject, t0, jsonserializationcontext);
        }
        // CraftBukkit end

        @Override
        public final T a(JsonObject jsonobject, JsonDeserializationContext jsondeserializationcontext) {
            LootItemCondition[] alootitemcondition = (LootItemCondition[]) GsonHelper.getAsObject(jsonobject, "conditions", new LootItemCondition[0], jsondeserializationcontext, LootItemCondition[].class);

            return this.deserializeType(jsonobject, jsondeserializationcontext, alootitemcondition);
        }

        public abstract void serializeCustom(JsonObject jsonobject, T t0, JsonSerializationContext jsonserializationcontext);

        public abstract T deserializeType(JsonObject jsonobject, JsonDeserializationContext jsondeserializationcontext, LootItemCondition[] alootitemcondition);
    }

    public abstract static class Builder<T extends LootPoolEntryContainer.Builder<T>> implements ConditionUserBuilder<T> {

        private final List<LootItemCondition> conditions = Lists.newArrayList();

        public Builder() {}

        protected abstract T getThis();

        @Override
        public T when(LootItemCondition.Builder lootitemcondition_a) {
            this.conditions.add(lootitemcondition_a.build());
            return this.getThis();
        }

        @Override
        public final T unwrap() {
            return this.getThis();
        }

        protected LootItemCondition[] getConditions() {
            return (LootItemCondition[]) this.conditions.toArray(new LootItemCondition[0]);
        }

        public AlternativesEntry.Builder otherwise(LootPoolEntryContainer.Builder<?> lootentryabstract_a) {
            return new AlternativesEntry.Builder(new LootPoolEntryContainer.Builder[]{this, lootentryabstract_a});
        }

        public abstract LootPoolEntryContainer build();
    }
}
