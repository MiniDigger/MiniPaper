package net.minecraft.world.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagManager;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.enchantment.DigDurabilityEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public final class ItemStack {

    public static final Codec<ItemStack> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Registry.ITEM.fieldOf("id").forGetter((itemstack) -> {
            return itemstack.item;
        }), Codec.INT.fieldOf("Count").forGetter((itemstack) -> {
            return itemstack.count;
        }), CompoundTag.CODEC.optionalFieldOf("tag").forGetter((itemstack) -> {
            return Optional.ofNullable(itemstack.tag);
        })).apply(instance, ItemStack::new);
    });
    public static final Logger LOGGER = LogManager.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Item) null);
    public static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = (DecimalFormat) Util.make((new DecimalFormat("#.##")), (decimalformat) -> { // CraftBukkit - decompile error
        decimalformat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final Style LORE_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);
    public int count;
    public int popTime;
    @Deprecated
    public Item item;
    public CompoundTag tag;
    public boolean emptyCacheFlag;
    public Entity entityRepresentation;
    public BlockInWorld cachedBreakBlock;
    public boolean cachedBreakBlockResult;
    public BlockInWorld cachedPlaceBlock;
    public boolean cachedPlaceBlockResult;

    public ItemStack(ItemLike imaterial) {
        this(imaterial, 1);
    }

    private ItemStack(ItemLike imaterial, int i, Optional<CompoundTag> optional) {
        this(imaterial, i);
        optional.ifPresent(this::setTag);
    }

    public ItemStack(ItemLike imaterial, int i) {
        this.item = imaterial == null ? null : imaterial.asItem();
        this.count = i;
        if (this.item != null && this.item.canBeDepleted()) {
            this.setDamageValue(this.getDamageValue());
        }

        this.updateEmptyCacheFlag();
    }

    // Called to run this stack through the data converter to handle older storage methods and serialized items
    public void convertStack(int version) {
        if (0 < version && version < CraftMagicNumbers.INSTANCE.getDataVersion()) {
            CompoundTag savedStack = new CompoundTag();
            this.save(savedStack);
            savedStack = (CompoundTag) MinecraftServer.getServer().fixerUpper.update(References.ITEM_STACK, new Dynamic(NbtOps.INSTANCE, savedStack), version, CraftMagicNumbers.INSTANCE.getDataVersion()).getValue();
            this.load(savedStack);
        }
    }

    private void updateEmptyCacheFlag() {
        if (this.emptyCacheFlag && this == ItemStack.EMPTY) throw new AssertionError("TRAP"); // CraftBukkit
        this.emptyCacheFlag = false;
        this.emptyCacheFlag = this.isEmpty();
    }

    // CraftBukkit - break into own method
    private void load(CompoundTag nbttagcompound) {
        this.item = (Item) Registry.ITEM.get(new ResourceLocation(nbttagcompound.getString("id")));
        this.count = nbttagcompound.getByte("Count");
        if (nbttagcompound.contains("tag", 10)) {
            // CraftBukkit start - make defensive copy as this data may be coming from the save thread
            this.tag = (CompoundTag) nbttagcompound.getCompound("tag").copy();
            this.getItem().verifyTagAfterLoad(this.tag);
            // CraftBukkit end
        }

        if (this.getItem().canBeDepleted()) {
            this.setDamageValue(this.getDamageValue());
        }

    }

    private ItemStack(CompoundTag nbttagcompound) {
        this.load(nbttagcompound);
        // CraftBukkit end
        this.updateEmptyCacheFlag();
    }

    public static ItemStack of(CompoundTag nbttagcompound) {
        try {
            return new ItemStack(nbttagcompound);
        } catch (RuntimeException runtimeexception) {
            ItemStack.LOGGER.debug("Tried to load invalid item: {}", nbttagcompound, runtimeexception);
            return ItemStack.EMPTY;
        }
    }

    public boolean isEmpty() {
        return this == ItemStack.EMPTY ? true : (this.getItem() != null && this.getItem() != Items.AIR ? this.count <= 0 : true);
    }

    public ItemStack split(int i) {
        int j = Math.min(i, this.count);
        ItemStack itemstack = this.copy();

        itemstack.setCount(j);
        this.shrink(j);
        return itemstack;
    }

    public Item getItem() {
        return this.emptyCacheFlag ? Items.AIR : this.item;
    }

    public InteractionResult placeItem(UseOnContext itemactioncontext, InteractionHand enumhand) { // CraftBukkit - add hand
        net.minecraft.world.entity.player.Player entityhuman = itemactioncontext.getPlayer();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockInWorld shapedetectorblock = new BlockInWorld(itemactioncontext.getLevel(), blockposition, false);

        if (entityhuman != null && !entityhuman.abilities.mayBuild && !this.hasAdventureModePlaceTagForBlock(itemactioncontext.getLevel().getTagManager(), shapedetectorblock)) {
            return InteractionResult.PASS;
        } else {
            // CraftBukkit start - handle all block place event logic here
            CompoundTag oldData = this.getTagClone();
            int oldCount = this.getCount();
            ServerLevel world = (ServerLevel) itemactioncontext.getLevel();

            if (!(this.getItem() instanceof BucketItem)) { // if not bucket
                world.captureBlockStates = true;
                // special case bonemeal
                if (this.getItem() == Items.BONE_MEAL) {
                    world.captureTreeGeneration = true;
                }
            }
            Item item = this.getItem();
            InteractionResult enuminteractionresult = item.useOn(itemactioncontext);
            CompoundTag newData = this.getTagClone();
            int newCount = this.getCount();
            this.setCount(oldCount);
            this.setTagClone(oldData);
            world.captureBlockStates = false;
            if (enuminteractionresult.consumesAction() && world.captureTreeGeneration && world.capturedBlockStates.size() > 0) {
                world.captureTreeGeneration = false;
                Location location = new Location(world.getWorld(), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                TreeType treeType = SaplingBlock.treeType;
                SaplingBlock.treeType = null;
                List<BlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                world.capturedBlockStates.clear();
                StructureGrowEvent structureEvent = null;
                if (treeType != null) {
                    boolean isBonemeal = getItem() == Items.BONE_MEAL;
                    structureEvent = new StructureGrowEvent(location, treeType, isBonemeal, (Player) entityhuman.getBukkitEntity(), blocks);
                    org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                }

                BlockFertilizeEvent fertilizeEvent = new BlockFertilizeEvent(CraftBlock.at(world, blockposition), (Player) entityhuman.getBukkitEntity(), blocks);
                fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                if (!fertilizeEvent.isCancelled()) {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.tag, oldData)) {
                        this.setTag(newData);
                        this.setCount(newCount);
                    }
                    for (BlockState blockstate : blocks) {
                        blockstate.update(true);
                    }
                }

                return enuminteractionresult;
            }
            world.captureTreeGeneration = false;

            if (entityhuman != null && enuminteractionresult.consumesAction()) {
                org.bukkit.event.block.BlockPlaceEvent placeEvent = null;
                List<BlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                world.capturedBlockStates.clear();
                if (blocks.size() > 1) {
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockMultiPlaceEvent(world, entityhuman, enumhand, blocks, blockposition.getX(), blockposition.getY(), blockposition.getZ());
                } else if (blocks.size() == 1) {
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(world, entityhuman, enumhand, blocks.get(0), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                }

                if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                    enuminteractionresult = InteractionResult.FAIL; // cancel placement
                    // PAIL: Remove this when MC-99075 fixed
                    placeEvent.getPlayer().updateInventory();
                    // revert back all captured blocks
                    for (BlockState blockstate : blocks) {
                        blockstate.update(true, false);
                    }

                    // Brute force all possible updates
                    BlockPos placedPos = ((CraftBlock) placeEvent.getBlock()).getPosition();
                    for (Direction dir : Direction.values()) {
                        ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundBlockUpdatePacket(world, placedPos.relative(dir)));
                    }
                } else {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.tag, oldData)) {
                        this.setTag(newData);
                        this.setCount(newCount);
                    }

                    for (Map.Entry<BlockPos, BlockEntity> e : world.capturedTileEntities.entrySet()) {
                        world.setBlockEntity(e.getKey(), e.getValue());
                    }

                    for (BlockState blockstate : blocks) {
                        int updateFlag = ((CraftBlockState) blockstate).getFlag();
                        net.minecraft.world.level.block.state.BlockState oldBlock = ((CraftBlockState) blockstate).getHandle();
                        BlockPos newblockposition = ((CraftBlockState) blockstate).getPosition();
                        net.minecraft.world.level.block.state.BlockState block = world.getType(newblockposition);

                        if (!(block.getBlock() instanceof BaseEntityBlock)) { // Containers get placed automatically
                            block.getBlock().onPlace(block, world, newblockposition, oldBlock, true);
                        }

                        world.notifyAndUpdatePhysics(newblockposition, null, oldBlock, block, world.getType(newblockposition), updateFlag); // send null chunk as chunk.k() returns false by this point
                    }

                    // Special case juke boxes as they update their tile entity. Copied from ItemRecord.
                    // PAIL: checkme on updates.
                    if (this.item instanceof RecordItem) {
                        ((JukeboxBlock) Blocks.JUKEBOX).setRecord(world, blockposition, world.getType(blockposition), this);
                        world.levelEvent((net.minecraft.world.entity.player.Player) null, 1010, blockposition, Item.getId(this.item));
                        this.shrink(1);
                        entityhuman.awardStat(Stats.PLAY_RECORD);
                    }

                    if (this.item == Items.WITHER_SKELETON_SKULL) { // Special case skulls to allow wither spawns to be cancelled
                        BlockPos bp = blockposition;
                        if (!world.getType(blockposition).getMaterial().isReplaceable()) {
                            if (!world.getType(blockposition).getMaterial().isSolid()) {
                                bp = null;
                            } else {
                                bp = bp.relative(itemactioncontext.getClickedFace());
                            }
                        }
                        if (bp != null) {
                            BlockEntity te = world.getBlockEntity(bp);
                            if (te instanceof SkullBlockEntity) {
                                WitherSkullBlock.checkSpawn(world, bp, (SkullBlockEntity) te);
                            }
                        }
                    }

                    // SPIGOT-4678
                    if (this.item instanceof SignItem && SignItem.openSign != null) {
                        try {
                            entityhuman.openTextEdit((SignBlockEntity) world.getBlockEntity(SignItem.openSign));
                        } finally {
                            SignItem.openSign = null;
                        }
                    }

                    // SPIGOT-1288 - play sound stripped from ItemBlock
                    if (this.item instanceof BlockItem) {
                        SoundType soundeffecttype = ((BlockItem) this.item).getBlock().soundType;
                        world.playSound(entityhuman, blockposition, soundeffecttype.getPlaceSound(), SoundSource.BLOCKS, (soundeffecttype.getVolume() + 1.0F) / 2.0F, soundeffecttype.getPitch() * 0.8F);
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(item));
                }
            }
            world.capturedTileEntities.clear();
            world.capturedBlockStates.clear();
            // CraftBukkit end

            return enuminteractionresult;
        }
    }

    public float getDestroySpeed(net.minecraft.world.level.block.state.BlockState iblockdata) {
        return this.getItem().getDestroySpeed(this, iblockdata);
    }

    public InteractionResultHolder<ItemStack> use(Level world, net.minecraft.world.entity.player.Player entityhuman, InteractionHand enumhand) {
        return this.getItem().use(world, entityhuman, enumhand);
    }

    public ItemStack finishUsingItem(Level world, LivingEntity entityliving) {
        return this.getItem().finishUsingItem(this, world, entityliving);
    }

    public CompoundTag save(CompoundTag nbttagcompound) {
        ResourceLocation minecraftkey = Registry.ITEM.getKey(this.getItem());

        nbttagcompound.putString("id", minecraftkey == null ? "minecraft:air" : minecraftkey.toString());
        nbttagcompound.putByte("Count", (byte) this.count);
        if (this.tag != null) {
            nbttagcompound.put("tag", this.tag.copy());
        }

        return nbttagcompound;
    }

    public int getMaxStackSize() {
        return this.getItem().getMaxStackSize();
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        if (!this.emptyCacheFlag && this.getItem().getMaxDamage() > 0) {
            CompoundTag nbttagcompound = this.getTag();

            return nbttagcompound == null || !nbttagcompound.getBoolean("Unbreakable");
        } else {
            return false;
        }
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return this.tag == null ? 0 : this.tag.getInt("Damage");
    }

    public void setDamageValue(int i) {
        this.getOrCreateTag().putInt("Damage", Math.max(0, i));
    }

    public int getMaxDamage() {
        return this.getItem().getMaxDamage();
    }

    public boolean hurt(int i, Random random, @Nullable ServerPlayer entityplayer) {
        if (!this.isDamageableItem()) {
            return false;
        } else {
            int j;

            if (i > 0) {
                j = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, this);
                int k = 0;

                for (int l = 0; j > 0 && l < i; ++l) {
                    if (DigDurabilityEnchantment.shouldIgnoreDurabilityDrop(this, j, random)) {
                        ++k;
                    }
                }

                i -= k;
                // CraftBukkit start
                if (entityplayer != null) {
                    PlayerItemDamageEvent event = new PlayerItemDamageEvent(entityplayer.getBukkitEntity(), CraftItemStack.asCraftMirror(this), i);
                    event.getPlayer().getServer().getPluginManager().callEvent(event);

                    if (i != event.getDamage() || event.isCancelled()) {
                        event.getPlayer().updateInventory();
                    }
                    if (event.isCancelled()) {
                        return false;
                    }

                    i = event.getDamage();
                }
                // CraftBukkit end
                if (i <= 0) {
                    return false;
                }
            }

            if (entityplayer != null && i != 0) {
                CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(entityplayer, this, this.getDamageValue() + i);
            }

            j = this.getDamageValue() + i;
            this.setDamageValue(j);
            return j >= this.getMaxDamage();
        }
    }

    public <T extends LivingEntity> void hurtAndBreak(int i, T t0, Consumer<T> consumer) {
        if (!t0.level.isClientSide && (!(t0 instanceof net.minecraft.world.entity.player.Player) || !((net.minecraft.world.entity.player.Player) t0).abilities.instabuild)) {
            if (this.isDamageableItem()) {
                if (this.hurt(i, t0.getRandom(), t0 instanceof ServerPlayer ? (ServerPlayer) t0 : null)) {
                    consumer.accept(t0);
                    Item item = this.getItem();
                    // CraftBukkit start - Check for item breaking
                    if (this.count == 1 && t0 instanceof net.minecraft.world.entity.player.Player) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerItemBreakEvent((net.minecraft.world.entity.player.Player) t0, this);
                    }
                    // CraftBukkit end

                    this.shrink(1);
                    if (t0 instanceof net.minecraft.world.entity.player.Player) {
                        ((net.minecraft.world.entity.player.Player) t0).awardStat(Stats.ITEM_BROKEN.get(item));
                    }

                    this.setDamageValue(0);
                }

            }
        }
    }

    public void hurtEnemy(LivingEntity entityliving, net.minecraft.world.entity.player.Player entityhuman) {
        Item item = this.getItem();

        if (item.hurtEnemy(this, entityliving, (LivingEntity) entityhuman)) {
            entityhuman.awardStat(Stats.ITEM_USED.get(item));
        }

    }

    public void mineBlock(Level world, net.minecraft.world.level.block.state.BlockState iblockdata, BlockPos blockposition, net.minecraft.world.entity.player.Player entityhuman) {
        Item item = this.getItem();

        if (item.mineBlock(this, world, iblockdata, blockposition, entityhuman)) {
            entityhuman.awardStat(Stats.ITEM_USED.get(item));
        }

    }

    public boolean canDestroySpecialBlock(net.minecraft.world.level.block.state.BlockState iblockdata) {
        return this.getItem().canDestroySpecialBlock(iblockdata);
    }

    public InteractionResult interactLivingEntity(net.minecraft.world.entity.player.Player entityhuman, LivingEntity entityliving, InteractionHand enumhand) {
        return this.getItem().interactLivingEntity(this, entityhuman, entityliving, enumhand);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(this.getItem(), this.count);

            itemstack.setPopTime(this.getPopTime());
            if (this.tag != null) {
                itemstack.tag = this.tag.copy();
            }

            return itemstack;
        }
    }

    public static boolean tagMatches(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.isEmpty() && itemstack1.isEmpty() ? true : (!itemstack.isEmpty() && !itemstack1.isEmpty() ? (itemstack.tag == null && itemstack1.tag != null ? false : itemstack.tag == null || itemstack.tag.equals(itemstack1.tag)) : false);
    }

    public static boolean matches(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.isEmpty() && itemstack1.isEmpty() ? true : (!itemstack.isEmpty() && !itemstack1.isEmpty() ? itemstack.matches(itemstack1) : false);
    }

    private boolean matches(ItemStack itemstack) {
        return this.count != itemstack.count ? false : (this.getItem() != itemstack.getItem() ? false : (this.tag == null && itemstack.tag != null ? false : this.tag == null || this.tag.equals(itemstack.tag)));
    }

    public static boolean isSame(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack == itemstack1 ? true : (!itemstack.isEmpty() && !itemstack1.isEmpty() ? itemstack.sameItem(itemstack1) : false);
    }

    public static boolean isSameIgnoreDurability(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack == itemstack1 ? true : (!itemstack.isEmpty() && !itemstack1.isEmpty() ? itemstack.sameItemStackIgnoreDurability(itemstack1) : false);
    }

    public boolean sameItem(ItemStack itemstack) {
        return !itemstack.isEmpty() && this.getItem() == itemstack.getItem();
    }

    public boolean sameItemStackIgnoreDurability(ItemStack itemstack) {
        return !this.isDamageableItem() ? this.sameItem(itemstack) : !itemstack.isEmpty() && this.getItem() == itemstack.getItem();
    }

    public String getDescriptionId() {
        return this.getItem().getDescriptionId(this);
    }

    public String toString() {
        return this.count + " " + this.getItem();
    }

    public void inventoryTick(Level world, Entity entity, int i, boolean flag) {
        if (this.popTime > 0) {
            --this.popTime;
        }

        if (this.getItem() != null) {
            this.getItem().inventoryTick(this, world, entity, i, flag);
        }

    }

    public void onCraftedBy(Level world, net.minecraft.world.entity.player.Player entityhuman, int i) {
        entityhuman.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), i);
        this.getItem().onCraftedBy(this, world, entityhuman);
    }

    public int getUseDuration() {
        return this.getItem().getUseDuration(this);
    }

    public UseAnim getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(Level world, LivingEntity entityliving, int i) {
        this.getItem().releaseUsing(this, world, entityliving, i);
    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    public boolean hasTag() {
        return !this.emptyCacheFlag && this.tag != null && !this.tag.isEmpty();
    }

    @Nullable
    public CompoundTag getTag() {
        return this.tag;
    }

    // CraftBukkit start
    @Nullable
    private CompoundTag getTagClone() {
        return this.tag == null ? null : this.tag.copy();
    }

    private void setTagClone(@Nullable CompoundTag nbtttagcompound) {
        this.setTag(nbtttagcompound == null ? null : nbtttagcompound.copy());
    }
    // CraftBukkit end

    public CompoundTag getOrCreateTag() {
        if (this.tag == null) {
            this.setTag(new CompoundTag());
        }

        return this.tag;
    }

    public CompoundTag getOrCreateTagElement(String s) {
        if (this.tag != null && this.tag.contains(s, 10)) {
            return this.tag.getCompound(s);
        } else {
            CompoundTag nbttagcompound = new CompoundTag();

            this.addTagElement(s, (Tag) nbttagcompound);
            return nbttagcompound;
        }
    }

    @Nullable
    public CompoundTag getTagElement(String s) {
        return this.tag != null && this.tag.contains(s, 10) ? this.tag.getCompound(s) : null;
    }

    public void removeTagKey(String s) {
        if (this.tag != null && this.tag.contains(s)) {
            this.tag.remove(s);
            if (this.tag.isEmpty()) {
                this.tag = null;
            }
        }

    }

    public ListTag getEnchantmentTags() {
        return this.tag != null ? this.tag.getList("Enchantments", 10) : new ListTag();
    }

    public void setTag(@Nullable CompoundTag nbttagcompound) {
        this.tag = nbttagcompound;
        if (this.getItem().canBeDepleted()) {
            this.setDamageValue(this.getDamageValue());
        }

    }

    public Component getHoverName() {
        CompoundTag nbttagcompound = this.getTagElement("display");

        if (nbttagcompound != null && nbttagcompound.contains("Name", 8)) {
            try {
                MutableComponent ichatmutablecomponent = Component.ChatSerializer.a(nbttagcompound.getString("Name"));

                if (ichatmutablecomponent != null) {
                    return ichatmutablecomponent;
                }

                nbttagcompound.remove("Name");
            } catch (JsonParseException jsonparseexception) {
                nbttagcompound.remove("Name");
            }
        }

        return this.getItem().getName(this);
    }

    public ItemStack setHoverName(@Nullable Component ichatbasecomponent) {
        CompoundTag nbttagcompound = this.getOrCreateTagElement("display");

        if (ichatbasecomponent != null) {
            nbttagcompound.putString("Name", Component.ChatSerializer.a(ichatbasecomponent));
        } else {
            nbttagcompound.remove("Name");
        }

        return this;
    }

    public void resetHoverName() {
        CompoundTag nbttagcompound = this.getTagElement("display");

        if (nbttagcompound != null) {
            nbttagcompound.remove("Name");
            if (nbttagcompound.isEmpty()) {
                this.removeTagKey("display");
            }
        }

        if (this.tag != null && this.tag.isEmpty()) {
            this.tag = null;
        }

    }

    public boolean hasCustomHoverName() {
        CompoundTag nbttagcompound = this.getTagElement("display");

        return nbttagcompound != null && nbttagcompound.contains("Name", 8);
    }

    public boolean hasFoil() {
        return this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        return this.getItem().getRarity(this);
    }

    public boolean isEnchantable() {
        return !this.getItem().isEnchantable(this) ? false : !this.isEnchanted();
    }

    public void enchant(Enchantment enchantment, int i) {
        this.getOrCreateTag();
        if (!this.tag.contains("Enchantments", 9)) {
            this.tag.put("Enchantments", new ListTag());
        }

        ListTag nbttaglist = this.tag.getList("Enchantments", 10);
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.putString("id", String.valueOf(Registry.ENCHANTMENT.getKey(enchantment)));
        nbttagcompound.putShort("lvl", (short) ((byte) i));
        nbttaglist.add(nbttagcompound);
    }

    public boolean isEnchanted() {
        return this.tag != null && this.tag.contains("Enchantments", 9) ? !this.tag.getList("Enchantments", 10).isEmpty() : false;
    }

    public void addTagElement(String s, Tag nbtbase) {
        this.getOrCreateTag().put(s, nbtbase);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity entity) {
        this.entityRepresentation = entity;
    }

    @Nullable
    public ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame) this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.emptyCacheFlag ? this.entityRepresentation : null;
    }

    public int getBaseRepairCost() {
        return this.hasTag() && this.tag.contains("RepairCost", 3) ? this.tag.getInt("RepairCost") : 0;
    }

    public void setRepairCost(int i) {
        // CraftBukkit start - remove RepairCost tag when 0 (SPIGOT-3945)
        if (i == 0) {
            this.removeTagKey("RepairCost");
            return;
        }
        // CraftBukkit end
        this.getOrCreateTag().putInt("RepairCost", i);
    }

    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot enumitemslot) {
        Object object;

        if (this.hasTag() && this.tag.contains("AttributeModifiers", 9)) {
            object = HashMultimap.create();
            ListTag nbttaglist = this.tag.getList("AttributeModifiers", 10);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                CompoundTag nbttagcompound = nbttaglist.getCompound(i);

                if (!nbttagcompound.contains("Slot", 8) || nbttagcompound.getString("Slot").equals(enumitemslot.getName())) {
                    Optional<Attribute> optional = Registry.ATTRIBUTE.getOptional(ResourceLocation.tryParse(nbttagcompound.getString("AttributeName")));

                    if (optional.isPresent()) {
                        AttributeModifier attributemodifier = AttributeModifier.load(nbttagcompound);

                        if (attributemodifier != null && attributemodifier.getId().getLeastSignificantBits() != 0L && attributemodifier.getId().getMostSignificantBits() != 0L) {
                            ((Multimap) object).put(optional.get(), attributemodifier);
                        }
                    }
                }
            }
        } else {
            object = this.getItem().getDefaultAttributeModifiers(enumitemslot);
        }

        return (Multimap) object;
    }

    public void addAttributeModifier(Attribute attributebase, AttributeModifier attributemodifier, @Nullable EquipmentSlot enumitemslot) {
        this.getOrCreateTag();
        if (!this.tag.contains("AttributeModifiers", 9)) {
            this.tag.put("AttributeModifiers", new ListTag());
        }

        ListTag nbttaglist = this.tag.getList("AttributeModifiers", 10);
        CompoundTag nbttagcompound = attributemodifier.save();

        nbttagcompound.putString("AttributeName", Registry.ATTRIBUTE.getKey(attributebase).toString());
        if (enumitemslot != null) {
            nbttagcompound.putString("Slot", enumitemslot.getName());
        }

        nbttaglist.add(nbttagcompound);
    }

    // CraftBukkit start
    @Deprecated
    public void setItem(Item item) {
        this.item = item;
    }
    // CraftBukkit end

    public Component getDisplayName() {
        MutableComponent ichatmutablecomponent = (new TextComponent("")).append(this.getHoverName());

        if (this.hasCustomHoverName()) {
            ichatmutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent ichatmutablecomponent1 = ComponentUtils.wrapInSquareBrackets((Component) ichatmutablecomponent);

        if (!this.emptyCacheFlag) {
            ichatmutablecomponent1.withStyle(this.getRarity().color).withStyle((chatmodifier) -> {
                return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(this)));
            });
        }

        return ichatmutablecomponent1;
    }

    private static boolean areSameBlocks(BlockInWorld shapedetectorblock, @Nullable BlockInWorld shapedetectorblock1) {
        return shapedetectorblock1 != null && shapedetectorblock.getState() == shapedetectorblock1.getState() ? (shapedetectorblock.getEntity() == null && shapedetectorblock1.getEntity() == null ? true : (shapedetectorblock.getEntity() != null && shapedetectorblock1.getEntity() != null ? Objects.equals(shapedetectorblock.getEntity().save(new CompoundTag()), shapedetectorblock1.getEntity().save(new CompoundTag())) : false)) : false;
    }

    public boolean hasAdventureModeBreakTagForBlock(TagManager tagregistry, BlockInWorld shapedetectorblock) {
        if (areSameBlocks(shapedetectorblock, this.cachedBreakBlock)) {
            return this.cachedBreakBlockResult;
        } else {
            this.cachedBreakBlock = shapedetectorblock;
            if (this.hasTag() && this.tag.contains("CanDestroy", 9)) {
                ListTag nbttaglist = this.tag.getList("CanDestroy", 8);

                for (int i = 0; i < nbttaglist.size(); ++i) {
                    String s = nbttaglist.getString(i);

                    try {
                        Predicate<BlockInWorld> predicate = BlockPredicateArgument.blockPredicate().parse(new StringReader(s)).create(tagregistry);

                        if (predicate.test(shapedetectorblock)) {
                            this.cachedBreakBlockResult = true;
                            return true;
                        }
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        ;
                    }
                }
            }

            this.cachedBreakBlockResult = false;
            return false;
        }
    }

    public boolean hasAdventureModePlaceTagForBlock(TagManager tagregistry, BlockInWorld shapedetectorblock) {
        if (areSameBlocks(shapedetectorblock, this.cachedPlaceBlock)) {
            return this.cachedPlaceBlockResult;
        } else {
            this.cachedPlaceBlock = shapedetectorblock;
            if (this.hasTag() && this.tag.contains("CanPlaceOn", 9)) {
                ListTag nbttaglist = this.tag.getList("CanPlaceOn", 8);

                for (int i = 0; i < nbttaglist.size(); ++i) {
                    String s = nbttaglist.getString(i);

                    try {
                        Predicate<BlockInWorld> predicate = BlockPredicateArgument.blockPredicate().parse(new StringReader(s)).create(tagregistry);

                        if (predicate.test(shapedetectorblock)) {
                            this.cachedPlaceBlockResult = true;
                            return true;
                        }
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        ;
                    }
                }
            }

            this.cachedPlaceBlockResult = false;
            return false;
        }
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int i) {
        this.popTime = i;
    }

    public int getCount() {
        return this.emptyCacheFlag ? 0 : this.count;
    }

    public void setCount(int i) {
        this.count = i;
        this.updateEmptyCacheFlag();
    }

    public void grow(int i) {
        this.setCount(this.count + i);
    }

    public void shrink(int i) {
        this.grow(-i);
    }

    public void onUseTick(Level world, LivingEntity entityliving, int i) {
        this.getItem().onUseTick(world, entityliving, this, i);
    }

    public boolean isEdible() {
        return this.getItem().isEdible();
    }

    public SoundEvent getDrinkingSound() {
        return this.getItem().getDrinkingSound();
    }

    public SoundEvent getEatingSound() {
        return this.getItem().getEatingSound();
    }
}
