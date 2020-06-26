package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

public class NoteBlock extends Block {

    public static final EnumProperty<NoteBlockInstrument> INSTRUMENT = BlockStateProperties.NOTEBLOCK_INSTRUMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty NOTE = BlockStateProperties.NOTE;

    public NoteBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.HARP)).setValue(NoteBlock.NOTE, 0)).setValue(NoteBlock.POWERED, false));
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.byState(blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos().below())));
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection == Direction.DOWN ? (BlockState) iblockdata.setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.byState(iblockdata1)) : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        boolean flag1 = world.hasNeighborSignal(blockposition);

        if (flag1 != (Boolean) iblockdata.getValue(NoteBlock.POWERED)) {
            if (flag1) {
                this.play(world, blockposition, iblockdata); // CraftBukkit
                iblockdata = world.getType(blockposition); // CraftBukkit - SPIGOT-5617: update in case changed in event
            }

            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(NoteBlock.POWERED, flag1), 3);
        }

    }

    private void play(Level world, BlockPos blockposition, BlockState data) { // CraftBukkit
        if (world.getType(blockposition.above()).isAir()) {
            // CraftBukkit start
            org.bukkit.event.block.NotePlayEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callNotePlayEvent(world, blockposition, data.getValue(NoteBlock.INSTRUMENT), data.getValue(NoteBlock.NOTE));
            if (!event.isCancelled()) {
                world.blockEvent(blockposition, this, 0, 0);
            }
            // CraftBukkit end
        }

    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            iblockdata = (BlockState) iblockdata.cycle((Property) NoteBlock.NOTE);
            world.setTypeAndData(blockposition, iblockdata, 3);
            this.play(world, blockposition, iblockdata); // CraftBukkit
            entityhuman.awardStat(Stats.TUNE_NOTEBLOCK);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void attack(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman) {
        if (!world.isClientSide) {
            this.play(world, blockposition, iblockdata); // CraftBukkit
            entityhuman.awardStat(Stats.PLAY_NOTEBLOCK);
        }
    }

    @Override
    public boolean triggerEvent(BlockState iblockdata, Level world, BlockPos blockposition, int i, int j) {
        int k = (Integer) iblockdata.getValue(NoteBlock.NOTE);
        float f = (float) Math.pow(2.0D, (double) (k - 12) / 12.0D);

        world.playSound((Player) null, blockposition, ((NoteBlockInstrument) iblockdata.getValue(NoteBlock.INSTRUMENT)).getSoundEvent(), SoundSource.RECORDS, 3.0F, f);
        world.addParticle(ParticleTypes.NOTE, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 1.2D, (double) blockposition.getZ() + 0.5D, (double) k / 24.0D, 0.0D, 0.0D);
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(NoteBlock.INSTRUMENT, NoteBlock.POWERED, NoteBlock.NOTE);
    }
}
