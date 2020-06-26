package net.minecraft.network.protocol.game;

import java.io.IOException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ServerboundContainerClosePacket implements Packet<ServerGamePacketListener> {

    private int containerId;

    public ServerboundContainerClosePacket() {}

    // CraftBukkit start
    public ServerboundContainerClosePacket(int id) {
        this.containerId = id;
    }
    // CraftBukkit end

    public void handle(ServerGamePacketListener packetlistenerplayin) {
        packetlistenerplayin.handleContainerClose(this);
    }

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.containerId = packetdataserializer.readByte();
    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        packetdataserializer.writeByte(this.containerId);
    }
}
