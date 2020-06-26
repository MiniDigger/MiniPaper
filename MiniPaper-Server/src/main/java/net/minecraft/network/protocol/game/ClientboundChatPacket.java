package net.minecraft.network.protocol.game;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public class ClientboundChatPacket implements Packet<ClientGamePacketListener> {

    public Component message;
    public net.md_5.bungee.api.chat.BaseComponent[] components; // Spigot
    private ChatType type;
    private UUID sender;

    public ClientboundChatPacket() {}

    public ClientboundChatPacket(Component ichatbasecomponent, ChatType chatmessagetype, UUID uuid) {
        this.message = ichatbasecomponent;
        this.type = chatmessagetype;
        this.sender = uuid;
    }

    @Override
    public void read(FriendlyByteBuf packetdataserializer) throws IOException {
        this.message = packetdataserializer.readComponent();
        this.type = ChatType.getForIndex(packetdataserializer.readByte());
        this.sender = packetdataserializer.readUUID();
    }

    @Override
    public void write(FriendlyByteBuf packetdataserializer) throws IOException {
        // Spigot start
        if (components != null) {
            packetdataserializer.writeUtf(net.md_5.bungee.chat.ComponentSerializer.toString(components));
        } else {
            packetdataserializer.writeComponent(this.message);
        }
        // Spigot end
        packetdataserializer.writeByte(this.type.getIndex());
        packetdataserializer.writeUUID(this.sender);
    }

    public void handle(ClientGamePacketListener packetlistenerplayout) {
        packetlistenerplayout.handleChat(this);
    }

    public boolean isSystem() {
        return this.type == ChatType.SYSTEM || this.type == ChatType.GAME_INFO;
    }

    public ChatType getType() {
        return this.type;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
