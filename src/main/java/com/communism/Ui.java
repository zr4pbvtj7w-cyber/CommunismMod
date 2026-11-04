package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Окна (диалоги) Minecraft 1.21.11. Сервер присылает их сам — клиенту мод не нужен.
 * Кнопки выполняют команды «/komm ui ...».
 */
public final class Ui {
	public static final int WIDTH = 300;

	public record Btn(Component label, String cmd, Component tooltip, int width) {}

	/** Окно в процессе сборки. */
	public static final class Win {
		final Component title;
		final List<DialogBody> body = new ArrayList<>();
		final List<Btn> buttons = new ArrayList<>();
		int columns = 2;
		String exitLabel = "Закрыть";

		Win(Component title) {
			this.title = title;
		}

		/** Портрет/иконка со строкой текста справа. */
		public Win icon(ItemStack icon, Component text) {
			body.add(new ItemBody(icon, Optional.of(new PlainMessage(text, WIDTH - 40)), false, false, 16, 16));
			return this;
		}

		public Win text(Component c) {
			body.add(new PlainMessage(c, WIDTH));
			return this;
		}

		public Win text(String s, ChatFormatting... f) {
			return text(Component.literal(s).withStyle(f));
		}

		public Win line() {
			return text(Component.literal("─────────────────────────").withStyle(ChatFormatting.DARK_GRAY));
		}

		public Win btn(String label, String cmd) {
			return btn(Component.literal(label), cmd, null);
		}

		public Win btn(Component label, String cmd, String tooltip) {
			buttons.add(new Btn(label, cmd, tooltip == null ? null : Component.literal(tooltip), 145));
			return this;
		}

		public Win wide(Component label, String cmd, String tooltip) {
			buttons.add(new Btn(label, cmd, tooltip == null ? null : Component.literal(tooltip), 296));
			return this;
		}

		public Win columns(int c) {
			columns = c;
			return this;
		}

		public Win exit(String label) {
			exitLabel = label;
			return this;
		}

		public void open(ServerPlayer p) {
			CommonDialogData common = new CommonDialogData(title, Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
			List<ActionButton> actions = new ArrayList<>();
			for (Btn b : buttons) {
				actions.add(new ActionButton(new CommonButtonData(b.label(), Optional.ofNullable(b.tooltip()), b.width()),
						Optional.of(new StaticAction(new ClickEvent.RunCommand("/komm ui " + b.cmd())))));
			}
			ActionButton exit = new ActionButton(new CommonButtonData(Component.literal(exitLabel).withStyle(ChatFormatting.GRAY), 150), Optional.empty());
			if (actions.isEmpty()) actions.add(exit);
			Dialog d = new MultiActionDialog(common, actions, Optional.of(exit), columns);
			p.openDialog(Holder.direct(d));
		}
	}

	public static Win win(String title, ChatFormatting color) {
		return new Win(Component.literal(title).withStyle(color, ChatFormatting.BOLD));
	}

	/** Окно разговора с NPC: портрет + реплика. */
	public static Win npc(String name, ChatFormatting color, ItemStack portrait, String speech) {
		return win(name, color).icon(portrait, Component.literal("«" + speech + "»").withStyle(ChatFormatting.WHITE));
	}

	public static MutableComponent label(String s, ChatFormatting... f) {
		return Component.literal(s).withStyle(f);
	}

	public static void close(ServerPlayer p) {
		p.connection.send(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);
	}
}
