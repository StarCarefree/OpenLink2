package cn.scarefree.openlink2.platform.forge;

//? forge {

import cn.scarefree.openlink2.OpenLink2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = OpenLink2.MOD_ID, value = Dist.CLIENT)
public class ForgeClientEventSubscriber {

	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		OpenLink2.onInitializeClient();
	}
}
//?}
