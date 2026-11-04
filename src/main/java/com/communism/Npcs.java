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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Жители города, их окна разговоров, буржуи и милиция. */
public final class Npcs {
	public static final String TAG = "cm_npc";
	public static final String BURZHUY = "cm_burzhuy";
	public static final String TEMP = "cm_temp";

	public static final String GOSPLAN = "Председатель Госплана Цифиркин";
	public static final String ZAVHOZ = "Завхоз Петрович";
	public static final String POLITRUK = "Политрук Задорный";
	public static final String AGRONOM = "Агроном Колосков";
	public static final String KOSMONAVT = "Космонавт Звёздочкин";
	public static final String PIONER = "Пионер Вася";
	/** Совместимость со старым кодом: «Генсек» на трибуне теперь — Госплан. */
	public static final String DICTATOR = GOSPLAN;

	public record Role(String id, String name, String profession, ChatFormatting color, boolean baby, double x, double y, double z, float yaw, ItemStack portrait) {}

	public static final List<Role> ROLES = new ArrayList<>();

	private static void r(String id, String name, String prof, ChatFormatting c, boolean baby, double x, double y, double z, float yaw, ItemStack portrait) {
		ROLES.add(new Role(id, name, prof, c, baby, x, y, z, yaw, portrait));
	}

	static {
		r("vozhd", Leader.VOZHD, "cleric", ChatFormatting.DARK_RED, false, 0.5, 1, -55.5, 0f, new ItemStack(Items.GOLDEN_HELMET));
		r("gosplan", GOSPLAN, "cartographer", ChatFormatting.RED, false, -10.5, 1, -13.5, 0f, new ItemStack(Items.WRITABLE_BOOK));
		r("politruk", POLITRUK, "librarian", ChatFormatting.GOLD, false, 9.5, 1, -13.5, 0f, new ItemStack(Items.RED_BANNER));
		r("kgb", Justice.KGB + " (КГБ)", "nitwit", ChatFormatting.DARK_GRAY, false, 5.5, 0, -18.5, 0f, new ItemStack(Items.SPYGLASS));
		r("zavhoz", ZAVHOZ, "armorer", ChatFormatting.YELLOW, false, -20.5, 0, 22.5, 180f, new ItemStack(Items.CHEST));
		r("agronom", AGRONOM, "farmer", ChatFormatting.GREEN, false, -25.5, 0, 10.5, -90f, new ItemStack(Items.WHEAT));
		r("kosmonavt", KOSMONAVT, "cartographer", ChatFormatting.AQUA, false, 30.5, 0, -27.5, 0f, new ItemStack(Items.FIREWORK_ROCKET));
		r("pioner", PIONER, "none", ChatFormatting.RED, true, 22.5, 0, 3.5, 90f, new ItemStack(Items.RED_DYE));
		r("zina", "Баба Зина", "none", ChatFormatting.LIGHT_PURPLE, false, 22.5, 0, -6.5, 90f, new ItemStack(Items.SUNFLOWER));
		r("valya", "Баба Валя", "none", ChatFormatting.LIGHT_PURPLE, false, 22.5, 0, -2.5, 90f, new ItemStack(Items.POPPY));
		r("poet", "Поэт-агитатор Маяковкин", "librarian", ChatFormatting.WHITE, false, 5.5, 0, 11.5, 90f, new ItemStack(Items.FEATHER));
		r("bankir", Loans.BANKIR, "weaponsmith", ChatFormatting.GREEN, false, 43.5, 0, -1.5, 90f, new ItemStack(Items.GOLD_NUGGET));
		r("upgrader", Upgrader.NAME, "toolsmith", ChatFormatting.LIGHT_PURPLE, false, 43.5, 0, 8.5, 90f, new ItemStack(Items.ENDER_EYE));
		r("uchastkovy", "Участковый Анискин", "nitwit", ChatFormatting.AQUA, false, 30.5, 0, 14.5, 180f, new ItemStack(Items.IRON_BARS));
		r("tyuremshik", "Надзиратель Кузьмич", "mason", ChatFormatting.GRAY, false, 50.5, 0, 46.5, 0f, new ItemStack(Items.TRIPWIRE_HOOK));
		r("prodavshitsa", "Продавщица Галя", "butcher", ChatFormatting.YELLOW, false, -48.5, 0, -18.5, -90f, new ItemStack(Items.COOKED_PORKCHOP));
		r("doyarka", "Доярка Маруся", "shepherd", ChatFormatting.WHITE, false, -44.5, 0, 16.5, -90f, new ItemStack(Items.MILK_BUCKET));
		r("akademik", "Академик Колосов", "cleric", ChatFormatting.DARK_GREEN, false, -50.5, 0, 36.5, -90f, new ItemStack(Items.BREWING_STAND));
		for (int i = 0; i < 8; i++) {
			r("ochered" + i, new String[]{"Гражданин в очереди", "Гражданка с авоськой", "Пенсионер Семёныч", "Студент",
					"Тётя Люба", "Инженер Петров", "Мать троих детей", "Последний"}[i], "none", ChatFormatting.GRAY, false,
					-44.5 + i, 0, -18.5, -90f, new ItemStack(Items.PAPER));
		}
	}

	public static Role role(String id) {
		for (Role r : ROLES) if (r.id().equals(id)) return r;
		return null;
	}

	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	private static final Map<String, Long> GIFTS = new HashMap<>();
	static final Map<UUID, List<ItemStack>> STOLEN = new HashMap<>();

	// ------------------------------------------------------------ появление

	public static void spawnAll(ServerLevel l, int cx, int cy, int cz) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
		for (Entity e : old) e.discard();
		for (Role r : ROLES) {
			if (r.id().equals("vozhd") && Leader.retired()) continue;
			spawn(l, r, cx + r.x(), cy + r.y(), cz + r.z());
		}
		// коровы на ферме
		for (int i = 0; i < 5; i++) {
			var cow = EntityType.COW.create(l, EntitySpawnReason.EVENT);
			if (cow == null) continue;
			cow.setPos(cx - 54 + i * 2.2, cy, cz + 16 + (i % 2) * 3);
			cow.setPersistenceRequired();
			cow.setCustomName(Component.literal(new String[]{"Бурёнка", "Зорька", "Пеструшка", "Марта", "Пятилетка"}[i]));
			l.addFreshEntity(cow);
		}
	}

	private static Entity spawn(ServerLevel l, Role r, double x, double y, double z) {
		var v = EntityType.VILLAGER.create(l, EntitySpawnReason.COMMAND);
		if (v == null) return null;
		v.setPos(x, y, z);
		v.setYRot(r.yaw());
		v.setYHeadRot(r.yaw());
		v.setYBodyRot(r.yaw());
		v.setNoAi(true);
		v.setInvulnerable(true);
		v.setPersistenceRequired();
		v.setCustomName(Component.literal(r.name()).withStyle(r.color(), ChatFormatting.BOLD));
		v.setCustomNameVisible(!r.id().startsWith("ochered"));
		v.addTag(TAG);
		v.addTag("cm_role_" + r.id());
		var reg = l.registryAccess();
		var prof = reg.lookupOrThrow(Registries.VILLAGER_PROFESSION)
				.getOrThrow(ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace(r.profession())));
		var type = reg.lookupOrThrow(Registries.VILLAGER_TYPE)
				.getOrThrow(ResourceKey.create(Registries.VILLAGER_TYPE, Identifier.withDefaultNamespace(r.id().startsWith("ochered") ? "plains" : "snow")));
		v.setVillagerData(v.getVillagerData().withProfession(prof).withType(type).withLevel(5));
		v.setVillagerXp(1);
		if (r.baby()) v.setAge(-1_000_000_000);
		if (r.id().equals("vozhd") || r.id().equals("gosplan")) v.setGlowingTag(true);
		l.addFreshEntity(v);
		return v;
	}

	/** Милиционер (временный). */
	public static Entity spawnCop(ServerLevel l, Vec3 at, Entity lookAt) {
		var v = EntityType.VILLAGER.create(l, EntitySpawnReason.EVENT);
		if (v == null) return null;
		v.setPos(at.x, at.y, at.z);
		v.setNoAi(true);
		v.setInvulnerable(true);
		v.setCustomName(Component.literal(new String[]{Justice.COP, "Лейтенант Ковалёв", "Старшина Дубов"}[CommunismMod.RNG.nextInt(3)]).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		v.setCustomNameVisible(true);
		v.addTag(TEMP);
		v.addTag("cm_role_cop");
		var reg = l.registryAccess();
		v.setVillagerData(v.getVillagerData().withProfession(reg.lookupOrThrow(Registries.VILLAGER_PROFESSION)
				.getOrThrow(ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace("nitwit")))));
		if (lookAt != null) {
			Vec3 d = lookAt.position().subtract(at);
			float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
			v.setYRot(yaw);
			v.setYHeadRot(yaw);
			v.setYBodyRot(yaw);
		}
		l.addFreshEntity(v);
		return v;
	}

	/** Возмущённый гражданин у позорного столба. */
	public static Entity spawnCitizen(ServerLevel l, Vec3 at, Vec3 lookAt) {
		var v = EntityType.VILLAGER.create(l, EntitySpawnReason.EVENT);
		if (v == null) return null;
		v.setPos(at.x, at.y, at.z);
		v.setNoAi(true);
		v.setInvulnerable(true);
		v.setCustomName(Component.literal(new String[]{"Возмущённый гражданин", "Гражданка с помидорами", "Пионер-активист", "Бабка с авоськой", "Дружинник"}[CommunismMod.RNG.nextInt(5)]).withStyle(ChatFormatting.GRAY));
		v.setCustomNameVisible(true);
		v.addTag(TEMP);
		Vec3 d = lookAt.subtract(at);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		v.setYRot(yaw);
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
		l.addFreshEntity(v);
		return v;
	}

	public static void spawnBurzhuy(ServerLevel l, BlockPos pos) {
		var b = EntityType.VINDICATOR.create(l, EntitySpawnReason.EVENT);
		if (b == null) return;
		b.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		String[] names = {"Буржуй", "Капиталист", "Олигарх", "Кулак", "Спекулянт", "Акционер", "Рантье", "Брокер"};
		b.setCustomName(Component.literal(names[CommunismMod.RNG.nextInt(names.length)]).withStyle(ChatFormatting.GOLD));
		b.setCustomNameVisible(true);
		b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
		b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
		b.addTag(BURZHUY);
		l.addFreshEntity(b);
	}

	public static void steal(ServerPlayer p, Entity burzhuy) {
		var inv = p.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) if (!inv.getItem(i).isEmpty()) slots.add(i);
		if (slots.isEmpty()) return;
		int slot = slots.get(CommunismMod.RNG.nextInt(slots.size()));
		ItemStack st = inv.getItem(slot).split(Math.min(8, inv.getItem(slot).getCount()));
		STOLEN.computeIfAbsent(burzhuy.getUUID(), k -> new ArrayList<>()).add(st);
		Plaque.show(p, Plaque.Kind.BAD, "Буржуй украл: " + st.getCount() + "× " + st.getHoverName().getString() + "! Убейте его, чтобы вернуть.", 5);
	}

	public static void onBurzhuyDeath(LivingEntity e, ServerPlayer killer) {
		ServerLevel l = (ServerLevel) e.level();
		List<ItemStack> loot = STOLEN.remove(e.getUUID());
		if (loot != null) for (ItemStack st : loot) Msg.drop(l, e.getX(), e.getY() + 0.5, e.getZ(), st);
		Msg.drop(l, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(Items.GOLD_NUGGET, 2 + CommunismMod.RNG.nextInt(5)));
		if (CommunismMod.RNG.nextInt(4) == 0) Msg.drop(l, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(Items.EMERALD, 1));
		if (killer != null) Ranks.addTrud(killer, 5, "раскулачивание");
	}

	// ------------------------------------------------------------ разговоры

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

	public static Entity near(ServerPlayer p, String role, double radius) {
		ServerLevel l = Msg.level(p);
		List<Entity> list = l.getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e.getTags().contains("cm_role_" + role));
		return list.isEmpty() ? null : list.get(0);
	}

	public static void interact(ServerPlayer p, Entity npc) {
		String role = roleOf(npc);
		Quests.onTalk(p, role);
		open(p, role);
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

	private static String pick(String... s) {
		return s[CommunismMod.RNG.nextInt(s.length)];
	}

	public static final String[] QUOTES = {
			"Учиться, учиться и ещё раз копать!", "Кто не работает, тот не крафтит.",
			"Пятилетку — за три года, а алмазы — за три минуты!", "Крипер — это капиталист, который взорвался от зависти.",
			"Свобода — это осознанная необходимость сдать мне пшеницу.", "Жить стало лучше, жить стало веселее! Особенно мне.",
			"Мы наш, мы новый мир построим. Из булыжника.", "Каждой семье — по квартире в хрущёвке! Каждому криперу — по выговору!",
			"Незер — это ад капитализма. Но огненные стержни оттуда нам нужны.", "Слава труду! И немножко мне."};

	/** Открыть окно жителя. */
	public static void open(ServerPlayer p, String role) {
		Role r = role(role);
		if (role.startsWith("ochered")) {
			Ui.npc(r.name(), ChatFormatting.GRAY, r.portrait(), pick("Вы за кем стоите? Я за Семёнычем, а он отошёл.", "Говорят, колбасу завезут. В четверг. Какого года — не сказали.",
					"Не толкайтесь, товарищ! Я тут с прошлой пятилетки стою.", "Я последний? Нет, вы последний. Теперь вы.",
					"Очередь — это коллективное ожидание светлого будущего.")).open(p);
			return;
		}
		switch (role) {
			case "vozhd" -> Leader.openVozhd(p);
			case "gosplan" -> gosplan(p);
			case "zavhoz" -> Shop.show(p);
			case "bankir" -> Loans.open(p);
			case "upgrader" -> Upgrader.open(p);
			case "kgb" -> kgb(p);
			case "politruk" -> {
				Ui.Win w = Ui.npc(POLITRUK, ChatFormatting.GOLD, r.portrait(), pick("Партия поручает вам ответственное задание!",
						"Безделье — путь к капитализму. Берите задание, товарищ!", "Задание выполнил — Родине послужил. Не выполнил — Сибирь послужит вам."));
				Quests.addButtons(w, p, "politruk");
				w.btn("Мои показатели", "stats").open(p);
			}
			case "agronom" -> {
				Ui.Win w = Ui.npc(AGRONOM, ChatFormatting.GREEN, r.portrait(), "Колхоз — сердце пятилетки! Урожай на поле созревает сам, а собранные грядки вырастают снова.");
				w.btn(Ui.label("Посевной набор", ChatFormatting.GREEN), "agro seeds", "Семена и костная мука (раз в 10 минут)");
				Quests.addButtons(w, p, "agronom");
				w.open(p);
			}
			case "kosmonavt" -> {
				boolean free = Plan.stage() >= 4;
				Ui.npc(KOSMONAVT, ChatFormatting.AQUA, r.portrait(), "Ракета «Восток» доставит вас за 500–900 блоков отсюда. Удобно искать алмазы! Обратно — пешком или на метро.")
						.wide(Ui.label("Полететь! (" + (free ? "бесплатно" : "5 трудодней") + ")", ChatFormatting.AQUA, ChatFormatting.BOLD), "kosmos fly", "Поехали!")
						.open(p);
			}
			case "pioner" -> {
				Ui.Win w = Ui.npc(PIONER, ChatFormatting.RED, r.portrait(), pick(
						"Всегда готов! Хотите совет?", "Я собрал 3 тонны макулатуры! Ну, 3 листа. Но с энтузиазмом!", "Когда вырасту — стану космонавтом. Или Генсеком. Как повезёт на выборах."));
				w.btn(Ui.label("Совет", ChatFormatting.WHITE), "pioner tip", null);
				w.btn(Ui.label("Подарок (раз в 5 мин)", ChatFormatting.YELLOW), "pioner gift", null);
				Quests.addButtons(w, p, "pioner");
				w.open(p);
			}
			case "zina", "valya" -> {
				List<ServerPlayer> ps = CommunismMod.server.getPlayerList().getPlayers();
				ServerPlayer o = ps.get(CommunismMod.RNG.nextInt(ps.size()));
				String n = o.getName().getString();
				Ui.npc(r.name(), ChatFormatting.LIGHT_PURPLE, r.portrait(), pick(
						"Видала я вашего " + n + ". Без шапки ходит! Точно шпион.", "А " + n + " опять в Апгрейдер всё спустил. Тьфу, буржуйские замашки!",
						"В наше время колбаса была по 2.20! А сейчас по трудодням. Безобразие.", "Милок, ты бы женился. Или хоть пятилетку выполнил.",
						n + " вчера на запад смотрел. Я всё видела! КГБ уже знает.", "Вот при Вожде порядок был! А, Вождь ещё здесь? Ну, тогда порядок есть.")).open(p);
			}
			case "poet" -> {
				Ui.Win w = Ui.npc("Поэт-агитатор Маяковкин", ChatFormatting.WHITE, r.portrait(), pick(
						"Я волком бы выгрыз крипера! К алмазам — лопатой и киркой! Эх, рифмы нет, зато какой порыв!",
						"Коммунизм — это молодость мира, и его возводить блокам! Блокам? Молодым! Не важно, главное — громко!",
						"Товарищ, верь: взойдёт она, звезда пленительного счастья — и пятилетка будет сдана, и Госплан скажет «здрасьте»!",
						"Ешь кукурузу, буржуй, день твой последний приходит! А может, и не последний... Поэзия — дело тонкое."));
				w.btn(Ui.label("Вдохновиться (раз в 5 мин)", ChatFormatting.GOLD), "poet inspire", "Сила и спешка на 2 минуты");
				w.open(p);
			}
			case "uchastkovy" -> {
				Ui.npc("Участковый Анискин", ChatFormatting.AQUA, r.portrait(),
						"Правила простые: в городе ничего не ломать! Сломал — поедешь в вагонетке «Чёрный воронок» в тюрьму или на Площадь позора. Закон нарушил дважды — выговор. Три выговора — арест.")
						.text("Ваши выговоры: " + Data.get(p, Data.vyg) + "/3", ChatFormatting.GRAY)
						.btn(Ui.label("Свод законов", ChatFormatting.RED), "laws", null).open(p);
			}
			case "tyuremshik" -> {
				int left = Justice.sentenceLeft(p);
				Ui.Win w = Ui.npc("Надзиратель Кузьмич", ChatFormatting.GRAY, r.portrait(), left > 0 ? "Сидишь? Сиди. Осталось " + left + " сек. Хочешь выйти пораньше — УДО за 15 трудодней."
						: "Тюрьма «Светлый путь» — самая светлая тюрьма в мире! Окна есть, свет есть, выхода нет.");
				if (left > 0) w.btn(Ui.label("УДО за 15 трудодней", ChatFormatting.GREEN), "jail udo", null);
				w.open(p);
			}
			case "prodavshitsa" -> {
				Ui.Win w = Ui.npc("Продавщица Галя", ChatFormatting.YELLOW, r.portrait(), pick("Колбасы нет! И не будет! Есть «товар дня».",
						"Без очереди не обслуживаю! Ой, вы из очереди? Тогда тоже не обслуживаю, у меня обед.", "Сдачи нет. Трудодней тоже не размениваем."));
				w.btn(Ui.label("Товар дня (3 трудодня)", ChatFormatting.GOLD), "galya buy", "Случайный товар. Иногда — дефицит!");
				Quests.addButtons(w, p, "prodavshitsa");
				w.open(p);
			}
			case "doyarka" -> {
				Ui.Win w = Ui.npc("Доярка Маруся", ChatFormatting.WHITE, r.portrait(), pick("Бурёнки дают по 3 ведра в день! По плану — 300. Мы стараемся.",
						"Коровы у нас партийные: мычат только по делу.", "Пятилетка — это не корова, её доить сложнее!"));
				Quests.addButtons(w, p, "doyarka");
				w.open(p);
			}
			case "akademik" -> {
				Ui.Win w = Ui.npc("Академик Колосов", ChatFormatting.DARK_GREEN, r.portrait(), pick(
						"Мы скрестили пшеницу с кукурузой и получили... выговор. Но наука не стоит на месте!",
						"Генетика — буржуазная лженаука! Но вот ветвистая пшеница — это да, это сила!",
						"Мой опыт: если долго кричать на картошку, она растёт быстрее. Проверено на 3 картофелинах."));
				Quests.addButtons(w, p, "akademik");
				w.open(p);
			}
			default -> { }
		}
	}

	private static void gosplan(ServerPlayer p) {
		int st = Plan.stage();
		Ui.Win w = Ui.npc(GOSPLAN, ChatFormatting.RED, role("gosplan").portrait(),
				st >= Plan.VICTORY ? "Коммунизм построен! Каждому — по потребностям. Заходите раз в 5 минут за потребностями."
						: st == Plan.BOSS ? "Пятилетка выполнена! Но Мистер Капитал летит сюда. Когда будете готовы — скажите."
						: pick(QUOTES));
		w.text(Plan.statusText().getString().replace("☭ ", ""), ChatFormatting.YELLOW);
		if (st < Plan.BOSS) {
			w.btn(Ui.label("Сдать из руки", ChatFormatting.GREEN, ChatFormatting.BOLD), "plan hand", "Отдать предмет, который держите");
			w.btn(Ui.label("Сдать всё подходящее", ChatFormatting.GREEN), "plan all", "Пройти по всему инвентарю");
		} else if (st == Plan.BOSS) {
			w.wide(Ui.label(Boss.alive() ? "Мистер Капитал уже здесь! В бой!" : "НАЧАТЬ ФИНАЛЬНУЮ БИТВУ", ChatFormatting.DARK_RED, ChatFormatting.BOLD), "plan boss", "Вызвать Мистера Капитала на Красную площадь");
		} else {
			w.wide(Ui.label("Получить «по потребностям»", ChatFormatting.GOLD), "plan need", null);
		}
		if (p.getMainHandItem().is(Items.PAPER) && Goods.FORM_NAME.equals(Goods.nameOf(p.getMainHandItem()))) {
			w.wide(Ui.label("Сдать " + Goods.FORM_NAME, ChatFormatting.AQUA), "plan form", null);
		}
		w.open(p);
	}

	private static void kgb(ServerPlayer p) {
		int v = Data.get(p, Data.vyg);
		Ui.Win w = Ui.npc(Justice.KGB + " (КГБ)", ChatFormatting.DARK_GRAY, role("kgb").portrait(), pick(
				"Я за вами не слежу. Просто стою тут. Каждый день. С блокнотом.", "Поступил донос, что кто-то копает прямо вниз. Это вы? Не отвечайте, я знаю.",
				"Знаете, что общего у вас и алмаза? Мы найдём вас обоих.", "Слышали анекдот про Вождя? Нет? И правильно."));
		w.text("Ваши выговоры: " + v + "/3. При трёх — арест.", ChatFormatting.GRAY);
		List<ServerPlayer> ps = CommunismMod.server.getPlayerList().getPlayers();
		if (ps.size() > 1) {
			ServerPlayer lazy = null;
			int min = Integer.MAX_VALUE;
			for (ServerPlayer o : ps) if (Data.get(o, Data.stazh) < min) { min = Data.get(o, Data.stazh); lazy = o; }
			w.text("Самый ленивый товарищ по данным органов: " + lazy.getName().getString() + " (стаж " + min + ").", ChatFormatting.DARK_GRAY);
		}
		if (v > 0) w.btn(Ui.label("«Потерять» дело (10 тр.)", ChatFormatting.YELLOW), "kgb bribe", "Снять один выговор");
		w.btn(Ui.label("Написать донос (2 тр.)", ChatFormatting.RED), "kgb denounce", "Случайному товарищу — выговор. Или вам.");
		w.btn(Ui.label("Свод законов", ChatFormatting.WHITE), "laws", null);
		w.open(p);
	}

	/** Свод законов. */
	public static void openLaws(ServerPlayer p) {
		Ui.Win w = Ui.win("⚖ Свод законов СССР", ChatFormatting.DARK_RED);
		List<Laws.Law> act = Laws.active();
		if (act.isEmpty()) w.text("Сейчас законов нет. Подозрительно спокойно...", ChatFormatting.GRAY);
		for (Laws.Law l : act) {
			w.icon(l.icon().copy(), Component.literal("«" + l.name() + "» ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
					.append(Component.literal(l.desc() + " ").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(l.rule()).withStyle(ChatFormatting.YELLOW)));
		}
		w.line();
		w.text("Нарушение: сначала предупреждение, повтор за 2 минуты — выговор. 3 выговора — арест.", ChatFormatting.GRAY);
		w.text("Ломать блоки в городе нельзя — сразу арест!", ChatFormatting.RED);
		w.text("Всего законов принято: " + Laws.enactedCount() + ". Новый закон через " + Msg.time(Data.get("lawTimer")) + ".", ChatFormatting.GRAY);
		w.open(p);
	}

	/** Обработка кнопок жителей. */
	public static void handle(ServerPlayer p, String[] a) {
		String who = a[0];
		String act = a.length > 1 ? a[1] : "";
		MinecraftServer s = CommunismMod.server;
		switch (who) {
			case "plan" -> {
				if (near(p, "gosplan", 10) == null) return;
				switch (act) {
					case "hand", "all" -> {
						if (!Plan.contribute(p, act.equals("all"))) Plaque.show(p, Plaque.Kind.INFO, "Подходящих для пятилетки предметов нет.", 4);
						gosplan(p);
					}
					case "boss" -> {
						if (Plan.stage() == Plan.BOSS && !Boss.alive()) Boss.summon(s);
					}
					case "need" -> {
						if (Plan.stage() < Plan.VICTORY) return;
						if (giftReady(p, "need", 300)) {
							ItemStack[] needs = {new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.EMERALD, 8),
									new ItemStack(Items.NETHERITE_SCRAP, 1), new ItemStack(Items.EXPERIENCE_BOTTLE, 16), Goods.sausage(16)};
							ItemStack g = needs[CommunismMod.RNG.nextInt(needs.length)].copy();
							Msg.give(p, g);
							Plaque.show(p, Plaque.Kind.GOOD, "По потребностям выдано: " + g.getCount() + "× " + g.getHoverName().getString(), 6);
						} else Plaque.show(p, Plaque.Kind.INFO, "Потребности удовлетворяются раз в 5 минут. Осталось " + Msg.time(giftLeft(p, "need", 300)), 5);
					}
					case "form" -> Decrees.handInForm(p);
					default -> { }
				}
			}
			case "agro" -> {
				if (near(p, "agronom", 10) == null) return;
				if (giftReady(p, "seeds", 600)) {
					Msg.give(p, new ItemStack(Items.WHEAT_SEEDS, 16));
					Msg.give(p, new ItemStack(Items.POTATO, 6));
					Msg.give(p, new ItemStack(Items.CARROT, 6));
					Msg.give(p, new ItemStack(Items.BONE_MEAL, 12));
					Msg.give(p, Goods.corn(2));
					Plaque.show(p, Plaque.Kind.GOOD, "Посевной набор выдан. Сажайте всё, особенно кукурузу!", 5);
				} else Plaque.show(p, Plaque.Kind.INFO, "Следующий набор через " + Msg.time(giftLeft(p, "seeds", 600)), 4);
			}
			case "kosmos" -> {
				if (near(p, "kosmonavt", 10) == null) return;
				int price = Plan.stage() >= 4 ? 0 : 5;
				if (!Ranks.spend(p, price)) {
					Plaque.show(p, Plaque.Kind.BAD, "Не хватает трудодней на топливо (нужно " + price + ").", 4);
					return;
				}
				Msg.title(p, Msg.t("ПОЕХАЛИ!", ChatFormatting.AQUA, ChatFormatting.BOLD), Msg.t("Ключ на старт!", ChatFormatting.WHITE));
				Msg.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
				p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 15));
				Scheduler.later(60, () -> {
					if (p.isRemoved()) return;
					ServerLevel ow = s.overworld();
					double ang = CommunismMod.RNG.nextDouble() * Math.PI * 2;
					int d = 500 + CommunismMod.RNG.nextInt(400);
					int x = (int) (p.getX() + Math.cos(ang) * d), z = (int) (p.getZ() + Math.sin(ang) * d);
					int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 50;
					p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1600, 0));
					Justice.teleport(p, ow, x + 0.5, y, z + 0.5, p.getYRot());
					Plaque.show(p, Plaque.Kind.INFO, "Приземление в точке " + x + " " + z + ". Обратно — /komm metro (3 трудодня).", 10);
				});
			}
			case "pioner" -> {
				if (near(p, "pioner", 10) == null) return;
				if (act.equals("tip")) {
					Ui.npc(PIONER, ChatFormatting.RED, role("pioner").portrait(), pick(
							"Пятилетку сдают Председателю Госплана у Мавзолея! Кнопка «Сдать всё» — очень удобно!",
							"Законы принимает Вождь во Дворце Советов, за Кремлём. Каждые 6 минут — новый!",
							"Не ломай ничего в городе! Меня уже два раза на вагонетке катали. Весело, но стыдно.",
							"Банкир даёт трудодни в долг. Но потом приходит милиция. Я проверил.",
							"Апгрейдер Фортунатов умножает вещи! Или забирает. Я поставил яблоко и остался без яблока.",
							"Если Вождь примет 10 законов, он устанет. Тогда главным станет кто-то из вас!",
							"Каждые 10 минут — Указ Партии. Таймер — в полосе сверху!",
							"На Площади позора кидаются помидорами. А помидоры — это свёкла. Не спрашивай.")).btn("Ещё совет", "pioner tip").open(p);
				} else if (act.equals("gift")) {
					if (giftReady(p, "pioner", 300)) {
						ItemStack[] gifts = {new ItemStack(Items.APPLE, 3), new ItemStack(Items.COOKIE, 6), Goods.bread(2), new ItemStack(Items.TORCH, 16)};
						ItemStack g = gifts[CommunismMod.RNG.nextInt(gifts.length)].copy();
						Msg.give(p, g);
						Plaque.show(p, Plaque.Kind.GOOD, "Пионер Вася подарил: " + g.getHoverName().getString() + "!", 4);
					} else Plaque.show(p, Plaque.Kind.INFO, "Вася ещё собирает макулатуру. Через " + Msg.time(giftLeft(p, "pioner", 300)), 4);
				}
			}
			case "poet" -> {
				if (near(p, "poet", 10) == null) return;
				if (giftReady(p, "poet", 300)) {
					p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 2400, 0));
					p.addEffect(new MobEffectInstance(MobEffects.HASTE, 2400, 1));
					Plaque.show(p, Plaque.Kind.GOOD, "Вы вдохновлены! Хочется копать и рифмовать.", 5);
				} else Plaque.show(p, Plaque.Kind.INFO, "Муза поэта отдыхает. Через " + Msg.time(giftLeft(p, "poet", 300)), 4);
			}
			case "kgb" -> {
				if (near(p, "kgb", 10) == null) return;
				if (act.equals("bribe")) {
					int v = Data.get(p, Data.vyg);
					if (v > 0 && Ranks.spend(p, 10)) {
						Data.set(p, Data.vyg, v - 1);
						Plaque.show(p, Plaque.Kind.GOOD, "Дело «потерялось». Выговоров: " + (v - 1) + ". Мы друг друга не видели.", 6);
					} else Plaque.show(p, Plaque.Kind.BAD, "Взятка? Маловато. Нужно 10 трудодней.", 4);
				} else if (act.equals("denounce")) {
					if (!Ranks.spend(p, 2)) return;
					List<ServerPlayer> ps = new ArrayList<>(s.getPlayerList().getPlayers());
					ServerPlayer target = ps.get(CommunismMod.RNG.nextInt(ps.size()));
					if (target == p || CommunismMod.RNG.nextInt(4) == 0) {
						Justice.vygovor(p, "донос на самого себя (органы разобрались)");
					} else {
						Justice.vygovor(target, "анонимный донос");
						Plaque.show(p, Plaque.Kind.INFO, "Донос принят. Органы разберутся с товарищем " + target.getName().getString() + ".", 5);
					}
				}
				kgb(p);
			}
			case "jail" -> {
				if (near(p, "tyuremshik", 16) == null && Justice.sentenceLeft(p) <= 0) return;
				if (Ranks.spend(p, 15)) {
					if (!Justice.pardon(p, "Условно-досрочное освобождение!")) Ranks.addTrud(p, 15, "возврат");
				} else Plaque.show(p, Plaque.Kind.BAD, "На УДО нужно 15 трудодней.", 4);
			}
			case "galya" -> {
				if (near(p, "prodavshitsa", 10) == null) return;
				if (!Ranks.spend(p, 3)) {
					Plaque.show(p, Plaque.Kind.BAD, "Нужно 3 трудодня. Галя смотрит осуждающе.", 4);
					return;
				}
				ItemStack[] goods = {Goods.sausage(3), Goods.bread(4), new ItemStack(Items.COOKED_COD, 4), new ItemStack(Items.MILK_BUCKET),
						Goods.named(Items.SUSPICIOUS_STEW, 1, "Суп «Из того, что было»", ChatFormatting.GRAY), new ItemStack(Items.SWEET_BERRIES, 12),
						Goods.named(Items.BEETROOT, 8, "Помидоры (свёкла)", ChatFormatting.RED), new ItemStack(Items.CAKE),
						Goods.named(Items.GLASS_BOTTLE, 1, "Пустая бутылка (дефицит!)", ChatFormatting.AQUA, "Сдать нельзя, но можно гордиться."),
						Goods.corn(2), new ItemStack(Items.PUMPKIN_PIE, 3)};
				ItemStack g = goods[CommunismMod.RNG.nextInt(goods.length)].copy();
				Msg.give(p, g);
				Plaque.show(p, Plaque.Kind.GOOD, "Товар дня: " + g.getHoverName().getString() + ". Следующий!", 5);
			}
			default -> { }
		}
	}

	// ------------------------------------------------------------ периодически

	private static int sloganTimer = 0;

	static void secondTick(MinecraftServer s) {
		if (++sloganTimer >= 300) {
			sloganTimer = 0;
			Plaque.all(s, Plaque.Kind.INFO, "Лозунг дня: «" + pick(QUOTES) + "»", 8);
		}
	}

	static void reset() {
		COOLDOWN.clear();
		GIFTS.clear();
		STOLEN.clear();
		sloganTimer = 0;
	}
}
