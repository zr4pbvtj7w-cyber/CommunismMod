package com.communism;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.List;

/** Сообщения, титры, звуки, фейерверки и выдача предметов. */
public final class Msg {
	public static MutableComponent t(String s, ChatFormatting... f) {
		return Component.literal(s).withStyle(f);
	}

	public static MutableComponent party() {
		return t("☭ ", ChatFormatting.RED, ChatFormatting.BOLD);
	}

	public static void broadcast(MinecraftServer s, Component c) {
		s.getPlayerList().broadcastSystemMessage(c, false);
	}

	public static void broadcast(MinecraftServer s, String text, ChatFormatting color) {
		broadcast(s, party().append(t(text, color)));
	}

	/** Реплика NPC в чат одному игроку. */
	public static void say(ServerPlayer p, String npc, String text) {
		p.sendSystemMessage(t("[" + npc + "] ", ChatFormatting.RED, ChatFormatting.BOLD).append(t(text, ChatFormatting.WHITE)));
	}

	/** Реплика NPC всем. */
	public static void sayAll(MinecraftServer s, String npc, String text) {
		broadcast(s, t("[" + npc + "] ", ChatFormatting.RED, ChatFormatting.BOLD).append(t(text, ChatFormatting.YELLOW)));
	}

	public static void info(ServerPlayer p, String text) {
		p.sendSystemMessage(party().append(t(text, ChatFormatting.GOLD)));
	}

	public static void title(ServerPlayer p, Component title, Component sub) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	public static void titleAll(MinecraftServer s, Component title, Component sub) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) title(p, title, sub);
	}

	public static void actionbar(ServerPlayer p, Component c) {
		p.connection.send(new ClientboundSetActionBarTextPacket(c));
	}

	public static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	public static void sound(ServerPlayer p, SoundEvent e, float vol, float pitch) {
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), e, SoundSource.MASTER, vol, pitch);
	}

	public static void soundAll(MinecraftServer s, SoundEvent e, float vol, float pitch) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) sound(p, e, vol, pitch);
	}

	public static void give(ServerPlayer p, ItemStack s) {
		if (s.isEmpty()) return;
		if (!p.getInventory().add(s) && !s.isEmpty()) {
			drop(level(p), p.getX(), p.getY() + 0.5, p.getZ(), s);
		}
	}

	public static void drop(ServerLevel l, double x, double y, double z, ItemStack s) {
		ItemEntity e = new ItemEntity(l, x, y, z, s);
		l.addFreshEntity(e);
	}

	public static void firework(ServerLevel l, double x, double y, double z) {
		int[] palette = {0xFF0000, 0xFFD700, 0xFFFFFF, 0xCC0000};
		FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		FireworkExplosion ex = new FireworkExplosion(
				shapes[CommunismMod.RNG.nextInt(shapes.length)],
				IntList.of(palette[CommunismMod.RNG.nextInt(palette.length)], 0xFFD700),
				IntList.of(0xFFFFFF), true, true);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + CommunismMod.RNG.nextInt(2), List.of(ex)));
		l.addFreshEntity(new FireworkRocketEntity(l, x, y, z, rocket));
	}

	public static void fireworksAround(ServerPlayer p, int count) {
		ServerLevel l = level(p);
		for (int i = 0; i < count; i++) {
			firework(l, p.getX() + CommunismMod.RNG.nextInt(11) - 5, p.getY() + 1, p.getZ() + CommunismMod.RNG.nextInt(11) - 5);
		}
	}

	public static String time(int seconds) {
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}
}
