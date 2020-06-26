package net.minecraft.commands.arguments.selector;

import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntitySelector {

    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final Predicate<Entity> predicate;
    private final MinMaxBounds.FloatRange range;
    private final Function<Vec3, Vec3> position;
    @Nullable
    private final AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    private final boolean currentEntity;
    @Nullable
    private final String playerName;
    @Nullable
    private final UUID entityUUID;
    @Nullable
    private final EntityType<?> type;
    private final boolean usesSelector;

    public EntitySelector(int i, boolean flag, boolean flag1, Predicate<Entity> predicate, MinMaxBounds.FloatRange criterionconditionvalue_floatrange, Function<Vec3, Vec3> function, @Nullable AABB axisalignedbb, BiConsumer<Vec3, List<? extends Entity>> biconsumer, boolean flag2, @Nullable String s, @Nullable UUID uuid, @Nullable EntityType<?> entitytypes, boolean flag3) {
        this.maxResults = i;
        this.includesEntities = flag;
        this.worldLimited = flag1;
        this.predicate = predicate;
        this.range = criterionconditionvalue_floatrange;
        this.position = function;
        this.aabb = axisalignedbb;
        this.order = biconsumer;
        this.currentEntity = flag2;
        this.playerName = s;
        this.entityUUID = uuid;
        this.type = entitytypes;
        this.usesSelector = flag3;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    private void checkPermissions(CommandSourceStack commandlistenerwrapper) throws CommandSyntaxException {
        if (this.usesSelector && !commandlistenerwrapper.hasPermission(2, "minecraft.command.selector")) { // CraftBukkit
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack commandlistenerwrapper) throws CommandSyntaxException {
        this.checkPermissions(commandlistenerwrapper);
        List<? extends Entity> list = this.findEntities(commandlistenerwrapper);

        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return (Entity) list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack commandlistenerwrapper) throws CommandSyntaxException {
        this.checkPermissions(commandlistenerwrapper);
        if (!this.includesEntities) {
            return this.findPlayers(commandlistenerwrapper);
        } else if (this.playerName != null) {
            ServerPlayer entityplayer = commandlistenerwrapper.getServer().getPlayerList().getPlayerByName(this.playerName);

            return (List) (entityplayer == null ? Collections.emptyList() : Lists.newArrayList(new ServerPlayer[]{entityplayer}));
        } else if (this.entityUUID != null) {
            Iterator iterator = commandlistenerwrapper.getServer().getAllLevels().iterator();

            Entity entity;

            do {
                if (!iterator.hasNext()) {
                    return Collections.emptyList();
                }

                ServerLevel worldserver = (ServerLevel) iterator.next();

                entity = worldserver.getEntity(this.entityUUID);
            } while (entity == null);

            return Lists.newArrayList(new Entity[]{entity});
        } else {
            Vec3 vec3d = (Vec3) this.position.apply(commandlistenerwrapper.getPosition());
            Predicate<Entity> predicate = this.getPredicate(vec3d);

            if (this.currentEntity) {
                return (List) (commandlistenerwrapper.getEntity() != null && predicate.test(commandlistenerwrapper.getEntity()) ? Lists.newArrayList(new Entity[]{commandlistenerwrapper.getEntity()}) : Collections.emptyList());
            } else {
                List<Entity> list = Lists.newArrayList();

                if (this.isWorldLimited()) {
                    this.addEntities(list, commandlistenerwrapper.getLevel(), vec3d, predicate);
                } else {
                    Iterator iterator1 = commandlistenerwrapper.getServer().getAllLevels().iterator();

                    while (iterator1.hasNext()) {
                        ServerLevel worldserver1 = (ServerLevel) iterator1.next();

                        this.addEntities(list, worldserver1, vec3d, predicate);
                    }
                }

                return this.sortAndLimit(vec3d, (List) list);
            }
        }
    }

    private void addEntities(List<Entity> list, ServerLevel worldserver, Vec3 vec3d, Predicate<Entity> predicate) {
        if (this.aabb != null) {
            list.addAll(worldserver.getEntities(this.type, this.aabb.move(vec3d), predicate));
        } else {
            list.addAll(worldserver.getEntities(this.type, predicate));
        }

    }

    public ServerPlayer findSinglePlayer(CommandSourceStack commandlistenerwrapper) throws CommandSyntaxException {
        this.checkPermissions(commandlistenerwrapper);
        List<ServerPlayer> list = this.findPlayers(commandlistenerwrapper);

        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return (ServerPlayer) list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack commandlistenerwrapper) throws CommandSyntaxException {
        this.checkPermissions(commandlistenerwrapper);
        ServerPlayer entityplayer;

        if (this.playerName != null) {
            entityplayer = commandlistenerwrapper.getServer().getPlayerList().getPlayerByName(this.playerName);
            return (List) (entityplayer == null ? Collections.emptyList() : Lists.newArrayList(new ServerPlayer[]{entityplayer}));
        } else if (this.entityUUID != null) {
            entityplayer = commandlistenerwrapper.getServer().getPlayerList().getPlayer(this.entityUUID);
            return (List) (entityplayer == null ? Collections.emptyList() : Lists.newArrayList(new ServerPlayer[]{entityplayer}));
        } else {
            Vec3 vec3d = (Vec3) this.position.apply(commandlistenerwrapper.getPosition());
            Predicate<Entity> predicate = this.getPredicate(vec3d);

            if (this.currentEntity) {
                if (commandlistenerwrapper.getEntity() instanceof ServerPlayer) {
                    ServerPlayer entityplayer1 = (ServerPlayer) commandlistenerwrapper.getEntity();

                    if (predicate.test(entityplayer1)) {
                        return Lists.newArrayList(new ServerPlayer[]{entityplayer1});
                    }
                }

                return Collections.emptyList();
            } else {
                Object object;

                if (this.isWorldLimited()) {
                    ServerLevel worldserver = commandlistenerwrapper.getLevel();

                    predicate.getClass();
                    object = worldserver.getPlayers(predicate::test);
                } else {
                    object = Lists.newArrayList();
                    Iterator iterator = commandlistenerwrapper.getServer().getPlayerList().getPlayers().iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer2 = (ServerPlayer) iterator.next();

                        if (predicate.test(entityplayer2)) {
                            ((List) object).add(entityplayer2);
                        }
                    }
                }

                return this.sortAndLimit(vec3d, (List) object);
            }
        }
    }

    private Predicate<Entity> getPredicate(Vec3 vec3d) {
        Predicate<Entity> predicate = this.predicate;

        if (this.aabb != null) {
            AABB axisalignedbb = this.aabb.move(vec3d);

            predicate = predicate.and((entity) -> {
                return axisalignedbb.intersects(entity.getBoundingBox());
            });
        }

        if (!this.range.isAny()) {
            predicate = predicate.and((entity) -> {
                return this.range.a(entity.distanceToSqr(vec3d));
            });
        }

        return predicate;
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 vec3d, List<T> list) {
        if (list.size() > 1) {
            this.order.accept(vec3d, list);
        }

        return list.subList(0, Math.min(this.maxResults, list.size()));
    }

    public static MutableComponent joinNames(List<? extends Entity> list) {
        return ComponentUtils.formatList(list, Entity::getDisplayName);
    }
}
