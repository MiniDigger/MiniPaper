package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerLoginPacketListener;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Crypt;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
// CraftBukkit end

public class ServerLoginPacketListenerImpl implements ServerLoginPacketListener {

    private static final AtomicInteger UNIQUE_THREAD_ID = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();
    private final byte[] nonce = new byte[4];
    private final MinecraftServer server;
    public final Connection connection;
    private ServerLoginPacketListenerImpl.State state;
    private int tick;
    private GameProfile gameProfile;
    private final String serverId;
    private SecretKey secretKey;
    private ServerPlayer delayedAcceptPlayer;
    public String hostname = ""; // CraftBukkit - add field

    public ServerLoginPacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager) {
        this.state = ServerLoginPacketListenerImpl.State.HELLO;
        this.serverId = "";
        this.server = minecraftserver;
        this.connection = networkmanager;
        ServerLoginPacketListenerImpl.RANDOM.nextBytes(this.nonce);
    }

    public void tick() {
        if (this.state == ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT) {
            this.handleAcceptedLogin();
        } else if (this.state == ServerLoginPacketListenerImpl.State.DELAY_ACCEPT) {
            ServerPlayer entityplayer = this.server.getPlayerList().getPlayer(this.gameProfile.getId());

            if (entityplayer == null) {
                this.state = ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT;
                this.server.getPlayerList().placeNewPlayer(this.connection, this.delayedAcceptPlayer);
                this.delayedAcceptPlayer = null;
            }
        }

        if (this.tick++ == 600) {
            this.disconnect(new TranslatableComponent("multiplayer.disconnect.slow_login"));
        }

    }

    // CraftBukkit start
    @Deprecated
    public void disconnect(String s) {
        try {
            Component ichatbasecomponent = new TextComponent(s);
            ServerLoginPacketListenerImpl.LOGGER.info("Disconnecting {}: {}", this.getUserName(), s);
            this.connection.sendPacket(new ClientboundLoginDisconnectPacket(ichatbasecomponent));
            this.connection.disconnect(ichatbasecomponent);
        } catch (Exception exception) {
            ServerLoginPacketListenerImpl.LOGGER.error("Error whilst disconnecting player", exception);
        }
    }
    // CraftBukkit end

    @Override
    public Connection getConnection() {
        return this.connection;
    }

    public void disconnect(Component ichatbasecomponent) {
        try {
            ServerLoginPacketListenerImpl.LOGGER.info("Disconnecting {}: {}", this.getUserName(), ichatbasecomponent.getString());
            this.connection.sendPacket(new ClientboundLoginDisconnectPacket(ichatbasecomponent));
            this.connection.disconnect(ichatbasecomponent);
        } catch (Exception exception) {
            ServerLoginPacketListenerImpl.LOGGER.error("Error whilst disconnecting player", exception);
        }

    }

    // Spigot start
    public void initUUID()
    {
        UUID uuid;
        if ( connection.spoofedUUID != null )
        {
            uuid = connection.spoofedUUID;
        } else
        {
            uuid = Player.createPlayerUUID( this.gameProfile.getName() );
        }

        this.gameProfile = new GameProfile( uuid, this.gameProfile.getName() );

        if (connection.spoofedProfile != null)
        {
            for ( com.mojang.authlib.properties.Property property : connection.spoofedProfile )
            {
                this.gameProfile.getProperties().put( property.getName(), property );
            }
        }
    }
    // Spigot end

    public void handleAcceptedLogin() {
        // Spigot start - Moved to initUUID
        /*
        if (!this.i.isComplete()) {
            this.i = this.a(this.i);
        }
        */
        // Spigot end

        // CraftBukkit start - fire PlayerLoginEvent
        ServerPlayer s = this.server.getPlayerList().attemptLogin(this, this.gameProfile, hostname);

        if (s == null) {
            // this.disconnect(ichatbasecomponent);
            // CraftBukkit end
        } else {
            this.state = ServerLoginPacketListenerImpl.State.ACCEPTED;
            if (this.server.getCompressionThreshold() >= 0 && !this.connection.isMemoryConnection()) {
                this.connection.sendPacketOH(new ClientboundLoginCompressionPacket(this.server.getCompressionThreshold()), (channelfuture) -> {
                    this.connection.setupCompression(this.server.getCompressionThreshold());
                });
            }

            this.connection.sendPacket(new ClientboundGameProfilePacket(this.gameProfile));
            ServerPlayer entityplayer = this.server.getPlayerList().getPlayer(this.gameProfile.getId());

            if (entityplayer != null) {
                this.state = ServerLoginPacketListenerImpl.State.DELAY_ACCEPT;
                this.delayedAcceptPlayer = this.server.getPlayerList().processLogin(this.gameProfile, s); // CraftBukkit - add player reference
            } else {
                this.server.getPlayerList().placeNewPlayer(this.connection, this.server.getPlayerList().processLogin(this.gameProfile, s)); // CraftBukkit - add player reference
            }
        }

    }

    @Override
    public void onDisconnect(Component ichatbasecomponent) {
        ServerLoginPacketListenerImpl.LOGGER.info("{} lost connection: {}", this.getUserName(), ichatbasecomponent.getString());
    }

    public String getUserName() {
        return this.gameProfile != null ? this.gameProfile + " (" + this.connection.getRemoteAddress() + ")" : String.valueOf(this.connection.getRemoteAddress());
    }

    @Override
    public void handleHello(ServerboundHelloPacket packetlogininstart) {
        Validate.validState(this.state == ServerLoginPacketListenerImpl.State.HELLO, "Unexpected hello packet", new Object[0]);
        this.gameProfile = packetlogininstart.getGameProfile();
        if (this.server.usesAuthentication() && !this.connection.isMemoryConnection()) {
            this.state = ServerLoginPacketListenerImpl.State.KEY;
            this.connection.sendPacket(new ClientboundHelloPacket("", this.server.getKeyPair().getPublic(), this.nonce));
        } else {
            // Spigot start
            new Thread("User Authenticator #" + ServerLoginPacketListenerImpl.UNIQUE_THREAD_ID.incrementAndGet()) {

                @Override
                public void run() {
                    try {
                        initUUID();
                        new net.minecraft.server.network.ServerLoginPacketListenerImpl.LoginHandler().fireEvents();
                    } catch (Exception ex) {
                        disconnect("Failed to verify username!");
                        server.server.getLogger().log(java.util.logging.Level.WARNING, "Exception verifying " + gameProfile.getName(), ex);
                    }
                }
            }.start();
            // Spigot end
        }

    }

    @Override
    public void handleKey(ServerboundKeyPacket packetlogininencryptionbegin) {
        Validate.validState(this.state == ServerLoginPacketListenerImpl.State.KEY, "Unexpected key packet", new Object[0]);
        PrivateKey privatekey = this.server.getKeyPair().getPrivate();

        if (!Arrays.equals(this.nonce, packetlogininencryptionbegin.getNonce(privatekey))) {
            throw new IllegalStateException("Invalid nonce!");
        } else {
            this.secretKey = packetlogininencryptionbegin.getSecretKey(privatekey);
            this.state = ServerLoginPacketListenerImpl.State.AUTHENTICATING;
            this.connection.setEncryptionKey(this.secretKey);
            Thread thread = new Thread("User Authenticator #" + ServerLoginPacketListenerImpl.UNIQUE_THREAD_ID.incrementAndGet()) {
                public void run() {
                    GameProfile gameprofile = ServerLoginPacketListenerImpl.this.gameProfile;

                    try {
                        String s = (new BigInteger(Crypt.digestData("", ServerLoginPacketListenerImpl.this.server.getKeyPair().getPublic(), ServerLoginPacketListenerImpl.this.secretKey))).toString(16);

                        ServerLoginPacketListenerImpl.this.gameProfile = ServerLoginPacketListenerImpl.this.server.getSessionService().hasJoinedServer(new GameProfile((UUID) null, gameprofile.getName()), s, this.a());
                        if (ServerLoginPacketListenerImpl.this.gameProfile != null) {
                            // CraftBukkit start - fire PlayerPreLoginEvent
                            if (!connection.isConnected()) {
                                return;
                            }

                            new net.minecraft.server.network.ServerLoginPacketListenerImpl.LoginHandler().fireEvents();
                        } else if (ServerLoginPacketListenerImpl.this.server.isSingleplayer()) {
                            ServerLoginPacketListenerImpl.LOGGER.warn("Failed to verify username but will let them in anyway!");
                            ServerLoginPacketListenerImpl.this.gameProfile = ServerLoginPacketListenerImpl.this.createFakeProfile(gameprofile);
                            ServerLoginPacketListenerImpl.this.state = ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT;
                        } else {
                            ServerLoginPacketListenerImpl.this.disconnect(new TranslatableComponent("multiplayer.disconnect.unverified_username"));
                            ServerLoginPacketListenerImpl.LOGGER.error("Username '{}' tried to join with an invalid session", gameprofile.getName());
                        }
                    } catch (AuthenticationUnavailableException authenticationunavailableexception) {
                        if (ServerLoginPacketListenerImpl.this.server.isSingleplayer()) {
                            ServerLoginPacketListenerImpl.LOGGER.warn("Authentication servers are down but will let them in anyway!");
                            ServerLoginPacketListenerImpl.this.gameProfile = ServerLoginPacketListenerImpl.this.createFakeProfile(gameprofile);
                            ServerLoginPacketListenerImpl.this.state = ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT;
                        } else {
                            ServerLoginPacketListenerImpl.this.disconnect(new TranslatableComponent("multiplayer.disconnect.authservers_down"));
                            ServerLoginPacketListenerImpl.LOGGER.error("Couldn't verify username because servers are unavailable");
                        }
                        // CraftBukkit start - catch all exceptions
                    } catch (Exception exception) {
                        disconnect("Failed to verify username!");
                        server.server.getLogger().log(java.util.logging.Level.WARNING, "Exception verifying " + gameprofile.getName(), exception);
                        // CraftBukkit end
                    }

                }

                @Nullable
                private InetAddress a() {
                    SocketAddress socketaddress = ServerLoginPacketListenerImpl.this.connection.getRemoteAddress();

                    return ServerLoginPacketListenerImpl.this.server.getPreventProxyConnections() && socketaddress instanceof InetSocketAddress ? ((InetSocketAddress) socketaddress).getAddress() : null;
                }
            };

            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(ServerLoginPacketListenerImpl.LOGGER));
            thread.start();
        }
    }

    // Spigot start
    public class LoginHandler {

        public void fireEvents() throws Exception {
                            String playerName = gameProfile.getName();
                            java.net.InetAddress address = ((java.net.InetSocketAddress) connection.getRemoteAddress()).getAddress();
                            java.util.UUID uniqueId = gameProfile.getId();
                            final org.bukkit.craftbukkit.CraftServer server = ServerLoginPacketListenerImpl.this.server.server;

                            AsyncPlayerPreLoginEvent asyncEvent = new AsyncPlayerPreLoginEvent(playerName, address, uniqueId);
                            server.getPluginManager().callEvent(asyncEvent);

                            if (PlayerPreLoginEvent.getHandlerList().getRegisteredListeners().length != 0) {
                                final PlayerPreLoginEvent event = new PlayerPreLoginEvent(playerName, address, uniqueId);
                                if (asyncEvent.getResult() != PlayerPreLoginEvent.Result.ALLOWED) {
                                    event.disallow(asyncEvent.getResult(), asyncEvent.getKickMessage());
                                }
                                Waitable<PlayerPreLoginEvent.Result> waitable = new Waitable<PlayerPreLoginEvent.Result>() {
                                    @Override
                                    protected PlayerPreLoginEvent.Result evaluate() {
                                        server.getPluginManager().callEvent(event);
                                        return event.getResult();
                                    }};

                                ServerLoginPacketListenerImpl.this.server.processQueue.add(waitable);
                                if (waitable.get() != PlayerPreLoginEvent.Result.ALLOWED) {
                                    disconnect(event.getKickMessage());
                                    return;
                                }
                            } else {
                                if (asyncEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                                    disconnect(asyncEvent.getKickMessage());
                                    return;
                                }
                            }
                            // CraftBukkit end
                            ServerLoginPacketListenerImpl.LOGGER.info("UUID of player {} is {}", ServerLoginPacketListenerImpl.this.gameProfile.getName(), ServerLoginPacketListenerImpl.this.gameProfile.getId());
                            ServerLoginPacketListenerImpl.this.state = ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT;
                }
        }
    // Spigot end

    public void handleCustomQueryPacket(ServerboundCustomQueryPacket packetloginincustompayload) {
        this.disconnect(new TranslatableComponent("multiplayer.disconnect.unexpected_query_response"));
    }

    protected GameProfile createFakeProfile(GameProfile gameprofile) {
        UUID uuid = Player.createPlayerUUID(gameprofile.getName());

        return new GameProfile(uuid, gameprofile.getName());
    }

    public static enum State {

        HELLO, KEY, AUTHENTICATING, NEGOTIATING, READY_TO_ACCEPT, DELAY_ACCEPT, ACCEPTED;

        private State() {}
    }
}
