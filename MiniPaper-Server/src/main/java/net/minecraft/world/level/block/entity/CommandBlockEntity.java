package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandBlockEntity extends BlockEntity {

    private boolean powered;
    private boolean auto;
    private boolean conditionMet;
    private boolean sendToClient;
    private final BaseCommandBlock commandBlock = new BaseCommandBlock() {
        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, CommandBlockEntity.this);
        }
        // CraftBukkit end

        @Override
        public void setCommand(String s) {
            super.setCommand(s);
            CommandBlockEntity.this.setChanged();
        }

        @Override
        public ServerLevel getLevel() {
            return (ServerLevel) CommandBlockEntity.this.level;
        }

        @Override
        public void onUpdated() {
            BlockState iblockdata = CommandBlockEntity.this.level.getType(CommandBlockEntity.this.worldPosition);

            this.getLevel().notify(CommandBlockEntity.this.worldPosition, iblockdata, iblockdata, 3);
        }

        @Override
        public CommandSourceStack createCommandSourceStack() {
            return new CommandSourceStack(this, Vec3.atCenterOf((Vec3i) CommandBlockEntity.this.worldPosition), Vec2.ZERO, this.getLevel(), 2, this.getName().getString(), this.getName(), this.getLevel().getServer(), (Entity) null);
        }
    };

    public CommandBlockEntity() {
        super(BlockEntityType.COMMAND_BLOCK);
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        this.commandBlock.save(nbttagcompound);
        nbttagcompound.putBoolean("powered", this.isPowered());
        nbttagcompound.putBoolean("conditionMet", this.wasConditionMet());
        nbttagcompound.putBoolean("auto", this.isAutomatic());
        return nbttagcompound;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.commandBlock.load(nbttagcompound);
        this.powered = nbttagcompound.getBoolean("powered");
        this.conditionMet = nbttagcompound.getBoolean("conditionMet");
        this.setAutomatic(nbttagcompound.getBoolean("auto"));
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        if (this.isSendToClient()) {
            this.setSendToClient(false);
            CompoundTag nbttagcompound = this.save(new CompoundTag());

            return new ClientboundBlockEntityDataPacket(this.worldPosition, 2, nbttagcompound);
        } else {
            return null;
        }
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    public void setPowered(boolean flag) {
        this.powered = flag;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isAutomatic() {
        return this.auto;
    }

    public void setAutomatic(boolean flag) {
        boolean flag1 = this.auto;

        this.auto = flag;
        if (!flag1 && flag && !this.powered && this.level != null && this.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
            this.scheduleTick();
        }

    }

    public void onModeSwitch() {
        CommandBlockEntity.Mode tileentitycommand_type = this.getMode();

        if (tileentitycommand_type == CommandBlockEntity.Mode.AUTO && (this.powered || this.auto) && this.level != null) {
            this.scheduleTick();
        }

    }

    private void scheduleTick() {
        Block block = this.getBlock().getBlock();

        if (block instanceof CommandBlock) {
            this.markConditionMet();
            this.level.getBlockTickList().scheduleTick(this.worldPosition, block, 1);
        }

    }

    public boolean wasConditionMet() {
        return this.conditionMet;
    }

    public boolean markConditionMet() {
        this.conditionMet = true;
        if (this.isConditional()) {
            BlockPos blockposition = this.worldPosition.relative(((Direction) this.level.getType(this.worldPosition).getValue(CommandBlock.FACING)).getOpposite());

            if (this.level.getType(blockposition).getBlock() instanceof CommandBlock) {
                BlockEntity tileentity = this.level.getBlockEntity(blockposition);

                this.conditionMet = tileentity instanceof CommandBlockEntity && ((CommandBlockEntity) tileentity).getCommandBlock().getSuccessCount() > 0;
            } else {
                this.conditionMet = false;
            }
        }

        return this.conditionMet;
    }

    public boolean isSendToClient() {
        return this.sendToClient;
    }

    public void setSendToClient(boolean flag) {
        this.sendToClient = flag;
    }

    public CommandBlockEntity.Mode getMode() {
        BlockState iblockdata = this.getBlock();

        return iblockdata.is(Blocks.COMMAND_BLOCK) ? CommandBlockEntity.Mode.REDSTONE : (iblockdata.is(Blocks.REPEATING_COMMAND_BLOCK) ? CommandBlockEntity.Mode.AUTO : (iblockdata.is(Blocks.CHAIN_COMMAND_BLOCK) ? CommandBlockEntity.Mode.SEQUENCE : CommandBlockEntity.Mode.REDSTONE));
    }

    public boolean isConditional() {
        BlockState iblockdata = this.level.getType(this.getBlockPos());

        return iblockdata.getBlock() instanceof CommandBlock ? (Boolean) iblockdata.getValue(CommandBlock.CONDITIONAL) : false;
    }

    @Override
    public void clearRemoved() {
        this.clearCache();
        super.clearRemoved();
    }

    public static enum Mode {

        SEQUENCE, AUTO, REDSTONE;

        private Mode() {}
    }
}
