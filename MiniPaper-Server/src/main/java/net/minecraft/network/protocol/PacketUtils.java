package net.minecraft.network.protocol;

import net.minecraft.network.PacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.thread.BlockableEventLoop;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PacketUtils {

    private static final Logger LOGGER = LogManager.getLogger();

    public static <T extends PacketListener> void ensureMainThread(Packet<T> packet, T t0, ServerLevel worldserver) throws RunningOnDifferentThreadException {
        ensureMainThread(packet, t0, (BlockableEventLoop) worldserver.getServer());
    }

    public static <T extends PacketListener> void ensureMainThread(Packet<T> packet, T t0, BlockableEventLoop<?> iasynctaskhandler) throws RunningOnDifferentThreadException {
        if (!iasynctaskhandler.isSameThread()) {
            iasynctaskhandler.execute(() -> {
                if (MinecraftServer.getServer().hasStopped() || (t0 instanceof ServerGamePacketListenerImpl && ((ServerGamePacketListenerImpl) t0).processedDisconnect)) return; // CraftBukkit, MC-142590
                if (t0.getConnection().isConnected()) {
                    packet.handle(t0);
                } else {
                    PacketUtils.LOGGER.debug("Ignoring packet due to disconnection: " + packet);
                }

            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
        // CraftBukkit start - SPIGOT-5477, MC-142590
        else if (MinecraftServer.getServer().hasStopped() || (t0 instanceof ServerGamePacketListenerImpl && ((ServerGamePacketListenerImpl) t0).processedDisconnect)) {
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
        // CraftBukkit end
    }
}
