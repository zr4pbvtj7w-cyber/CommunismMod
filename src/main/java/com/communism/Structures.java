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
			l.setBlock(new BlockPos(x, y, z), s, 2);
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
	public static void buildPlaza(ServerLevel l, int cx, int cy, int cz) {
		B b = new B(l);
		int R = 38;
		// --- выравнивание территории
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = cx - R; x <= cx + R; x++) {
			for (int z = cz - R; z <= cz + R; z++) {
				for (int y = cy - 6; y <= cy - 2; y++) b.raw(x, y, z, dirt);
				b.raw(x, cy - 1, z, grass);
				for (int y = cy; y <= cy + 45; y++) b.raw(x, y, z, air);
			}
		}

		// --- брусчатка
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
		// дорожки к постройкам
		for (int x = cx - 23; x <= cx - 22; x++) b.set(x, cy - 1, cz, Blocks.DIRT_PATH);
		for (int x = cx + 22; x <= cx + 23; x++) b.set(x, cy - 1, cz, Blocks.STONE_BRICKS);
		for (int z = cz + 22; z <= cz + 24; z++) {
			b.set(cx - 24, cy - 1, z, Blocks.STONE_BRICKS);
			b.set(cx + 14, cy - 1, z, Blocks.STONE_BRICKS);
		}
		// фонари
		int[][] lamps = {{-19, -19}, {19, -19}, {-19, 19}, {19, 19}, {0, 19}, {-19, 0}, {19, 0}, {-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
		for (int[] p : lamps) b.lamp(cx + p[0], cy, cz + p[1], 3);

		kremlin(b, cx, cy, cz);
		mausoleum(b, cx, cy, cz);
		statue(b, cx, cy, cz);
		khrushchevka(b, cx + 24, cy, cz - 12);
		kolkhoz(b, cx, cy, cz);
		warehouse(b, cx, cy, cz);
		factory(b, cx, cy, cz);
		kosmodrom(b, cx, cy, cz);
		sibir(b, cx, cy, cz);
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

	/** Отдельная постройка по имени (для админ-команды). */
	public static boolean buildNamed(ServerLevel l, String name, BlockPos p) {
		B b = new B(l);
		int x = p.getX(), y = p.getY(), z = p.getZ();
		switch (name) {
			case "hrushevka" -> khrushchevka(b, x, y, z);
			case "kvartira" -> miniHouse(l, p);
			case "statuya" -> statue(b, x, y, z - 11);
			case "raketa" -> kosmodrom(b, x - 32, y, z + 33);
			case "sibir" -> sibir(b, x - SIBIR_DX, y, z - SIBIR_DZ);
			case "mavzoley" -> mausoleum(b, x + 11, y, z + 20);
			case "zavod" -> factory(b, x - 15, y, z - 29);
			case "kolhoz" -> kolkhoz(b, x + 30, y, z);
			case "sklad" -> warehouse(b, x + 25, y, z - 28);
			default -> {
				return false;
			}
		}
		return true;
	}

	public static final String[] NAMES = {"hrushevka", "kvartira", "statuya", "raketa", "sibir", "mavzoley", "zavod", "kolhoz", "sklad"};
}
