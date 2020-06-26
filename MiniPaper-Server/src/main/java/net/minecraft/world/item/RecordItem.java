package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;

public class RecordItem extends Item {

    private static final Map<SoundEvent, RecordItem> BY_NAME = Maps.newHashMap();
    private final int analogOutput;
    private final SoundEvent sound;

    protected RecordItem(int i, SoundEvent soundeffect, Item.Info item_info) {
        super(item_info);
        this.analogOutput = i;
        this.sound = soundeffect;
        RecordItem.BY_NAME.put(this.sound, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockState iblockdata = world.getType(blockposition);

        if (iblockdata.is(Blocks.JUKEBOX) && !(Boolean) iblockdata.getValue(JukeboxBlock.HAS_RECORD)) {
            ItemStack itemstack = itemactioncontext.getItemInHand();

            if (!world.isClientSide) {
                if (true) return InteractionResult.SUCCESS; // CraftBukkit - handled in ItemStack
                ((JukeboxBlock) Blocks.JUKEBOX).setRecord((LevelAccessor) world, blockposition, iblockdata, itemstack);
                world.levelEvent((Player) null, 1010, blockposition, Item.getId(this));
                itemstack.shrink(1);
                Player entityhuman = itemactioncontext.getPlayer();

                if (entityhuman != null) {
                    entityhuman.awardStat(Stats.PLAY_RECORD);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public int getAnalogOutput() {
        return this.analogOutput;
    }
}
