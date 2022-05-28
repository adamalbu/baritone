/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for automatically testing Baritone's pathing algorithm by automatically creating a world with a specific
 * seed, setting a specified goal, and only allowing a certain amount of ticks to pass before the pathing test is
 * considered a failure. In order to test locally, docker may be used, or through an IDE: Create a run config which runs
 * in a separate directory from the primary one (./run), and set the enrivonmental variable {@code BARITONE_AUTO_TEST}
 * to {@code true}.
 *
 * @author leijurv, Brady
 */
public class BaritoneAutoTest implements AbstractGameEventListener, Helper {

    public static final BaritoneAutoTest INSTANCE = new BaritoneAutoTest();

    public static final boolean ENABLE_AUTO_TEST = "true".equals(System.getenv("BARITONE_AUTO_TEST"));
    private static final long TEST_SEED = -928872506371745L;
    private static final BlockPos STARTING_POSITION = new BlockPos(0, 63, 0);
    private static final Goal GOAL = new GoalBlock(69, 69, 420);
    private static final Path RESULT_PATH = mc.gameDir.toPath().resolve("baritone").resolve("autotest.txt");
    private static final int N = 100;
    private static final int TIMEOUT = 5*20;
    private static final int DELAY = 1*20;

    private final List<Integer> npss = new ArrayList<>();
    private int n = -10; // recording starts at 0
    private boolean pathing = false;
    private int timeout = 0;
    private int delay = 0;

    /**
     * Called by AStarPathFinder when pathing completes successfully
     */
    public void recordNodesPerSecond(int nps) {
        n++;
        if (n > 0) npss.add(nps);
        pathing = false;
    }

    /**
     * Called every tick once the world is set up
     */
    private void run(IPlayerContext ctx, int time) {
        if (time == 0) System.out.println("Will not record first " + -n + " iterations");
        if (timeout > 0 && pathing) {
            timeout--;
            System.out.println("Waiting for pathing to complete " + timeout);
            return;
        }
        if (n >= N) {
            System.out.println("Automatic test done, dumping results to stdout and " + RESULT_PATH);
            dumpResults();
            System.out.println("Shutting down Minecraft");
            mc.shutdown();
            return;
        }
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone.getCustomGoalProcess().isActive()) {
            System.out.println("Canceling custom goal process");
            baritone.getCustomGoalProcess().onLostControl();
        }
        if (!ctx.playerFeet().equals(STARTING_POSITION)) {
            System.out.println("Teleporting to start");
            ctx.player().sendChatMessage(String.format(
                    "/tp @s %s %s %s",
                    STARTING_POSITION.getX(),
                    STARTING_POSITION.getY(),
                    STARTING_POSITION.getZ()
            ));
        }
        if (delay > 0) {
            delay--;
            System.out.println("Waiting " + delay);
            return;
        }
        if (!baritone.getCustomGoalProcess().isActive()) {
            if (n == 0) System.out.println("Starting recording");
            System.out.println("Setting goal, " + (N - n) + " iterations remaining");
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(GOAL);
            pathing = true;
            timeout = TIMEOUT;
            delay = DELAY;
            return;
        }
    }

    private void dumpResults() {
        System.out.println("Nodes per second were:");
        try (BufferedWriter out = Files.newBufferedWriter(RESULT_PATH)) {
            for (int nps : npss) { // BOXING
                System.out.println("    " + nps);
                out.write(nps + "\n");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Called right after the {@link GameSettings} object is created in the {@link Minecraft} instance.
     */
    public void onPreInit() {
        if (!BaritoneAutoTest.ENABLE_AUTO_TEST) {
            return;
        }
        System.out.println("Optimizing Game Settings");

        GameSettings s = mc.gameSettings;
        s.limitFramerate = 20;
        s.mipmapLevels = 0;
        s.particleSetting = 2;
        s.overrideWidth = 128;
        s.overrideHeight = 128;
        s.heldItemTooltips = false;
        s.entityShadows = false;
        s.chatScale = 0.0F;
        s.ambientOcclusion = 0;
        s.clouds = 0;
        s.fancyGraphics = false;
        s.tutorialStep = TutorialSteps.NONE;
        s.hideGUI = true;
        s.fovSetting = 30.0F;
    }

    @Override
    public void onTick(TickEvent event) {
        IPlayerContext ctx = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext();
        // If we're on the main menu then create the test world and launch the integrated server
        if (mc.currentScreen instanceof GuiMainMenu) {
            System.out.println("Beginning Baritone automatic test routine");
            mc.displayGuiScreen(null);
            WorldSettings worldsettings = new WorldSettings(TEST_SEED, GameType.SURVIVAL, true, false, WorldType.DEFAULT);
            worldsettings.enableCommands();
            mc.launchIntegratedServer("BaritoneAutoTest", "BaritoneAutoTest", worldsettings);
        }

        // If the integrated server is running, set the difficulty to peaceful
        if (mc.getIntegratedServer() != null) {
            mc.getIntegratedServer().setDifficultyForAllWorlds(EnumDifficulty.PEACEFUL);

            for (final WorldServer world : mc.getIntegratedServer().worlds) {
                // If the world has initialized, set the spawn point to our defined starting position
                if (world != null) {
                    world.setSpawnPoint(STARTING_POSITION);
                    world.getGameRules().setOrCreateGameRule("spawnRadius", "0");
                }
            }
        }

        if (event.getType() == TickEvent.Type.IN) { // If we're in-game

            // Force the integrated server to share the world to LAN so that
            // the ingame pause menu gui doesn't actually pause our game
            if (mc.isSingleplayer() && !mc.getIntegratedServer().getPublic()) {
                mc.getIntegratedServer().shareToLAN(GameType.SURVIVAL, true);
            }

            // For the first 200 ticks, wait for the world to generate
            if (event.getCount() < 200) {
                System.out.println("Waiting for world to generate... " + event.getCount());
                return;
            }

            run(ctx, event.getCount() - 200);
        }
    }

    private BaritoneAutoTest() {}
}
