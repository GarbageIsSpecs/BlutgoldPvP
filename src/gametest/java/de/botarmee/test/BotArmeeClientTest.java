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
			sp.getServer().runCommand("gamerule doDaylightCycle false");

			sp.getServer().runOnServer(server -> {
				ServerPlayer pl = server.getPlayerList().getPlayers().get(0);
				pl.getAbilities().flying = true;
				pl.onUpdateAbilities();
			});

			// ---- Vorschaubilder aller Arenen (für das Menü)
			int[] home = sp.getServer().computeOnServer(server -> {
				ServerPlayer pl = server.getPlayerList().getPlayers().get(0);
				return new int[] {pl.getBlockX(), pl.getBlockY(), pl.getBlockZ()};
			});
			for (int a = 0; a < 6; a++) {
				final int arena = a;
				int ax = home[0] - 2000 - a * 400, az = home[2];
				sp.getServer().runCommand("tp @a " + ax + " " + home[1] + " " + az + " 0 0");
				sp.getConnection().waitForChunksRender();
				ctx.runOnClient(mc -> {
					BattleConfig c = ClientState.config();
					c.arena = arena;
					c.arenaSize = 1;
					ClientState.send("arena");
				});
				ctx.waitTicks(130);
				ctx.runOnClient(mc -> {
					BattleConfig c = ClientState.config();
					c.useArena = true;
					c.teams.get(0).count = 40;
					c.teams.get(1).count = 40;
					c.names = false;
					ClientState.send("setup");
				});
				ctx.waitTicks(40);
				sp.getServer().runCommand("tp @a " + ax + " " + (home[1] + 46) + " " + (az + 66) + " 180 36");
				sp.getConnection().waitForChunksRender();
				ctx.waitTicks(30);
				ctx.runOnClient(mc -> {
					if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				});
				ctx.waitTicks(3);
				log("shot");
				ctx.takeScreenshot("preview_arena_" + arena);
				ctx.runOnClient(mc -> {
					if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
					ClientState.config().names = true;
					ClientState.send("clear");
				});
			}
			sp.getServer().runCommand("tp @a " + home[0] + " " + home[1] + " " + home[2] + " 0 0");
			sp.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> ClientState.send("forgetArena"));

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

			// 3. Kolosseum mit 4 Teams
			ctx.runOnClient(mc -> {
				BattleConfig c = ClientState.config();
				ClientState.send("clear");
				c.winner = -1;
				c.arena = 4;
				c.arenaSize = 0;
				c.teams.get(0).count = 20;
				c.teams.get(1).count = 20;
				BattleConfig.Team t3 = new BattleConfig.Team("Mongolen", 2);
				t3.count = 20;
				t3.archers = 50;
				t3.sequence.add(new BattleConfig.Phase(BattleConfig.ACT_ATTACK, 0));
				BattleConfig.Team t4 = new BattleConfig.Team("Samurai", 3);
				t4.count = 20;
				t4.kit[1] = new BattleConfig.Piece();
				t4.kit[1].item = "minecraft:netherite_chestplate";
				t4.kit[4].item = "minecraft:diamond_sword";
				t4.sequence.add(new BattleConfig.Phase(BattleConfig.ACT_FLANK, 0));
				c.teams.add(t3);
				c.teams.add(t4);
			});
			sp.getServer().runCommand("tp @a " + (p[0] + 300) + " " + (p[1] + 1) + " " + p[2]);
			sp.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> ClientState.send("arena"));
			ctx.waitTicks(140);
			ctx.runOnClient(mc -> ClientState.send("setup"));
			ctx.waitTicks(30);
			sp.getServer().runCommand("tp @a " + (p[0] + 300) + " " + (p[1] + 22) + " " + (p[2] + 38) + " 180 32");
			ctx.waitTicks(30);
			log("shot");
			ctx.takeScreenshot("kolosseum_aufgestellt");
			ctx.runOnClient(mc -> ClientState.send("start"));
			ctx.waitTicks(200);
			ctx.takeScreenshot("kolosseum_kampf");
			ctx.waitFor(mc -> ClientState.state.equals("OVER"), 3000);
			ctx.runOnClient(mc -> {
				StringBuilder sb = new StringBuilder("BOTARMEE-RESULT3 state=" + ClientState.state);
				for (ClientState.TeamStatus t : ClientState.teams) sb.append(" | ").append(t.name()).append(' ').append(t.alive()).append('/').append(t.start());
				System.out.println(sb);
			});

			// 4. Leistungstest: 700 Bots im Sparmodus
			ctx.runOnClient(mc -> {
				BattleConfig c = ClientState.config();
				ClientState.send("clear");
				while (c.teams.size() > 2) c.teams.remove(c.teams.size() - 1);
				c.teams.get(0).count = 350;
				c.teams.get(1).count = 350;
				c.winner = 0;
				c.closeness = 70;
				c.useArena = false;
			});
			sp.getServer().runCommand("tp @a " + (p[0] + 700) + " " + (p[1] + 1) + " " + p[2] + " 90 0");
			sp.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> ClientState.send("setup"));
			ctx.waitTicks(40);
			sp.getServer().runCommand("tp @a " + (p[0] + 700) + " " + (p[1] + 30) + " " + (p[2] + 2) + " 90 50");
			ctx.waitTicks(20);
			log("shot");
			ctx.takeScreenshot("riesig_aufgestellt");
			ctx.runOnClient(mc -> ClientState.send("start"));
			ctx.waitTicks(260);
			ctx.takeScreenshot("riesig_kampf");
			sp.getServer().runOnServer(server -> System.out.println("BOTARMEE-PERF ms/tick=" + server.getAverageTickTimeNanos() / 1.0e6));
			ctx.runOnClient(mc -> System.out.println("BOTARMEE-PERF fps=" + mc.getFps()));
			for (int i = 0; i < 30 && !ctx.computeOnClient(mc -> ClientState.state.equals("OVER")); i++) {
				ctx.waitTicks(400);
				ctx.runOnClient(mc -> {
					StringBuilder sb = new StringBuilder("BOTARMEE-PROGRESS fps=" + mc.getFps());
					for (ClientState.TeamStatus t : ClientState.teams) sb.append(" | ").append(t.name()).append(' ').append(t.alive());
					System.out.println(sb);
				});
				if (i == 2) ctx.takeScreenshot("riesig_spaeter");
			}
			ctx.runOnClient(mc -> {
				StringBuilder sb = new StringBuilder("BOTARMEE-RESULT4 state=" + ClientState.state);
				for (ClientState.TeamStatus t : ClientState.teams) sb.append(" | ").append(t.name()).append(' ').append(t.alive()).append('/').append(t.start());
				System.out.println(sb);
			});
		}
	}
}
