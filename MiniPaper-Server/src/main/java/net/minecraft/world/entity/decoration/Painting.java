package net.minecraft.world.entity.decoration;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddPaintingPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class Painting extends HangingEntity {

    public Motive motive;

    public Painting(EntityType<? extends Painting> entitytypes, Level world) {
        super(entitytypes, world);
        // CraftBukkit start - generate a non-null painting
        List<Motive> list = Lists.newArrayList(Motive.KEBAB);
        this.motive = (Motive) list.get(this.random.nextInt(list.size()));
        // CraftBukkit end
    }

    public Painting(Level world, BlockPos blockposition, Direction enumdirection) {
        super(EntityType.PAINTING, world, blockposition);
        List<Motive> list = Lists.newArrayList();
        int i = 0;
        Iterator iterator = Registry.MOTIVE.iterator();

        Motive paintings;

        while (iterator.hasNext()) {
            paintings = (Motive) iterator.next();
            this.motive = paintings;
            this.setDirection(enumdirection);
            if (this.survives()) {
                list.add(paintings);
                int j = paintings.getWidth() * paintings.getHeight();

                if (j > i) {
                    i = j;
                }
            }
        }

        if (!list.isEmpty()) {
            iterator = list.iterator();

            while (iterator.hasNext()) {
                paintings = (Motive) iterator.next();
                if (paintings.getWidth() * paintings.getHeight() < i) {
                    iterator.remove();
                }
            }

            this.motive = (Motive) list.get(this.random.nextInt(list.size()));
        }

        this.setDirection(enumdirection);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putString("Motive", Registry.MOTIVE.getKey(this.motive).toString());
        super.addAdditionalSaveData(nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.motive = (Motive) Registry.MOTIVE.get(ResourceLocation.tryParse(nbttagcompound.getString("Motive")));
        super.readAdditionalSaveData(nbttagcompound);
    }

    @Override
    public int getWidth() {
        return this.motive == null ? 1 : this.motive.getWidth();
    }

    @Override
    public int getHeight() {
        return this.motive == null ? 1 : this.motive.getHeight();
    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (entity instanceof Player) {
                Player entityhuman = (Player) entity;

                if (entityhuman.abilities.instabuild) {
                    return;
                }
            }

            this.spawnAtLocation((ItemLike) Items.PAINTING);
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double d0, double d1, double d2, float f, float f1) {
        this.setPos(d0, d1, d2);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddPaintingPacket(this);
    }
}
