/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftChain extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.Waterlogged {

    public CraftChain() {
        super();
    }

    public CraftChain(net.minecraft.world.level.block.state.BlockState state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.CraftWaterlogged

    private static final net.minecraft.world.level.block.state.properties.BooleanProperty WATERLOGGED = getBoolean(net.minecraft.world.level.block.ChainBlock.class, "waterlogged");

    @Override
    public boolean isWaterlogged() {
        return get(WATERLOGGED);
    }

    @Override
    public void setWaterlogged(boolean waterlogged) {
        set(WATERLOGGED, waterlogged);
    }
}