package net.minecraft.world.entity.decoration;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
// CraftBukkit end

public class ArmorStand extends LivingEntity {

    private static final Rotations DEFAULT_HEAD_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    private static final Rotations DEFAULT_BODY_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    private static final Rotations DEFAULT_LEFT_ARM_POSE = new Rotations(-10.0F, 0.0F, -10.0F);
    private static final Rotations DEFAULT_RIGHT_ARM_POSE = new Rotations(-15.0F, 0.0F, 10.0F);
    private static final Rotations DEFAULT_LEFT_LEG_POSE = new Rotations(-1.0F, 0.0F, -1.0F);
    private static final Rotations DEFAULT_RIGHT_LEG_POSE = new Rotations(1.0F, 0.0F, 1.0F);
    public static final EntityDataAccessor<Byte> DATA_CLIENT_FLAGS = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Rotations> DATA_HEAD_POSE = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_BODY_POSE = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_ARM_POSE = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_ARM_POSE = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_LEG_POSE = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_LEG_POSE = SynchedEntityData.defineId(net.minecraft.world.entity.decoration.ArmorStand.class, EntityDataSerializers.ROTATIONS);
    private static final Predicate<Entity> RIDABLE_MINECARTS = (entity) -> {
        return entity instanceof AbstractMinecart && ((AbstractMinecart) entity).getMinecartType() == AbstractMinecart.Type.RIDEABLE;
    };
    private final NonNullList<ItemStack> handItems;
    private final NonNullList<ItemStack> armorItems;
    private boolean invisible;
    public long lastHit;
    private int disabledSlots;
    public Rotations headPose;
    public Rotations bodyPose;
    public Rotations leftArmPose;
    public Rotations rightArmPose;
    public Rotations leftLegPose;
    public Rotations rightLegPose;

    public ArmorStand(EntityType<? extends net.minecraft.world.entity.decoration.ArmorStand> entitytypes, Level world) {
        super(entitytypes, world);
        this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
        this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
        this.headPose = net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_HEAD_POSE;
        this.bodyPose = net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_BODY_POSE;
        this.leftArmPose = net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_ARM_POSE;
        this.rightArmPose = net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_ARM_POSE;
        this.leftLegPose = net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_LEG_POSE;
        this.rightLegPose = net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_LEG_POSE;
        this.maxUpStep = 0.0F;
    }

    public ArmorStand(Level world, double d0, double d1, double d2) {
        this(EntityType.ARMOR_STAND, world);
        this.setPos(d0, d1, d2);
    }

    // CraftBukkit start - SPIGOT-3607, SPIGOT-3637
    @Override
    public float getBukkitYaw() {
        return this.yRot;
    }
    // CraftBukkit end

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    private boolean hasPhysics() {
        return !this.isMarker() && !this.isNoGravity();
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.hasPhysics();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS, (byte) 0);
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_HEAD_POSE, net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_HEAD_POSE);
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_BODY_POSE, net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_BODY_POSE);
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_LEFT_ARM_POSE, net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_ARM_POSE);
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_RIGHT_ARM_POSE, net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_ARM_POSE);
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_LEFT_LEG_POSE, net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_LEG_POSE);
        this.entityData.register(net.minecraft.world.entity.decoration.ArmorStand.DATA_RIGHT_LEG_POSE, net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_LEG_POSE);
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
    public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot enumitemslot) {
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
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot enumitemslot, ItemStack itemstack) {
        switch (enumitemslot.getType()) {
            case HAND:
                this.playEquipSound(itemstack);
                this.handItems.set(enumitemslot.getIndex(), itemstack);
                break;
            case ARMOR:
                this.playEquipSound(itemstack);
                this.armorItems.set(enumitemslot.getIndex(), itemstack);
        }

    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        net.minecraft.world.entity.EquipmentSlot enumitemslot;

        if (i == 98) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        } else if (i == 99) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.OFFHAND;
        } else if (i == 100 + net.minecraft.world.entity.EquipmentSlot.HEAD.getIndex()) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.HEAD;
        } else if (i == 100 + net.minecraft.world.entity.EquipmentSlot.CHEST.getIndex()) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.CHEST;
        } else if (i == 100 + net.minecraft.world.entity.EquipmentSlot.LEGS.getIndex()) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.LEGS;
        } else {
            if (i != 100 + net.minecraft.world.entity.EquipmentSlot.FEET.getIndex()) {
                return false;
            }

            enumitemslot = net.minecraft.world.entity.EquipmentSlot.FEET;
        }

        if (!itemstack.isEmpty() && !Mob.isValidSlotForItem(enumitemslot, itemstack) && enumitemslot != net.minecraft.world.entity.EquipmentSlot.HEAD) {
            return false;
        } else {
            this.setItemSlot(enumitemslot, itemstack);
            return true;
        }
    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        net.minecraft.world.entity.EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

        return this.getItemBySlot(enumitemslot).isEmpty() && !this.isDisabled(enumitemslot);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
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
        nbttagcompound.putBoolean("Invisible", this.isInvisible());
        nbttagcompound.putBoolean("Small", this.isSmall());
        nbttagcompound.putBoolean("ShowArms", this.isShowArms());
        nbttagcompound.putInt("DisabledSlots", this.disabledSlots);
        nbttagcompound.putBoolean("NoBasePlate", this.isNoBasePlate());
        if (this.isMarker()) {
            nbttagcompound.putBoolean("Marker", this.isMarker());
        }

        nbttagcompound.put("Pose", this.writePose());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
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

        this.setInvisible(nbttagcompound.getBoolean("Invisible"));
        this.setSmall(nbttagcompound.getBoolean("Small"));
        this.setShowArms(nbttagcompound.getBoolean("ShowArms"));
        this.disabledSlots = nbttagcompound.getInt("DisabledSlots");
        this.setNoBasePlate(nbttagcompound.getBoolean("NoBasePlate"));
        this.setMarker(nbttagcompound.getBoolean("Marker"));
        this.noPhysics = !this.hasPhysics();
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Pose");

        this.readPose(nbttagcompound1);
    }

    private void readPose(CompoundTag nbttagcompound) {
        ListTag nbttaglist = nbttagcompound.getList("Head", 5);

        this.setHeadPose(nbttaglist.isEmpty() ? net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_HEAD_POSE : new Rotations(nbttaglist));
        ListTag nbttaglist1 = nbttagcompound.getList("Body", 5);

        this.setBodyPose(nbttaglist1.isEmpty() ? net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_BODY_POSE : new Rotations(nbttaglist1));
        ListTag nbttaglist2 = nbttagcompound.getList("LeftArm", 5);

        this.setLeftArmPose(nbttaglist2.isEmpty() ? net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_ARM_POSE : new Rotations(nbttaglist2));
        ListTag nbttaglist3 = nbttagcompound.getList("RightArm", 5);

        this.setRightArmPose(nbttaglist3.isEmpty() ? net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_ARM_POSE : new Rotations(nbttaglist3));
        ListTag nbttaglist4 = nbttagcompound.getList("LeftLeg", 5);

        this.setLeftLegPose(nbttaglist4.isEmpty() ? net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_LEG_POSE : new Rotations(nbttaglist4));
        ListTag nbttaglist5 = nbttagcompound.getList("RightLeg", 5);

        this.setRightLegPose(nbttaglist5.isEmpty() ? net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_LEG_POSE : new Rotations(nbttaglist5));
    }

    private CompoundTag writePose() {
        CompoundTag nbttagcompound = new CompoundTag();

        if (!net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_HEAD_POSE.equals(this.headPose)) {
            nbttagcompound.put("Head", this.headPose.save());
        }

        if (!net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_BODY_POSE.equals(this.bodyPose)) {
            nbttagcompound.put("Body", this.bodyPose.save());
        }

        if (!net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_ARM_POSE.equals(this.leftArmPose)) {
            nbttagcompound.put("LeftArm", this.leftArmPose.save());
        }

        if (!net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_ARM_POSE.equals(this.rightArmPose)) {
            nbttagcompound.put("RightArm", this.rightArmPose.save());
        }

        if (!net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_LEFT_LEG_POSE.equals(this.leftLegPose)) {
            nbttagcompound.put("LeftLeg", this.leftLegPose.save());
        }

        if (!net.minecraft.world.entity.decoration.ArmorStand.DEFAULT_RIGHT_LEG_POSE.equals(this.rightLegPose)) {
            nbttagcompound.put("RightLeg", this.rightLegPose.save());
        }

        return nbttagcompound;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {
        List<Entity> list = this.level.getEntities(this, this.getBoundingBox(), net.minecraft.world.entity.decoration.ArmorStand.RIDABLE_MINECARTS);

        for (int i = 0; i < list.size(); ++i) {
            Entity entity = (Entity) list.get(i);

            if (this.distanceToSqr(entity) <= 0.2D) {
                entity.push(this);
            }
        }

    }

    @Override
    public InteractionResult interactAt(net.minecraft.world.entity.player.Player entityhuman, Vec3 vec3d, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (!this.isMarker() && itemstack.getItem() != Items.NAME_TAG) {
            if (entityhuman.isSpectator()) {
                return InteractionResult.SUCCESS;
            } else if (entityhuman.level.isClientSide) {
                return InteractionResult.CONSUME;
            } else {
                net.minecraft.world.entity.EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

                if (itemstack.isEmpty()) {
                    net.minecraft.world.entity.EquipmentSlot enumitemslot1 = this.getClickedSlot(vec3d);
                    net.minecraft.world.entity.EquipmentSlot enumitemslot2 = this.isDisabled(enumitemslot1) ? enumitemslot : enumitemslot1;

                    if (this.hasItemInSlot(enumitemslot2) && this.swapItem(entityhuman, enumitemslot2, itemstack, enumhand)) {
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    if (this.isDisabled(enumitemslot)) {
                        return InteractionResult.FAIL;
                    }

                    if (enumitemslot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND && !this.isShowArms()) {
                        return InteractionResult.FAIL;
                    }

                    if (this.swapItem(entityhuman, enumitemslot, itemstack, enumhand)) {
                        return InteractionResult.SUCCESS;
                    }
                }

                return InteractionResult.PASS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private net.minecraft.world.entity.EquipmentSlot getClickedSlot(Vec3 vec3d) {
        net.minecraft.world.entity.EquipmentSlot enumitemslot = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        boolean flag = this.isSmall();
        double d0 = flag ? vec3d.y * 2.0D : vec3d.y;
        net.minecraft.world.entity.EquipmentSlot enumitemslot1 = net.minecraft.world.entity.EquipmentSlot.FEET;

        if (d0 >= 0.1D && d0 < 0.1D + (flag ? 0.8D : 0.45D) && this.hasItemInSlot(enumitemslot1)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.FEET;
        } else if (d0 >= 0.9D + (flag ? 0.3D : 0.0D) && d0 < 0.9D + (flag ? 1.0D : 0.7D) && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.CHEST;
        } else if (d0 >= 0.4D && d0 < 0.4D + (flag ? 1.0D : 0.8D) && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.LEGS)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.LEGS;
        } else if (d0 >= 1.6D && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.HEAD;
        } else if (!this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND) && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.OFFHAND;
        }

        return enumitemslot;
    }

    private boolean isDisabled(net.minecraft.world.entity.EquipmentSlot enumitemslot) {
        return (this.disabledSlots & 1 << enumitemslot.getFilterFlag()) != 0 || enumitemslot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND && !this.isShowArms();
    }

    private boolean swapItem(net.minecraft.world.entity.player.Player entityhuman, net.minecraft.world.entity.EquipmentSlot enumitemslot, ItemStack itemstack, InteractionHand enumhand) {
        ItemStack itemstack1 = this.getItemBySlot(enumitemslot);

        if (!itemstack1.isEmpty() && (this.disabledSlots & 1 << enumitemslot.getFilterFlag() + 8) != 0) {
            return false;
        } else if (itemstack1.isEmpty() && (this.disabledSlots & 1 << enumitemslot.getFilterFlag() + 16) != 0) {
            return false;
        } else {
            ItemStack itemstack2;
            // CraftBukkit start
            org.bukkit.inventory.ItemStack armorStandItem = CraftItemStack.asCraftMirror(itemstack1);
            org.bukkit.inventory.ItemStack playerHeldItem = CraftItemStack.asCraftMirror(itemstack);

            Player player = (Player) entityhuman.getBukkitEntity();
            org.bukkit.entity.ArmorStand self = (org.bukkit.entity.ArmorStand) this.getBukkitEntity();

            EquipmentSlot slot = CraftEquipmentSlot.getSlot(enumitemslot);
            PlayerArmorStandManipulateEvent armorStandManipulateEvent = new PlayerArmorStandManipulateEvent(player,self,playerHeldItem,armorStandItem,slot);
            this.level.getServerOH().getPluginManager().callEvent(armorStandManipulateEvent);

            if (armorStandManipulateEvent.isCancelled()) {
                return true;
            }
            // CraftBukkit end

            if (entityhuman.abilities.instabuild && itemstack1.isEmpty() && !itemstack.isEmpty()) {
                itemstack2 = itemstack.copy();
                itemstack2.setCount(1);
                this.setItemSlot(enumitemslot, itemstack2);
                return true;
            } else if (!itemstack.isEmpty() && itemstack.getCount() > 1) {
                if (!itemstack1.isEmpty()) {
                    return false;
                } else {
                    itemstack2 = itemstack.copy();
                    itemstack2.setCount(1);
                    this.setItemSlot(enumitemslot, itemstack2);
                    itemstack.shrink(1);
                    return true;
                }
            } else {
                this.setItemSlot(enumitemslot, itemstack);
                entityhuman.setItemInHand(enumhand, itemstack1);
                return true;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (!this.level.isClientSide && !this.removed) {
            if (DamageSource.OUT_OF_WORLD.equals(damagesource)) {
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f)) {
                    return false;
                }
                // CraftBukkit end
                this.kill(); // CraftBukkit - this.die() -> this.killEntity()
                return false;
            } else if (!this.isInvulnerableTo(damagesource) && (true || !this.invisible) && !this.isMarker()) { // CraftBukkit
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f, true, this.invisible)) {
                    return false;
                }
                // CraftBukkit end
                if (damagesource.isExplosion()) {
                    this.brokenByAnything(damagesource);
                    this.kill(); // CraftBukkit - this.die() -> this.killEntity()
                    return false;
                } else if (DamageSource.IN_FIRE.equals(damagesource)) {
                    if (this.isOnFire()) {
                        this.causeDamage(damagesource, 0.15F);
                    } else {
                        this.setSecondsOnFire(5);
                    }

                    return false;
                } else if (DamageSource.ON_FIRE.equals(damagesource) && this.getHealth() > 0.5F) {
                    this.causeDamage(damagesource, 4.0F);
                    return false;
                } else {
                    boolean flag = damagesource.getDirectEntity() instanceof AbstractArrow;
                    boolean flag1 = flag && ((AbstractArrow) damagesource.getDirectEntity()).getPierceLevel() > 0;
                    boolean flag2 = "player".equals(damagesource.getMsgId());

                    if (!flag2 && !flag) {
                        return false;
                    } else if (damagesource.getEntity() instanceof net.minecraft.world.entity.player.Player && !((net.minecraft.world.entity.player.Player) damagesource.getEntity()).abilities.mayBuild) {
                        return false;
                    } else if (damagesource.isCreativePlayer()) {
                        this.playBrokenSound();
                        this.showBreakingParticles();
                        this.kill(); // CraftBukkit - this.die() -> this.killEntity()
                        return flag1;
                    } else {
                        long i = this.level.getGameTime();

                        if (i - this.lastHit > 5L && !flag) {
                            this.level.broadcastEntityEvent(this, (byte) 32);
                            this.lastHit = i;
                        } else {
                            this.brokenByPlayer(damagesource);
                            this.showBreakingParticles();
                            this.remove(); // CraftBukkit - SPIGOT-4890: remain as this.die() since above damagesource method will call death event
                        }

                        return true;
                    }
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void showBreakingParticles() {
        if (this.level instanceof ServerLevel) {
            ((ServerLevel) this.level).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getBlockData()), this.getX(), this.getY(0.6666666666666666D), this.getZ(), 10, (double) (this.getBbWidth() / 4.0F), (double) (this.getBbHeight() / 4.0F), (double) (this.getBbWidth() / 4.0F), 0.05D);
        }

    }

    private void causeDamage(DamageSource damagesource, float f) {
        float f1 = this.getHealth();

        f1 -= f;
        if (f1 <= 0.5F) {
            this.brokenByAnything(damagesource);
            this.kill(); // CraftBukkit - this.die() -> this.killEntity()
        } else {
            this.setHealth(f1);
        }

    }

    private void brokenByPlayer(DamageSource damagesource) {
        drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(new ItemStack(Items.ARMOR_STAND))); // CraftBukkit - add to drops
        this.brokenByAnything(damagesource);
    }

    private void brokenByAnything(DamageSource damagesource) {
        this.playBrokenSound();
        // this.d(damagesource); // CraftBukkit - moved down

        ItemStack itemstack;
        int i;

        for (i = 0; i < this.handItems.size(); ++i) {
            itemstack = (ItemStack) this.handItems.get(i);
            if (!itemstack.isEmpty()) {
                drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack)); // CraftBukkit - add to drops
                this.handItems.set(i, ItemStack.EMPTY);
            }
        }

        for (i = 0; i < this.armorItems.size(); ++i) {
            itemstack = (ItemStack) this.armorItems.get(i);
            if (!itemstack.isEmpty()) {
                drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack)); // CraftBukkit - add to drops
                this.armorItems.set(i, ItemStack.EMPTY);
            }
        }
        this.dropAllDeathLoot(damagesource); // CraftBukkit - moved from above

    }

    private void playBrokenSound() {
        this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    protected float tickHeadTurn(float f, float f1) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.yRot;
        return 0.0F;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * (this.isBaby() ? 0.5F : 0.9F);
    }

    @Override
    public double getMyRidingOffset() {
        return this.isMarker() ? 0.0D : 0.10000000149011612D;
    }

    @Override
    public void travel(Vec3 vec3d) {
        if (this.hasPhysics()) {
            super.travel(vec3d);
        }
    }

    @Override
    public void setYBodyRot(float f) {
        this.yBodyRotO = this.yRotO = f;
        this.yHeadRotO = this.yHeadRot = f;
    }

    @Override
    public void setYHeadRot(float f) {
        this.yBodyRotO = this.yRotO = f;
        this.yHeadRotO = this.yHeadRot = f;
    }

    @Override
    public void tick() {
        super.tick();
        Rotations vector3f = (Rotations) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_HEAD_POSE);

        if (!this.headPose.equals(vector3f)) {
            this.setHeadPose(vector3f);
        }

        Rotations vector3f1 = (Rotations) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_BODY_POSE);

        if (!this.bodyPose.equals(vector3f1)) {
            this.setBodyPose(vector3f1);
        }

        Rotations vector3f2 = (Rotations) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_LEFT_ARM_POSE);

        if (!this.leftArmPose.equals(vector3f2)) {
            this.setLeftArmPose(vector3f2);
        }

        Rotations vector3f3 = (Rotations) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_RIGHT_ARM_POSE);

        if (!this.rightArmPose.equals(vector3f3)) {
            this.setRightArmPose(vector3f3);
        }

        Rotations vector3f4 = (Rotations) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_LEFT_LEG_POSE);

        if (!this.leftLegPose.equals(vector3f4)) {
            this.setLeftLegPose(vector3f4);
        }

        Rotations vector3f5 = (Rotations) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_RIGHT_LEG_POSE);

        if (!this.rightLegPose.equals(vector3f5)) {
            this.setRightLegPose(vector3f5);
        }

    }

    @Override
    protected void updateInvisibilityStatus() {
        this.setInvisible(this.invisible);
    }

    @Override
    public void setInvisible(boolean flag) {
        this.invisible = flag;
        super.setInvisible(flag);
    }

    @Override
    public boolean isBaby() {
        return this.isSmall();
    }

    // CraftBukkit start
    @Override
    protected boolean shouldDropExperience() {
        return true; // MC-157395, SPIGOT-5193 even baby (small) armor stands should drop
    }
    // CraftBukkit end

    @Override
    public void kill() {
        org.bukkit.craftbukkit.event.CraftEventFactory.callEntityDeathEvent(this, drops); // CraftBukkit - call event
        this.remove();
    }

    @Override
    public boolean ignoreExplosion() {
        return this.isInvisible();
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return this.isMarker() ? PushReaction.IGNORE : super.getPistonPushReaction();
    }

    public void setSmall(boolean flag) {
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS), 1, flag));
    }

    public boolean isSmall() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS) & 1) != 0;
    }

    public void setShowArms(boolean flag) {
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS), 4, flag));
    }

    public boolean isShowArms() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS) & 4) != 0;
    }

    public void setNoBasePlate(boolean flag) {
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS), 8, flag));
    }

    public boolean isNoBasePlate() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS) & 8) != 0;
    }

    public void setMarker(boolean flag) {
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS), 16, flag));
    }

    public boolean isMarker() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS) & 16) != 0;
    }

    private byte setBit(byte b0, int i, boolean flag) {
        if (flag) {
            b0 = (byte) (b0 | i);
        } else {
            b0 = (byte) (b0 & ~i);
        }

        return b0;
    }

    public void setHeadPose(Rotations vector3f) {
        this.headPose = vector3f;
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_HEAD_POSE, vector3f);
    }

    public void setBodyPose(Rotations vector3f) {
        this.bodyPose = vector3f;
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_BODY_POSE, vector3f);
    }

    public void setLeftArmPose(Rotations vector3f) {
        this.leftArmPose = vector3f;
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_LEFT_ARM_POSE, vector3f);
    }

    public void setRightArmPose(Rotations vector3f) {
        this.rightArmPose = vector3f;
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_RIGHT_ARM_POSE, vector3f);
    }

    public void setLeftLegPose(Rotations vector3f) {
        this.leftLegPose = vector3f;
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_LEFT_LEG_POSE, vector3f);
    }

    public void setRightLegPose(Rotations vector3f) {
        this.rightLegPose = vector3f;
        this.entityData.set(net.minecraft.world.entity.decoration.ArmorStand.DATA_RIGHT_LEG_POSE, vector3f);
    }

    public Rotations getHeadPose() {
        return this.headPose;
    }

    public Rotations getBodyPose() {
        return this.bodyPose;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isMarker();
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player && !this.level.mayInteract((net.minecraft.world.entity.player.Player) entity, this.blockPosition());
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected SoundEvent getSoundFall(int i) {
        return SoundEvents.ARMOR_STAND_FALL;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    @Override
    public void thunderHit(LightningBolt entitylightning) {}

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS.equals(datawatcherobject)) {
            this.refreshDimensions();
            this.blocksBuilding = !this.isMarker();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        float f = this.isMarker() ? 0.0F : (this.isBaby() ? 0.5F : 1.0F);

        return this.getType().getDimensions().scale(f);
    }
}
