package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class CommandBlock extends BaseEntityBlock {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty CONDITIONAL = BlockStateProperties.CONDITIONAL;

    public CommandBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CommandBlock.FACING, Direction.NORTH)).setValue(CommandBlock.CONDITIONAL, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        CommandBlockEntity tileentitycommand = new CommandBlockEntity();

        tileentitycommand.setAutomatic(this == Blocks.CHAIN_COMMAND_BLOCK);
        return tileentitycommand;
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (!world.isClientSide) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof CommandBlockEntity) {
                CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;
                boolean flag1 = world.hasNeighborSignal(blockposition);
                boolean flag2 = tileentitycommand.isPowered();
                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                int old = flag2 ? 15 : 0;
                int current = flag1 ? 15 : 0;

                BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bukkitBlock, old, current);
                world.getServerOH().getPluginManager().callEvent(eventRedstone);
                flag1 = eventRedstone.getNewCurrent() > 0;
                // CraftBukkit end

                tileentitycommand.setPowered(flag1);
                if (!flag2 && !tileentitycommand.isAutomatic() && tileentitycommand.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                    if (flag1) {
                        tileentitycommand.markConditionMet();
                        world.getBlockTickList().scheduleTick(blockposition, this, 1);
                    }

                }
            }
        }
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        BlockEntity tileentity = worldserver.getBlockEntity(blockposition);

        if (tileentity instanceof CommandBlockEntity) {
            CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;
            BaseCommandBlock commandblocklistenerabstract = tileentitycommand.getCommandBlock();
            boolean flag = !StringUtil.isNullOrEmpty(commandblocklistenerabstract.getCommand());
            CommandBlockEntity.Mode tileentitycommand_type = tileentitycommand.getMode();
            boolean flag1 = tileentitycommand.wasConditionMet();

            if (tileentitycommand_type == CommandBlockEntity.Mode.AUTO) {
                tileentitycommand.markConditionMet();
                if (flag1) {
                    this.execute(iblockdata, worldserver, blockposition, commandblocklistenerabstract, flag);
                } else if (tileentitycommand.isConditional()) {
                    commandblocklistenerabstract.setSuccessCount(0);
                }

                if (tileentitycommand.isPowered() || tileentitycommand.isAutomatic()) {
                    worldserver.getBlockTickList().scheduleTick(blockposition, this, 1);
                }
            } else if (tileentitycommand_type == CommandBlockEntity.Mode.REDSTONE) {
                if (flag1) {
                    this.execute(iblockdata, worldserver, blockposition, commandblocklistenerabstract, flag);
                } else if (tileentitycommand.isConditional()) {
                    commandblocklistenerabstract.setSuccessCount(0);
                }
            }

            worldserver.updateNeighbourForOutputSignal(blockposition, this);
        }

    }

    private void execute(BlockState iblockdata, Level world, BlockPos blockposition, BaseCommandBlock commandblocklistenerabstract, boolean flag) {
        if (flag) {
            commandblocklistenerabstract.performCommand(world);
        } else {
            commandblocklistenerabstract.setSuccessCount(0);
        }

        executeChain(world, blockposition, (Direction) iblockdata.getValue(CommandBlock.FACING));
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof CommandBlockEntity && entityhuman.canUseGameMasterBlocks()) {
            entityhuman.openCommandBlock((CommandBlockEntity) tileentity);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        return tileentity instanceof CommandBlockEntity ? ((CommandBlockEntity) tileentity).getCommandBlock().getSuccessCount() : 0;
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof CommandBlockEntity) {
            CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;
            BaseCommandBlock commandblocklistenerabstract = tileentitycommand.getCommandBlock();

            if (itemstack.hasCustomHoverName()) {
                commandblocklistenerabstract.setName(itemstack.getHoverName());
            }

            if (!world.isClientSide) {
                if (itemstack.getTagElement("BlockEntityTag") == null) {
                    commandblocklistenerabstract.setTrackOutput(world.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK));
                    tileentitycommand.setAutomatic(this == Blocks.CHAIN_COMMAND_BLOCK);
                }

                if (tileentitycommand.getMode() == CommandBlockEntity.Mode.SEQUENCE) {
                    boolean flag = world.hasNeighborSignal(blockposition);

                    tileentitycommand.setPowered(flag);
                }
            }

        }
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(CommandBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(CommandBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(CommandBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CommandBlock.FACING, CommandBlock.CONDITIONAL);
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(CommandBlock.FACING, blockactioncontext.getNearestLookingDirection().getOpposite());
    }

    private static void executeChain(Level world, BlockPos blockposition, Direction enumdirection) {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = blockposition.i();
        GameRules gamerules = world.getGameRules();

        BlockState iblockdata;
        int i;

        for (i = gamerules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH); i-- > 0; enumdirection = (Direction) iblockdata.getValue(CommandBlock.FACING)) {
            blockposition_mutableblockposition.c(enumdirection);
            iblockdata = world.getType(blockposition_mutableblockposition);
            Block block = iblockdata.getBlock();

            if (!iblockdata.is(Blocks.CHAIN_COMMAND_BLOCK)) {
                break;
            }

            BlockEntity tileentity = world.getBlockEntity(blockposition_mutableblockposition);

            if (!(tileentity instanceof CommandBlockEntity)) {
                break;
            }

            CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;

            if (tileentitycommand.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                break;
            }

            if (tileentitycommand.isPowered() || tileentitycommand.isAutomatic()) {
                BaseCommandBlock commandblocklistenerabstract = tileentitycommand.getCommandBlock();

                if (tileentitycommand.markConditionMet()) {
                    if (!commandblocklistenerabstract.performCommand(world)) {
                        break;
                    }

                    world.updateNeighbourForOutputSignal(blockposition_mutableblockposition, block);
                } else if (tileentitycommand.isConditional()) {
                    commandblocklistenerabstract.setSuccessCount(0);
                }
            }
        }

        if (i <= 0) {
            int j = Math.max(gamerules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH), 0);

            CommandBlock.LOGGER.warn("Command Block chain tried to execute more than {} steps!", j);
        }

    }
}
