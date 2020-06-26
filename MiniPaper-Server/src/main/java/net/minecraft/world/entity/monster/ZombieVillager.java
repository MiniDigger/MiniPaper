package net.minecraft.world.entity.monster;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class ZombieVillager extends Zombie implements VillagerDataHolder {

    public static final EntityDataAccessor<Boolean> DATA_CONVERTING_ID = SynchedEntityData.defineId(net.minecraft.world.entity.monster.ZombieVillager.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(net.minecraft.world.entity.monster.ZombieVillager.class, EntityDataSerializers.VILLAGER_DATA);
    public int villagerConversionTime;
    public UUID conversionStarter;
    private Tag gossips;
    private CompoundTag tradeOffers;
    private int villagerXp;
    private int lastTick = MinecraftServer.currentTick; // CraftBukkit - add field

    public ZombieVillager(EntityType<? extends net.minecraft.world.entity.monster.ZombieVillager> entitytypes, Level world) {
        super(entitytypes, world);
        this.setVillagerData(this.getVillagerData().setProfession((VillagerProfession) Registry.VILLAGER_PROFESSION.getRandom(this.random)));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(net.minecraft.world.entity.monster.ZombieVillager.DATA_CONVERTING_ID, false);
        this.entityData.register(net.minecraft.world.entity.monster.ZombieVillager.DATA_VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        DataResult<Tag> dataresult = VillagerData.CODEC.encodeStart(NbtOps.INSTANCE, this.getVillagerData()); // CraftBukkit - decompile error
        Logger logger = net.minecraft.world.entity.monster.ZombieVillager.LOGGER;

        logger.getClass();
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbttagcompound.put("VillagerData", nbtbase);
        });
        if (this.tradeOffers != null) {
            nbttagcompound.put("Offers", this.tradeOffers);
        }

        if (this.gossips != null) {
            nbttagcompound.put("Gossips", this.gossips);
        }

        nbttagcompound.putInt("ConversionTime", this.isConverting() ? this.villagerConversionTime : -1);
        if (this.conversionStarter != null) {
            nbttagcompound.putUUID("ConversionPlayer", this.conversionStarter);
        }

        nbttagcompound.putInt("Xp", this.villagerXp);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("VillagerData", 10)) {
            DataResult<VillagerData> dataresult = VillagerData.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("VillagerData")));
            Logger logger = net.minecraft.world.entity.monster.ZombieVillager.LOGGER;

            logger.getClass();
            dataresult.resultOrPartial(logger::error).ifPresent(this::setVillagerData);
        }

        if (nbttagcompound.contains("Offers", 10)) {
            this.tradeOffers = nbttagcompound.getCompound("Offers");
        }

        if (nbttagcompound.contains("Gossips", 10)) {
            this.gossips = nbttagcompound.getList("Gossips", 10);
        }

        if (nbttagcompound.contains("ConversionTime", 99) && nbttagcompound.getInt("ConversionTime") > -1) {
            this.startConverting(nbttagcompound.hasUUID("ConversionPlayer") ? nbttagcompound.getUUID("ConversionPlayer") : null, nbttagcompound.getInt("ConversionTime"));
        }

        if (nbttagcompound.contains("Xp", 3)) {
            this.villagerXp = nbttagcompound.getInt("Xp");
        }

    }

    @Override
    public void tick() {
        if (!this.level.isClientSide && this.isAlive() && this.isConverting()) {
            int i = this.getConversionProgress();
            // CraftBukkit start - Use wall time instead of ticks for villager conversion
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            i *= elapsedTicks;
            // CraftBukkit end

            this.villagerConversionTime -= i;
            if (this.villagerConversionTime <= 0) {
                this.finishConversion((ServerLevel) this.level);
            }
        }

        super.tick();
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.getItem() == Items.GOLDEN_APPLE) {
            if (this.hasEffect(MobEffects.WEAKNESS)) {
                if (!entityhuman.abilities.instabuild) {
                    itemstack.shrink(1);
                }

                if (!this.level.isClientSide) {
                    this.startConverting(entityhuman.getUUID(), this.random.nextInt(2401) + 3600);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isConverting() && this.villagerXp == 0;
    }

    public boolean isConverting() {
        return (Boolean) this.getEntityData().get(net.minecraft.world.entity.monster.ZombieVillager.DATA_CONVERTING_ID);
    }

    public void startConverting(@Nullable UUID uuid, int i) {
        this.conversionStarter = uuid;
        this.villagerConversionTime = i;
        this.getEntityData().set(net.minecraft.world.entity.monster.ZombieVillager.DATA_CONVERTING_ID, true);
        // CraftBukkit start
        this.persistenceRequired = true; // CraftBukkit - SPIGOT-4684 update persistence
        this.removeEffect(MobEffects.WEAKNESS, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, i, Math.min(this.level.getDifficulty().getId() - 1, 0)), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        // CraftBukkit end
        this.level.broadcastEntityEvent(this, (byte) 16);
    }

    private void finishConversion(ServerLevel worldserver) {
        Villager entityvillager = (Villager) EntityType.VILLAGER.create((Level) worldserver);
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];
            ItemStack itemstack = this.getItemBySlot(enumitemslot);

            if (!itemstack.isEmpty()) {
                if (EnchantmentHelper.hasBindingCurse(itemstack)) {
                    entityvillager.setSlot(enumitemslot.getIndex() + 300, itemstack);
                } else {
                    double d0 = (double) this.getEquipmentDropChance(enumitemslot);

                    if (d0 > 1.0D) {
                        this.spawnAtLocation(itemstack);
                    }
                }
            }
        }

        entityvillager.copyPosition(this);
        entityvillager.setVillagerData(this.getVillagerData());
        if (this.gossips != null) {
            entityvillager.setGossips(this.gossips);
        }

        if (this.tradeOffers != null) {
            entityvillager.setOffers(new MerchantOffers(this.tradeOffers));
        }

        entityvillager.overrideXp(this.villagerXp);
        entityvillager.prepare(worldserver, worldserver.getDamageScaler(entityvillager.blockPosition()), MobSpawnType.CONVERSION, (SpawnGroupData) null, (CompoundTag) null);
        if (this.isBaby()) {
            entityvillager.setAge(-24000);
        }

        // this.die(); // CraftBukkit - moved down
        entityvillager.setNoAi(this.isNoAi());
        if (this.hasCustomName()) {
            entityvillager.setCustomName(this.getCustomName());
            entityvillager.setCustomNameVisible(this.isCustomNameVisible());
        }

        if (this.isPersistenceRequired()) {
            entityvillager.setPersistenceRequired();
        }

        entityvillager.setInvulnerable(this.isInvulnerable());
        // CraftBukkit start
        if (CraftEventFactory.callEntityTransformEvent(this, entityvillager, EntityTransformEvent.TransformReason.CURED).isCancelled()) {
            ((org.bukkit.entity.ZombieVillager) getBukkitEntity()).setConversionTime(-1); // SPIGOT-5208: End conversion to stop event spam
            return;
        }
        this.remove(); // CraftBukkit - from above
        worldserver.addEntity(entityvillager, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CURED); // CraftBukkit - add SpawnReason
        // CraftBukkit end
        if (this.conversionStarter != null) {
            Player entityhuman = worldserver.getPlayerByUUID(this.conversionStarter);

            if (entityhuman instanceof ServerPlayer) {
                CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger((ServerPlayer) entityhuman, (Zombie) this, entityvillager);
                worldserver.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, (Entity) entityhuman, (ReputationEventHandler) entityvillager);
            }
        }

        entityvillager.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION); // CraftBukkit
        if (!this.isSilent()) {
            worldserver.levelEvent((Player) null, 1027, this.blockPosition(), 0);
        }

    }

    private int getConversionProgress() {
        int i = 1;

        if (this.random.nextFloat() < 0.01F) {
            int j = 0;
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

            for (int k = (int) this.getX() - 4; k < (int) this.getX() + 4 && j < 14; ++k) {
                for (int l = (int) this.getY() - 4; l < (int) this.getY() + 4 && j < 14; ++l) {
                    for (int i1 = (int) this.getZ() - 4; i1 < (int) this.getZ() + 4 && j < 14; ++i1) {
                        Block block = this.level.getType(blockposition_mutableblockposition.d(k, l, i1)).getBlock();

                        if (block == Blocks.IRON_BARS || block instanceof BedBlock) {
                            if (this.random.nextFloat() < 0.3F) {
                                ++i;
                            }

                            ++j;
                        }
                    }
                }
            }
        }

        return i;
    }

    @Override
    protected float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 2.0F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundEvent getSoundAmbient() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    public SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    public SoundEvent getSoundDeath() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public SoundEvent getSoundStep() {
        return SoundEvents.ZOMBIE_VILLAGER_STEP;
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    public void setTradeOffers(CompoundTag nbttagcompound) {
        this.tradeOffers = nbttagcompound;
    }

    public void setGossips(Tag nbtbase) {
        this.gossips = nbtbase;
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.setVillagerData(this.getVillagerData().setType(VillagerType.byBiome(generatoraccess.getBiome(this.blockPosition()))));
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    public void setVillagerData(VillagerData villagerdata) {
        VillagerData villagerdata1 = this.getVillagerData();

        if (villagerdata1.getProfession() != villagerdata.getProfession()) {
            this.tradeOffers = null;
        }

        this.entityData.set(net.minecraft.world.entity.monster.ZombieVillager.DATA_VILLAGER_DATA, villagerdata);
    }

    @Override
    public VillagerData getVillagerData() {
        return (VillagerData) this.entityData.get(net.minecraft.world.entity.monster.ZombieVillager.DATA_VILLAGER_DATA);
    }

    public void setVillagerXp(int i) {
        this.villagerXp = i;
    }
}
