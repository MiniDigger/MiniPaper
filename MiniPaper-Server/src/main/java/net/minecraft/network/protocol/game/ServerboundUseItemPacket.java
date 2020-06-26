package net.minecraft.network.protocol.game;

import java.io.IOException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;

public class ServerboundUseItemPacket implements Packet<ServerGamePacketListener> {

    private InteractionHand hand;
    public long timestamp; // Spigot

    public ServerboundUseItemPacket() {}

    public ServerboundUseItemPacket(InteractionHand enumhand) {
        this.hand = enumhand;
    }

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.timestamp = System.currentTimeMillis(); // Spigot
        this.hand = (InteractionHand) packetdataserializer.readEnum(InteractionHand.class);
    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        packetdataserializer.writeEnum((Enum) this.hand);
    }

    public void handle(ServerGamePacketListener packetlistenerplayin) {
        packetlistenerplayin.handleUseItem(this);
    }

    public InteractionHand getHand() {
        return this.hand;
    }
}
