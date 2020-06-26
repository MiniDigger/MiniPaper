package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkTickList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoTickList;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkSerializer {

    private static final Logger LOGGER = LogManager.getLogger();

    public static ProtoChunk loadChunk(ServerLevel worldserver, StructureManager definedstructuremanager, PoiManager villageplace, ChunkPos chunkcoordintpair, CompoundTag nbttagcompound) {
        ChunkGenerator chunkgenerator = worldserver.getChunkSourceOH().getGenerator();
        BiomeSource worldchunkmanager = chunkgenerator.getWorldChunkManager();
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Level");
        ChunkPos chunkcoordintpair1 = new ChunkPos(nbttagcompound1.getInt("xPos"), nbttagcompound1.getInt("zPos"));

        if (!Objects.equals(chunkcoordintpair, chunkcoordintpair1)) {
            ChunkSerializer.LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", chunkcoordintpair, chunkcoordintpair, chunkcoordintpair1);
        }

        ChunkBiomeContainer biomestorage = new ChunkBiomeContainer(chunkcoordintpair, worldchunkmanager, nbttagcompound1.contains("Biomes", 11) ? nbttagcompound1.getIntArray("Biomes") : null);
        UpgradeData chunkconverter = nbttagcompound1.contains("UpgradeData", 10) ? new UpgradeData(nbttagcompound1.getCompound("UpgradeData")) : UpgradeData.EMPTY;
        ProtoTickList<Block> protochunkticklist = new ProtoTickList<>((block) -> {
            return block == null || block.getBlockData().isAir();
        }, chunkcoordintpair, nbttagcompound1.getList("ToBeTicked", 9));
        ProtoTickList<Fluid> protochunkticklist1 = new ProtoTickList<>((fluidtype) -> {
            return fluidtype == null || fluidtype == Fluids.EMPTY;
        }, chunkcoordintpair, nbttagcompound1.getList("LiquidsToBeTicked", 9));
        boolean flag = nbttagcompound1.getBoolean("isLightOn");
        ListTag nbttaglist = nbttagcompound1.getList("Sections", 10);
        boolean flag1 = true;
        LevelChunkSection[] achunksection = new LevelChunkSection[16];
        boolean flag2 = worldserver.dimensionType().hasSkyLight();
        ServerChunkCache chunkproviderserver = worldserver.getChunkSourceOH();
        LevelLightEngine lightengine = chunkproviderserver.getLightEngine();

        if (flag) {
            lightengine.retainData(chunkcoordintpair, true);
        }

        for (int i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound2 = nbttaglist.getCompound(i);
            byte b0 = nbttagcompound2.getByte("Y");

            if (nbttagcompound2.contains("Palette", 9) && nbttagcompound2.contains("BlockStates", 12)) {
                LevelChunkSection chunksection = new LevelChunkSection(b0 << 4);

                chunksection.getStates().read(nbttagcompound2.getList("Palette", 10), nbttagcompound2.getLongArray("BlockStates"));
                chunksection.recalcBlockCounts();
                if (!chunksection.isEmpty()) {
                    achunksection[b0] = chunksection;
                }

                villageplace.checkConsistencyWithBlocks(chunkcoordintpair, chunksection);
            }

            if (flag) {
                if (nbttagcompound2.contains("BlockLight", 7)) {
                    lightengine.queueSectionData(LightLayer.BLOCK, SectionPos.of(chunkcoordintpair, b0), new DataLayer(nbttagcompound2.getByteArray("BlockLight")), true);
                }

                if (flag2 && nbttagcompound2.contains("SkyLight", 7)) {
                    lightengine.queueSectionData(LightLayer.SKY, SectionPos.of(chunkcoordintpair, b0), new DataLayer(nbttagcompound2.getByteArray("SkyLight")), true);
                }
            }
        }

        long j = nbttagcompound1.getLong("InhabitedTime");
        ChunkStatus.ChunkType chunkstatus_type = getChunkTypeFromTag(nbttagcompound);
        Object object;

        if (chunkstatus_type == ChunkStatus.ChunkType.LEVELCHUNK) {
            ListTag nbttaglist1;
            Function function;
            DefaultedRegistry registryblocks;
            Object object1;

            if (nbttagcompound1.contains("TileTicks", 9)) {
                nbttaglist1 = nbttagcompound1.getList("TileTicks", 10);
                // function = IRegistry.BLOCK::getKey;
                registryblocks = Registry.BLOCK;
                registryblocks.getClass();
                object1 = ChunkTickList.create(nbttaglist1, Registry.BLOCK::getKey, Registry.BLOCK::get);
            } else {
                object1 = protochunkticklist;
            }

            Object object2;

            if (nbttagcompound1.contains("LiquidTicks", 9)) {
                nbttaglist1 = nbttagcompound1.getList("LiquidTicks", 10);
                // function = IRegistry.FLUID::getKey;
                registryblocks = Registry.FLUID;
                registryblocks.getClass();
                object2 = ChunkTickList.create(nbttaglist1, Registry.FLUID::getKey, Registry.FLUID::get);
            } else {
                object2 = protochunkticklist1;
            }

            object = new LevelChunk(worldserver.getLevel(), chunkcoordintpair, biomestorage, chunkconverter, (TickList) object1, (TickList) object2, j, achunksection, (chunk) -> {
                postLoadChunk(nbttagcompound1, chunk);
            });
        } else {
            ProtoChunk protochunk = new ProtoChunk(chunkcoordintpair, chunkconverter, achunksection, protochunkticklist, protochunkticklist1);

            protochunk.setBiomes(biomestorage);
            object = protochunk;
            protochunk.setInhabitedTime(j);
            protochunk.setStatus(ChunkStatus.byName(nbttagcompound1.getString("Status")));
            if (protochunk.getStatus().isOrAfter(ChunkStatus.FEATURES)) {
                protochunk.setLightEngine(lightengine);
            }

            if (!flag && protochunk.getStatus().isOrAfter(ChunkStatus.LIGHT)) {
                Iterator iterator = BlockPos.betweenClosed(chunkcoordintpair.getMinBlockX(), 0, chunkcoordintpair.getMinBlockZ(), chunkcoordintpair.getMaxBlockX(), 255, chunkcoordintpair.getMaxBlockZ()).iterator();

                while (iterator.hasNext()) {
                    BlockPos blockposition = (BlockPos) iterator.next();

                    if (((ChunkAccess) object).getType(blockposition).getLightEmission() != 0) {
                        protochunk.addLight(blockposition);
                    }
                }
            }
        }

        ((ChunkAccess) object).setLightCorrect(flag);
        CompoundTag nbttagcompound3 = nbttagcompound1.getCompound("Heightmaps");
        EnumSet<Heightmap.Types> enumset = EnumSet.noneOf(Heightmap.Types.class);
        Iterator iterator1 = ((ChunkAccess) object).getStatus().heightmapsAfter().iterator();

        while (iterator1.hasNext()) {
            Heightmap.Types heightmap_type = (Heightmap.Types) iterator1.next();
            String s = heightmap_type.getSerializationKey();

            if (nbttagcompound3.contains(s, 12)) {
                ((ChunkAccess) object).setHeightmap(heightmap_type, nbttagcompound3.getLongArray(s));
            } else {
                enumset.add(heightmap_type);
            }
        }

        Heightmap.primeHeightmaps((ChunkAccess) object, enumset);
        CompoundTag nbttagcompound4 = nbttagcompound1.getCompound("Structures");

        ((ChunkAccess) object).setAllStarts(unpackStructureStart(definedstructuremanager, nbttagcompound4, worldserver.getSeed()));
        ((ChunkAccess) object).setAllReferences(unpackStructureReferences(chunkcoordintpair, nbttagcompound4));
        if (nbttagcompound1.getBoolean("shouldSave")) {
            ((ChunkAccess) object).setUnsaved(true);
        }

        ListTag nbttaglist2 = nbttagcompound1.getList("PostProcessing", 9);

        ListTag nbttaglist3;
        int k;

        for (int l = 0; l < nbttaglist2.size(); ++l) {
            nbttaglist3 = nbttaglist2.getList(l);

            for (k = 0; k < nbttaglist3.size(); ++k) {
                ((ChunkAccess) object).addPackedPostProcess(nbttaglist3.getShort(k), l);
            }
        }

        if (chunkstatus_type == ChunkStatus.ChunkType.LEVELCHUNK) {
            return new ImposterProtoChunk((LevelChunk) object);
        } else {
            ProtoChunk protochunk1 = (ProtoChunk) object;

            nbttaglist3 = nbttagcompound1.getList("Entities", 10);

            for (k = 0; k < nbttaglist3.size(); ++k) {
                protochunk1.addEntity(nbttaglist3.getCompound(k));
            }

            ListTag nbttaglist4 = nbttagcompound1.getList("TileEntities", 10);

            CompoundTag nbttagcompound5;

            for (int i1 = 0; i1 < nbttaglist4.size(); ++i1) {
                nbttagcompound5 = nbttaglist4.getCompound(i1);
                ((ChunkAccess) object).setBlockEntityNbt(nbttagcompound5);
            }

            ListTag nbttaglist5 = nbttagcompound1.getList("Lights", 9);

            for (int j1 = 0; j1 < nbttaglist5.size(); ++j1) {
                ListTag nbttaglist6 = nbttaglist5.getList(j1);

                for (int k1 = 0; k1 < nbttaglist6.size(); ++k1) {
                    protochunk1.addLight(nbttaglist6.getShort(k1), j1);
                }
            }

            nbttagcompound5 = nbttagcompound1.getCompound("CarvingMasks");
            Iterator iterator2 = nbttagcompound5.getAllKeys().iterator();

            while (iterator2.hasNext()) {
                String s1 = (String) iterator2.next();
                GenerationStep.Carving worldgenstage_features = GenerationStep.Carving.valueOf(s1);

                protochunk1.setCarvingMask(worldgenstage_features, BitSet.valueOf(nbttagcompound5.getByteArray(s1)));
            }

            return protochunk1;
        }
    }

    public static CompoundTag write(ServerLevel worldserver, ChunkAccess ichunkaccess) {
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();
        CompoundTag nbttagcompound = new CompoundTag();
        CompoundTag nbttagcompound1 = new CompoundTag();

        nbttagcompound.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        nbttagcompound.put("Level", nbttagcompound1);
        nbttagcompound1.putInt("xPos", chunkcoordintpair.x);
        nbttagcompound1.putInt("zPos", chunkcoordintpair.z);
        nbttagcompound1.putLong("LastUpdate", worldserver.getGameTime());
        nbttagcompound1.putLong("InhabitedTime", ichunkaccess.getInhabitedTime());
        nbttagcompound1.putString("Status", ichunkaccess.getStatus().getName());
        UpgradeData chunkconverter = ichunkaccess.getUpgradeData();

        if (!chunkconverter.isEmpty()) {
            nbttagcompound1.put("UpgradeData", chunkconverter.write());
        }

        LevelChunkSection[] achunksection = ichunkaccess.getSections();
        ListTag nbttaglist = new ListTag();
        ThreadedLevelLightEngine lightenginethreaded = worldserver.getChunkSourceOH().getLightEngine();
        boolean flag = ichunkaccess.isLightCorrect();

        CompoundTag nbttagcompound2;

        for (int i = -1; i < 17; ++i) {
            int finalI = i;
            LevelChunkSection chunksection = (LevelChunkSection) Arrays.stream(achunksection).filter((chunksection1) -> {
                return chunksection1 != null && chunksection1.bottomBlockY() >> 4 == finalI;
            }).findFirst().orElse(LevelChunk.EMPTY_SECTION);
            DataLayer nibblearray = lightenginethreaded.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkcoordintpair, i));
            DataLayer nibblearray1 = lightenginethreaded.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkcoordintpair, i));

            if (chunksection != LevelChunk.EMPTY_SECTION || nibblearray != null || nibblearray1 != null) {
                nbttagcompound2 = new CompoundTag();
                nbttagcompound2.putByte("Y", (byte) (i & 255));
                if (chunksection != LevelChunk.EMPTY_SECTION) {
                    chunksection.getStates().write(nbttagcompound2, "Palette", "BlockStates");
                }

                if (nibblearray != null && !nibblearray.isEmpty()) {
                    nbttagcompound2.putByteArray("BlockLight", nibblearray.getData());
                }

                if (nibblearray1 != null && !nibblearray1.isEmpty()) {
                    nbttagcompound2.putByteArray("SkyLight", nibblearray1.getData());
                }

                nbttaglist.add(nbttagcompound2);
            }
        }

        nbttagcompound1.put("Sections", nbttaglist);
        if (flag) {
            nbttagcompound1.putBoolean("isLightOn", true);
        }

        ChunkBiomeContainer biomestorage = ichunkaccess.getBiomeIndex();

        if (biomestorage != null) {
            nbttagcompound1.putIntArray("Biomes", biomestorage.writeBiomes());
        }

        ListTag nbttaglist1 = new ListTag();
        Iterator iterator = ichunkaccess.getBlockEntitiesPos().iterator();

        CompoundTag nbttagcompound3;

        while (iterator.hasNext()) {
            BlockPos blockposition = (BlockPos) iterator.next();

            nbttagcompound3 = ichunkaccess.getBlockEntityNbtForSaving(blockposition);
            if (nbttagcompound3 != null) {
                nbttaglist1.add(nbttagcompound3);
            }
        }

        nbttagcompound1.put("TileEntities", nbttaglist1);
        ListTag nbttaglist2 = new ListTag();

        if (ichunkaccess.getStatus().getChunkType() == ChunkStatus.ChunkType.LEVELCHUNK) {
            LevelChunk chunk = (LevelChunk) ichunkaccess;

            chunk.setLastSaveHadEntities(false);

            for (int j = 0; j < chunk.getEntitySlices().length; ++j) {
                Iterator iterator1 = chunk.getEntitySlices()[j].iterator();

                while (iterator1.hasNext()) {
                    Entity entity = (Entity) iterator1.next();
                    CompoundTag nbttagcompound4 = new CompoundTag();

                    if (entity.save(nbttagcompound4)) {
                        chunk.setLastSaveHadEntities(true);
                        nbttaglist2.add(nbttagcompound4);
                    }
                }
            }
        } else {
            ProtoChunk protochunk = (ProtoChunk) ichunkaccess;

            nbttaglist2.addAll(protochunk.getEntities());
            nbttagcompound1.put("Lights", packOffsets(protochunk.getPackedLights()));
            nbttagcompound3 = new CompoundTag();
            GenerationStep.Carving[] aworldgenstage_features = GenerationStep.Carving.values();
            int k = aworldgenstage_features.length;

            for (int l = 0; l < k; ++l) {
                GenerationStep.Carving worldgenstage_features = aworldgenstage_features[l];
                BitSet bitset = protochunk.getCarvingMask(worldgenstage_features);

                if (bitset != null) {
                    nbttagcompound3.putByteArray(worldgenstage_features.toString(), bitset.toByteArray());
                }
            }

            nbttagcompound1.put("CarvingMasks", nbttagcompound3);
        }

        nbttagcompound1.put("Entities", nbttaglist2);
        TickList<Block> ticklist = ichunkaccess.getBlockTicks();

        if (ticklist instanceof ProtoTickList) {
            nbttagcompound1.put("ToBeTicked", ((ProtoTickList) ticklist).save());
        } else if (ticklist instanceof ChunkTickList) {
            nbttagcompound1.put("TileTicks", ((ChunkTickList) ticklist).save());
        } else {
            nbttagcompound1.put("TileTicks", worldserver.getBlockTickList().save(chunkcoordintpair));
        }

        TickList<Fluid> ticklist1 = ichunkaccess.getLiquidTicks();

        if (ticklist1 instanceof ProtoTickList) {
            nbttagcompound1.put("LiquidsToBeTicked", ((ProtoTickList) ticklist1).save());
        } else if (ticklist1 instanceof ChunkTickList) {
            nbttagcompound1.put("LiquidTicks", ((ChunkTickList) ticklist1).save());
        } else {
            nbttagcompound1.put("LiquidTicks", worldserver.getFluidTickList().save(chunkcoordintpair));
        }

        nbttagcompound1.put("PostProcessing", packOffsets(ichunkaccess.getPostProcessing()));
        nbttagcompound2 = new CompoundTag();
        Iterator iterator2 = ichunkaccess.getHeightmaps().iterator();

        while (iterator2.hasNext()) {
            Entry<Heightmap.Types, Heightmap> entry = (Entry) iterator2.next();

            if (ichunkaccess.getStatus().heightmapsAfter().contains(entry.getKey())) {
                nbttagcompound2.put(((Heightmap.Types) entry.getKey()).getSerializationKey(), new LongArrayTag(((Heightmap) entry.getValue()).getRawData()));
            }
        }

        nbttagcompound1.put("Heightmaps", nbttagcompound2);
        nbttagcompound1.put("Structures", packStructureData(chunkcoordintpair, ichunkaccess.getAllStarts(), ichunkaccess.getAllReferences()));
        return nbttagcompound;
    }

    public static ChunkStatus.ChunkType getChunkTypeFromTag(@Nullable CompoundTag nbttagcompound) {
        if (nbttagcompound != null) {
            ChunkStatus chunkstatus = ChunkStatus.byName(nbttagcompound.getCompound("Level").getString("Status"));

            if (chunkstatus != null) {
                return chunkstatus.getChunkType();
            }
        }

        return ChunkStatus.ChunkType.PROTOCHUNK;
    }

    private static void postLoadChunk(CompoundTag nbttagcompound, LevelChunk chunk) {
        ListTag nbttaglist = nbttagcompound.getList("Entities", 10);
        Level world = chunk.getLevel();
        world.timings.syncChunkLoadEntitiesTimer.startTiming(); // Spigot

        for (int i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);

            EntityType.loadEntityRecursive(nbttagcompound1, world, (entity) -> {
                chunk.addEntity(entity);
                return entity;
            });
            chunk.setLastSaveHadEntities(true);
        }

        world.timings.syncChunkLoadEntitiesTimer.stopTiming(); // Spigot
        world.timings.syncChunkLoadTileEntitiesTimer.startTiming(); // Spigot
        ListTag nbttaglist1 = nbttagcompound.getList("TileEntities", 10);

        for (int j = 0; j < nbttaglist1.size(); ++j) {
            CompoundTag nbttagcompound2 = nbttaglist1.getCompound(j);
            boolean flag = nbttagcompound2.getBoolean("keepPacked");

            if (flag) {
                chunk.setBlockEntityNbt(nbttagcompound2);
            } else {
                BlockPos blockposition = new BlockPos(nbttagcompound2.getInt("x"), nbttagcompound2.getInt("y"), nbttagcompound2.getInt("z"));
                BlockEntity tileentity = BlockEntity.create(chunk.getType(blockposition), nbttagcompound2);

                if (tileentity != null) {
                    chunk.addBlockEntity(tileentity);
                }
            }
        }
        world.timings.syncChunkLoadTileEntitiesTimer.stopTiming(); // Spigot

    }

    private static CompoundTag packStructureData(ChunkPos chunkcoordintpair, Map<StructureFeature<?>, StructureStart<?>> map, Map<StructureFeature<?>, LongSet> map1) {
        CompoundTag nbttagcompound = new CompoundTag();
        CompoundTag nbttagcompound1 = new CompoundTag();
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<StructureFeature<?>, StructureStart<?>> entry = (Entry) iterator.next();

            nbttagcompound1.put(((StructureFeature) entry.getKey()).getFeatureName(), ((StructureStart) entry.getValue()).createTag(chunkcoordintpair.x, chunkcoordintpair.z));
        }

        nbttagcompound.put("Starts", nbttagcompound1);
        CompoundTag nbttagcompound2 = new CompoundTag();
        Iterator iterator1 = map1.entrySet().iterator();

        while (iterator1.hasNext()) {
            Entry<StructureFeature<?>, LongSet> entry1 = (Entry) iterator1.next();

            nbttagcompound2.put(((StructureFeature) entry1.getKey()).getFeatureName(), new LongArrayTag((LongSet) entry1.getValue()));
        }

        nbttagcompound.put("References", nbttagcompound2);
        return nbttagcompound;
    }

    private static Map<StructureFeature<?>, StructureStart<?>> unpackStructureStart(StructureManager definedstructuremanager, CompoundTag nbttagcompound, long i) {
        Map<StructureFeature<?>, StructureStart<?>> map = Maps.newHashMap();
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Starts");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            String s1 = s.toLowerCase(Locale.ROOT);
            StructureFeature<?> structuregenerator = (StructureFeature) StructureFeature.STRUCTURES_REGISTRY.get(s1);

            if (structuregenerator == null) {
                ChunkSerializer.LOGGER.error("Unknown structure start: {}", s1);
            } else {
                StructureStart<?> structurestart = StructureFeature.loadStaticStart(definedstructuremanager, nbttagcompound1.getCompound(s), i);

                if (structurestart != null) {
                    map.put(structuregenerator, structurestart);
                }
            }
        }

        return map;
    }

    private static Map<StructureFeature<?>, LongSet> unpackStructureReferences(ChunkPos chunkcoordintpair, CompoundTag nbttagcompound) {
        Map<StructureFeature<?>, LongSet> map = Maps.newHashMap();
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("References");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            map.put(StructureFeature.STRUCTURES_REGISTRY.get(s.toLowerCase(Locale.ROOT)), new LongOpenHashSet(Arrays.stream(nbttagcompound1.getLongArray(s)).filter((i) -> {
                ChunkPos chunkcoordintpair1 = new ChunkPos(i);

                if (chunkcoordintpair1.getChessboardDistance(chunkcoordintpair) > 8) {
                    ChunkSerializer.LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", s, chunkcoordintpair1, chunkcoordintpair);
                    return false;
                } else {
                    return true;
                }
            }).toArray()));
        }

        return map;
    }

    public static ListTag packOffsets(ShortList[] ashortlist) {
        ListTag nbttaglist = new ListTag();
        ShortList[] ashortlist1 = ashortlist;
        int i = ashortlist.length;

        for (int j = 0; j < i; ++j) {
            ShortList shortlist = ashortlist1[j];
            ListTag nbttaglist1 = new ListTag();

            if (shortlist != null) {
                ShortListIterator shortlistiterator = shortlist.iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();

                    nbttaglist1.add(ShortTag.valueOf(oshort));
                }
            }

            nbttaglist.add(nbttaglist1);
        }

        return nbttaglist;
    }
}
