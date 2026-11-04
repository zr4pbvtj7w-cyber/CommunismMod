package com.communism;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** /komm — меню, кнопки окон и админ-команды. */
public final class KommCommand {
	static void register(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("komm")
				.executes(c -> menu(c.getSource().getPlayerOrException()))
				.then(Commands.literal("ui").then(Commands.argument("args", StringArgumentType.greedyString()).executes(c -> {
					ui(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "args"));
					return 1;
				})))
				.then(Commands.literal("zakony").executes(c -> {
					Npcs.openLaws(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("plan").executes(c -> {
					c.getSource().getPlayerOrException().sendSystemMessage(Plan.statusText());
					return 1;
				}))
				.then(Commands.literal("vlast").executes(c -> {
					Leader.openLeader(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("metro").executes(c -> metro(c.getSource().getPlayerOrException())))
				.then(admin());
		LiteralCommandNode<CommandSourceStack> node = d.register(root);
		d.register(Commands.literal("kommunizm").executes(c -> menu(c.getSource().getPlayerOrException())).redirect(node));
		d.register(Commands.literal("ussr").executes(c -> menu(c.getSource().getPlayerOrException())).redirect(node));
	}

	/** Обработка кнопок из окон. */
	static void ui(ServerPlayer p, String args) {
		String[] a = args.trim().split("\\s+");
		if (a.length == 0) return;
		try {
			switch (a[0]) {
				case "menu" -> menu(p);
				case "laws" -> Npcs.openLaws(p);
				case "stats" -> stats(p);
				case "vozhd" -> Leader.handleVozhd(p, a);
				case "lead" -> Leader.handleLeader(p, a);
				case "bank" -> Loans.handle(p, a);
				case "upg" -> Upgrader.handle(p, a);
				case "quest" -> Quests.handle(p, a);
				case "shop" -> Shop.buy(p, Integer.parseInt(a[1]));
				case "decrees" -> decrees(p);
				case "metro" -> metro(p);
				default -> Npcs.handle(p, a);
			}
		} catch (RuntimeException e) {
			CommunismMod.LOG.warn("Ошибка кнопки '{}'", args, e);
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> admin() {
		LiteralArgumentBuilder<CommandSourceStack> a = Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		a.then(Commands.literal("ukaz")
				.executes(c -> {
					Decrees.runRandom(c.getSource().getServer());
					return 1;
				})
				.then(Commands.argument("n", IntegerArgumentType.integer(1, Decrees.ALL.size())).executes(c -> {
					Decrees.run(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "n") - 1);
					return 1;
				})));
		a.then(Commands.literal("zakon").then(Commands.argument("n", IntegerArgumentType.integer(1, Laws.ALL.size())).executes(c -> {
			Laws.enact(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "n"), "администрация");
			return 1;
		})));
		a.then(Commands.literal("otstavka").executes(c -> {
			for (int i = Laws.enactedCount(); i < Leader.LAWS_TO_RETIRE; i++) Data.add("lawsEnacted", 1);
			Leader.retire(c.getSource().getServer());
			return 1;
		}));
		a.then(Commands.literal("arest").executes(c -> {
			Justice.arrest(c.getSource().getPlayerOrException(), "проверка милиции", null);
			return 1;
		}).then(Commands.argument("n", IntegerArgumentType.integer(1, Justice.Punishment.values().length)).executes(c -> {
			Justice.arrest(c.getSource().getPlayerOrException(), "проверка милиции", Justice.Punishment.values()[IntegerArgumentType.getInteger(c, "n") - 1]);
			return 1;
		})));
		a.then(Commands.literal("etap").then(Commands.argument("n", IntegerArgumentType.integer(1, 7)).executes(c -> {
			int n = IntegerArgumentType.getInteger(c, "n") - 1;
			MinecraftServer s = c.getSource().getServer();
			if (n < Plan.STAGES.length) Plan.startStage(n, s.getPlayerList().getPlayers().size());
			else Data.set("stage", n);
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
			Ranks.addTrud(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"), "щедрость администрации");
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
		build.then(Commands.literal("gorod").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			if (p.level().dimension() != Level.OVERWORLD) return 0;
			Events.buildPlazaAt(c.getSource().getServer(), p.getBlockX(), p.getBlockY(), p.getBlockZ(), null);
			return 1;
		}));
		build.then(Commands.literal("kvartira").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			Structures.miniHouse(Msg.level(p), p.blockPosition().offset(5, 0, 5));
			return 1;
		}));
		a.then(build);
		return a;
	}

	static int menu(ServerPlayer p) {
		int r = Ranks.rank(p);
		Ui.Win w = Ui.win("☭ Мод про Коммунизм 2.0", ChatFormatting.RED)
				.icon(Goods.partyCard(), Ui.label("Товарищ " + p.getName().getString() + " — " + Ranks.NAMES[r]
						+ (Leader.isLeader(p) ? " и ГЕНСЕК" : ""), Ranks.COLORS[r], ChatFormatting.BOLD))
				.text("Трудодни: " + Ranks.trud(p) + "  |  Стаж: " + Data.get(p, Data.stazh)
						+ (r + 1 < Ranks.NAMES.length ? " (до «" + Ranks.NAMES[r + 1] + "»: " + Ranks.THRESHOLDS[r + 1] + ")" : ""), ChatFormatting.GOLD)
				.text("Выговоры: " + Data.get(p, Data.vyg) + "/3  |  Долг МФО: " + Loans.debt(p), ChatFormatting.GRAY)
				.line()
				.text(Plan.statusText().getString().replace("☭ ", ""), ChatFormatting.YELLOW)
				.line()
				.text("Законы: " + Laws.shortList(), ChatFormatting.RED)
				.text("Следующий указ через " + Msg.time(Data.get("decree")) + ", закон — через " + Msg.time(Data.get("lawTimer")), ChatFormatting.GRAY);
		w.btn(Ui.label("⚖ Свод законов", ChatFormatting.RED), "laws", null);
		w.btn(Ui.label("Указы Партии", ChatFormatting.GOLD), "decrees", null);
		w.btn(Ui.label("Моё задание", ChatFormatting.YELLOW), "stats", null);
		w.btn(Ui.label("Метро на площадь (3 тр.)", ChatFormatting.AQUA), "metro", "Телепорт на Красную площадь");
		if (Leader.isLeader(p)) w.wide(Ui.label("☭ Пульт Генсека", ChatFormatting.DARK_RED, ChatFormatting.BOLD), "lead", null);
		w.open(p);
		return 1;
	}

	private static void stats(ServerPlayer p) {
		Quests.Quest q = Quests.current(p);
		Ui.Win w = Ui.win("Личное дело товарища " + p.getName().getString(), ChatFormatting.GOLD);
		if (q == null) w.text("Задания нет. Задания дают: Политрук (у Мавзолея), Агроном, Доярка, Академик, Пионер и Продавщица Галя.", ChatFormatting.GRAY);
		else w.icon(q.icon().copy(), Ui.label("«" + q.title() + "»: " + q.desc() + " — " + Math.min(Data.get(p, Data.qprog), Data.get(p, Data.qgoal)) + "/" + Data.get(p, Data.qgoal)
				+ ". Награда " + q.reward() + " тр. Сдавать: " + Npcs.role(q.giver()).name(), ChatFormatting.YELLOW));
		w.text("Займов взято: " + Data.get(p, Data.loans) + ". Звание: " + Ranks.NAMES[Ranks.rank(p)] + ".", ChatFormatting.GRAY);
		w.btn("Назад", "menu").open(p);
	}

	private static void decrees(ServerPlayer p) {
		Ui.Win w = Ui.win("Указы Партии (каждые 10 минут)", ChatFormatting.GOLD);
		StringBuilder sb = new StringBuilder();
		for (Decrees.Decree dc : Decrees.ALL) sb.append("• «").append(dc.name()).append("» — ").append(dc.desc()).append("\n");
		w.text(sb.toString().trim(), ChatFormatting.WHITE);
		w.btn("Назад", "menu").open(p);
	}

	private static int metro(ServerPlayer p) {
		if (!Data.built() || Justice.inCustody(p)) return 0;
		if (!Ranks.spend(p, 3)) {
			Plaque.show(p, Plaque.Kind.BAD, "Проезд стоит 3 трудодня. Жетонов нет — идите пешком.", 4);
			return 0;
		}
		Justice.teleport(p, CommunismMod.server.overworld(), Data.px() + 0.5, Data.py(), Data.pz() + 3.5, 180f);
		Plaque.show(p, Plaque.Kind.INFO, "Станция «Красная площадь». Осторожно, двери закрываются!", 4);
		return 1;
	}

	static ItemStack icon() {
		return new ItemStack(Items.RED_BANNER);
	}
}
