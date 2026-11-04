package com.communism;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Автопроверка для CI: ./gradlew runServer -Pselftest
 * Строит город, ставит NPC, проверяет законы, правосудие, указы, отставку Вождя и босса — и выключает сервер.
 */
final class SelfTest {
	private static boolean ok = true;

	private static void check(boolean cond, String what) {
		if (cond) CommunismMod.LOG.info("SELFTEST ok: {}", what);
		else {
			ok = false;
			CommunismMod.LOG.error("SELFTEST FAIL: {}", what);
		}
	}

	static void run(MinecraftServer s) {
		try {
			ServerLevel l = s.overworld();
			int y = Math.max(l.getSeaLevel() + 1, Math.min(200, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0)));
			CommunismMod.LOG.info("SELFTEST plaza y = {}", y);
			Events.buildPlazaAt(s, 0, y, 0, () -> {
				try {
					afterBuild(s, l, y);
				} catch (Throwable t) {
					ok = false;
					CommunismMod.LOG.error("COMMUNISM SELFTEST FAILED", t);
				}
				Scheduler.later(400, () -> {
					CommunismMod.LOG.info(ok ? "COMMUNISM SELFTEST OK" : "COMMUNISM SELFTEST FAILED");
					s.halt(false);
				});
			});
		} catch (Throwable t) {
			CommunismMod.LOG.error("COMMUNISM SELFTEST FAILED", t);
			s.halt(false);
		}
	}

	private static void afterBuild(MinecraftServer s, ServerLevel l, int y) {
		int npcs = 0;
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(Npcs.TAG)) npcs++;
		check(npcs == Npcs.ROLES.size(), "NPC: " + npcs + "/" + Npcs.ROLES.size());
		check(l.getBlockState(new BlockPos(0, y - 1, 0)).is(Blocks.GOLD_BLOCK), "нулевой километр");
		check(l.getBlockState(new BlockPos(31, y, 25)).is(Blocks.RAIL), "рельсы до тюрьмы");
		check(l.getBlockState(new BlockPos(45, y, 58)).is(Blocks.COBBLESTONE), "камень в камере");
		check(l.getBlockState(new BlockPos(0, y + 41, -49)).is(Blocks.GOLD_BLOCK), "шпиль Дворца Советов");
		check(l.getBlockState(new BlockPos(18, y + 1, -12)).is(Blocks.STONE_BUTTON), "кнопка автомата газировки");
		check(Justice.length(City.railPath()) > 30, "длина рельсов " + Justice.length(City.railPath()));

		// законы
		for (Laws.Law law : Laws.ALL) Laws.enact(s, law.id(), "тест");
		check(Laws.active().size() == Laws.SLOTS, "одновременно действуют 3 закона");
		check(Laws.enactedCount() >= Laws.ALL.size(), "принято законов: " + Laws.enactedCount());

		// фейковый игрок
		FakePlayer fp = FakePlayer.get(l);
		fp.setPos(5, y, 5);
		fp.getInventory().add(new ItemStack(Items.GOLD_INGOT, 9));
		Laws.confiscateGold(fp);
		check(fp.getInventory().countItem(Items.GOLD_INGOT) == 0, "Закон о буржуйском металле изымает золото");
		Data.set("law0", Laws.VEGETARIAN);
		check(Laws.forbidItem(fp, new ItemStack(Items.COOKED_BEEF)), "вегетарианство запрещает мясо");
		check(!Laws.forbidItem(fp, Goods.sausage(1)), "колбаса — не мясо");
		Data.set("law0", Laws.DRY);
		check(Laws.forbidItem(fp, new ItemStack(Items.POTION)), "сухой закон запрещает зелья");
		check(!Justice.allowBreakInCity(fp, new BlockPos(3, y - 1, 3), Blocks.STONE_BRICKS.defaultBlockState()), "в городе ломать нельзя");
		check(Justice.allowBreakInCity(fp, new BlockPos(-30, y, -6), Blocks.WHEAT.defaultBlockState()), "урожай собирать можно");

		// окна
		Leader.openVozhd(fp);
		Npcs.openLaws(fp);
		for (Npcs.Role r : Npcs.ROLES) {
			if (r.id().equals("zina") || r.id().equals("valya") || r.id().equals("kgb")) continue; // нужны живые игроки
			Npcs.open(fp, r.id());
		}
		Loans.open(fp);
		fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 4));
		Upgrader.open(fp);
		check(Upgrader.target(240 * 2).getCount() > 0, "апгрейдер подбирает приз: " + Upgrader.target(480));
		KommCommand.menu(fp);
		Plaque.show(fp, Plaque.Kind.LAW, "проверка плашки", 1);

		// правосудие: вагонетки, милиционер, снежки
		Justice.selfTest(l, y);
		Npcs.spawnBurzhuy(l, new BlockPos(5, y, 5));
		Boss.summon(s);
		Plan.updateBar(s);
		for (int i = 0; i < 4; i++) Data.set("prog" + i, Plan.goal(i));
		Plan.completeStage(s);
		for (int i = 0; i < Decrees.ALL.size(); i++) Decrees.run(s, i);
		Structures.miniHouse(l, new BlockPos(90, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 90, 90), 90));
		Leader.retire(s);
		check(Leader.retired(), "Вождь ушёл на покой");

		Scheduler.later(300, () -> {
			List<Entity> kill = new ArrayList<>();
			int temp = 0;
			for (Entity e : l.getAllEntities()) {
				if (e.getTags().contains(Boss.TAG) || e.getTags().contains(Npcs.BURZHUY)) kill.add(e);
				if (e.getTags().contains(Npcs.TEMP)) temp++;
			}
			check(kill.size() >= 2, "враги появились: " + kill.size());
			check(temp == 0, "временные NPC убраны: " + temp);
			boolean vozhd = false;
			for (Entity e : l.getAllEntities()) if (e.getTags().contains("cm_role_vozhd")) vozhd = true;
			check(!vozhd, "Вождь улетел на ракете");
			for (Entity e : kill) e.discard();
		});
	}
}
