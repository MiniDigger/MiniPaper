package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class TheEndGatewayBlockEntity extends TheEndPortalBlockEntity implements TickableBlockEntity {

    private static final Logger LOGGER = LogManager.getLogger();
    public long age;
    private int teleportCooldown;
    @Nullable
    public BlockPos exitPortal;
    public boolean exactTeleport;

    public TheEndGatewayBlockEntity() {
        super(BlockEntityType.END_GATEWAY);
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        nbttagcompound.putLong("Age", this.age);
        if (this.exitPortal != null) {
            nbttagcompound.put("ExitPortal", NbtUtils.writeBlockPos(this.exitPortal));
        }

        if (this.exactTeleport) {
            nbttagcompound.putBoolean("ExactTeleport", this.exactTeleport);
        }

        return nbttagcompound;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.age = nbttagcompound.getLong("Age");
        if (nbttagcompound.contains("ExitPortal", 10)) {
            this.exitPortal = NbtUtils.readBlockPos(nbttagcompound.getCompound("ExitPortal"));
        }

        this.exactTeleport = nbttagcompound.getBoolean("ExactTeleport");
    }

    @Override
    public void tick() {
        boolean flag = this.isSpawning();
        boolean flag1 = this.isCoolingDown();

        ++this.age;
        if (flag1) {
            --this.teleportCooldown;
        } else if (!this.level.isClientSide) {
            List<Entity> list = this.level.getEntitiesOfClass(Entity.class, new AABB(this.getBlockPos()));

            if (!list.isEmpty()) {
                this.teleportEntity((Entity) list.get(this.level.random.nextInt(list.size())));
            }

            if (this.age % 2400L == 0L) {
                this.triggerCooldown();
            }
        }

        if (flag != this.isSpawning() || flag1 != this.isCoolingDown()) {
            this.setChanged();
        }

    }

    public boolean isSpawning() {
        return this.age < 200L;
    }

    public boolean isCoolingDown() {
        return this.teleportCooldown > 0;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 8, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    public void triggerCooldown() {
        if (!this.level.isClientSide) {
            this.teleportCooldown = 40;
            this.level.blockEvent(this.getBlockPos(), this.getBlock().getBlock(), 1, 0);
            this.setChanged();
        }

    }

    @Override
    public boolean triggerEvent(int i, int j) {
        if (i == 1) {
            this.teleportCooldown = 40;
            return true;
        } else {
            return super.triggerEvent(i, j);
        }
    }

    public void teleportEntity(Entity entity) {
        if (this.level instanceof ServerLevel && !this.isCoolingDown()) {
            this.teleportCooldown = 100;
            if (this.exitPortal == null && this.level.getDimensionKey() == Level.END) {
                this.findExitPortal((ServerLevel) this.level);
            }

            if (this.exitPortal != null) {
                BlockPos blockposition = this.exactTeleport ? this.exitPortal : this.findExitPosition();
                Entity entity1;

                if (entity instanceof ThrownEnderpearl) {
                    Entity entity2 = ((ThrownEnderpearl) entity).getOwner();

                    if (entity2 instanceof ServerPlayer) {
                        CriteriaTriggers.ENTER_BLOCK.trigger((ServerPlayer) entity2, this.level.getType(this.getBlockPos()));
                    }

                    if (entity2 != null) {
                        entity1 = entity2;
                        entity.remove();
                    } else {
                        entity1 = entity;
                    }
                } else {
                    entity1 = entity.getRootVehicle();
                }

                // CraftBukkit start - Fire PlayerTeleportEvent
                if (entity1 instanceof ServerPlayer) {
                    org.bukkit.craftbukkit.entity.CraftPlayer player = (CraftPlayer) entity1.getBukkitEntity();
                    org.bukkit.Location location = new Location(level.getWorld(), (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D);
                    location.setPitch(player.getLocation().getPitch());
                    location.setYaw(player.getLocation().getYaw());

                    PlayerTeleportEvent teleEvent = new PlayerTeleportEvent(player, player.getLocation(), location, PlayerTeleportEvent.TeleportCause.END_GATEWAY);
                    Bukkit.getPluginManager().callEvent(teleEvent);
                    if (teleEvent.isCancelled()) {
                        return;
                    }

                    ((ServerPlayer) entity).connection.teleport(teleEvent.getTo());
                    this.triggerCooldown(); // CraftBukkit - call at end of method
                    return;

                }
                // CraftBukkit end

                entity1.teleportToWithTicket((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D);
            }

            this.triggerCooldown();
        }
    }

    private BlockPos findExitPosition() {
        BlockPos blockposition = findTallestBlock(this.level, this.exitPortal, 5, false);

        TheEndGatewayBlockEntity.LOGGER.debug("Best exit position for portal at {} is {}", this.exitPortal, blockposition);
        return blockposition.above();
    }

    private void findExitPortal(ServerLevel worldserver) {
        Vec3 vec3d = (new Vec3((double) this.getBlockPos().getX(), 0.0D, (double) this.getBlockPos().getZ())).normalize();
        Vec3 vec3d1 = vec3d.scale(1024.0D);

        int i;

        for (i = 16; getChunk((Level) worldserver, vec3d1).getHighestSectionPosition() > 0 && i-- > 0; vec3d1 = vec3d1.add(vec3d.scale(-16.0D))) {
            TheEndGatewayBlockEntity.LOGGER.debug("Skipping backwards past nonempty chunk at {}", vec3d1);
        }

        for (i = 16; getChunk((Level) worldserver, vec3d1).getHighestSectionPosition() == 0 && i-- > 0; vec3d1 = vec3d1.add(vec3d.scale(16.0D))) {
            TheEndGatewayBlockEntity.LOGGER.debug("Skipping forward past empty chunk at {}", vec3d1);
        }

        TheEndGatewayBlockEntity.LOGGER.debug("Found chunk at {}", vec3d1);
        LevelChunk chunk = getChunk((Level) worldserver, vec3d1);

        this.exitPortal = findValidSpawnInChunk(chunk);
        if (this.exitPortal == null) {
            this.exitPortal = new BlockPos(vec3d1.x + 0.5D, 75.0D, vec3d1.z + 0.5D);
            TheEndGatewayBlockEntity.LOGGER.debug("Failed to find suitable block, settling on {}", this.exitPortal);
            Feature.END_ISLAND.configured(FeatureConfiguration.NONE).place(worldserver, worldserver.getStructureManager(), worldserver.getChunkSourceOH().getGenerator(), new Random(this.exitPortal.asLong()), this.exitPortal); // CraftBukkit - decompile error
        } else {
            TheEndGatewayBlockEntity.LOGGER.debug("Found block at {}", this.exitPortal);
        }

        this.exitPortal = findTallestBlock(worldserver, this.exitPortal, 16, true);
        TheEndGatewayBlockEntity.LOGGER.debug("Creating portal at {}", this.exitPortal);
        this.exitPortal = this.exitPortal.above(10);
        this.createExitPortal(worldserver, this.exitPortal);
        this.setChanged();
    }

    private static BlockPos findTallestBlock(BlockGetter iblockaccess, BlockPos blockposition, int i, boolean flag) {
        BlockPos blockposition1 = null;

        for (int j = -i; j <= i; ++j) {
            for (int k = -i; k <= i; ++k) {
                if (j != 0 || k != 0 || flag) {
                    for (int l = 255; l > (blockposition1 == null ? 0 : blockposition1.getY()); --l) {
                        BlockPos blockposition2 = new BlockPos(blockposition.getX() + j, l, blockposition.getZ() + k);
                        BlockState iblockdata = iblockaccess.getType(blockposition2);

                        if (iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition2) && (flag || !iblockdata.is(Blocks.BEDROCK))) {
                            blockposition1 = blockposition2;
                            break;
                        }
                    }
                }
            }
        }

        return blockposition1 == null ? blockposition : blockposition1;
    }

    private static LevelChunk getChunk(Level world, Vec3 vec3d) {
        return world.getChunk(Mth.floor(vec3d.x / 16.0D), Mth.floor(vec3d.z / 16.0D));
    }

    @Nullable
    private static BlockPos findValidSpawnInChunk(LevelChunk chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        BlockPos blockposition = new BlockPos(chunkcoordintpair.getMinBlockX(), 30, chunkcoordintpair.getMinBlockZ());
        int i = chunk.getHighestSectionPosition() + 16 - 1;
        BlockPos blockposition1 = new BlockPos(chunkcoordintpair.getMaxBlockX(), i, chunkcoordintpair.getMaxBlockZ());
        BlockPos blockposition2 = null;
        double d0 = 0.0D;
        Iterator iterator = BlockPos.betweenClosed(blockposition, blockposition1).iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition3 = (BlockPos) iterator.next();
            BlockState iblockdata = chunk.getType(blockposition3);
            BlockPos blockposition4 = blockposition3.above();
            BlockPos blockposition5 = blockposition3.above(2);

            if (iblockdata.is(Blocks.END_STONE) && !chunk.getType(blockposition4).isCollisionShapeFullBlock(chunk, blockposition4) && !chunk.getType(blockposition5).isCollisionShapeFullBlock(chunk, blockposition5)) {
                double d1 = blockposition3.distSqr(0.0D, 0.0D, 0.0D, true);

                if (blockposition2 == null || d1 < d0) {
                    blockposition2 = blockposition3;
                    d0 = d1;
                }
            }
        }

        return blockposition2;
    }

    private void createExitPortal(ServerLevel worldserver, BlockPos blockposition) {
        Feature.END_GATEWAY.configured(EndGatewayConfiguration.knownExit(this.getBlockPos(), false)).place(worldserver, worldserver.getStructureManager(), worldserver.getChunkSourceOH().getGenerator(), new Random(), blockposition); // CraftBukkit - decompile error
    }

    public void setExitPosition(BlockPos blockposition, boolean flag) {
        this.exactTeleport = flag;
        this.exitPortal = blockposition;
    }
}
