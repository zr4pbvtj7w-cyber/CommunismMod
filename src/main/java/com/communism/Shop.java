package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Function;

/** Магазин Завхоза Петровича: всё по талонам (за трудодни). */
public final class Shop {
	public record Offer(String name, int price, Function<ServerLevel, ItemStack> make) {}

	public static final List<Offer> OFFERS = List.of(
			new Offer("Колбаса по талону ×8", 4, l -> Goods.sausage(8)),
			new Offer("Батон Нарезной ×16", 5, l -> Goods.bread(16)),
			new Offer("Ракеты «Восток» ×16", 8, l -> new ItemStack(Items.FIREWORK_ROCKET, 16)),
			new Offer("Квас «Богатырский» (Сила II)", 8, l -> Goods.kvas()),
			new Offer("Ушанка (Защита II)", 10, Goods::ushanka),
			new Offer("Ватник (Защита III)", 15, Goods::vatnik),
			new Offer("Серп Ударника (Острота IV)", 18, Goods::sickle),
			new Offer("Путёвка в санаторий ×3 (зол. яблоки)", 20, l -> Goods.sanatorium(3)),
			new Offer("Алмазы из Госрезерва ×3", 30, l -> Goods.reserveDiamonds(3)),
			new Offer("Кирка Стахановца", 45, Goods::stakhanovPick),
			new Offer("Медаль «За отвагу» (тотем)", 60, l -> Goods.medal()),
			new Offer("Молот Пролетариата (булава)", 90, Goods::hammer),
			new Offer("Крылья Советов (элитры)", 150, l -> Goods.wings()));

	public static int price(Offer o) {
		return Laws.is(Laws.COMMUNISM) ? 0 : o.price();
	}

	public static void show(ServerPlayer p) {
		if (Data.get("deficit") > 0 && !Laws.is(Laws.COMMUNISM)) {
			Msg.say(p, Npcs.ZAVHOZ, "Товара нет! Дефицит! Приходите через " + Msg.time(Data.get("deficit")) + ". Или завтра. Или в следующей пятилетке.");
			return;
		}
		Msg.say(p, Npcs.ZAVHOZ, Laws.is(Laws.COMMUNISM) ? "При коммунизме всё бесплатно! Берите что хотите!"
				: "Всё строго по талонам, товарищ! У вас " + Ranks.trud(p) + " трудодней. Нажмите на товар в чате:");
		for (int i = 0; i < OFFERS.size(); i++) {
			Offer o = OFFERS.get(i);
			boolean can = Ranks.trud(p) >= price(o);
			String cmd = "/komm kupit " + (i + 1);
			MutableComponent line = Msg.t(" [" + (i + 1) + "] ", ChatFormatting.DARK_GRAY)
					.append(Msg.t(o.name(), can ? ChatFormatting.WHITE : ChatFormatting.GRAY))
					.append(Msg.t(" — " + price(o) + " тр. ", ChatFormatting.GOLD))
					.append(Component.literal("[КУПИТЬ]").withStyle(st -> st
							.withColor(can ? ChatFormatting.GREEN : ChatFormatting.DARK_RED)
							.withBold(true)
							.withClickEvent(new ClickEvent.RunCommand(cmd))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Купить: " + o.name())))));
			p.sendSystemMessage(line);
		}
	}

	public static void buy(ServerPlayer p, int n) {
		if (n < 1 || n > OFFERS.size()) {
			Msg.info(p, "Такого товара нет. Вообще ничего нет. Это СССР.");
			return;
		}
		if (Npcs.findNear(p, "zavhoz", 12) == null) {
			Msg.info(p, "Покупать можно только у Завхоза Петровича (подойдите к нему на складе у Красной площади).");
			return;
		}
		if (Data.get("deficit") > 0 && !Laws.is(Laws.COMMUNISM)) {
			Msg.say(p, Npcs.ZAVHOZ, "Сказал же — дефицит!");
			return;
		}
		Offer o = OFFERS.get(n - 1);
		if (!Ranks.spend(p, price(o))) {
			Msg.say(p, Npcs.ZAVHOZ, "Не хватает трудодней! Нужно " + price(o) + ", а у вас " + Ranks.trud(p) + ". Идите работать!");
			Msg.sound(p, SoundEvents.VILLAGER_NO, 1f, 1f);
			return;
		}
		Msg.give(p, o.make().apply(Msg.level(p)));
		Msg.say(p, Npcs.ZAVHOZ, "Выдано: " + o.name() + ". Распишитесь тут, тут и вот тут. Осталось " + Ranks.trud(p) + " трудодней.");
		Msg.sound(p, SoundEvents.VILLAGER_YES, 1f, 1f);
	}
}
