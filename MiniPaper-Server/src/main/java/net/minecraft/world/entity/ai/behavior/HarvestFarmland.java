package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

public class HarvestFarmland extends Behavior<Villager> {

    @Nullable
    private BlockPos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();

    public HarvestFarmland() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel worldserver, Villager entityvillager) {
        if (!worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        } else if (entityvillager.getVillagerData().getProfession() != VillagerProfession.FARMER) {
            return false;
        } else {
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = entityvillager.blockPosition().i();

            this.validFarmlandAroundVillager.clear();

            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    for (int k = -1; k <= 1; ++k) {
                        blockposition_mutableblockposition.c(entityvillager.getX() + (double) i, entityvillager.getY() + (double) j, entityvillager.getZ() + (double) k);
                        if (this.validPos((BlockPos) blockposition_mutableblockposition, worldserver)) {
                            this.validFarmlandAroundVillager.add(new BlockPos(blockposition_mutableblockposition));
                        }
                    }
                }
            }

            this.aboveFarmlandPos = this.getValidFarmland(worldserver);
            return this.aboveFarmlandPos != null;
        }
    }

    @Nullable
    private BlockPos getValidFarmland(ServerLevel worldserver) {
        return this.validFarmlandAroundVillager.isEmpty() ? null : (BlockPos) this.validFarmlandAroundVillager.get(worldserver.getRandom().nextInt(this.validFarmlandAroundVillager.size()));
    }

    private boolean validPos(BlockPos blockposition, ServerLevel worldserver) {
        BlockState iblockdata = worldserver.getType(blockposition);
        Block block = iblockdata.getBlock();
        Block block1 = worldserver.getType(blockposition.below()).getBlock();

        return block instanceof CropBlock && ((CropBlock) block).isRipe(iblockdata) || iblockdata.isAir() && block1 instanceof FarmBlock;
    }

    protected void start(ServerLevel worldserver, Villager entityvillager, long i) {
        if (i > this.nextOkStartTime && this.aboveFarmlandPos != null) {
            entityvillager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new BlockPosTracker(this.aboveFarmlandPos))); // CraftBukkit - decompile error
            entityvillager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1))); // CraftBukkit - decompile error
        }

    }

    protected void tick(ServerLevel worldserver, Villager entityvillager, long i) {
        entityvillager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        entityvillager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.timeWorkedSoFar = 0;
        this.nextOkStartTime = i + 40L;
    }

    protected void d(ServerLevel worldserver, Villager entityvillager, long i) {
        if (this.aboveFarmlandPos == null || this.aboveFarmlandPos.closerThan((Position) entityvillager.position(), 1.0D)) {
            if (this.aboveFarmlandPos != null && i > this.nextOkStartTime) {
                BlockState iblockdata = worldserver.getType(this.aboveFarmlandPos);
                Block block = iblockdata.getBlock();
                Block block1 = worldserver.getType(this.aboveFarmlandPos.below()).getBlock();

                if (block instanceof CropBlock && ((CropBlock) block).isRipe(iblockdata)) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entityvillager, this.aboveFarmlandPos, Blocks.AIR.getBlockData()).isCancelled()) {
                        worldserver.destroyBlock(this.aboveFarmlandPos, true, entityvillager);
                    }
                    // CraftBukkit end
                }

                if (iblockdata.isAir() && block1 instanceof FarmBlock && entityvillager.hasFarmSeeds()) {
                    SimpleContainer inventorysubcontainer = entityvillager.getInventory();

                    for (int j = 0; j < inventorysubcontainer.getContainerSize(); ++j) {
                        ItemStack itemstack = inventorysubcontainer.getItem(j);
                        boolean flag = false;

                        if (!itemstack.isEmpty()) {
                            // CraftBukkit start
                            Block planted = null;
                            if (itemstack.getItem() == Items.WHEAT_SEEDS) {
                                planted = Blocks.WHEAT;
                                flag = true;
                            } else if (itemstack.getItem() == Items.POTATO) {
                                planted = Blocks.POTATOES;
                                flag = true;
                            } else if (itemstack.getItem() == Items.CARROT) {
                                planted = Blocks.CARROTS;
                                flag = true;
                            } else if (itemstack.getItem() == Items.BEETROOT_SEEDS) {
                                planted = Blocks.BEETROOTS;
                                flag = true;
                            }

                            if (planted != null && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entityvillager, this.aboveFarmlandPos, planted.getBlockData()).isCancelled()) {
                                worldserver.setTypeAndData(this.aboveFarmlandPos, planted.getBlockData(), 3);
                            } else {
                                flag = false;
                            }
                            // CraftBukkit end
                        }

                        if (flag) {
                            worldserver.playSound((Player) null, (double) this.aboveFarmlandPos.getX(), (double) this.aboveFarmlandPos.getY(), (double) this.aboveFarmlandPos.getZ(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                            itemstack.shrink(1);
                            if (itemstack.isEmpty()) {
                                inventorysubcontainer.setItem(j, ItemStack.EMPTY);
                            }
                            break;
                        }
                    }
                }

                if (block instanceof CropBlock && !((CropBlock) block).isRipe(iblockdata)) {
                    this.validFarmlandAroundVillager.remove(this.aboveFarmlandPos);
                    this.aboveFarmlandPos = this.getValidFarmland(worldserver);
                    if (this.aboveFarmlandPos != null) {
                        this.nextOkStartTime = i + 20L;
                        entityvillager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1))); // CraftBukkit - decompile error
                        entityvillager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new BlockPosTracker(this.aboveFarmlandPos))); // CraftBukkit - decompile error
                    }
                }
            }

            ++this.timeWorkedSoFar;
        }
    }

    protected boolean b(ServerLevel worldserver, Villager entityvillager, long i) {
        return this.timeWorkedSoFar < 200;
    }
}
