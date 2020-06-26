package net.minecraft.tags;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SynchronizableTagCollection<T> extends TagCollection<T> {

    private final Registry<T> registry;
    public int version; // CraftBukkit

    public SynchronizableTagCollection(Registry<T> iregistry, String s, String s1) {
        super(iregistry::getOptional, s, s1);
        this.registry = iregistry;
    }

    public void serializeToNetwork(FriendlyByteBuf packetdataserializer) {
        Map<ResourceLocation, Tag<T>> map = this.getAllTags();

        packetdataserializer.writeVarInt(map.size());
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<ResourceLocation, Tag<T>> entry = (Entry) iterator.next();

            packetdataserializer.writeResourceLocation((ResourceLocation) entry.getKey());
            packetdataserializer.writeVarInt(((Tag) entry.getValue()).getValues().size());
            Iterator iterator1 = ((Tag) entry.getValue()).getValues().iterator();

            while (iterator1.hasNext()) {
                T t0 = (T) iterator1.next(); // CraftBukkit - decompile error

                packetdataserializer.writeVarInt(this.registry.getId(t0));
            }
        }

    }

    public void loadFromNetwork(FriendlyByteBuf packetdataserializer) {
        Map<ResourceLocation, Tag<T>> map = Maps.newHashMap();
        int i = packetdataserializer.readVarInt();

        for (int j = 0; j < i; ++j) {
            ResourceLocation minecraftkey = packetdataserializer.readResourceLocation();
            int k = packetdataserializer.readVarInt();
            Builder<T> builder = ImmutableSet.builder();

            for (int l = 0; l < k; ++l) {
                builder.add(this.registry.byId(packetdataserializer.readVarInt()));
            }

            map.put(minecraftkey, Tag.fromSet(builder.build()));
        }

        this.replace((Map) map);
    }
}
