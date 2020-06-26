package net.minecraft.world.entity.boss.enderdragon;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
// CraftBukkit end

// PAIL: Fixme
public class EnderDragon extends Mob implements Enemy {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
    private static final TargetingConditions CRYSTAL_DESTROY_TARGETING = (new TargetingConditions()).range(64.0D);
    public final double[][] positions = new double[64][3];
    public int posPointer = -1;
    public final EnderDragonPart[] subEntities;
    public final EnderDragonPart head = new EnderDragonPart(this, "head", 1.0F, 1.0F);
    private final EnderDragonPart neck = new EnderDragonPart(this, "neck", 3.0F, 3.0F);
    private final EnderDragonPart body = new EnderDragonPart(this, "body", 5.0F, 3.0F);
    private final EnderDragonPart tail1 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
    private final EnderDragonPart tail2 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
    private final EnderDragonPart tail3 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
    private final EnderDragonPart wing1 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
    private final EnderDragonPart wing2 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
    public float oFlapTime;
    public float flapTime;
    public boolean inWall;
    public int dragonDeathTime;
    public float yRotA;
    @Nullable
    public EndCrystal nearestCrystal;
    @Nullable
    private final EndDragonFight dragonFight;
    private final EnderDragonPhaseManager phaseManager;
    private int growlTime = 100;
    private int sittingDamageReceived;
    private final Node[] nodes = new Node[24];
    private final int[] nodeAdjacency = new int[24];
    private final BinaryHeap openSet = new BinaryHeap();
    private Explosion explosionSource = new Explosion(null, this, null, null, Double.NaN, Double.NaN, Double.NaN, Float.NaN, true, Explosion.BlockInteraction.DESTROY); // CraftBukkit - reusable source for CraftTNTPrimed.getSource()

    public EnderDragon(EntityType<? extends EnderDragon> entitytypes, Level world) {
        super(EntityType.ENDER_DRAGON, world);
        this.subEntities = new EnderDragonPart[]{this.head, this.neck, this.body, this.tail1, this.tail2, this.tail3, this.wing1, this.wing2};
        this.setHealth(this.getMaxHealth());
        this.noPhysics = true;
        this.noCulling = true;
        if (world instanceof ServerLevel) {
            this.dragonFight = ((ServerLevel) world).dragonFight();
        } else {
            this.dragonFight = null;
        }

        this.phaseManager = new EnderDragonPhaseManager(this);
    }

    public static AttributeSupplier.Builder m() {
        return Mob.p().a(Attributes.MAX_HEALTH, 200.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().register(EnderDragon.DATA_PHASE, EnderDragonPhase.HOVERING.getId());
    }

    public double[] getLatencyPos(int i, float f) {
        if (this.isDeadOrDying()) {
            f = 0.0F;
        }

        f = 1.0F - f;
        int j = this.posPointer - i & 63;
        int k = this.posPointer - i - 1 & 63;
        double[] adouble = new double[3];
        double d0 = this.positions[j][0];
        double d1 = Mth.wrapDegrees(this.positions[k][0] - d0);

        adouble[0] = d0 + d1 * (double) f;
        d0 = this.positions[j][1];
        d1 = this.positions[k][1] - d0;
        adouble[1] = d0 + d1 * (double) f;
        adouble[2] = Mth.lerp((double) f, this.positions[j][2], this.positions[k][2]);
        return adouble;
    }

    @Override
    public void aiStep() {
        float f;
        float f1;

        if (this.level.isClientSide) {
            this.setHealth(this.getHealth());
            if (!this.isSilent()) {
                f = Mth.cos(this.flapTime * 6.2831855F);
                f1 = Mth.cos(this.oFlapTime * 6.2831855F);
                if (f1 <= -0.3F && f >= -0.3F) {
                    this.level.playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, this.getSoundSource(), 5.0F, 0.8F + this.random.nextFloat() * 0.3F, false);
                }

                if (!this.phaseManager.getCurrentPhase().isSitting() && --this.growlTime < 0) {
                    this.level.playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, this.getSoundSource(), 2.5F, 0.8F + this.random.nextFloat() * 0.3F, false);
                    this.growlTime = 200 + this.random.nextInt(200);
                }
            }
        }

        this.oFlapTime = this.flapTime;
        if (this.isDeadOrDying()) {
            f = (this.random.nextFloat() - 0.5F) * 8.0F;
            f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level.addParticle(ParticleTypes.EXPLOSION, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        } else {
            this.checkCrystals();
            Vec3 vec3d = this.getDeltaMovement();

            f1 = 0.2F / (Mth.sqrt(getHorizontalDistanceSqr(vec3d)) * 10.0F + 1.0F);
            f1 *= (float) Math.pow(2.0D, vec3d.y);
            if (this.phaseManager.getCurrentPhase().isSitting()) {
                this.flapTime += 0.1F;
            } else if (this.inWall) {
                this.flapTime += f1 * 0.5F;
            } else {
                this.flapTime += f1;
            }

            this.yRot = Mth.wrapDegrees(this.yRot);
            if (this.isNoAi()) {
                this.flapTime = 0.5F;
            } else {
                if (this.posPointer < 0) {
                    for (int i = 0; i < this.positions.length; ++i) {
                        this.positions[i][0] = (double) this.yRot;
                        this.positions[i][1] = this.getY();
                    }
                }

                if (++this.posPointer == this.positions.length) {
                    this.posPointer = 0;
                }

                this.positions[this.posPointer][0] = (double) this.yRot;
                this.positions[this.posPointer][1] = this.getY();
                double d0;
                double d1;
                double d2;
                float f3;
                float f4;

                if (this.level.isClientSide) {
                    if (this.lerpSteps > 0) {
                        double d3 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;

                        d0 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
                        d1 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
                        d2 = Mth.wrapDegrees(this.lerpYRot - (double) this.yRot);
                        this.yRot = (float) ((double) this.yRot + d2 / (double) this.lerpSteps);
                        this.xRot = (float) ((double) this.xRot + (this.lerpXRot - (double) this.xRot) / (double) this.lerpSteps);
                        --this.lerpSteps;
                        this.setPos(d3, d0, d1);
                        this.setRot(this.yRot, this.xRot);
                    }

                    this.phaseManager.getCurrentPhase().doClientTick();
                } else {
                    DragonPhaseInstance idragoncontroller = this.phaseManager.getCurrentPhase();

                    idragoncontroller.doServerTick();
                    if (this.phaseManager.getCurrentPhase() != idragoncontroller) {
                        idragoncontroller = this.phaseManager.getCurrentPhase();
                        idragoncontroller.doServerTick();
                    }

                    Vec3 vec3d1 = idragoncontroller.getFlyTargetLocation();

                    if (vec3d1 != null && idragoncontroller.getPhase() != EnderDragonPhase.HOVERING) { // CraftBukkit - Don't move when hovering
                        d0 = vec3d1.x - this.getX();
                        d1 = vec3d1.y - this.getY();
                        d2 = vec3d1.z - this.getZ();
                        double d4 = d0 * d0 + d1 * d1 + d2 * d2;
                        float f5 = idragoncontroller.getFlySpeed();
                        double d5 = (double) Mth.sqrt(d0 * d0 + d2 * d2);

                        if (d5 > 0.0D) {
                            d1 = Mth.clamp(d1 / d5, (double) (-f5), (double) f5);
                        }

                        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d1 * 0.01D, 0.0D));
                        this.yRot = Mth.wrapDegrees(this.yRot);
                        double d6 = Mth.clamp(Mth.wrapDegrees(180.0D - Mth.atan2(d0, d2) * 57.2957763671875D - (double) this.yRot), -50.0D, 50.0D);
                        Vec3 vec3d2 = vec3d1.subtract(this.getX(), this.getY(), this.getZ()).normalize();
                        Vec3 vec3d3 = (new Vec3((double) Mth.sin(this.yRot * 0.017453292F), this.getDeltaMovement().y, (double) (-Mth.cos(this.yRot * 0.017453292F)))).normalize();

                        f3 = Math.max(((float) vec3d3.dot(vec3d2) + 0.5F) / 1.5F, 0.0F);
                        this.yRotA *= 0.8F;
                        this.yRotA = (float) ((double) this.yRotA + d6 * (double) idragoncontroller.getTurnSpeed());
                        this.yRot += this.yRotA * 0.1F;
                        f4 = (float) (2.0D / (d4 + 1.0D));
                        float f6 = 0.06F;

                        this.moveRelative(0.06F * (f3 * f4 + (1.0F - f4)), new Vec3(0.0D, 0.0D, -1.0D));
                        if (this.inWall) {
                            this.move(MoverType.SELF, this.getDeltaMovement().scale(0.800000011920929D));
                        } else {
                            this.move(MoverType.SELF, this.getDeltaMovement());
                        }

                        Vec3 vec3d4 = this.getDeltaMovement().normalize();
                        double d7 = 0.8D + 0.15D * (vec3d4.dot(vec3d3) + 1.0D) / 2.0D;

                        this.setDeltaMovement(this.getDeltaMovement().multiply(d7, 0.9100000262260437D, d7));
                    }
                }

                this.yBodyRot = this.yRot;
                Vec3[] avec3d = new Vec3[this.subEntities.length];

                for (int j = 0; j < this.subEntities.length; ++j) {
                    avec3d[j] = new Vec3(this.subEntities[j].getX(), this.subEntities[j].getY(), this.subEntities[j].getZ());
                }

                float f7 = (float) (this.getLatencyPos(5, 1.0F)[1] - this.getLatencyPos(10, 1.0F)[1]) * 10.0F * 0.017453292F;
                float f8 = Mth.cos(f7);
                float f9 = Mth.sin(f7);
                float f10 = this.yRot * 0.017453292F;
                float f11 = Mth.sin(f10);
                float f12 = Mth.cos(f10);

                this.tickPart(this.body, (double) (f11 * 0.5F), 0.0D, (double) (-f12 * 0.5F));
                this.tickPart(this.wing1, (double) (f12 * 4.5F), 2.0D, (double) (f11 * 4.5F));
                this.tickPart(this.wing2, (double) (f12 * -4.5F), 2.0D, (double) (f11 * -4.5F));
                if (!this.level.isClientSide && this.hurtTime == 0) {
                    this.knockBack(this.level.getEntities(this, this.wing1.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.knockBack(this.level.getEntities(this, this.wing2.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.hurt(this.level.getEntities(this, this.head.getBoundingBox().inflate(1.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.hurt(this.level.getEntities(this, this.neck.getBoundingBox().inflate(1.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                }

                float f13 = Mth.sin(this.yRot * 0.017453292F - this.yRotA * 0.01F);
                float f14 = Mth.cos(this.yRot * 0.017453292F - this.yRotA * 0.01F);
                float f15 = this.getHeadYOffset();

                this.tickPart(this.head, (double) (f13 * 6.5F * f8), (double) (f15 + f9 * 6.5F), (double) (-f14 * 6.5F * f8));
                this.tickPart(this.neck, (double) (f13 * 5.5F * f8), (double) (f15 + f9 * 5.5F), (double) (-f14 * 5.5F * f8));
                double[] adouble = this.getLatencyPos(5, 1.0F);

                int k;

                for (k = 0; k < 3; ++k) {
                    EnderDragonPart entitycomplexpart = null;

                    if (k == 0) {
                        entitycomplexpart = this.tail1;
                    }

                    if (k == 1) {
                        entitycomplexpart = this.tail2;
                    }

                    if (k == 2) {
                        entitycomplexpart = this.tail3;
                    }

                    double[] adouble1 = this.getLatencyPos(12 + k * 2, 1.0F);
                    float f16 = this.yRot * 0.017453292F + this.rotWrap(adouble1[0] - adouble[0]) * 0.017453292F;
                    float f17 = Mth.sin(f16);
                    float f18 = Mth.cos(f16);

                    f3 = 1.5F;
                    f4 = (float) (k + 1) * 2.0F;
                    this.tickPart(entitycomplexpart, (double) (-(f11 * 1.5F + f17 * f4) * f8), adouble1[1] - adouble[1] - (double) ((f4 + 1.5F) * f9) + 1.5D, (double) ((f12 * 1.5F + f18 * f4) * f8));
                }

                if (!this.level.isClientSide) {
                    this.inWall = this.checkWalls(this.head.getBoundingBox()) | this.checkWalls(this.neck.getBoundingBox()) | this.checkWalls(this.body.getBoundingBox());
                    if (this.dragonFight != null) {
                        this.dragonFight.updateDragon(this);
                    }
                }

                for (k = 0; k < this.subEntities.length; ++k) {
                    this.subEntities[k].xo = avec3d[k].x;
                    this.subEntities[k].yo = avec3d[k].y;
                    this.subEntities[k].zo = avec3d[k].z;
                    this.subEntities[k].xOld = avec3d[k].x;
                    this.subEntities[k].yOld = avec3d[k].y;
                    this.subEntities[k].zOld = avec3d[k].z;
                }

            }
        }
    }

    private void tickPart(EnderDragonPart entitycomplexpart, double d0, double d1, double d2) {
        entitycomplexpart.setPos(this.getX() + d0, this.getY() + d1, this.getZ() + d2);
    }

    private float getHeadYOffset() {
        if (this.phaseManager.getCurrentPhase().isSitting()) {
            return -1.0F;
        } else {
            double[] adouble = this.getLatencyPos(5, 1.0F);
            double[] adouble1 = this.getLatencyPos(0, 1.0F);

            return (float) (adouble[1] - adouble1[1]);
        }
    }

    private void checkCrystals() {
        if (this.nearestCrystal != null) {
            if (this.nearestCrystal.removed) {
                this.nearestCrystal = null;
            } else if (this.tickCount % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                // CraftBukkit start
                EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), 1.0F, EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);
                this.level.getServerOH().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    this.setHealth((float) (this.getHealth() + event.getAmount()));
                }
                // CraftBukkit end
            }
        }

        if (this.random.nextInt(10) == 0) {
            List<EndCrystal> list = this.level.getEntitiesOfClass(EndCrystal.class, this.getBoundingBox().inflate(32.0D));
            EndCrystal entityendercrystal = null;
            double d0 = Double.MAX_VALUE;
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                EndCrystal entityendercrystal1 = (EndCrystal) iterator.next();
                double d1 = entityendercrystal1.distanceToSqr(this);

                if (d1 < d0) {
                    d0 = d1;
                    entityendercrystal = entityendercrystal1;
                }
            }

            this.nearestCrystal = entityendercrystal;
        }

    }

    private void knockBack(List<Entity> list) {
        double d0 = (this.body.getBoundingBox().minX + this.body.getBoundingBox().maxX) / 2.0D;
        double d1 = (this.body.getBoundingBox().minZ + this.body.getBoundingBox().maxZ) / 2.0D;
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) {
                double d2 = entity.getX() - d0;
                double d3 = entity.getZ() - d1;
                double d4 = d2 * d2 + d3 * d3;

                entity.push(d2 / d4 * 4.0D, 0.20000000298023224D, d3 / d4 * 4.0D);
                if (!this.phaseManager.getCurrentPhase().isSitting() && ((LivingEntity) entity).getLastHurtByMobTimestamp() < entity.tickCount - 2) {
                    entity.hurt(DamageSource.mobAttack(this), 5.0F);
                    this.doEnchantDamageEffects((LivingEntity) this, entity);
                }
            }
        }

    }

    private void hurt(List<Entity> list) {
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) {
                entity.hurt(DamageSource.mobAttack(this), 10.0F);
                this.doEnchantDamageEffects((LivingEntity) this, entity);
            }
        }

    }

    private float rotWrap(double d0) {
        return (float) Mth.wrapDegrees(d0);
    }

    private boolean checkWalls(AABB axisalignedbb) {
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.floor(axisalignedbb.minY);
        int k = Mth.floor(axisalignedbb.minZ);
        int l = Mth.floor(axisalignedbb.maxX);
        int i1 = Mth.floor(axisalignedbb.maxY);
        int j1 = Mth.floor(axisalignedbb.maxZ);
        boolean flag = false;
        boolean flag1 = false;
        // CraftBukkit start - Create a list to hold all the destroyed blocks
        List<org.bukkit.block.Block> destroyedBlocks = new java.util.ArrayList<org.bukkit.block.Block>();
        // CraftBukkit end

        for (int k1 = i; k1 <= l; ++k1) {
            for (int l1 = j; l1 <= i1; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    BlockPos blockposition = new BlockPos(k1, l1, i2);
                    BlockState iblockdata = this.level.getType(blockposition);
                    Block block = iblockdata.getBlock();

                    if (!iblockdata.isAir() && iblockdata.getMaterial() != Material.FIRE) {
                        if (this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && !BlockTags.DRAGON_IMMUNE.contains(block)) {
                            // CraftBukkit start - Add blocks to list rather than destroying them
                            // flag1 = this.world.a(blockposition, false) || flag1;
                            flag1 = true;
                            destroyedBlocks.add(CraftBlock.at(level, blockposition));
                            // CraftBukkit end
                        } else {
                            flag = true;
                        }
                    }
                }
            }
        }

        // CraftBukkit start - Set off an EntityExplodeEvent for the dragon exploding all these blocks
        // SPIGOT-4882: don't fire event if nothing hit
        if (!flag1) {
            return flag;
        }

        org.bukkit.entity.Entity bukkitEntity = this.getBukkitEntity();
        EntityExplodeEvent event = new EntityExplodeEvent(bukkitEntity, bukkitEntity.getLocation(), destroyedBlocks, 0F);
        bukkitEntity.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            // This flag literally means 'Dragon hit something hard' (Obsidian, White Stone or Bedrock) and will cause the dragon to slow down.
            // We should consider adding an event extension for it, or perhaps returning true if the event is cancelled.
            return flag;
        } else if (event.getYield() == 0F) {
            // Yield zero ==> no drops
            for (org.bukkit.block.Block block : event.blockList()) {
                this.level.removeBlock(new BlockPos(block.getX(), block.getY(), block.getZ()), false);
            }
        } else {
            for (org.bukkit.block.Block block : event.blockList()) {
                org.bukkit.Material blockId = block.getType();
                if (blockId.isAir()) {
                    continue;
                }

                CraftBlock craftBlock = ((CraftBlock) block);
                BlockPos blockposition = craftBlock.getPosition();

                Block nmsBlock = craftBlock.getNMS().getBlock();
                if (nmsBlock.dropFromExplosion(explosionSource)) {
                    BlockEntity tileentity = nmsBlock.isEntityBlock() ? this.level.getBlockEntity(blockposition) : null;
                    LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.level)).withRandom(this.level.random).set(LootContextParams.BLOCK_POS, blockposition).set(LootContextParams.TOOL, ItemStack.EMPTY).set(LootContextParams.EXPLOSION_RADIUS, 1.0F / event.getYield()).setOptional(LootContextParams.BLOCK_ENTITY, tileentity);

                    craftBlock.getNMS().getDrops(loottableinfo_builder).forEach((itemstack) -> {
                        Block.popResource(level, blockposition, itemstack);
                    });
                    craftBlock.getNMS().spawnAfterBreak(level, blockposition, ItemStack.EMPTY);
                }
                nmsBlock.wasExploded(level, blockposition, explosionSource);

                this.level.removeBlock(blockposition, false);
            }
        }
        // CraftBukkit end

        if (flag1) {
            BlockPos blockposition1 = new BlockPos(i + this.random.nextInt(l - i + 1), j + this.random.nextInt(i1 - j + 1), k + this.random.nextInt(j1 - k + 1));

            this.level.levelEvent(2008, blockposition1, 0);
        }

        return flag;
    }

    public boolean hurt(EnderDragonPart entitycomplexpart, DamageSource damagesource, float f) {
        if (this.phaseManager.getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            return false;
        } else {
            f = this.phaseManager.getCurrentPhase().onHurt(damagesource, f);
            if (entitycomplexpart != this.head) {
                f = f / 4.0F + Math.min(f, 1.0F);
            }

            if (f < 0.01F) {
                return false;
            } else {
                if (damagesource.getEntity() instanceof Player || damagesource.isExplosion()) {
                    float f1 = this.getHealth();

                    this.reallyHurt(damagesource, f);
                    if (this.isDeadOrDying() && !this.phaseManager.getCurrentPhase().isSitting()) {
                        this.setHealth(1.0F);
                        this.phaseManager.setPhase(EnderDragonPhase.DYING);
                    }

                    if (this.phaseManager.getCurrentPhase().isSitting()) {
                        this.sittingDamageReceived = (int) ((float) this.sittingDamageReceived + (f1 - this.getHealth()));
                        if ((float) this.sittingDamageReceived > 0.25F * this.getMaxHealth()) {
                            this.sittingDamageReceived = 0;
                            this.phaseManager.setPhase(EnderDragonPhase.TAKEOFF);
                        }
                    }
                }

                return true;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (damagesource instanceof EntityDamageSource && ((EntityDamageSource) damagesource).isThorns()) {
            this.hurt(this.body, damagesource, f);
        }

        return false;
    }

    protected boolean reallyHurt(DamageSource damagesource, float f) {
        return super.hurt(damagesource, f);
    }

    @Override
    public void kill() {
        this.remove();
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
            this.dragonFight.setDragonKilled(this);
        }

    }

    @Override
    protected void tickDeath() {
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
        }

        ++this.dragonDeathTime;
        if (this.dragonDeathTime >= 180 && this.dragonDeathTime <= 200) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        }

        boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        short short0 = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            short0 = 12000;
        }

        if (!this.level.isClientSide) {
            if (this.dragonDeathTime > 150 && this.dragonDeathTime % 5 == 0 && flag) {
                this.dropExperience(Mth.floor((float) short0 * 0.08F));
            }

            if (this.dragonDeathTime == 1 && !this.isSilent()) {
                // CraftBukkit start - Use relative location for far away sounds
                // this.world.b(1028, this.getChunkCoordinates(), 0);
                int viewDistance = ((ServerLevel) this.level).getServerOH().getViewDistance() * 16;
                for (ServerPlayer player : (List<ServerPlayer>) MinecraftServer.getServer().getPlayerList().players) {
                    double deltaX = this.getX() - player.getX();
                    double deltaZ = this.getZ() - player.getZ();
                    double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                    if ( level.spigotConfig.dragonDeathSoundRadius > 0 && distanceSquared > level.spigotConfig.dragonDeathSoundRadius * level.spigotConfig.dragonDeathSoundRadius ) continue; // Spigot
                    if (distanceSquared > viewDistance * viewDistance) {
                        double deltaLength = Math.sqrt(distanceSquared);
                        double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                        double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                        player.connection.sendPacket(new ClientboundLevelEventPacket(1028, new BlockPos((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                    } else {
                        player.connection.sendPacket(new ClientboundLevelEventPacket(1028, new BlockPos((int) this.getX(), (int) this.getY(), (int) this.getZ()), 0, true));
                    }
                }
                // CraftBukkit end
            }
        }

        this.move(MoverType.SELF, new Vec3(0.0D, 0.10000000149011612D, 0.0D));
        this.yRot += 20.0F;
        this.yBodyRot = this.yRot;
        if (this.dragonDeathTime == 200 && !this.level.isClientSide) {
            if (flag) {
                this.dropExperience(Mth.floor((float) short0 * 0.2F));
            }

            if (this.dragonFight != null) {
                this.dragonFight.setDragonKilled(this);
            }

            this.remove();
        }

    }

    private void dropExperience(int i) {
        while (i > 0) {
            int j = ExperienceOrb.getExperienceValue(i);

            i -= j;
            this.level.addFreshEntity(new ExperienceOrb(this.level, this.getX(), this.getY(), this.getZ(), j));
        }

    }

    public int findClosestNode() {
        if (this.nodes[0] == null) {
            for (int i = 0; i < 24; ++i) {
                int j = 5;
                int k;
                int l;

                if (i < 12) {
                    k = Mth.floor(60.0F * Mth.cos(2.0F * (-3.1415927F + 0.2617994F * (float) i)));
                    l = Mth.floor(60.0F * Mth.sin(2.0F * (-3.1415927F + 0.2617994F * (float) i)));
                } else {
                    int i1;

                    if (i < 20) {
                        i1 = i - 12;
                        k = Mth.floor(40.0F * Mth.cos(2.0F * (-3.1415927F + 0.3926991F * (float) i1)));
                        l = Mth.floor(40.0F * Mth.sin(2.0F * (-3.1415927F + 0.3926991F * (float) i1)));
                        j += 10;
                    } else {
                        i1 = i - 20;
                        k = Mth.floor(20.0F * Mth.cos(2.0F * (-3.1415927F + 0.7853982F * (float) i1)));
                        l = Mth.floor(20.0F * Mth.sin(2.0F * (-3.1415927F + 0.7853982F * (float) i1)));
                    }
                }

                int j1 = Math.max(this.level.getSeaLevel() + 10, this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(k, 0, l)).getY() + j);

                this.nodes[i] = new Node(k, j1, l);
            }

            this.nodeAdjacency[0] = 6146;
            this.nodeAdjacency[1] = 8197;
            this.nodeAdjacency[2] = 8202;
            this.nodeAdjacency[3] = 16404;
            this.nodeAdjacency[4] = 32808;
            this.nodeAdjacency[5] = 32848;
            this.nodeAdjacency[6] = 65696;
            this.nodeAdjacency[7] = 131392;
            this.nodeAdjacency[8] = 131712;
            this.nodeAdjacency[9] = 263424;
            this.nodeAdjacency[10] = 526848;
            this.nodeAdjacency[11] = 525313;
            this.nodeAdjacency[12] = 1581057;
            this.nodeAdjacency[13] = 3166214;
            this.nodeAdjacency[14] = 2138120;
            this.nodeAdjacency[15] = 6373424;
            this.nodeAdjacency[16] = 4358208;
            this.nodeAdjacency[17] = 12910976;
            this.nodeAdjacency[18] = 9044480;
            this.nodeAdjacency[19] = 9706496;
            this.nodeAdjacency[20] = 15216640;
            this.nodeAdjacency[21] = 13688832;
            this.nodeAdjacency[22] = 11763712;
            this.nodeAdjacency[23] = 8257536;
        }

        return this.findClosestNode(this.getX(), this.getY(), this.getZ());
    }

    public int findClosestNode(double d0, double d1, double d2) {
        float f = 10000.0F;
        int i = 0;
        Node pathpoint = new Node(Mth.floor(d0), Mth.floor(d1), Mth.floor(d2));
        byte b0 = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            b0 = 12;
        }

        for (int j = b0; j < 24; ++j) {
            if (this.nodes[j] != null) {
                float f1 = this.nodes[j].distanceToSqr(pathpoint);

                if (f1 < f) {
                    f = f1;
                    i = j;
                }
            }
        }

        return i;
    }

    @Nullable
    public Path findPath(int i, int j, @Nullable Node pathpoint) {
        Node pathpoint1;

        for (int k = 0; k < 24; ++k) {
            pathpoint1 = this.nodes[k];
            pathpoint1.closed = false;
            pathpoint1.f = 0.0F;
            pathpoint1.g = 0.0F;
            pathpoint1.h = 0.0F;
            pathpoint1.cameFrom = null;
            pathpoint1.heapIdx = -1;
        }

        Node pathpoint2 = this.nodes[i];

        pathpoint1 = this.nodes[j];
        pathpoint2.g = 0.0F;
        pathpoint2.h = pathpoint2.distanceTo(pathpoint1);
        pathpoint2.f = pathpoint2.h;
        this.openSet.clear();
        this.openSet.insert(pathpoint2);
        Node pathpoint3 = pathpoint2;
        byte b0 = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            b0 = 12;
        }

        label70:
        while (!this.openSet.isEmpty()) {
            Node pathpoint4 = this.openSet.pop();

            if (pathpoint4.equals(pathpoint1)) {
                if (pathpoint != null) {
                    pathpoint.cameFrom = pathpoint1;
                    pathpoint1 = pathpoint;
                }

                return this.reconstructPath(pathpoint2, pathpoint1);
            }

            if (pathpoint4.distanceTo(pathpoint1) < pathpoint3.distanceTo(pathpoint1)) {
                pathpoint3 = pathpoint4;
            }

            pathpoint4.closed = true;
            int l = 0;
            int i1 = 0;

            while (true) {
                if (i1 < 24) {
                    if (this.nodes[i1] != pathpoint4) {
                        ++i1;
                        continue;
                    }

                    l = i1;
                }

                i1 = b0;

                while (true) {
                    if (i1 >= 24) {
                        continue label70;
                    }

                    if ((this.nodeAdjacency[l] & 1 << i1) > 0) {
                        Node pathpoint5 = this.nodes[i1];

                        if (!pathpoint5.closed) {
                            float f = pathpoint4.g + pathpoint4.distanceTo(pathpoint5);

                            if (!pathpoint5.inOpenSet() || f < pathpoint5.g) {
                                pathpoint5.cameFrom = pathpoint4;
                                pathpoint5.g = f;
                                pathpoint5.h = pathpoint5.distanceTo(pathpoint1);
                                if (pathpoint5.inOpenSet()) {
                                    this.openSet.changeCost(pathpoint5, pathpoint5.g + pathpoint5.h);
                                } else {
                                    pathpoint5.f = pathpoint5.g + pathpoint5.h;
                                    this.openSet.insert(pathpoint5);
                                }
                            }
                        }
                    }

                    ++i1;
                }
            }
        }

        if (pathpoint3 == pathpoint2) {
            return null;
        } else {
            EnderDragon.LOGGER.debug("Failed to find path from {} to {}", i, j);
            if (pathpoint != null) {
                pathpoint.cameFrom = pathpoint3;
                pathpoint3 = pathpoint;
            }

            return this.reconstructPath(pathpoint2, pathpoint3);
        }
    }

    private Path reconstructPath(Node pathpoint, Node pathpoint1) {
        List<Node> list = Lists.newArrayList();
        Node pathpoint2 = pathpoint1;

        list.add(0, pathpoint1);

        while (pathpoint2.cameFrom != null) {
            pathpoint2 = pathpoint2.cameFrom;
            list.add(0, pathpoint2);
        }

        return new Path(list, new BlockPos(pathpoint1.x, pathpoint1.y, pathpoint1.z), true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("DragonPhase", this.phaseManager.getCurrentPhase().getPhase().getId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("DragonPhase")) {
            this.phaseManager.setPhase(EnderDragonPhase.getById(nbttagcompound.getInt("DragonPhase")));
        }

    }

    @Override
    public void checkDespawn() {}

    public EnderDragonPart[] getSubEntities() {
        return this.subEntities;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }

    public Vec3 getHeadLookVector(float f) {
        DragonPhaseInstance idragoncontroller = this.phaseManager.getCurrentPhase();
        EnderDragonPhase<? extends DragonPhaseInstance> dragoncontrollerphase = idragoncontroller.getPhase();
        float f1;
        Vec3 vec3d;

        if (dragoncontrollerphase != EnderDragonPhase.LANDING && dragoncontrollerphase != EnderDragonPhase.TAKEOFF) {
            if (idragoncontroller.isSitting()) {
                float f2 = this.xRot;

                f1 = 1.5F;
                this.xRot = -45.0F;
                vec3d = this.getViewVector(f);
                this.xRot = f2;
            } else {
                vec3d = this.getViewVector(f);
            }
        } else {
            BlockPos blockposition = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.END_PODIUM_LOCATION);

            f1 = Math.max(Mth.sqrt(blockposition.distSqr(this.position(), true)) / 4.0F, 1.0F);
            float f3 = 6.0F / f1;
            float f4 = this.xRot;
            float f5 = 1.5F;

            this.xRot = -f3 * 1.5F * 5.0F;
            vec3d = this.getViewVector(f);
            this.xRot = f4;
        }

        return vec3d;
    }

    public void onCrystalDestroyed(EndCrystal entityendercrystal, BlockPos blockposition, DamageSource damagesource) {
        Player entityhuman;

        if (damagesource.getEntity() instanceof Player) {
            entityhuman = (Player) damagesource.getEntity();
        } else {
            entityhuman = this.level.getNearestPlayer(EnderDragon.CRYSTAL_DESTROY_TARGETING, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
        }

        if (entityendercrystal == this.nearestCrystal) {
            this.hurt(this.head, DamageSource.explosion(entityhuman), 10.0F);
        }

        this.phaseManager.getCurrentPhase().onCrystalDestroyed(entityendercrystal, blockposition, damagesource, entityhuman);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (EnderDragon.DATA_PHASE.equals(datawatcherobject) && this.level.isClientSide) {
            this.phaseManager.setPhase(EnderDragonPhase.getById((Integer) this.getEntityData().get(EnderDragon.DATA_PHASE)));
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    public EnderDragonPhaseManager getPhaseManager() {
        return this.phaseManager;
    }

    @Nullable
    public EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public boolean addEffect(MobEffectInstance mobeffect) {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }
}
