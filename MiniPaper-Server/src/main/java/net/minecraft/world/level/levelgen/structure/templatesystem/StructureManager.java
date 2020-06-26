package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StructureManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<ResourceLocation, StructureTemplate> structureRepository = Maps.newConcurrentMap(); // SPIGOT-5287
    private final DataFixer fixerUpper;
    private ResourceManager resourceManager;
    private final java.nio.file.Path generatedDir;

    public StructureManager(ResourceManager iresourcemanager, LevelStorageSource.LevelStorageAccess convertable_conversionsession, DataFixer datafixer) {
        this.resourceManager = iresourcemanager;
        this.fixerUpper = datafixer;
        this.generatedDir = convertable_conversionsession.getLevelPath(LevelResource.GENERATED_DIR).normalize();
    }

    public StructureTemplate getOrCreate(ResourceLocation minecraftkey) {
        StructureTemplate definedstructure = this.get(minecraftkey);

        if (definedstructure == null) {
            definedstructure = new StructureTemplate();
            this.structureRepository.put(minecraftkey, definedstructure);
        }

        return definedstructure;
    }

    @Nullable
    public StructureTemplate get(ResourceLocation minecraftkey) {
        return (StructureTemplate) this.structureRepository.computeIfAbsent(minecraftkey, (minecraftkey1) -> {
            StructureTemplate definedstructure = this.loadFromGenerated(minecraftkey1);

            return definedstructure != null ? definedstructure : this.loadFromResource(minecraftkey1);
        });
    }

    public void onResourceManagerReload(ResourceManager iresourcemanager) {
        this.resourceManager = iresourcemanager;
        this.structureRepository.clear();
    }

    @Nullable
    private StructureTemplate loadFromResource(ResourceLocation minecraftkey) {
        ResourceLocation minecraftkey1 = new ResourceLocation(minecraftkey.getNamespace(), "structures/" + minecraftkey.getPath() + ".nbt");

        try {
            Resource iresource = this.resourceManager.getResource(minecraftkey1);
            Throwable throwable = null;

            StructureTemplate definedstructure;

            try {
                definedstructure = this.readStructure(iresource.getInputStream());
            } catch (Throwable throwable1) {
                throwable = throwable1;
                throw throwable1;
            } finally {
                if (iresource != null) {
                    if (throwable != null) {
                        try {
                            iresource.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    } else {
                        iresource.close();
                    }
                }

            }

            return definedstructure;
        } catch (FileNotFoundException filenotfoundexception) {
            return null;
        } catch (Throwable throwable3) {
            StructureManager.LOGGER.error("Couldn't load structure {}: {}", minecraftkey, throwable3.toString());
            return null;
        }
    }

    @Nullable
    private StructureTemplate loadFromGenerated(ResourceLocation minecraftkey) {
        if (!this.generatedDir.toFile().isDirectory()) {
            return null;
        } else {
            java.nio.file.Path java_nio_file_path = this.createAndValidatePathToStructure(minecraftkey, ".nbt");

            try {
                FileInputStream fileinputstream = new FileInputStream(java_nio_file_path.toFile());
                Throwable throwable = null;

                StructureTemplate definedstructure;

                try {
                    definedstructure = this.readStructure((InputStream) fileinputstream);
                } catch (Throwable throwable1) {
                    throwable = throwable1;
                    throw throwable1;
                } finally {
                    if (fileinputstream != null) {
                        if (throwable != null) {
                            try {
                                fileinputstream.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        } else {
                            fileinputstream.close();
                        }
                    }

                }

                return definedstructure;
            } catch (FileNotFoundException filenotfoundexception) {
                return null;
            } catch (IOException ioexception) {
                StructureManager.LOGGER.error("Couldn't load structure from {}", java_nio_file_path, ioexception);
                return null;
            }
        }
    }

    private StructureTemplate readStructure(InputStream inputstream) throws IOException {
        CompoundTag nbttagcompound = NbtIo.readCompressed(inputstream);

        return this.readStructure(nbttagcompound);
    }

    public StructureTemplate readStructure(CompoundTag nbttagcompound) {
        if (!nbttagcompound.contains("DataVersion", 99)) {
            nbttagcompound.putInt("DataVersion", 500);
        }

        StructureTemplate definedstructure = new StructureTemplate();

        definedstructure.load(NbtUtils.update(this.fixerUpper, DataFixTypes.STRUCTURE, nbttagcompound, nbttagcompound.getInt("DataVersion")));
        return definedstructure;
    }

    public boolean save(ResourceLocation minecraftkey) {
        StructureTemplate definedstructure = (StructureTemplate) this.structureRepository.get(minecraftkey);

        if (definedstructure == null) {
            return false;
        } else {
            java.nio.file.Path java_nio_file_path = this.createAndValidatePathToStructure(minecraftkey, ".nbt");
            java.nio.file.Path java_nio_file_path1 = java_nio_file_path.getParent();

            if (java_nio_file_path1 == null) {
                return false;
            } else {
                try {
                    Files.createDirectories(Files.exists(java_nio_file_path1, new LinkOption[0]) ? java_nio_file_path1.toRealPath() : java_nio_file_path1);
                } catch (IOException ioexception) {
                    StructureManager.LOGGER.error("Failed to create parent directory: {}", java_nio_file_path1);
                    return false;
                }

                CompoundTag nbttagcompound = definedstructure.save(new CompoundTag());

                try {
                    FileOutputStream fileoutputstream = new FileOutputStream(java_nio_file_path.toFile());
                    Throwable throwable = null;

                    try {
                        NbtIo.writeCompressed(nbttagcompound, (OutputStream) fileoutputstream);
                    } catch (Throwable throwable1) {
                        throwable = throwable1;
                        throw throwable1;
                    } finally {
                        if (fileoutputstream != null) {
                            if (throwable != null) {
                                try {
                                    fileoutputstream.close();
                                } catch (Throwable throwable2) {
                                    throwable.addSuppressed(throwable2);
                                }
                            } else {
                                fileoutputstream.close();
                            }
                        }

                    }

                    return true;
                } catch (Throwable throwable3) {
                    return false;
                }
            }
        }
    }

    public java.nio.file.Path createPathToStructure(ResourceLocation minecraftkey, String s) {
        try {
            java.nio.file.Path java_nio_file_path = this.generatedDir.resolve(minecraftkey.getNamespace());
            java.nio.file.Path java_nio_file_path1 = java_nio_file_path.resolve("structures");

            return FileUtil.createPathToResource(java_nio_file_path1, minecraftkey.getPath(), s);
        } catch (InvalidPathException invalidpathexception) {
            throw new ResourceLocationException("Invalid resource path: " + minecraftkey, invalidpathexception);
        }
    }

    private java.nio.file.Path createAndValidatePathToStructure(ResourceLocation minecraftkey, String s) {
        if (minecraftkey.getPath().contains("//")) {
            throw new ResourceLocationException("Invalid resource path: " + minecraftkey);
        } else {
            java.nio.file.Path java_nio_file_path = this.createPathToStructure(minecraftkey, s);

            if (java_nio_file_path.startsWith(this.generatedDir) && FileUtil.isPathNormalized(java_nio_file_path) && FileUtil.isPathPortable(java_nio_file_path)) {
                return java_nio_file_path;
            } else {
                throw new ResourceLocationException("Invalid resource path: " + java_nio_file_path);
            }
        }
    }

    public void remove(ResourceLocation minecraftkey) {
        this.structureRepository.remove(minecraftkey);
    }
}
