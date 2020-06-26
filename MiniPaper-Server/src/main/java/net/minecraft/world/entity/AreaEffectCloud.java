package net.minecraft.world.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
// CraftBukkit end

public class AreaEffectCloud extends Entity {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAITING = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ParticleOptions> DATA_PARTICLE = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.PARTICLE);
    private Potion potion;
    public List<MobEffectInstance> effects;
    private final Map<Entity, Integer> victims;
    private int duration;
    public int waitTime;
    public int reapplicationDelay;
    private boolean fixedColor;
    public int durationOnUse;
    public float radiusOnUse;
    public float radiusPerTick;
    private net.minecraft.world.entity.LivingEntity owner;
    private UUID ownerUUID;

    public AreaEffectCloud(EntityType<? extends AreaEffectCloud> entitytypes, Level world) {
        super(entitytypes, world);
        this.potion = Potions.EMPTY;
        this.effects = Lists.newArrayList();
        this.victims = Maps.newHashMap();
        this.duration = 600;
        this.waitTime = 20;
        this.reapplicationDelay = 20;
        this.noPhysics = true;
        this.setRadius(3.0F);
    }

    public AreaEffectCloud(Level world, double d0, double d1, double d2) {
        this(EntityType.AREA_EFFECT_CLOUD, world);
        this.setPos(d0, d1, d2);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(AreaEffectCloud.DATA_COLOR, 0);
        this.getEntityData().register(AreaEffectCloud.DATA_RADIUS, 0.5F);
        this.getEntityData().register(AreaEffectCloud.DATA_WAITING, false);
        this.getEntityData().register(AreaEffectCloud.DATA_PARTICLE, ParticleTypes.ENTITY_EFFECT);
    }

    public void setRadius(float f) {
        if (!this.level.isClientSide) {
            this.getEntityData().set(AreaEffectCloud.DATA_RADIUS, f);
        }

    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    public float getRadius() {
        return (Float) this.getEntityData().get(AreaEffectCloud.DATA_RADIUS);
    }

    public void setPotion(Potion potionregistry) {
        this.potion = potionregistry;
        if (!this.fixedColor) {
            this.updateColor();
        }

    }

    private void updateColor() {
        if (this.potion == Potions.EMPTY && this.effects.isEmpty()) {
            this.getEntityData().set(AreaEffectCloud.DATA_COLOR, 0);
        } else {
            this.getEntityData().set(AreaEffectCloud.DATA_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, (Collection) this.effects)));
        }

    }

    public void addEffect(MobEffectInstance mobeffect) {
        this.effects.add(mobeffect);
        if (!this.fixedColor) {
            this.updateColor();
        }

    }

    // CraftBukkit start accessor methods
    public void refreshEffects() {
        if (!this.fixedColor) {
            this.getEntityData().set(AreaEffectCloud.DATA_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, (Collection) this.effects))); // PAIL: rename
        }
    }

    public String getTypeOH() {
        return ((ResourceLocation) Registry.POTION.getKey(this.potion)).toString();
    }

    public void setType(String string) {
        setPotion(Registry.POTION.get(new ResourceLocation(string)));
    }
    // CraftBukkit end

    public int getColor() {
        return (Integer) this.getEntityData().get(AreaEffectCloud.DATA_COLOR);
    }

    public void setFixedColor(int i) {
        this.fixedColor = true;
        this.getEntityData().set(AreaEffectCloud.DATA_COLOR, i);
    }

    public ParticleOptions getParticle() {
        return (ParticleOptions) this.getEntityData().get(AreaEffectCloud.DATA_PARTICLE);
    }

    public void setParticle(ParticleOptions particleparam) {
        this.getEntityData().set(AreaEffectCloud.DATA_PARTICLE, particleparam);
    }

    protected void setWaiting(boolean flag) {
        this.getEntityData().set(AreaEffectCloud.DATA_WAITING, flag);
    }

    public boolean isWaiting() {
        return (Boolean) this.getEntityData().get(AreaEffectCloud.DATA_WAITING);
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int i) {
        this.duration = i;
    }

    // Spigot start - copied from below
    @Override
    public void inactiveTick() {
        super.inactiveTick();

        if (this.tickCount >= this.waitTime + this.duration) {
            this.remove();
            return;
        }
    }
    // Spigot end

    @Override
    public void tick() {
        super.tick();
        boolean flag = this.isWaiting();
        float f = this.getRadius();

        if (this.level.isClientSide) {
            ParticleOptions particleparam = this.getParticle();
            float f1;
            float f2;
            float f3;
            int i;
            int j;
            int k;

            if (flag) {
                if (this.random.nextBoolean()) {
                    for (int l = 0; l < 2; ++l) {
                        float f4 = this.random.nextFloat() * 6.2831855F;

                        f1 = Mth.sqrt(this.random.nextFloat()) * 0.2F;
                        f2 = Mth.cos(f4) * f1;
                        f3 = Mth.sin(f4) * f1;
                        if (particleparam.getParticle() == ParticleTypes.ENTITY_EFFECT) {
                            int i1 = this.random.nextBoolean() ? 16777215 : this.getColor();

                            i = i1 >> 16 & 255;
                            j = i1 >> 8 & 255;
                            k = i1 & 255;
                            this.level.addAlwaysVisibleParticle(particleparam, this.getX() + (double) f2, this.getY(), this.getZ() + (double) f3, (double) ((float) i / 255.0F), (double) ((float) j / 255.0F), (double) ((float) k / 255.0F));
                        } else {
                            this.level.addAlwaysVisibleParticle(particleparam, this.getX() + (double) f2, this.getY(), this.getZ() + (double) f3, 0.0D, 0.0D, 0.0D);
                        }
                    }
                }
            } else {
                float f5 = 3.1415927F * f * f;

                for (int j1 = 0; (float) j1 < f5; ++j1) {
                    f1 = this.random.nextFloat() * 6.2831855F;
                    f2 = Mth.sqrt(this.random.nextFloat()) * f;
                    f3 = Mth.cos(f1) * f2;
                    float f6 = Mth.sin(f1) * f2;

                    if (particleparam.getParticle() == ParticleTypes.ENTITY_EFFECT) {
                        i = this.getColor();
                        j = i >> 16 & 255;
                        k = i >> 8 & 255;
                        int k1 = i & 255;

                        this.level.addAlwaysVisibleParticle(particleparam, this.getX() + (double) f3, this.getY(), this.getZ() + (double) f6, (double) ((float) j / 255.0F), (double) ((float) k / 255.0F), (double) ((float) k1 / 255.0F));
                    } else {
                        this.level.addAlwaysVisibleParticle(particleparam, this.getX() + (double) f3, this.getY(), this.getZ() + (double) f6, (0.5D - this.random.nextDouble()) * 0.15D, 0.009999999776482582D, (0.5D - this.random.nextDouble()) * 0.15D);
                    }
                }
            }
        } else {
            if (this.tickCount >= this.waitTime + this.duration) {
                this.remove();
                return;
            }

            boolean flag1 = this.tickCount < this.waitTime;

            if (flag != flag1) {
                this.setWaiting(flag1);
            }

            if (flag1) {
                return;
            }

            if (this.radiusPerTick != 0.0F) {
                f += this.radiusPerTick;
                if (f < 0.5F) {
                    this.remove();
                    return;
                }

                this.setRadius(f);
            }

            if (this.tickCount % 5 == 0) {
                Iterator iterator = this.victims.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<Entity, Integer> entry = (Entry) iterator.next();

                    if (this.tickCount >= (Integer) entry.getValue()) {
                        iterator.remove();
                    }
                }

                List<MobEffectInstance> list = Lists.newArrayList();
                Iterator iterator1 = this.potion.getEffects().iterator();

                while (iterator1.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator1.next();

                    list.add(new MobEffectInstance(mobeffect.getEffect(), mobeffect.getDuration() / 4, mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible()));
                }

                list.addAll(this.effects);
                if (list.isEmpty()) {
                    this.victims.clear();
                } else {
                    List<net.minecraft.world.entity.LivingEntity> list1 = this.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, this.getBoundingBox());

                    if (!list1.isEmpty()) {
                        Iterator iterator2 = list1.iterator();

                        List<LivingEntity> entities = new java.util.ArrayList<LivingEntity>(); // CraftBukkit
                        while (iterator2.hasNext()) {
                            net.minecraft.world.entity.LivingEntity entityliving = (net.minecraft.world.entity.LivingEntity) iterator2.next();

                            if (!this.victims.containsKey(entityliving) && entityliving.isAffectedByPotions()) {
                                double d0 = entityliving.getX() - this.getX();
                                double d1 = entityliving.getZ() - this.getZ();
                                double d2 = d0 * d0 + d1 * d1;

                                if (d2 <= (double) (f * f)) {
                                    // CraftBukkit start
                                    entities.add((LivingEntity) entityliving.getBukkitEntity());
                                }
                            }
                        }
                        org.bukkit.event.entity.AreaEffectCloudApplyEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callAreaEffectCloudApplyEvent(this, entities);
                        if (!event.isCancelled()) {
                            for (LivingEntity entity : event.getAffectedEntities()) {
                                if (entity instanceof CraftLivingEntity) {
                                    net.minecraft.world.entity.LivingEntity entityliving = ((CraftLivingEntity) entity).getHandle();
                                    // CraftBukkit end
                                    this.victims.put(entityliving, this.tickCount + this.reapplicationDelay);
                                    Iterator iterator3 = list.iterator();

                                    while (iterator3.hasNext()) {
                                        MobEffectInstance mobeffect1 = (MobEffectInstance) iterator3.next();

                                        if (mobeffect1.getEffect().isInstantenous()) {
                                            mobeffect1.getEffect().applyInstantenousEffect(this, this.getOwner(), entityliving, mobeffect1.getAmplifier(), 0.5D);
                                        } else {
                                            entityliving.addEffect(new MobEffectInstance(mobeffect1), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD); // CraftBukkit
                                        }
                                    }

                                    if (this.radiusOnUse != 0.0F) {
                                        f += this.radiusOnUse;
                                        if (f < 0.5F) {
                                            this.remove();
                                            return;
                                        }

                                        this.setRadius(f);
                                    }

                                    if (this.durationOnUse != 0) {
                                        this.duration += this.durationOnUse;
                                        if (this.duration <= 0) {
                                            this.remove();
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    public void setRadiusOnUse(float f) {
        this.radiusOnUse = f;
    }

    public void setRadiusPerTick(float f) {
        this.radiusPerTick = f;
    }

    public void setWaitTime(int i) {
        this.waitTime = i;
    }

    public void setOwner(@Nullable net.minecraft.world.entity.LivingEntity entityliving) {
        this.owner = entityliving;
        this.ownerUUID = entityliving == null ? null : entityliving.getUUID();
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getOwner() {
        if (this.owner == null && this.ownerUUID != null && this.level instanceof ServerLevel) {
            Entity entity = ((ServerLevel) this.level).getEntity(this.ownerUUID);

            if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                this.owner = (net.minecraft.world.entity.LivingEntity) entity;
            }
        }

        return this.owner;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.tickCount = nbttagcompound.getInt("Age");
        this.duration = nbttagcompound.getInt("Duration");
        this.waitTime = nbttagcompound.getInt("WaitTime");
        this.reapplicationDelay = nbttagcompound.getInt("ReapplicationDelay");
        this.durationOnUse = nbttagcompound.getInt("DurationOnUse");
        this.radiusOnUse = nbttagcompound.getFloat("RadiusOnUse");
        this.radiusPerTick = nbttagcompound.getFloat("RadiusPerTick");
        this.setRadius(nbttagcompound.getFloat("Radius"));
        if (nbttagcompound.hasUUID("Owner")) {
            this.ownerUUID = nbttagcompound.getUUID("Owner");
        }

        if (nbttagcompound.contains("Particle", 8)) {
            try {
                this.setParticle(ParticleArgument.readParticle(new StringReader(nbttagcompound.getString("Particle"))));
            } catch (CommandSyntaxException commandsyntaxexception) {
                AreaEffectCloud.LOGGER.warn("Couldn't load custom particle {}", nbttagcompound.getString("Particle"), commandsyntaxexception);
            }
        }

        if (nbttagcompound.contains("Color", 99)) {
            this.setFixedColor(nbttagcompound.getInt("Color"));
        }

        if (nbttagcompound.contains("Potion", 8)) {
            this.setPotion(PotionUtils.getPotion(nbttagcompound));
        }

        if (nbttagcompound.contains("Effects", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("Effects", 10);

            this.effects.clear();

            for (int i = 0; i < nbttaglist.size(); ++i) {
                MobEffectInstance mobeffect = MobEffectInstance.load(nbttaglist.getCompound(i));

                if (mobeffect != null) {
                    this.addEffect(mobeffect);
                }
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putInt("Age", this.tickCount);
        nbttagcompound.putInt("Duration", this.duration);
        nbttagcompound.putInt("WaitTime", this.waitTime);
        nbttagcompound.putInt("ReapplicationDelay", this.reapplicationDelay);
        nbttagcompound.putInt("DurationOnUse", this.durationOnUse);
        nbttagcompound.putFloat("RadiusOnUse", this.radiusOnUse);
        nbttagcompound.putFloat("RadiusPerTick", this.radiusPerTick);
        nbttagcompound.putFloat("Radius", this.getRadius());
        nbttagcompound.putString("Particle", this.getParticle().writeToString());
        if (this.ownerUUID != null) {
            nbttagcompound.putUUID("Owner", this.ownerUUID);
        }

        if (this.fixedColor) {
            nbttagcompound.putInt("Color", this.getColor());
        }

        if (this.potion != Potions.EMPTY && this.potion != null) {
            nbttagcompound.putString("Potion", Registry.POTION.getKey(this.potion).toString());
        }

        if (!this.effects.isEmpty()) {
            ListTag nbttaglist = new ListTag();
            Iterator iterator = this.effects.iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                nbttaglist.add(mobeffect.save(new CompoundTag()));
            }

            nbttagcompound.put("Effects", nbttaglist);
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (AreaEffectCloud.DATA_RADIUS.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }
}
