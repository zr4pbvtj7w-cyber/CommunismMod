package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** КОММУНИЗМ ПОСТРОЕН. */
public final class Victory {
	public static void win(MinecraftServer s, ServerLevel l, double x, double y, double z) {
		if (Plan.stage() >= Plan.VICTORY) return;
		Data.set("stage", Plan.VICTORY);
		Msg.drop(l, x, y + 1, z, new ItemStack(Items.NETHER_STAR, 1));
		Msg.drop(l, x, y + 1, z, new ItemStack(Items.DIAMOND_BLOCK, 3));
		Msg.titleAll(s, Msg.t("КОММУНИЗМ ПОСТРОЕН!", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t("Каждому — по потребностям!", ChatFormatting.GOLD));
		Msg.soundAll(s, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
		Msg.sayAll(s, Boss.NAME, "Нееет! Мои акции... мои офшоры... я банкрот!..");
		Scheduler.later(60, () -> {
			Msg.sayAll(s, Npcs.DICTATOR, "ТОВАРИЩИ! Мы сделали это! Мистер Капитал повержен, пятилетка выполнена, коммунизм построен!");
			Msg.sayAll(s, Npcs.DICTATOR, "Отныне — никакого голода, магазин Завхоза бесплатный, а я выдаю каждому по потребностям раз в 5 минут!");
			Plaque.all(s, Plaque.Kind.GOOD, "Поздравляем! Вы прошли «Мод про Коммунизм». Указы продолжатся — веселье бесконечно, как очередь за колбасой.", 15);
		});
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			Ranks.addTrud(p, 200, "построение коммунизма");
			Msg.give(p, Goods.orderOfVictory());
		}
		Scheduler.repeat(15, 20, () -> {
			for (ServerPlayer p : s.getPlayerList().getPlayers()) Msg.fireworksAround(p, 3);
		});
	}
}
