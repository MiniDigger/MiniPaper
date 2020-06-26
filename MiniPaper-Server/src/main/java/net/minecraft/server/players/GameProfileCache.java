package net.minecraft.server.players;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.IOUtils;

public class GameProfileCache {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    private static boolean usesAuthentication;
    private final Map<String, GameProfileCache.UserCacheEntry> profilesByName = Maps.newHashMap();
    private final Map<UUID, GameProfileCache.UserCacheEntry> profilesByUUID = Maps.newHashMap();
    private final Deque<GameProfile> profileMRUList = new java.util.concurrent.LinkedBlockingDeque<GameProfile>(); // CraftBukkit
    private final GameProfileRepository profileRepository;
    protected final Gson gson;
    private final File file;
    private static final TypeToken<List<GameProfileCache.UserCacheEntry>> GAMEPROFILE_ENTRY_TYPE = new TypeToken<List<GameProfileCache.UserCacheEntry>>() {
    };

    public GameProfileCache(GameProfileRepository gameprofilerepository, File file) {
        this.profileRepository = gameprofilerepository;
        this.file = file;
        GsonBuilder gsonbuilder = new GsonBuilder();

        gsonbuilder.registerTypeHierarchyAdapter(GameProfileCache.UserCacheEntry.class, new GameProfileCache.BanEntrySerializer());
        this.gson = gsonbuilder.create();
        this.load();
    }

    private static GameProfile lookupGameProfile(GameProfileRepository gameprofilerepository, String s) {
        final GameProfile[] agameprofile = new GameProfile[1];
        ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
            public void onProfileLookupSucceeded(GameProfile gameprofile) {
                agameprofile[0] = gameprofile;
            }

            public void onProfileLookupFailed(GameProfile gameprofile, Exception exception) {
                agameprofile[0] = null;
            }
        };

        gameprofilerepository.findProfilesByNames(new String[]{s}, Agent.MINECRAFT, profilelookupcallback);
        if (!usesAuthentication() && agameprofile[0] == null) {
            UUID uuid = Player.createPlayerUUID(new GameProfile((UUID) null, s));
            GameProfile gameprofile = new GameProfile(uuid, s);

            profilelookupcallback.onProfileLookupSucceeded(gameprofile);
        }

        return agameprofile[0];
    }

    public static void setUsesAuthentication(boolean flag) {
        GameProfileCache.usesAuthentication = flag;
    }

    private static boolean usesAuthentication() {
        return GameProfileCache.usesAuthentication;
    }

    public void add(GameProfile gameprofile) {
        this.add(gameprofile, (Date) null);
    }

    private void add(GameProfile gameprofile, Date date) {
        UUID uuid = gameprofile.getId();

        if (date == null) {
            Calendar calendar = Calendar.getInstance();

            calendar.setTime(new Date());
            calendar.add(2, 1);
            date = calendar.getTime();
        }

        GameProfileCache.UserCacheEntry usercache_usercacheentry = new GameProfileCache.UserCacheEntry(gameprofile, date);

        if (this.profilesByUUID.containsKey(uuid)) {
            GameProfileCache.UserCacheEntry usercache_usercacheentry1 = (GameProfileCache.UserCacheEntry) this.profilesByUUID.get(uuid);

            this.profilesByName.remove(usercache_usercacheentry1.a().getName().toLowerCase(Locale.ROOT));
            this.profileMRUList.remove(gameprofile);
        }

        this.profilesByName.put(gameprofile.getName().toLowerCase(Locale.ROOT), usercache_usercacheentry);
        this.profilesByUUID.put(uuid, usercache_usercacheentry);
        this.profileMRUList.addFirst(gameprofile);
        if( !org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly ) this.save(); // Spigot - skip saving if disabled
    }

    @Nullable
    public GameProfile get(String s) {
        String s1 = s.toLowerCase(Locale.ROOT);
        GameProfileCache.UserCacheEntry usercache_usercacheentry = (GameProfileCache.UserCacheEntry) this.profilesByName.get(s1);

        if (usercache_usercacheentry != null && (new Date()).getTime() >= usercache_usercacheentry.c.getTime()) {
            this.profilesByUUID.remove(usercache_usercacheentry.a().getId());
            this.profilesByName.remove(usercache_usercacheentry.a().getName().toLowerCase(Locale.ROOT));
            this.profileMRUList.remove(usercache_usercacheentry.a());
            usercache_usercacheentry = null;
        }

        GameProfile gameprofile;

        if (usercache_usercacheentry != null) {
            gameprofile = usercache_usercacheentry.a();
            this.profileMRUList.remove(gameprofile);
            this.profileMRUList.addFirst(gameprofile);
        } else {
            gameprofile = lookupGameProfile(this.profileRepository, s); // Spigot - use correct case for offline players
            if (gameprofile != null) {
                this.add(gameprofile);
                usercache_usercacheentry = (GameProfileCache.UserCacheEntry) this.profilesByName.get(s1);
            }
        }

        if( !org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly ) this.save(); // Spigot - skip saving if disabled
        return usercache_usercacheentry == null ? null : usercache_usercacheentry.a();
    }

    @Nullable
    public GameProfile get(UUID uuid) {
        GameProfileCache.UserCacheEntry usercache_usercacheentry = (GameProfileCache.UserCacheEntry) this.profilesByUUID.get(uuid);

        return usercache_usercacheentry == null ? null : usercache_usercacheentry.a();
    }

    private GameProfileCache.UserCacheEntry b(UUID uuid) {
        GameProfileCache.UserCacheEntry usercache_usercacheentry = (GameProfileCache.UserCacheEntry) this.profilesByUUID.get(uuid);

        if (usercache_usercacheentry != null) {
            GameProfile gameprofile = usercache_usercacheentry.a();

            this.profileMRUList.remove(gameprofile);
            this.profileMRUList.addFirst(gameprofile);
        }

        return usercache_usercacheentry;
    }

    public void load() {
        BufferedReader bufferedreader = null;

        try {
            bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8);
            List<GameProfileCache.UserCacheEntry> list = (List) GsonHelper.fromJson(this.gson, (Reader) bufferedreader, GameProfileCache.GAMEPROFILE_ENTRY_TYPE);

            this.profilesByName.clear();
            this.profilesByUUID.clear();
            this.profileMRUList.clear();
            if (list != null) {
                Iterator iterator = Lists.reverse(list).iterator();

                while (iterator.hasNext()) {
                    GameProfileCache.UserCacheEntry usercache_usercacheentry = (GameProfileCache.UserCacheEntry) iterator.next();

                    if (usercache_usercacheentry != null) {
                        this.add(usercache_usercacheentry.a(), usercache_usercacheentry.b());
                    }
                }
            }
        } catch (FileNotFoundException filenotfoundexception) {
            ;
        // Spigot Start
        } catch (com.google.gson.JsonSyntaxException ex) {
            StoredUserList.LOGGER.warn( "Usercache.json is corrupted or has bad formatting. Deleting it to prevent further issues." );
            this.file.delete();
        // Spigot End
        } catch (JsonParseException jsonparseexception) {
            ;
        } finally {
            IOUtils.closeQuietly(bufferedreader);
        }

    }

    public void save() {
        String s = this.gson.toJson(this.getTopMRUProfiles(org.spigotmc.SpigotConfig.userCacheCap));
        BufferedWriter bufferedwriter = null;

        try {
            bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8);
            bufferedwriter.write(s);
            return;
        } catch (FileNotFoundException filenotfoundexception) {
            return;
        } catch (IOException ioexception) {
            ;
        } finally {
            IOUtils.closeQuietly(bufferedwriter);
        }

    }

    private List<GameProfileCache.UserCacheEntry> getTopMRUProfiles(int i) {
        List<GameProfileCache.UserCacheEntry> list = Lists.newArrayList();
        List<GameProfile> list1 = Lists.newArrayList(Iterators.limit(this.profileMRUList.iterator(), i));
        Iterator iterator = list1.iterator();

        while (iterator.hasNext()) {
            GameProfile gameprofile = (GameProfile) iterator.next();
            GameProfileCache.UserCacheEntry usercache_usercacheentry = this.b(gameprofile.getId());

            if (usercache_usercacheentry != null) {
                list.add(usercache_usercacheentry);
            }
        }

        return list;
    }

    class UserCacheEntry {

        private final GameProfile b;
        private final Date c;

        private UserCacheEntry(GameProfile gameprofile, Date date) {
            this.b = gameprofile;
            this.c = date;
        }

        public GameProfile a() {
            return this.b;
        }

        public Date b() {
            return this.c;
        }
    }

    class BanEntrySerializer implements JsonDeserializer<GameProfileCache.UserCacheEntry>, JsonSerializer<GameProfileCache.UserCacheEntry> {

        private BanEntrySerializer() {}

        public JsonElement serialize(GameProfileCache.UserCacheEntry usercache_usercacheentry, Type type, JsonSerializationContext jsonserializationcontext) {
            JsonObject jsonobject = new JsonObject();

            jsonobject.addProperty("name", usercache_usercacheentry.a().getName());
            UUID uuid = usercache_usercacheentry.a().getId();

            jsonobject.addProperty("uuid", uuid == null ? "" : uuid.toString());
            jsonobject.addProperty("expiresOn", GameProfileCache.DATE_FORMAT.format(usercache_usercacheentry.b()));
            return jsonobject;
        }

        public GameProfileCache.UserCacheEntry deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                JsonElement jsonelement1 = jsonobject.get("name");
                JsonElement jsonelement2 = jsonobject.get("uuid");
                JsonElement jsonelement3 = jsonobject.get("expiresOn");

                if (jsonelement1 != null && jsonelement2 != null) {
                    String s = jsonelement2.getAsString();
                    String s1 = jsonelement1.getAsString();
                    Date date = null;

                    if (jsonelement3 != null) {
                        try {
                            date = GameProfileCache.DATE_FORMAT.parse(jsonelement3.getAsString());
                        } catch (ParseException parseexception) {
                            date = null;
                        }
                    }

                    if (s1 != null && s != null) {
                        UUID uuid;

                        try {
                            uuid = UUID.fromString(s);
                        } catch (Throwable throwable) {
                            return null;
                        }

                        return GameProfileCache.this.new UserCacheEntry(new GameProfile(uuid, s1), date); // Toothpick decomp fix
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }
}
