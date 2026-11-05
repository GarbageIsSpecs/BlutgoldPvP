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

	static double clickScale = -1;

	private static void clickGui(ClientGameTestContext ctx, int gx, int gy) {
		if (clickScale < 0) {
			// herausfinden, ob setCursorPos GUI- oder Fenster-Koordinaten will (am Namensfeld)
			for (double sc : new double[] {1, ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale())}) {
				ctx.getInput().setCursorPos(gx * sc, gy * sc);
				ctx.waitTick();
				ctx.getInput().pressMouse(0);
				ctx.waitTicks(2);
				boolean ok = ctx.computeOnClient(mc -> mc.gui.screen() != null && mc.gui.screen().getFocused() != null);
				System.out.println("BOTARMEE-NAMEDBG try scale=" + sc + " focused=" + ok);
				if (ok) {
					clickScale = sc;
					return;
				}
			}
			clickScale = 1;
			return;
		}
		ctx.getInput().setCursorPos(gx * clickScale, gy * clickScale);
		ctx.waitTick();
		ctx.getInput().pressMouse(0);
		ctx.waitTicks(2);
	}

	/** Testet: Teamnamen ändern (beide Teams), Held duckt/dreht sich mit, Mitspielen, Schilde. */
	private static void heldUndNamen(ClientGameTestContext ctx, TestSingleplayerContext sp, int[] home) {
		// --- Namen ändern, zweimal hintereinander (das war der Bug)
		ctx.runOnClient(mc -> ArmeeScreen.select(0, 0, 1));
		ctx.setScreen(ArmeeScreen::new);
		ctx.waitTicks(3);
		clickGui(ctx, ArmeeScreen.testNameBox[0], ArmeeScreen.testNameBox[1]);
		ctx.runOnClient(mc -> System.out.println("BOTARMEE-NAMEDBG scale=" + mc.getWindow().getGuiScale() + " box=" + ArmeeScreen.testNameBox[0] + "," + ArmeeScreen.testNameBox[1]
				+ " win=" + mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " gui=" + mc.getWindow().getGuiScaledWidth()
				+ " focused=" + (mc.gui.screen() == null ? "none" : String.valueOf(mc.gui.screen().getFocused()))));
		ctx.getInput().typeChars("A");
		ctx.waitTicks(2);
		clickGui(ctx, ArmeeScreen.testTeamRows[1][0], ArmeeScreen.testTeamRows[1][1]);
		ctx.waitTicks(2);
		clickGui(ctx, ArmeeScreen.testNameBox[0], ArmeeScreen.testNameBox[1]);
		ctx.getInput().typeChars("B");
		ctx.waitTicks(2);
		clickGui(ctx, ArmeeScreen.testTeamRows[0][0], ArmeeScreen.testTeamRows[0][1]);
		ctx.waitTicks(2);
		clickGui(ctx, ArmeeScreen.testNameBox[0], ArmeeScreen.testNameBox[1]);
		ctx.getInput().typeChars("C");
		ctx.waitTicks(2);
		log("shot");
		ctx.takeScreenshot("namen_test");
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> {
			BattleConfig c = ClientState.config();
			System.out.println("BOTARMEE-NAMES " + c.teams.get(0).name + " / " + c.teams.get(1).name);
			c.teams.get(0).name = "Römer";
			c.teams.get(1).name = "Wikinger";
		});

		// --- Held + Mitspielen
		int hx = home[0] + 1500, hz = home[2];
		sp.getServer().runCommand("tp @a " + hx + " " + home[1] + " " + hz + " 0 0");
		sp.getConnection().waitForChunksRender();
		ctx.runOnClient(mc -> {
			BattleConfig c = ClientState.config();
			c.useArena = false;
			c.winner = -1;
			c.distance = 24;
			c.teams.get(0).count = 12;
			c.teams.get(0).hero = true;
			c.teams.get(0).archers = 0;
			c.teams.get(1).count = 12;
			c.teams.get(1).archers = 0;
			c.teams.get(1).kit[5].item = "minecraft:shield";
			c.playerTeam = 1;
			c.playerTeleport = true;
			c.playerKit = true;
			ClientState.send("setup");
		});
		ctx.waitTicks(40);
		sp.getServer().runCommand("gamemode survival @a");
		// zum Helden schauen
		ctx.waitTicks(5);
		lookAtHero(ctx, sp);
		ctx.waitTicks(10);
		log("shot");
		ctx.takeScreenshot("held_anschauen");
		// 2x ducken
		for (int i = 0; i < 2; i++) {
			ctx.getInput().holdKeyFor(o -> o.keyShift, 4);
			ctx.waitTicks(4);
		}
		boolean crouched = false;
		for (int i = 0; i < 40 && !crouched; i++) {
			ctx.waitTick();
			crouched = sp.getServer().computeOnServer(server -> {
				var h = de.botarmee.Battle.firstHero();
				return h != null && h.isCrouching();
			});
		}
		ctx.waitTicks(1);
		ctx.takeScreenshot("held_duckt");
		System.out.println("BOTARMEE-HERO crouched=" + crouched);
		ctx.waitTicks(40);
		// einmal drehen
		lookAtHero(ctx, sp);
		ctx.waitTicks(5);
		float baseYaw = ctx.computeOnClient(mc -> mc.player.getYRot());
		float pitch = ctx.computeOnClient(mc -> mc.player.getXRot());
		for (int i = 1; i <= 12; i++) {
			ctx.getInput().lookAt(baseYaw + i * 30, pitch);
			ctx.waitTick();
		}
		float startYaw = sp.getServer().computeOnServer(server -> de.botarmee.Battle.firstHero().getYRot());
		float maxTurn = 0;
		for (int i = 0; i < 40; i++) {
			ctx.waitTick();
			float y = sp.getServer().computeOnServer(server -> de.botarmee.Battle.firstHero().getYRot());
			maxTurn = Math.max(maxTurn, Math.abs(y - startYaw));
			if (i == 12) ctx.takeScreenshot("held_dreht");
		}
		System.out.println("BOTARMEE-HERO spin=" + maxTurn);

		// Kampf mit dir im Team Wikinger
		ctx.runOnClient(mc -> ClientState.send("start"));
		float minHp = 20;
		for (int i = 0; i < 60; i++) {
			ctx.waitTicks(20);
			minHp = Math.min(minHp, ctx.computeOnClient(mc -> mc.player == null ? 20f : mc.player.getHealth()));
			if (i == 8) ctx.takeScreenshot("mitkaempfen");
			if (ctx.computeOnClient(mc -> ClientState.state.equals("OVER"))) break;
		}
		sp.getServer().runOnServer(server -> System.out.println("BOTARMEE-PLAYERDBG targeted=" + de.botarmee.Battle.statPlayerTargeted + " hits=" + de.botarmee.Battle.statPlayerHits
				+ " mode=" + server.getPlayerList().getPlayers().get(0).gameMode() + " invul=" + server.getPlayerList().getPlayers().get(0).isInvulnerable()));
		System.out.println("BOTARMEE-PLAYER minHp=" + minHp + " state=" + ctx.computeOnClient(mc -> ClientState.state));
		sp.getServer().runOnServer(server -> System.out.println("BOTARMEE-STATS blocks=" + de.botarmee.Battle.statBlocks + " heroBlocks=" + de.botarmee.Battle.statHeroBlocks
				+ " missedBlocks=" + de.botarmee.Battle.statMissedBlocks + " misses=" + de.botarmee.Battle.statMisses));
		sp.getServer().runCommand("gamemode creative @a");
		sp.getServer().runCommand("effect give @a instant_health 1 10 true");
		ctx.runOnClient(mc -> {
			BattleConfig c = ClientState.config();
			c.playerTeam = -1;
			c.teams.get(0).hero = false;
			c.distance = 40;
			ClientState.send("clear");
		});
		ctx.waitTicks(5);
		sp.getServer().runCommand("tp @a " + home[0] + " " + home[1] + " " + home[2] + " 0 0");
		sp.getServer().runOnServer(server -> {
			ServerPlayer pl = server.getPlayerList().getPlayers().get(0);
			pl.getAbilities().flying = true;
			pl.onUpdateAbilities();
		});
		sp.getConnection().waitForChunksRender();
	}

	private static void waitOver(ClientGameTestContext ctx, TestSingleplayerContext sp, int maxTicks, String label) {
		for (int t = 0; t < maxTicks; t += 200) {
			if (ctx.computeOnClient(mc -> ClientState.state.equals("OVER"))) return;
			ctx.waitTicks(200);
		}
		System.out.println("BOTARMEE-TIMEOUT " + label);
		sp.getServer().runOnServer(server -> System.out.println("BOTARMEE-DEBUG " + de.botarmee.Battle.debug().replace("\n", "\nBOTARMEE-DEBUG ")));
	}

	private static void lookAtHero(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		double[] h = sp.getServer().computeOnServer(server -> {
			var hero = de.botarmee.Battle.firstHero();
			return hero == null ? null : new double[] {hero.getX(), hero.getEyeY(), hero.getZ()};
		});
		if (h == null) {
			System.out.println("BOTARMEE-HERO missing");
			return;
		}
		ctx.runOnClient(mc -> {
			double dx = h[0] - mc.player.getX(), dy = h[1] - mc.player.getEyeY(), dz = h[2] - mc.player.getZ();
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
		});
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
			sp.getServer().runCommand("difficulty normal");

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
			boolean previews = System.getenv("BOTARMEE_PREVIEW_ONLY") != null;
			for (int a = 0; a < (previews ? 6 : 0); a++) {
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
				sp.getServer().runCommand("tp @a " + ax + " " + (home[1] + (arena == 4 ? 34 : 30)) + " " + (az + (arena == 4 ? 46 : 40)) + " 180 " + (arena == 4 ? 44 : 42));
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
			if (previews) return;

			heldUndNamen(ctx, sp, home);

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
			waitOver(ctx, sp, 1800, "B1");
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
			waitOver(ctx, sp, 3000, "B23");
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
			waitOver(ctx, sp, 3000, "B23");
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
			ctx.runOnClient(mc -> ArmeeScreen.select(0, 0, 1));
			ctx.setScreen(ArmeeScreen::new);
			ctx.waitTicks(5);
			ctx.takeScreenshot("menu_warnung");
			ctx.runOnClient(mc -> ArmeeScreen.select(3, 0, 1));
			ctx.setScreen(ArmeeScreen::new);
			ctx.waitTicks(5);
			ctx.takeScreenshot("menu_arena_vorschau");
			ctx.setScreen(() -> null);
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
				if (i == 4 || i == 8) sp.getServer().runOnServer(server -> System.out.println("BOTARMEE-DEBUG " + de.botarmee.Battle.debug().replace("\n", "\nBOTARMEE-DEBUG ")));
			}
			ctx.runOnClient(mc -> {
				StringBuilder sb = new StringBuilder("BOTARMEE-RESULT4 state=" + ClientState.state);
				for (ClientState.TeamStatus t : ClientState.teams) sb.append(" | ").append(t.name()).append(' ').append(t.alive()).append('/').append(t.start());
				System.out.println(sb);
			});
		}
	}
}
