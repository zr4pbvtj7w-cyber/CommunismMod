package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** МФО «Серп и Процент» — государственные микрозаймы. */
public final class Loans {
	public static final String BANKIR = "Банкир Процентов";

	public record Offer(int amount, int owe, int minutes, int minRank, String name) {}

	public static final Offer[] OFFERS = {
			new Offer(10, 15, 10, 0, "«До получки»"),
			new Offer(30, 50, 15, 1, "«Комсомольский»"),
			new Offer(80, 140, 20, 2, "«Пятилетка в кредит»"),
			new Offer(200, 400, 30, 3, "«Ударный ипотечный»")};

	public static int debt(ServerPlayer p) {
		return Data.get(p, Data.debt);
	}

	public static void open(ServerPlayer p) {
		int d = debt(p);
		Ui.Win w = Ui.npc(BANKIR + " — МФО «Серп и Процент»", ChatFormatting.GREEN, new ItemStack(Items.GOLD_NUGGET),
				d > 0 ? "Товарищ, за вами должок! Вернуть " + d + " трудодней нужно через " + Msg.time(Data.get(p, Data.debtT)) + ". Иначе — коллекторы из милиции."
						: "Займы без справок, поручителей и совести! Ставка — 0% годовых, но 50% поминутно... шучу. Почти.");
		w.text("У вас: " + Ranks.trud(p) + " трудодней. Звание: " + Ranks.NAMES[Ranks.rank(p)] + ". Займов взято: " + Data.get(p, Data.loans), ChatFormatting.GRAY);
		if (d > 0) {
			w.wide(Ui.label("Вернуть долг (" + d + " тр.)", ChatFormatting.GREEN, ChatFormatting.BOLD), "bank repay", "Списать " + d + " трудодней и закрыть займ");
			w.wide(Ui.label("Отсрочка на 5 минут (+30%)", ChatFormatting.YELLOW), "bank delay", "Долг вырастет на 30%");
		} else {
			for (int i = 0; i < OFFERS.length; i++) {
				Offer o = OFFERS[i];
				boolean ok = Ranks.rank(p) >= o.minRank();
				w.btn(Ui.label("Займ " + o.name() + ": " + o.amount() + " → вернуть " + o.owe(), ok ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
						"bank take " + i, ok ? "Срок: " + o.minutes() + " минут" : "Нужно звание: " + Ranks.NAMES[o.minRank()]);
			}
		}
		w.text("Мелкий шрифт: при просрочке приходит милиция и забирает вас. Долг растёт. Банк всегда прав.", ChatFormatting.DARK_GRAY);
		w.open(p);
	}

	public static void handle(ServerPlayer p, String[] a) {
		if (Npcs.near(p, "bankir", 10) == null) {
			Plaque.show(p, Plaque.Kind.INFO, "Подойдите к банкиру в МФО «Серп и Процент».", 4);
			return;
		}
		String act = a.length > 1 ? a[1] : "";
		switch (act) {
			case "take" -> {
				if (debt(p) > 0) return;
				int i = a.length > 2 ? parse(a[2]) : -1;
				if (i < 0 || i >= OFFERS.length) return;
				Offer o = OFFERS[i];
				if (Ranks.rank(p) < o.minRank()) return;
				Data.set(p, Data.debt, o.owe());
				Data.set(p, Data.debtT, o.minutes() * 60);
				Data.add(p, Data.loans, 1);
				Data.add(p, Data.trud, o.amount());
				Plaque.show(p, Plaque.Kind.MONEY, "Займ " + o.name() + ": +" + o.amount() + " трудодней. Вернуть " + o.owe() + " за " + o.minutes() + " мин.", 8);
				Msg.sound(p, SoundEvents.VILLAGER_YES, 1f, 1.3f);
			}
			case "repay" -> {
				int d = debt(p);
				if (d <= 0) return;
				if (!Ranks.spend(p, d)) {
					Plaque.show(p, Plaque.Kind.BAD, "Не хватает трудодней: нужно " + d + ", у вас " + Ranks.trud(p) + ".", 5);
					return;
				}
				Data.set(p, Data.debt, 0);
				Data.set(p, Data.debtT, 0);
				Plaque.show(p, Plaque.Kind.GOOD, "Долг погашен! Банк благодарит за сотрудничество и грустит.", 6);
				Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
			}
			case "delay" -> {
				int d = debt(p);
				if (d <= 0) return;
				Data.set(p, Data.debt, d + Math.max(1, d * 3 / 10));
				Data.add(p, Data.debtT, 300);
				Plaque.show(p, Plaque.Kind.MONEY, "Отсрочка оформлена. Теперь вы должны " + debt(p) + ". Удобно!", 6);
			}
			default -> { }
		}
		open(p);
	}

	static void secondTick(MinecraftServer s) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			int d = debt(p);
			if (d <= 0) continue;
			int t = Data.get(p, Data.debtT) - 1;
			if (t == 60) Plaque.show(p, Plaque.Kind.MONEY, "Банк напоминает: через минуту вернуть " + d + " трудодней! Коллекторы уже обуваются.", 8);
			if (t <= 0) {
				// коллекторы
				int have = Ranks.trud(p);
				int take = Math.min(have, d);
				Ranks.spend(p, take);
				int left = d - take;
				if (left > 0) {
					Data.set(p, Data.debt, left + left / 2);
					Data.set(p, Data.debtT, 300);
					Plaque.show(p, Plaque.Kind.BAD, "Просрочка! Коллекторы списали " + take + ", долг вырос до " + debt(p) + ".", 8);
					Justice.arrest(p, "просрочка по микрозайму", CommunismMod.RNG.nextBoolean() ? Justice.Punishment.CONFISCATION : Justice.Punishment.PILLORY);
				} else {
					Data.set(p, Data.debt, 0);
					Plaque.show(p, Plaque.Kind.BAD, "Коллекторы забрали долг (" + take + " тр.) прямо из кармана. Без насилия. Почти.", 8);
				}
				t = Data.get(p, Data.debtT);
			}
			Data.set(p, Data.debtT, Math.max(0, t));
		}
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
