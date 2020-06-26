package net.minecraft.world.entity.vehicle;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MinecartCommandBlock extends AbstractMinecart {

    public static final EntityDataAccessor<String> DATA_ID_COMMAND_NAME = SynchedEntityData.defineId(MinecartCommandBlock.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Component> DATA_ID_LAST_OUTPUT = SynchedEntityData.defineId(MinecartCommandBlock.class, EntityDataSerializers.COMPONENT);
    private final BaseCommandBlock commandBlock = new MinecartCommandBlock.MinecartCommandBase();
    private int lastActivated;

    public MinecartCommandBlock(EntityType<? extends MinecartCommandBlock> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public MinecartCommandBlock(Level world, double d0, double d1, double d2) {
        super(EntityType.COMMAND_BLOCK_MINECART, world, d0, d1, d2);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().register(MinecartCommandBlock.DATA_ID_COMMAND_NAME, "");
        this.getEntityData().register(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, TextComponent.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.commandBlock.load(nbttagcompound);
        this.getEntityData().set(MinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommandBlock().getCommand());
        this.getEntityData().set(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getCommandBlock().getLastOutput());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        this.commandBlock.save(nbttagcompound);
    }

    @Override
    public AbstractMinecart.Type getMinecartType() {
        return AbstractMinecart.Type.COMMAND_BLOCK;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.COMMAND_BLOCK.getBlockData();
    }

    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    @Override
    public void activateMinecart(int i, int j, int k, boolean flag) {
        if (flag && this.tickCount - this.lastActivated >= 4) {
            this.getCommandBlock().performCommand(this.level);
            this.lastActivated = this.tickCount;
        }

    }

    @Override
    public InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        return this.commandBlock.usedBy(entityhuman);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (MinecartCommandBlock.DATA_ID_LAST_OUTPUT.equals(datawatcherobject)) {
            try {
                this.commandBlock.setLastOutput((Component) this.getEntityData().get(MinecartCommandBlock.DATA_ID_LAST_OUTPUT));
            } catch (Throwable throwable) {
                ;
            }
        } else if (MinecartCommandBlock.DATA_ID_COMMAND_NAME.equals(datawatcherobject)) {
            this.commandBlock.setCommand((String) this.getEntityData().get(MinecartCommandBlock.DATA_ID_COMMAND_NAME));
        }

    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public class MinecartCommandBase extends BaseCommandBlock {

        public MinecartCommandBase() {}

        @Override
        public ServerLevel getLevel() {
            return (ServerLevel) MinecartCommandBlock.this.level;
        }

        @Override
        public void onUpdated() {
            MinecartCommandBlock.this.getEntityData().set(MinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommand());
            MinecartCommandBlock.this.getEntityData().set(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getLastOutput());
        }

        @Override
        public CommandSourceStack createCommandSourceStack() {
            return new CommandSourceStack(this, MinecartCommandBlock.this.position(), MinecartCommandBlock.this.getRotationVector(), this.getLevel(), 2, this.getName().getString(), MinecartCommandBlock.this.getDisplayName(), this.getLevel().getServer(), MinecartCommandBlock.this);
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return (org.bukkit.craftbukkit.entity.CraftMinecartCommand) MinecartCommandBlock.this.getBukkitEntity();
        }
        // CraftBukkit end
    }
}
