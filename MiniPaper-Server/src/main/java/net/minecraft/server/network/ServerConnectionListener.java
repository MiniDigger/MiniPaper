package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.LazyLoadedValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerConnectionListener {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final LazyLoadedValue<NioEventLoopGroup> SERVER_EVENT_GROUP = new LazyLoadedValue<>(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Server IO #%d").setDaemon(true).build());
    });
    public static final LazyLoadedValue<EpollEventLoopGroup> SERVER_EPOLL_EVENT_GROUP = new LazyLoadedValue<>(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Server IO #%d").setDaemon(true).build());
    });
    private final MinecraftServer server;
    public volatile boolean running;
    private final List<ChannelFuture> channels = Collections.synchronizedList(Lists.newArrayList());
    private final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());

    public ServerConnectionListener(MinecraftServer minecraftserver) {
        this.server = minecraftserver;
        this.running = true;
    }

    public void startTcpServerListener(@Nullable InetAddress inetaddress, int i) throws IOException {
        List list = this.channels;

        synchronized (this.channels) {
            Class oclass;
            LazyLoadedValue lazyinitvar;

            if (Epoll.isAvailable() && this.server.isEpollEnabled()) {
                oclass = EpollServerSocketChannel.class;
                lazyinitvar = ServerConnectionListener.SERVER_EPOLL_EVENT_GROUP;
                ServerConnectionListener.LOGGER.info("Using epoll channel type");
            } else {
                oclass = NioServerSocketChannel.class;
                lazyinitvar = ServerConnectionListener.SERVER_EVENT_GROUP;
                ServerConnectionListener.LOGGER.info("Using default channel type");
            }

            this.channels.add(((ServerBootstrap) ((ServerBootstrap) (new ServerBootstrap()).channel(oclass)).childHandler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel channel) throws Exception {
                    try {
                        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                    } catch (ChannelException channelexception) {
                        ;
                    }

                    channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30)).addLast("legacy_query", new LegacyQueryHandler(ServerConnectionListener.this)).addLast("splitter", new Varint21FrameDecoder()).addLast("decoder", new PacketDecoder(PacketFlow.SERVERBOUND)).addLast("prepender", new Varint21LengthFieldPrepender()).addLast("encoder", new PacketEncoder(PacketFlow.CLIENTBOUND));
                    Connection networkmanager = new Connection(PacketFlow.SERVERBOUND);

                    ServerConnectionListener.this.connections.add(networkmanager);
                    channel.pipeline().addLast("packet_handler", networkmanager);
                    networkmanager.setPacketListener(new ServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, networkmanager));
                }
            }).group((EventLoopGroup) lazyinitvar.get()).localAddress(inetaddress, i)).option(ChannelOption.AUTO_READ, false).bind().syncUninterruptibly()); // CraftBukkit
        }
    }

    // CraftBukkit start
    public void acceptConnections() {
        synchronized (this.channels) {
            for (ChannelFuture future : this.channels) {
                future.channel().config().setAutoRead(true);
            }
        }
    }
    // CraftBukkit end

    public void stop() {
        this.running = false;
        Iterator iterator = this.channels.iterator();

        while (iterator.hasNext()) {
            ChannelFuture channelfuture = (ChannelFuture) iterator.next();

            try {
                channelfuture.channel().close().sync();
            } catch (InterruptedException interruptedexception) {
                ServerConnectionListener.LOGGER.error("Interrupted whilst closing channel");
            }
        }

    }

    public void tick() {
        List list = this.connections;

        synchronized (this.connections) {
            // Spigot Start
            // This prevents players from 'gaming' the server, and strategically relogging to increase their position in the tick order
            if ( org.spigotmc.SpigotConfig.playerShuffle > 0 && MinecraftServer.currentTick % org.spigotmc.SpigotConfig.playerShuffle == 0 )
            {
                Collections.shuffle( this.connections );
            }
            // Spigot End
            Iterator iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection networkmanager = (Connection) iterator.next();

                if (!networkmanager.isConnecting()) {
                    if (networkmanager.isConnected()) {
                        try {
                            networkmanager.tick();
                        } catch (Exception exception) {
                            if (networkmanager.isMemoryConnection()) {
                                throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
                            }

                            ServerConnectionListener.LOGGER.warn("Failed to handle packet for {}", networkmanager.getRemoteAddress(), exception);
                            TextComponent chatcomponenttext = new TextComponent("Internal server error");

                            networkmanager.sendPacketOH(new ClientboundDisconnectPacket(chatcomponenttext), (future) -> {
                                networkmanager.disconnect(chatcomponenttext);
                            });
                            networkmanager.setReadOnly();
                        }
                    } else {
                        // Spigot Start
                        // Fix a race condition where a NetworkManager could be unregistered just before connection.
                        if (networkmanager.preparing) continue;
                        // Spigot End
                        iterator.remove();
                        networkmanager.handleDisconnection();
                    }
                }
            }

        }
    }

    public MinecraftServer getServer() {
        return this.server;
    }
}
