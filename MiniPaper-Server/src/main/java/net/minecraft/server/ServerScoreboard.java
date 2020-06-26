package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;

public class ServerScoreboard extends Scoreboard {

    private final MinecraftServer server;
    private final Set<Objective> trackedObjectives = Sets.newHashSet();
    private Runnable[] dirtyListeners = new Runnable[0];

    public ServerScoreboard(MinecraftServer minecraftserver) {
        this.server = minecraftserver;
    }

    @Override
    public void onScoreChanged(Score scoreboardscore) {
        super.onScoreChanged(scoreboardscore);
        if (this.trackedObjectives.contains(scoreboardscore.getObjective())) {
            this.sendAll(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, scoreboardscore.getObjective().getName(), scoreboardscore.getOwner(), scoreboardscore.getScore()));
        }

        this.setDirty();
    }

    @Override
    public void onPlayerRemoved(String s) {
        super.onPlayerRemoved(s);
        this.sendAll(new ClientboundSetScorePacket(ServerScoreboard.Method.REMOVE, (String) null, s, 0));
        this.setDirty();
    }

    @Override
    public void onPlayerScoreRemoved(String s, Objective scoreboardobjective) {
        super.onPlayerScoreRemoved(s, scoreboardobjective);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.sendAll(new ClientboundSetScorePacket(ServerScoreboard.Method.REMOVE, scoreboardobjective.getName(), s, 0));
        }

        this.setDirty();
    }

    @Override
    public void setDisplayObjective(int i, @Nullable Objective scoreboardobjective) {
        Objective scoreboardobjective1 = this.getDisplayObjective(i);

        super.setDisplayObjective(i, scoreboardobjective);
        if (scoreboardobjective1 != scoreboardobjective && scoreboardobjective1 != null) {
            if (this.getObjectiveDisplaySlotCount(scoreboardobjective1) > 0) {
                this.sendAll(new ClientboundSetDisplayObjectivePacket(i, scoreboardobjective));
            } else {
                this.stopTrackingObjective(scoreboardobjective1);
            }
        }

        if (scoreboardobjective != null) {
            if (this.trackedObjectives.contains(scoreboardobjective)) {
                this.sendAll(new ClientboundSetDisplayObjectivePacket(i, scoreboardobjective));
            } else {
                this.startTrackingObjective(scoreboardobjective);
            }
        }

        this.setDirty();
    }

    @Override
    public boolean addPlayerToTeam(String s, PlayerTeam scoreboardteam) {
        if (super.addPlayerToTeam(s, scoreboardteam)) {
            this.sendAll(new ClientboundSetPlayerTeamPacket(scoreboardteam, Arrays.asList(s), 3));
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removePlayerFromTeam(String s, PlayerTeam scoreboardteam) {
        super.removePlayerFromTeam(s, scoreboardteam);
        this.sendAll(new ClientboundSetPlayerTeamPacket(scoreboardteam, Arrays.asList(s), 4));
        this.setDirty();
    }

    @Override
    public void onObjectiveAdded(Objective scoreboardobjective) {
        super.onObjectiveAdded(scoreboardobjective);
        this.setDirty();
    }

    @Override
    public void onObjectiveChanged(Objective scoreboardobjective) {
        super.onObjectiveChanged(scoreboardobjective);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.sendAll(new ClientboundSetObjectivePacket(scoreboardobjective, 2));
        }

        this.setDirty();
    }

    @Override
    public void onObjectiveRemoved(Objective scoreboardobjective) {
        super.onObjectiveRemoved(scoreboardobjective);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.stopTrackingObjective(scoreboardobjective);
        }

        this.setDirty();
    }

    @Override
    public void onTeamAdded(PlayerTeam scoreboardteam) {
        super.onTeamAdded(scoreboardteam);
        this.sendAll(new ClientboundSetPlayerTeamPacket(scoreboardteam, 0));
        this.setDirty();
    }

    @Override
    public void onTeamChanged(PlayerTeam scoreboardteam) {
        super.onTeamChanged(scoreboardteam);
        this.sendAll(new ClientboundSetPlayerTeamPacket(scoreboardteam, 2));
        this.setDirty();
    }

    @Override
    public void onTeamRemoved(PlayerTeam scoreboardteam) {
        super.onTeamRemoved(scoreboardteam);
        this.sendAll(new ClientboundSetPlayerTeamPacket(scoreboardteam, 1));
        this.setDirty();
    }

    public void addDirtyListener(Runnable runnable) {
        this.dirtyListeners = (Runnable[]) Arrays.copyOf(this.dirtyListeners, this.dirtyListeners.length + 1);
        this.dirtyListeners[this.dirtyListeners.length - 1] = runnable;
    }

    protected void setDirty() {
        Runnable[] arunnable = this.dirtyListeners;
        int i = arunnable.length;

        for (int j = 0; j < i; ++j) {
            Runnable runnable = arunnable[j];

            runnable.run();
        }

    }

    public List<Packet<?>> getStartTrackingPackets(Objective scoreboardobjective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new ClientboundSetObjectivePacket(scoreboardobjective, 0));

        for (int i = 0; i < 19; ++i) {
            if (this.getDisplayObjective(i) == scoreboardobjective) {
                list.add(new ClientboundSetDisplayObjectivePacket(i, scoreboardobjective));
            }
        }

        Iterator iterator = this.getPlayerScores(scoreboardobjective).iterator();

        while (iterator.hasNext()) {
            Score scoreboardscore = (Score) iterator.next();

            list.add(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, scoreboardscore.getObjective().getName(), scoreboardscore.getOwner(), scoreboardscore.getScore()));
        }

        return list;
    }

    public void startTrackingObjective(Objective scoreboardobjective) {
        List<Packet<?>> list = this.getStartTrackingPackets(scoreboardobjective);
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                Packet<?> packet = (Packet) iterator1.next();

                entityplayer.connection.sendPacket(packet);
            }
        }

        this.trackedObjectives.add(scoreboardobjective);
    }

    public List<Packet<?>> getStopTrackingPackets(Objective scoreboardobjective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new ClientboundSetObjectivePacket(scoreboardobjective, 1));

        for (int i = 0; i < 19; ++i) {
            if (this.getDisplayObjective(i) == scoreboardobjective) {
                list.add(new ClientboundSetDisplayObjectivePacket(i, scoreboardobjective));
            }
        }

        return list;
    }

    public void stopTrackingObjective(Objective scoreboardobjective) {
        List<Packet<?>> list = this.getStopTrackingPackets(scoreboardobjective);
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                Packet<?> packet = (Packet) iterator1.next();

                entityplayer.connection.sendPacket(packet);
            }
        }

        this.trackedObjectives.remove(scoreboardobjective);
    }

    public int getObjectiveDisplaySlotCount(Objective scoreboardobjective) {
        int i = 0;

        for (int j = 0; j < 19; ++j) {
            if (this.getDisplayObjective(j) == scoreboardobjective) {
                ++i;
            }
        }

        return i;
    }

    // CraftBukkit start - Send to players
    private void sendAll(Packet packet) {
        for (ServerPlayer entityplayer : (List<ServerPlayer>) this.server.getPlayerList().players) {
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() == this) {
                entityplayer.connection.sendPacket(packet);
            }
        }
    }
    // CraftBukkit end

    public static enum Method {

        CHANGE, REMOVE;

        private Method() {}
    }
}
