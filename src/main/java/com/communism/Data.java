package com.communism;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Всё состояние мода хранится в обычном скорборде мира — он сам сохраняется вместе с миром.
 * Глобальные значения лежат у «фейковых игроков» вида #stage в цели cm_data.
 */
public final class Data {
	public static Objective data, trud, stazh, vyg, qtype, qprog, qgoal, qmask, qbase, joined, debt, debtT, loans, leader;
	private static MinecraftServer server;

	static void init(MinecraftServer s) {
		server = s;
		data = obj("cm_data", "Данные Партии");
		trud = obj("cm_trud", "☭ Трудодни");
		stazh = obj("cm_stazh", "Партстаж");
		vyg = obj("cm_vyg", "Выговоры");
		qtype = obj("cm_qtype", "Задание");
		qprog = obj("cm_qprog", "Прогресс задания");
		qgoal = obj("cm_qgoal", "Цель задания");
		joined = obj("cm_joined", "Партбилет");
		qmask = obj("cm_qmask", "Задание: отметки");
		qbase = obj("cm_qbase", "Задание: база");
		debt = obj("cm_debt", "Долг МФО");
		debtT = obj("cm_debtt", "Срок долга");
		loans = obj("cm_loans", "Займов взято");
		leader = obj("cm_leader", "Генсек");
		Scoreboard sb = s.getScoreboard();
		if (sb.getDisplayObjective(DisplaySlot.SIDEBAR) == null) {
			sb.setDisplayObjective(DisplaySlot.SIDEBAR, trud);
		}
	}

	private static Objective obj(String name, String display) {
		Scoreboard sb = server.getScoreboard();
		Objective o = sb.getObjective(name);
		if (o == null) {
			o = sb.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(display),
					ObjectiveCriteria.RenderType.INTEGER, false, null);
		}
		return o;
	}

	public static int get(String key) {
		return server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#" + key), data).get();
	}

	public static void set(String key, int v) {
		server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#" + key), data).set(v);
	}

	public static int add(String key, int v) {
		int n = get(key) + v;
		set(key, n);
		return n;
	}

	public static int get(ServerPlayer p, Objective o) {
		return server.getScoreboard().getOrCreatePlayerScore(p, o).get();
	}

	public static void set(ServerPlayer p, Objective o, int v) {
		server.getScoreboard().getOrCreatePlayerScore(p, o).set(v);
	}

	public static int add(ServerPlayer p, Objective o, int v) {
		int n = get(p, o) + v;
		set(p, o, n);
		return n;
	}

	// ---- координаты Красной площади ----
	public static boolean built() {
		return get("built") == 1;
	}

	public static int px() { return get("px"); }
	public static int py() { return get("py"); }
	public static int pz() { return get("pz"); }
}
