package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Выговоры от КГБ и ссылка в Сибирь. */
public final class Discipline {
	public static final String KGB = "Майор Бдительный";
	public static final int EXILE_SECONDS = 45;
	private static final Map<UUID, Integer> EXILED = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	public static void vygovor(ServerPlayer p, String reason) {
		// не чаще одного выговора в 3 секунды — даже КГБ нужно время на бумаги
		Long last = LAST.get(p.getUUID());
		if (last != null && CommunismMod.tick - last < 60) return;
		LAST.put(p.getUUID(), CommunismMod.tick);

		int v = Data.add(p, Data.vyg, 1);
		Msg.say(p, KGB, "Товарищ, вам выговор: " + reason + ". (" + v + "/3)");
		Msg.sound(p, SoundEvents.VILLAGER_NO, 1f, 0.6f);
		if (v >= 3) {
			Data.set(p, Data.vyg, 0);
			exile(p);
		}
	}

	public static boolean isExiled(ServerPlayer p) {
		return EXILED.containsKey(p.getUUID());
	}

	public static void exile(ServerPlayer p) {
		MinecraftServer s = CommunismMod.server;
		Msg.broadcast(s, Msg.party()
				.append(Msg.t(p.getName().getString(), ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Msg.t(" получил 3 выговора и отправлен в Сибирь на " + EXILE_SECONDS + " секунд! Пусть подумает о своём поведении.", ChatFormatting.AQUA)));
		Msg.title(p, Msg.t("ССЫЛКА В СИБИРЬ", ChatFormatting.AQUA, ChatFormatting.BOLD), Msg.t("Холодно? Это воспитательный момент.", ChatFormatting.WHITE));
		p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, EXILE_SECONDS * 20, 3));
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EXILE_SECONDS * 20, 1));
		if (Data.built()) {
			ServerLevel ow = s.overworld();
			teleport(p, ow, Data.px() + Structures.SIBIR_DX + 0.5, Data.py(), Data.pz() + Structures.SIBIR_DZ + 0.5);
		}
		EXILED.put(p.getUUID(), EXILE_SECONDS);
	}

	public static void teleport(ServerPlayer p, ServerLevel l, double x, double y, double z) {
		p.teleportTo(l, x, y, z, Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
		p.resetFallDistance();
	}

	static void secondTick(MinecraftServer s) {
		Iterator<Map.Entry<UUID, Integer>> it = EXILED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			int left = e.getValue() - 1;
			ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
			if (left <= 0) {
				it.remove();
				if (p != null) {
					Msg.say(p, KGB, "Срок окончен. Возвращайтесь к труду, товарищ. И больше не попадайтесь.");
					if (Data.built()) teleport(p, s.overworld(), Data.px() + 0.5, Data.py(), Data.pz() + 3.5);
				}
			} else {
				e.setValue(left);
				if (p != null) {
					p.setTicksFrozen(Math.min(130, p.getTicksFrozen() + 40));
					Msg.actionbar(p, Msg.t("Сибирь: осталось " + left + " сек.", ChatFormatting.AQUA));
				}
			}
		}
	}

	static void reset() {
		EXILED.clear();
		LAST.clear();
	}
}
