package net.minecraft.world.item;

import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockCanBuildEvent;
// CraftBukkit end

public class BlockItem extends Item {

    @Deprecated
    private final Block block;

    public BlockItem(Block block, Item.Info item_info) {
        super(item_info);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        InteractionResult enuminteractionresult = this.place(new BlockPlaceContext(itemactioncontext));

        return !enuminteractionresult.consumesAction() && this.isEdible() ? this.use(itemactioncontext.level, itemactioncontext.player, itemactioncontext.hand).getResult() : enuminteractionresult;
    }

    public InteractionResult place(BlockPlaceContext blockactioncontext) {
        if (!blockactioncontext.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockactioncontext1 = this.updatePlacementContext(blockactioncontext);

            if (blockactioncontext1 == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState iblockdata = this.getPlacementState(blockactioncontext1);

                if (iblockdata == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockactioncontext1, iblockdata)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos blockposition = blockactioncontext1.getClickedPos();
                    Level world = blockactioncontext1.getLevel();
                    Player entityhuman = blockactioncontext1.getPlayer();
                    ItemStack itemstack = blockactioncontext1.getItemInHand();
                    BlockState iblockdata1 = world.getType(blockposition);
                    Block block = iblockdata1.getBlock();

                    if (block == iblockdata.getBlock()) {
                        iblockdata1 = this.updateBlockStateFromTag(blockposition, world, itemstack, iblockdata1);
                        this.updateCustomBlockEntityTag(blockposition, world, entityhuman, itemstack, iblockdata1);
                        block.postPlace(world, blockposition, iblockdata1, entityhuman, itemstack);
                        if (entityhuman instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) entityhuman, blockposition, itemstack);
                        }
                    }

                    SoundType soundeffecttype = iblockdata1.getStepSound();

                    // world.playSound(entityhuman, blockposition, this.a(iblockdata1), SoundCategory.BLOCKS, (soundeffecttype.a() + 1.0F) / 2.0F, soundeffecttype.b() * 0.8F);
                    if (entityhuman == null || !entityhuman.abilities.instabuild) {
                        itemstack.shrink(1);
                    }

                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            }
        }
    }

    protected SoundEvent getPlaceSound(BlockState iblockdata) {
        return iblockdata.getStepSound().getPlaceSound();
    }

    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext blockactioncontext) {
        return blockactioncontext;
    }

    protected boolean updateCustomBlockEntityTag(BlockPos blockposition, Level world, @Nullable Player entityhuman, ItemStack itemstack, BlockState iblockdata) {
        return updateCustomBlockEntityTag(world, entityhuman, blockposition, itemstack);
    }

    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = this.getBlock().getPlacedState(blockactioncontext);

        return iblockdata != null && this.canPlace(blockactioncontext, iblockdata) ? iblockdata : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos blockposition, Level world, ItemStack itemstack, BlockState iblockdata) {
        BlockState iblockdata1 = iblockdata;
        CompoundTag nbttagcompound = itemstack.getTag();

        if (nbttagcompound != null) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("BlockStateTag");
            // CraftBukkit start
            iblockdata1 = getBlockState(iblockdata1, nbttagcompound1);
        }

        if (iblockdata1 != iblockdata) {
            world.setTypeAndData(blockposition, iblockdata1, 2);
        }

        return iblockdata1;
    }

    public static BlockState getBlockState(BlockState iblockdata, CompoundTag nbttagcompound1) {
        BlockState iblockdata1 = iblockdata;
        {
            // CraftBukkit end
            StateDefinition<Block, BlockState> blockstatelist = iblockdata.getBlock().getStateDefinition();
            Iterator iterator = nbttagcompound1.getAllKeys().iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                Property<?> iblockstate = blockstatelist.getProperty(s);

                if (iblockstate != null) {
                    String s1 = nbttagcompound1.get(s).getAsString();

                    iblockdata1 = updateState(iblockdata1, iblockstate, s1);
                }
            }
        }
        return iblockdata1;
    }

    private static <T extends Comparable<T>> BlockState updateState(BlockState iblockdata, Property<T> iblockstate, String s) {
        return (BlockState) iblockstate.getValue(s).map((comparable) -> {
            return (BlockState) iblockdata.setValue(iblockstate, comparable);
        }).orElse(iblockdata);
    }

    protected boolean canPlace(BlockPlaceContext blockactioncontext, BlockState iblockdata) {
        Player entityhuman = blockactioncontext.getPlayer();
        CollisionContext voxelshapecollision = entityhuman == null ? CollisionContext.empty() : CollisionContext.of((Entity) entityhuman);
        // CraftBukkit start - store default return
        boolean defaultReturn = (!this.mustSurvive() || iblockdata.canSurvive(blockactioncontext.getLevel(), blockactioncontext.getClickedPos())) && blockactioncontext.getLevel().isUnobstructed(iblockdata, blockactioncontext.getClickedPos(), voxelshapecollision);
        org.bukkit.entity.Player player = (blockactioncontext.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) blockactioncontext.getPlayer().getBukkitEntity() : null;

        BlockCanBuildEvent event = new BlockCanBuildEvent(CraftBlock.at(blockactioncontext.getLevel(), blockactioncontext.getClickedPos()), player, CraftBlockData.fromData(iblockdata), defaultReturn);
        blockactioncontext.getLevel().getServerOH().getPluginManager().callEvent(event);

        return event.isBuildable();
        // CraftBukkit end
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext blockactioncontext, BlockState iblockdata) {
        return blockactioncontext.getLevel().setTypeAndData(blockactioncontext.getClickedPos(), iblockdata, 11);
    }

    public static boolean updateCustomBlockEntityTag(Level world, @Nullable Player entityhuman, BlockPos blockposition, ItemStack itemstack) {
        MinecraftServer minecraftserver = world.getServer();

        if (minecraftserver == null) {
            return false;
        } else {
            CompoundTag nbttagcompound = itemstack.getTagElement("BlockEntityTag");

            if (nbttagcompound != null) {
                BlockEntity tileentity = world.getBlockEntity(blockposition);

                if (tileentity != null) {
                    if (!world.isClientSide && tileentity.onlyOpCanSetNbt() && (entityhuman == null || !(entityhuman.canUseGameMasterBlocks() || (entityhuman.abilities.instabuild && entityhuman.getBukkitEntity().hasPermission("minecraft.nbt.place"))))) { // Spigot - add permission
                        return false;
                    }

                    CompoundTag nbttagcompound1 = tileentity.save(new CompoundTag());
                    CompoundTag nbttagcompound2 = nbttagcompound1.copy();

                    nbttagcompound1.merge(nbttagcompound);
                    nbttagcompound1.putInt("x", blockposition.getX());
                    nbttagcompound1.putInt("y", blockposition.getY());
                    nbttagcompound1.putInt("z", blockposition.getZ());
                    if (!nbttagcompound1.equals(nbttagcompound2)) {
                        tileentity.load(world.getType(blockposition), nbttagcompound1);
                        tileentity.setChanged();
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    public String getDescriptionId() {
        return this.getBlock().getDescriptionId();
    }

    @Override
    public void fillItemCategory(CreativeModeTab creativemodetab, NonNullList<ItemStack> nonnulllist) {
        if (this.allowdedIn(creativemodetab)) {
            this.getBlock().fillItemCategory(creativemodetab, nonnulllist);
        }

    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> map, Item item) {
        map.put(this.getBlock(), item);
    }
}
