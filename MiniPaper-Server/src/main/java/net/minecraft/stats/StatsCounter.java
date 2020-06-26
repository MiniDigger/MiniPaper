package net.minecraft.stats;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.player.Player;

public class StatsCounter {

    protected final Object2IntMap<Stat<?>> stats = Object2IntMaps.synchronize(new Object2IntOpenHashMap());

    public StatsCounter() {
        this.stats.defaultReturnValue(0);
    }

    public void increment(Player entityhuman, Stat<?> statistic, int i) {
        int j = (int) Math.min((long) this.getValue(statistic) + (long) i, 2147483647L);

        // CraftBukkit start - fire Statistic events
        org.bukkit.event.Cancellable cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.handleStatisticsIncrease(entityhuman, statistic, this.getValue(statistic), j);
        if (cancellable != null && cancellable.isCancelled()) {
            return;
        }
        // CraftBukkit end
        this.setValue(entityhuman, statistic, j);
    }

    public void setValue(Player entityhuman, Stat<?> statistic, int i) {
        this.stats.put(statistic, i);
    }

    public int getValue(Stat<?> statistic) {
        return this.stats.getInt(statistic);
    }
}
