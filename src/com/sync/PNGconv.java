/**
 * PNGconv - a "whatever Java can load, so not only PNG, to various Atari ST image formats converter"
 *
 * Needs recompiling between changing settings. Does not use the STE palette.
 *
 * Input:   .png, .gif, ...
 * Output:  .pi1-compatible (ST low resolution)
 *          .pi2-compatible (ST medium resolution)
 *          .pi3-compatible (ST high resolution - untested)
 *          chunky (one word per pixel, 0x000-0x777 ST color range)
 *
 * Settings: (set at compile time)
 *          Color to be forced as background color (in bitmap mode)
 *          Number of bitplanes (1,2,4 - or 0 for chunky)
 *
 * If used in palette mode the program will output the number of unique colors found (used for building the palette).
 * No translation between image sizes or palette sizes is made - input image should be valid for the intended output use case.
 *
 * Written by Troed of SYNC
 */

package com.sync;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PNGconv {

    List<Integer> palette = new ArrayList<Integer>();

    public static void main(String[] args) {
        if(args.length < 2) {
            System.out.println("Please supply input and output filenames as parameters");
        } else {
            PNGconv conv = new PNGconv();
            conv.conv(args[0], args[1]);
        }
    }

    void conv(String inputfn, String outputfn) {
        // preload palette with the color that needs to be background color
        // not used in chunky mode
//        palette.add(-1); // white
        palette.add(-16777216); // black

        int noBitplanes = 0; // 4 & 2 tested, 1 (hires) might also work. Set to 0 for 16 bit chunky.

        BufferedImage img = null;
        try {
            img = ImageIO.read(new File(inputfn));
            System.out.println("Converting " + inputfn);
        } catch (IOException e) {
            System.out.println("Input file not found");
            return;
        }

        int x = img.getWidth();
        int y = img.getHeight();

        // med res is 0,1,2,3 colors
        // two words encode 16 pixels palette lookup
        // 0000000000000000
        // 0000000000000000

        // for low res it's 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15
        // four words encode 16 pixels palette lookup

        // chunky is RGB, 0x000 - 0x777, encoded as one word per pixel

        char[] output;
        if(noBitplanes > 0) {
            output = new char[(x * y / 16) * noBitplanes];
        } else {
            output = new char[x * y];   // one word per pixel
        }

        int addrc = 0;
        int currbit = 0;
        for(int j=0;j<y;j++) {
            for(int i=0;i<x;i++) {
                int pixel = img.getRGB(i, j);
                if(noBitplanes == 0) {
                    output[addrc] = chunky(pixel);
                    System.out.print(".");
                    addrc++;
                } else {
                    int palette = lookup(pixel);
                    for (int n = 0; n < noBitplanes; n++) {
//                    System.out.println("Bitplane: " + n + " Palette: " + palette);
                        if (palette == -1) {
                            System.out.println("Error in palette lookup!");
                        } else {
                            output[addrc + n] = (char) (output[addrc + n] << 1);
                            output[addrc + n] |= (char) ((palette >> n) & 0x1);
//                        System.out.println(Integer.toBinaryString(output[addrc + n]));
                        }
//                    System.out.print(pixel + "|");
                    }
                    currbit += 1;
                    if (currbit == 16) {        // current bitplane words filled, move on to next
                        System.out.print(".");
                        currbit = 0;
                        addrc += noBitplanes;
                    }
                }
            }
        }

        System.out.println("");

        int headerlength = 0;

        if(noBitplanes > 0) {
            headerlength = 34; // degas header length, always 16 colors for palette regardless of resolution
        }

        ByteBuffer buffer = ByteBuffer.allocate(output.length * 2 + headerlength);

        if(noBitplanes > 0) {
            if(noBitplanes == 1) {
                buffer.putChar((char)0x2); // high res
            } else if(noBitplanes == 2) {
                buffer.putChar((char)0x1); // med res
            } else if(noBitplanes == 4) {
                buffer.putChar((char)0x0); // low res
            }
            System.out.println(palette.size() + " unique colors found.");
            for (int i = 0; i < 16; i++) {     // degas palette always 16 colors, also in med res.
                int pal;
                if(i < palette.size()) {
                    pal = palette.get(i);
                } else {
                    pal = -1;
                }
                buffer.putChar(chunky(pal));
            }
        }

        for(int i=0;i<output.length;i++) {
            buffer.putChar(output[i]);
        }

        try {
            FileOutputStream fos = new FileOutputStream(outputfn);
            fos.write(buffer.array());
            System.out.println(outputfn + " saved.");
        } catch (IOException e) {
            System.out.println("Error writing file");
        }
    }

    // palette lookup, uses Java native pixel values for storage
    // if a color isn't already in the palette, it's added
    int lookup(int pixel) {
        int lookup = palette.indexOf(pixel);
        if(lookup == -1) {
            palette.add(pixel);
            lookup = palette.indexOf(pixel); // ...
        }
        return lookup;
    }

    // translates from Java pixel values to Atari ST 0xRGB (0x000-0x777)
    // if extending for STE, remember proper ordering: 0x000-0x777 as ST, 0x888-0xfff is interleaved one value higher
    char chunky(int value) {
        int R = (value >> 16) & 0xff;
        int G = (value >> 8) & 0xff;
        int B = (value) & 0xff;
        // assume ST, not STE, bit depth
        R = R >> 5; // go from 8 bit per color to 3
        G = G >> 5; // go from 8 bit per color to 3
        B = B >> 5; // go from 8 bit per color to 3
        value = (R << 8) + (G << 4) + B;
        return (char)value;
    }
}
