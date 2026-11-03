package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * ГЛАВНАЯ ЦЕЛЬ МОДА: выполнить Пятилетку (5 «годов»-этапов), затем победить Мистера Капитала
 * и построить Коммунизм.
 */
public final class Plan {
	public record Req(String name, Predicate<ItemStack> test, int base, int tenthsPerItem) {}

	public record Stage(String year, String title, String intro, int reward, Req... reqs) {}

	public static final int BOSS = 5;
	public static final int VICTORY = 6;

	public static final Stage[] STAGES = {
			new Stage("Год 1", "Коллективизация",
					"Партия требует хлеба! Соберите урожай на колхозе и сдайте его Генсеку Усачу.", 25,
					new Req("Пшеница", s -> s.is(Items.WHEAT), 64, 2),
					new Req("Картофель", s -> s.is(Items.POTATO), 32, 3),
					new Req("Морковь", s -> s.is(Items.CARROT), 32, 3),
					new Req("Брёвна", s -> s.is(ItemTags.LOGS), 64, 2)),
			new Stage("Год 2", "Индустриализация",
					"Стране нужна сталь! Даёшь заводы, шахты и булыжник в промышленных масштабах!", 35,
					new Req("Уголь", s -> s.is(Items.COAL), 64, 3),
					new Req("Железные слитки", s -> s.is(Items.IRON_INGOT), 32, 8),
					new Req("Медные слитки", s -> s.is(Items.COPPER_INGOT), 32, 4),
					new Req("Булыжник", s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE), 256, 1)),
			new Stage("Год 3", "Электрификация",
					"Коммунизм есть советская власть плюс электрификация всего Майнкрафта!", 45,
					new Req("Редстоун", s -> s.is(Items.REDSTONE), 64, 3),
					new Req("Золотые слитки (на провода)", s -> s.is(Items.GOLD_INGOT), 16, 10),
					new Req("Лазурит", s -> s.is(Items.LAPIS_LAZULI), 32, 4),
					new Req("Стекло (для лампочек Ильича)", s -> s.is(Items.GLASS), 64, 3)),
			new Stage("Год 4", "Космическая программа",
					"Капиталисты уже смотрят на Луну. Мы должны быть там первыми! Собирайте ракету!", 60,
					new Req("Порох", s -> s.is(Items.GUNPOWDER), 24, 8),
					new Req("Алмазы", s -> s.is(Items.DIAMOND), 6, 40),
					new Req("Обсидиан", s -> s.is(Items.OBSIDIAN), 12, 12),
					new Req("Фейерверки", s -> s.is(Items.FIREWORK_ROCKET), 16, 6)),
			new Stage("Год 5", "Светлое будущее",
					"Последний рывок! Ресурсы из Незера и Края — и коммунизм будет построен!", 100,
					new Req("Огненные стержни", s -> s.is(Items.BLAZE_ROD), 6, 25),
					new Req("Жемчуг Края", s -> s.is(Items.ENDER_PEARL), 8, 20),
					new Req("Алмазы", s -> s.is(Items.DIAMOND), 10, 40),
					new Req("Изумруды (изъятые у кулаков)", s -> s.is(Items.EMERALD), 12, 15)),
	};

	private static ServerBossEvent bar;

	static void init() {
		if (Data.get("factor") == 0) startStage(0, 1);
	}

	public static int stage() {
		return Data.get("stage");
	}

	public static int goal(int i) {
		int s = stage();
		if (s >= STAGES.length) return 0;
		return Math.max(1, (int) Math.round(STAGES[s].reqs()[i].base() * Data.get("factor") / 100.0));
	}

	public static int prog(int i) {
		return Data.get("prog" + i);
	}

	/** Квоты растут с числом игроков: +25% за каждого товарища сверх первого. */
	public static void startStage(int s, int players) {
		Data.set("stage", s);
		for (int i = 0; i < 4; i++) Data.set("prog" + i, 0);
		int n = Math.max(1, Math.min(5, players));
		Data.set("factor", 100 + 25 * (n - 1));
	}

	public static boolean doubleActive() {
		return Data.get("double") > 0;
	}

	/** Сдать предметы Генсеку. all=true — из всего инвентаря. */
	public static boolean contribute(ServerPlayer p, boolean all) {
		int s = stage();
		if (s >= STAGES.length) return false;
		Stage st = STAGES[s];
		Inventory inv = p.getInventory();
		int items = 0, tenths = 0;
		int from = all ? 0 : inv.getSelectedSlot();
		int to = all ? 36 : inv.getSelectedSlot() + 1;
		int mult = doubleActive() ? 2 : 1;
		for (int slot = from; slot < to; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) continue;
			for (int i = 0; i < st.reqs().length; i++) {
				Req r = st.reqs()[i];
				if (!r.test().test(stack)) continue;
				int need = goal(i) - prog(i);
				if (need <= 0) continue;
				int take = Math.min(stack.getCount(), (need + mult - 1) / mult);
				if (take <= 0) continue;
				stack.shrink(take);
				Data.set("prog" + i, Math.min(goal(i), prog(i) + take * mult));
				items += take;
				tenths += take * r.tenthsPerItem();
				if (stack.isEmpty()) break;
			}
		}
		if (items == 0) return false;
		inv.setChanged();
		int reward = Math.max(1, tenths / 10);
		Ranks.addTrud(p, reward, "сдача плана");
		Msg.say(p, Npcs.DICTATOR, phrase() + " Принято " + items + " ед. продукции" + (mult > 1 ? " (×2 по ударному!)" : "") + ".");
		Msg.sound(p, SoundEvents.VILLAGER_YES, 1f, 0.8f);
		if (complete()) completeStage(CommunismMod.server);
		return true;
	}

	private static String phrase() {
		String[] ph = {"Партия гордится вами!", "Вот это по-нашему!", "Так держать, товарищ!", "Запишу в личное дело. В хорошем смысле.",
				"Норма перевыполнена на 300%! Ну, почти.", "Капиталисты рыдают в своих офшорах!"};
		return ph[CommunismMod.RNG.nextInt(ph.length)];
	}

	public static boolean complete() {
		int s = stage();
		if (s >= STAGES.length) return false;
		for (int i = 0; i < STAGES[s].reqs().length; i++) if (prog(i) < goal(i)) return false;
		return true;
	}

	public static void completeStage(MinecraftServer srv) {
		int s = stage();
		Stage st = STAGES[s];
		Msg.broadcast(srv, Msg.party().append(Msg.t(st.year() + " «" + st.title() + "» ВЫПОЛНЕН ДОСРОЧНО!", ChatFormatting.GOLD, ChatFormatting.BOLD)));
		Msg.titleAll(srv, Msg.t("ПЛАН ВЫПОЛНЕН!", ChatFormatting.RED, ChatFormatting.BOLD),
				Msg.t(st.year() + ": " + st.title() + " — каждому +" + st.reward() + " трудодней", ChatFormatting.GOLD));
		Msg.soundAll(srv, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
			Ranks.addTrud(p, st.reward(), "выполнение пятилетки");
			Msg.fireworksAround(p, 5);
		}
		int players = srv.getPlayerList().getPlayers().size();
		if (s + 1 >= STAGES.length) {
			Data.set("stage", BOSS);
			Scheduler.later(80, () -> {
				Msg.sayAll(srv, Npcs.DICTATOR, "Товарищи! Пятилетка выполнена за рекордные сроки! Но... разведка докладывает: МИСТЕР КАПИТАЛ узнал о наших успехах и летит сюда на золотом вертолёте!");
				Msg.sayAll(srv, Npcs.DICTATOR, "Когда будете готовы — подойдите ко мне на Красной площади. Дадим последний и решительный бой!");
				Msg.titleAll(srv, Msg.t("ФИНАЛЬНАЯ БИТВА", ChatFormatting.DARK_RED, ChatFormatting.BOLD),
						Msg.t("Поговорите с Генсеком, когда будете готовы", ChatFormatting.GOLD));
			});
		} else {
			startStage(s + 1, players);
			Stage next = STAGES[s + 1];
			Scheduler.later(80, () -> {
				Msg.sayAll(srv, Npcs.DICTATOR, "Начинаем " + next.year() + ": «" + next.title() + "»! " + next.intro());
				Msg.broadcast(srv, statusText());
			});
		}
	}

	public static MutableComponent statusText() {
		int s = stage();
		MutableComponent c = Msg.party();
		if (s >= VICTORY) return c.append(Msg.t("Коммунизм построен! Каждому — по потребностям.", ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (s == BOSS) return c.append(Msg.t("Пятилетка выполнена. Осталось победить Мистера Капитала! Поговорите с Генсеком.", ChatFormatting.GOLD));
		Stage st = STAGES[s];
		c.append(Msg.t("Пятилетка — " + st.year() + ": «" + st.title() + "»", ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (doubleActive()) c.append(Msg.t("  [×2 УДАРНЫЙ РЕЖИМ]", ChatFormatting.RED, ChatFormatting.BOLD));
		for (int i = 0; i < st.reqs().length; i++) {
			int pr = prog(i), g = goal(i);
			boolean done = pr >= g;
			c.append(Component.literal("\n  "))
					.append(Msg.t(done ? "✔ " : "• ", done ? ChatFormatting.GREEN : ChatFormatting.GRAY))
					.append(Msg.t(st.reqs()[i].name() + ": ", ChatFormatting.WHITE))
					.append(Msg.t(pr + "/" + g, done ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		}
		c.append(Msg.t("\n  Сдавайте ресурсы Генсеку Усачу (ПКМ с предметом, Shift+ПКМ — сдать всё).", ChatFormatting.GRAY));
		return c;
	}

	public static float progress() {
		int s = stage();
		if (s >= STAGES.length) return 1f;
		int sum = 0, total = 0;
		for (int i = 0; i < STAGES[s].reqs().length; i++) {
			sum += Math.min(prog(i), goal(i));
			total += goal(i);
		}
		return total == 0 ? 0f : (float) sum / total;
	}

	static void updateBar(MinecraftServer srv) {
		if (bar == null) {
			bar = new ServerBossEvent(Component.literal("☭"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) bar.addPlayer(p);
		int s = stage();
		String decree = " | Указ через " + Msg.time(Math.max(0, Data.get("decree")));
		String name;
		if (s >= VICTORY) {
			name = "☭ КОММУНИЗМ ПОСТРОЕН ☭" + decree;
			bar.setColor(BossEvent.BossBarColor.YELLOW);
		} else if (s == BOSS) {
			name = "☭ Финал: победите Мистера Капитала!" + decree;
			bar.setColor(BossEvent.BossBarColor.PURPLE);
		} else {
			Stage st = STAGES[s];
			name = "☭ Пятилетка — " + st.year() + ": " + st.title() + " — " + Math.round(progress() * 100) + "%"
					+ (doubleActive() ? " ×2" : "") + decree;
			bar.setColor(BossEvent.BossBarColor.RED);
		}
		bar.setName(Component.literal(name));
		bar.setProgress(Math.max(0f, Math.min(1f, s >= BOSS ? 1f : progress())));
	}

	static void removePlayer(ServerPlayer p) {
		if (bar != null) bar.removePlayer(p);
	}

	static void shutdown() {
		if (bar != null) bar.removeAllPlayers();
		bar = null;
	}
}
