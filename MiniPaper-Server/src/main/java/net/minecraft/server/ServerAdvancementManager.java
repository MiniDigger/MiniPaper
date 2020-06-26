package net.minecraft.server;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.PredicateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerAdvancementManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final Gson GSON = (new GsonBuilder()).create();
    public AdvancementList advancements = new AdvancementList();
    private final PredicateManager predicateManager;

    public ServerAdvancementManager(PredicateManager lootpredicatemanager) {
        super(ServerAdvancementManager.GSON, "advancements");
        this.predicateManager = lootpredicatemanager;
    }

    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager iresourcemanager, ProfilerFiller gameprofilerfiller) {
        Map<ResourceLocation, Advancement.SerializedAdvancement> map1 = Maps.newHashMap();

        map.forEach((minecraftkey, jsonelement) -> {
            // Spigot start
            if (org.spigotmc.SpigotConfig.disabledAdvancements != null && (org.spigotmc.SpigotConfig.disabledAdvancements.contains("*") || org.spigotmc.SpigotConfig.disabledAdvancements.contains(minecraftkey.toString()))) {
                return;
            }
            // Spigot end

            try {
                JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "advancement");
                Advancement.SerializedAdvancement advancement_serializedadvancement = Advancement.SerializedAdvancement.a(jsonobject, new DeserializationContext(minecraftkey, this.predicateManager));

                map1.put(minecraftkey, advancement_serializedadvancement);
            } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
                ServerAdvancementManager.LOGGER.error("Parsing error loading custom advancement {}: {}", minecraftkey, jsonparseexception.getMessage());
            }

        });
        AdvancementList advancements = new AdvancementList();

        advancements.add((Map) map1);
        Iterator iterator = advancements.getRoots().iterator();

        while (iterator.hasNext()) {
            Advancement advancement = (Advancement) iterator.next();

            if (advancement.getDisplay() != null) {
                TreeNodePosition.run(advancement);
            }
        }

        this.advancements = advancements;
    }

    @Nullable
    public Advancement getAdvancement(ResourceLocation minecraftkey) {
        return this.advancements.get(minecraftkey);
    }

    public Collection<Advancement> getAllAdvancements() {
        return this.advancements.getAllAdvancements();
    }
}
