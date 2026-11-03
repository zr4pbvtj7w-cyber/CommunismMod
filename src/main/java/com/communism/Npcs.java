package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NPC Красной площади, буржуи и разговоры с ними. */
public final class Npcs {
	public static final String TAG = "cm_npc";
	public static final String BURZHUY = "cm_burzhuy";

	public static final String DICTATOR = "Генсек Усач";
	public static final String ZAVHOZ = "Завхоз Петрович";
	public static final String POLITRUK = "Политрук Задорный";
	public static final String AGRONOM = "Агроном Колосков";
	public static final String KOSMONAVT = "Космонавт Звёздочкин";
	public static final String PIONER = "Пионер Вася";

	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	private static final Map<String, Long> GIFTS = new HashMap<>();
	/** Что украли буржуи (UUID буржуя → вещи). */
	static final Map<UUID, List<ItemStack>> STOLEN = new HashMap<>();

	public record Role(String id, String name, String profession, ChatFormatting color, boolean baby) {}

	public static final Role[] ROLES = {
			new Role("dictator", DICTATOR, "cleric", ChatFormatting.RED, false),
			new Role("politruk", POLITRUK, "librarian", ChatFormatting.GOLD, false),
			new Role("kgb", Discipline.KGB + " (КГБ)", "nitwit", ChatFormatting.DARK_GRAY, false),
			new Role("zavhoz", ZAVHOZ, "armorer", ChatFormatting.YELLOW, false),
			new Role("agronom", AGRONOM, "farmer", ChatFormatting.GREEN, false),
			new Role("kosmonavt", KOSMONAVT, "cartographer", ChatFormatting.AQUA, false),
			new Role("pioner", PIONER, "none", ChatFormatting.RED, true),
	};

	// ------------------------------------------------------------ spawning

	public static void spawnAll(ServerLevel l, int cx, int cy, int cz) {
		removeAll(l);
		spawn(l, ROLES[0], cx - 11 + 0.5, cy + 1, cz - 14 + 0.5, 0f);
		spawn(l, ROLES[1], cx + 9 + 0.5, cy + 1, cz - 14 + 0.5, 0f);
		spawn(l, ROLES[2], cx + 5 + 0.5, cy, cz - 19 + 0.5, 0f);
		spawn(l, ROLES[3], cx - 21 + 0.5, cy, cz + 22 + 0.5, 180f);
		spawn(l, ROLES[4], cx - 26 + 0.5, cy, cz + 10 + 0.5, -90f);
		spawn(l, ROLES[5], cx + 30 + 0.5, cy, cz - 28 + 0.5, 0f);
		spawn(l, ROLES[6], cx + 21 + 0.5, cy, cz + 2 + 0.5, 90f);
	}

	private static void removeAll(ServerLevel l) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
		for (Entity e : old) e.discard();
	}

	private static void spawn(ServerLevel l, Role r, double x, double y, double z, float yaw) {
		var v = EntityType.VILLAGER.create(l, EntitySpawnReason.COMMAND);
		if (v == null) return;
		v.setPos(x, y, z);
		v.setYRot(yaw);
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
		v.setNoAi(true);
		v.setInvulnerable(true);
		v.setPersistenceRequired();
		v.setCustomName(Component.literal(r.name()).withStyle(r.color(), ChatFormatting.BOLD));
		v.setCustomNameVisible(true);
		v.addTag(TAG);
		v.addTag("cm_role_" + r.id());
		var reg = l.registryAccess();
		var prof = reg.lookupOrThrow(Registries.VILLAGER_PROFESSION)
				.getOrThrow(ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace(r.profession())));
		var type = reg.lookupOrThrow(Registries.VILLAGER_TYPE)
				.getOrThrow(ResourceKey.create(Registries.VILLAGER_TYPE, Identifier.withDefaultNamespace("snow")));
		v.setVillagerData(v.getVillagerData().withProfession(prof).withType(type).withLevel(5));
		v.setVillagerXp(1);
		if (r.baby()) v.setAge(-1_000_000_000);
		if (r.id().equals("dictator")) v.setGlowingTag(true);
		l.addFreshEntity(v);
	}

	public static void spawnBurzhuy(ServerLevel l, BlockPos pos) {
		var b = EntityType.VINDICATOR.create(l, EntitySpawnReason.EVENT);
		if (b == null) return;
		b.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		String[] names = {"Буржуй", "Капиталист", "Олигарх", "Кулак", "Спекулянт", "Акционер"};
		b.setCustomName(Component.literal(names[CommunismMod.RNG.nextInt(names.length)]).withStyle(ChatFormatting.GOLD));
		b.setCustomNameVisible(true);
		b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
		b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
		b.addTag(BURZHUY);
		l.addFreshEntity(b);
	}

	/** Буржуй ударил игрока — ворует вещь. */
	public static void steal(ServerPlayer p, Entity burzhuy) {
		var inv = p.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) if (!inv.getItem(i).isEmpty()) slots.add(i);
		if (slots.isEmpty()) return;
		int slot = slots.get(CommunismMod.RNG.nextInt(slots.size()));
		ItemStack st = inv.getItem(slot).split(Math.min(8, inv.getItem(slot).getCount()));
		STOLEN.computeIfAbsent(burzhuy.getUUID(), k -> new ArrayList<>()).add(st);
		Msg.actionbar(p, Msg.t("Буржуй украл у вас: " + st.getCount() + "× " + st.getHoverName().getString() + "! Убейте его, чтобы вернуть!", ChatFormatting.GOLD));
	}

	public static void onBurzhuyDeath(LivingEntity e, ServerPlayer killer) {
		ServerLevel l = (ServerLevel) e.level();
		List<ItemStack> loot = STOLEN.remove(e.getUUID());
		if (loot != null) for (ItemStack st : loot) Msg.drop(l, e.getX(), e.getY() + 0.5, e.getZ(), st);
		Msg.drop(l, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(Items.GOLD_NUGGET, 2 + CommunismMod.RNG.nextInt(5)));
		if (CommunismMod.RNG.nextInt(4) == 0) Msg.drop(l, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(Items.EMERALD, 1));
		if (killer != null) Ranks.addTrud(killer, 5, "раскулачивание");
	}

	// ------------------------------------------------------------ interaction

	public static boolean debounce(ServerPlayer p) {
		Long last = COOLDOWN.get(p.getUUID());
		if (last != null && CommunismMod.tick - last < 5) return false;
		COOLDOWN.put(p.getUUID(), CommunismMod.tick);
		return true;
	}

	public static String roleOf(Entity e) {
		for (String t : e.getTags()) if (t.startsWith("cm_role_")) return t.substring(8);
		return "";
	}

	public static void interact(ServerPlayer p, Entity npc) {
		switch (roleOf(npc)) {
			case "dictator" -> dictator(p);
			case "politruk" -> Quests.talk(p);
			case "kgb" -> kgb(p);
			case "zavhoz" -> Shop.show(p);
			case "agronom" -> agronom(p);
			case "kosmonavt" -> kosmonavt(p);
			case "pioner" -> pioner(p);
			default -> { }
		}
	}

	private static boolean giftReady(ServerPlayer p, String kind, int seconds) {
		String k = p.getUUID() + kind;
		Long last = GIFTS.get(k);
		if (last != null && CommunismMod.tick - last < seconds * 20L) return false;
		GIFTS.put(k, CommunismMod.tick);
		return true;
	}

	private static int giftLeft(ServerPlayer p, String kind, int seconds) {
		Long last = GIFTS.get(p.getUUID() + kind);
		if (last == null) return 0;
		return (int) Math.max(0, seconds - (CommunismMod.tick - last) / 20);
	}

	public static final String[] QUOTES = {
			"Учиться, учиться и ещё раз копать!",
			"Кто не работает, тот не крафтит.",
			"Пятилетку — за три года, а алмазы — за три минуты!",
			"Крипер — это капиталист, который взорвался от зависти.",
			"Свобода — это осознанная необходимость сдать мне пшеницу.",
			"Нет такой крепости, которую не взяла бы деревянная кирка большевика!",
			"Товарищи, Эндермены — это шпионы. Не смотрите им в глаза. Вообще никому не смотрите.",
			"Жить стало лучше, жить стало веселее! Особенно мне.",
			"Мы наш, мы новый мир построим. Из булыжника.",
			"Каждой семье — по квартире в хрущёвке! Каждому криперу — по выговору!",
			"Незер — это ад капитализма. Но огненные стержни оттуда нам нужны.",
			"Жители деревень торгуют изумрудами? Спекулянты! Раскулачить!",
			"Мой усы — гордость нации. Не спрашивайте, почему 'мой'.",
			"Ночью спят только враги пятилетки. И то в 'Законе о трудовой дисциплине' это запрещено.",
			"Слава труду! И немножко мне."};

	private static void dictator(ServerPlayer p) {
		if (Decrees.handInForm(p)) return;
		int st = Plan.stage();
		if (st < Plan.BOSS) {
			if (Plan.contribute(p, p.isShiftKeyDown())) return;
			Msg.say(p, DICTATOR, QUOTES[CommunismMod.RNG.nextInt(QUOTES.length)]);
			p.sendSystemMessage(Plan.statusText());
			return;
		}
		if (st == Plan.BOSS) {
			if (Boss.alive()) {
				Msg.say(p, DICTATOR, "Чего стоите?! Мистер Капитал здесь! В бой, товарищи!");
			} else {
				Msg.say(p, DICTATOR, "Вы готовы, товарищи? Тогда — В ПОСЛЕДНИЙ И РЕШИТЕЛЬНЫЙ БОЙ!");
				Boss.summon(CommunismMod.server);
			}
			return;
		}
		// победа: каждому по потребностям
		if (giftReady(p, "need", 300)) {
			ItemStack[] needs = {new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.EMERALD, 8),
					new ItemStack(Items.NETHERITE_SCRAP, 1), new ItemStack(Items.EXPERIENCE_BOTTLE, 16), Goods.sausage(16),
					new ItemStack(Items.ENDER_PEARL, 4), new ItemStack(Items.IRON_BLOCK, 4)};
			ItemStack g = needs[CommunismMod.RNG.nextInt(needs.length)].copy();
			Msg.say(p, DICTATOR, "При коммунизме каждому — по потребностям! Ваша потребность сегодня: " + g.getCount() + "× " + g.getHoverName().getString() + ".");
			Msg.give(p, g);
		} else {
			Msg.say(p, DICTATOR, "Ваши потребности будут удовлетворены через " + Msg.time(giftLeft(p, "need", 300)) + ". Коммунизм — не значит без очереди!");
		}
	}

	private static void kgb(ServerPlayer p) {
		int v = Data.get(p, Data.vyg);
		if (p.isShiftKeyDown() && v > 0) {
			if (Ranks.spend(p, 10)) {
				Data.set(p, Data.vyg, v - 1);
				Msg.say(p, Discipline.KGB, "За 10 трудодней... ваше дело случайно потерялось. Выговоров теперь: " + (v - 1) + ". Мы друг друга не видели.");
			} else {
				Msg.say(p, Discipline.KGB, "Взятка? Маловато. Нужно 10 трудодней.");
			}
			return;
		}
		String[] jokes = {
				"Я за вами не слежу. Просто стою тут. Каждый день. С блокнотом.",
				"Поступил донос, что кто-то копает прямо вниз. Это вы? Не отвечайте, я знаю.",
				"Если увидите крипера — доложите. Он не наш.",
				"Ваш сосед по хрущёвке говорит, что вы храните золото. Это правда? Шучу. Пока что.",
				"Слышали анекдот про Генсека? Нет? И правильно.",
				"Знаете, что общего у вас и алмаза? Мы найдём вас обоих."};
		Msg.say(p, Discipline.KGB, jokes[CommunismMod.RNG.nextInt(jokes.length)]);
		Msg.say(p, Discipline.KGB, "У вас выговоров: " + v + "/3. При трёх — Сибирь." + (v > 0 ? " (Shift+ПКМ — «потерять» дело за 10 трудодней)" : ""));
		List<ServerPlayer> ps = CommunismMod.server.getPlayerList().getPlayers();
		if (ps.size() > 1) {
			ServerPlayer lazy = null;
			int min = Integer.MAX_VALUE;
			for (ServerPlayer o : ps) {
				int s = Data.get(o, Data.stazh);
				if (s < min) {
					min = s;
					lazy = o;
				}
			}
			if (lazy != null) Msg.say(p, Discipline.KGB, "По моим данным, самый ленивый товарищ — " + lazy.getName().getString() + " (стаж " + min + "). Присмотритесь к нему.");
		}
		Msg.say(p, Discipline.KGB, "Действующий закон: " + Laws.NAMES[Laws.current()] + ". " + Laws.DESCS[Laws.current()]);
	}

	private static void agronom(ServerPlayer p) {
		if (giftReady(p, "seeds", 600)) {
			Msg.say(p, AGRONOM, "Держите посевной набор, товарищ! Сажайте всё, даже кукурузу. Особенно кукурузу.");
			Msg.give(p, new ItemStack(Items.WHEAT_SEEDS, 16));
			Msg.give(p, new ItemStack(Items.POTATO, 6));
			Msg.give(p, new ItemStack(Items.CARROT, 6));
			Msg.give(p, new ItemStack(Items.BONE_MEAL, 12));
			Msg.give(p, Goods.corn(2));
		} else {
			Msg.say(p, AGRONOM, "Следующий набор семян — через " + Msg.time(giftLeft(p, "seeds", 600)) + ". Пока поливайте то, что есть.");
			Msg.say(p, AGRONOM, "Совет: урожай колхоза идёт в Год 1 пятилетки. Костная мука ускоряет рост, как пропаганда — энтузиазм.");
		}
	}

	private static void kosmonavt(ServerPlayer p) {
		boolean free = Plan.stage() >= 4;
		int price = free ? 0 : 5;
		if (!p.isShiftKeyDown()) {
			Msg.say(p, KOSMONAVT, "Хотите полететь в неизведанные земли? Ракета «Восток» доставит вас за 500-900 блоков отсюда — ищите там ресурсы!");
			Msg.say(p, KOSMONAVT, "Цена: " + (free ? "бесплатно (космическая программа выполнена!)" : price + " трудодней") + ". Shift+ПКМ — на старт!");
			return;
		}
		if (!Ranks.spend(p, price)) {
			Msg.say(p, KOSMONAVT, "Не хватает трудодней на топливо. Нужно " + price + ".");
			return;
		}
		Msg.title(p, Msg.t("ПОЕХАЛИ!", ChatFormatting.AQUA, ChatFormatting.BOLD), Msg.t("Ключ на старт!", ChatFormatting.WHITE));
		Msg.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
		p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 15));
		Scheduler.later(60, () -> {
			if (p.isRemoved()) return;
			ServerLevel ow = CommunismMod.server.overworld();
			double a = CommunismMod.RNG.nextDouble() * Math.PI * 2;
			int d = 500 + CommunismMod.RNG.nextInt(400);
			int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
			int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 50;
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1600, 0));
			Discipline.teleport(p, ow, x + 0.5, y, z + 0.5);
			Msg.info(p, "Приземление в точке " + x + " " + z + ". Космонавт, удачной разведки! Обратно — пешком (или /komm metro за 3 трудодня).");
		});
	}

	private static void pioner(ServerPlayer p) {
		String[] tips = {
				"Генсек принимает ресурсы для Пятилетки. Shift+ПКМ по нему — сдать сразу всё!",
				"Каждые 10 минут — новый Указ Партии. Таймер — в полоске сверху!",
				"Политрук даёт задания за трудодни. А трудодни можно потратить у Завхоза!",
				"3 выговора — и ты в Сибири! Не бей партийных работников!",
				"Космонавт может закинуть тебя далеко-далеко. Удобно искать алмазы!",
				"В хрущёвке справа — квартиры для всех. На каждом этаже есть кровати!",
				"На заводе есть печки, наковальня и всё для работы.",
				"Буржуи воруют вещи! Убей буржуя — и вещи выпадут обратно.",
				"В Мавзолее что-то лежит... Сходи посмотри!",
				"Ранги: Пионер → Комсомолец → Член Партии → Ударник → Герой Труда → Маршал Коммунизма!",
				"Команда /komm — вся информация. /komm metro — поездка на Красную площадь."};
		Msg.say(p, PIONER, tips[CommunismMod.RNG.nextInt(tips.length)]);
		if (giftReady(p, "pioner", 300)) {
			ItemStack[] gifts = {new ItemStack(Items.APPLE, 3), new ItemStack(Items.COOKIE, 6), Goods.bread(2), new ItemStack(Items.TORCH, 16)};
			ItemStack g = gifts[CommunismMod.RNG.nextInt(gifts.length)].copy();
			Msg.say(p, PIONER, "А ещё вот, держите! Я собрал макулатуру и получил " + g.getHoverName().getString() + "!");
			Msg.give(p, g);
		}
	}

	// ------------------------------------------------------------ периодически

	private static int sloganTimer = 0;

	static void secondTick(MinecraftServer s) {
		if (++sloganTimer >= 240) {
			sloganTimer = 0;
			Msg.sayAll(s, DICTATOR, QUOTES[CommunismMod.RNG.nextInt(QUOTES.length)]);
		}
	}

	public static Entity findNear(ServerPlayer p, String role, double radius) {
		ServerLevel l = Msg.level(p);
		List<Entity> list = l.getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e.getTags().contains("cm_role_" + role));
		return list.isEmpty() ? null : list.get(0);
	}

	static void reset() {
		COOLDOWN.clear();
		GIFTS.clear();
		STOLEN.clear();
		sloganTimer = 0;
	}
}
