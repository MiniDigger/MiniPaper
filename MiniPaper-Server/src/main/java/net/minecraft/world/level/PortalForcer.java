package net.minecraft.world.level;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.phys.Vec3;

public class PortalForcer {

    private final ServerLevel level;
    private final Random random;

    public PortalForcer(ServerLevel worldserver) {
        this.level = worldserver;
        this.random = new Random(worldserver.getSeed());
    }

    public boolean findAndMoveToPortal(Entity entity, float f) {
        // CraftBukkit start
        return findAndTeleport(entity, entity.blockPosition(), f, 128, false) != null;
    }

    public BlockPattern.PortalInfo findAndTeleport(Entity entity, BlockPos findPosition, float f, int searchRadius, boolean searchOnly) {
        // CraftBukkit end
        Vec3 vec3d = entity.getPortalEntranceOffset();
        Direction enumdirection = entity.getPortalEntranceForwards();
        BlockPattern.PortalInfo shapedetector_shape = this.findPortal(findPosition, entity.getDeltaMovement(), enumdirection, vec3d.x, vec3d.y, entity instanceof Player, searchRadius); // CraftBukkit - add location and searchRadius
        if (searchOnly) return shapedetector_shape; // CraftBukkit - optional teleporting

        if (shapedetector_shape == null) {
            return null; // CraftBukkit - return shape
        } else {
            Vec3 vec3d1 = shapedetector_shape.pos;
            Vec3 vec3d2 = shapedetector_shape.speed;

            entity.setDeltaMovement(vec3d2);
            entity.yRot = f + (float) shapedetector_shape.angle;
            entity.moveTo(vec3d1.x, vec3d1.y, vec3d1.z);
            return shapedetector_shape; // CraftBukkit - return shape
        }
    }

    @Nullable
    public BlockPattern.PortalInfo findPortal(BlockPos blockposition, Vec3 vec3d, Direction enumdirection, double d0, double d1, boolean flag) { // PAIL: rename to findPortal, d0 = portal offset x, d1 = portal offset z, flag = instanceof EntityHuman
        // CraftBukkit start
        return findPortal(blockposition, vec3d, enumdirection, d0, d1, flag, 128);
    }

    @Nullable
    public BlockPattern.PortalInfo findPortal(BlockPos blockposition, Vec3 vec3d, Direction enumdirection, double d0, double d1, boolean flag, int searchRadius) {
        // CraftBukkit end
        PoiManager villageplace = this.level.getPoiManager();

        villageplace.ensureLoadedAndValid(this.level, blockposition, 128);
        List<PoiRecord> list = (List) villageplace.getInSquare((villageplacetype) -> {
            return villageplacetype == PoiType.NETHER_PORTAL;
        }, blockposition, searchRadius, PoiManager.Occupancy.ANY).collect(Collectors.toList()); // CraftBukkit - searchRadius
        Optional<PoiRecord> optional = list.stream().min(Comparator.<PoiRecord>comparingDouble((villageplacerecord) -> { // CraftBukkit - decompile error
            return villageplacerecord.getPos().distSqr(blockposition);
        }).thenComparingInt((villageplacerecord) -> {
            return villageplacerecord.getPos().getY();
        }));

        return (BlockPattern.PortalInfo) optional.map((villageplacerecord) -> {
            BlockPos blockposition1 = villageplacerecord.getPos();

            this.level.getChunkSourceOH().addRegionTicket(TicketType.PORTAL, new ChunkPos(blockposition1), 3, blockposition1);
            BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = NetherPortalBlock.getPortalShape((LevelAccessor) this.level, blockposition1);

            return shapedetector_shapedetectorcollection.getPortalOutput(enumdirection, blockposition1, d1, vec3d, d0);
        }).orElse(null); // CraftBukkit - decompile error
    }

    public boolean createPortal(Entity entity) {
        // CraftBukkit start - providable position and creation radius
        return createPortal(entity, entity.blockPosition(), 16);
    }

    public boolean createPortal(Entity entity, BlockPos createPosition, int createRadius) {
        // CraftBukkit end
        boolean flag = true;
        double d0 = -1.0D;
        // CraftBukkit start - providable position
        int i = createPosition.getX();
        int j = createPosition.getY();
        int k = createPosition.getZ();
        // CraftBukkit end
        int l = i;
        int i1 = j;
        int j1 = k;
        int k1 = 0;
        int l1 = this.random.nextInt(4);
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        double d1;
        int i2;
        double d2;
        int j2;
        int k2;
        int l2;
        int i3;
        int j3;
        int k3;
        int l3;
        int i4;
        int j4;
        int k4;
        double d3;
        double d4;

        for (i2 = i - createRadius; i2 <= i + createRadius; ++i2) { // CraftBukkit - createRadius
            d1 = (double) i2 + 0.5D - createPosition.getX(); // CraftBukkit - providable position

            for (j2 = k - createRadius; j2 <= k + createRadius; ++j2) { // CraftBukkit - createRadius
                d2 = (double) j2 + 0.5D - createPosition.getZ(); // CraftBukkit - providable position

                label257:
                for (k2 = this.level.getHeight() - 1; k2 >= 0; --k2) {
                    if (this.level.isEmptyBlock(blockposition_mutableblockposition.d(i2, k2, j2))) {
                        while (k2 > 0 && this.level.isEmptyBlock(blockposition_mutableblockposition.d(i2, k2 - 1, j2))) {
                            --k2;
                        }

                        for (i3 = l1; i3 < l1 + 4; ++i3) {
                            l2 = i3 % 2;
                            j3 = 1 - l2;
                            if (i3 % 4 >= 2) {
                                l2 = -l2;
                                j3 = -j3;
                            }

                            for (l3 = 0; l3 < 3; ++l3) {
                                for (i4 = 0; i4 < 4; ++i4) {
                                    for (k4 = -1; k4 < 4; ++k4) {
                                        k3 = i2 + (i4 - 1) * l2 + l3 * j3;
                                        j4 = k2 + k4;
                                        int l4 = j2 + (i4 - 1) * j3 - l3 * l2;

                                        blockposition_mutableblockposition.d(k3, j4, l4);
                                        if (k4 < 0 && !this.level.getType(blockposition_mutableblockposition).getMaterial().isSolid() || k4 >= 0 && !this.level.isEmptyBlock(blockposition_mutableblockposition)) {
                                            continue label257;
                                        }
                                    }
                                }
                            }

                            d3 = (double) k2 + 0.5D - entity.getY();
                            d4 = d1 * d1 + d3 * d3 + d2 * d2;
                            if (d0 < 0.0D || d4 < d0) {
                                d0 = d4;
                                l = i2;
                                i1 = k2;
                                j1 = j2;
                                k1 = i3 % 4;
                            }
                        }
                    }
                }
            }
        }

        if (d0 < 0.0D) {
            for (i2 = i - createRadius; i2 <= i + createRadius; ++i2) { // CraftBukkit - createRadius
                d1 = (double) i2 + 0.5D - createPosition.getX(); // CraftBukkit - providable position

                for (j2 = k - createRadius; j2 <= k + createRadius; ++j2) { // CraftBukkit - createRadius
                    d2 = (double) j2 + 0.5D - createPosition.getZ(); // CraftBukkit - providable position

                    label205:
                    for (k2 = this.level.getHeight() - 1; k2 >= 0; --k2) {
                        if (this.level.isEmptyBlock(blockposition_mutableblockposition.d(i2, k2, j2))) {
                            while (k2 > 0 && this.level.isEmptyBlock(blockposition_mutableblockposition.d(i2, k2 - 1, j2))) {
                                --k2;
                            }

                            for (i3 = l1; i3 < l1 + 2; ++i3) {
                                l2 = i3 % 2;
                                j3 = 1 - l2;

                                for (l3 = 0; l3 < 4; ++l3) {
                                    for (i4 = -1; i4 < 4; ++i4) {
                                        k4 = i2 + (l3 - 1) * l2;
                                        k3 = k2 + i4;
                                        j4 = j2 + (l3 - 1) * j3;
                                        blockposition_mutableblockposition.d(k4, k3, j4);
                                        if (i4 < 0 && !this.level.getType(blockposition_mutableblockposition).getMaterial().isSolid() || i4 >= 0 && !this.level.isEmptyBlock(blockposition_mutableblockposition)) {
                                            continue label205;
                                        }
                                    }
                                }

                                d3 = (double) k2 + 0.5D - entity.getY();
                                d4 = d1 * d1 + d3 * d3 + d2 * d2;
                                if (d0 < 0.0D || d4 < d0) {
                                    d0 = d4;
                                    l = i2;
                                    i1 = k2;
                                    j1 = j2;
                                    k1 = i3 % 2;
                                }
                            }
                        }
                    }
                }
            }
        }

        int i5 = l;
        int j5 = i1;

        j2 = j1;
        int k5 = k1 % 2;
        int l5 = 1 - k5;

        if (k1 % 4 >= 2) {
            k5 = -k5;
            l5 = -l5;
        }

        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(this.level); // CraftBukkit - Use BlockStateListPopulator
        if (d0 < 0.0D) {
            i1 = Mth.clamp(i1, 70, this.level.getHeight() - 10);
            j5 = i1;

            for (k2 = -1; k2 <= 1; ++k2) {
                for (i3 = 1; i3 < 3; ++i3) {
                    for (l2 = -1; l2 < 3; ++l2) {
                        j3 = i5 + (i3 - 1) * k5 + k2 * l5;
                        l3 = j5 + l2;
                        i4 = j2 + (i3 - 1) * l5 - k2 * k5;
                        boolean flag1 = l2 < 0;

                        blockposition_mutableblockposition.d(j3, l3, i4);
                        blockList.setTypeAndData(blockposition_mutableblockposition, flag1 ? Blocks.OBSIDIAN.getBlockData() : Blocks.AIR.getBlockData(), 3); // CraftBukkit
                    }
                }
            }
        }

        for (k2 = -1; k2 < 3; ++k2) {
            for (i3 = -1; i3 < 4; ++i3) {
                if (k2 == -1 || k2 == 2 || i3 == -1 || i3 == 3) {
                    blockposition_mutableblockposition.d(i5 + k2 * k5, j5 + i3, j2 + k2 * l5);
                    blockList.setTypeAndData(blockposition_mutableblockposition, Blocks.OBSIDIAN.getBlockData(), 3); // CraftBukkit
                }
            }
        }

        BlockState iblockdata = (BlockState) Blocks.NETHER_PORTAL.getBlockData().setValue(NetherPortalBlock.AXIS, k5 == 0 ? Direction.Axis.Z : Direction.Axis.X);

        for (i3 = 0; i3 < 2; ++i3) {
            for (l2 = 0; l2 < 3; ++l2) {
                blockposition_mutableblockposition.d(i5 + i3 * k5, j5 + l2, j2 + i3 * l5);
                blockList.setTypeAndData(blockposition_mutableblockposition, iblockdata, 18); // CraftBukkit
            }
        }

        // CraftBukkit start
        org.bukkit.World bworld = this.level.getWorld();
        org.bukkit.event.world.PortalCreateEvent event = new org.bukkit.event.world.PortalCreateEvent((java.util.List<org.bukkit.block.BlockState>) (java.util.List) blockList.getList(), bworld, entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.NETHER_PAIR);

        this.level.getServerOH().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            blockList.updateList();
        }
        // CraftBukkit end
        return true;
    }
}
