package cn.scarefree.openlink2.platform.forge;

//? forge {

import cn.scarefree.openlink2.OpenLink2;
import net.minecraftforge.fml.common.Mod;

@Mod(OpenLink2.MOD_ID)
public class ForgeEntrypoint {

	public ForgeEntrypoint() {
		OpenLink2.onInitialize();
	}
}
//?}
