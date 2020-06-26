package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SpawnEggItem extends Item {

    private static final Map<EntityType<?>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();
    private final int color1;
    private final int color2;
    private final EntityType<?> defaultType;

    public SpawnEggItem(EntityType<?> entitytypes, int i, int j, Item.Info item_info) {
        super(item_info);
        this.defaultType = entitytypes;
        this.color1 = i;
        this.color2 = j;
        SpawnEggItem.BY_ID.put(entitytypes, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Level world = itemactioncontext.getLevel();

        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemstack = itemactioncontext.getItemInHand();
            BlockPos blockposition = itemactioncontext.getClickedPos();
            Direction enumdirection = itemactioncontext.getClickedFace();
            BlockState iblockdata = world.getType(blockposition);

            if (iblockdata.is(Blocks.SPAWNER)) {
                BlockEntity tileentity = world.getBlockEntity(blockposition);

                if (tileentity instanceof SpawnerBlockEntity) {
                    BaseSpawner mobspawnerabstract = ((SpawnerBlockEntity) tileentity).getSpawner();
                    EntityType<?> entitytypes = this.getType(itemstack.getTag());

                    mobspawnerabstract.setEntityId(entitytypes);
                    tileentity.setChanged();
                    world.notify(blockposition, iblockdata, iblockdata, 3);
                    itemstack.shrink(1);
                    return InteractionResult.CONSUME;
                }
            }

            BlockPos blockposition1;

            if (iblockdata.getCollisionShape(world, blockposition).isEmpty()) {
                blockposition1 = blockposition;
            } else {
                blockposition1 = blockposition.relative(enumdirection);
            }

            EntityType<?> entitytypes1 = this.getType(itemstack.getTag());

            if (entitytypes1.spawn(world, itemstack, itemactioncontext.getPlayer(), blockposition1, MobSpawnType.SPAWN_EGG, true, !Objects.equals(blockposition, blockposition1) && enumdirection == Direction.UP) != null) {
                itemstack.shrink(1);
            }

            return InteractionResult.CONSUME;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        BlockHitResult movingobjectpositionblock = getPlayerPOVHitResult(world, entityhuman, ClipContext.Fluid.SOURCE_ONLY);

        if (movingobjectpositionblock.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        } else if (world.isClientSide) {
            return InteractionResultHolder.success(itemstack);
        } else {
            BlockHitResult movingobjectpositionblock1 = (BlockHitResult) movingobjectpositionblock;
            BlockPos blockposition = movingobjectpositionblock1.getBlockPos();

            if (!(world.getType(blockposition).getBlock() instanceof LiquidBlock)) {
                return InteractionResultHolder.pass(itemstack);
            } else if (world.mayInteract(entityhuman, blockposition) && entityhuman.mayUseItemAt(blockposition, movingobjectpositionblock1.getDirection(), itemstack)) {
                EntityType<?> entitytypes = this.getType(itemstack.getTag());

                if (entitytypes.spawn(world, itemstack, entityhuman, blockposition, MobSpawnType.SPAWN_EGG, false, false) == null) {
                    return InteractionResultHolder.pass(itemstack);
                } else {
                    if (!entityhuman.abilities.instabuild) {
                        itemstack.shrink(1);
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(this));
                    return InteractionResultHolder.consume(itemstack);
                }
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        }
    }

    public boolean spawnsEntity(@Nullable CompoundTag nbttagcompound, EntityType<?> entitytypes) {
        return Objects.equals(this.getType(nbttagcompound), entitytypes);
    }

    public static Iterable<SpawnEggItem> eggs() {
        return Iterables.unmodifiableIterable(SpawnEggItem.BY_ID.values());
    }

    public EntityType<?> getType(@Nullable CompoundTag nbttagcompound) {
        if (nbttagcompound != null && nbttagcompound.contains("EntityTag", 10)) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("EntityTag");

            if (nbttagcompound1.contains("id", 8)) {
                return (EntityType) EntityType.byString(nbttagcompound1.getString("id")).orElse(this.defaultType);
            }
        }

        return this.defaultType;
    }

    public Optional<Mob> spawnOffspringFromSpawnEgg(Player entityhuman, Mob entityinsentient, EntityType<? extends Mob> entitytypes, Level world, Vec3 vec3d, ItemStack itemstack) {
        if (!this.spawnsEntity(itemstack.getTag(), entitytypes)) {
            return Optional.empty();
        } else {
            Object object;

            if (entityinsentient instanceof AgableMob) {
                object = ((AgableMob) entityinsentient).getBreedOffspring((AgableMob) entityinsentient);
            } else {
                object = (Mob) entitytypes.create(world);
            }

            if (object == null) {
                return Optional.empty();
            } else {
                ((Mob) object).setBaby(true);
                if (!((Mob) object).isBaby()) {
                    return Optional.empty();
                } else {
                    ((Mob) object).moveTo(vec3d.x(), vec3d.y(), vec3d.z(), 0.0F, 0.0F);
                    world.addEntity((Entity) object, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG); // CraftBukkit
                    if (itemstack.hasCustomHoverName()) {
                        ((Mob) object).setCustomName(itemstack.getHoverName());
                    }

                    if (!entityhuman.abilities.instabuild) {
                        itemstack.shrink(1);
                    }

                    return Optional.of((Mob) object); // CraftBukkit - decompile error
                }
            }
        }
    }
}
