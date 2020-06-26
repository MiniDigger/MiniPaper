package net.minecraft.server.bossevents;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
// CraftBukkit start
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.craftbukkit.boss.CraftKeyedBossbar;
// CraftBukkit end

public class CustomBossEvent extends ServerBossEvent {

    private final ResourceLocation id;
    private final Set<UUID> players = Sets.newHashSet();
    private int value;
    private int max = 100;
    // CraftBukkit start
    private KeyedBossBar bossBar;

    public KeyedBossBar getBukkitEntity() {
        if (bossBar == null) {
            bossBar = new CraftKeyedBossbar(this);
        }
        return bossBar;
    }
    // CraftBukkit end

    public CustomBossEvent(ResourceLocation minecraftkey, Component ichatbasecomponent) {
        super(ichatbasecomponent, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.id = minecraftkey;
        this.setPercent(0.0F);
    }

    public ResourceLocation getTextId() {
        return this.id;
    }

    @Override
    public void addPlayer(ServerPlayer entityplayer) {
        super.addPlayer(entityplayer);
        this.players.add(entityplayer.getUUID());
    }

    public void addOfflinePlayer(UUID uuid) {
        this.players.add(uuid);
    }

    @Override
    public void removePlayer(ServerPlayer entityplayer) {
        super.removePlayer(entityplayer);
        this.players.remove(entityplayer.getUUID());
    }

    @Override
    public void removeAllPlayers() {
        super.removeAllPlayers();
        this.players.clear();
    }

    public int getValue() {
        return this.value;
    }

    public int getMax() {
        return this.max;
    }

    public void setValue(int i) {
        this.value = i;
        this.setPercent(Mth.clamp((float) i / (float) this.max, 0.0F, 1.0F));
    }

    public void setMax(int i) {
        this.max = i;
        this.setPercent(Mth.clamp((float) this.value / (float) i, 0.0F, 1.0F));
    }

    public final Component getDisplayName() {
        return ComponentUtils.wrapInSquareBrackets(this.getName()).withStyle((chatmodifier) -> {
            return chatmodifier.withColor(this.getColor().getFormatting()).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(this.getTextId().toString()))).withInsertion(this.getTextId().toString());
        });
    }

    public boolean setPlayers(Collection<ServerPlayer> collection) {
        Set<UUID> set = Sets.newHashSet();
        Set<ServerPlayer> set1 = Sets.newHashSet();
        Iterator iterator = this.players.iterator();

        UUID uuid;
        boolean flag;
        Iterator iterator1;

        while (iterator.hasNext()) {
            uuid = (UUID) iterator.next();
            flag = false;
            iterator1 = collection.iterator();

            while (true) {
                if (iterator1.hasNext()) {
                    ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

                    if (!entityplayer.getUUID().equals(uuid)) {
                        continue;
                    }

                    flag = true;
                }

                if (!flag) {
                    set.add(uuid);
                }
                break;
            }
        }

        iterator = collection.iterator();

        ServerPlayer entityplayer1;

        while (iterator.hasNext()) {
            entityplayer1 = (ServerPlayer) iterator.next();
            flag = false;
            iterator1 = this.players.iterator();

            while (true) {
                if (iterator1.hasNext()) {
                    UUID uuid1 = (UUID) iterator1.next();

                    if (!entityplayer1.getUUID().equals(uuid1)) {
                        continue;
                    }

                    flag = true;
                }

                if (!flag) {
                    set1.add(entityplayer1);
                }
                break;
            }
        }

        iterator = set.iterator();

        while (iterator.hasNext()) {
            uuid = (UUID) iterator.next();
            Iterator iterator2 = this.getPlayers().iterator();

            while (true) {
                if (iterator2.hasNext()) {
                    ServerPlayer entityplayer2 = (ServerPlayer) iterator2.next();

                    if (!entityplayer2.getUUID().equals(uuid)) {
                        continue;
                    }

                    this.removePlayer(entityplayer2);
                }

                this.players.remove(uuid);
                break;
            }
        }

        iterator = set1.iterator();

        while (iterator.hasNext()) {
            entityplayer1 = (ServerPlayer) iterator.next();
            this.addPlayer(entityplayer1);
        }

        return !set.isEmpty() || !set1.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.putString("Name", Component.ChatSerializer.a(this.name));
        nbttagcompound.putBoolean("Visible", this.isVisible());
        nbttagcompound.putInt("Value", this.value);
        nbttagcompound.putInt("Max", this.max);
        nbttagcompound.putString("Color", this.getColor().getName());
        nbttagcompound.putString("Overlay", this.getOverlay().getName());
        nbttagcompound.putBoolean("DarkenScreen", this.shouldDarkenScreen());
        nbttagcompound.putBoolean("PlayBossMusic", this.shouldPlayBossMusic());
        nbttagcompound.putBoolean("CreateWorldFog", this.shouldCreateWorldFog());
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            UUID uuid = (UUID) iterator.next();

            nbttaglist.add(NbtUtils.createUUID(uuid));
        }

        nbttagcompound.put("Players", nbttaglist);
        return nbttagcompound;
    }

    public static CustomBossEvent load(CompoundTag nbttagcompound, ResourceLocation minecraftkey) {
        CustomBossEvent bossbattlecustom = new CustomBossEvent(minecraftkey, Component.ChatSerializer.a(nbttagcompound.getString("Name")));

        bossbattlecustom.setVisible(nbttagcompound.getBoolean("Visible"));
        bossbattlecustom.setValue(nbttagcompound.getInt("Value"));
        bossbattlecustom.setMax(nbttagcompound.getInt("Max"));
        bossbattlecustom.setColor(BossEvent.BossBarColor.byName(nbttagcompound.getString("Color")));
        bossbattlecustom.setOverlay(BossEvent.BossBarOverlay.byName(nbttagcompound.getString("Overlay")));
        bossbattlecustom.setDarkenScreen(nbttagcompound.getBoolean("DarkenScreen"));
        bossbattlecustom.setPlayBossMusic(nbttagcompound.getBoolean("PlayBossMusic"));
        bossbattlecustom.setCreateWorldFog(nbttagcompound.getBoolean("CreateWorldFog"));
        ListTag nbttaglist = nbttagcompound.getList("Players", 11);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            bossbattlecustom.addOfflinePlayer(NbtUtils.loadUUID(nbttaglist.get(i)));
        }

        return bossbattlecustom;
    }

    public void onPlayerConnect(ServerPlayer entityplayer) {
        if (this.players.contains(entityplayer.getUUID())) {
            this.addPlayer(entityplayer);
        }

    }

    public void onPlayerDisconnect(ServerPlayer entityplayer) {
        super.removePlayer(entityplayer);
    }
}
