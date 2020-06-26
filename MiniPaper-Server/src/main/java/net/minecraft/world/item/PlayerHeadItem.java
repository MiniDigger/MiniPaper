package net.minecraft.world.item;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.apache.commons.lang3.StringUtils;

public class PlayerHeadItem extends StandingAndWallBlockItem {

    public PlayerHeadItem(Block block, Block block1, Item.Info item_info) {
        super(block, block1, item_info);
    }

    @Override
    public Component getName(ItemStack itemstack) {
        if (itemstack.getItem() == Items.PLAYER_HEAD && itemstack.hasTag()) {
            String s = null;
            CompoundTag nbttagcompound = itemstack.getTag();

            if (nbttagcompound.contains("SkullOwner", 8)) {
                s = nbttagcompound.getString("SkullOwner");
            } else if (nbttagcompound.contains("SkullOwner", 10)) {
                CompoundTag nbttagcompound1 = nbttagcompound.getCompound("SkullOwner");

                if (nbttagcompound1.contains("Name", 8)) {
                    s = nbttagcompound1.getString("Name");
                }
            }

            if (s != null) {
                return new TranslatableComponent(this.getDescriptionId() + ".named", new Object[]{s});
            }
        }

        return super.getName(itemstack);
    }

    @Override
    public boolean verifyTagAfterLoad(CompoundTag nbttagcompound) {
        super.verifyTagAfterLoad(nbttagcompound);
        if (nbttagcompound.contains("SkullOwner", 8) && !StringUtils.isBlank(nbttagcompound.getString("SkullOwner"))) {
            GameProfile gameprofile = new GameProfile((UUID) null, nbttagcompound.getString("SkullOwner"));

            // Spigot start
            SkullBlockEntity.b(gameprofile, new com.google.common.base.Predicate<GameProfile>() {

                @Override
                public boolean apply(GameProfile gameprofile) {
                    nbttagcompound.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), gameprofile));
                    return false;
                }
            }, false);
            // Spigot end
            return true;
        } else {
            // CraftBukkit start
            ListTag textures = nbttagcompound.getCompound("SkullOwner").getCompound("Properties").getList("textures", 10); // Safe due to method contracts
            for (int i = 0; i < textures.size(); i++) {
                if (textures.get(i) instanceof CompoundTag && !((CompoundTag) textures.get(i)).contains("Signature", 8) && ((CompoundTag) textures.get(i)).getString("Value").trim().isEmpty()) {
                    nbttagcompound.remove("SkullOwner");
                    break;
                }
            }
            // CraftBukkit end
            return false;
        }
    }
}
