package net.minecraft.world.item.crafting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap; // CraftBukkit

public class RecipeManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogManager.getLogger();
    public Map<RecipeType<?>, Object2ObjectLinkedOpenHashMap<ResourceLocation, Recipe<?>>> recipes = ImmutableMap.of(); // CraftBukkit
    private boolean hasErrors;

    public RecipeManager() {
        super(RecipeManager.GSON, "recipes");
    }

    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager iresourcemanager, ProfilerFiller gameprofilerfiller) {
        this.hasErrors = false;
        // CraftBukkit start - SPIGOT-5667 make sure all types are populated and mutable
        Map<RecipeType<?>, Object2ObjectLinkedOpenHashMap<ResourceLocation, Recipe<?>>> map1 = Maps.newHashMap();
        for (RecipeType<?> recipeType : Registry.RECIPE_TYPE) {
            map1.put(recipeType, new Object2ObjectLinkedOpenHashMap<>());
        }
        // CraftBukkit end
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<ResourceLocation, JsonElement> entry = (Entry) iterator.next();
            ResourceLocation minecraftkey = (ResourceLocation) entry.getKey();

            try {
                Recipe<?> irecipe = fromJson(minecraftkey, GsonHelper.convertToJsonObject((JsonElement) entry.getValue(), "top element"));

                // CraftBukkit start - SPIGOT-4638: last recipe gets priority
                (map1.computeIfAbsent(irecipe.getType(), (recipes) -> {
                    return new Object2ObjectLinkedOpenHashMap<>();
                })).putAndMoveToFirst(minecraftkey, irecipe);
                // CraftBukkit end
            } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
                RecipeManager.LOGGER.error("Parsing error loading recipe {}", minecraftkey, jsonparseexception);
            }
        }

        this.recipes = (Map) map1.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry1) -> {
            return (entry1.getValue()); // CraftBukkit
        }));
        RecipeManager.LOGGER.info("Loaded {} recipes", map1.size());
    }

    // CraftBukkit start
    public void addRecipe(Recipe<?> irecipe) {
        org.spigotmc.AsyncCatcher.catchOp("Recipe Add"); // Spigot
        Object2ObjectLinkedOpenHashMap<ResourceLocation, Recipe<?>> map = this.recipes.get(irecipe.getType()); // CraftBukkit

        if (map.containsKey(irecipe.getId())) {
            throw new IllegalStateException("Duplicate recipe ignored with ID " + irecipe.getId());
        } else {
            map.putAndMoveToFirst(irecipe.getId(), irecipe); // CraftBukkit - SPIGOT-4638: last recipe gets priority
        }
    }
    // CraftBukkit end

    public <C extends Container, T extends Recipe<C>> Optional<T> getRecipeFor(RecipeType<T> recipes, C c0, Level world) {
        // CraftBukkit start
        Optional<T> recipe = this.byType(recipes).values().stream().flatMap((irecipe) -> {
            return Util.toStream(recipes.tryMatch(irecipe, world, c0));
        }).findFirst();
        c0.setCurrentRecipe(recipe.orElse(null)); // CraftBukkit - Clear recipe when no recipe is found
        // CraftBukkit end
        return recipe;
    }

    public <C extends Container, T extends Recipe<C>> List<T> getAllRecipesFor(RecipeType<T> recipes) {
        return (List) this.byType(recipes).values().stream().map((irecipe) -> {
            return irecipe;
        }).collect(Collectors.toList());
    }

    public <C extends Container, T extends Recipe<C>> List<T> getRecipesFor(RecipeType<T> recipes, C c0, Level world) {
        return (List) this.byType(recipes).values().stream().flatMap((irecipe) -> {
            return Util.toStream(recipes.tryMatch(irecipe, world, c0));
        }).sorted(Comparator.comparing((irecipe) -> {
            return irecipe.getResultItem().getDescriptionId();
        })).collect(Collectors.toList());
    }

    private <C extends Container, T extends Recipe<C>> Map<ResourceLocation, Recipe<C>> byType(RecipeType<T> recipes) {
        return (Map) this.recipes.getOrDefault(recipes, new Object2ObjectLinkedOpenHashMap<>()); // CraftBukkit
    }

    public <C extends Container, T extends Recipe<C>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> recipes, C c0, Level world) {
        Optional<T> optional = this.getRecipeFor(recipes, c0, world);

        if (optional.isPresent()) {
            return ((Recipe) optional.get()).getRemainingItems(c0);
        } else {
            NonNullList<ItemStack> nonnulllist = NonNullList.withSize(c0.getContainerSize(), ItemStack.EMPTY);

            for (int i = 0; i < nonnulllist.size(); ++i) {
                nonnulllist.set(i, c0.getItem(i));
            }

            return nonnulllist;
        }
    }

    public Optional<? extends Recipe<?>> byKey(ResourceLocation minecraftkey) {
        return this.recipes.values().stream().map((map) -> {
            return map.get(minecraftkey); // CraftBukkit - decompile error
        }).filter(Objects::nonNull).findFirst();
    }

    public Collection<Recipe<?>> getRecipes() {
        return (Collection) this.recipes.values().stream().flatMap((map) -> {
            return map.values().stream();
        }).collect(Collectors.toSet());
    }

    public Stream<ResourceLocation> getRecipeIds() {
        return this.recipes.values().stream().flatMap((map) -> {
            return map.keySet().stream();
        });
    }

    public static Recipe<?> fromJson(ResourceLocation minecraftkey, JsonObject jsonobject) {
        String s = GsonHelper.getAsString(jsonobject, "type");

        return ((RecipeSerializer) Registry.RECIPE_SERIALIZER.getOptional(new ResourceLocation(s)).orElseThrow(() -> {
            return new JsonSyntaxException("Invalid or unsupported recipe type '" + s + "'");
        })).fromJson(minecraftkey, jsonobject);
    }

    // CraftBukkit start
    public void clearRecipes() {
        this.recipes = Maps.newHashMap();

        for (RecipeType<?> recipeType : Registry.RECIPE_TYPE) {
            this.recipes.put(recipeType, new Object2ObjectLinkedOpenHashMap<>());
        }
    }
    // CraftBukkit end
}
