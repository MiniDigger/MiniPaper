package net.minecraft.world.entity.item;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
// CraftBukkit end

public class ItemEntity extends Entity {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    public int age;
    public int pickupDelay;
    private int health;
    private UUID thrower;
    private UUID owner;
    public final float bobOffs;
    private int lastTick = MinecraftServer.currentTick - 1; // CraftBukkit

    public ItemEntity(EntityType<? extends ItemEntity> entitytypes, Level world) {
        super(entitytypes, world);
        this.health = 5;
        this.bobOffs = (float) (Math.random() * 3.141592653589793D * 2.0D);
    }

    public ItemEntity(Level world, double d0, double d1, double d2) {
        this(EntityType.ITEM, world);
        this.setPos(d0, d1, d2);
        this.yRot = this.random.nextFloat() * 360.0F;
        this.setDeltaMovement(this.random.nextDouble() * 0.2D - 0.1D, 0.2D, this.random.nextDouble() * 0.2D - 0.1D);
    }

    public ItemEntity(Level world, double d0, double d1, double d2, ItemStack itemstack) {
        this(world, d0, d1, d2);
        this.setItem(itemstack);
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(ItemEntity.DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.remove();
        } else {
            super.tick();
            // CraftBukkit start - Use wall time for pickup and despawn timers
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
            if (this.age != -32768) this.age += elapsedTicks;
            this.lastTick = MinecraftServer.currentTick;
            // CraftBukkit end

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 vec3d = this.getDeltaMovement();

            if (this.isEyeInFluid((Tag) FluidTags.WATER)) {
                this.setUnderwaterMovement();
            } else if (this.isEyeInFluid((Tag) FluidTags.LAVA)) {
                this.setUnderLavaMovement();
            } else if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            if (this.level.isClientSide) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level.noCollision(this);
                if (this.noPhysics) {
                    this.checkInBlock(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                }
            }

            if (!this.onGround || getHorizontalDistanceSqr(this.getDeltaMovement()) > 9.999999747378752E-6D || (this.tickCount + this.getId()) % 4 == 0) {
                this.move(MoverType.SELF, this.getDeltaMovement());
                float f = 0.98F;

                if (this.onGround) {
                    f = this.level.getType(new BlockPos(this.getX(), this.getY() - 1.0D, this.getZ())).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.98D, (double) f));
                if (this.onGround) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, -0.5D, 1.0D));
                }
            }

            boolean flag = Mth.floor(this.xo) != Mth.floor(this.getX()) || Mth.floor(this.yo) != Mth.floor(this.getY()) || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int i = flag ? 2 : 40;

            if (this.tickCount % i == 0) {
                if (this.level.getFluidState(this.blockPosition()).is((Tag) FluidTags.LAVA) && !this.fireImmune()) {
                    this.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
                }

                if (!this.level.isClientSide && this.isMergable()) {
                    this.mergeWithNeighbours();
                }
            }

            /* CraftBukkit start - moved up
            if (this.age != -32768) {
                ++this.age;
            }
            // CraftBukkit end */

            this.hasImpulse |= this.updateInWaterStateAndDoFluidPushing();
            if (!this.level.isClientSide) {
                double d0 = this.getDeltaMovement().subtract(vec3d).lengthSqr();

                if (d0 > 0.01D) {
                    this.hasImpulse = true;
                }
            }

            if (!this.level.isClientSide && this.age >= level.spigotConfig.itemDespawnRate) { // Spigot
                // CraftBukkit start - fire ItemDespawnEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                    this.age = 0;
                    return;
                }
                // CraftBukkit end
                this.remove();
            }

        }
    }

    // Spigot start - copied from above
    @Override
    public void inactiveTick() {
        // CraftBukkit start - Use wall time for pickup and despawn timers
        int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
        if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
        if (this.age != -32768) this.age += elapsedTicks;
        this.lastTick = MinecraftServer.currentTick;
        // CraftBukkit end

        if (!this.level.isClientSide && this.age >= level.spigotConfig.itemDespawnRate) { // Spigot
            // CraftBukkit start - fire ItemDespawnEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                this.age = 0;
                return;
            }
            // CraftBukkit end
            this.remove();
        }
    }
    // Spigot end

    private void setUnderwaterMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.9900000095367432D, vec3d.y + (double) (vec3d.y < 0.05999999865889549D ? 5.0E-4F : 0.0F), vec3d.z * 0.9900000095367432D);
    }

    private void setUnderLavaMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.949999988079071D, vec3d.y + (double) (vec3d.y < 0.05999999865889549D ? 5.0E-4F : 0.0F), vec3d.z * 0.949999988079071D);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            // Spigot start
            double radius = level.spigotConfig.itemMerge;
            List<ItemEntity> list = this.level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(radius, radius, radius), (entityitem) -> {
                // Spigot end
                return entityitem != this && entityitem.isMergable();
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemEntity entityitem = (ItemEntity) iterator.next();

                if (entityitem.isMergable()) {
                    this.tryToMerge(entityitem);
                    if (this.removed) {
                        break;
                    }
                }
            }

        }
    }

    private boolean isMergable() {
        ItemStack itemstack = this.getItem();

        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < 6000 && itemstack.getCount() < itemstack.getMaxStackSize();
    }

    private void tryToMerge(ItemEntity entityitem) {
        ItemStack itemstack = this.getItem();
        ItemStack itemstack1 = entityitem.getItem();

        if (Objects.equals(this.getOwner(), entityitem.getOwner()) && areMergable(itemstack, itemstack1)) {
            if (true || itemstack1.getCount() < itemstack.getCount()) { // Spigot
                merge(this, itemstack, entityitem, itemstack1);
            } else {
                merge(entityitem, itemstack1, this, itemstack);
            }

        }
    }

    public static boolean areMergable(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack1.getItem() != itemstack.getItem() ? false : (itemstack1.getCount() + itemstack.getCount() > itemstack1.getMaxStackSize() ? false : (itemstack1.hasTag() ^ itemstack.hasTag() ? false : !itemstack1.hasTag() || itemstack1.getTag().equals(itemstack.getTag())));
    }

    public static ItemStack merge(ItemStack itemstack, ItemStack itemstack1, int i) {
        int j = Math.min(Math.min(itemstack.getMaxStackSize(), i) - itemstack.getCount(), itemstack1.getCount());
        ItemStack itemstack2 = itemstack.copy();

        itemstack2.grow(j);
        itemstack1.shrink(j);
        return itemstack2;
    }

    private static void merge(ItemEntity entityitem, ItemStack itemstack, ItemStack itemstack1) {
        ItemStack itemstack2 = merge(itemstack, itemstack1, 64);

        if (!itemstack2.isEmpty()) entityitem.setItem(itemstack2); // CraftBukkit - don't set empty stacks
    }

    private static void merge(ItemEntity entityitem, ItemStack itemstack, ItemEntity entityitem1, ItemStack itemstack1) {
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemMergeEvent(entityitem1, entityitem).isCancelled()) return; // CraftBukkit
        merge(entityitem, itemstack, itemstack1);
        entityitem.pickupDelay = Math.max(entityitem.pickupDelay, entityitem1.pickupDelay);
        entityitem.age = Math.min(entityitem.age, entityitem1.age);
        if (itemstack1.isEmpty()) {
            entityitem1.remove();
        }

    }

    @Override
    public boolean fireImmune() {
        return this.getItem().getItem().isFireResistant() || super.fireImmune();
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (!this.getItem().isEmpty() && this.getItem().getItem() == Items.NETHER_STAR && damagesource.isExplosion()) {
            return false;
        } else if (!this.getItem().getItem().canBeHurtBy(damagesource)) {
            return false;
        } else {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f)) {
                return false;
            }
            // CraftBukkit end
            this.markHurt();
            this.health = (int) ((float) this.health - f);
            if (this.health <= 0) {
                this.remove();
            }

            return false;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putShort("Health", (short) this.health);
        nbttagcompound.putShort("Age", (short) this.age);
        nbttagcompound.putShort("PickupDelay", (short) this.pickupDelay);
        if (this.getThrower() != null) {
            nbttagcompound.putUUID("Thrower", this.getThrower());
        }

        if (this.getOwner() != null) {
            nbttagcompound.putUUID("Owner", this.getOwner());
        }

        if (!this.getItem().isEmpty()) {
            nbttagcompound.put("Item", this.getItem().save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.health = nbttagcompound.getShort("Health");
        this.age = nbttagcompound.getShort("Age");
        if (nbttagcompound.contains("PickupDelay")) {
            this.pickupDelay = nbttagcompound.getShort("PickupDelay");
        }

        if (nbttagcompound.hasUUID("Owner")) {
            this.owner = nbttagcompound.getUUID("Owner");
        }

        if (nbttagcompound.hasUUID("Thrower")) {
            this.thrower = nbttagcompound.getUUID("Thrower");
        }

        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Item");

        this.setItem(ItemStack.of(nbttagcompound1));
        if (this.getItem().isEmpty()) {
            this.remove();
        }

    }

    @Override
    public void playerTouch(Player entityhuman) {
        if (!this.level.isClientSide) {
            ItemStack itemstack = this.getItem();
            Item item = itemstack.getItem();
            int i = itemstack.getCount();

            // CraftBukkit start - fire PlayerPickupItemEvent
            int canHold = entityhuman.inventory.canHold(itemstack);
            int remaining = i - canHold;

            if (this.pickupDelay <= 0 && canHold > 0) {
                itemstack.setCount(canHold);
                // Call legacy event
                PlayerPickupItemEvent playerEvent = new PlayerPickupItemEvent((org.bukkit.entity.Player) entityhuman.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                playerEvent.setCancelled(!entityhuman.canPickUpLoot);
                this.level.getServerOH().getPluginManager().callEvent(playerEvent);
                if (playerEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                // Call newer event afterwards
                EntityPickupItemEvent entityEvent = new EntityPickupItemEvent((org.bukkit.entity.Player) entityhuman.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                entityEvent.setCancelled(!entityhuman.canPickUpLoot);
                this.level.getServerOH().getPluginManager().callEvent(entityEvent);
                if (entityEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                itemstack.setCount(canHold + remaining); // = i

                // Possibly < 0; fix here so we do not have to modify code below
                this.pickupDelay = 0;
            } else if (this.pickupDelay == 0) {
                // ensure that the code below isn't triggered if canHold says we can't pick the items up
                this.pickupDelay = -1;
            }
            // CraftBukkit end

            if (this.pickupDelay == 0 && (this.owner == null || this.owner.equals(entityhuman.getUUID())) && entityhuman.inventory.add(itemstack)) {
                entityhuman.take(this, i);
                if (itemstack.isEmpty()) {
                    this.remove();
                    itemstack.setCount(i);
                }

                entityhuman.awardStat(Stats.ITEM_PICKED_UP.get(item), i);
                entityhuman.onItemPickup(this);
            }

        }
    }

    @Override
    public Component getName() {
        Component ichatbasecomponent = this.getCustomName();

        return (Component) (ichatbasecomponent != null ? ichatbasecomponent : new TranslatableComponent(this.getItem().getDescriptionId()));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel worldserver) {
        Entity entity = super.changeDimension(worldserver);

        if (!this.level.isClientSide && entity instanceof ItemEntity) {
            ((ItemEntity) entity).mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemEntity.DATA_ITEM);
    }

    public void setItem(ItemStack itemstack) {
        com.google.common.base.Preconditions.checkArgument(!itemstack.isEmpty(), "Cannot drop air"); // CraftBukkit
        this.getEntityData().set(ItemEntity.DATA_ITEM, itemstack);
        this.getEntityData().markDirty(ItemEntity.DATA_ITEM); // CraftBukkit - SPIGOT-4591, must mark dirty
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (ItemEntity.DATA_ITEM.equals(datawatcherobject)) {
            this.getItem().setEntityRepresentation((Entity) this);
        }

    }

    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID uuid) {
        this.owner = uuid;
    }

    @Nullable
    public UUID getThrower() {
        return this.thrower;
    }

    public void setThrower(@Nullable UUID uuid) {
        this.thrower = uuid;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int i) {
        this.pickupDelay = i;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = level.spigotConfig.itemDespawnRate - 1; // Spigot
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
