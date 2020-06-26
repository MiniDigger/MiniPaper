package net.minecraft.network.chat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import java.lang.reflect.Type;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public class Style {

    public static final ResourceLocation DEFAULT_FONT = new ResourceLocation("minecraft", "default");
    public static final Style EMPTY = new Style((TextColor) null, (Boolean) null, (Boolean) null, (Boolean) null, (Boolean) null, (Boolean) null, (ClickEvent) null, (HoverEvent) null, (String) null, (ResourceLocation) null);
    @Nullable
    private final TextColor color;
    @Nullable
    private final Boolean bold;
    @Nullable
    private final Boolean italic;
    @Nullable
    private final Boolean underlined;
    @Nullable
    private final Boolean strikethrough;
    @Nullable
    private final Boolean obfuscated;
    @Nullable
    private final ClickEvent clickEvent;
    @Nullable
    private final HoverEvent hoverEvent;
    @Nullable
    private final String insertion;
    @Nullable
    private final ResourceLocation font;

    private Style(@Nullable TextColor chathexcolor, @Nullable Boolean obool, @Nullable Boolean obool1, @Nullable Boolean obool2, @Nullable Boolean obool3, @Nullable Boolean obool4, @Nullable ClickEvent chatclickable, @Nullable HoverEvent chathoverable, @Nullable String s, @Nullable ResourceLocation minecraftkey) {
        this.color = chathexcolor;
        this.bold = obool;
        this.italic = obool1;
        this.underlined = obool2;
        this.strikethrough = obool3;
        this.obfuscated = obool4;
        this.clickEvent = chatclickable;
        this.hoverEvent = chathoverable;
        this.insertion = s;
        this.font = minecraftkey;
    }

    @Nullable
    public TextColor getColor() {
        return this.color;
    }

    public boolean isBold() {
        return this.bold == Boolean.TRUE;
    }

    public boolean isItalic() {
        return this.italic == Boolean.TRUE;
    }

    public boolean isStrikethrough() {
        return this.strikethrough == Boolean.TRUE;
    }

    public boolean isUnderlined() {
        return this.underlined == Boolean.TRUE;
    }

    public boolean isObfuscated() {
        return this.obfuscated == Boolean.TRUE;
    }

    public boolean isEmpty() {
        return this == Style.EMPTY;
    }

    @Nullable
    public ClickEvent getClickEvent() {
        return this.clickEvent;
    }

    @Nullable
    public HoverEvent getHoverEvent() {
        return this.hoverEvent;
    }

    @Nullable
    public String getInsertion() {
        return this.insertion;
    }

    public ResourceLocation getFont() {
        return this.font != null ? this.font : Style.DEFAULT_FONT;
    }

    public Style withColor(@Nullable TextColor chathexcolor) {
        return new Style(chathexcolor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style withColor(@Nullable ChatFormatting enumchatformat) {
        return this.withColor(enumchatformat != null ? TextColor.fromLegacyFormat(enumchatformat) : null);
    }

    public Style withBold(@Nullable Boolean obool) {
        return new Style(this.color, obool, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style withItalic(@Nullable Boolean obool) {
        return new Style(this.color, this.bold, obool, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    // CraftBukkit start
    public Style setStrikethrough(@Nullable Boolean obool) {
        return new Style(this.color, this.bold, this.italic, this.underlined, obool, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style setUnderline(@Nullable Boolean obool) {
        return new Style(this.color, this.bold, this.italic, obool, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style setRandom(@Nullable Boolean obool) {
        return new Style(this.color, this.bold, this.italic, this.underlined, this.strikethrough, obool, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }
    // CraftBukkit end

    public Style withClickEvent(@Nullable ClickEvent chatclickable) {
        return new Style(this.color, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, chatclickable, this.hoverEvent, this.insertion, this.font);
    }

    public Style withHoverEvent(@Nullable HoverEvent chathoverable) {
        return new Style(this.color, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, chathoverable, this.insertion, this.font);
    }

    public Style withInsertion(@Nullable String s) {
        return new Style(this.color, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, s, this.font);
    }

    public Style applyFormat(ChatFormatting enumchatformat) {
        TextColor chathexcolor = this.color;
        Boolean obool = this.bold;
        Boolean obool1 = this.italic;
        Boolean obool2 = this.strikethrough;
        Boolean obool3 = this.underlined;
        Boolean obool4 = this.obfuscated;

        switch (enumchatformat) {
            case OBFUSCATED:
                obool4 = true;
                break;
            case BOLD:
                obool = true;
                break;
            case STRIKETHROUGH:
                obool2 = true;
                break;
            case UNDERLINE:
                obool3 = true;
                break;
            case ITALIC:
                obool1 = true;
                break;
            case RESET:
                return Style.EMPTY;
            default:
                chathexcolor = TextColor.fromLegacyFormat(enumchatformat);
        }

        return new Style(chathexcolor, obool, obool1, obool3, obool2, obool4, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyFormats(ChatFormatting... aenumchatformat) {
        TextColor chathexcolor = this.color;
        Boolean obool = this.bold;
        Boolean obool1 = this.italic;
        Boolean obool2 = this.strikethrough;
        Boolean obool3 = this.underlined;
        Boolean obool4 = this.obfuscated;
        ChatFormatting[] aenumchatformat1 = aenumchatformat;
        int i = aenumchatformat.length;

        for (int j = 0; j < i; ++j) {
            ChatFormatting enumchatformat = aenumchatformat1[j];

            switch (enumchatformat) {
                case OBFUSCATED:
                    obool4 = true;
                    break;
                case BOLD:
                    obool = true;
                    break;
                case STRIKETHROUGH:
                    obool2 = true;
                    break;
                case UNDERLINE:
                    obool3 = true;
                    break;
                case ITALIC:
                    obool1 = true;
                    break;
                case RESET:
                    return Style.EMPTY;
                default:
                    chathexcolor = TextColor.fromLegacyFormat(enumchatformat);
            }
        }

        return new Style(chathexcolor, obool, obool1, obool3, obool2, obool4, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyTo(Style chatmodifier) {
        return this == Style.EMPTY ? chatmodifier : (chatmodifier == Style.EMPTY ? this : new Style(this.color != null ? this.color : chatmodifier.color, this.bold != null ? this.bold : chatmodifier.bold, this.italic != null ? this.italic : chatmodifier.italic, this.underlined != null ? this.underlined : chatmodifier.underlined, this.strikethrough != null ? this.strikethrough : chatmodifier.strikethrough, this.obfuscated != null ? this.obfuscated : chatmodifier.obfuscated, this.clickEvent != null ? this.clickEvent : chatmodifier.clickEvent, this.hoverEvent != null ? this.hoverEvent : chatmodifier.hoverEvent, this.insertion != null ? this.insertion : chatmodifier.insertion, this.font != null ? this.font : chatmodifier.font));
    }

    public String toString() {
        return "Style{ color=" + this.color + ", bold=" + this.bold + ", italic=" + this.italic + ", underlined=" + this.underlined + ", strikethrough=" + this.strikethrough + ", obfuscated=" + this.obfuscated + ", clickEvent=" + this.getClickEvent() + ", hoverEvent=" + this.getHoverEvent() + ", insertion=" + this.getInsertion() + ", font=" + this.getFont() + '}';
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (!(object instanceof Style)) {
            return false;
        } else {
            Style chatmodifier = (Style) object;

            return this.isBold() == chatmodifier.isBold() && Objects.equals(this.getColor(), chatmodifier.getColor()) && this.isItalic() == chatmodifier.isItalic() && this.isObfuscated() == chatmodifier.isObfuscated() && this.isStrikethrough() == chatmodifier.isStrikethrough() && this.isUnderlined() == chatmodifier.isUnderlined() && Objects.equals(this.getClickEvent(), chatmodifier.getClickEvent()) && Objects.equals(this.getHoverEvent(), chatmodifier.getHoverEvent()) && Objects.equals(this.getInsertion(), chatmodifier.getInsertion()) && Objects.equals(this.getFont(), chatmodifier.getFont());
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.color, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion});
    }

    public static class ChatModifierSerializer implements JsonDeserializer<Style>, JsonSerializer<Style> {

        public ChatModifierSerializer() {}

        @Nullable
        public Style deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = jsonelement.getAsJsonObject();

                if (jsonobject == null) {
                    return null;
                } else {
                    Boolean obool = a(jsonobject, "bold");
                    Boolean obool1 = a(jsonobject, "italic");
                    Boolean obool2 = a(jsonobject, "underlined");
                    Boolean obool3 = a(jsonobject, "strikethrough");
                    Boolean obool4 = a(jsonobject, "obfuscated");
                    TextColor chathexcolor = e(jsonobject);
                    String s = d(jsonobject);
                    ClickEvent chatclickable = c(jsonobject);
                    HoverEvent chathoverable = b(jsonobject);
                    ResourceLocation minecraftkey = a(jsonobject);

                    return new Style(chathexcolor, obool, obool1, obool2, obool3, obool4, chatclickable, chathoverable, s, minecraftkey);
                }
            } else {
                return null;
            }
        }

        @Nullable
        private static ResourceLocation a(JsonObject jsonobject) {
            if (jsonobject.has("font")) {
                String s = GsonHelper.getAsString(jsonobject, "font");

                try {
                    return new ResourceLocation(s);
                } catch (ResourceLocationException resourcekeyinvalidexception) {
                    throw new JsonSyntaxException("Invalid font name: " + s);
                }
            } else {
                return null;
            }
        }

        @Nullable
        private static HoverEvent b(JsonObject jsonobject) {
            if (jsonobject.has("hoverEvent")) {
                JsonObject jsonobject1 = GsonHelper.getAsJsonObject(jsonobject, "hoverEvent");
                HoverEvent chathoverable = HoverEvent.deserialize(jsonobject1);

                if (chathoverable != null && chathoverable.getAction().isAllowedFromServer()) {
                    return chathoverable;
                }
            }

            return null;
        }

        @Nullable
        private static ClickEvent c(JsonObject jsonobject) {
            if (jsonobject.has("clickEvent")) {
                JsonObject jsonobject1 = GsonHelper.getAsJsonObject(jsonobject, "clickEvent");
                String s = GsonHelper.getAsString(jsonobject1, "action", (String) null);
                ClickEvent.Action chatclickable_enumclickaction = s == null ? null : ClickEvent.Action.getByName(s);
                String s1 = GsonHelper.getAsString(jsonobject1, "value", (String) null);

                if (chatclickable_enumclickaction != null && s1 != null && chatclickable_enumclickaction.isAllowedFromServer()) {
                    return new ClickEvent(chatclickable_enumclickaction, s1);
                }
            }

            return null;
        }

        @Nullable
        private static String d(JsonObject jsonobject) {
            return GsonHelper.getAsString(jsonobject, "insertion", (String) null);
        }

        @Nullable
        private static TextColor e(JsonObject jsonobject) {
            if (jsonobject.has("color")) {
                String s = GsonHelper.getAsString(jsonobject, "color");

                return TextColor.parseColor(s);
            } else {
                return null;
            }
        }

        @Nullable
        private static Boolean a(JsonObject jsonobject, String s) {
            return jsonobject.has(s) ? jsonobject.get(s).getAsBoolean() : null;
        }

        @Nullable
        public JsonElement serialize(Style chatmodifier, Type type, JsonSerializationContext jsonserializationcontext) {
            if (chatmodifier.isEmpty()) {
                return null;
            } else {
                JsonObject jsonobject = new JsonObject();

                if (chatmodifier.bold != null) {
                    jsonobject.addProperty("bold", chatmodifier.bold);
                }

                if (chatmodifier.italic != null) {
                    jsonobject.addProperty("italic", chatmodifier.italic);
                }

                if (chatmodifier.underlined != null) {
                    jsonobject.addProperty("underlined", chatmodifier.underlined);
                }

                if (chatmodifier.strikethrough != null) {
                    jsonobject.addProperty("strikethrough", chatmodifier.strikethrough);
                }

                if (chatmodifier.obfuscated != null) {
                    jsonobject.addProperty("obfuscated", chatmodifier.obfuscated);
                }

                if (chatmodifier.color != null) {
                    jsonobject.addProperty("color", chatmodifier.color.serialize());
                }

                if (chatmodifier.insertion != null) {
                    jsonobject.add("insertion", jsonserializationcontext.serialize(chatmodifier.insertion));
                }

                if (chatmodifier.clickEvent != null) {
                    JsonObject jsonobject1 = new JsonObject();

                    jsonobject1.addProperty("action", chatmodifier.clickEvent.getAction().getName());
                    jsonobject1.addProperty("value", chatmodifier.clickEvent.getValue());
                    jsonobject.add("clickEvent", jsonobject1);
                }

                if (chatmodifier.hoverEvent != null) {
                    jsonobject.add("hoverEvent", chatmodifier.hoverEvent.serialize());
                }

                if (chatmodifier.font != null) {
                    jsonobject.addProperty("font", chatmodifier.font.toString());
                }

                return jsonobject;
            }
        }
    }
}
