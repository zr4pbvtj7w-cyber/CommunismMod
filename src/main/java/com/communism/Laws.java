package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Законы СССР. Одновременно действуют 3 последних закона. Новый закон Вождь издаёт каждые 6 минут
 * (или его предлагают игроки, или назначает игрок-Генсек после отставки Вождя).
 */
public final class Laws {
	public static final int PERIOD = 360;
	public static final int SLOTS = 3;

	public record Law(int id, String name, String desc, String rule, boolean multiOnly, ItemStack icon) {}

	public static final int COLLECTIVISM = 1, GOLD = 2, NO_SLEEP = 3, DRY = 4, PEACE = 5, EXERCISE = 6, PLAN_ECONOMY = 7,
			THRIFT = 8, POLITENESS = 9, UPRIGHT = 10, EAST_GAZE = 11, NO_RUN = 12, HATS = 13, AIR_TAX = 14, VEGETARIAN = 15,
			NO_GRAVITY = 16, NO_NIGHT = 17, NO_SPECULATION = 18, WORSHIP = 19, SILENCE = 20;

	public static final List<Law> ALL = new ArrayList<>();

	private static void add(int id, String name, String desc, String rule, boolean multi, ItemStack icon) {
		ALL.add(new Law(id, name, desc, rule, multi, icon));
	}

	static {
		add(COLLECTIVISM, "О коллективизме", "Отрываться от коллектива запрещено!", "Нельзя отходить дальше 96 блоков от товарищей дольше 20 секунд.", true, new ItemStack(Items.LEAD));
		add(GOLD, "О буржуйском металле", "Золото — металл буржуазии!", "Всё золото из инвентаря изымается в пользу народа (с выплатой трудодней).", false, new ItemStack(Items.GOLD_INGOT));
		add(NO_SLEEP, "О трудовой дисциплине", "Спать на рабочем месте запрещено!", "Кровати не работают. Попытка лечь — нарушение.", false, new ItemStack(Items.RED_BED));
		add(DRY, "Сухой закон", "Зелья — пережиток капитализма.", "Пить зелья запрещено. Даже квас.", false, new ItemStack(Items.POTION));
		add(PEACE, "О мирном небе", "Товарищ товарищу — друг!", "Бить других игроков запрещено.", true, new ItemStack(Items.WHITE_BANNER));
		add(EXERCISE, "О всеобщей зарядке", "В здоровом теле — здоровый план!", "Каждые 3 минуты — зарядка: за 30 секунд подпрыгнуть 10 раз.", false, new ItemStack(Items.RABBIT_FOOT));
		add(PLAN_ECONOMY, "О плановой экономике", "Перевыполним норму!", "Добыча блоков приносит в 3 раза больше трудодней.", false, new ItemStack(Items.IRON_PICKAXE));
		add(THRIFT, "О бережливости", "Вы — государственное имущество.", "Умирать запрещено. Смерть — нарушение.", false, new ItemStack(Items.SKELETON_SKULL));
		add(POLITENESS, "О вежливости", "Обращайтесь по форме!", "В каждом сообщении в чате должно быть слово «товарищ».", false, new ItemStack(Items.WRITABLE_BOOK));
		add(UPRIGHT, "О прямохождении", "Партия не прячется!", "Приседать дольше 3 секунд запрещено.", false, new ItemStack(Items.ARMOR_STAND));
		add(EAST_GAZE, "«Взгляд на Восток»", "Не смотрите на загнивающий Запад!", "Смотреть на запад дольше 8 секунд подряд запрещено.", false, new ItemStack(Items.COMPASS));
		add(NO_RUN, "О спокойствии", "Не бегите впереди Партии!", "Бежать (спринт) дольше 5 секунд подряд запрещено.", false, new ItemStack(Items.LEATHER_BOOTS));
		add(HATS, "О головных уборах", "Без шапки — простудишься и сорвёшь план!", "Ходить без шлема дольше 20 секунд запрещено.", false, new ItemStack(Items.LEATHER_HELMET));
		add(AIR_TAX, "Налог на воздух", "Воздух — народное достояние.", "Каждые 10 прыжков списывается 1 трудодень.", false, new ItemStack(Items.FEATHER));
		add(VEGETARIAN, "О вегетарианстве", "Мясо — еда буржуев.", "Есть мясо запрещено. Колбаса — не мясо, её можно.", false, new ItemStack(Items.COOKED_BEEF));
		add(NO_GRAVITY, "Об отмене гравитации", "Партия отменила гравитацию как буржуазную лженауку!", "Все прыгают выше и падают медленно.", false, new ItemStack(Items.PHANTOM_MEMBRANE));
		add(NO_NIGHT, "Об отмене ночи", "Ночь отменена постановлением Совета.", "В Верхнем мире всегда день.", false, new ItemStack(Items.SUNFLOWER));
		add(NO_SPECULATION, "О спекуляции", "Торговля с частниками — спекуляция!", "Торговать с обычными жителями деревень запрещено.", false, new ItemStack(Items.EMERALD));
		add(WORSHIP, "О минуте почтения", "Почтим Вождя!", "Каждые 4 минуты — минута почтения: за 20 секунд нужно присесть (поклониться).", false, new ItemStack(Items.GOLDEN_HELMET));
		add(SILENCE, "О тишине", "Болтун — находка для шпиона!", "Сообщения в чате длиннее 25 символов запрещены.", false, new ItemStack(Items.PAPER));
	}

	public static Law get(int id) {
		for (Law l : ALL) if (l.id() == id) return l;
		return null;
	}

	public static int slot(int i) {
		return Data.get("law" + i);
	}

	public static boolean is(int id) {
		if (id <= 0) return false;
		for (int i = 0; i < SLOTS; i++) if (slot(i) == id) return true;
		return false;
	}

	public static List<Law> active() {
		List<Law> l = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			Law law = get(slot(i));
			if (law != null) l.add(law);
		}
		return l;
	}

	public static int enactedCount() {
		return Data.get("lawsEnacted");
	}

	public static String shortList() {
		StringBuilder sb = new StringBuilder();
		for (Law l : active()) {
			if (sb.length() > 0) sb.append("  •  ");
			sb.append(l.name());
		}
		return sb.length() == 0 ? "законов нет" : sb.toString();
	}

	/** Законы, которые сейчас можно принять. */
	public static List<Law> candidates(MinecraftServer s) {
		int players = s.getPlayerList().getPlayers().size();
		List<Law> pool = new ArrayList<>();
		for (Law l : ALL) {
			if (is(l.id())) continue;
			if (l.multiOnly() && players < 2) continue;
			if (l.id() == GOLD && Plan.stage() == 2) continue; // золото нужно для электрификации
			if (l.id() == UPRIGHT && is(WORSHIP)) continue;
			if (l.id() == WORSHIP && is(UPRIGHT)) continue;
			pool.add(l);
		}
		return pool;
	}

	public static List<Law> randomCandidates(MinecraftServer s, int n) {
		List<Law> pool = candidates(s);
		java.util.Collections.shuffle(pool, CommunismMod.RNG);
		return pool.subList(0, Math.min(n, pool.size()));
	}

	/** Принять закон: он вытесняет самый старый из трёх. */
	public static void enact(MinecraftServer s, int id, String author) {
		Law law = get(id);
		if (law == null || is(id)) return;
		Law old = get(slot(SLOTS - 1));
		for (int i = SLOTS - 1; i > 0; i--) Data.set("law" + i, slot(i - 1));
		Data.set("law0", id);
		int n = Data.add("lawsEnacted", 1);
		Data.set("lawTimer", PERIOD);
		if (old != null) Plaque.all(s, Plaque.Kind.INFO, "Отменён закон «" + old.name() + "»", 6);
		Plaque.all(s, Plaque.Kind.LAW, "НОВЫЙ ЗАКОН №" + n + " «" + law.name() + "»: " + law.rule(), 12);
		Msg.titleAll(s, Msg.t("ЗАКОН №" + n, ChatFormatting.DARK_RED, ChatFormatting.BOLD), Msg.t("«" + law.name() + "» — " + law.desc(), ChatFormatting.GOLD));
		Msg.broadcast(s, Msg.party().append(Msg.t("Закон №" + n + " «" + law.name() + "» (" + author + "): ", ChatFormatting.DARK_RED, ChatFormatting.BOLD))
				.append(Msg.t(law.rule(), ChatFormatting.YELLOW)));
		Msg.soundAll(s, SoundEvents.ANVIL_LAND, 0.7f, 0.6f);
		STATE.clear();
		if (n == Leader.LAWS_TO_RETIRE && !Leader.retired()) {
			Scheduler.later(200, () -> Plaque.all(s, Plaque.Kind.DECREE, "Вождь устало вздыхает... Кажется, он хочет поговорить. Зайдите во Дворец Советов.", 12));
		}
	}

	public static void repeal(MinecraftServer s, int id) {
		List<Integer> keep = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) if (slot(i) != id && slot(i) != 0) keep.add(slot(i));
		for (int i = 0; i < SLOTS; i++) Data.set("law" + i, i < keep.size() ? keep.get(i) : 0);
		Law l = get(id);
		if (l != null) Plaque.all(s, Plaque.Kind.INFO, "Закон «" + l.name() + "» отменён", 8);
	}

	// ------------------------------------------------------------------ исполнение

	/** Состояние игрока по законам. */
	private static final class St {
		int sneak, west, sprint, noHat, lonely;
		int lastJumps = -1, taxJumps;
		boolean exerciseDone, worshipDone;
		int exerciseStartJumps;
	}

	private static final Map<UUID, St> STATE = new HashMap<>();
	private static int exerciseWindow = 0, worshipWindow = 0;

	private static St st(ServerPlayer p) {
		return STATE.computeIfAbsent(p.getUUID(), u -> new St());
	}

	private static int jumps(ServerPlayer p) {
		return p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
	}

	static void secondTick(MinecraftServer s) {
		boolean victory = Plan.stage() >= Plan.VICTORY;
		// автоматическое принятие законов
		int t = Data.get("lawTimer");
		if (t <= 0 || t > PERIOD * 2) t = PERIOD;
		t--;
		if (t <= 0) {
			List<Law> c = randomCandidates(s, 1);
			if (!c.isEmpty()) enact(s, c.get(0).id(), Leader.retired() ? "Верховный Совет" : "Великий Вождь");
			t = Leader.retired() ? PERIOD * 2 : PERIOD;
		}
		Data.set("lawTimer", t);

		long sec = CommunismMod.tick / 20;
		List<ServerPlayer> ps = s.getPlayerList().getPlayers();

		// окна зарядки и почтения
		if (is(EXERCISE)) {
			if (sec % 180 == 0) {
				exerciseWindow = 30;
				for (ServerPlayer p : ps) {
					St st = st(p);
					st.exerciseDone = false;
					st.exerciseStartJumps = jumps(p);
					Plaque.show(p, Plaque.Kind.LAW, "ЗАРЯДКА! Подпрыгните 10 раз за 30 секунд! Раз-два, раз-два!", 10);
					Msg.sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.2f);
				}
			} else if (exerciseWindow > 0) {
				exerciseWindow--;
				for (ServerPlayer p : ps) {
					St st = st(p);
					if (st.exerciseDone) continue;
					int done = jumps(p) - st.exerciseStartJumps;
					if (done >= 10) {
						st.exerciseDone = true;
						p.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1));
						Ranks.addTrud(p, 1, "зарядка выполнена");
						Plaque.show(p, Plaque.Kind.GOOD, "Зарядка выполнена! Бодрость +100%.", 5);
					} else if (exerciseWindow == 0) {
						Justice.violate(p, EXERCISE, "пропущена обязательная зарядка (" + done + "/10 прыжков)");
					} else if (exerciseWindow % 5 == 0) {
						Msg.actionbar(p, Msg.t("Зарядка: " + done + "/10 прыжков, осталось " + exerciseWindow + " сек.", ChatFormatting.YELLOW));
					}
				}
			}
		}
		if (is(WORSHIP)) {
			if (sec % 240 == 0) {
				worshipWindow = 20;
				for (ServerPlayer p : ps) {
					st(p).worshipDone = false;
					Plaque.show(p, Plaque.Kind.LAW, "МИНУТА ПОЧТЕНИЯ! Присядьте (поклонитесь) в течение 20 секунд!", 10);
				}
			} else if (worshipWindow > 0) {
				worshipWindow--;
				for (ServerPlayer p : ps) {
					St st = st(p);
					if (st.worshipDone) continue;
					if (p.isShiftKeyDown()) {
						st.worshipDone = true;
						Plaque.show(p, Plaque.Kind.GOOD, "Поклон засчитан. Вождь растроган.", 4);
					} else if (worshipWindow == 0) {
						Justice.violate(p, WORSHIP, "не поклонился в минуту почтения");
					}
				}
			}
		}

		for (ServerPlayer p : ps) {
			if (Justice.inCustody(p)) continue;
			St st = st(p);
			// приседание
			if (is(UPRIGHT) && p.isShiftKeyDown()) {
				if (++st.sneak == 2) Msg.actionbar(p, Msg.t("Закон о прямохождении: встаньте!", ChatFormatting.RED));
				if (st.sneak >= 3) {
					st.sneak = -5;
					Justice.violate(p, UPRIGHT, "приседал дольше 3 секунд");
				}
			} else if (st.sneak > 0) st.sneak = 0; else if (st.sneak < 0) st.sneak++;
			// взгляд на запад (yaw около 90° — это запад)
			float yaw = ((p.getYRot() % 360) + 360) % 360;
			boolean west = yaw > 45 && yaw < 135;
			if (is(EAST_GAZE) && west) {
				st.west++;
				if (st.west == 5) Msg.actionbar(p, Msg.t("Не смотрите на загнивающий Запад!", ChatFormatting.RED));
				if (st.west >= 8) {
					st.west = -4;
					Justice.violate(p, EAST_GAZE, "засмотрелся на Запад");
				}
			} else if (st.west > 0) st.west = 0; else if (st.west < 0) st.west++;
			// спринт
			if (is(NO_RUN) && p.isSprinting()) {
				st.sprint++;
				if (st.sprint == 3) Msg.actionbar(p, Msg.t("Не бегите впереди Партии!", ChatFormatting.RED));
				if (st.sprint >= 5) {
					st.sprint = -3;
					Justice.violate(p, NO_RUN, "бежал впереди Партии");
				}
			} else if (st.sprint > 0) st.sprint = 0; else if (st.sprint < 0) st.sprint++;
			// шапка
			if (is(HATS) && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				st.noHat++;
				if (st.noHat == 5) Plaque.show(p, Plaque.Kind.LAW, "Закон о головных уборах: наденьте шапку! Осталось 15 секунд.", 6);
				if (st.noHat >= 20) {
					st.noHat = 0;
					Justice.violate(p, HATS, "ходил без шапки");
				}
			} else st.noHat = 0;
			// налог на воздух
			int j = jumps(p);
			if (st.lastJumps >= 0 && j > st.lastJumps && is(AIR_TAX)) {
				st.taxJumps += j - st.lastJumps;
				while (st.taxJumps >= 10) {
					st.taxJumps -= 10;
					if (Ranks.spend(p, 1)) Plaque.show(p, Plaque.Kind.MONEY, "Налог на воздух: −1 трудодень за 10 прыжков.", 4);
					else Justice.violate(p, AIR_TAX, "неуплата налога на воздух");
				}
			}
			st.lastJumps = j;
			// коллективизм
			if (is(COLLECTIVISM) && ps.size() > 1) {
				double best = Double.MAX_VALUE;
				for (ServerPlayer o : ps) {
					if (o == p || o.level().dimension() != p.level().dimension()) continue;
					best = Math.min(best, o.distanceTo(p));
				}
				if (best > 96) {
					st.lonely++;
					if (st.lonely == 5) Plaque.show(p, Plaque.Kind.LAW, "Вы оторвались от коллектива! Вернитесь к товарищам за 15 секунд.", 6);
					if (st.lonely >= 20) {
						st.lonely = 0;
						Justice.violate(p, COLLECTIVISM, "отрыв от коллектива");
					}
				} else st.lonely = 0;
			}
			// гравитация
			if (is(NO_GRAVITY) && sec % 5 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, true, false));
				p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 140, 2, true, false));
			}
			if (is(GOLD) && sec % 5 == 0) confiscateGold(p);
			if (victory && sec % 20 == 0) p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20, 0, true, false));
		}
		if (is(NO_NIGHT) && sec % 10 == 0) {
			ServerLevel ow = s.overworld();
			long day = ow.getDayTime() % 24000L;
			if (day > 12000L) ow.setDayTime(ow.getDayTime() + (24000L - day) + 1000L);
		}
	}

	public static void confiscateGold(ServerPlayer p) {
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
		Plaque.show(p, Plaque.Kind.LAW, "Изъят буржуйский металл. Выдано " + trud + " трудодней за сознательность.", 6);
		Ranks.addTrud(p, trud, "сдача золота");
		Msg.sound(p, SoundEvents.ANVIL_LAND, 0.6f, 1.4f);
	}

	private static final java.util.Set<net.minecraft.world.item.Item> MEAT = java.util.Set.of(
			Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.CHICKEN, Items.COOKED_CHICKEN, Items.MUTTON,
			Items.COOKED_MUTTON, Items.RABBIT, Items.COOKED_RABBIT, Items.RABBIT_STEW, Items.ROTTEN_FLESH);

	/** Проверка использования предмета. true — запретить. */
	public static boolean forbidItem(ServerPlayer p, ItemStack st) {
		if (is(DRY) && (st.is(Items.POTION) || st.is(Items.HONEY_BOTTLE))) {
			Justice.violate(p, DRY, "попытка выпить зелье");
			return true;
		}
		if (is(VEGETARIAN) && (MEAT.contains(st.getItem()) || (st.is(Items.COOKED_PORKCHOP) && !Goods.isSausage(st)))) {
			Justice.violate(p, VEGETARIAN, "попытка съесть мясо");
			return true;
		}
		return false;
	}

	public static void onChat(ServerPlayer p, String text) {
		String t = text.toLowerCase(Locale.ROOT);
		if (is(POLITENESS) && !(t.contains("товарищ") || t.contains("tovarish") || t.contains("tovarisch"))) {
			CommunismMod.server.execute(() -> Justice.violate(p, POLITENESS, "обращение без слова «товарищ»"));
		}
		if (is(SILENCE) && text.length() > 25) {
			CommunismMod.server.execute(() -> Justice.violate(p, SILENCE, "болтовня (" + text.length() + " символов)"));
		}
	}

	static void reset() {
		STATE.clear();
		exerciseWindow = 0;
		worshipWindow = 0;
	}

	static boolean overworld(ServerPlayer p) {
		return p.level().dimension() == Level.OVERWORLD;
	}
}
