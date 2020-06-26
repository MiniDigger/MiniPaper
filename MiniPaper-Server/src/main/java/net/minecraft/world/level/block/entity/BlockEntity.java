package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Supplier;
// CraftBukkit start
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end
import org.spigotmc.CustomTimingsHandler; // Spigot

public abstract class BlockEntity {

    public CustomTimingsHandler tickTimer = org.bukkit.craftbukkit.SpigotTimings.getTileEntityTimings(this); // Spigot
    // CraftBukkit start - data containers
    private static final CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new CraftPersistentDataTypeRegistry();
    public CraftPersistentDataContainer persistentDataContainer;
    // CraftBukkit end
    private static final Logger LOGGER = LogManager.getLogger();
    private final BlockEntityType<?> type;
    @Nullable
    public Level level;
    public BlockPos worldPosition;
    protected boolean remove;
    @Nullable
    private BlockState blockState;
    private boolean hasLoggedInvalidStateBefore;

    public BlockEntity(BlockEntityType<?> tileentitytypes) {
        this.worldPosition = BlockPos.ZERO;
        this.type = tileentitytypes;
    }

    @Nullable
    public Level getLevel() {
        return this.level;
    }

    public void setLevelAndPosition(Level world, BlockPos blockposition) {
        this.level = world;
        this.worldPosition = blockposition.immutable();
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        this.worldPosition = new BlockPos(nbttagcompound.getInt("x"), nbttagcompound.getInt("y"), nbttagcompound.getInt("z"));
        // CraftBukkit start - read container
        this.persistentDataContainer = new CraftPersistentDataContainer(DATA_TYPE_REGISTRY);

        CompoundTag persistentDataTag = nbttagcompound.getCompound("PublicBukkitValues");
        if (persistentDataTag != null) {
            this.persistentDataContainer.putAll(persistentDataTag);
        }
        // CraftBukkit end
    }

    public CompoundTag save(CompoundTag nbttagcompound) {
        return this.saveMetadata(nbttagcompound);
    }

    private CompoundTag saveMetadata(CompoundTag nbttagcompound) {
        ResourceLocation minecraftkey = BlockEntityType.getKey(this.getType());

        if (minecraftkey == null) {
            throw new RuntimeException(this.getClass() + " is missing a mapping! This is a bug!");
        } else {
            nbttagcompound.putString("id", minecraftkey.toString());
            nbttagcompound.putInt("x", this.worldPosition.getX());
            nbttagcompound.putInt("y", this.worldPosition.getY());
            nbttagcompound.putInt("z", this.worldPosition.getZ());
            // CraftBukkit start - store container
            if (this.persistentDataContainer != null && !this.persistentDataContainer.isEmpty()) {
                nbttagcompound.put("PublicBukkitValues", this.persistentDataContainer.toTagCompound());
            }
            // CraftBukkit end
            return nbttagcompound;
        }
    }

    @Nullable
    public static BlockEntity create(BlockState iblockdata, CompoundTag nbttagcompound) {
        String s = nbttagcompound.getString("id");

        return (BlockEntity) Registry.BLOCK_ENTITY_TYPE.getOptional(new ResourceLocation(s)).map((tileentitytypes) -> {
            try {
                return tileentitytypes.create();
            } catch (Throwable throwable) {
                BlockEntity.LOGGER.error("Failed to create block entity {}", s, throwable);
                return null;
            }
        }).map((tileentity) -> {
            try {
                tileentity.load(iblockdata, nbttagcompound);
                return tileentity;
            } catch (Throwable throwable) {
                BlockEntity.LOGGER.error("Failed to load data for block entity {}", s, throwable);
                return null;
            }
        }).orElseGet(() -> {
            BlockEntity.LOGGER.warn("Skipping BlockEntity with id {}", s);
            return null;
        });
    }

    public void setChanged() {
        if (this.level != null) {
            this.blockState = this.level.getType(this.worldPosition);
            this.level.blockEntityChanged(this.worldPosition, this);
            if (!this.blockState.isAir()) {
                this.level.updateNeighbourForOutputSignal(this.worldPosition, this.blockState.getBlock());
            }
        }

    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    public BlockState getBlock() {
        if (this.blockState == null) {
            this.blockState = this.level.getType(this.worldPosition);
        }

        return this.blockState;
    }

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag() {
        return this.saveMetadata(new CompoundTag());
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public boolean triggerEvent(int i, int j) {
        return false;
    }

    public void clearCache() {
        this.blockState = null;
    }

    public void fillCrashReportCategory(CrashReportCategory crashreportsystemdetails) {
        crashreportsystemdetails.setDetail("Name", () -> {
            return Registry.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
        });
        if (this.level != null) {
            CrashReportCategory.populateBlockDetails(crashreportsystemdetails, this.worldPosition, this.getBlock());
            CrashReportCategory.populateBlockDetails(crashreportsystemdetails, this.worldPosition, this.level.getType(this.worldPosition));
        }
    }

    public void setPosition(BlockPos blockposition) {
        this.worldPosition = blockposition.immutable();
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public void rotate(Rotation enumblockrotation) {}

    public void mirror(Mirror enumblockmirror) {}

    public BlockEntityType<?> getType() {
        return this.type;
    }

    public void logInvalidState() {
        if (!this.hasLoggedInvalidStateBefore) {
            this.hasLoggedInvalidStateBefore = true;
            BlockEntity.LOGGER.warn("Block entity invalid: {} @ {}", new Supplier[]{() -> {
                        return Registry.BLOCK_ENTITY_TYPE.getKey(this.getType());
                    }, this::getBlockPos});
        }
    }

    // CraftBukkit start - add method
    public InventoryHolder getOwner() {
        if (level == null) return null;
        // Spigot start
        org.bukkit.block.Block block = level.getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (block == null) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING, "No block for owner at %s %d %d %d", new Object[]{level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()});
            return null;
        }
        // Spigot end
        org.bukkit.block.BlockState state = block.getState();
        if (state instanceof InventoryHolder) return (InventoryHolder) state;
        return null;
    }
    // CraftBukkit end
}
