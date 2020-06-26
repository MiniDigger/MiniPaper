package net.minecraft.network.syncher;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SynchedEntityData {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Class<? extends Entity>, Integer> ENTITY_ID_POOL = Maps.newHashMap();
    private final Entity entity;
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<SynchedEntityData.Item<?>> itemsById = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(); // Spigot - use better map // PAIL
    // private final ReadWriteLock lock = new ReentrantReadWriteLock(); // Spigot - not required
    private boolean isEmpty = true;
    private boolean isDirty;

    public SynchedEntityData(Entity entity) {
        this.entity = entity;
    }

    public static <T> EntityDataAccessor<T> defineId(Class<? extends Entity> oclass, EntityDataSerializer<T> datawatcherserializer) {
        if (SynchedEntityData.LOGGER.isDebugEnabled()) {
            try {
                Class<?> oclass1 = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());

                if (!oclass1.equals(oclass)) {
                    SynchedEntityData.LOGGER.debug("defineId called for: {} from {}", oclass, oclass1, new RuntimeException());
                }
            } catch (ClassNotFoundException classnotfoundexception) {
                ;
            }
        }

        int i;

        if (SynchedEntityData.ENTITY_ID_POOL.containsKey(oclass)) {
            i = (Integer) SynchedEntityData.ENTITY_ID_POOL.get(oclass) + 1;
        } else {
            int j = 0;
            Class oclass2 = oclass;

            while (oclass2 != Entity.class) {
                oclass2 = oclass2.getSuperclass();
                if (SynchedEntityData.ENTITY_ID_POOL.containsKey(oclass2)) {
                    j = (Integer) SynchedEntityData.ENTITY_ID_POOL.get(oclass2) + 1;
                    break;
                }
            }

            i = j;
        }

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is " + 254 + ")");
        } else {
            SynchedEntityData.ENTITY_ID_POOL.put(oclass, i);
            return datawatcherserializer.createAccessor(i);
        }
    }

    public boolean registrationLocked; // Spigot
    public <T> void register(EntityDataAccessor<T> datawatcherobject, T t0) {
        if (this.registrationLocked) throw new IllegalStateException("Registering datawatcher object after entity initialization"); // Spigot
        int i = datawatcherobject.getId();

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is " + 254 + ")");
        } else if (this.itemsById.containsKey(i)) {
            throw new IllegalArgumentException("Duplicate id value for " + i + "!");
        } else if (EntityDataSerializers.getSerializedId(datawatcherobject.getSerializer()) < 0) {
            throw new IllegalArgumentException("Unregistered serializer " + datawatcherobject.getSerializer() + " for " + i + "!");
        } else {
            this.registerObject(datawatcherobject, t0);
        }
    }

    private <T> void registerObject(EntityDataAccessor<T> datawatcherobject, T t0) {
        SynchedEntityData.Item<T> datawatcher_item = new SynchedEntityData.Item<>(datawatcherobject, t0);

        // this.lock.writeLock().lock(); // Spigot - not required
        this.itemsById.put(datawatcherobject.getId(), datawatcher_item);
        this.isEmpty = false;
        // this.lock.writeLock().unlock(); // Spigot - not required
    }

    private <T> SynchedEntityData.Item<T> b(EntityDataAccessor<T> datawatcherobject) {
        // Spigot start
        /*
        this.lock.readLock().lock();

        DataWatcher.Item datawatcher_item;

        try {
            datawatcher_item = (DataWatcher.Item) this.entries.get(datawatcherobject.a());
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.a(throwable, "Getting synched entity data");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Synched entity data");

            crashreportsystemdetails.a("Data ID", (Object) datawatcherobject);
            throw new ReportedException(crashreport);
        } finally {
            this.lock.readLock().unlock();
        }

        return datawatcher_item;
        */
        return (SynchedEntityData.Item) this.itemsById.get(datawatcherobject.getId());
        // Spigot end
    }

    public <T> T get(EntityDataAccessor<T> datawatcherobject) {
        return this.b(datawatcherobject).b();
    }

    public <T> void set(EntityDataAccessor<T> datawatcherobject, T t0) {
        SynchedEntityData.Item<T> datawatcher_item = this.b(datawatcherobject);

        if (ObjectUtils.notEqual(t0, datawatcher_item.b())) {
            datawatcher_item.a(t0);
            this.entity.onSyncedDataUpdated(datawatcherobject);
            datawatcher_item.a(true);
            this.isDirty = true;
        }

    }

    // CraftBukkit start - add method from above
    public <T> void markDirty(EntityDataAccessor<T> datawatcherobject) {
        this.b(datawatcherobject).a(true);
        this.isDirty = true;
    }
    // CraftBukkit end

    public boolean isDirty() {
        return this.isDirty;
    }

    public static void pack(List<SynchedEntityData.Item<?>> list, FriendlyByteBuf packetdataserializer) throws IOException {
        if (list != null) {
            int i = 0;

            for (int j = list.size(); i < j; ++i) {
                a(packetdataserializer, (SynchedEntityData.Item) list.get(i));
            }
        }

        packetdataserializer.writeByte(255);
    }

    @Nullable
    public List<SynchedEntityData.Item<?>> packDirty() {
        List<SynchedEntityData.Item<?>> list = null;

        if (this.isDirty) {
            // this.lock.readLock().lock(); // Spigot - not required
            Iterator iterator = this.itemsById.values().iterator();

            while (iterator.hasNext()) {
                SynchedEntityData.Item<?> datawatcher_item = (SynchedEntityData.Item) iterator.next();

                if (datawatcher_item.c()) {
                    datawatcher_item.a(false);
                    if (list == null) {
                        list = Lists.newArrayList();
                    }

                    list.add(datawatcher_item.d());
                }
            }

            // this.lock.readLock().unlock(); // Spigot - not required
        }

        this.isDirty = false;
        return list;
    }

    @Nullable
    public List<SynchedEntityData.Item<?>> getAll() {
        List<SynchedEntityData.Item<?>> list = null;

        // this.lock.readLock().lock(); // Spigot - not required

        SynchedEntityData.Item datawatcher_item;

        for (Iterator iterator = this.itemsById.values().iterator(); iterator.hasNext(); list.add(datawatcher_item.d())) {
            datawatcher_item = (SynchedEntityData.Item) iterator.next();
            if (list == null) {
                list = Lists.newArrayList();
            }
        }

        // this.lock.readLock().unlock(); // Spigot - not required
        return list;
    }

    private static <T> void a(FriendlyByteBuf packetdataserializer, SynchedEntityData.Item<T> datawatcher_item) throws IOException {
        EntityDataAccessor<T> datawatcherobject = datawatcher_item.a();
        int i = EntityDataSerializers.getSerializedId(datawatcherobject.getSerializer());

        if (i < 0) {
            throw new EncoderException("Unknown serializer type " + datawatcherobject.getSerializer());
        } else {
            packetdataserializer.writeByte(datawatcherobject.getId());
            packetdataserializer.writeVarInt(i);
            datawatcherobject.getSerializer().write(packetdataserializer, datawatcher_item.b());
        }
    }

    @Nullable
    public static List<SynchedEntityData.Item<?>> unpack(FriendlyByteBuf packetdataserializer) throws IOException {
        ArrayList arraylist = null;

        short short0;

        while ((short0 = packetdataserializer.readUnsignedByte()) != 255) {
            if (arraylist == null) {
                arraylist = Lists.newArrayList();
            }

            int i = packetdataserializer.readVarInt();
            EntityDataSerializer<?> datawatcherserializer = EntityDataSerializers.getSerializer(i);

            if (datawatcherserializer == null) {
                throw new DecoderException("Unknown serializer type " + i);
            }

            arraylist.add(a(packetdataserializer, short0, datawatcherserializer));
        }

        return arraylist;
    }

    private static <T> SynchedEntityData.Item<T> a(FriendlyByteBuf packetdataserializer, int i, EntityDataSerializer<T> datawatcherserializer) {
        return new SynchedEntityData.Item<>(datawatcherserializer.createAccessor(i), datawatcherserializer.read(packetdataserializer));
    }

    public boolean isEmpty() {
        return this.isEmpty;
    }

    public void clearDirty() {
        this.isDirty = false;
        // this.lock.readLock().lock(); // Spigot - not required
        Iterator iterator = this.itemsById.values().iterator();

        while (iterator.hasNext()) {
            SynchedEntityData.Item<?> datawatcher_item = (SynchedEntityData.Item) iterator.next();

            datawatcher_item.a(false);
        }

        // this.lock.readLock().unlock(); // Spigot - not required
    }

    public static class Item<T> {

        private final EntityDataAccessor<T> a;
        private T b;
        private boolean c;

        public Item(EntityDataAccessor<T> datawatcherobject, T t0) {
            this.a = datawatcherobject;
            this.b = t0;
            this.c = true;
        }

        public EntityDataAccessor<T> a() {
            return this.a;
        }

        public void a(T t0) {
            this.b = t0;
        }

        public T b() {
            return this.b;
        }

        public boolean c() {
            return this.c;
        }

        public void a(boolean flag) {
            this.c = flag;
        }

        public SynchedEntityData.Item<T> d() {
            return new SynchedEntityData.Item<>(this.a, this.a.getSerializer().copy(this.b));
        }
    }
}
