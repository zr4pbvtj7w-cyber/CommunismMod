package com.communism;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Автопроверка для CI: ./gradlew runServer -Pselftest
 * Строит все постройки, ставит NPC, вызывает все указы, законы и босса — и выключает сервер.
 */
final class SelfTest {
	static void run(MinecraftServer s) {
		boolean ok = true;
		try {
			ServerLevel l = s.overworld();
			int y = Math.max(l.getSeaLevel() + 1, Math.min(200, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0)));
			CommunismMod.LOG.info("SELFTEST plaza y = {}", y);
			Events.buildPlazaAt(s, 0, y, 0);
			int npcs = 0;
			for (Entity e : l.getAllEntities()) if (e.getTags().contains(Npcs.TAG)) npcs++;
			CommunismMod.LOG.info("SELFTEST npcs spawned: {}", npcs);
			if (npcs != Npcs.ROLES.length) {
				ok = false;
				CommunismMod.LOG.error("SELFTEST expected {} NPCs", Npcs.ROLES.length);
			}
			Structures.miniHouse(l, new BlockPos(80, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 80, 80), 80));
			for (String n : Structures.NAMES) Structures.buildNamed(l, n, new BlockPos(150, y, 150));
			for (int i = 0; i < Decrees.ALL.size(); i++) Decrees.run(s, i);
			for (int i = 0; i < Laws.NAMES.length; i++) Laws.change(s, i);
			Laws.change(s, -1);
			for (Shop.Offer o : Shop.OFFERS) o.make().apply(l);
			Npcs.spawnBurzhuy(l, new BlockPos(5, y, 5));
			Boss.summon(s);
			Plan.updateBar(s);
			Plan.statusText();
			for (int i = 0; i < 4; i++) Data.set("prog" + i, Plan.goal(i));
			Plan.completeStage(s);
			// убрать мобов
			List<Entity> kill = new ArrayList<>();
			for (Entity e : l.getAllEntities()) if (e.getTags().contains(Boss.TAG) || e.getTags().contains(Npcs.BURZHUY)) kill.add(e);
			CommunismMod.LOG.info("SELFTEST hostile spawned: {}", kill.size());
			if (kill.size() < 2) ok = false;
			for (Entity e : kill) e.discard();
		} catch (Throwable t) {
			ok = false;
			CommunismMod.LOG.error("COMMUNISM SELFTEST FAILED", t);
		}
		final boolean result = ok;
		// дать отработать отложенным задачам
		Scheduler.later(200, () -> {
			CommunismMod.LOG.info(result ? "COMMUNISM SELFTEST OK" : "COMMUNISM SELFTEST FAILED");
			s.halt(false);
		});
	}
}
