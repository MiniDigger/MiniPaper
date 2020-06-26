package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class MushroomCow extends Cow implements Shearable {

    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.STRING);
    private MobEffect effect;
    private int effectDuration;
    private UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    public float getWalkTargetValue(BlockPos blockposition, LevelReader iworldreader) {
        return iworldreader.getType(blockposition.below()).is(Blocks.MYCELIUM) ? 10.0F : iworldreader.getBrightness(blockposition) - 0.5F;
    }

    public static boolean checkMushroomSpawnRules(EntityType<MushroomCow> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return generatoraccess.getType(blockposition.below()).is(Blocks.MYCELIUM) && generatoraccess.getRawBrightness(blockposition, 0) > 8;
    }

    @Override
    public void thunderHit(LightningBolt entitylightning) {
        UUID uuid = entitylightning.getUUID();

        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setMushroomType(this.getMushroomType() == MushroomCow.MushroomType.RED ? MushroomCow.MushroomType.BROWN : MushroomCow.MushroomType.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(MushroomCow.DATA_TYPE, MushroomCow.MushroomType.RED.type);
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.getItem() == Items.BOWL && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemstack1;

            if (this.effect != null) {
                flag = true;
                itemstack1 = new ItemStack(Items.SUSPICIOUS_STEW);
                SuspiciousStewItem.saveMobEffect(itemstack1, this.effect, this.effectDuration);
                this.effect = null;
                this.effectDuration = 0;
            } else {
                itemstack1 = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemstack2 = ItemUtils.createBucketResult(itemstack, entityhuman, itemstack1);

            entityhuman.setItemInHand(enumhand, itemstack2);
            SoundEvent soundeffect;

            if (flag) {
                soundeffect = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundeffect = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundeffect, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else if (itemstack.getItem() == Items.SHEARS && this.readyForShearing()) {
            // CraftBukkit start
            if (!CraftEventFactory.handlePlayerShearEntityEvent(entityhuman, this, itemstack, enumhand)) {
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.shear(SoundSource.PLAYERS);
            if (!this.level.isClientSide) {
                itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(enumhand);
                });
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else if (this.getMushroomType() == MushroomCow.MushroomType.BROWN && itemstack.getItem().is((Tag) ItemTags.SMALL_FLOWERS)) {
            if (this.effect != null) {
                for (int i = 0; i < 2; ++i) {
                    this.level.addParticle(ParticleTypes.SMOKE, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                }
            } else {
                Optional<Pair<MobEffect, Integer>> optional = this.getEffectFromItemStack(itemstack);

                if (!optional.isPresent()) {
                    return InteractionResult.PASS;
                }

                Pair<MobEffect, Integer> pair = (Pair) optional.get();

                if (!entityhuman.abilities.instabuild) {
                    itemstack.shrink(1);
                }

                for (int j = 0; j < 4; ++j) {
                    this.level.addParticle(ParticleTypes.EFFECT, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                }

                this.effect = (MobEffect) pair.getLeft();
                this.effectDuration = (Integer) pair.getRight();
                this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    public void shear(SoundSource soundcategory) {
        this.level.playSound((Player) null, (Entity) this, SoundEvents.MOOSHROOM_SHEAR, soundcategory, 1.0F, 1.0F);
        if (!this.level.isClientSide()) {
            ((ServerLevel) this.level).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5D), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            // this.die(); // CraftBukkit - moved down
            Cow entitycow = (Cow) EntityType.COW.create(this.level);

            entitycow.moveTo(this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
            entitycow.setHealth(this.getHealth());
            entitycow.yBodyRot = this.yBodyRot;
            if (this.hasCustomName()) {
                entitycow.setCustomName(this.getCustomName());
                entitycow.setCustomNameVisible(this.isCustomNameVisible());
            }

            if (this.isPersistenceRequired()) {
                entitycow.setPersistenceRequired();
            }

            entitycow.setInvulnerable(this.isInvulnerable());
            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, entitycow, EntityTransformEvent.TransformReason.SHEARED).isCancelled()) {
                return;
            }
            this.level.addEntity(entitycow, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHEARED);

            this.remove(); // CraftBukkit - from above
            // CraftBukkit end

            for (int i = 0; i < 5; ++i) {
                this.level.addFreshEntity(new ItemEntity(this.level, this.getX(), this.getY(1.0D), this.getZ(), new ItemStack(this.getMushroomType().blockState.getBlock())));
            }
        }

    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putString("Type", this.getMushroomType().type);
        if (this.effect != null) {
            nbttagcompound.putByte("EffectId", (byte) MobEffect.getId(this.effect));
            nbttagcompound.putInt("EffectDuration", this.effectDuration);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setMushroomType(MushroomCow.MushroomType.byType(nbttagcompound.getString("Type")));
        if (nbttagcompound.contains("EffectId", 1)) {
            this.effect = MobEffect.byId(nbttagcompound.getByte("EffectId"));
        }

        if (nbttagcompound.contains("EffectDuration", 3)) {
            this.effectDuration = nbttagcompound.getInt("EffectDuration");
        }

    }

    private Optional<Pair<MobEffect, Integer>> getEffectFromItemStack(ItemStack itemstack) {
        Item item = itemstack.getItem();

        if (item instanceof BlockItem) {
            Block block = ((BlockItem) item).getBlock();

            if (block instanceof FlowerBlock) {
                FlowerBlock blockflowers = (FlowerBlock) block;

                return Optional.of(Pair.of(blockflowers.getSuspiciousStewEffect(), blockflowers.getEffectDuration()));
            }
        }

        return Optional.empty();
    }

    public void setMushroomType(MushroomCow.MushroomType entitymushroomcow_type) {
        this.entityData.set(MushroomCow.DATA_TYPE, entitymushroomcow_type.type);
    }

    public MushroomCow.MushroomType getMushroomType() {
        return MushroomCow.MushroomType.byType((String) this.entityData.get(MushroomCow.DATA_TYPE));
    }

    @Override
    public MushroomCow getBreedOffspring(AgableMob entityageable) {
        MushroomCow entitymushroomcow = (MushroomCow) EntityType.MOOSHROOM.create(this.level);

        entitymushroomcow.setMushroomType(this.getOffspringType((MushroomCow) entityageable));
        return entitymushroomcow;
    }

    private MushroomCow.MushroomType getOffspringType(MushroomCow entitymushroomcow) {
        MushroomCow.MushroomType entitymushroomcow_type = this.getMushroomType();
        MushroomCow.MushroomType entitymushroomcow_type1 = entitymushroomcow.getMushroomType();
        MushroomCow.MushroomType entitymushroomcow_type2;

        if (entitymushroomcow_type == entitymushroomcow_type1 && this.random.nextInt(1024) == 0) {
            entitymushroomcow_type2 = entitymushroomcow_type == MushroomCow.MushroomType.BROWN ? MushroomCow.MushroomType.RED : MushroomCow.MushroomType.BROWN;
        } else {
            entitymushroomcow_type2 = this.random.nextBoolean() ? entitymushroomcow_type : entitymushroomcow_type1;
        }

        return entitymushroomcow_type2;
    }

    public static enum MushroomType {

        RED("red", Blocks.RED_MUSHROOM.getBlockData()), BROWN("brown", Blocks.BROWN_MUSHROOM.getBlockData());

        private final String type;
        private final BlockState blockState;

        private MushroomType(String s, BlockState iblockdata) {
            this.type = s;
            this.blockState = iblockdata;
        }

        private static MushroomCow.MushroomType byType(String s) {
            MushroomCow.MushroomType[] aentitymushroomcow_type = values();
            int i = aentitymushroomcow_type.length;

            for (int j = 0; j < i; ++j) {
                MushroomCow.MushroomType entitymushroomcow_type = aentitymushroomcow_type[j];

                if (entitymushroomcow_type.type.equals(s)) {
                    return entitymushroomcow_type;
                }
            }

            return MushroomCow.MushroomType.RED;
        }
    }
}
