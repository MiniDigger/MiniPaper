package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class StructureTemplate {

    private final List<StructureTemplate.Palette> palettes = Lists.newArrayList();
    private final List<StructureTemplate.EntityInfo> entityInfoList = Lists.newArrayList();
    private BlockPos size;
    private String author;

    public StructureTemplate() {
        this.size = BlockPos.ZERO;
        this.author = "?";
    }

    public BlockPos getSize() {
        return this.size;
    }

    public void setAuthor(String s) {
        this.author = s;
    }

    public String getAuthor() {
        return this.author;
    }

    public void fillFromWorld(Level world, BlockPos blockposition, BlockPos blockposition1, boolean flag, @Nullable Block block) {
        if (blockposition1.getX() >= 1 && blockposition1.getY() >= 1 && blockposition1.getZ() >= 1) {
            BlockPos blockposition2 = blockposition.offset((Vec3i) blockposition1).offset(-1, -1, -1);
            List<StructureTemplate.BlockInfo> list = Lists.newArrayList();
            List<StructureTemplate.BlockInfo> list1 = Lists.newArrayList();
            List<StructureTemplate.BlockInfo> list2 = Lists.newArrayList();
            BlockPos blockposition3 = new BlockPos(Math.min(blockposition.getX(), blockposition2.getX()), Math.min(blockposition.getY(), blockposition2.getY()), Math.min(blockposition.getZ(), blockposition2.getZ()));
            BlockPos blockposition4 = new BlockPos(Math.max(blockposition.getX(), blockposition2.getX()), Math.max(blockposition.getY(), blockposition2.getY()), Math.max(blockposition.getZ(), blockposition2.getZ()));

            this.size = blockposition1;
            Iterator iterator = BlockPos.betweenClosed(blockposition3, blockposition4).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition5 = (BlockPos) iterator.next();
                BlockPos blockposition6 = blockposition5.subtract(blockposition3);
                BlockState iblockdata = world.getType(blockposition5);

                if (block == null || block != iblockdata.getBlock()) {
                    BlockEntity tileentity = world.getBlockEntity(blockposition5);
                    StructureTemplate.BlockInfo definedstructure_blockinfo;

                    if (tileentity != null) {
                        CompoundTag nbttagcompound = tileentity.save(new CompoundTag());

                        nbttagcompound.remove("x");
                        nbttagcompound.remove("y");
                        nbttagcompound.remove("z");
                        definedstructure_blockinfo = new StructureTemplate.BlockInfo(blockposition6, iblockdata, nbttagcompound.copy());
                    } else {
                        definedstructure_blockinfo = new StructureTemplate.BlockInfo(blockposition6, iblockdata, (CompoundTag) null);
                    }

                    a(definedstructure_blockinfo, (List) list, (List) list1, (List) list2);
                }
            }

            List<StructureTemplate.BlockInfo> list3 = buildInfoList((List) list, (List) list1, (List) list2);

            this.palettes.clear();
            this.palettes.add(new StructureTemplate.Palette(list3));
            if (flag) {
                this.fillEntityList(world, blockposition3, blockposition4.offset(1, 1, 1));
            } else {
                this.entityInfoList.clear();
            }

        }
    }

    private static void a(StructureTemplate.BlockInfo definedstructure_blockinfo, List<StructureTemplate.BlockInfo> list, List<StructureTemplate.BlockInfo> list1, List<StructureTemplate.BlockInfo> list2) {
        if (definedstructure_blockinfo.c != null) {
            list1.add(definedstructure_blockinfo);
        } else if (!definedstructure_blockinfo.b.getBlock().hasDynamicShape() && definedstructure_blockinfo.b.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            list.add(definedstructure_blockinfo);
        } else {
            list2.add(definedstructure_blockinfo);
        }

    }

    private static List<StructureTemplate.BlockInfo> buildInfoList(List<StructureTemplate.BlockInfo> list, List<StructureTemplate.BlockInfo> list1, List<StructureTemplate.BlockInfo> list2) {
        Comparator<StructureTemplate.BlockInfo> comparator = Comparator.<StructureTemplate.BlockInfo>comparingInt((definedstructure_blockinfo) -> { // CraftBukkit - decompile error
            return definedstructure_blockinfo.a.getY();
        }).thenComparingInt((definedstructure_blockinfo) -> {
            return definedstructure_blockinfo.a.getX();
        }).thenComparingInt((definedstructure_blockinfo) -> {
            return definedstructure_blockinfo.a.getZ();
        });

        list.sort(comparator);
        list2.sort(comparator);
        list1.sort(comparator);
        List<StructureTemplate.BlockInfo> list3 = Lists.newArrayList();

        list3.addAll(list);
        list3.addAll(list2);
        list3.addAll(list1);
        return list3;
    }

    private void fillEntityList(Level world, BlockPos blockposition, BlockPos blockposition1) {
        List<Entity> list = world.getEntitiesOfClass(Entity.class, new AABB(blockposition, blockposition1), (java.util.function.Predicate) (entity) -> { // CraftBukkit - decompile error
            return !(entity instanceof Player);
        });

        this.entityInfoList.clear();

        Vec3 vec3d;
        CompoundTag nbttagcompound;
        BlockPos blockposition2;

        for (Iterator iterator = list.iterator(); iterator.hasNext(); this.entityInfoList.add(new StructureTemplate.EntityInfo(vec3d, blockposition2, nbttagcompound.copy()))) {
            Entity entity = (Entity) iterator.next();

            vec3d = new Vec3(entity.getX() - (double) blockposition.getX(), entity.getY() - (double) blockposition.getY(), entity.getZ() - (double) blockposition.getZ());
            nbttagcompound = new CompoundTag();
            entity.save(nbttagcompound);
            if (entity instanceof Painting) {
                blockposition2 = ((Painting) entity).getPos().subtract(blockposition);
            } else {
                blockposition2 = new BlockPos(vec3d);
            }
        }

    }

    public List<StructureTemplate.BlockInfo> filterBlocks(BlockPos blockposition, StructurePlaceSettings definedstructureinfo, Block block) {
        return this.filterBlocks(blockposition, definedstructureinfo, block, true);
    }

    public List<StructureTemplate.BlockInfo> filterBlocks(BlockPos blockposition, StructurePlaceSettings definedstructureinfo, Block block, boolean flag) {
        List<StructureTemplate.BlockInfo> list = Lists.newArrayList();
        BoundingBox structureboundingbox = definedstructureinfo.getBoundingBox();

        if (this.palettes.isEmpty()) {
            return Collections.emptyList();
        } else {
            Iterator iterator = definedstructureinfo.getRandomPalette(this.palettes, blockposition).blocks(block).iterator();

            while (iterator.hasNext()) {
                StructureTemplate.BlockInfo definedstructure_blockinfo = (StructureTemplate.BlockInfo) iterator.next();
                BlockPos blockposition1 = flag ? calculateRelativePosition(definedstructureinfo, definedstructure_blockinfo.a).offset((Vec3i) blockposition) : definedstructure_blockinfo.a;

                if (structureboundingbox == null || structureboundingbox.isInside((Vec3i) blockposition1)) {
                    list.add(new StructureTemplate.BlockInfo(blockposition1, definedstructure_blockinfo.b.rotate(definedstructureinfo.getRotation()), definedstructure_blockinfo.c));
                }
            }

            return list;
        }
    }

    public BlockPos calculateConnectedPosition(StructurePlaceSettings definedstructureinfo, BlockPos blockposition, StructurePlaceSettings definedstructureinfo1, BlockPos blockposition1) {
        BlockPos blockposition2 = calculateRelativePosition(definedstructureinfo, blockposition);
        BlockPos blockposition3 = calculateRelativePosition(definedstructureinfo1, blockposition1);

        return blockposition2.subtract(blockposition3);
    }

    public static BlockPos calculateRelativePosition(StructurePlaceSettings definedstructureinfo, BlockPos blockposition) {
        return transform(blockposition, definedstructureinfo.getMirror(), definedstructureinfo.getRotation(), definedstructureinfo.getRotationPivot());
    }

    public void placeInWorldChunk(LevelAccessor generatoraccess, BlockPos blockposition, StructurePlaceSettings definedstructureinfo, Random random) {
        definedstructureinfo.updateBoundingBoxFromChunkPos();
        this.placeInWorld(generatoraccess, blockposition, definedstructureinfo, random);
    }

    public void placeInWorld(LevelAccessor generatoraccess, BlockPos blockposition, StructurePlaceSettings definedstructureinfo, Random random) {
        this.placeInWorld(generatoraccess, blockposition, blockposition, definedstructureinfo, random, 2);
    }

    public boolean placeInWorld(LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1, StructurePlaceSettings definedstructureinfo, Random random, int i) {
        if (this.palettes.isEmpty()) {
            return false;
        } else {
            List<StructureTemplate.BlockInfo> list = definedstructureinfo.getRandomPalette(this.palettes, blockposition).blocks();

            if ((!list.isEmpty() || !definedstructureinfo.isIgnoreEntities() && !this.entityInfoList.isEmpty()) && this.size.getX() >= 1 && this.size.getY() >= 1 && this.size.getZ() >= 1) {
                BoundingBox structureboundingbox = definedstructureinfo.getBoundingBox();
                List<BlockPos> list1 = Lists.newArrayListWithCapacity(definedstructureinfo.shouldKeepLiquids() ? list.size() : 0);
                List<Pair<BlockPos, CompoundTag>> list2 = Lists.newArrayListWithCapacity(list.size());
                int j = Integer.MAX_VALUE;
                int k = Integer.MAX_VALUE;
                int l = Integer.MAX_VALUE;
                int i1 = Integer.MIN_VALUE;
                int j1 = Integer.MIN_VALUE;
                int k1 = Integer.MIN_VALUE;
                List<StructureTemplate.BlockInfo> list3 = processBlockInfos(generatoraccess, blockposition, blockposition1, definedstructureinfo, list);
                Iterator iterator = list3.iterator();

                BlockEntity tileentity;

                while (iterator.hasNext()) {
                    StructureTemplate.BlockInfo definedstructure_blockinfo = (StructureTemplate.BlockInfo) iterator.next();
                    BlockPos blockposition2 = definedstructure_blockinfo.a;

                    if (structureboundingbox == null || structureboundingbox.isInside((Vec3i) blockposition2)) {
                        FluidState fluid = definedstructureinfo.shouldKeepLiquids() ? generatoraccess.getFluidState(blockposition2) : null;
                        BlockState iblockdata = definedstructure_blockinfo.b.mirror(definedstructureinfo.getMirror()).rotate(definedstructureinfo.getRotation());

                        if (definedstructure_blockinfo.c != null) {
                            tileentity = generatoraccess.getBlockEntity(blockposition2);
                            Clearable.tryClear(tileentity);
                            generatoraccess.setTypeAndData(blockposition2, Blocks.BARRIER.getBlockData(), 20);
                        }

                        if (generatoraccess.setTypeAndData(blockposition2, iblockdata, i)) {
                            j = Math.min(j, blockposition2.getX());
                            k = Math.min(k, blockposition2.getY());
                            l = Math.min(l, blockposition2.getZ());
                            i1 = Math.max(i1, blockposition2.getX());
                            j1 = Math.max(j1, blockposition2.getY());
                            k1 = Math.max(k1, blockposition2.getZ());
                            list2.add(Pair.of(blockposition2, definedstructure_blockinfo.c));
                            if (definedstructure_blockinfo.c != null) {
                                tileentity = generatoraccess.getBlockEntity(blockposition2);
                                if (tileentity != null) {
                                    definedstructure_blockinfo.c.putInt("x", blockposition2.getX());
                                    definedstructure_blockinfo.c.putInt("y", blockposition2.getY());
                                    definedstructure_blockinfo.c.putInt("z", blockposition2.getZ());
                                    if (tileentity instanceof RandomizableContainerBlockEntity) {
                                        definedstructure_blockinfo.c.putLong("LootTableSeed", random.nextLong());
                                    }

                                    tileentity.load(definedstructure_blockinfo.b, definedstructure_blockinfo.c);
                                    tileentity.mirror(definedstructureinfo.getMirror());
                                    tileentity.rotate(definedstructureinfo.getRotation());
                                }
                            }

                            if (fluid != null && iblockdata.getBlock() instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer) iblockdata.getBlock()).place(generatoraccess, blockposition2, iblockdata, fluid);
                                if (!fluid.isSource()) {
                                    list1.add(blockposition2);
                                }
                            }
                        }
                    }
                }

                boolean flag = true;
                Direction[] aenumdirection = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

                Iterator iterator1;
                BlockPos blockposition3;
                BlockState iblockdata1;

                while (flag && !list1.isEmpty()) {
                    flag = false;
                    iterator1 = list1.iterator();

                    while (iterator1.hasNext()) {
                        BlockPos blockposition4 = (BlockPos) iterator1.next();

                        blockposition3 = blockposition4;
                        FluidState fluid1 = generatoraccess.getFluidState(blockposition4);

                        for (int l1 = 0; l1 < aenumdirection.length && !fluid1.isSource(); ++l1) {
                            BlockPos blockposition5 = blockposition3.relative(aenumdirection[l1]);
                            FluidState fluid2 = generatoraccess.getFluidState(blockposition5);

                            if (fluid2.getHeight(generatoraccess, blockposition5) > fluid1.getHeight(generatoraccess, blockposition3) || fluid2.isSource() && !fluid1.isSource()) {
                                fluid1 = fluid2;
                                blockposition3 = blockposition5;
                            }
                        }

                        if (fluid1.isSource()) {
                            iblockdata1 = generatoraccess.getType(blockposition4);
                            Block block = iblockdata1.getBlock();

                            if (block instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer) block).place(generatoraccess, blockposition4, iblockdata1, fluid1);
                                flag = true;
                                iterator1.remove();
                            }
                        }
                    }
                }

                if (j <= i1) {
                    if (!definedstructureinfo.getKnownShape()) {
                        BitSetDiscreteVoxelShape voxelshapebitset = new BitSetDiscreteVoxelShape(i1 - j + 1, j1 - k + 1, k1 - l + 1);
                        int i2 = j;
                        int j2 = k;
                        int k2 = l;
                        Iterator iterator2 = list2.iterator();

                        while (iterator2.hasNext()) {
                            Pair<BlockPos, CompoundTag> pair = (Pair) iterator2.next();
                            BlockPos blockposition6 = (BlockPos) pair.getFirst();

                            voxelshapebitset.setFull(blockposition6.getX() - i2, blockposition6.getY() - j2, blockposition6.getZ() - k2, true, true);
                        }

                        updateShapeAtEdge(generatoraccess, i, voxelshapebitset, i2, j2, k2);
                    }

                    iterator1 = list2.iterator();

                    while (iterator1.hasNext()) {
                        Pair<BlockPos, CompoundTag> pair1 = (Pair) iterator1.next();

                        blockposition3 = (BlockPos) pair1.getFirst();
                        if (!definedstructureinfo.getKnownShape()) {
                            BlockState iblockdata2 = generatoraccess.getType(blockposition3);

                            iblockdata1 = Block.updateFromNeighbourShapes(iblockdata2, generatoraccess, blockposition3);
                            if (iblockdata2 != iblockdata1) {
                                generatoraccess.setTypeAndData(blockposition3, iblockdata1, i & -2 | 16);
                            }

                            generatoraccess.blockUpdated(blockposition3, iblockdata1.getBlock());
                        }

                        if (pair1.getSecond() != null) {
                            tileentity = generatoraccess.getBlockEntity(blockposition3);
                            if (tileentity != null) {
                                tileentity.setChanged();
                            }
                        }
                    }
                }

                if (!definedstructureinfo.isIgnoreEntities()) {
                    this.placeEntities(generatoraccess, blockposition, definedstructureinfo.getMirror(), definedstructureinfo.getRotation(), definedstructureinfo.getRotationPivot(), structureboundingbox, definedstructureinfo.shouldFinalizeEntities());
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static void updateShapeAtEdge(LevelAccessor generatoraccess, int i, DiscreteVoxelShape voxelshapediscrete, int j, int k, int l) {
        voxelshapediscrete.forAllFaces((enumdirection, i1, j1, k1) -> {
            BlockPos blockposition = new BlockPos(j + i1, k + j1, l + k1);
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            BlockState iblockdata = generatoraccess.getType(blockposition);
            BlockState iblockdata1 = generatoraccess.getType(blockposition1);
            BlockState iblockdata2 = iblockdata.updateState(enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);

            if (iblockdata != iblockdata2) {
                generatoraccess.setTypeAndData(blockposition, iblockdata2, i & -2);
            }

            BlockState iblockdata3 = iblockdata1.updateState(enumdirection.getOpposite(), iblockdata2, generatoraccess, blockposition1, blockposition);

            if (iblockdata1 != iblockdata3) {
                generatoraccess.setTypeAndData(blockposition1, iblockdata3, i & -2);
            }

        });
    }

    public static List<StructureTemplate.BlockInfo> processBlockInfos(LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1, StructurePlaceSettings definedstructureinfo, List<StructureTemplate.BlockInfo> list) {
        List<StructureTemplate.BlockInfo> list1 = Lists.newArrayList();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            StructureTemplate.BlockInfo definedstructure_blockinfo = (StructureTemplate.BlockInfo) iterator.next();
            BlockPos blockposition2 = calculateRelativePosition(definedstructureinfo, definedstructure_blockinfo.a).offset((Vec3i) blockposition);
            StructureTemplate.BlockInfo definedstructure_blockinfo1 = new StructureTemplate.BlockInfo(blockposition2, definedstructure_blockinfo.b, definedstructure_blockinfo.c != null ? definedstructure_blockinfo.c.copy() : null);

            for (Iterator iterator1 = definedstructureinfo.getProcessors().iterator(); definedstructure_blockinfo1 != null && iterator1.hasNext(); definedstructure_blockinfo1 = ((StructureProcessor) iterator1.next()).a(generatoraccess, blockposition, blockposition1, definedstructure_blockinfo, definedstructure_blockinfo1, definedstructureinfo)) {
                ;
            }

            if (definedstructure_blockinfo1 != null) {
                list1.add(definedstructure_blockinfo1);
            }
        }

        return list1;
    }

    private void placeEntities(LevelAccessor generatoraccess, BlockPos blockposition, Mirror enumblockmirror, Rotation enumblockrotation, BlockPos blockposition1, @Nullable BoundingBox structureboundingbox, boolean flag) {
        Iterator iterator = this.entityInfoList.iterator();

        while (iterator.hasNext()) {
            StructureTemplate.EntityInfo definedstructure_entityinfo = (StructureTemplate.EntityInfo) iterator.next();
            BlockPos blockposition2 = transform(definedstructure_entityinfo.b, enumblockmirror, enumblockrotation, blockposition1).offset((Vec3i) blockposition);

            if (structureboundingbox == null || structureboundingbox.isInside((Vec3i) blockposition2)) {
                CompoundTag nbttagcompound = definedstructure_entityinfo.c.copy();
                Vec3 vec3d = transform(definedstructure_entityinfo.a, enumblockmirror, enumblockrotation, blockposition1);
                Vec3 vec3d1 = vec3d.add((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                ListTag nbttaglist = new ListTag();

                nbttaglist.add(DoubleTag.valueOf(vec3d1.x));
                nbttaglist.add(DoubleTag.valueOf(vec3d1.y));
                nbttaglist.add(DoubleTag.valueOf(vec3d1.z));
                nbttagcompound.put("Pos", nbttaglist);
                nbttagcompound.remove("UUID");
                createEntityIgnoreException(generatoraccess, nbttagcompound).ifPresent((entity) -> {
                    float f = entity.mirror(enumblockmirror);

                    f += entity.yRot - entity.rotate(enumblockrotation);
                    entity.moveTo(vec3d1.x, vec3d1.y, vec3d1.z, f, entity.xRot);
                    if (flag && entity instanceof Mob) {
                        ((Mob) entity).prepare(generatoraccess, generatoraccess.getDamageScaler(new BlockPos(vec3d1)), MobSpawnType.STRUCTURE, (SpawnGroupData) null, nbttagcompound);
                    }

                    generatoraccess.addFreshEntity(entity);
                });
            }
        }

    }

    private static Optional<Entity> createEntityIgnoreException(LevelAccessor generatoraccess, CompoundTag nbttagcompound) {
        // CraftBukkit start
        // try {
            return EntityType.create(nbttagcompound, generatoraccess.getLevel());
        // } catch (Exception exception) {
            // return Optional.empty();
        // }
        // CraftBukkit end
    }

    public BlockPos getSize(Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new BlockPos(this.size.getZ(), this.size.getY(), this.size.getX());
            default:
                return this.size;
        }
    }

    public static BlockPos transform(BlockPos blockposition, Mirror enumblockmirror, Rotation enumblockrotation, BlockPos blockposition1) {
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();
        boolean flag = true;

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                k = -k;
                break;
            case FRONT_BACK:
                i = -i;
                break;
            default:
                flag = false;
        }

        int l = blockposition1.getX();
        int i1 = blockposition1.getZ();

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPos(l - i1 + k, j, l + i1 - i);
            case CLOCKWISE_90:
                return new BlockPos(l + i1 - k, j, i1 - l + i);
            case CLOCKWISE_180:
                return new BlockPos(l + l - i, j, i1 + i1 - k);
            default:
                return flag ? new BlockPos(i, j, k) : blockposition;
        }
    }

    public static Vec3 transform(Vec3 vec3d, Mirror enumblockmirror, Rotation enumblockrotation, BlockPos blockposition) {
        double d0 = vec3d.x;
        double d1 = vec3d.y;
        double d2 = vec3d.z;
        boolean flag = true;

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                d2 = 1.0D - d2;
                break;
            case FRONT_BACK:
                d0 = 1.0D - d0;
                break;
            default:
                flag = false;
        }

        int i = blockposition.getX();
        int j = blockposition.getZ();

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3((double) (i - j) + d2, d1, (double) (i + j + 1) - d0);
            case CLOCKWISE_90:
                return new Vec3((double) (i + j + 1) - d2, d1, (double) (j - i) + d0);
            case CLOCKWISE_180:
                return new Vec3((double) (i + i + 1) - d0, d1, (double) (j + j + 1) - d2);
            default:
                return flag ? new Vec3(d0, d1, d2) : vec3d;
        }
    }

    public BlockPos getZeroPositionWithTransform(BlockPos blockposition, Mirror enumblockmirror, Rotation enumblockrotation) {
        return getZeroPositionWithTransform(blockposition, enumblockmirror, enumblockrotation, this.getSize().getX(), this.getSize().getZ());
    }

    public static BlockPos getZeroPositionWithTransform(BlockPos blockposition, Mirror enumblockmirror, Rotation enumblockrotation, int i, int j) {
        --i;
        --j;
        int k = enumblockmirror == Mirror.FRONT_BACK ? i : 0;
        int l = enumblockmirror == Mirror.LEFT_RIGHT ? j : 0;
        BlockPos blockposition1 = blockposition;

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                blockposition1 = blockposition.offset(l, 0, i - k);
                break;
            case CLOCKWISE_90:
                blockposition1 = blockposition.offset(j - l, 0, k);
                break;
            case CLOCKWISE_180:
                blockposition1 = blockposition.offset(i - k, 0, j - l);
                break;
            case NONE:
                blockposition1 = blockposition.offset(k, 0, l);
        }

        return blockposition1;
    }

    public BoundingBox getBoundingBox(StructurePlaceSettings definedstructureinfo, BlockPos blockposition) {
        return this.getBoundingBox(blockposition, definedstructureinfo.getRotation(), definedstructureinfo.getRotationPivot(), definedstructureinfo.getMirror());
    }

    public BoundingBox getBoundingBox(BlockPos blockposition, Rotation enumblockrotation, BlockPos blockposition1, Mirror enumblockmirror) {
        BlockPos blockposition2 = this.getSize(enumblockrotation);
        int i = blockposition1.getX();
        int j = blockposition1.getZ();
        int k = blockposition2.getX() - 1;
        int l = blockposition2.getY() - 1;
        int i1 = blockposition2.getZ() - 1;
        BoundingBox structureboundingbox = new BoundingBox(0, 0, 0, 0, 0, 0);

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                structureboundingbox = new BoundingBox(i - j, 0, i + j - i1, i - j + k, l, i + j);
                break;
            case CLOCKWISE_90:
                structureboundingbox = new BoundingBox(i + j - k, 0, j - i, i + j, l, j - i + i1);
                break;
            case CLOCKWISE_180:
                structureboundingbox = new BoundingBox(i + i - k, 0, j + j - i1, i + i, l, j + j);
                break;
            case NONE:
                structureboundingbox = new BoundingBox(0, 0, 0, k, l, i1);
        }

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                this.mirrorAABB(enumblockrotation, i1, k, structureboundingbox, Direction.NORTH, Direction.SOUTH);
                break;
            case FRONT_BACK:
                this.mirrorAABB(enumblockrotation, k, i1, structureboundingbox, Direction.WEST, Direction.EAST);
            case NONE:
        }

        structureboundingbox.move(blockposition.getX(), blockposition.getY(), blockposition.getZ());
        return structureboundingbox;
    }

    private void mirrorAABB(Rotation enumblockrotation, int i, int j, BoundingBox structureboundingbox, Direction enumdirection, Direction enumdirection1) {
        BlockPos blockposition = BlockPos.ZERO;

        if (enumblockrotation != Rotation.CLOCKWISE_90 && enumblockrotation != Rotation.COUNTERCLOCKWISE_90) {
            if (enumblockrotation == Rotation.CLOCKWISE_180) {
                blockposition = blockposition.relative(enumdirection1, i);
            } else {
                blockposition = blockposition.relative(enumdirection, i);
            }
        } else {
            blockposition = blockposition.relative(enumblockrotation.rotate(enumdirection), j);
        }

        structureboundingbox.move(blockposition.getX(), 0, blockposition.getZ());
    }

    public CompoundTag save(CompoundTag nbttagcompound) {
        if (this.palettes.isEmpty()) {
            nbttagcompound.put("blocks", new ListTag());
            nbttagcompound.put("palette", new ListTag());
        } else {
            List<StructureTemplate.SimplePalette> list = Lists.newArrayList();
            StructureTemplate.SimplePalette definedstructure_b = new StructureTemplate.SimplePalette();

            list.add(definedstructure_b);

            for (int i = 1; i < this.palettes.size(); ++i) {
                list.add(new StructureTemplate.SimplePalette());
            }

            ListTag nbttaglist = new ListTag();
            List<StructureTemplate.BlockInfo> list1 = ((StructureTemplate.Palette) this.palettes.get(0)).blocks();

            for (int j = 0; j < list1.size(); ++j) {
                StructureTemplate.BlockInfo definedstructure_blockinfo = (StructureTemplate.BlockInfo) list1.get(j);
                CompoundTag nbttagcompound1 = new CompoundTag();

                nbttagcompound1.put("pos", this.newIntegerList(definedstructure_blockinfo.a.getX(), definedstructure_blockinfo.a.getY(), definedstructure_blockinfo.a.getZ()));
                int k = definedstructure_b.idFor(definedstructure_blockinfo.b);

                nbttagcompound1.putInt("state", k);
                if (definedstructure_blockinfo.c != null) {
                    nbttagcompound1.put("nbt", definedstructure_blockinfo.c);
                }

                nbttaglist.add(nbttagcompound1);

                for (int l = 1; l < this.palettes.size(); ++l) {
                    StructureTemplate.SimplePalette definedstructure_b1 = (StructureTemplate.SimplePalette) list.get(l);

                    definedstructure_b1.addMapping(((StructureTemplate.BlockInfo) ((StructureTemplate.Palette) this.palettes.get(l)).blocks().get(j)).b, k);
                }
            }

            nbttagcompound.put("blocks", nbttaglist);
            ListTag nbttaglist1;
            Iterator iterator;

            if (list.size() == 1) {
                nbttaglist1 = new ListTag();
                iterator = definedstructure_b.iterator();

                while (iterator.hasNext()) {
                    BlockState iblockdata = (BlockState) iterator.next();

                    nbttaglist1.add(NbtUtils.writeBlockState(iblockdata));
                }

                nbttagcompound.put("palette", nbttaglist1);
            } else {
                nbttaglist1 = new ListTag();
                iterator = list.iterator();

                while (iterator.hasNext()) {
                    StructureTemplate.SimplePalette definedstructure_b2 = (StructureTemplate.SimplePalette) iterator.next();
                    ListTag nbttaglist2 = new ListTag();
                    Iterator iterator1 = definedstructure_b2.iterator();

                    while (iterator1.hasNext()) {
                        BlockState iblockdata1 = (BlockState) iterator1.next();

                        nbttaglist2.add(NbtUtils.writeBlockState(iblockdata1));
                    }

                    nbttaglist1.add(nbttaglist2);
                }

                nbttagcompound.put("palettes", nbttaglist1);
            }
        }

        ListTag nbttaglist3 = new ListTag();

        CompoundTag nbttagcompound2;

        for (Iterator iterator2 = this.entityInfoList.iterator(); iterator2.hasNext(); nbttaglist3.add(nbttagcompound2)) {
            StructureTemplate.EntityInfo definedstructure_entityinfo = (StructureTemplate.EntityInfo) iterator2.next();

            nbttagcompound2 = new CompoundTag();
            nbttagcompound2.put("pos", this.newDoubleList(definedstructure_entityinfo.a.x, definedstructure_entityinfo.a.y, definedstructure_entityinfo.a.z));
            nbttagcompound2.put("blockPos", this.newIntegerList(definedstructure_entityinfo.b.getX(), definedstructure_entityinfo.b.getY(), definedstructure_entityinfo.b.getZ()));
            if (definedstructure_entityinfo.c != null) {
                nbttagcompound2.put("nbt", definedstructure_entityinfo.c);
            }
        }

        nbttagcompound.put("entities", nbttaglist3);
        nbttagcompound.put("size", this.newIntegerList(this.size.getX(), this.size.getY(), this.size.getZ()));
        nbttagcompound.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        return nbttagcompound;
    }

    public void load(CompoundTag nbttagcompound) {
        this.palettes.clear();
        this.entityInfoList.clear();
        ListTag nbttaglist = nbttagcompound.getList("size", 3);

        this.size = new BlockPos(nbttaglist.getInt(0), nbttaglist.getInt(1), nbttaglist.getInt(2));
        ListTag nbttaglist1 = nbttagcompound.getList("blocks", 10);
        ListTag nbttaglist2;
        int i;

        if (nbttagcompound.contains("palettes", 9)) {
            nbttaglist2 = nbttagcompound.getList("palettes", 9);

            for (i = 0; i < nbttaglist2.size(); ++i) {
                this.loadPalette(nbttaglist2.getList(i), nbttaglist1);
            }
        } else {
            this.loadPalette(nbttagcompound.getList("palette", 10), nbttaglist1);
        }

        nbttaglist2 = nbttagcompound.getList("entities", 10);

        for (i = 0; i < nbttaglist2.size(); ++i) {
            CompoundTag nbttagcompound1 = nbttaglist2.getCompound(i);
            ListTag nbttaglist3 = nbttagcompound1.getList("pos", 6);
            Vec3 vec3d = new Vec3(nbttaglist3.getDouble(0), nbttaglist3.getDouble(1), nbttaglist3.getDouble(2));
            ListTag nbttaglist4 = nbttagcompound1.getList("blockPos", 3);
            BlockPos blockposition = new BlockPos(nbttaglist4.getInt(0), nbttaglist4.getInt(1), nbttaglist4.getInt(2));

            if (nbttagcompound1.contains("nbt")) {
                CompoundTag nbttagcompound2 = nbttagcompound1.getCompound("nbt");

                this.entityInfoList.add(new StructureTemplate.EntityInfo(vec3d, blockposition, nbttagcompound2));
            }
        }

    }

    private void loadPalette(ListTag nbttaglist, ListTag nbttaglist1) {
        StructureTemplate.SimplePalette definedstructure_b = new StructureTemplate.SimplePalette();

        for (int i = 0; i < nbttaglist.size(); ++i) {
            definedstructure_b.addMapping(NbtUtils.readBlockState(nbttaglist.getCompound(i)), i);
        }

        List<StructureTemplate.BlockInfo> list = Lists.newArrayList();
        List<StructureTemplate.BlockInfo> list1 = Lists.newArrayList();
        List<StructureTemplate.BlockInfo> list2 = Lists.newArrayList();

        for (int j = 0; j < nbttaglist1.size(); ++j) {
            CompoundTag nbttagcompound = nbttaglist1.getCompound(j);
            ListTag nbttaglist2 = nbttagcompound.getList("pos", 3);
            BlockPos blockposition = new BlockPos(nbttaglist2.getInt(0), nbttaglist2.getInt(1), nbttaglist2.getInt(2));
            BlockState iblockdata = definedstructure_b.stateFor(nbttagcompound.getInt("state"));
            CompoundTag nbttagcompound1;

            if (nbttagcompound.contains("nbt")) {
                nbttagcompound1 = nbttagcompound.getCompound("nbt");
            } else {
                nbttagcompound1 = null;
            }

            StructureTemplate.BlockInfo definedstructure_blockinfo = new StructureTemplate.BlockInfo(blockposition, iblockdata, nbttagcompound1);

            a(definedstructure_blockinfo, (List) list, (List) list1, (List) list2);
        }

        List<StructureTemplate.BlockInfo> list3 = buildInfoList((List) list, (List) list1, (List) list2);

        this.palettes.add(new StructureTemplate.Palette(list3));
    }

    private ListTag newIntegerList(int... aint) {
        ListTag nbttaglist = new ListTag();
        int[] aint1 = aint;
        int i = aint.length;

        for (int j = 0; j < i; ++j) {
            int k = aint1[j];

            nbttaglist.add(IntTag.valueOf(k));
        }

        return nbttaglist;
    }

    private ListTag newDoubleList(double... adouble) {
        ListTag nbttaglist = new ListTag();
        double[] adouble1 = adouble;
        int i = adouble.length;

        for (int j = 0; j < i; ++j) {
            double d0 = adouble1[j];

            nbttaglist.add(DoubleTag.valueOf(d0));
        }

        return nbttaglist;
    }

    public static final class Palette {

        private final List<StructureTemplate.BlockInfo> blocks;
        private final Map<Block, List<StructureTemplate.BlockInfo>> cache;

        private Palette(List<StructureTemplate.BlockInfo> list) {
            this.cache = Maps.newHashMap();
            this.blocks = list;
        }

        public List<StructureTemplate.BlockInfo> blocks() {
            return this.blocks;
        }

        public List<StructureTemplate.BlockInfo> blocks(Block block) {
            return (List) this.cache.computeIfAbsent(block, (block1) -> {
                return (List) this.blocks.stream().filter((definedstructure_blockinfo) -> {
                    return definedstructure_blockinfo.b.is(block1);
                }).collect(Collectors.toList());
            });
        }
    }

    public static class EntityInfo {

        public final Vec3 a;
        public final BlockPos b;
        public final CompoundTag c;

        public EntityInfo(Vec3 vec3d, BlockPos blockposition, CompoundTag nbttagcompound) {
            this.a = vec3d;
            this.b = blockposition;
            this.c = nbttagcompound;
        }
    }

    public static class BlockInfo {

        public final BlockPos a;
        public final BlockState b;
        public final CompoundTag c;

        public BlockInfo(BlockPos blockposition, BlockState iblockdata, @Nullable CompoundTag nbttagcompound) {
            this.a = blockposition;
            this.b = iblockdata;
            this.c = nbttagcompound;
        }

        public String toString() {
            return String.format("<StructureBlockInfo | %s | %s | %s>", this.a, this.b, this.c);
        }
    }

    static class SimplePalette implements Iterable<BlockState> {

        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.getBlockData();
        private final IdMapper<BlockState> ids;
        private int lastId;

        private SimplePalette() {
            this.ids = new IdMapper<>(16);
        }

        public int idFor(BlockState iblockdata) {
            int i = this.ids.getId(iblockdata);

            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(iblockdata, i);
            }

            return i;
        }

        @Nullable
        public BlockState stateFor(int i) {
            BlockState iblockdata = (BlockState) this.ids.byId(i);

            return iblockdata == null ? DEFAULT_BLOCK_STATE : iblockdata; // CraftBukkit - decompile error
        }

        public Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState iblockdata, int i) {
            this.ids.addMapping(iblockdata, i);
        }
    }
}
