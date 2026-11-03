package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Указы Партии — случайное событие каждые 10 минут. */
public final class Decrees {
	public static final int PERIOD = 600; // секунд

	public record Decree(String name, String desc, boolean multiOnly, Consumer<MinecraftServer> run) {}

	public static final List<Decree> ALL = new ArrayList<>();
	private static int lastIndex = -1;

	// состояние длительных указов
	static final Map<UUID, Integer> subbotnik = new HashMap<>();
	static final Map<UUID, Integer> competition = new HashMap<>();
	static final Set<UUID> pendingForms = new HashSet<>();

	static {
		ALL.add(new Decree("О всеобщем равенстве", "Здоровье и сытость всех товарищей уравниваются. Никто не должен быть здоровее других!", false, Decrees::equality));
		ALL.add(new Decree("О национализации", "Частная собственность отменяется! Часть вашего имущества передаётся товарищу.", false, Decrees::nationalization));
		ALL.add(new Decree("Об очереди за колбасой", "Всем встать в очередь! Не двигаться 20 секунд — и получите колбасу.", false, Decrees::sausageQueue));
		ALL.add(new Decree("Об урожае", "Урожай перевыполнен на 1000%! Продукты падают прямо с неба!", false, Decrees::harvestRain));
		ALL.add(new Decree("О субботнике", "Всесоюзный субботник! 3 минуты: кто больше всех сломает блоков — получит премию!", false, Decrees::subbotnik));
		ALL.add(new Decree("О капиталистической угрозе", "Буржуи высадились рядом с вами! Они воруют вещи. Бейте их — за каждого +5 трудодней!", false, Decrees::invasion));
		ALL.add(new Decree("О космической гонке", "Партия назначает космонавта! Поехали!", false, Decrees::spaceRace));
		ALL.add(new Decree("О сибирских морозах", "Зима пришла по плану. Всем выданы ушанки.", false, Decrees::frost));
		ALL.add(new Decree("Пятилетку — за три года!", "3 минуты ударного труда: всё, сданное Генсеку, засчитывается ВДВОЙНЕ!", false, Decrees::doublePlan));
		ALL.add(new Decree("О квартирном вопросе", "Партия решила квартирный вопрос! Одному из товарищей выделена квартира.", false, Decrees::apartment));
		ALL.add(new Decree("О параде", "Парад в честь достижений! Салют, здоровье и всеобщее ликование!", false, Decrees::parade));
		ALL.add(new Decree("О дефиците", "Временный дефицит товаров. Магазин Завхоза закрыт на 3 минуты, всем немного голодно.", false, Decrees::deficit));
		ALL.add(new Decree("О партсобрании", "Срочное партсобрание на Красной площади! Явка обязательна. Через 40 секунд вернём обратно.", false, Decrees::meeting));
		ALL.add(new Decree("О перестановке кадров", "Кадры решают всё! Товарищи меняются местами.", true, Decrees::reshuffle));
		ALL.add(new Decree("О борьбе с бюрократией", "Каждому выдана " + Goods.FORM_NAME + ". Сдайте её Генсеку за 5 минут, иначе — выговор!", false, Decrees::bureaucracy));
		ALL.add(new Decree("О кукурузе", "Кукуруза — царица полей! Каждому положена кукуруза.", false, Decrees::corn));
		ALL.add(new Decree("О соцсоревновании", "Соцсоревнование: 3 минуты, кто убьёт больше мобов — тот Герой!", false, Decrees::competition));
	}

	static void secondTick(MinecraftServer s) {
		int left = Data.get("decree");
		if (left <= 0 || left > PERIOD) left = PERIOD;
		left--;
		if (left == 30) {
			Msg.broadcast(s, "Внимание! Через 30 секунд будет зачитан новый Указ Партии!", ChatFormatting.YELLOW);
			Msg.soundAll(s, SoundEvents.BELL_BLOCK, 1f, 0.8f);
		}
		if (left <= 0) {
			runRandom(s);
			left = PERIOD;
		}
		Data.set("decree", left);

		// длительные эффекты
		int dbl = Data.get("double");
		if (dbl > 0) {
			Data.set("double", dbl - 1);
			if (dbl == 1) Msg.broadcast(s, "Ударный режим окончен. Пятилетка снова считается в одинарном размере.", ChatFormatting.GRAY);
		}
		int def = Data.get("deficit");
		if (def > 0) {
			Data.set("deficit", def - 1);
			if (def == 1) Msg.broadcast(s, "Дефицит преодолён! Завхоз снова открыл магазин.", ChatFormatting.GREEN);
		}
		tickTimed(s, "subbotnik", subbotnik, "Субботник", "сломал больше всех блоков", 15);
		tickTimed(s, "competition", competition, "Соцсоревнование", "уничтожил больше всех врагов народа", 20);
		int forms = Data.get("forms");
		if (forms > 0) {
			Data.set("forms", forms - 1);
			if (forms == 1) {
				for (UUID id : pendingForms) {
					ServerPlayer p = s.getPlayerList().getPlayer(id);
					if (p != null) Discipline.vygovor(p, "не сдана " + Goods.FORM_NAME + " (бюрократия не дремлет)");
				}
				pendingForms.clear();
			}
		}
	}

	private static void tickTimed(MinecraftServer s, String key, Map<UUID, Integer> scores, String title, String what, int prize) {
		int t = Data.get(key);
		if (t <= 0) return;
		Data.set(key, t - 1);
		if (t != 1) return;
		UUID best = null;
		int bestScore = 0;
		for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
			if (e.getValue() > bestScore) {
				bestScore = e.getValue();
				best = e.getKey();
			}
		}
		for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
			ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
			if (p != null) Ranks.addTrud(p, Math.max(1, e.getValue() / 10), title.toLowerCase());
		}
		ServerPlayer winner = best == null ? null : s.getPlayerList().getPlayer(best);
		if (winner != null) {
			Msg.broadcast(s, Msg.party().append(Msg.t(title + " окончен! ", ChatFormatting.GOLD, ChatFormatting.BOLD))
					.append(Msg.t(winner.getName().getString(), ChatFormatting.WHITE, ChatFormatting.BOLD))
					.append(Msg.t(" " + what + " (" + bestScore + ") и получает +" + prize + " трудодней!", ChatFormatting.GOLD)));
			Ranks.addTrud(winner, prize, "победа: " + title.toLowerCase());
			Msg.fireworksAround(winner, 3);
		} else {
			Msg.broadcast(s, title + " окончен. Никто не отличился. Партия разочарована.", ChatFormatting.GRAY);
		}
		scores.clear();
	}

	public static void runRandom(MinecraftServer s) {
		int players = s.getPlayerList().getPlayers().size();
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < ALL.size(); i++) {
			if (i == lastIndex) continue;
			if (ALL.get(i).multiOnly() && players < 2) continue;
			idx.add(i);
		}
		int pick = idx.get(CommunismMod.RNG.nextInt(idx.size()));
		run(s, pick);
	}

	public static void run(MinecraftServer s, int index) {
		lastIndex = index;
		Decree d = ALL.get(index);
		int no = Data.add("decreeNo", 1);
		Msg.titleAll(s, Msg.t("УКАЗ ПАРТИИ №" + no, ChatFormatting.RED, ChatFormatting.BOLD), Msg.t(d.name(), ChatFormatting.GOLD));
		Msg.broadcast(s, Msg.party().append(Msg.t("УКАЗ №" + no + " «" + d.name() + "»: ", ChatFormatting.RED, ChatFormatting.BOLD))
				.append(Msg.t(d.desc(), ChatFormatting.YELLOW)));
		Msg.soundAll(s, SoundEvents.BELL_BLOCK, 1f, 0.5f);
		d.run().accept(s);
	}

	// ------------------------------------------------------------------ указы

	private static List<ServerPlayer> players(MinecraftServer s) {
		return new ArrayList<>(s.getPlayerList().getPlayers());
	}

	private static void equality(MinecraftServer s) {
		List<ServerPlayer> ps = players(s);
		if (ps.isEmpty()) return;
		if (ps.size() == 1) {
			ServerPlayer p = ps.get(0);
			float h = p.getHealth();
			int f = p.getFoodData().getFoodLevel();
			p.setHealth(Math.max(2f, Math.min(p.getMaxHealth(), f)));
			p.getFoodData().setFoodLevel(Math.max(2, Math.min(20, Math.round(h))));
			Msg.info(p, "Вы один, поэтому равенство наступило между вашим здоровьем и сытостью. Они поменялись местами.");
			return;
		}
		float hs = 0;
		int fs = 0;
		for (ServerPlayer p : ps) {
			hs += p.getHealth();
			fs += p.getFoodData().getFoodLevel();
		}
		float h = Math.max(1f, hs / ps.size());
		int f = Math.round((float) fs / ps.size());
		for (ServerPlayer p : ps) {
			p.setHealth(Math.min(p.getMaxHealth(), h));
			p.getFoodData().setFoodLevel(f);
			Msg.info(p, "Теперь у всех поровну: " + Math.round(h) + " здоровья и " + f + " сытости.");
		}
	}

	private static int randomFilledSlot(Inventory inv) {
		List<Integer> main = new ArrayList<>(), hot = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			if (inv.getItem(i).isEmpty()) continue;
			if (i < 9) hot.add(i); else main.add(i);
		}
		List<Integer> src = main.isEmpty() ? hot : main;
		return src.isEmpty() ? -1 : src.get(CommunismMod.RNG.nextInt(src.size()));
	}

	private static void nationalization(MinecraftServer s) {
		List<ServerPlayer> ps = players(s);
		if (ps.isEmpty()) return;
		if (ps.size() == 1) {
			ServerPlayer p = ps.get(0);
			Inventory inv = p.getInventory();
			List<ItemStack> items = new ArrayList<>();
			for (int i = 9; i < 36; i++) items.add(inv.getItem(i).copy());
			Collections.shuffle(items, CommunismMod.RNG);
			for (int i = 9; i < 36; i++) inv.setItem(i, items.get(i - 9));
			Msg.info(p, "Вы единственный товарищ, поэтому ваш рюкзак национализирован и перераспределён... внутри самого себя. Удачи найти вещи!");
			return;
		}
		List<ItemStack> taken = new ArrayList<>();
		for (ServerPlayer p : ps) {
			Inventory inv = p.getInventory();
			int slot = randomFilledSlot(inv);
			if (slot < 0) {
				taken.add(ItemStack.EMPTY);
				continue;
			}
			taken.add(inv.getItem(slot).copy());
			inv.setItem(slot, ItemStack.EMPTY);
		}
		for (int i = 0; i < ps.size(); i++) {
			ServerPlayer from = ps.get(i), to = ps.get((i + 1) % ps.size());
			ItemStack st = taken.get(i);
			if (st.isEmpty()) {
				Msg.info(from, "У вас нечего национализировать. Пролетарию нечего терять, кроме своих цепей!");
				continue;
			}
			Msg.info(from, "Ваше имущество (" + st.getCount() + "× " + st.getHoverName().getString() + ") передано товарищу " + to.getName().getString() + ".");
			Msg.info(to, "Вы получили от товарища " + from.getName().getString() + ": " + st.getCount() + "× " + st.getHoverName().getString() + ".");
			Msg.give(to, st);
		}
	}

	private static void sausageQueue(MinecraftServer s) {
		for (ServerPlayer p : players(s)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 6));
			p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 400, 2));
			Msg.title(p, Msg.t("ОЧЕРЕДЬ", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t("Вы 147-й. Ждите.", ChatFormatting.GRAY));
		}
		Scheduler.later(400, () -> {
			for (ServerPlayer p : players(s)) {
				Msg.give(p, Goods.sausage(4));
				Msg.say(p, Npcs.ZAVHOZ, "Ваша очередь подошла! Колбаса выдана. Следующая очередь — через 10 лет.");
				Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
			}
		});
	}

	private static void harvestRain(MinecraftServer s) {
		ItemStack[] food = {new ItemStack(Items.POTATO), new ItemStack(Items.CARROT), new ItemStack(Items.WHEAT),
				new ItemStack(Items.BREAD), new ItemStack(Items.BAKED_POTATO), new ItemStack(Items.APPLE), new ItemStack(Items.BEETROOT)};
		Scheduler.repeat(16, 10, () -> {
			for (ServerPlayer p : players(s)) {
				ItemStack st = food[CommunismMod.RNG.nextInt(food.length)].copy();
				st.setCount(1 + CommunismMod.RNG.nextInt(3));
				Msg.drop(Msg.level(p), p.getX() + CommunismMod.RNG.nextInt(9) - 4, p.getY() + 8, p.getZ() + CommunismMod.RNG.nextInt(9) - 4, st);
			}
		});
	}

	private static void subbotnik(MinecraftServer s) {
		subbotnik.clear();
		Data.set("subbotnik", 180);
	}

	private static void competition(MinecraftServer s) {
		competition.clear();
		Data.set("competition", 180);
	}

	public static boolean subbotnikActive() {
		return Data.get("subbotnik") > 0;
	}

	public static boolean competitionActive() {
		return Data.get("competition") > 0;
	}

	private static void invasion(MinecraftServer s) {
		int n = Plan.stage() >= 2 ? 3 : 2;
		for (ServerPlayer p : players(s)) {
			for (int i = 0; i < n; i++) {
				BlockPos pos = surfaceNear(p, 8, 14);
				Npcs.spawnBurzhuy(Msg.level(p), pos);
			}
			Msg.sound(p, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 1f);
		}
	}

	private static void spaceRace(MinecraftServer s) {
		List<ServerPlayer> ps = players(s);
		if (ps.isEmpty()) return;
		ServerPlayer p = ps.get(CommunismMod.RNG.nextInt(ps.size()));
		Msg.broadcast(s, Msg.party().append(Msg.t("Товарищ " + p.getName().getString() + " назначен космонавтом! Поехали!", ChatFormatting.AQUA, ChatFormatting.BOLD)));
		p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 2));
		Scheduler.later(100, () -> p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0)));
		Msg.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.6f);
		Ranks.addTrud(p, 5, "вклад в космонавтику");
	}

	private static void frost(MinecraftServer s) {
		for (ServerPlayer p : players(s)) {
			ServerLevel l = Msg.level(p);
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
			if (p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				p.setItemSlot(EquipmentSlot.HEAD, Goods.ushanka(l));
			} else {
				Msg.give(p, Goods.ushanka(l));
			}
			if (l.dimension() == Level.OVERWORLD) {
				BlockPos c = p.blockPosition();
				BlockState snow = Blocks.SNOW.defaultBlockState();
				for (int dx = -6; dx <= 6; dx++) {
					for (int dz = -6; dz <= 6; dz++) {
						if (dx * dx + dz * dz > 36) continue;
						int x = c.getX() + dx, z = c.getZ() + dz;
						BlockPos pos = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
						if (l.getBlockState(pos).isAir() && snow.canSurvive(l, pos)) l.setBlock(pos, snow, 3);
					}
				}
			}
		}
		Scheduler.repeat(15, 20, () -> {
			for (ServerPlayer p : players(s)) p.setTicksFrozen(Math.min(130, p.getTicksFrozen() + 30));
		});
	}

	private static void doublePlan(MinecraftServer s) {
		Data.set("double", 180);
	}

	private static void apartment(MinecraftServer s) {
		List<ServerPlayer> ps = new ArrayList<>();
		for (ServerPlayer p : players(s)) if (p.level().dimension() == Level.OVERWORLD) ps.add(p);
		if (ps.isEmpty()) {
			Msg.broadcast(s, "...но в Верхнем мире никого нет. Квартира ушла по блату.", ChatFormatting.GRAY);
			return;
		}
		ServerPlayer p = ps.get(CommunismMod.RNG.nextInt(ps.size()));
		BlockPos pos = surfaceNear(p, 9, 12);
		Structures.miniHouse(Msg.level(p), pos);
		Msg.broadcast(s, Msg.party().append(Msg.t("Товарищ " + p.getName().getString() + " получил квартиру на " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "! Новоселье!", ChatFormatting.GREEN)));
		Msg.give(p, Goods.apartmentOrder());
	}

	private static void parade(MinecraftServer s) {
		Msg.broadcast(s, "♪ Вперёд, товарищи, к сияющим блокам! Кирку держите выше головы! ♪", ChatFormatting.GOLD);
		Msg.broadcast(s, "♪ Крипер не страшен, когда мы едины, и даже Эндермен нам не враг! ♪", ChatFormatting.GOLD);
		for (ServerPlayer p : players(s)) {
			p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0));
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
			p.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 1));
		}
		Scheduler.repeat(6, 20, () -> {
			for (ServerPlayer p : players(s)) Msg.fireworksAround(p, 2);
		});
	}

	private static void deficit(MinecraftServer s) {
		Data.set("deficit", 180);
		for (ServerPlayer p : players(s)) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
	}

	private static void meeting(MinecraftServer s) {
		if (!Data.built()) {
			Msg.broadcast(s, "...но Красная площадь ещё не построена. Собрание переносится.", ChatFormatting.GRAY);
			return;
		}
		ServerLevel ow = s.overworld();
		Map<UUID, Vec3> back = new HashMap<>();
		Map<UUID, ServerLevel> backLevel = new HashMap<>();
		int i = 0;
		for (ServerPlayer p : players(s)) {
			back.put(p.getUUID(), p.position());
			backLevel.put(p.getUUID(), Msg.level(p));
			Discipline.teleport(p, ow, Data.px() - 3 + (i++) * 1.5, Data.py(), Data.pz() - 8.5);
			Ranks.addTrud(p, 3, "явка на партсобрание");
		}
		String[] speech = {
				"Товарищи! Повестка дня: первое — коммунизм. Второе — тоже коммунизм. Разное — коммунизм.",
				"Сегодня мы обсудим, почему крипер взорвал колхоз. Крипер, встаньте! ...Он не пришёл. Позор.",
				"Поступило предложение: поднять норму выработки на 500%. Кто против? Никого? Единогласно!",
				"Товарищи, капиталисты говорят, что у нас нет свободы. Это ложь! У нас есть свобода соглашаться с Партией!"};
		Scheduler.later(40, () -> Msg.sayAll(s, Npcs.DICTATOR, speech[CommunismMod.RNG.nextInt(speech.length)]));
		Scheduler.later(800, () -> {
			for (Map.Entry<UUID, Vec3> e : back.entrySet()) {
				ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
				if (p == null || Discipline.isExiled(p)) continue;
				Vec3 v = e.getValue();
				Discipline.teleport(p, backLevel.get(e.getKey()), v.x, v.y, v.z);
				Msg.info(p, "Собрание окончено. Вас вернули на рабочее место.");
			}
		});
	}

	private static void reshuffle(MinecraftServer s) {
		List<ServerPlayer> ps = players(s);
		if (ps.size() < 2) return;
		List<Vec3> pos = new ArrayList<>();
		List<ServerLevel> lv = new ArrayList<>();
		for (ServerPlayer p : ps) {
			pos.add(p.position());
			lv.add(Msg.level(p));
		}
		for (int i = 0; i < ps.size(); i++) {
			int j = (i + 1) % ps.size();
			Vec3 v = pos.get(j);
			Discipline.teleport(ps.get(i), lv.get(j), v.x, v.y, v.z);
			Msg.info(ps.get(i), "Вас перевели на должность товарища " + ps.get(j).getName().getString() + ". Работайте!");
		}
	}

	private static void bureaucracy(MinecraftServer s) {
		pendingForms.clear();
		for (ServerPlayer p : players(s)) {
			Msg.give(p, Goods.form37b());
			pendingForms.add(p.getUUID());
		}
		Data.set("forms", 300);
	}

	/** Вызывается, когда игрок отдаёт форму Генсеку. */
	public static boolean handInForm(ServerPlayer p) {
		ItemStack st = p.getMainHandItem();
		if (!st.is(Items.PAPER) || !Goods.FORM_NAME.equals(Goods.nameOf(st))) return false;
		st.shrink(1);
		if (pendingForms.remove(p.getUUID())) {
			Msg.say(p, Npcs.DICTATOR, "Так-так... Подпись, печать, ещё печать... Всё верно! Бюрократия побеждена бюрократией.");
			Ranks.addTrud(p, 8, "сдана " + Goods.FORM_NAME);
		} else {
			Msg.say(p, Npcs.DICTATOR, "Эта форма уже недействительна... или поддельная? Ладно, приму. Но я запомнил.");
		}
		return true;
	}

	private static void corn(MinecraftServer s) {
		for (ServerPlayer p : players(s)) {
			Msg.give(p, Goods.corn(6));
			Msg.give(p, Goods.bread(4));
		}
	}

	// ------------------------------------------------------------------ helpers

	public static BlockPos surfaceNear(ServerPlayer p, int min, int max) {
		ServerLevel l = Msg.level(p);
		double a = CommunismMod.RNG.nextDouble() * Math.PI * 2;
		int d = min + CommunismMod.RNG.nextInt(max - min + 1);
		int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
		int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
		int y = l.dimension() == Level.OVERWORLD ? l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : (int) p.getY();
		return new BlockPos(x, y, z);
	}

	static void reset() {
		subbotnik.clear();
		competition.clear();
		pendingForms.clear();
		lastIndex = -1;
	}
}
