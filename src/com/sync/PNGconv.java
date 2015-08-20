/**
 * PNGconv - a "whatever Java can load, so not only PNG,
 *              to various image formats, so not only Atari ST,
 *              converter"
 *
 * Input:   .png, .gif, .jpg, .bmp, ...
 * Output:  .pi1-compatible (ST low resolution)
 *          .pi2-compatible (ST medium resolution)
 *          .pi3-compatible (ST high resolution - untested)
 *          chunky (one word per pixel, 0x000-0x777/0xfff ST/STE/Amiga color range)
 *          .png (reduced to 3 or 4 color bitdepth)
 *          ... and more
 *
 * Usage:
 *
 * java -jar PNGconv.jar <input filename> <output filename> [mode] [-option1] [-option2] [-option...]
 *
 * mode = "reduce"  - Converts input image from 8 bit per color channel to 3 or 4, outputs in .PNG
 *      = "chunky"  - Converts input image from 8 bit per color channel to 3 or 4, outputs binary
 *                    with one word per pixel: 0x000-0x777 or 0x000-0xfff.
 *      = "high"    - Converts input image to ST Degas compatible 1 bitplane image (.pi3)
 *      = "medium"  - Converts input image to ST Degas compatible 2 bitplane image (.pi2)
 *      = "low"     - Converts input image to ST Degas compatible 4 bitplane image (.pi1, default)
 *
 * options:
 *
 * -fit         = Calculates number of bitplanes needed for palette in input and uses only those. Might be 3, 5 etc!
 * -ACBM        = Outputs Amiga continuous bitplane format, one full image bitplane after another
 * -ILBM        = Outputs Amiga interleaved bitplane format, one image row of bitplane after another
 * -header      = Adds header with palette to output (in 1,2 and 4 bitplane modes the header will be Atari Degas compatible)
 * -512         = Use ST color depth (3 bits per color channel, default)
 * -4096        = Use Amiga color depth (4 bits per color channel, bit order 4321)
 * -4096STE     = Use STE color depth (4 bits per color channel, bit order 1432)
 * -bgcol xyz   = Force first palette entry (background color) to be "xyz" (in target bit depth hexadecimal, 000 = black)
 *
 * If used in bitplane mode the program will output the number of unique colors found
 * (used for building the palette). No translation between image sizes or palette sizes is made
 * - input image should be valid for the intended output use case.
 *
 * Written by Troed of SYNC
 *
 * Licensed under Creative Commons Zero
 */

// todo: Are ACBM and ILBM required to be 16 pixel aligned or not? Important for rounding up row length

package com.sync;

import com.sun.org.apache.xerces.internal.xs.StringList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PNGconv {

    static final int REDUCE_BITDEPTH = -1;
    static final int CHUNKY_WORD = 0;
    static final int ST_HIGH = 1;
    static final int ST_MEDIUM = 2;
    static final int ST_LOW = 4;

    static final int ST_BITDEPTH = 0;
    static final int STE_BITDEPTH = 1;
    static final int AMIGA_BITDEPTH = 2;

    static final int PLANAR_ST = 0;
    static final int PLANAR_ACBM = 1;
    static final int PLANAR_ILBM = 2;

    int mode = 0;
    char bgcol = 0;

    int coldepth = ST_BITDEPTH;
    boolean header = false;
    boolean fit = false;
    int planarstructure = PLANAR_ST;

    List<Character> palette = new ArrayList<Character>();

    public static void main(String[] args) {
        if(args.length < 2) {
            System.out.println("Please supply at least input and output filenames as parameters");
        } else {
            List<String> parameters = new ArrayList<String>(){};
            for(String s : args) {
                parameters.add(s.toLowerCase());
            }

            int mode = ST_LOW;  // defaults to ST low

            if(parameters.size() >= 3) {
                if (parameters.get(2).equals("reduce")) {
                    mode = REDUCE_BITDEPTH;
                } else if (parameters.get(2).equals("chunky")) {
                    mode = CHUNKY_WORD;
                } else if (parameters.get(2).equals("high")) {
                    mode = ST_HIGH;
                } else if (parameters.get(2).equals("medium")) {
                    mode = ST_MEDIUM;
                } else if (parameters.get(2).equals("low")) {
                    mode = ST_LOW;
                }
            }

            PNGconv conv = new PNGconv(mode);

            if(parameters.contains("-bgcol")) {
                char bgcol = 0x0000; // defaults to black background color
                int pos = parameters.indexOf("-bgcol");
                try {
                    bgcol |= Integer.parseInt(parameters.get(pos + 1), 16);  // parameter in hex
                } catch (NumberFormatException e) {
                    System.out.println("Unable to parse \"" + parameters.get(pos + 1) + "\" as parameter to -bgcol. Continuing with default value.");
                } catch (Exception e) {
                    System.out.println("Unable to find color parameter for -bgcol option");
                }
                conv.setBgcol(bgcol);
            }

            if(parameters.contains("-512")) {
                conv.setColdepth(ST_BITDEPTH);
            } else if(parameters.contains("-4096")) {
                conv.setColdepth(AMIGA_BITDEPTH);
            } else if(parameters.contains("-4096ste")) {
                conv.setColdepth(STE_BITDEPTH);
            }

            if(parameters.contains("-header")) {
                conv.setHeader(true);
            }

            if(parameters.contains("-fit")) {
                conv.setFit(true);
            }

            if(parameters.contains("-acbm")) {
                conv.setPlanarStructure(PLANAR_ACBM);
            } else if(parameters.contains("-ilbm")) {
                conv.setPlanarStructure(PLANAR_ILBM);
            } // else default ST


            conv.conv(args[0], args[1]);
        }
    }

    public PNGconv(int mode) {
        this.mode = mode;
    }

    void setColdepth(int coldepth) {
        this.coldepth = coldepth;
    }

    void setHeader(boolean header) {
        this.header = header;
    }

    void setFit(boolean fit) {
        this.fit = fit;
    }

    void setPlanarStructure(int planarstructure) {
        this.planarstructure = planarstructure;
    }

    void setBgcol(char bgcol) {
        this.bgcol = bgcol;
    }

    void conv(String inputfn, String outputfn) {
        // preload palette with the color that needs to be background color
        // not used in chunky mode
        palette.add(bgcol);

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

        int noBitplanes = 1;

        if (fit) {
            // scan image and count unique colors (fills the palette)
            for (int j = 0; j < y; j++) {
                for (int i = 0; i < x; i++) {
                    lookup(img.getRGB(i, j));
                }
            }

            // now calculate number of bitplanes needed to represent the palette
            while ((1 << noBitplanes) < palette.size()) {
                noBitplanes++;
            }

            System.out.println(palette.size() + " unique colors found. Using " + noBitplanes + " bitplanes.");
        } else {
            noBitplanes = mode; // positive values for mode correspond to number of bitplanes
        }

        char[] output = null;
        int bitplaneRowSize = 0;   // only used in bitmap modes
        if (noBitplanes > 0) {
            bitplaneRowSize = (int) (Math.ceil((double) x / (double) 16)); // size of one bitplane
            output = new char[bitplaneRowSize * noBitplanes * y]; // Make sure we round upwards
        } else if (mode == CHUNKY_WORD) {
            output = new char[x * y];   // one word per pixel
        } // else, not used when outputting .png image

        int addrc = 0;
        int currbit = 0;
        for (int j = 0; j < y; j++) {
            for (int i = 0; i < x; i++) {
                int pixel = img.getRGB(i, j);
                if (mode == REDUCE_BITDEPTH) {
                    if (coldepth == ST_BITDEPTH) {
                        img.setRGB(i, j, (pixel & 0xffe0e0e0)); // AND color channels with 0xe0 to go to 3 bit color depth
                    } else if (coldepth == STE_BITDEPTH || coldepth == AMIGA_BITDEPTH) {
                        img.setRGB(i, j, (pixel & 0xfff0f0f0)); // AND color channels with 0xf0 to go to 4 bit color depth
                        // in this case the STE/Amiga color storage format difference is irrelevant, allow both parameters
                    }
                } else if (mode == CHUNKY_WORD) {
                    // chunky is RGB, 0x000 - 0x777/0xfff, encoded as one word per pixel

                    output[addrc] = chunky(pixel);
                    addrc++;
                } else {
                    int palette = lookup(pixel);
                    for (int n = 0; n < noBitplanes; n++) {
                        if (planarstructure == PLANAR_ST) {
                            // med res is 0,1,2,3 colors
                            // two words encode 16 pixels palette lookup
                            // 0000000000000000
                            // 0000000000000000

                            // for low res it's 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15
                            // four words encode 16 pixels palette lookup

                            output[addrc + n] = (char) (output[addrc + n] << 1);
                            output[addrc + n] |= (char) ((palette >> n) & 0x1);

                        } else if (planarstructure == PLANAR_ACBM) {
                            // each bitplane in full after one another

                            output[addrc + n * bitplaneRowSize * y] = (char) (output[addrc + n * bitplaneRowSize * y] << 1);
                            output[addrc + n * bitplaneRowSize * y] |= (char) ((palette >> n) & 0x1);

                        } else if (planarstructure == PLANAR_ILBM) {
                            // each bitplane image row after one another

                            output[addrc + n * bitplaneRowSize] = (char) (output[addrc + n * bitplaneRowSize] << 1);
                            output[addrc + n * bitplaneRowSize] |= (char) ((palette >> n) & 0x1);

                        }
                    }
                    currbit += 1;
                    if (currbit == 16) {        // current bitplane words filled, move on to next
                        currbit = 0;

                        if (planarstructure == PLANAR_ST) {
                            addrc += noBitplanes;
                        } else if (planarstructure == PLANAR_ACBM) {
                            addrc++;
                        } else if (planarstructure == PLANAR_ILBM) {
                            addrc++;
                            if (addrc % bitplaneRowSize == 0) {
                                // noBitplanes-1 since we just counted up one plane
                                addrc += bitplaneRowSize * (noBitplanes - 1);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("");

        if (mode == REDUCE_BITDEPTH) {
            File f = new File(outputfn);
            try {
                ImageIO.write(img, "PNG", f);
                System.out.println(outputfn + " saved.");
            } catch (IOException e) {
                System.out.println("Error writing file");
            }
        } else {
            int headerlength = 0;

            if (noBitplanes == ST_HIGH || noBitplanes == ST_MEDIUM || noBitplanes == ST_LOW) {
                headerlength = 17; // degas header length, always 16 colors for palette regardless of resolution
            } else {
//                    headerlength = palette.size();
                // Maybe it's more natural for the palette to fully represent the bitplanes instead
                // of just the number of used entries
                headerlength = (int)Math.pow(2, noBitplanes);
            }

            ByteBuffer buffer = ByteBuffer.allocate((output.length + headerlength) * 2);

            if (header) {
                if (noBitplanes == ST_HIGH) {
                    buffer.putChar((char) 0x2); // high res
                    headerlength--;
                } else if (noBitplanes == ST_MEDIUM) {
                    buffer.putChar((char) 0x1); // med res
                    headerlength--;
                } else if (noBitplanes == ST_LOW) {
                    buffer.putChar((char) 0x0); // low res
                    headerlength--;
                }

                System.out.println(palette.size() + " unique colors found. Header length " + headerlength + " words.");

                for (int i = 0; i < headerlength; i++) {
                    char pal;
                    if (i < palette.size()) {
                        pal = palette.get(i);
                    } else {
                        pal = 0;
                    }
                    buffer.putChar(pal);
                }
            }

            for (char c : output) {
                buffer.putChar(c);
            }

            try {
                FileOutputStream fos = new FileOutputStream(outputfn);
                fos.write(buffer.array());
                System.out.println(outputfn + " saved.");
            } catch (IOException e) {
                System.out.println("Error writing file");
            }
        }
    }

    // palette lookup, uses intended output bitdepth values for storage
    // (else we might create palette indexes for colors that will truncate to become the same)
    // if a color isn't already in the palette, it's added

    int lookup(int pixel) {
        char c = chunky(pixel);
        int lookup = palette.indexOf(c);
        if(lookup == -1) {
            palette.add(c);
            lookup = palette.indexOf(c); // ...
        }
        return lookup;
    }

    // translates from Java pixel values to Atari ST 0xRGB (0x000-0x777), Amiga RGB (0x000-0xfff)
    // or Atari STE RGB (0x000-0xfff, where bit 4 is lowest bit to be otherwise compatible with ST)

    char chunky(int value) {
        int R = (value >> 16) & 0xff;
        int G = (value >> 8) & 0xff;
        int B = (value) & 0xff;

        if(coldepth == ST_BITDEPTH) {
            R = R >> 5; // go from 8 bit per color to 3
            G = G >> 5; // go from 8 bit per color to 3
            B = B >> 5; // go from 8 bit per color to 3
        } else if (coldepth == AMIGA_BITDEPTH) {
            R = R >> 4; // go from 8 bit per color to 4
            G = G >> 4; // go from 8 bit per color to 4
            B = B >> 4; // go from 8 bit per color to 4
        } else if (coldepth == STE_BITDEPTH) {
            R = R >> 4; // go from 8 bit per color to 4
            G = G >> 4; // go from 8 bit per color to 4
            B = B >> 4; // go from 8 bit per color to 4
            // rotate nibble (4321 -> 1432)
            int lowbit = (R & 0x1) << 3;
            R = (R >> 1) | lowbit;
            lowbit = (G & 0x1) << 3;
            G = (G >> 1) | lowbit;
            lowbit = (B & 0x1) << 3;
            B = (B >> 1) | lowbit;
        }
        value = (R << 8) + (G << 4) + B; // store in word
        return (char)value;
    }
}
