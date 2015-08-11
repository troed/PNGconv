    PNGconv - a "whatever Java can load, so not only PNG, to various Atari ST image formats converter"

    Needs recompiling between changing settings. Does not use the STE palette.

    Input:   .png, .gif, ...
    Output:  .pi1-compatible (ST low resolution)
             .pi2-compatible (ST medium resolution)
             .pi3-compatible (ST high resolution - untested)
             chunky (one word per pixel, 0x000-0x777 ST color range)

    Settings: (set at compile time)
             Color to be forced as background color (in bitmap mode)
             Number of bitplanes (1,2,4 - or 0 for chunky)

    If used in palette mode the program will output the number of unique colors found (used for building
    the palette). No translation between image sizes or palette sizes is made - input image should be
    valid for the intended output use case.

    Written by Troed of SYNC

    Licensed under Creative Commons Zero
