package net.minecraft.world.level.block.entity;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
// Spigot start
import com.google.common.base.Predicate;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.authlib.Agent;
import com.mojang.authlib.ProfileLookupCallback;
import java.util.concurrent.Callable;
// Spigot end

public class SkullBlockEntity extends BlockEntity implements TickableBlockEntity {

    @Nullable
    private static GameProfileCache profileCache;
    @Nullable
    private static MinecraftSessionService sessionService;
    @Nullable
    public GameProfile owner;
    private int mouthTickCount;
    private boolean isMovingMouth;
    // Spigot start
    public static final ExecutorService executor = Executors.newFixedThreadPool(3,
            new ThreadFactoryBuilder()
                    .setNameFormat("Head Conversion Thread - %1$d")
                    .build()
    );
    public static final LoadingCache<String, GameProfile> skinCache = CacheBuilder.newBuilder()
            .maximumSize( 5000 )
            .expireAfterAccess( 60, TimeUnit.MINUTES )
            .build( new CacheLoader<String, GameProfile>()
            {
                @Override
                public GameProfile load(String key) throws Exception
                {
                    final GameProfile[] profiles = new GameProfile[1];
                    ProfileLookupCallback gameProfileLookup = new ProfileLookupCallback() {

                        @Override
                        public void onProfileLookupSucceeded(GameProfile gp) {
                            profiles[0] = gp;
                        }

                        @Override
                        public void onProfileLookupFailed(GameProfile gp, Exception excptn) {
                            profiles[0] = gp;
                        }
                    };

                    MinecraftServer.getServer().getProfileRepository().findProfilesByNames(new String[] { key }, Agent.MINECRAFT, gameProfileLookup);

                    GameProfile profile = profiles[ 0 ];
                    if (profile == null) {
                        UUID uuid = Player.createPlayerUUID(new GameProfile(null, key));
                        profile = new GameProfile(uuid, key);

                        gameProfileLookup.onProfileLookupSucceeded(profile);
                    } else
                    {

                        Property property = Iterables.getFirst( profile.getProperties().get( "textures" ), null );

                        if ( property == null )
                        {
                            profile = SkullBlockEntity.sessionService.fillProfileProperties( profile, true );
                        }
                    }


                    return profile;
                }
            } );
    // Spigot end

    public SkullBlockEntity() {
        super(BlockEntityType.SKULL);
    }

    public static void setProfileCache(GameProfileCache usercache) {
        SkullBlockEntity.profileCache = usercache;
    }

    public static void setSessionService(MinecraftSessionService minecraftsessionservice) {
        SkullBlockEntity.sessionService = minecraftsessionservice;
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (this.owner != null) {
            CompoundTag nbttagcompound1 = new CompoundTag();

            NbtUtils.writeGameProfile(nbttagcompound1, this.owner);
            nbttagcompound.put("SkullOwner", nbttagcompound1);
        }

        return nbttagcompound;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        if (nbttagcompound.contains("SkullOwner", 10)) {
            this.setOwner(NbtUtils.readGameProfile(nbttagcompound.getCompound("SkullOwner")));
        } else if (nbttagcompound.contains("ExtraType", 8)) {
            String s = nbttagcompound.getString("ExtraType");

            if (!StringUtil.isNullOrEmpty(s)) {
                this.setOwner(new GameProfile((UUID) null, s));
            }
        }

    }

    @Override
    public void tick() {
        BlockState iblockdata = this.getBlock();

        if (iblockdata.is(Blocks.DRAGON_HEAD) || iblockdata.is(Blocks.DRAGON_WALL_HEAD)) {
            if (this.level.hasNeighborSignal(this.worldPosition)) {
                this.isMovingMouth = true;
                ++this.mouthTickCount;
            } else {
                this.isMovingMouth = false;
            }
        }

    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 4, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    public void setOwner(@Nullable GameProfile gameprofile) {
        this.owner = gameprofile;
        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        // Spigot start
        GameProfile profile = this.owner;
        b(profile, new Predicate<GameProfile>() {

            @Override
            public boolean apply(GameProfile input) {
                owner = input;
                setChanged();
                return false;
            }
        }, false);
        // Spigot end
    }

    // Spigot start - Support async lookups
    public static Future<GameProfile> b(@Nullable final GameProfile gameprofile, final Predicate<GameProfile> callback, boolean sync) {
        if (gameprofile != null && !StringUtil.isNullOrEmpty(gameprofile.getName())) {
            if (gameprofile.isComplete() && gameprofile.getProperties().containsKey("textures")) {
                callback.apply(gameprofile);
            } else if (MinecraftServer.getServer() == null) {
                callback.apply(gameprofile);
            } else {
                GameProfile profile = skinCache.getIfPresent(gameprofile.getName().toLowerCase(java.util.Locale.ROOT));
                if (profile != null && Iterables.getFirst(profile.getProperties().get("textures"), (Object) null) != null) {
                    callback.apply(profile);

                    return Futures.immediateFuture(profile);
                } else {
                    Callable<GameProfile> callable = new Callable<GameProfile>() {
                        @Override
                        public GameProfile call() {
                            final GameProfile profile = skinCache.getUnchecked(gameprofile.getName().toLowerCase(java.util.Locale.ROOT));
                            MinecraftServer.getServer().processQueue.add(new Runnable() {
                                @Override
                                public void run() {
                                    if (profile == null) {
                                        callback.apply(gameprofile);
                                    } else {
                                        callback.apply(profile);
                                    }
                                }
                            });
                            return profile;
                        }
                    };
                    if (sync) {
                        try {
                            return Futures.immediateFuture(callable.call());
                        } catch (Exception ex) {
                            com.google.common.base.Throwables.throwIfUnchecked(ex);
                            throw new RuntimeException(ex); // Not possible
                        }
                    } else {
                        return executor.submit(callable);
                    }
                }
            }
        } else {
            callback.apply(gameprofile);
        }

        return Futures.immediateFuture(gameprofile);
    }
    // Spigot end
}
