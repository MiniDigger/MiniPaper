package net.minecraft.util.datafix;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.util.datafix.fixes.*;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import net.minecraft.util.datafix.schemas.V100;
import net.minecraft.util.datafix.schemas.V102;
import net.minecraft.util.datafix.schemas.V1022;
import net.minecraft.util.datafix.schemas.V106;
import net.minecraft.util.datafix.schemas.V107;
import net.minecraft.util.datafix.schemas.V1125;
import net.minecraft.util.datafix.schemas.V135;
import net.minecraft.util.datafix.schemas.V143;
import net.minecraft.util.datafix.schemas.V1451;
import net.minecraft.util.datafix.schemas.V1451_1;
import net.minecraft.util.datafix.schemas.V1451_2;
import net.minecraft.util.datafix.schemas.V1451_3;
import net.minecraft.util.datafix.schemas.V1451_4;
import net.minecraft.util.datafix.schemas.V1451_5;
import net.minecraft.util.datafix.schemas.V1451_6;
import net.minecraft.util.datafix.schemas.V1451_7;
import net.minecraft.util.datafix.schemas.V1460;
import net.minecraft.util.datafix.schemas.V1466;
import net.minecraft.util.datafix.schemas.V1470;
import net.minecraft.util.datafix.schemas.V1481;
import net.minecraft.util.datafix.schemas.V1483;
import net.minecraft.util.datafix.schemas.V1486;
import net.minecraft.util.datafix.schemas.V1510;
import net.minecraft.util.datafix.schemas.V1800;
import net.minecraft.util.datafix.schemas.V1801;
import net.minecraft.util.datafix.schemas.V1904;
import net.minecraft.util.datafix.schemas.V1906;
import net.minecraft.util.datafix.schemas.V1909;
import net.minecraft.util.datafix.schemas.V1920;
import net.minecraft.util.datafix.schemas.V1928;
import net.minecraft.util.datafix.schemas.V1929;
import net.minecraft.util.datafix.schemas.V1931;
import net.minecraft.util.datafix.schemas.V2100;
import net.minecraft.util.datafix.schemas.V2501;
import net.minecraft.util.datafix.schemas.V2502;
import net.minecraft.util.datafix.schemas.V2505;
import net.minecraft.util.datafix.schemas.V2509;
import net.minecraft.util.datafix.schemas.V2519;
import net.minecraft.util.datafix.schemas.V2522;
import net.minecraft.util.datafix.schemas.V2551;
import net.minecraft.util.datafix.schemas.V501;
import net.minecraft.util.datafix.schemas.V700;
import net.minecraft.util.datafix.schemas.V701;
import net.minecraft.util.datafix.schemas.V702;
import net.minecraft.util.datafix.schemas.V703;
import net.minecraft.util.datafix.schemas.V704;
import net.minecraft.util.datafix.schemas.V705;
import net.minecraft.util.datafix.schemas.V808;
import net.minecraft.util.datafix.schemas.V99;

public class DataFixers {

    private static final BiFunction<Integer, Schema, Schema> SAME = Schema::new;
    private static final BiFunction<Integer, Schema, Schema> SAME_NAMESPACED = NamespacedSchema::new;
    private static final DataFixer DATA_FIXER = createFixerUpper();

    private static DataFixer createFixerUpper() {
        DataFixerBuilder datafixerbuilder = new DataFixerBuilder(SharedConstants.getCurrentVersion().getWorldVersion());

        addFixers(datafixerbuilder);
        return datafixerbuilder.build(Util.bootstrapExecutor());
    }

    public static DataFixer getDataFixerOH() {
        return DataFixers.DATA_FIXER;
    }

    private static void addFixers(DataFixerBuilder datafixerbuilder) {
        Schema schema = datafixerbuilder.addSchema(99, V99::new);
        Schema schema1 = datafixerbuilder.addSchema(100, V100::new);

        datafixerbuilder.addFixer(new EntityEquipmentToArmorAndHandFix(schema1, true));
        Schema schema2 = datafixerbuilder.addSchema(101, DataFixers.SAME);

        datafixerbuilder.addFixer(new BlockEntitySignTextStrictJsonFix(schema2, false));
        Schema schema3 = datafixerbuilder.addSchema(102, V102::new);

        datafixerbuilder.addFixer(new ItemIdFix(schema3, true));
        datafixerbuilder.addFixer(new ItemPotionFix(schema3, false));
        Schema schema4 = datafixerbuilder.addSchema(105, DataFixers.SAME);

        datafixerbuilder.addFixer(new ItemSpawnEggFix(schema4, true));
        Schema schema5 = datafixerbuilder.addSchema(106, V106::new);

        datafixerbuilder.addFixer(new MobSpawnerEntityIdentifiersFix(schema5, true));
        Schema schema6 = datafixerbuilder.addSchema(107, V107::new);

        datafixerbuilder.addFixer(new EntityMinecartIdentifiersFix(schema6, true));
        Schema schema7 = datafixerbuilder.addSchema(108, DataFixers.SAME);

        datafixerbuilder.addFixer(new EntityStringUuidFix(schema7, true));
        Schema schema8 = datafixerbuilder.addSchema(109, DataFixers.SAME);

        datafixerbuilder.addFixer(new EntityHealthFix(schema8, true));
        Schema schema9 = datafixerbuilder.addSchema(110, DataFixers.SAME);

        datafixerbuilder.addFixer(new EntityHorseSaddleFix(schema9, true));
        Schema schema10 = datafixerbuilder.addSchema(111, DataFixers.SAME);

        datafixerbuilder.addFixer(new EntityPaintingItemFrameDirectionFix(schema10, true));
        Schema schema11 = datafixerbuilder.addSchema(113, DataFixers.SAME);

        datafixerbuilder.addFixer(new EntityRedundantChanceTagsFix(schema11, true));
        Schema schema12 = datafixerbuilder.addSchema(135, V135::new);

        datafixerbuilder.addFixer(new EntityRidingToPassengersFix(schema12, true));
        Schema schema13 = datafixerbuilder.addSchema(143, V143::new);

        datafixerbuilder.addFixer(new EntityTippedArrowFix(schema13, true));
        Schema schema14 = datafixerbuilder.addSchema(147, DataFixers.SAME);

        datafixerbuilder.addFixer(new EntityArmorStandSilentFix(schema14, true));
        Schema schema15 = datafixerbuilder.addSchema(165, DataFixers.SAME);

        datafixerbuilder.addFixer(new ItemWrittenBookPagesStrictJsonFix(schema15, true));
        Schema schema16 = datafixerbuilder.addSchema(501, V501::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema16, "Add 1.10 entities fix", References.ENTITY));
        Schema schema17 = datafixerbuilder.addSchema(502, DataFixers.SAME);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema17, "cooked_fished item renamer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:cooked_fished") ? "minecraft:cooked_fish" : s;
        }));
        datafixerbuilder.addFixer(new EntityZombieVillagerTypeFix(schema17, false));
        Schema schema18 = datafixerbuilder.addSchema(505, DataFixers.SAME);

        datafixerbuilder.addFixer(new OptionsForceVBOFix(schema18, false));
        Schema schema19 = datafixerbuilder.addSchema(700, V700::new);

        datafixerbuilder.addFixer(new EntityElderGuardianSplitFix(schema19, true));
        Schema schema20 = datafixerbuilder.addSchema(701, V701::new);

        datafixerbuilder.addFixer(new EntitySkeletonSplitFix(schema20, true));
        Schema schema21 = datafixerbuilder.addSchema(702, V702::new);

        datafixerbuilder.addFixer(new EntityZombieSplitFix(schema21, true));
        Schema schema22 = datafixerbuilder.addSchema(703, V703::new);

        datafixerbuilder.addFixer(new EntityHorseSplitFix(schema22, true));
        Schema schema23 = datafixerbuilder.addSchema(704, V704::new);

        datafixerbuilder.addFixer(new BlockEntityIdFix(schema23, true));
        Schema schema24 = datafixerbuilder.addSchema(705, V705::new);

        datafixerbuilder.addFixer(new EntityIdFix(schema24, true));
        Schema schema25 = datafixerbuilder.addSchema(804, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ItemBannerColorFix(schema25, true));
        Schema schema26 = datafixerbuilder.addSchema(806, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ItemWaterPotionFix(schema26, false));
        Schema schema27 = datafixerbuilder.addSchema(808, V808::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema27, "added shulker box", References.BLOCK_ENTITY));
        Schema schema28 = datafixerbuilder.addSchema(808, 1, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new EntityShulkerColorFix(schema28, false));
        Schema schema29 = datafixerbuilder.addSchema(813, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ItemShulkerBoxColorFix(schema29, false));
        datafixerbuilder.addFixer(new BlockEntityShulkerBoxColorFix(schema29, false));
        Schema schema30 = datafixerbuilder.addSchema(816, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new OptionsLowerCaseLanguageFix(schema30, false));
        Schema schema31 = datafixerbuilder.addSchema(820, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema31, "totem item renamer", createRenamer("minecraft:totem", "minecraft:totem_of_undying")));
        Schema schema32 = datafixerbuilder.addSchema(1022, V1022::new);

        datafixerbuilder.addFixer(new WriteAndReadFix(schema32, "added shoulder entities to players", References.PLAYER));
        Schema schema33 = datafixerbuilder.addSchema(1125, V1125::new);

        datafixerbuilder.addFixer(new BedBlockEntityInjecter(schema33, true));
        datafixerbuilder.addFixer(new BedItemColorFix(schema33, false));
        Schema schema34 = datafixerbuilder.addSchema(1344, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new OptionsKeyLwjgl3Fix(schema34, false));
        Schema schema35 = datafixerbuilder.addSchema(1446, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new OptionsKeyTranslationFix(schema35, false));
        Schema schema36 = datafixerbuilder.addSchema(1450, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new BlockStateStructureTemplateFix(schema36, false));
        Schema schema37 = datafixerbuilder.addSchema(1451, V1451::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema37, "AddTrappedChestFix", References.BLOCK_ENTITY));
        Schema schema38 = datafixerbuilder.addSchema(1451, 1, V1451_1::new);

        datafixerbuilder.addFixer(new ChunkPalettedStorageFix(schema38, true));
        Schema schema39 = datafixerbuilder.addSchema(1451, 2, V1451_2::new);

        datafixerbuilder.addFixer(new BlockEntityBlockStateFix(schema39, true));
        Schema schema40 = datafixerbuilder.addSchema(1451, 3, V1451_3::new);

        datafixerbuilder.addFixer(new EntityBlockStateFix(schema40, true));
        datafixerbuilder.addFixer(new ItemStackMapIdFix(schema40, false));
        Schema schema41 = datafixerbuilder.addSchema(1451, 4, V1451_4::new);

        datafixerbuilder.addFixer(new BlockNameFlatteningFix(schema41, true));
        datafixerbuilder.addFixer(new ItemStackTheFlatteningFix(schema41, false));
        Schema schema42 = datafixerbuilder.addSchema(1451, 5, V1451_5::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema42, "RemoveNoteBlockFlowerPotFix", References.BLOCK_ENTITY));
        datafixerbuilder.addFixer(new ItemStackSpawnEggFix(schema42, false));
        datafixerbuilder.addFixer(new EntityWolfColorFix(schema42, false));
        datafixerbuilder.addFixer(new BlockEntityBannerColorFix(schema42, false));
        datafixerbuilder.addFixer(new LevelFlatGeneratorInfoFix(schema42, false));
        Schema schema43 = datafixerbuilder.addSchema(1451, 6, V1451_6::new);

        datafixerbuilder.addFixer(new StatsCounterFix(schema43, true));
        datafixerbuilder.addFixer(new BlockEntityJukeboxFix(schema43, false));
        Schema schema44 = datafixerbuilder.addSchema(1451, 7, V1451_7::new);

        datafixerbuilder.addFixer(new SavedDataVillageCropFix(schema44, true));
        Schema schema45 = datafixerbuilder.addSchema(1451, 7, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new VillagerTradeFix(schema45, false));
        Schema schema46 = datafixerbuilder.addSchema(1456, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new EntityItemFrameDirectionFix(schema46, false));
        Schema schema47 = datafixerbuilder.addSchema(1458, DataFixers.SAME_NAMESPACED);

        // CraftBukkit start
        datafixerbuilder.addFixer(new com.mojang.datafixers.DataFix(schema47, false) {
            @Override
            protected com.mojang.datafixers.TypeRewriteRule makeRule() {
                return this.fixTypeEverywhereTyped("Player CustomName", this.getInputSchema().getType(References.PLAYER), (typed) -> {
                    return typed.update(DSL.remainderFinder(), (dynamic) -> {
                        return EntityCustomNameToComponentFix.fixTagCustomName(dynamic);
                    });
                });
            }
        });
        // CraftBukkit end
        datafixerbuilder.addFixer(new EntityCustomNameToComponentFix(schema47, false));
        datafixerbuilder.addFixer(new ItemCustomNameToComponentFix(schema47, false));
        datafixerbuilder.addFixer(new BlockEntityCustomNameToComponentFix(schema47, false));
        Schema schema48 = datafixerbuilder.addSchema(1460, V1460::new);

        datafixerbuilder.addFixer(new EntityPaintingMotiveFix(schema48, false));
        Schema schema49 = datafixerbuilder.addSchema(1466, V1466::new);

        datafixerbuilder.addFixer(new ChunkToProtochunkFix(schema49, true));
        Schema schema50 = datafixerbuilder.addSchema(1470, V1470::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema50, "Add 1.13 entities fix", References.ENTITY));
        Schema schema51 = datafixerbuilder.addSchema(1474, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ColorlessShulkerEntityFix(schema51, false));
        datafixerbuilder.addFixer(BlockRenameFix.create(schema51, "Colorless shulker block fixer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:purple_shulker_box") ? "minecraft:shulker_box" : s;
        }));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema51, "Colorless shulker item fixer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:purple_shulker_box") ? "minecraft:shulker_box" : s;
        }));
        Schema schema52 = datafixerbuilder.addSchema(1475, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema52, "Flowing fixer", createRenamer((Map) ImmutableMap.of("minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"))));
        Schema schema53 = datafixerbuilder.addSchema(1480, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema53, "Rename coral blocks", createRenamer(RenamedCoralFix.RENAMED_IDS)));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema53, "Rename coral items", createRenamer(RenamedCoralFix.RENAMED_IDS)));
        Schema schema54 = datafixerbuilder.addSchema(1481, V1481::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema54, "Add conduit", References.BLOCK_ENTITY));
        Schema schema55 = datafixerbuilder.addSchema(1483, V1483::new);

        datafixerbuilder.addFixer(new EntityPufferfishRenameFix(schema55, true));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema55, "Rename pufferfish egg item", createRenamer(EntityPufferfishRenameFix.RENAMED_IDS)));
        Schema schema56 = datafixerbuilder.addSchema(1484, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema56, "Rename seagrass items", createRenamer((Map) ImmutableMap.of("minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass"))));
        datafixerbuilder.addFixer(BlockRenameFix.create(schema56, "Rename seagrass blocks", createRenamer((Map) ImmutableMap.of("minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass"))));
        datafixerbuilder.addFixer(new HeightmapRenamingFix(schema56, false));
        Schema schema57 = datafixerbuilder.addSchema(1486, V1486::new);

        datafixerbuilder.addFixer(new EntityCodSalmonFix(schema57, true));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema57, "Rename cod/salmon egg items", createRenamer(EntityCodSalmonFix.RENAMED_EGG_IDS)));
        Schema schema58 = datafixerbuilder.addSchema(1487, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema58, "Rename prismarine_brick(s)_* blocks", createRenamer((Map) ImmutableMap.of("minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"))));
        datafixerbuilder.addFixer(BlockRenameFix.create(schema58, "Rename prismarine_brick(s)_* items", createRenamer((Map) ImmutableMap.of("minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"))));
        Schema schema59 = datafixerbuilder.addSchema(1488, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema59, "Rename kelp/kelptop", createRenamer((Map) ImmutableMap.of("minecraft:kelp_top", "minecraft:kelp", "minecraft:kelp", "minecraft:kelp_plant"))));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema59, "Rename kelptop", createRenamer("minecraft:kelp_top", "minecraft:kelp")));
        datafixerbuilder.addFixer(new NamedEntityFix(schema59, false, "Command block block entity custom name fix", References.BLOCK_ENTITY, "minecraft:command_block") {
            @Override
            protected Typed<?> fix(Typed<?> typed) {
                return typed.update(DSL.remainderFinder(), EntityCustomNameToComponentFix::fixTagCustomName);
            }
        });
        datafixerbuilder.addFixer(new NamedEntityFix(schema59, false, "Command block minecart custom name fix", References.ENTITY, "minecraft:commandblock_minecart") {
            @Override
            protected Typed<?> fix(Typed<?> typed) {
                return typed.update(DSL.remainderFinder(), EntityCustomNameToComponentFix::fixTagCustomName);
            }
        });
        datafixerbuilder.addFixer(new IglooMetadataRemovalFix(schema59, false));
        Schema schema60 = datafixerbuilder.addSchema(1490, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema60, "Rename melon_block", createRenamer("minecraft:melon_block", "minecraft:melon")));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema60, "Rename melon_block/melon/speckled_melon", createRenamer((Map) ImmutableMap.of("minecraft:melon_block", "minecraft:melon", "minecraft:melon", "minecraft:melon_slice", "minecraft:speckled_melon", "minecraft:glistering_melon_slice"))));
        Schema schema61 = datafixerbuilder.addSchema(1492, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ChunkStructuresTemplateRenameFix(schema61, false));
        Schema schema62 = datafixerbuilder.addSchema(1494, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ItemStackEnchantmentNamesFix(schema62, false));
        Schema schema63 = datafixerbuilder.addSchema(1496, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new LeavesFix(schema63, false));
        Schema schema64 = datafixerbuilder.addSchema(1500, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new BlockEntityKeepPacked(schema64, false));
        Schema schema65 = datafixerbuilder.addSchema(1501, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new AdvancementsFix(schema65, false));
        Schema schema66 = datafixerbuilder.addSchema(1502, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new RecipesFix(schema66, false));
        Schema schema67 = datafixerbuilder.addSchema(1506, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new LevelDataGeneratorOptionsFix(schema67, false));
        Schema schema68 = datafixerbuilder.addSchema(1510, V1510::new);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema68, "Block renamening fix", createRenamer(EntityTheRenameningFix.RENAMED_BLOCKS)));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema68, "Item renamening fix", createRenamer(EntityTheRenameningFix.RENAMED_ITEMS)));
        datafixerbuilder.addFixer(new RecipesRenameningFix(schema68, false));
        datafixerbuilder.addFixer(new EntityTheRenameningFix(schema68, true));
        datafixerbuilder.addFixer(new SwimStatsRenameFix(schema68, false));
        Schema schema69 = datafixerbuilder.addSchema(1514, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ObjectiveDisplayNameFix(schema69, false));
        datafixerbuilder.addFixer(new TeamDisplayNameFix(schema69, false));
        datafixerbuilder.addFixer(new ObjectiveRenderTypeFix(schema69, false));
        Schema schema70 = datafixerbuilder.addSchema(1515, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema70, "Rename coral fan blocks", createRenamer(RenamedCoralFansFix.RENAMED_IDS)));
        Schema schema71 = datafixerbuilder.addSchema(1624, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new TrappedChestBlockEntityFix(schema71, false));
        Schema schema72 = datafixerbuilder.addSchema(1800, V1800::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema72, "Added 1.14 mobs fix", References.ENTITY));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema72, "Rename dye items", createRenamer(DyeItemRenameFix.RENAMED_IDS)));
        Schema schema73 = datafixerbuilder.addSchema(1801, V1801::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema73, "Added Illager Beast", References.ENTITY));
        Schema schema74 = datafixerbuilder.addSchema(1802, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(BlockRenameFix.create(schema74, "Rename sign blocks & stone slabs", createRenamer((Map) ImmutableMap.of("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign", "minecraft:wall_sign", "minecraft:oak_wall_sign"))));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema74, "Rename sign item & stone slabs", createRenamer((Map) ImmutableMap.of("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign"))));
        Schema schema75 = datafixerbuilder.addSchema(1803, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ItemLoreFix(schema75, false));
        Schema schema76 = datafixerbuilder.addSchema(1904, V1904::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema76, "Added Cats", References.ENTITY));
        datafixerbuilder.addFixer(new EntityCatSplitFix(schema76, false));
        Schema schema77 = datafixerbuilder.addSchema(1905, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ChunkStatusFix(schema77, false));
        Schema schema78 = datafixerbuilder.addSchema(1906, V1906::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema78, "Add POI Blocks", References.BLOCK_ENTITY));
        Schema schema79 = datafixerbuilder.addSchema(1909, V1909::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema79, "Add jigsaw", References.BLOCK_ENTITY));
        Schema schema80 = datafixerbuilder.addSchema(1911, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ChunkStatusFix2(schema80, false));
        Schema schema81 = datafixerbuilder.addSchema(1917, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new CatTypeFix(schema81, false));
        Schema schema82 = datafixerbuilder.addSchema(1918, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new VillagerDataFix(schema82, "minecraft:villager"));
        datafixerbuilder.addFixer(new VillagerDataFix(schema82, "minecraft:zombie_villager"));
        Schema schema83 = datafixerbuilder.addSchema(1920, V1920::new);

        datafixerbuilder.addFixer(new NewVillageFix(schema83, false));
        datafixerbuilder.addFixer(new AddNewChoices(schema83, "Add campfire", References.BLOCK_ENTITY));
        Schema schema84 = datafixerbuilder.addSchema(1925, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new MapIdFix(schema84, false));
        Schema schema85 = datafixerbuilder.addSchema(1928, V1928::new);

        datafixerbuilder.addFixer(new EntityRavagerRenameFix(schema85, true));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema85, "Rename ravager egg item", createRenamer(EntityRavagerRenameFix.RENAMED_IDS)));
        Schema schema86 = datafixerbuilder.addSchema(1929, V1929::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema86, "Add Wandering Trader and Trader Llama", References.ENTITY));
        Schema schema87 = datafixerbuilder.addSchema(1931, V1931::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema87, "Added Fox", References.ENTITY));
        Schema schema88 = datafixerbuilder.addSchema(1936, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new OptionsAddTextBackgroundFix(schema88, false));
        Schema schema89 = datafixerbuilder.addSchema(1946, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ReorganizePoi(schema89, false));
        Schema schema90 = datafixerbuilder.addSchema(1948, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new OminousBannerRenameFix(schema90, false));
        Schema schema91 = datafixerbuilder.addSchema(1953, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new OminousBannerBlockEntityRenameFix(schema91, false));
        Schema schema92 = datafixerbuilder.addSchema(1955, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new VillagerRebuildLevelAndXpFix(schema92, false));
        datafixerbuilder.addFixer(new ZombieVillagerRebuildXpFix(schema92, false));
        Schema schema93 = datafixerbuilder.addSchema(1961, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ChunkLightRemoveFix(schema93, false));
        Schema schema94 = datafixerbuilder.addSchema(2100, V2100::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema94, "Added Bee and Bee Stinger", References.ENTITY));
        datafixerbuilder.addFixer(new AddNewChoices(schema94, "Add beehive", References.BLOCK_ENTITY));
        datafixerbuilder.addFixer(new RecipesRenameFix(schema94, false, "Rename sugar recipe", createRenamer("minecraft:sugar", "sugar_from_sugar_cane")));
        datafixerbuilder.addFixer(new AdvancementsRenameFix(schema94, false, "Rename sugar recipe advancement", createRenamer("minecraft:recipes/misc/sugar", "minecraft:recipes/misc/sugar_from_sugar_cane")));
        Schema schema95 = datafixerbuilder.addSchema(2202, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ChunkBiomeFix(schema95, false));
        Schema schema96 = datafixerbuilder.addSchema(2209, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema96, "Rename bee_hive item to beehive", createRenamer("minecraft:bee_hive", "minecraft:beehive")));
        datafixerbuilder.addFixer(new BeehivePoiRenameFix(schema96));
        datafixerbuilder.addFixer(BlockRenameFix.create(schema96, "Rename bee_hive block to beehive", createRenamer("minecraft:bee_hive", "minecraft:beehive")));
        Schema schema97 = datafixerbuilder.addSchema(2211, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new StructureReferenceCountFix(schema97, false));
        Schema schema98 = datafixerbuilder.addSchema(2218, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new ForcePoiRebuild(schema98, false));
        Schema schema99 = datafixerbuilder.addSchema(2501, V2501::new);

        datafixerbuilder.addFixer(new FurnaceRecipeFix(schema99, true));
        Schema schema100 = datafixerbuilder.addSchema(2502, V2502::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema100, "Added Hoglin", References.ENTITY));
        Schema schema101 = datafixerbuilder.addSchema(2503, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new WallPropertyFix(schema101, false));
        datafixerbuilder.addFixer(new AdvancementsRenameFix(schema101, false, "Composter category change", createRenamer("minecraft:recipes/misc/composter", "minecraft:recipes/decorations/composter")));
        Schema schema102 = datafixerbuilder.addSchema(2505, V2505::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema102, "Added Piglin", References.ENTITY));
        datafixerbuilder.addFixer(new MemoryExpiryDataFix(schema102, "minecraft:villager"));
        Schema schema103 = datafixerbuilder.addSchema(2508, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema103, "Renamed fungi items to fungus", createRenamer((Map) ImmutableMap.of("minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus"))));
        datafixerbuilder.addFixer(BlockRenameFix.create(schema103, "Renamed fungi blocks to fungus", createRenamer((Map) ImmutableMap.of("minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus"))));
        Schema schema104 = datafixerbuilder.addSchema(2509, V2509::new);

        datafixerbuilder.addFixer(new EntityZombifiedPiglinRenameFix(schema104));
        datafixerbuilder.addFixer(ItemRenameFix.create(schema104, "Rename zombie pigman egg item", createRenamer(EntityZombifiedPiglinRenameFix.RENAMED_IDS)));
        Schema schema105 = datafixerbuilder.addSchema(2511, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new EntityProjectileOwnerFix(schema105));
        Schema schema106 = datafixerbuilder.addSchema(2514, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new EntityUUIDFix(schema106));
        datafixerbuilder.addFixer(new BlockEntityUUIDFix(schema106));
        datafixerbuilder.addFixer(new PlayerUUIDFix(schema106));
        datafixerbuilder.addFixer(new LevelUUIDFix(schema106));
        datafixerbuilder.addFixer(new SavedDataUUIDFix(schema106));
        datafixerbuilder.addFixer(new ItemStackUUIDFix(schema106));
        Schema schema107 = datafixerbuilder.addSchema(2516, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new GossipUUIDFix(schema107, "minecraft:villager"));
        datafixerbuilder.addFixer(new GossipUUIDFix(schema107, "minecraft:zombie_villager"));
        Schema schema108 = datafixerbuilder.addSchema(2518, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new JigsawPropertiesFix(schema108, false));
        datafixerbuilder.addFixer(new JigsawRotationFix(schema108, false));
        Schema schema109 = datafixerbuilder.addSchema(2519, V2519::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema109, "Added Strider", References.ENTITY));
        Schema schema110 = datafixerbuilder.addSchema(2522, V2522::new);

        datafixerbuilder.addFixer(new AddNewChoices(schema110, "Added Zoglin", References.ENTITY));
        Schema schema111 = datafixerbuilder.addSchema(2523, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new AttributesRename(schema111));
        Schema schema112 = datafixerbuilder.addSchema(2527, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new BitStorageAlignFix(schema112));
        Schema schema113 = datafixerbuilder.addSchema(2528, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(ItemRenameFix.create(schema113, "Rename soul fire torch and soul fire lantern", createRenamer((Map) ImmutableMap.of("minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        datafixerbuilder.addFixer(BlockRenameFix.create(schema113, "Rename soul fire torch and soul fire lantern", createRenamer((Map) ImmutableMap.of("minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_wall_torch", "minecraft:soul_wall_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        Schema schema114 = datafixerbuilder.addSchema(2529, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new StriderGravityFix(schema114, false));
        Schema schema115 = datafixerbuilder.addSchema(2531, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new RedstoneWireConnectionsFix(schema115));
        Schema schema116 = datafixerbuilder.addSchema(2533, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new VillagerFollowRangeFix(schema116));
        Schema schema117 = datafixerbuilder.addSchema(2535, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new EntityShulkerRotationFix(schema117));
        Schema schema118 = datafixerbuilder.addSchema(2550, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new WorldGenSettingsFix(schema118));
        Schema schema119 = datafixerbuilder.addSchema(2551, V2551::new);

        datafixerbuilder.addFixer(new WriteAndReadFix(schema119, "add types to WorldGenData", References.WORLD_GEN_SETTINGS));
        Schema schema120 = datafixerbuilder.addSchema(2552, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new RenameBiomesFix(schema120, false, "Nether biome rename", ImmutableMap.of("minecraft:nether", "minecraft:nether_wastes")));
        Schema schema121 = datafixerbuilder.addSchema(2553, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new BiomeFix(schema121, false));
        Schema schema122 = datafixerbuilder.addSchema(2558, DataFixers.SAME_NAMESPACED);

        datafixerbuilder.addFixer(new MissingDimensionFix(schema122, false));
        datafixerbuilder.addFixer(new OptionsRenameFieldFix(schema122, false, "Rename swapHands setting", "key_key.swapHands", "key_key.swapOffhand"));
    }

    private static UnaryOperator<String> createRenamer(Map<String, String> map) {
        return (s) -> {
            return (String) map.getOrDefault(s, s);
        };
    }

    private static UnaryOperator<String> createRenamer(String s, String s1) {
        return (s2) -> {
            return Objects.equals(s2, s) ? s1 : s2;
        };
    }
}
