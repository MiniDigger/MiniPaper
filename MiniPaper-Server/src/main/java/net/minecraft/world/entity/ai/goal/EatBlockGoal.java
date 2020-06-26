package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EatBlockGoal extends Goal {

    private static final Predicate<BlockState> IS_TALL_GRASS = BlockStatePredicate.forBlock(Blocks.GRASS);
    private final Mob mob;
    private final Level level;
    private int eatAnimationTick;

    public EatBlockGoal(Mob entityinsentient) {
        this.mob = entityinsentient;
        this.level = entityinsentient.level;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getRandom().nextInt(this.mob.isBaby() ? 50 : 1000) != 0) {
            return false;
        } else {
            BlockPos blockposition = this.mob.blockPosition();

            return EatBlockGoal.IS_TALL_GRASS.test(this.level.getType(blockposition)) ? true : this.level.getType(blockposition.below()).is(Blocks.GRASS_BLOCK);
        }
    }

    @Override
    public void start() {
        this.eatAnimationTick = 40;
        this.level.broadcastEntityEvent(this.mob, (byte) 10);
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.eatAnimationTick = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.eatAnimationTick > 0;
    }

    public int getEatAnimationTick() {
        return this.eatAnimationTick;
    }

    @Override
    public void tick() {
        this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
        if (this.eatAnimationTick == 4) {
            BlockPos blockposition = this.mob.blockPosition();

            if (EatBlockGoal.IS_TALL_GRASS.test(this.level.getType(blockposition))) {
                // CraftBukkit
                if (!CraftEventFactory.callEntityChangeBlockEvent(this.mob, blockposition, Blocks.AIR.getBlockData(), !this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)).isCancelled()) {
                    this.level.destroyBlock(blockposition, false);
                }

                this.mob.ate();
            } else {
                BlockPos blockposition1 = blockposition.below();

                if (this.level.getType(blockposition1).is(Blocks.GRASS_BLOCK)) {
                    // CraftBukkit
                    if (!CraftEventFactory.callEntityChangeBlockEvent(this.mob, blockposition, Blocks.AIR.getBlockData(), !this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)).isCancelled()) {
                        this.level.levelEvent(2001, blockposition1, Block.getCombinedId(Blocks.GRASS_BLOCK.getBlockData()));
                        this.level.setTypeAndData(blockposition1, Blocks.DIRT.getBlockData(), 2);
                    }

                    this.mob.ate();
                }
            }

        }
    }
}
