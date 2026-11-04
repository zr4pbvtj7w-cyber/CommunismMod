package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Правосудие: предупреждения → выговоры → арест. Милиция везёт в вагонетке по рельсам
 * в тюрьму или на площадь позора и потом обратно.
 */
public final class Justice {
	public static final String KGB = "Майор Бдительный";
	public static final String COP = "Сержант Петренко";

	public enum Punishment {
		PRISON("Тюрьма «Светлый путь»", "60 секунд в камере. Камень в камере можно долбить — каждый блок сокращает срок."),
		PILLORY("Площадь позора", "45 секунд у позорного столба. Двигаться нельзя, граждане кидаются снежками и помидорами."),
		KOLKHOZ("Исправительные работы", "Собрать 12 спелых растений на колхозе. Уходить с поля нельзя."),
		FINE("Штраф", "Списывается треть трудодней (минимум 5)."),
		CONFISCATION("Конфискация", "Случайная вещь из инвентаря переходит в собственность народа."),
		LECTURE("Лекция о вреде капитализма", "Обязательное прослушивание лекции. Уйти нельзя."),
		SIBIR("Ссылка в Сибирь", "45 секунд в ледяной камере. Холодно.");

		public final String title, desc;

		Punishment(String title, String desc) {
			this.title = title;
			this.desc = desc;
		}
	}

	/** Активное дело заключённого. */
	static final class Case {
		final UUID id;
		final Punishment p;
		int left;          // секунд до конца
		Vec3 pin;          // где должен стоять
		double radius;     // сколько можно отойти
		int harvested;
		int cell = -1;
		boolean riding;
		final List<Entity> extras = new ArrayList<>();

		Case(UUID id, Punishment p) {
			this.id = id;
			this.p = p;
		}
	}

	/** Поездка в вагонетке. */
	static final class Ride {
		final UUID player;
		final Entity cart, policeCart, police;
		final List<Vec3> path;
		final double total, stop;
		double dist;
		final Runnable onArrive;

		Ride(UUID player, Entity cart, Entity policeCart, Entity police, List<Vec3> path, double stop, Runnable onArrive) {
			this.player = player;
			this.cart = cart;
			this.policeCart = policeCart;
			this.police = police;
			this.path = path;
			this.total = length(path);
			this.stop = Math.min(stop, total);
			this.onArrive = onArrive;
		}
	}

	private static final Map<UUID, Case> CASES = new HashMap<>();
	private static final List<Ride> RIDES = new ArrayList<>();
	private static final Map<String, Long> WARN = new HashMap<>();
	private static final Map<UUID, Long> LAST_VYG = new HashMap<>();

	public static boolean inCustody(ServerPlayer p) {
		return CASES.containsKey(p.getUUID());
	}

	// ------------------------------------------------------------------ нарушения

	/** Нарушение закона: сначала предупреждение, повтор в течение 2 минут — выговор. */
	public static void violate(ServerPlayer p, int law, String what) {
		if (inCustody(p)) return;
		String key = p.getUUID() + ":" + law;
		Long last = WARN.get(key);
		Laws.Law l = Laws.get(law);
		String lawName = l == null ? "закон" : "«" + l.name() + "»";
		if (last == null || CommunismMod.tick - last > 2400) {
			WARN.put(key, CommunismMod.tick);
			Plaque.show(p, Plaque.Kind.LAW, "ПРЕДУПРЕЖДЕНИЕ: " + what + ". Закон " + lawName + ". Повторите — будет выговор.", 7);
			Msg.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.6f);
			return;
		}
		WARN.put(key, CommunismMod.tick);
		vygovor(p, what + " (закон " + lawName + ")");
	}

	public static void vygovor(ServerPlayer p, String reason) {
		if (inCustody(p)) return;
		Long last = LAST_VYG.get(p.getUUID());
		if (last != null && CommunismMod.tick - last < 60) return;
		LAST_VYG.put(p.getUUID(), CommunismMod.tick);
		int v = Data.add(p, Data.vyg, 1);
		Plaque.show(p, Plaque.Kind.BAD, "ВЫГОВОР " + v + "/3: " + reason, 8);
		Msg.sound(p, SoundEvents.VILLAGER_NO, 1f, 0.6f);
		if (v >= 3) {
			Data.set(p, Data.vyg, 0);
			Scheduler.later(30, () -> arrest(p, "3 выговора", null));
		}
	}

	// ------------------------------------------------------------------ арест

	public static void arrest(ServerPlayer p, String reason, Punishment forced) {
		MinecraftServer s = CommunismMod.server;
		if (s == null || inCustody(p) || p.isRemoved()) return;
		Punishment pun = forced != null ? forced : pick();
		if (!City.built() && (pun == Punishment.PRISON || pun == Punishment.PILLORY || pun == Punishment.KOLKHOZ || pun == Punishment.SIBIR)) {
			pun = Punishment.FINE;
		}
		Case c = new Case(p.getUUID(), pun);
		CASES.put(p.getUUID(), c);
		Msg.title(p, Msg.t("СТОЯТЬ! МИЛИЦИЯ!", ChatFormatting.AQUA, ChatFormatting.BOLD), Msg.t(reason, ChatFormatting.WHITE));
		Msg.sound(p, SoundEvents.RAID_HORN.value(), 0.6f, 1.6f);
		Plaque.all(s, Plaque.Kind.POLICE, p.getName().getString() + " арестован: " + reason + ". Приговор: " + pun.title, 8);
		// милиционер появляется рядом
		ServerLevel l = Msg.level(p);
		Entity cop = Npcs.spawnCop(l, p.position().add(1.2, 0, 0.8), p);
		if (cop != null) c.extras.add(cop);
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 10, false, false));
		final Punishment fp = pun;
		Scheduler.later(50, () -> {
			for (Entity e : c.extras) e.discard();
			c.extras.clear();
			if (p.isRemoved()) {
				CASES.remove(p.getUUID());
				return;
			}
			start(p, c, fp);
		});
	}

	private static Punishment pick() {
		Punishment[] w = {Punishment.PRISON, Punishment.PRISON, Punishment.PILLORY, Punishment.PILLORY, Punishment.KOLKHOZ,
				Punishment.FINE, Punishment.CONFISCATION, Punishment.LECTURE, Punishment.SIBIR};
		return w[CommunismMod.RNG.nextInt(w.length)];
	}

	private static void start(ServerPlayer p, Case c, Punishment pun) {
		MinecraftServer s = CommunismMod.server;
		ServerLevel ow = s.overworld();
		switch (pun) {
			case PRISON -> {
				c.left = 60;
				rideToPrison(p, c, ow);
			}
			case PILLORY -> {
				c.left = 45;
				List<Vec3> path = City.railPath();
				startRide(p, c, ow, path, City.PILLORY_STOP, () -> {
					Vec3 pin = City.at(City.PILLORY[0] + 0.5, 1, City.PILLORY[1] + 0.5);
					c.pin = pin;
					c.radius = 0.6;
					teleport(p, ow, pin.x, pin.y, pin.z, 180f);
					spawnThrowers(ow, c);
					Plaque.show(p, Plaque.Kind.BAD, "Вы у позорного столба! Граждане выражают возмущение...", 8);
					Plaque.all(s, Plaque.Kind.POLICE, "На Площади позора стоит " + p.getName().getString() + "! Приходите посмотреть.", 8);
				});
			}
			case KOLKHOZ -> {
				c.left = 180;
				Vec3 pin = City.at(City.KOLKHOZ[0] + 0.5, 0, City.KOLKHOZ[1] + 0.5);
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
				teleport(p, ow, pin.x, pin.y, pin.z, -90f);
				c.pin = pin;
				c.radius = 9;
				Msg.give(p, Goods.named(Items.IRON_HOE, 1, "Казённая мотыга", ChatFormatting.GRAY, "Вернуть после работ."));
				Plaque.show(p, Plaque.Kind.BAD, "Исправительные работы: соберите 12 спелых растений на колхозе (0/12).", 10);
			}
			case SIBIR -> {
				c.left = 45;
				Vec3 pin = City.at(Structures.SIBIR_DX + 0.5, 0, Structures.SIBIR_DZ + 0.5);
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 45 * 20, 3));
				teleport(p, ow, pin.x, pin.y, pin.z, 0f);
				c.pin = pin;
				c.radius = 1.5;
				Msg.title(p, Msg.t("ССЫЛКА В СИБИРЬ", ChatFormatting.AQUA, ChatFormatting.BOLD), Msg.t("Холодно? Это воспитательный момент.", ChatFormatting.WHITE));
			}
			case FINE -> {
				int have = Ranks.trud(p);
				int fine = Math.min(have, Math.max(5, have / 3));
				Ranks.spend(p, fine);
				Plaque.show(p, Plaque.Kind.MONEY, "Штраф: −" + fine + " трудодней. Квитанцию не выдаём.", 8);
				CASES.remove(p.getUUID());
			}
			case CONFISCATION -> {
				Inventory inv = p.getInventory();
				List<Integer> slots = new ArrayList<>();
				for (int i = 0; i < 36; i++) if (!inv.getItem(i).isEmpty()) slots.add(i);
				if (slots.isEmpty()) {
					Plaque.show(p, Plaque.Kind.BAD, "Конфисковать нечего. Милиция разочарована и выписала выговор.", 8);
					CASES.remove(p.getUUID());
					Data.add(p, Data.vyg, 1);
				} else {
					int slot = slots.get(CommunismMod.RNG.nextInt(slots.size()));
					ItemStack st = inv.getItem(slot).copy();
					inv.setItem(slot, ItemStack.EMPTY);
					Warehouse.store(st);
					Plaque.show(p, Plaque.Kind.BAD, "Конфисковано: " + st.getCount() + "× " + st.getHoverName().getString() + " (передано на Склад Народа).", 8);
					CASES.remove(p.getUUID());
				}
			}
			case LECTURE -> {
				c.left = 36;
				c.pin = p.position();
				c.radius = 0.8;
				lecture(p, 0);
			}
		}
	}

	private static void rideToPrison(ServerPlayer p, Case c, ServerLevel ow) {
		startRide(p, c, ow, City.railPath(), 1e9, () -> {
			int cell = freeCell();
			c.cell = cell;
			int[] cc = City.CELLS[cell];
			Vec3 pin = City.at(cc[0] + 0.5, 0, cc[1] + 0.5);
			c.pin = pin;
			c.radius = 1.6;
			teleport(p, ow, pin.x, pin.y, pin.z, 180f);
			Msg.give(p, Goods.named(Items.WOODEN_PICKAXE, 1, "Казённая кирка", ChatFormatting.GRAY, "Для исправительных работ."));
			Plaque.show(p, Plaque.Kind.BAD, "Камера №" + (cell + 1) + ". Срок " + c.left + " сек. Долбите камень в камере — каждый блок −8 сек.", 10);
			Msg.title(p, Msg.t("ТЮРЬМА «СВЕТЛЫЙ ПУТЬ»", ChatFormatting.GRAY, ChatFormatting.BOLD), Msg.t("Добро пожаловать. Снова.", ChatFormatting.WHITE));
		});
	}

	private static int freeCell() {
		boolean[] used = new boolean[City.CELLS.length];
		for (Case c : CASES.values()) if (c.cell >= 0) used[c.cell] = true;
		for (int i = 0; i < used.length; i++) if (!used[i]) return i;
		return CommunismMod.RNG.nextInt(used.length);
	}

	private static void release(ServerPlayer p, Case c, String why) {
		MinecraftServer s = CommunismMod.server;
		for (Entity e : c.extras) e.discard();
		c.extras.clear();
		removeKazenny(p);
		ServerLevel ow = s.overworld();
		if (c.p == Punishment.PRISON || c.p == Punishment.PILLORY) {
			c.pin = null;
			List<Vec3> back = new ArrayList<>(City.railPath());
			java.util.Collections.reverse(back);
			double start = c.p == Punishment.PILLORY ? length(back) - City.PILLORY_STOP : 0;
			if (c.p == Punishment.PILLORY) back = cut(back, start);
			Plaque.show(p, Plaque.Kind.GOOD, why + " Вас везут обратно на станцию.", 6);
			startRide(p, c, ow, back, 1e9, () -> finish(p, ow));
		} else if (c.p == Punishment.LECTURE) {
			CASES.remove(p.getUUID());
			Ui.close(p);
			Plaque.show(p, Plaque.Kind.GOOD, why + " Лекция окончена, вы свободны.", 6);
		} else {
			finish(p, ow);
			Plaque.show(p, Plaque.Kind.GOOD, why, 6);
		}
	}

	private static void finish(ServerPlayer p, ServerLevel ow) {
		CASES.remove(p.getUUID());
		if (City.built() && !p.isRemoved()) {
			Vec3 out = City.at(City.STATION_EXIT[0] + 0.5, 0, City.STATION_EXIT[1] + 0.5);
			teleport(p, ow, out.x, out.y, out.z, 180f);
		}
		Plaque.show(p, Plaque.Kind.GOOD, "Вы на свободе. Больше не нарушайте, товарищ!", 6);
	}

	private static void removeKazenny(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String n = Goods.nameOf(inv.getItem(i));
			if (n.startsWith("Казённ")) inv.setItem(i, ItemStack.EMPTY);
		}
	}

	/** Досрочное освобождение (УДО, амнистия, помилование). */
	public static boolean pardon(ServerPlayer p, String why) {
		Case c = CASES.get(p.getUUID());
		if (c == null || c.riding) return false;
		release(p, c, why);
		return true;
	}

	public static int pardonAll(MinecraftServer s, String why) {
		int n = 0;
		for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) if (pardon(p, why)) n++;
		return n;
	}

	public static int sentenceLeft(ServerPlayer p) {
		Case c = CASES.get(p.getUUID());
		return c == null ? 0 : c.left;
	}

	public static Punishment punishment(ServerPlayer p) {
		Case c = CASES.get(p.getUUID());
		return c == null ? null : c.p;
	}

	// ------------------------------------------------------------------ события

	/** Можно ли сломать блок в городе (true — разрешить). */
	public static boolean allowBreakInCity(ServerPlayer p, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		Case c = CASES.get(p.getUUID());
		if (c != null && c.p == Punishment.PRISON && c.pin != null && state.is(Blocks.COBBLESTONE)
				&& pos.distToCenterSqr(c.pin) < 9) {
			c.left = Math.max(1, c.left - 8);
			Plaque.show(p, Plaque.Kind.INFO, "Исправительный труд засчитан: −8 сек. Осталось " + c.left + " сек.", 4);
			BlockPos bp = pos.immutable();
			ServerLevel l = Msg.level(p);
			Scheduler.later(200, () -> l.setBlock(bp, Blocks.COBBLESTONE.defaultBlockState(), 3));
			return true;
		}
		if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
			if (c != null && c.p == Punishment.KOLKHOZ && ((net.minecraft.world.level.block.CropBlock) state.getBlock()).isMaxAge(state)) {
				c.harvested++;
				Plaque.show(p, Plaque.Kind.INFO, "Исправительные работы: " + c.harvested + "/12", 3);
				if (c.harvested >= 12) Scheduler.later(5, () -> release(p, c, "Норма выполнена!"));
			}
			return true;
		}
		return state.canBeReplaced() || state.is(Blocks.SNOW) || state.is(net.minecraft.tags.BlockTags.FLOWERS)
				|| state.is(net.minecraft.tags.BlockTags.LEAVES) || p.isCreative();
	}

	static void secondTick(MinecraftServer s) {
		for (Map.Entry<UUID, Case> e : new ArrayList<>(CASES.entrySet())) {
			Case c = e.getValue();
			ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
			if (p == null) continue;
			if (c.riding || c.pin == null) continue;
			c.left--;
			switch (c.p) {
				case PRISON -> Msg.actionbar(p, Msg.t("Тюрьма: осталось " + c.left + " сек. (долбите булыжник — быстрее)", ChatFormatting.GRAY));
				case PILLORY -> Msg.actionbar(p, Msg.t("Позорный столб: осталось " + c.left + " сек.", ChatFormatting.LIGHT_PURPLE));
				case KOLKHOZ -> Msg.actionbar(p, Msg.t("Колхоз: собрано " + c.harvested + "/12, осталось " + c.left + " сек.", ChatFormatting.GREEN));
				case SIBIR -> {
					p.setTicksFrozen(Math.min(130, p.getTicksFrozen() + 40));
					Msg.actionbar(p, Msg.t("Сибирь: осталось " + c.left + " сек.", ChatFormatting.AQUA));
				}
				case LECTURE -> {
					if (c.left % 6 == 0 && c.left > 0) lecture(p, (36 - c.left) / 6);
				}
				default -> { }
			}
			if (c.left <= 0) {
				if (c.p == Punishment.KOLKHOZ && c.harvested < 12) {
					Data.add(p, Data.vyg, 1);
					release(p, c, "Норма не выполнена — плюс выговор. Но вас отпускают.");
				} else {
					release(p, c, "Срок окончен.");
				}
			}
		}
	}

	private static int throwTick = 0;

	static void tick(MinecraftServer s) {
		// поездки
		Iterator<Ride> it = RIDES.iterator();
		List<Runnable> arrived = new ArrayList<>();
		while (it.hasNext()) {
			Ride r = it.next();
			ServerPlayer p = s.getPlayerList().getPlayer(r.player);
			if (p == null || r.cart.isRemoved()) {
				cleanupRide(r);
				it.remove();
				CASES.remove(r.player);
				continue;
			}
			r.dist = Math.min(r.stop, r.dist + 0.32);
			Vec3 pos = point(r.path, r.dist);
			Vec3 dir = point(r.path, Math.min(r.total, r.dist + 0.5)).subtract(pos);
			float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)));
			r.cart.setPos(pos.x, pos.y, pos.z);
			r.cart.setDeltaMovement(Vec3.ZERO);
			r.cart.setYRot(yaw);
			Vec3 ppos = point(r.path, Math.min(r.total, r.dist + 1.6));
			r.policeCart.setPos(ppos.x, ppos.y, ppos.z);
			r.policeCart.setDeltaMovement(Vec3.ZERO);
			r.policeCart.setYRot(yaw);
			if (p.getVehicle() != r.cart) p.startRiding(r.cart, true, true);
			if (CommunismMod.tick % 10 == 0) Msg.actionbar(p, Msg.t("Вас везёт милиция... Не высовывайтесь из вагонетки.", ChatFormatting.AQUA));
			if (r.dist >= r.stop) {
				p.stopRiding();
				cleanupRide(r);
				it.remove();
				Case c = CASES.get(r.player);
				if (c != null) c.riding = false;
				arrived.add(r.onArrive);
			}
		}
		for (Runnable a : arrived) a.run();

		// удерживаем на месте
		for (Map.Entry<UUID, Case> e : CASES.entrySet()) {
			Case c = e.getValue();
			if (c.pin == null || c.riding) continue;
			ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
			if (p == null) continue;
			if (p.level() != s.overworld() || p.position().distanceToSqr(c.pin) > c.radius * c.radius) {
				teleport(p, s.overworld(), c.pin.x, c.pin.y, c.pin.z, p.getYRot());
				if (c.p == Punishment.PRISON) {
					c.left += 10;
					Plaque.show(p, Plaque.Kind.BAD, "Попытка побега! +10 секунд к сроку.", 4);
				}
			}
		}

		// граждане кидаются в стоящих у столба
		if (++throwTick % 12 == 0) {
			for (Map.Entry<UUID, Case> e : CASES.entrySet()) {
				Case c = e.getValue();
				if (c.p != Punishment.PILLORY || c.pin == null || c.extras.isEmpty()) continue;
				ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
				if (p == null) continue;
				Entity thrower = c.extras.get(CommunismMod.RNG.nextInt(c.extras.size()));
				throwAt((ServerLevel) thrower.level(), thrower, p);
			}
		}
	}

	private static final String[] JEERS = {"Позор!", "Тунеядец!", "Вот так становятся буржуями!", "А ещё в шапке!", "Мой внук так не делает!",
			"Помидоры по талонам, а на тебя не жалко!", "Стыдно, товарищ!", "Сломал — плати!"};

	private static void throwAt(ServerLevel l, Entity from, ServerPlayer target) {
		boolean tomato = target == null || CommunismMod.RNG.nextBoolean();
		var ball = EntityType.SNOWBALL.create(l, EntitySpawnReason.EVENT);
		if (ball == null) return;
		ball.setItem(new ItemStack(tomato ? Items.BEETROOT : Items.SNOWBALL));
		Vec3 a = from.position().add(0, 1.5, 0);
		ball.setPos(a.x, a.y, a.z);
		ball.setOwner(from);
		Vec3 d = (target == null ? from.position().add(0, 0, 4) : target.position().add(0, 1.2, 0)).subtract(a);
		ball.shoot(d.x, d.y + d.horizontalDistance() * 0.12, d.z, 1.1f, 3f);
		l.addFreshEntity(ball);
		l.playSound(null, a.x, a.y, a.z, SoundEvents.SNOWBALL_THROW, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 1f);
		if (target != null && CommunismMod.RNG.nextInt(3) == 0) {
			Plaque.show(target, tomato ? Plaque.Kind.BAD : Plaque.Kind.INFO,
					(tomato ? "В вас прилетел помидор! " : "Снежок в лицо! ") + "«" + JEERS[CommunismMod.RNG.nextInt(JEERS.length)] + "»", 3);
			if (tomato) target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
		}
	}

	private static void spawnThrowers(ServerLevel l, Case c) {
		for (int i = 0; i < 5; i++) {
			double a = Math.PI * 2 * i / 5;
			Vec3 pos = c.pin.add(Math.cos(a) * 5, -1, Math.sin(a) * 5);
			Entity e = Npcs.spawnCitizen(l, pos, c.pin);
			if (e != null) c.extras.add(e);
		}
	}

	private static final String[][] LECTURE = {
			{"Лекция. Часть 1: Что такое капитализм", "Капитализм — это когда у одного всё, а у других ничего. Как у вас в инвентаре до вступления в Партию."},
			{"Часть 2: Почему криперы — капиталисты", "Крипер подходит тихо, взрывает чужое имущество и исчезает. Типичный рейдер с Уолл-стрит."},
			{"Часть 3: Тайна золотого слитка", "Золото не нужно трудящимся. Им нельзя копать, им нельзя рубить, оно мягкое. Как совесть буржуя."},
			{"Часть 4: Трудодень как высшая ценность", "Один трудодень = одна радость. Десять трудодней = колбаса. Это и есть счастье."},
			{"Часть 5: Вопросы из зала", "Вопросов нет. Вопросы — это сомнения. Сомнения — это выговор."},
			{"Заключение", "Лекция окончена. Распишитесь в журнале посещаемости. Нет ручки? Распишитесь мыслями."}};

	private static void lecture(ServerPlayer p, int part) {
		String[] l = LECTURE[Math.min(part, LECTURE.length - 1)];
		Ui.win("Лекция о вреде капитализма", ChatFormatting.DARK_RED)
				.icon(new ItemStack(Items.BOOK), Component.literal(l[0]).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.text(l[1], ChatFormatting.WHITE)
				.text("Окно будет открываться, пока лекция не закончится (" + Math.max(0, sentenceLeft(p)) + " сек).", ChatFormatting.GRAY)
				.exit("Я всё понял, товарищ лектор")
				.open(p);
	}

	// ------------------------------------------------------------------ вагонетки

	private static void startRide(ServerPlayer p, Case c, ServerLevel ow, List<Vec3> path, double stop, Runnable onArrive) {
		var cart = EntityType.MINECART.create(ow, EntitySpawnReason.EVENT);
		var pcart = EntityType.MINECART.create(ow, EntitySpawnReason.EVENT);
		if (cart == null || pcart == null) {
			onArrive.run();
			return;
		}
		Vec3 a = path.get(0);
		Vec3 b = point(path, 1.6);
		cart.setPos(a.x, a.y, a.z);
		pcart.setPos(b.x, b.y, b.z);
		cart.setNoGravity(true);
		pcart.setNoGravity(true);
		cart.setCustomName(Component.literal("Чёрный воронок").withStyle(ChatFormatting.DARK_GRAY));
		ow.addFreshEntity(cart);
		ow.addFreshEntity(pcart);
		Entity cop = Npcs.spawnCop(ow, b, null);
		if (cop != null) cop.startRiding(pcart, true, true);
		p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
		teleport(p, ow, a.x, a.y + 0.2, a.z, 0f);
		p.startRiding(cart, true, true);
		c.riding = true;
		RIDES.add(new Ride(p.getUUID(), cart, pcart, cop, path, stop, onArrive));
	}

	private static void cleanupRide(Ride r) {
		if (r.police != null) r.police.discard();
		r.policeCart.discard();
		r.cart.ejectPassengers();
		r.cart.discard();
	}

	static double length(List<Vec3> path) {
		double d = 0;
		for (int i = 1; i < path.size(); i++) d += path.get(i).distanceTo(path.get(i - 1));
		return d;
	}

	static Vec3 point(List<Vec3> path, double dist) {
		for (int i = 1; i < path.size(); i++) {
			double seg = path.get(i).distanceTo(path.get(i - 1));
			if (dist <= seg) return path.get(i - 1).lerp(path.get(i), seg == 0 ? 0 : dist / seg);
			dist -= seg;
		}
		return path.get(path.size() - 1);
	}

	/** Отрезать начало пути. */
	static List<Vec3> cut(List<Vec3> path, double from) {
		List<Vec3> out = new ArrayList<>();
		out.add(point(path, from));
		double acc = 0;
		for (int i = 1; i < path.size(); i++) {
			acc += path.get(i).distanceTo(path.get(i - 1));
			if (acc > from) out.add(path.get(i));
		}
		return out;
	}

	public static void teleport(ServerPlayer p, ServerLevel l, double x, double y, double z, float yaw) {
		p.teleportTo(l, x, y, z, Set.<Relative>of(), yaw, p.getXRot(), true);
		p.resetFallDistance();
	}

	static void onLeave(ServerPlayer p) {
		Iterator<Ride> it = RIDES.iterator();
		while (it.hasNext()) {
			Ride r = it.next();
			if (r.player.equals(p.getUUID())) {
				cleanupRide(r);
				it.remove();
			}
		}
		Case c = CASES.remove(p.getUUID());
		if (c != null) for (Entity e : c.extras) e.discard();
	}

	/** Для автотеста: создать и убрать вагонетки, милиционера, снежок-помидор. */
	static void selfTest(ServerLevel l, int y) {
		List<Vec3> path = City.railPath();
		var cart = EntityType.MINECART.create(l, EntitySpawnReason.EVENT);
		var pcart = EntityType.MINECART.create(l, EntitySpawnReason.EVENT);
		Vec3 a = point(path, 5);
		cart.setPos(a.x, a.y, a.z);
		pcart.setPos(a.x, a.y, a.z + 2);
		l.addFreshEntity(cart);
		l.addFreshEntity(pcart);
		Entity cop = Npcs.spawnCop(l, a, null);
		cop.startRiding(pcart, true, true);
		Entity cit = Npcs.spawnCitizen(l, a.add(3, 0, 0), a);
		throwAt(l, cit, null);
		Scheduler.later(40, () -> {
			cop.discard();
			cit.discard();
			cart.discard();
			pcart.discard();
		});
	}

	static void reset() {
		for (Ride r : RIDES) cleanupRide(r);
		RIDES.clear();
		for (Case c : CASES.values()) for (Entity e : c.extras) e.discard();
		CASES.clear();
		WARN.clear();
		LAST_VYG.clear();
	}
}
