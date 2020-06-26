package net.minecraft.stats;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ResourceLocationException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ServerRecipeBook extends RecipeBook {

    private static final Logger LOGGER = LogManager.getLogger();

    public ServerRecipeBook() {}

    public int addRecipes(Collection<Recipe<?>> collection, ServerPlayer entityplayer) {
        List<ResourceLocation> list = Lists.newArrayList();
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Recipe<?> irecipe = (Recipe) iterator.next();
            ResourceLocation minecraftkey = irecipe.getId();

            if (!this.known.contains(minecraftkey) && !irecipe.isSpecial() && CraftEventFactory.handlePlayerRecipeListUpdateEvent(entityplayer, minecraftkey)) { // CraftBukkit
                this.add(minecraftkey);
                this.addHighlight(minecraftkey);
                list.add(minecraftkey);
                CriteriaTriggers.RECIPE_UNLOCKED.trigger(entityplayer, irecipe);
                ++i;
            }
        }

        this.sendRecipes(ClientboundRecipePacket.State.ADD, entityplayer, (List) list);
        return i;
    }

    public int removeRecipes(Collection<Recipe<?>> collection, ServerPlayer entityplayer) {
        List<ResourceLocation> list = Lists.newArrayList();
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            Recipe<?> irecipe = (Recipe) iterator.next();
            ResourceLocation minecraftkey = irecipe.getId();

            if (this.known.contains(minecraftkey)) {
                this.remove(minecraftkey);
                list.add(minecraftkey);
                ++i;
            }
        }

        this.sendRecipes(ClientboundRecipePacket.State.REMOVE, entityplayer, (List) list);
        return i;
    }

    private void sendRecipes(ClientboundRecipePacket.State packetplayoutrecipes_action, ServerPlayer entityplayer, List<ResourceLocation> list) {
        if (entityplayer.connection == null) return; // SPIGOT-4478 during PlayerLoginEvent
        entityplayer.connection.sendPacket(new ClientboundRecipePacket(packetplayoutrecipes_action, list, Collections.emptyList(), this.guiOpen, this.filteringCraftable, this.furnaceGuiOpen, this.furnaceFilteringCraftable));
    }

    public CompoundTag toNbt() {
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.putBoolean("isGuiOpen", this.guiOpen);
        nbttagcompound.putBoolean("isFilteringCraftable", this.filteringCraftable);
        nbttagcompound.putBoolean("isFurnaceGuiOpen", this.furnaceGuiOpen);
        nbttagcompound.putBoolean("isFurnaceFilteringCraftable", this.furnaceFilteringCraftable);
        nbttagcompound.putBoolean("isBlastingFurnaceGuiOpen", this.blastingFurnaceGuiOpen);
        nbttagcompound.putBoolean("isBlastingFurnaceFilteringCraftable", this.blastingFurnaceFilteringCraftable);
        nbttagcompound.putBoolean("isSmokerGuiOpen", this.smokerGuiOpen);
        nbttagcompound.putBoolean("isSmokerFilteringCraftable", this.smokerFilteringCraftable);
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.known.iterator();

        while (iterator.hasNext()) {
            ResourceLocation minecraftkey = (ResourceLocation) iterator.next();

            nbttaglist.add(StringTag.valueOf(minecraftkey.toString()));
        }

        nbttagcompound.put("recipes", nbttaglist);
        ListTag nbttaglist1 = new ListTag();
        Iterator iterator1 = this.highlight.iterator();

        while (iterator1.hasNext()) {
            ResourceLocation minecraftkey1 = (ResourceLocation) iterator1.next();

            nbttaglist1.add(StringTag.valueOf(minecraftkey1.toString()));
        }

        nbttagcompound.put("toBeDisplayed", nbttaglist1);
        return nbttagcompound;
    }

    public void fromNbt(CompoundTag nbttagcompound, RecipeManager craftingmanager) {
        this.guiOpen = nbttagcompound.getBoolean("isGuiOpen");
        this.filteringCraftable = nbttagcompound.getBoolean("isFilteringCraftable");
        this.furnaceGuiOpen = nbttagcompound.getBoolean("isFurnaceGuiOpen");
        this.furnaceFilteringCraftable = nbttagcompound.getBoolean("isFurnaceFilteringCraftable");
        this.blastingFurnaceGuiOpen = nbttagcompound.getBoolean("isBlastingFurnaceGuiOpen");
        this.blastingFurnaceFilteringCraftable = nbttagcompound.getBoolean("isBlastingFurnaceFilteringCraftable");
        this.smokerGuiOpen = nbttagcompound.getBoolean("isSmokerGuiOpen");
        this.smokerFilteringCraftable = nbttagcompound.getBoolean("isSmokerFilteringCraftable");
        ListTag nbttaglist = nbttagcompound.getList("recipes", 8);

        this.loadRecipes(nbttaglist, this::add, craftingmanager);
        ListTag nbttaglist1 = nbttagcompound.getList("toBeDisplayed", 8);

        this.loadRecipes(nbttaglist1, this::addHighlight, craftingmanager);
    }

    private void loadRecipes(ListTag nbttaglist, Consumer<Recipe<?>> consumer, RecipeManager craftingmanager) {
        for (int i = 0; i < nbttaglist.size(); ++i) {
            String s = nbttaglist.getString(i);

            try {
                ResourceLocation minecraftkey = new ResourceLocation(s);
                Optional<? extends Recipe<?>> optional = craftingmanager.byKey(minecraftkey);

                if (!optional.isPresent()) {
                    ServerRecipeBook.LOGGER.error("Tried to load unrecognized recipe: {} removed now.", minecraftkey);
                } else {
                    consumer.accept(optional.get());
                }
            } catch (ResourceLocationException resourcekeyinvalidexception) {
                ServerRecipeBook.LOGGER.error("Tried to load improperly formatted recipe: {} removed now.", s);
            }
        }

    }

    public void sendInitialRecipeBook(ServerPlayer entityplayer) {
        entityplayer.connection.sendPacket(new ClientboundRecipePacket(ClientboundRecipePacket.State.INIT, this.known, this.highlight, this.guiOpen, this.filteringCraftable, this.furnaceGuiOpen, this.furnaceFilteringCraftable));
    }
}
