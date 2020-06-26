package net.minecraft.world.entity.npc;

import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;

public class WanderingTraderSpawner implements CustomSpawner {

    private final Random random = new Random();
    private final ServerLevelData serverLevelData;
    private int tickDelay;
    private int spawnDelay;
    private int spawnChance;

    public WanderingTraderSpawner(ServerLevelData iworlddataserver) {
        this.serverLevelData = iworlddataserver;
        this.tickDelay = 1200;
        this.spawnDelay = iworlddataserver.getWanderingTraderSpawnDelay();
        this.spawnChance = iworlddataserver.getWanderingTraderSpawnChance();
        if (this.spawnDelay == 0 && this.spawnChance == 0) {
            this.spawnDelay = 24000;
            iworlddataserver.setWanderingTraderSpawnDelay(this.spawnDelay);
            this.spawnChance = 25;
            iworlddataserver.setWanderingTraderSpawnChance(this.spawnChance);
        }

    }

    @Override
    public int tick(ServerLevel worldserver, boolean flag, boolean flag1) {
        if (!worldserver.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING)) {
            return 0;
        } else if (--this.tickDelay > 0) {
            return 0;
        } else {
            this.tickDelay = 1200;
            this.spawnDelay -= 1200;
            this.serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
            if (this.spawnDelay > 0) {
                return 0;
            } else {
                this.spawnDelay = 24000;
                if (!worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                    return 0;
                } else {
                    int i = this.spawnChance;

                    this.spawnChance = Mth.clamp(this.spawnChance + 25, 25, 75);
                    this.serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
                    if (this.random.nextInt(100) > i) {
                        return 0;
                    } else if (this.spawn(worldserver)) {
                        this.spawnChance = 25;
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    }

    private boolean spawn(ServerLevel worldserver) {
        ServerPlayer entityplayer = worldserver.getRandomPlayer();

        if (entityplayer == null) {
            return true;
        } else if (this.random.nextInt(10) != 0) {
            return false;
        } else {
            BlockPos blockposition = entityplayer.blockPosition();
            boolean flag = true;
            PoiManager villageplace = worldserver.getPoiManager();
            Optional<BlockPos> optional = villageplace.find(PoiType.MEETING.getPredicate(), (blockposition1) -> {
                return true;
            }, blockposition, 48, PoiManager.Occupancy.ANY);
            BlockPos blockposition1 = (BlockPos) optional.orElse(blockposition);
            BlockPos blockposition2 = this.findSpawnPositionNear(worldserver, blockposition1, 48);

            if (blockposition2 != null && this.hasEnoughSpace(worldserver, blockposition2)) {
                if (worldserver.getBiome(blockposition2) == Biomes.THE_VOID) {
                    return false;
                }

                WanderingTrader entityvillagertrader = (WanderingTrader) EntityType.WANDERING_TRADER.spawnCreature(worldserver, (CompoundTag) null, (Component) null, (Player) null, blockposition2, MobSpawnType.EVENT, false, false, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit

                if (entityvillagertrader != null) {
                    for (int i = 0; i < 2; ++i) {
                        this.tryToSpawnLlamaFor(entityvillagertrader, 4);
                    }

                    this.serverLevelData.setWanderingTraderId(entityvillagertrader.getUUID());
                    entityvillagertrader.setDespawnDelay(48000);
                    entityvillagertrader.setWanderTarget(blockposition1);
                    entityvillagertrader.restrictTo(blockposition1, 16);
                    return true;
                }
            }

            return false;
        }
    }

    private void tryToSpawnLlamaFor(WanderingTrader entityvillagertrader, int i) {
        BlockPos blockposition = this.findSpawnPositionNear(entityvillagertrader.level, entityvillagertrader.blockPosition(), i);

        if (blockposition != null) {
            TraderLlama entityllamatrader = (TraderLlama) EntityType.TRADER_LLAMA.spawnCreature(entityvillagertrader.level, (CompoundTag) null, (Component) null, (Player) null, blockposition, MobSpawnType.EVENT, false, false, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit

            if (entityllamatrader != null) {
                entityllamatrader.setLeashedTo(entityvillagertrader, true);
            }
        }
    }

    @Nullable
    private BlockPos findSpawnPositionNear(LevelReader iworldreader, BlockPos blockposition, int i) {
        BlockPos blockposition1 = null;

        for (int j = 0; j < 10; ++j) {
            int k = blockposition.getX() + this.random.nextInt(i * 2) - i;
            int l = blockposition.getZ() + this.random.nextInt(i * 2) - i;
            int i1 = iworldreader.getHeight(Heightmap.Types.WORLD_SURFACE, k, l);
            BlockPos blockposition2 = new BlockPos(k, i1, l);

            if (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, iworldreader, blockposition2, EntityType.WANDERING_TRADER)) {
                blockposition1 = blockposition2;
                break;
            }
        }

        return blockposition1;
    }

    private boolean hasEnoughSpace(BlockGetter iblockaccess, BlockPos blockposition) {
        Iterator iterator = BlockPos.betweenClosed(blockposition, blockposition.offset(1, 2, 1)).iterator();

        BlockPos blockposition1;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            blockposition1 = (BlockPos) iterator.next();
        } while (iblockaccess.getType(blockposition1).getCollisionShape(iblockaccess, blockposition1).isEmpty());

        return false;
    }
}
