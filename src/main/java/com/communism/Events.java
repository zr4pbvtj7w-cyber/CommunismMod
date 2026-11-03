package com.communism;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Подписка на события игры. */
public final class Events {
	private static final Map<UUID, Integer> MINED = new HashMap<>();
	private static final Set<UUID> WARNED = new HashSet<>();
	private static boolean buildScheduled = false;

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!entity.getTags().contains(Npcs.TAG)) return InteractionResult.PASS;
			if (hand == InteractionHand.MAIN_HAND && Npcs.debounce(sp)) Npcs.interact(sp, entity);
			return InteractionResult.SUCCESS;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (entity.getTags().contains(Npcs.TAG)) {
				if (Npcs.debounce(sp)) {
					Discipline.vygovor(sp, "покушение на партийного работника");
					if (Npcs.roleOf(entity).equals("dictator")) {
						Msg.say(sp, Npcs.DICTATOR, "Вы подняли руку на Генсека?! Охрана!");
					}
				}
				return InteractionResult.FAIL;
			}
			if (entity instanceof ServerPlayer && Laws.is(Laws.PEACE)) {
				Msg.actionbar(sp, Msg.t("Закон о мирном небе: товарищей бить нельзя!", ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (Laws.is(Laws.NO_SLEEP) && world.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock) {
				Msg.say(sp, Npcs.DICTATOR, "Закон о трудовой дисциплине! Спать на рабочем месте запрещено! Ночь — лучшее время для перевыполнения плана.");
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (Laws.is(Laws.DRY) && player.getItemInHand(hand).is(Items.POTION)) {
				Msg.actionbar(sp, Msg.t("Сухой закон! Зелья запрещены.", ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp)) return;
			Quests.onMine(sp, state);
			if (Decrees.subbotnikActive()) Decrees.subbotnik.merge(sp.getUUID(), 1, Integer::sum);
			int per = Laws.is(Laws.PLAN_ECONOMY) ? 22 : 64;
			int n = MINED.merge(sp.getUUID(), 1, Integer::sum);
			if (n >= per) {
				MINED.put(sp.getUUID(), 0);
				Ranks.addTrud(sp, 1, "трудовая норма");
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity attacker = source.getEntity();
			if (entity instanceof ServerPlayer p && attacker != null && attacker.getTags().contains(Npcs.BURZHUY)) {
				Npcs.steal(p, attacker);
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			ServerPlayer killer = source.getEntity() instanceof ServerPlayer k ? k : null;
			if (entity.getTags().contains(Boss.TAG)) {
				Boss.onDeath(entity);
				return;
			}
			if (entity.getTags().contains(Npcs.BURZHUY)) Npcs.onBurzhuyDeath(entity, killer);
			if (killer != null && killer != entity) {
				Quests.onKill(killer, entity);
				if (Decrees.competitionActive()) Decrees.competition.merge(killer.getUUID(), 1, Integer::sum);
			}
			if (entity instanceof ServerPlayer dead && Laws.is(Laws.THRIFT)) {
				Discipline.vygovor(dead, "порча государственного имущества (смерть)");
			}
		});

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> Laws.onChat(sender, message.signedContent()));
	}

	static void onJoin(ServerPlayer p) {
		MinecraftServer s = CommunismMod.server;
		if (s == null) return;
		Ranks.updateTeam(p);
		if (Data.get(p, Data.joined) == 0) {
			Data.set(p, Data.joined, 1);
			Scheduler.later(40, () -> {
				ServerLevel l = Msg.level(p);
				Msg.give(p, Goods.partyCard());
				Msg.give(p, Goods.bread(6));
				Msg.give(p, Goods.ushanka(l));
				Msg.broadcast(s, Msg.party().append(Msg.t("В ряды Партии вступил товарищ " + p.getName().getString() + "! Выдан партбилет, батон и ушанка.", ChatFormatting.GOLD)));
			});
		}
		Scheduler.later(30, () -> {
			Msg.title(p, Msg.t("☭ ДОБРО ПОЖАЛОВАТЬ В СССР ☭", ChatFormatting.RED, ChatFormatting.BOLD),
					Msg.t("Союз Самых Счастливых Работяг", ChatFormatting.GOLD));
			Msg.info(p, "Цель: выполнить Пятилетку (5 этапов), победить Мистера Капитала и построить Коммунизм!");
			Msg.info(p, "Говорите с NPC на Красной площади (ПКМ). Каждые 10 минут — Указ Партии. /komm — вся информация.");
		});
		if (!Data.built() && !buildScheduled) {
			buildScheduled = true;
			Scheduler.later(100, () -> autoBuild(s));
		}
	}

	private static void autoBuild(MinecraftServer s) {
		buildScheduled = false;
		if (Data.built()) return;
		ServerPlayer p = null;
		for (ServerPlayer o : s.getPlayerList().getPlayers()) if (o.level().dimension() == Level.OVERWORLD) { p = o; break; }
		if (p == null) return;
		ServerLevel l = s.overworld();
		BlockPos at = p.blockPosition();
		int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		y = Math.max(l.getSeaLevel() + 1, Math.min(200, y));
		Msg.broadcast(s, "Партия строит для вас Красную площадь... Не волнуйтесь, это займёт пару секунд (стройка века!).", ChatFormatting.YELLOW);
		buildPlazaAt(s, at.getX(), y, at.getZ());
	}

	public static void buildPlazaAt(MinecraftServer s, int x, int y, int z) {
		ServerLevel l = s.overworld();
		Structures.buildPlaza(l, x, y, z);
		Data.set("px", x);
		Data.set("py", y);
		Data.set("pz", z);
		Data.set("built", 1);
		Npcs.spawnAll(l, x, y, z);
		s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), "setworldspawn " + x + " " + y + " " + (z + 3));
		for (ServerPlayer o : s.getPlayerList().getPlayers()) {
			if (o.level().dimension() != Level.OVERWORLD) continue;
			o.teleportTo(l, x + 0.5, y, z + 3.5, java.util.Set.of(), 180f, 0f, true);
			Msg.title(o, Msg.t("КРАСНАЯ ПЛОЩАДЬ", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t("Генсек Усач ждёт вас у Мавзолея", ChatFormatting.GOLD));
		}
		Msg.sayAll(s, Npcs.DICTATOR, "Товарищи! Добро пожаловать на Красную площадь! Наша цель — выполнить Пятилетку и построить коммунизм!");
		Msg.sayAll(s, Npcs.DICTATOR, "Начинаем с Года 1 — Коллективизации. На колхозе (на западе) уже созрел урожай. Несите мне пшеницу, картошку, морковь и брёвна!");
		Msg.broadcast(s, Plan.statusText());
	}

	static void onLeave(ServerPlayer p) {
		Plan.removePlayer(p);
		WARNED.remove(p.getUUID());
	}

	static void reset() {
		MINED.clear();
		WARNED.clear();
		buildScheduled = false;
	}
}
