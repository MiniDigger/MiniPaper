package net.minecraft.network.protocol.game;

import java.io.IOException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public class ServerboundUseItemOnPacket implements Packet<ServerGamePacketListener> {

    private BlockHitResult blockHit;
    private InteractionHand hand;
    public long timestamp;

    public ServerboundUseItemOnPacket() {}

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.timestamp = System.currentTimeMillis(); // Spigot
        this.hand = (InteractionHand) packetdataserializer.readEnum(InteractionHand.class);
        this.blockHit = packetdataserializer.readBlockHitResult();
    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        packetdataserializer.writeEnum((Enum) this.hand);
        packetdataserializer.writeBlockHitResult(this.blockHit);
    }

    public void handle(ServerGamePacketListener packetlistenerplayin) {
        packetlistenerplayin.handleUseItemOn(this);
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public BlockHitResult getHitResult() {
        return this.blockHit;
    }
}
