package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/** Советские товары народного потребления (переименованные ванильные предметы). */
public final class Goods {
	public static final String FORM_NAME = "Форма №37-Б";

	public static ItemStack named(Item item, int count, String name, ChatFormatting color, String... lore) {
		ItemStack s = new ItemStack(item, count);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)).withStyle(color));
		if (lore.length > 0) {
			List<Component> lines = new ArrayList<>();
			for (String l : lore) lines.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
			s.set(DataComponents.LORE, new ItemLore(lines));
		}
		return s;
	}

	public static ItemStack ench(ServerLevel l, ItemStack s, ResourceKey<Enchantment> key, int lvl) {
		Holder<Enchantment> h = l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		s.enchant(h, lvl);
		return s;
	}

	public static String nameOf(ItemStack s) {
		Component c = s.get(DataComponents.CUSTOM_NAME);
		return c == null ? "" : c.getString();
	}

	public static ItemStack sausage(int n) {
		return named(Items.COOKED_PORKCHOP, n, "Колбаса по талону", ChatFormatting.LIGHT_PURPLE,
				"Докторская. Почти.", "Очередь отстояна честно.");
	}

	public static boolean isSausage(ItemStack s) {
		return s.is(Items.COOKED_PORKCHOP) && "Колбаса по талону".equals(nameOf(s));
	}

	public static ItemStack bread(int n) {
		return named(Items.BREAD, n, "Батон Нарезной", ChatFormatting.YELLOW, "ГОСТ соблюдён.");
	}

	public static ItemStack corn(int n) {
		return named(Items.GOLDEN_CARROT, n, "Кукуруза — царица полей", ChatFormatting.GOLD,
				"Посажена даже там, где не растёт.");
	}

	public static ItemStack partyCard() {
		return named(Items.PAPER, 1, "Партбилет", ChatFormatting.RED,
				"Предъявлять по первому требованию.", "Потеря карается выговором.");
	}

	public static ItemStack form37b() {
		return named(Items.PAPER, 1, FORM_NAME, ChatFormatting.WHITE,
				"Заполнить в трёх экземплярах.", "Сдать Генсеку в течение 5 минут!", "Печать: есть. Подпись: неразборчиво.");
	}

	public static ItemStack ushanka(ServerLevel l) {
		ItemStack s = named(Items.LEATHER_HELMET, 1, "Ушанка", ChatFormatting.AQUA, "Тепло. По-советски.");
		ench(l, s, Enchantments.PROTECTION, 2);
		return ench(l, s, Enchantments.UNBREAKING, 3);
	}

	public static ItemStack vatnik(ServerLevel l) {
		ItemStack s = named(Items.LEATHER_CHESTPLATE, 1, "Ватник", ChatFormatting.DARK_AQUA, "Непробиваем для критики.");
		ench(l, s, Enchantments.PROTECTION, 3);
		return ench(l, s, Enchantments.UNBREAKING, 3);
	}

	public static ItemStack kvas() {
		ItemStack s = named(Items.POTION, 1, "Квас «Богатырский»", ChatFormatting.GOLD, "Даёт силу. Сухой закон не распространяется... наверное.");
		s.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_STRENGTH));
		return s;
	}

	public static ItemStack sickle(ServerLevel l) {
		ItemStack s = named(Items.IRON_HOE, 1, "Серп Ударника", ChatFormatting.RED, "Половина герба. Вторая — у Молота.");
		ench(l, s, Enchantments.SHARPNESS, 4);
		ench(l, s, Enchantments.EFFICIENCY, 3);
		return ench(l, s, Enchantments.UNBREAKING, 3);
	}

	public static ItemStack hammer(ServerLevel l) {
		ItemStack s = named(Items.MACE, 1, "Молот Пролетариата", ChatFormatting.DARK_RED, "Вторая половина герба.", "Бьёт по капиталу с высоты.");
		ench(l, s, Enchantments.DENSITY, 3);
		return ench(l, s, Enchantments.UNBREAKING, 3);
	}

	public static ItemStack stakhanovPick(ServerLevel l) {
		ItemStack s = named(Items.DIAMOND_PICKAXE, 1, "Кирка Стахановца", ChatFormatting.AQUA, "Норма: 14 смен за одну.");
		ench(l, s, Enchantments.EFFICIENCY, 4);
		ench(l, s, Enchantments.FORTUNE, 2);
		return ench(l, s, Enchantments.UNBREAKING, 3);
	}

	public static ItemStack komsomolPick(ServerLevel l) {
		ItemStack s = named(Items.IRON_PICKAXE, 1, "Кирка Комсомольца", ChatFormatting.GREEN, "Выдана за активную жизненную позицию.");
		return ench(l, s, Enchantments.EFFICIENCY, 2);
	}

	public static ItemStack medal() {
		return named(Items.TOTEM_OF_UNDYING, 1, "Медаль «За отвагу»", ChatFormatting.GOLD, "Спасает от смерти. Один раз. По разнарядке.");
	}

	public static ItemStack heroStar() {
		return named(Items.NETHER_STAR, 1, "Звезда Героя Труда", ChatFormatting.GOLD, "Висит на груди. Светится от гордости.");
	}

	public static ItemStack orderOfVictory() {
		return named(Items.NETHER_STAR, 1, "Орден «Победа над Капиталом»", ChatFormatting.RED,
				"Вручён за построение коммунизма", "в отдельно взятом мире Minecraft.");
	}

	public static ItemStack wings() {
		return named(Items.ELYTRA, 1, "Крылья Советов", ChatFormatting.AQUA, "Выше, дальше, коммунистичнее!");
	}

	public static ItemStack marshalBaton(ServerLevel l) {
		ItemStack s = named(Items.BLAZE_ROD, 1, "Маршальский Жезл", ChatFormatting.DARK_RED, "Указывает направление в светлое будущее.");
		ench(l, s, Enchantments.KNOCKBACK, 5);
		return ench(l, s, Enchantments.FIRE_ASPECT, 2);
	}

	public static ItemStack sanatorium(int n) {
		return named(Items.GOLDEN_APPLE, n, "Путёвка в санаторий", ChatFormatting.YELLOW, "Ялта. Две недели. Всё включено (кроме тепла).");
	}

	public static ItemStack reserveDiamonds(int n) {
		return named(Items.DIAMOND, n, "Алмазы из Госрезерва", ChatFormatting.AQUA, "Учёт и контроль!");
	}

	public static ItemStack apartmentOrder() {
		return named(Items.PAPER, 1, "Ордер на квартиру", ChatFormatting.GREEN, "Очередь подошла через 0 лет.", "Это рекорд!");
	}
}
