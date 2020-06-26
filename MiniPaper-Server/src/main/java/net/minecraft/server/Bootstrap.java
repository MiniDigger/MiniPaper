package net.minecraft.server;

import java.io.PrintStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.locale.Language;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.util.datafix.fixes.ItemSpawnEggFix;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public class Bootstrap {

    public static final PrintStream STDOUT = System.out;
    private static boolean isBootstrapped;
    private static final Logger LOGGER = LogManager.getLogger();

    public static void bootStrap() {
        if (!Bootstrap.isBootstrapped) {
            Bootstrap.isBootstrapped = true;
            if (Registry.REGISTRY.keySet().isEmpty()) {
                throw new IllegalStateException("Unable to load registries");
            } else {
                FireBlock.bootStrap();
                ComposterBlock.bootStrap();
                if (EntityType.getKey(EntityType.PLAYER) == null) {
                    throw new IllegalStateException("Failed loading EntityTypes");
                } else {
                    PotionBrewing.bootStrap();
                    EntitySelectorOptions.bootStrap();
                    DispenseItemBehavior.bootStrap();
                    ArgumentTypes.bootStrap();
                    wrapStreams();
                }
                // CraftBukkit start - easier than fixing the decompile
                BlockStateData.register(1008, "{Name:'minecraft:oak_sign',Properties:{rotation:'0'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'0'}}");
                BlockStateData.register(1009, "{Name:'minecraft:oak_sign',Properties:{rotation:'1'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'1'}}");
                BlockStateData.register(1010, "{Name:'minecraft:oak_sign',Properties:{rotation:'2'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'2'}}");
                BlockStateData.register(1011, "{Name:'minecraft:oak_sign',Properties:{rotation:'3'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'3'}}");
                BlockStateData.register(1012, "{Name:'minecraft:oak_sign',Properties:{rotation:'4'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'4'}}");
                BlockStateData.register(1013, "{Name:'minecraft:oak_sign',Properties:{rotation:'5'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'5'}}");
                BlockStateData.register(1014, "{Name:'minecraft:oak_sign',Properties:{rotation:'6'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'6'}}");
                BlockStateData.register(1015, "{Name:'minecraft:oak_sign',Properties:{rotation:'7'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'7'}}");
                BlockStateData.register(1016, "{Name:'minecraft:oak_sign',Properties:{rotation:'8'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'8'}}");
                BlockStateData.register(1017, "{Name:'minecraft:oak_sign',Properties:{rotation:'9'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'9'}}");
                BlockStateData.register(1018, "{Name:'minecraft:oak_sign',Properties:{rotation:'10'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'10'}}");
                BlockStateData.register(1019, "{Name:'minecraft:oak_sign',Properties:{rotation:'11'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'11'}}");
                BlockStateData.register(1020, "{Name:'minecraft:oak_sign',Properties:{rotation:'12'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'12'}}");
                BlockStateData.register(1021, "{Name:'minecraft:oak_sign',Properties:{rotation:'13'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'13'}}");
                BlockStateData.register(1022, "{Name:'minecraft:oak_sign',Properties:{rotation:'14'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'14'}}");
                BlockStateData.register(1023, "{Name:'minecraft:oak_sign',Properties:{rotation:'15'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'15'}}");
                ItemIdFix.ITEM_NAMES.put(323, "minecraft:oak_sign");

                BlockStateData.register(1440, "{Name:\'minecraft:portal\',Properties:{axis:\'x\'}}", new String[]{"{Name:\'minecraft:portal\',Properties:{axis:\'x\'}}"});

                ItemIdFix.ITEM_NAMES.put(409, "minecraft:prismarine_shard");
                ItemIdFix.ITEM_NAMES.put(410, "minecraft:prismarine_crystals");
                ItemIdFix.ITEM_NAMES.put(411, "minecraft:rabbit");
                ItemIdFix.ITEM_NAMES.put(412, "minecraft:cooked_rabbit");
                ItemIdFix.ITEM_NAMES.put(413, "minecraft:rabbit_stew");
                ItemIdFix.ITEM_NAMES.put(414, "minecraft:rabbit_foot");
                ItemIdFix.ITEM_NAMES.put(415, "minecraft:rabbit_hide");
                ItemIdFix.ITEM_NAMES.put(416, "minecraft:armor_stand");

                ItemIdFix.ITEM_NAMES.put(423, "minecraft:mutton");
                ItemIdFix.ITEM_NAMES.put(424, "minecraft:cooked_mutton");
                ItemIdFix.ITEM_NAMES.put(425, "minecraft:banner");
                ItemIdFix.ITEM_NAMES.put(426, "minecraft:end_crystal");
                ItemIdFix.ITEM_NAMES.put(427, "minecraft:spruce_door");
                ItemIdFix.ITEM_NAMES.put(428, "minecraft:birch_door");
                ItemIdFix.ITEM_NAMES.put(429, "minecraft:jungle_door");
                ItemIdFix.ITEM_NAMES.put(430, "minecraft:acacia_door");
                ItemIdFix.ITEM_NAMES.put(431, "minecraft:dark_oak_door");
                ItemIdFix.ITEM_NAMES.put(432, "minecraft:chorus_fruit");
                ItemIdFix.ITEM_NAMES.put(433, "minecraft:chorus_fruit_popped");
                ItemIdFix.ITEM_NAMES.put(434, "minecraft:beetroot");
                ItemIdFix.ITEM_NAMES.put(435, "minecraft:beetroot_seeds");
                ItemIdFix.ITEM_NAMES.put(436, "minecraft:beetroot_soup");
                ItemIdFix.ITEM_NAMES.put(437, "minecraft:dragon_breath");
                ItemIdFix.ITEM_NAMES.put(438, "minecraft:splash_potion");
                ItemIdFix.ITEM_NAMES.put(439, "minecraft:spectral_arrow");
                ItemIdFix.ITEM_NAMES.put(440, "minecraft:tipped_arrow");
                ItemIdFix.ITEM_NAMES.put(441, "minecraft:lingering_potion");
                ItemIdFix.ITEM_NAMES.put(442, "minecraft:shield");
                ItemIdFix.ITEM_NAMES.put(443, "minecraft:elytra");
                ItemIdFix.ITEM_NAMES.put(444, "minecraft:spruce_boat");
                ItemIdFix.ITEM_NAMES.put(445, "minecraft:birch_boat");
                ItemIdFix.ITEM_NAMES.put(446, "minecraft:jungle_boat");
                ItemIdFix.ITEM_NAMES.put(447, "minecraft:acacia_boat");
                ItemIdFix.ITEM_NAMES.put(448, "minecraft:dark_oak_boat");
                ItemIdFix.ITEM_NAMES.put(449, "minecraft:totem_of_undying");
                ItemIdFix.ITEM_NAMES.put(450, "minecraft:shulker_shell");
                ItemIdFix.ITEM_NAMES.put(452, "minecraft:iron_nugget");
                ItemIdFix.ITEM_NAMES.put(453, "minecraft:knowledge_book");

                ItemSpawnEggFix.ID_TO_ENTITY[23] = "Arrow";
                // CraftBukkit end
            }
        }
    }

    private static <T> void checkTranslations(Iterable<T> iterable, Function<T, String> function, Set<String> set) {
        Language localelanguage = Language.getInstance();

        iterable.forEach((object) -> {
            String s = (String) function.apply(object);

            if (!localelanguage.has(s)) {
                set.add(s);
            }

        });
    }

    private static void checkGameruleTranslations(final Set<String> set) {
        final Language localelanguage = Language.getInstance();

        GameRules.a(new GameRules.GameRuleVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void a(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.Type<T> gamerules_gameruledefinition) {
                if (!localelanguage.has(gamerules_gamerulekey.b())) {
                    set.add(gamerules_gamerulekey.a());
                }

            }
        });
    }

    public static Set<String> getMissingTranslations() {
        Set<String> set = new TreeSet();

        checkTranslations(Registry.ATTRIBUTE, Attribute::getDescriptionId, set);
        checkTranslations(Registry.ENTITY_TYPE, EntityType::getDescriptionId, set);
        checkTranslations(Registry.MOB_EFFECT, MobEffect::getDescriptionId, set);
        checkTranslations(Registry.ITEM, Item::getDescriptionId, set);
        checkTranslations(Registry.ENCHANTMENT, Enchantment::getDescriptionId, set);
        checkTranslations(Registry.BIOME, Biome::getDescriptionId, set);
        checkTranslations(Registry.BLOCK, Block::getDescriptionId, set);
        checkTranslations(Registry.CUSTOM_STAT, (minecraftkey) -> {
            return "stat." + minecraftkey.toString().replace(':', '.');
        }, set);
        checkGameruleTranslations((Set) set);
        return set;
    }

    public static void validate() {
        if (!Bootstrap.isBootstrapped) {
            throw new IllegalArgumentException("Not bootstrapped");
        } else {
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                getMissingTranslations().forEach((s) -> {
                    Bootstrap.LOGGER.error("Missing translations: " + s);
                });
            }

            DefaultAttributes.validate();
        }
    }

    private static void wrapStreams() {
        if (Bootstrap.LOGGER.isDebugEnabled()) {
            System.setErr(new DebugLoggedPrintStream("STDERR", System.err));
            System.setOut(new DebugLoggedPrintStream("STDOUT", Bootstrap.STDOUT));
        } else {
            System.setErr(new LoggedPrintStream("STDERR", System.err));
            System.setOut(new LoggedPrintStream("STDOUT", Bootstrap.STDOUT));
        }

    }

    public static void realStdoutPrintln(String s) {
        Bootstrap.STDOUT.println(s);
    }
}
