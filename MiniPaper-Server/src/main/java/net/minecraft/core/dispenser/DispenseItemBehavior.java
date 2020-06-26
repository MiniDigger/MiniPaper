package net.minecraft.core.dispenser;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public interface DispenseItemBehavior {

    DispenseItemBehavior NOOP = (isourceblock, itemstack) -> {
        return itemstack;
    };

    ItemStack dispense(BlockSource isourceblock, ItemStack itemstack);

    static void bootStrap() {
        DispenserBlock.registerBehavior((ItemLike) Items.ARROW, (DispenseItemBehavior) (new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack) {
                Arrow entitytippedarrow = new Arrow(world, iposition.x(), iposition.y(), iposition.z());

                entitytippedarrow.pickup = AbstractArrow.Pickup.ALLOWED;
                return entitytippedarrow;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.TIPPED_ARROW, (DispenseItemBehavior) (new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack) {
                Arrow entitytippedarrow = new Arrow(world, iposition.x(), iposition.y(), iposition.z());

                entitytippedarrow.setEffectsFromItem(itemstack);
                entitytippedarrow.pickup = AbstractArrow.Pickup.ALLOWED;
                return entitytippedarrow;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.SPECTRAL_ARROW, (DispenseItemBehavior) (new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack) {
                SpectralArrow entityspectralarrow = new SpectralArrow(world, iposition.x(), iposition.y(), iposition.z());

                entityspectralarrow.pickup = AbstractArrow.Pickup.ALLOWED;
                return entityspectralarrow;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.EGG, (DispenseItemBehavior) (new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack) {
                return (Projectile) Util.make((new ThrownEgg(world, iposition.x(), iposition.y(), iposition.z())), (entityegg) -> { // CraftBukkit - decompile error
                    entityegg.setItem(itemstack);
                });
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.SNOWBALL, (DispenseItemBehavior) (new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack) {
                return (Projectile) Util.make((new Snowball(world, iposition.x(), iposition.y(), iposition.z())), (entitysnowball) -> { // CraftBukkit - decompile error
                    entitysnowball.setItem(itemstack);
                });
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.EXPERIENCE_BOTTLE, (DispenseItemBehavior) (new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack) {
                return (Projectile) Util.make((new ThrownExperienceBottle(world, iposition.x(), iposition.y(), iposition.z())), (entitythrownexpbottle) -> { // CraftBukkit - decompile error
                    entitythrownexpbottle.setItem(itemstack);
                });
            }

            @Override
            protected float getUncertainty() {
                return super.getUncertainty() * 0.5F;
            }

            @Override
            protected float getPower() {
                return super.getPower() * 1.25F;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.SPLASH_POTION, new DispenseItemBehavior() {
            @Override
            public ItemStack dispense(BlockSource isourceblock, ItemStack itemstack) {
                return (new AbstractProjectileDispenseBehavior() {
                    @Override
                    protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack1) {
                        return (Projectile) Util.make((new ThrownPotion(world, iposition.x(), iposition.y(), iposition.z())), (entitypotion) -> { // CraftBukkit - decompile error
                            entitypotion.setItem(itemstack1);
                        });
                    }

                    @Override
                    protected float getUncertainty() {
                        return super.getUncertainty() * 0.5F;
                    }

                    @Override
                    protected float getPower() {
                        return super.getPower() * 1.25F;
                    }
                }).dispense(isourceblock, itemstack);
            }
        });
        DispenserBlock.registerBehavior((ItemLike) Items.LINGERING_POTION, new DispenseItemBehavior() {
            @Override
            public ItemStack dispense(BlockSource isourceblock, ItemStack itemstack) {
                return (new AbstractProjectileDispenseBehavior() {
                    @Override
                    protected Projectile getProjectile(Level world, Position iposition, ItemStack itemstack1) {
                        return (Projectile) Util.make((new ThrownPotion(world, iposition.x(), iposition.y(), iposition.z())), (entitypotion) -> { // CraftBukkit - decompile error
                            entitypotion.setItem(itemstack1);
                        });
                    }

                    @Override
                    protected float getUncertainty() {
                        return super.getUncertainty() * 0.5F;
                    }

                    @Override
                    protected float getPower() {
                        return super.getPower() * 1.25F;
                    }
                }).dispense(isourceblock, itemstack);
            }
        });
        DefaultDispenseItemBehavior dispensebehavioritem = new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
                EntityType<?> entitytypes = ((SpawnEggItem) itemstack.getItem()).getType(itemstack.getTag());

                // CraftBukkit start
                Level world = isourceblock.getLevel();
                ItemStack itemstack1 = itemstack.split(1);
                org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    itemstack.grow(1);
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    itemstack.grow(1);
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }

                entitytypes.spawn(isourceblock.getLevel(), itemstack, (Player) null, isourceblock.getPos().relative(enumdirection), MobSpawnType.DISPENSER, enumdirection != Direction.UP, false);
                // itemstack.subtract(1); // Handled during event processing
                // CraftBukkit end
                return itemstack;
            }
        };
        Iterator iterator = SpawnEggItem.eggs().iterator();

        while (iterator.hasNext()) {
            SpawnEggItem itemmonsteregg = (SpawnEggItem) iterator.next();

            DispenserBlock.registerBehavior((ItemLike) itemmonsteregg, (DispenseItemBehavior) dispensebehavioritem);
        }

        DispenserBlock.registerBehavior((ItemLike) Items.ARMOR_STAND, (DispenseItemBehavior) (new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
                BlockPos blockposition = isourceblock.getPos().relative(enumdirection);
                Level world = isourceblock.getLevel();

                // CraftBukkit start
                ItemStack itemstack1 = itemstack.split(1);
                org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    itemstack.grow(1);
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    itemstack.grow(1);
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }
                // CraftBukkit end

                ArmorStand entityarmorstand = new ArmorStand(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D);

                EntityType.updateCustomEntityTag(world, (Player) null, (Entity) entityarmorstand, itemstack.getTag());
                entityarmorstand.yRot = enumdirection.toYRot();
                world.addFreshEntity(entityarmorstand);
                // itemstack.subtract(1); // CraftBukkit - Handled during event processing
                return itemstack;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.SADDLE, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                List<LivingEntity> list = isourceblock.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(blockposition), (entityliving) -> {
                    if (!(entityliving instanceof Saddleable)) {
                        return false;
                    } else {
                        Saddleable isaddleable = (Saddleable) entityliving;

                        return !isaddleable.isSaddled() && isaddleable.isSaddleable();
                    }
                });

                if (!list.isEmpty()) {
                    ((Saddleable) list.get(0)).equipSaddle(SoundSource.BLOCKS);
                    itemstack.shrink(1);
                    this.setSuccess(true);
                    return itemstack;
                } else {
                    return super.execute(isourceblock, itemstack);
                }
            }
        }));
        OptionalDispenseItemBehavior dispensebehaviormaybe = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                List<AbstractHorse> list = isourceblock.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(blockposition), (entityhorseabstract) -> {
                    return entityhorseabstract.isAlive() && entityhorseabstract.canWearArmor();
                });
                Iterator iterator1 = list.iterator();

                AbstractHorse entityhorseabstract;

                do {
                    if (!iterator1.hasNext()) {
                        return super.execute(isourceblock, itemstack);
                    }

                    entityhorseabstract = (AbstractHorse) iterator1.next();
                } while (!entityhorseabstract.isArmor(itemstack) || entityhorseabstract.isWearingArmor() || !entityhorseabstract.isTamed());

                entityhorseabstract.setSlot(401, itemstack.split(1));
                this.setSuccess(true);
                return itemstack;
            }
        };

        DispenserBlock.registerBehavior((ItemLike) Items.LEATHER_HORSE_ARMOR, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.IRON_HORSE_ARMOR, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.GOLDEN_HORSE_ARMOR, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.DIAMOND_HORSE_ARMOR, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.WHITE_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.ORANGE_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.CYAN_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.BLUE_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.BROWN_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.BLACK_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.GRAY_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.GREEN_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.LIGHT_BLUE_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.LIGHT_GRAY_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.LIME_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.MAGENTA_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.PINK_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.PURPLE_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.RED_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.YELLOW_CARPET, (DispenseItemBehavior) dispensebehaviormaybe);
        DispenserBlock.registerBehavior((ItemLike) Items.CHEST, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                List<AbstractChestedHorse> list = isourceblock.getLevel().getEntitiesOfClass(AbstractChestedHorse.class, new AABB(blockposition), (entityhorsechestedabstract) -> {
                    return entityhorsechestedabstract.isAlive() && !entityhorsechestedabstract.hasChest();
                });
                Iterator iterator1 = list.iterator();

                AbstractChestedHorse entityhorsechestedabstract;

                do {
                    if (!iterator1.hasNext()) {
                        return super.execute(isourceblock, itemstack);
                    }

                    entityhorsechestedabstract = (AbstractChestedHorse) iterator1.next();
                } while (!entityhorsechestedabstract.isTamed() || !entityhorsechestedabstract.setSlot(499, itemstack));

                itemstack.shrink(1);
                this.setSuccess(true);
                return itemstack;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.FIREWORK_ROCKET, (DispenseItemBehavior) (new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
                // CraftBukkit start
                Level world = isourceblock.getLevel();
                ItemStack itemstack1 = itemstack.split(1);
                org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(enumdirection.getStepX(), enumdirection.getStepY(), enumdirection.getStepZ()));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    itemstack.grow(1);
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    itemstack.grow(1);
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }

                itemstack1 = CraftItemStack.asNMSCopy(event.getItem());
                FireworkRocketEntity entityfireworks = new FireworkRocketEntity(isourceblock.getLevel(), itemstack, isourceblock.x(), isourceblock.y(), isourceblock.x(), true);

                DispenseItemBehavior.setEntityPokingOutOfBlock(isourceblock, entityfireworks, enumdirection);
                entityfireworks.shoot((double) enumdirection.getStepX(), (double) enumdirection.getStepY(), (double) enumdirection.getStepZ(), 0.5F, 1.0F);
                isourceblock.getLevel().addFreshEntity(entityfireworks);
                // itemstack.subtract(1); // Handled during event processing
                // CraftBukkit end
                return itemstack;
            }

            @Override
            protected void playSound(BlockSource isourceblock) {
                isourceblock.getLevel().levelEvent(1004, isourceblock.getPos(), 0);
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.FIRE_CHARGE, (DispenseItemBehavior) (new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
                Position iposition = DispenserBlock.getDispensePosition(isourceblock);
                double d0 = iposition.x() + (double) ((float) enumdirection.getStepX() * 0.3F);
                double d1 = iposition.y() + (double) ((float) enumdirection.getStepY() * 0.3F);
                double d2 = iposition.z() + (double) ((float) enumdirection.getStepZ() * 0.3F);
                Level world = isourceblock.getLevel();
                Random random = world.random;
                double d3 = random.nextGaussian() * 0.05D + (double) enumdirection.getStepX();
                double d4 = random.nextGaussian() * 0.05D + (double) enumdirection.getStepY();
                double d5 = random.nextGaussian() * 0.05D + (double) enumdirection.getStepZ();

                // CraftBukkit start
                ItemStack itemstack1 = itemstack.split(1);
                org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(d3, d4, d5));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    itemstack.grow(1);
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    itemstack.grow(1);
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }

                SmallFireball entitysmallfireball = new SmallFireball(world, d0, d1, d2, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ());
                entitysmallfireball.setItem(itemstack1);
                entitysmallfireball.projectileSource = new org.bukkit.craftbukkit.projectiles.CraftBlockProjectileSource((DispenserBlockEntity) isourceblock.getEntity());

                world.addFreshEntity(entitysmallfireball);
                // itemstack.subtract(1); // Handled during event processing
                // CraftBukkit end
                return itemstack;
            }

            @Override
            protected void playSound(BlockSource isourceblock) {
                isourceblock.getLevel().levelEvent(1018, isourceblock.getPos(), 0);
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.OAK_BOAT, (DispenseItemBehavior) (new BoatDispenseItemBehavior(Boat.Type.OAK)));
        DispenserBlock.registerBehavior((ItemLike) Items.SPRUCE_BOAT, (DispenseItemBehavior) (new BoatDispenseItemBehavior(Boat.Type.SPRUCE)));
        DispenserBlock.registerBehavior((ItemLike) Items.BIRCH_BOAT, (DispenseItemBehavior) (new BoatDispenseItemBehavior(Boat.Type.BIRCH)));
        DispenserBlock.registerBehavior((ItemLike) Items.JUNGLE_BOAT, (DispenseItemBehavior) (new BoatDispenseItemBehavior(Boat.Type.JUNGLE)));
        DispenserBlock.registerBehavior((ItemLike) Items.DARK_OAK_BOAT, (DispenseItemBehavior) (new BoatDispenseItemBehavior(Boat.Type.DARK_OAK)));
        DispenserBlock.registerBehavior((ItemLike) Items.ACACIA_BOAT, (DispenseItemBehavior) (new BoatDispenseItemBehavior(Boat.Type.ACACIA)));
        DefaultDispenseItemBehavior dispensebehavioritem1 = new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior b = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                BucketItem itembucket = (BucketItem) itemstack.getItem();
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                Level world = isourceblock.getLevel();

                // CraftBukkit start
                int x = blockposition.getX();
                int y = blockposition.getY();
                int z = blockposition.getZ();
                BlockState iblockdata = world.getType(blockposition);
                Material material = iblockdata.getMaterial();
                if (world.isEmptyBlock(blockposition) || !material.isSolid() || material.isReplaceable() || ((iblockdata.getBlock() instanceof LiquidBlockContainer) && ((LiquidBlockContainer) iblockdata.getBlock()).canPlace(world, blockposition, iblockdata, itembucket.content))) {
                    org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                    CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                    BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(x, y, z));
                    if (!DispenserBlock.eventFired) {
                        world.getServerOH().getPluginManager().callEvent(event);
                    }

                    if (event.isCancelled()) {
                        return itemstack;
                    }

                    if (!event.getItem().equals(craftItem)) {
                        // Chain to handler for new item
                        ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                        if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                            idispensebehavior.dispense(isourceblock, eventStack);
                            return itemstack;
                        }
                    }

                    itembucket = (BucketItem) CraftItemStack.asNMSCopy(event.getItem()).getItem();
                }
                // CraftBukkit end

                if (itembucket.emptyBucket((Player) null, world, blockposition, (BlockHitResult) null)) {
                    itembucket.checkExtraContent(world, itemstack, blockposition);
                    // CraftBukkit start - Handle stacked buckets
                    Item item = Items.BUCKET;
                    itemstack.shrink(1);
                    if (itemstack.isEmpty()) {
                        itemstack.setItem(Items.BUCKET);
                        itemstack.setCount(1);
                    } else if (((DispenserBlockEntity) isourceblock.getEntity()).addItem(new ItemStack(item)) < 0) {
                        this.b.dispense(isourceblock, new ItemStack(item));
                    }
                    // CraftBukkit end
                    return itemstack;
                } else {
                    return this.b.dispense(isourceblock, itemstack);
                }
            }
        };

        DispenserBlock.registerBehavior((ItemLike) Items.LAVA_BUCKET, (DispenseItemBehavior) dispensebehavioritem1);
        DispenserBlock.registerBehavior((ItemLike) Items.WATER_BUCKET, (DispenseItemBehavior) dispensebehavioritem1);
        DispenserBlock.registerBehavior((ItemLike) Items.SALMON_BUCKET, (DispenseItemBehavior) dispensebehavioritem1);
        DispenserBlock.registerBehavior((ItemLike) Items.COD_BUCKET, (DispenseItemBehavior) dispensebehavioritem1);
        DispenserBlock.registerBehavior((ItemLike) Items.PUFFERFISH_BUCKET, (DispenseItemBehavior) dispensebehavioritem1);
        DispenserBlock.registerBehavior((ItemLike) Items.TROPICAL_FISH_BUCKET, (DispenseItemBehavior) dispensebehavioritem1);
        DispenserBlock.registerBehavior((ItemLike) Items.BUCKET, (DispenseItemBehavior) (new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Level world = isourceblock.getLevel();
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                BlockState iblockdata = world.getType(blockposition);
                Block block = iblockdata.getBlock();

                if (block instanceof BucketPickup) {
                    Fluid fluidtype = ((BucketPickup) block).removeFluid(DummyGeneratorAccess.INSTANCE, blockposition, iblockdata); // CraftBukkit

                    if (!(fluidtype instanceof FlowingFluid)) {
                        return super.execute(isourceblock, itemstack);
                    } else {
                        Item item = fluidtype.getBucket();

                        // CraftBukkit start
                        org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                        BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                        if (!DispenserBlock.eventFired) {
                            world.getServerOH().getPluginManager().callEvent(event);
                        }

                        if (event.isCancelled()) {
                            return itemstack;
                        }

                        if (!event.getItem().equals(craftItem)) {
                            // Chain to handler for new item
                            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                                idispensebehavior.dispense(isourceblock, eventStack);
                                return itemstack;
                            }
                        }

                        fluidtype = ((BucketPickup) block).removeFluid(world, blockposition, iblockdata); // From above
                        // CraftBukkit end

                        itemstack.shrink(1);
                        if (itemstack.isEmpty()) {
                            return new ItemStack(item);
                        } else {
                            if (((DispenserBlockEntity) isourceblock.getEntity()).addItem(new ItemStack(item)) < 0) {
                                this.defaultDispenseItemBehavior.dispense(isourceblock, new ItemStack(item));
                            }

                            return itemstack;
                        }
                    }
                } else {
                    return super.execute(isourceblock, itemstack);
                }
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.FLINT_AND_STEEL, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Level world = isourceblock.getLevel();

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }
                // CraftBukkit end

                this.setSuccess(true);
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                BlockState iblockdata = world.getType(blockposition);

                if (BaseFireBlock.canBePlacedAt((LevelAccessor) world, blockposition)) {
                    // CraftBukkit start - Ignition by dispensing flint and steel
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, isourceblock.getPos()).isCancelled()) {
                        world.setTypeUpdate(blockposition, BaseFireBlock.getState((BlockGetter) world, blockposition));
                    }
                    // CraftBukkit end
                } else if (CampfireBlock.canLight(iblockdata)) {
                    world.setTypeUpdate(blockposition, (BlockState) iblockdata.setValue(BlockStateProperties.LIT, true));
                } else if (iblockdata.getBlock() instanceof TntBlock) {
                    TntBlock.explode(world, blockposition);
                    world.removeBlock(blockposition, false);
                } else {
                    this.setSuccess(false);
                }

                if (this.isSuccess() && itemstack.hurt(1, world.random, (ServerPlayer) null)) {
                    itemstack.setCount(0);
                }

                return itemstack;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.BONE_MEAL, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                this.setSuccess(true);
                Level world = isourceblock.getLevel();
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                // CraftBukkit start
                org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }

                world.captureTreeGeneration = true;
                // CraftBukkit end

                if (!BoneMealItem.growCrop(itemstack, world, blockposition) && !BoneMealItem.growWaterPlant(itemstack, world, blockposition, (Direction) null)) {
                    this.setSuccess(false);
                } else if (!world.isClientSide) {
                    world.levelEvent(2005, blockposition, 0);
                }
                // CraftBukkit start
                world.captureTreeGeneration = false;
                if (world.capturedBlockStates.size() > 0) {
                    TreeType treeType = SaplingBlock.treeType;
                    SaplingBlock.treeType = null;
                    Location location = new Location(world.getWorld(), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                    world.capturedBlockStates.clear();
                    StructureGrowEvent structureEvent = null;
                    if (treeType != null) {
                        structureEvent = new StructureGrowEvent(location, treeType, false, null, blocks);
                        org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                    }
                    if (structureEvent == null || !structureEvent.isCancelled()) {
                        for (org.bukkit.block.BlockState blockstate : blocks) {
                            blockstate.update(true);
                        }
                    }
                }
                // CraftBukkit end

                return itemstack;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Blocks.TNT, (DispenseItemBehavior) (new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Level world = isourceblock.getLevel();
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                // EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, (EntityLiving) null);

                // CraftBukkit start
                ItemStack itemstack1 = itemstack.split(1);
                org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D));
                if (!DispenserBlock.eventFired) {
                   world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    itemstack.grow(1);
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    itemstack.grow(1);
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }

                PrimedTnt entitytntprimed = new PrimedTnt(world, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), (LivingEntity) null);
                // CraftBukkit end

                world.addFreshEntity(entitytntprimed);
                world.playSound((Player) null, entitytntprimed.getX(), entitytntprimed.getY(), entitytntprimed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                // itemstack.subtract(1); // CraftBukkit - handled above
                return itemstack;
            }
        }));
        OptionalDispenseItemBehavior dispensebehaviormaybe1 = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                this.setSuccess(ArmorItem.dispenseArmor(isourceblock, itemstack));
                return itemstack;
            }
        };

        DispenserBlock.registerBehavior((ItemLike) Items.CREEPER_HEAD, (DispenseItemBehavior) dispensebehaviormaybe1);
        DispenserBlock.registerBehavior((ItemLike) Items.ZOMBIE_HEAD, (DispenseItemBehavior) dispensebehaviormaybe1);
        DispenserBlock.registerBehavior((ItemLike) Items.DRAGON_HEAD, (DispenseItemBehavior) dispensebehaviormaybe1);
        DispenserBlock.registerBehavior((ItemLike) Items.SKELETON_SKULL, (DispenseItemBehavior) dispensebehaviormaybe1);
        DispenserBlock.registerBehavior((ItemLike) Items.PLAYER_HEAD, (DispenseItemBehavior) dispensebehaviormaybe1);
        DispenserBlock.registerBehavior((ItemLike) Items.WITHER_SKELETON_SKULL, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Level world = isourceblock.getLevel();
                Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
                BlockPos blockposition = isourceblock.getPos().relative(enumdirection);

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }
                // CraftBukkit end

                if (world.isEmptyBlock(blockposition) && WitherSkullBlock.canSpawnMob(world, blockposition, itemstack)) {
                    world.setTypeAndData(blockposition, (BlockState) Blocks.WITHER_SKELETON_SKULL.getBlockData().setValue(SkullBlock.ROTATION, enumdirection.getAxis() == Direction.Axis.Y ? 0 : enumdirection.getOpposite().get2DDataValue() * 4), 3);
                    BlockEntity tileentity = world.getBlockEntity(blockposition);

                    if (tileentity instanceof SkullBlockEntity) {
                        WitherSkullBlock.checkSpawn(world, blockposition, (SkullBlockEntity) tileentity);
                    }

                    itemstack.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(ArmorItem.dispenseArmor(isourceblock, itemstack));
                }

                return itemstack;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Blocks.CARVED_PUMPKIN, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Level world = isourceblock.getLevel();
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                CarvedPumpkinBlock blockpumpkincarved = (CarvedPumpkinBlock) Blocks.CARVED_PUMPKIN;

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }
                // CraftBukkit end

                if (world.isEmptyBlock(blockposition) && blockpumpkincarved.canSpawnGolem((LevelReader) world, blockposition)) {
                    if (!world.isClientSide) {
                        world.setTypeAndData(blockposition, blockpumpkincarved.getBlockData(), 3);
                    }

                    itemstack.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(ArmorItem.dispenseArmor(isourceblock, itemstack));
                }

                return itemstack;
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Blocks.SHULKER_BOX.asItem(), (DispenseItemBehavior) (new ShulkerBoxDispenseBehavior()));
        DyeColor[] aenumcolor = DyeColor.values();
        int i = aenumcolor.length;

        for (int j = 0; j < i; ++j) {
            DyeColor enumcolor = aenumcolor[j];

            DispenserBlock.registerBehavior((ItemLike) ShulkerBoxBlock.getBlockByColor(enumcolor).asItem(), (DispenseItemBehavior) (new ShulkerBoxDispenseBehavior()));
        }

        DispenserBlock.registerBehavior((ItemLike) Items.GLASS_BOTTLE.asItem(), (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior b = new DefaultDispenseItemBehavior();

            private ItemStack a(BlockSource isourceblock, ItemStack itemstack, ItemStack itemstack1) {
                itemstack.shrink(1);
                if (itemstack.isEmpty()) {
                    return itemstack1.copy();
                } else {
                    if (((DispenserBlockEntity) isourceblock.getEntity()).addItem(itemstack1.copy()) < 0) {
                        this.b.dispense(isourceblock, itemstack1.copy());
                    }

                    return itemstack;
                }
            }

            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                this.setSuccess(false);
                Level world = isourceblock.getLevel();
                BlockPos blockposition = isourceblock.getPos().relative((Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
                BlockState iblockdata = world.getType(blockposition);

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                if (!DispenserBlock.eventFired) {
                    world.getServerOH().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return itemstack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(isourceblock, eventStack);
                        return itemstack;
                    }
                }
                // CraftBukkit end

                if (iblockdata.is((Tag) BlockTags.BEEHIVES, (blockbase_blockdata) -> {
                    return blockbase_blockdata.hasProperty(BeehiveBlock.HONEY_LEVEL);
                }) && (Integer) iblockdata.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                    ((BeehiveBlock) iblockdata.getBlock()).releaseBeesAndResetHoneyLevel(world.getLevel(), iblockdata, blockposition, (Player) null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                    this.setSuccess(true);
                    return this.a(isourceblock, itemstack, new ItemStack(Items.HONEY_BOTTLE));
                } else if (world.getFluidState(blockposition).is((Tag) FluidTags.WATER)) {
                    this.setSuccess(true);
                    return this.a(isourceblock, itemstack, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
                } else {
                    return super.execute(isourceblock, itemstack);
                }
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.GLOWSTONE, (DispenseItemBehavior) (new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
                Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
                BlockPos blockposition = isourceblock.getPos().relative(enumdirection);
                Level world = isourceblock.getLevel();
                BlockState iblockdata = world.getType(blockposition);

                this.setSuccess(true);
                if (iblockdata.is(Blocks.RESPAWN_ANCHOR)) {
                    if ((Integer) iblockdata.getValue(RespawnAnchorBlock.CHARGE) != 4) {
                        RespawnAnchorBlock.charge(world, blockposition, iblockdata);
                        itemstack.shrink(1);
                    } else {
                        this.setSuccess(false);
                    }

                    return itemstack;
                } else {
                    return super.execute(isourceblock, itemstack);
                }
            }
        }));
        DispenserBlock.registerBehavior((ItemLike) Items.SHEARS.asItem(), (DispenseItemBehavior) (new ShearsDispenseItemBehavior()));
    }

    static void setEntityPokingOutOfBlock(BlockSource isourceblock, Entity entity, Direction enumdirection) {
        entity.setPos(isourceblock.x() + (double) enumdirection.getStepX() * (0.5000099999997474D - (double) entity.getBbWidth() / 2.0D), isourceblock.y() + (double) enumdirection.getStepY() * (0.5000099999997474D - (double) entity.getBbHeight() / 2.0D) - (double) entity.getBbHeight() / 2.0D, isourceblock.z() + (double) enumdirection.getStepZ() * (0.5000099999997474D - (double) entity.getBbWidth() / 2.0D));
    }
}
