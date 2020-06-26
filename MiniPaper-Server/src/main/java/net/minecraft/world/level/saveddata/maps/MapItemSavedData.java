package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import java.util.UUID;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.bukkit.craftbukkit.util.CraftChatMessage;
// CraftBukkit end

public class MapItemSavedData extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    public int x;
    public int z;
    public ResourceKey<Level> dimension;
    public boolean trackingPosition;
    public boolean unlimitedTracking;
    public byte scale;
    public byte[] colors = new byte[16384];
    public boolean locked;
    public final List<MapItemSavedData.HoldingPlayer> carriedBy = Lists.newArrayList();
    public final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers = Maps.newHashMap();
    private final Map<String, MapBanner> bannerMarkers = Maps.newHashMap();
    public final Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();
    private final Map<String, MapFrame> frameMarkers = Maps.newHashMap();

    // CraftBukkit start
    public final CraftMapView mapView;
    private CraftServer server;
    private UUID uniqueId = null;
    // CraftBukkit end

    public MapItemSavedData(String s) {
        super(s);
        // CraftBukkit start
        mapView = new CraftMapView(this);
        server = (CraftServer) org.bukkit.Bukkit.getServer();
        // CraftBukkit end
    }

    public void setProperties(int i, int j, int k, boolean flag, boolean flag1, ResourceKey<Level> resourcekey) {
        this.scale = (byte) k;
        this.setOrigin((double) i, (double) j, this.scale);
        this.dimension = resourcekey;
        this.trackingPosition = flag;
        this.unlimitedTracking = flag1;
        this.setDirty();
    }

    public void setOrigin(double d0, double d1, int i) {
        int j = 128 * (1 << i);
        int k = Mth.floor((d0 + 64.0D) / (double) j);
        int l = Mth.floor((d1 + 64.0D) / (double) j);

        this.x = k * j + j / 2 - 64;
        this.z = l * j + j / 2 - 64;
    }

    @Override
    public void load(CompoundTag nbttagcompound) {
        DataResult<ResourceKey<Level>> dataresult = DimensionType.parseLegacy(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("dimension"))); // CraftBukkit - decompile error
        Logger logger = MapItemSavedData.LOGGER;

        logger.getClass();
        // CraftBukkit start
        this.dimension = (ResourceKey) dataresult.resultOrPartial(logger::error).orElseGet(() -> {
            long least = nbttagcompound.getLong("UUIDLeast");
            long most = nbttagcompound.getLong("UUIDMost");

            if (least != 0L && most != 0L) {
                this.uniqueId = new UUID(most, least);

                CraftWorld world = (CraftWorld) server.getWorld(this.uniqueId);
                // Check if the stored world details are correct.
                if (world == null) {
                    /* All Maps which do not have their valid world loaded are set to a dimension which hopefully won't be reached.
                       This is to prevent them being corrupted with the wrong map data. */
                    // PAIL: Use Vanilla exception handling for now
                } else {
                    return world.getHandle().getDimensionKey();
                }
            }
            throw new IllegalArgumentException("Invalid map dimension: " + nbttagcompound.get("dimension"));
            // CraftBukkit end
        });
        this.x = nbttagcompound.getInt("xCenter");
        this.z = nbttagcompound.getInt("zCenter");
        this.scale = (byte) Mth.clamp(nbttagcompound.getByte("scale"), 0, 4);
        this.trackingPosition = !nbttagcompound.contains("trackingPosition", 1) || nbttagcompound.getBoolean("trackingPosition");
        this.unlimitedTracking = nbttagcompound.getBoolean("unlimitedTracking");
        this.locked = nbttagcompound.getBoolean("locked");
        this.colors = nbttagcompound.getByteArray("colors");
        if (this.colors.length != 16384) {
            this.colors = new byte[16384];
        }

        ListTag nbttaglist = nbttagcompound.getList("banners", 10);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            MapBanner mapiconbanner = MapBanner.load(nbttaglist.getCompound(i));

            this.bannerMarkers.put(mapiconbanner.getId(), mapiconbanner);
            this.addDecoration(mapiconbanner.getDecoration(), (LevelAccessor) null, mapiconbanner.getId(), (double) mapiconbanner.getPos().getX(), (double) mapiconbanner.getPos().getZ(), 180.0D, mapiconbanner.getName());
        }

        ListTag nbttaglist1 = nbttagcompound.getList("frames", 10);

        for (int j = 0; j < nbttaglist1.size(); ++j) {
            MapFrame worldmapframe = MapFrame.load(nbttaglist1.getCompound(j));

            this.frameMarkers.put(worldmapframe.getId(), worldmapframe);
            this.addDecoration(MapDecoration.Type.FRAME, (LevelAccessor) null, "frame-" + worldmapframe.getEntityId(), (double) worldmapframe.getPos().getX(), (double) worldmapframe.getPos().getZ(), (double) worldmapframe.getRotation(), (Component) null);
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        DataResult<Tag> dataresult = ResourceLocation.CODEC.encodeStart(NbtOps.INSTANCE, this.dimension.location()); // CraftBukkit - decompile error
        Logger logger = MapItemSavedData.LOGGER;

        logger.getClass();
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbttagcompound.put("dimension", nbtbase);
        });
        // CraftBukkit start
        if (true) {
            if (this.uniqueId == null) {
                for (org.bukkit.World world : server.getWorlds()) {
                    CraftWorld cWorld = (CraftWorld) world;
                    if (cWorld.getHandle().getDimensionKey() == this.dimension) {
                        this.uniqueId = cWorld.getUID();
                        break;
                    }
                }
            }
            /* Perform a second check to see if a matching world was found, this is a necessary
               change incase Maps are forcefully unlinked from a World and lack a UID.*/
            if (this.uniqueId != null) {
                nbttagcompound.putLong("UUIDLeast", this.uniqueId.getLeastSignificantBits());
                nbttagcompound.putLong("UUIDMost", this.uniqueId.getMostSignificantBits());
            }
        }
        // CraftBukkit end
        nbttagcompound.putInt("xCenter", this.x);
        nbttagcompound.putInt("zCenter", this.z);
        nbttagcompound.putByte("scale", this.scale);
        nbttagcompound.putByteArray("colors", this.colors);
        nbttagcompound.putBoolean("trackingPosition", this.trackingPosition);
        nbttagcompound.putBoolean("unlimitedTracking", this.unlimitedTracking);
        nbttagcompound.putBoolean("locked", this.locked);
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapiconbanner = (MapBanner) iterator.next();

            nbttaglist.add(mapiconbanner.save());
        }

        nbttagcompound.put("banners", nbttaglist);
        ListTag nbttaglist1 = new ListTag();
        Iterator iterator1 = this.frameMarkers.values().iterator();

        while (iterator1.hasNext()) {
            MapFrame worldmapframe = (MapFrame) iterator1.next();

            nbttaglist1.add(worldmapframe.save());
        }

        nbttagcompound.put("frames", nbttaglist1);
        return nbttagcompound;
    }

    public void lockData(MapItemSavedData worldmap) {
        this.locked = true;
        this.x = worldmap.x;
        this.z = worldmap.z;
        this.bannerMarkers.putAll(worldmap.bannerMarkers);
        this.decorations.putAll(worldmap.decorations);
        System.arraycopy(worldmap.colors, 0, this.colors, 0, worldmap.colors.length);
        this.setDirty();
    }

    public void tickCarriedBy(Player entityhuman, ItemStack itemstack) {
        if (!this.carriedByPlayers.containsKey(entityhuman)) {
            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = new MapItemSavedData.HoldingPlayer(entityhuman);

            this.carriedByPlayers.put(entityhuman, worldmap_worldmaphumantracker);
            this.carriedBy.add(worldmap_worldmaphumantracker);
        }

        if (!entityhuman.inventory.contains(itemstack)) {
            this.decorations.remove(entityhuman.getName().getString());
        }

        for (int i = 0; i < this.carriedBy.size(); ++i) {
            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker1 = (MapItemSavedData.HoldingPlayer) this.carriedBy.get(i);
            String s = worldmap_worldmaphumantracker1.player.getName().getString();

            if (!worldmap_worldmaphumantracker1.player.removed && (worldmap_worldmaphumantracker1.player.inventory.contains(itemstack) || itemstack.isFramed())) {
                if (!itemstack.isFramed() && worldmap_worldmaphumantracker1.player.level.getDimensionKey() == this.dimension && this.trackingPosition) {
                    this.addDecoration(MapDecoration.Type.PLAYER, worldmap_worldmaphumantracker1.player.level, s, worldmap_worldmaphumantracker1.player.getX(), worldmap_worldmaphumantracker1.player.getZ(), (double) worldmap_worldmaphumantracker1.player.yRot, (Component) null);
                }
            } else {
                this.carriedByPlayers.remove(worldmap_worldmaphumantracker1.player);
                this.carriedBy.remove(worldmap_worldmaphumantracker1);
                this.decorations.remove(s);
            }
        }

        if (itemstack.isFramed() && this.trackingPosition) {
            ItemFrame entityitemframe = itemstack.getFrame();
            BlockPos blockposition = entityitemframe.getPos();
            MapFrame worldmapframe = (MapFrame) this.frameMarkers.get(MapFrame.frameId(blockposition));

            if (worldmapframe != null && entityitemframe.getId() != worldmapframe.getEntityId() && this.frameMarkers.containsKey(worldmapframe.getId())) {
                this.decorations.remove("frame-" + worldmapframe.getEntityId());
            }

            MapFrame worldmapframe1 = new MapFrame(blockposition, entityitemframe.getDirection().get2DDataValue() * 90, entityitemframe.getId());

            this.addDecoration(MapDecoration.Type.FRAME, entityhuman.level, "frame-" + entityitemframe.getId(), (double) blockposition.getX(), (double) blockposition.getZ(), (double) (entityitemframe.getDirection().get2DDataValue() * 90), (Component) null);
            this.frameMarkers.put(worldmapframe1.getId(), worldmapframe1);
        }

        CompoundTag nbttagcompound = itemstack.getTag();

        if (nbttagcompound != null && nbttagcompound.contains("Decorations", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("Decorations", 10);

            for (int j = 0; j < nbttaglist.size(); ++j) {
                CompoundTag nbttagcompound1 = nbttaglist.getCompound(j);

                if (!this.decorations.containsKey(nbttagcompound1.getString("id"))) {
                    this.addDecoration(MapDecoration.Type.byIcon(nbttagcompound1.getByte("type")), entityhuman.level, nbttagcompound1.getString("id"), nbttagcompound1.getDouble("x"), nbttagcompound1.getDouble("z"), nbttagcompound1.getDouble("rot"), (Component) null);
                }
            }
        }

    }

    public static void addTargetDecoration(ItemStack itemstack, BlockPos blockposition, String s, MapDecoration.Type mapicon_type) {
        ListTag nbttaglist;

        if (itemstack.hasTag() && itemstack.getTag().contains("Decorations", 9)) {
            nbttaglist = itemstack.getTag().getList("Decorations", 10);
        } else {
            nbttaglist = new ListTag();
            itemstack.addTagElement("Decorations", (Tag) nbttaglist);
        }

        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.putByte("type", mapicon_type.getIcon());
        nbttagcompound.putString("id", s);
        nbttagcompound.putDouble("x", (double) blockposition.getX());
        nbttagcompound.putDouble("z", (double) blockposition.getZ());
        nbttagcompound.putDouble("rot", 180.0D);
        nbttaglist.add(nbttagcompound);
        if (mapicon_type.hasMapColor()) {
            CompoundTag nbttagcompound1 = itemstack.getOrCreateTagElement("display");

            nbttagcompound1.putInt("MapColor", mapicon_type.getMapColor());
        }

    }

    private void addDecoration(MapDecoration.Type mapicon_type, @Nullable LevelAccessor generatoraccess, String s, double d0, double d1, double d2, @Nullable Component ichatbasecomponent) {
        int i = 1 << this.scale;
        float f = (float) (d0 - (double) this.x) / (float) i;
        float f1 = (float) (d1 - (double) this.z) / (float) i;
        byte b0 = (byte) ((int) ((double) (f * 2.0F) + 0.5D));
        byte b1 = (byte) ((int) ((double) (f1 * 2.0F) + 0.5D));
        boolean flag = true;
        byte b2;

        if (f >= -63.0F && f1 >= -63.0F && f <= 63.0F && f1 <= 63.0F) {
            d2 += d2 < 0.0D ? -8.0D : 8.0D;
            b2 = (byte) ((int) (d2 * 16.0D / 360.0D));
            if (this.dimension == Level.NETHER && generatoraccess != null) {
                int j = (int) (generatoraccess.getLevelData().getDayTime() / 10L);

                b2 = (byte) (j * j * 34187121 + j * 121 >> 15 & 15);
            }
        } else {
            if (mapicon_type != MapDecoration.Type.PLAYER) {
                this.decorations.remove(s);
                return;
            }

            boolean flag1 = true;

            if (Math.abs(f) < 320.0F && Math.abs(f1) < 320.0F) {
                mapicon_type = MapDecoration.Type.PLAYER_OFF_MAP;
            } else {
                if (!this.unlimitedTracking) {
                    this.decorations.remove(s);
                    return;
                }

                mapicon_type = MapDecoration.Type.PLAYER_OFF_LIMITS;
            }

            b2 = 0;
            if (f <= -63.0F) {
                b0 = -128;
            }

            if (f1 <= -63.0F) {
                b1 = -128;
            }

            if (f >= 63.0F) {
                b0 = 127;
            }

            if (f1 >= 63.0F) {
                b1 = 127;
            }
        }

        this.decorations.put(s, new MapDecoration(mapicon_type, b0, b1, b2, ichatbasecomponent));
    }

    @Nullable
    public Packet<?> getUpdatePacket(ItemStack itemstack, BlockGetter iblockaccess, Player entityhuman) {
        MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = (MapItemSavedData.HoldingPlayer) this.carriedByPlayers.get(entityhuman);

        return worldmap_worldmaphumantracker == null ? null : worldmap_worldmaphumantracker.nextUpdatePacket(itemstack);
    }

    public void setDirty(int i, int j) {
        this.setDirty();
        Iterator iterator = this.carriedBy.iterator();

        while (iterator.hasNext()) {
            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = (MapItemSavedData.HoldingPlayer) iterator.next();

            worldmap_worldmaphumantracker.markDirty(i, j);
        }

    }

    public MapItemSavedData.HoldingPlayer getHoldingPlayer(Player entityhuman) {
        MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = (MapItemSavedData.HoldingPlayer) this.carriedByPlayers.get(entityhuman);

        if (worldmap_worldmaphumantracker == null) {
            worldmap_worldmaphumantracker = new MapItemSavedData.HoldingPlayer(entityhuman);
            this.carriedByPlayers.put(entityhuman, worldmap_worldmaphumantracker);
            this.carriedBy.add(worldmap_worldmaphumantracker);
        }

        return worldmap_worldmaphumantracker;
    }

    public void toggleBanner(LevelAccessor generatoraccess, BlockPos blockposition) {
        double d0 = (double) blockposition.getX() + 0.5D;
        double d1 = (double) blockposition.getZ() + 0.5D;
        int i = 1 << this.scale;
        double d2 = (d0 - (double) this.x) / (double) i;
        double d3 = (d1 - (double) this.z) / (double) i;
        boolean flag = true;
        boolean flag1 = false;

        if (d2 >= -63.0D && d3 >= -63.0D && d2 <= 63.0D && d3 <= 63.0D) {
            MapBanner mapiconbanner = MapBanner.fromWorld(generatoraccess, blockposition);

            if (mapiconbanner == null) {
                return;
            }

            boolean flag2 = true;

            if (this.bannerMarkers.containsKey(mapiconbanner.getId()) && ((MapBanner) this.bannerMarkers.get(mapiconbanner.getId())).equals(mapiconbanner)) {
                this.bannerMarkers.remove(mapiconbanner.getId());
                this.decorations.remove(mapiconbanner.getId());
                flag2 = false;
                flag1 = true;
            }

            if (flag2) {
                this.bannerMarkers.put(mapiconbanner.getId(), mapiconbanner);
                this.addDecoration(mapiconbanner.getDecoration(), generatoraccess, mapiconbanner.getId(), d0, d1, 180.0D, mapiconbanner.getName());
                flag1 = true;
            }

            if (flag1) {
                this.setDirty();
            }
        }

    }

    public void checkBanners(BlockGetter iblockaccess, int i, int j) {
        Iterator iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapiconbanner = (MapBanner) iterator.next();

            if (mapiconbanner.getPos().getX() == i && mapiconbanner.getPos().getZ() == j) {
                MapBanner mapiconbanner1 = MapBanner.fromWorld(iblockaccess, mapiconbanner.getPos());

                if (!mapiconbanner.equals(mapiconbanner1)) {
                    iterator.remove();
                    this.decorations.remove(mapiconbanner.getId());
                }
            }
        }

    }

    public void removedFromFrame(BlockPos blockposition, int i) {
        this.decorations.remove("frame-" + i);
        this.frameMarkers.remove(MapFrame.frameId(blockposition));
    }

    public class HoldingPlayer {

<<<<<<< HEAD
=======
        // Paper start
        private void addSeenPlayers(java.util.Collection<MapDecoration> icons) {
            org.bukkit.entity.Player bPlayer = (org.bukkit.entity.Player) player.getBukkitEntity();
            MapItemSavedData.this.decorations.forEach((name, mapIcon) -> {
                // If this cursor is for a player check visibility with vanish system
                org.bukkit.entity.Player other = org.bukkit.Bukkit.getPlayerExact(name); // Spigot
                if (other == null || bPlayer.canSee(other)) {
                    icons.add(mapIcon);
                }
            });
        }
        private boolean shouldUseVanillaMap() {
            return mapView.getRenderers().size() == 1 && mapView.getRenderers().get(0).getClass() == org.bukkit.craftbukkit.map.CraftMapRenderer.class;
        }
        // Paper end
>>>>>>> Toothpick
        public final Player player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private int tick;
        public int step;

        public HoldingPlayer(Player entityhuman) {
            this.player = entityhuman;
        }

        @Nullable
        public Packet<?> nextUpdatePacket(ItemStack itemstack) {
            // CraftBukkit start
            org.bukkit.craftbukkit.map.RenderData render = MapItemSavedData.this.mapView.render((org.bukkit.craftbukkit.entity.CraftPlayer) this.player.getBukkitEntity()); // CraftBukkit

            java.util.Collection<MapDecoration> icons = new java.util.ArrayList<MapDecoration>();

            for ( org.bukkit.map.MapCursor cursor : render.cursors) {

                if (cursor.isVisible()) {
                    icons.add(new MapDecoration(MapDecoration.Type.byIcon(cursor.getRawType()), cursor.getX(), cursor.getY(), cursor.getDirection(), CraftChatMessage.fromStringOrNull(cursor.getCaption())));
                }
            }

            if (this.dirtyData) {
                this.dirtyData = false;
                return new ClientboundMapItemDataPacket(MapItem.getMapId(itemstack), MapItemSavedData.this.scale, MapItemSavedData.this.trackingPosition, MapItemSavedData.this.locked, icons, render.buffer, this.minDirtyX, this.minDirtyY, this.maxDirtyX + 1 - this.minDirtyX, this.maxDirtyY + 1 - this.minDirtyY);
            } else {
                return this.tick++ % 5 == 0 ? new ClientboundMapItemDataPacket(MapItem.getMapId(itemstack), MapItemSavedData.this.scale, MapItemSavedData.this.trackingPosition, MapItemSavedData.this.locked, icons, render.buffer, 0, 0, 0, 0) : null;
            }
            // CraftBukkit end
        }

        public void markDirty(int i, int j) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, i);
                this.minDirtyY = Math.min(this.minDirtyY, j);
                this.maxDirtyX = Math.max(this.maxDirtyX, i);
                this.maxDirtyY = Math.max(this.maxDirtyY, j);
            } else {
                this.dirtyData = true;
                this.minDirtyX = i;
                this.minDirtyY = j;
                this.maxDirtyX = i;
                this.maxDirtyY = j;
            }

        }
    }
}
