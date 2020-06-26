package net.minecraft.tags;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

public class TagManager implements PreparableReloadListener {

    private final SynchronizableTagCollection<Block> blocks;
    private final SynchronizableTagCollection<Item> items;
    private final SynchronizableTagCollection<Fluid> fluids;
    private final SynchronizableTagCollection<EntityType<?>> entityTypes;

    public TagManager() {
        this.blocks = new SynchronizableTagCollection<>(Registry.BLOCK, "tags/blocks", "block");
        this.items = new SynchronizableTagCollection<>(Registry.ITEM, "tags/items", "item");
        this.fluids = new SynchronizableTagCollection<>(Registry.FLUID, "tags/fluids", "fluid");
        this.entityTypes = new SynchronizableTagCollection<>(Registry.ENTITY_TYPE, "tags/entity_types", "entity_type");
    }

    public SynchronizableTagCollection<Block> getBlockTags() {
        return this.blocks;
    }

    public SynchronizableTagCollection<Item> getItemTags() {
        return this.items;
    }

    public SynchronizableTagCollection<Fluid> getFluidTags() {
        return this.fluids;
    }

    public SynchronizableTagCollection<EntityType<?>> getEntityTags() {
        return this.entityTypes;
    }

    public void serializeToNetwork(FriendlyByteBuf packetdataserializer) {
        this.blocks.serializeToNetwork(packetdataserializer);
        this.items.serializeToNetwork(packetdataserializer);
        this.fluids.serializeToNetwork(packetdataserializer);
        this.entityTypes.serializeToNetwork(packetdataserializer);
    }

    public static TagManager deserializeFromNetwork(FriendlyByteBuf packetdataserializer) {
        TagManager tagregistry = new TagManager();

        tagregistry.getBlockTags().loadFromNetwork(packetdataserializer);
        tagregistry.getItemTags().loadFromNetwork(packetdataserializer);
        tagregistry.getFluidTags().loadFromNetwork(packetdataserializer);
        tagregistry.getEntityTags().loadFromNetwork(packetdataserializer);
        return tagregistry;
    }

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier ireloadlistener_a, ResourceManager iresourcemanager, ProfilerFiller gameprofilerfiller, ProfilerFiller gameprofilerfiller1, Executor executor, Executor executor1) {
        CompletableFuture<Map<ResourceLocation, Tag.Builder>> completablefuture = this.blocks.prepare(iresourcemanager, executor);
        CompletableFuture<Map<ResourceLocation, Tag.Builder>> completablefuture1 = this.items.prepare(iresourcemanager, executor);
        CompletableFuture<Map<ResourceLocation, Tag.Builder>> completablefuture2 = this.fluids.prepare(iresourcemanager, executor);
        CompletableFuture<Map<ResourceLocation, Tag.Builder>> completablefuture3 = this.entityTypes.prepare(iresourcemanager, executor);
        CompletableFuture completablefuture4 = CompletableFuture.allOf(completablefuture, completablefuture1, completablefuture2, completablefuture3);

        ireloadlistener_a.getClass();
        return completablefuture4.thenCompose(ireloadlistener_a::wait).thenAcceptAsync((ovoid) -> {
            this.blocks.load((Map) completablefuture.join());
            this.items.load((Map) completablefuture1.join());
            this.fluids.load((Map) completablefuture2.join());
            this.entityTypes.load((Map) completablefuture3.join());
            // CraftBukkit start
            this.blocks.version++;
            this.items.version++;
            this.fluids.version++;
            this.entityTypes.version++;
            // CraftBukkit end
            SerializationTags.bind(this.blocks, this.items, this.fluids, this.entityTypes);
            Multimap<String, ResourceLocation> multimap = HashMultimap.create();

            multimap.putAll("blocks", BlockTags.getMissingTags(this.blocks));
            multimap.putAll("items", ItemTags.getMissingTags(this.items));
            multimap.putAll("fluids", FluidTags.getMissingTags(this.fluids));
            multimap.putAll("entity_types", EntityTypeTags.getMissingTags(this.entityTypes));
            if (!multimap.isEmpty()) {
                throw new IllegalStateException("Missing required tags: " + (String) multimap.entries().stream().map((entry) -> {
                    return (String) entry.getKey() + ":" + entry.getValue();
                }).sorted().collect(Collectors.joining(",")));
            }
        }, executor1);
    }

    public void bindToGlobal() {
        BlockTags.reset((TagCollection) this.blocks);
        ItemTags.reset((TagCollection) this.items);
        FluidTags.reset((TagCollection) this.fluids);
        EntityTypeTags.reset((TagCollection) this.entityTypes);
        Blocks.rebuildCache();
    }
}
