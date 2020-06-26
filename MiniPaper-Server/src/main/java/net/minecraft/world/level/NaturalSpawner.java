package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.WeighedRandom;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.NearestNeighborBiomeZoomer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public final class NaturalSpawner {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAGIC_NUMBER = (int) Math.pow(17.0D, 2.0D);
    private static final MobCategory[] SPAWNING_CATEGORIES = (MobCategory[]) Stream.of(MobCategory.values()).filter((enumcreaturetype) -> {
        return enumcreaturetype != MobCategory.MISC;
    }).toArray((i) -> {
        return new MobCategory[i];
    });

    public static NaturalSpawner.SpawnState createState(int i, Iterable<Entity> iterable, NaturalSpawner.ChunkGetter spawnercreature_b) {
        PotentialCalculator spawnercreatureprobabilities = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> object2intopenhashmap = new Object2IntOpenHashMap();
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                // CraftBukkit - Split out persistent check, don't apply it to special persistent mobs
                if (entityinsentient.removeWhenFarAway(0) && entityinsentient.isPersistenceRequired()) {
                    continue;
                }
            }

            MobCategory enumcreaturetype = entity.getType().getCategory();

            if (enumcreaturetype != MobCategory.MISC) {
                BlockPos blockposition = entity.blockPosition();
                long j = ChunkPos.asLong(blockposition.getX() >> 4, blockposition.getZ() >> 4);

                spawnercreature_b.query(j, (chunk) -> {
                    Biome biomebase = getRoughBiome(blockposition, chunk);
                    Biome.MobSpawnCost biomebase_e = biomebase.getMobSpawnCost(entity.getType());

                    if (biomebase_e != null) {
                        spawnercreatureprobabilities.addCharge(entity.blockPosition(), biomebase_e.getCharge());
                    }

                    object2intopenhashmap.addTo(enumcreaturetype, 1);
                });
            }
        }

        return new NaturalSpawner.SpawnState(i, object2intopenhashmap, spawnercreatureprobabilities);
    }

    private static Biome getRoughBiome(BlockPos blockposition, ChunkAccess ichunkaccess) {
        return NearestNeighborBiomeZoomer.INSTANCE.getBiome(0L, blockposition.getX(), blockposition.getY(), blockposition.getZ(), ichunkaccess.getBiomeIndex());
    }

    public static void spawnForChunk(ServerLevel worldserver, LevelChunk chunk, NaturalSpawner.SpawnState spawnercreature_d, boolean flag, boolean flag1, boolean flag2) {
        worldserver.getProfiler().push("spawner");
        worldserver.timings.mobSpawn.startTiming(); // Spigot
        MobCategory[] aenumcreaturetype = NaturalSpawner.SPAWNING_CATEGORIES;
        int i = aenumcreaturetype.length;

        // CraftBukkit start - Other mob type spawn tick rate
        LevelData worlddata = worldserver.getLevelData();
        boolean spawnAnimalThisTick = worldserver.ticksPerAnimalSpawns != 0L && worlddata.getGameTime() % worldserver.ticksPerAnimalSpawns == 0L;
        boolean spawnMonsterThisTick = worldserver.ticksPerMonsterSpawns != 0L && worlddata.getGameTime() % worldserver.ticksPerMonsterSpawns == 0L;
        boolean spawnWaterThisTick = worldserver.ticksPerWaterSpawns != 0L && worlddata.getGameTime() % worldserver.ticksPerWaterSpawns == 0L;
        boolean spawnAmbientThisTick = worldserver.ticksPerAmbientSpawns != 0L && worlddata.getGameTime() % worldserver.ticksPerAmbientSpawns == 0L;
        // CraftBukkit end

        for (int j = 0; j < i; ++j) {
            MobCategory enumcreaturetype = aenumcreaturetype[j];
            // CraftBukkit start - Use per-world spawn limits
            boolean spawnThisTick = true;
            int limit = enumcreaturetype.getMaxInstancesPerChunk();
            switch (enumcreaturetype) {
                case MONSTER:
                    spawnThisTick = spawnMonsterThisTick;
                    limit = worldserver.getWorld().getMonsterSpawnLimit();
                    break;
                case CREATURE:
                    spawnThisTick = spawnAnimalThisTick;
                    limit = worldserver.getWorld().getAnimalSpawnLimit();
                    break;
                case WATER_CREATURE:
                    spawnThisTick = spawnWaterThisTick;
                    limit = worldserver.getWorld().getWaterAnimalSpawnLimit();
                    break;
                case AMBIENT:
                    spawnThisTick = spawnAmbientThisTick;
                    limit = worldserver.getWorld().getAmbientSpawnLimit();
                    break;
            }

            if (!spawnThisTick || limit == 0) {
                continue;
            }

            if ((flag || !enumcreaturetype.isFriendly()) && (flag1 || enumcreaturetype.isFriendly()) && (flag2 || !enumcreaturetype.isPersistent()) && spawnercreature_d.a(enumcreaturetype, limit)) {
                // CraftBukkit end
                spawnCategoryForChunk(enumcreaturetype, worldserver, chunk, (entitytypes, blockposition, ichunkaccess) -> {
                    return spawnercreature_d.canSpawn(entitytypes, blockposition, ichunkaccess);
                }, (entityinsentient, ichunkaccess) -> {
                    spawnercreature_d.afterSpawn(entityinsentient, ichunkaccess);
                });
            }
        }

        worldserver.timings.mobSpawn.stopTiming(); // Spigot
        worldserver.getProfiler().pop();
    }

    public static void spawnCategoryForChunk(MobCategory enumcreaturetype, ServerLevel worldserver, LevelChunk chunk, NaturalSpawner.SpawnPredicate spawnercreature_c, NaturalSpawner.AfterSpawnCallback spawnercreature_a) {
        BlockPos blockposition = getRandomPosWithin(worldserver, chunk);

        if (blockposition.getY() >= 1) {
            spawnCategoryForPosition(enumcreaturetype, worldserver, (ChunkAccess) chunk, blockposition, spawnercreature_c, spawnercreature_a);
        }
    }

    public static void spawnCategoryForPosition(MobCategory enumcreaturetype, ServerLevel worldserver, ChunkAccess ichunkaccess, BlockPos blockposition, NaturalSpawner.SpawnPredicate spawnercreature_c, NaturalSpawner.AfterSpawnCallback spawnercreature_a) {
        StructureFeatureManager structuremanager = worldserver.getStructureManager();
        ChunkGenerator chunkgenerator = worldserver.getChunkSourceOH().getGenerator();
        int i = blockposition.getY();
        BlockState iblockdata = ichunkaccess.getType(blockposition);

        if (!iblockdata.isRedstoneConductor(ichunkaccess, blockposition)) {
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
            int j = 0;
            int k = 0;

            while (k < 3) {
                int l = blockposition.getX();
                int i1 = blockposition.getZ();
                boolean flag = true;
                Biome.BiomeMeta biomebase_biomemeta = null;
                SpawnGroupData groupdataentity = null;
                int j1 = Mth.ceil(worldserver.random.nextFloat() * 4.0F);
                int k1 = 0;
                int l1 = 0;

                while (true) {
                    if (l1 < j1) {
                        label53:
                        {
                            l += worldserver.random.nextInt(6) - worldserver.random.nextInt(6);
                            i1 += worldserver.random.nextInt(6) - worldserver.random.nextInt(6);
                            blockposition_mutableblockposition.d(l, i, i1);
                            double d0 = (double) l + 0.5D;
                            double d1 = (double) i1 + 0.5D;
                            Player entityhuman = worldserver.getNearestPlayer(d0, (double) i, d1, -1.0D, false);

                            if (entityhuman != null) {
                                double d2 = entityhuman.distanceToSqr(d0, (double) i, d1);

                                if (a(worldserver, ichunkaccess, blockposition_mutableblockposition, d2)) {
                                    if (biomebase_biomemeta == null) {
                                        biomebase_biomemeta = a(worldserver, structuremanager, chunkgenerator, enumcreaturetype, worldserver.random, (BlockPos) blockposition_mutableblockposition);
                                        if (biomebase_biomemeta == null) {
                                            break label53;
                                        }

                                        j1 = biomebase_biomemeta.d + worldserver.random.nextInt(1 + biomebase_biomemeta.e - biomebase_biomemeta.d);
                                    }

                                    if (a(worldserver, enumcreaturetype, structuremanager, chunkgenerator, biomebase_biomemeta, blockposition_mutableblockposition, d2) && spawnercreature_c.test(biomebase_biomemeta.c, blockposition_mutableblockposition, ichunkaccess)) {
                                        Mob entityinsentient = getMobForSpawn(worldserver, biomebase_biomemeta.c);

                                        if (entityinsentient == null) {
                                            return;
                                        }

                                        entityinsentient.moveTo(d0, (double) i, d1, worldserver.random.nextFloat() * 360.0F, 0.0F);
                                        if (isValidPositionForMob(worldserver, entityinsentient, d2)) {
                                            groupdataentity = entityinsentient.prepare(worldserver, worldserver.getDamageScaler(entityinsentient.blockPosition()), MobSpawnType.NATURAL, groupdataentity, (CompoundTag) null);
                                            // CraftBukkit start
                                            if (worldserver.addEntity(entityinsentient, SpawnReason.NATURAL)) {
                                                ++j;
                                                ++k1;
                                                spawnercreature_a.run(entityinsentient, ichunkaccess);
                                            }
                                            // CraftBukkit end
                                            if (j >= entityinsentient.getMaxSpawnClusterSize()) {
                                                return;
                                            }

                                            if (entityinsentient.isMaxGroupSizeReached(k1)) {
                                                break label53;
                                            }
                                        }
                                    }
                                }
                            }

                            ++l1;
                            continue;
                        }
                    }

                    ++k;
                    break;
                }
            }

        }
    }

    private static boolean a(ServerLevel worldserver, ChunkAccess ichunkaccess, BlockPos.MutableBlockPosition blockposition_mutableblockposition, double d0) {
        if (d0 <= 576.0D) {
            return false;
        } else if (worldserver.getSharedSpawnPos().closerThan((Position) (new Vec3((double) blockposition_mutableblockposition.getX() + 0.5D, (double) blockposition_mutableblockposition.getY(), (double) blockposition_mutableblockposition.getZ() + 0.5D)), 24.0D)) {
            return false;
        } else {
            ChunkPos chunkcoordintpair = new ChunkPos(blockposition_mutableblockposition);

            return Objects.equals(chunkcoordintpair, ichunkaccess.getPos()) || worldserver.getChunkSourceOH().isEntityTickingChunk(chunkcoordintpair);
        }
    }

    private static boolean a(ServerLevel worldserver, MobCategory enumcreaturetype, StructureFeatureManager structuremanager, ChunkGenerator chunkgenerator, Biome.BiomeMeta biomebase_biomemeta, BlockPos.MutableBlockPosition blockposition_mutableblockposition, double d0) {
        EntityType<?> entitytypes = biomebase_biomemeta.c;

        if (entitytypes.getCategory() == MobCategory.MISC) {
            return false;
        } else if (!entitytypes.canSpawnFarFromPlayer() && d0 > (double) (entitytypes.getCategory().getDespawnDistance() * entitytypes.getCategory().getDespawnDistance())) {
            return false;
        } else if (entitytypes.canSummon() && a(worldserver, structuremanager, chunkgenerator, enumcreaturetype, biomebase_biomemeta, (BlockPos) blockposition_mutableblockposition)) {
            SpawnPlacements.Type entitypositiontypes_surface = SpawnPlacements.getPlacementType(entitytypes);

            return !isSpawnPositionOk(entitypositiontypes_surface, (LevelReader) worldserver, blockposition_mutableblockposition, entitytypes) ? false : (!SpawnPlacements.checkSpawnRules(entitytypes, worldserver, MobSpawnType.NATURAL, blockposition_mutableblockposition, worldserver.random) ? false : worldserver.noCollision(entitytypes.getAABB((double) blockposition_mutableblockposition.getX() + 0.5D, (double) blockposition_mutableblockposition.getY(), (double) blockposition_mutableblockposition.getZ() + 0.5D)));
        } else {
            return false;
        }
    }

    @Nullable
    private static Mob getMobForSpawn(ServerLevel worldserver, EntityType<?> entitytypes) {
        try {
            Entity entity = entitytypes.create((Level) worldserver);

            if (!(entity instanceof Mob)) {
                throw new IllegalStateException("Trying to spawn a non-mob: " + Registry.ENTITY_TYPE.getKey(entitytypes));
            } else {
                Mob entityinsentient = (Mob) entity;

                return entityinsentient;
            }
        } catch (Exception exception) {
            NaturalSpawner.LOGGER.warn("Failed to create mob", exception);
            return null;
        }
    }

    private static boolean isValidPositionForMob(ServerLevel worldserver, Mob entityinsentient, double d0) {
        return d0 > (double) (entityinsentient.getType().getCategory().getDespawnDistance() * entityinsentient.getType().getCategory().getDespawnDistance()) && entityinsentient.removeWhenFarAway(d0) ? false : entityinsentient.checkSpawnRules((LevelAccessor) worldserver, MobSpawnType.NATURAL) && entityinsentient.checkSpawnObstruction((LevelReader) worldserver);
    }

    @Nullable
    private static Biome.BiomeMeta a(ServerLevel worldserver, StructureFeatureManager structuremanager, ChunkGenerator chunkgenerator, MobCategory enumcreaturetype, Random random, BlockPos blockposition) {
        Biome biomebase = worldserver.getBiome(blockposition);

        if (enumcreaturetype == MobCategory.WATER_AMBIENT && biomebase.getBiomeCategory() == Biome.BiomeCategory.RIVER && random.nextFloat() < 0.98F) {
            return null;
        } else {
            List<Biome.BiomeMeta> list = mobsAt(worldserver, structuremanager, chunkgenerator, enumcreaturetype, blockposition, biomebase);

            return list.isEmpty() ? null : (Biome.BiomeMeta) WeighedRandom.a(random, list);
        }
    }

    private static boolean a(ServerLevel worldserver, StructureFeatureManager structuremanager, ChunkGenerator chunkgenerator, MobCategory enumcreaturetype, Biome.BiomeMeta biomebase_biomemeta, BlockPos blockposition) {
        return mobsAt(worldserver, structuremanager, chunkgenerator, enumcreaturetype, blockposition, (Biome) null).contains(biomebase_biomemeta);
    }

    private static List<Biome.BiomeMeta> mobsAt(ServerLevel worldserver, StructureFeatureManager structuremanager, ChunkGenerator chunkgenerator, MobCategory enumcreaturetype, BlockPos blockposition, @Nullable Biome biomebase) {
        return enumcreaturetype == MobCategory.MONSTER && worldserver.getType(blockposition.below()).getBlock() == Blocks.NETHER_BRICKS && structuremanager.getStructureAt(blockposition, false, StructureFeature.NETHER_BRIDGE).isValid() ? StructureFeature.NETHER_BRIDGE.getSpecialEnemies() : chunkgenerator.getMobsFor(biomebase != null ? biomebase : worldserver.getBiome(blockposition), structuremanager, enumcreaturetype, blockposition);
    }

    private static BlockPos getRandomPosWithin(Level world, LevelChunk chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        int i = chunkcoordintpair.getMinBlockX() + world.random.nextInt(16);
        int j = chunkcoordintpair.getMinBlockZ() + world.random.nextInt(16);
        int k = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, j) + 1;
        int l = world.random.nextInt(k + 1);

        return new BlockPos(i, l, j);
    }

    public static boolean isValidEmptySpawnBlock(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, FluidState fluid, EntityType entitytypes) {
        return iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition) ? false : (iblockdata.isSignalSource() ? false : (!fluid.isEmpty() ? false : (iblockdata.is((Tag) BlockTags.PREVENT_MOB_SPAWNING_INSIDE) ? false : !entitytypes.isBlockDangerous(iblockdata))));
    }

    public static boolean isSpawnPositionOk(SpawnPlacements.Type entitypositiontypes_surface, LevelReader iworldreader, BlockPos blockposition, @Nullable EntityType<?> entitytypes) {
        if (entitypositiontypes_surface == SpawnPlacements.Type.NO_RESTRICTIONS) {
            return true;
        } else if (entitytypes != null && iworldreader.getWorldBorder().isWithinBounds(blockposition)) {
            BlockState iblockdata = iworldreader.getType(blockposition);
            FluidState fluid = iworldreader.getFluidState(blockposition);
            BlockPos blockposition1 = blockposition.above();
            BlockPos blockposition2 = blockposition.below();

            switch (entitypositiontypes_surface) {
                case IN_WATER:
                    return fluid.is((Tag) FluidTags.WATER) && iworldreader.getFluidState(blockposition2).is((Tag) FluidTags.WATER) && !iworldreader.getType(blockposition1).isRedstoneConductor(iworldreader, blockposition1);
                case IN_LAVA:
                    return fluid.is((Tag) FluidTags.LAVA);
                case ON_GROUND:
                default:
                    BlockState iblockdata1 = iworldreader.getType(blockposition2);

                    return !iblockdata1.isValidSpawn((BlockGetter) iworldreader, blockposition2, entitytypes) ? false : isValidEmptySpawnBlock((BlockGetter) iworldreader, blockposition, iblockdata, fluid, entitytypes) && isValidEmptySpawnBlock((BlockGetter) iworldreader, blockposition1, iworldreader.getType(blockposition1), iworldreader.getFluidState(blockposition1), entitytypes);
            }
        } else {
            return false;
        }
    }

    public static void spawnMobsForChunkGeneration(LevelAccessor generatoraccess, Biome biomebase, int i, int j, Random random) {
        List<Biome.BiomeMeta> list = biomebase.getMobs(MobCategory.CREATURE);

        if (!list.isEmpty()) {
            int k = i << 4;
            int l = j << 4;

            while (random.nextFloat() < biomebase.getCreatureProbability()) {
                Biome.BiomeMeta biomebase_biomemeta = (Biome.BiomeMeta) WeighedRandom.a(random, list);
                int i1 = biomebase_biomemeta.d + random.nextInt(1 + biomebase_biomemeta.e - biomebase_biomemeta.d);
                SpawnGroupData groupdataentity = null;
                int j1 = k + random.nextInt(16);
                int k1 = l + random.nextInt(16);
                int l1 = j1;
                int i2 = k1;

                for (int j2 = 0; j2 < i1; ++j2) {
                    boolean flag = false;

                    for (int k2 = 0; !flag && k2 < 4; ++k2) {
                        BlockPos blockposition = getTopNonCollidingPos(generatoraccess, biomebase_biomemeta.c, j1, k1);

                        if (biomebase_biomemeta.c.canSummon() && isSpawnPositionOk(SpawnPlacements.getPlacementType(biomebase_biomemeta.c), (LevelReader) generatoraccess, blockposition, biomebase_biomemeta.c)) {
                            float f = biomebase_biomemeta.c.getWidth();
                            double d0 = Mth.clamp((double) j1, (double) k + (double) f, (double) k + 16.0D - (double) f);
                            double d1 = Mth.clamp((double) k1, (double) l + (double) f, (double) l + 16.0D - (double) f);

                            if (!generatoraccess.noCollision(biomebase_biomemeta.c.getAABB(d0, (double) blockposition.getY(), d1)) || !SpawnPlacements.checkSpawnRules(biomebase_biomemeta.c, generatoraccess, MobSpawnType.CHUNK_GENERATION, new BlockPos(d0, (double) blockposition.getY(), d1), generatoraccess.getRandom())) {
                                continue;
                            }

                            Entity entity;

                            try {
                                entity = biomebase_biomemeta.c.create(generatoraccess.getLevel());
                            } catch (Exception exception) {
                                NaturalSpawner.LOGGER.warn("Failed to create mob", exception);
                                continue;
                            }

                            entity.moveTo(d0, (double) blockposition.getY(), d1, random.nextFloat() * 360.0F, 0.0F);
                            if (entity instanceof Mob) {
                                Mob entityinsentient = (Mob) entity;

                                if (entityinsentient.checkSpawnRules(generatoraccess, MobSpawnType.CHUNK_GENERATION) && entityinsentient.checkSpawnObstruction((LevelReader) generatoraccess)) {
                                    groupdataentity = entityinsentient.prepare(generatoraccess, generatoraccess.getDamageScaler(entityinsentient.blockPosition()), MobSpawnType.CHUNK_GENERATION, groupdataentity, (CompoundTag) null);
                                    generatoraccess.addEntity(entityinsentient, SpawnReason.CHUNK_GEN); // CraftBukkit
                                    flag = true;
                                }
                            }
                        }

                        j1 += random.nextInt(5) - random.nextInt(5);

                        for (k1 += random.nextInt(5) - random.nextInt(5); j1 < k || j1 >= k + 16 || k1 < l || k1 >= l + 16; k1 = i2 + random.nextInt(5) - random.nextInt(5)) {
                            j1 = l1 + random.nextInt(5) - random.nextInt(5);
                        }
                    }
                }
            }

        }
    }

    private static BlockPos getTopNonCollidingPos(LevelReader iworldreader, EntityType<?> entitytypes, int i, int j) {
        int k = iworldreader.getHeight(SpawnPlacements.getHeightmapType(entitytypes), i, j);
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition(i, k, j);

        if (iworldreader.dimensionType().hasCeiling()) {
            do {
                blockposition_mutableblockposition.c(Direction.DOWN);
            } while (!iworldreader.getType(blockposition_mutableblockposition).isAir());

            do {
                blockposition_mutableblockposition.c(Direction.DOWN);
            } while (iworldreader.getType(blockposition_mutableblockposition).isAir() && blockposition_mutableblockposition.getY() > 0);
        }

        if (SpawnPlacements.getPlacementType(entitytypes) == SpawnPlacements.Type.ON_GROUND) {
            BlockPos blockposition = blockposition_mutableblockposition.below();

            if (iworldreader.getType(blockposition).isPathfindable((BlockGetter) iworldreader, blockposition, PathComputationType.LAND)) {
                return blockposition;
            }
        }

        return blockposition_mutableblockposition.immutable();
    }

    @FunctionalInterface
    public interface ChunkGetter {

        void query(long i, Consumer<LevelChunk> consumer);
    }

    @FunctionalInterface
    public interface AfterSpawnCallback {

        void run(Mob entityinsentient, ChunkAccess ichunkaccess);
    }

    @FunctionalInterface
    public interface SpawnPredicate {

        boolean test(EntityType<?> entitytypes, BlockPos blockposition, ChunkAccess ichunkaccess);
    }

    public static class SpawnState {

        private final int spawnableChunkCount;
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
        private final PotentialCalculator spawnPotential;
        private final Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;
        @Nullable
        private BlockPos lastCheckedPos;
        @Nullable
        private EntityType<?> lastCheckedType;
        private double lastCharge;

        private SpawnState(int i, Object2IntOpenHashMap<MobCategory> object2intopenhashmap, PotentialCalculator spawnercreatureprobabilities) {
            this.spawnableChunkCount = i;
            this.mobCategoryCounts = object2intopenhashmap;
            this.spawnPotential = spawnercreatureprobabilities;
            this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(object2intopenhashmap);
        }

        private boolean canSpawn(EntityType<?> entitytypes, BlockPos blockposition, ChunkAccess ichunkaccess) {
            this.lastCheckedPos = blockposition;
            this.lastCheckedType = entitytypes;
            Biome biomebase = NaturalSpawner.getRoughBiome(blockposition, ichunkaccess);
            Biome.MobSpawnCost biomebase_e = biomebase.getMobSpawnCost(entitytypes);

            if (biomebase_e == null) {
                this.lastCharge = 0.0D;
                return true;
            } else {
                double d0 = biomebase_e.getCharge();

                this.lastCharge = d0;
                double d1 = this.spawnPotential.getPotentialEnergyChange(blockposition, d0);

                return d1 <= biomebase_e.getEnergyBudget();
            }
        }

        private void afterSpawn(Mob entityinsentient, ChunkAccess ichunkaccess) {
            EntityType<?> entitytypes = entityinsentient.getType();
            BlockPos blockposition = entityinsentient.blockPosition();
            double d0;

            if (blockposition.equals(this.lastCheckedPos) && entitytypes == this.lastCheckedType) {
                d0 = this.lastCharge;
            } else {
                Biome biomebase = NaturalSpawner.getRoughBiome(blockposition, ichunkaccess);
                Biome.MobSpawnCost biomebase_e = biomebase.getMobSpawnCost(entitytypes);

                if (biomebase_e != null) {
                    d0 = biomebase_e.getCharge();
                } else {
                    d0 = 0.0D;
                }
            }

            this.spawnPotential.addCharge(blockposition, d0);
            this.mobCategoryCounts.addTo(entitytypes.getCategory(), 1);
        }

        public Object2IntMap<MobCategory> getMobCategoryCounts() {
            return this.unmodifiableMobCategoryCounts;
        }

        // CraftBukkit start
        private boolean a(MobCategory enumcreaturetype, int limit) {
            int i = limit * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
            // CraftBukkit end

            return this.mobCategoryCounts.getInt(enumcreaturetype) < i;
        }
    }
}
