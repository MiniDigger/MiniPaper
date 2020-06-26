package net.minecraft.advancements;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdvancementList {

    private static final Logger LOGGER = LogManager.getLogger();
    public final Map<ResourceLocation, Advancement> advancements = Maps.newHashMap();
    private final Set<Advancement> roots = Sets.newLinkedHashSet();
    private final Set<Advancement> tasks = Sets.newLinkedHashSet();
    private AdvancementList.Listener listener;

    public AdvancementList() {}

    public void add(Map<ResourceLocation, Advancement.SerializedAdvancement> map) {
        Function function = Functions.forMap(this.advancements, (Object) null);

        label42:
        while (!map.isEmpty()) {
            boolean flag = false;
            Iterator iterator = map.entrySet().iterator();

            Entry entry;

            while (iterator.hasNext()) {
                entry = (Entry) iterator.next();
                ResourceLocation minecraftkey = (ResourceLocation) entry.getKey();
                Advancement.SerializedAdvancement advancement_serializedadvancement = (Advancement.SerializedAdvancement) entry.getValue();

                if (advancement_serializedadvancement.a((java.util.function.Function) function)) {
                    Advancement advancement = advancement_serializedadvancement.b(minecraftkey);

                    this.advancements.put(minecraftkey, advancement);
                    flag = true;
                    iterator.remove();
                    if (advancement.getParent() == null) {
                        this.roots.add(advancement);
                        if (this.listener != null) {
                            this.listener.onAddAdvancementRoot(advancement);
                        }
                    } else {
                        this.tasks.add(advancement);
                        if (this.listener != null) {
                            this.listener.onAddAdvancementTask(advancement);
                        }
                    }
                }
            }

            if (!flag) {
                iterator = map.entrySet().iterator();

                while (true) {
                    if (!iterator.hasNext()) {
                        break label42;
                    }

                    entry = (Entry) iterator.next();
                    AdvancementList.LOGGER.error("Couldn't load advancement {}: {}", entry.getKey(), entry.getValue());
                }
            }
        }

        // Advancements.LOGGER.info("Loaded {} advancements", this.advancements.size()); // CraftBukkit - moved to AdvancementDataWorld#reload
    }

    public Iterable<Advancement> getRoots() {
        return this.roots;
    }

    public Collection<Advancement> getAllAdvancements() {
        return this.advancements.values();
    }

    @Nullable
    public Advancement get(ResourceLocation minecraftkey) {
        return (Advancement) this.advancements.get(minecraftkey);
    }

    public interface Listener {

        void onAddAdvancementRoot(Advancement advancement);

        void onAddAdvancementTask(Advancement advancement);
    }
}
