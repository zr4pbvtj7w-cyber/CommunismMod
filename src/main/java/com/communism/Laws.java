package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Законы СССР: действует один закон, он меняется каждые 20 минут. */
public final class Laws {
	public static final int PERIOD = 1200;

	public static final int NONE = 0, COLLECTIVISM = 1, GOLD = 2, NO_SLEEP = 3, DRY = 4, PEACE = 5,
			EXERCISE = 6, PLAN_ECONOMY = 7, THRIFT = 8, POLITENESS = 9, COMMUNISM = 10;

	public static final String[] NAMES = {
			"Нет закона (анархия!)",
			"Закон о коллективизме",
			"Закон о буржуйском металле",
			"Закон о трудовой дисциплине",
			"Сухой закон",
			"Закон о мирном небе",
			"Закон о всеобщей зарядке",
			"Закон о плановой экономике",
			"Закон о бережливости",
			"Закон о вежливости",
			"КОММУНИЗМ"};

	public static final String[] DESCS = {
			"Законов нет. Подозрительно.",
			"Отрываться от коллектива запрещено! Кто дальше 96 блоков от товарищей — получает слабость.",
			"Золото — металл буржуазии! Всё золото из инвентаря конфискуется в пользу народа (с выплатой трудодней).",
			"Спать на рабочем месте запрещено! Кровати не работают.",
			"Зелья запрещены! Пейте кефир. Его, правда, нет.",
			"Драки между товарищами запрещены! Урон по игрокам отключён.",
			"Каждые 2 минуты — обязательная зарядка: всем скорость и прыгучесть!",
			"Добыча блоков приносит в 3 раза больше трудодней!",
			"Смерть — это порча государственного имущества (вас). За смерть — выговор!",
			"Каждое сообщение в чате должно содержать слово «товарищ». Иначе — выговор!",
			"Голода нет, магазин бесплатный, все счастливы. Навсегда."};

	public static int current() {
		return Data.get("law");
	}

	public static boolean is(int law) {
		return current() == law;
	}

	static void secondTick(MinecraftServer s) {
		int law = current();
		if (law != COMMUNISM) {
			int t = Data.get("lawTimer");
			if (t <= 0) t = 300; // первый закон — через 5 минут после начала
			t--;
			if (t <= 0) {
				change(s, -1);
				t = PERIOD;
			}
			Data.set("lawTimer", t);
		}
		long sec = CommunismMod.tick / 20;
		List<ServerPlayer> ps = s.getPlayerList().getPlayers();
		switch (law) {
			case COLLECTIVISM -> {
				if (sec % 5 == 0 && ps.size() > 1) {
					for (ServerPlayer p : ps) {
						double best = Double.MAX_VALUE;
						for (ServerPlayer o : ps) {
							if (o == p) continue;
							if (o.level().dimension() != p.level().dimension()) continue;
							best = Math.min(best, o.distanceTo(p));
						}
						if (best > 96) {
							p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0));
							Msg.actionbar(p, Msg.t("Вы оторвались от коллектива! Вернитесь к товарищам!", ChatFormatting.RED));
						}
					}
				}
			}
			case GOLD -> {
				if (sec % 5 == 0) for (ServerPlayer p : ps) confiscateGold(p);
			}
			case EXERCISE -> {
				if (sec % 120 == 0) {
					for (ServerPlayer p : ps) {
						p.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1));
						p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 1200, 1));
						Msg.actionbar(p, Msg.t("Зарядка! Руки вверх, ноги шире, кирку держать крепче!", ChatFormatting.GREEN));
					}
				}
			}
			case COMMUNISM -> {
				if (sec % 20 == 0) for (ServerPlayer p : ps) p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20, 0));
			}
			default -> { }
		}
	}

	private static void confiscateGold(ServerPlayer p) {
		Inventory inv = p.getInventory();
		int value = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			int per = 0;
			if (st.is(Items.GOLD_INGOT) || st.is(Items.RAW_GOLD)) per = 9;
			else if (st.is(Items.GOLD_BLOCK) || st.is(Items.RAW_GOLD_BLOCK)) per = 81;
			else if (st.is(Items.GOLD_NUGGET)) per = 1;
			if (per == 0) continue;
			value += per * st.getCount();
			inv.setItem(i, ItemStack.EMPTY);
		}
		if (value == 0) return;
		int trud = Math.max(1, value / 9);
		Msg.say(p, Discipline.KGB, "Изъят буржуйский металл. Выдано " + trud + " трудодней. Спасибо за сознательность!");
		Ranks.addTrud(p, trud, "сдача золота");
		Msg.sound(p, SoundEvents.ANVIL_LAND, 0.6f, 1.4f);
	}

	/** Сменить закон. id = -1 — случайный. */
	public static void change(MinecraftServer s, int id) {
		int players = s.getPlayerList().getPlayers().size();
		if (id < 0) {
			List<Integer> pool = new ArrayList<>();
			for (int i = 1; i <= POLITENESS; i++) {
				if (i == current()) continue;
				if ((i == COLLECTIVISM || i == PEACE) && players < 2) continue;
				if (i == GOLD && Plan.stage() == 2) continue; // золото нужно для электрификации
				pool.add(i);
			}
			id = pool.get(CommunismMod.RNG.nextInt(pool.size()));
		}
		Data.set("law", id);
		Msg.titleAll(s, Msg.t("НОВЫЙ ЗАКОН", ChatFormatting.DARK_RED, ChatFormatting.BOLD), Msg.t(NAMES[id], ChatFormatting.GOLD));
		Msg.broadcast(s, Msg.party().append(Msg.t("Вступает в силу " + NAMES[id] + ": ", ChatFormatting.DARK_RED, ChatFormatting.BOLD))
				.append(Msg.t(DESCS[id], ChatFormatting.YELLOW)));
		Msg.soundAll(s, SoundEvents.ANVIL_LAND, 0.7f, 0.6f);
	}

	public static void onChat(ServerPlayer p, String text) {
		if (!is(POLITENESS)) return;
		String t = text.toLowerCase(Locale.ROOT);
		if (t.contains("товарищ") || t.contains("tovarish") || t.contains("tovarisch")) return;
		CommunismMod.server.execute(() -> Discipline.vygovor(p, "обращение без слова «товарищ»"));
	}
}
