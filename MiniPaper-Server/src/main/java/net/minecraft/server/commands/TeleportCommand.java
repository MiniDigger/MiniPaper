package net.minecraft.server.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.entity.EntityTeleportEvent;
// CraftBukkit end

public class TeleportCommand {

    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(new TranslatableComponent("commands.teleport.invalidPosition"));

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = com_mojang_brigadier_commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("teleport").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((RequiredArgumentBuilder) Commands.argument("targets", (ArgumentType) EntityArgument.entities()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("location", (ArgumentType) Vec3Argument.vec3()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, (TeleportCommand.LookAt) null);
        })).then(Commands.argument("rotation", (ArgumentType) RotationArgument.rotation()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), RotationArgument.getRotation(commandcontext, "rotation"), (TeleportCommand.LookAt) null);
        }))).then(((LiteralArgumentBuilder) Commands.literal("facing").then(Commands.literal("entity").then(((RequiredArgumentBuilder) Commands.argument("facingEntity", (ArgumentType) EntityArgument.entity()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, new TeleportCommand.LookAt(EntityArgument.getEntity(commandcontext, "facingEntity"), EntityAnchorArgument.Anchor.FEET));
        })).then(Commands.argument("facingAnchor", (ArgumentType) EntityAnchorArgument.anchor()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, new TeleportCommand.LookAt(EntityArgument.getEntity(commandcontext, "facingEntity"), EntityAnchorArgument.getAnchor(commandcontext, "facingAnchor")));
        }))))).then(Commands.argument("facingLocation", (ArgumentType) Vec3Argument.vec3()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), (Coordinates) null, new TeleportCommand.LookAt(Vec3Argument.getVec3(commandcontext, "facingLocation")));
        }))))).then(Commands.argument("destination", (ArgumentType) EntityArgument.entity()).executes((commandcontext) -> {
            return teleportToEntity((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), EntityArgument.getEntity(commandcontext, "destination"));
        })))).then(Commands.argument("location", (ArgumentType) Vec3Argument.vec3()).executes((commandcontext) -> {
            return teleportToPos((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()), ((CommandSourceStack) commandcontext.getSource()).getLevel(), Vec3Argument.getCoordinates(commandcontext, "location"), WorldCoordinates.current(), (TeleportCommand.LookAt) null);
        }))).then(Commands.argument("destination", (ArgumentType) EntityArgument.entity()).executes((commandcontext) -> {
            return teleportToEntity((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()), EntityArgument.getEntity(commandcontext, "destination"));
        })));

        com_mojang_brigadier_commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("tp").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).redirect(literalcommandnode));
    }

    private static int teleportToEntity(CommandSourceStack commandlistenerwrapper, Collection<? extends Entity> collection, Entity entity) throws CommandSyntaxException {
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Entity entity1 = (Entity) iterator.next();

            performTeleport(commandlistenerwrapper, entity1, (ServerLevel) entity.level, entity.getX(), entity.getY(), entity.getZ(), EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class), entity.yRot, entity.xRot, (TeleportCommand.LookAt) null);
        }

        if (collection.size() == 1) {
            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.teleport.success.entity.single", new Object[]{((Entity) collection.iterator().next()).getDisplayName(), entity.getDisplayName()}), true);
        } else {
            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.teleport.success.entity.multiple", new Object[]{collection.size(), entity.getDisplayName()}), true);
        }

        return collection.size();
    }

    private static int teleportToPos(CommandSourceStack commandlistenerwrapper, Collection<? extends Entity> collection, ServerLevel worldserver, Coordinates ivectorposition, @Nullable Coordinates ivectorposition1, @Nullable TeleportCommand.LookAt commandteleport_a) throws CommandSyntaxException {
        Vec3 vec3d = ivectorposition.getPosition(commandlistenerwrapper);
        Vec2 vec2f = ivectorposition1 == null ? null : ivectorposition1.getRotation(commandlistenerwrapper);
        Set<ClientboundPlayerPositionPacket.RelativeArgument> set = EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class);

        if (ivectorposition.isXRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.X);
        }

        if (ivectorposition.isYRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y);
        }

        if (ivectorposition.isZRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Z);
        }

        if (ivectorposition1 == null) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT);
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT);
        } else {
            if (ivectorposition1.isXRelative()) {
                set.add(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT);
            }

            if (ivectorposition1.isYRelative()) {
                set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT);
            }
        }

        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (ivectorposition1 == null) {
                performTeleport(commandlistenerwrapper, entity, worldserver, vec3d.x, vec3d.y, vec3d.z, set, entity.yRot, entity.xRot, commandteleport_a);
            } else {
                performTeleport(commandlistenerwrapper, entity, worldserver, vec3d.x, vec3d.y, vec3d.z, set, vec2f.y, vec2f.x, commandteleport_a);
            }
        }

        if (collection.size() == 1) {
            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.teleport.success.location.single", new Object[]{((Entity) collection.iterator().next()).getDisplayName(), vec3d.x, vec3d.y, vec3d.z}), true);
        } else {
            commandlistenerwrapper.sendSuccess(new TranslatableComponent("commands.teleport.success.location.multiple", new Object[]{collection.size(), vec3d.x, vec3d.y, vec3d.z}), true);
        }

        return collection.size();
    }

    private static void performTeleport(CommandSourceStack commandlistenerwrapper, Entity entity, ServerLevel worldserver, double d0, double d1, double d2, Set<ClientboundPlayerPositionPacket.RelativeArgument> set, float f, float f1, @Nullable TeleportCommand.LookAt commandteleport_a) throws CommandSyntaxException {
        BlockPos blockposition = new BlockPos(d0, d1, d2);

        if (!Level.isInSpawnableBounds(blockposition)) {
            throw TeleportCommand.INVALID_POSITION.create();
        } else {
            if (entity instanceof ServerPlayer) {
                ChunkPos chunkcoordintpair = new ChunkPos(new BlockPos(d0, d1, d2));

                worldserver.getChunkSourceOH().addRegionTicket(TicketType.POST_TELEPORT, chunkcoordintpair, 1, entity.getId());
                entity.stopRiding();
                if (((ServerPlayer) entity).isSleeping()) {
                    ((ServerPlayer) entity).stopSleepInBed(true, true);
                }

                if (worldserver == entity.level) {
                    ((ServerPlayer) entity).connection.a(d0, d1, d2, f, f1, set, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND); // CraftBukkit
                } else {
                    ((ServerPlayer) entity).a(worldserver, d0, d1, d2, f, f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND); // CraftBukkit
                }

                entity.setYHeadRot(f);
            } else {
                float f2 = Mth.wrapDegrees(f);
                float f3 = Mth.wrapDegrees(f1);

                f3 = Mth.clamp(f3, -90.0F, 90.0F);
                // CraftBukkit start - Teleport event
                Location to = new Location(worldserver.getWorld(), d0, d1, d2, f2, f3);
                EntityTeleportEvent event = new EntityTeleportEvent(entity.getBukkitEntity(), entity.getBukkitEntity().getLocation(), to);
                worldserver.getServerOH().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }

                d0 = to.getX();
                d1 = to.getY();
                d2 = to.getZ();
                f2 = to.getYaw();
                f3 = to.getPitch();
                worldserver = ((CraftWorld) to.getWorld()).getHandle();
                // CraftBukkit end
                if (worldserver == entity.level) {
                    entity.moveTo(d0, d1, d2, f2, f3);
                    entity.setYHeadRot(f2);
                } else {
                    entity.unRide();
                    Entity entity1 = entity;

                    entity = entity.getType().create((Level) worldserver);
                    if (entity == null) {
                        return;
                    }

                    entity.restoreFrom(entity1);
                    entity.moveTo(d0, d1, d2, f2, f3);
                    entity.setYHeadRot(f2);
                    worldserver.addFromAnotherDimension(entity);
                    entity1.removed = true;
                }
            }

            if (commandteleport_a != null) {
                commandteleport_a.perform(commandlistenerwrapper, entity);
            }

            if (!(entity instanceof LivingEntity) || !((LivingEntity) entity).isFallFlying()) {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                entity.setOnGround(true);
            }

            if (entity instanceof PathfinderMob) {
                ((PathfinderMob) entity).getNavigation().stop();
            }

        }
    }

    static class LookAt {

        private final Vec3 position;
        private final Entity entity;
        private final EntityAnchorArgument.Anchor anchor;

        public LookAt(Entity entity, EntityAnchorArgument.Anchor argumentanchor_anchor) {
            this.entity = entity;
            this.anchor = argumentanchor_anchor;
            this.position = argumentanchor_anchor.apply(entity);
        }

        public LookAt(Vec3 vec3d) {
            this.entity = null;
            this.position = vec3d;
            this.anchor = null;
        }

        public void perform(CommandSourceStack commandlistenerwrapper, Entity entity) {
            if (this.entity != null) {
                if (entity instanceof ServerPlayer) {
                    ((ServerPlayer) entity).lookAt(commandlistenerwrapper.getAnchor(), this.entity, this.anchor);
                } else {
                    entity.lookAt(commandlistenerwrapper.getAnchor(), this.position);
                }
            } else {
                entity.lookAt(commandlistenerwrapper.getAnchor(), this.position);
            }

        }
    }
}
