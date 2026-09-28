package cn.scarefree.openlink2.logic;

import cn.scarefree.openlink2.mixin.IScreenAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public class EventCallback {
	public static void onScreenInit(Screen screen) {
		if(screen instanceof TitleScreen) {
//			IScreenAccessor accessor = (IScreenAccessor) screen;
//			TODO: add a button
		}
	}
}
