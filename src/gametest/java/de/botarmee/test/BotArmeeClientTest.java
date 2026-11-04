package de.botarmee.test;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import de.botarmee.ArmeeScreen;
import de.botarmee.BattleConfig;
import de.botarmee.ClientState;

/** Automatischer Test: Arena bauen, Menü-Screenshots, Schlacht starten und Screenshots vom Kampf. */
public class BotArmeeClientTest implements FabricClientGameTest {
	private static void log(String s) {
		System.out.println("BOTARMEE-TEST " + s);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			log("welt geladen");
			sp.getConnection().waitForChunksRender();
			log("chunks gerendert");
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");

			ctx.runOnClient(mc -> {
				BattleConfig c = ClientState.config();
				c.arena = 1;
				c.arenaSize = 0;
				c.winner = 0;
				c.teams.get(0).count = 45;
				c.teams.get(1).count = 45;
				ClientState.save();
				ClientState.send("arena");
			});
			ctx.waitTicks(140);
			int[] p = sp.getServer().computeOnServer(server -> {
				ServerPlayer pl = server.getPlayerList().getPlayers().get(0);
				return new int[] {pl.getBlockX(), pl.getBlockY(), pl.getBlockZ()};
			});
			sp.getServer().runCommand("tp @a " + p[0] + " " + (p[1] + 16) + " " + (p[2] + 34) + " 180 24");
			sp.getServer().runOnServer(server -> {
				ServerPlayer pl = server.getPlayerList().getPlayers().get(0);
				pl.getAbilities().flying = true;
				pl.onUpdateAbilities();
			});
			sp.getConnection().waitForChunksRender();

			String[] names = {"menu_teams", "menu_ausruestung", "menu_ablauf", "menu_arena", "menu_extras"};
			for (int t = 0; t < names.length; t++) {
				final int tab = t;
				ctx.runOnClient(mc -> ArmeeScreen.select(tab, 0, 1));
				ctx.setScreen(ArmeeScreen::new);
				ctx.waitTicks(5);
				log("shot");
			ctx.takeScreenshot(names[t]);
			}
			ctx.runOnClient(mc -> ArmeeScreen.select(1, 1, 4));
			ctx.setScreen(ArmeeScreen::new);
			ctx.waitTicks(5);
			log("shot");
			ctx.takeScreenshot("menu_ausruestung_waffe");
			ctx.setScreen(() -> null);

			ctx.runOnClient(mc -> ClientState.send("setup"));
			ctx.waitTicks(40);
			log("shot");
			ctx.takeScreenshot("aufgestellt");
			ctx.runOnClient(mc -> ClientState.send("start"));
			ctx.waitTicks(70);
			log("shot");
			ctx.takeScreenshot("kampf_start");
			ctx.waitTicks(160);
			log("shot");
			ctx.takeScreenshot("kampf_mitte");
			sp.getServer().runCommand("tp @a " + p[0] + " " + (p[1] + 4) + " " + (p[2] + 12) + " 180 10");
			ctx.waitTicks(60);
			log("shot");
			ctx.takeScreenshot("kampf_nah");
			ctx.waitFor(mc -> ClientState.state.equals("OVER"), 1800);
			ctx.waitTicks(20);
			log("shot");
			ctx.takeScreenshot("ende");
			ctx.runOnClient(mc -> {
				StringBuilder sb = new StringBuilder("BOTARMEE-RESULT state=" + ClientState.state);
				for (ClientState.TeamStatus t : ClientState.teams) sb.append(" | ").append(t.name()).append(' ').append(t.alive()).append('/').append(t.start());
				System.out.println(sb);
			});

			// 2. Schlacht: das schwächere Team (Wikinger) soll "super knapp" gewinnen
			ctx.runOnClient(mc -> {
				BattleConfig c = ClientState.config();
				c.winner = 1;
				c.closeness = 85;
				ClientState.send("start");
			});
			ctx.waitTicks(80);
			ctx.waitFor(mc -> ClientState.state.equals("OVER"), 3000);
			ctx.takeScreenshot("ende_rigged");
			ctx.runOnClient(mc -> {
				StringBuilder sb = new StringBuilder("BOTARMEE-RESULT2 state=" + ClientState.state);
				for (ClientState.TeamStatus t : ClientState.teams) sb.append(" | ").append(t.name()).append(' ').append(t.alive()).append('/').append(t.start());
				System.out.println(sb);
			});
		}
	}
}
