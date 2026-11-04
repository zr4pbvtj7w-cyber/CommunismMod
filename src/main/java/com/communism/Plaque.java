package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Плашки: цветные полоски вверху экрана, которые появляются на несколько секунд и тают.
 * Используются вместо сообщений в чат.
 */
public final class Plaque {
	public enum Kind {
		LAW("⚖ ", BossEvent.BossBarColor.RED, ChatFormatting.RED),
		DECREE("★ ", BossEvent.BossBarColor.YELLOW, ChatFormatting.GOLD),
		MONEY("₽ ", BossEvent.BossBarColor.GREEN, ChatFormatting.GREEN),
		POLICE("⛓ ", BossEvent.BossBarColor.BLUE, ChatFormatting.AQUA),
		INFO("☭ ", BossEvent.BossBarColor.WHITE, ChatFormatting.WHITE),
		BAD("✖ ", BossEvent.BossBarColor.PURPLE, ChatFormatting.LIGHT_PURPLE),
		GOOD("✔ ", BossEvent.BossBarColor.GREEN, ChatFormatting.GREEN);

		final String icon;
		final BossEvent.BossBarColor color;
		final ChatFormatting text;

		Kind(String icon, BossEvent.BossBarColor color, ChatFormatting text) {
			this.icon = icon;
			this.color = color;
			this.text = text;
		}
	}

	private static final class Shown {
		final ServerBossEvent bar;
		final int total;
		int left;

		Shown(ServerBossEvent bar, int total) {
			this.bar = bar;
			this.total = total;
			this.left = total;
		}
	}

	private static final int MAX = 3;
	private static final Map<UUID, Deque<Shown>> SHOWN = new HashMap<>();

	public static void show(ServerPlayer p, Kind k, String text) {
		show(p, k, text, 6);
	}

	public static void show(ServerPlayer p, Kind k, String text, int seconds) {
		MutableComponent c = Component.literal(k.icon).withStyle(k.text, ChatFormatting.BOLD)
				.append(Component.literal(text).withStyle(k.text));
		ServerBossEvent bar = new ServerBossEvent(c, k.color, BossEvent.BossBarOverlay.PROGRESS);
		bar.setProgress(1f);
		bar.addPlayer(p);
		Deque<Shown> q = SHOWN.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
		q.addLast(new Shown(bar, seconds * 20));
		while (q.size() > MAX) q.removeFirst().bar.removeAllPlayers();
	}

	public static void all(MinecraftServer s, Kind k, String text) {
		all(s, k, text, 7);
	}

	public static void all(MinecraftServer s, Kind k, String text, int seconds) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) show(p, k, text, seconds);
	}

	static void tick() {
		Iterator<Map.Entry<UUID, Deque<Shown>>> it = SHOWN.entrySet().iterator();
		while (it.hasNext()) {
			Deque<Shown> q = it.next().getValue();
			List<Shown> done = new ArrayList<>();
			for (Shown s : q) {
				s.left--;
				s.bar.setProgress(Math.max(0f, (float) s.left / s.total));
				if (s.left <= 0) done.add(s);
			}
			for (Shown s : done) {
				s.bar.removeAllPlayers();
				q.remove(s);
			}
			if (q.isEmpty()) it.remove();
		}
	}

	static void remove(ServerPlayer p) {
		Deque<Shown> q = SHOWN.remove(p.getUUID());
		if (q != null) for (Shown s : q) s.bar.removeAllPlayers();
	}

	static void reset() {
		for (Deque<Shown> q : SHOWN.values()) for (Shown s : q) s.bar.removeAllPlayers();
		SHOWN.clear();
	}
}
