package net.minecraft.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.SocketAddress;
import java.util.Queue;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import net.minecraft.util.LazyLoadedValue;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final Marker ROOT_MARKER = MarkerManager.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = MarkerManager.getMarker("NETWORK_PACKETS", Connection.ROOT_MARKER);
    public static final AttributeKey<ConnectionProtocol> ATTRIBUTE_PROTOCOL = AttributeKey.valueOf("protocol");
    public static final LazyLoadedValue<NioEventLoopGroup> NETWORK_WORKER_GROUP = new LazyLoadedValue<>(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
    });
    public static final LazyLoadedValue<EpollEventLoopGroup> NETWORK_EPOLL_WORKER_GROUP = new LazyLoadedValue<>(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
    });
    public static final LazyLoadedValue<DefaultEventLoopGroup> LOCAL_WORKER_GROUP = new LazyLoadedValue<>(() -> {
        return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
    });
    private final PacketFlow receiving;
    private final Queue<Connection.QueuedPacket> queue = Queues.newConcurrentLinkedQueue();
    public Channel channel;
    public SocketAddress address;
    // Spigot Start
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public boolean preparing = true;
    // Spigot End
    private PacketListener packetListener;
    private Component disconnectedReason;
    private boolean encrypted;
    private boolean disconnectionHandled;
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;

    public Connection(PacketFlow enumprotocoldirection) {
        this.receiving = enumprotocoldirection;
    }

    public void channelActive(ChannelHandlerContext channelhandlercontext) throws Exception {
        super.channelActive(channelhandlercontext);
        this.channel = channelhandlercontext.channel();
        this.address = this.channel.remoteAddress();
        // Spigot Start
        this.preparing = false;
        // Spigot End

        try {
            this.setProtocol(ConnectionProtocol.HANDSHAKING);
        } catch (Throwable throwable) {
            Connection.LOGGER.fatal(throwable);
        }

    }

    public void setProtocol(ConnectionProtocol enumprotocol) {
        this.channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(enumprotocol);
        this.channel.config().setAutoRead(true);
        Connection.LOGGER.debug("Enabled auto read");
    }

    public void channelInactive(ChannelHandlerContext channelhandlercontext) throws Exception {
        this.disconnect(new TranslatableComponent("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext channelhandlercontext, Throwable throwable) {
        if (throwable instanceof SkipPacketException) {
            Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
        } else {
            boolean flag = !this.handlingFault;

            this.handlingFault = true;
            if (this.channel.isOpen()) {
                if (throwable instanceof TimeoutException) {
                    Connection.LOGGER.debug("Timeout", throwable);
                    this.disconnect(new TranslatableComponent("disconnect.timeout"));
                } else {
                    TranslatableComponent chatmessage = new TranslatableComponent("disconnect.genericReason", new Object[]{"Internal Exception: " + throwable});

                    if (flag) {
                        Connection.LOGGER.debug("Failed to sent packet", throwable);
                        this.sendPacketOH(new ClientboundDisconnectPacket(chatmessage), (future) -> {
                            this.disconnect(chatmessage);
                        });
                        this.setReadOnly();
                    } else {
                        Connection.LOGGER.debug("Double fault", throwable);
                        this.disconnect(chatmessage);
                    }
                }

            }
        }
        if (MinecraftServer.getServer().isDebugging()) throwable.printStackTrace(); // Spigot
    }

    protected void channelRead0(ChannelHandlerContext channelhandlercontext, Packet<?> packet) throws Exception {
        if (this.channel.isOpen()) {
            try {
                genericsFtw(packet, this.packetListener);
            } catch (RunningOnDifferentThreadException cancelledpackethandleexception) {
                ;
            }

            ++this.receivedPackets;
        }

    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener packetlistener) {
        packet.handle((T) packetlistener); // CraftBukkit - decompile error
    }

    public void setPacketListener(PacketListener packetlistener) {
        Validate.notNull(packetlistener, "packetListener", new Object[0]);
        this.packetListener = packetlistener;
    }

    public void sendPacket(Packet<?> packet) {
        this.sendPacketOH(packet, (GenericFutureListener) null);
    }

    public void sendPacketOH(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
        if (this.isConnected()) {
            this.flushQueue();
            this.sendPacket(packet, genericfuturelistener);
        } else {
            this.queue.add(new Connection.QueuedPacket(packet, genericfuturelistener));
        }

    }

    private void sendPacket(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
        ConnectionProtocol enumprotocol = ConnectionProtocol.getProtocolForPacket(packet);
        ConnectionProtocol enumprotocol1 = (ConnectionProtocol) this.channel.attr(Connection.ATTRIBUTE_PROTOCOL).get();

        ++this.sentPackets;
        if (enumprotocol1 != enumprotocol) {
            Connection.LOGGER.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }

        if (this.channel.eventLoop().inEventLoop()) {
            if (enumprotocol != enumprotocol1) {
                this.setProtocol(enumprotocol);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(packet);

            if (genericfuturelistener != null) {
                channelfuture.addListener(genericfuturelistener);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            this.channel.eventLoop().execute(() -> {
                if (enumprotocol != enumprotocol1) {
                    this.setProtocol(enumprotocol);
                }

                ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet);

                if (genericfuturelistener != null) {
                    channelfuture1.addListener(genericfuturelistener);
                }

                channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            });
        }

    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            Queue queue = this.queue;

            synchronized (this.queue) {
                Connection.QueuedPacket networkmanager_queuedpacket;

                while ((networkmanager_queuedpacket = (Connection.QueuedPacket) this.queue.poll()) != null) {
                    this.sendPacket(networkmanager_queuedpacket.a, networkmanager_queuedpacket.b);
                }

            }
        }
    }

    public void tick() {
        this.flushQueue();
        if (this.packetListener instanceof ServerLoginPacketListenerImpl) {
            ((ServerLoginPacketListenerImpl) this.packetListener).tick();
        }

        if (this.packetListener instanceof ServerGamePacketListenerImpl) {
            ((ServerGamePacketListenerImpl) this.packetListener).tick();
        }

        if (this.channel != null) {
            this.channel.flush();
        }

        if (this.tickCount++ % 20 == 0) {
            this.averageSentPackets = this.averageSentPackets * 0.75F + (float) this.sentPackets * 0.25F;
            this.averageReceivedPackets = this.averageReceivedPackets * 0.75F + (float) this.receivedPackets * 0.25F;
            this.sentPackets = 0;
            this.receivedPackets = 0;
        }

    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public void disconnect(Component ichatbasecomponent) {
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.channel.isOpen()) {
            this.channel.close(); // We can't wait as this may be called from an event loop.
            this.disconnectedReason = ichatbasecomponent;
        }

    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public void setEncryptionKey(SecretKey secretkey) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(Crypt.getCipher(2, secretkey)));
        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(Crypt.getCipher(1, secretkey)));
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    public PacketListener getPacketListener() {
        return this.packetListener;
    }

    @Nullable
    public Component getDisconnectedReason() {
        return this.disconnectedReason;
    }

    public void setReadOnly() {
        this.channel.config().setAutoRead(false);
    }

    public void setupCompression(int i) {
        if (i >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                ((CompressionDecoder) this.channel.pipeline().get("decompress")).setThreshold(i);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new CompressionDecoder(i));
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                ((CompressionEncoder) this.channel.pipeline().get("compress")).setThreshold(i);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new CompressionEncoder(i));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }

    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled) {
                Connection.LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnectionHandled = true;
                if (this.getDisconnectedReason() != null) {
                    this.getPacketListener().onDisconnect(this.getDisconnectedReason());
                } else if (this.getPacketListener() != null) {
                    this.getPacketListener().onDisconnect(new TranslatableComponent("multiplayer.disconnect.generic"));
                }
                this.queue.clear(); // Free up packet queue.
            }

        }
    }

    static class QueuedPacket {

        private final Packet<?> a;
        @Nullable
        private final GenericFutureListener<? extends Future<? super Void>> b;

        public QueuedPacket(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
            this.a = packet;
            this.b = genericfuturelistener;
        }
    }

    // Spigot Start
    public SocketAddress getRawAddress()
    {
        return this.channel.remoteAddress();
    }
    // Spigot End
}
