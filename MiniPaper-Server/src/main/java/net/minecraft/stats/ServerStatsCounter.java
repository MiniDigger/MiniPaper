package net.minecraft.stats;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerStatsCounter extends StatsCounter {

    private static final Logger LOGGER = LogManager.getLogger();
    private final MinecraftServer server;
    private final File file;
    private final Set<Stat<?>> dirty = Sets.newHashSet();
    private int lastStatRequest = -300;

    public ServerStatsCounter(MinecraftServer minecraftserver, File file) {
        this.server = minecraftserver;
        this.file = file;
        // Spigot start
        for ( Map.Entry<ResourceLocation, Integer> entry : org.spigotmc.SpigotConfig.forcedStats.entrySet() )
        {
            Stat<ResourceLocation> wrapper = Stats.CUSTOM.get( entry.getKey() );
            this.stats.put( wrapper, entry.getValue().intValue() );
        }
        // Spigot end
        if (file.isFile()) {
            try {
                this.parseLocal(minecraftserver.getFixerUpper(), org.apache.commons.io.FileUtils.readFileToString(file));
            } catch (IOException ioexception) {
                ServerStatsCounter.LOGGER.error("Couldn't read statistics file {}", file, ioexception);
            } catch (JsonParseException jsonparseexception) {
                ServerStatsCounter.LOGGER.error("Couldn't parse statistics file {}", file, jsonparseexception);
            }
        }

    }

    public void save() {
        if ( org.spigotmc.SpigotConfig.disableStatSaving ) return; // Spigot
        try {
            org.apache.commons.io.FileUtils.writeStringToFile(this.file, this.toJson());
        } catch (IOException ioexception) {
            ServerStatsCounter.LOGGER.error("Couldn't save stats", ioexception);
        }

    }

    @Override
    public void setValue(Player entityhuman, Stat<?> statistic, int i) {
        if ( org.spigotmc.SpigotConfig.disableStatSaving ) return; // Spigot
        super.setValue(entityhuman, statistic, i);
        this.dirty.add(statistic);
    }

    private Set<Stat<?>> getDirty() {
        Set<Stat<?>> set = Sets.newHashSet(this.dirty);

        this.dirty.clear();
        return set;
    }

    public void parseLocal(DataFixer datafixer, String s) {
        try {
            JsonReader jsonreader = new JsonReader(new StringReader(s));
            Throwable throwable = null;

            try {
                jsonreader.setLenient(false);
                JsonElement jsonelement = Streams.parse(jsonreader);

                if (!jsonelement.isJsonNull()) {
                    CompoundTag nbttagcompound = fromJson(jsonelement.getAsJsonObject());

                    if (!nbttagcompound.contains("DataVersion", 99)) {
                        nbttagcompound.putInt("DataVersion", 1343);
                    }

                    nbttagcompound = NbtUtils.update(datafixer, DataFixTypes.STATS, nbttagcompound, nbttagcompound.getInt("DataVersion"));
                    if (nbttagcompound.contains("stats", 10)) {
                        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("stats");
                        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

                        while (iterator.hasNext()) {
                            String s1 = (String) iterator.next();

                            if (nbttagcompound1.contains(s1, 10)) {
                                Util.ifElse(Registry.STAT_TYPE.getOptional(new ResourceLocation(s1)), (statisticwrapper) -> {
                                    CompoundTag nbttagcompound2 = nbttagcompound1.getCompound(s1);
                                    Iterator iterator1 = nbttagcompound2.getAllKeys().iterator();

                                    while (iterator1.hasNext()) {
                                        String s2 = (String) iterator1.next();

                                        if (nbttagcompound2.contains(s2, 99)) {
                                            Util.ifElse(this.getStat(statisticwrapper, s2), (statistic) -> {
                                                this.stats.put(statistic, nbttagcompound2.getInt(s2));
                                            }, () -> {
                                                ServerStatsCounter.LOGGER.warn("Invalid statistic in {}: Don't know what {} is", this.file, s2);
                                            });
                                        } else {
                                            ServerStatsCounter.LOGGER.warn("Invalid statistic value in {}: Don't know what {} is for key {}", this.file, nbttagcompound2.get(s2), s2);
                                        }
                                    }

                                }, () -> {
                                    ServerStatsCounter.LOGGER.warn("Invalid statistic type in {}: Don't know what {} is", this.file, s1);
                                });
                            }
                        }
                    }

                    return;
                }

                ServerStatsCounter.LOGGER.error("Unable to parse Stat data from {}", this.file);
            } catch (Throwable throwable1) {
                throwable = throwable1;
                throw throwable1;
            } finally {
                if (jsonreader != null) {
                    if (throwable != null) {
                        try {
                            jsonreader.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    } else {
                        jsonreader.close();
                    }
                }

            }

        } catch (IOException | JsonParseException jsonparseexception) {
            ServerStatsCounter.LOGGER.error("Unable to parse Stat data from {}", this.file, jsonparseexception);
        }
    }

    private <T> Optional<Stat<T>> getStat(StatType<T> statisticwrapper, String s) {
        Optional<ResourceLocation> optional = Optional.ofNullable(ResourceLocation.tryParse(s));
        Registry<T> iregistry = statisticwrapper.getRegistry();

        iregistry.getClass();
        Optional<T> optional2 = optional.flatMap(iregistry::getOptional);
        statisticwrapper.getClass();
        return optional2.map(statisticwrapper::get);
    }

    private static CompoundTag fromJson(JsonObject jsonobject) {
        CompoundTag nbttagcompound = new CompoundTag();
        Iterator iterator = jsonobject.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, JsonElement> entry = (Entry) iterator.next();
            JsonElement jsonelement = (JsonElement) entry.getValue();

            if (jsonelement.isJsonObject()) {
                nbttagcompound.put((String) entry.getKey(), fromJson(jsonelement.getAsJsonObject()));
            } else if (jsonelement.isJsonPrimitive()) {
                JsonPrimitive jsonprimitive = jsonelement.getAsJsonPrimitive();

                if (jsonprimitive.isNumber()) {
                    nbttagcompound.putInt((String) entry.getKey(), jsonprimitive.getAsInt());
                }
            }
        }

        return nbttagcompound;
    }

    protected String toJson() {
        Map<StatType<?>, JsonObject> map = Maps.newHashMap();
        ObjectIterator objectiterator = this.stats.object2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Stat<?>> it_unimi_dsi_fastutil_objects_object2intmap_entry = (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry) objectiterator.next();
            Stat<?> statistic = (Stat) it_unimi_dsi_fastutil_objects_object2intmap_entry.getKey();

            ((JsonObject) map.computeIfAbsent(statistic.getType(), (statisticwrapper) -> {
                return new JsonObject();
            })).addProperty(getKey(statistic).toString(), it_unimi_dsi_fastutil_objects_object2intmap_entry.getIntValue());
        }

        JsonObject jsonobject = new JsonObject();
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<StatType<?>, JsonObject> entry = (Entry) iterator.next();

            jsonobject.add(Registry.STAT_TYPE.getKey(entry.getKey()).toString(), (JsonElement) entry.getValue());
        }

        JsonObject jsonobject1 = new JsonObject();

        jsonobject1.add("stats", jsonobject);
        jsonobject1.addProperty("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        return jsonobject1.toString();
    }

    private static <T> ResourceLocation getKey(Stat<T> statistic) {
        return statistic.getType().getRegistry().getKey(statistic.getValue());
    }

    public void markAllDirty() {
        this.dirty.addAll(this.stats.keySet());
    }

    public void sendStats(ServerPlayer entityplayer) {
        int i = this.server.getTickCount();
        Object2IntMap<Stat<?>> object2intmap = new Object2IntOpenHashMap();

        if (i - this.lastStatRequest > 300) {
            this.lastStatRequest = i;
            Iterator iterator = this.getDirty().iterator();

            while (iterator.hasNext()) {
                Stat<?> statistic = (Stat) iterator.next();

                object2intmap.put(statistic, this.getValue(statistic));
            }
        }

        entityplayer.connection.sendPacket(new ClientboundAwardStatsPacket(object2intmap));
    }
}
