package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {

    protected static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final File file;
    private final Map<String, V> map = Maps.newHashMap();

    public StoredUserList(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public void add(V v0) {
        this.map.put(this.getKeyForUser(v0.getUser()), v0);

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after adding a user.", ioexception);
        }

    }

    @Nullable
    public V get(K k0) {
        this.removeExpired();
        return (V) this.map.get(this.getKeyForUser(k0)); // CraftBukkit - fix decompile error
    }

    public void remove(K k0) {
        this.map.remove(this.getKeyForUser(k0));

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after removing a user.", ioexception);
        }

    }

    public void remove(StoredUserEntry<K> jsonlistentry) {
        this.remove(jsonlistentry.getUser());
    }

    public String[] getUserList() {
        return (String[]) this.map.keySet().toArray(new String[this.map.size()]);
    }

    // CraftBukkit start
    public Collection<V> getValues() {
        return this.map.values();
    }
    // CraftBukkit end

    public boolean isEmpty() {
        return this.map.size() < 1;
    }

    protected String getKeyForUser(K k0) {
        return k0.toString();
    }

    protected boolean contains(K k0) {
        return this.map.containsKey(this.getKeyForUser(k0));
    }

    private void removeExpired() {
        List<K> list = Lists.newArrayList();
        Iterator iterator = this.map.values().iterator();

        while (iterator.hasNext()) {
            V v0 = (V) iterator.next(); // CraftBukkit - decompile error

            if (v0.hasExpired()) {
                list.add(v0.getUser());
            }
        }

        iterator = list.iterator();

        while (iterator.hasNext()) {
            K k0 = (K) iterator.next(); // CraftBukkit - decompile error

            this.map.remove(this.getKeyForUser(k0));
        }

    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject jsonobject);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        JsonArray jsonarray = new JsonArray();

        this.map.values().stream().map((jsonlistentry) -> {
            JsonObject jsonobject = new JsonObject();

            jsonlistentry.getClass();
            return (JsonObject) Util.make(jsonobject, jsonlistentry::serialize); // CraftBukkit - decompile error
        }).forEach(jsonarray::add);
        BufferedWriter bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8);
        Throwable throwable = null;

        try {
            StoredUserList.GSON.toJson(jsonarray, bufferedwriter);
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

    }

    public void load() throws IOException {
        if (this.file.exists()) {
            BufferedReader bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8);
            Throwable throwable = null;

            try {
                JsonArray jsonarray = (JsonArray) StoredUserList.GSON.fromJson(bufferedreader, JsonArray.class);

                this.map.clear();
                Iterator iterator = jsonarray.iterator();

                while (iterator.hasNext()) {
                    JsonElement jsonelement = (JsonElement) iterator.next();
                    JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "entry");
                    StoredUserEntry<K> jsonlistentry = this.createEntry(jsonobject);

                    if (jsonlistentry.getUser() != null) {
                        this.map.put(this.getKeyForUser(jsonlistentry.getUser()), (V) jsonlistentry); // CraftBukkit - fix decompile error
                    }
                }
            // Spigot Start
            } catch ( com.google.gson.JsonParseException ex )
            {
                org.bukkit.Bukkit.getLogger().log( java.util.logging.Level.WARNING, "Unable to read file " + this.file + ", backing it up to {0}.backup and creating new copy.", ex );
                File backup = new File( this.file + ".backup" );
                this.file.renameTo( backup );
                this.file.delete();
            // Spigot End
            } catch (Throwable throwable1) {
                throwable = throwable1;
                throw throwable1;
            } finally {
                if (bufferedreader != null) {
                    if (throwable != null) {
                        try {
                            bufferedreader.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    } else {
                        bufferedreader.close();
                    }
                }

            }

        }
    }
}
