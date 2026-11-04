package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Госупгрейдер «Пятилетка за 5 секунд»: кладёшь предмет — с шансом получаешь предмет дороже,
 * иначе предмет уходит «в фонд мира».
 */
public final class Upgrader {
	public static final String NAME = "Апгрейдер Фортунатов";

	/** Цена предметов в «копейках». */
	public static final Map<Item, Integer> VALUE = new LinkedHashMap<>();

	static {
		VALUE.put(Items.DIRT, 1);
		VALUE.put(Items.COBBLESTONE, 1);
		VALUE.put(Items.COBBLED_DEEPSLATE, 1);
		VALUE.put(Items.SAND, 1);
		VALUE.put(Items.OAK_LOG, 2);
		VALUE.put(Items.WHEAT, 2);
		VALUE.put(Items.POTATO, 2);
		VALUE.put(Items.CARROT, 2);
		VALUE.put(Items.BREAD, 4);
		VALUE.put(Items.REDSTONE, 3);
		VALUE.put(Items.COAL, 4);
		VALUE.put(Items.COPPER_INGOT, 4);
		VALUE.put(Items.LAPIS_LAZULI, 5);
		VALUE.put(Items.IRON_INGOT, 10);
		VALUE.put(Items.GOLD_INGOT, 12);
		VALUE.put(Items.ENDER_PEARL, 15);
		VALUE.put(Items.BLAZE_ROD, 20);
		VALUE.put(Items.EMERALD, 25);
		VALUE.put(Items.DIAMOND, 60);
		VALUE.put(Items.IRON_BLOCK, 90);
		VALUE.put(Items.GOLDEN_APPLE, 100);
		VALUE.put(Items.NETHERITE_SCRAP, 150);
		VALUE.put(Items.TOTEM_OF_UNDYING, 400);
		VALUE.put(Items.DIAMOND_BLOCK, 540);
		VALUE.put(Items.NETHERITE_INGOT, 600);
		VALUE.put(Items.ELYTRA, 1200);
		VALUE.put(Items.ENCHANTED_GOLDEN_APPLE, 1500);
		VALUE.put(Items.NETHER_STAR, 2000);
	}

	public static final int[] MULT = {2, 3, 5, 10};
	public static final int[] CHANCE = {45, 29, 17, 8};

	private static final Set<UUID> SPINNING = new HashSet<>();

	public static int value(ItemStack s) {
		Integer v = VALUE.get(s.getItem());
		return v == null ? 0 : v * s.getCount();
	}

	/** Лучший предмет на заданную сумму. */
	public static ItemStack target(int value) {
		Item best = null;
		int bestCount = 0;
		for (Map.Entry<Item, Integer> e : VALUE.entrySet()) {
			int unit = e.getValue();
			if (unit > value) continue;
			int count = (int) Math.ceil((double) value / unit);
			int max = new ItemStack(e.getKey()).getMaxStackSize();
			if (count > max) continue;
			if (best == null || unit > VALUE.get(best)) {
				best = e.getKey();
				bestCount = count;
			}
		}
		if (best == null) {
			// очень дорого: несколько звёзд Незера
			return new ItemStack(Items.NETHER_STAR, Math.min(64, (int) Math.ceil(value / 2000.0)));
		}
		return new ItemStack(best, bestCount);
	}

	public static void open(ServerPlayer p) {
		ItemStack hand = p.getMainHandItem();
		int v = value(hand);
		Ui.Win w = Ui.npc(NAME + " — Госупгрейдер", ChatFormatting.LIGHT_PURPLE, new ItemStack(Items.ENDER_EYE),
				"Пятилетка за 5 секунд! Кладёте вещь — получаете вещь дороже. Или не получаете. Это называется плановая удача!");
		if (hand.isEmpty() || v == 0) {
			w.text("Возьмите в руку ценный предмет (уголь, железо, алмазы, изумруды...) и поговорите со мной снова.", ChatFormatting.YELLOW);
			w.text("Принимаю: " + String.join(", ", names()), ChatFormatting.DARK_GRAY);
			w.open(p);
			return;
		}
		w.icon(hand.copy(), Component.literal("Ставка: " + hand.getCount() + "× " + hand.getHoverName().getString() + " (стоимость " + v + ")").withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < MULT.length; i++) {
			ItemStack t = target(v * MULT[i]);
			w.btn(Ui.label("×" + MULT[i] + " — шанс " + CHANCE[i] + "%", i < 2 ? ChatFormatting.GREEN : i < 3 ? ChatFormatting.YELLOW : ChatFormatting.RED),
					"upg spin " + i, "Можно выиграть: " + t.getCount() + "× " + t.getHoverName().getString());
		}
		w.text("Проигранные вещи идут в Фонд Мира. Фонд Мира очень богат.", ChatFormatting.DARK_GRAY);
		w.open(p);
	}

	private static String[] names() {
		String[] n = new String[Math.min(12, VALUE.size())];
		int i = 0;
		for (Item it : VALUE.keySet()) {
			if (i >= n.length) break;
			n[i++] = new ItemStack(it).getHoverName().getString();
		}
		return n;
	}

	public static void handle(ServerPlayer p, String[] a) {
		if (Npcs.near(p, "upgrader", 10) == null) {
			Plaque.show(p, Plaque.Kind.INFO, "Апгрейдер работает только в киоске «Госудача».", 4);
			return;
		}
		if (a.length < 3 || !a[1].equals("spin") || SPINNING.contains(p.getUUID())) return;
		int i;
		try {
			i = Integer.parseInt(a[2]);
		} catch (NumberFormatException e) {
			return;
		}
		if (i < 0 || i >= MULT.length) return;
		ItemStack hand = p.getMainHandItem();
		int v = value(hand);
		if (v == 0) return;
		ItemStack bet = hand.copy();
		hand.setCount(0);
		ItemStack prize = target(v * MULT[i]);
		boolean win = CommunismMod.RNG.nextInt(100) < CHANCE[i];
		SPINNING.add(p.getUUID());
		Item[] reel = VALUE.keySet().toArray(new Item[0]);
		for (int k = 0; k < 14; k++) {
			final int step = k;
			Scheduler.later(1 + k * (2 + k / 4), () -> {
				ItemStack shown = step == 13 ? (win ? prize : new ItemStack(Items.BARRIER)) : new ItemStack(reel[CommunismMod.RNG.nextInt(reel.length)]);
				Msg.actionbar(p, Msg.t("[ ", ChatFormatting.DARK_GRAY).append(Msg.t(shown.getHoverName().getString(), step == 13 ? (win ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)).append(Msg.t(" ]", ChatFormatting.DARK_GRAY)));
				Msg.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 0.8f + step * 0.08f);
			});
		}
		Scheduler.later(75, () -> {
			SPINNING.remove(p.getUUID());
			if (win) {
				Msg.give(p, prize.copy());
				Msg.title(p, Msg.t("АПГРЕЙД!", ChatFormatting.GREEN, ChatFormatting.BOLD), Msg.t(prize.getCount() + "× " + prize.getHoverName().getString(), ChatFormatting.GOLD));
				Plaque.show(p, Plaque.Kind.GOOD, "Апгрейд ×" + MULT[i] + " удался: " + prize.getCount() + "× " + prize.getHoverName().getString() + "!", 7);
				Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
				if (MULT[i] >= 5) Plaque.all(CommunismMod.server, Plaque.Kind.MONEY, p.getName().getString() + " выиграл в Госудачу ×" + MULT[i] + "! Подозрительно... КГБ уже выехал.", 7);
			} else {
				Msg.title(p, Msg.t("НЕ ПОВЕЗЛО", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t(bet.getCount() + "× " + bet.getHoverName().getString() + " ушло в Фонд Мира", ChatFormatting.GRAY));
				Plaque.show(p, Plaque.Kind.BAD, "Ставка проиграна. Фонд Мира благодарит вас за вклад!", 6);
				Msg.sound(p, SoundEvents.VILLAGER_NO, 1f, 0.7f);
			}
		});
	}
}
