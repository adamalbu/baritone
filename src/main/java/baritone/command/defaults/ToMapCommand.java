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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.cache.ICachedRegion;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.cache.CachedWorld;
import baritone.cache.WorldScanner;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class ToMapCommand extends Command {

    private ExecutorService executor;

    public ToMapCommand(IBaritone baritone) {
        super(baritone, "tomap");
        executor = Executors.newFixedThreadPool(5);
    }


    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        WorldScanner.INSTANCE.repack(ctx);
        ctx.worldData().getCachedWorld().save();
        //todo: make options for rendering
        // all cached regions,(use executor for this)
        // a specific one(region -1 3),
        // a specific region at a given blockpos(pos 1337420 322),
        // default to the region the player is standing in
        // use the args parameter for that
        executor.execute(saveToImage((int) Math.floor(mc.player.posX / 512), (int) Math.floor(mc.player.posZ / 512)));

    }

    private Runnable saveToImage(int rX, int rZ) {
        return new Runnable() {
            @Override
            public void run() {
                CachedWorld cw = (CachedWorld) ctx.worldData().getCachedWorld(); // is this cast a fair assumption?
                if (cw == null) {
                    logDirect("Failed! No world saved.");
                }
                ICachedRegion cr = cw.loadRegionForMapcreation(rX, rZ);
                if (cr == null) {
                    logDirect("Failed! No regionfile saved at this location.");
                }
                BufferedImage result = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                for (int x = 0; x < 512; x++) {
                    for (int z = 0; z < 512; z++) {
                        for (int y = 255; y >= 0; y--) {
                            IBlockState bs = cr.getBlock(x, y, z);
                            if (bs != null) {
                                if (bs.getBlock().equals(Blocks.AIR)) {
                                    continue;
                                } else {
                                    //todo: use different mapping here. redstone will be grey with this one. the cachedColors hashmap was a good idea
                                    result.setRGB(x, z, new Color(bs.getMaterial().getMaterialMapColor().colorValue).getRGB());
                                    break;
                                }
                            }
                        }
                    }
                }
                try {
                    File screenshotsDir = new File(mc.gameDir, "screenshots");
                    screenshotsDir.mkdir();
                    File outputFile = new File(screenshotsDir, mc.getCurrentServerData().serverIP + "." + rX + "." + rZ + ".png");
                    ImageIO.write(result, "png", outputFile);
                } catch (IOException e) {
                    logDirect("Failed! Image could not be written.(see log)");
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Write chunk view of current world to image";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Loads all cached chunks to create an image file",
                "stored under minecraft screenshots folder as <world name>.png"
        );
    }
}