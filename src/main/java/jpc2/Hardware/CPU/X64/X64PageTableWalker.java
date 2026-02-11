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



public final class X64PageTableWalker {

    public interface PhysicalMemoryReader {

        long readU64(long physicalAddress);
    }

    public static final class TranslationResult {

        public final long physicalAddress;
        public final int pageSize;

        public TranslationResult(long physicalAddress, int pageSize) {

            this.physicalAddress = physicalAddress;
            this.pageSize = pageSize;
        }
    }

    public static final class PageFault extends RuntimeException {

        private final long m_linearAddress;
        private final boolean m_presentViolation;
        private final boolean m_writeAccess;
        private final boolean m_userAccess;
        private final boolean m_executeAccess;

        public PageFault(long linearAddress,
                         boolean presentViolation,
                         boolean writeAccess,
                         boolean userAccess,
                         boolean executeAccess) {

            super(String.format(
                "Page fault at %016X (presentViolation=%s, write=%s, user=%s, execute=%s)",
                linearAddress,
                presentViolation,
                writeAccess,
                userAccess,
                executeAccess
            ));

            m_linearAddress = linearAddress;
            m_presentViolation = presentViolation;
            m_writeAccess = writeAccess;
            m_userAccess = userAccess;
            m_executeAccess = executeAccess;
        }

        public long getLinearAddress() {

            return m_linearAddress;
        }

        public boolean isPresentViolation() {

            return m_presentViolation;
        }

        public boolean isWriteAccess() {

            return m_writeAccess;
        }

        public boolean isUserAccess() {

            return m_userAccess;
        }

        public boolean isExecuteAccess() {

            return m_executeAccess;
        }
    }

    private static final long PRESENT = 1L << 0;
    private static final long WRITEABLE = 1L << 1;
    private static final long USER = 1L << 2;
    private static final long PAGE_SIZE = 1L << 7;
    private static final long EXECUTE_DISABLE = 1L << 63;
    private static final long ENTRY_ADDR_MASK = 0x000FFFFFFFFFF000L;

    private final PhysicalMemoryReader m_reader;



    public X64PageTableWalker(PhysicalMemoryReader reader) {

        m_reader = reader;
    }



    public TranslationResult translate(long cr3,
                                       long linearAddress,
                                       boolean writeAccess,
                                       boolean userAccess,
                                       boolean executeAccess,
                                       boolean nxeEnabled) {

        long pml4Base = cr3 & ENTRY_ADDR_MASK;

        long pml4e = readEntry(pml4Base, linearAddress, 39);
        validateAccess(linearAddress, pml4e, writeAccess, userAccess, executeAccess, nxeEnabled);

        long pdptBase = pml4e & ENTRY_ADDR_MASK;
        long pdpte = readEntry(pdptBase, linearAddress, 30);
        validateAccess(linearAddress, pdpte, writeAccess, userAccess, executeAccess, nxeEnabled);

        if((pdpte & PAGE_SIZE) != 0L) {
            long pageBase = pdpte & 0x000FFFFFC0000000L;
            return new TranslationResult(pageBase | (linearAddress & 0x3FFFFFFFL), 1 << 30);
        }

        long pdBase = pdpte & ENTRY_ADDR_MASK;
        long pde = readEntry(pdBase, linearAddress, 21);
        validateAccess(linearAddress, pde, writeAccess, userAccess, executeAccess, nxeEnabled);

        if((pde & PAGE_SIZE) != 0L) {
            long pageBase = pde & 0x000FFFFFFFE00000L;
            return new TranslationResult(pageBase | (linearAddress & 0x1FFFFFL), 1 << 21);
        }

        long ptBase = pde & ENTRY_ADDR_MASK;
        long pte = readEntry(ptBase, linearAddress, 12);
        validateAccess(linearAddress, pte, writeAccess, userAccess, executeAccess, nxeEnabled);

        long pageBase = pte & ENTRY_ADDR_MASK;
        return new TranslationResult(pageBase | (linearAddress & 0xFFFL), 1 << 12);
    }



    private long readEntry(long tableBase, long linearAddress, int levelShift) {

        long index = (linearAddress >>> levelShift) & 0x1FFL;
        long entryAddress = tableBase + (index << 3);
        long entry = m_reader.readU64(entryAddress);

        if((entry & PRESENT) == 0L)
            throw new PageFault(linearAddress, false, false, false, false);

        return entry;
    }

    private void validateAccess(long linearAddress,
                                long entry,
                                boolean writeAccess,
                                boolean userAccess,
                                boolean executeAccess,
                                boolean nxeEnabled) {

        if(writeAccess && (entry & WRITEABLE) == 0L)
            throw new PageFault(linearAddress, true, true, userAccess, false);

        if(userAccess && (entry & USER) == 0L)
            throw new PageFault(linearAddress, true, writeAccess, true, false);

        if(executeAccess && nxeEnabled && (entry & EXECUTE_DISABLE) != 0L)
            throw new PageFault(linearAddress, true, writeAccess, userAccess, true);
    }
}
