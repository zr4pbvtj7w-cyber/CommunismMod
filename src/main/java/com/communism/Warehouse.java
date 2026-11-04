package com.communism;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Склад Народа: сюда попадает всё конфискованное. */
public final class Warehouse {
	public static void store(ItemStack st) {
		if (st.isEmpty() || CommunismMod.server == null || !City.built()) return;
		ServerLevel l = CommunismMod.server.overworld();
		for (int x = -31; x <= -19; x += 2) {
			BlockPos pos = City.pos(x, 0, 32);
			BlockEntity be = l.getBlockEntity(pos);
			if (!(be instanceof Container c)) continue;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack in = c.getItem(i);
				if (in.isEmpty()) {
					c.setItem(i, st);
					c.setChanged();
					return;
				}
				if (ItemStack.isSameItemSameComponents(in, st) && in.getCount() < in.getMaxStackSize()) {
					int move = Math.min(st.getCount(), in.getMaxStackSize() - in.getCount());
					in.grow(move);
					st.shrink(move);
					c.setChanged();
					if (st.isEmpty()) return;
				}
			}
		}
		// склад переполнен — кладём на крыльцо
		Msg.drop(l, City.x() - 24.5, City.y() + 0.5, City.z() + 22.5, st);
	}
}
