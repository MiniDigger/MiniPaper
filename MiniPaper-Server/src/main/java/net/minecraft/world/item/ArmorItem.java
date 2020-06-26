package net.minecraft.world.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseArmorEvent;
// CraftBukkit end

public class ArmorItem extends Item implements Wearable {

    private static final UUID[] ARMOR_MODIFIER_UUID_PER_SLOT = new UUID[]{UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B"), UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D"), UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E"), UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150")};
    public static final DispenseItemBehavior DISPENSE_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
            return ArmorItem.dispenseArmor(isourceblock, itemstack) ? itemstack : super.execute(isourceblock, itemstack);
        }
    };
    protected final EquipmentSlot slot;
    private final int defense;
    private final float toughness;
    protected final float knockbackResistance;
    protected final ArmorMaterial material;
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public static boolean dispenseArmor(BlockSource isourceblock, ItemStack itemstack) {
        BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
        List<LivingEntity> list = isourceblock.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(blockposition), EntitySelector.NO_SPECTATORS.and(new EntitySelector.EntitySelectorEquipable(itemstack)));

        if (list.isEmpty()) {
            return false;
        } else {
            LivingEntity entityliving = (LivingEntity) list.get(0);
            EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);
            ItemStack itemstack1 = itemstack.split(1);
            // CraftBukkit start
            Level world = isourceblock.getLevel();
            org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

            BlockDispenseArmorEvent event = new BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) entityliving.getBukkitEntity());
            if (!DispenserBlock.eventFired) {
                world.getServerOH().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                itemstack.grow(1);
                return false;
            }

            if (!event.getItem().equals(craftItem)) {
                itemstack.grow(1);
                // Chain to handler for new item
                ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != ArmorItem.DISPENSE_ITEM_BEHAVIOR) {
                    idispensebehavior.dispense(isourceblock, eventStack);
                    return true;
                }
            }
            // CraftBukkit end

            entityliving.setItemSlot(enumitemslot, itemstack1);
            if (entityliving instanceof Mob) {
                ((Mob) entityliving).setDropChance(enumitemslot, 2.0F);
                ((Mob) entityliving).setPersistenceRequired();
            }

            return true;
        }
    }

    public ArmorItem(ArmorMaterial armormaterial, EquipmentSlot enumitemslot, Item.Info item_info) {
        super(item_info.b(armormaterial.getDurabilityForSlot(enumitemslot)));
        this.material = armormaterial;
        this.slot = enumitemslot;
        this.defense = armormaterial.getDefenseForSlot(enumitemslot);
        this.toughness = armormaterial.getToughness();
        this.knockbackResistance = armormaterial.getKnockbackResistance();
        DispenserBlock.registerBehavior((ItemLike) this, ArmorItem.DISPENSE_ITEM_BEHAVIOR);
        Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        UUID uuid = ArmorItem.ARMOR_MODIFIER_UUID_PER_SLOT[enumitemslot.getIndex()];

        builder.put(Attributes.ARMOR, new AttributeModifier(uuid, "Armor modifier", (double) this.defense, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(uuid, "Armor toughness", (double) this.toughness, AttributeModifier.Operation.ADDITION));
        if (armormaterial == ArmorMaterials.NETHERITE) {
            builder.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(uuid, "Armor knockback resistance", (double) this.knockbackResistance, AttributeModifier.Operation.ADDITION));
        }

        this.defaultModifiers = builder.build();
    }

    public EquipmentSlot getSlot() {
        return this.slot;
    }

    @Override
    public int getEnchantmentValue() {
        return this.material.getEnchantmentValue();
    }

    public ArmorMaterial getMaterial() {
        return this.material;
    }

    @Override
    public boolean isValidRepairItem(ItemStack itemstack, ItemStack itemstack1) {
        return this.material.getRepairIngredient().test(itemstack1) || super.isValidRepairItem(itemstack, itemstack1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);
        ItemStack itemstack1 = entityhuman.getItemBySlot(enumitemslot);

        if (itemstack1.isEmpty()) {
            entityhuman.setItemSlot(enumitemslot, itemstack.copy());
            itemstack.setCount(0);
            return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
        } else {
            return InteractionResultHolder.fail(itemstack);
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot enumitemslot) {
        return enumitemslot == this.slot ? this.defaultModifiers : super.getDefaultAttributeModifiers(enumitemslot);
    }

    public int getDefense() {
        return this.defense;
    }

    public float getToughness() {
        return this.toughness;
    }
}
