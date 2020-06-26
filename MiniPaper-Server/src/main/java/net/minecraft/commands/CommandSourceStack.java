package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandSourceStack implements SharedSuggestionProvider {

    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(new TranslatableComponent("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(new TranslatableComponent("permissions.requires.entity"));
    public final CommandSource source;
    private final Vec3 worldPosition;
    private final ServerLevel level;
    private final int permissionLevel;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final boolean silent;
    @Nullable
    private final Entity entity;
    private final ResultConsumer<CommandSourceStack> consumer;
    private final EntityAnchorArgument.Anchor anchor;
    private final Vec2 rotation;
    public CommandNode currentCommand; // CraftBukkit

    public CommandSourceStack(CommandSource icommandlistener, Vec3 vec3d, Vec2 vec2f, ServerLevel worldserver, int i, String s, Component ichatbasecomponent, MinecraftServer minecraftserver, @Nullable Entity entity) {
        this(icommandlistener, vec3d, vec2f, worldserver, i, s, ichatbasecomponent, minecraftserver, entity, false, (commandcontext, flag, j) -> {
        }, EntityAnchorArgument.Anchor.FEET);
    }

    protected CommandSourceStack(CommandSource icommandlistener, Vec3 vec3d, Vec2 vec2f, ServerLevel worldserver, int i, String s, Component ichatbasecomponent, MinecraftServer minecraftserver, @Nullable Entity entity, boolean flag, ResultConsumer<CommandSourceStack> resultconsumer, EntityAnchorArgument.Anchor argumentanchor_anchor) {
        this.source = icommandlistener;
        this.worldPosition = vec3d;
        this.level = worldserver;
        this.silent = flag;
        this.entity = entity;
        this.permissionLevel = i;
        this.textName = s;
        this.displayName = ichatbasecomponent;
        this.server = minecraftserver;
        this.consumer = resultconsumer;
        this.anchor = argumentanchor_anchor;
        this.rotation = vec2f;
    }

    public CommandSourceStack withEntity(Entity entity) {
        return this.entity == entity ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, entity.getName().getString(), entity.getDisplayName(), this.server, entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withPosition(Vec3 vec3d) {
        return this.worldPosition.equals(vec3d) ? this : new CommandSourceStack(this.source, vec3d, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withRotation(Vec2 vec2f) {
        return this.rotation.equals(vec2f) ? this : new CommandSourceStack(this.source, this.worldPosition, vec2f, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> resultconsumer) {
        return this.consumer.equals(resultconsumer) ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, resultconsumer, this.anchor);
    }

    public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> resultconsumer, BinaryOperator<ResultConsumer<CommandSourceStack>> binaryoperator) {
        ResultConsumer<CommandSourceStack> resultconsumer1 = (ResultConsumer) binaryoperator.apply(this.consumer, resultconsumer);

        return this.withCallback(resultconsumer1);
    }

    public CommandSourceStack withSuppressedOutput() {
        return this.silent ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, true, this.consumer, this.anchor);
    }

    public CommandSourceStack withPermission(int i) {
        return i == this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, i, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withMaximumPermission(int i) {
        return i <= this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, i, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withAnchor(EntityAnchorArgument.Anchor argumentanchor_anchor) {
        return argumentanchor_anchor == this.anchor ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, argumentanchor_anchor);
    }

    public CommandSourceStack withLevel(ServerLevel worldserver) {
        return worldserver == this.level ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, worldserver, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack facing(Entity entity, EntityAnchorArgument.Anchor argumentanchor_anchor) throws CommandSyntaxException {
        return this.facing(argumentanchor_anchor.apply(entity));
    }

    public CommandSourceStack facing(Vec3 vec3d) throws CommandSyntaxException {
        Vec3 vec3d1 = this.anchor.apply(this);
        double d0 = vec3d.x - vec3d1.x;
        double d1 = vec3d.y - vec3d1.y;
        double d2 = vec3d.z - vec3d1.z;
        double d3 = (double) Mth.sqrt(d0 * d0 + d2 * d2);
        float f = Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 57.2957763671875D)));
        float f1 = Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F);

        return this.withRotation(new Vec2(f, f1));
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTextName() {
        return this.textName;
    }

    @Override
    public boolean hasPermission(int i) {
        // CraftBukkit start
        if (currentCommand != null) {
            return hasPermission(i, org.bukkit.craftbukkit.command.VanillaCommandWrapper.getPermission(currentCommand));
        }
        // CraftBukkit end

        return this.permissionLevel >= i;
    }

    // CraftBukkit start
    public boolean hasPermission(int i, String bukkitPermission) {
        // World is null when loading functions
        return ((getLevel() == null || !getLevel().getServerOH().ignoreVanillaPermissions) && this.permissionLevel >= i) || getBukkitSender().hasPermission(bukkitPermission);
    }
    // CraftBukkit end

    public Vec3 getPosition() {
        return this.worldPosition;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (this.entity == null) {
            throw CommandSourceStack.ERROR_NOT_ENTITY.create();
        } else {
            return this.entity;
        }
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        if (!(this.entity instanceof ServerPlayer)) {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        } else {
            return (ServerPlayer) this.entity;
        }
    }

    public Vec2 getRotation() {
        return this.rotation;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public EntityAnchorArgument.Anchor getAnchor() {
        return this.anchor;
    }

    public void sendSuccess(Component ichatbasecomponent, boolean flag) {
        if (this.source.acceptsSuccess() && !this.silent) {
            this.source.sendMessage(ichatbasecomponent, Util.NIL_UUID);
        }

        if (flag && this.source.shouldInformAdmins() && !this.silent) {
            this.broadcastToAdmins(ichatbasecomponent);
        }

    }

    private void broadcastToAdmins(Component ichatbasecomponent) {
        MutableComponent ichatmutablecomponent = (new TranslatableComponent("chat.type.admin", new Object[]{this.getDisplayName(), ichatbasecomponent})).withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC});

        if (this.server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
            Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (entityplayer != this.source && entityplayer.getBukkitEntity().hasPermission("minecraft.admin.command_feedback")) { // CraftBukkit
                    entityplayer.sendMessage(ichatmutablecomponent, Util.NIL_UUID);
                }
            }
        }

        if (this.source != this.server && this.server.getGameRules().getBoolean(GameRules.RULE_LOGADMINCOMMANDS) && !org.spigotmc.SpigotConfig.silentCommandBlocks) { // Spigot
            this.server.sendMessage(ichatmutablecomponent, Util.NIL_UUID);
        }

    }

    public void sendFailure(Component ichatbasecomponent) {
        if (this.source.acceptsFailure() && !this.silent) {
            this.source.sendMessage((new TextComponent("")).append(ichatbasecomponent).withStyle(ChatFormatting.RED), Util.NIL_UUID);
        }

    }

    public void onCommandComplete(CommandContext<CommandSourceStack> commandcontext, boolean flag, int i) {
        if (this.consumer != null) {
            this.consumer.onCommandComplete(commandcontext, flag, i);
        }

    }

    @Override
    public Collection<String> getOnlinePlayerNames() {
        return Lists.newArrayList(this.server.getPlayerNames());
    }

    @Override
    public Collection<String> getAllTeams() {
        return this.server.getScoreboard().getTeamNames();
    }

    @Override
    public Collection<ResourceLocation> getAvailableSoundEvents() {
        return Registry.SOUND_EVENT.keySet();
    }

    @Override
    public Stream<ResourceLocation> getRecipeNames() {
        return this.server.getRecipeManager().getRecipeIds();
    }

    @Override
    public CompletableFuture<Suggestions> customSuggestion(CommandContext<SharedSuggestionProvider> commandcontext, SuggestionsBuilder suggestionsbuilder) {
        return null;
    }

    @Override
    public Set<ResourceKey<Level>> levels() {
        return this.server.levelKeys();
    }

    // CraftBukkit start
    public org.bukkit.command.CommandSender getBukkitSender() {
        return source.getBukkitSender(this);
    }
    // CraftBukkit end
}
