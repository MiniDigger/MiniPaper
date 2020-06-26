package net.minecraft.world.inventory;

import net.minecraft.core.Registry;
import net.minecraft.world.entity.player.Inventory;

public class MenuType<T extends AbstractContainerMenu> {

    public static final MenuType<ChestMenu> GENERIC_9x1 = a("generic_9x1", ChestMenu::oneRow);
    public static final MenuType<ChestMenu> GENERIC_9x2 = a("generic_9x2", ChestMenu::twoRows);
    public static final MenuType<ChestMenu> GENERIC_9x3 = a("generic_9x3", ChestMenu::threeRows);
    public static final MenuType<ChestMenu> GENERIC_9x4 = a("generic_9x4", ChestMenu::fourRows);
    public static final MenuType<ChestMenu> GENERIC_9x5 = a("generic_9x5", ChestMenu::fiveRows);
    public static final MenuType<ChestMenu> GENERIC_9x6 = a("generic_9x6", ChestMenu::sixRows);
    public static final MenuType<DispenserMenu> GENERIC_3x3 = a("generic_3x3", DispenserMenu::new);
    public static final MenuType<AnvilMenu> ANVIL = a("anvil", AnvilMenu::new);
    public static final MenuType<BeaconMenu> BEACON = a("beacon", BeaconMenu::new);
    public static final MenuType<BlastFurnaceMenu> BLAST_FURNACE = a("blast_furnace", BlastFurnaceMenu::new);
    public static final MenuType<BrewingStandMenu> BREWING_STAND = a("brewing_stand", BrewingStandMenu::new);
    public static final MenuType<CraftingMenu> CRAFTING = a("crafting", CraftingMenu::new);
    public static final MenuType<EnchantmentMenu> ENCHANTMENT = a("enchantment", EnchantmentMenu::new);
    public static final MenuType<FurnaceMenu> FURNACE = a("furnace", FurnaceMenu::new);
    public static final MenuType<GrindstoneMenu> GRINDSTONE = a("grindstone", GrindstoneMenu::new);
    public static final MenuType<HopperMenu> HOPPER = a("hopper", HopperMenu::new);
    public static final MenuType<LecternMenu> LECTERN = a("lectern", (i, playerinventory) -> {
        return new LecternMenu(i, playerinventory); // CraftBukkit
    });
    public static final MenuType<LoomMenu> LOOM = a("loom", LoomMenu::new);
    public static final MenuType<MerchantMenu> MERCHANT = a("merchant", MerchantMenu::new);
    public static final MenuType<ShulkerBoxMenu> SHULKER_BOX = a("shulker_box", ShulkerBoxMenu::new);
    public static final MenuType<SmithingMenu> SMITHING = a("smithing", SmithingMenu::new);
    public static final MenuType<SmokerMenu> SMOKER = a("smoker", SmokerMenu::new);
    public static final MenuType<CartographyTableMenu> CARTOGRAPHY_TABLE = a("cartography_table", CartographyTableMenu::new);
    public static final MenuType<StonecutterMenu> STONECUTTER = a("stonecutter", StonecutterMenu::new);
    private final MenuType.Supplier<T> constructor;

    private static <T extends AbstractContainerMenu> MenuType<T> a(String s, MenuType.Supplier<T> containers_supplier) {
        return (MenuType) Registry.register(Registry.MENU, s, (new MenuType<>(containers_supplier))); // CraftBukkit - decompile error
    }

    private MenuType(MenuType.Supplier<T> containers_supplier) {
        this.constructor = containers_supplier;
    }

    // CraftBukkit start
    interface Supplier<T extends AbstractContainerMenu> {

        T supply(int id, Inventory playerinventory);
    }
    // CraftBukkit end
}
