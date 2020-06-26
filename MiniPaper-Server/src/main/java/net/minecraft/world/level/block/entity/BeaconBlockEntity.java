package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.entity.HumanEntity;
import org.bukkit.potion.PotionEffect;
// CraftBukkit end

public class BeaconBlockEntity extends BlockEntity implements MenuProvider, TickableBlockEntity {

    public static final MobEffect[][] BEACON_EFFECTS = new MobEffect[][]{{MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED}, {MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP}, {MobEffects.DAMAGE_BOOST}, {MobEffects.REGENERATION}};
    private static final Set<MobEffect> VALID_EFFECTS = (Set) Arrays.stream(BeaconBlockEntity.BEACON_EFFECTS).flatMap(Arrays::stream).collect(Collectors.toSet());
    private List<BeaconBlockEntity.BeaconColorTracker> beamSections = Lists.newArrayList();
    private List<BeaconBlockEntity.BeaconColorTracker> checkingBeamSections = Lists.newArrayList();
    public int levels;
    private int lastCheckY = -1;
    @Nullable
    public MobEffect primaryPower;
    @Nullable
    public MobEffect secondaryPower;
    @Nullable
    public Component name;
    public LockCode lockKey;
    private final ContainerData dataAccess;
    // CraftBukkit start - add fields and methods
    public PotionEffect getPrimaryEffect() {
        return (this.primaryPower != null) ? CraftPotionUtil.toBukkit(new MobEffectInstance(this.primaryPower, getLevelOH(), getAmplification(), true, true)) : null;
    }

    public PotionEffect getSecondaryEffect() {
        return (hasSecondaryEffect()) ? CraftPotionUtil.toBukkit(new MobEffectInstance(this.secondaryPower, getLevelOH(), getAmplification(), true, true)) : null;
    }
    // CraftBukkit end

    public BeaconBlockEntity() {
        super(BlockEntityType.BEACON);
        this.lockKey = LockCode.NO_LOCK;
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int i) {
                switch (i) {
                    case 0:
                        return BeaconBlockEntity.this.levels;
                    case 1:
                        return MobEffect.getId(BeaconBlockEntity.this.primaryPower);
                    case 2:
                        return MobEffect.getId(BeaconBlockEntity.this.secondaryPower);
                    default:
                        return 0;
                }
            }

            @Override
            public void set(int i, int j) {
                switch (i) {
                    case 0:
                        BeaconBlockEntity.this.levels = j;
                        break;
                    case 1:
                        if (!BeaconBlockEntity.this.level.isClientSide && !BeaconBlockEntity.this.beamSections.isEmpty()) {
                            BeaconBlockEntity.this.playSound(SoundEvents.BEACON_POWER_SELECT);
                        }

                        BeaconBlockEntity.this.primaryPower = BeaconBlockEntity.getValidEffectById(j);
                        break;
                    case 2:
                        BeaconBlockEntity.this.secondaryPower = BeaconBlockEntity.getValidEffectById(j);
                }

            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    @Override
    public void tick() {
        int i = this.worldPosition.getX();
        int j = this.worldPosition.getY();
        int k = this.worldPosition.getZ();
        BlockPos blockposition;

        if (this.lastCheckY < j) {
            blockposition = this.worldPosition;
            this.checkingBeamSections = Lists.newArrayList();
            this.lastCheckY = blockposition.getY() - 1;
        } else {
            blockposition = new BlockPos(i, this.lastCheckY + 1, k);
        }

        BeaconBlockEntity.BeaconColorTracker tileentitybeacon_beaconcolortracker = this.checkingBeamSections.isEmpty() ? null : (BeaconBlockEntity.BeaconColorTracker) this.checkingBeamSections.get(this.checkingBeamSections.size() - 1);
        int l = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, i, k);

        int i1;

        for (i1 = 0; i1 < 10 && blockposition.getY() <= l; ++i1) {
            BlockState iblockdata = this.level.getType(blockposition);
            Block block = iblockdata.getBlock();

            if (block instanceof BeaconBeamBlock) {
                float[] afloat = ((BeaconBeamBlock) block).getColor().getTextureDiffuseColors();

                if (this.checkingBeamSections.size() <= 1) {
                    tileentitybeacon_beaconcolortracker = new BeaconBlockEntity.BeaconColorTracker(afloat);
                    this.checkingBeamSections.add(tileentitybeacon_beaconcolortracker);
                } else if (tileentitybeacon_beaconcolortracker != null) {
                    if (Arrays.equals(afloat, tileentitybeacon_beaconcolortracker.a)) {
                        tileentitybeacon_beaconcolortracker.a();
                    } else {
                        tileentitybeacon_beaconcolortracker = new BeaconBlockEntity.BeaconColorTracker(new float[]{(tileentitybeacon_beaconcolortracker.a[0] + afloat[0]) / 2.0F, (tileentitybeacon_beaconcolortracker.a[1] + afloat[1]) / 2.0F, (tileentitybeacon_beaconcolortracker.a[2] + afloat[2]) / 2.0F});
                        this.checkingBeamSections.add(tileentitybeacon_beaconcolortracker);
                    }
                }
            } else {
                if (tileentitybeacon_beaconcolortracker == null || iblockdata.getLightBlock((BlockGetter) this.level, blockposition) >= 15 && block != Blocks.BEDROCK) {
                    this.checkingBeamSections.clear();
                    this.lastCheckY = l;
                    break;
                }

                tileentitybeacon_beaconcolortracker.a();
            }

            blockposition = blockposition.above();
            ++this.lastCheckY;
        }

        i1 = this.levels;
        if (this.level.getGameTime() % 80L == 0L) {
            if (!this.beamSections.isEmpty()) {
                this.updateBase(i, j, k);
            }

            if (this.levels > 0 && !this.beamSections.isEmpty()) {
                this.applyEffects();
                this.playSound(SoundEvents.BEACON_AMBIENT);
            }
        }

        if (this.lastCheckY >= l) {
            this.lastCheckY = -1;
            boolean flag = i1 > 0;

            this.beamSections = this.checkingBeamSections;
            if (!this.level.isClientSide) {
                boolean flag1 = this.levels > 0;

                if (!flag && flag1) {
                    this.playSound(SoundEvents.BEACON_ACTIVATE);
                    Iterator iterator = this.level.getEntitiesOfClass(ServerPlayer.class, (new AABB((double) i, (double) j, (double) k, (double) i, (double) (j - 4), (double) k)).inflate(10.0D, 5.0D, 10.0D)).iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                        CriteriaTriggers.CONSTRUCT_BEACON.trigger(entityplayer, this);
                    }
                } else if (flag && !flag1) {
                    this.playSound(SoundEvents.BEACON_DEACTIVATE);
                }
            }
        }

    }

    private void updateBase(int i, int j, int k) {
        this.levels = 0;

        for (int l = 1; l <= 4; this.levels = l++) {
            int i1 = j - l;

            if (i1 < 0) {
                break;
            }

            boolean flag = true;

            for (int j1 = i - l; j1 <= i + l && flag; ++j1) {
                for (int k1 = k - l; k1 <= k + l; ++k1) {
                    if (!this.level.getType(new BlockPos(j1, i1, k1)).is((Tag) BlockTags.BEACON_BASE_BLOCKS)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (!flag) {
                break;
            }
        }

    }

    @Override
    public void setRemoved() {
        this.playSound(SoundEvents.BEACON_DEACTIVATE);
        super.setRemoved();
    }

    // CraftBukkit start - split into components
    private byte getAmplification() {
        {
            byte b0 = 0;

            if (this.levels >= 4 && this.primaryPower == this.secondaryPower) {
                b0 = 1;
            }

            return b0;
        }
    }

    public int getLevelOH() {
        {
            int i = (9 + this.levels * 2) * 20;
            return i;
        }
    }

    public List getHumansInRange() {
        {
            double d0 = (double) (this.levels * 10 + 10);

            AABB axisalignedbb = (new AABB(this.worldPosition)).inflate(d0).expandTowards(0.0D, (double) this.level.getMaxBuildHeight(), 0.0D);
            List<Player> list = this.level.getEntitiesOfClass(Player.class, axisalignedbb);

            return list;
        }
    }

    private void applyEffect(List list, MobEffect effects, int i, int b0) {
        {
            Iterator iterator = list.iterator();

            Player entityhuman;

            while (iterator.hasNext()) {
                entityhuman = (Player) iterator.next();
                entityhuman.addEffect(new MobEffectInstance(effects, i, b0, true, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.BEACON);
            }
        }
    }

    private boolean hasSecondaryEffect() {
        {
            if (this.levels >= 4 && this.primaryPower != this.secondaryPower && this.secondaryPower != null) {
                return true;
            }

            return false;
        }
    }

    private void applyEffects() {
        if (!this.level.isClientSide && this.primaryPower != null) {
            double d0 = (double) (this.levels * 10 + 10);
            byte b0 = getAmplification();

            int i = getLevelOH();
            List list = getHumansInRange();

            applyEffect(list, this.primaryPower, i, b0);

            if (hasSecondaryEffect()) {
                applyEffect(list, this.secondaryPower, i, 0);
            }
        }

    }
    // CraftBukkit end

    public void playSound(SoundEvent soundeffect) {
        this.level.playSound((Player) null, this.worldPosition, soundeffect, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public int getLevels() {
        return this.levels;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 3, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    @Nullable
    private static MobEffect getValidEffectById(int i) {
        MobEffect mobeffectlist = MobEffect.byId(i);

        return BeaconBlockEntity.VALID_EFFECTS.contains(mobeffectlist) ? mobeffectlist : null;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        // CraftBukkit start - persist manually set non-default beacon effects (SPIGOT-3598)
        this.primaryPower = MobEffect.byId(nbttagcompound.getInt("Primary"));
        this.secondaryPower = MobEffect.byId(nbttagcompound.getInt("Secondary"));
        this.levels = nbttagcompound.getInt("Levels"); // SPIGOT-5053, use where available
        // CraftBukkit end
        if (nbttagcompound.contains("CustomName", 8)) {
            this.name = Component.ChatSerializer.a(nbttagcompound.getString("CustomName"));
        }

        this.lockKey = LockCode.fromTag(nbttagcompound);
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        nbttagcompound.putInt("Primary", MobEffect.getId(this.primaryPower));
        nbttagcompound.putInt("Secondary", MobEffect.getId(this.secondaryPower));
        nbttagcompound.putInt("Levels", this.levels);
        if (this.name != null) {
            nbttagcompound.putString("CustomName", Component.ChatSerializer.a(this.name));
        }

        this.lockKey.addToTag(nbttagcompound);
        return nbttagcompound;
    }

    public void setCustomName(@Nullable Component ichatbasecomponent) {
        this.name = ichatbasecomponent;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerinventory, Player entityhuman) {
        return BaseContainerBlockEntity.canUnlock(entityhuman, this.lockKey, this.getDisplayName()) ? new BeaconMenu(i, playerinventory, this.dataAccess, ContainerLevelAccess.create(this.level, this.getBlockPos())) : null;
    }

    @Override
    public Component getDisplayName() {
        return (Component) (this.name != null ? this.name : new TranslatableComponent("container.beacon"));
    }

    public static class BeaconColorTracker {

        private final float[] a;
        private int b;

        public BeaconColorTracker(float[] afloat) {
            this.a = afloat;
            this.b = 1;
        }

        protected void a() {
            ++this.b;
        }
    }
}
