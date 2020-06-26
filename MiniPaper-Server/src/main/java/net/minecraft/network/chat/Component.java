package net.minecraft.network.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.Message;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LowerCaseEnumTypeAdapterFactory;
// CraftBukkit start
import com.google.common.collect.Streams;
import java.util.stream.Stream;

public interface Component extends Message, FormattedText, Iterable<Component> {

    default Stream<Component> stream() {
        return Streams.concat(new Stream[]{Stream.of(this), this.getSiblings().stream().flatMap(Component::stream)});
    }

    @Override
    default Iterator<Component> iterator() {
        return this.stream().iterator();
    }
    // CraftBukkit end

    Style getStyle();

    String getContents();

    @Override
    default String getString() {
        return FormattedText.super.getString();
    }

    default String getString(int i) {
        StringBuilder stringbuilder = new StringBuilder();

        this.visit((s) -> {
            int j = i - stringbuilder.length();

            if (j <= 0) {
                return Component.STOP_ITERATION;
            } else {
                stringbuilder.append(s.length() <= j ? s : s.substring(0, j));
                return Optional.empty();
            }
        });
        return stringbuilder.toString();
    }

    List<Component> getSiblings();

    MutableComponent plainCopy();

    MutableComponent copy();

    @Override
    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> ichatformatted_a) {
        Optional<T> optional = this.visitSelf(ichatformatted_a);

        if (optional.isPresent()) {
            return optional;
        } else {
            Iterator iterator = this.getSiblings().iterator();

            Optional optional1;

            do {
                if (!iterator.hasNext()) {
                    return Optional.empty();
                }

                Component ichatbasecomponent = (Component) iterator.next();

                optional1 = ichatbasecomponent.visit(ichatformatted_a);
            } while (!optional1.isPresent());

            return optional1;
        }
    }

    default <T> Optional<T> visitSelf(FormattedText.ContentConsumer<T> ichatformatted_a) {
        return ichatformatted_a.accept(this.getContents());
    }

    public static class ChatSerializer implements JsonDeserializer<MutableComponent>, JsonSerializer<Component> {

        private static final Gson a = (Gson) Util.make(() -> {
            GsonBuilder gsonbuilder = new GsonBuilder();

            gsonbuilder.disableHtmlEscaping();
            gsonbuilder.registerTypeHierarchyAdapter(Component.class, new Component.ChatSerializer());
            gsonbuilder.registerTypeHierarchyAdapter(Style.class, new Style.ChatModifierSerializer());
            gsonbuilder.registerTypeAdapterFactory(new LowerCaseEnumTypeAdapterFactory());
            return gsonbuilder.create();
        });
        private static final Field b = (Field) Util.make(() -> {
            try {
                new JsonReader(new StringReader(""));
                Field field = JsonReader.class.getDeclaredField("pos");

                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException nosuchfieldexception) {
                throw new IllegalStateException("Couldn't get field 'pos' for JsonReader", nosuchfieldexception);
            }
        });
        private static final Field c = (Field) Util.make(() -> {
            try {
                new JsonReader(new StringReader(""));
                Field field = JsonReader.class.getDeclaredField("lineStart");

                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException nosuchfieldexception) {
                throw new IllegalStateException("Couldn't get field 'lineStart' for JsonReader", nosuchfieldexception);
            }
        });

        public ChatSerializer() {}

        public MutableComponent deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            if (jsonelement.isJsonPrimitive()) {
                return new TextComponent(jsonelement.getAsString());
            } else if (!jsonelement.isJsonObject()) {
                if (jsonelement.isJsonArray()) {
                    JsonArray jsonarray = jsonelement.getAsJsonArray();
                    MutableComponent ichatmutablecomponent = null;
                    Iterator iterator = jsonarray.iterator();

                    while (iterator.hasNext()) {
                        JsonElement jsonelement1 = (JsonElement) iterator.next();
                        MutableComponent ichatmutablecomponent1 = this.deserialize(jsonelement1, jsonelement1.getClass(), jsondeserializationcontext);

                        if (ichatmutablecomponent == null) {
                            ichatmutablecomponent = ichatmutablecomponent1;
                        } else {
                            ichatmutablecomponent.append(ichatmutablecomponent1);
                        }
                    }

                    return ichatmutablecomponent;
                } else {
                    throw new JsonParseException("Don't know how to turn " + jsonelement + " into a Component");
                }
            } else {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                Object object;

                if (jsonobject.has("text")) {
                    object = new TextComponent(GsonHelper.getAsString(jsonobject, "text"));
                } else {
                    String s;

                    if (jsonobject.has("translate")) {
                        s = GsonHelper.getAsString(jsonobject, "translate");
                        if (jsonobject.has("with")) {
                            JsonArray jsonarray1 = GsonHelper.getAsJsonArray(jsonobject, "with");
                            Object[] aobject = new Object[jsonarray1.size()];

                            for (int i = 0; i < aobject.length; ++i) {
                                aobject[i] = this.deserialize(jsonarray1.get(i), type, jsondeserializationcontext);
                                if (aobject[i] instanceof TextComponent) {
                                    TextComponent chatcomponenttext = (TextComponent) aobject[i];

                                    if (chatcomponenttext.getStyle().isEmpty() && chatcomponenttext.getSiblings().isEmpty()) {
                                        aobject[i] = chatcomponenttext.getText();
                                    }
                                }
                            }

                            object = new TranslatableComponent(s, aobject);
                        } else {
                            object = new TranslatableComponent(s);
                        }
                    } else if (jsonobject.has("score")) {
                        JsonObject jsonobject1 = GsonHelper.getAsJsonObject(jsonobject, "score");

                        if (!jsonobject1.has("name") || !jsonobject1.has("objective")) {
                            throw new JsonParseException("A score component needs a least a name and an objective");
                        }

                        object = new ScoreComponent(GsonHelper.getAsString(jsonobject1, "name"), GsonHelper.getAsString(jsonobject1, "objective"));
                    } else if (jsonobject.has("selector")) {
                        object = new SelectorComponent(GsonHelper.getAsString(jsonobject, "selector"));
                    } else if (jsonobject.has("keybind")) {
                        object = new KeybindComponent(GsonHelper.getAsString(jsonobject, "keybind"));
                    } else {
                        if (!jsonobject.has("nbt")) {
                            throw new JsonParseException("Don't know how to turn " + jsonelement + " into a Component");
                        }

                        s = GsonHelper.getAsString(jsonobject, "nbt");
                        boolean flag = GsonHelper.getAsBoolean(jsonobject, "interpret", false);

                        if (jsonobject.has("block")) {
                            object = new NbtComponent.BlockNbtComponent(s, flag, GsonHelper.getAsString(jsonobject, "block"));
                        } else if (jsonobject.has("entity")) {
                            object = new NbtComponent.EntityNbtComponent(s, flag, GsonHelper.getAsString(jsonobject, "entity"));
                        } else {
                            if (!jsonobject.has("storage")) {
                                throw new JsonParseException("Don't know how to turn " + jsonelement + " into a Component");
                            }

                            object = new NbtComponent.StorageNbtComponent(s, flag, new ResourceLocation(GsonHelper.getAsString(jsonobject, "storage")));
                        }
                    }
                }

                if (jsonobject.has("extra")) {
                    JsonArray jsonarray2 = GsonHelper.getAsJsonArray(jsonobject, "extra");

                    if (jsonarray2.size() <= 0) {
                        throw new JsonParseException("Unexpected empty array of components");
                    }

                    for (int j = 0; j < jsonarray2.size(); ++j) {
                        ((MutableComponent) object).append(this.deserialize(jsonarray2.get(j), type, jsondeserializationcontext));
                    }
                }

                ((MutableComponent) object).setStyle((Style) jsondeserializationcontext.deserialize(jsonelement, Style.class));
                return (MutableComponent) object;
            }
        }

        private void a(Style chatmodifier, JsonObject jsonobject, JsonSerializationContext jsonserializationcontext) {
            JsonElement jsonelement = jsonserializationcontext.serialize(chatmodifier);

            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject1 = (JsonObject) jsonelement;
                Iterator iterator = jsonobject1.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<String, JsonElement> entry = (Entry) iterator.next();

                    jsonobject.add((String) entry.getKey(), (JsonElement) entry.getValue());
                }
            }

        }

        public JsonElement serialize(Component ichatbasecomponent, Type type, JsonSerializationContext jsonserializationcontext) {
            JsonObject jsonobject = new JsonObject();

            if (!ichatbasecomponent.getStyle().isEmpty()) {
                this.a(ichatbasecomponent.getStyle(), jsonobject, jsonserializationcontext);
            }

            if (!ichatbasecomponent.getSiblings().isEmpty()) {
                JsonArray jsonarray = new JsonArray();
                Iterator iterator = ichatbasecomponent.getSiblings().iterator();

                while (iterator.hasNext()) {
                    Component ichatbasecomponent1 = (Component) iterator.next();

                    jsonarray.add(this.serialize(ichatbasecomponent1, ichatbasecomponent1.getClass(), jsonserializationcontext));
                }

                jsonobject.add("extra", jsonarray);
            }

            if (ichatbasecomponent instanceof TextComponent) {
                jsonobject.addProperty("text", ((TextComponent) ichatbasecomponent).getText());
            } else if (ichatbasecomponent instanceof TranslatableComponent) {
                TranslatableComponent chatmessage = (TranslatableComponent) ichatbasecomponent;

                jsonobject.addProperty("translate", chatmessage.getKey());
                if (chatmessage.getArgs() != null && chatmessage.getArgs().length > 0) {
                    JsonArray jsonarray1 = new JsonArray();
                    Object[] aobject = chatmessage.getArgs();
                    int i = aobject.length;

                    for (int j = 0; j < i; ++j) {
                        Object object = aobject[j];

                        if (object instanceof Component) {
                            jsonarray1.add(this.serialize((Component) object, object.getClass(), jsonserializationcontext));
                        } else {
                            jsonarray1.add(new JsonPrimitive(String.valueOf(object)));
                        }
                    }

                    jsonobject.add("with", jsonarray1);
                }
            } else if (ichatbasecomponent instanceof ScoreComponent) {
                ScoreComponent chatcomponentscore = (ScoreComponent) ichatbasecomponent;
                JsonObject jsonobject1 = new JsonObject();

                jsonobject1.addProperty("name", chatcomponentscore.getName());
                jsonobject1.addProperty("objective", chatcomponentscore.getObjective());
                jsonobject.add("score", jsonobject1);
            } else if (ichatbasecomponent instanceof SelectorComponent) {
                SelectorComponent chatcomponentselector = (SelectorComponent) ichatbasecomponent;

                jsonobject.addProperty("selector", chatcomponentselector.getPattern());
            } else if (ichatbasecomponent instanceof KeybindComponent) {
                KeybindComponent chatcomponentkeybind = (KeybindComponent) ichatbasecomponent;

                jsonobject.addProperty("keybind", chatcomponentkeybind.getName());
            } else {
                if (!(ichatbasecomponent instanceof NbtComponent)) {
                    throw new IllegalArgumentException("Don't know how to serialize " + ichatbasecomponent + " as a Component");
                }

                NbtComponent chatcomponentnbt = (NbtComponent) ichatbasecomponent;

                jsonobject.addProperty("nbt", chatcomponentnbt.getNbtPath());
                jsonobject.addProperty("interpret", chatcomponentnbt.isInterpreting());
                if (ichatbasecomponent instanceof NbtComponent.BlockNbtComponent) {
                    NbtComponent.BlockNbtComponent chatcomponentnbt_a = (NbtComponent.BlockNbtComponent) ichatbasecomponent;

                    jsonobject.addProperty("block", chatcomponentnbt_a.getPos());
                } else if (ichatbasecomponent instanceof NbtComponent.EntityNbtComponent) {
                    NbtComponent.EntityNbtComponent chatcomponentnbt_b = (NbtComponent.EntityNbtComponent) ichatbasecomponent;

                    jsonobject.addProperty("entity", chatcomponentnbt_b.getSelector());
                } else {
                    if (!(ichatbasecomponent instanceof NbtComponent.StorageNbtComponent)) {
                        throw new IllegalArgumentException("Don't know how to serialize " + ichatbasecomponent + " as a Component");
                    }

                    NbtComponent.StorageNbtComponent chatcomponentnbt_c = (NbtComponent.StorageNbtComponent) ichatbasecomponent;

                    jsonobject.addProperty("storage", chatcomponentnbt_c.getId().toString());
                }
            }

            return jsonobject;
        }

        public static String a(Component ichatbasecomponent) {
            return Component.ChatSerializer.a.toJson(ichatbasecomponent);
        }

        public static JsonElement b(Component ichatbasecomponent) {
            return Component.ChatSerializer.a.toJsonTree(ichatbasecomponent);
        }

        @Nullable
        public static MutableComponent a(String s) {
            return (MutableComponent) GsonHelper.fromJson(Component.ChatSerializer.a, s, MutableComponent.class, false);
        }

        @Nullable
        public static MutableComponent a(JsonElement jsonelement) {
            return (MutableComponent) Component.ChatSerializer.a.fromJson(jsonelement, MutableComponent.class);
        }

        @Nullable
        public static MutableComponent b(String s) {
            return (MutableComponent) GsonHelper.fromJson(Component.ChatSerializer.a, s, MutableComponent.class, true);
        }

        public static MutableComponent a(com.mojang.brigadier.StringReader com_mojang_brigadier_stringreader) {
            try {
                JsonReader jsonreader = new JsonReader(new StringReader(com_mojang_brigadier_stringreader.getRemaining()));

                jsonreader.setLenient(false);
                MutableComponent ichatmutablecomponent = (MutableComponent) Component.ChatSerializer.a.getAdapter(MutableComponent.class).read(jsonreader);

                com_mojang_brigadier_stringreader.setCursor(com_mojang_brigadier_stringreader.getCursor() + a(jsonreader));
                return ichatmutablecomponent;
            } catch (StackOverflowError | IOException ioexception) {
                throw new JsonParseException(ioexception);
            }
        }

        private static int a(JsonReader jsonreader) {
            try {
                return Component.ChatSerializer.b.getInt(jsonreader) - Component.ChatSerializer.c.getInt(jsonreader) + 1;
            } catch (IllegalAccessException illegalaccessexception) {
                throw new IllegalStateException("Couldn't read position of JsonReader", illegalaccessexception);
            }
        }
    }
}
