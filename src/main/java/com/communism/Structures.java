package com.communism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Готовые постройки. Всё строится кодом относительно центра Красной площади (cx, cy, cz),
 * где cy — уровень, на котором стоят ноги игрока.
 */
public final class Structures {
	/** Камера в Сибири относительно центра площади. */
	public static final int SIBIR_DX = -33, SIBIR_DZ = -33;

	/** Маленький строитель. */
	static final class B {
		final ServerLevel l;

		B(ServerLevel l) {
			this.l = l;
		}

		void set(int x, int y, int z, BlockState s) {
			l.setBlock(new BlockPos(x, y, z), s, 3);
		}

		void set(int x, int y, int z, Block b) {
			set(x, y, z, b.defaultBlockState());
		}

		void raw(int x, int y, int z, BlockState s) {
			BlockPos p = new BlockPos(x, y, z);
			// блоки с содержимым (сундуки, спавнеры) убираем «по-честному», иначе игра ругается в лог
			l.setBlock(p, s, l.getBlockEntity(p) != null ? 3 : 2);
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
			fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
		}

		/** Стены коробки (без пола и потолка). */
		void walls(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
			for (int x = x1; x <= x2; x++)
				for (int y = y1; y <= y2; y++)
					for (int z = z1; z <= z2; z++)
						if (x == x1 || x == x2 || z == z1 || z == z2) set(x, y, z, b);
		}

		void air(int x1, int y1, int z1, int x2, int y2, int z2) {
			fill(x1, y1, z1, x2, y2, z2, Blocks.AIR);
		}

		void facing(int x, int y, int z, Block b, Direction d) {
			set(x, y, z, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d));
		}

		/** Кровать: голова в (x,z), ноги — в сторону, противоположную dir. */
		void bed(int x, int y, int z, Block bed, Direction headDir) {
			BlockState base = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, headDir);
			BlockPos head = new BlockPos(x, y, z);
			BlockPos foot = head.relative(headDir.getOpposite());
			l.setBlock(foot, base.setValue(BlockStateProperties.BED_PART, BedPart.FOOT), 2);
			l.setBlock(head, base.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), 2);
		}

		void door(int x, int y, int z, Block door, Direction d) {
			BlockState s = door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
			l.setBlock(new BlockPos(x, y, z), s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), 2);
			l.setBlock(new BlockPos(x, y + 1, z), s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 2);
		}

		void chest(int x, int y, int z, Direction d, ItemStack... items) {
			facing(x, y, z, Blocks.CHEST, d);
			BlockEntity be = l.getBlockEntity(new BlockPos(x, y, z));
			if (be instanceof Container c) {
				for (int i = 0; i < items.length && i < c.getContainerSize(); i++) c.setItem(i * 2 % c.getContainerSize(), items[i]);
			}
		}

		void lamp(int x, int y, int z, int h) {
			fill(x, y, z, x, y + h - 1, z, Blocks.DARK_OAK_FENCE);
			set(x, y + h, z, Blocks.LANTERN);
		}

		/** Объёмная «кремлёвская» звезда. */
		void star(int x, int y, int z) {
			set(x, y, z, Blocks.GLOWSTONE);
			Block g = Blocks.RED_STAINED_GLASS;
			set(x + 1, y, z, g);
			set(x - 1, y, z, g);
			set(x, y + 1, z, g);
			set(x, y - 1, z, g);
			set(x, y, z + 1, g);
			set(x, y, z - 1, g);
		}
	}

	// ======================================================================
	/** Строит весь город по частям (по шагу за тик), затем вызывает done. */
	public static void buildCity(ServerLevel l, int cx, int cy, int cz, Runnable done) {
		B b = new B(l);
		List<Runnable> steps = new ArrayList<>();
		int R = City.R + 2;
		for (int x0 = cx - R; x0 <= cx + R; x0 += 6) {
			final int from = x0, to = Math.min(cx + R, x0 + 5);
			steps.add(() -> terrain(b, from, to, cz - R, cz + R, cy));
		}
		steps.add(() -> plaza(b, cx, cy, cz));
		steps.add(() -> kremlin(b, cx, cy, cz));
		steps.add(() -> mausoleum(b, cx, cy, cz));
		steps.add(() -> statue(b, cx, cy, cz));
		steps.add(() -> khrushchevka(b, cx + 24, cy, cz - 12));
		steps.add(() -> khrushchevka(b, cx + 42, cy, cz - 40));
		steps.add(() -> kolkhoz(b, cx, cy, cz));
		steps.add(() -> warehouse(b, cx, cy, cz));
		steps.add(() -> factory(b, cx, cy, cz));
		steps.add(() -> kosmodrom(b, cx, cy, cz));
		steps.add(() -> sibir(b, cx, cy, cz));
		steps.add(() -> palace(b, cx, cy, cz));
		steps.add(() -> station(b, cx, cy, cz));
		steps.add(() -> pillorySquare(b, cx, cy, cz));
		steps.add(() -> prison(b, cx, cy, cz));
		steps.add(() -> bank(b, cx, cy, cz));
		steps.add(() -> kiosk(b, cx, cy, cz));
		steps.add(() -> gastronom(b, cx, cy, cz));
		steps.add(() -> farm(b, cx, cy, cz));
		steps.add(() -> greenhouse(b, cx, cy, cz));
		steps.add(() -> southSquare(b, cx, cy, cz));
		steps.add(() -> stela(b, cx, cy, cz));
		steps.add(() -> street(b, cx, cy, cz));
		steps.add(done);
		for (int i = 0; i < steps.size(); i++) Scheduler.later(1 + i, steps.get(i));
	}

	/** Синхронная версия (для автотеста). */
	public static void buildCityNow(ServerLevel l, int cx, int cy, int cz) {
		buildCity(l, cx, cy, cz, () -> { });
	}

	private static void terrain(B b, int x1, int x2, int z1, int z2, int cy) {
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				for (int y = cy - 6; y <= cy - 2; y++) b.raw(x, y, z, dirt);
				b.raw(x, cy - 1, z, grass);
				for (int y = cy; y <= cy + 50; y++) b.raw(x, y, z, air);
			}
		}
	}

	private static void plaza(B b, int cx, int cy, int cz) {
		for (int x = cx - 21; x <= cx + 21; x++) {
			for (int z = cz - 21; z <= cz + 21; z++) {
				int dx = x - cx, dz = z - cz;
				Block f;
				if (Math.abs(dx) == 21 || Math.abs(dz) == 21) f = Blocks.RED_CONCRETE;
				else if (dx * dx + dz * dz <= 10) f = Blocks.RED_CONCRETE;
				else f = ((x + z) & 1) == 0 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE;
				b.set(x, cy - 1, z, f);
			}
		}
		b.set(cx, cy - 1, cz, Blocks.GOLD_BLOCK); // нулевой километр
		for (int z = cz - 20; z <= cz - 4; z++) b.set(cx, cy, z, Blocks.RED_CARPET);
		for (int x = cx - 23; x <= cx - 22; x++) b.set(x, cy - 1, cz, Blocks.DIRT_PATH);
		for (int x = cx + 22; x <= cx + 23; x++) b.set(x, cy - 1, cz, Blocks.STONE_BRICKS);
		for (int z = cz + 22; z <= cz + 24; z++) {
			b.set(cx - 24, cy - 1, z, Blocks.STONE_BRICKS);
			b.set(cx + 14, cy - 1, z, Blocks.STONE_BRICKS);
		}
		int[][] lamps = {{-19, -19}, {19, -19}, {-19, 19}, {19, 19}, {0, 19}, {-19, 0}, {19, 3}, {-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
		for (int[] p : lamps) b.lamp(cx + p[0], cy, cz + p[1], 3);
	}

	// ----------------------------------------------------------------------
	private static final String[] EMBLEM = {
			"...##....",
			".....##..",
			".##...##.",
			"..##...##",
			"...##..##",
			"..#.##.##",
			".#...###.",
			"#...##.#.",
			"...##....",
	};

	private static void kremlin(B b, int cx, int cy, int cz) {
		// стена
		b.fill(cx - 26, cy, cz - 26, cx + 26, cy + 6, cz - 25, Blocks.BRICKS);
		for (int x = cx - 26; x <= cx + 26; x += 2) b.fill(x, cy + 7, cz - 26, x, cy + 7, cz - 25, Blocks.BRICKS);
		// угловые башни
		for (int sx : new int[]{-1, 1}) {
			int tx = cx + sx * 26;
			int tz = cz - 26;
			b.fill(tx - 2, cy, tz - 2, tx + 2, cy + 11, tz + 2, Blocks.BRICKS);
			b.fill(tx - 1, cy + 12, tz - 1, tx + 1, cy + 12, tz + 1, Blocks.GREEN_TERRACOTTA);
			b.set(tx, cy + 13, tz, Blocks.GREEN_TERRACOTTA);
			b.star(tx, cy + 15, tz);
			b.set(tx, cy + 14, tz, Blocks.GREEN_TERRACOTTA);
		}
		// Спасская башня с воротами
		b.fill(cx - 4, cy, cz - 28, cx + 4, cy + 15, cz - 22, Blocks.BRICKS);
		b.air(cx - 1, cy, cz - 28, cx + 1, cy + 3, cz - 22);
		b.fill(cx - 1, cy - 1, cz - 28, cx + 1, cy - 1, cz - 22, Blocks.STONE_BRICKS);
		b.fill(cx - 2, cy + 16, cz - 27, cx + 2, cy + 21, cz - 23, Blocks.BRICKS);
		// часы
		b.fill(cx - 1, cy + 18, cz - 23, cx + 1, cy + 20, cz - 23, Blocks.BLACK_CONCRETE);
		b.set(cx, cy + 19, cz - 23, Blocks.GOLD_BLOCK);
		b.set(cx, cy + 20, cz - 23, Blocks.GOLD_BLOCK);
		b.set(cx + 1, cy + 19, cz - 23, Blocks.GOLD_BLOCK);
		// шатёр
		b.fill(cx - 1, cy + 22, cz - 26, cx + 1, cy + 23, cz - 24, Blocks.GREEN_TERRACOTTA);
		b.fill(cx, cy + 24, cz - 25, cx, cy + 25, cz - 25, Blocks.GREEN_TERRACOTTA);
		b.star(cx, cy + 27, cz - 25);
		// герб на фасаде (серп и молот из золота)
		b.fill(cx - 4, cy + 5, cz - 22, cx + 4, cy + 13, cz - 22, Blocks.RED_CONCRETE);
		for (int r = 0; r < EMBLEM.length; r++) {
			for (int c = 0; c < EMBLEM[r].length(); c++) {
				if (EMBLEM[r].charAt(c) == '#') b.set(cx - 4 + c, cy + 13 - r, cz - 22, Blocks.GOLD_BLOCK);
			}
		}
		// флаги на стене
		for (int x : new int[]{cx - 15, cx + 15}) {
			b.fill(x, cy + 7, cz - 25, x, cy + 11, cz - 25, Blocks.DARK_OAK_FENCE);
			b.fill(x + 1, cy + 9, cz - 25, x + 3, cy + 11, cz - 25, Blocks.RED_WOOL);
			b.set(x + 1, cy + 11, cz - 25, Blocks.YELLOW_WOOL);
		}
	}

	private static void mausoleum(B b, int cx, int cy, int cz) {
		int m = cx - 11; // ось мавзолея
		b.fill(m - 5, cy, cz - 23, m + 5, cy + 2, cz - 17, Blocks.RED_NETHER_BRICKS);
		b.fill(m - 5, cy + 2, cz - 23, m + 5, cy + 2, cz - 17, Blocks.POLISHED_BLACKSTONE);
		b.fill(m - 4, cy + 3, cz - 22, m + 4, cy + 4, cz - 18, Blocks.POLISHED_GRANITE);
		b.fill(m - 2, cy + 5, cz - 21, m + 2, cy + 5, cz - 19, Blocks.RED_NETHER_BRICKS);
		// внутри
		b.air(m - 3, cy, cz - 22, m + 3, cy + 1, cz - 18);
		b.air(m, cy, cz - 17, m, cy + 1, cz - 17);
		b.set(m, cy, cz - 21, Blocks.GOLD_BLOCK);
		b.set(m, cy + 1, cz - 21, Blocks.GLASS);
		b.set(m + 2, cy, cz - 22, Blocks.LANTERN);
		b.set(m - 2, cy, cz - 22, Blocks.LANTERN);
		b.chest(m - 2, cy, cz - 19, Direction.EAST,
				Goods.named(Items.PAPER, 1, "Пыльный партбилет №1", ChatFormatting.DARK_RED, "Владелец: неизвестен.", "Взносы не уплачены с 1924 года."),
				new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.GOLDEN_APPLE, 2), Goods.medal(),
				Goods.named(Items.BOOK, 1, "Полное собрание сочинений, том 1 из 55", ChatFormatting.GOLD, "Читать перед сном.", "Сон гарантирован."));
		// трибуны Генсека и Политрука
		b.fill(m - 1, cy, cz - 15, m + 1, cy, cz - 13, Blocks.POLISHED_GRANITE);
		b.fill(cx + 8, cy, cz - 15, cx + 10, cy, cz - 13, Blocks.POLISHED_GRANITE);
		b.facing(cx + 11, cy, cz - 14, Blocks.LECTERN, Direction.SOUTH);
		// будка КГБ
		b.set(cx + 4, cy, cz - 20, Blocks.BLACK_CONCRETE);
		b.set(cx + 6, cy, cz - 20, Blocks.BLACK_CONCRETE);
		b.lamp(cx + 4, cy + 1, cz - 20, 1);
	}

	/** Статуя Генсека. Смотрит на юг (+z), к игрокам на площади. */
	private static void statue(B b, int cx, int cy, int cz) {
		int z0 = cz + 10; // задний ряд фигуры, лицо — в сторону +z
		b.fill(cx - 3, cy, cz + 8, cx + 3, cy + 2, cz + 14, Blocks.POLISHED_GRANITE);
		b.fill(cx - 3, cy + 2, cz + 14, cx + 3, cy + 2, cz + 14, Blocks.GOLD_BLOCK);
		int y = cy + 3;
		Block skin = Blocks.WHITE_TERRACOTTA;
		Block coat = Blocks.LIGHT_GRAY_CONCRETE;
		// ноги
		for (int sx : new int[]{-1, 1}) {
			b.fill(cx + sx, y, z0, cx + sx, y, z0 + 1, Blocks.BLACK_CONCRETE);
			b.fill(cx + sx, y + 1, z0, cx + sx, y + 3, z0 + 1, Blocks.GRAY_CONCRETE);
		}
		// шинель
		b.fill(cx - 2, y + 3, z0, cx + 2, y + 8, z0 + 1, coat);
		b.fill(cx - 2, y + 4, z0 + 1, cx + 2, y + 4, z0 + 1, Blocks.BLACK_CONCRETE);
		b.set(cx + 1, y + 7, z0 + 1, Blocks.GOLD_BLOCK);
		// одна рука вниз
		b.fill(cx + 3, y + 5, z0 + 1, cx + 3, y + 8, z0 + 1, coat);
		b.set(cx + 3, y + 4, z0 + 1, skin);
		// другая указывает в светлое будущее
		b.set(cx - 3, y + 8, z0 + 1, coat);
		b.set(cx - 3, y + 9, z0 + 2, coat);
		b.set(cx - 3, y + 10, z0 + 3, skin);
		// голова
		b.fill(cx - 1, y + 9, z0, cx + 1, y + 11, z0 + 2, skin);
		b.set(cx - 1, y + 10, z0 + 2, Blocks.BLACK_CONCRETE);
		b.set(cx + 1, y + 10, z0 + 2, Blocks.BLACK_CONCRETE);
		b.set(cx, y + 10, z0 + 3, skin);
		// УСЫ
		b.fill(cx - 2, y + 9, z0 + 2, cx + 2, y + 9, z0 + 2, Blocks.BLACK_WOOL);
		b.set(cx - 2, y + 9, z0 + 3, Blocks.BLACK_WOOL);
		b.set(cx + 2, y + 9, z0 + 3, Blocks.BLACK_WOOL);
		// фуражка
		b.fill(cx - 1, y + 12, z0, cx + 1, y + 13, z0 + 2, Blocks.GREEN_CONCRETE);
		b.fill(cx - 1, y + 12, z0 + 3, cx + 1, y + 12, z0 + 3, Blocks.BLACK_CONCRETE);
		b.fill(cx - 1, y + 12, z0 + 2, cx + 1, y + 12, z0 + 2, Blocks.RED_CONCRETE);
		b.set(cx, y + 13, z0 + 2, Blocks.GOLD_BLOCK);
	}

	/** Пятиэтажная хрущёвка. x0,z0 — северо-западный угол, ширина 12 (x), длина 25 (z). */
	public static void khrushchevka(B b, int x0, int cy, int z0) {
		int x1 = x0 + 11, z1 = z0 + 24;
		int top = cy + 19;
		int zc = z0 + 12;
		// стены
		b.walls(x0, cy, z0, x1, top, z1, Blocks.WHITE_CONCRETE);
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.SMOOTH_STONE);
		for (int k = 1; k <= 5; k++) {
			int fy = cy - 1 + 4 * k;
			b.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, Blocks.LIGHT_GRAY_CONCRETE);
			b.walls(x0, fy, z0, x1, fy, z1, Blocks.LIGHT_GRAY_CONCRETE);
		}
		// окна
		for (int k = 0; k < 5; k++) {
			int fy = cy - 1 + 4 * k;
			for (int z = z0 + 2; z < z1; z += 3) {
				for (int y = fy + 2; y <= fy + 3; y++) {
					if (!(k == 0 && Math.abs(z - zc) <= 1)) b.set(x0, y, z, Blocks.GLASS_PANE);
					b.set(x1, y, z, Blocks.GLASS_PANE);
				}
			}
			for (int x = x0 + 2; x < x1; x += 3) {
				for (int y = fy + 2; y <= fy + 3; y++) {
					b.set(x, y, z0, Blocks.GLASS_PANE);
					b.set(x, y, z1, Blocks.GLASS_PANE);
				}
			}
		}
		// подъезд
		b.door(x0, cy, zc, Blocks.SPRUCE_DOOR, Direction.WEST);
		b.fill(x0 - 2, cy + 3, zc - 1, x0 - 1, cy + 3, zc + 1, Blocks.SMOOTH_STONE_SLAB);
		b.set(x0 - 1, cy + 2, zc + 1, Blocks.LANTERN);
		// лестница на все этажи и крышу
		int lx = x0 + 1, lz = zc + 3;
		for (int y = cy; y <= top; y++) b.facing(lx, y, lz, Blocks.LADDER, Direction.EAST);
		// квартиры
		for (int k = 0; k < 5; k++) {
			int fy = cy - 1 + 4 * k;
			int y = fy + 1;
			b.bed(x1 - 2, y, z0 + 2, Blocks.RED_BED, Direction.NORTH);
			b.bed(x1 - 4, y, z0 + 2, Blocks.RED_BED, Direction.NORTH);
			b.chest(x1 - 2, y, z1 - 2, Direction.WEST, Goods.bread(4));
			b.set(x1 - 3, y, z1 - 2, Blocks.CRAFTING_TABLE);
			b.facing(x1 - 4, y, z1 - 2, Blocks.FURNACE, Direction.NORTH);
			b.set(x1 - 5, y, z1 - 2, Blocks.POTTED_RED_TULIP);
			// перегородка
			b.fill(x0 + 1, y, zc - 3, x1 - 4, y + 2, zc - 3, Blocks.WHITE_CONCRETE);
			b.air(x0 + 3, y, zc - 3, x0 + 3, y + 1, zc - 3);
			// ковёр на стене — обязательный элемент интерьера!
			b.fill(x1, y, zc - 2, x1, y + 2, zc + 2, Blocks.RED_WOOL);
			b.set(x1, y + 1, zc, Blocks.YELLOW_WOOL);
			b.set(x1, y, zc - 2, Blocks.YELLOW_WOOL);
			b.set(x1, y + 2, zc + 2, Blocks.YELLOW_WOOL);
			b.set(x1, y + 2, zc - 2, Blocks.YELLOW_WOOL);
			b.set(x1, y, zc + 2, Blocks.YELLOW_WOOL);
			// свет
			for (int z = z0 + 4; z < z1; z += 6) b.set(x0 + 6, fy + 4, z, Blocks.GLOWSTONE);
			// ковёр на полу
			b.fill(x0 + 5, y, zc + 2, x0 + 7, y, zc + 5, Blocks.RED_CARPET);
		}
		// крыша
		b.walls(x0, top + 1, z0, x1, top + 1, z1, Blocks.LIGHT_GRAY_CONCRETE);
		b.set(lx, top, lz, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		b.fill(x0 + 6, top + 1, zc, x0 + 6, top + 4, zc, Blocks.IRON_BARS);
		b.set(x0 + 6, top + 5, zc, Blocks.LIGHTNING_ROD);
		b.fill(x0 + 5, top + 4, zc, x0 + 7, top + 4, zc, Blocks.IRON_BARS);
		b.fill(x1 - 1, top + 1, z0 + 1, x1 - 1, top + 6, z0 + 1, Blocks.DARK_OAK_FENCE);
		b.fill(x1 - 1, top + 4, z0 + 2, x1 - 1, top + 6, z0 + 4, Blocks.RED_WOOL);
		b.set(x1 - 1, top + 6, z0 + 2, Blocks.YELLOW_WOOL);
	}

	private static void kolkhoz(B b, int cx, int cy, int cz) {
		int xa = cx - 35, xb = cx - 25, za = cz - 8, zb = cz + 4;
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
		for (int x = xa; x <= xb; x++) {
			for (int z = za; z <= zb; z++) {
				if (z == cz - 4 || z == cz + 2) {
					b.set(x, cy - 1, z, Blocks.WATER);
					continue;
				}
				b.set(x, cy - 1, z, farmland);
				Block crop = x <= cx - 32 ? Blocks.WHEAT : x <= cx - 29 ? Blocks.POTATOES : Blocks.CARROTS;
				b.set(x, cy, z, crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
			}
		}
		// забор
		for (int x = xa - 1; x <= xb + 1; x++) {
			b.set(x, cy, za - 1, Blocks.OAK_FENCE);
			b.set(x, cy, zb + 1, Blocks.OAK_FENCE);
		}
		for (int z = za - 1; z <= zb + 1; z++) {
			b.set(xa - 1, cy, z, Blocks.OAK_FENCE);
			b.set(xb + 1, cy, z, Blocks.OAK_FENCE);
		}
		b.facing(xb + 1, cy, cz, Blocks.OAK_FENCE_GATE, Direction.EAST);
		// пугало
		b.set(cx - 30, cy - 1, cz - 2, Blocks.DIRT);
		b.set(cx - 30, cy, cz - 2, Blocks.OAK_FENCE);
		b.set(cx - 30, cy + 1, cz - 2, Blocks.HAY_BLOCK);
		b.facing(cx - 30, cy + 2, cz - 2, Blocks.CARVED_PUMPKIN, Direction.EAST);
		b.set(cx - 31, cy + 1, cz - 2, Blocks.OAK_FENCE);
		b.set(cx - 29, cy + 1, cz - 2, Blocks.OAK_FENCE);
		// амбар
		int bx0 = cx - 35, bx1 = cx - 28, bz0 = cz + 7, bz1 = cz + 14;
		b.walls(bx0, cy, bz0, bx1, cy + 4, bz1, Blocks.SPRUCE_PLANKS);
		b.fill(bx0, cy - 1, bz0, bx1, cy - 1, bz1, Blocks.SPRUCE_PLANKS);
		for (int i = 0; i < 4; i++) b.fill(bx0 + i, cy + 5 + i, bz0, bx1 - i, cy + 5 + i, bz1, Blocks.DARK_OAK_PLANKS);
		b.air(bx1, cy, cz + 10, bx1, cy + 2, cz + 11);
		b.fill(bx0 + 1, cy, bz0 + 1, bx0 + 2, cy + 1, bz1 - 1, Blocks.HAY_BLOCK);
		b.chest(cx - 30, cy, bz1 - 1, Direction.NORTH, new ItemStack(Items.WHEAT_SEEDS, 32), new ItemStack(Items.POTATO, 16),
				new ItemStack(Items.CARROT, 16), new ItemStack(Items.BONE_MEAL, 32), new ItemStack(Items.IRON_HOE), Goods.corn(4));
		b.set(cx - 29, cy, bz1 - 1, Blocks.COMPOSTER);
		b.set(cx - 31, cy, bz1 - 1, Blocks.CRAFTING_TABLE);
		b.set(cx - 31, cy + 5, cz + 10, Blocks.GLOWSTONE);
		for (int z = cz + 5; z <= cz + 10; z++) b.set(cx - 26, cy - 1, z, Blocks.DIRT_PATH);
	}

	private static void warehouse(B b, int cx, int cy, int cz) {
		int x0 = cx - 32, x1 = cx - 18, z0 = cz + 24, z1 = cz + 33;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.SPRUCE_PLANKS);
		b.walls(x0, cy, z0, x1, cy + 5, z1, Blocks.STONE_BRICKS);
		b.fill(x0, cy + 6, z0, x1, cy + 6, z1, Blocks.SPRUCE_PLANKS);
		b.air(cx - 25, cy, z0, cx - 24, cy + 2, z0);
		// надпись-полоса
		b.fill(x0, cy + 4, z0, x1, cy + 4, z0, Blocks.RED_CONCRETE);
		for (int x = x0 + 1; x < x1; x += 2) {
			b.chest(x, cy, z1 - 1, Direction.NORTH);
			b.set(x + 1, cy, z1 - 1, Blocks.BARREL);
			b.set(x + 1, cy + 1, z1 - 1, Blocks.BARREL);
		}
		b.chest(x0 + 1, cy, z1 - 1, Direction.NORTH, Goods.bread(16), new ItemStack(Items.COAL, 16), new ItemStack(Items.IRON_INGOT, 6),
				new ItemStack(Items.TORCH, 32), Goods.named(Items.PAPER, 1, "Накладная", ChatFormatting.WHITE, "Склад Народа: кладите сюда общее имущество!"));
		for (int x = x0 + 3; x < x1; x += 4) b.set(x, cy + 6, cz + 28, Blocks.GLOWSTONE);
		b.fill(x0 + 1, cy, z0 + 2, x0 + 1, cy + 2, z1 - 3, Blocks.BARREL);
	}

	private static void factory(B b, int cx, int cy, int cz) {
		int x0 = cx + 8, x1 = cx + 22, z0 = cz + 24, z1 = cz + 34;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.POLISHED_ANDESITE);
		b.walls(x0, cy, z0, x1, cy + 6, z1, Blocks.BRICKS);
		b.fill(x0, cy + 7, z0, x1, cy + 7, z1, Blocks.LIGHT_GRAY_CONCRETE);
		for (int x = x0 + 2; x < x1; x += 3) {
			b.fill(x, cy + 3, z0, x, cy + 4, z0, Blocks.GLASS);
			b.fill(x, cy + 3, z1, x, cy + 4, z1, Blocks.GLASS);
		}
		b.air(cx + 14, cy, z0, cx + 15, cy + 2, z0);
		// труба
		b.fill(cx + 19, cy, cz + 31, cx + 21, cy + 20, cz + 33, Blocks.BRICKS);
		b.set(cx + 20, cy + 20, cz + 32, Blocks.HAY_BLOCK);
		b.set(cx + 20, cy + 21, cz + 32, Blocks.CAMPFIRE);
		b.fill(cx + 19, cy + 18, cz + 31, cx + 21, cy + 18, cz + 33, Blocks.RED_CONCRETE);
		// оборудование
		for (int z = z0 + 2; z <= z1 - 2; z++) b.facing(x0 + 1, cy, z, Blocks.FURNACE, Direction.EAST);
		b.facing(x0 + 3, cy, z1 - 1, Blocks.BLAST_FURNACE, Direction.NORTH);
		b.facing(x0 + 4, cy, z1 - 1, Blocks.BLAST_FURNACE, Direction.NORTH);
		b.facing(x0 + 5, cy, z1 - 1, Blocks.SMOKER, Direction.NORTH);
		b.set(x0 + 7, cy, z1 - 1, Blocks.CRAFTING_TABLE);
		b.set(x0 + 8, cy, z1 - 1, Blocks.SMITHING_TABLE);
		b.facing(x0 + 9, cy, z1 - 1, Blocks.STONECUTTER, Direction.NORTH);
		b.facing(x0 + 4, cy, z0 + 4, Blocks.ANVIL, Direction.EAST);
		b.set(x0 + 6, cy, z0 + 4, Blocks.CAULDRON);
		b.facing(x0 + 8, cy, z0 + 4, Blocks.LOOM, Direction.NORTH);
		b.set(x0 + 9, cy, z0 + 4, Blocks.CARTOGRAPHY_TABLE);
		b.chest(x0 + 6, cy, z1 - 1, Direction.NORTH, new ItemStack(Items.COAL, 24), new ItemStack(Items.IRON_INGOT, 4),
				Goods.named(Items.PAPER, 1, "Норма выработки", ChatFormatting.WHITE, "Выплавить: всё.", "Срок: вчера."));
		for (int x = x0 + 3; x < x1; x += 4) b.set(x, cy + 7, cz + 29, Blocks.GLOWSTONE);
	}

	private static void kosmodrom(B b, int cx, int cy, int cz) {
		int rx = cx + 32, rz = cz - 33;
		b.fill(rx - 3, cy - 1, rz - 3, rx + 3, cy - 1, rz + 3, Blocks.SMOOTH_STONE);
		for (int i = -3; i <= 3; i++) {
			b.set(rx + i, cy - 1, rz - 3, Blocks.YELLOW_CONCRETE);
			b.set(rx + i, cy - 1, rz + 3, Blocks.YELLOW_CONCRETE);
			b.set(rx - 3, cy - 1, rz + i, Blocks.YELLOW_CONCRETE);
			b.set(rx + 3, cy - 1, rz + i, Blocks.YELLOW_CONCRETE);
		}
		// двигатели
		for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) b.set(rx + dx, cy, rz + dz, Blocks.BLACK_CONCRETE);
		b.set(rx, cy, rz, Blocks.MAGMA_BLOCK);
		// корпус
		b.fill(rx - 1, cy + 1, rz - 1, rx + 1, cy + 14, rz + 1, Blocks.WHITE_CONCRETE);
		b.fill(rx - 1, cy + 5, rz - 1, rx + 1, cy + 5, rz + 1, Blocks.RED_CONCRETE);
		b.fill(rx - 1, cy + 10, rz - 1, rx + 1, cy + 10, rz + 1, Blocks.RED_CONCRETE);
		b.set(rx, cy + 12, rz + 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
		b.set(rx, cy + 8, rz + 1, Blocks.RED_CONCRETE);
		// стабилизаторы
		int[][] fins = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}};
		for (int[] f : fins) b.fill(rx + f[0], cy, rz + f[1], rx + f[0], cy + 3, rz + f[1], Blocks.RED_CONCRETE);
		// нос
		b.set(rx, cy + 15, rz, Blocks.WHITE_CONCRETE);
		b.set(rx + 1, cy + 15, rz, Blocks.WHITE_CONCRETE);
		b.set(rx - 1, cy + 15, rz, Blocks.WHITE_CONCRETE);
		b.set(rx, cy + 15, rz + 1, Blocks.WHITE_CONCRETE);
		b.set(rx, cy + 15, rz - 1, Blocks.WHITE_CONCRETE);
		b.set(rx, cy + 16, rz, Blocks.RED_CONCRETE);
		b.set(rx, cy + 17, rz, Blocks.LIGHTNING_ROD);
		// башня обслуживания
		b.fill(rx - 3, cy, rz - 3, rx - 3, cy + 12, rz - 3, Blocks.IRON_BARS);
		b.fill(rx - 3, cy + 12, rz - 3, rx - 1, cy + 12, rz - 3, Blocks.IRON_BARS);
	}

	private static void sibir(B b, int cx, int cy, int cz) {
		int sx = cx + SIBIR_DX, sz = cz + SIBIR_DZ;
		for (int x = sx - 4; x <= sx + 4; x++) {
			for (int z = sz - 4; z <= sz + 4; z++) {
				b.set(x, cy - 1, z, Blocks.SNOW_BLOCK);
				if (Math.abs(x - sx) > 2 || Math.abs(z - sz) > 2) b.set(x, cy, z, Blocks.SNOW);
			}
		}
		b.fill(sx - 2, cy - 1, sz - 2, sx + 2, cy + 3, sz + 2, Blocks.PACKED_ICE);
		b.air(sx - 1, cy, sz - 1, sx + 1, cy + 2, sz + 1);
		b.fill(sx - 1, cy + 3, sz - 1, sx + 1, cy + 3, sz + 1, Blocks.GLASS);
		b.set(sx - 1, cy, sz - 1, Blocks.LANTERN);
		b.set(sx + 1, cy, sz + 1, Blocks.SNOW);
		// табличка из блоков: синие «решётки»
		b.fill(sx - 1, cy + 1, sz + 2, sx + 1, cy + 1, sz + 2, Blocks.IRON_BARS);
		// ёлки
		for (int[] t : new int[][]{{-4, -4}, {4, -4}, {-4, 4}}) tree(b, sx + t[0], cy, sz + t[1]);
	}

	private static void tree(B b, int x, int y, int z) {
		for (int i = 0; i < 6; i++) b.set(x, y + i, z, Blocks.SPRUCE_LOG);
		BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		for (int ly = 2; ly <= 6; ly++) {
			int r = ly <= 3 ? 2 : ly <= 5 ? 1 : 0;
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (dx == 0 && dz == 0 && ly < 6) continue;
					if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
					b.set(x + dx, y + ly, z + dz, leaves);
				}
		}
		b.set(x, y + 7, z, leaves);
	}

	// ======================================================================
	/** Маленькая квартира для указа «О квартирном вопросе». pos — уровень земли. */
	public static void miniHouse(ServerLevel l, BlockPos pos) {
		B b = new B(l);
		int x = pos.getX() - 3, y = pos.getY(), z = pos.getZ() - 3;
		b.air(x, y, z, x + 6, y + 6, z + 6);
		b.fill(x, y - 2, z, x + 6, y - 2, z + 6, Blocks.STONE);
		b.fill(x, y - 1, z, x + 6, y - 1, z + 6, Blocks.LIGHT_GRAY_CONCRETE);
		b.walls(x, y, z, x + 6, y + 3, z + 6, Blocks.WHITE_CONCRETE);
		b.walls(x, y + 3, z, x + 6, y + 3, z + 6, Blocks.LIGHT_GRAY_CONCRETE);
		b.fill(x, y + 4, z, x + 6, y + 4, z + 6, Blocks.LIGHT_GRAY_CONCRETE);
		b.set(x + 3, y + 4, z + 3, Blocks.GLOWSTONE);
		for (int i : new int[]{1, 5}) {
			b.fill(x + i, y + 1, z, x + i, y + 2, z, Blocks.GLASS_PANE);
			b.fill(x, y + 1, z + i, x, y + 2, z + i, Blocks.GLASS_PANE);
			b.fill(x + i, y + 1, z + 6, x + i, y + 2, z + 6, Blocks.GLASS_PANE);
		}
		b.door(x + 3, y, z + 6, Blocks.SPRUCE_DOOR, Direction.SOUTH);
		b.bed(x + 1, y, z + 1, Blocks.RED_BED, Direction.NORTH);
		b.chest(x + 5, y, z + 1, Direction.SOUTH, Goods.apartmentOrder(), Goods.bread(8), Goods.sausage(4), new ItemStack(Items.TORCH, 16));
		b.set(x + 5, y, z + 2, Blocks.CRAFTING_TABLE);
		b.facing(x + 5, y, z + 3, Blocks.FURNACE, Direction.WEST);
		b.fill(x + 6, y + 1, z + 2, x + 6, y + 2, z + 4, Blocks.RED_WOOL);
		b.set(x + 6, y + 1, z + 3, Blocks.YELLOW_WOOL);
		b.fill(x + 2, y, z + 3, x + 4, y, z + 4, Blocks.RED_CARPET);
	}

	// ======================================================================
	// Версия 2.0: новые постройки
	// ======================================================================

	/** Пиксельный шрифт 3×5. */
	private static final java.util.Map<Character, String[]> FONT = new java.util.HashMap<>();

	static {
		FONT.put('С', new String[]{"###", "#..", "#..", "#..", "###"});
		FONT.put('Л', new String[]{".##", "#.#", "#.#", "#.#", "#.#"});
		FONT.put('А', new String[]{"###", "#.#", "###", "#.#", "#.#"});
		FONT.put('В', new String[]{"##.", "#.#", "##.", "#.#", "##."});
		FONT.put('Т', new String[]{"###", ".#.", ".#.", ".#.", ".#."});
		FONT.put('Р', new String[]{"###", "#.#", "###", "#..", "#.."});
		FONT.put('У', new String[]{"#.#", "#.#", "###", "..#", "###"});
		FONT.put('Д', new String[]{".#.", "#.#", "#.#", "###", "#.#"});
	}

	/** Надпись на плоскости z, читается с севера (зритель смотрит на юг: буквы идут с востока на запад). */
	private static void text(B b, String txt, int xStart, int yTop, int z, Block ink) {
		for (int i = 0; i < txt.length(); i++) {
			String[] g = FONT.get(txt.charAt(i));
			if (g == null) continue;
			int cx = xStart - i * 4;
			for (int r = 0; r < 5; r++)
				for (int c = 0; c < 3; c++)
					if (g[r].charAt(c) == '#') b.set(cx - c, yTop - r, z, ink);
		}
	}

	/** Дворец Советов — резиденция Великого Вождя. */
	private static void palace(B b, int cx, int cy, int cz) {
		int z0 = cz - 62, z1 = cz - 36;
		b.fill(cx - 15, cy - 1, z0, cx + 15, cy - 1, cz - 33, Blocks.POLISHED_GRANITE);
		for (int z = cz - 33; z <= cz - 29; z++) b.fill(cx - 1, cy - 1, z, cx + 1, cy - 1, z, Blocks.STONE_BRICKS);
		// 1 ярус — тронный зал
		b.walls(cx - 14, cy, z0, cx + 14, cy + 9, z1, Blocks.WHITE_CONCRETE);
		b.walls(cx - 14, cy + 9, z0, cx + 14, cy + 9, z1, Blocks.RED_CONCRETE);
		b.fill(cx - 13, cy + 10, z0 + 1, cx + 13, cy + 10, z1 - 1, Blocks.QUARTZ_BLOCK);
		b.fill(cx - 14, cy + 10, z0, cx + 14, cy + 10, z0, Blocks.RED_CONCRETE);
		for (int x = cx - 12; x <= cx + 12; x += 4) {
			b.fill(x, cy + 3, z1, x, cy + 7, z1, Blocks.GLASS);
			b.fill(x, cy + 3, z0, x, cy + 7, z0, Blocks.GLASS);
		}
		for (int z = z0 + 3; z < z1; z += 4) {
			b.fill(cx - 14, cy + 3, z, cx - 14, cy + 7, z, Blocks.GLASS);
			b.fill(cx + 14, cy + 3, z, cx + 14, cy + 7, z, Blocks.GLASS);
		}
		b.air(cx - 2, cy, z1, cx + 2, cy + 4, z1);
		// колоннада
		for (int x = cx - 13; x <= cx + 13; x += 3) b.fill(x, cy, cz - 34, x, cy + 8, cz - 34, Blocks.QUARTZ_PILLAR);
		b.fill(cx - 14, cy + 9, cz - 35, cx + 14, cy + 9, cz - 34, Blocks.QUARTZ_BLOCK);
		b.fill(cx - 14, cy + 10, cz - 35, cx + 14, cy + 10, cz - 34, Blocks.RED_CONCRETE);
		// зал
		b.fill(cx - 13, cy - 1, z0 + 1, cx + 13, cy - 1, z1 - 1, Blocks.POLISHED_DIORITE);
		for (int z = z0 + 5; z < z1; z++) b.fill(cx - 1, cy, z, cx + 1, cy, z, Blocks.RED_CARPET);
		b.fill(cx - 3, cy, cz - 58, cx + 3, cy, cz - 54, Blocks.RED_CONCRETE);
		b.fill(cx - 3, cy, cz - 54, cx + 3, cy, cz - 54, Blocks.GOLD_BLOCK);
		b.fill(cx - 1, cy + 1, cz - 58, cx + 1, cy + 3, cz - 58, Blocks.GOLD_BLOCK);
		b.set(cx, cy + 1, cz - 57, Blocks.GOLD_BLOCK);
		b.fill(cx - 5, cy + 1, z0 + 1, cx + 5, cy + 8, z0 + 1, Blocks.RED_WOOL);
		b.star(cx, cy + 6, z0 + 2);
		for (int z = z0 + 4; z < z1 - 2; z += 5) {
			b.set(cx - 8, cy + 9, z, Blocks.GLOWSTONE);
			b.set(cx + 8, cy + 9, z, Blocks.GLOWSTONE);
			b.facing(cx - 12, cy, z, Blocks.LECTERN, Direction.EAST);
			b.facing(cx + 12, cy, z, Blocks.LECTERN, Direction.WEST);
			b.set(cx - 13, cy, z, Blocks.RED_BANNER);
			b.set(cx + 13, cy, z, Blocks.RED_BANNER);
		}
		// верхние ярусы («сталинская высотка»)
		b.walls(cx - 10, cy + 11, cz - 58, cx + 10, cy + 20, cz - 40, Blocks.WHITE_CONCRETE);
		b.fill(cx - 10, cy + 21, cz - 58, cx + 10, cy + 21, cz - 40, Blocks.RED_CONCRETE);
		b.walls(cx - 6, cy + 22, cz - 54, cx + 6, cy + 31, cz - 44, Blocks.WHITE_CONCRETE);
		b.fill(cx - 6, cy + 32, cz - 54, cx + 6, cy + 32, cz - 44, Blocks.RED_CONCRETE);
		for (int y = cy + 13; y <= cy + 30; y += 3) {
			for (int x = cx - 8; x <= cx + 8; x += 4) {
				if (y <= cy + 19) {
					b.set(x, y, cz - 40, Blocks.LIGHT_BLUE_STAINED_GLASS);
					b.set(x, y, cz - 58, Blocks.LIGHT_BLUE_STAINED_GLASS);
				} else if (Math.abs(x - cx) <= 4) {
					b.set(x, y, cz - 44, Blocks.LIGHT_BLUE_STAINED_GLASS);
					b.set(x, y, cz - 54, Blocks.LIGHT_BLUE_STAINED_GLASS);
				}
			}
		}
		b.walls(cx - 2, cy + 33, cz - 51, cx + 2, cy + 40, cz - 47, Blocks.WHITE_CONCRETE);
		b.fill(cx - 1, cy + 41, cz - 50, cx + 1, cy + 43, cz - 48, Blocks.GOLD_BLOCK);
		b.fill(cx, cy + 44, cz - 49, cx, cy + 45, cz - 49, Blocks.GOLD_BLOCK);
		b.star(cx, cy + 47, cz - 49);
		for (int sx : new int[]{-1, 1}) {
			int fx = cx + sx * 13;
			b.fill(fx, cy + 11, cz - 35, fx, cy + 16, cz - 35, Blocks.DARK_OAK_FENCE);
			b.fill(fx + (sx > 0 ? -3 : 1), cy + 14, cz - 35, fx + (sx > 0 ? -1 : 3), cy + 16, cz - 35, Blocks.RED_WOOL);
		}
	}

	/** Отделение милиции и рельсы «Чёрного воронка» до тюрьмы. */
	private static void station(B b, int cx, int cy, int cz) {
		int x0 = cx + 26, x1 = cx + 36, z0 = cz + 16, z1 = cz + 24;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.SMOOTH_STONE);
		b.walls(x0, cy, z0, x1, cy + 5, z1, Blocks.LIGHT_BLUE_CONCRETE);
		b.walls(x0, cy + 4, z0, x1, cy + 4, z1, Blocks.WHITE_CONCRETE);
		b.fill(x0, cy + 6, z0, x1, cy + 6, z1, Blocks.LIGHT_GRAY_CONCRETE);
		b.air(cx + 30, cy, z0, cx + 31, cy + 2, z0);
		b.air(cx + 31, cy, z1, cx + 31, cy + 2, z1);
		for (int x = x0 + 2; x < x1; x += 3) b.fill(x, cy + 2, z0, x, cy + 3, z0, Blocks.GLASS_PANE);
		// «обезьянник»
		b.walls(cx + 33, cy, cz + 17, cx + 35, cy + 2, cz + 20, Blocks.IRON_BARS);
		b.fill(cx + 28, cy, cz + 18, cx + 29, cy, cz + 18, Blocks.SPRUCE_PLANKS);
		b.facing(cx + 28, cy + 1, cz + 18, Blocks.LECTERN, Direction.NORTH);
		b.set(cx + 31, cy + 5, cz + 20, Blocks.GLOWSTONE);
		// мигалка
		b.set(cx + 31, cy + 7, cz + 16, Blocks.BLUE_STAINED_GLASS);
		b.set(cx + 30, cy + 7, cz + 16, Blocks.RED_STAINED_GLASS);
		b.set(cx + 31, cy + 6, cz + 16, Blocks.GLOWSTONE);
		// рельсы
		for (int z = cz + 22; z <= cz + 31; z++) rail(b, cx + 31, cy, z, RailShape.NORTH_SOUTH);
		rail(b, cx + 31, cy, cz + 32, RailShape.NORTH_EAST);
		for (int x = cx + 32; x <= cx + 49; x++) rail(b, x, cy, cz + 32, RailShape.EAST_WEST);
		rail(b, cx + 50, cy, cz + 32, RailShape.SOUTH_WEST);
		for (int z = cz + 33; z <= cz + 41; z++) rail(b, cx + 50, cy, z, RailShape.NORTH_SOUTH);
		for (int x = cx + 34; x <= cx + 48; x += 7) b.lamp(x, cy, cz + 31, 3);
	}

	private static void rail(B b, int x, int y, int z, RailShape shape) {
		b.set(x, y - 1, z, Blocks.STONE_BRICKS);
		b.l.setBlock(new BlockPos(x, y, z), Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape), 2);
	}

	/** Площадь позора у рельсов. */
	private static void pillorySquare(B b, int cx, int cy, int cz) {
		b.fill(cx + 34, cy - 1, cz + 34, cx + 46, cy - 1, cz + 40, Blocks.POLISHED_ANDESITE);
		b.fill(cx + 39, cy, cz + 36, cx + 41, cy, cz + 38, Blocks.POLISHED_BLACKSTONE);
		b.fill(cx + 39, cy + 1, cz + 38, cx + 39, cy + 3, cz + 38, Blocks.DARK_OAK_FENCE);
		b.fill(cx + 41, cy + 1, cz + 38, cx + 41, cy + 3, cz + 38, Blocks.DARK_OAK_FENCE);
		b.fill(cx + 39, cy + 3, cz + 38, cx + 41, cy + 3, cz + 38, Blocks.DARK_OAK_PLANKS);
		b.fill(cx + 37, cy + 1, cz + 40, cx + 43, cy + 4, cz + 40, Blocks.RED_WOOL);
		b.fill(cx + 38, cy + 2, cz + 40, cx + 42, cy + 3, cz + 40, Blocks.BLACK_WOOL);
		b.set(cx + 40, cy - 1, cz + 33, Blocks.STONE_BRICKS);
		b.lamp(cx + 35, cy, cz + 35, 3);
		b.lamp(cx + 45, cy, cz + 35, 3);
	}

	/** Тюрьма «Светлый путь». */
	private static void prison(B b, int cx, int cy, int cz) {
		int x0 = cx + 42, x1 = cx + 62, z0 = cz + 42, z1 = cz + 62;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.GRAVEL);
		b.fill(x0, cy - 2, z0, x1, cy - 2, z1, Blocks.STONE);
		b.walls(x0, cy, z0, x1, cy + 6, z1, Blocks.STONE_BRICKS);
		b.walls(x0, cy + 7, z0, x1, cy + 7, z1, Blocks.IRON_BARS);
		b.air(cx + 49, cy, z0, cx + 51, cy + 3, z0);
		b.fill(cx + 49, cy - 1, z0, cx + 51, cy - 1, z0 + 4, Blocks.STONE_BRICKS);
		for (int k = 0; k < 4; k++) {
			int a = cx + 43 + 4 * k;
			b.fill(a, cy - 1, cz + 55, a + 4, cy + 3, cz + 59, Blocks.STONE_BRICKS);
			b.air(a + 1, cy, cz + 56, a + 3, cy + 2, cz + 58);
			b.fill(a + 1, cy, cz + 55, a + 3, cy + 2, cz + 55, Blocks.IRON_BARS);
			b.set(a + 1, cy, cz + 58, Blocks.COBBLESTONE);
			b.set(a + 3, cy, cz + 58, Blocks.COBBLESTONE);
			b.set(a + 3, cy + 1, cz + 58, Blocks.COBBLESTONE);
			b.set(a + 1, cy, cz + 56, Blocks.HAY_BLOCK);
			b.set(a + 2, cy + 3, cz + 57, Blocks.GLOWSTONE);
		}
		// вышка
		b.fill(cx + 58, cy, cz + 43, cx + 61, cy + 9, cz + 46, Blocks.STONE_BRICKS);
		b.fill(cx + 57, cy + 10, cz + 42, cx + 62, cy + 10, cz + 47, Blocks.SPRUCE_PLANKS);
		b.walls(cx + 57, cy + 11, cz + 42, cx + 62, cy + 11, cz + 47, Blocks.SPRUCE_FENCE);
		b.set(cx + 59, cy + 12, cz + 44, Blocks.LANTERN);
		for (int x = cx + 45; x <= cx + 57; x += 6) b.lamp(x, cy, cz + 48, 3);
	}

	/** МФО «Серп и Процент». */
	private static void bank(B b, int cx, int cy, int cz) {
		int x0 = cx + 40, x1 = cx + 52, z0 = cz - 8, z1 = cz + 4;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.POLISHED_DIORITE);
		b.walls(x0, cy, z0, x1, cy + 6, z1, Blocks.QUARTZ_BLOCK);
		b.fill(x0, cy + 1, z0 + 1, x0, cy + 4, z1 - 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
		b.air(x0, cy, cz - 2, x0, cy + 2, cz - 1);
		b.fill(x0, cy + 7, z0, x1, cy + 7, z1, Blocks.GOLD_BLOCK);
		b.fill(x0 + 1, cy + 8, z0 + 1, x1 - 1, cy + 8, z1 - 1, Blocks.QUARTZ_BLOCK);
		b.fill(cx + 46, cy, z0 + 1, cx + 46, cy, z1 - 1, Blocks.SMOOTH_QUARTZ);
		b.air(cx + 46, cy, cz - 2, cx + 46, cy, cz - 1);
		b.fill(cx + 50, cy, z0 + 1, cx + 51, cy + 2, z0 + 2, Blocks.IRON_BLOCK);
		b.set(cx + 51, cy, z1 - 1, Blocks.GOLD_BLOCK);
		b.set(cx + 51, cy + 1, z1 - 1, Blocks.GOLD_BLOCK);
		b.set(cx + 46, cy + 6, cz - 2, Blocks.GLOWSTONE);
		b.set(cx + 43, cy + 6, cz - 2, Blocks.GLOWSTONE);
		b.lamp(x0 - 2, cy, cz - 4, 3);
	}

	/** Киоск «Госудача» (апгрейдер). */
	private static void kiosk(B b, int cx, int cy, int cz) {
		int x0 = cx + 40, x1 = cx + 46, z0 = cz + 6, z1 = cz + 11;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.PURPLE_CONCRETE);
		b.walls(x0, cy, z0, x1, cy + 4, z1, Blocks.PURPLE_CONCRETE);
		b.fill(x0, cy + 5, z0, x1, cy + 5, z1, Blocks.MAGENTA_STAINED_GLASS);
		b.air(x0, cy, z0 + 1, x0, cy + 2, z1 - 1);
		Block[] wheel = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL};
		for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) b.set(x1, cy + 1 + y, z0 + 1 + z, wheel[(y + z) % 4]);
		b.set(x1, cy + 2, z0 + 2, Blocks.GOLD_BLOCK);
		b.set(x0 + 3, cy + 4, z0 + 2, Blocks.GLOWSTONE);
		b.set(x0 + 3, cy + 4, z1 - 2, Blocks.SEA_LANTERN);
		b.fill(x0 - 1, cy + 4, z0, x0 - 1, cy + 4, z1, Blocks.MAGENTA_STAINED_GLASS);
	}

	/** Гастроном «Продукты» с очередью. */
	private static void gastronom(B b, int cx, int cy, int cz) {
		int x0 = cx - 62, x1 = cx - 46, z0 = cz - 26, z1 = cz - 12;
		b.fill(x0, cy - 1, z0, x1, cy - 1, z1, Blocks.SMOOTH_STONE);
		b.walls(x0, cy, z0, x1, cy + 5, z1, Blocks.WHITE_CONCRETE);
		b.walls(x0, cy + 5, z0, x1, cy + 5, z1, Blocks.BLUE_CONCRETE);
		b.fill(x0, cy + 6, z0, x1, cy + 6, z1, Blocks.LIGHT_GRAY_CONCRETE);
		b.fill(x1, cy + 1, z0 + 1, x1, cy + 3, cz - 21, Blocks.GLASS);
		b.fill(x1, cy + 1, cz - 16, x1, cy + 3, z1 - 1, Blocks.GLASS);
		b.air(x1, cy, cz - 19, x1, cy + 2, cz - 18);
		b.fill(x1 + 1, cy + 4, cz - 22, x1 + 1, cy + 6, cz - 15, Blocks.RED_CONCRETE);
		// прилавок и пустые полки
		b.fill(cx - 51, cy, z0 + 2, cx - 51, cy, z1 - 2, Blocks.SMOOTH_STONE_SLAB);
		for (int z = z0 + 1; z < z1; z += 2) {
			b.set(x0 + 1, cy, z, Blocks.BARREL);
			b.set(x0 + 1, cy + 1, z, Blocks.BARREL);
		}
		b.set(cx - 54, cy + 5, cz - 19, Blocks.GLOWSTONE);
		b.set(cx - 58, cy + 5, cz - 19, Blocks.GLOWSTONE);
		for (int x = cx - 45; x <= cx - 22; x++) b.set(x, cy - 1, cz - 19, Blocks.DIRT_PATH);
	}

	/** Ферма с коровами. */
	private static void farm(B b, int cx, int cy, int cz) {
		int x0 = cx - 62, x1 = cx - 46, z0 = cz + 8, z1 = cz + 24;
		for (int x = x0; x <= x1; x++) {
			b.set(x, cy, z0, Blocks.OAK_FENCE);
			b.set(x, cy, z1, Blocks.OAK_FENCE);
		}
		for (int z = z0; z <= z1; z++) {
			b.set(x0, cy, z, Blocks.OAK_FENCE);
			b.set(x1, cy, z, Blocks.OAK_FENCE);
		}
		b.facing(x1, cy, cz + 16, Blocks.OAK_FENCE_GATE, Direction.EAST);
		b.walls(x0 + 1, cy, z0 + 1, x0 + 6, cy + 3, z0 + 5, Blocks.SPRUCE_PLANKS);
		b.air(x0 + 6, cy, z0 + 3, x0 + 6, cy + 1, z0 + 3);
		b.fill(x0 + 1, cy + 4, z0 + 1, x0 + 6, cy + 4, z0 + 5, Blocks.DARK_OAK_PLANKS);
		b.fill(x0 + 2, cy, z0 + 2, x0 + 3, cy + 1, z0 + 2, Blocks.HAY_BLOCK);
		b.fill(cx - 52, cy, z1 - 2, cx - 49, cy, z1 - 2, Blocks.WATER_CAULDRON);
		b.lamp(x1 + 1, cy, cz + 14, 3);
		for (int z = cz - 10; z <= cz + 40; z++) b.set(cx - 44, cy - 1, z, Blocks.DIRT_PATH);
	}

	/** Теплица академика. */
	private static void greenhouse(B b, int cx, int cy, int cz) {
		int x0 = cx - 60, x1 = cx - 48, z0 = cz + 30, z1 = cz + 42;
		b.walls(x0, cy, z0, x1, cy + 4, z1, Blocks.GLASS);
		b.fill(x0, cy + 5, z0, x1, cy + 5, z1, Blocks.GLASS);
		b.air(x1, cy, cz + 36, x1, cy + 1, cz + 36);
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
		for (int x = x0 + 1; x <= x0 + 8; x++) {
			for (int z = z0 + 1; z < z1; z++) {
				if (z == cz + 36) {
					b.set(x, cy - 1, z, Blocks.WATER);
					continue;
				}
				b.set(x, cy - 1, z, farmland);
				Block crop = (x % 3 == 0) ? Blocks.WHEAT : (x % 3 == 1) ? Blocks.CARROTS : Blocks.POTATOES;
				b.set(x, cy, z, crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, 4 + (x + z) % 4));
			}
		}
		b.set(x1 - 1, cy, z0 + 1, Blocks.BREWING_STAND);
		b.set(x1 - 1, cy, z1 - 1, Blocks.CAULDRON);
		b.set(x1 - 2, cy, z1 - 1, Blocks.POTTED_CACTUS);
		// памятник кукурузе
		int mx = cx - 40, mz = cz + 48;
		b.fill(mx - 2, cy, mz - 2, mx + 2, cy + 1, mz + 2, Blocks.POLISHED_GRANITE);
		b.fill(mx - 1, cy + 2, mz - 1, mx + 1, cy + 12, mz + 1, Blocks.YELLOW_CONCRETE);
		for (int y = cy + 3; y <= cy + 11; y += 2) {
			b.set(mx - 1, y, mz - 1, Blocks.GOLD_BLOCK);
			b.set(mx + 1, y, mz + 1, Blocks.GOLD_BLOCK);
		}
		b.set(mx, cy + 13, mz, Blocks.YELLOW_CONCRETE);
		for (int y = cy + 2; y <= cy + 7; y++) {
			b.set(mx - 2, y, mz, Blocks.GREEN_CONCRETE);
			b.set(mx + 2, y, mz, Blocks.GREEN_CONCRETE);
			b.set(mx, y, mz - 2, Blocks.GREEN_CONCRETE);
		}
		b.set(mx - 3, cy + 8, mz, Blocks.GREEN_CONCRETE);
		b.set(mx + 3, cy + 8, mz, Blocks.GREEN_CONCRETE);
		b.set(mx, cy + 8, mz - 3, Blocks.GREEN_CONCRETE);
		b.set(mx, cy + 14, mz, Blocks.HAY_BLOCK);
	}

	/** Южный проспект и статуя «Рабочий и Колхозница» (с гигантскими киркой и мотыгой). */
	private static void southSquare(B b, int cx, int cy, int cz) {
		for (int z = cz + 22; z <= cz + 34; z++) b.fill(cx - 2, cy - 1, z, cx + 2, cy - 1, z, Blocks.STONE_BRICKS);
		for (int x = cx - 12; x <= cx + 12; x++)
			for (int z = cz + 35; z <= cz + 50; z++) b.set(x, cy - 1, z, ((x + z) & 1) == 0 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE);
		b.fill(cx - 6, cy, cz + 38, cx + 6, cy + 2, cz + 44, Blocks.POLISHED_GRANITE);
		int y = cy + 3, z = cz + 41;
		Block steel = Blocks.IRON_BLOCK;
		for (int sx : new int[]{-2, 2}) {
			int x = cx + sx;
			b.fill(x - 1, y, z, x - 1, y + 3, z, steel);
			b.fill(x + 1, y, z, x + 1, y + 3, z, steel);
			b.fill(x - 1, y + 4, z, x + 1, y + 8, z, steel);
			b.fill(x - 1, y + 9, z, x + 1, y + 11, z, steel);
		}
		// руки вверх, в руках инструменты
		b.fill(cx - 4, y + 8, z, cx - 4, y + 13, z, steel);
		b.fill(cx + 4, y + 8, z, cx + 4, y + 13, z, steel);
		b.fill(cx - 4, y + 14, z, cx - 4, y + 18, z, Blocks.DARK_OAK_LOG);
		b.fill(cx - 7, y + 18, z, cx - 1, y + 18, z, Blocks.IRON_BLOCK);
		b.fill(cx + 4, y + 14, z, cx + 4, y + 18, z, Blocks.DARK_OAK_LOG);
		b.fill(cx + 4, y + 18, z, cx + 7, y + 18, z, Blocks.IRON_BLOCK);
		b.set(cx + 7, y + 17, z, Blocks.IRON_BLOCK);
		b.fill(cx - 3, y + 7, z, cx + 3, y + 7, z, Blocks.RED_CONCRETE);
		for (int[] p : new int[][]{{-11, 36}, {11, 36}, {-11, 49}, {11, 49}}) b.lamp(cx + p[0], cy, cz + p[1], 3);
	}

	/** Стела «СЛАВА ТРУДУ». */
	private static void stela(B b, int cx, int cy, int cz) {
		b.fill(cx - 23, cy, cz + 52, cx + 22, cy + 7, cz + 53, Blocks.RED_CONCRETE);
		b.fill(cx - 23, cy + 8, cz + 52, cx + 22, cy + 8, cz + 53, Blocks.GOLD_BLOCK);
		text(b, "СЛАВА ТРУДУ", cx + 20, cy + 6, cz + 52, Blocks.GOLD_BLOCK);
	}

	/** Улица: дорога на восток, автомат с газировкой, телефонная будка, лавочка. */
	private static void street(B b, int cx, int cy, int cz) {
		for (int x = cx + 22; x <= cx + 62; x++) b.fill(x, cy - 1, cz + 13, x, cy - 1, cz + 15, Blocks.STONE_BRICKS);
		for (int x = cx + 38; x <= cx + 62; x += 8) b.lamp(x, cy, cz + 12, 3);
		// автомат газированной воды
		b.set(cx + 19, cy, cz - 12, Blocks.RED_CONCRETE);
		b.set(cx + 19, cy + 1, cz - 12, Blocks.LIGHT_BLUE_STAINED_GLASS);
		b.set(cx + 19, cy + 2, cz - 12, Blocks.RED_CONCRETE);
		button(b, cx + 18, cy + 1, cz - 12);
		// телефонная будка
		b.fill(cx + 17, cy, cz - 8, cx + 19, cy + 2, cz - 6, Blocks.RED_CONCRETE);
		b.air(cx + 18, cy, cz - 7, cx + 18, cy + 2, cz - 7);
		b.air(cx + 17, cy, cz - 7, cx + 17, cy + 1, cz - 7);
		b.set(cx + 18, cy + 2, cz - 8, Blocks.GLASS);
		b.set(cx + 18, cy + 2, cz - 6, Blocks.GLASS);
		b.fill(cx + 17, cy + 3, cz - 8, cx + 19, cy + 3, cz - 6, Blocks.RED_CONCRETE);
		button(b, cx + 18, cy + 1, cz - 7);
		// лавочка у подъезда
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
		b.set(cx + 22, cy, cz - 5, stair);
		b.set(cx + 22, cy, cz - 4, stair);
	}

	private static void button(B b, int x, int y, int z) {
		b.l.setBlock(new BlockPos(x, y, z), Blocks.STONE_BUTTON.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 3);
	}
}
