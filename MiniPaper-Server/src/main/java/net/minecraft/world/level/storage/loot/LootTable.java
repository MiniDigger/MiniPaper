package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Lists;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.world.LootGenerateEvent;
// CraftBukkit end

public class LootTable {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, new LootPool[0], new LootItemFunction[0]);
    public static final LootContextParamSet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    private final LootContextParamSet paramSet;
    private final LootPool[] pools;
    private final LootItemFunction[] functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

    private LootTable(LootContextParamSet lootcontextparameterset, LootPool[] alootselector, LootItemFunction[] alootitemfunction) {
        this.paramSet = lootcontextparameterset;
        this.pools = alootselector;
        this.functions = alootitemfunction;
        this.compositeFunction = LootItemFunctions.compose(alootitemfunction);
    }

    public static Consumer<ItemStack> createStackSplitter(Consumer<ItemStack> consumer) {
        return (itemstack) -> {
            if (itemstack.getCount() < itemstack.getMaxStackSize()) {
                consumer.accept(itemstack);
            } else {
                int i = itemstack.getCount();

                while (i > 0) {
                    ItemStack itemstack1 = itemstack.copy();

                    itemstack1.setCount(Math.min(itemstack.getMaxStackSize(), i));
                    i -= itemstack1.getCount();
                    consumer.accept(itemstack1);
                }
            }

        };
    }

    public void getRandomItemsRaw(LootContext loottableinfo, Consumer<ItemStack> consumer) {
        if (loottableinfo.addVisitedTable(this)) {
            Consumer<ItemStack> consumer1 = LootItemFunction.decorate(this.compositeFunction, consumer, loottableinfo);
            LootPool[] alootselector = this.pools;
            int i = alootselector.length;

            for (int j = 0; j < i; ++j) {
                LootPool lootselector = alootselector[j];

                lootselector.addRandomItems(consumer1, loottableinfo);
            }

            loottableinfo.removeVisitedTable(this);
        } else {
            LootTable.LOGGER.warn("Detected infinite loop in loot tables");
        }

    }

    public void getRandomItems(LootContext loottableinfo, Consumer<ItemStack> consumer) {
        this.getRandomItemsRaw(loottableinfo, createStackSplitter(consumer));
    }

    public List<ItemStack> getRandomItems(LootContext loottableinfo) {
        List<ItemStack> list = Lists.newArrayList();

        this.getRandomItems(loottableinfo, list::add);
        return list;
    }

    public LootContextParamSet getParamSet() {
        return this.paramSet;
    }

    public void validate(ValidationContext lootcollector) {
        int i;

        for (i = 0; i < this.pools.length; ++i) {
            this.pools[i].validate(lootcollector.forChild(".pools[" + i + "]"));
        }

        for (i = 0; i < this.functions.length; ++i) {
            this.functions[i].validate(lootcollector.forChild(".functions[" + i + "]"));
        }

    }

    public void fill(Container iinventory, LootContext loottableinfo) {
        // CraftBukkit start
        this.fillInventory(iinventory, loottableinfo, false);
    }

    public void fillInventory(Container iinventory, LootContext loottableinfo, boolean plugin) {
        // CraftBukkit end
        List<ItemStack> list = this.getRandomItems(loottableinfo);
        Random random = loottableinfo.getRandom();
        // CraftBukkit start
        LootGenerateEvent event = CraftEventFactory.callLootGenerateEvent(iinventory, this, loottableinfo, list, plugin);
        if (event.isCancelled()) {
            return;
        }
        list = event.getLoot().stream().map(CraftItemStack::asNMSCopy).collect(Collectors.toList());
        // CraftBukkit end
        List<Integer> list1 = this.getAvailableSlots(iinventory, random);

        this.shuffleAndSplitItems(list, list1.size(), random);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (list1.isEmpty()) {
                LootTable.LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemstack.isEmpty()) {
                iinventory.setItem((Integer) list1.remove(list1.size() - 1), ItemStack.EMPTY);
            } else {
                iinventory.setItem((Integer) list1.remove(list1.size() - 1), itemstack);
            }
        }

    }

    private void shuffleAndSplitItems(List<ItemStack> list, int i, Random random) {
        List<ItemStack> list1 = Lists.newArrayList();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (itemstack.isEmpty()) {
                iterator.remove();
            } else if (itemstack.getCount() > 1) {
                list1.add(itemstack);
                iterator.remove();
            }
        }

        while (i - list.size() - list1.size() > 0 && !list1.isEmpty()) {
            ItemStack itemstack1 = (ItemStack) list1.remove(Mth.nextInt(random, 0, list1.size() - 1));
            int j = Mth.nextInt(random, 1, itemstack1.getCount() / 2);
            ItemStack itemstack2 = itemstack1.split(j);

            if (itemstack1.getCount() > 1 && random.nextBoolean()) {
                list1.add(itemstack1);
            } else {
                list.add(itemstack1);
            }

            if (itemstack2.getCount() > 1 && random.nextBoolean()) {
                list1.add(itemstack2);
            } else {
                list.add(itemstack2);
            }
        }

        list.addAll(list1);
        Collections.shuffle(list, random);
    }

    private List<Integer> getAvailableSlots(Container iinventory, Random random) {
        List<Integer> list = Lists.newArrayList();

        for (int i = 0; i < iinventory.getContainerSize(); ++i) {
            if (iinventory.getItem(i).isEmpty()) {
                list.add(i);
            }
        }

        Collections.shuffle(list, random);
        return list;
    }

    public static LootTable.Builder lootTable() {
        return new LootTable.Builder();
    }

    public static class Serializer implements JsonDeserializer<LootTable>, JsonSerializer<LootTable> {

        public Serializer() {}

        public LootTable deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "loot table");
            LootPool[] alootselector = (LootPool[]) GsonHelper.getAsObject(jsonobject, "pools", new LootPool[0], jsondeserializationcontext, LootPool[].class);
            LootContextParamSet lootcontextparameterset = null;

            if (jsonobject.has("type")) {
                String s = GsonHelper.getAsString(jsonobject, "type");

                lootcontextparameterset = LootContextParamSets.get(new ResourceLocation(s));
            }

            LootItemFunction[] alootitemfunction = (LootItemFunction[]) GsonHelper.getAsObject(jsonobject, "functions", new LootItemFunction[0], jsondeserializationcontext, LootItemFunction[].class);

            return new LootTable(lootcontextparameterset != null ? lootcontextparameterset : LootContextParamSets.ALL_PARAMS, alootselector, alootitemfunction);
        }

        public JsonElement serialize(LootTable loottable, Type type, JsonSerializationContext jsonserializationcontext) {
            JsonObject jsonobject = new JsonObject();

            if (loottable.paramSet != LootTable.DEFAULT_PARAM_SET) {
                ResourceLocation minecraftkey = LootContextParamSets.getKey(loottable.paramSet);

                if (minecraftkey != null) {
                    jsonobject.addProperty("type", minecraftkey.toString());
                } else {
                    LootTable.LOGGER.warn("Failed to find id for param set " + loottable.paramSet);
                }
            }

            if (loottable.pools.length > 0) {
                jsonobject.add("pools", jsonserializationcontext.serialize(loottable.pools));
            }

            if (!ArrayUtils.isEmpty(loottable.functions)) {
                jsonobject.add("functions", jsonserializationcontext.serialize(loottable.functions));
            }

            return jsonobject;
        }
    }

    public static class Builder implements FunctionUserBuilder<LootTable.Builder> {

        private final List<LootPool> pools = Lists.newArrayList();
        private final List<LootItemFunction> functions = Lists.newArrayList();
        private LootContextParamSet paramSet;

        public Builder() {
            this.paramSet = LootTable.DEFAULT_PARAM_SET;
        }

        public LootTable.Builder withPool(LootPool.Builder lootselector_a) {
            this.pools.add(lootselector_a.build());
            return this;
        }

        public LootTable.Builder setParamSet(LootContextParamSet lootcontextparameterset) {
            this.paramSet = lootcontextparameterset;
            return this;
        }

        @Override
        public LootTable.Builder apply(LootItemFunction.Builder lootitemfunction_a) {
            this.functions.add(lootitemfunction_a.build());
            return this;
        }

        @Override
        public LootTable.Builder unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, (LootPool[]) this.pools.toArray(new LootPool[0]), (LootItemFunction[]) this.functions.toArray(new LootItemFunction[0]));
        }
    }
}
