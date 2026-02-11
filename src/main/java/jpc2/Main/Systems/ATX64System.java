/*
 * Copyright (C) 2017 h0MER247
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
package Main.Systems;

import Hardware.CMOS.BochsCMOSMap;
import Hardware.CMOS.CMOS;
import Hardware.CPU.X64.X64CPU;
import Hardware.Control.ControlPorts;
import Hardware.DMAController.Intel8237;
import Hardware.IDE.IDE;
import Hardware.InterruptController.PICs;
import Hardware.Keyboard.PS2.PS2Keyboard;
import Hardware.Mouse.PS2.PS2Mouse;
import Hardware.PS2.PS2Controller;
import Hardware.ROM.System.Bochs.BochsBios;
import Hardware.Speaker.Speaker;
import Hardware.SystemRAM.ATSystemRAM;
import Hardware.Timer.Intel8253;
import Hardware.Video.GraphicsCardListener;
import Hardware.Video.VGA.TsengET4000.TsengET4000;



public final class ATX64System extends JPCSystem {

    public static final String SYSTEM_NAME = "AT x64 System";



    public ATX64System(GraphicsCardListener gfxListener) {

        // Keep current 32-bit memory map infrastructure while x64 core is being built.
        super(SYSTEM_NAME, 32);

        addComponent(new X64CPU(getIOMap(), getMemoryMap(), getScheduler()));
        addComponent(new TsengET4000(gfxListener));
        addComponent(new PS2Controller());
        addComponent(new PS2Keyboard());
        addComponent(new PS2Mouse());
        addComponent(new IDE(true, 14));
        addComponent(new IDE(false, 15));
        addComponent(new ControlPorts());
        addComponent(new PICs(false));
        addComponent(new Intel8253());
        addComponent(new Intel8237());
        addComponent(new ATSystemRAM());
        addComponent(new BochsBios());
        addComponent(new Speaker());
        addComponent(new CMOS("atx64.nvr", new BochsCMOSMap()));
    }
}
