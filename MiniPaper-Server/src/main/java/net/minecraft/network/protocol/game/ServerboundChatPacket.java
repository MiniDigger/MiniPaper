package net.minecraft.network.protocol.game;

import java.io.IOException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ServerboundChatPacket implements Packet<ServerGamePacketListener> {

    private String message;

    public ServerboundChatPacket() {}

    public ServerboundChatPacket(String s) {
        if (s.length() > 256) {
            s = s.substring(0, 256);
        }

        this.message = s;
    }

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.message = packetdataserializer.readUtf(256);
    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        packetdataserializer.writeUtf(this.message);
    }

    // Spigot Start
    private static final java.util.concurrent.ExecutorService executors = java.util.concurrent.Executors.newCachedThreadPool(
            new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon( true ).setNameFormat( "Async Chat Thread - #%d" ).build() );
    public void handle(final ServerGamePacketListener packetlistenerplayin) {
        if ( !message.startsWith("/") )
        {
            executors.submit( new Runnable()
            {

                @Override
                public void run()
                {
                    packetlistenerplayin.handleChat( ServerboundChatPacket.this );
                }
            } );
            return;
        }
        // Spigot End
        packetlistenerplayin.handleChat(this);
    }

    public String getMessage() {
        return this.message;
    }
}
