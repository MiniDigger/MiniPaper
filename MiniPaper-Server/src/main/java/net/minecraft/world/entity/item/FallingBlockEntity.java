package net.minecraft.world.entity.item;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.DirectionalPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class FallingBlockEntity extends Entity {

    private BlockState blockState;
    public int time;
    public boolean dropItem;
    private boolean cancelDrop;
    public boolean hurtEntities;
    private int fallDamageMax;
    private float fallDamageAmount;
    public CompoundTag blockData;
    protected static final EntityDataAccessor<BlockPos> DATA_START_POS = SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.BLOCK_POS);

    public FallingBlockEntity(EntityType<? extends FallingBlockEntity> entitytypes, Level world) {
        super(entitytypes, world);
        this.blockState = Blocks.SAND.getBlockData();
        this.dropItem = true;
        this.fallDamageMax = 40;
        this.fallDamageAmount = 2.0F;
    }

    public FallingBlockEntity(Level world, double d0, double d1, double d2, BlockState iblockdata) {
        this(EntityType.FALLING_BLOCK, world);
        this.blockState = iblockdata;
        this.blocksBuilding = true;
        this.setPos(d0, d1 + (double) ((1.0F - this.getBbHeight()) / 2.0F), d2);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
        this.setStartPos(this.blockPosition());
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    public void setStartPos(BlockPos blockposition) {
        this.entityData.set(FallingBlockEntity.DATA_START_POS, blockposition);
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(FallingBlockEntity.DATA_START_POS, BlockPos.ZERO);
    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }

    @Override
    public void tick() {
        if (this.blockState.isAir()) {
            this.remove();
        } else {
            Block block = this.blockState.getBlock();
            BlockPos blockposition;

            if (this.time++ == 0) {
                blockposition = this.blockPosition();
                if (this.level.getType(blockposition).is(block) && !CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                    this.level.removeBlock(blockposition, false);
                } else if (!this.level.isClientSide) {
                    this.remove();
                    return;
                }
            }

            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
            if (!this.level.isClientSide) {
                blockposition = this.blockPosition();
                boolean flag = this.blockState.getBlock() instanceof ConcretePowderBlock;
                boolean flag1 = flag && this.level.getFluidState(blockposition).is((Tag) FluidTags.WATER);
                double d0 = this.getDeltaMovement().lengthSqr();

                if (flag && d0 > 1.0D) {
                    BlockHitResult movingobjectpositionblock = this.level.clip(new ClipContext(new Vec3(this.xo, this.yo, this.zo), this.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this));

                    if (movingobjectpositionblock.getType() != HitResult.Type.MISS && this.level.getFluidState(movingobjectpositionblock.getBlockPos()).is((Tag) FluidTags.WATER)) {
                        blockposition = movingobjectpositionblock.getBlockPos();
                        flag1 = true;
                    }
                }

                if (!this.onGround && !flag1) {
                    if (!this.level.isClientSide && (this.time > 100 && (blockposition.getY() < 1 || blockposition.getY() > 256) || this.time > 600)) {
                        if (this.dropItem && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                            this.spawnAtLocation((ItemLike) block);
                        }

                        this.remove();
                    }
                } else {
                    BlockState iblockdata = this.level.getType(blockposition);

                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
                    if (!iblockdata.is(Blocks.MOVING_PISTON)) {
                        this.remove();
                        if (!this.cancelDrop) {
                            boolean flag2 = iblockdata.canBeReplaced((BlockPlaceContext) (new DirectionalPlaceContext(this.level, blockposition, Direction.DOWN, ItemStack.EMPTY, Direction.UP)));
                            boolean flag3 = FallingBlock.canFallThrough(this.level.getType(blockposition.below())) && (!flag || !flag1);
                            boolean flag4 = this.blockState.canSurvive(this.level, blockposition) && !flag3;

                            if (flag2 && flag4) {
                                if (this.blockState.hasProperty(BlockStateProperties.WATERLOGGED) && this.level.getFluidState(blockposition).getType() == Fluids.WATER) {
                                    this.blockState = (BlockState) this.blockState.setValue(BlockStateProperties.WATERLOGGED, true);
                                }

                                // CraftBukkit start
                                if (CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, this.blockState).isCancelled()) {
                                    return;
                                }
                                // CraftBukkit end
                                if (this.level.setTypeAndData(blockposition, this.blockState, 3)) {
                                    if (block instanceof FallingBlock) {
                                        ((FallingBlock) block).onLand(this.level, blockposition, this.blockState, iblockdata, this);
                                    }

                                    if (this.blockData != null && block instanceof EntityBlock) {
                                        BlockEntity tileentity = this.level.getBlockEntity(blockposition);

                                        if (tileentity != null) {
                                            CompoundTag nbttagcompound = tileentity.save(new CompoundTag());
                                            Iterator iterator = this.blockData.getAllKeys().iterator();

                                            while (iterator.hasNext()) {
                                                String s = (String) iterator.next();
                                                net.minecraft.nbt.Tag nbtbase = this.blockData.get(s);

                                                if (!"x".equals(s) && !"y".equals(s) && !"z".equals(s)) {
                                                    nbttagcompound.put(s, nbtbase.copy());
                                                }
                                            }

                                            tileentity.load(this.blockState, nbttagcompound);
                                            tileentity.setChanged();
                                        }
                                    }
                                } else if (this.dropItem && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                    this.spawnAtLocation((ItemLike) block);
                                }
                            } else if (this.dropItem && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                this.spawnAtLocation((ItemLike) block);
                            }
                        } else if (block instanceof FallingBlock) {
                            ((FallingBlock) block).onBroken(this.level, blockposition, this);
                        }
                    }
                }
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        if (this.hurtEntities) {
            int i = Mth.ceil(f - 1.0F);

            if (i > 0) {
                List<Entity> list = Lists.newArrayList(this.level.getEntities(this, this.getBoundingBox()));
                boolean flag = this.blockState.is((Tag) BlockTags.ANVIL);
                DamageSource damagesource = flag ? DamageSource.ANVIL : DamageSource.FALLING_BLOCK;
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();

                    CraftEventFactory.entityDamage = this; // CraftBukkit
                    entity.hurt(damagesource, (float) Math.min(Mth.floor((float) i * this.fallDamageAmount), this.fallDamageMax));
                    CraftEventFactory.entityDamage = null; // CraftBukkit
                }

                if (flag && (double) this.random.nextFloat() < 0.05000000074505806D + (double) i * 0.05D) {
                    BlockState iblockdata = AnvilBlock.damage(this.blockState);

                    if (iblockdata == null) {
                        this.cancelDrop = true;
                    } else {
                        this.blockState = iblockdata;
                    }
                }
            }
        }

        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.put("BlockState", NbtUtils.writeBlockState(this.blockState));
        nbttagcompound.putInt("Time", this.time);
        nbttagcompound.putBoolean("DropItem", this.dropItem);
        nbttagcompound.putBoolean("HurtEntities", this.hurtEntities);
        nbttagcompound.putFloat("FallHurtAmount", this.fallDamageAmount);
        nbttagcompound.putInt("FallHurtMax", this.fallDamageMax);
        if (this.blockData != null) {
            nbttagcompound.put("TileEntityData", this.blockData);
        }

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.blockState = NbtUtils.readBlockState(nbttagcompound.getCompound("BlockState"));
        this.time = nbttagcompound.getInt("Time");
        if (nbttagcompound.contains("HurtEntities", 99)) {
            this.hurtEntities = nbttagcompound.getBoolean("HurtEntities");
            this.fallDamageAmount = nbttagcompound.getFloat("FallHurtAmount");
            this.fallDamageMax = nbttagcompound.getInt("FallHurtMax");
        } else if (this.blockState.is((Tag) BlockTags.ANVIL)) {
            this.hurtEntities = true;
        }

        if (nbttagcompound.contains("DropItem", 99)) {
            this.dropItem = nbttagcompound.getBoolean("DropItem");
        }

        if (nbttagcompound.contains("TileEntityData", 10)) {
            this.blockData = nbttagcompound.getCompound("TileEntityData");
        }

        if (this.blockState.isAir()) {
            this.blockState = Blocks.SAND.getBlockData();
        }

    }

    public void setHurtsEntities(boolean flag) {
        this.hurtEntities = flag;
    }

    @Override
    public void appendEntityCrashDetails(CrashReportCategory crashreportsystemdetails) {
        super.appendEntityCrashDetails(crashreportsystemdetails);
        crashreportsystemdetails.setDetail("Immitating BlockState", (Object) this.blockState.toString());
    }

    public BlockState getBlock() {
        return this.blockState;
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, Block.getCombinedId(this.getBlock()));
    }
}
