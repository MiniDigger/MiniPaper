package net.minecraft.world.entity.vehicle;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public abstract class AbstractMinecartContainer extends AbstractMinecart implements Container, MenuProvider {

    private NonNullList<ItemStack> itemStacks;
    private boolean dropEquipment;
    @Nullable
    public ResourceLocation lootTable;
    public long lootTableSeed;

    // CraftBukkit start
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.itemStacks;
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

    public InventoryHolder getOwner() {
        org.bukkit.entity.Entity cart = getBukkitEntity();
        if(cart instanceof InventoryHolder) return (InventoryHolder) cart;
        return null;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }

    @Override
    public Location getLocation() {
        return getBukkitEntity().getLocation();
    }
    // CraftBukkit end

    protected AbstractMinecartContainer(EntityType<?> entitytypes, Level world) {
        super(entitytypes, world);
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); // CraftBukkit - SPIGOT-3513
        this.dropEquipment = true;
    }

    protected AbstractMinecartContainer(EntityType<?> entitytypes, double d0, double d1, double d2, Level world) {
        super(entitytypes, world, d0, d1, d2);
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); // CraftBukkit - SPIGOT-3513
        this.dropEquipment = true;
    }

    @Override
    public void destroy(DamageSource damagesource) {
        super.destroy(damagesource);
        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            Containers.dropContents(this.level, this, this);
        }

    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.itemStacks.iterator();

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
        this.unpackLootTable((Player) null);
        return (ItemStack) this.itemStacks.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        this.unpackLootTable((Player) null);
        return ContainerHelper.removeItem(this.itemStacks, i, j);
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        this.unpackLootTable((Player) null);
        ItemStack itemstack = (ItemStack) this.itemStacks.get(i);

        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.itemStacks.set(i, ItemStack.EMPTY);
            return itemstack;
        }
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.unpackLootTable((Player) null);
        this.itemStacks.set(i, itemstack);
        if (!itemstack.isEmpty() && itemstack.getCount() > this.getMaxStackSize()) {
            itemstack.setCount(this.getMaxStackSize());
        }

    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        if (i >= 0 && i < this.getContainerSize()) {
            this.setItem(i, itemstack);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.removed ? false : entityhuman.distanceToSqr((Entity) this) <= 64.0D;
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel worldserver) {
        this.dropEquipment = false;
        return super.changeDimension(worldserver);
    }

    @Override
    public void remove() {
        if (!this.level.isClientSide && this.dropEquipment) {
            Containers.dropContents(this.level, this, this);
        }

        super.remove();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (this.lootTable != null) {
            nbttagcompound.putString("LootTable", this.lootTable.toString());
            if (this.lootTableSeed != 0L) {
                nbttagcompound.putLong("LootTableSeed", this.lootTableSeed);
            }
        } else {
            ContainerHelper.saveAllItems(nbttagcompound, this.itemStacks);
        }

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (nbttagcompound.contains("LootTable", 8)) {
            this.lootTable = new ResourceLocation(nbttagcompound.getString("LootTable"));
            this.lootTableSeed = nbttagcompound.getLong("LootTableSeed");
        } else {
            ContainerHelper.loadAllItems(nbttagcompound, this.itemStacks);
        }

    }

    @Override
    public InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        entityhuman.openMenu(this);
        return InteractionResult.sidedSuccess(this.level.isClientSide);
    }

    @Override
    protected void applyNaturalSlowdown() {
        float f = 0.98F;

        if (this.lootTable == null) {
            int i = 15 - AbstractContainerMenu.getRedstoneSignalFromContainer((Container) this);

            f += (float) i * 0.001F;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.0D, (double) f));
    }

    public void unpackLootTable(@Nullable Player entityhuman) {
        if (this.lootTable != null && this.level.getServer() != null) {
            LootTable loottable = this.level.getServer().getLootTables().get(this.lootTable);

            if (entityhuman instanceof ServerPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer) entityhuman, this.lootTable);
            }

            this.lootTable = null;
            LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.level)).set(LootContextParams.BLOCK_POS, this.blockPosition()).withOptionalRandomSeed(this.lootTableSeed);

            if (entityhuman != null) {
                loottableinfo_builder.withLuck(entityhuman.getLuck()).set(LootContextParams.THIS_ENTITY, entityhuman);
            }

            loottable.fill(this, loottableinfo_builder.create(LootContextParamSets.CHEST));
        }

    }

    @Override
    public void clearContent() {
        this.unpackLootTable((Player) null);
        this.itemStacks.clear();
    }

    public void setLootTable(ResourceLocation minecraftkey, long i) {
        this.lootTable = minecraftkey;
        this.lootTableSeed = i;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerinventory, Player entityhuman) {
        if (this.lootTable != null && entityhuman.isSpectator()) {
            return null;
        } else {
            this.unpackLootTable(playerinventory.player);
            return this.createMenu(i, playerinventory);
        }
    }

    protected abstract AbstractContainerMenu createMenu(int i, Inventory playerinventory);
}
