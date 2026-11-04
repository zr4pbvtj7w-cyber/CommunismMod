package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Великий Вождь (главный NPC, Дворец Советов): издаёт законы. После 10 законов устаёт,
 * уходит на покой и случайно назначает Генсеком одного из игроков.
 */
public final class Leader {
	public static final String VOZHD = "Великий Вождь";
	public static final int LAWS_TO_RETIRE = 10;
	public static final String SEAL = "Печать Генсека";

	public static boolean retired() {
		return Data.get("retired") == 1;
	}

	public static boolean isLeader(ServerPlayer p) {
		return Data.get(p, Data.leader) == 1;
	}

	public static ServerPlayer current(MinecraftServer s) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) if (isLeader(p)) return p;
		return null;
	}

	// ------------------------------------------------------------------ Вождь

	public static void openVozhd(ServerPlayer p) {
		int n = Laws.enactedCount();
		String speech;
		if (n >= LAWS_TO_RETIRE) speech = "Товарищ... Подойдите ближе. Я принял уже " + n + " законов. Я... так устал.";
		else speech = new String[]{"Закон — это то, что я сказал сегодня. Завтра закон — это то, что я скажу завтра.",
				"Народ любит законы. Я люблю народ. Значит, я люблю законы. Логика!",
				"Каждый новый закон делает страну лучше. Особенно закон об отмене гравитации.",
				"Вы пришли с жалобой? Жалобы принимаются в письменном виде в Сибири."}[CommunismMod.RNG.nextInt(4)];
		Ui.Win w = Ui.npc(VOZHD, ChatFormatting.DARK_RED, new ItemStack(Items.GOLDEN_HELMET), speech);
		w.text("Действующие законы (" + Laws.active().size() + "/" + Laws.SLOTS + "). Всего принято: " + n + ". Следующий — через " + Msg.time(Data.get("lawTimer")), ChatFormatting.GOLD);
		w.wide(Ui.label("⚖ Свод законов", ChatFormatting.RED, ChatFormatting.BOLD), "laws", "Подробно о каждом действующем законе");
		w.btn(Ui.label("Предложить закон (10 тр.)", ChatFormatting.YELLOW), "vozhd propose", "Выберите один из трёх законопроектов — он вступит в силу сразу");
		w.btn(Ui.label("Спросить совета", ChatFormatting.WHITE), "vozhd advice", null);
		if (n >= LAWS_TO_RETIRE) {
			w.wide(Ui.label("«Товарищ Вождь, вы устали?»", ChatFormatting.GOLD, ChatFormatting.BOLD), "vozhd retire 0", "Важный разговор о будущем страны");
		} else {
			w.text("(Говорят, после " + LAWS_TO_RETIRE + " законов Вождь хочет поговорить о чём-то важном...)", ChatFormatting.DARK_GRAY);
		}
		w.open(p);
	}

	public static void handleVozhd(ServerPlayer p, String[] a) {
		if (Npcs.near(p, "vozhd", 12) == null) {
			Plaque.show(p, Plaque.Kind.INFO, "Вождь принимает только во Дворце Советов.", 4);
			return;
		}
		MinecraftServer s = CommunismMod.server;
		String act = a.length > 1 ? a[1] : "";
		switch (act) {
			case "propose" -> {
				long next = Data.get("proposeAt");
				if (CommunismMod.tick / 20 < next) {
					Plaque.show(p, Plaque.Kind.INFO, "Совет ещё обсуждает прошлый законопроект. Подождите " + Msg.time((int) (next - CommunismMod.tick / 20)) + ".", 5);
					return;
				}
				List<Laws.Law> c = Laws.randomCandidates(s, 3);
				Ui.Win w = Ui.npc(VOZHD + ": законопроекты", ChatFormatting.DARK_RED, new ItemStack(Items.WRITABLE_BOOK),
						"Выбирайте, товарищ. Помните: любой закон — мудрый, если его предложил народ. И одобрил я.");
				for (Laws.Law l : c) w.wide(Ui.label("«" + l.name() + "»", ChatFormatting.GOLD), "vozhd enact " + l.id(), l.rule());
				w.text("Стоимость: 10 трудодней (у вас " + Ranks.trud(p) + ").", ChatFormatting.GRAY);
				w.open(p);
			}
			case "enact" -> {
				if (a.length < 3) return;
				int id = Integer.parseInt(a[2]);
				boolean ok = false;
				for (Laws.Law l : Laws.candidates(s)) if (l.id() == id) ok = true;
				if (!ok || CommunismMod.tick / 20 < Data.get("proposeAt")) return;
				if (!Ranks.spend(p, 10)) {
					Plaque.show(p, Plaque.Kind.BAD, "Нужно 10 трудодней на госпошлину.", 5);
					return;
				}
				Data.set("proposeAt", (int) (CommunismMod.tick / 20) + 180);
				Laws.enact(s, id, "по предложению товарища " + p.getName().getString());
			}
			case "advice" -> {
				String[] adv = {"Если не знаешь, что делать — прими закон. Если знаешь — тоже прими закон.",
						"Пятилетку сдают Председателю Госплана у Мавзолея. Я лично слежу за процентами.",
						"Сломаешь что-нибудь в городе — милиция приедет быстрее, чем ты скажешь «капитализм».",
						"Деньги в долг берут только буржуи. И мы, когда очень надо. Банк — на востоке.",
						"Апгрейдер — это наука. Наука, которая иногда забирает ваши алмазы."};
				Ui.npc(VOZHD, ChatFormatting.DARK_RED, new ItemStack(Items.GOLDEN_HELMET), adv[CommunismMod.RNG.nextInt(adv.length)])
						.btn("Назад", "vozhd").open(p);
			}
			case "retire" -> retireStory(p, a.length > 2 ? Integer.parseInt(a[2]) : 0);
			default -> openVozhd(p);
		}
	}

	private static final String[] STORY = {
			"Знаешь, товарищ... Я принял " + LAWS_TO_RETIRE + " законов. Отменил гравитацию. Отменил ночь. Запретил смотреть на запад. А сам каждый вечер смотрю на запад — там закат красивый.",
			"Я руковожу страной с тех пор, как появился этот мир. Ни одного выходного. Даже криперы отдыхают, когда их взрывают.",
			"Я решил. Ухожу на покой. Полечу на ракете «Восток» на дачу. На Луну. Там тихо, никаких пятилеток.",
			"Но страна не может остаться без руководителя! Я проведу самые честные выборы в истории: выберу случайно. Это и есть демократия, верно?"};

	private static void retireStory(ServerPlayer p, int page) {
		if (retired() || Laws.enactedCount() < LAWS_TO_RETIRE) return;
		if (page < STORY.length) {
			Ui.Win w = Ui.npc(VOZHD, ChatFormatting.DARK_RED, new ItemStack(Items.GOLDEN_HELMET), STORY[page]);
			if (page + 1 < STORY.length) w.wide(Ui.label("Далее...", ChatFormatting.YELLOW), "vozhd retire " + (page + 1), null);
			else w.wide(Ui.label("Провести выборы!", ChatFormatting.RED, ChatFormatting.BOLD), "vozhd retire " + STORY.length, "Вождь уйдёт, руководителем станет случайный игрок");
			w.exit("Подождите, не уходите!").open(p);
			return;
		}
		retire(CommunismMod.server);
	}

	public static void retire(MinecraftServer s) {
		if (retired()) return;
		Data.set("retired", 1);
		List<ServerPlayer> ps = new ArrayList<>(s.getPlayerList().getPlayers());
		ServerPlayer chosen = ps.isEmpty() ? null : ps.get(CommunismMod.RNG.nextInt(ps.size()));
		Msg.titleAll(s, Msg.t("ВОЖДЬ УХОДИТ НА ПОКОЙ", ChatFormatting.DARK_RED, ChatFormatting.BOLD), Msg.t("Великая эпоха закончилась...", ChatFormatting.GOLD));
		Msg.sayAll(s, VOZHD, "Товарищи! Я устал. Ухожу на покой — на дачу на Луне. Берегите пятилетку и не смотрите на запад!");
		// Вождь летит на ракете
		ServerLevel ow = s.overworld();
		Entity v = null;
		for (Entity e : ow.getAllEntities()) if (e.getTags().contains("cm_role_vozhd")) v = e;
		if (v != null && City.built()) {
			final Entity vozhd = v;
			Vec3 pad = City.at(32.5, 16, -32.5);
			vozhd.teleportTo(pad.x, pad.y, pad.z);
			vozhd.setNoGravity(true);
			for (int i = 0; i < 60; i++) {
				final int k = i;
				Scheduler.later(40 + i * 2, () -> {
					vozhd.teleportTo(pad.x, pad.y + k * 1.5, pad.z);
					if (k % 6 == 0) Msg.firework(ow, pad.x, pad.y + k * 1.5, pad.z);
				});
			}
			Scheduler.later(170, vozhd::discard);
		}
		Scheduler.later(180, () -> {
			if (chosen == null || chosen.isRemoved()) return;
			appoint(s, chosen);
		});
	}

	public static void appoint(MinecraftServer s, ServerPlayer p) {
		for (ServerPlayer o : s.getPlayerList().getPlayers()) Data.set(o, Data.leader, 0);
		Data.set(p, Data.leader, 1);
		Ranks.updateTeam(p);
		Msg.titleAll(s, Msg.t("НОВЫЙ ГЕНСЕК", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t("Товарищ " + p.getName().getString() + "! Выборы прошли честно (99,9%)", ChatFormatting.GOLD));
		Plaque.all(s, Plaque.Kind.DECREE, "Руководитель страны — " + p.getName().getString() + "! Теперь законы принимает он.", 12);
		Msg.soundAll(s, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f);
		for (ServerPlayer o : s.getPlayerList().getPlayers()) Msg.fireworksAround(o, 3);
		Msg.give(p, seal());
		Ranks.addTrud(p, 50, "назначение Генсеком");
		Scheduler.later(60, () -> openLeader(p));
	}

	public static ItemStack seal() {
		return Goods.named(Items.NETHER_STAR, 1, SEAL, ChatFormatting.RED, "ПКМ — пульт управления страной.", "Не терять! Не давать пионерам!");
	}

	public static boolean isSeal(ItemStack s) {
		return s.is(Items.NETHER_STAR) && SEAL.equals(Goods.nameOf(s));
	}

	// ------------------------------------------------------------------ игрок-Генсек

	public static void openLeader(ServerPlayer p) {
		if (!isLeader(p)) {
			Plaque.show(p, Plaque.Kind.BAD, "Печать работает только в руках Генсека. Самозванство — это выговор.", 5);
			return;
		}
		Ui.Win w = Ui.win("☭ Пульт Генсека: товарищ " + p.getName().getString(), ChatFormatting.RED)
				.icon(seal(), Component.literal("Страна ждёт ваших решений! Законов принято: " + Laws.enactedCount() + ".").withStyle(ChatFormatting.GOLD))
				.text("Действуют: " + Laws.shortList(), ChatFormatting.YELLOW);
		w.btn(Ui.label("Издать закон", ChatFormatting.RED, ChatFormatting.BOLD), "lead laws", "Выбор из четырёх законопроектов");
		w.btn(Ui.label("Отменить закон", ChatFormatting.WHITE), "lead repeal", null);
		w.btn(Ui.label("Объявить указ", ChatFormatting.GOLD), "lead decree", "Случайный Указ Партии (раз в 5 минут)");
		w.btn(Ui.label("Амнистия", ChatFormatting.GREEN), "lead amnesty", "Освободить всех заключённых и снять выговоры");
		w.btn(Ui.label("Наградить товарища", ChatFormatting.AQUA), "lead award", "+15 трудодней (раз в 3 минуты)");
		w.btn(Ui.label("Объявить праздник", ChatFormatting.LIGHT_PURPLE), "lead holiday", "Салют и бодрость всем (раз в 10 минут)");
		w.wide(Ui.label("Уйти в отставку", ChatFormatting.DARK_GRAY), "lead resign", "Новый Генсек будет выбран случайно");
		w.open(p);
	}

	private static boolean cooldown(ServerPlayer p, String key, int seconds) {
		int now = (int) (CommunismMod.tick / 20);
		int at = Data.get("cd_" + key);
		if (now < at) {
			Plaque.show(p, Plaque.Kind.INFO, "Рано! Можно будет через " + Msg.time(at - now) + ".", 4);
			return false;
		}
		Data.set("cd_" + key, now + seconds);
		return true;
	}

	public static void handleLeader(ServerPlayer p, String[] a) {
		if (!isLeader(p)) return;
		MinecraftServer s = CommunismMod.server;
		String act = a.length > 1 ? a[1] : "";
		switch (act) {
			case "laws" -> {
				Ui.Win w = Ui.win("Законопроекты на подпись", ChatFormatting.DARK_RED);
				for (Laws.Law l : Laws.randomCandidates(s, 4)) w.wide(Ui.label("«" + l.name() + "»", ChatFormatting.GOLD), "lead enact " + l.id(), l.rule());
				w.btn("Назад", "lead").open(p);
			}
			case "enact" -> {
				if (a.length < 3 || !cooldown(p, "lead_law", 60)) return;
				int id = Integer.parseInt(a[2]);
				Laws.enact(s, id, "подписал Генсек " + p.getName().getString());
				Ranks.addTrud(p, 5, "государственная работа");
			}
			case "repeal" -> {
				Ui.Win w = Ui.win("Отменить закон", ChatFormatting.DARK_RED);
				for (Laws.Law l : Laws.active()) w.wide(Ui.label("Отменить «" + l.name() + "»", ChatFormatting.WHITE), "lead unlaw " + l.id(), l.rule());
				w.btn("Назад", "lead").open(p);
			}
			case "unlaw" -> {
				if (a.length > 2) Laws.repeal(s, Integer.parseInt(a[2]));
			}
			case "decree" -> {
				if (cooldown(p, "lead_decree", 300)) Decrees.runRandom(s);
			}
			case "amnesty" -> {
				if (!cooldown(p, "lead_amnesty", 300)) return;
				int n = Justice.pardonAll(s, "Амнистия от Генсека!");
				for (ServerPlayer o : s.getPlayerList().getPlayers()) Data.set(o, Data.vyg, 0);
				Plaque.all(s, Plaque.Kind.GOOD, "Генсек объявил амнистию! Освобождено: " + n + ". Выговоры сняты.", 8);
			}
			case "award" -> {
				Ui.Win w = Ui.win("Кого наградить?", ChatFormatting.AQUA);
				for (ServerPlayer o : s.getPlayerList().getPlayers()) if (o != p) w.btn(o.getName().getString(), "lead give " + o.getName().getString());
				if (s.getPlayerList().getPlayers().size() < 2) w.text("Других товарищей нет. Наградить себя нельзя — это коррупция.", ChatFormatting.GRAY);
				w.btn("Назад", "lead").open(p);
			}
			case "give" -> {
				if (a.length < 3) return;
				ServerPlayer o = s.getPlayerList().getPlayerByName(a[2]);
				if (o == null || o == p || !cooldown(p, "lead_award", 180)) return;
				Ranks.addTrud(o, 15, "награда от Генсека");
				Plaque.all(s, Plaque.Kind.GOOD, "Генсек наградил товарища " + o.getName().getString() + " (+15 трудодней)!", 7);
			}
			case "holiday" -> {
				if (!cooldown(p, "lead_holiday", 600)) return;
				Plaque.all(s, Plaque.Kind.DECREE, "ПРАЗДНИК! Генсек объявил всенародное гулянье!", 8);
				for (ServerPlayer o : s.getPlayerList().getPlayers()) {
					o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
					o.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1));
					o.addEffect(new MobEffectInstance(MobEffects.HASTE, 1200, 1));
					Msg.fireworksAround(o, 4);
				}
			}
			case "resign" -> {
				List<ServerPlayer> ps = new ArrayList<>(s.getPlayerList().getPlayers());
				ps.remove(p);
				Data.set(p, Data.leader, 0);
				Ranks.updateTeam(p);
				Plaque.all(s, Plaque.Kind.DECREE, p.getName().getString() + " ушёл в отставку по собственному желанию (говорят, устал как Вождь).", 8);
				if (!ps.isEmpty()) appoint(s, ps.get(CommunismMod.RNG.nextInt(ps.size())));
				else Scheduler.later(40, () -> appoint(s, p));
				return;
			}
			default -> {
				openLeader(p);
				return;
			}
		}
	}
}
