package com.communism;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * «Мод про Коммунизм».
 * Весь мод работает на стороне сервера: друзьям, которые заходят по LAN или на сервер,
 * ставить его не обязательно.
 */
public class CommunismMod implements ModInitializer {
	public static final String MOD_ID = "communism";
	public static final Logger LOG = LoggerFactory.getLogger("Communism");
	public static final Random RNG = new Random();

	public static MinecraftServer server;
	public static long tick;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			tick = 0;
			Data.init(s);
			Plan.init();
			if (System.getProperty("communism.selftest") != null) {
				Scheduler.later(60, () -> SelfTest.run(s));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			Plan.shutdown();
			Boss.shutdown();
			Scheduler.clear();
			Decrees.reset();
			Justice.reset();
			Laws.reset();
			Plaque.reset();
			Npcs.reset();
			Events.reset();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(CommunismMod::onTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> Events.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> Events.onLeave(handler.getPlayer()));
		Events.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> KommCommand.register(dispatcher));
		LOG.info("☭ Мод про Коммунизм загружен. Версия 2.0. Пролетарии всех модов, соединяйтесь!");
	}

	private static void onTick(MinecraftServer s) {
		if (server == null) return;
		tick++;
		try {
			Scheduler.tick();
			Plaque.tick();
			Justice.tick(s);
			Boss.tick(s);
			if (tick % 20 == 0) secondTick(s);
		} catch (Exception e) {
			LOG.error("Ошибка в тике коммунизма", e);
		}
	}

	private static void secondTick(MinecraftServer s) {
		boolean anyone = !s.getPlayerList().getPlayers().isEmpty();
		Plan.updateBar(s);
		if (!anyone) return;
		Decrees.secondTick(s);
		Laws.secondTick(s);
		Justice.secondTick(s);
		Loans.secondTick(s);
		Npcs.secondTick(s);
		Events.secondTick(s);
	}
}
