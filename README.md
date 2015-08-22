    PNGconv - a "whatever Java can load, so not only PNG,
                 to various image formats, so not only Atari ST,
                 converter"

    Input:   .png, .gif, .jpg, .bmp, ...
    Output:  .pi1-compatible (ST low resolution)
             .pi2-compatible (ST medium resolution)
             .pi3-compatible (ST high resolution - untested)
             chunky (one word per pixel, 0x000-0x777/0xfff ST/STE/Amiga color range)
             .png (reduced to 3 or 4 color bitdepth)
             ... and more

    Usage:

    java -jar PNGconv.jar <input filename> <output filename> [mode] [-option1] [-option2] [-option...]

    mode = "reduce"  - Converts input image from 8 bit per color channel to 3 or 4, outputs in .PNG
         = "chunky"  - Converts input image from 8 bit per color channel to 3 or 4, outputs binary
                       with one word per pixel: 0x000-0x777 or 0x000-0xfff.
         = "high"    - Converts input image to ST high resolution
         = "medium"  - Converts input image to ST medium resolution
         = "low"     - Converts input image to ST low resolution

    options:

    -fit         = Calculates number of bitplanes needed for palette in input and uses only those. Might be 3, 5 etc!
    -ACBM        = Outputs Amiga continuous bitplane format, one full image bitplane after another
    -ILBM        = Outputs Amiga interleaved bitplane format, one image row of bitplane after another
    -header      = Adds header with palette to output (in 1,2 and 4 bitplane modes the header will be Atari Degas compatible)
    -512         = Use ST color depth (3 bits per color channel, default)
    -4096        = Use Amiga color depth (4 bits per color channel, bit order 4321)
    -4096STE     = Use STE color depth (4 bits per color channel, bit order 1432)
    -bgcol xyz   = Force first palette entry (background color) to be "xyz" (in target bit depth hexadecimal, 000 = black)

    If used in bitplane mode the program will output the number of unique colors found
    (used for building the palette). No translation between image sizes or palette sizes is made
    - input image should be valid for the intended output use case.

    Written by Troed of SYNC

    Licensed under Creative Commons Zero

