package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Задания Политрука Задорного. */
public final class Quests {
	public record Quest(String title, String desc, int goal, int reward) {}

	public static final Quest[] QUESTS = {
			null,
			new Quest("Раскулачивание", "Уничтожить кулаков-зомби", 10, 15),
			new Quest("Белая гвардия", "Разбить белогвардейцев-скелетов", 8, 15),
			new Quest("Шахтёрская смена", "Добыть руды (любой)", 24, 18),
			new Quest("Лесозаготовка", "Нарубить брёвен", 48, 12),
			new Quest("Шпионская сеть", "Обезвредить пауков-шпионов", 6, 14),
			new Quest("Антисаботаж", "Обезвредить криперов-саботажников", 4, 20),
			new Quest("Враги народа", "Уничтожить любых враждебных мобов", 20, 20),
			new Quest("Метростроевец", "Выкопать камня (камень, глубинный сланец, туф...)", 150, 15),
	};

	public static void talk(ServerPlayer p) {
		int type = Data.get(p, Data.qtype);
		if (type <= 0 || type >= QUESTS.length) {
			type = 1 + CommunismMod.RNG.nextInt(QUESTS.length - 1);
			Quest q = QUESTS[type];
			int goal = q.goal() + CommunismMod.RNG.nextInt(q.goal() / 2 + 1);
			Data.set(p, Data.qtype, type);
			Data.set(p, Data.qprog, 0);
			Data.set(p, Data.qgoal, goal);
			Msg.say(p, Npcs.POLITRUK, "Партия поручает вам задание «" + q.title() + "»: " + q.desc() + " — " + goal + " шт. Награда: " + q.reward() + " трудодней!");
			Msg.sound(p, SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
			return;
		}
		Quest q = QUESTS[type];
		int prog = Data.get(p, Data.qprog), goal = Data.get(p, Data.qgoal);
		if (prog >= goal) {
			Data.set(p, Data.qtype, 0);
			Msg.say(p, Npcs.POLITRUK, "Задание «" + q.title() + "» выполнено! Вы — пример для всей пионерии!");
			Ranks.addTrud(p, q.reward(), "задание Политрука");
			ItemStack[] bonus = {new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.COAL, 16), Goods.sausage(4),
					new ItemStack(Items.EXPERIENCE_BOTTLE, 8), new ItemStack(Items.ARROW, 24), new ItemStack(Items.GOLDEN_CARROT, 4)};
			Msg.give(p, bonus[CommunismMod.RNG.nextInt(bonus.length)].copy());
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
			Msg.say(p, Npcs.POLITRUK, "Приходите за новым заданием!");
		} else if (p.isShiftKeyDown()) {
			Data.set(p, Data.qtype, 0);
			Discipline.vygovor(p, "отказ от партийного задания");
		} else {
			Msg.say(p, Npcs.POLITRUK, "Задание «" + q.title() + "»: " + q.desc() + " — " + prog + "/" + goal + ". Не подведите Партию! (Shift+ПКМ — отказаться, но это выговор)");
		}
	}

	private static void progress(ServerPlayer p, int type, int amount) {
		if (Data.get(p, Data.qtype) != type) return;
		int goal = Data.get(p, Data.qgoal);
		int prog = Data.get(p, Data.qprog);
		if (prog >= goal) return;
		prog = Math.min(goal, prog + amount);
		Data.set(p, Data.qprog, prog);
		if (prog >= goal) {
			Msg.title(p, Msg.t("Задание выполнено!", ChatFormatting.GREEN, ChatFormatting.BOLD), Msg.t("Вернитесь к Политруку за наградой", ChatFormatting.WHITE));
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1.5f);
		} else {
			Msg.actionbar(p, Msg.t(QUESTS[type].title() + ": " + prog + "/" + goal, ChatFormatting.YELLOW));
		}
	}

	public static void onKill(ServerPlayer p, LivingEntity e) {
		EntityType<?> t = e.getType();
		if (t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED || t == EntityType.ZOMBIE_VILLAGER) progress(p, 1, 1);
		if (t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.BOGGED) progress(p, 2, 1);
		if (t == EntityType.SPIDER || t == EntityType.CAVE_SPIDER) progress(p, 5, 1);
		if (t == EntityType.CREEPER) progress(p, 6, 1);
		if (t.getCategory() == MobCategory.MONSTER) progress(p, 7, 1);
	}

	public static void onMine(ServerPlayer p, BlockState s) {
		if (s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
				|| s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.EMERALD_ORES)) {
			progress(p, 3, 1);
		}
		if (s.is(BlockTags.LOGS)) progress(p, 4, 1);
		if (s.is(BlockTags.BASE_STONE_OVERWORLD)) progress(p, 8, 1);
	}
}
