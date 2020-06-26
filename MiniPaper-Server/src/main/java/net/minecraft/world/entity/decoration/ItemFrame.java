package net.minecraft.world.entity.decoration;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ItemFrame extends HangingEntity {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemFrame.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_ROTATION = SynchedEntityData.defineId(ItemFrame.class, EntityDataSerializers.INT);
    private float dropChance = 1.0F;
    private boolean fixed;

    public ItemFrame(EntityType<? extends ItemFrame> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public ItemFrame(Level world, BlockPos blockposition, Direction enumdirection) {
        super(EntityType.ITEM_FRAME, world, blockposition);
        this.setDirection(enumdirection);
    }

    @Override
    protected float getEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.0F;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(ItemFrame.DATA_ITEM, ItemStack.EMPTY);
        this.getEntityData().register(ItemFrame.DATA_ROTATION, 0);
    }

    @Override
    public void setDirection(Direction enumdirection) {
        Validate.notNull(enumdirection);
        this.direction = enumdirection;
        if (enumdirection.getAxis().isHorizontal()) {
            this.xRot = 0.0F;
            this.yRot = (float) (this.direction.get2DDataValue() * 90);
        } else {
            this.xRot = (float) (-90 * enumdirection.getAxisDirection().getStep());
            this.yRot = 0.0F;
        }

        this.xRotO = this.xRot;
        this.yRotO = this.yRot;
        this.recalculateBoundingBox();
    }

    @Override
    protected void recalculateBoundingBox() {
        if (this.direction != null) {
            // CraftBukkit start code moved in to calculateBoundingBox
            this.setBoundingBox(calculateBoundingBox(this, this.pos, this.direction, this.getWidth(), this.getHeight()));
            // CraftBukkit end
        }
    }

    // CraftBukkit start - break out BB calc into own method
    public static AABB calculateBoundingBox(@Nullable Entity entity, BlockPos blockPosition, Direction direction, int width, int height) {
        {
            double d0 = 0.46875D;
            double d1 = (double) blockPosition.getX() + 0.5D - (double) direction.getStepX() * 0.46875D;
            double d2 = (double) blockPosition.getY() + 0.5D - (double) direction.getStepY() * 0.46875D;
            double d3 = (double) blockPosition.getZ() + 0.5D - (double) direction.getStepZ() * 0.46875D;

            if (entity != null) {
                entity.setPosRaw(d1, d2, d3);
            }
            double d4 = (double) width;
            double d5 = (double) height;
            double d6 = (double) width;
            Direction.Axis enumdirection_enumaxis = direction.getAxis();

            switch (enumdirection_enumaxis) {
                case X:
                    d4 = 1.0D;
                    break;
                case Y:
                    d5 = 1.0D;
                    break;
                case Z:
                    d6 = 1.0D;
            }

            d4 /= 32.0D;
            d5 /= 32.0D;
            d6 /= 32.0D;
            return new AABB(d1 - d4, d2 - d5, d3 - d6, d1 + d4, d2 + d5, d3 + d6);
        }
    }
    // CraftBukkit end

    @Override
    public boolean survives() {
        if (this.fixed) {
            return true;
        } else if (!this.level.noCollision(this)) {
            return false;
        } else {
            BlockState iblockdata = this.level.getType(this.pos.relative(this.direction.getOpposite()));

            return !iblockdata.getMaterial().isSolid() && (!this.direction.getAxis().isHorizontal() || !DiodeBlock.isDiode(iblockdata)) ? false : this.level.getEntities(this, this.getBoundingBox(), ItemFrame.HANGING_ENTITY).isEmpty();
        }
    }

    @Override
    public void move(MoverType enummovetype, Vec3 vec3d) {
        if (!this.fixed) {
            super.move(enummovetype, vec3d);
        }

    }

    @Override
    public void push(double d0, double d1, double d2) {
        if (!this.fixed) {
            super.push(d0, d1, d2);
        }

    }

    @Override
    public float getPickRadius() {
        return 0.0F;
    }

    @Override
    public void kill() {
        this.removeFramedMap(this.getItem());
        super.kill();
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.fixed) {
            return damagesource != DamageSource.OUT_OF_WORLD && !damagesource.isCreativePlayer() ? false : super.hurt(damagesource, f);
        } else if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (!damagesource.isExplosion() && !this.getItem().isEmpty()) {
            if (!this.level.isClientSide) {
                // CraftBukkit start - fire EntityDamageEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f, false) || this.removed) {
                    return true;
                }
                // CraftBukkit end
                this.dropItem(damagesource.getEntity(), false);
                this.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
            }

            return true;
        } else {
            return super.hurt(damagesource, f);
        }
    }

    @Override
    public int getWidth() {
        return 12;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        this.playSound(SoundEvents.ITEM_FRAME_BREAK, 1.0F, 1.0F);
        this.dropItem(entity, true);
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.ITEM_FRAME_PLACE, 1.0F, 1.0F);
    }

    private void dropItem(@Nullable Entity entity, boolean flag) {
        if (!this.fixed) {
            if (!this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                if (entity == null) {
                    this.removeFramedMap(this.getItem());
                }

            } else {
                ItemStack itemstack = this.getItem();

                this.setItem(ItemStack.EMPTY);
                if (entity instanceof Player) {
                    Player entityhuman = (Player) entity;

                    if (entityhuman.abilities.instabuild) {
                        this.removeFramedMap(itemstack);
                        return;
                    }
                }

                if (flag) {
                    this.spawnAtLocation((ItemLike) Items.ITEM_FRAME);
                }

                if (!itemstack.isEmpty()) {
                    itemstack = itemstack.copy();
                    this.removeFramedMap(itemstack);
                    if (this.random.nextFloat() < this.dropChance) {
                        this.spawnAtLocation(itemstack);
                    }
                }

            }
        }
    }

    private void removeFramedMap(ItemStack itemstack) {
        if (itemstack.getItem() == Items.FILLED_MAP) {
            MapItemSavedData worldmap = MapItem.getOrCreateSavedData(itemstack, this.level);

            worldmap.removedFromFrame(this.pos, this.getId());
            worldmap.setDirty(true);
        }

        itemstack.setEntityRepresentation((Entity) null);
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemFrame.DATA_ITEM);
    }

    public void setItem(ItemStack itemstack) {
        this.setItem(itemstack, true);
    }

    public void setItem(ItemStack itemstack, boolean flag) {
        // CraftBukkit start
        this.setItem(itemstack, flag, true);
    }

    public void setItem(ItemStack itemstack, boolean flag, boolean playSound) {
        // CraftBukkit end
        if (!itemstack.isEmpty()) {
            itemstack = itemstack.copy();
            itemstack.setCount(1);
            itemstack.setEntityRepresentation((Entity) this);
        }

        this.getEntityData().set(ItemFrame.DATA_ITEM, itemstack);
        if (!itemstack.isEmpty() && playSound) { // CraftBukkit
            this.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F, 1.0F);
        }

        if (flag && this.pos != null) {
            this.level.updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        if (i == 0) {
            this.setItem(itemstack);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (datawatcherobject.equals(ItemFrame.DATA_ITEM)) {
            ItemStack itemstack = this.getItem();

            if (!itemstack.isEmpty() && itemstack.getFrame() != this) {
                itemstack.setEntityRepresentation((Entity) this);
            }
        }

    }

    public int getRotation() {
        return (Integer) this.getEntityData().get(ItemFrame.DATA_ROTATION);
    }

    public void setRotation(int i) {
        this.setRotation(i, true);
    }

    private void setRotation(int i, boolean flag) {
        this.getEntityData().set(ItemFrame.DATA_ROTATION, i % 8);
        if (flag && this.pos != null) {
            this.level.updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (!this.getItem().isEmpty()) {
            nbttagcompound.put("Item", this.getItem().save(new CompoundTag()));
            nbttagcompound.putByte("ItemRotation", (byte) this.getRotation());
            nbttagcompound.putFloat("ItemDropChance", this.dropChance);
        }

        nbttagcompound.putByte("Facing", (byte) this.direction.get3DDataValue());
        nbttagcompound.putBoolean("Invisible", this.isInvisible());
        nbttagcompound.putBoolean("Fixed", this.fixed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Item");

        if (nbttagcompound1 != null && !nbttagcompound1.isEmpty()) {
            ItemStack itemstack = ItemStack.of(nbttagcompound1);

            if (itemstack.isEmpty()) {
                ItemFrame.LOGGER.warn("Unable to load item from: {}", nbttagcompound1);
            }

            ItemStack itemstack1 = this.getItem();

            if (!itemstack1.isEmpty() && !ItemStack.matches(itemstack, itemstack1)) {
                this.removeFramedMap(itemstack1);
            }

            this.setItem(itemstack, false);
            this.setRotation(nbttagcompound.getByte("ItemRotation"), false);
            if (nbttagcompound.contains("ItemDropChance", 99)) {
                this.dropChance = nbttagcompound.getFloat("ItemDropChance");
            }
        }

        this.setDirection(Direction.from3DDataValue(nbttagcompound.getByte("Facing")));
        this.setInvisible(nbttagcompound.getBoolean("Invisible"));
        this.fixed = nbttagcompound.getBoolean("Fixed");
    }

    @Override
    public InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        boolean flag = !this.getItem().isEmpty();
        boolean flag1 = !itemstack.isEmpty();

        if (this.fixed) {
            return InteractionResult.PASS;
        } else if (!this.level.isClientSide) {
            if (!flag) {
                if (flag1 && !this.removed) {
                    this.setItem(itemstack);
                    if (!entityhuman.abilities.instabuild) {
                        itemstack.shrink(1);
                    }
                }
            } else {
                this.playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
            }

            return InteractionResult.CONSUME;
        } else {
            return !flag && !flag1 ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
    }

    public int getAnalogOutput() {
        return this.getItem().isEmpty() ? 0 : this.getRotation() % 8 + 1;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, this.getType(), this.direction.get3DDataValue(), this.getPos());
    }
}
