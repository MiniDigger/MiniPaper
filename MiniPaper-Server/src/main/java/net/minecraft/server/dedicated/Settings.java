package net.minecraft.server.dedicated;

import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import joptsimple.OptionSet; // CraftBukkit

public abstract class Settings<T extends Settings<T>> {

    private static final Logger LOGGER = LogManager.getLogger();
    public final Properties properties;
    // CraftBukkit start
    private OptionSet options = null;

    public Settings(Properties properties, final OptionSet options) {
        this.properties = properties;

        this.options = options;
    }

    private String getOverride(String name, String value) {
        if ((this.options != null) && (this.options.has(name)) && !name.equals( "online-mode")) { // Spigot
            return String.valueOf(this.options.valueOf(name));
        }

        return value;
    }
    // CraftBukkit end

    public static Properties loadFromFile(java.nio.file.Path java_nio_file_path) {
        Properties properties = new Properties();

        try {
            InputStream inputstream = Files.newInputStream(java_nio_file_path);
            Throwable throwable = null;

            try {
                properties.load(inputstream);
            } catch (Throwable throwable1) {
                throwable = throwable1;
                throw throwable1;
            } finally {
                if (inputstream != null) {
                    if (throwable != null) {
                        try {
                            inputstream.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    } else {
                        inputstream.close();
                    }
                }

            }
        } catch (IOException ioexception) {
            Settings.LOGGER.error("Failed to load properties from file: " + java_nio_file_path);
        }

        return properties;
    }

    public void store(java.nio.file.Path java_nio_file_path) {
        try {
            // CraftBukkit start - Don't attempt writing to file if it's read only
            if (java_nio_file_path.toFile().exists() && !java_nio_file_path.toFile().canWrite()) {
                return;
            }
            // CraftBukkit end
            OutputStream outputstream = Files.newOutputStream(java_nio_file_path);
            Throwable throwable = null;

            try {
                this.properties.store(outputstream, "Minecraft server properties");
            } catch (Throwable throwable1) {
                throwable = throwable1;
                throw throwable1;
            } finally {
                if (outputstream != null) {
                    if (throwable != null) {
                        try {
                            outputstream.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    } else {
                        outputstream.close();
                    }
                }

            }
        } catch (IOException ioexception) {
            Settings.LOGGER.error("Failed to store properties to file: " + java_nio_file_path);
        }

    }

    private static <V extends Number> Function<String, V> wrapNumberDeserializer(Function<String, V> function) {
        return (s) -> {
            try {
                return (V) function.apply(s); // CraftBukkit - decompile error
            } catch (NumberFormatException numberformatexception) {
                return null;
            }
        };
    }

    protected static <V> Function<String, V> dispatchNumberOrString(IntFunction<V> intfunction, Function<String, V> function) {
        return (s) -> {
            try {
                return intfunction.apply(Integer.parseInt(s));
            } catch (NumberFormatException numberformatexception) {
                return function.apply(s);
            }
        };
    }

    @Nullable
    private String getStringRaw(String s) {
        return (String) getOverride(s, this.properties.getProperty(s)); // CraftBukkit
    }

    @Nullable
    protected <V> V getLegacy(String s, Function<String, V> function) {
        String s1 = this.getStringRaw(s);

        if (s1 == null) {
            return null;
        } else {
            this.properties.remove(s);
            return function.apply(s1);
        }
    }

    protected <V> V get(String s, Function<String, V> function, Function<V, String> function1, V v0) {
        String s1 = this.getStringRaw(s);
        V v1 = MoreObjects.firstNonNull(s1 != null ? function.apply(s1) : null, v0);

        this.properties.put(s, function1.apply(v1));
        return v1;
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String s, Function<String, V> function, Function<V, String> function1, V v0) {
        String s1 = this.getStringRaw(s);
        V v1 = MoreObjects.firstNonNull(s1 != null ? function.apply(s1) : null, v0);

        this.properties.put(s, function1.apply(v1));
        return new Settings.MutableValue(s, v1, function1); // CraftBukkit - decompile error
    }

    protected <V> V get(String s, Function<String, V> function, UnaryOperator<V> unaryoperator, Function<V, String> function1, V v0) {
        return this.get(s, (s1) -> {
            V v1 = function.apply(s1);

            return v1 != null ? unaryoperator.apply(v1) : null;
        }, function1, v0);
    }

    protected <V> V get(String s, Function<String, V> function, V v0) {
        return this.get(s, function, Objects::toString, v0);
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String s, Function<String, V> function, V v0) {
        return this.getMutable(s, function, Objects::toString, v0);
    }

    protected String get(String s, String s1) {
        return (String) this.get(s, Function.identity(), Function.identity(), s1);
    }

    @Nullable
    protected String getLegacyString(String s) {
        return (String) this.getLegacy(s, Function.identity());
    }

    protected int get(String s, int i) {
        return (Integer) this.get(s, wrapNumberDeserializer(Integer::parseInt), i); // CraftBukkit - decompile error
    }

    protected Settings<T>.MutableValue<Integer> getMutable(String s, int i) {
        return this.getMutable(s, wrapNumberDeserializer(Integer::parseInt), i);
    }

    protected int get(String s, UnaryOperator<Integer> unaryoperator, int i) {
        return (Integer) this.get(s, wrapNumberDeserializer(Integer::parseInt), unaryoperator, Objects::toString, i);
    }

    protected long get(String s, long i) {
        return (Long) this.get(s, wrapNumberDeserializer(Long::parseLong), i); // CraftBukkit - decompile error
    }

    protected boolean get(String s, boolean flag) {
        return (Boolean) this.get(s, Boolean::valueOf, (Object) flag);
    }

    protected Settings<T>.MutableValue<Boolean> getMutable(String s, boolean flag) {
        return this.getMutable(s, Boolean::valueOf, flag);
    }

    @Nullable
    protected Boolean getLegacyBoolean(String s) {
        return (Boolean) this.getLegacy(s, Boolean::valueOf);
    }

    protected Properties cloneProperties() {
        Properties properties = new Properties();

        properties.putAll(this.properties);
        return properties;
    }

    protected abstract T reload(Properties properties, OptionSet optionset); // CraftBukkit

    public class MutableValue<V> implements Supplier<V> {

        private final String key;
        private final V value;
        private final Function<V, String> serializer;

        private MutableValue(String s, V object, Function function) { // CraftBukkit - decompile error
            this.key = s;
            this.value = object;
            this.serializer = function;
        }

        public V get() {
            return this.value;
        }

        public T update(V v0) {
            Properties properties = Settings.this.cloneProperties();

            properties.put(this.key, this.serializer.apply(v0));
            return Settings.this.reload(properties, Settings.this.options); // CraftBukkit
        }
    }
}
