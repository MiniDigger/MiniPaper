package net.minecraft.network.protocol.handshake;

import java.io.IOException;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ClientIntentionPacket implements Packet<ServerHandshakePacketListener> {

    private int protocolVersion;
    public String hostName;
    public int port;
    private ConnectionProtocol intention;

    public ClientIntentionPacket() {}

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.protocolVersion = packetdataserializer.readVarInt();
        this.hostName = packetdataserializer.readUtf(Short.MAX_VALUE); // Spigot
        this.port = packetdataserializer.readUnsignedShort();
        this.intention = ConnectionProtocol.getById(packetdataserializer.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        packetdataserializer.writeVarInt(this.protocolVersion);
        packetdataserializer.writeUtf(this.hostName);
        packetdataserializer.writeShort(this.port);
        packetdataserializer.writeVarInt(this.intention.getId());
    }

    public void handle(ServerHandshakePacketListener packethandshakinginlistener) {
        packethandshakinginlistener.handleIntention(this);
    }

    public ConnectionProtocol getIntention() {
        return this.intention;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }
}
