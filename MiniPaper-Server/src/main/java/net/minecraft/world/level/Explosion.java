package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Location;
import org.bukkit.event.block.BlockExplodeEvent;
// CraftBukkit end

public class Explosion {

    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;
    private final Random random = new Random();
    private final Level level;
    private final double x;
    private final double y;
    private final double z;
    @Nullable
    public final Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final List<BlockPos> toBlow = Lists.newArrayList();
    private final Map<Player, Vec3> hitPlayers = Maps.newHashMap();
    public boolean wasCanceled = false; // CraftBukkit - add field

    public Explosion(Level world, @Nullable Entity entity, @Nullable DamageSource damagesource, @Nullable ExplosionDamageCalculator explosiondamagecalculator, double d0, double d1, double d2, float f, boolean flag, Explosion.BlockInteraction explosion_effect) {
        this.level = world;
        this.source = entity;
        this.radius = (float) Math.max(f, 0.0); // CraftBukkit - clamp bad values
        this.x = d0;
        this.y = d1;
        this.z = d2;
        this.fire = flag;
        this.blockInteraction = explosion_effect;
        this.damageSource = damagesource == null ? DamageSource.explosion(this) : damagesource;
        this.damageCalculator = explosiondamagecalculator == null ? this.makeDamageCalculator(entity) : explosiondamagecalculator;
    }

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return (ExplosionDamageCalculator) (entity == null ? DefaultExplosionDamageCalculator.INSTANCE : new EntityBasedExplosionDamageCalculator(entity));
    }

    public static float getSeenPercent(Vec3 vec3d, Entity entity) {
        AABB axisalignedbb = entity.getBoundingBox();
        double d0 = 1.0D / ((axisalignedbb.maxX - axisalignedbb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((axisalignedbb.maxY - axisalignedbb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((axisalignedbb.maxZ - axisalignedbb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D) {
            int i = 0;
            int j = 0;

            for (float f = 0.0F; f <= 1.0F; f = (float) ((double) f + d0)) {
                for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float) ((double) f1 + d1)) {
                    for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float) ((double) f2 + d2)) {
                        double d5 = Mth.lerp((double) f, axisalignedbb.minX, axisalignedbb.maxX);
                        double d6 = Mth.lerp((double) f1, axisalignedbb.minY, axisalignedbb.maxY);
                        double d7 = Mth.lerp((double) f2, axisalignedbb.minZ, axisalignedbb.maxZ);
                        Vec3 vec3d1 = new Vec3(d5 + d3, d6, d7 + d4);

                        if (entity.level.clip(new ClipContext(vec3d1, vec3d, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) {
                            ++i;
                        }

                        ++j;
                    }
                }
            }

            return (float) i / (float) j;
        } else {
            return 0.0F;
        }
    }

    public void explode() {
        // CraftBukkit start
        if (this.radius < 0.1F) {
            return;
        }
        // CraftBukkit end
        Set<BlockPos> set = Sets.newHashSet();
        boolean flag = true;

        int i;
        int j;

        for (int k = 0; k < 16; ++k) {
            for (i = 0; i < 16; ++i) {
                for (j = 0; j < 16; ++j) {
                    if (k == 0 || k == 15 || i == 0 || i == 15 || j == 0 || j == 15) {
                        double d0 = (double) ((float) k / 15.0F * 2.0F - 1.0F);
                        double d1 = (double) ((float) i / 15.0F * 2.0F - 1.0F);
                        double d2 = (double) ((float) j / 15.0F * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                        double d4 = this.x;
                        double d5 = this.y;
                        double d6 = this.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            BlockPos blockposition = new BlockPos(d4, d5, d6);
                            BlockState iblockdata = this.level.getType(blockposition);
                            FluidState fluid = this.level.getFluidState(blockposition);
                            Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, blockposition, iblockdata, fluid);

                            if (optional.isPresent()) {
                                f -= ((Float) optional.get() + 0.3F) * 0.3F;
                            }

                            if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockposition, iblockdata, f) && blockposition.getY() < 256 && blockposition.getY() >= 0) { // CraftBukkit - don't wrap explosions
                                set.add(blockposition);
                            }

                            d4 += d0 * 0.30000001192092896D;
                            d5 += d1 * 0.30000001192092896D;
                            d6 += d2 * 0.30000001192092896D;
                        }
                    }
                }
            }
        }

        this.toBlow.addAll(set);
        float f2 = this.radius * 2.0F;

        i = Mth.floor(this.x - (double) f2 - 1.0D);
        j = Mth.floor(this.x + (double) f2 + 1.0D);
        int l = Mth.floor(this.y - (double) f2 - 1.0D);
        int i1 = Mth.floor(this.y + (double) f2 + 1.0D);
        int j1 = Mth.floor(this.z - (double) f2 - 1.0D);
        int k1 = Mth.floor(this.z + (double) f2 + 1.0D);
        List<Entity> list = this.level.getEntities(this.source, new AABB((double) i, (double) l, (double) j1, (double) j, (double) i1, (double) k1));
        Vec3 vec3d = new Vec3(this.x, this.y, this.z);

        for (int l1 = 0; l1 < list.size(); ++l1) {
            Entity entity = (Entity) list.get(l1);

            if (!entity.ignoreExplosion()) {
                double d7 = (double) (Mth.sqrt(entity.distanceToSqr(vec3d)) / f2);

                if (d7 <= 1.0D) {
                    double d8 = entity.getX() - this.x;
                    double d9 = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                    double d10 = entity.getZ() - this.z;
                    double d11 = (double) Mth.sqrt(d8 * d8 + d9 * d9 + d10 * d10);

                    if (d11 != 0.0D) {
                        d8 /= d11;
                        d9 /= d11;
                        d10 /= d11;
                        double d12 = (double) getSeenPercent(vec3d, entity);
                        double d13 = (1.0D - d7) * d12;

                        // CraftBukkit start
                        // entity.damageEntity(this.b(), (float) ((int) ((d13 * d13 + d13) / 2.0D * 7.0D * (double) f2 + 1.0D)));
                        CraftEventFactory.entityDamage = source;
                        entity.forceExplosionKnockback = false;
                        boolean wasDamaged = entity.hurt(this.getDamageSource(), (float) ((int) ((d13 * d13 + d13) / 2.0D * 7.0D * (double) f2 + 1.0D)));
                        CraftEventFactory.entityDamage = null;
                        if (!wasDamaged && !(entity instanceof PrimedTnt || entity instanceof FallingBlockEntity) && !entity.forceExplosionKnockback) {
                            continue;
                        }
                        // CraftBukkit end
                        double d14 = d13;

                        if (entity instanceof LivingEntity) {
                            d14 = ProtectionEnchantment.getExplosionKnockbackAfterDampener((LivingEntity) entity, d13);
                        }

                        entity.setDeltaMovement(entity.getDeltaMovement().add(d8 * d14, d9 * d14, d10 * d14));
                        if (entity instanceof Player) {
                            Player entityhuman = (Player) entity;

                            if (!entityhuman.isSpectator() && (!entityhuman.isCreative() || !entityhuman.abilities.flying)) {
                                this.hitPlayers.put(entityhuman, new Vec3(d8 * d13, d9 * d13, d10 * d13));
                            }
                        }
                    }
                }
            }
        }

    }

    public void finalizeExplosion(boolean flag) {
        if (this.level.isClientSide) {
            this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
        }

        boolean flag1 = this.blockInteraction != Explosion.BlockInteraction.NONE;

        if (flag) {
            if (this.radius >= 2.0F && flag1) {
                this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
            } else {
                this.level.addParticle(ParticleTypes.EXPLOSION, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
            }
        }

        if (flag1) {
            ObjectArrayList<Pair<ItemStack, BlockPos>> objectarraylist = new ObjectArrayList();

            Collections.shuffle(this.toBlow, this.level.random);
            Iterator iterator = this.toBlow.iterator();
            // CraftBukkit start
            org.bukkit.World bworld = this.level.getWorld();
            org.bukkit.entity.Entity explode = this.source == null ? null : this.source.getBukkitEntity();
            Location location = new Location(bworld, this.x, this.y, this.z);

            List<org.bukkit.block.Block> blockList = Lists.newArrayList();
            for (int i1 = this.toBlow.size() - 1; i1 >= 0; i1--) {
                BlockPos cpos = (BlockPos) this.toBlow.get(i1);
                org.bukkit.block.Block bblock = bworld.getBlockAt(cpos.getX(), cpos.getY(), cpos.getZ());
                if (!bblock.getType().isAir()) {
                    blockList.add(bblock);
                }
            }

            boolean cancelled;
            List<org.bukkit.block.Block> bukkitBlocks;
            float yield;

            if (explode != null) {
                EntityExplodeEvent event = new EntityExplodeEvent(explode, location, blockList, this.blockInteraction == Explosion.BlockInteraction.DESTROY ? 1.0F / this.radius : 1.0F);
                this.level.getServerOH().getPluginManager().callEvent(event);
                cancelled = event.isCancelled();
                bukkitBlocks = event.blockList();
                yield = event.getYield();
            } else {
                BlockExplodeEvent event = new BlockExplodeEvent(location.getBlock(), blockList, this.blockInteraction == Explosion.BlockInteraction.DESTROY ? 1.0F / this.radius : 1.0F);
                this.level.getServerOH().getPluginManager().callEvent(event);
                cancelled = event.isCancelled();
                bukkitBlocks = event.blockList();
                yield = event.getYield();
            }

            this.toBlow.clear();

            for (org.bukkit.block.Block bblock : bukkitBlocks) {
                BlockPos coords = new BlockPos(bblock.getX(), bblock.getY(), bblock.getZ());
                toBlow.add(coords);
            }

            if (cancelled) {
                this.wasCanceled = true;
                return;
            }
            // CraftBukkit end
            iterator = this.toBlow.iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition = (BlockPos) iterator.next();
                BlockState iblockdata = this.level.getType(blockposition);
                Block block = iblockdata.getBlock();

                if (!iblockdata.isAir()) {
                    BlockPos blockposition1 = blockposition.immutable();

                    this.level.getProfiler().push("explosion_blocks");
                    if (block.dropFromExplosion(this) && this.level instanceof ServerLevel) {
                        BlockEntity tileentity = block.isEntityBlock() ? this.level.getBlockEntity(blockposition) : null;
                        LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.level)).withRandom(this.level.random).set(LootContextParams.BLOCK_POS, blockposition).set(LootContextParams.TOOL, ItemStack.EMPTY).setOptional(LootContextParams.BLOCK_ENTITY, tileentity).setOptional(LootContextParams.THIS_ENTITY, this.source);

                        if (this.blockInteraction == Explosion.BlockInteraction.DESTROY || yield < 1.0F) { // CraftBukkit - add yield
                            loottableinfo_builder.set(LootContextParams.EXPLOSION_RADIUS, 1.0F / yield); // CraftBukkit - add yield
                        }

                        iblockdata.getDrops(loottableinfo_builder).forEach((itemstack) -> {
                            addBlockDrops(objectarraylist, itemstack, blockposition1);
                        });
                    }

                    this.level.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 3);
                    block.wasExploded(this.level, blockposition, this);
                    this.level.getProfiler().pop();
                }
            }

            ObjectListIterator objectlistiterator = objectarraylist.iterator();

            while (objectlistiterator.hasNext()) {
                Pair<ItemStack, BlockPos> pair = (Pair) objectlistiterator.next();

                Block.popResource(this.level, (BlockPos) pair.getSecond(), (ItemStack) pair.getFirst());
            }
        }

        if (this.fire) {
            Iterator iterator1 = this.toBlow.iterator();

            while (iterator1.hasNext()) {
                BlockPos blockposition2 = (BlockPos) iterator1.next();

                if (this.random.nextInt(3) == 0 && this.level.getType(blockposition2).isAir() && this.level.getType(blockposition2.below()).isSolidRender(this.level, blockposition2.below())) {
                    // CraftBukkit start - Ignition by explosion
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level, blockposition2.getX(), blockposition2.getY(), blockposition2.getZ(), this).isCancelled()) {
                        this.level.setTypeUpdate(blockposition2, BaseFireBlock.getState((BlockGetter) this.level, blockposition2));
                    }
                    // CraftBukkit end
                }
            }
        }

    }

    private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> objectarraylist, ItemStack itemstack, BlockPos blockposition) {
        if (itemstack.isEmpty()) return; // CraftBukkit - SPIGOT-5425
        int i = objectarraylist.size();

        for (int j = 0; j < i; ++j) {
            Pair<ItemStack, BlockPos> pair = (Pair) objectarraylist.get(j);
            ItemStack itemstack1 = (ItemStack) pair.getFirst();

            if (ItemEntity.areMergable(itemstack1, itemstack)) {
                ItemStack itemstack2 = ItemEntity.merge(itemstack1, itemstack, 16);

                objectarraylist.set(j, Pair.of(itemstack2, pair.getSecond()));
                if (itemstack.isEmpty()) {
                    return;
                }
            }
        }

        objectarraylist.add(Pair.of(itemstack, blockposition));
    }

    public DamageSource getDamageSource() {
        return this.damageSource;
    }

    public Map<Player, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Nullable
    public LivingEntity getSourceMob() {
        if (this.source == null) {
            return null;
        } else if (this.source instanceof PrimedTnt) {
            return ((PrimedTnt) this.source).getOwner();
        } else if (this.source instanceof LivingEntity) {
            return (LivingEntity) this.source;
        } else {
            if (this.source instanceof Projectile) {
                Entity entity = ((Projectile) this.source).getOwner();

                if (entity instanceof LivingEntity) {
                    return (LivingEntity) entity;
                }
            }

            return null;
        }
    }

    public void clearToBlow() {
        this.toBlow.clear();
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public static enum BlockInteraction {

        NONE, BREAK, DESTROY;

        private BlockInteraction() {}
    }
}
