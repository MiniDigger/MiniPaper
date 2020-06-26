package net.minecraft.server.level;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ComplexItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.MainHand;
// CraftBukkit end

public class ServerPlayer extends Player implements ContainerListener {

    private static final Logger LOGGER = LogManager.getLogger();
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    public final List<Integer> entitiesToRemove = Lists.newLinkedList();
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    public int spawnInvulnerableTime = 60;
    private ChatVisiblity chatVisibility;
    private boolean canChatColor = true;
    private long lastActionTime = Util.getMillis();
    private Entity camera;
    public boolean isChangingDimension;
    private boolean seenCredits;
    private final ServerRecipeBook recipeBook = new ServerRecipeBook();
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    @Nullable
    private Vec3 enteredNetherPosition;
    private SectionPos lastSectionPos = SectionPos.of(0, 0, 0);
    private ResourceKey<Level> respawnDimension;
    @Nullable
    private BlockPos respawnPosition;
    private boolean respawnForced;
    private int containerCounter;
    public boolean ignoreSlotUpdateHack;
    public int latency;
    public boolean wonGame;

    // CraftBukkit start
    public String displayName;
    public Component listName;
    public org.bukkit.Location compassTarget;
    public int newExp = 0;
    public int newLevel = 0;
    public int newTotalExp = 0;
    public boolean keepLevel = false;
    public double maxHealthCache;
    public boolean joining = true;
    public boolean sentListPacket = false;
    public Integer clientViewDistance;
    // CraftBukkit end

    public ServerPlayer(MinecraftServer minecraftserver, ServerLevel worldserver, GameProfile gameprofile, ServerPlayerGameMode playerinteractmanager) {
        super(worldserver, worldserver.getSharedSpawnPos(), gameprofile);
        this.respawnDimension = Level.OVERWORLD;
        playerinteractmanager.player = this;
        this.gameMode = playerinteractmanager;
        this.server = minecraftserver;
        this.stats = minecraftserver.getPlayerList().getStatisticManager(this);
        this.advancements = minecraftserver.getPlayerList().getPlayerAdvancements(this);
        this.maxUpStep = 1.0F;
        this.fudgeSpawnLocation(worldserver);

        // CraftBukkit start
        this.displayName = this.getScoreboardName();
        this.canPickUpLoot = true;
        this.maxHealthCache = this.getMaxHealth();
    }

    // Yes, this doesn't match Vanilla, but it's the best we can do for now.
    // If this is an issue, PRs are welcome
    public final BlockPos getSpawnPoint(ServerLevel worldserver) {
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = Mth.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = (new Random()).nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i, false);

                if (blockposition1 != null) {
                    this.moveTo(blockposition1, 0.0F, 0.0F);
                    if (worldserver.noCollision(this)) {
                        break;
                    }
                }
            }
        }

        return blockposition;
    }
    // CraftBukkit end

    private void fudgeSpawnLocation(ServerLevel worldserver) {
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        if (worldserver.dimensionType().hasSkyLight() && worldserver.getServer().getWorldData().getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = Mth.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = (new Random()).nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i, false);

                if (blockposition1 != null) {
                    this.moveTo(blockposition1, 0.0F, 0.0F);
                    if (worldserver.noCollision(this)) {
                        break;
                    }
                }
            }
        } else {
            this.moveTo(blockposition, 0.0F, 0.0F);

            while (!worldserver.noCollision(this) && this.getY() < 255.0D) {
                this.setPos(this.getX(), this.getY() + 1.0D, this.getZ());
            }
        }

    }

    private int getCoprime(int i) {
        return i <= 16 ? i - 1 : 17;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("playerGameType", 99)) {
            if (this.getServer().getForceGameType()) {
                this.gameMode.setGameModeForPlayer(this.getServer().getDefaultGameType(), GameType.NOT_SET);
            } else {
                this.gameMode.setGameModeForPlayer(GameType.byId(nbttagcompound.getInt("playerGameType")), nbttagcompound.contains("previousPlayerGameType", 3) ? GameType.byId(nbttagcompound.getInt("previousPlayerGameType")) : GameType.NOT_SET);
            }
        }

        if (nbttagcompound.contains("enteredNetherPosition", 10)) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("enteredNetherPosition");

            this.enteredNetherPosition = new Vec3(nbttagcompound1.getDouble("x"), nbttagcompound1.getDouble("y"), nbttagcompound1.getDouble("z"));
        }

        this.seenCredits = nbttagcompound.getBoolean("seenCredits");
        if (nbttagcompound.contains("recipeBook", 10)) {
            this.recipeBook.fromNbt(nbttagcompound.getCompound("recipeBook"), this.server.getRecipeManager());
        }
        this.getBukkitEntity().readExtraData(nbttagcompound); // CraftBukkit

        if (this.isSleeping()) {
            this.stopSleeping();
        }

        // CraftBukkit start
        String spawnWorld = nbttagcompound.getString("SpawnWorld");
        CraftWorld oldWorld = (CraftWorld) Bukkit.getWorld(spawnWorld);
        if (oldWorld != null) {
            this.respawnDimension = oldWorld.getHandle().getDimensionKey();
        }
        // CraftBukkit end

        if (nbttagcompound.contains("SpawnX", 99) && nbttagcompound.contains("SpawnY", 99) && nbttagcompound.contains("SpawnZ", 99)) {
            this.respawnPosition = new BlockPos(nbttagcompound.getInt("SpawnX"), nbttagcompound.getInt("SpawnY"), nbttagcompound.getInt("SpawnZ"));
            this.respawnForced = nbttagcompound.getBoolean("SpawnForced");
            if (nbttagcompound.contains("SpawnDimension")) {
                DataResult dataresult = Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, nbttagcompound.get("SpawnDimension"));
                Logger logger = ServerPlayer.LOGGER;

                logger.getClass();
                this.respawnDimension = (ResourceKey) dataresult.resultOrPartial(logger::error).orElse(Level.OVERWORLD);
            }
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
        nbttagcompound.putInt("previousPlayerGameType", this.gameMode.getPreviousGameModeForPlayer().getId());
        nbttagcompound.putBoolean("seenCredits", this.seenCredits);
        if (this.enteredNetherPosition != null) {
            CompoundTag nbttagcompound1 = new CompoundTag();

            nbttagcompound1.putDouble("x", this.enteredNetherPosition.x);
            nbttagcompound1.putDouble("y", this.enteredNetherPosition.y);
            nbttagcompound1.putDouble("z", this.enteredNetherPosition.z);
            nbttagcompound.put("enteredNetherPosition", nbttagcompound1);
        }

        Entity entity = this.getRootVehicle();
        Entity entity1 = this.getVehicle();

        // CraftBukkit start - handle non-persistent vehicles
        boolean persistVehicle = true;
        if (entity1 != null) {
            Entity vehicle;
            for (vehicle = entity1; vehicle != null; vehicle = vehicle.getVehicle()) {
                if (!vehicle.persist) {
                    persistVehicle = false;
                    break;
                }
            }
        }

        if (persistVehicle && entity1 != null && entity != this && entity.hasOnePlayerPassenger()) {
            // CraftBukkit end
            CompoundTag nbttagcompound2 = new CompoundTag();
            CompoundTag nbttagcompound3 = new CompoundTag();

            entity.save(nbttagcompound3);
            nbttagcompound2.putUUID("Attach", entity1.getUUID());
            nbttagcompound2.put("Entity", nbttagcompound3);
            nbttagcompound.put("RootVehicle", nbttagcompound2);
        }

        nbttagcompound.put("recipeBook", this.recipeBook.toNbt());
        nbttagcompound.putString("Dimension", this.level.getDimensionKey().location().toString());
        if (this.respawnPosition != null) {
            nbttagcompound.putInt("SpawnX", this.respawnPosition.getX());
            nbttagcompound.putInt("SpawnY", this.respawnPosition.getY());
            nbttagcompound.putInt("SpawnZ", this.respawnPosition.getZ());
            nbttagcompound.putBoolean("SpawnForced", this.respawnForced);
            DataResult<Tag> dataresult = ResourceLocation.CODEC.encodeStart(NbtOps.INSTANCE, this.respawnDimension.location()); // CraftBukkit - decompile error
            Logger logger = ServerPlayer.LOGGER;

            logger.getClass();
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbttagcompound.put("SpawnDimension", nbtbase);
            });
        }
        this.getBukkitEntity().setExtraData(nbttagcompound); // CraftBukkit

    }

    // CraftBukkit start - World fallback code, either respawn location or global spawn
    public void setLevel(Level world) {
        super.setLevel(world);
        if (world == null) {
            this.removed = false;
            Vec3 position = null;
            if (this.respawnDimension != null) {
                world = this.getLevel().getServerOH().getHandle().getServer().getWorldServer(this.respawnDimension);
                if (world != null && this.getRespawnPosition() != null) {
                    position = Player.findRespawnPositionAndUseSpawnBlock((ServerLevel) world, this.getRespawnPosition(), false, false).orElse(null);
                }
            }
            if (world == null || position == null) {
                world = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
                position = Vec3.atCenterOf(((ServerLevel) world).getSharedSpawnPos());
            }
            this.level = world;
            this.setPos(position.x(), position.y(), position.z());
        }
        this.gameMode.setLevel((ServerLevel) world);
    }
    // CraftBukkit end

    public void setExperiencePoints(int i) {
        float f = (float) this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;

        this.experienceProgress = Mth.clamp((float) i / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int i) {
        this.experienceLevel = i;
        this.lastSentExp = -1;
    }

    @Override
    public void giveExperienceLevels(int i) {
        super.giveExperienceLevels(i);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack itemstack, int i) {
        super.onEnchantmentPerformed(itemstack, i);
        this.lastSentExp = -1;
    }

    public void initMenu() {
        this.containerMenu.addSlotListener(this);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.sendPacket(new ClientboundPlayerCombatPacket(this.getCombatTracker(), ClientboundPlayerCombatPacket.Event.ENTER_COMBAT));
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.sendPacket(new ClientboundPlayerCombatPacket(this.getCombatTracker(), ClientboundPlayerCombatPacket.Event.END_COMBAT));
    }

    @Override
    protected void onInsideBlock(BlockState iblockdata) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, iblockdata);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        // CraftBukkit start
        if (this.joining) {
            this.joining = false;
        }
        // CraftBukkit end
        this.gameMode.tick();
        --this.spawnInvulnerableTime;
        if (this.invulnerableTime > 0) {
            --this.invulnerableTime;
        }

        this.containerMenu.broadcastChanges();
        if (!this.level.isClientSide && !this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        while (!this.entitiesToRemove.isEmpty()) {
            int i = Math.min(this.entitiesToRemove.size(), Integer.MAX_VALUE);
            int[] aint = new int[i];
            Iterator<Integer> iterator = this.entitiesToRemove.iterator();
            int j = 0;

            while (iterator.hasNext() && j < i) {
                aint[j++] = (Integer) iterator.next();
                iterator.remove();
            }

            this.connection.sendPacket(new ClientboundRemoveEntitiesPacket(aint));
        }

        Entity entity = this.getCamera();

        if (entity != this) {
            if (entity.isAlive()) {
                this.absMoveTo(entity.getX(), entity.getY(), entity.getZ(), entity.yRot, entity.xRot);
                this.getLevel().getChunkSourceOH().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.advancements.flushDirty(this);
    }

    public void doTick() {
        try {
            if (!this.isSpectator() || this.level.hasChunkAt(this.blockPosition())) {
                super.tick();
            }

            for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);

                if (itemstack.getItem().isComplex()) {
                    Packet<?> packet = ((ComplexItem) itemstack.getItem()).getUpdatePacket(itemstack, this.level, (Player) this);

                    if (packet != null) {
                        this.connection.sendPacket(packet);
                    }
                }
            }

            if (this.getHealth() != this.lastSentHealth || this.lastSentFood != this.foodData.getFoodLevel() || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.sendPacket(new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float) this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float) this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float) this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float) this.lastRecordedExperience));
            }

            // CraftBukkit start - Force max health updates
            if (this.maxHealthCache != this.getMaxHealth()) {
                this.getBukkitEntity().updateScaledHealth();
            }
            // CraftBukkit end

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float) this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.sendPacket(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }

            // CraftBukkit start - initialize oldLevel and fire PlayerLevelChangeEvent
            if (this.oldLevel == -1) {
                this.oldLevel = this.experienceLevel;
            }

            if (this.oldLevel != this.experienceLevel) {
                CraftEventFactory.callPlayerLevelChangeEvent(this.level.getServerOH().getPlayer((ServerPlayer) this), this.oldLevel, this.experienceLevel);
                this.oldLevel = this.experienceLevel;
            }
            // CraftBukkit end
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Player being ticked");

            this.appendEntityCrashDetails(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    private void updateScoreForCriteria(ObjectiveCriteria iscoreboardcriteria, int i) {
        // CraftBukkit - Use our scores instead
        this.level.getServerOH().getScoreboardManager().getScoreboardScores(iscoreboardcriteria, this.getScoreboardName(), (scoreboardscore) -> {
            scoreboardscore.setScore(i);
        });
    }

    @Override
    public void die(DamageSource damagesource) {
        boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.removed) {
            return;
        }
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<org.bukkit.inventory.ItemStack>(this.inventory.getContainerSize());
        boolean keepInventory = this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();

        if (!keepInventory) {
            for (ItemStack item : this.inventory.getContents()) {
                if (!item.isEmpty() && !EnchantmentHelper.hasVanishingCurse(item)) {
                    loot.add(CraftItemStack.asCraftMirror(item));
                }
            }
        }
        // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
        this.dropFromLootTable(damagesource, this.lastHurtByPlayerTime > 0);
        for (org.bukkit.inventory.ItemStack item : this.drops) {
            loot.add(item);
        }
        this.drops.clear(); // SPIGOT-5188: make sure to clear

        Component defaultMessage = this.getCombatTracker().getDeathMessage();

        String deathmessage = defaultMessage.getString();
        org.bukkit.event.entity.PlayerDeathEvent event = CraftEventFactory.callPlayerDeathEvent(this, loot, deathmessage, keepInventory);

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        String deathMessage = event.getDeathMessage();

        if (deathMessage != null && deathMessage.length() > 0 && flag) { // TODO: allow plugins to override?
            Component ichatbasecomponent;
            if (deathMessage.equals(deathmessage)) {
                ichatbasecomponent = this.getCombatTracker().getDeathMessage();
            } else {
                ichatbasecomponent = org.bukkit.craftbukkit.util.CraftChatMessage.fromStringOrNull(deathMessage);
            }

            this.connection.send((Packet) (new ClientboundPlayerCombatPacket(this.getCombatTracker(), ClientboundPlayerCombatPacket.Event.ENTITY_DIED, ichatbasecomponent)), (future) -> {
                if (!future.isSuccess()) {
                    boolean flag1 = true;
                    String s = ichatbasecomponent.getString(256);
                    TranslatableComponent chatmessage = new TranslatableComponent("death.attack.message_too_long", new Object[]{(new TextComponent(s)).withStyle(ChatFormatting.YELLOW)});
                    MutableComponent ichatmutablecomponent = (new TranslatableComponent("death.attack.even_more_magic", new Object[]{this.getDisplayName()})).withStyle((chatmodifier) -> {
                        return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, chatmessage));
                    });

                    this.connection.sendPacket(new ClientboundPlayerCombatPacket(this.getCombatTracker(), ClientboundPlayerCombatPacket.Event.ENTITY_DIED, ichatmutablecomponent));
                }

            });
            Team scoreboardteambase = this.getTeam();

            if (scoreboardteambase != null && scoreboardteambase.getDeathMessageVisibility() != Team.Visibility.ALWAYS) {
                if (scoreboardteambase.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                    this.server.getPlayerList().broadcastToTeam((Player) this, ichatbasecomponent);
                } else if (scoreboardteambase.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                    this.server.getPlayerList().broadcastToAllExceptTeam(this, ichatbasecomponent);
                }
            } else {
                this.server.getPlayerList().broadcastMessage(ichatbasecomponent, ChatType.SYSTEM, Util.NIL_UUID);
            }
        } else {
            this.connection.sendPacket(new ClientboundPlayerCombatPacket(this.getCombatTracker(), ClientboundPlayerCombatPacket.Event.ENTITY_DIED));
        }

        this.removeEntitiesOnShoulder();
        if (this.level.getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }
        // SPIGOT-5478 must be called manually now
        this.dropExperience();
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!event.getKeepInventory()) {
            this.inventory.clearContent();
        }

        this.setCamera(this); // Remove spectated target
        // CraftBukkit end

        // CraftBukkit - Get our scores instead
        this.level.getServerOH().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.DEATH_COUNT, this.getScoreboardName(), Score::increment);
        LivingEntity entityliving = this.getKillCredit();

        if (entityliving != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(entityliving.getType()));
            entityliving.awardKillScore(this, this.deathScore, damagesource);
            this.createWitherRose(entityliving);
        }

        this.level.broadcastEntityEvent(this, (byte) 3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlag(0, false);
        this.getCombatTracker().recheckStatus();
    }

    private void tellNeutralMobsThatIDied() {
        AABB axisalignedbb = (new AABB(this.blockPosition())).inflate(32.0D, 10.0D, 32.0D);

        this.level.getLoadedEntitiesOfClass(Mob.class, axisalignedbb).stream().filter((entityinsentient) -> {
            return entityinsentient instanceof NeutralMob;
        }).forEach((entityinsentient) -> {
            ((NeutralMob) entityinsentient).playerDied((Player) this);
        });
    }

    @Override
    public void awardKillScore(Entity entity, int i, DamageSource damagesource) {
        if (entity != this) {
            super.awardKillScore(entity, i, damagesource);
            this.increaseScore(i);
            String s = this.getScoreboardName();
            String s1 = entity.getScoreboardName();

            // CraftBukkit - Get our scores instead
            this.level.getServerOH().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.KILL_COUNT_ALL, s, Score::increment);
            if (entity instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                // CraftBukkit - Get our scores instead
                this.level.getServerOH().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.KILL_COUNT_PLAYERS, s, Score::increment);
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(s, s1, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(s1, s, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, entity, damagesource);
        }
    }

    private void handleTeamKill(String s, String s1, ObjectiveCriteria[] aiscoreboardcriteria) {
        PlayerTeam scoreboardteam = this.getScoreboard().getPlayersTeam(s1);

        if (scoreboardteam != null) {
            int i = scoreboardteam.getColor().getId();

            if (i >= 0 && i < aiscoreboardcriteria.length) {
                // CraftBukkit - Get our scores instead
                this.level.getServerOH().getScoreboardManager().getScoreboardScores(aiscoreboardcriteria[i], s, Score::increment);
            }
        }

    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            boolean flag = this.server.isDedicatedServer() && this.isPvpAllowed() && "fall".equals(damagesource.msgId);

            if (!flag && this.spawnInvulnerableTime > 0 && damagesource != DamageSource.OUT_OF_WORLD) {
                return false;
            } else {
                if (damagesource instanceof EntityDamageSource) {
                    Entity entity = damagesource.getEntity();

                    if (entity instanceof Player && !this.canHarmPlayer((Player) entity)) {
                        return false;
                    }

                    if (entity instanceof AbstractArrow) {
                        AbstractArrow entityarrow = (AbstractArrow) entity;
                        Entity entity1 = entityarrow.getOwner();

                        if (entity1 instanceof Player && !this.canHarmPlayer((Player) entity1)) {
                            return false;
                        }
                    }
                }

                return super.hurt(damagesource, f);
            }
        }
    }

    @Override
    public boolean canHarmPlayer(Player entityhuman) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(entityhuman);
    }

    private boolean isPvpAllowed() {
        // CraftBukkit - this.server.getPvP() -> this.world.pvpMode
        return this.level.pvpMode;
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel worldserver) {
        // CraftBukkit start
        return a(worldserver, TeleportCause.UNKNOWN);
    }

    @Nullable
    public Entity a(ServerLevel worldserver, PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        if (this.isSleeping()) return this; // CraftBukkit - SPIGOT-3154
        // this.worldChangeInvuln = true; // CraftBukkit - Moved down and into PlayerList#changeDimension
        ServerLevel worldserver1 = this.getLevel();
        ResourceKey<Level> resourcekey = worldserver1.getDimensionKey();

        if (resourcekey == Level.END && worldserver.getDimensionKey() == Level.OVERWORLD) {
            this.isChangingDimension = true; // CraftBukkit - Moved down from above
            this.unRide();
            this.getLevel().removePlayerImmediately(this);
            if (!this.wonGame) {
                this.wonGame = true;
                this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, this.seenCredits ? 0.0F : 1.0F));
                this.seenCredits = true;
            }

            return this;
        } else {
            // CraftBukkit start
            /*
            WorldData worlddata = worldserver.getWorldData();

            this.playerConnection.sendPacket(new PacketPlayOutRespawn(worldserver.getTypeKey(), worldserver.getDimensionKey(), BiomeManager.a(worldserver.getSeed()), this.playerInteractManager.getGameMode(), this.playerInteractManager.c(), worldserver.isDebugWorld(), worldserver.isFlatWorld(), true));
            this.playerConnection.sendPacket(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
            PlayerList playerlist = this.server.getPlayerList();

            playerlist.d(this);
            worldserver1.removePlayer(this);
            this.dead = false;
            */
            // CraftBukkit end
            double d0 = this.getX();
            double d1 = this.getY();
            double d2 = this.getZ();
            float f = this.xRot;
            float f1 = this.yRot;
            float f2 = f1;

            worldserver1.getProfiler().push("moving");
            double d3;

            if (worldserver.getDimensionKey() == Level.END) {
                BlockPos blockposition = ServerLevel.END_SPAWN_POINT;

                d0 = (double) blockposition.getX();
                d1 = (double) blockposition.getY();
                d2 = (double) blockposition.getZ();
                f1 = 90.0F;
                f = 0.0F;
            } else {
                if (resourcekey == Level.OVERWORLD && worldserver.getDimensionKey() == Level.NETHER) {
                    this.enteredNetherPosition = this.position();
                }

                DimensionType dimensionmanager = worldserver1.dimensionType();
                DimensionType dimensionmanager1 = worldserver.dimensionType();

                d3 = 8.0D;
                if (!dimensionmanager.shrunk() && dimensionmanager1.shrunk()) {
                    d0 /= 8.0D;
                    d2 /= 8.0D;
                } else if (dimensionmanager.shrunk() && !dimensionmanager1.shrunk()) {
                    d0 *= 8.0D;
                    d2 *= 8.0D;
                }
            }

            // CraftBukkit start
            Location enter = this.getBukkitEntity().getLocation();
            Location exit = (worldserver == null) ? null : new Location(worldserver.getWorld(), d0, d1, d2, f1, f);
            PlayerPortalEvent event = new PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, 128, true, resourcekey == Level.END ? 0 : 16);
            Bukkit.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getTo() == null) {
                return null;
            }

            exit = event.getTo();
            if (exit == null) {
                return null;
            }
            worldserver = ((CraftWorld) exit.getWorld()).getHandle();
            d0 = exit.getX();
            d1 = exit.getY();
            d2 = exit.getZ();
            // CraftBukkit end

            // this.setPositionRotation(d0, d1, d2, f1, f); // CraftBukkit - PlayerTeleportEvent handles position changes
            worldserver1.getProfiler().pop();
            worldserver1.getProfiler().push("placing");
            // Spigot start - SPIGOT-5677, MC-114796: Fix portals generating outside world border
            double d4 = Math.max(-2.9999872E7D, worldserver.getWorldBorder().getMinX() + 16.0D);

            d3 = Math.max(-2.9999872E7D, worldserver.getWorldBorder().getMinZ() + 16.0D);
            // Spigot end
            double d5 = Math.min(2.9999872E7D, worldserver.getWorldBorder().getMaxX() - 16.0D);
            double d6 = Math.min(2.9999872E7D, worldserver.getWorldBorder().getMaxZ() - 16.0D);

            d0 = Mth.clamp(d0, d4, d5);
            d2 = Mth.clamp(d2, d3, d6);
            // this.setPositionRotation(d0, d1, d2, f1, f); // CraftBukkit - PlayerTeleportEvent handles position changes
            // CraftBukkit start - PlayerPortalEvent implementation
            Vec3 exitVelocity = Vec3.ZERO;
            BlockPos exitPosition = new BlockPos(d0, d1, d2);
            if (worldserver.getDimensionKey() == Level.END) {
                int i = exitPosition.getX();
                int j = exitPosition.getY() - 1;
                int k = exitPosition.getZ();
                if (event.getCanCreatePortal()) {
                    ServerLevel.a(worldserver, this);
                }
                // handled below for PlayerTeleportEvent
                // this.setPositionRotation((double) i, (double) j, (double) k, f1, 0.0F);
                exit.setX(i);
                exit.setY(j);
                exit.setZ(k);
                // this.setMot(Vec3D.a);
                exitVelocity = Vec3.ZERO;
            } else {
                BlockPattern.PortalInfo portalShape = worldserver.getPortalForcer().findAndTeleport(this, exitPosition, f2, event.getSearchRadius(), true);
                if (portalShape == null && event.getCanCreatePortal()) {
                    if (worldserver.getPortalForcer().createPortal(this, exitPosition, event.getCreationRadius())) { // Only check for new portal if creation succeeded
                        portalShape = worldserver.getPortalForcer().findAndTeleport(this, exitPosition, f2, event.getSearchRadius(), true);
                    }
                }
                // Check if portal was found
                if (portalShape == null) {
                    return null;
                }
                // Teleport handling - logic from PortalTravelAgent#findAndTeleport
                exitVelocity = portalShape.speed;
                exit.setX(portalShape.pos.x());
                exit.setY(portalShape.pos.y());
                exit.setZ(portalShape.pos.z());
                exit.setYaw(f2 + (float) portalShape.angle);
                // CraftBukkit end
            }

            worldserver1.getProfiler().pop();
            // CraftBukkit start - PlayerTeleportEvent
            PlayerTeleportEvent tpEvent = new PlayerTeleportEvent(this.getBukkitEntity(), enter, exit, cause);
            Bukkit.getServer().getPluginManager().callEvent(tpEvent);
            if (tpEvent.isCancelled() || tpEvent.getTo() == null) {
                return null;
            }

            exit = tpEvent.getTo();
            if (exit == null) {
                return null;
            }
            worldserver = ((CraftWorld) exit.getWorld()).getHandle();
            this.isChangingDimension = true; // CraftBukkit - Set teleport invulnerability only if player changing worlds

            this.connection.sendPacket(new ClientboundRespawnPacket(worldserver.getTypeKey(), worldserver.getDimensionKey(), BiomeManager.obfuscateSeed(worldserver.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), worldserver.isDebug(), worldserver.isFlat(), true));
            this.connection.sendPacket(new ClientboundChangeDifficultyPacket(this.level.getDifficulty(), this.level.getLevelData().isDifficultyLocked()));
            PlayerList playerlist = this.server.getPlayerList();

            playerlist.sendPlayerPermissionLevel(this);
            worldserver1.removePlayerImmediately(this);
            this.removed = false;

            this.setDeltaMovement(exitVelocity);
            // CraftBukkit end
            this.setLevel(worldserver);
            worldserver.addDuringPortalTeleport(this);
            this.triggerDimensionChangeTriggers(worldserver1);
            this.connection.teleport(exit); // CraftBukkit - use internal teleport without event
            this.connection.resetPosition(); // CraftBukkit - sync position after changing it (from PortalTravelAgent#findAndteleport)
            this.gameMode.setLevel(worldserver);
            this.connection.sendPacket(new ClientboundPlayerAbilitiesPacket(this.abilities));
            playerlist.sendLevelInfo(this, worldserver);
            playerlist.sendAllPlayerInfo(this);
            Iterator iterator = this.getActiveEffects().iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                this.connection.sendPacket(new ClientboundUpdateMobEffectPacket(this.getId(), mobeffect));
            }

            this.connection.sendPacket(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
            this.lastSentExp = -1;
            this.lastSentHealth = -1.0F;
            this.lastSentFood = -1;

            // CraftBukkit start
            PlayerChangedWorldEvent changeEvent = new PlayerChangedWorldEvent(this.getBukkitEntity(), worldserver.getWorld());
            this.level.getServerOH().getPluginManager().callEvent(changeEvent);
            // CraftBukkit end
            return this;
        }
    }

    public void triggerDimensionChangeTriggers(ServerLevel worldserver) {
        ResourceKey<Level> resourcekey = worldserver.getDimensionKey();
        ResourceKey<Level> resourcekey1 = this.level.getDimensionKey();

        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        if (resourcekey == Level.NETHER && resourcekey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (resourcekey1 != Level.NETHER) {
            this.enteredNetherPosition = null;
        }

    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer entityplayer) {
        return entityplayer.isSpectator() ? this.getCamera() == this : (this.isSpectator() ? false : super.broadcastToPlayer(entityplayer));
    }

    private void broadcast(BlockEntity tileentity) {
        if (tileentity != null) {
            ClientboundBlockEntityDataPacket packetplayouttileentitydata = tileentity.getUpdatePacket();

            if (packetplayouttileentitydata != null) {
                this.connection.sendPacket(packetplayouttileentitydata);
            }
        }

    }

    @Override
    public void take(Entity entity, int i) {
        super.take(entity, i);
        this.containerMenu.broadcastChanges();
    }

    // CraftBukkit start - moved bed result checks from below into separate method
    private Either<Player.BedSleepingProblem, Unit> getBedResult(BlockPos blockposition, Direction enumdirection) {
        if (!this.isSleeping() && this.isAlive()) {
            if (!this.level.dimensionType().natural()) {
                return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
            } else if (!this.bedInRange(blockposition, enumdirection)) {
                return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(blockposition, enumdirection)) {
                return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                this.setRespawnPosition(this.level.getDimensionKey(), blockposition, false, true);
                if (this.level.isDay()) {
                    return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
                } else {
                    if (!this.isCreative()) {
                        double d0 = 8.0D;
                        double d1 = 5.0D;
                        Vec3 vec3d = Vec3.atBottomCenterOf((Vec3i) blockposition);
                        List<Monster> list = this.level.getEntitiesOfClass(Monster.class, new AABB(vec3d.x() - 8.0D, vec3d.y() - 5.0D, vec3d.z() - 8.0D, vec3d.x() + 8.0D, vec3d.y() + 5.0D, vec3d.z() + 8.0D), (entitymonster) -> {
                            return entitymonster.isPreventingPlayerRest((Player) this);
                        });

                        if (!list.isEmpty()) {
                            return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                        }
                    }

                    return Either.right(Unit.INSTANCE);
                }
            }
        } else {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @Override
    public Either<Player.BedSleepingProblem, Unit> sleep(BlockPos blockposition, boolean force) {
        Direction enumdirection = (Direction) this.level.getType(blockposition).getValue(HorizontalDirectionalBlock.FACING);
        Either<Player.BedSleepingProblem, Unit> bedResult = this.getBedResult(blockposition, enumdirection);

        if (bedResult.left().orElse(null) == Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, blockposition, bedResult);
        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        {
            {
                {
                    Either<Player.BedSleepingProblem, Unit> either = super.sleep(blockposition, force).ifRight((unit) -> {
                        this.awardStat(Stats.SLEEP_IN_BED);
                        CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                    });

                    ((ServerLevel) this.level).updateSleepingPlayerList();
                    return either;
                }
            }
        }
        // CraftBukkit end
    }

    @Override
    public void startSleeping(BlockPos blockposition) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(blockposition);
    }

    private boolean bedInRange(BlockPos blockposition, Direction enumdirection) {
        return this.isReachableBedBlock(blockposition) || this.isReachableBedBlock(blockposition.relative(enumdirection.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos blockposition) {
        Vec3 vec3d = Vec3.atBottomCenterOf((Vec3i) blockposition);

        return Math.abs(this.getX() - vec3d.x()) <= 3.0D && Math.abs(this.getY() - vec3d.y()) <= 2.0D && Math.abs(this.getZ() - vec3d.z()) <= 3.0D;
    }

    private boolean bedBlocked(BlockPos blockposition, Direction enumdirection) {
        BlockPos blockposition1 = blockposition.above();

        return !this.freeAt(blockposition1) || !this.freeAt(blockposition1.relative(enumdirection.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean flag, boolean flag1) {
        if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
        if (this.isSleeping()) {
            this.getLevel().getChunkSourceOH().broadcastIncludingSelf(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(flag, flag1);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
        }

    }

    @Override
    public boolean startRiding(Entity entity, boolean flag) {
        Entity entity1 = this.getVehicle();

        if (!super.startRiding(entity, flag)) {
            return false;
        } else {
            Entity entity2 = this.getVehicle();

            if (entity2 != entity1 && this.connection != null) {
                this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
            }

            return true;
        }
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();

        super.stopRiding();
        Entity entity1 = this.getVehicle();

        if (entity1 != entity && this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
        }

    }

    @Override
    public boolean isInvulnerableTo(DamageSource damagesource) {
        return super.isInvulnerableTo(damagesource) || this.isChangingDimension() || this.abilities.invulnerable && damagesource == DamageSource.WITHER;
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {}

    @Override
    protected void onChangedBlock(BlockPos blockposition) {
        if (!this.isSpectator()) {
            super.onChangedBlock(blockposition);
        }

    }

    public void doCheckFallDamage(double d0, boolean flag) {
        BlockPos blockposition = this.getOnPos();

        if (this.level.hasChunkAt(blockposition)) {
            super.checkFallDamage(d0, flag, this.level.getType(blockposition), blockposition);
        }
    }

    @Override
    public void openTextEdit(SignBlockEntity tileentitysign) {
        tileentitysign.setAllowedPlayerEditor((Player) this);
        this.connection.sendPacket(new ClientboundOpenSignEditorPacket(tileentitysign.getBlockPos()));
    }

    public int nextContainerCounter() { // CraftBukkit - void -> int
        this.containerCounter = this.containerCounter % 100 + 1;
        return containerCounter; // CraftBukkit
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider itileinventory) {
        if (itileinventory == null) {
            return OptionalInt.empty();
        } else {
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu container = itileinventory.createMenu(this.containerCounter, this.inventory, this);

            // CraftBukkit start - Inventory open hook
            if (container != null) {
                container.setTitle(itileinventory.getDisplayName());

                boolean cancelled = false;
                container = CraftEventFactory.callInventoryOpenEvent(this, container, cancelled);
                if (container == null && !cancelled) { // Let pre-cancelled events fall through
                    // SPIGOT-5263 - close chest if cancelled
                    if (itileinventory instanceof Container) {
                        ((Container) itileinventory).stopOpen(this);
                    } else if (itileinventory instanceof ChestBlock.DoubleInventory) {
                        // SPIGOT-5355 - double chests too :(
                        ((ChestBlock.DoubleInventory) itileinventory).inventorylargechest.stopOpen(this);
                    }
                    return OptionalInt.empty();
                }
            }
            // CraftBukkit end
            if (container == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage((Component) (new TranslatableComponent("container.spectatorCantOpen")).withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // CraftBukkit start
                this.containerMenu = container;
                this.connection.sendPacket(new ClientboundOpenScreenPacket(container.containerId, container.getType(), container.getTitle()));
                // CraftBukkit end
                container.addSlotListener(this);
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void openTrade(int i, MerchantOffers merchantrecipelist, int j, int k, boolean flag, boolean flag1) {
        this.connection.sendPacket(new ClientboundMerchantOffersPacket(i, merchantrecipelist, j, k, flag, flag1));
    }

    @Override
    public void openHorseInventory(AbstractHorse entityhorseabstract, Container iinventory) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounter();
        AbstractContainerMenu container = new HorseInventoryMenu(this.containerCounter, this.inventory, iinventory, entityhorseabstract);
        container.setTitle(entityhorseabstract.getDisplayName());
        container = CraftEventFactory.callInventoryOpenEvent(this, container);

        if (container == null) {
            iinventory.stopOpen(this);
            return;
        }
        // CraftBukkit end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        // this.nextContainerCounter(); // CraftBukkit - moved up
        this.connection.sendPacket(new ClientboundHorseScreenOpenPacket(this.containerCounter, iinventory.getContainerSize(), entityhorseabstract.getId()));
        this.containerMenu = container; // CraftBukkit
        this.containerMenu.addSlotListener(this);
    }

    @Override
    public void openItemGui(ItemStack itemstack, InteractionHand enumhand) {
        Item item = itemstack.getItem();

        if (item == Items.WRITTEN_BOOK) {
            if (WrittenBookItem.resolveBookComponents(itemstack, this.createCommandSourceStack(), (Player) this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.sendPacket(new ClientboundOpenBookPacket(enumhand));
        }

    }

    @Override
    public void openCommandBlock(CommandBlockEntity tileentitycommand) {
        tileentitycommand.setSendToClient(true);
        this.broadcast((BlockEntity) tileentitycommand);
    }

    @Override
    public void slotChanged(AbstractContainerMenu container, int i, ItemStack itemstack) {
        if (!(container.getSlot(i) instanceof ResultSlot)) {
            if (container == this.inventoryMenu) {
                CriteriaTriggers.INVENTORY_CHANGED.trigger(this, this.inventory, itemstack);
            }

            if (!this.ignoreSlotUpdateHack) {
                this.connection.sendPacket(new ClientboundContainerSetSlotPacket(container.containerId, i, itemstack));
            }
        }
    }

    public void refreshContainer(AbstractContainerMenu container) {
        this.refreshContainer(container, container.getItems());
    }

    @Override
    public void refreshContainer(AbstractContainerMenu container, NonNullList<ItemStack> nonnulllist) {
        this.connection.sendPacket(new ClientboundContainerSetContentPacket(container.containerId, nonnulllist));
        this.connection.sendPacket(new ClientboundContainerSetSlotPacket(-1, -1, this.inventory.getCarried()));
        // CraftBukkit start - Send a Set Slot to update the crafting result slot
        if (java.util.EnumSet.of(InventoryType.CRAFTING,InventoryType.WORKBENCH).contains(container.getBukkitView().getType())) {
            this.connection.sendPacket(new ClientboundContainerSetSlotPacket(container.containerId, 0, container.getSlot(0).getItem()));
        }
        // CraftBukkit end
    }

    @Override
    public void setContainerData(AbstractContainerMenu container, int i, int j) {
        this.connection.sendPacket(new ClientboundContainerSetDataPacket(container.containerId, i, j));
    }

    @Override
    public void closeContainer() {
        CraftEventFactory.handleInventoryCloseEvent(this); // CraftBukkit
        this.connection.sendPacket(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    public void broadcastCarriedItem() {
        if (!this.ignoreSlotUpdateHack) {
            this.connection.sendPacket(new ClientboundContainerSetSlotPacket(-1, -1, this.inventory.getCarried()));
        }
    }

    public void doCloseContainer() {
        this.containerMenu.removed((Player) this);
        this.containerMenu = this.inventoryMenu;
    }

    public void setPlayerInput(float f, float f1, boolean flag, boolean flag1) {
        if (this.isPassenger()) {
            if (f >= -1.0F && f <= 1.0F) {
                this.xxa = f;
            }

            if (f1 >= -1.0F && f1 <= 1.0F) {
                this.zza = f1;
            }

            this.jumping = flag;
            this.setShiftKeyDown(flag1);
        }

    }

    @Override
    public void awardStat(Stat<?> statistic, int i) {
        this.stats.increment(this, statistic, i);
        this.level.getServerOH().getScoreboardManager().getScoreboardScores(statistic, this.getScoreboardName(), (scoreboardscore) -> { // CraftBukkit - Get our scores instead
            scoreboardscore.add(i);
        });
    }

    @Override
    public void resetStat(Stat<?> statistic) {
        this.stats.setValue(this, statistic, 0);
        this.level.getServerOH().getScoreboardManager().getScoreboardScores(statistic, this.getScoreboardName(), Score::reset); // CraftBukkit - Get our scores instead
    }

    @Override
    public int awardRecipes(Collection<Recipe<?>> collection) {
        return this.recipeBook.addRecipes(collection, this);
    }

    @Override
    public void awardRecipesByKey(ResourceLocation[] aminecraftkey) {
        List<Recipe<?>> list = Lists.newArrayList();
        ResourceLocation[] aminecraftkey1 = aminecraftkey;
        int i = aminecraftkey.length;

        for (int j = 0; j < i; ++j) {
            ResourceLocation minecraftkey = aminecraftkey1[j];

            this.server.getRecipeManager().byKey(minecraftkey).ifPresent(list::add);
        }

        this.awardRecipes(list);
    }

    @Override
    public int resetRecipes(Collection<Recipe<?>> collection) {
        return this.recipeBook.removeRecipes(collection, this);
    }

    @Override
    public void giveExperiencePoints(int i) {
        super.giveExperiencePoints(i);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }

    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
        this.lastSentExp = -1; // CraftBukkit - Added to reset
    }

    // CraftBukkit start - Support multi-line messages
    public void sendMessage(Component[] ichatbasecomponent) {
        for (Component component : ichatbasecomponent) {
            this.sendMessage(component, Util.NIL_UUID);
        }
    }
    // CraftBukkit end

    @Override
    public void displayClientMessage(Component ichatbasecomponent, boolean flag) {
        this.connection.sendPacket(new ClientboundChatPacket(ichatbasecomponent, flag ? ChatType.GAME_INFO : ChatType.CHAT, Util.NIL_UUID));
    }

    @Override
    protected void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.sendPacket(new ClientboundEntityEventPacket(this, (byte) 9));
            super.completeUsingItem();
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor argumentanchor_anchor, Vec3 vec3d) {
        super.lookAt(argumentanchor_anchor, vec3d);
        this.connection.sendPacket(new ClientboundPlayerLookAtPacket(argumentanchor_anchor, vec3d.x, vec3d.y, vec3d.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor argumentanchor_anchor, Entity entity, EntityAnchorArgument.Anchor argumentanchor_anchor1) {
        Vec3 vec3d = argumentanchor_anchor1.apply(entity);

        super.lookAt(argumentanchor_anchor, vec3d);
        this.connection.sendPacket(new ClientboundPlayerLookAtPacket(argumentanchor_anchor, entity, argumentanchor_anchor1));
    }

    public void restoreFrom(ServerPlayer entityplayer, boolean flag) {
        if (flag) {
            this.inventory.replaceWith(entityplayer.inventory);
            this.setHealth(entityplayer.getHealth());
            this.foodData = entityplayer.foodData;
            this.experienceLevel = entityplayer.experienceLevel;
            this.totalExperience = entityplayer.totalExperience;
            this.experienceProgress = entityplayer.experienceProgress;
            this.setScore(entityplayer.getScore());
            this.portalEntranceBlock = entityplayer.portalEntranceBlock;
            this.portalEntranceOffset = entityplayer.portalEntranceOffset;
            this.portalEntranceForwards = entityplayer.portalEntranceForwards;
        } else if (this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || entityplayer.isSpectator()) {
            this.inventory.replaceWith(entityplayer.inventory);
            this.experienceLevel = entityplayer.experienceLevel;
            this.totalExperience = entityplayer.totalExperience;
            this.experienceProgress = entityplayer.experienceProgress;
            this.setScore(entityplayer.getScore());
        }

        this.enchantmentSeed = entityplayer.enchantmentSeed;
        this.enderChestInventory = entityplayer.enderChestInventory;
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, entityplayer.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        // this.recipeBook.a((RecipeBook) entityplayer.recipeBook); // CraftBukkit
        this.entitiesToRemove.addAll(entityplayer.entitiesToRemove);
        this.seenCredits = entityplayer.seenCredits;
        this.enteredNetherPosition = entityplayer.enteredNetherPosition;
        this.setShoulderEntityLeft(entityplayer.getShoulderEntityLeft());
        this.setShoulderEntityRight(entityplayer.getShoulderEntityRight());

        this.isTouchingLava = false; // SPIGOT-4767
    }

    @Override
    protected void onEffectAdded(MobEffectInstance mobeffect) {
        super.onEffectAdded(mobeffect);
        this.connection.sendPacket(new ClientboundUpdateMobEffectPacket(this.getId(), mobeffect));
        if (mobeffect.getEffect() == MobEffects.LEVITATION) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance mobeffect, boolean flag) {
        super.onEffectUpdated(mobeffect, flag);
        this.connection.sendPacket(new ClientboundUpdateMobEffectPacket(this.getId(), mobeffect));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this);
    }

    @Override
    protected void onEffectRemoved(MobEffectInstance mobeffect) {
        super.onEffectRemoved(mobeffect);
        this.connection.sendPacket(new ClientboundRemoveMobEffectPacket(this.getId(), mobeffect.getEffect()));
        if (mobeffect.getEffect() == MobEffects.LEVITATION) {
            this.levitationStartPos = null;
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this);
    }

    @Override
    public void teleportTo(double d0, double d1, double d2) {
        this.connection.teleport(d0, d1, d2, this.yRot, this.xRot);
    }

    @Override
    public void moveTo(double d0, double d1, double d2) {
        this.connection.teleport(d0, d1, d2, this.yRot, this.xRot);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity entity) {
        this.getLevel().getChunkSourceOH().broadcastIncludingSelf(this, new ClientboundAnimatePacket(entity, 4));
    }

    @Override
    public void magicCrit(Entity entity) {
        this.getLevel().getChunkSourceOH().broadcastIncludingSelf(this, new ClientboundAnimatePacket(entity, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.sendPacket(new ClientboundPlayerAbilitiesPacket(this.abilities));
            this.updateInvisibilityStatus();
        }
    }

    public ServerLevel getLevel() {
        return (ServerLevel) this.level;
    }

    @Override
    public void setGameMode(GameType enumgamemode) {
        // CraftBukkit start
        if (enumgamemode == this.gameMode.getGameModeForPlayer()) {
            return;
        }

        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(getBukkitEntity(), GameMode.getByValue(enumgamemode.getId()));
        level.getServerOH().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end

        this.gameMode.setGameModeForPlayer(enumgamemode);
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float) enumgamemode.getId()));
        if (enumgamemode == GameType.SPECTATOR) {
            this.removeEntitiesOnShoulder();
            this.stopRiding();
        } else {
            this.setCamera(this);
        }

        this.onUpdateAbilities();
        this.updateEffectVisibility();
    }

    @Override
    public boolean isSpectator() {
        return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    @Override
    public void sendMessage(Component ichatbasecomponent, UUID uuid) {
        this.sendMessage(ichatbasecomponent, ChatType.SYSTEM, uuid);
    }

    public void sendMessage(Component ichatbasecomponent, ChatType chatmessagetype, UUID uuid) {
        this.connection.send((Packet) (new ClientboundChatPacket(ichatbasecomponent, chatmessagetype, uuid)), (future) -> {
            if (!future.isSuccess() && (chatmessagetype == ChatType.GAME_INFO || chatmessagetype == ChatType.SYSTEM)) {
                boolean flag = true;
                String s = ichatbasecomponent.getString(256);
                MutableComponent ichatmutablecomponent = (new TextComponent(s)).withStyle(ChatFormatting.YELLOW);

                this.connection.sendPacket(new ClientboundChatPacket((new TranslatableComponent("multiplayer.message_not_delivered", new Object[]{ichatmutablecomponent})).withStyle(ChatFormatting.RED), ChatType.SYSTEM, uuid));
            }

        });
    }

    public String getIpAddress() {
        String s = this.connection.connection.getRemoteAddress().toString();

        s = s.substring(s.indexOf("/") + 1);
        s = s.substring(0, s.indexOf(":"));
        return s;
    }

    public String locale = "en_us"; // CraftBukkit - add, lowercase
    public void updateOptions(ServerboundClientInformationPacket packetplayinsettings) {
        // CraftBukkit start
        if (getMainArm() != packetplayinsettings.getMainHand()) {
            PlayerChangedMainHandEvent event = new PlayerChangedMainHandEvent(getBukkitEntity(), getMainArm() == HumanoidArm.LEFT ? MainHand.LEFT : MainHand.RIGHT);
            this.server.server.getPluginManager().callEvent(event);
        }
        if (!this.locale.equals(packetplayinsettings.language)) {
            PlayerLocaleChangeEvent event = new PlayerLocaleChangeEvent(getBukkitEntity(), packetplayinsettings.language);
            this.server.server.getPluginManager().callEvent(event);
        }
        this.clientViewDistance = packetplayinsettings.viewDistance;
        // CraftBukkit end
        this.chatVisibility = packetplayinsettings.getChatVisibility();
        this.canChatColor = packetplayinsettings.getChatColors();
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) packetplayinsettings.getModelCustomisation());
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MAIN_HAND, (byte) (packetplayinsettings.getMainHand() == HumanoidArm.LEFT ? 0 : 1));
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    public void sendTexturePack(String s, String s1) {
        this.connection.sendPacket(new ClientboundResourcePackPacket(s, s1));
    }

    @Override
    protected int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    public void sendRemoveEntity(Entity entity) {
        if (entity instanceof Player) {
            this.connection.sendPacket(new ClientboundRemoveEntitiesPacket(new int[]{entity.getId()}));
        } else {
            this.entitiesToRemove.add((Integer) entity.getId()); // CraftBukkit - decompile error
        }

    }

    public void cancelRemoveEntity(Entity entity) {
        this.entitiesToRemove.remove((Integer) entity.getId()); // CraftBukkit - decompile error
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }

    }

    public Entity getCamera() {
        return (Entity) (this.camera == null ? this : this.camera);
    }

    public void setCamera(Entity entity) {
        Entity entity1 = this.getCamera();

        this.camera = (Entity) (entity == null ? this : entity);
        if (entity1 != this.camera) {
            this.connection.sendPacket(new ClientboundSetCameraPacket(this.camera));
            this.connection.a(this.camera.getX(), this.camera.getY(), this.camera.getZ(), this.yRot, this.xRot, TeleportCause.SPECTATE); // CraftBukkit
        }

    }

    @Override
    protected void processDimensionDelay() {
        if (this.changingDimensionDelay > 0 && !this.isChangingDimension) {
            --this.changingDimensionDelay;
        }

    }

    @Override
    public void attack(Entity entity) {
        if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            this.setCamera(entity);
        } else {
            super.attack(entity);
        }

    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public Component getTabListDisplayName() {
        return listName; // CraftBukkit
    }

    @Override
    public void swing(InteractionHand enumhand) {
        super.swing(enumhand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    // CraftBukkit start
    public void teleportTo(ServerLevel worldserver, double d0, double d1, double d2, float f, float f1) {
        this.a(worldserver, d0, d1, d2, f, f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public void a(ServerLevel worldserver, double d0, double d1, double d2, float f, float f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        this.setCamera(this);
        this.stopRiding();
        /* CraftBukkit start - replace with bukkit handling for multi-world
        if (worldserver == this.world) {
            this.playerConnection.a(d0, d1, d2, f, f1);
        } else {
            WorldServer worldserver1 = this.getWorldServer();
            WorldData worlddata = worldserver.getWorldData();

            this.playerConnection.sendPacket(new PacketPlayOutRespawn(worldserver.getTypeKey(), worldserver.getDimensionKey(), BiomeManager.a(worldserver.getSeed()), this.playerInteractManager.getGameMode(), this.playerInteractManager.c(), worldserver.isDebugWorld(), worldserver.isFlatWorld(), true));
            this.playerConnection.sendPacket(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
            this.server.getPlayerList().d(this);
            worldserver1.removePlayer(this);
            this.dead = false;
            this.setPositionRotation(d0, d1, d2, f, f1);
            this.spawnIn(worldserver);
            worldserver.addPlayerCommand(this);
            this.triggerDimensionAdvancements(worldserver1);
            this.playerConnection.a(d0, d1, d2, f, f1);
            this.playerInteractManager.a(worldserver);
            this.server.getPlayerList().a(this, worldserver);
            this.server.getPlayerList().updateClient(this);
        }
        */
        this.getBukkitEntity().teleport(new Location(worldserver.getWorld(), d0, d1, d2, f, f1), cause);
        // CraftBukkit end

    }

    @Nullable
    public BlockPos getRespawnPosition() {
        return this.respawnPosition;
    }

    public ResourceKey<Level> getSpawnDimension() {
        return this.respawnDimension;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void setRespawnPosition(ResourceKey<Level> resourcekey, @Nullable BlockPos blockposition, boolean flag, boolean flag1) {
        if (blockposition != null) {
            boolean flag2 = blockposition.equals(this.respawnPosition) && resourcekey.equals(this.respawnDimension);

            if (flag1 && !flag2) {
                this.sendMessage(new TranslatableComponent("block.minecraft.set_spawn"), Util.NIL_UUID);
            }

            this.respawnPosition = blockposition;
            this.respawnDimension = resourcekey;
            this.respawnForced = flag;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnForced = false;
        }

    }

    public void trackChunk(ChunkPos chunkcoordintpair, Packet<?> packet, Packet<?> packet1) {
        this.connection.sendPacket(packet1);
        this.connection.sendPacket(packet);
    }

    public void untrackChunk(ChunkPos chunkcoordintpair) {
        if (this.isAlive()) {
            this.connection.sendPacket(new ClientboundForgetLevelChunkPacket(chunkcoordintpair.x, chunkcoordintpair.z));
        }

    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos sectionposition) {
        this.lastSectionPos = sectionposition;
    }

    @Override
    public void playNotifySound(SoundEvent soundeffect, SoundSource soundcategory, float f, float f1) {
        this.connection.sendPacket(new ClientboundSoundPacket(soundeffect, soundcategory, this.getX(), this.getY(), this.getZ(), f, f1));
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddPlayerPacket(this);
    }

    @Override
    public ItemEntity drop(ItemStack itemstack, boolean flag, boolean flag1) {
        ItemEntity entityitem = super.drop(itemstack, flag, flag1);

        if (entityitem == null) {
            return null;
        } else {
            this.level.addFreshEntity(entityitem);
            ItemStack itemstack1 = entityitem.getItem();

            if (flag1) {
                if (!itemstack1.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(itemstack1.getItem()), itemstack.getCount());
                }

                this.awardStat(Stats.DROP);
            }

            return entityitem;
        }
    }

    // CraftBukkit start - Add per-player time and weather.
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.level.getDayTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.level.getDayTime() - (this.level.getDayTime() % 24000) + this.timeOffset;
        }
    }

    public WeatherType weather = null;

    public WeatherType getPlayerWeather() {
        return this.weather;
    }

    public void setPlayerWeather(WeatherType type, boolean plugin) {
        if (!plugin && this.weather != null) {
            return;
        }

        if (plugin) {
            this.weather = type;
        }

        if (type == WeatherType.DOWNFALL) {
            this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        } else {
            this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        }
    }

    private float pluginRainPosition;
    private float pluginRainPositionPrevious;

    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weather == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (pluginRainPositionPrevious != pluginRainPosition) {
                this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, pluginRainPosition));
            }
        }

        if (oldThunder != newThunder) {
            if (weather == WeatherType.DOWNFALL || weather == null) {
                this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
            }
        }
    }

    public void tickWeather() {
        if (this.weather == null) return;

        pluginRainPositionPrevious = pluginRainPosition;
        if (weather == WeatherType.DOWNFALL) {
            pluginRainPosition += 0.01;
        } else {
            pluginRainPosition -= 0.01;
        }

        pluginRainPosition = Mth.clamp(pluginRainPosition, 0.0F, 1.0F);
    }

    public void resetPlayerWeather() {
        this.weather = null;
        this.setPlayerWeather(this.level.getLevelData().isRaining() ? WeatherType.DOWNFALL : WeatherType.CLEAR, false);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    // SPIGOT-1903, MC-98153
    public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
        this.moveTo(x, y, z, yaw, pitch);
        this.connection.resetPosition();
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || !getBukkitEntity().isOnline();
    }

    @Override
    public Scoreboard getScoreboard() {
        return getBukkitEntity().getScoreboard().getHandle();
    }

    public void reset() {
        float exp = 0;
        boolean keepInventory = this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

        if (this.keepLevel || keepInventory) {
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }

        this.setHealth(this.getMaxHealth());
        this.remainingFireTicks = 0;
        this.fallDistance = 0;
        this.foodData = new FoodData(this);
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0;
        this.deathTime = 0;
        this.setArrowCount(0);
        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new CombatTracker(this);
        this.lastSentExp = -1;
        if (this.keepLevel || keepInventory) {
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
    }

    @Override
    public CraftPlayer getBukkitEntity() {
        return (CraftPlayer) super.getBukkitEntity();
    }
    // CraftBukkit end
}
