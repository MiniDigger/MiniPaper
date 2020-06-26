package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
// CraftBukkit end

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeHolder, StackedContentsCompatible, TickableBlockEntity {

    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    protected NonNullList<ItemStack> items;
    public int litTime;
    private int litDuration;
    public int cookingProgress;
    public int cookingTotalTime;
    protected final ContainerData dataAccess;
    private final Object2IntOpenHashMap<ResourceLocation> recipesUsed;
    protected final RecipeType<? extends AbstractCookingRecipe> recipeType;

    protected AbstractFurnaceBlockEntity(BlockEntityType<?> tileentitytypes, RecipeType<? extends AbstractCookingRecipe> recipes) {
        super(tileentitytypes);
        this.items = NonNullList.withSize(3, ItemStack.EMPTY);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int i) {
                switch (i) {
                    case 0:
                        return AbstractFurnaceBlockEntity.this.litTime;
                    case 1:
                        return AbstractFurnaceBlockEntity.this.litDuration;
                    case 2:
                        return AbstractFurnaceBlockEntity.this.cookingProgress;
                    case 3:
                        return AbstractFurnaceBlockEntity.this.cookingTotalTime;
                    default:
                        return 0;
                }
            }

            @Override
            public void set(int i, int j) {
                switch (i) {
                    case 0:
                        AbstractFurnaceBlockEntity.this.litTime = j;
                        break;
                    case 1:
                        AbstractFurnaceBlockEntity.this.litDuration = j;
                        break;
                    case 2:
                        AbstractFurnaceBlockEntity.this.cookingProgress = j;
                        break;
                    case 3:
                        AbstractFurnaceBlockEntity.this.cookingTotalTime = j;
                }

            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        this.recipesUsed = new Object2IntOpenHashMap();
        this.recipeType = recipes;
    }

    public static Map<Item, Integer> getFuel() {
        Map<Item, Integer> map = Maps.newLinkedHashMap();

        add(map, (ItemLike) Items.LAVA_BUCKET, 20000);
        add(map, (ItemLike) Blocks.COAL_BLOCK, 16000);
        add(map, (ItemLike) Items.BLAZE_ROD, 2400);
        add(map, (ItemLike) Items.COAL, 1600);
        add(map, (ItemLike) Items.CHARCOAL, 1600);
        add(map, (Tag) ItemTags.LOGS, 300);
        add(map, (Tag) ItemTags.PLANKS, 300);
        add(map, (Tag) ItemTags.WOODEN_STAIRS, 300);
        add(map, (Tag) ItemTags.WOODEN_SLABS, 150);
        add(map, (Tag) ItemTags.WOODEN_TRAPDOORS, 300);
        add(map, (Tag) ItemTags.WOODEN_PRESSURE_PLATES, 300);
        add(map, (ItemLike) Blocks.OAK_FENCE, 300);
        add(map, (ItemLike) Blocks.BIRCH_FENCE, 300);
        add(map, (ItemLike) Blocks.SPRUCE_FENCE, 300);
        add(map, (ItemLike) Blocks.JUNGLE_FENCE, 300);
        add(map, (ItemLike) Blocks.DARK_OAK_FENCE, 300);
        add(map, (ItemLike) Blocks.ACACIA_FENCE, 300);
        add(map, (ItemLike) Blocks.OAK_FENCE_GATE, 300);
        add(map, (ItemLike) Blocks.BIRCH_FENCE_GATE, 300);
        add(map, (ItemLike) Blocks.SPRUCE_FENCE_GATE, 300);
        add(map, (ItemLike) Blocks.JUNGLE_FENCE_GATE, 300);
        add(map, (ItemLike) Blocks.DARK_OAK_FENCE_GATE, 300);
        add(map, (ItemLike) Blocks.ACACIA_FENCE_GATE, 300);
        add(map, (ItemLike) Blocks.NOTE_BLOCK, 300);
        add(map, (ItemLike) Blocks.BOOKSHELF, 300);
        add(map, (ItemLike) Blocks.LECTERN, 300);
        add(map, (ItemLike) Blocks.JUKEBOX, 300);
        add(map, (ItemLike) Blocks.CHEST, 300);
        add(map, (ItemLike) Blocks.TRAPPED_CHEST, 300);
        add(map, (ItemLike) Blocks.CRAFTING_TABLE, 300);
        add(map, (ItemLike) Blocks.DAYLIGHT_DETECTOR, 300);
        add(map, (Tag) ItemTags.BANNERS, 300);
        add(map, (ItemLike) Items.BOW, 300);
        add(map, (ItemLike) Items.FISHING_ROD, 300);
        add(map, (ItemLike) Blocks.LADDER, 300);
        add(map, (Tag) ItemTags.SIGNS, 200);
        add(map, (ItemLike) Items.WOODEN_SHOVEL, 200);
        add(map, (ItemLike) Items.WOODEN_SWORD, 200);
        add(map, (ItemLike) Items.WOODEN_HOE, 200);
        add(map, (ItemLike) Items.WOODEN_AXE, 200);
        add(map, (ItemLike) Items.WOODEN_PICKAXE, 200);
        add(map, (Tag) ItemTags.WOODEN_DOORS, 200);
        add(map, (Tag) ItemTags.BOATS, 1200);
        add(map, (Tag) ItemTags.WOOL, 100);
        add(map, (Tag) ItemTags.WOODEN_BUTTONS, 100);
        add(map, (ItemLike) Items.STICK, 100);
        add(map, (Tag) ItemTags.SAPLINGS, 100);
        add(map, (ItemLike) Items.BOWL, 100);
        add(map, (Tag) ItemTags.CARPETS, 67);
        add(map, (ItemLike) Blocks.DRIED_KELP_BLOCK, 4001);
        add(map, (ItemLike) Items.CROSSBOW, 300);
        add(map, (ItemLike) Blocks.BAMBOO, 50);
        add(map, (ItemLike) Blocks.DEAD_BUSH, 100);
        add(map, (ItemLike) Blocks.SCAFFOLDING, 400);
        add(map, (ItemLike) Blocks.LOOM, 300);
        add(map, (ItemLike) Blocks.BARREL, 300);
        add(map, (ItemLike) Blocks.CARTOGRAPHY_TABLE, 300);
        add(map, (ItemLike) Blocks.FLETCHING_TABLE, 300);
        add(map, (ItemLike) Blocks.SMITHING_TABLE, 300);
        add(map, (ItemLike) Blocks.COMPOSTER, 300);
        return map;
    }

    // CraftBukkit start - add fields and methods
    private int maxStack = MAX_STACK;
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();

    public List<ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    private static boolean isNeverAFurnaceFuel(Item item) {
        return ItemTags.NON_FLAMMABLE_WOOD.contains(item);
    }

    private static void add(Map<Item, Integer> map, Tag<Item> tag, int i) {
        Iterator iterator = tag.getValues().iterator();

        while (iterator.hasNext()) {
            Item item = (Item) iterator.next();

            if (!isNeverAFurnaceFuel(item)) {
                map.put(item, i);
            }
        }

    }

    private static void add(Map<Item, Integer> map, ItemLike imaterial, int i) {
        Item item = imaterial.asItem();

        if (isNeverAFurnaceFuel(item)) {
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("A developer tried to explicitly make fire resistant item " + item.getName((ItemStack) null).getString() + " a furnace fuel. That will not work!"));
            }
        } else {
            map.put(item, i);
        }
    }

    private boolean isLit() {
        return this.litTime > 0;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbttagcompound, this.items);
        this.litTime = nbttagcompound.getShort("BurnTime");
        this.cookingProgress = nbttagcompound.getShort("CookTime");
        this.cookingTotalTime = nbttagcompound.getShort("CookTimeTotal");
        this.litDuration = this.getBurnDuration((ItemStack) this.items.get(1));
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("RecipesUsed");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            this.recipesUsed.put(new ResourceLocation(s), nbttagcompound1.getInt(s));
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        nbttagcompound.putShort("BurnTime", (short) this.litTime);
        nbttagcompound.putShort("CookTime", (short) this.cookingProgress);
        nbttagcompound.putShort("CookTimeTotal", (short) this.cookingTotalTime);
        ContainerHelper.saveAllItems(nbttagcompound, this.items);
        CompoundTag nbttagcompound1 = new CompoundTag();

        this.recipesUsed.forEach((minecraftkey, integer) -> {
            nbttagcompound1.putInt(minecraftkey.toString(), integer);
        });
        nbttagcompound.put("RecipesUsed", nbttagcompound1);
        return nbttagcompound;
    }

    @Override
    public void tick() {
        boolean flag = this.isLit();
        boolean flag1 = false;

        if (this.isLit()) {
            --this.litTime;
        }

        if (!this.level.isClientSide) {
            ItemStack itemstack = (ItemStack) this.items.get(1);

            if (!this.isLit() && (itemstack.isEmpty() || ((ItemStack) this.items.get(0)).isEmpty())) {
                if (!this.isLit() && this.cookingProgress > 0) {
                    this.cookingProgress = Mth.clamp(this.cookingProgress - 2, 0, this.cookingTotalTime);
                }
            } else {
                Recipe irecipe = this.level.getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>) this.recipeType, this, this.level).orElse(null); // Eclipse fail

                if (!this.isLit() && this.canBurn(irecipe)) {
                    // CraftBukkit start
                    CraftItemStack fuel = CraftItemStack.asCraftMirror(itemstack);

                    FurnaceBurnEvent furnaceBurnEvent = new FurnaceBurnEvent(CraftBlock.at(this.level, this.worldPosition), fuel, getBurnDuration(itemstack));
                    this.level.getServerOH().getPluginManager().callEvent(furnaceBurnEvent);

                    if (furnaceBurnEvent.isCancelled()) {
                        return;
                    }

                    this.litTime = furnaceBurnEvent.getBurnTime();
                    this.litDuration = this.litTime;
                    if (this.isLit() && furnaceBurnEvent.isBurning()) {
                        // CraftBukkit end
                        flag1 = true;
                        if (!itemstack.isEmpty()) {
                            Item item = itemstack.getItem();

                            itemstack.shrink(1);
                            if (itemstack.isEmpty()) {
                                Item item1 = item.getCraftingRemainingItem();

                                this.items.set(1, item1 == null ? ItemStack.EMPTY : new ItemStack(item1));
                            }
                        }
                    }
                }

                if (this.isLit() && this.canBurn(irecipe)) {
                    ++this.cookingProgress;
                    if (this.cookingProgress == this.cookingTotalTime) {
                        this.cookingProgress = 0;
                        this.cookingTotalTime = this.getTotalCookTime();
                        this.burn(irecipe);
                        flag1 = true;
                    }
                } else {
                    this.cookingProgress = 0;
                }
            }

            if (flag != this.isLit()) {
                flag1 = true;
                this.level.setTypeAndData(this.worldPosition, (BlockState) this.level.getType(this.worldPosition).setValue(AbstractFurnaceBlock.LIT, this.isLit()), 3);
            }
        }

        if (flag1) {
            this.setChanged();
        }

    }

    protected boolean canBurn(@Nullable Recipe<?> irecipe) {
        if (!((ItemStack) this.items.get(0)).isEmpty() && irecipe != null) {
            ItemStack itemstack = irecipe.getResultItem();

            if (itemstack.isEmpty()) {
                return false;
            } else {
                ItemStack itemstack1 = (ItemStack) this.items.get(2);

                return itemstack1.isEmpty() ? true : (!itemstack1.sameItem(itemstack) ? false : (itemstack1.getCount() < this.getMaxStackSize() && itemstack1.getCount() < itemstack1.getMaxStackSize() ? true : itemstack1.getCount() < itemstack.getMaxStackSize()));
            }
        } else {
            return false;
        }
    }

    private void burn(@Nullable Recipe<?> irecipe) {
        if (irecipe != null && this.canBurn(irecipe)) {
            ItemStack itemstack = (ItemStack) this.items.get(0);
            ItemStack itemstack1 = irecipe.getResultItem();
            ItemStack itemstack2 = (ItemStack) this.items.get(2);

            // CraftBukkit start - fire FurnaceSmeltEvent
            CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
            org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);

            FurnaceSmeltEvent furnaceSmeltEvent = new FurnaceSmeltEvent(this.level.getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()), source, result);
            this.level.getServerOH().getPluginManager().callEvent(furnaceSmeltEvent);

            if (furnaceSmeltEvent.isCancelled()) {
                return;
            }

            result = furnaceSmeltEvent.getResult();
            itemstack1 = CraftItemStack.asNMSCopy(result);

            if (!itemstack1.isEmpty()) {
                if (itemstack2.isEmpty()) {
                    this.items.set(2, itemstack1.copy());
                } else if (CraftItemStack.asCraftMirror(itemstack2).isSimilar(result)) {
                    itemstack2.grow(itemstack1.getCount());
                } else {
                    return;
                }
            }

            /*
            if (itemstack2.isEmpty()) {
                this.items.set(2, itemstack1.cloneItemStack());
            } else if (itemstack2.getItem() == itemstack1.getItem()) {
                itemstack2.add(1);
            }
            */
            // CraftBukkit end

            if (!this.level.isClientSide) {
                this.setRecipeUsed(irecipe);
            }

            if (itemstack.getItem() == Blocks.WET_SPONGE.asItem() && !((ItemStack) this.items.get(1)).isEmpty() && ((ItemStack) this.items.get(1)).getItem() == Items.BUCKET) {
                this.items.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemstack.shrink(1);
        }
    }

    protected int getBurnDuration(ItemStack itemstack) {
        if (itemstack.isEmpty()) {
            return 0;
        } else {
            Item item = itemstack.getItem();

            return (Integer) getFuel().getOrDefault(item, 0);
        }
    }

    protected int getTotalCookTime() {
        return (this.hasLevel()) ? (Integer) this.level.getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>) this.recipeType, this, this.level).map(AbstractCookingRecipe::getCookingTime).orElse(200) : 200; // CraftBukkit - SPIGOT-4302 // Eclipse fail
    }

    public static boolean isFuel(ItemStack itemstack) {
        return getFuel().containsKey(itemstack.getItem());
    }

    @Override
    public int[] getSlotsForFace(Direction enumdirection) {
        return enumdirection == Direction.DOWN ? AbstractFurnaceBlockEntity.SLOTS_FOR_DOWN : (enumdirection == Direction.UP ? AbstractFurnaceBlockEntity.SLOTS_FOR_UP : AbstractFurnaceBlockEntity.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable Direction enumdirection) {
        return this.canPlaceItem(i, itemstack);
    }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack itemstack, Direction enumdirection) {
        if (enumdirection == Direction.DOWN && i == 1) {
            Item item = itemstack.getItem();

            if (item != Items.WATER_BUCKET && item != Items.BUCKET) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getItem(int i) {
        return (ItemStack) this.items.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        return ContainerHelper.removeItem(this.items, i, j);
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(this.items, i);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        ItemStack itemstack1 = (ItemStack) this.items.get(i);
        boolean flag = !itemstack.isEmpty() && itemstack.sameItem(itemstack1) && ItemStack.tagMatches(itemstack, itemstack1);

        this.items.set(i, itemstack);
        if (itemstack.getCount() > this.getMaxStackSize()) {
            itemstack.setCount(this.getMaxStackSize());
        }

        if (i == 0 && !flag) {
            this.cookingTotalTime = this.getTotalCookTime();
            this.cookingProgress = 0;
            this.setChanged();
        }

    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        return this.level.getBlockEntity(this.worldPosition) != this ? false : entityhuman.distanceToSqr((double) this.worldPosition.getX() + 0.5D, (double) this.worldPosition.getY() + 0.5D, (double) this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        if (i == 2) {
            return false;
        } else if (i != 1) {
            return true;
        } else {
            ItemStack itemstack1 = (ItemStack) this.items.get(1);

            return isFuel(itemstack) || itemstack.getItem() == Items.BUCKET && itemstack1.getItem() != Items.BUCKET;
        }
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public void setRecipeUsed(@Nullable Recipe<?> irecipe) {
        if (irecipe != null) {
            ResourceLocation minecraftkey = irecipe.getId();

            this.recipesUsed.addTo(minecraftkey, 1);
        }

    }

    @Nullable
    @Override
    public Recipe<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(net.minecraft.world.entity.player.Player entityhuman) {}

    public void d(net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack, int amount) { // CraftBukkit
        List<Recipe<?>> list = this.a(entityhuman.level, entityhuman.position(), entityhuman, itemstack, amount); // CraftBukkit

        entityhuman.awardRecipes(list);
        this.recipesUsed.clear();
    }

    public List<Recipe<?>> getRecipesToAwardAndPopExperience(Level world, Vec3 vec3d) {
        // CraftBukkit start
        return this.a(world, vec3d, null, null, 0);
    }

    public List<Recipe<?>> a(Level world, Vec3 vec3d, net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack, int amount) {
        // CraftBukkit end
        List<Recipe<?>> list = Lists.newArrayList();
        ObjectIterator objectiterator = this.recipesUsed.object2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<ResourceLocation> entry = (Entry) objectiterator.next();

            world.getRecipeManager().byKey((ResourceLocation) entry.getKey()).ifPresent((irecipe) -> {
                list.add(irecipe);
                a(world, vec3d, entry.getIntValue(), ((AbstractCookingRecipe) irecipe).getExperience(), entityhuman, itemstack, amount); // CraftBukkit
            });
        }

        return list;
    }

    private void a(Level world, Vec3 vec3d, int i, float f, net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack, int amount) { // CraftBukkit
        int j = Mth.floor((float) i * f);
        float f1 = Mth.frac((float) i * f);

        if (f1 != 0.0F && Math.random() < (double) f1) {
            ++j;
        }

        // CraftBukkit start - fire FurnaceExtractEvent
        if (amount != 0) {
            FurnaceExtractEvent event = new FurnaceExtractEvent((Player) entityhuman.getBukkitEntity(), CraftBlock.at(world, worldPosition), org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(itemstack.getItem()), amount, j);
            world.getServerOH().getPluginManager().callEvent(event);
            j = event.getExpToDrop();
        }
        // CraftBukkit end

        while (j > 0) {
            int k = ExperienceOrb.getExperienceValue(j);

            j -= k;
            world.addFreshEntity(new ExperienceOrb(world, vec3d.x, vec3d.y, vec3d.z, k));
        }

    }

    @Override
    public void fillStackedContents(StackedContents autorecipestackmanager) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            autorecipestackmanager.accountStack(itemstack);
        }

    }
}
