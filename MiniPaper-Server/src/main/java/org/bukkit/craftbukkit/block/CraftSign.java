package org.bukkit.craftbukkit.block;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.util.CraftChatMessage;

public class CraftSign extends CraftBlockEntityState<SignBlockEntity> implements Sign {

    private String[] lines;
    private boolean editable;

    public CraftSign(final Block block) {
        super(block, SignBlockEntity.class);
    }

    public CraftSign(final Material material, final SignBlockEntity te) {
        super(material, te);
    }

    @Override
    public void load(SignBlockEntity sign) {
        super.load(sign);

        lines = new String[sign.messages.length];
        System.arraycopy(revertComponents(sign.messages), 0, lines, 0, lines.length);
        editable = sign.isEditable;
    }

    @Override
    public String[] getLines() {
        return lines;
    }

    @Override
    public String getLine(int index) throws IndexOutOfBoundsException {
        return lines[index];
    }

    @Override
    public void setLine(int index, String line) throws IndexOutOfBoundsException {
        lines[index] = line;
    }

    @Override
    public boolean isEditable() {
        return this.editable;
    }

    @Override
    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    @Override
    public DyeColor getColor() {
        return DyeColor.getByWoolData((byte) getSnapshot().getColor().getId());
    }

    @Override
    public void setColor(DyeColor color) {
        getSnapshot().setColor(net.minecraft.world.item.DyeColor.byId(color.getWoolData()));
    }

    @Override
    public void applyTo(SignBlockEntity sign) {
        super.applyTo(sign);

        Component[] newLines = sanitizeLines(lines);
        System.arraycopy(newLines, 0, sign.messages, 0, 4);
        sign.isEditable = editable;
    }

    public static Component[] sanitizeLines(String[] lines) {
        Component[] components = new Component[4];

        for (int i = 0; i < 4; i++) {
            if (i < lines.length && lines[i] != null) {
                components[i] = CraftChatMessage.fromString(lines[i])[0];
            } else {
                components[i] = new TextComponent("");
            }
        }

        return components;
    }

    public static String[] revertComponents(Component[] components) {
        String[] lines = new String[components.length];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = revertComponent(components[i]);
        }
        return lines;
    }

    private static String revertComponent(Component component) {
        return CraftChatMessage.fromComponent(component);
    }
}
