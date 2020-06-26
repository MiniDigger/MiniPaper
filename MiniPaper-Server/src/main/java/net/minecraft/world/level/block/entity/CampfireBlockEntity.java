package net.minecraft.world.level.block.entity;

import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockCookEvent;
// CraftBukkit end

public class CampfireBlockEntity extends BlockEntity implements Clearable, TickableBlockEntity {

    private final NonNullList<ItemStack> items;
    public final int[] cookingProgress;
    public final int[] cookingTime;

    public CampfireBlockEntity() {
        super(BlockEntityType.CAMPFIRE);
        this.items = NonNullList.withSize(4, ItemStack.EMPTY);
        this.cookingProgress = new int[4];
        this.cookingTime = new int[4];
    }

    @Override
    public void tick() {
        boolean flag = (Boolean) this.getBlock().getValue(CampfireBlock.LIT);
        boolean flag1 = this.level.isClientSide;

        if (flag1) {
            if (flag) {
                this.makeParticles();
            }

        } else {
            if (flag) {
                this.cook();
            } else {
                for (int i = 0; i < this.items.size(); ++i) {
                    if (this.cookingProgress[i] > 0) {
                        this.cookingProgress[i] = Mth.clamp(this.cookingProgress[i] - 2, 0, this.cookingTime[i]);
                    }
                }
            }

        }
    }

    private void cook() {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = (ItemStack) this.items.get(i);

            if (!itemstack.isEmpty()) {
                int j = this.cookingProgress[i]++;

                if (this.cookingProgress[i] >= this.cookingTime[i]) {
                    SimpleContainer inventorysubcontainer = new SimpleContainer(new ItemStack[]{itemstack});
                    ItemStack itemstack1 = (ItemStack) this.level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, inventorysubcontainer, this.level).map((recipecampfire) -> {
                        return recipecampfire.assemble(inventorysubcontainer);
                    }).orElse(itemstack);
                    BlockPos blockposition = this.getBlockPos();

                    // CraftBukkit start - fire BlockCookEvent
                    CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
                    org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);

                    BlockCookEvent blockCookEvent = new BlockCookEvent(CraftBlock.at(this.level, this.worldPosition), source, result);
                    this.level.getServerOH().getPluginManager().callEvent(blockCookEvent);

                    if (blockCookEvent.isCancelled()) {
                        return;
                    }

                    result = blockCookEvent.getResult();
                    itemstack1 = CraftItemStack.asNMSCopy(result);
                    // CraftBukkit end
                    Containers.dropItemStack(this.level, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), itemstack1);
                    this.items.set(i, ItemStack.EMPTY);
                    this.markUpdated();
                }
            }
        }

    }

    private void makeParticles() {
        Level world = this.getLevel();

        if (world != null) {
            BlockPos blockposition = this.getBlockPos();
            Random random = world.random;
            int i;

            if (random.nextFloat() < 0.11F) {
                for (i = 0; i < random.nextInt(2) + 2; ++i) {
                    CampfireBlock.makeParticles(world, blockposition, (Boolean) this.getBlock().getValue(CampfireBlock.SIGNAL_FIRE), false);
                }
            }

            i = ((Direction) this.getBlock().getValue(CampfireBlock.FACING)).get2DDataValue();

            for (int j = 0; j < this.items.size(); ++j) {
                if (!((ItemStack) this.items.get(j)).isEmpty() && random.nextFloat() < 0.2F) {
                    Direction enumdirection = Direction.from2DDataValue(Math.floorMod(j + i, 4));
                    float f = 0.3125F;
                    double d0 = (double) blockposition.getX() + 0.5D - (double) ((float) enumdirection.getStepX() * 0.3125F) + (double) ((float) enumdirection.getClockWise().getStepX() * 0.3125F);
                    double d1 = (double) blockposition.getY() + 0.5D;
                    double d2 = (double) blockposition.getZ() + 0.5D - (double) ((float) enumdirection.getStepZ() * 0.3125F) + (double) ((float) enumdirection.getClockWise().getStepZ() * 0.3125F);

                    for (int k = 0; k < 4; ++k) {
                        world.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 5.0E-4D, 0.0D);
                    }
                }
            }

        }
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items.clear();
        ContainerHelper.loadAllItems(nbttagcompound, this.items);
        int[] aint;

        if (nbttagcompound.contains("CookingTimes", 11)) {
            aint = nbttagcompound.getIntArray("CookingTimes");
            System.arraycopy(aint, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, aint.length));
        }

        if (nbttagcompound.contains("CookingTotalTimes", 11)) {
            aint = nbttagcompound.getIntArray("CookingTotalTimes");
            System.arraycopy(aint, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, aint.length));
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        this.saveMetadataAndItems(nbttagcompound);
        nbttagcompound.putIntArray("CookingTimes", this.cookingProgress);
        nbttagcompound.putIntArray("CookingTotalTimes", this.cookingTime);
        return nbttagcompound;
    }

    private CompoundTag saveMetadataAndItems(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        ContainerHelper.saveAllItems(nbttagcompound, this.items, true);
        return nbttagcompound;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 13, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveMetadataAndItems(new CompoundTag());
    }

    public Optional<CampfireCookingRecipe> getCookableRecipe(ItemStack itemstack) {
        return this.items.stream().noneMatch(ItemStack::isEmpty) ? Optional.empty() : this.level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SimpleContainer(new ItemStack[]{itemstack}), this.level);
    }

    public boolean placeFood(ItemStack itemstack, int i) {
        for (int j = 0; j < this.items.size(); ++j) {
            ItemStack itemstack1 = (ItemStack) this.items.get(j);

            if (itemstack1.isEmpty()) {
                this.cookingTime[j] = i;
                this.cookingProgress[j] = 0;
                this.items.set(j, itemstack.split(1));
                this.markUpdated();
                return true;
            }
        }

        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().notify(this.getBlockPos(), this.getBlock(), this.getBlock(), 3);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    public void dowse() {
        if (this.level != null) {
            if (!this.level.isClientSide) {
                Containers.dropContents(this.level, this.getBlockPos(), this.getItems());
            }

            this.markUpdated();
        }

    }
}
