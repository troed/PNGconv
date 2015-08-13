    PNGconv - a "whatever Java can load, so not only PNG, to various Atari ST image formats converter"

    Does not currently make use of the extended STE palette.

    Input:   .png, .gif, .jpg, .bmp, ...
    Output:  .pi1-compatible (ST low resolution)
             .pi2-compatible (ST medium resolution)
             .pi3-compatible (ST high resolution - untested)
             chunky (one word per pixel, 0x000-0x777 ST color range)
             .png (reduced to ST 512 color palette bitdepth)
    
    Usage:

    java -jar PNGconv.jar <input filename> <output filename> [mode] [bgcol]

    mode = "reduce"  - Converts input image from 8 bit per color channel to 3, outputs in .PNG
         = "chunky"  - Converts input image from 8 bit per color channel to 3, outputs binary
                       with one word per pixel. 0x000-0x777
         = "high"    - Converts input image to ST Degas compatible 1 bitplane image (.pi3)
         = "medium"  - Converts input image to ST Degas compatible 2 bitplane image (.pi2)
         = "low"     - Converts input image to ST Degas compatible 4 bitplane image (.pi1)

    bgcol = Color to be forced as background color (in bitplane mode), in hexadecimal.
            "fffff" = white, "000000" = black.

    If used in bitplane mode the program will output the number of unique colors found
    (used for building the palette). No translation between image sizes or palette sizes is made
    - input image should be valid for the intended output use case.

    Written by Troed of SYNC
    
    Licensed under Creative Commons Zero
