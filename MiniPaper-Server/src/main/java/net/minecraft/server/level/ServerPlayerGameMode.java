package net.minecraft.server.level;

import java.util.Objects;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockBreakAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import java.util.ArrayList;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
// CraftBukkit end

public class ServerPlayerGameMode {

    private static final Logger LOGGER = LogManager.getLogger();
    public ServerLevel level;
    public ServerPlayer player;
    private GameType gameModeForPlayer;
    private GameType previousGameModeForPlayer;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private BlockPos destroyPos;
    private int gameTicks;
    private boolean hasDelayedDestroy;
    private BlockPos delayedDestroyPos;
    private int delayedTickStart;
    private int lastSentState;

    public ServerPlayerGameMode(ServerLevel worldserver) {
        this.gameModeForPlayer = GameType.NOT_SET;
        this.previousGameModeForPlayer = GameType.NOT_SET;
        this.destroyPos = BlockPos.ZERO;
        this.delayedDestroyPos = BlockPos.ZERO;
        this.lastSentState = -1;
        this.level = worldserver;
    }

    public void setGameModeForPlayer(GameType enumgamemode) {
        this.setGameModeForPlayer(enumgamemode, enumgamemode != this.gameModeForPlayer ? this.gameModeForPlayer : this.previousGameModeForPlayer);
    }

    public void setGameModeForPlayer(GameType enumgamemode, GameType enumgamemode1) {
        this.previousGameModeForPlayer = enumgamemode1;
        this.gameModeForPlayer = enumgamemode;
        enumgamemode.updatePlayerAbilities(this.player.abilities);
        this.player.onUpdateAbilities();
        this.player.server.getPlayerList().sendAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.UPDATE_GAME_MODE, new ServerPlayer[]{this.player}), this.player); // CraftBukkit
        this.level.updateSleepingPlayerList();
    }

    public GameType getGameModeForPlayer() {
        return this.gameModeForPlayer;
    }

    public GameType getPreviousGameModeForPlayer() {
        return this.previousGameModeForPlayer;
    }

    public boolean isSurvival() {
        return this.gameModeForPlayer.isSurvival();
    }

    public boolean isCreative() {
        return this.gameModeForPlayer.isCreative();
    }

    public void updateGameMode(GameType enumgamemode) {
        if (this.gameModeForPlayer == GameType.NOT_SET) {
            this.gameModeForPlayer = enumgamemode;
        }

        this.setGameModeForPlayer(this.gameModeForPlayer);
    }

    public void tick() {
        this.gameTicks = MinecraftServer.currentTick; // CraftBukkit;
        BlockState iblockdata;

        if (this.hasDelayedDestroy) {
            iblockdata = this.level.getType(this.delayedDestroyPos);
            if (iblockdata.isAir()) {
                this.hasDelayedDestroy = false;
            } else {
                float f = this.incrementDestroyProgress(iblockdata, this.delayedDestroyPos, this.delayedTickStart);

                if (f >= 1.0F) {
                    this.hasDelayedDestroy = false;
                    this.destroyBlock(this.delayedDestroyPos);
                }
            }
        } else if (this.isDestroyingBlock) {
            iblockdata = this.level.getType(this.destroyPos);
            if (iblockdata.isAir()) {
                this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                this.lastSentState = -1;
                this.isDestroyingBlock = false;
            } else {
                this.incrementDestroyProgress(iblockdata, this.destroyPos, this.destroyProgressStart);
            }
        }

    }

    private float incrementDestroyProgress(BlockState iblockdata, BlockPos blockposition, int i) {
        int j = this.gameTicks - i;
        float f = iblockdata.getDestroyProgress(this.player, this.player.level, blockposition) * (float) (j + 1);
        int k = (int) (f * 10.0F);

        if (k != this.lastSentState) {
            this.level.destroyBlockProgress(this.player.getId(), blockposition, k);
            this.lastSentState = k;
        }

        return f;
    }

    public void handleBlockBreakAction(BlockPos blockposition, ServerboundPlayerActionPacket.Action packetplayinblockdig_enumplayerdigtype, Direction enumdirection, int i) {
        double d0 = this.player.getX() - ((double) blockposition.getX() + 0.5D);
        double d1 = this.player.getY() - ((double) blockposition.getY() + 0.5D) + 1.5D;
        double d2 = this.player.getZ() - ((double) blockposition.getZ() + 0.5D);
        double d3 = d0 * d0 + d1 * d1 + d2 * d2;

        if (d3 > 36.0D) {
            this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, false, "too far"));
        } else if (blockposition.getY() >= i) {
            this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, false, "too high"));
        } else {
            BlockState iblockdata;

            if (packetplayinblockdig_enumplayerdigtype == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract((Player) this.player, blockposition)) {
                    // CraftBukkit start - fire PlayerInteractEvent
                    CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, blockposition, enumdirection, this.player.inventory.getSelected(), InteractionHand.MAIN_HAND);
                    this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, false, "may not interact"));
                    // Update any tile entity data for this block
                    BlockEntity tileentity = level.getBlockEntity(blockposition);
                    if (tileentity != null) {
                        this.player.connection.sendPacket(tileentity.getUpdatePacket());
                    }
                    // CraftBukkit end
                    return;
                }

                // CraftBukkit start
                PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, blockposition, enumdirection, this.player.inventory.getSelected(), InteractionHand.MAIN_HAND);
                if (event.isCancelled()) {
                    // Let the client know the block still exists
                    this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    // Update any tile entity data for this block
                    BlockEntity tileentity = this.level.getBlockEntity(blockposition);
                    if (tileentity != null) {
                        this.player.connection.sendPacket(tileentity.getUpdatePacket());
                    }
                    return;
                }
                // CraftBukkit end

                if (this.isCreative()) {
                    this.destroyAndAck(blockposition, packetplayinblockdig_enumplayerdigtype, "creative destroy");
                    return;
                }

                if (this.player.blockActionRestricted((Level) this.level, blockposition, this.gameModeForPlayer)) {
                    this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, false, "block action restricted"));
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;

                iblockdata = this.level.getType(blockposition);
                // CraftBukkit start - Swings at air do *NOT* exist.
                if (event.useInteractedBlock() == Event.Result.DENY) {
                    // If we denied a door from opening, we need to send a correcting update to the client, as it already opened the door.
                    BlockState data = this.level.getType(blockposition);
                    if (data.getBlock() instanceof DoorBlock) {
                        // For some reason *BOTH* the bottom/top part have to be marked updated.
                        boolean bottom = data.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                        this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition));
                        this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, bottom ? blockposition.above() : blockposition.below()));
                    } else if (data.getBlock() instanceof TrapDoorBlock) {
                        this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    }
                } else if (!iblockdata.isAir()) {
                    iblockdata.attack(this.level, blockposition, this.player);
                    f = iblockdata.getDestroyProgress(this.player, this.player.level, blockposition);
                }

                if (event.useItemInHand() == Event.Result.DENY) {
                    // If we 'insta destroyed' then the client needs to be informed.
                    if (f > 1.0f) {
                        this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    }
                    return;
                }
                org.bukkit.event.block.BlockDamageEvent blockEvent = CraftEventFactory.callBlockDamageEvent(this.player, blockposition.getX(), blockposition.getY(), blockposition.getZ(), this.player.inventory.getSelected(), f >= 1.0f);

                if (blockEvent.isCancelled()) {
                    // Let the client know the block still exists
                    this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    return;
                }

                if (blockEvent.getInstaBreak()) {
                    f = 2.0f;
                }
                // CraftBukkit end

                if (!iblockdata.isAir() && f >= 1.0F) {
                    this.destroyAndAck(blockposition, packetplayinblockdig_enumplayerdigtype, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(this.destroyPos, this.level.getType(this.destroyPos), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, false, "abort destroying since another started (client insta mine, server disagreed)"));
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = blockposition.immutable();
                    int j = (int) (f * 10.0F);

                    this.level.destroyBlockProgress(this.player.getId(), blockposition, j);
                    this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, true, "actual start of destroying"));
                    this.lastSentState = j;
                }
            } else if (packetplayinblockdig_enumplayerdigtype == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (blockposition.equals(this.destroyPos)) {
                    int k = this.gameTicks - this.destroyProgressStart;

                    iblockdata = this.level.getType(blockposition);
                    if (!iblockdata.isAir()) {
                        float f1 = iblockdata.getDestroyProgress(this.player, this.player.level, blockposition) * (float) (k + 1);

                        if (f1 >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), blockposition, -1);
                            this.destroyAndAck(blockposition, packetplayinblockdig_enumplayerdigtype, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = blockposition;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, true, "stopped destroying"));
            } else if (packetplayinblockdig_enumplayerdigtype == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                if (!Objects.equals(this.destroyPos, blockposition)) {
                    ServerPlayerGameMode.LOGGER.debug("Mismatch in destroy block pos: " + this.destroyPos + " " + blockposition); // CraftBukkit - SPIGOT-5457 sent by client when interact event cancelled
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(this.destroyPos, this.level.getType(this.destroyPos), packetplayinblockdig_enumplayerdigtype, true, "aborted mismatched destroying"));
                }

                this.level.destroyBlockProgress(this.player.getId(), blockposition, -1);
                this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, true, "aborted destroying"));
            }

        }
    }

    public void destroyAndAck(BlockPos blockposition, ServerboundPlayerActionPacket.Action packetplayinblockdig_enumplayerdigtype, String s) {
        if (this.destroyBlock(blockposition)) {
            this.player.connection.sendPacket(new ClientboundBlockBreakAckPacket(blockposition, this.level.getType(blockposition), packetplayinblockdig_enumplayerdigtype, true, s));
        } else {
            this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition)); // CraftBukkit - SPIGOT-5196
        }

    }

    public boolean destroyBlock(BlockPos blockposition) {
        BlockState iblockdata = this.level.getType(blockposition);
        // CraftBukkit start - fire BlockBreakEvent
        org.bukkit.block.Block bblock = CraftBlock.at(level, blockposition);
        BlockBreakEvent event = null;

        if (this.player instanceof ServerPlayer) {
            // Sword + Creative mode pre-cancel
            boolean isSwordNoBreak = !this.player.getMainHandItem().getItem().canAttackBlock(iblockdata, this.level, blockposition, (Player) this.player);

            // Tell client the block is gone immediately then process events
            // Don't tell the client if its a creative sword break because its not broken!
            if (level.getBlockEntity(blockposition) == null && !isSwordNoBreak) {
                ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(this.level, blockposition);
                packet.blockState = Blocks.AIR.getBlockData();
                this.player.connection.sendPacket(packet);
            }

            event = new BlockBreakEvent(bblock, this.player.getBukkitEntity());

            // Sword + Creative mode pre-cancel
            event.setCancelled(isSwordNoBreak);

            // Calculate default block experience
            BlockState nmsData = this.level.getType(blockposition);
            Block nmsBlock = nmsData.getBlock();

            ItemStack itemstack = this.player.getItemBySlot(EquipmentSlot.MAINHAND);

            if (nmsBlock != null && !event.isCancelled() && !this.isCreative() && this.player.hasBlock(nmsBlock.getBlockData())) {
                event.setExpToDrop(nmsBlock.getExpDrop(nmsData, this.level, blockposition, itemstack));
            }

            this.level.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                if (isSwordNoBreak) {
                    return false;
                }
                // Let the client know the block still exists
                this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(this.level, blockposition));

                // Brute force all possible updates
                for (Direction dir : Direction.values()) {
                    this.player.connection.sendPacket(new ClientboundBlockUpdatePacket(level, blockposition.relative(dir)));
                }

                // Update any tile entity data for this block
                BlockEntity tileentity = this.level.getBlockEntity(blockposition);
                if (tileentity != null) {
                    this.player.connection.sendPacket(tileentity.getUpdatePacket());
                }
                return false;
            }
        }
        // CraftBukkit end

        if (false && !this.player.getMainHandItem().getItem().canAttackBlock(iblockdata, (Level) this.level, blockposition, (Player) this.player)) { // CraftBukkit - false
            return false;
        } else {
            iblockdata = this.level.getType(blockposition); // CraftBukkit - update state from plugins
            if (iblockdata.isAir()) return false; // CraftBukkit - A plugin set block to air without cancelling
            BlockEntity tileentity = this.level.getBlockEntity(blockposition);
            Block block = iblockdata.getBlock();

            if ((block instanceof CommandBlock || block instanceof StructureBlock || block instanceof JigsawBlock) && !this.player.canUseGameMasterBlocks()) {
                this.level.notify(blockposition, iblockdata, iblockdata, 3);
                return false;
            } else if (this.player.blockActionRestricted((Level) this.level, blockposition, this.gameModeForPlayer)) {
                return false;
            } else {
                // CraftBukkit start
                org.bukkit.block.BlockState state = bblock.getState();
                level.captureDrops = new ArrayList<>();
                // CraftBukkit end
                block.playerWillDestroy((Level) this.level, blockposition, iblockdata, (Player) this.player);
                boolean flag = this.level.removeBlock(blockposition, false);

                if (flag) {
                    block.postBreak(this.level, blockposition, iblockdata);
                }

                if (this.isCreative()) {
                    // return true; // CraftBukkit
                } else {
                    ItemStack itemstack = this.player.getMainHandItem();
                    ItemStack itemstack1 = itemstack.copy();
                    boolean flag1 = this.player.hasBlock(iblockdata);

                    itemstack.mineBlock(this.level, iblockdata, blockposition, this.player);
                    if (flag && flag1 && event.isDropItems()) { // CraftBukkit - Check if block should drop items
                        block.playerDestroy(this.level, this.player, blockposition, iblockdata, tileentity, itemstack1);
                    }

                    // return true; // CraftBukkit
                }
                // CraftBukkit start
                if (event.isDropItems()) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDropItemEvent(bblock, state, this.player, level.captureDrops);
                }
                level.captureDrops = null;

                // Drop event experience
                if (flag && event != null) {
                    iblockdata.getBlock().popExperience(this.level, blockposition, event.getExpToDrop());
                }

                return true;
                // CraftBukkit end
            }
        }
    }

    public InteractionResult useItem(ServerPlayer entityplayer, Level world, ItemStack itemstack, InteractionHand enumhand) {
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        } else if (entityplayer.getCooldowns().isOnCooldown(itemstack.getItem())) {
            return InteractionResult.PASS;
        } else {
            int i = itemstack.getCount();
            int j = itemstack.getDamageValue();
            InteractionResultHolder<ItemStack> interactionresultwrapper = itemstack.use(world, (Player) entityplayer, enumhand);
            ItemStack itemstack1 = (ItemStack) interactionresultwrapper.getObject();

            if (itemstack1 == itemstack && itemstack1.getCount() == i && itemstack1.getUseDuration() <= 0 && itemstack1.getDamageValue() == j) {
                return interactionresultwrapper.getResult();
            } else if (interactionresultwrapper.getResult() == InteractionResult.FAIL && itemstack1.getUseDuration() > 0 && !entityplayer.isUsingItem()) {
                return interactionresultwrapper.getResult();
            } else {
                entityplayer.setItemInHand(enumhand, itemstack1);
                if (this.isCreative()) {
                    itemstack1.setCount(i);
                    if (itemstack1.isDamageableItem() && itemstack1.getDamageValue() != j) {
                        itemstack1.setDamageValue(j);
                    }
                }

                if (itemstack1.isEmpty()) {
                    entityplayer.setItemInHand(enumhand, ItemStack.EMPTY);
                }

                if (!entityplayer.isUsingItem()) {
                    entityplayer.refreshContainer(entityplayer.inventoryMenu);
                }

                return interactionresultwrapper.getResult();
            }
        }
    }

    // CraftBukkit start - whole method
    public boolean interactResult = false;
    public boolean firedInteract = false;
    public InteractionResult useItemOn(ServerPlayer entityplayer, Level world, ItemStack itemstack, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        BlockPos blockposition = movingobjectpositionblock.getBlockPos();
        BlockState iblockdata = world.getType(blockposition);
        InteractionResult enuminteractionresult = InteractionResult.PASS;
        boolean cancelledBlock = false;

        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider itileinventory = iblockdata.getMenuProvider(world, blockposition);
            cancelledBlock = !(itileinventory instanceof MenuProvider);
        }

        if (entityplayer.getCooldowns().isOnCooldown(itemstack.getItem())) {
            cancelledBlock = true;
        }

        PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(entityplayer, Action.RIGHT_CLICK_BLOCK, blockposition, movingobjectpositionblock.getDirection(), itemstack, cancelledBlock, enumhand);
        firedInteract = true;
        interactResult = event.useItemInHand() == Event.Result.DENY;

        if (event.useInteractedBlock() == Event.Result.DENY) {
            // If we denied a door from opening, we need to send a correcting update to the client, as it already opened the door.
            if (iblockdata.getBlock() instanceof DoorBlock) {
                boolean bottom = iblockdata.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                entityplayer.connection.sendPacket(new ClientboundBlockUpdatePacket(world, bottom ? blockposition.above() : blockposition.below()));
            } else if (iblockdata.getBlock() instanceof CakeBlock) {
                entityplayer.getBukkitEntity().sendHealthUpdate(); // SPIGOT-1341 - reset health for cake
            }
            entityplayer.getBukkitEntity().updateInventory(); // SPIGOT-2867
            enuminteractionresult = (event.useItemInHand() != Event.Result.ALLOW) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider itileinventory = iblockdata.getMenuProvider(world, blockposition);

            if (itileinventory != null) {
                entityplayer.openMenu(itileinventory);
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            boolean flag = !entityplayer.getMainHandItem().isEmpty() || !entityplayer.getOffhandItem().isEmpty();
            boolean flag1 = entityplayer.isSecondaryUseActive() && flag;
            ItemStack itemstack1 = itemstack.copy();

            if (!flag1) {
                enuminteractionresult = iblockdata.use(world, entityplayer, enumhand, movingobjectpositionblock);

                if (enuminteractionresult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(entityplayer, blockposition, itemstack1);
                    return enuminteractionresult;
                }
            }

            if (!itemstack.isEmpty() && enuminteractionresult != InteractionResult.SUCCESS && !interactResult) { // add !interactResult SPIGOT-764
                UseOnContext itemactioncontext = new UseOnContext(entityplayer, enumhand, movingobjectpositionblock);
                InteractionResult enuminteractionresult1;

                if (this.isCreative()) {
                    int i = itemstack.getCount();

                    enuminteractionresult1 = itemstack.placeItem(itemactioncontext, enumhand);
                    itemstack.setCount(i);
                } else {
                    enuminteractionresult1 = itemstack.placeItem(itemactioncontext, enumhand);
                }

                if (enuminteractionresult1.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(entityplayer, blockposition, itemstack1);
                }

                return enuminteractionresult1;
            }
        }
        return enuminteractionresult;
        // CraftBukkit end
    }

    public void setLevel(ServerLevel worldserver) {
        this.level = worldserver;
    }
}
