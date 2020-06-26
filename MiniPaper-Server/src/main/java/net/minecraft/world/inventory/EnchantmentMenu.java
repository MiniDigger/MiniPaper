package net.minecraft.world.inventory;

import java.util.List;
import java.util.Random;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
// CraftBukkit start
import java.util.Collections;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftInventoryEnchanting;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.entity.Player;
// CraftBukkit end

public class EnchantmentMenu extends AbstractContainerMenu {

    private final Container enchantSlots;
    private final ContainerLevelAccess access;
    private final Random random;
    private final DataSlot enchantmentSeed;
    public final int[] costs;
    public final int[] enchantClue;
    public final int[] levelClue;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;
    // CraftBukkit end

    public EnchantmentMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, ContainerLevelAccess.NULL);
    }

    public EnchantmentMenu(int i, Inventory playerinventory, ContainerLevelAccess containeraccess) {
        super(MenuType.ENCHANTMENT, i);
        this.enchantSlots = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                EnchantmentMenu.this.slotsChanged((Container) this);
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return containeraccess.getLocation();
            }
            // CraftBukkit end
        };
        this.random = new Random();
        this.enchantmentSeed = DataSlot.standalone();
        this.costs = new int[3];
        this.enchantClue = new int[]{-1, -1, -1};
        this.levelClue = new int[]{-1, -1, -1};
        this.access = containeraccess;
        this.addSlot(new Slot(this.enchantSlots, 0, 15, 47) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return true;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(this.enchantSlots, 1, 35, 47) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.getItem() == Items.LAPIS_LAZULI;
            }
        });

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 142));
        }

        this.addDataSlot(DataSlot.shared(this.costs, 0));
        this.addDataSlot(DataSlot.shared(this.costs, 1));
        this.addDataSlot(DataSlot.shared(this.costs, 2));
        this.addDataSlot(this.enchantmentSeed).set(playerinventory.player.getEnchantmentSeed());
        this.addDataSlot(DataSlot.shared(this.enchantClue, 0));
        this.addDataSlot(DataSlot.shared(this.enchantClue, 1));
        this.addDataSlot(DataSlot.shared(this.enchantClue, 2));
        this.addDataSlot(DataSlot.shared(this.levelClue, 0));
        this.addDataSlot(DataSlot.shared(this.levelClue, 1));
        this.addDataSlot(DataSlot.shared(this.levelClue, 2));
        // CraftBukkit start
        player = (Player) playerinventory.player.getBukkitEntity();
        // CraftBukkit end
    }

    @Override
    public void slotsChanged(Container iinventory) {
        if (iinventory == this.enchantSlots) {
            ItemStack itemstack = iinventory.getItem(0);

            if (!itemstack.isEmpty()) { // CraftBukkit - relax condition
                this.access.execute((world, blockposition) -> {
                    int i = 0;

                    int j;

                    for (j = -1; j <= 1; ++j) {
                        for (int k = -1; k <= 1; ++k) {
                            if ((j != 0 || k != 0) && world.isEmptyBlock(blockposition.offset(k, 0, j)) && world.isEmptyBlock(blockposition.offset(k, 1, j))) {
                                if (world.getType(blockposition.offset(k * 2, 0, j * 2)).is(Blocks.BOOKSHELF)) {
                                    ++i;
                                }

                                if (world.getType(blockposition.offset(k * 2, 1, j * 2)).is(Blocks.BOOKSHELF)) {
                                    ++i;
                                }

                                if (k != 0 && j != 0) {
                                    if (world.getType(blockposition.offset(k * 2, 0, j)).is(Blocks.BOOKSHELF)) {
                                        ++i;
                                    }

                                    if (world.getType(blockposition.offset(k * 2, 1, j)).is(Blocks.BOOKSHELF)) {
                                        ++i;
                                    }

                                    if (world.getType(blockposition.offset(k, 0, j * 2)).is(Blocks.BOOKSHELF)) {
                                        ++i;
                                    }

                                    if (world.getType(blockposition.offset(k, 1, j * 2)).is(Blocks.BOOKSHELF)) {
                                        ++i;
                                    }
                                }
                            }
                        }
                    }

                    this.random.setSeed((long) this.enchantmentSeed.get());

                    for (j = 0; j < 3; ++j) {
                        this.costs[j] = EnchantmentHelper.getEnchantmentCost(this.random, j, i, itemstack);
                        this.enchantClue[j] = -1;
                        this.levelClue[j] = -1;
                        if (this.costs[j] < j + 1) {
                            this.costs[j] = 0;
                        }
                    }

                    for (j = 0; j < 3; ++j) {
                        if (this.costs[j] > 0) {
                            List<EnchantmentInstance> list = this.getEnchantmentList(itemstack, j, this.costs[j]);

                            if (list != null && !list.isEmpty()) {
                                EnchantmentInstance weightedrandomenchant = (EnchantmentInstance) list.get(this.random.nextInt(list.size()));

                                this.enchantClue[j] = Registry.ENCHANTMENT.getId(weightedrandomenchant.enchantment); // CraftBukkit - decompile error
                                this.levelClue[j] = weightedrandomenchant.level;
                            }
                        }
                    }

                    // CraftBukkit start
                    CraftItemStack item = CraftItemStack.asCraftMirror(itemstack);
                    org.bukkit.enchantments.EnchantmentOffer[] offers = new EnchantmentOffer[3];
                    for (j = 0; j < 3; ++j) {
                        org.bukkit.enchantments.Enchantment enchantment = (this.enchantClue[j] >= 0) ? org.bukkit.enchantments.Enchantment.getByKey(CraftNamespacedKey.fromMinecraft(Registry.ENCHANTMENT.getKey(Registry.ENCHANTMENT.byId(this.enchantClue[j])))) : null;
                        offers[j] = (enchantment != null) ? new EnchantmentOffer(enchantment, this.levelClue[j], this.costs[j]) : null;
                    }

                    PrepareItemEnchantEvent event = new PrepareItemEnchantEvent(player, this.getBukkitView(), access.getLocation().getBlock(), item, offers, i);
                    event.setCancelled(!itemstack.isEnchantable());
                    world.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        for (j = 0; j < 3; ++j) {
                            this.costs[j] = 0;
                            this.enchantClue[j] = -1;
                            this.levelClue[j] = -1;
                        }
                        return;
                    }

                    for (j = 0; j < 3; j++) {
                        EnchantmentOffer offer = event.getOffers()[j];
                        if (offer != null) {
                            this.costs[j] = offer.getCost();
                            this.enchantClue[j] = Registry.ENCHANTMENT.getId(Registry.ENCHANTMENT.get(CraftNamespacedKey.toMinecraft(offer.getEnchantment().getKey())));
                            this.levelClue[j] = offer.getEnchantmentLevel();
                        } else {
                            this.costs[j] = 0;
                            this.enchantClue[j] = -1;
                            this.levelClue[j] = -1;
                        }
                    }
                    // CraftBukkit end

                    this.broadcastChanges();
                });
            } else {
                for (int i = 0; i < 3; ++i) {
                    this.costs[i] = 0;
                    this.enchantClue[i] = -1;
                    this.levelClue[i] = -1;
                }
            }
        }

    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player entityhuman, int i) {
        ItemStack itemstack = this.enchantSlots.getItem(0);
        ItemStack itemstack1 = this.enchantSlots.getItem(1);
        int j = i + 1;

        if ((itemstack1.isEmpty() || itemstack1.getCount() < j) && !entityhuman.abilities.instabuild) {
            return false;
        } else if (this.costs[i] > 0 && !itemstack.isEmpty() && (entityhuman.experienceLevel >= j && entityhuman.experienceLevel >= this.costs[i] || entityhuman.abilities.instabuild)) {
            this.access.execute((world, blockposition) -> {
                ItemStack itemstack2 = itemstack;
                List<EnchantmentInstance> list = this.getEnchantmentList(itemstack, i, this.costs[i]);

                // CraftBukkit start
                if (true || !list.isEmpty()) {
                    // entityhuman.enchantDone(itemstack, j); // Moved down
                    boolean flag = itemstack.getItem() == Items.BOOK;
                    Map<org.bukkit.enchantments.Enchantment, Integer> enchants = new java.util.HashMap<org.bukkit.enchantments.Enchantment, Integer>();
                    for (Object obj : list) {
                        EnchantmentInstance instance = (EnchantmentInstance) obj;
                        enchants.put(org.bukkit.enchantments.Enchantment.getByKey(CraftNamespacedKey.fromMinecraft(Registry.ENCHANTMENT.getKey(instance.enchantment))), instance.level);
                    }
                    CraftItemStack item = CraftItemStack.asCraftMirror(itemstack2);

                    EnchantItemEvent event = new EnchantItemEvent((Player) entityhuman.getBukkitEntity(), this.getBukkitView(), access.getLocation().getBlock(), item, this.costs[i], enchants, i);
                    world.getServerOH().getPluginManager().callEvent(event);

                    int level = event.getExpLevelCost();
                    if (event.isCancelled() || (level > entityhuman.experienceLevel && !entityhuman.abilities.instabuild) || event.getEnchantsToAdd().isEmpty()) {
                        return;
                    }

                    if (flag) {
                        itemstack2 = new ItemStack(Items.ENCHANTED_BOOK);
                        CompoundTag nbttagcompound = itemstack.getTag();

                        if (nbttagcompound != null) {
                            itemstack2.setTag(nbttagcompound.copy());
                        }

                        this.enchantSlots.setItem(0, itemstack2);
                    }

                    for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
                        try {
                            if (flag) {
                                NamespacedKey enchantId = entry.getKey().getKey();
                                Enchantment nms = Registry.ENCHANTMENT.get(CraftNamespacedKey.toMinecraft(enchantId));
                                if (nms == null) {
                                    continue;
                                }

                                EnchantmentInstance weightedrandomenchant = new EnchantmentInstance(nms, entry.getValue());
                                EnchantedBookItem.addEnchantment(itemstack2, weightedrandomenchant);
                            } else {
                                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                            }
                        } catch (IllegalArgumentException e) {
                            /* Just swallow invalid enchantments */
                        }
                    }

                    entityhuman.onEnchantmentPerformed(itemstack, j);
                    // CraftBukkit end

                    // CraftBukkit - TODO: let plugins change this
                    if (!entityhuman.abilities.instabuild) {
                        itemstack1.shrink(j);
                        if (itemstack1.isEmpty()) {
                            this.enchantSlots.setItem(1, ItemStack.EMPTY);
                        }
                    }

                    entityhuman.awardStat(Stats.ENCHANT_ITEM);
                    if (entityhuman instanceof ServerPlayer) {
                        CriteriaTriggers.ENCHANTED_ITEM.trigger((ServerPlayer) entityhuman, itemstack2, j);
                    }

                    this.enchantSlots.setChanged();
                    this.enchantmentSeed.set(entityhuman.getEnchantmentSeed());
                    this.slotsChanged(this.enchantSlots);
                    world.playSound((net.minecraft.world.entity.player.Player) null, blockposition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, world.random.nextFloat() * 0.1F + 0.9F);
                }

            });
            return true;
        } else {
            return false;
        }
    }

    private List<EnchantmentInstance> getEnchantmentList(ItemStack itemstack, int i, int j) {
        this.random.setSeed((long) (this.enchantmentSeed.get() + i));
        List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(this.random, itemstack, j, false);

        if (itemstack.getItem() == Items.BOOK && list.size() > 1) {
            list.remove(this.random.nextInt(list.size()));
        }

        return list;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player entityhuman) {
        super.removed(entityhuman);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(entityhuman, entityhuman.level, this.enchantSlots);
        });
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, entityhuman, Blocks.ENCHANTING_TABLE);
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i == 0) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (i == 1) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (itemstack1.getItem() == Items.LAPIS_LAZULI) {
                if (!this.moveItemStackTo(itemstack1, 1, 2, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (((Slot) this.slots.get(0)).hasItem() || !((Slot) this.slots.get(0)).mayPlace(itemstack1)) {
                    return ItemStack.EMPTY;
                }

                ItemStack itemstack2 = itemstack1.copy();

                itemstack2.setCount(1);
                itemstack1.shrink(1);
                ((Slot) this.slots.get(0)).set(itemstack2);
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(entityhuman, itemstack1);
        }

        return itemstack;
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryEnchanting inventory = new CraftInventoryEnchanting(this.enchantSlots);
        bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
