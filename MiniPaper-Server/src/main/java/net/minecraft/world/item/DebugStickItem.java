package net.minecraft.world.item;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class DebugStickItem extends Item {

    public DebugStickItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public boolean isFoil(ItemStack itemstack) {
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman) {
        if (!world.isClientSide) {
            this.handleInteraction(entityhuman, iblockdata, world, blockposition, false, entityhuman.getItemInHand(InteractionHand.MAIN_HAND));
        }

        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Player entityhuman = itemactioncontext.getPlayer();
        Level world = itemactioncontext.getLevel();

        if (!world.isClientSide && entityhuman != null) {
            BlockPos blockposition = itemactioncontext.getClickedPos();

            this.handleInteraction(entityhuman, world.getType(blockposition), world, blockposition, true, itemactioncontext.getItemInHand());
        }

        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    private void handleInteraction(Player entityhuman, BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, boolean flag, ItemStack itemstack) {
        if (entityhuman.canUseGameMasterBlocks() || (entityhuman.abilities.instabuild && entityhuman.getBukkitEntity().hasPermission("minecraft.debugstick")) || entityhuman.getBukkitEntity().hasPermission("minecraft.debugstick.always")) { // Spigot
            Block block = iblockdata.getBlock();
            StateDefinition<Block, BlockState> blockstatelist = block.getStateDefinition();
            Collection<Property<?>> collection = blockstatelist.getProperties();
            String s = Registry.BLOCK.getKey(block).toString();

            if (collection.isEmpty()) {
                message(entityhuman, (Component) (new TranslatableComponent(this.getDescriptionId() + ".empty", new Object[]{s})));
            } else {
                CompoundTag nbttagcompound = itemstack.getOrCreateTagElement("DebugProperty");
                String s1 = nbttagcompound.getString(s);
                Property<?> iblockstate = blockstatelist.getProperty(s1);

                if (flag) {
                    if (iblockstate == null) {
                        iblockstate = (Property) collection.iterator().next();
                    }

                    BlockState iblockdata1 = cycleState(iblockdata, iblockstate, entityhuman.isSecondaryUseActive());

                    generatoraccess.setTypeAndData(blockposition, iblockdata1, 18);
                    message(entityhuman, (Component) (new TranslatableComponent(this.getDescriptionId() + ".update", new Object[]{iblockstate.getName(), getNameHelper(iblockdata1, iblockstate)})));
                } else {
                    iblockstate = (Property) getRelative((Iterable) collection, (Object) iblockstate, entityhuman.isSecondaryUseActive());
                    String s2 = iblockstate.getName();

                    nbttagcompound.putString(s, s2);
                    message(entityhuman, (Component) (new TranslatableComponent(this.getDescriptionId() + ".select", new Object[]{s2, getNameHelper(iblockdata, iblockstate)})));
                }

            }
        }
    }

    private static <T extends Comparable<T>> BlockState cycleState(BlockState iblockdata, Property<T> iblockstate, boolean flag) {
        return (BlockState) iblockdata.setValue(iblockstate, getRelative(iblockstate.getPossibleValues(), iblockdata.getValue(iblockstate), flag));
    }

    private static <T> T getRelative(Iterable<T> iterable, @Nullable T t0, boolean flag) {
        return flag ? Util.findPreviousInIterable(iterable, t0) : Util.findNextInIterable(iterable, t0);
    }

    private static void message(Player entityhuman, Component ichatbasecomponent) {
        ((ServerPlayer) entityhuman).sendMessage(ichatbasecomponent, ChatType.GAME_INFO, Util.NIL_UUID);
    }

    private static <T extends Comparable<T>> String getNameHelper(BlockState iblockdata, Property<T> iblockstate) {
        return iblockstate.getName(iblockdata.getValue(iblockstate));
    }
}
