package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Lectern;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class LecternBlockEntity extends BlockEntity implements Clearable, MenuProvider, CommandSource { // CraftBukkit - ICommandListener

    // CraftBukkit start - add fields and methods
    public final Container bookAccess = new net.minecraft.world.level.block.entity.LecternBlockEntity.LecternInventory();
    public class LecternInventory implements Container {

        public List<HumanEntity> transaction = new ArrayList<>();
        private int maxStack = 1;

        @Override
        public List<ItemStack> getContents() {
            return Arrays.asList(book);
        }

        @Override
        public void onOpen(CraftHumanEntity who) {
            transaction.add(who);
        }

        @Override
        public void onClose(CraftHumanEntity who) {
            transaction.remove(who);
        }

        @Override
        public List<HumanEntity> getViewers() {
            return transaction;
        }

        @Override
        public void setMaxStackSize(int i) {
            maxStack = i;
        }

        @Override
        public Location getLocation() {
            return new Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        }

        @Override
        public InventoryHolder getOwner() {
            return (Lectern) LecternBlockEntity.this.getOwner();
        }
        // CraftBukkit end

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return LecternBlockEntity.this.book.isEmpty();
        }

        @Override
        public ItemStack getItem(int i) {
            return i == 0 ? LecternBlockEntity.this.book : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int j) {
            if (i == 0) {
                ItemStack itemstack = LecternBlockEntity.this.book.split(j);

                if (LecternBlockEntity.this.book.isEmpty()) {
                    LecternBlockEntity.this.onBookItemRemove();
                }

                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            if (i == 0) {
                ItemStack itemstack = LecternBlockEntity.this.book;

                LecternBlockEntity.this.book = ItemStack.EMPTY;
                LecternBlockEntity.this.onBookItemRemove();
                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        // CraftBukkit start
        public void setItem(int i, ItemStack itemstack) {
            if (i == 0) {
                LecternBlockEntity.this.setBook(itemstack);
                if (LecternBlockEntity.this.getLevel() != null) {
                    LecternBlock.setHasBook(LecternBlockEntity.this.getLevel(), LecternBlockEntity.this.getBlockPos(), LecternBlockEntity.this.getBlock(), LecternBlockEntity.this.hasBook());
                }
            }
        }
        // CraftBukkit end

        @Override
        public int getMaxStackSize() {
            return maxStack; // CraftBukkit
        }

        @Override
        public void setChanged() {
            LecternBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player entityhuman) {
            return LecternBlockEntity.this.level.getBlockEntity(LecternBlockEntity.this.worldPosition) != LecternBlockEntity.this ? false : (entityhuman.distanceToSqr((double) LecternBlockEntity.this.worldPosition.getX() + 0.5D, (double) LecternBlockEntity.this.worldPosition.getY() + 0.5D, (double) LecternBlockEntity.this.worldPosition.getZ() + 0.5D) > 64.0D ? false : LecternBlockEntity.this.hasBook());
        }

        @Override
        public boolean canPlaceItem(int i, ItemStack itemstack) {
            return false;
        }

        @Override
        public void clearContent() {}
    };
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int i) {
            return i == 0 ? LecternBlockEntity.this.page : 0;
        }

        @Override
        public void set(int i, int j) {
            if (i == 0) {
                LecternBlockEntity.this.setPage(j);
            }

        }

        @Override
        public int getCount() {
            return 1;
        }
    };
    private ItemStack book;
    private int page;
    private int pageCount;

    public LecternBlockEntity() {
        super(BlockEntityType.LECTERN);
        this.book = ItemStack.EMPTY;
    }

    public ItemStack getBook() {
        return this.book;
    }

    public boolean hasBook() {
        Item item = this.book.getItem();

        return item == Items.WRITABLE_BOOK || item == Items.WRITTEN_BOOK;
    }

    public void setBook(ItemStack itemstack) {
        this.setBook(itemstack, (Player) null);
    }

    private void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        LecternBlock.setHasBook(this.getLevel(), this.getBlockPos(), this.getBlock(), false);
    }

    public void setBook(ItemStack itemstack, @Nullable Player entityhuman) {
        this.book = this.resolveBook(itemstack, entityhuman);
        this.page = 0;
        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.setChanged();
    }

    public void setPage(int i) {
        int j = Mth.clamp(i, 0, this.pageCount - 1);

        if (j != this.page) {
            this.page = j;
            this.setChanged();
            if (this.level != null) LecternBlock.signalPageChange(this.getLevel(), this.getBlockPos(), this.getBlock()); // CraftBukkit
        }

    }

    public int getPage() {
        return this.page;
    }

    public int getRedstoneSignal() {
        float f = this.pageCount > 1 ? (float) this.getPage() / ((float) this.pageCount - 1.0F) : 1.0F;

        return Mth.floor(f * 14.0F) + (this.hasBook() ? 1 : 0);
    }

    private ItemStack resolveBook(ItemStack itemstack, @Nullable Player entityhuman) {
        if (this.level instanceof ServerLevel && itemstack.getItem() == Items.WRITTEN_BOOK) {
            WrittenBookItem.resolveBookComponents(itemstack, this.createCommandSourceStack(entityhuman), entityhuman);
        }

        return itemstack;
    }

    // CraftBukkit start
    @Override
    public void sendMessage(Component ichatbasecomponent, UUID uuid) {
    }

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitSender(wrapper) : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, this);
    }

    @Override
    public boolean acceptsSuccess() {
        return false;
    }

    @Override
    public boolean acceptsFailure() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    // CraftBukkit end
    private CommandSourceStack createCommandSourceStack(@Nullable Player entityhuman) {
        String s;
        Object object;

        if (entityhuman == null) {
            s = "Lectern";
            object = new TextComponent("Lectern");
        } else {
            s = entityhuman.getName().getString();
            object = entityhuman.getDisplayName();
        }

        Vec3 vec3d = Vec3.atCenterOf((Vec3i) this.worldPosition);

        // CraftBukkit - this
        return new CommandSourceStack(this, vec3d, Vec2.ZERO, (ServerLevel) this.level, 2, s, (Component) object, this.level.getServer(), entityhuman);
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        if (nbttagcompound.contains("Book", 10)) {
            this.book = this.resolveBook(ItemStack.of(nbttagcompound.getCompound("Book")), (Player) null);
        } else {
            this.book = ItemStack.EMPTY;
        }

        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.page = Mth.clamp(nbttagcompound.getInt("Page"), 0, this.pageCount - 1);
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (!this.getBook().isEmpty()) {
            nbttagcompound.put("Book", this.getBook().save(new CompoundTag()));
            nbttagcompound.putInt("Page", this.page);
        }

        return nbttagcompound;
    }

    @Override
    public void clearContent() {
        this.setBook(ItemStack.EMPTY);
    }

    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerinventory, Player entityhuman) {
        return new LecternMenu(i, this.bookAccess, this.dataAccess, playerinventory); // CraftBukkit
    }

    @Override
    public Component getDisplayName() {
        return new TranslatableComponent("container.lectern");
    }
}
