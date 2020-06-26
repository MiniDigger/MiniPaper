package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class ConduitBlockEntity extends BlockEntity implements TickableBlockEntity {

    private static final Block[] VALID_BLOCKS = new Block[]{Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE};
    public int tickCount;
    private float activeRotation;
    private boolean isActive;
    private boolean isHunting;
    private final List<BlockPos> effectBlocks;
    @Nullable
    private LivingEntity destroyTarget;
    @Nullable
    private UUID destroyTargetUUID;
    private long nextAmbientSoundActivation;

    public ConduitBlockEntity() {
        this(BlockEntityType.CONDUIT);
    }

    public ConduitBlockEntity(BlockEntityType<?> tileentitytypes) {
        super(tileentitytypes);
        this.effectBlocks = Lists.newArrayList();
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        if (nbttagcompound.hasUUID("Target")) {
            this.destroyTargetUUID = nbttagcompound.getUUID("Target");
        } else {
            this.destroyTargetUUID = null;
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (this.destroyTarget != null) {
            nbttagcompound.putUUID("Target", this.destroyTarget.getUUID());
        }

        return nbttagcompound;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 5, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    @Override
    public void tick() {
        ++this.tickCount;
        long i = this.level.getGameTime();

        if (i % 40L == 0L) {
            this.setActive(this.updateShape());
            if (!this.level.isClientSide && this.isActive()) {
                this.applyEffects();
                this.updateDestroyTarget();
            }
        }

        if (i % 80L == 0L && this.isActive()) {
            this.playSound(SoundEvents.CONDUIT_AMBIENT);
        }

        if (i > this.nextAmbientSoundActivation && this.isActive()) {
            this.nextAmbientSoundActivation = i + 60L + (long) this.level.getRandom().nextInt(40);
            this.playSound(SoundEvents.CONDUIT_AMBIENT_SHORT);
        }

        if (this.level.isClientSide) {
            this.updateClientTarget();
            this.animationTick();
            if (this.isActive()) {
                ++this.activeRotation;
            }
        }

    }

    private boolean updateShape() {
        this.effectBlocks.clear();

        int i;
        int j;
        int k;

        for (i = -1; i <= 1; ++i) {
            for (j = -1; j <= 1; ++j) {
                for (k = -1; k <= 1; ++k) {
                    BlockPos blockposition = this.worldPosition.offset(i, j, k);

                    if (!this.level.isWaterAt(blockposition)) {
                        return false;
                    }
                }
            }
        }

        for (i = -2; i <= 2; ++i) {
            for (j = -2; j <= 2; ++j) {
                for (k = -2; k <= 2; ++k) {
                    int l = Math.abs(i);
                    int i1 = Math.abs(j);
                    int j1 = Math.abs(k);

                    if ((l > 1 || i1 > 1 || j1 > 1) && (i == 0 && (i1 == 2 || j1 == 2) || j == 0 && (l == 2 || j1 == 2) || k == 0 && (l == 2 || i1 == 2))) {
                        BlockPos blockposition1 = this.worldPosition.offset(i, j, k);
                        BlockState iblockdata = this.level.getType(blockposition1);
                        Block[] ablock = ConduitBlockEntity.VALID_BLOCKS;
                        int k1 = ablock.length;

                        for (int l1 = 0; l1 < k1; ++l1) {
                            Block block = ablock[l1];

                            if (iblockdata.is(block)) {
                                this.effectBlocks.add(blockposition1);
                            }
                        }
                    }
                }
            }
        }

        this.setHunting(this.effectBlocks.size() >= 42);
        return this.effectBlocks.size() >= 16;
    }

    private void applyEffects() {
        int i = this.effectBlocks.size();
        int j = i / 7 * 16;
        int k = this.worldPosition.getX();
        int l = this.worldPosition.getY();
        int i1 = this.worldPosition.getZ();
        AABB axisalignedbb = (new AABB((double) k, (double) l, (double) i1, (double) (k + 1), (double) (l + 1), (double) (i1 + 1))).inflate((double) j).expandTowards(0.0D, (double) this.level.getMaxBuildHeight(), 0.0D);
        List<Player> list = this.level.getEntitiesOfClass(Player.class, axisalignedbb);

        if (!list.isEmpty()) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Player entityhuman = (Player) iterator.next();

                if (this.worldPosition.closerThan((Vec3i) entityhuman.blockPosition(), (double) j) && entityhuman.isInWaterOrRain()) {
                    entityhuman.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONDUIT); // CraftBukkit
                }
            }

        }
    }

    private void updateDestroyTarget() {
        LivingEntity entityliving = this.destroyTarget;
        int i = this.effectBlocks.size();

        if (i < 42) {
            this.destroyTarget = null;
        } else if (this.destroyTarget == null && this.destroyTargetUUID != null) {
            this.destroyTarget = this.findDestroyTarget();
            this.destroyTargetUUID = null;
        } else if (this.destroyTarget == null) {
            List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, this.getDestroyRangeAABB(), (java.util.function.Predicate<LivingEntity>) (entityliving1) -> { // CraftBukkit - decompile error
                return entityliving1 instanceof Enemy && entityliving1.isInWaterOrRain();
            });

            if (!list.isEmpty()) {
                this.destroyTarget = (LivingEntity) list.get(this.level.random.nextInt(list.size()));
            }
        } else if (!this.destroyTarget.isAlive() || !this.worldPosition.closerThan((Vec3i) this.destroyTarget.blockPosition(), 8.0D)) {
            this.destroyTarget = null;
        }

        if (this.destroyTarget != null) {
            // CraftBukkit start
            CraftEventFactory.blockDamage = CraftBlock.at(this.level, this.worldPosition);
            if (this.destroyTarget.hurt(DamageSource.MAGIC, 4.0F)) {
                this.level.playSound((Player) null, this.destroyTarget.getX(), this.destroyTarget.getY(), this.destroyTarget.getZ(), SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            CraftEventFactory.blockDamage = null;
            // CraftBukkit end
        }

        if (entityliving != this.destroyTarget) {
            BlockState iblockdata = this.getBlock();

            this.level.notify(this.worldPosition, iblockdata, iblockdata, 2);
        }

    }

    private void updateClientTarget() {
        if (this.destroyTargetUUID == null) {
            this.destroyTarget = null;
        } else if (this.destroyTarget == null || !this.destroyTarget.getUUID().equals(this.destroyTargetUUID)) {
            this.destroyTarget = this.findDestroyTarget();
            if (this.destroyTarget == null) {
                this.destroyTargetUUID = null;
            }
        }

    }

    private AABB getDestroyRangeAABB() {
        int i = this.worldPosition.getX();
        int j = this.worldPosition.getY();
        int k = this.worldPosition.getZ();

        return (new AABB((double) i, (double) j, (double) k, (double) (i + 1), (double) (j + 1), (double) (k + 1))).inflate(8.0D);
    }

    @Nullable
    private LivingEntity findDestroyTarget() {
        List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, this.getDestroyRangeAABB(), (java.util.function.Predicate<LivingEntity>) (entityliving) -> { // CraftBukkit - decompile error
            return entityliving.getUUID().equals(this.destroyTargetUUID);
        });

        return list.size() == 1 ? (LivingEntity) list.get(0) : null;
    }

    private void animationTick() {
        Random random = this.level.random;
        double d0 = (double) (Mth.sin((float) (this.tickCount + 35) * 0.1F) / 2.0F + 0.5F);

        d0 = (d0 * d0 + d0) * 0.30000001192092896D;
        Vec3 vec3d = new Vec3((double) this.worldPosition.getX() + 0.5D, (double) this.worldPosition.getY() + 1.5D + d0, (double) this.worldPosition.getZ() + 0.5D);
        Iterator iterator = this.effectBlocks.iterator();

        float f;
        float f1;

        while (iterator.hasNext()) {
            BlockPos blockposition = (BlockPos) iterator.next();

            if (random.nextInt(50) == 0) {
                f = -0.5F + random.nextFloat();
                f1 = -2.0F + random.nextFloat();
                float f2 = -0.5F + random.nextFloat();
                BlockPos blockposition1 = blockposition.subtract(this.worldPosition);
                Vec3 vec3d1 = (new Vec3((double) f, (double) f1, (double) f2)).add((double) blockposition1.getX(), (double) blockposition1.getY(), (double) blockposition1.getZ());

                this.level.addParticle(ParticleTypes.NAUTILUS, vec3d.x, vec3d.y, vec3d.z, vec3d1.x, vec3d1.y, vec3d1.z);
            }
        }

        if (this.destroyTarget != null) {
            Vec3 vec3d2 = new Vec3(this.destroyTarget.getX(), this.destroyTarget.getEyeY(), this.destroyTarget.getZ());
            float f3 = (-0.5F + random.nextFloat()) * (3.0F + this.destroyTarget.getBbWidth());

            f = -1.0F + random.nextFloat() * this.destroyTarget.getBbHeight();
            f1 = (-0.5F + random.nextFloat()) * (3.0F + this.destroyTarget.getBbWidth());
            Vec3 vec3d3 = new Vec3((double) f3, (double) f, (double) f1);

            this.level.addParticle(ParticleTypes.NAUTILUS, vec3d2.x, vec3d2.y, vec3d2.z, vec3d3.x, vec3d3.y, vec3d3.z);
        }

    }

    public boolean isActive() {
        return this.isActive;
    }

    private void setActive(boolean flag) {
        if (flag != this.isActive) {
            this.playSound(flag ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE);
        }

        this.isActive = flag;
    }

    private void setHunting(boolean flag) {
        this.isHunting = flag;
    }

    public void playSound(SoundEvent soundeffect) {
        this.level.playSound((Player) null, this.worldPosition, soundeffect, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
