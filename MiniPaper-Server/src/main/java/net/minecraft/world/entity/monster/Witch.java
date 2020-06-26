package net.minecraft.world.entity.monster;

<<<<<<< HEAD
=======
// Paper start
import com.destroystokyo.paper.event.entity.WitchReadyPotionEvent;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
// Paper end

>>>>>>> Toothpick
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableWitchTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestHealableRaiderTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Witch extends Raider implements RangedAttackMob {

    private static final UUID SPEED_MODIFIER_DRINKING_UUID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181E");
    private static final AttributeModifier SPEED_MODIFIER_DRINKING = new AttributeModifier(Witch.SPEED_MODIFIER_DRINKING_UUID, "Drinking speed penalty", -0.25D, AttributeModifier.Operation.ADDITION);
    private static final EntityDataAccessor<Boolean> DATA_USING_ITEM = SynchedEntityData.defineId(Witch.class, EntityDataSerializers.BOOLEAN);
    private int usingTime;
    private NearestHealableRaiderTargetGoal<Raider> healRaidersGoal;
    private NearestAttackableWitchTargetGoal<Player> attackPlayersGoal;

    public Witch(EntityType<? extends Witch> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.healRaidersGoal = new NearestHealableRaiderTargetGoal<>(this, Raider.class, true, (entityliving) -> {
            return entityliving != null && this.hasActiveRaid() && entityliving.getType() != EntityType.WITCH;
        });
        this.attackPlayersGoal = new NearestAttackableWitchTargetGoal<>(this, Player.class, 10, true, false, (Predicate) null);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 60, 10.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}));
        this.targetSelector.addGoal(2, this.healRaidersGoal);
        this.targetSelector.addGoal(3, this.attackPlayersGoal);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().register(Witch.DATA_USING_ITEM, false);
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.WITCH_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.WITCH_DEATH;
    }

    public void setUsingItem(boolean flag) {
        this.getEntityData().set(Witch.DATA_USING_ITEM, flag);
    }

    public boolean isDrinkingPotion() {
        return (Boolean) this.getEntityData().get(Witch.DATA_USING_ITEM);
    }

    public static AttributeSupplier.Builder eL() {
        return Monster.eS().a(Attributes.MAX_HEALTH, 26.0D).a(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public void aiStep() {
        if (!this.level.isClientSide && this.isAlive()) {
            this.healRaidersGoal.decrementCooldown();
            if (this.healRaidersGoal.getCooldown() <= 0) {
                this.attackPlayersGoal.setCanAttack(true);
            } else {
                this.attackPlayersGoal.setCanAttack(false);
            }

            if (this.isDrinkingPotion()) {
                if (this.usingTime-- <= 0) {
                    this.setUsingItem(false);
                    ItemStack itemstack = this.getMainHandItem();

                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    if (itemstack.getItem() == Items.POTION) {
                        List<MobEffectInstance> list = PotionUtils.getMobEffects(itemstack);

                        if (list != null) {
                            Iterator iterator = list.iterator();

                            while (iterator.hasNext()) {
                                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                                this.addEffect(new MobEffectInstance(mobeffect), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                            }
                        }
                    }

                    this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(Witch.SPEED_MODIFIER_DRINKING);
                }
            } else {
                Potion potionregistry = null;

                if (this.random.nextFloat() < 0.15F && this.isEyeInFluid((Tag) FluidTags.WATER) && !this.hasEffect(MobEffects.WATER_BREATHING)) {
                    potionregistry = Potions.WATER_BREATHING;
                } else if (this.random.nextFloat() < 0.15F && (this.isOnFire() || this.getLastDamageSource() != null && this.getLastDamageSource().isFire()) && !this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    potionregistry = Potions.FIRE_RESISTANCE;
                } else if (this.random.nextFloat() < 0.05F && this.getHealth() < this.getMaxHealth()) {
                    potionregistry = Potions.HEALING;
                } else if (this.random.nextFloat() < 0.5F && this.getTarget() != null && !this.hasEffect(MobEffects.MOVEMENT_SPEED) && this.getTarget().distanceToSqr((Entity) this) > 121.0D) {
                    potionregistry = Potions.SWIFTNESS;
                }

                if (potionregistry != null) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, PotionUtils.setPotion(new ItemStack(Items.POTION), potionregistry));
                    this.usingTime = this.getMainHandItem().getUseDuration();
                    this.setUsingItem(true);
                    if (!this.isSilent()) {
                        this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_DRINK, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
                    }

                    AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

                    attributemodifiable.removeModifier(Witch.SPEED_MODIFIER_DRINKING);
                    attributemodifiable.addTransientModifier(Witch.SPEED_MODIFIER_DRINKING);
                }
            }

            if (this.random.nextFloat() < 7.5E-4F) {
                this.level.broadcastEntityEvent(this, (byte) 15);
            }
        }

        super.aiStep();
    }

<<<<<<< HEAD
=======
    // Paper start
    public void setDrinkingPotion(ItemStack potion) {
        setItemSlot(EquipmentSlot.MAINHAND, CraftItemStack.asNMSCopy(WitchReadyPotionEvent.process((org.bukkit.entity.Witch) getBukkitEntity(), CraftItemStack.asCraftMirror(potion))));
        setPotionUseTimeLeft(getMainHandItem().getItemUseMaxDuration());
        setDrinkingPotion(true);
        level.sendSoundEffect(null, getX(), getY(), getZ(), SoundEvents.WITCH_DRINK, getSoundSource(), 1.0F, 0.8F + random.nextFloat() * 0.4F);
        AttributeInstance attributeinstance = getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        attributeinstance.removeModifier(net.minecraft.world.entity.monster.Witch.DRINKING_SPEED);
        attributeinstance.addModifier(net.minecraft.world.entity.monster.Witch.DRINKING_SPEED);
    }
    // Paper end

>>>>>>> Toothpick
    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.WITCH_CELEBRATE;
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource damagesource, float f) {
        f = super.getDamageAfterMagicAbsorb(damagesource, f);
        if (damagesource.getEntity() == this) {
            f = 0.0F;
        }

        if (damagesource.isMagic()) {
            f = (float) ((double) f * 0.15D);
        }

        return f;
    }

    @Override
    public void performRangedAttack(LivingEntity entityliving, float f) {
        if (!this.isDrinkingPotion()) {
            Vec3 vec3d = entityliving.getDeltaMovement();
            double d0 = entityliving.getX() + vec3d.x - this.getX();
            double d1 = entityliving.getEyeY() - 1.100000023841858D - this.getY();
            double d2 = entityliving.getZ() + vec3d.z - this.getZ();
            float f1 = Mth.sqrt(d0 * d0 + d2 * d2);
            Potion potionregistry = Potions.HARMING;

            if (entityliving instanceof Raider) {
                if (entityliving.getHealth() <= 4.0F) {
                    potionregistry = Potions.HEALING;
                } else {
                    potionregistry = Potions.REGENERATION;
                }

                this.setTarget((LivingEntity) null);
            } else if (f1 >= 8.0F && !entityliving.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                potionregistry = Potions.SLOWNESS;
            } else if (entityliving.getHealth() >= 8.0F && !entityliving.hasEffect(MobEffects.POISON)) {
                potionregistry = Potions.POISON;
            } else if (f1 <= 3.0F && !entityliving.hasEffect(MobEffects.WEAKNESS) && this.random.nextFloat() < 0.25F) {
                potionregistry = Potions.WEAKNESS;
            }

            ThrownPotion entitypotion = new ThrownPotion(this.level, this);

            entitypotion.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), potionregistry));
            entitypotion.xRot -= -20.0F;
            entitypotion.shoot(d0, d1 + (double) (f1 * 0.2F), d2, 0.75F, 8.0F);
            if (!this.isSilent()) {
                this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
            }

            this.level.addFreshEntity(entitypotion);
        }
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 1.62F;
    }

    @Override
    public void applyRaidBuffs(int i, boolean flag) {}

    @Override
    public boolean canBeLeader() {
        return false;
    }
}
