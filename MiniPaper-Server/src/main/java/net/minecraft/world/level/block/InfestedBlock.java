package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason; // CraftBukkit

public class InfestedBlock extends Block {

    private final Block hostBlock;
    private static final Map<Block, Block> BLOCK_BY_HOST_BLOCK = Maps.newIdentityHashMap();

    public InfestedBlock(Block block, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.hostBlock = block;
        InfestedBlock.BLOCK_BY_HOST_BLOCK.put(block, this);
    }

    public Block getHostBlock() {
        return this.hostBlock;
    }

    public static boolean isCompatibleHostBlock(BlockState iblockdata) {
        return InfestedBlock.BLOCK_BY_HOST_BLOCK.containsKey(iblockdata.getBlock());
    }

    private void spawnInfestation(Level world, BlockPos blockposition) {
        Silverfish entitysilverfish = (Silverfish) EntityType.SILVERFISH.create(world);

        entitysilverfish.moveTo((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, 0.0F, 0.0F);
        world.addEntity(entitysilverfish, SpawnReason.SILVERFISH_BLOCK); // CraftBukkit - add SpawnReason
        entitysilverfish.spawnAnim();
    }

    @Override
    public void dropNaturally(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        super.dropNaturally(iblockdata, world, blockposition, itemstack);
        if (!world.isClientSide && world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            this.spawnInfestation(world, blockposition);
        }

    }

    @Override
    public void wasExploded(Level world, BlockPos blockposition, Explosion explosion) {
        if (!world.isClientSide) {
            this.spawnInfestation(world, blockposition);
        }

    }

    public static BlockState stateByHostBlock(Block block) {
        return ((Block) InfestedBlock.BLOCK_BY_HOST_BLOCK.get(block)).getBlockData();
    }
}
