package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BeehiveBlockEntity extends BlockEntity implements TickableBlockEntity {

    private final List<BeehiveBlockEntity.BeeData> stored = Lists.newArrayList();
    @Nullable
    public BlockPos savedFlowerPos = null;
    public int maxBees = 3; // CraftBukkit - allow setting max amount of bees a hive can hold

    public BeehiveBlockEntity() {
        super(BlockEntityType.BEEHIVE);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive((Player) null, this.level.getType(this.getBlockPos()), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            Iterator iterator = BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1)).iterator();

            BlockPos blockposition;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                blockposition = (BlockPos) iterator.next();
            } while (!(this.level.getType(blockposition).getBlock() instanceof FireBlock));

            return true;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == this.maxBees; // CraftBukkit
    }

    public void emptyAllLivingFromHive(@Nullable Player entityhuman, BlockState iblockdata, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus) {
        List<Entity> list = this.releaseBees(iblockdata, tileentitybeehive_releasestatus);

        if (entityhuman != null) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    if (entityhuman.position().distanceToSqr(entity.position()) <= 16.0D) {
                        if (!this.isSedated()) {
                            entitybee.setGoalTarget(entityhuman, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit
                        } else {
                            entitybee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    private List<Entity> releaseBees(BlockState iblockdata, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return releaseBees(iblockdata, tileentitybeehive_releasestatus, false);
    }

    public List<Entity> releaseBees(BlockState iblockdata, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus, boolean force) {
        List<Entity> list = Lists.newArrayList();

        this.stored.removeIf((tileentitybeehive_hivebee) -> {
            return this.releaseBee(iblockdata, tileentitybeehive_hivebee, list, tileentitybeehive_releasestatus, force);
            // CraftBukkit end
        });
        return list;
    }

    public void addOccupant(Entity entity, boolean flag) {
        this.addOccupantWithPresetTicks(entity, flag, 0);
    }

    public int getOccupantCount() {
        return this.stored.size();
    }

    public static int getHoneyLevel(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    public boolean isSedated() {
        return CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    protected void sendDebugPackets() {
        DebugPackets.sendHiveInfo(this);
    }

    public void addOccupantWithPresetTicks(Entity entity, boolean flag, int i) {
        if (this.stored.size() < this.maxBees) { // CraftBukkit
            // CraftBukkit start
            if (this.level != null) {
                org.bukkit.event.entity.EntityEnterBlockEvent event = new org.bukkit.event.entity.EntityEnterBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, getBlockPos()));
                org.bukkit.Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    if (entity instanceof Bee) {
                        ((Bee) entity).setStayOutOfHiveCountdown(400);
                    }
                    return;
                }
            }
            // CraftBukkit end
            entity.stopRiding();
            entity.ejectPassengers();
            CompoundTag nbttagcompound = new CompoundTag();

            entity.save(nbttagcompound);
            this.stored.add(new BeehiveBlockEntity.BeeData(nbttagcompound, i, flag ? 2400 : 600));
            if (this.level != null) {
                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    if (entitybee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                        this.savedFlowerPos = entitybee.getSavedFlowerPos();
                    }
                }

                BlockPos blockposition = this.getBlockPos();

                this.level.playSound((Player) null, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            entity.remove();
        }
    }

    private boolean releaseBee(BlockState iblockdata, BeehiveBlockEntity.BeeData tileentitybeehive_hivebee, @Nullable List<Entity> list, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return releaseBee(iblockdata, tileentitybeehive_hivebee, list, tileentitybeehive_releasestatus, false);
    }

    private boolean releaseBee(BlockState iblockdata, BeehiveBlockEntity.BeeData tileentitybeehive_hivebee, @Nullable List<Entity> list, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus, boolean force) {
        if (!force && (this.level.isNight() || this.level.isRaining()) && tileentitybeehive_releasestatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
            // CraftBukkit end
            return false;
        } else {
            BlockPos blockposition = this.getBlockPos();
            CompoundTag nbttagcompound = tileentitybeehive_hivebee.entityData;

            nbttagcompound.remove("Passengers");
            nbttagcompound.remove("Leash");
            nbttagcompound.remove("UUID");
            Direction enumdirection = (Direction) iblockdata.getValue(BeehiveBlock.FACING);
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            boolean flag = !this.level.getType(blockposition1).getCollisionShape(this.level, blockposition1).isEmpty();

            if (flag && tileentitybeehive_releasestatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = EntityType.loadEntityRecursive(nbttagcompound, this.level, (entity1) -> {
                    return entity1;
                });

                if (entity != null) {
                    if (!entity.getType().is((Tag) EntityTypeTags.BEEHIVE_INHABITORS)) {
                        return false;
                    } else {
                        if (!this.level.addEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BEEHIVE)) return false; // CraftBukkit - SpawnReason, moved from below
                        if (entity instanceof Bee) {
                            Bee entitybee = (Bee) entity;

                            if (this.hasSavedFlowerPos() && !entitybee.hasSavedFlowerPos() && this.level.random.nextFloat() < 0.9F) {
                                entitybee.setSavedFlowerPos(this.savedFlowerPos);
                            }

                            if (tileentitybeehive_releasestatus == BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                                entitybee.dropOffNectar();
                                if (iblockdata.getBlock().is((Tag) BlockTags.BEEHIVES)) {
                                    int i = getHoneyLevel(iblockdata);

                                    if (i < 5) {
                                        int j = this.level.random.nextInt(100) == 0 ? 2 : 1;

                                        if (i + j > 5) {
                                            --j;
                                        }

                                        this.level.setTypeUpdate(this.getBlockPos(), (BlockState) iblockdata.setValue(BeehiveBlock.HONEY_LEVEL, i + j));
                                    }
                                }
                            }

                            this.setBeeReleaseData(tileentitybeehive_hivebee.ticksInHive, entitybee);
                            if (list != null) {
                                list.add(entitybee);
                            }

                            float f = entity.getBbWidth();
                            double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                            double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                            double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                            double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                            entity.moveTo(d1, d2, d3, entity.yRot, entity.xRot);
                        }

                        this.level.playSound((Player) null, blockposition, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return true; // return this.world.addEntity(entity); // CraftBukkit - moved up
                    }
                } else {
                    return false;
                }
            }
        }
    }

    private void setBeeReleaseData(int i, Bee entitybee) {
        int j = entitybee.getAge();

        if (j < 0) {
            entitybee.setAge(Math.min(0, j + i));
        } else if (j > 0) {
            entitybee.setAge(Math.max(0, j - i));
        }

        entitybee.setInLoveTime(Math.max(0, entitybee.getInLoveTime() - i));
        entitybee.resetTicksWithoutNectarSinceExitingHive();
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private void tickOccupants() {
        Iterator<BeehiveBlockEntity.BeeData> iterator = this.stored.iterator();

        BeehiveBlockEntity.BeeData tileentitybeehive_hivebee;

        for (BlockState iblockdata = this.getBlock(); iterator.hasNext(); tileentitybeehive_hivebee.ticksInHive++) {
            tileentitybeehive_hivebee = (BeehiveBlockEntity.BeeData) iterator.next();
            if (tileentitybeehive_hivebee.ticksInHive > tileentitybeehive_hivebee.minOccupationTicks) {
                BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus = tileentitybeehive_hivebee.entityData.getBoolean("HasNectar") ? BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED : BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED;

                if (this.releaseBee(iblockdata, tileentitybeehive_hivebee, (List) null, tileentitybeehive_releasestatus)) {
                    iterator.remove();
                }
                // CraftBukkit start
                else {
                    tileentitybeehive_hivebee.ticksInHive = tileentitybeehive_hivebee.minOccupationTicks / 2; // Not strictly Vanilla behaviour in cases where bees cannot spawn but still reasonable
                }
                // CraftBukkit end
            }
        }

    }

    @Override
    public void tick() {
        if (!this.level.isClientSide) {
            this.tickOccupants();
            BlockPos blockposition = this.getBlockPos();

            if (this.stored.size() > 0 && this.level.getRandom().nextDouble() < 0.005D) {
                double d0 = (double) blockposition.getX() + 0.5D;
                double d1 = (double) blockposition.getY();
                double d2 = (double) blockposition.getZ() + 0.5D;

                this.level.playSound((Player) null, d0, d1, d2, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            this.sendDebugPackets();
        }
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.stored.clear();
        ListTag nbttaglist = nbttagcompound.getList("Bees", 10);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);
            BeehiveBlockEntity.BeeData tileentitybeehive_hivebee = new BeehiveBlockEntity.BeeData(nbttagcompound1.getCompound("EntityData"), nbttagcompound1.getInt("TicksInHive"), nbttagcompound1.getInt("MinOccupationTicks"));

            this.stored.add(tileentitybeehive_hivebee);
        }

        this.savedFlowerPos = null;
        if (nbttagcompound.contains("FlowerPos")) {
            this.savedFlowerPos = NbtUtils.readBlockPos(nbttagcompound.getCompound("FlowerPos"));
        }

        // CraftBukkit start
        if (nbttagcompound.contains("Bukkit.MaxEntities")) {
            this.maxBees = nbttagcompound.getInt("Bukkit.MaxEntities");
        }
        // CraftBukkit end
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        nbttagcompound.put("Bees", this.writeBees());
        if (this.hasSavedFlowerPos()) {
            nbttagcompound.put("FlowerPos", NbtUtils.writeBlockPos(this.savedFlowerPos));
        }
        nbttagcompound.putInt("Bukkit.MaxEntities", this.maxBees); // CraftBukkit

        return nbttagcompound;
    }

    public ListTag writeBees() {
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.stored.iterator();

        while (iterator.hasNext()) {
            BeehiveBlockEntity.BeeData tileentitybeehive_hivebee = (BeehiveBlockEntity.BeeData) iterator.next();

            tileentitybeehive_hivebee.entityData.remove("UUID");
            CompoundTag nbttagcompound = new CompoundTag();

            nbttagcompound.put("EntityData", tileentitybeehive_hivebee.entityData);
            nbttagcompound.putInt("TicksInHive", tileentitybeehive_hivebee.ticksInHive);
            nbttagcompound.putInt("MinOccupationTicks", tileentitybeehive_hivebee.minOccupationTicks);
            nbttaglist.add(nbttagcompound);
        }

        return nbttaglist;
    }

    static class BeeData {

        private final CompoundTag entityData;
        private int ticksInHive;
        private final int minOccupationTicks;

        private BeeData(CompoundTag nbttagcompound, int i, int j) {
            nbttagcompound.remove("UUID");
            this.entityData = nbttagcompound;
            this.ticksInHive = i;
            this.minOccupationTicks = j;
        }
    }

    public static enum BeeReleaseStatus {

        HONEY_DELIVERED, BEE_RELEASED, EMERGENCY;

        private BeeReleaseStatus() {}
    }
}
