package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftPlayer;
// CraftBukkit end

public class PlayerDataStorage {

    private static final Logger LOGGER = LogManager.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess convertable_conversionsession, DataFixer datafixer) {
        this.fixerUpper = datafixer;
        this.playerDir = convertable_conversionsession.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player entityhuman) {
        try {
            CompoundTag nbttagcompound = entityhuman.saveWithoutId(new CompoundTag());
            File file = File.createTempFile(entityhuman.getStringUUID() + "-", ".dat", this.playerDir);

            NbtIo.writeCompressed(nbttagcompound, (OutputStream) (new FileOutputStream(file)));
            File file1 = new File(this.playerDir, entityhuman.getStringUUID() + ".dat");
            File file2 = new File(this.playerDir, entityhuman.getStringUUID() + ".dat_old");

            Util.safeReplaceFile(file1, file, file2);
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to save player data for {}", entityhuman.getName().getString());
        }

    }

    @Nullable
    public CompoundTag load(Player entityhuman) {
        CompoundTag nbttagcompound = null;

        try {
            File file = new File(this.playerDir, entityhuman.getStringUUID() + ".dat");
            // Spigot Start
            boolean usingWrongFile = false;
            if ( !file.exists() )
            {
                file = new File( this.playerDir, java.util.UUID.nameUUIDFromBytes( ( "OfflinePlayer:" + entityhuman.getScoreboardName() ).getBytes( "UTF-8" ) ).toString() + ".dat");
                if ( file.exists() )
                {
                    usingWrongFile = true;
                    org.bukkit.Bukkit.getServer().getLogger().warning( "Using offline mode UUID file for player " + entityhuman.getScoreboardName() + " as it is the only copy we can find." );
                }
            }
            // Spigot End

            if (file.exists() && file.isFile()) {
                nbttagcompound = NbtIo.readCompressed((InputStream) (new FileInputStream(file)));
            }
            // Spigot Start
            if ( usingWrongFile )
            {
                file.renameTo( new File( file.getPath() + ".offline-read" ) );
            }
            // Spigot End
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to load player data for {}", entityhuman.getName().getString());
        }

        if (nbttagcompound != null) {
            // CraftBukkit start
            if (entityhuman instanceof ServerPlayer) {
                CraftPlayer player = (CraftPlayer) entityhuman.getBukkitEntity();
                // Only update first played if it is older than the one we have
                long modified = new File(this.playerDir, entityhuman.getUUID().toString() + ".dat").lastModified();
                if (modified < player.getFirstPlayed()) {
                    player.setFirstPlayed(modified);
                }
            }
            // CraftBukkit end
            int i = nbttagcompound.contains("DataVersion", 3) ? nbttagcompound.getInt("DataVersion") : -1;

            entityhuman.load(NbtUtils.update(this.fixerUpper, DataFixTypes.PLAYER, nbttagcompound, i));
        }

        return nbttagcompound;
    }

    // CraftBukkit start
    public CompoundTag getPlayerData(String s) {
        try {
            File file1 = new File(this.playerDir, s + ".dat");

            if (file1.exists()) {
                return NbtIo.readCompressed((InputStream) (new FileInputStream(file1)));
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load player data for " + s);
        }

        return null;
    }
    // CraftBukkit end

    public String[] getSeenPlayers() {
        String[] astring = this.playerDir.list();

        if (astring == null) {
            astring = new String[0];
        }

        for (int i = 0; i < astring.length; ++i) {
            if (astring[i].endsWith(".dat")) {
                astring[i] = astring[i].substring(0, astring[i].length() - 4);
            }
        }

        return astring;
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return playerDir;
    }
    // CraftBukkit end
}
