package net.minecraft.server;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameRules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerAdvancements {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = (new GsonBuilder()).registerTypeAdapter(AdvancementProgress.class, new AdvancementProgress.Serializer()).registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).setPrettyPrinting().create();
    private static final TypeToken<Map<ResourceLocation, AdvancementProgress>> TYPE_TOKEN = new TypeToken<Map<ResourceLocation, AdvancementProgress>>() {
    };
    private final DataFixer dataFixer;
    private final PlayerList playerList;
    private final File file;
    public final Map<Advancement, AdvancementProgress> advancements = Maps.newLinkedHashMap();
    private final Set<Advancement> visible = Sets.newLinkedHashSet();
    private final Set<Advancement> visibilityChanged = Sets.newLinkedHashSet();
    private final Set<Advancement> progressChanged = Sets.newLinkedHashSet();
    private ServerPlayer player;
    @Nullable
    private Advancement lastSelectedTab;
    private boolean isFirstPacket = true;

    public PlayerAdvancements(DataFixer datafixer, PlayerList playerlist, ServerAdvancementManager advancementdataworld, File file, ServerPlayer entityplayer) {
        this.dataFixer = datafixer;
        this.playerList = playerlist;
        this.file = file;
        this.player = entityplayer;
        this.load(advancementdataworld);
    }

    public void setPlayer(ServerPlayer entityplayer) {
        this.player = entityplayer;
    }

    public void stopListening() {
        Iterator iterator = CriteriaTriggers.all().iterator();

        while (iterator.hasNext()) {
            CriterionTrigger<?> criteriontrigger = (CriterionTrigger) iterator.next();

            criteriontrigger.removePlayerListeners(this);
        }

    }

    public void reload(ServerAdvancementManager advancementdataworld) {
        this.stopListening();
        this.advancements.clear();
        this.visible.clear();
        this.visibilityChanged.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.lastSelectedTab = null;
        this.load(advancementdataworld);
    }

    private void registerListeners(ServerAdvancementManager advancementdataworld) {
        Iterator iterator = advancementdataworld.getAllAdvancements().iterator();

        while (iterator.hasNext()) {
            Advancement advancement = (Advancement) iterator.next();

            this.registerListeners(advancement);
        }

    }

    private void ensureAllVisible() {
        List<Advancement> list = Lists.newArrayList();
        Iterator iterator = this.advancements.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Advancement, AdvancementProgress> entry = (Entry) iterator.next();

            if (((AdvancementProgress) entry.getValue()).isDone()) {
                list.add(entry.getKey());
                this.progressChanged.add(entry.getKey());
            }
        }

        iterator = list.iterator();

        while (iterator.hasNext()) {
            Advancement advancement = (Advancement) iterator.next();

            this.ensureVisibility(advancement);
        }

    }

    private void checkForAutomaticTriggers(ServerAdvancementManager advancementdataworld) {
        Iterator iterator = advancementdataworld.getAllAdvancements().iterator();

        while (iterator.hasNext()) {
            Advancement advancement = (Advancement) iterator.next();

            if (advancement.getCriteria().isEmpty()) {
                this.award(advancement, "");
                advancement.getRewards().grant(this.player);
            }
        }

    }

    private void load(ServerAdvancementManager advancementdataworld) {
        if (this.file.isFile()) {
            try {
                JsonReader jsonreader = new JsonReader(new StringReader(Files.toString(this.file, StandardCharsets.UTF_8)));
                Throwable throwable = null;

                try {
                    jsonreader.setLenient(false);
                    Dynamic<JsonElement> dynamic = new Dynamic(JsonOps.INSTANCE, Streams.parse(jsonreader));

                    if (!dynamic.get("DataVersion").asNumber().result().isPresent()) {
                        dynamic = dynamic.set("DataVersion", dynamic.createInt(1343));
                    }

                    dynamic = this.dataFixer.update(DataFixTypes.ADVANCEMENTS.getType(), dynamic, dynamic.get("DataVersion").asInt(0), SharedConstants.getCurrentVersion().getWorldVersion());
                    dynamic = dynamic.remove("DataVersion");
                    Map<ResourceLocation, AdvancementProgress> map = (Map) PlayerAdvancements.GSON.getAdapter(PlayerAdvancements.TYPE_TOKEN).fromJsonTree((JsonElement) dynamic.getValue());

                    if (map == null) {
                        throw new JsonParseException("Found null for advancements");
                    }

                    Stream<Entry<ResourceLocation, AdvancementProgress>> stream = map.entrySet().stream().sorted(Comparator.comparing(Entry::getValue));
                    Iterator iterator = ((List) stream.collect(Collectors.toList())).iterator();

                    while (iterator.hasNext()) {
                        Entry<ResourceLocation, AdvancementProgress> entry = (Entry) iterator.next();
                        Advancement advancement = advancementdataworld.getAdvancement((ResourceLocation) entry.getKey());

                        if (advancement == null) {
                            // CraftBukkit start
                            if (entry.getKey().getNamespace().equals("minecraft")) {
                                PlayerAdvancements.LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", entry.getKey(), this.file);
                            }
                            // CraftBukkit end
                        } else {
                            this.startProgress(advancement, (AdvancementProgress) entry.getValue());
                        }
                    }
                } catch (Throwable throwable1) {
                    throwable = throwable1;
                    throw throwable1;
                } finally {
                    if (jsonreader != null) {
                        if (throwable != null) {
                            try {
                                jsonreader.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        } else {
                            jsonreader.close();
                        }
                    }

                }
            } catch (JsonParseException jsonparseexception) {
                PlayerAdvancements.LOGGER.error("Couldn't parse player advancements in {}", this.file, jsonparseexception);
            } catch (IOException ioexception) {
                PlayerAdvancements.LOGGER.error("Couldn't access player advancements in {}", this.file, ioexception);
            }
        }

        this.checkForAutomaticTriggers(advancementdataworld);
        this.ensureAllVisible();
        this.registerListeners(advancementdataworld);
    }

    public void save() {
        if (org.spigotmc.SpigotConfig.disableAdvancementSaving) return; // Spigot
        Map<ResourceLocation, AdvancementProgress> map = Maps.newHashMap();
        Iterator iterator = this.advancements.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Advancement, AdvancementProgress> entry = (Entry) iterator.next();
            AdvancementProgress advancementprogress = (AdvancementProgress) entry.getValue();

            if (advancementprogress.hasProgress()) {
                map.put(((Advancement) entry.getKey()).getId(), advancementprogress);
            }
        }

        if (this.file.getParentFile() != null) {
            this.file.getParentFile().mkdirs();
        }

        JsonElement jsonelement = PlayerAdvancements.GSON.toJsonTree(map);

        jsonelement.getAsJsonObject().addProperty("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());

        try {
            FileOutputStream fileoutputstream = new FileOutputStream(this.file);
            Throwable throwable = null;

            try {
                OutputStreamWriter outputstreamwriter = new OutputStreamWriter(fileoutputstream, Charsets.UTF_8.newEncoder());
                Throwable throwable1 = null;

                try {
                    PlayerAdvancements.GSON.toJson(jsonelement, outputstreamwriter);
                } catch (Throwable throwable2) {
                    throwable1 = throwable2;
                    throw throwable2;
                } finally {
                    if (outputstreamwriter != null) {
                        if (throwable1 != null) {
                            try {
                                outputstreamwriter.close();
                            } catch (Throwable throwable3) {
                                throwable1.addSuppressed(throwable3);
                            }
                        } else {
                            outputstreamwriter.close();
                        }
                    }

                }
            } catch (Throwable throwable4) {
                throwable = throwable4;
                throw throwable4;
            } finally {
                if (fileoutputstream != null) {
                    if (throwable != null) {
                        try {
                            fileoutputstream.close();
                        } catch (Throwable throwable5) {
                            throwable.addSuppressed(throwable5);
                        }
                    } else {
                        fileoutputstream.close();
                    }
                }

            }
        } catch (IOException ioexception) {
            PlayerAdvancements.LOGGER.error("Couldn't save player advancements to {}", this.file, ioexception);
        }

    }

    public boolean award(Advancement advancement, String s) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.grantProgress(s)) {
            this.unregisterListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
            if (!flag1 && advancementprogress.isDone()) {
                this.player.level.getServerOH().getPluginManager().callEvent(new org.bukkit.event.player.PlayerAdvancementDoneEvent(this.player.getBukkitEntity(), advancement.bukkit)); // CraftBukkit
                advancement.getRewards().grant(this.player);
                if (advancement.getDisplay() != null && advancement.getDisplay().shouldAnnounceChat() && this.player.level.getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
                    this.playerList.broadcastMessage(new TranslatableComponent("chat.type.advancement." + advancement.getDisplay().getFrame().getName(), new Object[]{this.player.getDisplayName(), advancement.getChatComponent()}), ChatType.SYSTEM, Util.NIL_UUID);
                }
            }
        }

        if (advancementprogress.isDone()) {
            this.ensureVisibility(advancement);
        }

        return flag;
    }

    public boolean revoke(Advancement advancement, String s) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);

        if (advancementprogress.revokeProgress(s)) {
            this.registerListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
        }

        if (!advancementprogress.hasProgress()) {
            this.ensureVisibility(advancement);
        }

        return flag;
    }

    private void registerListeners(Advancement advancement) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);

        if (!advancementprogress.isDone()) {
            Iterator iterator = advancement.getCriteria().entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<String, Criterion> entry = (Entry) iterator.next();
                CriterionProgress criterionprogress = advancementprogress.getCriterion((String) entry.getKey());

                if (criterionprogress != null && !criterionprogress.isDone()) {
                    CriterionTriggerInstance criterioninstance = ((Criterion) entry.getValue()).getTrigger();

                    if (criterioninstance != null) {
                        CriterionTrigger<CriterionTriggerInstance> criteriontrigger = CriteriaTriggers.getCriterion(criterioninstance.getCriterion());

                        if (criteriontrigger != null) {
                            criteriontrigger.addPlayerListener(this, new CriterionTrigger.Listener<>(criterioninstance, advancement, (String) entry.getKey()));
                        }
                    }
                }
            }

        }
    }

    private void unregisterListeners(Advancement advancement) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);
        Iterator iterator = advancement.getCriteria().entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, Criterion> entry = (Entry) iterator.next();
            CriterionProgress criterionprogress = advancementprogress.getCriterion((String) entry.getKey());

            if (criterionprogress != null && (criterionprogress.isDone() || advancementprogress.isDone())) {
                CriterionTriggerInstance criterioninstance = ((Criterion) entry.getValue()).getTrigger();

                if (criterioninstance != null) {
                    CriterionTrigger<CriterionTriggerInstance> criteriontrigger = CriteriaTriggers.getCriterion(criterioninstance.getCriterion());

                    if (criteriontrigger != null) {
                        criteriontrigger.removePlayerListener(this, new CriterionTrigger.Listener<>(criterioninstance, advancement, (String) entry.getKey()));
                    }
                }
            }
        }

    }

    public void flushDirty(ServerPlayer entityplayer) {
        if (this.isFirstPacket || !this.visibilityChanged.isEmpty() || !this.progressChanged.isEmpty()) {
            Map<ResourceLocation, AdvancementProgress> map = Maps.newHashMap();
            Set<Advancement> set = Sets.newLinkedHashSet();
            Set<ResourceLocation> set1 = Sets.newLinkedHashSet();
            Iterator iterator = this.progressChanged.iterator();

            Advancement advancement;

            while (iterator.hasNext()) {
                advancement = (Advancement) iterator.next();
                if (this.visible.contains(advancement)) {
                    map.put(advancement.getId(), this.advancements.get(advancement));
                }
            }

            iterator = this.visibilityChanged.iterator();

            while (iterator.hasNext()) {
                advancement = (Advancement) iterator.next();
                if (this.visible.contains(advancement)) {
                    set.add(advancement);
                } else {
                    set1.add(advancement.getId());
                }
            }

            if (this.isFirstPacket || !map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
                entityplayer.connection.sendPacket(new ClientboundUpdateAdvancementsPacket(this.isFirstPacket, set, set1, map));
                this.visibilityChanged.clear();
                this.progressChanged.clear();
            }
        }

        this.isFirstPacket = false;
    }

    public void setSelectedTab(@Nullable Advancement advancement) {
        Advancement advancement1 = this.lastSelectedTab;

        if (advancement != null && advancement.getParent() == null && advancement.getDisplay() != null) {
            this.lastSelectedTab = advancement;
        } else {
            this.lastSelectedTab = null;
        }

        if (advancement1 != this.lastSelectedTab) {
            this.player.connection.sendPacket(new ClientboundSelectAdvancementsTabPacket(this.lastSelectedTab == null ? null : this.lastSelectedTab.getId()));
        }

    }

    public AdvancementProgress getOrStartProgress(Advancement advancement) {
        AdvancementProgress advancementprogress = (AdvancementProgress) this.advancements.get(advancement);

        if (advancementprogress == null) {
            advancementprogress = new AdvancementProgress();
            this.startProgress(advancement, advancementprogress);
        }

        return advancementprogress;
    }

    private void startProgress(Advancement advancement, AdvancementProgress advancementprogress) {
        advancementprogress.update(advancement.getCriteria(), advancement.getRequirements());
        this.advancements.put(advancement, advancementprogress);
    }

    private void ensureVisibility(Advancement advancement) {
        boolean flag = this.shouldBeVisible(advancement);
        boolean flag1 = this.visible.contains(advancement);

        if (flag && !flag1) {
            this.visible.add(advancement);
            this.visibilityChanged.add(advancement);
            if (this.advancements.containsKey(advancement)) {
                this.progressChanged.add(advancement);
            }
        } else if (!flag && flag1) {
            this.visible.remove(advancement);
            this.visibilityChanged.add(advancement);
        }

        if (flag != flag1 && advancement.getParent() != null) {
            this.ensureVisibility(advancement.getParent());
        }

        Iterator iterator = advancement.getChildren().iterator();

        while (iterator.hasNext()) {
            Advancement advancement1 = (Advancement) iterator.next();

            this.ensureVisibility(advancement1);
        }

    }

    private boolean shouldBeVisible(Advancement advancement) {
        for (int i = 0; advancement != null && i <= 2; ++i) {
            if (i == 0 && this.hasCompletedChildrenOrSelf(advancement)) {
                return true;
            }

            if (advancement.getDisplay() == null) {
                return false;
            }

            AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);

            if (advancementprogress.isDone()) {
                return true;
            }

            if (advancement.getDisplay().isHidden()) {
                return false;
            }

            advancement = advancement.getParent();
        }

        return false;
    }

    private boolean hasCompletedChildrenOrSelf(Advancement advancement) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);

        if (advancementprogress.isDone()) {
            return true;
        } else {
            Iterator iterator = advancement.getChildren().iterator();

            Advancement advancement1;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                advancement1 = (Advancement) iterator.next();
            } while (!this.hasCompletedChildrenOrSelf(advancement1));

            return true;
        }
    }
}
