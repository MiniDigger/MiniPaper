package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LootTables extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = Deserializers.createLootTableSerializer().create();
    private Map<ResourceLocation, LootTable> tables = ImmutableMap.of();
    public Map<LootTable, ResourceLocation> lootTableToKey = ImmutableMap.of(); // CraftBukkit
    private final PredicateManager predicateManager;

    public LootTables(PredicateManager lootpredicatemanager) {
        super(LootTables.GSON, "loot_tables");
        this.predicateManager = lootpredicatemanager;
    }

    public LootTable get(ResourceLocation minecraftkey) {
        return (LootTable) this.tables.getOrDefault(minecraftkey, LootTable.EMPTY);
    }

    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager iresourcemanager, ProfilerFiller gameprofilerfiller) {
        Builder<ResourceLocation, LootTable> builder = ImmutableMap.builder();
        JsonElement jsonelement = (JsonElement) map.remove(BuiltInLootTables.EMPTY);

        if (jsonelement != null) {
            LootTables.LOGGER.warn("Datapack tried to redefine {} loot table, ignoring", BuiltInLootTables.EMPTY);
        }

        map.forEach((minecraftkey, jsonelement1) -> {
            try {
                LootTable loottable = (LootTable) LootTables.GSON.fromJson(jsonelement1, LootTable.class);

                builder.put(minecraftkey, loottable);
            } catch (Exception exception) {
                LootTables.LOGGER.error("Couldn't parse loot table {}", minecraftkey, exception);
            }

        });
        builder.put(BuiltInLootTables.EMPTY, LootTable.EMPTY);
        ImmutableMap<ResourceLocation, LootTable> immutablemap = builder.build();
        LootContextParamSet lootcontextparameterset = LootContextParamSets.ALL_PARAMS;
        PredicateManager lootpredicatemanager = this.predicateManager;

        this.predicateManager.getClass();
        Function<ResourceLocation, LootItemCondition> function = lootpredicatemanager::get; // CraftBukkit - decompile error

        immutablemap.getClass();
        ValidationContext lootcollector = new ValidationContext(lootcontextparameterset, function, immutablemap::get);

        immutablemap.forEach((minecraftkey, loottable) -> {
            validate(lootcollector, minecraftkey, loottable);
        });
        lootcollector.getProblems().forEach((s, s1) -> {
            LootTables.LOGGER.warn("Found validation problem in " + s + ": " + s1);
        });
        this.tables = immutablemap;
        // CraftBukkit start - build a reversed registry map
        ImmutableMap.Builder<LootTable, ResourceLocation> lootTableToKeyBuilder = ImmutableMap.builder();
        this.tables.forEach((lootTable, key) -> lootTableToKeyBuilder.put(key, lootTable)); // PAIL rename keyToLootTable
        this.lootTableToKey = lootTableToKeyBuilder.build();
        // CraftBukkit end
    }

    public static void validate(ValidationContext lootcollector, ResourceLocation minecraftkey, LootTable loottable) {
        loottable.validate(lootcollector.setParams(loottable.getParamSet()).enterTable("{" + minecraftkey + "}", minecraftkey));
    }

    public static JsonElement serialize(LootTable loottable) {
        return LootTables.GSON.toJsonTree(loottable);
    }

    public Set<ResourceLocation> getIds() {
        return this.tables.keySet();
    }
}
