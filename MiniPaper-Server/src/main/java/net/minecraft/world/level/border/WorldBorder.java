package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.DynamicLike;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldBorder {

    private final List<BorderChangeListener> listeners = Lists.newArrayList();
    private double damagePerBlock = 0.2D;
    private double damageSafeZone = 5.0D;
    private int warningTime = 15;
    private int warningBlocks = 5;
    private double centerX;
    private double centerZ;
    private int absoluteMaxSize = 29999984;
    private WorldBorder.BorderExtent extent = new WorldBorder.StaticBorderExtent(6.0E7D);
    public static final WorldBorder.Settings DEFAULT_SETTINGS = new WorldBorder.Settings(0.0D, 0.0D, 0.2D, 5.0D, 5, 15, 6.0E7D, 0L, 0.0D);
    public ServerLevel world; // CraftBukkit

    public WorldBorder() {}

    public boolean isWithinBounds(BlockPos blockposition) {
        return (double) (blockposition.getX() + 1) > this.getMinX() && (double) blockposition.getX() < this.getMaxX() && (double) (blockposition.getZ() + 1) > this.getMinZ() && (double) blockposition.getZ() < this.getMaxZ();
    }

    public boolean isWithinBounds(ChunkPos chunkcoordintpair) {
        return (double) chunkcoordintpair.getMaxBlockX() > this.getMinX() && (double) chunkcoordintpair.getMinBlockX() < this.getMaxX() && (double) chunkcoordintpair.getMaxBlockZ() > this.getMinZ() && (double) chunkcoordintpair.getMinBlockZ() < this.getMaxZ();
    }

    public boolean isWithinBounds(AABB axisalignedbb) {
        return axisalignedbb.maxX > this.getMinX() && axisalignedbb.minX < this.getMaxX() && axisalignedbb.maxZ > this.getMinZ() && axisalignedbb.minZ < this.getMaxZ();
    }

    public double getDistanceToBorder(Entity entity) {
        return this.getDistanceToBorder(entity.getX(), entity.getZ());
    }

    public VoxelShape getCollisionShape() {
        return this.extent.getCollisionShape();
    }

    public double getDistanceToBorder(double d0, double d1) {
        double d2 = d1 - this.getMinZ();
        double d3 = this.getMaxZ() - d1;
        double d4 = d0 - this.getMinX();
        double d5 = this.getMaxX() - d0;
        double d6 = Math.min(d4, d5);

        d6 = Math.min(d6, d2);
        return Math.min(d6, d3);
    }

    public double getMinX() {
        return this.extent.getMinX();
    }

    public double getMinZ() {
        return this.extent.getMinZ();
    }

    public double getMaxX() {
        return this.extent.getMaxX();
    }

    public double getMaxZ() {
        return this.extent.getMaxZ();
    }

    public double getCenterX() {
        return this.centerX;
    }

    public double getCenterZ() {
        return this.centerZ;
    }

    public void setCenter(double d0, double d1) {
        this.centerX = d0;
        this.centerZ = d1;
        this.extent.onCenterChange();
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderCenterSet(this, d0, d1);
        }

    }

    public double getSize() {
        return this.extent.getSize();
    }

    public long getLerpRemainingTime() {
        return this.extent.getLerpRemainingTime();
    }

    public double getLerpTarget() {
        return this.extent.getLerpTarget();
    }

    public void setSize(double d0) {
        this.extent = new WorldBorder.StaticBorderExtent(d0);
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSizeSet(this, d0);
        }

    }

    public void lerpSizeBetween(double d0, double d1, long i) {
        this.extent = (WorldBorder.BorderExtent) (d0 == d1 ? new WorldBorder.StaticBorderExtent(d1) : new WorldBorder.MovingBorderExtent(d0, d1, i));
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSizeLerping(this, d0, d1, i);
        }

    }

    protected List<BorderChangeListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(BorderChangeListener iworldborderlistener) {
        if (listeners.contains(iworldborderlistener)) return; // CraftBukkit
        this.listeners.add(iworldborderlistener);
    }

    public void setAbsoluteMaxSize(int i) {
        this.absoluteMaxSize = i;
        this.extent.onAbsoluteMaxSizeChange();
    }

    public int getAbsoluteMaxSize() {
        return this.absoluteMaxSize;
    }

    public double getDamageSafeZone() {
        return this.damageSafeZone;
    }

    public void setDamageSafeZone(double d0) {
        this.damageSafeZone = d0;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetDamageSafeZOne(this, d0);
        }

    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double d0) {
        this.damagePerBlock = d0;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetDamagePerBlock(this, d0);
        }

    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int i) {
        this.warningTime = i;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetWarningTime(this, i);
        }

    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }

    public void setWarningBlocks(int i) {
        this.warningBlocks = i;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetWarningBlocks(this, i);
        }

    }

    public void tick() {
        this.extent = this.extent.update();
    }

    public WorldBorder.Settings createSettings() {
        return new WorldBorder.Settings(this);
    }

    public void applySettings(WorldBorder.Settings worldborder_c) {
        this.setCenter(worldborder_c.getCenterX(), worldborder_c.getCenterZ());
        this.setDamagePerBlock(worldborder_c.getDamagePerBlock());
        this.setDamageSafeZone(worldborder_c.getSafeZone());
        this.setWarningBlocks(worldborder_c.getWarningBlocks());
        this.setWarningTime(worldborder_c.getWarningTime());
        if (worldborder_c.getSizeLerpTime() > 0L) {
            this.lerpSizeBetween(worldborder_c.getSize(), worldborder_c.getSizeLerpTarget(), worldborder_c.getSizeLerpTime());
        } else {
            this.setSize(worldborder_c.getSize());
        }

    }

    public static class Settings {

        private final double centerX;
        private final double centerZ;
        private final double damagePerBlock;
        private final double safeZone;
        private final int warningBlocks;
        private final int warningTime;
        private final double size;
        private final long sizeLerpTime;
        private final double sizeLerpTarget;

        private Settings(double d0, double d1, double d2, double d3, int i, int j, double d4, long k, double d5) {
            this.centerX = d0;
            this.centerZ = d1;
            this.damagePerBlock = d2;
            this.safeZone = d3;
            this.warningBlocks = i;
            this.warningTime = j;
            this.size = d4;
            this.sizeLerpTime = k;
            this.sizeLerpTarget = d5;
        }

        private Settings(WorldBorder worldborder) {
            this.centerX = worldborder.getCenterX();
            this.centerZ = worldborder.getCenterZ();
            this.damagePerBlock = worldborder.getDamagePerBlock();
            this.safeZone = worldborder.getDamageSafeZone();
            this.warningBlocks = worldborder.getWarningBlocks();
            this.warningTime = worldborder.getWarningTime();
            this.size = worldborder.getSize();
            this.sizeLerpTime = worldborder.getLerpRemainingTime();
            this.sizeLerpTarget = worldborder.getLerpTarget();
        }

        public double getCenterX() {
            return this.centerX;
        }

        public double getCenterZ() {
            return this.centerZ;
        }

        public double getDamagePerBlock() {
            return this.damagePerBlock;
        }

        public double getSafeZone() {
            return this.safeZone;
        }

        public int getWarningBlocks() {
            return this.warningBlocks;
        }

        public int getWarningTime() {
            return this.warningTime;
        }

        public double getSize() {
            return this.size;
        }

        public long getSizeLerpTime() {
            return this.sizeLerpTime;
        }

        public double getSizeLerpTarget() {
            return this.sizeLerpTarget;
        }

        public static WorldBorder.Settings read(DynamicLike<?> dynamiclike, WorldBorder.Settings worldborder_c) {
            double d0 = dynamiclike.get("BorderCenterX").asDouble(worldborder_c.centerX);
            double d1 = dynamiclike.get("BorderCenterZ").asDouble(worldborder_c.centerZ);
            double d2 = dynamiclike.get("BorderSize").asDouble(worldborder_c.size);
            long i = dynamiclike.get("BorderSizeLerpTime").asLong(worldborder_c.sizeLerpTime);
            double d3 = dynamiclike.get("BorderSizeLerpTarget").asDouble(worldborder_c.sizeLerpTarget);
            double d4 = dynamiclike.get("BorderSafeZone").asDouble(worldborder_c.safeZone);
            double d5 = dynamiclike.get("BorderDamagePerBlock").asDouble(worldborder_c.damagePerBlock);
            int j = dynamiclike.get("BorderWarningBlocks").asInt(worldborder_c.warningBlocks);
            int k = dynamiclike.get("BorderWarningTime").asInt(worldborder_c.warningTime);

            return new WorldBorder.Settings(d0, d1, d5, d4, j, k, d2, i, d3);
        }

        public void write(CompoundTag nbttagcompound) {
            nbttagcompound.putDouble("BorderCenterX", this.centerX);
            nbttagcompound.putDouble("BorderCenterZ", this.centerZ);
            nbttagcompound.putDouble("BorderSize", this.size);
            nbttagcompound.putLong("BorderSizeLerpTime", this.sizeLerpTime);
            nbttagcompound.putDouble("BorderSafeZone", this.safeZone);
            nbttagcompound.putDouble("BorderDamagePerBlock", this.damagePerBlock);
            nbttagcompound.putDouble("BorderSizeLerpTarget", this.sizeLerpTarget);
            nbttagcompound.putDouble("BorderWarningBlocks", (double) this.warningBlocks);
            nbttagcompound.putDouble("BorderWarningTime", (double) this.warningTime);
        }
    }

    class StaticBorderExtent implements WorldBorder.BorderExtent {

        private final double size;
        private double minX;
        private double minZ;
        private double maxX;
        private double maxZ;
        private VoxelShape shape;

        public StaticBorderExtent(double d0) {
            this.size = d0;
            this.updateBox();
        }

        @Override
        public double getMinX() {
            return this.minX;
        }

        @Override
        public double getMaxX() {
            return this.maxX;
        }

        @Override
        public double getMinZ() {
            return this.minZ;
        }

        @Override
        public double getMaxZ() {
            return this.maxZ;
        }

        @Override
        public double getSize() {
            return this.size;
        }

        @Override
        public long getLerpRemainingTime() {
            return 0L;
        }

        @Override
        public double getLerpTarget() {
            return this.size;
        }

        private void updateBox() {
            this.minX = Math.max(WorldBorder.this.getCenterX() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize));
            this.minZ = Math.max(WorldBorder.this.getCenterZ() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize));
            this.maxX = Math.min(WorldBorder.this.getCenterX() + this.size / 2.0D, (double) WorldBorder.this.absoluteMaxSize);
            this.maxZ = Math.min(WorldBorder.this.getCenterZ() + this.size / 2.0D, (double) WorldBorder.this.absoluteMaxSize);
            this.shape = Shapes.join(Shapes.INFINITY, Shapes.box(Math.floor(this.getMinX()), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ()), Math.ceil(this.getMaxX()), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ())), BooleanOp.ONLY_FIRST);
        }

        @Override
        public void onAbsoluteMaxSizeChange() {
            this.updateBox();
        }

        @Override
        public void onCenterChange() {
            this.updateBox();
        }

        @Override
        public WorldBorder.BorderExtent update() {
            return this;
        }

        @Override
        public VoxelShape getCollisionShape() {
            return this.shape;
        }
    }

    class MovingBorderExtent implements WorldBorder.BorderExtent {

        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;

        private MovingBorderExtent(double d0, double d1, long i) {
            this.from = d0;
            this.to = d1;
            this.lerpDuration = (double) i;
            this.lerpBegin = Util.getMillis();
            this.lerpEnd = this.lerpBegin + i;
        }

        @Override
        public double getMinX() {
            return Math.max(WorldBorder.this.getCenterX() - this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize));
        }

        @Override
        public double getMinZ() {
            return Math.max(WorldBorder.this.getCenterZ() - this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize));
        }

        @Override
        public double getMaxX() {
            return Math.min(WorldBorder.this.getCenterX() + this.getSize() / 2.0D, (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxZ() {
            return Math.min(WorldBorder.this.getCenterZ() + this.getSize() / 2.0D, (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getSize() {
            double d0 = (double) (Util.getMillis() - this.lerpBegin) / this.lerpDuration;

            return d0 < 1.0D ? Mth.lerp(d0, this.from, this.to) : this.to;
        }

        @Override
        public long getLerpRemainingTime() {
            return this.lerpEnd - Util.getMillis();
        }

        @Override
        public double getLerpTarget() {
            return this.to;
        }

        @Override
        public void onCenterChange() {}

        @Override
        public void onAbsoluteMaxSizeChange() {}

        @Override
        public WorldBorder.BorderExtent update() {
            return (WorldBorder.BorderExtent) (this.getLerpRemainingTime() <= 0L ? WorldBorder.this.new StaticBorderExtent(this.to) : this);
        }

        @Override
        public VoxelShape getCollisionShape() {
            return Shapes.join(Shapes.INFINITY, Shapes.box(Math.floor(this.getMinX()), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ()), Math.ceil(this.getMaxX()), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ())), BooleanOp.ONLY_FIRST);
        }
    }

    interface BorderExtent {

        double getMinX();

        double getMaxX();

        double getMinZ();

        double getMaxZ();

        double getSize();

        long getLerpRemainingTime();

        double getLerpTarget();

        void onAbsoluteMaxSizeChange();

        void onCenterChange();

        WorldBorder.BorderExtent update();

        VoxelShape getCollisionShape();
    }
}
