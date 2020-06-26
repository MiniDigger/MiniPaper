package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    protected final Date created;
    protected final String source;
    protected final Date expires;
    protected final String reason;

    public BanListEntry(T t0, @Nullable Date date, @Nullable String s, @Nullable Date date1, @Nullable String s1) {
        super(t0);
        this.created = date == null ? new Date() : date;
        this.source = s == null ? "(Unknown)" : s;
        this.expires = date1;
        this.reason = s1 == null ? "Banned by an operator." : s1;
    }

    protected BanListEntry(T t0, JsonObject jsonobject) {
        super(checkExpiry(t0, jsonobject));

        Date date;

        try {
            date = jsonobject.has("created") ? BanListEntry.DATE_FORMAT.parse(jsonobject.get("created").getAsString()) : new Date();
        } catch (ParseException parseexception) {
            date = new Date();
        }

        this.created = date;
        this.source = jsonobject.has("source") ? jsonobject.get("source").getAsString() : "(Unknown)";

        Date date1;

        try {
            date1 = jsonobject.has("expires") ? BanListEntry.DATE_FORMAT.parse(jsonobject.get("expires").getAsString()) : null;
        } catch (ParseException parseexception1) {
            date1 = null;
        }

        this.expires = date1;
        this.reason = jsonobject.has("reason") ? jsonobject.get("reason").getAsString() : "Banned by an operator.";
    }

    public String getSource() {
        return this.source;
    }

    public Date getExpires() {
        return this.expires;
    }

    public String getReason() {
        return this.reason;
    }

    public abstract Component getDisplayName();

    @Override
    boolean hasExpired() {
        return this.expires == null ? false : this.expires.before(new Date());
    }

    @Override
    protected void serialize(JsonObject jsonobject) {
        jsonobject.addProperty("created", BanListEntry.DATE_FORMAT.format(this.created));
        jsonobject.addProperty("source", this.source);
        jsonobject.addProperty("expires", this.expires == null ? "forever" : BanListEntry.DATE_FORMAT.format(this.expires));
        jsonobject.addProperty("reason", this.reason);
    }

    // CraftBukkit start
    public Date getCreated() {
        return this.created;
    }

    private static <T> T checkExpiry(T object, JsonObject jsonobject) {
        Date expires = null;

        try {
            expires = jsonobject.has("expires") ? DATE_FORMAT.parse(jsonobject.get("expires").getAsString()) : null;
        } catch (ParseException ex) {
            // Guess we don't have a date
        }

        if (expires == null || expires.after(new Date())) {
            return object;
        } else {
            return null;
        }
    }
    // CraftBukkit end
}
