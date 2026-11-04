package com.communism;

import net.minecraft.ChatFormatting;
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
			new Offer("Квас «Богатырский»", 8, l -> Goods.kvas()),
			new Offer("Ушанка (Защита II)", 10, Goods::ushanka),
			new Offer("Ватник (Защита III)", 15, Goods::vatnik),
			new Offer("Серп Ударника", 18, Goods::sickle),
			new Offer("Путёвка в санаторий ×3", 20, l -> Goods.sanatorium(3)),
			new Offer("Алмазы из Госрезерва ×3", 30, l -> Goods.reserveDiamonds(3)),
			new Offer("Кирка Стахановца", 45, Goods::stakhanovPick),
			new Offer("Медаль «За отвагу» (тотем)", 60, l -> Goods.medal()),
			new Offer("Молот Пролетариата", 90, Goods::hammer),
			new Offer("Крылья Советов (элитры)", 150, l -> Goods.wings()),
			new Offer("Партбилет (если потеряли)", 1, l -> Goods.partyCard()));

	public static int price(Offer o) {
		return Plan.stage() >= Plan.VICTORY ? 0 : o.price();
	}

	public static void show(ServerPlayer p) {
		if (Data.get("deficit") > 0 && Plan.stage() < Plan.VICTORY) {
			Ui.npc(Npcs.ZAVHOZ, ChatFormatting.YELLOW, new ItemStack(Items.CHEST), "Товара нет! Дефицит! Приходите через " + Msg.time(Data.get("deficit")) + ". Или в следующей пятилетке.").open(p);
			return;
		}
		Ui.Win w = Ui.npc(Npcs.ZAVHOZ + " — Склад Народа", ChatFormatting.YELLOW, new ItemStack(Items.CHEST),
				Plan.stage() >= Plan.VICTORY ? "При коммунизме всё бесплатно! Берите что хотите!" : "Всё строго по талонам, товарищ! Выбирайте.");
		w.text("У вас " + Ranks.trud(p) + " трудодней.", ChatFormatting.GOLD);
		for (int i = 0; i < OFFERS.size(); i++) {
			Offer o = OFFERS.get(i);
			boolean can = Ranks.trud(p) >= price(o);
			w.btn(Ui.label(o.name() + " — " + price(o), can ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), "shop " + (i + 1), can ? "Купить" : "Не хватает трудодней");
		}
		w.open(p);
	}

	public static void buy(ServerPlayer p, int n) {
		if (n < 1 || n > OFFERS.size()) return;
		if (Npcs.near(p, "zavhoz", 12) == null) {
			Plaque.show(p, Plaque.Kind.INFO, "Покупать можно только у Завхоза (Склад Народа, юго-запад площади).", 5);
			return;
		}
		if (Data.get("deficit") > 0 && Plan.stage() < Plan.VICTORY) return;
		Offer o = OFFERS.get(n - 1);
		if (!Ranks.spend(p, price(o))) {
			Plaque.show(p, Plaque.Kind.BAD, "Не хватает трудодней: нужно " + price(o) + ", у вас " + Ranks.trud(p) + ".", 5);
			Msg.sound(p, SoundEvents.VILLAGER_NO, 1f, 1f);
			return;
		}
		Msg.give(p, o.make().apply(Msg.level(p)));
		Plaque.show(p, Plaque.Kind.MONEY, "Выдано: " + o.name() + ". Осталось " + Ranks.trud(p) + " трудодней.", 5);
		Msg.sound(p, SoundEvents.VILLAGER_YES, 1f, 1f);
		show(p);
	}
}
