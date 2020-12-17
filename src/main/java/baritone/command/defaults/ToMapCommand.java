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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.cache.ICachedRegion;
import baritone.api.cache.ICachedWorld;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.cache.CachedWorld;
import baritone.cache.WorldScanner;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ToMapCommand extends Command {

    private static HashMap<String, Color> cachedColors;
    private static Color DEFAULT_BLOCK_COLOR = new Color(Color.TRANSLUCENT, true);
    private static Pattern BLOCK_ID_PATTERN = Pattern.compile("minecraft:(.*?)(\\[.*\\])?$");
    private static String TEXTURE_BASE_PATH = "/home/<user>/.minecraft/versions/1.12.2/1.12.2/assets/minecraft/textures/blocks/";
    private ExecutorService executor;

    public ToMapCommand(IBaritone baritone) {
        super(baritone, "tomap");
        cachedColors = new HashMap<>();
        executor = Executors.newFixedThreadPool(5);
    }

    private List<int[]> getRegionCoordsOnDisk() {
        return Arrays.asList(
                new int[]{1, 1},
                new int[]{1, 0},
                new int[]{0, 1},
                new int[]{0, 0},
                new int[]{-1, 0},
                new int[]{0, -1},
                new int[]{-1, -1}
        );
//        System.out.println("Testing region coords on disk");
//        String[] regionCoordsOnDisk = new File(
//                Baritone.getDir(),
//                mc.getCurrentServerData().serverIP + "/DIM0/cache").list();
//        List<int[]> fileNamesForRegions = new ArrayList<>();
//        for (String regionFileName : regionCoordsOnDisk) {
//            Matcher m = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.bcr").matcher(regionFileName);
//            if (m.find()) {
//                int regionX = Integer.parseInt(m.group(1));
//                int regionZ = Integer.parseInt(m.group(2));
//                fileNamesForRegions.add(new int[]{regionX, regionZ});
//            }
//        }
//        return fileNamesForRegions;
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        WorldScanner.INSTANCE.repack(ctx);
        ctx.worldData().getCachedWorld().save();
        for (int[] regionCoords : getRegionCoordsOnDisk()) {
            int rX = regionCoords[0];
            int rZ = regionCoords[1];
            executor.execute(saveToImage(rX, rZ));
        }
/*
        //todo: make options for rendering
        // all cached regions,(use executor for this)
        // a specific one(region -1 3),
        // a specific region at a given blockpos(pos 1337420 322),
        // default to the region the player is standing in
        // use the args parameter for that
        executor.execute(saveToImage((int) Math.floor(mc.player.posX / 512), (int) Math.floor(mc.player.posZ / 512)));
*/
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
                logDirect("Getting top blocks for region " + rX + " " + rZ);
                for (int x = 0; x < 512; x++) {
                    for (int z = 0; z < 512; z++) {
                        for (int y = 255; y >= 0; y--) {
                            IBlockState bs = cr.getBlock(x, y, z);
                            if (bs == null || bs.getBlock().equals(Blocks.AIR)) {
                                continue;
                            }
                            try {
                                result.setRGB(x, z, getColorForBlock(bs).getRGB());
                            } catch (IOException e) {
                                e.printStackTrace();
                                result.setRGB(x, z, DEFAULT_BLOCK_COLOR.getRGB());
                            }
                            break;
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

    public Color averageColor(BufferedImage bi) {
        int x1 = 16;
        int z1 = 16;
        long sumr = 0, sumg = 0, sumb = 0;
        for (int x = 0; x < x1; x++) {
            for (int z = 0; z < z1; z++) {
                Color pixel = new Color(bi.getRGB(x, z));
                sumr += pixel.getRed();
                sumg += pixel.getGreen();
                sumb += pixel.getBlue();
            }
        }
        float num = 16 * 16 * 256;
        return new Color(sumr / num, sumg / num, sumb / num);
    }

    private Color getColorForBlock(IBlockState blockstate) throws IOException {
        String blockId = guessBlockId(blockstate);
        if (cachedColors.containsKey(blockId))
            return cachedColors.get(blockId);
        if (blockId == null) {
            return DEFAULT_BLOCK_COLOR;
        }
        System.out.println("Getting texture for " + blockId);
        Path withTopPath = Paths.get(TEXTURE_BASE_PATH + blockId + "_top.png");
        Path withoutTopPath = Paths.get(TEXTURE_BASE_PATH + blockId + ".png");
        Path debug = Paths.get(TEXTURE_BASE_PATH + "debug.png");
        BufferedImage texture = null;
        if (Files.exists(withTopPath))
            texture = ImageIO.read(Files.newInputStream(withTopPath));
        else if (Files.exists(withoutTopPath))
            texture = ImageIO.read(Files.newInputStream(withoutTopPath));
        Color color = texture != null ? averageColor(texture) : new Color(blockstate.getMaterial().getMaterialMapColor().colorValue);
        cachedColors.put(blockId, color);
        return color;
    }

    private String guessBlockId(IBlockState blockstate) {
        String id;
        try {
            Matcher m = BLOCK_ID_PATTERN.matcher(blockstate.toString());
            if (m.find())
                return m.group(1);
        } catch (Exception e) {
            logDebug("Failed to find block id for " + blockstate.toString());
            e.printStackTrace();
        }
        return null;
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
