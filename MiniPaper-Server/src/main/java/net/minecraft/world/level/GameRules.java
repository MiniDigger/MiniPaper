package net.minecraft.world.level;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.DynamicLike;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GameRules {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<GameRules.GameRuleKey<?>, GameRules.Type<?>> GAME_RULE_TYPES = Maps.newTreeMap(Comparator.comparing((gamerules_gamerulekey) -> {
        return gamerules_gamerulekey.a;
    }));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DOFIRETICK = a("doFireTick", GameRules.Category.UPDATES, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_MOBGRIEFING = a("mobGriefing", GameRules.Category.MOBS, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_KEEPINVENTORY = a("keepInventory", GameRules.Category.PLAYER, GameRules.BooleanValue.b(false));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DOMOBSPAWNING = a("doMobSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DOMOBLOOT = a("doMobLoot", GameRules.Category.DROPS, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DOBLOCKDROPS = a("doTileDrops", GameRules.Category.DROPS, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DOENTITYDROPS = a("doEntityDrops", GameRules.Category.DROPS, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_COMMANDBLOCKOUTPUT = a("commandBlockOutput", GameRules.Category.CHAT, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_NATURAL_REGENERATION = a("naturalRegeneration", GameRules.Category.PLAYER, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DAYLIGHT = a("doDaylightCycle", GameRules.Category.UPDATES, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_LOGADMINCOMMANDS = a("logAdminCommands", GameRules.Category.CHAT, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_SHOWDEATHMESSAGES = a("showDeathMessages", GameRules.Category.CHAT, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.IntegerValue> RULE_RANDOMTICKING = a("randomTickSpeed", GameRules.Category.UPDATES, GameRules.IntegerValue.b(3));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_SENDCOMMANDFEEDBACK = a("sendCommandFeedback", GameRules.Category.CHAT, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_REDUCEDDEBUGINFO = a("reducedDebugInfo", GameRules.Category.MISC, GameRules.BooleanValue.b(false, (minecraftserver, gamerules_gameruleboolean) -> {
        int i = gamerules_gameruleboolean.a() ? 22 : 23;
        Iterator iterator = minecraftserver.getPlayerList().getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.sendPacket(new ClientboundEntityEventPacket(entityplayer, (byte) i));
        }

    }));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_SPECTATORSGENERATECHUNKS = a("spectatorsGenerateChunks", GameRules.Category.PLAYER, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.IntegerValue> RULE_SPAWN_RADIUS = a("spawnRadius", GameRules.Category.PLAYER, GameRules.IntegerValue.b(10));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DISABLE_ELYTRA_MOVEMENT_CHECK = a("disableElytraMovementCheck", GameRules.Category.PLAYER, GameRules.BooleanValue.b(false));
    public static final GameRules.GameRuleKey<GameRules.IntegerValue> RULE_MAX_ENTITY_CRAMMING = a("maxEntityCramming", GameRules.Category.MOBS, GameRules.IntegerValue.b(24));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_WEATHER_CYCLE = a("doWeatherCycle", GameRules.Category.UPDATES, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_LIMITED_CRAFTING = a("doLimitedCrafting", GameRules.Category.PLAYER, GameRules.BooleanValue.b(false));
    public static final GameRules.GameRuleKey<GameRules.IntegerValue> RULE_MAX_COMMAND_CHAIN_LENGTH = a("maxCommandChainLength", GameRules.Category.MISC, GameRules.IntegerValue.b(65536));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_ANNOUNCE_ADVANCEMENTS = a("announceAdvancements", GameRules.Category.CHAT, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DISABLE_RAIDS = a("disableRaids", GameRules.Category.MOBS, GameRules.BooleanValue.b(false));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DOINSOMNIA = a("doInsomnia", GameRules.Category.SPAWNING, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DO_IMMEDIATE_RESPAWN = a("doImmediateRespawn", GameRules.Category.PLAYER, GameRules.BooleanValue.b(false, (minecraftserver, gamerules_gameruleboolean) -> {
        Iterator iterator = minecraftserver.getPlayerList().getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, gamerules_gameruleboolean.a() ? 1.0F : 0.0F));
        }

    }));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DROWNING_DAMAGE = a("drowningDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_FALL_DAMAGE = a("fallDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_FIRE_DAMAGE = a("fireDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DO_PATROL_SPAWNING = a("doPatrolSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_DO_TRADER_SPAWNING = a("doTraderSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_FORGIVE_DEAD_PLAYERS = a("forgiveDeadPlayers", GameRules.Category.MOBS, GameRules.BooleanValue.b(true));
    public static final GameRules.GameRuleKey<GameRules.BooleanValue> RULE_UNIVERSAL_ANGER = a("universalAnger", GameRules.Category.MOBS, GameRules.BooleanValue.b(false));
    private final Map<GameRules.GameRuleKey<?>, GameRules.Value<?>> rules;

    private static <T extends GameRules.Value<T>> GameRules.GameRuleKey<T> a(String s, GameRules.Category gamerules_gamerulecategory, GameRules.Type<T> gamerules_gameruledefinition) {
        GameRules.GameRuleKey<T> gamerules_gamerulekey = new GameRules.GameRuleKey<>(s, gamerules_gamerulecategory);
        GameRules.Type<?> gamerules_gameruledefinition1 = (GameRules.Type) GameRules.GAME_RULE_TYPES.put(gamerules_gamerulekey, gamerules_gameruledefinition);

        if (gamerules_gameruledefinition1 != null) {
            throw new IllegalStateException("Duplicate game rule registration for " + s);
        } else {
            return gamerules_gamerulekey;
        }
    }

    public GameRules(DynamicLike<?> dynamiclike) {
        this();
        this.loadFromTag(dynamiclike);
    }

    public GameRules() {
        this.rules = (Map) GameRules.GAME_RULE_TYPES.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
            return ((GameRules.Type) entry.getValue()).createRule();
        }));
    }

    private GameRules(Map<GameRules.GameRuleKey<?>, GameRules.Value<?>> map) {
        this.rules = map;
    }

    public <T extends GameRules.Value<T>> T get(GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        return (T) this.rules.get(gamerules_gamerulekey); // CraftBukkit - decompile error
    }

    public CompoundTag createTag() {
        CompoundTag nbttagcompound = new CompoundTag();

        this.rules.forEach((gamerules_gamerulekey, gamerules_gamerulevalue) -> {
            nbttagcompound.putString(gamerules_gamerulekey.a, gamerules_gamerulevalue.serialize());
        });
        return nbttagcompound;
    }

    private void loadFromTag(DynamicLike<?> dynamiclike) {
        this.rules.forEach((gamerules_gamerulekey, gamerules_gamerulevalue) -> {
            dynamiclike.get(gamerules_gamerulekey.a).asString().result().ifPresent(gamerules_gamerulevalue::deserialize);
        });
    }

    public GameRules copy() {
        return new GameRules((Map) this.rules.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
            return ((GameRules.Value) entry.getValue()).copy();
        })));
    }

    public static void a(GameRules.GameRuleVisitor gamerules_gamerulevisitor) {
        GameRules.GAME_RULE_TYPES.forEach((gamerules_gamerulekey, gamerules_gameruledefinition) -> {
            a(gamerules_gamerulevisitor, gamerules_gamerulekey, gamerules_gameruledefinition);
        });
    }

    private static <T extends GameRules.Value<T>> void a(GameRules.GameRuleVisitor gamerules_gamerulevisitor, GameRules.GameRuleKey<?> gamerules_gamerulekey, GameRules.Type<?> gamerules_gameruledefinition) {
        gamerules_gamerulevisitor.a((GameRules.GameRuleKey<T>) gamerules_gamerulekey, (GameRules.Type<T>) gamerules_gameruledefinition); // CraftBukkit - decompile error
        ((GameRules.Type<T>) gamerules_gameruledefinition).a(gamerules_gamerulevisitor, (GameRules.GameRuleKey<T>) gamerules_gamerulekey); // CraftBukkit - decompile error
    }

    public boolean getBoolean(GameRules.GameRuleKey<GameRules.BooleanValue> gamerules_gamerulekey) {
        return ((GameRules.BooleanValue) this.get(gamerules_gamerulekey)).a();
    }

    public int getInt(GameRules.GameRuleKey<GameRules.IntegerValue> gamerules_gamerulekey) {
        return ((GameRules.IntegerValue) this.get(gamerules_gamerulekey)).a();
    }

    public static class BooleanValue extends GameRules.Value<GameRules.BooleanValue> {

        private boolean b;

        private static GameRules.Type<GameRules.BooleanValue> b(boolean flag, BiConsumer<MinecraftServer, GameRules.BooleanValue> biconsumer) {
            return new GameRules.Type<>(BoolArgumentType::bool, (gamerules_gameruledefinition) -> {
                return new GameRules.BooleanValue(gamerules_gameruledefinition, flag);
            }, biconsumer, GameRules.GameRuleVisitor::b);
        }

        private static GameRules.Type<GameRules.BooleanValue> b(boolean flag) {
            return b(flag, (minecraftserver, gamerules_gameruleboolean) -> {
            });
        }

        public BooleanValue(GameRules.Type<GameRules.BooleanValue> gamerules_gameruledefinition, boolean flag) {
            super(gamerules_gameruledefinition);
            this.b = flag;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandSourceStack> commandcontext, String s) {
            this.b = BoolArgumentType.getBool(commandcontext, s);
        }

        public boolean a() {
            return this.b;
        }

        public void a(boolean flag, @Nullable MinecraftServer minecraftserver) {
            this.b = flag;
            this.onChanged(minecraftserver);
        }

        @Override
        public String serialize() {
            return Boolean.toString(this.b);
        }

        @Override
        public void deserialize(String s) { // PAIL - protected->public
            this.b = Boolean.parseBoolean(s);
        }

        @Override
        public int getCommandResult() {
            return this.b ? 1 : 0;
        }

        @Override
        protected GameRules.BooleanValue g() {
            return this;
        }

        @Override
        protected GameRules.BooleanValue f() {
            return new GameRules.BooleanValue(this.type, this.b);
        }
    }

    public static class IntegerValue extends GameRules.Value<GameRules.IntegerValue> {

        private int b;

        private static GameRules.Type<GameRules.IntegerValue> a(int i, BiConsumer<MinecraftServer, GameRules.IntegerValue> biconsumer) {
            return new GameRules.Type<>(IntegerArgumentType::integer, (gamerules_gameruledefinition) -> {
                return new GameRules.IntegerValue(gamerules_gameruledefinition, i);
            }, biconsumer, GameRules.GameRuleVisitor::c);
        }

        private static GameRules.Type<GameRules.IntegerValue> b(int i) {
            return a(i, (minecraftserver, gamerules_gameruleint) -> {
            });
        }

        public IntegerValue(GameRules.Type<GameRules.IntegerValue> gamerules_gameruledefinition, int i) {
            super(gamerules_gameruledefinition);
            this.b = i;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandSourceStack> commandcontext, String s) {
            this.b = IntegerArgumentType.getInteger(commandcontext, s);
        }

        public int a() {
            return this.b;
        }

        @Override
        public String serialize() {
            return Integer.toString(this.b);
        }

        @Override
        public void deserialize(String s) { // PAIL - protected->public
            this.b = c(s);
        }

        private static int c(String s) {
            if (!s.isEmpty()) {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException numberformatexception) {
                    GameRules.LOGGER.warn("Failed to parse integer {}", s);
                }
            }

            return 0;
        }

        @Override
        public int getCommandResult() {
            return this.b;
        }

        @Override
        protected GameRules.IntegerValue g() {
            return this;
        }

        @Override
        protected GameRules.IntegerValue f() {
            return new GameRules.IntegerValue(this.type, this.b);
        }
    }

    public abstract static class Value<T extends GameRules.Value<T>> {

        protected final GameRules.Type<T> type;

        public Value(GameRules.Type<T> gamerules_gameruledefinition) {
            this.type = gamerules_gameruledefinition;
        }

        protected abstract void updateFromArgument(CommandContext<CommandSourceStack> commandcontext, String s);

        public void setFromArgument(CommandContext<CommandSourceStack> commandcontext, String s) {
            this.updateFromArgument(commandcontext, s);
            this.onChanged(((CommandSourceStack) commandcontext.getSource()).getServer());
        }

        public void onChanged(@Nullable MinecraftServer minecraftserver) {
            if (minecraftserver != null) {
                this.type.callback.accept(minecraftserver, this.getSelf());
            }

        }

        public abstract void deserialize(String s); // PAIL - private->public

        public abstract String serialize();

        public String toString() {
            return this.serialize();
        }

        public abstract int getCommandResult();

        protected abstract T getSelf();

        protected abstract T copy();
    }

    public static class Type<T extends GameRules.Value<T>> {

        private final Supplier<ArgumentType<?>> argument;
        private final Function<GameRules.Type<T>, T> constructor;
        private final BiConsumer<MinecraftServer, T> callback;
        private final GameRules.VisitorCaller<T> visitorCaller;

        private Type(Supplier<ArgumentType<?>> supplier, Function<GameRules.Type<T>, T> function, BiConsumer<MinecraftServer, T> biconsumer, GameRules.VisitorCaller<T> gamerules_h) {
            this.argument = supplier;
            this.constructor = function;
            this.callback = biconsumer;
            this.visitorCaller = gamerules_h;
        }

        public RequiredArgumentBuilder<CommandSourceStack, ?> createArgument(String s) {
            return Commands.argument(s, (ArgumentType) this.argument.get());
        }

        public T createRule() {
            return this.constructor.apply(this); // CraftBukkit - decompile error
        }

        public void a(GameRules.GameRuleVisitor gamerules_gamerulevisitor, GameRules.GameRuleKey<T> gamerules_gamerulekey) {
            this.visitorCaller.call(gamerules_gamerulevisitor, gamerules_gamerulekey, this);
        }
    }

    public static final class GameRuleKey<T extends GameRules.Value<T>> {

        private final String a;
        private final GameRules.Category b;

        public GameRuleKey(String s, GameRules.Category gamerules_gamerulecategory) {
            this.a = s;
            this.b = gamerules_gamerulecategory;
        }

        public String toString() {
            return this.a;
        }

        public boolean equals(Object object) {
            return this == object ? true : object instanceof GameRules.GameRuleKey && ((GameRules.GameRuleKey) object).a.equals(this.a);
        }

        public int hashCode() {
            return this.a.hashCode();
        }

        public String a() {
            return this.a;
        }

        public String b() {
            return "gamerule." + this.a;
        }
    }

    public interface GameRuleVisitor {

        default <T extends GameRules.Value<T>> void a(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.Type<T> gamerules_gameruledefinition) {}

        default void b(GameRules.GameRuleKey<GameRules.BooleanValue> gamerules_gamerulekey, GameRules.Type<GameRules.BooleanValue> gamerules_gameruledefinition) {}

        default void c(GameRules.GameRuleKey<GameRules.IntegerValue> gamerules_gamerulekey, GameRules.Type<GameRules.IntegerValue> gamerules_gameruledefinition) {}
    }

    interface VisitorCaller<T extends GameRules.Value<T>> {

        void call(GameRules.GameRuleVisitor gamerules_gamerulevisitor, GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.Type<T> gamerules_gameruledefinition);
    }

    public static enum Category {

        PLAYER("gamerule.category.player"), MOBS("gamerule.category.mobs"), SPAWNING("gamerule.category.spawning"), DROPS("gamerule.category.drops"), UPDATES("gamerule.category.updates"), CHAT("gamerule.category.chat"), MISC("gamerule.category.misc");

        private final String descriptionId;

        private Category(String s) {
            this.descriptionId = s;
        }
    }
}
