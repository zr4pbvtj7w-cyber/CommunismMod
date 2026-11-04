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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Подписка на события игры. */
public final class Events {
	private static final Map<UUID, Integer> MINED = new HashMap<>();
	private static final Set<UUID> IN_CITY = new HashSet<>();
	private static boolean buildScheduled = false;

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (entity.getTags().contains(Npcs.TAG)) {
				if (hand == InteractionHand.MAIN_HAND && Npcs.debounce(sp)) Npcs.interact(sp, entity);
				return InteractionResult.SUCCESS;
			}
			if (entity.getTags().contains(Npcs.TEMP)) {
				if (hand == InteractionHand.MAIN_HAND && Npcs.debounce(sp)) Plaque.show(sp, Plaque.Kind.POLICE, "Не мешайте работе органов, гражданин!", 3);
				return InteractionResult.SUCCESS;
			}
			if (entity.getType() == EntityType.VILLAGER && Laws.is(Laws.NO_SPECULATION)) {
				if (hand == InteractionHand.MAIN_HAND && Npcs.debounce(sp)) Justice.violate(sp, Laws.NO_SPECULATION, "попытка торговли с частником");
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (entity.getTags().contains(Npcs.TAG) || entity.getTags().contains(Npcs.TEMP)) {
				if (Npcs.debounce(sp)) Justice.vygovor(sp, "покушение на партийного работника");
				return InteractionResult.FAIL;
			}
			if (entity instanceof ServerPlayer && (Laws.is(Laws.PEACE) || Justice.inCustody((ServerPlayer) entity))) {
				if (Laws.is(Laws.PEACE) && Npcs.debounce(sp)) Justice.violate(sp, Laws.PEACE, "драка с товарищем");
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			if (Laws.is(Laws.NO_SLEEP) && world.getBlockState(pos).getBlock() instanceof BedBlock) {
				if (Npcs.debounce(sp)) Justice.violate(sp, Laws.NO_SLEEP, "попытка лечь спать");
				return InteractionResult.FAIL;
			}
			if (City.built() && world.dimension() == Level.OVERWORLD && hand == InteractionHand.MAIN_HAND) {
				int[] v = City.VENDING_BUTTON, ph = City.PHONE_BUTTON;
				if (pos.equals(City.pos(v[0], v[1], v[2])) && Npcs.debounce(sp)) vending(sp);
				if (pos.equals(City.pos(ph[0], ph[1], ph[2])) && Npcs.debounce(sp)) phone(sp);
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			ItemStack st = player.getItemInHand(hand);
			if (Leader.isSeal(st)) {
				if (Npcs.debounce(sp)) Leader.openLeader(sp);
				return InteractionResult.SUCCESS;
			}
			if (Laws.forbidItem(sp, st)) return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp)) return true;
			if (!City.inside(world, pos)) return true;
			if (Justice.allowBreakInCity(sp, pos, state)) return true;
			if (!Justice.inCustody(sp)) {
				Justice.arrest(sp, "порча городского имущества (" + state.getBlock().getName().getString() + ")", null);
			} else {
				Plaque.show(sp, Plaque.Kind.BAD, "Это государственное имущество. Даже в тюрьме.", 3);
			}
			return false;
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
			// колхозные грядки вырастают сами
			if (state.getBlock() instanceof CropBlock crop && City.inside(world, pos)) {
				BlockPos bp = pos.immutable();
				BlockState grown = crop.getStateForAge(crop.getMaxAge());
				Scheduler.later(600, () -> {
					if (world.getBlockState(bp).isAir() && world.getBlockState(bp.below()).is(net.minecraft.world.level.block.Blocks.FARMLAND)) {
						world.setBlock(bp, grown, 3);
					}
				});
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity attacker = source.getEntity();
			if (entity instanceof ServerPlayer p) {
				if (Justice.inCustody(p) && !(attacker instanceof ServerPlayer)) return amount < 1f; // снежки — можно, урон — нельзя
				if (attacker != null && attacker.getTags().contains(Npcs.BURZHUY)) Npcs.steal(p, attacker);
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
				Justice.violate(dead, Laws.THRIFT, "порча государственного имущества (смерть)");
			}
		});

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> Laws.onChat(sender, message.signedContent()));
	}

	private static void vending(ServerPlayer p) {
		if (!Ranks.spend(p, 1)) {
			Plaque.show(p, Plaque.Kind.BAD, "Автомат требует 1 трудодень. Стучать по автомату бесполезно (проверено).", 4);
			return;
		}
		Msg.sound(p, SoundEvents.BOTTLE_FILL, 1f, 1f);
		String[] names = {"Газировка с сиропом", "Газировка без сиропа", "Газировка «Буратино»", "Тархун", "Просто вода (сироп кончился)"};
		int i = CommunismMod.RNG.nextInt(names.length);
		ItemStack drink = Goods.named(Items.POTION, 1, names[i], ChatFormatting.AQUA, "Стакан общий. Помыть не забудьте.");
		drink.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(
				i == 0 ? net.minecraft.world.item.alchemy.Potions.SWIFTNESS : i == 2 ? net.minecraft.world.item.alchemy.Potions.LEAPING
						: i == 3 ? net.minecraft.world.item.alchemy.Potions.REGENERATION : net.minecraft.world.item.alchemy.Potions.WATER));
		Msg.give(p, drink);
		Plaque.show(p, Plaque.Kind.MONEY, "Автомат выдал: " + names[i] + " (−1 трудодень)", 4);
	}

	private static void phone(ServerPlayer p) {
		Msg.sound(p, SoundEvents.NOTE_BLOCK_BIT.value(), 1f, 1.5f);
		String[] calls = {"Алло, это Кремль? — Нет, это прачечная. — А, тогда передайте Вождю, что я выполнил план.",
				"Алло, справочная? Когда будет колбаса? — (короткие гудки)", "Вы позвонили в КГБ. Ваш звонок очень важен для нас. И записан.",
				"Алло, Луна? Вождь, это вы? — Тут тихо, хорошо... Не звоните больше.", "Алло, милиция? Тут кто-то смотрит на запад! — Выезжаем."};
		Ui.win("☎ Телефон-автомат", ChatFormatting.RED).text(calls[CommunismMod.RNG.nextInt(calls.length)], ChatFormatting.WHITE)
				.text("Двушка проглочена автоматом.", ChatFormatting.DARK_GRAY).open(p);
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
				Plaque.all(s, Plaque.Kind.GOOD, "В ряды Партии вступил товарищ " + p.getName().getString() + "! Выданы партбилет, батон и ушанка.", 8);
			});
		}
		if (Leader.isLeader(p)) Scheduler.later(60, () -> Plaque.show(p, Plaque.Kind.DECREE, "Вы — Генсек! Печать Генсека (ПКМ) открывает пульт управления страной.", 8));
		Scheduler.later(30, () -> {
			Msg.title(p, Msg.t("☭ ДОБРО ПОЖАЛОВАТЬ В СССР ☭", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t("Союз Самых Счастливых Работяг — версия 2.0", ChatFormatting.GOLD));
			Plaque.show(p, Plaque.Kind.INFO, "Цель: выполнить Пятилетку, победить Мистера Капитала и построить Коммунизм!", 10);
			Plaque.show(p, Plaque.Kind.LAW, "Законы издаёт Вождь во Дворце Советов. Ломать в городе нельзя — арест!", 10);
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
		buildPlazaAt(s, at.getX(), y, at.getZ(), null);
	}

	public static void buildPlazaAt(MinecraftServer s, int x, int y, int z, Runnable after) {
		ServerLevel l = s.overworld();
		Plaque.all(s, Plaque.Kind.DECREE, "Стройка века! Партия возводит город... (несколько секунд)", 12);
		for (ServerPlayer o : s.getPlayerList().getPlayers()) {
			o.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 400, 0));
		}
		Structures.buildCity(l, x, y, z, () -> {
			Data.set("px", x);
			Data.set("py", y);
			Data.set("pz", z);
			Data.set("built", 1);
			Npcs.spawnAll(l, x, y, z);
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), "setworldspawn " + x + " " + y + " " + (z + 3));
			for (ServerPlayer o : s.getPlayerList().getPlayers()) {
				if (o.level().dimension() != Level.OVERWORLD) continue;
				Justice.teleport(o, l, x + 0.5, y, z + 3.5, 180f);
				Msg.title(o, Msg.t("КРАСНАЯ ПЛОЩАДЬ", ChatFormatting.RED, ChatFormatting.BOLD), Msg.t("Председатель Госплана ждёт вас у Мавзолея", ChatFormatting.GOLD));
			}
			if (Laws.enactedCount() == 0) Scheduler.later(200, () -> Laws.enact(s, Laws.HATS, "Великий Вождь"));
			Msg.sayAll(s, Npcs.GOSPLAN, "Добро пожаловать на Красную площадь! Год 1 — Коллективизация: несите мне урожай с колхоза и брёвна!");
			if (after != null) after.run();
		});
	}

	static void secondTick(MinecraftServer s) {
		if (!City.built()) return;
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			boolean in = City.inside(p);
			if (in && IN_CITY.add(p.getUUID())) {
				Plaque.show(p, Plaque.Kind.POLICE, "Вы в городе. Ломать здесь ничего нельзя — милиция приедет сразу!", 6);
			} else if (!in && IN_CITY.remove(p.getUUID())) {
				Plaque.show(p, Plaque.Kind.INFO, "Вы покинули город. Здесь можно копать и строить.", 4);
			}
			Quests.secondTick(p);
		}
	}

	static void onLeave(ServerPlayer p) {
		Plan.removePlayer(p);
		Plaque.remove(p);
		Justice.onLeave(p);
		IN_CITY.remove(p.getUUID());
	}

	static void reset() {
		MINED.clear();
		IN_CITY.clear();
		buildScheduled = false;
	}
}
