package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

public class DamageSource {

    public static final DamageSource IN_FIRE = (new DamageSource("inFire")).bypassArmor().setIsFire();
    public static final DamageSource LIGHTNING_BOLT = new DamageSource("lightningBolt");
    public static final DamageSource ON_FIRE = (new DamageSource("onFire")).bypassArmor().setIsFire();
    public static final DamageSource LAVA = (new DamageSource("lava")).setIsFire();
    public static final DamageSource HOT_FLOOR = (new DamageSource("hotFloor")).setIsFire();
    public static final DamageSource IN_WALL = (new DamageSource("inWall")).bypassArmor();
    public static final DamageSource CRAMMING = (new DamageSource("cramming")).bypassArmor();
    public static final DamageSource DROWN = (new DamageSource("drown")).bypassArmor();
    public static final DamageSource STARVE = (new DamageSource("starve")).bypassArmor().bypassMagic();
    public static final DamageSource CACTUS = new DamageSource("cactus");
    public static final DamageSource FALL = (new DamageSource("fall")).bypassArmor();
    public static final DamageSource FLY_INTO_WALL = (new DamageSource("flyIntoWall")).bypassArmor();
    public static final DamageSource OUT_OF_WORLD = (new DamageSource("outOfWorld")).bypassArmor().bypassInvul();
    public static final DamageSource GENERIC = (new DamageSource("generic")).bypassArmor();
    public static final DamageSource MAGIC = (new DamageSource("magic")).bypassArmor().setMagic();
    public static final DamageSource WITHER = (new DamageSource("wither")).bypassArmor();
    public static final DamageSource ANVIL = new DamageSource("anvil");
    public static final DamageSource FALLING_BLOCK = new DamageSource("fallingBlock");
    public static final DamageSource DRAGON_BREATH = (new DamageSource("dragonBreath")).bypassArmor();
    public static final DamageSource DRY_OUT = new DamageSource("dryout");
    public static final DamageSource SWEET_BERRY_BUSH = new DamageSource("sweetBerryBush");
    private boolean bypassArmor;
    private boolean bypassInvul;
    private boolean bypassMagic;
    private float exhaustion = 0.1F;
    private boolean isFireSource;
    private boolean isProjectile;
    private boolean scalesWithDifficulty;
    private boolean isMagic;
    private boolean isExplosion;
    public final String msgId;
    // CraftBukkit start
    private boolean sweep;

    public boolean isSweep() {
        return sweep;
    }

    public DamageSource sweep() {
        this.sweep = true;
        return this;
    }
    // CraftBukkit end

    public static DamageSource sting(LivingEntity entityliving) {
        return new EntityDamageSource("sting", entityliving);
    }

    public static DamageSource mobAttack(LivingEntity entityliving) {
        return new EntityDamageSource("mob", entityliving);
    }

    public static DamageSource indirectMobAttack(Entity entity, LivingEntity entityliving) {
        return new IndirectEntityDamageSource("mob", entity, entityliving);
    }

    public static DamageSource playerAttack(Player entityhuman) {
        return new EntityDamageSource("player", entityhuman);
    }

    public static DamageSource arrow(AbstractArrow entityarrow, @Nullable Entity entity) {
        return (new IndirectEntityDamageSource("arrow", entityarrow, entity)).setProjectile();
    }

    public static DamageSource trident(Entity entity, @Nullable Entity entity1) {
        return (new IndirectEntityDamageSource("trident", entity, entity1)).setProjectile();
    }

    public static DamageSource fireworks(FireworkRocketEntity entityfireworks, @Nullable Entity entity) {
        return (new IndirectEntityDamageSource("fireworks", entityfireworks, entity)).setExplosion();
    }

    public static DamageSource fireball(Fireball entityfireballfireball, @Nullable Entity entity) {
        return entity == null ? (new IndirectEntityDamageSource("onFire", entityfireballfireball, entityfireballfireball)).setIsFire().setProjectile() : (new IndirectEntityDamageSource("fireball", entityfireballfireball, entity)).setIsFire().setProjectile();
    }

    public static DamageSource witherSkull(WitherSkull entitywitherskull, Entity entity) {
        return (new IndirectEntityDamageSource("witherSkull", entitywitherskull, entity)).setProjectile();
    }

    public static DamageSource thrown(Entity entity, @Nullable Entity entity1) {
        return (new IndirectEntityDamageSource("thrown", entity, entity1)).setProjectile();
    }

    public static DamageSource indirectMagic(Entity entity, @Nullable Entity entity1) {
        return (new IndirectEntityDamageSource("indirectMagic", entity, entity1)).bypassArmor().setMagic();
    }

    public static DamageSource thorns(Entity entity) {
        return (new EntityDamageSource("thorns", entity)).setThorns().setMagic();
    }

    public static DamageSource explosion(@Nullable Explosion explosion) {
        return explosion(explosion != null ? explosion.getSourceMob() : null);
    }

    public static DamageSource explosion(@Nullable LivingEntity entityliving) {
        return entityliving != null ? (new EntityDamageSource("explosion.player", entityliving)).setScalesWithDifficulty().setExplosion() : (new DamageSource("explosion")).setScalesWithDifficulty().setExplosion();
    }

    public static DamageSource badRespawnPointExplosion() {
        return new BadRespawnPointDamage();
    }

    public String toString() {
        return "DamageSource (" + this.msgId + ")";
    }

    public boolean isProjectile() {
        return this.isProjectile;
    }

    public DamageSource setProjectile() {
        this.isProjectile = true;
        return this;
    }

    public boolean isExplosion() {
        return this.isExplosion;
    }

    public DamageSource setExplosion() {
        this.isExplosion = true;
        return this;
    }

    public boolean isBypassArmor() {
        return this.bypassArmor;
    }

    public float getFoodExhaustion() {
        return this.exhaustion;
    }

    public boolean isBypassInvul() {
        return this.bypassInvul;
    }

    public boolean isBypassMagic() {
        return this.bypassMagic;
    }

    protected DamageSource(String s) {
        this.msgId = s;
    }

    @Nullable
    public Entity getDirectEntity() {
        return this.getEntity();
    }

    @Nullable
    public Entity getEntity() {
        return null;
    }

    protected DamageSource bypassArmor() {
        this.bypassArmor = true;
        this.exhaustion = 0.0F;
        return this;
    }

    protected DamageSource bypassInvul() {
        this.bypassInvul = true;
        return this;
    }

    protected DamageSource bypassMagic() {
        this.bypassMagic = true;
        this.exhaustion = 0.0F;
        return this;
    }

    protected DamageSource setIsFire() {
        this.isFireSource = true;
        return this;
    }

    public Component getLocalizedDeathMessage(LivingEntity entityliving) {
        LivingEntity entityliving1 = entityliving.getKillCredit();
        String s = "death.attack." + this.msgId;
        String s1 = s + ".player";

        return entityliving1 != null ? new TranslatableComponent(s1, new Object[]{entityliving.getDisplayName(), entityliving1.getDisplayName()}) : new TranslatableComponent(s, new Object[]{entityliving.getDisplayName()});
    }

    public boolean isFire() {
        return this.isFireSource;
    }

    public String getMsgId() {
        return this.msgId;
    }

    public DamageSource setScalesWithDifficulty() {
        this.scalesWithDifficulty = true;
        return this;
    }

    public boolean scalesWithDifficulty() {
        return this.scalesWithDifficulty;
    }

    public boolean isMagic() {
        return this.isMagic;
    }

    public DamageSource setMagic() {
        this.isMagic = true;
        return this;
    }

    public boolean isCreativePlayer() {
        Entity entity = this.getEntity();

        return entity instanceof Player && ((Player) entity).abilities.instabuild;
    }

    @Nullable
    public Vec3 getSourcePosition() {
        return null;
    }
}
