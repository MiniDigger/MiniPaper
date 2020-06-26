package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.Lists;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.apache.commons.lang3.ArrayUtils;

public abstract class LootPoolSingletonContainer extends LootPoolEntryContainer {

    protected final int weight; public int getWeight() { return weight; } // Paper - OBFHELPER
    protected final int quality; public int getQuality() { return quality; } // Paper - OBFHELPER
    protected final LootItemFunction[] functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final LootPoolEntry entry = new LootPoolSingletonContainer.EntryBase() {
        @Override
        public void createItemStack(Consumer<ItemStack> consumer, LootContext loottableinfo) {
            LootPoolSingletonContainer.this.createItemStack(LootItemFunction.decorate(LootPoolSingletonContainer.this.compositeFunction, consumer, loottableinfo), loottableinfo);
        }
    };

    protected LootPoolSingletonContainer(int i, int j, LootItemCondition[] alootitemcondition, LootItemFunction[] alootitemfunction) {
        super(alootitemcondition);
        this.weight = i;
        this.quality = j;
        this.functions = alootitemfunction;
        this.compositeFunction = LootItemFunctions.compose((BiFunction[]) alootitemfunction);
    }

    @Override
    public void validate(ValidationContext lootcollector) {
        super.validate(lootcollector);

        for (int i = 0; i < this.functions.length; ++i) {
            this.functions[i].validate(lootcollector.forChild(".functions[" + i + "]"));
        }

    }

    protected abstract void createItemStack(Consumer<ItemStack> consumer, LootContext loottableinfo);

    @Override
    public boolean expand(LootContext loottableinfo, Consumer<LootPoolEntry> consumer) {
        if (this.canRun(loottableinfo)) {
            consumer.accept(this.entry);
            return true;
        } else {
            return false;
        }
    }

    public static LootPoolSingletonContainer.Builder<?> simpleBuilder(LootPoolSingletonContainer.EntryConstructor lootselectorentry_d) {
        return new LootPoolSingletonContainer.DummyBuilder(lootselectorentry_d);
    }

    public abstract static class Serializer<T extends LootPoolSingletonContainer> extends LootPoolEntryContainer.Serializer<T> {

        public Serializer(ResourceLocation minecraftkey, Class<T> oclass) {
            super(minecraftkey, oclass);
        }

        public void serialize(JsonObject jsonobject, T t0, JsonSerializationContext jsonserializationcontext) {
            if (t0.weight != 1) {
                jsonobject.addProperty("weight", t0.weight);
            }

            if (t0.quality != 0) {
                jsonobject.addProperty("quality", t0.quality);
            }

            if (!ArrayUtils.isEmpty(t0.functions)) {
                jsonobject.add("functions", jsonserializationcontext.serialize(t0.functions));
            }

        }

        @Override
        public final T deserialize(JsonObject jsonobject, JsonDeserializationContext jsondeserializationcontext, LootItemCondition[] alootitemcondition) {
            int i = GsonHelper.getAsInt(jsonobject, "weight", (int) 1);
            int j = GsonHelper.getAsInt(jsonobject, "quality", (int) 0);
            LootItemFunction[] alootitemfunction = (LootItemFunction[]) GsonHelper.getAsObject(jsonobject, "functions", new LootItemFunction[0], jsondeserializationcontext, LootItemFunction[].class);

            return this.deserialize(jsonobject, jsondeserializationcontext, i, j, alootitemcondition, alootitemfunction);
        }

        protected abstract T deserialize(JsonObject jsonobject, JsonDeserializationContext jsondeserializationcontext, int i, int j, LootItemCondition[] alootitemcondition, LootItemFunction[] alootitemfunction);
    }

    static class DummyBuilder extends LootPoolSingletonContainer.Builder<LootPoolSingletonContainer.DummyBuilder> {

        private final LootPoolSingletonContainer.EntryConstructor constructor;

        public DummyBuilder(LootPoolSingletonContainer.EntryConstructor lootselectorentry_d) {
            this.constructor = lootselectorentry_d;
        }

        @Override
        protected LootPoolSingletonContainer.DummyBuilder getThis() {
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return this.constructor.build(this.weight, this.quality, this.getConditions(), this.getFunctions());
        }
    }

    @FunctionalInterface
    public interface EntryConstructor {

        LootPoolSingletonContainer build(int i, int j, LootItemCondition[] alootitemcondition, LootItemFunction[] alootitemfunction);
    }

    public abstract static class Builder<T extends LootPoolSingletonContainer.Builder<T>> extends LootPoolEntryContainer.Builder<T> implements FunctionUserBuilder<T> {

        protected int weight = 1;
        protected int quality = 0;
        private final List<LootItemFunction> functions = Lists.newArrayList();

        public Builder() {}

        @Override
        public T apply(LootItemFunction.Builder lootitemfunction_a) {
            this.functions.add(lootitemfunction_a.build());
            return this.getThis(); // Paper - decompile fix
        }

        protected LootItemFunction[] getFunctions() {
            return (LootItemFunction[]) this.functions.toArray(new LootItemFunction[0]);
        }

        public T setWeight(int i) {
            this.weight = i;
            return this.getThis(); // Paper - decompile fix
        }

        public T setQuality(int i) {
            this.quality = i;
            return this.getThis(); // Paper - decompile fix
        }
    }

    public abstract class EntryBase implements LootPoolEntry {

        protected EntryBase() {
        }

        @Override
        public int getWeight(float f) {
            // Paper start - Offer an alternative loot formula to refactor how luck bonus applies
            // SEE: https://luckformula.emc.gs for details and data
            if (lastLuck != null && lastLuck == f) {
                return lastWeight;
            }
            // This is vanilla
            float qualityModifer = (float) getQuality() * f;
            double baseWeight = (LootPoolSingletonContainer.this.getWeight() + qualityModifer);
            if (com.destroystokyo.paper.PaperConfig.useAlternativeLuckFormula) {
                // Random boost to avoid losing precision in the final int cast on return
                final int weightBoost = 100;
                baseWeight *= weightBoost;
                // If we have vanilla 1, bump that down to 0 so nothing is is impacted
                // vanilla 3 = 300, 200 basis = impact 2%
                // =($B2*(($B2-100)/100/100))
                double impacted = baseWeight * ((baseWeight - weightBoost) / weightBoost / 100);
                // =($B$7/100)
                float luckModifier = Math.min(100, f * 10) / 100;
                // =B2 - (C2 *($B$7/100))
                baseWeight = Math.ceil(baseWeight - (impacted * luckModifier));
            }
            lastLuck = f;
            lastWeight = (int) Math.max(0, Math.floor(baseWeight));
            return lastWeight;
        }
    }
        private Float lastLuck = null;
        private int lastWeight = 0;
        // Paper end
}
