package com.maddyhome.idea.color.ui.colorchooser;

/*
 * Color Browser - Color browser plugin for IDEA
 * Copyright (C) 2006 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageProducer;

/**
 * A helper class to make computing synthetic images a little easier. All you need to do is define a subclass that
 * overrides computeRow to compute a row of the image.  It is passed the y coordinate of the row and an array into which
 * to put the pixels in <a href="http://java.sun.com/products/jdk/1.1/docs/api/java.awt.image.ColorModel.html#getRGBdefault()">
 * standard ARGB format</a>. <p>Normal usage looks something like this:
 * <pre>&nbsp;Image i = createImage(new SyntheticImage(200, 100) {
 *  &nbsp;    protected void computeRow(int y, int[] row) {
 *  &nbsp;	for(int i = width; --i>=0; ) {
 *  &nbsp;	    int grey = i*255/(width-1);
 *  &nbsp;	    row[i] = (255<<24)|(grey<<16)|(grey<<8)|grey;
 *  &nbsp;	}
 *  &nbsp;    }
 *  &nbsp;}
 *  </pre>This creates a image 200 pixels wide and 100 pixels high
 * that is a horizontal grey ramp, going from black on the left to white on the right.
 * <p/>
 * If the image is to be a movie, override isStatic to return false, <i>y</i> cycling back to 0 is computeRow's signal
 * that the next frame has started.  It is acceptable (expected?) for computeRow(0,r) to pause until the appropriate
 * time to start the next frame.
 *
 * This class was copied as-is from the JDK 1.5 source code.
 * @author James Gosling
 * @version 1.24 12/19/03
 */
abstract class SyntheticImage implements ImageProducer
{
    private SyntheticImageGenerator root;
    protected int width = 10, height = 100;
    static final ColorModel cm = ColorModel.getRGBdefault();
    public static final int pixMask = 0xFF;
    private Thread runner;

    protected SyntheticImage()
    {
    }

    protected SyntheticImage(int w, int h)
    {
        width = w;
        height = h;
    }

    protected void computeRow(int y, int[] row)
    {
        int p = 255 - 255 * y / (height - 1);
        p = (pixMask << 24) | (p << 16) | (p << 8) | p;
        for (int i = row.length; --i >= 0;)
        {
            row[i] = p;
        }
    }

    public synchronized void addConsumer(ImageConsumer ic)
    {
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
        {
            if (ics.ic == ic)
            {
                return;
            }
        }
        root = new SyntheticImageGenerator(ic, root, this);
    }

    public synchronized boolean isConsumer(ImageConsumer ic)
    {
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
        {
            if (ics.ic == ic)
            {
                return true;
            }
        }
        return false;
    }

    public synchronized void removeConsumer(ImageConsumer ic)
    {
        SyntheticImageGenerator prev = null;
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
        {
            if (ics.ic == ic)
            {
                ics.useful = false;
                if (prev != null)
                {
                    prev.next = ics.next;
                }
                else
                {
                    root = ics.next;
                }
                return;
            }
            prev = ics;
        }
    }

    public synchronized void startProduction(ImageConsumer ic)
    {
        addConsumer(ic);
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
        {
            if (ics.useful && !ics.isAlive())
            {
                ics.start();
            }
        }
    }

    protected boolean isStatic()
    {
        return true;
    }

    public void nextFrame(int param)
    {
    }//Override if !isStatic

    public void requestTopDownLeftRightResend(ImageConsumer ic)
    {
    }

    protected volatile boolean aborted = false;
}

class SyntheticImageGenerator extends Thread
{
    ImageConsumer ic;
    boolean useful;
    SyntheticImageGenerator next;
    SyntheticImage parent;

    SyntheticImageGenerator(ImageConsumer ic, SyntheticImageGenerator next,
        SyntheticImage parent)
    {
        super("SyntheticImageGenerator");
        this.ic = ic;
        this.next = next;
        this.parent = parent;
        useful = true;
        setDaemon(true);
    }

    public void run()
    {
        ImageConsumer ic = this.ic;
        int w = parent.width;
        int h = parent.height;
        int hints = ImageConsumer.SINGLEPASS | ImageConsumer.COMPLETESCANLINES | ImageConsumer.TOPDOWNLEFTRIGHT;
        if (parent.isStatic())
        {
            hints |= ImageConsumer.SINGLEFRAME;
        }
        ic.setHints(hints);
        ic.setDimensions(w, h);
        ic.setProperties(null);
        ic.setColorModel(parent.cm);

        if (useful)
        {
            int[] row = new int[w];
            doPrivileged(new Runnable()
            {
                public void run()
                {
                    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                }
            });

            do
            {
                for (int y = 0; y < h && useful; y++)
                {
                    parent.computeRow(y, row);

                    if (parent.aborted)
                    {
                        ic.imageComplete(ImageConsumer.IMAGEABORTED);
                        return;
                    }

                    ic.setPixels(0, y, w, 1, parent.cm, row, 0, w);
                }
                ic.imageComplete(parent.isStatic() ? ImageConsumer.STATICIMAGEDONE
                    : ImageConsumer.SINGLEFRAMEDONE);
            }
            while (!parent.isStatic() && useful);
        }
    }

    private static void doPrivileged(final Runnable doRun)
    {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Object>()
            {
                public Object run()
                {
                    doRun.run();
                    return null;
                }
            }
        );
    }
}