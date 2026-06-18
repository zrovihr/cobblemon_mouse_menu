package com.cobblemonmousemenu;

import com.cobblemonmousemenu.net.TrainerTeamNetworking;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CobblemonMouseMenu implements ModInitializer {
	public static final String MOD_ID = "cobblemon-mousemenu";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Loading Cobblemon Mouse Menu");
		// Server/common side of the trainer-team feature (also runs on the singleplayer integrated server).
		TrainerTeamNetworking.registerCommon();
	}
}
