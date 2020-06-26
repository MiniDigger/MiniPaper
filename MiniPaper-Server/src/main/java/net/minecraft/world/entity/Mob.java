package net.minecraft.world.entity;

import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.storage.loot.LootContext;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityUnleashEvent.UnleashReason;
// CraftBukkit end

public abstract class Mob extends LivingEntity {

    private static final EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID = SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BYTE);
    public int ambientSoundTime;
    protected int xpReward;
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private final BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    public GoalSelector goalSelector;
    public GoalSelector targetSelector;
    private LivingEntity target;
    private final Sensing sensing;
    private final NonNullList<ItemStack> handItems;
    public final float[] handDropChances;
    private final NonNullList<ItemStack> armorItems;
    public final float[] armorDropChances;
    // private boolean canPickUpLoot; // CraftBukkit - moved up to EntityLiving
    public boolean persistenceRequired;
    private final Map<BlockPathTypes, Float> pathfindingMalus;
    public ResourceLocation lootTable;
    public long lootTableSeed;
    @Nullable
    private Entity leashHolder;
    private int delayedLeashHolderId;
    @Nullable
    private CompoundTag leashInfoTag;
    private BlockPos restrictCenter;
    private float restrictRadius;

    public boolean aware = true; // CraftBukkit

    protected Mob(EntityType<? extends Mob> entitytypes, Level world) {
        super(entitytypes, world);
        this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
        this.handDropChances = new float[2];
        this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
        this.armorDropChances = new float[4];
        this.pathfindingMalus = Maps.newEnumMap(BlockPathTypes.class);
        this.restrictCenter = BlockPos.ZERO;
        this.restrictRadius = -1.0F;
        this.goalSelector = new GoalSelector(world.getProfilerSupplier());
        this.targetSelector = new GoalSelector(world.getProfilerSupplier());
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(world);
        this.sensing = new Sensing(this);
        Arrays.fill(this.armorDropChances, 0.085F);
        Arrays.fill(this.handDropChances, 0.085F);
        if (world != null && !world.isClientSide) {
            this.registerGoals();
        }

        // CraftBukkit start - default persistance to type's persistance value
        this.persistenceRequired = !removeWhenFarAway(0);
        // CraftBukkit end
    }

    protected void registerGoals() {}

    public static AttributeSupplier.Builder p() {
        return LivingEntity.cK().a(Attributes.FOLLOW_RANGE, 16.0D).a(Attributes.ATTACK_KNOCKBACK);
    }

    protected PathNavigation createNavigation(Level world) {
        return new GroundPathNavigation(this, world);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(BlockPathTypes pathtype) {
        Mob entityinsentient;

        if (this.getVehicle() instanceof Mob && ((Mob) this.getVehicle()).shouldPassengersInheritMalus()) {
            entityinsentient = (Mob) this.getVehicle();
        } else {
            entityinsentient = this;
        }

        Float ofloat = (Float) entityinsentient.pathfindingMalus.get(pathtype);

        return ofloat == null ? pathtype.getMalus() : ofloat;
    }

    public void setPathfindingMalus(BlockPathTypes pathtype, float f) {
        this.pathfindingMalus.put(pathtype, f);
    }

    public boolean canCutCorner(BlockPathTypes pathtype) {
        return pathtype != BlockPathTypes.DANGER_FIRE && pathtype != BlockPathTypes.DANGER_CACTUS && pathtype != BlockPathTypes.DANGER_OTHER;
    }

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getControllerLook() {
        return this.lookControl;
    }

    public MoveControl getMoveControl() {
        if (this.isPassenger() && this.getVehicle() instanceof Mob) {
            Mob entityinsentient = (Mob) this.getVehicle();

            return entityinsentient.getMoveControl();
        } else {
            return this.moveControl;
        }
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        if (this.isPassenger() && this.getVehicle() instanceof Mob) {
            Mob entityinsentient = (Mob) this.getVehicle();

            return entityinsentient.getNavigation();
        } else {
            return this.navigation;
        }
    }

    public Sensing getEntitySenses() {
        return this.sensing;
    }

    @Nullable
    public LivingEntity getTarget() {
        return this.target;
    }

    public void setTarget(@Nullable LivingEntity entityliving) {
        // CraftBukkit start - fire event
        setGoalTarget(entityliving, EntityTargetEvent.TargetReason.UNKNOWN, true);
    }

    public boolean setGoalTarget(LivingEntity entityliving, EntityTargetEvent.TargetReason reason, boolean fireEvent) {
        if (getTarget() == entityliving) return false;
        if (fireEvent) {
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN && getTarget() != null && entityliving == null) {
                reason = getTarget().isAlive() ? EntityTargetEvent.TargetReason.FORGOT_TARGET : EntityTargetEvent.TargetReason.TARGET_DIED;
            }
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN) {
                level.getServerOH().getLogger().log(java.util.logging.Level.WARNING, "Unknown target reason, please report on the issue tracker", new Exception());
            }
            CraftLivingEntity ctarget = null;
            if (entityliving != null) {
                ctarget = (CraftLivingEntity) entityliving.getBukkitEntity();
            }
            EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(this.getBukkitEntity(), ctarget, reason);
            level.getServerOH().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }

            if (event.getTarget() != null) {
                entityliving = ((CraftLivingEntity) event.getTarget()).getHandle();
            } else {
                entityliving = null;
            }
        }
        this.target = entityliving;
        return true;
        // CraftBukkit end
    }

    @Override
    public boolean canAttackType(EntityType<?> entitytypes) {
        return entitytypes != EntityType.GHAST;
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem itemprojectileweapon) {
        return false;
    }

    public void ate() {}

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Mob.DATA_MOB_FLAGS_ID, (byte) 0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        SoundEvent soundeffect = this.getSoundAmbient();

        if (soundeffect != null) {
            this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    @Override
    public void baseTick() {
        super.baseTick();
        this.level.getProfiler().push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        this.level.getProfiler().pop();
    }

    @Override
    protected void playHurtSound(DamageSource damagesource) {
        this.resetAmbientSoundTime();
        super.playHurtSound(damagesource);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getExperienceReward(Player entityhuman) {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            int j;

            for (j = 0; j < this.armorItems.size(); ++j) {
                if (!((ItemStack) this.armorItems.get(j)).isEmpty() && this.armorDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            for (j = 0; j < this.handItems.size(); ++j) {
                if (!((ItemStack) this.handItems.get(j)).isEmpty() && this.handDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level.isClientSide) {
            for (int i = 0; i < 20; ++i) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;
                double d3 = 10.0D;

                this.level.addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * 10.0D, this.getRandomY() - d1 * 10.0D, this.getRandomZ(1.0D) - d2 * 10.0D, d0, d1, d2);
            }
        } else {
            this.level.broadcastEntityEvent(this, (byte) 20);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide) {
            this.tickLeash();
            if (this.tickCount % 5 == 0) {
                this.updateControlFlags();
            }
        }

    }

    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof Mob);
        boolean flag1 = !(this.getVehicle() instanceof Boat);

        this.goalSelector.setControlFlag(Goal.Flag.MOVE, flag);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, flag);
    }

    @Override
    protected float tickHeadTurn(float f, float f1) {
        this.bodyRotationControl.clientTick();
        return f1;
    }

    @Nullable
    protected SoundEvent getSoundAmbient() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        nbttagcompound.putBoolean("PersistenceRequired", this.persistenceRequired);
        ListTag nbttaglist = new ListTag();

        CompoundTag nbttagcompound1;

        for (Iterator iterator = this.armorItems.iterator(); iterator.hasNext(); nbttaglist.add(nbttagcompound1)) {
            ItemStack itemstack = (ItemStack) iterator.next();

            nbttagcompound1 = new CompoundTag();
            if (!itemstack.isEmpty()) {
                itemstack.save(nbttagcompound1);
            }
        }

        nbttagcompound.put("ArmorItems", nbttaglist);
        ListTag nbttaglist1 = new ListTag();

        CompoundTag nbttagcompound2;

        for (Iterator iterator1 = this.handItems.iterator(); iterator1.hasNext(); nbttaglist1.add(nbttagcompound2)) {
            ItemStack itemstack1 = (ItemStack) iterator1.next();

            nbttagcompound2 = new CompoundTag();
            if (!itemstack1.isEmpty()) {
                itemstack1.save(nbttagcompound2);
            }
        }

        nbttagcompound.put("HandItems", nbttaglist1);
        ListTag nbttaglist2 = new ListTag();
        float[] afloat = this.armorDropChances;
        int i = afloat.length;

        int j;

        for (j = 0; j < i; ++j) {
            float f = afloat[j];

            nbttaglist2.add(FloatTag.valueOf(f));
        }

        nbttagcompound.put("ArmorDropChances", nbttaglist2);
        ListTag nbttaglist3 = new ListTag();
        float[] afloat1 = this.handDropChances;

        j = afloat1.length;

        for (int k = 0; k < j; ++k) {
            float f1 = afloat1[k];

            nbttaglist3.add(FloatTag.valueOf(f1));
        }

        nbttagcompound.put("HandDropChances", nbttaglist3);
        if (this.leashHolder != null) {
            nbttagcompound2 = new CompoundTag();
            if (this.leashHolder instanceof LivingEntity) {
                UUID uuid = this.leashHolder.getUUID();

                nbttagcompound2.putUUID("UUID", uuid);
            } else if (this.leashHolder instanceof HangingEntity) {
                BlockPos blockposition = ((HangingEntity) this.leashHolder).getPos();

                nbttagcompound2.putInt("X", blockposition.getX());
                nbttagcompound2.putInt("Y", blockposition.getY());
                nbttagcompound2.putInt("Z", blockposition.getZ());
            }

            nbttagcompound.put("Leash", nbttagcompound2);
        } else if (this.leashInfoTag != null) {
            nbttagcompound.put("Leash", this.leashInfoTag.copy());
        }

        nbttagcompound.putBoolean("LeftHanded", this.isLeftHanded());
        if (this.lootTable != null) {
            nbttagcompound.putString("DeathLootTable", this.lootTable.toString());
            if (this.lootTableSeed != 0L) {
                nbttagcompound.putLong("DeathLootTableSeed", this.lootTableSeed);
            }
        }

        if (this.isNoAi()) {
            nbttagcompound.putBoolean("NoAI", this.isNoAi());
        }

        nbttagcompound.putBoolean("Bukkit.Aware", this.aware); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);

        // CraftBukkit start - If looting or persistence is false only use it if it was set after we started using it
        if (nbttagcompound.contains("CanPickUpLoot", 1)) {
            boolean data = nbttagcompound.getBoolean("CanPickUpLoot");
            if (isLevelAtLeast(nbttagcompound, 1) || data) {
                this.setCanPickUpLoot(data);
            }
        }

        boolean data = nbttagcompound.getBoolean("PersistenceRequired");
        if (isLevelAtLeast(nbttagcompound, 1) || data) {
            this.persistenceRequired = data;
        }
        // CraftBukkit end
        ListTag nbttaglist;
        int i;

        if (nbttagcompound.contains("ArmorItems", 9)) {
            nbttaglist = nbttagcompound.getList("ArmorItems", 10);

            for (i = 0; i < this.armorItems.size(); ++i) {
                this.armorItems.set(i, ItemStack.of(nbttaglist.getCompound(i)));
            }
        }

        if (nbttagcompound.contains("HandItems", 9)) {
            nbttaglist = nbttagcompound.getList("HandItems", 10);

            for (i = 0; i < this.handItems.size(); ++i) {
                this.handItems.set(i, ItemStack.of(nbttaglist.getCompound(i)));
            }
        }

        if (nbttagcompound.contains("ArmorDropChances", 9)) {
            nbttaglist = nbttagcompound.getList("ArmorDropChances", 5);

            for (i = 0; i < nbttaglist.size(); ++i) {
                this.armorDropChances[i] = nbttaglist.getFloat(i);
            }
        }

        if (nbttagcompound.contains("HandDropChances", 9)) {
            nbttaglist = nbttagcompound.getList("HandDropChances", 5);

            for (i = 0; i < nbttaglist.size(); ++i) {
                this.handDropChances[i] = nbttaglist.getFloat(i);
            }
        }

        if (nbttagcompound.contains("Leash", 10)) {
            this.leashInfoTag = nbttagcompound.getCompound("Leash");
        }

        this.setLeftHanded(nbttagcompound.getBoolean("LeftHanded"));
        if (nbttagcompound.contains("DeathLootTable", 8)) {
            this.lootTable = new ResourceLocation(nbttagcompound.getString("DeathLootTable"));
            this.lootTableSeed = nbttagcompound.getLong("DeathLootTableSeed");
        }

        this.setNoAi(nbttagcompound.getBoolean("NoAI"));
        // CraftBukkit start
        if (nbttagcompound.contains("Bukkit.Aware")) {
            this.aware = nbttagcompound.getBoolean("Bukkit.Aware");
        }
        // CraftBukkit end
    }

    @Override
    protected void dropFromLootTable(DamageSource damagesource, boolean flag) {
        super.dropFromLootTable(damagesource, flag);
        this.lootTable = null;
    }
    // CraftBukkit - start
    public ResourceLocation getLootTableOH() {
        return getDefaultLootTable();
    }
    // CraftBukkit - end

    @Override
    protected LootContext.Builder createLootContext(boolean flag, DamageSource damagesource) {
        return super.createLootContext(flag, damagesource).withOptionalRandomSeed(this.lootTableSeed, this.random);
    }

    @Override
    public final ResourceLocation do_() {
        return this.lootTable == null ? this.getDefaultLootTable() : this.lootTable;
    }

    protected ResourceLocation getDefaultLootTable() {
        return super.do_();
    }

    public void setZza(float f) {
        this.zza = f;
    }

    public void setYya(float f) {
        this.yya = f;
    }

    public void setXxa(float f) {
        this.xxa = f;
    }

    @Override
    public void setSpeed(float f) {
        super.setSpeed(f);
        this.setZza(f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.level.getProfiler().push("looting");
        if (!this.level.isClientSide && this.canPickUpLoot() && this.isAlive() && !this.dead && this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            List<ItemEntity> list = this.level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.0D, 0.0D, 1.0D));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemEntity entityitem = (ItemEntity) iterator.next();

                if (!entityitem.removed && !entityitem.getItem().isEmpty() && !entityitem.hasPickUpDelay() && this.wantsToPickUp(entityitem.getItem())) {
                    this.pickUpItem(entityitem);
                }
            }
        }

        this.level.getProfiler().pop();
    }

    protected void pickUpItem(ItemEntity entityitem) {
        ItemStack itemstack = entityitem.getItem();

        if (this.g(itemstack, entityitem)) { // CraftBukkit - add item
            this.onItemPickup(entityitem);
            this.take(entityitem, itemstack.getCount());
            entityitem.remove();
        }

    }

    public boolean equipItemIfPossible(ItemStack itemstack) {
        // CraftBukkit start - add item
        return this.g(itemstack, null);
    }

    public boolean g(ItemStack itemstack, ItemEntity entityitem) {
        // CraftBukkit end
        EquipmentSlot enumitemslot = getEquipmentSlotForItem(itemstack);
        ItemStack itemstack1 = this.getItemBySlot(enumitemslot);
        boolean flag = this.canReplaceCurrentItem(itemstack, itemstack1);

        // CraftBukkit start
        boolean canPickup = flag && this.canHoldItem(itemstack);
        if (entityitem != null) {
            canPickup = !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, !canPickup).isCancelled();
        }
        if (canPickup) {
            // CraftBukkit end
            double d0 = (double) this.getEquipmentDropChance(enumitemslot);

            if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                this.forceDrops = true; // CraftBukkit
                this.spawnAtLocation(itemstack1);
                this.forceDrops = false; // CraftBukkit
            }

            this.setItemSlotAndDropWhenKilled(enumitemslot, itemstack);
            this.playEquipSound(itemstack);
            return true;
        } else {
            return false;
        }
    }

    protected void setItemSlotAndDropWhenKilled(EquipmentSlot enumitemslot, ItemStack itemstack) {
        this.setItemSlot(enumitemslot, itemstack);
        this.setGuaranteedDrop(enumitemslot);
        this.persistenceRequired = true;
    }

    public void setGuaranteedDrop(EquipmentSlot enumitemslot) {
        switch (enumitemslot.getType()) {
            case HAND:
                this.handDropChances[enumitemslot.getIndex()] = 2.0F;
                break;
            case ARMOR:
                this.armorDropChances[enumitemslot.getIndex()] = 2.0F;
        }

    }

    protected boolean canReplaceCurrentItem(ItemStack itemstack, ItemStack itemstack1) {
        if (itemstack1.isEmpty()) {
            return true;
        } else if (itemstack.getItem() instanceof SwordItem) {
            if (!(itemstack1.getItem() instanceof SwordItem)) {
                return true;
            } else {
                SwordItem itemsword = (SwordItem) itemstack.getItem();
                SwordItem itemsword1 = (SwordItem) itemstack1.getItem();

                return itemsword.getDamage() != itemsword1.getDamage() ? itemsword.getDamage() > itemsword1.getDamage() : this.canReplaceEqualItem(itemstack, itemstack1);
            }
        } else if (itemstack.getItem() instanceof BowItem && itemstack1.getItem() instanceof BowItem) {
            return this.canReplaceEqualItem(itemstack, itemstack1);
        } else if (itemstack.getItem() instanceof CrossbowItem && itemstack1.getItem() instanceof CrossbowItem) {
            return this.canReplaceEqualItem(itemstack, itemstack1);
        } else if (itemstack.getItem() instanceof ArmorItem) {
            if (EnchantmentHelper.hasBindingCurse(itemstack1)) {
                return false;
            } else if (!(itemstack1.getItem() instanceof ArmorItem)) {
                return true;
            } else {
                ArmorItem itemarmor = (ArmorItem) itemstack.getItem();
                ArmorItem itemarmor1 = (ArmorItem) itemstack1.getItem();

                return itemarmor.getDefense() != itemarmor1.getDefense() ? itemarmor.getDefense() > itemarmor1.getDefense() : (itemarmor.getToughness() != itemarmor1.getToughness() ? itemarmor.getToughness() > itemarmor1.getToughness() : this.canReplaceEqualItem(itemstack, itemstack1));
            }
        } else {
            if (itemstack.getItem() instanceof DiggerItem) {
                if (itemstack1.getItem() instanceof BlockItem) {
                    return true;
                }

                if (itemstack1.getItem() instanceof DiggerItem) {
                    DiggerItem itemtool = (DiggerItem) itemstack.getItem();
                    DiggerItem itemtool1 = (DiggerItem) itemstack1.getItem();

                    if (itemtool.getAttackDamage() != itemtool1.getAttackDamage()) {
                        return itemtool.getAttackDamage() > itemtool1.getAttackDamage();
                    }

                    return this.canReplaceEqualItem(itemstack, itemstack1);
                }
            }

            return false;
        }
    }

    public boolean canReplaceEqualItem(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.getDamageValue() >= itemstack1.getDamageValue() && (!itemstack.hasTag() || itemstack1.hasTag()) ? (itemstack.hasTag() && itemstack1.hasTag() ? itemstack.getTag().getAllKeys().stream().anyMatch((s) -> {
            return !s.equals("Damage");
        }) && !itemstack1.getTag().getAllKeys().stream().anyMatch((s) -> {
            return !s.equals("Damage");
        }) : false) : true;
    }

    public boolean canHoldItem(ItemStack itemstack) {
        return true;
    }

    public boolean wantsToPickUp(ItemStack itemstack) {
        return this.canHoldItem(itemstack);
    }

    public boolean removeWhenFarAway(double d0) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.level.getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.remove();
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Player entityhuman = this.level.getNearestPlayer(this, -1.0D);

            if (entityhuman != null) {
                double d0 = entityhuman.distanceToSqr((Entity) this); // CraftBukkit - decompile error
                int i = this.getType().getCategory().getDespawnDistance();
                int j = i * i;

                if (d0 > (double) j) { // CraftBukkit - remove isTypeNotPersistent() check
                    this.remove();
                }

                int k = this.getType().getCategory().getNoDespawnDistance();
                int l = k * k;

                if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && d0 > (double) l) { // CraftBukkit - remove isTypeNotPersistent() check
                    this.remove();
                } else if (d0 < (double) l) {
                    this.noActionTime = 0;
                }
            }

        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        ++this.noActionTime;
        if (!this.aware) return; // CraftBukkit
        this.level.getProfiler().push("sensing");
        this.sensing.tick();
        this.level.getProfiler().pop();
        this.level.getProfiler().push("targetSelector");
        this.targetSelector.tick();
        this.level.getProfiler().pop();
        this.level.getProfiler().push("goalSelector");
        this.goalSelector.tick();
        this.level.getProfiler().pop();
        this.level.getProfiler().push("navigation");
        this.navigation.tick();
        this.level.getProfiler().pop();
        this.level.getProfiler().push("mob tick");
        this.customServerAiStep();
        this.level.getProfiler().pop();
        this.level.getProfiler().push("controls");
        this.level.getProfiler().push("move");
        this.moveControl.tick();
        this.level.getProfiler().popPush("look");
        this.lookControl.tick();
        this.level.getProfiler().popPush("jump");
        this.jumpControl.tick();
        this.level.getProfiler().pop();
        this.level.getProfiler().pop();
        this.sendDebugPackets();
    }

    protected void sendDebugPackets() {
        DebugPackets.sendGoalSelector(this.level, this, this.goalSelector);
    }

    protected void customServerAiStep() {}

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    public void lookAt(Entity entity, float f, float f1) {
        double d0 = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double d2;

        if (entity instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) entity;

            d2 = entityliving.getEyeY() - this.getEyeY();
        } else {
            d2 = (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0D - this.getEyeY();
        }

        double d3 = (double) Mth.sqrt(d0 * d0 + d1 * d1);
        float f2 = (float) (Mth.atan2(d1, d0) * 57.2957763671875D) - 90.0F;
        float f3 = (float) (-(Mth.atan2(d2, d3) * 57.2957763671875D));

        this.xRot = this.rotlerp(this.xRot, f3, f1);
        this.yRot = this.rotlerp(this.yRot, f2, f);
    }

    private float rotlerp(float f, float f1, float f2) {
        float f3 = Mth.wrapDegrees(f1 - f);

        if (f3 > f2) {
            f3 = f2;
        }

        if (f3 < -f2) {
            f3 = -f2;
        }

        return f + f3;
    }

    public static boolean checkMobSpawnRules(EntityType<? extends Mob> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        BlockPos blockposition1 = blockposition.below();

        return enummobspawn == MobSpawnType.SPAWNER || generatoraccess.getType(blockposition1).isValidSpawn((BlockGetter) generatoraccess, blockposition1, entitytypes);
    }

    public boolean checkSpawnRules(LevelAccessor generatoraccess, MobSpawnType enummobspawn) {
        return true;
    }

    public boolean checkSpawnObstruction(LevelReader iworldreader) {
        return !iworldreader.containsAnyLiquid(this.getBoundingBox()) && iworldreader.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int i) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return 3;
        } else {
            int i = (int) (this.getHealth() - this.getMaxHealth() * 0.33F);

            i -= (3 - this.level.getDifficulty().getId()) * 4;
            if (i < 0) {
                i = 0;
            }

            return i + 3;
        }
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return this.handItems;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot enumitemslot) {
        switch (enumitemslot.getType()) {
            case HAND:
                return (ItemStack) this.handItems.get(enumitemslot.getIndex());
            case ARMOR:
                return (ItemStack) this.armorItems.get(enumitemslot.getIndex());
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    public void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack) {
        switch (enumitemslot.getType()) {
            case HAND:
                this.handItems.set(enumitemslot.getIndex(), itemstack);
                break;
            case ARMOR:
                this.armorItems.set(enumitemslot.getIndex(), itemstack);
        }

    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damagesource, int i, boolean flag) {
        super.dropCustomDeathLoot(damagesource, i, flag);
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int j = aenumitemslot.length;

        for (int k = 0; k < j; ++k) {
            EquipmentSlot enumitemslot = aenumitemslot[k];
            ItemStack itemstack = this.getItemBySlot(enumitemslot);
            float f = this.getEquipmentDropChance(enumitemslot);
            boolean flag1 = f > 1.0F;

            if (!itemstack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemstack) && (flag || flag1) && Math.max(this.random.nextFloat() - (float) i * 0.01F, 0.0F) < f) {
                if (!flag1 && itemstack.isDamageableItem()) {
                    itemstack.setDamageValue(itemstack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemstack.getMaxDamage() - 3, 1))));
                }

                this.spawnAtLocation(itemstack);
            }
        }

    }

    protected float getEquipmentDropChance(EquipmentSlot enumitemslot) {
        float f;

        switch (enumitemslot.getType()) {
            case HAND:
                f = this.handDropChances[enumitemslot.getIndex()];
                break;
            case ARMOR:
                f = this.armorDropChances[enumitemslot.getIndex()];
                break;
            default:
                f = 0.0F;
        }

        return f;
    }

    protected void populateDefaultEquipmentSlots(DifficultyInstance difficultydamagescaler) {
        if (this.random.nextFloat() < 0.15F * difficultydamagescaler.getSpecialMultiplier()) {
            int i = this.random.nextInt(2);
            float f = this.level.getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;

            if (this.random.nextFloat() < 0.095F) {
                ++i;
            }

            if (this.random.nextFloat() < 0.095F) {
                ++i;
            }

            if (this.random.nextFloat() < 0.095F) {
                ++i;
            }

            boolean flag = true;
            EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
            int j = aenumitemslot.length;

            for (int k = 0; k < j; ++k) {
                EquipmentSlot enumitemslot = aenumitemslot[k];

                if (enumitemslot.getType() == EquipmentSlot.Type.ARMOR) {
                    ItemStack itemstack = this.getItemBySlot(enumitemslot);

                    if (!flag && this.random.nextFloat() < f) {
                        break;
                    }

                    flag = false;
                    if (itemstack.isEmpty()) {
                        Item item = getEquipmentForSlot(enumitemslot, i);

                        if (item != null) {
                            this.setItemSlot(enumitemslot, new ItemStack(item));
                        }
                    }
                }
            }
        }

    }

    public static EquipmentSlot getEquipmentSlotForItem(ItemStack itemstack) {
        Item item = itemstack.getItem();

        return item != Blocks.CARVED_PUMPKIN.asItem() && (!(item instanceof BlockItem) || !(((BlockItem) item).getBlock() instanceof AbstractSkullBlock)) ? (item instanceof ArmorItem ? ((ArmorItem) item).getSlot() : (item == Items.ELYTRA ? EquipmentSlot.CHEST : (item == Items.SHIELD ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND))) : EquipmentSlot.HEAD;
    }

    @Nullable
    public static Item getEquipmentForSlot(EquipmentSlot enumitemslot, int i) {
        switch (enumitemslot) {
            case HEAD:
                if (i == 0) {
                    return Items.LEATHER_HELMET;
                } else if (i == 1) {
                    return Items.GOLDEN_HELMET;
                } else if (i == 2) {
                    return Items.CHAINMAIL_HELMET;
                } else if (i == 3) {
                    return Items.IRON_HELMET;
                } else if (i == 4) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (i == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (i == 1) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (i == 2) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (i == 3) {
                    return Items.IRON_CHESTPLATE;
                } else if (i == 4) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (i == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (i == 1) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (i == 2) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (i == 3) {
                    return Items.IRON_LEGGINGS;
                } else if (i == 4) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (i == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (i == 1) {
                    return Items.GOLDEN_BOOTS;
                } else if (i == 2) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (i == 3) {
                    return Items.IRON_BOOTS;
                } else if (i == 4) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(DifficultyInstance difficultydamagescaler) {
        float f = difficultydamagescaler.getSpecialMultiplier();

        if (!this.getMainHandItem().isEmpty() && this.random.nextFloat() < 0.25F * f) {
            this.setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(this.random, this.getMainHandItem(), (int) (5.0F + f * (float) this.random.nextInt(18)), false));
        }

        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];

            if (enumitemslot.getType() == EquipmentSlot.Type.ARMOR) {
                ItemStack itemstack = this.getItemBySlot(enumitemslot);

                if (!itemstack.isEmpty() && this.random.nextFloat() < 0.5F * f) {
                    this.setItemSlot(enumitemslot, EnchantmentHelper.enchantItem(this.random, itemstack, (int) (5.0F + f * (float) this.random.nextInt(18)), false));
                }
            }
        }

    }

    @Nullable
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.getAttribute(Attributes.FOLLOW_RANGE).addPermanentModifier(new AttributeModifier("Random spawn bonus", this.random.nextGaussian() * 0.05D, AttributeModifier.Operation.MULTIPLY_BASE));
        if (this.random.nextFloat() < 0.05F) {
            this.setLeftHanded(true);
        } else {
            this.setLeftHanded(false);
        }

        return groupdataentity;
    }

    public boolean canBeControlledByRider() {
        return false;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    public void setDropChance(EquipmentSlot enumitemslot, float f) {
        switch (enumitemslot.getType()) {
            case HAND:
                this.handDropChances[enumitemslot.getIndex()] = f;
                break;
            case ARMOR:
                this.armorDropChances[enumitemslot.getIndex()] = f;
        }

    }

    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean flag) {
        this.canPickUpLoot = flag;
    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        EquipmentSlot enumitemslot = getEquipmentSlotForItem(itemstack);

        return this.getItemBySlot(enumitemslot).isEmpty() && this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public final InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        if (!this.isAlive()) {
            return InteractionResult.PASS;
        } else if (this.getLeashHolder() == entityhuman) {
            // CraftBukkit start - fire PlayerUnleashEntityEvent
            if (CraftEventFactory.callPlayerUnleashEntityEvent(this, entityhuman).isCancelled()) {
                ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundSetEntityLinkPacket(this, this.getLeashHolder()));
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.dropLeash(true, !entityhuman.abilities.instabuild);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            InteractionResult enuminteractionresult = this.checkAndHandleImportantInteractions(entityhuman, enumhand);

            if (enuminteractionresult.consumesAction()) {
                return enuminteractionresult;
            } else {
                enuminteractionresult = this.mobInteract(entityhuman, enumhand);
                return enuminteractionresult.consumesAction() ? enuminteractionresult : super.interact(entityhuman, enumhand);
            }
        }
    }

    private InteractionResult checkAndHandleImportantInteractions(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.getItem() == Items.LEAD && this.canBeLeashed(entityhuman)) {
            // CraftBukkit start - fire PlayerLeashEntityEvent
            if (CraftEventFactory.callPlayerLeashEntityEvent(this, entityhuman, entityhuman).isCancelled()) {
                ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundSetEntityLinkPacket(this, this.getLeashHolder()));
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.setLeashedTo(entityhuman, true);
            itemstack.shrink(1);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            if (itemstack.getItem() == Items.NAME_TAG) {
                InteractionResult enuminteractionresult = itemstack.interactLivingEntity(entityhuman, (LivingEntity) this, enumhand);

                if (enuminteractionresult.consumesAction()) {
                    return enuminteractionresult;
                }
            }

            if (itemstack.getItem() instanceof SpawnEggItem) {
                if (!this.level.isClientSide) {
                    SpawnEggItem itemmonsteregg = (SpawnEggItem) itemstack.getItem();
                    Optional<Mob> optional = itemmonsteregg.spawnOffspringFromSpawnEgg(entityhuman, this, (EntityType<? extends Mob>) this.getType(), this.level, this.position(), itemstack); // CraftBukkit - decompile error

                    optional.ifPresent((entityinsentient) -> {
                        this.onOffspringSpawnedFromEgg(entityhuman, entityinsentient);
                    });
                    return optional.isPresent() ? InteractionResult.SUCCESS : InteractionResult.PASS;
                } else {
                    return InteractionResult.CONSUME;
                }
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    protected void onOffspringSpawnedFromEgg(Player entityhuman, Mob entityinsentient) {}

    protected InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        return InteractionResult.PASS;
    }

    public boolean isWithinRestriction() {
        return this.isWithinRestriction(this.blockPosition());
    }

    public boolean isWithinRestriction(BlockPos blockposition) {
        return this.restrictRadius == -1.0F ? true : this.restrictCenter.distSqr(blockposition) < (double) (this.restrictRadius * this.restrictRadius);
    }

    public void restrictTo(BlockPos blockposition, int i) {
        this.restrictCenter = blockposition;
        this.restrictRadius = (float) i;
    }

    public BlockPos getRestrictCenter() {
        return this.restrictCenter;
    }

    public float getRestrictRadius() {
        return this.restrictRadius;
    }

    public boolean hasRestriction() {
        return this.restrictRadius != -1.0F;
    }

    @Nullable
    protected <T extends Mob> T convertTo(EntityType<T> entitytypes) {
        if (this.removed) {
            return null;
        } else {
            T t0 = entitytypes.create(this.level); // CraftBukkit - decompile error

            t0.copyPosition(this);
            t0.setCanPickUpLoot(this.canPickUpLoot());
            t0.setBaby(this.isBaby());
            t0.setNoAi(this.isNoAi());
            if (this.hasCustomName()) {
                t0.setCustomName(this.getCustomName());
                t0.setCustomNameVisible(this.isCustomNameVisible());
            }

            if (this.isPersistenceRequired()) {
                t0.setPersistenceRequired();
            }

            t0.setInvulnerable(this.isInvulnerable());
            EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
            int i = aenumitemslot.length;

            for (int j = 0; j < i; ++j) {
                EquipmentSlot enumitemslot = aenumitemslot[j];
                ItemStack itemstack = this.getItemBySlot(enumitemslot);

                if (!itemstack.isEmpty()) {
                    t0.setItemSlot(enumitemslot, itemstack.copy());
                    t0.setDropChance(enumitemslot, this.getEquipmentDropChance(enumitemslot));
                    itemstack.setCount(0);
                }
            }

            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, t0, EntityTransformEvent.TransformReason.DROWNED).isCancelled()) {
                return null;
            }
            this.level.addEntity(t0, CreatureSpawnEvent.SpawnReason.DROWNED);
            // CraftBukkit end
            this.remove();
            return t0;
        }
    }

    protected void tickLeash() {
        if (this.leashInfoTag != null) {
            this.restoreLeashFromSave();
        }

        if (this.leashHolder != null) {
            if (!this.isAlive() || !this.leashHolder.isAlive()) {
                this.level.getServerOH().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), (!this.isAlive()) ? UnleashReason.PLAYER_UNLEASH : UnleashReason.HOLDER_GONE)); // CraftBukkit
                this.dropLeash(true, true);
            }

        }
    }

    public void dropLeash(boolean flag, boolean flag1) {
        if (this.leashHolder != null) {
            this.forcedLoading = false;
            if (!(this.leashHolder instanceof Player)) {
                this.leashHolder.forcedLoading = false;
            }

            this.leashHolder = null;
            this.leashInfoTag = null;
            if (!this.level.isClientSide && flag1) {
                this.forceDrops = true; // CraftBukkit
                this.spawnAtLocation((ItemLike) Items.LEAD);
                this.forceDrops = false; // CraftBukkit
            }

            if (!this.level.isClientSide && flag && this.level instanceof ServerLevel) {
                ((ServerLevel) this.level).getChunkSourceOH().broadcast(this, new ClientboundSetEntityLinkPacket(this, (Entity) null));
            }
        }

    }

    public boolean canBeLeashed(Player entityhuman) {
        return !this.isLeashed() && !(this instanceof Enemy);
    }

    public boolean isLeashed() {
        return this.leashHolder != null;
    }

    @Nullable
    public Entity getLeashHolder() {
        if (this.leashHolder == null && this.delayedLeashHolderId != 0 && this.level.isClientSide) {
            this.leashHolder = this.level.getEntity(this.delayedLeashHolderId);
        }

        return this.leashHolder;
    }

    public void setLeashedTo(Entity entity, boolean flag) {
        this.leashHolder = entity;
        this.leashInfoTag = null;
        this.forcedLoading = true;
        if (!(this.leashHolder instanceof Player)) {
            this.leashHolder.forcedLoading = true;
        }

        if (!this.level.isClientSide && flag && this.level instanceof ServerLevel) {
            ((ServerLevel) this.level).getChunkSourceOH().broadcast(this, new ClientboundSetEntityLinkPacket(this, this.leashHolder));
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

    }

    @Override
    public boolean startRiding(Entity entity, boolean flag) {
        boolean flag1 = super.startRiding(entity, flag);

        if (flag1 && this.isLeashed()) {
            this.level.getServerOH().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), UnleashReason.UNKNOWN)); // CraftBukkit
            this.dropLeash(true, true);
        }

        return flag1;
    }

    private void restoreLeashFromSave() {
        if (this.leashInfoTag != null && this.level instanceof ServerLevel) {
            if (this.leashInfoTag.hasUUID("UUID")) {
                UUID uuid = this.leashInfoTag.getUUID("UUID");
                Entity entity = ((ServerLevel) this.level).getEntity(uuid);

                if (entity != null) {
                    this.setLeashedTo(entity, true);
                    return;
                }
            } else if (this.leashInfoTag.contains("X", 99) && this.leashInfoTag.contains("Y", 99) && this.leashInfoTag.contains("Z", 99)) {
                BlockPos blockposition = new BlockPos(this.leashInfoTag.getInt("X"), this.leashInfoTag.getInt("Y"), this.leashInfoTag.getInt("Z"));

                this.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(this.level, blockposition), true);
                return;
            }

            if (this.tickCount > 100) {
                this.spawnAtLocation((ItemLike) Items.LEAD);
                this.leashInfoTag = null;
            }
        }

    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        EquipmentSlot enumitemslot;

        if (i == 98) {
            enumitemslot = EquipmentSlot.MAINHAND;
        } else if (i == 99) {
            enumitemslot = EquipmentSlot.OFFHAND;
        } else if (i == 100 + EquipmentSlot.HEAD.getIndex()) {
            enumitemslot = EquipmentSlot.HEAD;
        } else if (i == 100 + EquipmentSlot.CHEST.getIndex()) {
            enumitemslot = EquipmentSlot.CHEST;
        } else if (i == 100 + EquipmentSlot.LEGS.getIndex()) {
            enumitemslot = EquipmentSlot.LEGS;
        } else {
            if (i != 100 + EquipmentSlot.FEET.getIndex()) {
                return false;
            }

            enumitemslot = EquipmentSlot.FEET;
        }

        if (!itemstack.isEmpty() && !isValidSlotForItem(enumitemslot, itemstack) && enumitemslot != EquipmentSlot.HEAD) {
            return false;
        } else {
            this.setItemSlot(enumitemslot, itemstack);
            return true;
        }
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return this.canBeControlledByRider() && super.isControlledByLocalInstance();
    }

    public static boolean isValidSlotForItem(EquipmentSlot enumitemslot, ItemStack itemstack) {
        EquipmentSlot enumitemslot1 = getEquipmentSlotForItem(itemstack);

        return enumitemslot1 == enumitemslot || enumitemslot1 == EquipmentSlot.MAINHAND && enumitemslot == EquipmentSlot.OFFHAND || enumitemslot1 == EquipmentSlot.OFFHAND && enumitemslot == EquipmentSlot.MAINHAND;
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    public void setNoAi(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 1) : (byte) (b0 & -2));
    }

    public void setLeftHanded(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 2) : (byte) (b0 & -3));
    }

    public void setAggressive(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 4) : (byte) (b0 & -5));
    }

    public boolean isNoAi() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    public void setBaby(boolean flag) {}

    @Override
    public HumanoidArm getMainArm() {
        return this.isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    @Override
    public boolean canAttack(LivingEntity entityliving) {
        return entityliving.getType() == EntityType.PLAYER && ((Player) entityliving).abilities.invulnerable ? false : super.canAttack(entityliving);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        float f = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float f1 = (float) this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);

        if (entity instanceof LivingEntity) {
            f += EnchantmentHelper.getDamageBonus(this.getMainHandItem(), ((LivingEntity) entity).getMobType());
            f1 += (float) EnchantmentHelper.getKnockbackBonus((LivingEntity) this);
        }

        int i = EnchantmentHelper.getFireAspect(this);

        if (i > 0) {
            // CraftBukkit start - Call a combust event when somebody hits with a fire enchanted item
            EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), i * 4);
            org.bukkit.Bukkit.getPluginManager().callEvent(combustEvent);

            if (!combustEvent.isCancelled()) {
                entity.setOnFire(combustEvent.getDuration(), false);
            }
            // CraftBukkit end
        }

        boolean flag = entity.hurt(DamageSource.mobAttack(this), f);

        if (flag) {
            if (f1 > 0.0F && entity instanceof LivingEntity) {
                ((LivingEntity) entity).knockback(f1 * 0.5F, (double) Mth.sin(this.yRot * 0.017453292F), (double) (-Mth.cos(this.yRot * 0.017453292F)));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }

            if (entity instanceof Player) {
                Player entityhuman = (Player) entity;

                this.maybeDisableShield(entityhuman, this.getMainHandItem(), entityhuman.isUsingItem() ? entityhuman.getUseItem() : ItemStack.EMPTY);
            }

            this.doEnchantDamageEffects((LivingEntity) this, entity);
            this.setLastHurtMob(entity);
        }

        return flag;
    }

    private void maybeDisableShield(Player entityhuman, ItemStack itemstack, ItemStack itemstack1) {
        if (!itemstack.isEmpty() && !itemstack1.isEmpty() && itemstack.getItem() instanceof AxeItem && itemstack1.getItem() == Items.SHIELD) {
            float f = 0.25F + (float) EnchantmentHelper.getBlockEfficiency(this) * 0.05F;

            if (this.random.nextFloat() < f) {
                entityhuman.getCooldowns().addCooldown(Items.SHIELD, 100);
                this.level.broadcastEntityEvent(entityhuman, (byte) 30);
            }
        }

    }

    protected boolean isSunBurnTick() {
        if (this.level.isDay() && !this.level.isClientSide) {
            float f = this.getBrightness();
            BlockPos blockposition = this.getVehicle() instanceof Boat ? (new BlockPos(this.getX(), (double) Math.round(this.getY()), this.getZ())).above() : new BlockPos(this.getX(), (double) Math.round(this.getY()), this.getZ());

            if (f > 0.5F && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && this.level.canSeeSky(blockposition)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void jumpInLiquid(Tag<Fluid> tag) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(tag);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.3D, 0.0D));
        }

    }

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();
        this.level.getServerOH().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), UnleashReason.UNKNOWN)); // CraftBukkit
        this.dropLeash(true, false);
    }
}
