package cn.scarefree.openlink2.platform.forge;

//? forge {

import cn.scarefree.openlink2.logic.EventCallback;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ForgeEventSubscriber {
	@SubscribeEvent
	//? <= 1.18.2 {
	/*public static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
	*///?} > 1.18.2 {
	public static void onScreenInit(ScreenEvent.Init.Post event) {
	//?}
		EventCallback.onScreenInit(event.getScreen());
	}
}
//?}
