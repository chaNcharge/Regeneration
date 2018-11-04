package me.fril.regeneration.network;

import me.fril.regeneration.RegenerationMod;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Created by Sub
 * on 16/09/2018.
 */
public class NetworkHandler {
	
	public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(RegenerationMod.MODID);
	
	public static void init() {
		INSTANCE.registerMessage(MessageUpdateRegen.Handler.class, MessageUpdateRegen.class, 1, Side.CLIENT);
		INSTANCE.registerMessage(MessageRegenChoice.Handler.class, MessageRegenChoice.class, 2, Side.SERVER);
		INSTANCE.registerMessage(MessageRegenerationStyle.Handler.class, MessageRegenerationStyle.class, 3, Side.SERVER);
	}
	
}