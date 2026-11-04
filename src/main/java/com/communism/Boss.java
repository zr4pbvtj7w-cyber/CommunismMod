package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Финальный босс — Мистер Капитал (гигантский Вызыватель в золоте). */
public final class Boss {
	public static final String TAG = "cm_boss";
	public static final String NAME = "Мистер Капитал";
	private static UUID bossId;
	private static ServerBossEvent bar;

	public static boolean alive() {
		Entity e = find();
		return e != null && e.isAlive();
	}

	private static Entity find() {
		if (bossId == null || CommunismMod.server == null) return null;
		for (ServerLevel l : CommunismMod.server.getAllLevels()) {
			Entity e = l.getEntity(bossId);
			if (e != null) return e;
		}
		return null;
	}

	public static void summon(MinecraftServer s) {
		ServerLevel l = s.overworld();
		int players = Math.max(1, s.getPlayerList().getPlayers().size());
		BlockPos at;
		if (Data.built()) {
			at = new BlockPos(Data.px(), Data.py(), Data.pz() + 2);
		} else {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			at = p.blockPosition().offset(6, 0, 0);
		}
		spawn(l, at, 250 + 120 * players);
		for (int i = 0; i < 4; i++) {
			l.addFreshEntity(lightning(l, at.getX() + CommunismMod.RNG.nextInt(13) - 6, at.getY(), at.getZ() + CommunismMod.RNG.nextInt(13) - 6));
		}
		Msg.titleAll(s, Msg.t(NAME.toUpperCase(), ChatFormatting.GOLD, ChatFormatting.BOLD), Msg.t("«Всё в этом мире продаётся!»", ChatFormatting.YELLOW));
		Msg.soundAll(s, SoundEvents.WITHER_SPAWN, 1f, 0.8f);
		Msg.sayAll(s, NAME, "Ха-ха-ха! Ваша пятилетка — ничто против моих дивидендов! Я куплю ваш колхоз, вашу хрущёвку и даже вашего Генсека!");
	}

	private static Entity lightning(ServerLevel l, double x, double y, double z) {
		var b = EntityType.LIGHTNING_BOLT.create(l, EntitySpawnReason.EVENT);
		b.setPos(x, y, z);
		b.setVisualOnly(true);
		return b;
	}

	public static void spawn(ServerLevel l, BlockPos at, int hp) {
		var boss = EntityType.EVOKER.create(l, EntitySpawnReason.EVENT);
		if (boss == null) return;
		boss.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		boss.setCustomName(Component.literal(NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		boss.setCustomNameVisible(true);
		AttributeInstance mh = boss.getAttribute(Attributes.MAX_HEALTH);
		if (mh != null) mh.setBaseValue(Math.min(1000, hp));
		boss.setHealth(boss.getMaxHealth());
		AttributeInstance sc = boss.getAttribute(Attributes.SCALE);
		if (sc != null) sc.setBaseValue(2.5);
		AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) kb.setBaseValue(0.8);
		boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		boss.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
		boss.setPersistenceRequired();
		boss.addTag(TAG);
		l.addFreshEntity(boss);
		bossId = boss.getUUID();
	}

	static void tick(MinecraftServer s) {
		if (bossId == null) {
			if (bar != null) {
				bar.removeAllPlayers();
				bar = null;
			}
			return;
		}
		Entity e = find();
		if (!(e instanceof LivingEntity boss) || !boss.isAlive()) {
			if (CommunismMod.tick % 100 == 0 && e == null) {
				// босс в выгруженном чанке — просто прячем полоску
				if (bar != null) bar.removeAllPlayers();
			}
			return;
		}
		if (bar == null) {
			bar = new ServerBossEvent(Component.literal("☠ " + NAME + " ☠").withStyle(ChatFormatting.GOLD), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
			bar.setDarkenScreen(true);
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (p.level() == boss.level() && p.distanceTo(boss) < 96) bar.addPlayer(p); else bar.removePlayer(p);
		}
		bar.setProgress(Math.max(0f, Math.min(1f, boss.getHealth() / boss.getMaxHealth())));
		if (CommunismMod.tick % 160 == 0) ability(s, boss);
	}

	private static void ability(MinecraftServer s, LivingEntity boss) {
		ServerLevel l = (ServerLevel) boss.level();
		switch (CommunismMod.RNG.nextInt(4)) {
			case 0 -> {
				Msg.sayAll(s, NAME, "Выплата дивидендов! Мои акционеры, взять их!");
				for (int i = 0; i < 2; i++) {
					Npcs.spawnBurzhuy(l, boss.blockPosition().offset(CommunismMod.RNG.nextInt(7) - 3, 0, CommunismMod.RNG.nextInt(7) - 3));
				}
			}
			case 1 -> {
				Msg.sayAll(s, NAME, "ИНФЛЯЦИЯ! Ваши трудодни обесцениваются!");
				for (ServerPlayer p : s.getPlayerList().getPlayers()) {
					if (p.level() != l || p.distanceTo(boss) > 40) continue;
					p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0));
				}
			}
			case 2 -> {
				Msg.sayAll(s, NAME, "Возьмите взятку, товарищи! Никто не узнает!");
				for (int i = 0; i < 8; i++) {
					Msg.drop(l, boss.getX() + CommunismMod.RNG.nextInt(9) - 4, boss.getY() + 3, boss.getZ() + CommunismMod.RNG.nextInt(9) - 4, new ItemStack(Items.GOLD_NUGGET, 1 + CommunismMod.RNG.nextInt(3)));
				}
				Msg.sayAll(s, Npcs.DICTATOR, "Не брать! Это проверка на сознательность!");
			}
			default -> {
				Msg.sayAll(s, NAME, "Ухожу в офшор!");
				boss.randomTeleport(boss.getX() + CommunismMod.RNG.nextInt(17) - 8, boss.getY(), boss.getZ() + CommunismMod.RNG.nextInt(17) - 8, true);
				boss.heal(20f);
			}
		}
	}

	public static void onDeath(LivingEntity boss) {
		bossId = null;
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
		Victory.win(CommunismMod.server, (ServerLevel) boss.level(), boss.getX(), boss.getY(), boss.getZ());
	}

	static void shutdown() {
		if (bar != null) bar.removeAllPlayers();
		bar = null;
		bossId = null;
	}
}
