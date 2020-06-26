package net.minecraft.world.level.block.entity;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BannerBlockEntity extends BlockEntity implements Nameable {

    @Nullable
    private Component name;
    @Nullable
    public DyeColor baseColor;
    @Nullable
    public ListTag itemPatterns;
    private boolean receivedData;
    @Nullable
    private List<Pair<BannerPattern, DyeColor>> patterns;

    public BannerBlockEntity() {
        super(BlockEntityType.BANNER);
        this.baseColor = DyeColor.WHITE;
    }

    public BannerBlockEntity(DyeColor enumcolor) {
        this();
        this.baseColor = enumcolor;
    }

    @Override
    public Component getName() {
        return (Component) (this.name != null ? this.name : new TranslatableComponent("block.minecraft.banner"));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    public void setCustomName(Component ichatbasecomponent) {
        this.name = ichatbasecomponent;
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (this.itemPatterns != null) {
            nbttagcompound.put("Patterns", this.itemPatterns);
        }

        if (this.name != null) {
            nbttagcompound.putString("CustomName", Component.ChatSerializer.a(this.name));
        }

        return nbttagcompound;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        if (nbttagcompound.contains("CustomName", 8)) {
            this.name = Component.ChatSerializer.a(nbttagcompound.getString("CustomName"));
        }

        if (this.hasLevel()) {
            this.baseColor = ((AbstractBannerBlock) this.getBlock().getBlock()).getColor();
        } else {
            this.baseColor = null;
        }

        this.itemPatterns = nbttagcompound.getList("Patterns", 10);
        // CraftBukkit start
        while (this.itemPatterns.size() > 20) {
            this.itemPatterns.remove(20);
        }
        // CraftBukkit end
        this.patterns = null;
        this.receivedData = true;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 6, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    public static int getPatternCount(ItemStack itemstack) {
        CompoundTag nbttagcompound = itemstack.getTagElement("BlockEntityTag");

        return nbttagcompound != null && nbttagcompound.contains("Patterns") ? nbttagcompound.getList("Patterns", 10).size() : 0;
    }

    public static void removeLastPattern(ItemStack itemstack) {
        CompoundTag nbttagcompound = itemstack.getTagElement("BlockEntityTag");

        if (nbttagcompound != null && nbttagcompound.contains("Patterns", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("Patterns", 10);

            if (!nbttaglist.isEmpty()) {
                nbttaglist.remove(nbttaglist.size() - 1);
                if (nbttaglist.isEmpty()) {
                    itemstack.removeTagKey("BlockEntityTag");
                }

            }
        }
    }

    public DyeColor getBaseColor(Supplier<BlockState> supplier) {
        if (this.baseColor == null) {
            this.baseColor = ((AbstractBannerBlock) ((BlockState) supplier.get()).getBlock()).getColor();
        }

        return this.baseColor;
    }
}
