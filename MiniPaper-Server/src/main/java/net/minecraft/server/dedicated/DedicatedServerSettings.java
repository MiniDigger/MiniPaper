package net.minecraft.server.dedicated;

import java.util.function.UnaryOperator;
// CraftBukkit start
import java.io.File;
import joptsimple.OptionSet;
// CraftBukkit end

public class DedicatedServerSettings {

    private final java.nio.file.Path source;
    private DedicatedServerProperties properties;

    // CraftBukkit start
    public DedicatedServerSettings(OptionSet optionset) {
        this.source = ((File) optionset.valueOf("config")).toPath();
        this.properties = DedicatedServerProperties.load(source, optionset);
        // CraftBukkit end
    }

    public DedicatedServerProperties getProperties() {
        return this.properties;
    }

    public void forceSave() {
        this.properties.store(this.source);
    }

    public DedicatedServerSettings update(UnaryOperator<DedicatedServerProperties> unaryoperator) {
        (this.properties = (DedicatedServerProperties) unaryoperator.apply(this.properties)).store(this.source);
        return this;
    }
}
