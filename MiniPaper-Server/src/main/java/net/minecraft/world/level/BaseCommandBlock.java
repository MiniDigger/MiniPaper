package net.minecraft.world.level;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.bukkit.command.CommandSender;

public abstract class BaseCommandBlock implements CommandSource {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final Component DEFAULT_NAME = new TextComponent("@");
    private long lastExecution = -1L;
    private boolean updateLastExecution = true;
    private int successCount;
    private boolean trackOutput = true;
    @Nullable
    private Component lastOutput;
    private String command = "";
    private Component name;
    // CraftBukkit start
    @Override
    public abstract CommandSender getBukkitSender(CommandSourceStack wrapper);
    // CraftBukkit end

    public BaseCommandBlock() {
        this.name = BaseCommandBlock.DEFAULT_NAME;
    }

    public int getSuccessCount() {
        return this.successCount;
    }

    public void setSuccessCount(int i) {
        this.successCount = i;
    }

    public Component getLastOutput() {
        return this.lastOutput == null ? TextComponent.EMPTY : this.lastOutput;
    }

    public CompoundTag save(CompoundTag nbttagcompound) {
        nbttagcompound.putString("Command", this.command);
        nbttagcompound.putInt("SuccessCount", this.successCount);
        nbttagcompound.putString("CustomName", Component.ChatSerializer.a(this.name));
        nbttagcompound.putBoolean("TrackOutput", this.trackOutput);
        if (this.lastOutput != null && this.trackOutput) {
            nbttagcompound.putString("LastOutput", Component.ChatSerializer.a(this.lastOutput));
        }

        nbttagcompound.putBoolean("UpdateLastExecution", this.updateLastExecution);
        if (this.updateLastExecution && this.lastExecution > 0L) {
            nbttagcompound.putLong("LastExecution", this.lastExecution);
        }

        return nbttagcompound;
    }

    public void load(CompoundTag nbttagcompound) {
        this.command = nbttagcompound.getString("Command");
        this.successCount = nbttagcompound.getInt("SuccessCount");
        if (nbttagcompound.contains("CustomName", 8)) {
            this.setName(Component.ChatSerializer.a(nbttagcompound.getString("CustomName")));
        }

        if (nbttagcompound.contains("TrackOutput", 1)) {
            this.trackOutput = nbttagcompound.getBoolean("TrackOutput");
        }

        if (nbttagcompound.contains("LastOutput", 8) && this.trackOutput) {
            try {
                this.lastOutput = Component.ChatSerializer.a(nbttagcompound.getString("LastOutput"));
            } catch (Throwable throwable) {
                this.lastOutput = new TextComponent(throwable.getMessage());
            }
        } else {
            this.lastOutput = null;
        }

        if (nbttagcompound.contains("UpdateLastExecution")) {
            this.updateLastExecution = nbttagcompound.getBoolean("UpdateLastExecution");
        }

        if (this.updateLastExecution && nbttagcompound.contains("LastExecution")) {
            this.lastExecution = nbttagcompound.getLong("LastExecution");
        } else {
            this.lastExecution = -1L;
        }

    }

    public void setCommand(String s) {
        this.command = s;
        this.successCount = 0;
    }

    public String getCommand() {
        return this.command;
    }

    public boolean performCommand(Level world) {
        if (!world.isClientSide && world.getGameTime() != this.lastExecution) {
            if ("Searge".equalsIgnoreCase(this.command)) {
                this.lastOutput = new TextComponent("#itzlipofutzli");
                this.successCount = 1;
                return true;
            } else {
                this.successCount = 0;
                MinecraftServer minecraftserver = this.getLevel().getServer();

                if (minecraftserver.isCommandBlockEnabled() && !StringUtil.isNullOrEmpty(this.command)) {
                    try {
                        this.lastOutput = null;
                        CommandSourceStack commandlistenerwrapper = this.createCommandSourceStack().withCallback((commandcontext, flag, i) -> {
                            if (flag) {
                                ++this.successCount;
                            }

                        });

                        minecraftserver.getCommands().dispatchServerCommand(commandlistenerwrapper, this.command); // CraftBukkit
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Executing command block");
                        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Command to be executed");

                        crashreportsystemdetails.setDetail("Command", this::getCommand);
                        crashreportsystemdetails.setDetail("Name", () -> {
                            return this.getName().getString();
                        });
                        throw new ReportedException(crashreport);
                    }
                }

                if (this.updateLastExecution) {
                    this.lastExecution = world.getGameTime();
                } else {
                    this.lastExecution = -1L;
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public Component getName() {
        return this.name;
    }

    public void setName(@Nullable Component ichatbasecomponent) {
        if (ichatbasecomponent != null) {
            this.name = ichatbasecomponent;
        } else {
            this.name = BaseCommandBlock.DEFAULT_NAME;
        }

    }

    @Override
    public void sendMessage(Component ichatbasecomponent, UUID uuid) {
        if (this.trackOutput) {
            this.lastOutput = (new TextComponent("[" + BaseCommandBlock.TIME_FORMAT.format(new Date()) + "] ")).append(ichatbasecomponent);
            this.onUpdated();
        }

    }

    public abstract ServerLevel getLevel();

    public abstract void onUpdated();

    public void setLastOutput(@Nullable Component ichatbasecomponent) {
        this.lastOutput = ichatbasecomponent;
    }

    public void setTrackOutput(boolean flag) {
        this.trackOutput = flag;
    }

    public InteractionResult usedBy(Player entityhuman) {
        if (!entityhuman.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        } else {
            if (entityhuman.getCommandSenderWorld().isClientSide) {
                entityhuman.openMinecartCommandBlock(this);
            }

            return InteractionResult.sidedSuccess(entityhuman.level.isClientSide);
        }
    }

    public abstract CommandSourceStack createCommandSourceStack();

    @Override
    public boolean acceptsSuccess() {
        return this.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK) && this.trackOutput;
    }

    @Override
    public boolean acceptsFailure() {
        return this.trackOutput;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getLevel().getGameRules().getBoolean(GameRules.RULE_COMMANDBLOCKOUTPUT);
    }
}
