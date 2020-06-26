package net.minecraft.world.entity.npc;

import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
// CraftBukkit end

public abstract class AbstractVillager extends AgableMob implements Npc, Merchant {

    // CraftBukkit start
    private CraftMerchant craftMerchant;

    @Override
    public CraftMerchant getCraftMerchant() {
        return (craftMerchant == null) ? craftMerchant = new CraftMerchant(this) : craftMerchant;
    }
    // CraftBukkit end
    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(net.minecraft.world.entity.npc.AbstractVillager.class, EntityDataSerializers.INT);
    @Nullable
    private Player tradingPlayer;
    @Nullable
    protected MerchantOffers offers;
    private final SimpleContainer inventory = new SimpleContainer(8, (org.bukkit.craftbukkit.entity.CraftAbstractVillager) this.getBukkitEntity()); // CraftBukkit add argument

    public AbstractVillager(EntityType<? extends net.minecraft.world.entity.npc.AbstractVillager> entitytypes, Level world) {
        super(entitytypes, world);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
            ((AgableMob.AgableMobGroupData) groupdataentity).setShouldSpawnBaby(false);
        }

        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(net.minecraft.world.entity.npc.AbstractVillager.DATA_UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int i) {
        this.entityData.set(net.minecraft.world.entity.npc.AbstractVillager.DATA_UNHAPPY_COUNTER, i);
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return this.isBaby() ? 0.81F : 1.62F;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(net.minecraft.world.entity.npc.AbstractVillager.DATA_UNHAPPY_COUNTER, 0);
    }

    @Override
    public void setTradingPlayer(@Nullable Player entityhuman) {
        this.tradingPlayer = entityhuman;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public boolean isTrading() {
        return this.tradingPlayer != null;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.updateTrades();
        }

        return this.offers;
    }

    @Override
    public void overrideXp(int i) {}

    @Override
    public void notifyTrade(MerchantOffer merchantrecipe) {
        merchantrecipe.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        this.rewardTradeXp(merchantrecipe);
        if (this.tradingPlayer instanceof ServerPlayer) {
            CriteriaTriggers.TRADE.trigger((ServerPlayer) this.tradingPlayer, this, merchantrecipe.getResult());
        }

    }

    protected abstract void rewardTradeXp(MerchantOffer merchantrecipe);

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public void notifyTradeUpdated(ItemStack itemstack) {
        if (!this.level.isClientSide && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.playSound(this.getTradeUpdatedSound(!itemstack.isEmpty()), this.getSoundVolume(), this.getVoicePitch());
        }

    }

    @Override
    public SoundEvent getTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    protected SoundEvent getTradeUpdatedSound(boolean flag) {
        return flag ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
    }

    public void playCelebrateSound() {
        this.playSound(SoundEvents.VILLAGER_CELEBRATE, this.getSoundVolume(), this.getVoicePitch());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        MerchantOffers merchantrecipelist = this.getOffers();

        if (!merchantrecipelist.isEmpty()) {
            nbttagcompound.put("Offers", merchantrecipelist.createTag());
        }

        nbttagcompound.put("Inventory", this.inventory.createTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("Offers", 10)) {
            this.offers = new MerchantOffers(nbttagcompound.getCompound("Offers"));
        }

        this.inventory.fromTag(nbttagcompound.getList("Inventory", 10));
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel worldserver) {
        this.stopTrading();
        return super.changeDimension(worldserver);
    }

    protected void stopTrading() {
        this.setTradingPlayer((Player) null);
    }

    @Override
    public void die(DamageSource damagesource) {
        super.die(damagesource);
        this.stopTrading();
    }

    @Override
    public boolean canBeLeashed(Player entityhuman) {
        return false;
    }

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        if (super.setSlot(i, itemstack)) {
            return true;
        } else {
            int j = i - 300;

            if (j >= 0 && j < this.inventory.getContainerSize()) {
                this.inventory.setItem(j, itemstack);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    protected abstract void updateTrades();

    protected void a(MerchantOffers merchantrecipelist, VillagerTrades.ItemListing[] avillagertrades_imerchantrecipeoption, int i) {
        Set<Integer> set = Sets.newHashSet();

        if (avillagertrades_imerchantrecipeoption.length > i) {
            while (set.size() < i) {
                set.add(this.random.nextInt(avillagertrades_imerchantrecipeoption.length));
            }
        } else {
            for (int j = 0; j < avillagertrades_imerchantrecipeoption.length; ++j) {
                set.add(j);
            }
        }

        Iterator iterator = set.iterator();

        while (iterator.hasNext()) {
            Integer integer = (Integer) iterator.next();
            VillagerTrades.ItemListing villagertrades_imerchantrecipeoption = avillagertrades_imerchantrecipeoption[integer];
            MerchantOffer merchantrecipe = villagertrades_imerchantrecipeoption.getOffer(this, this.random);

            if (merchantrecipe != null) {
                // CraftBukkit start
                VillagerAcquireTradeEvent event = new VillagerAcquireTradeEvent((org.bukkit.entity.AbstractVillager) getBukkitEntity(), merchantrecipe.asBukkit());
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                if (!event.isCancelled()) {
                    merchantrecipelist.add(CraftMerchantRecipe.fromBukkit(event.getRecipe()).toMinecraft());
                }
                // CraftBukkit end
            }
        }

    }
}
