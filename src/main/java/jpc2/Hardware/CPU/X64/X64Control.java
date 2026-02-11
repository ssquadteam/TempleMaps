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
package Hardware.CPU.X64;



public final class X64Control {

    public static final long CR0_PE = 1L << 0;
    public static final long CR0_MP = 1L << 1;
    public static final long CR0_EM = 1L << 2;
    public static final long CR0_TS = 1L << 3;
    public static final long CR0_ET = 1L << 4;
    public static final long CR0_NE = 1L << 5;
    public static final long CR0_WP = 1L << 16;
    public static final long CR0_AM = 1L << 18;
    public static final long CR0_NW = 1L << 29;
    public static final long CR0_CD = 1L << 30;
    public static final long CR0_PG = 1L << 31;

    public static final long CR4_PAE = 1L << 5;
    public static final long CR4_PGE = 1L << 7;
    public static final long CR4_OSFXSR = 1L << 9;
    public static final long CR4_OSXMMEXCPT = 1L << 10;
    public static final long CR4_FSGSBASE = 1L << 16;
    public static final long CR4_OSXSAVE = 1L << 18;
    public static final long CR4_SMEP = 1L << 20;
    public static final long CR4_SMAP = 1L << 21;

    public static final long EFER_SCE = 1L << 0;
    public static final long EFER_LME = 1L << 8;
    public static final long EFER_LMA = 1L << 10;
    public static final long EFER_NXE = 1L << 11;

    private static final long CR3_VALID_MASK = 0x000FFFFFFFFFF000L;

    private long m_cr0;
    private long m_cr2;
    private long m_cr3;
    private long m_cr4;
    private long m_efer;



    public void reset(boolean hasFpu) {

        m_cr0 = hasFpu ? CR0_MP | CR0_ET : CR0_ET;
        m_cr2 = 0L;
        m_cr3 = 0L;
        m_cr4 = 0L;
        m_efer = 0L;
    }

    public long getCR0() {

        return m_cr0;
    }

    public void setCR0(long value) {

        m_cr0 = value;
        updateLongModeActiveBit();
    }

    public long getCR2() {

        return m_cr2;
    }

    public void setCR2(long value) {

        m_cr2 = value;
    }

    public long getCR3() {

        return m_cr3;
    }

    public void setCR3(long value) {

        m_cr3 = value & CR3_VALID_MASK;
    }

    public long getCR4() {

        return m_cr4;
    }

    public void setCR4(long value) {

        m_cr4 = value;
        updateLongModeActiveBit();
    }

    public long getEFER() {

        return m_efer;
    }

    public void setEFER(long value) {

        // LMA is a read-only architectural status bit.
        long requested = value & ~EFER_LMA;
        m_efer = requested;
        updateLongModeActiveBit();
    }



    public boolean isProtectedModeEnabled() {

        return (m_cr0 & CR0_PE) != 0;
    }

    public boolean isPagingEnabled() {

        return (m_cr0 & CR0_PG) != 0;
    }

    public boolean isPAEEnabled() {

        return (m_cr4 & CR4_PAE) != 0;
    }

    public boolean isLongModeEnabled() {

        return (m_efer & EFER_LME) != 0;
    }

    public boolean isLongModeActive() {

        return (m_efer & EFER_LMA) != 0;
    }



    private void updateLongModeActiveBit() {

        boolean active = isLongModeEnabled() && isPAEEnabled() && isPagingEnabled();

        if(active)
            m_efer |= EFER_LMA;
        else
            m_efer &= ~EFER_LMA;
    }



    @Override
    public String toString() {

        return String.format(
            "CR0:%016X CR2:%016X CR3:%016X CR4:%016X EFER:%016X",
            m_cr0,
            m_cr2,
            m_cr3,
            m_cr4,
            m_efer
        );
    }
}
