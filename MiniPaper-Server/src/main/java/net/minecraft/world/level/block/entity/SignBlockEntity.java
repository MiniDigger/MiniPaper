package net.minecraft.world.level.block.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class SignBlockEntity extends BlockEntity implements CommandSource { // CraftBukkit - implements

    public final Component[] messages;
    public boolean isEditable;
    private Player playerWhoMayEdit;
    private final FormattedText[] renderMessages;
    private DyeColor color;

    public SignBlockEntity() {
        super(BlockEntityType.SIGN);
        this.messages = new Component[]{TextComponent.EMPTY, TextComponent.EMPTY, TextComponent.EMPTY, TextComponent.EMPTY};
        this.isEditable = true;
        this.renderMessages = new FormattedText[4];
        this.color = DyeColor.BLACK;
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);

        for (int i = 0; i < 4; ++i) {
            String s = Component.ChatSerializer.a(this.messages[i]);

            nbttagcompound.putString("Text" + (i + 1), s);
        }

        // CraftBukkit start
        if (Boolean.getBoolean("convertLegacySigns")) {
            nbttagcompound.putBoolean("Bukkit.isConverted", true);
        }
        // CraftBukkit end

        nbttagcompound.putString("Color", this.color.getName());
        return nbttagcompound;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        this.isEditable = false;
        super.load(iblockdata, nbttagcompound);
        this.color = DyeColor.byName(nbttagcompound.getString("Color"), DyeColor.BLACK);

        // CraftBukkit start - Add an option to convert signs correctly
        // This is done with a flag instead of all the time because
        // we have no way to tell whether a sign is from 1.7.10 or 1.8

        boolean oldSign = Boolean.getBoolean("convertLegacySigns") && !nbttagcompound.getBoolean("Bukkit.isConverted");

        for (int i = 0; i < 4; ++i) {
            String s = nbttagcompound.getString("Text" + (i + 1));
            if (s != null && s.length() > 2048) {
                s = "\"\"";
            }

            try {
                MutableComponent ichatmutablecomponent = Component.ChatSerializer.a(s.isEmpty() ? "\"\"" : s);

                if (oldSign) {
                    messages[i] = org.bukkit.craftbukkit.util.CraftChatMessage.fromString(s)[0];
                    continue;
                }
                // CraftBukkit end

                if (this.level instanceof ServerLevel) {
                    try {
                        this.messages[i] = ComponentUtils.updateForEntity(this.createCommandSourceStack((ServerPlayer) null), ichatmutablecomponent, (Entity) null, 0);
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        this.messages[i] = ichatmutablecomponent;
                    }
                } else {
                    this.messages[i] = ichatmutablecomponent;
                }
            } catch (com.google.gson.JsonParseException jsonparseexception) {
                this.messages[i] = new TextComponent(s);
            }

            this.renderMessages[i] = null;
        }

    }

    public void setMessage(int i, Component ichatbasecomponent) {
        this.messages[i] = ichatbasecomponent;
        this.renderMessages[i] = null;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 9, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public boolean isEditable() {
        return this.isEditable;
    }

    public void setAllowedPlayerEditor(Player entityhuman) {
        this.playerWhoMayEdit = entityhuman;
    }

    public Player getPlayerWhoMayEdit() {
        return this.playerWhoMayEdit;
    }

    public boolean executeClickCommands(Player entityhuman) {
        Component[] aichatbasecomponent = this.messages;
        int i = aichatbasecomponent.length;

        for (int j = 0; j < i; ++j) {
            Component ichatbasecomponent = aichatbasecomponent[j];
            Style chatmodifier = ichatbasecomponent == null ? null : ichatbasecomponent.getStyle();

            if (chatmodifier != null && chatmodifier.getClickEvent() != null) {
                ClickEvent chatclickable = chatmodifier.getClickEvent();

                if (chatclickable.getAction() == ClickEvent.Action.RUN_COMMAND) {
                    entityhuman.getServer().getCommands().performCommand(this.createCommandSourceStack((ServerPlayer) entityhuman), chatclickable.getValue());
                }
            }
        }

        return true;
    }

    // CraftBukkit start
    @Override
    public void sendMessage(Component ichatbasecomponent, UUID uuid) {}

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitSender(wrapper) : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, this);
    }

    @Override
    public boolean acceptsSuccess() {
        return false;
    }

    @Override
    public boolean acceptsFailure() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }
    // CraftBukkit end

    public CommandSourceStack createCommandSourceStack(@Nullable ServerPlayer entityplayer) {
        String s = entityplayer == null ? "Sign" : entityplayer.getName().getString();
        Object object = entityplayer == null ? new TextComponent("Sign") : entityplayer.getDisplayName();

        // CraftBukkit - this
        return new CommandSourceStack(this, Vec3.atCenterOf((Vec3i) this.worldPosition), Vec2.ZERO, (ServerLevel) this.level, 2, s, (Component) object, this.level.getServer(), entityplayer);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public boolean setColor(DyeColor enumcolor) {
        if (enumcolor != this.getColor()) {
            this.color = enumcolor;
            this.setChanged();
            if (this.level != null) this.level.notify(this.getBlockPos(), this.getBlock(), this.getBlock(), 3); // CraftBukkit - skip notify if world is null (SPIGOT-5122)
            return true;
        } else {
            return false;
        }
    }
}
