package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.event.server.MapInitializeEvent;
// CraftBukkit end

public class MapItem extends ComplexItem {

    public MapItem(Item.Info item_info) {
        super(item_info);
    }

    public static ItemStack create(Level world, int i, int j, byte b0, boolean flag, boolean flag1) {
        ItemStack itemstack = new ItemStack(Items.FILLED_MAP);

        createAndStoreSavedData(itemstack, world, i, j, b0, flag, flag1, world.getDimensionKey());
        return itemstack;
    }

    @Nullable
    public static MapItemSavedData getSavedData(ItemStack itemstack, Level world) {
        return world.getMapData(makeKey(getMapId(itemstack)));
    }

    @Nullable
    public static MapItemSavedData getOrCreateSavedData(ItemStack itemstack, Level world) {
        MapItemSavedData worldmap = getSavedData(itemstack, world);

        if (worldmap == null && world instanceof ServerLevel) {
            worldmap = createAndStoreSavedData(itemstack, world, world.getLevelData().getXSpawn(), world.getLevelData().getZSpawn(), 3, false, false, world.getDimensionKey());
        }

        return worldmap;
    }

    public static int getMapId(ItemStack itemstack) {
        CompoundTag nbttagcompound = itemstack.getTag();

        return nbttagcompound != null && nbttagcompound.contains("map", 99) ? nbttagcompound.getInt("map") : -1; // CraftBukkit - make new maps for no tag
    }

    private static MapItemSavedData createAndStoreSavedData(ItemStack itemstack, Level world, int i, int j, int k, boolean flag, boolean flag1, ResourceKey<Level> resourcekey) {
        int l = world.getFreeMapId();
        MapItemSavedData worldmap = new MapItemSavedData(makeKey(l));

        worldmap.setProperties(i, j, k, flag, flag1, resourcekey);
        world.setMapData(worldmap);
        itemstack.getOrCreateTag().putInt("map", l);

        // CraftBukkit start
        MapInitializeEvent event = new MapInitializeEvent(worldmap.mapView);
        Bukkit.getServer().getPluginManager().callEvent(event);
        // CraftBukkit end
        return worldmap;
    }

    public static String makeKey(int i) {
        return "map_" + i;
    }

    public void update(Level world, Entity entity, MapItemSavedData worldmap) {
        if (world.getDimensionKey() == worldmap.dimension && entity instanceof Player) {
            int i = 1 << worldmap.scale;
            int j = worldmap.x;
            int k = worldmap.z;
            int l = Mth.floor(entity.getX() - (double) j) / i + 64;
            int i1 = Mth.floor(entity.getZ() - (double) k) / i + 64;
            int j1 = 128 / i;

            if (world.dimensionType().hasCeiling()) {
                j1 /= 2;
            }

            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = worldmap.getHoldingPlayer((Player) entity);

            ++worldmap_worldmaphumantracker.step;
            boolean flag = false;

            for (int k1 = l - j1 + 1; k1 < l + j1; ++k1) {
                if ((k1 & 15) == (worldmap_worldmaphumantracker.step & 15) || flag) {
                    flag = false;
                    double d0 = 0.0D;

                    for (int l1 = i1 - j1 - 1; l1 < i1 + j1; ++l1) {
                        if (k1 >= 0 && l1 >= -1 && k1 < 128 && l1 < 128) {
                            int i2 = k1 - l;
                            int j2 = l1 - i1;
                            boolean flag1 = i2 * i2 + j2 * j2 > (j1 - 2) * (j1 - 2);
                            int k2 = (j / i + k1 - 64) * i;
                            int l2 = (k / i + l1 - 64) * i;
                            Multiset<MaterialColor> multiset = LinkedHashMultiset.create();
                            LevelChunk chunk = world.getChunkAt(new BlockPos(k2, 0, l2));

                            if (!chunk.isEmpty()) {
                                ChunkPos chunkcoordintpair = chunk.getPos();
                                int i3 = k2 & 15;
                                int j3 = l2 & 15;
                                int k3 = 0;
                                double d1 = 0.0D;

                                if (world.dimensionType().hasCeiling()) {
                                    int l3 = k2 + l2 * 231871;

                                    l3 = l3 * l3 * 31287121 + l3 * 11;
                                    if ((l3 >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.getBlockData().getMapColor(world, BlockPos.ZERO), 10);
                                    } else {
                                        multiset.add(Blocks.STONE.getBlockData().getMapColor(world, BlockPos.ZERO), 100);
                                    }

                                    d1 = 100.0D;
                                } else {
                                    BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
                                    BlockPos.MutableBlockPosition blockposition_mutableblockposition1 = new BlockPos.MutableBlockPosition();

                                    for (int i4 = 0; i4 < i; ++i4) {
                                        for (int j4 = 0; j4 < i; ++j4) {
                                            int k4 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i4 + i3, j4 + j3) + 1;
                                            BlockState iblockdata;

                                            if (k4 > 1) {
                                                do {
                                                    --k4;
                                                    blockposition_mutableblockposition.d(chunkcoordintpair.getMinBlockX() + i4 + i3, k4, chunkcoordintpair.getMinBlockZ() + j4 + j3);
                                                    iblockdata = chunk.getType(blockposition_mutableblockposition);
                                                } while (iblockdata.getMapColor(world, blockposition_mutableblockposition) == MaterialColor.NONE && k4 > 0);

                                                if (k4 > 0 && !iblockdata.getFluidState().isEmpty()) {
                                                    int l4 = k4 - 1;

                                                    blockposition_mutableblockposition1.g(blockposition_mutableblockposition);

                                                    BlockState iblockdata1;

                                                    do {
                                                        blockposition_mutableblockposition1.setY(l4--);
                                                        iblockdata1 = chunk.getType(blockposition_mutableblockposition1);
                                                        ++k3;
                                                    } while (l4 > 0 && !iblockdata1.getFluidState().isEmpty());

                                                    iblockdata = this.getCorrectStateForFluidBlock(world, iblockdata, (BlockPos) blockposition_mutableblockposition);
                                                }
                                            } else {
                                                iblockdata = Blocks.BEDROCK.getBlockData();
                                            }

                                            worldmap.checkBanners(world, chunkcoordintpair.getMinBlockX() + i4 + i3, chunkcoordintpair.getMinBlockZ() + j4 + j3);
                                            d1 += (double) k4 / (double) (i * i);
                                            multiset.add(iblockdata.getMapColor(world, blockposition_mutableblockposition));
                                        }
                                    }
                                }

                                k3 /= i * i;
                                double d2 = (d1 - d0) * 4.0D / (double) (i + 4) + ((double) (k1 + l1 & 1) - 0.5D) * 0.4D;
                                byte b0 = 1;

                                if (d2 > 0.6D) {
                                    b0 = 2;
                                }

                                if (d2 < -0.6D) {
                                    b0 = 0;
                                }

                                MaterialColor materialmapcolor = (MaterialColor) Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MaterialColor.NONE);

                                if (materialmapcolor == MaterialColor.WATER) {
                                    d2 = (double) k3 * 0.1D + (double) (k1 + l1 & 1) * 0.2D;
                                    b0 = 1;
                                    if (d2 < 0.5D) {
                                        b0 = 2;
                                    }

                                    if (d2 > 0.9D) {
                                        b0 = 0;
                                    }
                                }

                                d0 = d1;
                                if (l1 >= 0 && i2 * i2 + j2 * j2 < j1 * j1 && (!flag1 || (k1 + l1 & 1) != 0)) {
                                    byte b1 = worldmap.colors[k1 + l1 * 128];
                                    byte b2 = (byte) (materialmapcolor.id * 4 + b0);

                                    if (b1 != b2) {
                                        worldmap.colors[k1 + l1 * 128] = b2;
                                        worldmap.setDirty(k1, l1);
                                        flag = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private BlockState getCorrectStateForFluidBlock(Level world, BlockState iblockdata, BlockPos blockposition) {
        FluidState fluid = iblockdata.getFluidState();

        return !fluid.isEmpty() && !iblockdata.isFaceSturdy(world, blockposition, Direction.UP) ? fluid.getBlockData() : iblockdata;
    }

    private static boolean isLand(Biome[] abiomebase, int i, int j, int k) {
        return abiomebase[j * i + k * i * 128 * i].getDepth() >= 0.0F;
    }

    public static void renderBiomePreviewMap(ServerLevel worldserver, ItemStack itemstack) {
        MapItemSavedData worldmap = getOrCreateSavedData(itemstack, worldserver);

        if (worldmap != null) {
            if (worldserver.getDimensionKey() == worldmap.dimension) {
                int i = 1 << worldmap.scale;
                int j = worldmap.x;
                int k = worldmap.z;
                Biome[] abiomebase = new Biome[128 * i * 128 * i];

                int l;
                int i1;

                for (l = 0; l < 128 * i; ++l) {
                    for (i1 = 0; i1 < 128 * i; ++i1) {
                        abiomebase[l * 128 * i + i1] = worldserver.getBiome(new BlockPos((j / i - 64) * i + i1, 0, (k / i - 64) * i + l));
                    }
                }

                for (l = 0; l < 128; ++l) {
                    for (i1 = 0; i1 < 128; ++i1) {
                        if (l > 0 && i1 > 0 && l < 127 && i1 < 127) {
                            Biome biomebase = abiomebase[l * i + i1 * i * 128 * i];
                            int j1 = 8;

                            if (isLand(abiomebase, i, l - 1, i1 - 1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l - 1, i1 + 1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l - 1, i1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l + 1, i1 - 1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l + 1, i1 + 1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l + 1, i1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l, i1 - 1)) {
                                --j1;
                            }

                            if (isLand(abiomebase, i, l, i1 + 1)) {
                                --j1;
                            }

                            int k1 = 3;
                            MaterialColor materialmapcolor = MaterialColor.NONE;

                            if (biomebase.getDepth() < 0.0F) {
                                materialmapcolor = MaterialColor.COLOR_ORANGE;
                                if (j1 > 7 && i1 % 2 == 0) {
                                    k1 = (l + (int) (Mth.sin((float) i1 + 0.0F) * 7.0F)) / 8 % 5;
                                    if (k1 == 3) {
                                        k1 = 1;
                                    } else if (k1 == 4) {
                                        k1 = 0;
                                    }
                                } else if (j1 > 7) {
                                    materialmapcolor = MaterialColor.NONE;
                                } else if (j1 > 5) {
                                    k1 = 1;
                                } else if (j1 > 3) {
                                    k1 = 0;
                                } else if (j1 > 1) {
                                    k1 = 0;
                                }
                            } else if (j1 > 0) {
                                materialmapcolor = MaterialColor.COLOR_BROWN;
                                if (j1 > 3) {
                                    k1 = 1;
                                } else {
                                    k1 = 3;
                                }
                            }

                            if (materialmapcolor != MaterialColor.NONE) {
                                worldmap.colors[l + i1 * 128] = (byte) (materialmapcolor.id * 4 + k1);
                                worldmap.setDirty(l, i1);
                            }
                        }
                    }
                }

            }
        }
    }

    @Override
    public void inventoryTick(ItemStack itemstack, Level world, Entity entity, int i, boolean flag) {
        if (!world.isClientSide) {
            MapItemSavedData worldmap = getOrCreateSavedData(itemstack, world);

            if (worldmap != null) {
                if (entity instanceof Player) {
                    Player entityhuman = (Player) entity;

                    worldmap.tickCarriedBy(entityhuman, itemstack);
                }

                if (!worldmap.locked && (flag || entity instanceof Player && ((Player) entity).getOffhandItem() == itemstack)) {
                    this.update(world, entity, worldmap);
                }

            }
        }
    }

    @Nullable
    @Override
    public Packet<?> getUpdatePacket(ItemStack itemstack, Level world, Player entityhuman) {
        return getOrCreateSavedData(itemstack, world).getUpdatePacket(itemstack, world, entityhuman);
    }

    @Override
    public void onCraftedBy(ItemStack itemstack, Level world, Player entityhuman) {
        CompoundTag nbttagcompound = itemstack.getTag();

        if (nbttagcompound != null && nbttagcompound.contains("map_scale_direction", 99)) {
            scaleMap(itemstack, world, nbttagcompound.getInt("map_scale_direction"));
            nbttagcompound.remove("map_scale_direction");
        }

    }

    protected static void scaleMap(ItemStack itemstack, Level world, int i) {
        MapItemSavedData worldmap = getOrCreateSavedData(itemstack, world);

        if (worldmap != null) {
            createAndStoreSavedData(itemstack, world, worldmap.x, worldmap.z, Mth.clamp(worldmap.scale + i, 0, 4), worldmap.trackingPosition, worldmap.unlimitedTracking, worldmap.dimension);
        }

    }

    @Nullable
    public static ItemStack lockMap(Level world, ItemStack itemstack) {
        MapItemSavedData worldmap = getOrCreateSavedData(itemstack, world);

        if (worldmap != null) {
            ItemStack itemstack1 = itemstack.copy();
            MapItemSavedData worldmap1 = createAndStoreSavedData(itemstack1, world, 0, 0, worldmap.scale, worldmap.trackingPosition, worldmap.unlimitedTracking, worldmap.dimension);

            worldmap1.lockData(worldmap);
            return itemstack1;
        } else {
            return null;
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        BlockState iblockdata = itemactioncontext.getLevel().getType(itemactioncontext.getClickedPos());

        if (iblockdata.is((Tag) BlockTags.BANNERS)) {
            if (!itemactioncontext.level.isClientSide) {
                MapItemSavedData worldmap = getOrCreateSavedData(itemactioncontext.getItemInHand(), itemactioncontext.getLevel());

                worldmap.toggleBanner((LevelAccessor) itemactioncontext.getLevel(), itemactioncontext.getClickedPos());
            }

            return InteractionResult.sidedSuccess(itemactioncontext.level.isClientSide);
        } else {
            return super.useOn(itemactioncontext);
        }
    }
}
