package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Задания от жителей. У игрока одно задание за раз. */
public final class Quests {
	public enum Kind { KILL, MINE, DELIVER, TALK, VISIT, JUMP, CONTRIBUTE, HARVEST, STAND }

	public record Quest(int id, String giver, String title, String desc, Kind kind, int goal, int reward,
						Predicate<ItemStack> item, ItemStack icon) {}

	public static final List<Quest> ALL = new ArrayList<>();

	private static void q(String giver, String title, String desc, Kind kind, int goal, int reward, Predicate<ItemStack> item, ItemStack icon) {
		ALL.add(new Quest(ALL.size() + 1, giver, title, desc, kind, goal, reward, item, icon));
	}

	static {
		q("politruk", "Раскулачивание", "Уничтожить кулаков-зомби", Kind.KILL, 10, 15, null, new ItemStack(Items.ROTTEN_FLESH));
		q("politruk", "Белая гвардия", "Разбить белогвардейцев-скелетов", Kind.KILL, 8, 15, null, new ItemStack(Items.BONE));
		q("politruk", "Шпионская сеть", "Обезвредить пауков-шпионов", Kind.KILL, 6, 14, null, new ItemStack(Items.SPIDER_EYE));
		q("politruk", "Антисаботаж", "Обезвредить криперов-саботажников", Kind.KILL, 4, 20, null, new ItemStack(Items.GUNPOWDER));
		q("politruk", "Враги народа", "Уничтожить любых враждебных мобов", Kind.KILL, 20, 20, null, new ItemStack(Items.IRON_SWORD));
		q("politruk", "Шахтёрская смена", "Добыть руды (любой)", Kind.MINE, 24, 18, null, new ItemStack(Items.IRON_ORE));
		q("politruk", "Метростроевец", "Выкопать камня под землёй", Kind.MINE, 150, 15, null, new ItemStack(Items.STONE));
		q("politruk", "Лесозаготовка", "Нарубить брёвен", Kind.MINE, 48, 12, null, new ItemStack(Items.OAK_LOG));
		q("politruk", "Сбор подписей", "Поговорить с 6 разными жителями города", Kind.TALK, 6, 12, null, new ItemStack(Items.WRITABLE_BOOK));
		q("politruk", "Политинформация", "Побывать в Сибири, на Космодроме и у Тюрьмы", Kind.VISIT, 3, 14, null, new ItemStack(Items.MAP));
		q("politruk", "Ударник", "Сдать Госплану 64 единицы продукции", Kind.CONTRIBUTE, 64, 20, null, new ItemStack(Items.HAY_BLOCK));
		q("politruk", "Физкультурник", "Подпрыгнуть 100 раз", Kind.JUMP, 100, 10, null, new ItemStack(Items.RABBIT_FOOT));
		q("agronom", "Битва за урожай", "Собрать спелые растения", Kind.HARVEST, 32, 14, null, new ItemStack(Items.WHEAT));
		q("agronom", "Кукурузная кампания", "Принести кукурузу (золотые морковки)", Kind.DELIVER, 6, 25, s -> s.is(Items.GOLDEN_CARROT), new ItemStack(Items.GOLDEN_CARROT));
		q("doyarka", "Надои", "Принести вёдра молока", Kind.DELIVER, 4, 16, s -> s.is(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET));
		q("doyarka", "Корма для бурёнок", "Принести пшеницу", Kind.DELIVER, 32, 12, s -> s.is(Items.WHEAT), new ItemStack(Items.WHEAT));
		q("akademik", "Ветвистая пшеница", "Принести костную муку для опытов", Kind.DELIVER, 24, 14, s -> s.is(Items.BONE_MEAL), new ItemStack(Items.BONE_MEAL));
		q("akademik", "Мичуринский сад", "Принести яблоки", Kind.DELIVER, 8, 16, s -> s.is(Items.APPLE), new ItemStack(Items.APPLE));
		q("pioner", "Макулатура", "Собрать бумагу", Kind.DELIVER, 24, 12, s -> s.is(Items.PAPER), new ItemStack(Items.PAPER));
		q("pioner", "Металлолом", "Собрать железные слитки", Kind.DELIVER, 8, 15, s -> s.is(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT));
		q("prodavshitsa", "Очередь", "Отстоять 60 секунд в очереди у Гастронома", Kind.STAND, 60, 12, null, new ItemStack(Items.COOKED_PORKCHOP));
	}

	public static Quest get(int id) {
		return id >= 1 && id <= ALL.size() ? ALL.get(id - 1) : null;
	}

	public static Quest current(ServerPlayer p) {
		return get(Data.get(p, Data.qtype));
	}

	public static List<Quest> of(String giver) {
		List<Quest> l = new ArrayList<>();
		for (Quest q : ALL) if (q.giver().equals(giver)) l.add(q);
		return l;
	}

	/** Кнопки заданий в окне NPC. */
	public static void addButtons(Ui.Win w, ServerPlayer p, String giver) {
		Quest cur = current(p);
		if (cur != null) {
			int prog = Data.get(p, Data.qprog), goal = Data.get(p, Data.qgoal);
			w.text("Ваше задание: «" + cur.title() + "» — " + cur.desc() + ": " + Math.min(prog, goal) + "/" + goal, ChatFormatting.YELLOW);
			if (cur.giver().equals(giver)) {
				if (cur.kind() == Kind.DELIVER) w.btn(Ui.label("Сдать предметы", ChatFormatting.GREEN, ChatFormatting.BOLD), "quest deliver " + giver, "Отдать подходящие предметы из инвентаря");
				if (prog >= goal) w.btn(Ui.label("Получить награду!", ChatFormatting.GREEN, ChatFormatting.BOLD), "quest claim " + giver, "+" + cur.reward() + " трудодней");
				w.btn(Ui.label("Отказаться (выговор)", ChatFormatting.DARK_GRAY), "quest drop " + giver, null);
			}
			return;
		}
		List<Quest> list = of(giver);
		if (list.isEmpty()) return;
		Quest a = list.get(CommunismMod.RNG.nextInt(list.size()));
		w.btn(Ui.label("Взять задание: «" + a.title() + "»", ChatFormatting.GOLD), "quest take " + a.id(), a.desc() + " (" + a.goal() + "). Награда: " + a.reward() + " трудодней");
		if (list.size() > 1) {
			Quest b;
			do b = list.get(CommunismMod.RNG.nextInt(list.size())); while (b == a);
			w.btn(Ui.label("Взять задание: «" + b.title() + "»", ChatFormatting.GOLD), "quest take " + b.id(), b.desc() + " (" + b.goal() + "). Награда: " + b.reward() + " трудодней");
		}
	}

	public static void handle(ServerPlayer p, String[] a) {
		if (a.length < 3) return;
		String act = a[1];
		switch (act) {
			case "take" -> {
				Quest q = get(Integer.parseInt(a[2]));
				if (q == null || current(p) != null || Npcs.near(p, q.giver(), 10) == null) return;
				int goal = q.goal() + (q.kind() == Kind.STAND || q.kind() == Kind.VISIT || q.kind() == Kind.TALK ? 0 : CommunismMod.RNG.nextInt(q.goal() / 3 + 1));
				Data.set(p, Data.qtype, q.id());
				Data.set(p, Data.qprog, 0);
				Data.set(p, Data.qgoal, goal);
				Data.set(p, Data.qmask, 0);
				Data.set(p, Data.qbase, q.kind() == Kind.JUMP ? p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) : 0);
				Plaque.show(p, Plaque.Kind.DECREE, "Новое задание «" + q.title() + "»: " + q.desc() + " (0/" + goal + ")", 8);
				Msg.sound(p, SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
			}
			case "deliver" -> {
				Quest q = current(p);
				if (q == null || q.kind() != Kind.DELIVER || !q.giver().equals(a[2]) || Npcs.near(p, q.giver(), 10) == null) return;
				int goal = Data.get(p, Data.qgoal), prog = Data.get(p, Data.qprog);
				var inv = p.getInventory();
				for (int i = 0; i < 36 && prog < goal; i++) {
					ItemStack st = inv.getItem(i);
					if (st.isEmpty() || !q.item().test(st)) continue;
					int take = Math.min(st.getCount(), goal - prog);
					if (st.is(Items.MILK_BUCKET)) {
						inv.setItem(i, new ItemStack(Items.BUCKET));
						take = 1;
					} else st.shrink(take);
					prog += take;
				}
				Data.set(p, Data.qprog, prog);
				Plaque.show(p, Plaque.Kind.INFO, "Сдано: " + prog + "/" + goal, 4);
				Npcs.open(p, q.giver());
			}
			case "claim" -> {
				Quest q = current(p);
				if (q == null || !q.giver().equals(a[2]) || Npcs.near(p, q.giver(), 10) == null) return;
				if (Data.get(p, Data.qprog) < Data.get(p, Data.qgoal)) return;
				Data.set(p, Data.qtype, 0);
				Ranks.addTrud(p, q.reward(), "задание «" + q.title() + "»");
				ItemStack[] bonus = {new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.COAL, 16), Goods.sausage(4),
						new ItemStack(Items.EXPERIENCE_BOTTLE, 8), new ItemStack(Items.ARROW, 24), Goods.corn(3), new ItemStack(Items.EMERALD, 2)};
				ItemStack b = bonus[CommunismMod.RNG.nextInt(bonus.length)].copy();
				Msg.give(p, b);
				Msg.title(p, Msg.t("Задание выполнено!", ChatFormatting.GREEN, ChatFormatting.BOLD), Msg.t("«" + q.title() + "»: +" + q.reward() + " трудодней и " + b.getHoverName().getString(), ChatFormatting.GOLD));
				Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
				Npcs.open(p, q.giver());
			}
			case "drop" -> {
				Quest q = current(p);
				if (q == null) return;
				Data.set(p, Data.qtype, 0);
				Justice.vygovor(p, "отказ от партийного задания");
			}
			default -> { }
		}
	}

	// ------------------------------------------------------------------ прогресс

	private static void progress(ServerPlayer p, int amount) {
		int goal = Data.get(p, Data.qgoal), prog = Data.get(p, Data.qprog);
		if (prog >= goal) return;
		prog = Math.min(goal, prog + amount);
		Data.set(p, Data.qprog, prog);
		Quest q = current(p);
		if (prog >= goal) {
			Plaque.show(p, Plaque.Kind.GOOD, "Задание «" + q.title() + "» выполнено! Вернитесь за наградой.", 8);
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1.5f);
		} else if (q.kind() != Kind.STAND || prog % 10 == 0) {
			Msg.actionbar(p, Msg.t(q.title() + ": " + prog + "/" + goal, ChatFormatting.YELLOW));
		}
	}

	private static boolean is(ServerPlayer p, Kind k) {
		Quest q = current(p);
		return q != null && q.kind() == k;
	}

	public static void onKill(ServerPlayer p, LivingEntity e) {
		Quest q = current(p);
		if (q == null || q.kind() != Kind.KILL) return;
		EntityType<?> t = e.getType();
		boolean ok = switch (q.title()) {
			case "Раскулачивание" -> t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED || t == EntityType.ZOMBIE_VILLAGER;
			case "Белая гвардия" -> t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.BOGGED;
			case "Шпионская сеть" -> t == EntityType.SPIDER || t == EntityType.CAVE_SPIDER;
			case "Антисаботаж" -> t == EntityType.CREEPER;
			default -> t.getCategory() == MobCategory.MONSTER;
		};
		if (ok) progress(p, 1);
	}

	public static void onMine(ServerPlayer p, BlockState s) {
		Quest q = current(p);
		if (q == null) return;
		if (q.kind() == Kind.HARVEST && s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) progress(p, 1);
		if (q.kind() != Kind.MINE) return;
		boolean ok = switch (q.title()) {
			case "Шахтёрская смена" -> s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
					|| s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.EMERALD_ORES);
			case "Лесозаготовка" -> s.is(BlockTags.LOGS);
			default -> s.is(BlockTags.BASE_STONE_OVERWORLD);
		};
		if (ok) progress(p, 1);
	}

	public static void onContribute(ServerPlayer p, int items) {
		if (is(p, Kind.CONTRIBUTE)) progress(p, items);
	}

	public static void onTalk(ServerPlayer p, String role) {
		if (!is(p, Kind.TALK)) return;
		int idx = role.hashCode() & 0x1F;
		int mask = Data.get(p, Data.qmask);
		if ((mask & (1 << idx)) != 0) return;
		Data.set(p, Data.qmask, mask | (1 << idx));
		progress(p, 1);
	}

	/** Раз в секунду: посещения, прыжки, очередь. */
	static void secondTick(ServerPlayer p) {
		Quest q = current(p);
		if (q == null) return;
		switch (q.kind()) {
			case JUMP -> {
				int done = p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) - Data.get(p, Data.qbase);
				int prog = Data.get(p, Data.qprog);
				if (done > prog) progress(p, done - prog);
			}
			case VISIT -> {
				int mask = Data.get(p, Data.qmask);
				int[][] spots = {{Structures.SIBIR_DX, Structures.SIBIR_DZ}, {32, -33}, {51, 50}};
				for (int i = 0; i < spots.length; i++) {
					if ((mask & (1 << i)) == 0 && City.near(p, spots[i][0], spots[i][1], 8)) {
						mask |= 1 << i;
						Data.set(p, Data.qmask, mask);
						progress(p, 1);
					}
				}
			}
			case STAND -> {
				if (City.near(p, City.QUEUE_END[0], City.QUEUE_END[1], 4)) progress(p, 1);
				else if (Data.get(p, Data.qprog) > 0 && Data.get(p, Data.qprog) < Data.get(p, Data.qgoal) && CommunismMod.tick % 100 == 0) {
					Msg.actionbar(p, Msg.t("Вы вышли из очереди! Встаньте в конец очереди у Гастронома.", ChatFormatting.RED));
				}
			}
			default -> { }
		}
	}
}
