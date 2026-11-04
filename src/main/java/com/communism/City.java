package com.communism;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Планировка города. Все координаты — смещения от центра Красной площади (золотой блок),
 * x — на восток, z — на юг. y = 0 — уровень ног на площади.
 */
public final class City {
	public static final int R = 64; // полуразмер территории города

	// --- станция милиции и рельсы до тюрьмы
	public static final double[][] RAIL = {{31.5, 22.5}, {31.5, 32.5}, {50.5, 32.5}, {50.5, 41.5}};
	public static final double PILLORY_STOP = 9.0 + 10.0; // расстояние по рельсам до остановки «Площадь позора»
	public static final int[] PILLORY = {40, 37};      // где стоит наказанный (на помосте, y+1)
	public static final int[][] CELLS = {{45, 57}, {49, 57}, {53, 57}, {57, 57}}; // центры камер
	public static final int[] KOLKHOZ = {-30, -2};
	public static final int[] STATION_EXIT = {30, 14};
	public static final int[] VENDING_BUTTON = {18, 1, -12};
	public static final int[] PHONE_BUTTON = {18, 1, -7};
	public static final int[] QUEUE_END = {-37, -19};

	public static boolean built() {
		return Data.built();
	}

	public static int x() { return Data.px(); }
	public static int y() { return Data.py(); }
	public static int z() { return Data.pz(); }

	public static Vec3 at(double dx, double dy, double dz) {
		return new Vec3(x() + dx, y() + dy, z() + dz);
	}

	public static BlockPos pos(int dx, int dy, int dz) {
		return new BlockPos(x() + dx, y() + dy, z() + dz);
	}

	/** Внутри города (только Верхний мир). */
	public static boolean inside(Level l, BlockPos p) {
		if (!built() || l.dimension() != Level.OVERWORLD) return false;
		return Math.abs(p.getX() - x()) <= R && Math.abs(p.getZ() - z()) <= R && p.getY() >= y() - 12;
	}

	public static boolean inside(ServerPlayer p) {
		return inside(p.level(), p.blockPosition());
	}

	public static boolean near(ServerPlayer p, double dx, double dz, double radius) {
		if (!built() || p.level().dimension() != Level.OVERWORLD) return false;
		double ax = p.getX() - (x() + dx), az = p.getZ() - (z() + dz);
		return ax * ax + az * az <= radius * radius;
	}

	/** Путь по рельсам (в мировых координатах), от станции к тюрьме. */
	public static List<Vec3> railPath() {
		return List.of(at(RAIL[0][0], 0.1, RAIL[0][1]), at(RAIL[1][0], 0.1, RAIL[1][1]), at(RAIL[2][0], 0.1, RAIL[2][1]), at(RAIL[3][0], 0.1, RAIL[3][1]));
	}
}
