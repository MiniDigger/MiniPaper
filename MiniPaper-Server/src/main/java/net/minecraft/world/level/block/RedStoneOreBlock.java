package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class RedStoneOreBlock extends Block {

    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    public RedStoneOreBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) this.getBlockData().setValue(RedStoneOreBlock.LIT, false));
    }

    @Override
    public void attack(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman) {
        interact(iblockdata, world, blockposition, entityhuman); // CraftBukkit - add entityhuman
        super.attack(iblockdata, world, blockposition, entityhuman);
    }

    @Override
    public void stepOn(Level world, BlockPos blockposition, Entity entity) {
        // CraftBukkit start
        // interact(world.getType(blockposition), world, blockposition);
        // super.stepOn(world, blockposition, entity);
        if (entity instanceof Player) {
            org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
            if (!event.isCancelled()) {
                interact(world.getType(blockposition), world, blockposition, entity); // add entity
                super.stepOn(world, blockposition, entity);
            }
        } else {
            EntityInteractEvent event = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
            world.getServerOH().getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                interact(world.getType(blockposition), world, blockposition, entity); // add entity
                super.stepOn(world, blockposition, entity);
            }
        }
        // CraftBukkit end
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (world.isClientSide) {
            spawnParticles(world, blockposition);
        } else {
            interact(iblockdata, world, blockposition, entityhuman); // CraftBukkit - add entityhuman
        }

        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        return itemstack.getItem() instanceof BlockItem && (new BlockPlaceContext(entityhuman, enumhand, itemstack, movingobjectpositionblock)).canPlace() ? InteractionResult.PASS : InteractionResult.SUCCESS;
    }

    private static void interact(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) { // CraftBukkit - add Entity
        spawnParticles(world, blockposition);
        if (!(Boolean) iblockdata.getValue(RedStoneOreBlock.LIT)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, iblockdata.setValue(RedStoneOreBlock.LIT, true)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(RedStoneOreBlock.LIT, true), 3);
        }

    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(RedStoneOreBlock.LIT);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Boolean) iblockdata.getValue(RedStoneOreBlock.LIT)) {
            // CraftBukkit start
            if (CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, iblockdata.setValue(RedStoneOreBlock.LIT, false)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(RedStoneOreBlock.LIT, false), 3);
        }

    }

    @Override
    public void dropNaturally(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        super.dropNaturally(iblockdata, world, blockposition, itemstack);
        /* CraftBukkit start - Delegated to getExpDrop
        if (EnchantmentManager.getEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            int i = 1 + world.random.nextInt(5);

            this.dropExperience(world, blockposition, i);
        }
        // */

    }

    @Override
    public int getExpDrop(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            int i = 1 + world.random.nextInt(5);

            return i;
        }
        return 0;
        // CraftBukkit end
    }

    private static void spawnParticles(Level world, BlockPos blockposition) {
        double d0 = 0.5625D;
        Random random = world.random;
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (!world.getType(blockposition1).isSolidRender(world, blockposition1)) {
                Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
                double d1 = enumdirection_enumaxis == Direction.Axis.X ? 0.5D + 0.5625D * (double) enumdirection.getStepX() : (double) random.nextFloat();
                double d2 = enumdirection_enumaxis == Direction.Axis.Y ? 0.5D + 0.5625D * (double) enumdirection.getStepY() : (double) random.nextFloat();
                double d3 = enumdirection_enumaxis == Direction.Axis.Z ? 0.5D + 0.5625D * (double) enumdirection.getStepZ() : (double) random.nextFloat();

                world.addParticle(DustParticleOptions.REDSTONE, (double) blockposition.getX() + d1, (double) blockposition.getY() + d2, (double) blockposition.getZ() + d3, 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(RedStoneOreBlock.LIT);
    }
}
