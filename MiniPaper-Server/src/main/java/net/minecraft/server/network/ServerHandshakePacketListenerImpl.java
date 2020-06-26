package net.minecraft.server.network;

// CraftBukkit start
import java.net.InetAddress;
import java.util.HashMap;
// CraftBukkit end
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.MinecraftServer;

public class ServerHandshakePacketListenerImpl implements ServerHandshakePacketListener {

    private static final com.google.gson.Gson gson = new com.google.gson.Gson(); // Spigot
    // CraftBukkit start - add fields
    private static final HashMap<InetAddress, Long> throttleTracker = new HashMap<InetAddress, Long>();
    private static int throttleCounter = 0;
    // CraftBukkit end
    private static final Component IGNORE_STATUS_REASON = new TextComponent("Ignoring status request");
    private final MinecraftServer server;
    private final Connection connection;

    public ServerHandshakePacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager) {
        this.server = minecraftserver;
        this.connection = networkmanager;
    }

    @Override
    public void handleIntention(ClientIntentionPacket packethandshakinginsetprotocol) {
        switch (packethandshakinginsetprotocol.getIntention()) {
            case LOGIN:
                this.connection.setProtocol(ConnectionProtocol.LOGIN);
                TranslatableComponent chatmessage;

                // CraftBukkit start - Connection throttle
                try {
                    long currentTime = System.currentTimeMillis();
                    long connectionThrottle = this.server.server.getConnectionThrottle();
                    InetAddress address = ((java.net.InetSocketAddress) this.connection.getRemoteAddress()).getAddress();

                    synchronized (throttleTracker) {
                        if (throttleTracker.containsKey(address) && !"127.0.0.1".equals(address.getHostAddress()) && currentTime - throttleTracker.get(address) < connectionThrottle) {
                            throttleTracker.put(address, currentTime);
                            chatmessage = new TranslatableComponent("Connection throttled! Please wait before reconnecting.");
                            this.connection.sendPacket(new ClientboundLoginDisconnectPacket(chatmessage));
                            this.connection.disconnect(chatmessage);
                            return;
                        }

                        throttleTracker.put(address, currentTime);
                        throttleCounter++;
                        if (throttleCounter > 200) {
                            throttleCounter = 0;

                            // Cleanup stale entries
                            java.util.Iterator iter = throttleTracker.entrySet().iterator();
                            while (iter.hasNext()) {
                                java.util.Map.Entry<InetAddress, Long> entry = (java.util.Map.Entry) iter.next();
                                if (entry.getValue() > connectionThrottle) {
                                    iter.remove();
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    org.apache.logging.log4j.LogManager.getLogger().debug("Failed to check connection throttle", t);
                }
                // CraftBukkit end

                if (packethandshakinginsetprotocol.getProtocolVersion() > SharedConstants.getCurrentVersion().getProtocolVersion()) {
                    chatmessage = new TranslatableComponent( java.text.MessageFormat.format( org.spigotmc.SpigotConfig.outdatedServerMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName() ) ); // Spigot
                    this.connection.sendPacket(new ClientboundLoginDisconnectPacket(chatmessage));
                    this.connection.disconnect(chatmessage);
                } else if (packethandshakinginsetprotocol.getProtocolVersion() < SharedConstants.getCurrentVersion().getProtocolVersion()) {
                    chatmessage = new TranslatableComponent( java.text.MessageFormat.format( org.spigotmc.SpigotConfig.outdatedServerMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName() ) ); // Spigot
                    this.connection.sendPacket(new ClientboundLoginDisconnectPacket(chatmessage));
                    this.connection.disconnect(chatmessage);
                } else {
                    this.connection.setPacketListener(new ServerLoginPacketListenerImpl(this.server, this.connection));
                    // Spigot Start
                    if (org.spigotmc.SpigotConfig.bungee) {
                        String[] split = packethandshakinginsetprotocol.hostName.split("\00");
                        if ( split.length == 3 || split.length == 4 ) {
                            packethandshakinginsetprotocol.hostName = split[0];
                            connection.address = new java.net.InetSocketAddress(split[1], ((java.net.InetSocketAddress) connection.getRemoteAddress()).getPort());
                            connection.spoofedUUID = com.mojang.util.UUIDTypeAdapter.fromString( split[2] );
                        } else
                        {
                            chatmessage = new TranslatableComponent("If you wish to use IP forwarding, please enable it in your BungeeCord config as well!");
                            this.connection.sendPacket(new ClientboundLoginDisconnectPacket(chatmessage));
                            this.connection.disconnect(chatmessage);
                            return;
                        }
                        if ( split.length == 4 )
                        {
                            connection.spoofedProfile = gson.fromJson(split[3], com.mojang.authlib.properties.Property[].class);
                        }
                    }
                    // Spigot End
                    ((ServerLoginPacketListenerImpl) this.connection.getPacketListener()).hostname = packethandshakinginsetprotocol.hostName + ":" + packethandshakinginsetprotocol.port; // CraftBukkit - set hostname
                }
                break;
            case STATUS:
                if (this.server.repliesToStatus()) {
                    this.connection.setProtocol(ConnectionProtocol.STATUS);
                    this.connection.setPacketListener(new ServerStatusPacketListenerImpl(this.server, this.connection));
                } else {
                    this.connection.disconnect(ServerHandshakePacketListenerImpl.IGNORE_STATUS_REASON);
                }
                break;
            default:
                throw new UnsupportedOperationException("Invalid intention " + packethandshakinginsetprotocol.getIntention());
        }

    }

    @Override
    public void onDisconnect(Component ichatbasecomponent) {}

    @Override
    public Connection getConnection() {
        return this.connection;
    }
}
