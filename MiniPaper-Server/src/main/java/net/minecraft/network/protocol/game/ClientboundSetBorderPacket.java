package net.minecraft.network.protocol.game;

import java.io.IOException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.border.WorldBorder;

public class ClientboundSetBorderPacket implements Packet<ClientGamePacketListener> {

    private ClientboundSetBorderPacket.Type type;
    private int newAbsoluteMaxSize;
    private double newCenterX;
    private double newCenterZ;
    private double newSize;
    private double oldSize;
    private long lerpTime;
    private int warningTime;
    private int warningBlocks;

    public ClientboundSetBorderPacket() {}

    public ClientboundSetBorderPacket(WorldBorder worldborder, ClientboundSetBorderPacket.Type packetplayoutworldborder_enumworldborderaction) {
        this.type = packetplayoutworldborder_enumworldborderaction;
        // CraftBukkit start - multiply out nether border
        this.newCenterX = worldborder.getCenterX() * (worldborder.world.dimensionType().shrunk() ? 8 : 1);
        this.newCenterZ = worldborder.getCenterZ() * (worldborder.world.dimensionType().shrunk() ? 8 : 1);
        // CraftBukkit end
        this.oldSize = worldborder.getSize();
        this.newSize = worldborder.getLerpTarget();
        this.lerpTime = worldborder.getLerpRemainingTime();
        this.newAbsoluteMaxSize = worldborder.getAbsoluteMaxSize();
        this.warningBlocks = worldborder.getWarningBlocks();
        this.warningTime = worldborder.getWarningTime();
    }

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.type = (ClientboundSetBorderPacket.Type) packetdataserializer.readEnum(ClientboundSetBorderPacket.Type.class);
        switch (this.type) {
            case SET_SIZE:
                this.newSize = packetdataserializer.readDouble();
                break;
            case LERP_SIZE:
                this.oldSize = packetdataserializer.readDouble();
                this.newSize = packetdataserializer.readDouble();
                this.lerpTime = packetdataserializer.readVarLong();
                break;
            case SET_CENTER:
                this.newCenterX = packetdataserializer.readDouble();
                this.newCenterZ = packetdataserializer.readDouble();
                break;
            case SET_WARNING_BLOCKS:
                this.warningBlocks = packetdataserializer.readVarInt();
                break;
            case SET_WARNING_TIME:
                this.warningTime = packetdataserializer.readVarInt();
                break;
            case INITIALIZE:
                this.newCenterX = packetdataserializer.readDouble();
                this.newCenterZ = packetdataserializer.readDouble();
                this.oldSize = packetdataserializer.readDouble();
                this.newSize = packetdataserializer.readDouble();
                this.lerpTime = packetdataserializer.readVarLong();
                this.newAbsoluteMaxSize = packetdataserializer.readVarInt();
                this.warningBlocks = packetdataserializer.readVarInt();
                this.warningTime = packetdataserializer.readVarInt();
        }

    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        packetdataserializer.writeEnum((Enum) this.type);
        switch (this.type) {
            case SET_SIZE:
                packetdataserializer.writeDouble(this.newSize);
                break;
            case LERP_SIZE:
                packetdataserializer.writeDouble(this.oldSize);
                packetdataserializer.writeDouble(this.newSize);
                packetdataserializer.writeVarLong(this.lerpTime);
                break;
            case SET_CENTER:
                packetdataserializer.writeDouble(this.newCenterX);
                packetdataserializer.writeDouble(this.newCenterZ);
                break;
            case SET_WARNING_BLOCKS:
                packetdataserializer.writeVarInt(this.warningBlocks);
                break;
            case SET_WARNING_TIME:
                packetdataserializer.writeVarInt(this.warningTime);
                break;
            case INITIALIZE:
                packetdataserializer.writeDouble(this.newCenterX);
                packetdataserializer.writeDouble(this.newCenterZ);
                packetdataserializer.writeDouble(this.oldSize);
                packetdataserializer.writeDouble(this.newSize);
                packetdataserializer.writeVarLong(this.lerpTime);
                packetdataserializer.writeVarInt(this.newAbsoluteMaxSize);
                packetdataserializer.writeVarInt(this.warningBlocks);
                packetdataserializer.writeVarInt(this.warningTime);
        }

    }

    public void handle(ClientGamePacketListener packetlistenerplayout) {
        packetlistenerplayout.handleSetBorder(this);
    }

    public static enum Type {

        SET_SIZE, LERP_SIZE, SET_CENTER, INITIALIZE, SET_WARNING_TIME, SET_WARNING_BLOCKS;

        private Type() {}
    }
}
