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



public final class Register64 {

    private final String m_name;
    private long m_value;



    public Register64(String name) {

        m_name = name;
    }



    public void reset() {

        m_value = 0L;
    }

    public long getValue() {

        return m_value;
    }

    public int getValue32() {

        return (int)m_value;
    }

    public void setValue(long value) {

        m_value = value;
    }

    /**
     * x86-64 semantics: 32-bit writes zero-extend into the 64-bit register.
     */
    public void setValue32(int value) {

        m_value = Integer.toUnsignedLong(value);
    }



    @Override
    public String toString() {

        return String.format("%s:%016X", m_name, m_value);
    }
}
