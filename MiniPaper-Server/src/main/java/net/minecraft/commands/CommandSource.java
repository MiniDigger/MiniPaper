package net.minecraft.commands;

import java.util.UUID;
import net.minecraft.network.chat.Component;

public interface CommandSource {

    CommandSource NULL = new CommandSource() {
        @Override
        public void sendMessage(Component ichatbasecomponent, UUID uuid) {}

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        // CraftBukkit end
    };

    void sendMessage(Component ichatbasecomponent, UUID uuid);

    boolean acceptsSuccess();

    boolean acceptsFailure();

    boolean shouldInformAdmins();

    org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper); // CraftBukkit
}
