package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Трудодни (валюта), партстаж и партийные ранги. */
public final class Ranks {
	public static final String[] NAMES = {"Пионер", "Комсомолец", "Член Партии", "Ударник Труда", "Герой Труда", "Маршал Коммунизма"};
	public static final int[] THRESHOLDS = {0, 40, 120, 250, 450, 800};
	public static final ChatFormatting[] COLORS = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED};

	public static int rankOf(int stazh) {
		int r = 0;
		for (int i = 0; i < THRESHOLDS.length; i++) if (stazh >= THRESHOLDS[i]) r = i;
		return r;
	}

	public static int rank(ServerPlayer p) {
		return rankOf(Data.get(p, Data.stazh));
	}

	public static int trud(ServerPlayer p) {
		return Data.get(p, Data.trud);
	}

	public static void addTrud(ServerPlayer p, int amount, String reason) {
		if (amount <= 0) return;
		int before = rank(p);
		Data.add(p, Data.trud, amount);
		Data.add(p, Data.stazh, amount);
		Msg.actionbar(p, Msg.t("+" + amount + " трудодней", ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Msg.t(reason == null ? "" : " — " + reason, ChatFormatting.YELLOW)));
		int after = rank(p);
		if (after > before) rankUp(p, after);
	}

	/** Списать трудодни. false — если не хватает. */
	public static boolean spend(ServerPlayer p, int amount) {
		if (amount <= 0) return true;
		int have = trud(p);
		if (have < amount) return false;
		Data.set(p, Data.trud, have - amount);
		return true;
	}

	private static void rankUp(ServerPlayer p, int r) {
		updateTeam(p);
		Msg.broadcast(CommunismMod.server, Msg.party()
				.append(Msg.t(p.getName().getString(), ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Msg.t(" повышен до звания ", ChatFormatting.GOLD))
				.append(Msg.t(NAMES[r], COLORS[r], ChatFormatting.BOLD))
				.append(Msg.t("! Ура, товарищи!", ChatFormatting.GOLD)));
		Msg.title(p, Msg.t("Новое звание!", ChatFormatting.GOLD, ChatFormatting.BOLD), Msg.t(NAMES[r], COLORS[r]));
		Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
		Msg.fireworksAround(p, 3);
		ServerLevel l = Msg.level(p);
		switch (r) {
			case 1 -> Msg.give(p, Goods.komsomolPick(l));
			case 2 -> { Msg.give(p, Goods.vatnik(l)); Msg.give(p, Goods.sanatorium(2)); }
			case 3 -> Msg.give(p, Goods.stakhanovPick(l));
			case 4 -> { Msg.give(p, Goods.heroStar()); Msg.give(p, Goods.medal()); Msg.give(p, Goods.reserveDiamonds(5)); }
			case 5 -> { Msg.give(p, Goods.marshalBaton(l)); Msg.give(p, Goods.wings()); }
			default -> { }
		}
		Msg.info(p, "Подарок от Партии за новое звание уже в инвентаре!");
	}

	public static void updateTeam(ServerPlayer p) {
		int r = rank(p);
		Scoreboard sb = CommunismMod.server.getScoreboard();
		String tn = "cm_rank" + r;
		PlayerTeam t = sb.getPlayerTeam(tn);
		if (t == null) {
			t = sb.addPlayerTeam(tn);
			t.setPlayerPrefix(Component.literal("[" + NAMES[r] + "] ").withStyle(COLORS[r]));
			t.setColor(COLORS[r]);
		}
		sb.addPlayerToTeam(p.getScoreboardName(), t);
	}
}
