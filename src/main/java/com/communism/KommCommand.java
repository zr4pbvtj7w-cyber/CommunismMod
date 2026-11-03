package com.communism;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** /komm — всё про коммунизм. */
public final class KommCommand {
	static void register(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("komm")
				.executes(c -> status(c.getSource().getPlayerOrException()))
				.then(Commands.literal("plan").executes(c -> {
					c.getSource().getPlayerOrException().sendSystemMessage(Plan.statusText());
					return 1;
				}))
				.then(Commands.literal("zakon").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Msg.info(p, Laws.NAMES[Laws.current()] + ": " + Laws.DESCS[Laws.current()]);
					return 1;
				}))
				.then(Commands.literal("ukazy").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Msg.info(p, "Указы Партии (случайный — каждые 10 минут):");
					for (int i = 0; i < Decrees.ALL.size(); i++) {
						Decrees.Decree dc = Decrees.ALL.get(i);
						p.sendSystemMessage(Msg.t(" " + (i + 1) + ". «" + dc.name() + "» ", ChatFormatting.RED).append(Msg.t(dc.desc(), ChatFormatting.GRAY)));
					}
					return 1;
				}))
				.then(Commands.literal("zadanie").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					int t = Data.get(p, Data.qtype);
					if (t <= 0 || t >= Quests.QUESTS.length) Msg.info(p, "У вас нет задания. Получите его у Политрука Задорного на Красной площади.");
					else Msg.info(p, "Задание «" + Quests.QUESTS[t].title() + "»: " + Quests.QUESTS[t].desc() + " — "
							+ Data.get(p, Data.qprog) + "/" + Data.get(p, Data.qgoal));
					return 1;
				}))
				.then(Commands.literal("magazin").executes(c -> {
					Shop.show(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("kupit").then(Commands.argument("n", IntegerArgumentType.integer(1, 99)).executes(c -> {
					Shop.buy(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"));
					return 1;
				})))
				.then(Commands.literal("metro").executes(c -> metro(c.getSource().getPlayerOrException())))
				.then(admin());
		LiteralCommandNode<CommandSourceStack> node = d.register(root);
		d.register(Commands.literal("kommunizm").executes(c -> status(c.getSource().getPlayerOrException())).redirect(node));
		d.register(Commands.literal("ussr").executes(c -> status(c.getSource().getPlayerOrException())).redirect(node));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> admin() {
		LiteralArgumentBuilder<CommandSourceStack> a = Commands.literal("admin").requires(src -> src.hasPermission(2));
		a.then(Commands.literal("ukaz")
				.executes(c -> {
					Decrees.runRandom(c.getSource().getServer());
					return 1;
				})
				.then(Commands.argument("n", IntegerArgumentType.integer(1, Decrees.ALL.size())).executes(c -> {
					Decrees.run(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "n") - 1);
					return 1;
				})));
		a.then(Commands.literal("zakon").then(Commands.argument("n", IntegerArgumentType.integer(0, Laws.NAMES.length - 1)).executes(c -> {
			Laws.change(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "n"));
			return 1;
		})));
		a.then(Commands.literal("etap").then(Commands.argument("n", IntegerArgumentType.integer(1, 7)).executes(c -> {
			int n = IntegerArgumentType.getInteger(c, "n") - 1;
			MinecraftServer s = c.getSource().getServer();
			if (n < Plan.STAGES.length) Plan.startStage(n, s.getPlayerList().getPlayers().size());
			else Data.set("stage", n);
			Msg.broadcast(s, Plan.statusText());
			return 1;
		})));
		a.then(Commands.literal("vypolnit").executes(c -> {
			MinecraftServer s = c.getSource().getServer();
			if (Plan.stage() < Plan.STAGES.length) {
				for (int i = 0; i < 4; i++) Data.set("prog" + i, Plan.goal(i));
				Plan.completeStage(s);
			}
			return 1;
		}));
		a.then(Commands.literal("boss").executes(c -> {
			Boss.summon(c.getSource().getServer());
			return 1;
		}));
		a.then(Commands.literal("trud").then(Commands.argument("n", IntegerArgumentType.integer(1, 10000)).executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			Ranks.addTrud(p, IntegerArgumentType.getInteger(c, "n"), "щедрость администрации");
			return 1;
		})));
		a.then(Commands.literal("npc").executes(c -> {
			if (Data.built()) Npcs.spawnAll(c.getSource().getServer().overworld(), Data.px(), Data.py(), Data.pz());
			return 1;
		}));
		a.then(Commands.literal("timer").then(Commands.argument("sec", IntegerArgumentType.integer(1, 600)).executes(c -> {
			Data.set("decree", IntegerArgumentType.getInteger(c, "sec"));
			return 1;
		})));
		var build = Commands.literal("postroit");
		build.then(Commands.literal("ploshad").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			if (p.level().dimension() != Level.OVERWORLD) return 0;
			Events.buildPlazaAt(c.getSource().getServer(), p.getBlockX(), p.getBlockY(), p.getBlockZ());
			return 1;
		}));
		for (String name : Structures.NAMES) {
			build.then(Commands.literal(name).executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				Structures.buildNamed(Msg.level(p), name, p.blockPosition().offset(3, 0, 3));
				return 1;
			}));
		}
		a.then(build);
		return a;
	}

	private static int status(ServerPlayer p) {
		int r = Ranks.rank(p);
		Msg.info(p, "═══ МОД ПРО КОММУНИЗМ ═══");
		p.sendSystemMessage(Plan.statusText());
		Msg.info(p, "Ваше звание: " + Ranks.NAMES[r] + " | Трудодни: " + Ranks.trud(p) + " | Стаж: " + Data.get(p, Data.stazh)
				+ (r + 1 < Ranks.NAMES.length ? " (до «" + Ranks.NAMES[r + 1] + "»: " + Ranks.THRESHOLDS[r + 1] + ")" : ""));
		Msg.info(p, "Выговоры: " + Data.get(p, Data.vyg) + "/3 | Закон: " + Laws.NAMES[Laws.current()]);
		Msg.info(p, "Следующий указ через " + Msg.time(Data.get("decree")));
		if (Data.built()) Msg.info(p, "Красная площадь: " + Data.px() + " " + Data.py() + " " + Data.pz());
		Msg.info(p, "Команды: /komm plan | zakon | ukazy | zadanie | magazin | metro");
		return 1;
	}

	private static int metro(ServerPlayer p) {
		if (!Data.built()) {
			Msg.info(p, "Метро ещё не построено. Как и Красная площадь.");
			return 0;
		}
		if (Discipline.isExiled(p)) {
			Msg.info(p, "Из Сибири на метро не уехать. Ждите окончания срока.");
			return 0;
		}
		if (!Ranks.spend(p, 3)) {
			Msg.info(p, "Проезд стоит 3 трудодня. Жетонов нет — идите пешком.");
			return 0;
		}
		Discipline.teleport(p, CommunismMod.server.overworld(), Data.px() + 0.5, Data.py(), Data.pz() + 3.5);
		Msg.info(p, "Станция «Красная площадь». Осторожно, двери закрываются!");
		return 1;
	}
}
