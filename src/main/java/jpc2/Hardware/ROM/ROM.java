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
package Hardware.ROM;

import Hardware.HardwareComponent;
import MemoryMap.MemoryReadable;
import Utility.FileResource;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;



public abstract class ROM implements HardwareComponent,
                                     MemoryReadable {

    /* ----------------------------------------------------- *
     * ROM data                                              *
     * ----------------------------------------------------- */
    private static final String ROM_PATH = "data/roms";
    private static final String RESOURCE_PATH = "roms/";
    private int m_data[];

    /* ----------------------------------------------------- *
     * ROM mapping information                               *
     * ----------------------------------------------------- */
    private final int m_startAddress;



    public ROM(String fileName, String expectedMD5Hash, int startAddress, int length, boolean isOptional) {

        m_startAddress = startAddress;

        m_data = new int[length];
        Arrays.fill(m_data, 0xff);

        // Try loading from classpath resources first (skip MD5 check for resources)
        try {
            FileResource.readResource(m_data, RESOURCE_PATH + fileName);
            System.out.println("[ROM] Loaded from resources: " + fileName);
            return;
        }
        catch(IOException ex) {
            // Fall through to file system
        }

        // Try loading from file system
        try {
            FileResource.read(m_data, new File(ROM_PATH, fileName), expectedMD5Hash);
            System.out.println("[ROM] Loaded from file: " + fileName);
        }
        catch(IOException ex) {

            if(!isOptional)
                throw new IllegalArgumentException("Error while reading the rom image " + fileName + ". Place it in " + ROM_PATH + " or in classpath resources at " + RESOURCE_PATH, ex);
            else
                System.out.println("[ROM] Optional ROM not found: " + fileName);
        }
    }



    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MemoryReadable">

    @Override
    public int[][] getReadableMemoryAddresses() {

        return new int[][] {

            new int[] { m_startAddress, m_data.length, 0x0000 }
        };
    }

    @Override
    public int readMEM8(int address) {

        return m_data[address];
    }

    @Override
    public int readMEM16(int address) {

        return m_data[address] |
              (m_data[address + 1] << 8);
    }

    @Override
    public int readMEM32(int address) {

        return m_data[address] |
              (m_data[address + 1] << 8) |
              (m_data[address + 2] << 16) |
              (m_data[address + 3] << 24);
    }

    // </editor-fold>
}
