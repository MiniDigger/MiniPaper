package net.minecraft.world.item.enchantment;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
// CraftBukkit start
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.event.block.EntityBlockFormEvent;
// CraftBukkit end

public class FrostWalkerEnchantment extends Enchantment {

    public FrostWalkerEnchantment(Enchantment.Rarity enchantment_rarity, EquipmentSlot... aenumitemslot) {
        super(enchantment_rarity, EnchantmentCategory.ARMOR_FEET, aenumitemslot);
    }

    @Override
    public int getMinCost(int i) {
        return i * 10;
    }

    @Override
    public int getMaxCost(int i) {
        return this.getMinCost(i) + 15;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    public static void onEntityMoved(LivingEntity entityliving, Level world, BlockPos blockposition, int i) {
        if (entityliving.isOnGround()) {
            BlockState iblockdata = Blocks.FROSTED_ICE.getBlockData();
            float f = (float) Math.min(16, 2 + i);
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
            Iterator iterator = BlockPos.betweenClosed(blockposition.offset((double) (-f), -1.0D, (double) (-f)), blockposition.offset((double) f, -1.0D, (double) f)).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition1 = (BlockPos) iterator.next();

                if (blockposition1.closerThan((Position) entityliving.position(), (double) f)) {
                    blockposition_mutableblockposition.d(blockposition1.getX(), blockposition1.getY() + 1, blockposition1.getZ());
                    BlockState iblockdata1 = world.getType(blockposition_mutableblockposition);

                    if (iblockdata1.isAir()) {
                        BlockState iblockdata2 = world.getType(blockposition1);

                        if (iblockdata2.getMaterial() == Material.WATER && (Integer) iblockdata2.getValue(LiquidBlock.LEVEL) == 0 && iblockdata.canSurvive(world, blockposition1) && world.isUnobstructed(iblockdata, blockposition1, CollisionContext.empty())) {
                            // CraftBukkit Start - Call EntityBlockFormEvent for Frost Walker
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition1, iblockdata, entityliving)) {
                                world.getBlockTickList().scheduleTick(blockposition1, Blocks.FROSTED_ICE, Mth.nextInt(entityliving.getRandom(), 60, 120));
                            }
                            // CraftBukkit End
                        }
                    }
                }
            }

        }
    }

    @Override
    public boolean checkCompatibility(Enchantment enchantment) {
        return super.checkCompatibility(enchantment) && enchantment != Enchantments.DEPTH_STRIDER;
    }
}
