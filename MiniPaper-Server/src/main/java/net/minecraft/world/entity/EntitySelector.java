package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;

public final class EntitySelector {

    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    public static final Predicate<LivingEntity> LIVING_ENTITY_STILL_ALIVE = LivingEntity::isAlive;
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = (entity) -> {
        return entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    };
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = (entity) -> {
        return entity instanceof Container && entity.isAlive();
    };
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = (entity) -> {
        return !(entity instanceof Player) || !entity.isSpectator() && !((Player) entity).isCreative();
    };
    public static final Predicate<Entity> ATTACK_ALLOWED = (entity) -> {
        return !(entity instanceof Player) || !entity.isSpectator() && !((Player) entity).isCreative() && entity.level.getDifficulty() != Difficulty.PEACEFUL;
    };
    public static final Predicate<Entity> NO_SPECTATORS = (entity) -> {
        return !entity.isSpectator();
    };

    public static Predicate<Entity> withinDistance(double d0, double d1, double d2, double d3) {
        double d4 = d3 * d3;

        return (entity) -> {
            return entity != null && entity.distanceToSqr(d0, d1, d2) <= d4;
        };
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        Team scoreboardteambase = entity.getTeam();
        Team.CollisionRule scoreboardteambase_enumteampush = scoreboardteambase == null ? Team.CollisionRule.ALWAYS : scoreboardteambase.getCollisionRule();

        return (Predicate) (scoreboardteambase_enumteampush == Team.CollisionRule.NEVER ? Predicates.alwaysFalse() : EntitySelector.NO_SPECTATORS.and((entity1) -> {
            if (!entity1.canCollideWith(entity) || !entity.canCollideWith(entity1)) { // CraftBukkit - collidable API
                return false;
            } else if (entity.level.isClientSide && (!(entity1 instanceof Player) || !((Player) entity1).isLocalPlayer())) {
                return false;
            } else {
                Team scoreboardteambase1 = entity1.getTeam();
                Team.CollisionRule scoreboardteambase_enumteampush1 = scoreboardteambase1 == null ? Team.CollisionRule.ALWAYS : scoreboardteambase1.getCollisionRule();

                if (scoreboardteambase_enumteampush1 == Team.CollisionRule.NEVER) {
                    return false;
                } else {
                    boolean flag = scoreboardteambase != null && scoreboardteambase.isAlliedTo(scoreboardteambase1);

                    return (scoreboardteambase_enumteampush == Team.CollisionRule.PUSH_OWN_TEAM || scoreboardteambase_enumteampush1 == Team.CollisionRule.PUSH_OWN_TEAM) && flag ? false : scoreboardteambase_enumteampush != Team.CollisionRule.PUSH_OTHER_TEAMS && scoreboardteambase_enumteampush1 != Team.CollisionRule.PUSH_OTHER_TEAMS || flag;
                }
            }
        }));
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return (entity1) -> {
            while (true) {
                if (entity1.isPassenger()) {
                    entity1 = entity1.getVehicle();
                    if (entity1 != entity) {
                        continue;
                    }

                    return false;
                }

                return true;
            }
        };
    }

    public static class EntitySelectorEquipable implements Predicate<Entity> {

        private final ItemStack a;

        public EntitySelectorEquipable(ItemStack itemstack) {
            this.a = itemstack;
        }

        public boolean test(@Nullable Entity entity) {
            if (!entity.isAlive()) {
                return false;
            } else if (!(entity instanceof LivingEntity)) {
                return false;
            } else {
                LivingEntity entityliving = (LivingEntity) entity;

                return entityliving.canTakeItem(this.a);
            }
        }
    }
}