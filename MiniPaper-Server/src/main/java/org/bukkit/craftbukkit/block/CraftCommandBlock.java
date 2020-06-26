package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.craftbukkit.util.CraftChatMessage;

public class CraftCommandBlock extends CraftBlockEntityState<CommandBlockEntity> implements CommandBlock {

    private String command;
    private String name;

    public CraftCommandBlock(Block block) {
        super(block, CommandBlockEntity.class);
    }

    public CraftCommandBlock(final Material material, final CommandBlockEntity te) {
        super(material, te);
    }

    @Override
    public void load(CommandBlockEntity commandBlock) {
        super.load(commandBlock);

        command = commandBlock.getCommandBlock().getCommand();
        name = CraftChatMessage.fromComponent(commandBlock.getCommandBlock().getName());
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public void setCommand(String command) {
        this.command = command != null ? command : "";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name != null ? name : "@";
    }

    @Override
    public void applyTo(CommandBlockEntity commandBlock) {
        super.applyTo(commandBlock);

        commandBlock.getCommandBlock().setCommand(command);
        commandBlock.getCommandBlock().setName(CraftChatMessage.fromStringOrNull(name));
    }
}
