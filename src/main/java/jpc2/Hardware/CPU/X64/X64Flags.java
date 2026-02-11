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



public final class X64Flags {

    public static final long CARRY = 1L << 0;
    public static final long PARITY = 1L << 2;
    public static final long AUX = 1L << 4;
    public static final long ZERO = 1L << 6;
    public static final long SIGN = 1L << 7;
    public static final long TRAP = 1L << 8;
    public static final long INTERRUPT = 1L << 9;
    public static final long DIRECTION = 1L << 10;
    public static final long OVERFLOW = 1L << 11;
    public static final long IOPL = 3L << 12;
    public static final long NESTED_TASK = 1L << 14;
    public static final long RESUME = 1L << 16;
    public static final long V8086 = 1L << 17;
    public static final long ALIGNMENT_CHECK = 1L << 18;
    public static final long VIRTUAL_INTERRUPT = 1L << 19;
    public static final long VIRTUAL_INTERRUPT_PENDING = 1L << 20;
    public static final long ID = 1L << 21;

    // Bit 1 of FLAGS/RFLAGS is architecturally fixed at 1.
    private static final long REQUIRED_ONE = 1L << 1;
    private long m_rflags;



    public void reset() {

        m_rflags = REQUIRED_ONE;
    }

    public long getValue() {

        return m_rflags;
    }

    public void setValue(long value) {

        m_rflags = value | REQUIRED_ONE;
    }

    public boolean isSet(long mask) {

        return (m_rflags & mask) != 0;
    }

    public void set(long mask, boolean enabled) {

        if(enabled)
            m_rflags |= mask;
        else
            m_rflags &= ~mask;

        m_rflags |= REQUIRED_ONE;
    }



    @Override
    public String toString() {

        return String.format("RFLAGS:%016X", m_rflags);
    }
}
