package net.minecraft.advancements;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.apache.commons.lang3.ArrayUtils;

public class Advancement {

    private final Advancement parent;
    private final DisplayInfo display;
    private final AdvancementRewards rewards;
    private final ResourceLocation id;
    private final Map<String, Criterion> criteria;
    private final String[][] requirements;
    private final Set<Advancement> children = Sets.newLinkedHashSet();
    private final Component chatComponent;
    public final org.bukkit.advancement.Advancement bukkit = new org.bukkit.craftbukkit.advancement.CraftAdvancement(this); // CraftBukkit

    public Advancement(ResourceLocation minecraftkey, @Nullable Advancement advancement, @Nullable DisplayInfo advancementdisplay, AdvancementRewards advancementrewards, Map<String, Criterion> map, String[][] astring) {
        this.id = minecraftkey;
        this.display = advancementdisplay;
        this.criteria = ImmutableMap.copyOf(map);
        this.parent = advancement;
        this.rewards = advancementrewards;
        this.requirements = astring;
        if (advancement != null) {
            advancement.addChild(this);
        }

        if (advancementdisplay == null) {
            this.chatComponent = new TextComponent(minecraftkey.toString());
        } else {
            Component ichatbasecomponent = advancementdisplay.getTitle();
            ChatFormatting enumchatformat = advancementdisplay.getFrame().getChatColor();
            MutableComponent ichatmutablecomponent = ComponentUtils.mergeStyles(ichatbasecomponent.copy(), Style.EMPTY.withColor(enumchatformat)).append("\n").append(advancementdisplay.getDescription());
            MutableComponent ichatmutablecomponent1 = ichatbasecomponent.copy().withStyle((chatmodifier) -> {
                return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ichatmutablecomponent));
            });

            this.chatComponent = (new TextComponent("[")).append(ichatmutablecomponent1).append("]").withStyle(enumchatformat);
        }

    }

    public Advancement.SerializedAdvancement a() {
        return new Advancement.SerializedAdvancement(this.parent == null ? null : this.parent.getId(), this.display, this.rewards, this.criteria, this.requirements);
    }

    @Nullable
    public Advancement getParent() {
        return this.parent;
    }

    @Nullable
    public DisplayInfo getDisplay() {
        return this.display;
    }

    public AdvancementRewards getRewards() {
        return this.rewards;
    }

    public String toString() {
        return "SimpleAdvancement{id=" + this.getId() + ", parent=" + (this.parent == null ? "null" : this.parent.getId()) + ", display=" + this.display + ", rewards=" + this.rewards + ", criteria=" + this.criteria + ", requirements=" + Arrays.deepToString(this.requirements) + '}';
    }

    public Iterable<Advancement> getChildren() {
        return this.children;
    }

    public Map<String, Criterion> getCriteria() {
        return this.criteria;
    }

    public void addChild(Advancement advancement) {
        this.children.add(advancement);
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (!(object instanceof Advancement)) {
            return false;
        } else {
            Advancement advancement = (Advancement) object;

            return this.id.equals(advancement.id);
        }
    }

    public int hashCode() {
        return this.id.hashCode();
    }

    public String[][] getRequirements() {
        return this.requirements;
    }

    public Component getChatComponent() {
        return this.chatComponent;
    }

    public static class SerializedAdvancement {

        private ResourceLocation a;
        private Advancement b;
        private DisplayInfo c;
        private AdvancementRewards d;
        private Map<String, Criterion> e;
        private String[][] f;
        private RequirementsStrategy g;

        private SerializedAdvancement(@Nullable ResourceLocation minecraftkey, @Nullable DisplayInfo advancementdisplay, AdvancementRewards advancementrewards, Map<String, Criterion> map, String[][] astring) {
            this.d = AdvancementRewards.EMPTY;
            this.e = Maps.newLinkedHashMap();
            this.g = RequirementsStrategy.AND;
            this.a = minecraftkey;
            this.c = advancementdisplay;
            this.d = advancementrewards;
            this.e = map;
            this.f = astring;
        }

        private SerializedAdvancement() {
            this.d = AdvancementRewards.EMPTY;
            this.e = Maps.newLinkedHashMap();
            this.g = RequirementsStrategy.AND;
        }

        public static Advancement.SerializedAdvancement a() {
            return new Advancement.SerializedAdvancement();
        }

        public Advancement.SerializedAdvancement a(Advancement advancement) {
            this.b = advancement;
            return this;
        }

        public Advancement.SerializedAdvancement a(ResourceLocation minecraftkey) {
            this.a = minecraftkey;
            return this;
        }

        public Advancement.SerializedAdvancement a(ItemStack itemstack, Component ichatbasecomponent, Component ichatbasecomponent1, @Nullable ResourceLocation minecraftkey, FrameType advancementframetype, boolean flag, boolean flag1, boolean flag2) {
            return this.a(new DisplayInfo(itemstack, ichatbasecomponent, ichatbasecomponent1, minecraftkey, advancementframetype, flag, flag1, flag2));
        }

        public Advancement.SerializedAdvancement a(ItemLike imaterial, Component ichatbasecomponent, Component ichatbasecomponent1, @Nullable ResourceLocation minecraftkey, FrameType advancementframetype, boolean flag, boolean flag1, boolean flag2) {
            return this.a(new DisplayInfo(new ItemStack(imaterial.asItem()), ichatbasecomponent, ichatbasecomponent1, minecraftkey, advancementframetype, flag, flag1, flag2));
        }

        public Advancement.SerializedAdvancement a(DisplayInfo advancementdisplay) {
            this.c = advancementdisplay;
            return this;
        }

        public Advancement.SerializedAdvancement a(AdvancementRewards.Builder advancementrewards_a) {
            return this.a(advancementrewards_a.build());
        }

        public Advancement.SerializedAdvancement a(AdvancementRewards advancementrewards) {
            this.d = advancementrewards;
            return this;
        }

        public Advancement.SerializedAdvancement a(String s, CriterionTriggerInstance criterioninstance) {
            return this.a(s, new Criterion(criterioninstance));
        }

        public Advancement.SerializedAdvancement a(String s, Criterion criterion) {
            if (this.e.containsKey(s)) {
                throw new IllegalArgumentException("Duplicate criterion " + s);
            } else {
                this.e.put(s, criterion);
                return this;
            }
        }

        public Advancement.SerializedAdvancement a(RequirementsStrategy advancementrequirements) {
            this.g = advancementrequirements;
            return this;
        }

        public boolean a(Function<ResourceLocation, Advancement> function) {
            if (this.a == null) {
                return true;
            } else {
                if (this.b == null) {
                    this.b = (Advancement) function.apply(this.a);
                }

                return this.b != null;
            }
        }

        public Advancement b(ResourceLocation minecraftkey) {
            if (!this.a((Function<ResourceLocation, Advancement>) (minecraftkey1) -> { // CraftBukkit - decompile error
                return null;
            })) {
                throw new IllegalStateException("Tried to build incomplete advancement!");
            } else {
                if (this.f == null) {
                    this.f = this.g.createRequirements(this.e.keySet());
                }

                return new Advancement(minecraftkey, this.b, this.c, this.d, this.e, this.f);
            }
        }

        public Advancement a(Consumer<Advancement> consumer, String s) {
            Advancement advancement = this.b(new ResourceLocation(s));

            consumer.accept(advancement);
            return advancement;
        }

        public JsonObject b() {
            if (this.f == null) {
                this.f = this.g.createRequirements(this.e.keySet());
            }

            JsonObject jsonobject = new JsonObject();

            if (this.b != null) {
                jsonobject.addProperty("parent", this.b.getId().toString());
            } else if (this.a != null) {
                jsonobject.addProperty("parent", this.a.toString());
            }

            if (this.c != null) {
                jsonobject.add("display", this.c.serializeToJson());
            }

            jsonobject.add("rewards", this.d.serializeToJson());
            JsonObject jsonobject1 = new JsonObject();
            Iterator iterator = this.e.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<String, Criterion> entry = (Entry) iterator.next();

                jsonobject1.add((String) entry.getKey(), ((Criterion) entry.getValue()).serializeToJson());
            }

            jsonobject.add("criteria", jsonobject1);
            JsonArray jsonarray = new JsonArray();
            String[][] astring = this.f;
            int i = astring.length;

            for (int j = 0; j < i; ++j) {
                String[] astring1 = astring[j];
                JsonArray jsonarray1 = new JsonArray();
                String[] astring2 = astring1;
                int k = astring1.length;

                for (int l = 0; l < k; ++l) {
                    String s = astring2[l];

                    jsonarray1.add(s);
                }

                jsonarray.add(jsonarray1);
            }

            jsonobject.add("requirements", jsonarray);
            return jsonobject;
        }

        public void a(FriendlyByteBuf packetdataserializer) {
            if (this.a == null) {
                packetdataserializer.writeBoolean(false);
            } else {
                packetdataserializer.writeBoolean(true);
                packetdataserializer.writeResourceLocation(this.a);
            }

            if (this.c == null) {
                packetdataserializer.writeBoolean(false);
            } else {
                packetdataserializer.writeBoolean(true);
                this.c.serializeToNetwork(packetdataserializer);
            }

            Criterion.serializeToNetwork(this.e, packetdataserializer);
            packetdataserializer.writeVarInt(this.f.length);
            String[][] astring = this.f;
            int i = astring.length;

            for (int j = 0; j < i; ++j) {
                String[] astring1 = astring[j];

                packetdataserializer.writeVarInt(astring1.length);
                String[] astring2 = astring1;
                int k = astring1.length;

                for (int l = 0; l < k; ++l) {
                    String s = astring2[l];

                    packetdataserializer.writeUtf(s);
                }
            }

        }

        public String toString() {
            return "Task Advancement{parentId=" + this.a + ", display=" + this.c + ", rewards=" + this.d + ", criteria=" + this.e + ", requirements=" + Arrays.deepToString(this.f) + '}';
        }

        public static Advancement.SerializedAdvancement a(JsonObject jsonobject, DeserializationContext lootdeserializationcontext) {
            ResourceLocation minecraftkey = jsonobject.has("parent") ? new ResourceLocation(GsonHelper.getAsString(jsonobject, "parent")) : null;
            DisplayInfo advancementdisplay = jsonobject.has("display") ? DisplayInfo.fromJson(GsonHelper.getAsJsonObject(jsonobject, "display")) : null;
            AdvancementRewards advancementrewards = jsonobject.has("rewards") ? AdvancementRewards.deserialize(GsonHelper.getAsJsonObject(jsonobject, "rewards")) : AdvancementRewards.EMPTY;
            Map<String, Criterion> map = Criterion.criteriaFromJson(GsonHelper.getAsJsonObject(jsonobject, "criteria"), lootdeserializationcontext);

            if (map.isEmpty()) {
                throw new JsonSyntaxException("Advancement criteria cannot be empty");
            } else {
                JsonArray jsonarray = GsonHelper.getAsJsonArray(jsonobject, "requirements", new JsonArray());
                String[][] astring = new String[jsonarray.size()][];

                int i;
                int j;

                for (i = 0; i < jsonarray.size(); ++i) {
                    JsonArray jsonarray1 = GsonHelper.convertToJsonArray(jsonarray.get(i), "requirements[" + i + "]");

                    astring[i] = new String[jsonarray1.size()];

                    for (j = 0; j < jsonarray1.size(); ++j) {
                        astring[i][j] = GsonHelper.convertToString(jsonarray1.get(j), "requirements[" + i + "][" + j + "]");
                    }
                }

                if (astring.length == 0) {
                    astring = new String[map.size()][];
                    i = 0;

                    String s;

                    for (Iterator iterator = map.keySet().iterator(); iterator.hasNext(); astring[i++] = new String[]{s}) {
                        s = (String) iterator.next();
                    }
                }

                String[][] astring1 = astring;
                int k = astring.length;

                int l;

                for (j = 0; j < k; ++j) {
                    String[] astring2 = astring1[j];

                    if (astring2.length == 0 && map.isEmpty()) {
                        throw new JsonSyntaxException("Requirement entry cannot be empty");
                    }

                    String[] astring3 = astring2;

                    l = astring2.length;

                    for (int i1 = 0; i1 < l; ++i1) {
                        String s1 = astring3[i1];

                        if (!map.containsKey(s1)) {
                            throw new JsonSyntaxException("Unknown required criterion '" + s1 + "'");
                        }
                    }
                }

                Iterator iterator1 = map.keySet().iterator();

                while (iterator1.hasNext()) {
                    String s2 = (String) iterator1.next();
                    boolean flag = false;
                    String[][] astring4 = astring;
                    int j1 = astring.length;

                    l = 0;

                    while (true) {
                        if (l < j1) {
                            String[] astring5 = astring4[l];

                            if (!ArrayUtils.contains(astring5, s2)) {
                                ++l;
                                continue;
                            }

                            flag = true;
                        }

                        if (!flag) {
                            throw new JsonSyntaxException("Criterion '" + s2 + "' isn't a requirement for completion. This isn't supported behaviour, all criteria must be required.");
                        }
                        break;
                    }
                }

                return new Advancement.SerializedAdvancement(minecraftkey, advancementdisplay, advancementrewards, map, astring);
            }
        }

        public static Advancement.SerializedAdvancement b(FriendlyByteBuf packetdataserializer) {
            ResourceLocation minecraftkey = packetdataserializer.readBoolean() ? packetdataserializer.readResourceLocation() : null;
            DisplayInfo advancementdisplay = packetdataserializer.readBoolean() ? DisplayInfo.fromNetwork(packetdataserializer) : null;
            Map<String, Criterion> map = Criterion.criteriaFromNetwork(packetdataserializer);
            String[][] astring = new String[packetdataserializer.readVarInt()][];

            for (int i = 0; i < astring.length; ++i) {
                astring[i] = new String[packetdataserializer.readVarInt()];

                for (int j = 0; j < astring[i].length; ++j) {
                    astring[i][j] = packetdataserializer.readUtf(32767);
                }
            }

            return new Advancement.SerializedAdvancement(minecraftkey, advancementdisplay, AdvancementRewards.EMPTY, map, astring);
        }

        public Map<String, Criterion> c() {
            return this.e;
        }
    }
}
