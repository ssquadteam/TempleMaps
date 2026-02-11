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

import Hardware.CPU.CPU;
import IOMap.IOMap;
import Main.Systems.ComponentConfig;
import Main.Systems.ComponentConfig.Type;
import MemoryMap.MemoryMap;
import Scheduler.Scheduler;
import java.util.HashMap;
import java.util.Map;



public final class X64CPU implements CPU {

    public enum ExecutionMode {

        REAL,
        PROTECTED,
        LONG
    }

    public final Register64 RAX;
    public final Register64 RBX;
    public final Register64 RCX;
    public final Register64 RDX;
    public final Register64 RSP;
    public final Register64 RBP;
    public final Register64 RSI;
    public final Register64 RDI;
    public final Register64 R8;
    public final Register64 R9;
    public final Register64 R10;
    public final Register64 R11;
    public final Register64 R12;
    public final Register64 R13;
    public final Register64 R14;
    public final Register64 R15;
    public final Register64 RIP;
    public final X64Flags FLAGS;
    public final X64Control CR;

    private static final int MSR_IA32_EFER = 0xC0000080;
    private static final int MSR_IA32_FS_BASE = 0xC0000100;
    private static final int MSR_IA32_GS_BASE = 0xC0000101;
    private static final int MSR_IA32_KERNEL_GS_BASE = 0xC0000102;

    private final IOMap m_ioMap;
    private final MemoryMap m_memoryMap;
    private final Scheduler m_scheduler;
    private final X64PageTableWalker m_pageWalker;
    private final Map<Integer, Long> m_msrStorage;

    private boolean m_decodeEnabled;
    private boolean m_isBootstrapNoticeShown;
    private boolean m_isHaltedByUnsupportedOpcode;
    private long m_a20GateMask;
    private long m_cr8;
    private long m_fsBase;
    private long m_gsBase;
    private long m_kernelGsBase;
    private boolean m_halted;

    // Segment state used for real-mode execution.
    private int m_segCS;
    private int m_segDS;
    private int m_segES;
    private int m_segSS;
    private int m_segFS;
    private int m_segGS;



    public X64CPU(IOMap ioMap, MemoryMap memoryMap, Scheduler scheduler) {

        m_ioMap = ioMap;
        m_memoryMap = memoryMap;
        m_scheduler = scheduler;
        m_pageWalker = new X64PageTableWalker(this::readPhysicalU64);
        m_msrStorage = new HashMap<>();

        RAX = new Register64("rax");
        RBX = new Register64("rbx");
        RCX = new Register64("rcx");
        RDX = new Register64("rdx");
        RSP = new Register64("rsp");
        RBP = new Register64("rbp");
        RSI = new Register64("rsi");
        RDI = new Register64("rdi");
        R8 = new Register64("r8");
        R9 = new Register64("r9");
        R10 = new Register64("r10");
        R11 = new Register64("r11");
        R12 = new Register64("r12");
        R13 = new Register64("r13");
        R14 = new Register64("r14");
        R15 = new Register64("r15");
        RIP = new Register64("rip");
        FLAGS = new X64Flags();
        CR = new X64Control();

        m_decodeEnabled = false;
    }



    @Override
    public void reset() {

        RAX.reset();
        RBX.reset();
        RCX.reset();
        RDX.reset();
        RSP.reset();
        RBP.reset();
        RSI.reset();
        RDI.reset();
        R8.reset();
        R9.reset();
        R10.reset();
        R11.reset();
        R12.reset();
        R13.reset();
        R14.reset();
        R15.reset();
        RIP.setValue(0x00000000000FFFF0L);
        FLAGS.reset();
        CR.reset(true);
        setA20Gate(false);
        m_cr8 = 0L;
        m_fsBase = 0L;
        m_gsBase = 0L;
        m_kernelGsBase = 0L;
        m_halted = false;
        m_isHaltedByUnsupportedOpcode = false;

        m_segCS = 0xF000;
        m_segDS = 0x0000;
        m_segES = 0x0000;
        m_segSS = 0x0000;
        m_segFS = 0x0000;
        m_segGS = 0x0000;
    }

    @Override
    public String getConfigCategory() {

        return "CPU";
    }

    @Override
    public void provideConfigValues(ComponentConfig.Builder builder) {

        builder.value("Frequency", "100000000", Type.ToggleGroup, this::setFrequency)
               .option("100 MHz", "100000000")
               .option("250 MHz", "250000000")
               .option("500 MHz", "500000000")
               .option("1 GHz", "1000000000")
               .build();

        builder.value("Execution", "idle", Type.ToggleGroup, this::setExecutionProfile)
               .option("Idle bootstrap", "idle")
               .option("Decode (experimental)", "decode")
               .build();
    }

    private boolean setFrequency(String value) {

        m_scheduler.setBaseFrequency(Float.valueOf(value), true);
        return true;
    }

    private boolean setExecutionProfile(String value) {

        m_decodeEnabled = "decode".equalsIgnoreCase(value);
        return true;
    }

    public ExecutionMode getExecutionMode() {

        if(CR.isLongModeActive())
            return ExecutionMode.LONG;

        if(CR.isProtectedModeEnabled())
            return ExecutionMode.PROTECTED;

        return ExecutionMode.REAL;
    }

    public void setA20Gate(boolean gate) {

        m_a20GateMask = 0xFFFFFFFFL;

        if(!gate)
            m_a20GateMask &= ~(1L << 20);
    }

    public long readControlRegister(int index) {

        switch(index) {

            case 0:
                return CR.getCR0();

            case 2:
                return CR.getCR2();

            case 3:
                return CR.getCR3();

            case 4:
                return CR.getCR4();

            case 8:
                return m_cr8;

            default:
                throw new UnsupportedOperationException("Unsupported control register CR" + index);
        }
    }

    public void writeControlRegister(int index, long value) {

        switch(index) {

            case 0:
                CR.setCR0(value);
                break;

            case 2:
                CR.setCR2(value);
                break;

            case 3:
                CR.setCR3(value);
                break;

            case 4:
                CR.setCR4(value);
                break;

            case 8:
                m_cr8 = value & 0x0fL;
                break;

            default:
                throw new UnsupportedOperationException("Unsupported control register CR" + index);
        }
    }

    public long readMSR(int index) {

        switch(index) {

            case MSR_IA32_EFER:
                return CR.getEFER();

            case MSR_IA32_FS_BASE:
                return m_fsBase;

            case MSR_IA32_GS_BASE:
                return m_gsBase;

            case MSR_IA32_KERNEL_GS_BASE:
                return m_kernelGsBase;

            default:
                return m_msrStorage.getOrDefault(index, 0L);
        }
    }

    public void writeMSR(int index, long value) {

        switch(index) {

            case MSR_IA32_EFER:
                CR.setEFER(value);
                break;

            case MSR_IA32_FS_BASE:
                m_fsBase = value;
                break;

            case MSR_IA32_GS_BASE:
                m_gsBase = value;
                break;

            case MSR_IA32_KERNEL_GS_BASE:
                m_kernelGsBase = value;
                break;

            default:
                m_msrStorage.put(index, value);
                break;
        }
    }

    @Override
    public void run(int numBlocks) {

        if(!m_isBootstrapNoticeShown) {
            m_isBootstrapNoticeShown = true;
            System.out.printf(
                "[X64CPU] Execution profile: %s\n",
                m_decodeEnabled ? "decode (experimental)" : "idle bootstrap"
            );
        }

        if(!m_decodeEnabled || m_isHaltedByUnsupportedOpcode || m_halted) {
            m_scheduler.updateClock(Scheduler.toFixedPoint(8 * Math.max(numBlocks, 1)));
            return;
        }

        while(numBlocks-- > 0) {

            try {

                int cycles = executeSingleInstruction();
                m_scheduler.updateClock(Scheduler.toFixedPoint(Math.max(cycles, 1)));
            }
            catch(X64PageTableWalker.PageFault pf) {

                CR.setCR2(pf.getLinearAddress());
                throw pf;
            }
            catch(UnsupportedOperationException ex) {

                m_isHaltedByUnsupportedOpcode = true;
                System.err.printf("[X64CPU] Halted on unsupported opcode: %s\n", ex.getMessage());
                break;
            }
        }
    }

    private int executeSingleInstruction() {

        if(!CR.isLongModeActive())
            return executeLegacyInstruction();

        int rex = 0;
        int opcode = fetchU8();

        if((opcode & 0xF0) == 0x40) {
            rex = opcode;
            opcode = fetchU8();
        }

        switch(opcode) {

            case 0x90:
                return 1; // NOP

            case 0x0F:
                return execute0F(rex);

            default:
                throw unsupportedOpcode(opcode, rex, -1);
        }
    }

    private static final int SEG_ES = 0;
    private static final int SEG_CS = 1;
    private static final int SEG_SS = 2;
    private static final int SEG_DS = 3;
    private static final int SEG_FS = 4;
    private static final int SEG_GS = 5;

    private static final class PrefixState {

        boolean rep;
        boolean repnz;
        boolean operand32;
        boolean address32;
        int segmentOverride = -1;
    }

    private static final class OperandRef {

        boolean register;
        int registerIndex;
        long linearAddress;
        int offsetValue;
    }

    private int executeLegacyInstruction() {

        PrefixState prefix = new PrefixState();

        int opcode = fetchU8();
        decodePrefixes:
        while(true) {

            switch(opcode) {

                case 0xF3:
                    prefix.rep = true;
                    opcode = fetchU8();
                    break;

                case 0xF2:
                    prefix.repnz = true;
                    opcode = fetchU8();
                    break;

                case 0x66:
                    prefix.operand32 = !prefix.operand32;
                    opcode = fetchU8();
                    break;

                case 0x67:
                    prefix.address32 = !prefix.address32;
                    opcode = fetchU8();
                    break;

                case 0x26:
                    prefix.segmentOverride = SEG_ES;
                    opcode = fetchU8();
                    break;

                case 0x2E:
                    prefix.segmentOverride = SEG_CS;
                    opcode = fetchU8();
                    break;

                case 0x36:
                    prefix.segmentOverride = SEG_SS;
                    opcode = fetchU8();
                    break;

                case 0x3E:
                    prefix.segmentOverride = SEG_DS;
                    opcode = fetchU8();
                    break;

                case 0x64:
                    prefix.segmentOverride = SEG_FS;
                    opcode = fetchU8();
                    break;

                case 0x65:
                    prefix.segmentOverride = SEG_GS;
                    opcode = fetchU8();
                    break;

                default:
                    break decodePrefixes;
            }
        }

        switch(opcode) {

            case 0x90: // NOP
                return 1;

            case 0x91:
            case 0x92:
            case 0x93:
            case 0x94:
            case 0x95:
            case 0x96:
            case 0x97:
                executeXchgAccWithReg(prefix, opcode & 0x07);
                return 3;

            case 0x0F:
                return executeLegacy0F(prefix);

            case 0xEA: { // JMP ptr16:16
                int offset = fetchU16();
                int segment = fetchU16();
                setSegmentRegister(SEG_CS, segment);
                setIP16(offset);
                return 15;
            }

            case 0xE9: { // JMP rel16/rel32
                if(prefix.operand32) {
                    int rel = fetchS32();
                    RIP.setValue(RIP.getValue() + rel);
                }
                else {
                    int rel = fetchS16();
                    setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 8;
            }

            case 0xEB: { // JMP rel8
                int rel = fetchS8();
                setIP16((getIP16() + rel) & 0xFFFF);
                return 7;
            }

            case 0xE8: { // CALL rel16/rel32
                if(prefix.operand32) {
                    int rel = fetchS32();
                    int nextEip = (int)RIP.getValue();
                    push32(nextEip);
                    RIP.setValue(RIP.getValue() + rel);
                }
                else {
                    int rel = fetchS16();
                    int nextIp = getIP16();
                    push16(nextIp);
                    setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 15;
            }

            case 0xE0: { // LOOPNE/LOOPNZ rel8
                int rel = fetchS8();
                int count = prefix.address32 ? getReg32(1) : getReg16(1);
                count--;
                if(prefix.address32)
                    setReg32(1, count);
                else
                    setReg16(1, count & 0xFFFF);

                if(count != 0 && !FLAGS.isSet(X64Flags.ZERO))
                    setIP16((getIP16() + rel) & 0xFFFF);
                return 5;
            }

            case 0xE1: { // LOOPE/LOOPZ rel8
                int rel = fetchS8();
                int count = prefix.address32 ? getReg32(1) : getReg16(1);
                count--;
                if(prefix.address32)
                    setReg32(1, count);
                else
                    setReg16(1, count & 0xFFFF);

                if(count != 0 && FLAGS.isSet(X64Flags.ZERO))
                    setIP16((getIP16() + rel) & 0xFFFF);
                return 5;
            }

            case 0xE2: { // LOOP rel8
                int rel = fetchS8();
                int count = prefix.address32 ? getReg32(1) : getReg16(1);
                count--;
                if(prefix.address32)
                    setReg32(1, count);
                else
                    setReg16(1, count & 0xFFFF);

                if(count != 0)
                    setIP16((getIP16() + rel) & 0xFFFF);
                return 5;
            }

            case 0xE3: { // JCXZ/JECXZ rel8
                int rel = fetchS8();
                int count = prefix.address32 ? getReg32(1) : getReg16(1);
                if(count == 0)
                    setIP16((getIP16() + rel) & 0xFFFF);
                return 5;
            }

            case 0xC3: // RET
                setIP16(pop16());
                return 10;

            case 0xC2: { // RET imm16
                int popBytes = fetchU16();
                if(prefix.operand32) {
                    RIP.setValue(Integer.toUnsignedLong(pop32()));
                    setReg32(4, getReg32(4) + popBytes);
                }
                else {
                    setIP16(pop16());
                    setReg16(4, (getReg16(4) + popBytes) & 0xFFFF);
                }
                return 10;
            }

            case 0xCB: // RETF
                if(prefix.operand32) {
                    int ip = pop32();
                    int cs = pop32();
                    setSegmentRegister(SEG_CS, cs);
                    RIP.setValue(Integer.toUnsignedLong(ip));
                }
                else {
                    int ip = pop16();
                    int cs = pop16();
                    setSegmentRegister(SEG_CS, cs);
                    setIP16(ip);
                }
                return 18;

            case 0xCA: { // RETF imm16
                int popBytes = fetchU16();
                if(prefix.operand32) {
                    int ip = pop32();
                    int cs = pop32();
                    setSegmentRegister(SEG_CS, cs);
                    RIP.setValue(Integer.toUnsignedLong(ip));
                    setReg32(4, getReg32(4) + popBytes);
                }
                else {
                    int ip = pop16();
                    int cs = pop16();
                    setSegmentRegister(SEG_CS, cs);
                    setIP16(ip);
                    setReg16(4, (getReg16(4) + popBytes) & 0xFFFF);
                }
                return 18;
            }

            case 0xC9: // LEAVE
                if(prefix.operand32) {
                    setReg32(4, getReg32(5));
                    setReg32(5, pop32());
                }
                else {
                    setReg16(4, getReg16(5));
                    setReg16(5, pop16());
                }
                return 4;

            case 0xC4:
                executeLoadFarPointer(prefix, SEG_ES);
                return 8;

            case 0xC5:
                executeLoadFarPointer(prefix, SEG_DS);
                return 8;

            case 0xFA: // CLI
                FLAGS.set(X64Flags.INTERRUPT, false);
                return 2;

            case 0xFB: // STI
                FLAGS.set(X64Flags.INTERRUPT, true);
                return 2;

            case 0xFC: // CLD
                FLAGS.set(X64Flags.DIRECTION, false);
                return 2;

            case 0xFD: // STD
                FLAGS.set(X64Flags.DIRECTION, true);
                return 2;

            case 0xF6:
                executeGrp3Rm8(prefix);
                return 4;

            case 0xF7:
                executeGrp3Rm16(prefix);
                return 4;

            case 0xF4: // HLT
                m_halted = true;
                return 2;

            case 0xCC: // INT3
                executeSoftwareInterrupt(prefix, 3);
                return 20;

            case 0xCD: // INT imm8
                executeSoftwareInterrupt(prefix, fetchU8());
                return 20;

            case 0xCE: // INTO
                if(FLAGS.isSet(X64Flags.OVERFLOW))
                    executeSoftwareInterrupt(prefix, 4);
                return 4;

            case 0xCF: // IRET/IRETD
                executeInterruptReturn(prefix);
                return 20;

            case 0xE4: { // IN AL, imm8
                int port = fetchU8();
                setReg8(0, m_ioMap.readIO8(port & 0xFFFF));
                return 8;
            }

            case 0xE5: { // IN AX/EAX, imm8
                int port = fetchU8() & 0xFFFF;
                if(prefix.operand32)
                    setReg32(0, m_ioMap.readIO32(port));
                else
                    setReg16(0, m_ioMap.readIO16(port));
                return 8;
            }

            case 0xE6: { // OUT imm8, AL
                int port = fetchU8();
                m_ioMap.writeIO8(port & 0xFFFF, getReg8(0));
                return 8;
            }

            case 0xE7: { // OUT imm8, AX/EAX
                int port = fetchU8() & 0xFFFF;
                if(prefix.operand32)
                    m_ioMap.writeIO32(port, getReg32(0));
                else
                    m_ioMap.writeIO16(port, getReg16(0));
                return 8;
            }

            case 0xEC: { // IN AL, DX
                int port = getReg16(2);
                setReg8(0, m_ioMap.readIO8(port));
                return 8;
            }

            case 0xED: { // IN AX/EAX, DX
                int port = getReg16(2);
                if(prefix.operand32)
                    setReg32(0, m_ioMap.readIO32(port));
                else
                    setReg16(0, m_ioMap.readIO16(port));
                return 8;
            }

            case 0xEE: { // OUT DX, AL
                int port = getReg16(2);
                m_ioMap.writeIO8(port, getReg8(0));
                return 8;
            }

            case 0xEF: { // OUT DX, AX/EAX
                int port = getReg16(2);
                if(prefix.operand32)
                    m_ioMap.writeIO32(port, getReg32(0));
                else
                    m_ioMap.writeIO16(port, getReg16(0));
                return 8;
            }

            case 0xA0: { // MOV AL, moffs8
                long linear = toLinearAddress(fetchOffset(prefix.address32), segmentForData(prefix));
                setReg8(0, readLinearU8(linear, false, false, false));
                return 10;
            }

            case 0xA1: { // MOV AX/EAX, moffs
                long linear = toLinearAddress(fetchOffset(prefix.address32), segmentForData(prefix));
                if(prefix.operand32)
                    setReg32(0, readLinearU32(linear, false, false, false));
                else
                    setReg16(0, readLinearU16(linear, false, false, false));
                return 10;
            }

            case 0xA2: { // MOV moffs8, AL
                long linear = toLinearAddress(fetchOffset(prefix.address32), segmentForData(prefix));
                writeLinearU8(linear, getReg8(0), false, false);
                return 10;
            }

            case 0xA3: { // MOV moffs, AX/EAX
                long linear = toLinearAddress(fetchOffset(prefix.address32), segmentForData(prefix));
                if(prefix.operand32)
                    writeLinearU32(linear, getReg32(0), false, false);
                else
                    writeLinearU16(linear, getReg16(0), false, false);
                return 10;
            }

            case 0xA4: // MOVSB
                executeMovs(prefix, 1);
                return 5;

            case 0xA5: // MOVSW/MOVSD
                executeMovs(prefix, prefix.operand32 ? 4 : 2);
                return 5;

            case 0xAC: // LODSB
                executeLods(prefix, 1);
                return 5;

            case 0xAD: // LODSW/LODSD
                executeLods(prefix, prefix.operand32 ? 4 : 2);
                return 5;

            case 0xA8: { // TEST AL, imm8
                int result = getReg8(0) & fetchU8();
                setLogicFlags(result, 8);
                return 4;
            }

            case 0xA9: { // TEST AX/EAX, imm
                if(prefix.operand32) {
                    int result = getReg32(0) & fetchU32();
                    setLogicFlags(result, 32);
                }
                else {
                    int result = getReg16(0) & fetchU16();
                    setLogicFlags(result, 16);
                }
                return 4;
            }

            case 0xAA: // STOSB
                executeStos(prefix, 1);
                return 5;

            case 0xAB: // STOSW/STOSD
                executeStos(prefix, prefix.operand32 ? 4 : 2);
                return 5;

            case 0xAE: // SCASB
                executeScas(prefix, 1);
                return 5;

            case 0xAF: // SCASW/SCASD
                executeScas(prefix, prefix.operand32 ? 4 : 2);
                return 5;

            case 0x00:
                executeAddRm8R8(prefix);
                return 3;

            case 0x01:
                executeAddRm16R16(prefix);
                return 3;

            case 0x02:
                executeAddR8Rm8(prefix);
                return 3;

            case 0x03:
                executeAddR16Rm16(prefix);
                return 3;

            case 0x04: {
                int left = getReg8(0);
                int right = fetchU8();
                int result = (left + right) & 0xFF;
                setReg8(0, result);
                setAddFlags(left, right, result, 8);
                return 4;
            }

            case 0x05: {
                if(prefix.operand32) {
                    int left = getReg32(0);
                    int right = fetchU32();
                    int result = left + right;
                    setReg32(0, result);
                    setAddFlags(left, right, result, 32);
                }
                else {
                    int left = getReg16(0);
                    int right = fetchU16();
                    int result = (left + right) & 0xFFFF;
                    setReg16(0, result);
                    setAddFlags(left, right, result, 16);
                }
                return 4;
            }

            case 0x28:
                executeSubRm8R8(prefix);
                return 3;

            case 0x29:
                executeSubRm16R16(prefix);
                return 3;

            case 0x2A:
                executeSubR8Rm8(prefix);
                return 3;

            case 0x2B:
                executeSubR16Rm16(prefix);
                return 3;

            case 0x20:
                executeAndRm8R8(prefix);
                return 3;

            case 0x21:
                executeAndRm16R16(prefix);
                return 3;

            case 0x22:
                executeAndR8Rm8(prefix);
                return 3;

            case 0x23:
                executeAndR16Rm16(prefix);
                return 3;

            case 0x08:
                executeOrRm8R8(prefix);
                return 3;

            case 0x09:
                executeOrRm16R16(prefix);
                return 3;

            case 0x0A:
                executeOrR8Rm8(prefix);
                return 3;

            case 0x0B:
                executeOrR16Rm16(prefix);
                return 3;

            case 0x30:
                executeXorRm8R8(prefix);
                return 3;

            case 0x31:
                executeXorRm16R16(prefix);
                return 3;

            case 0x32:
                executeXorR8Rm8(prefix);
                return 3;

            case 0x33:
                executeXorR16Rm16(prefix);
                return 3;

            case 0x38:
                executeCmpRm8R8(prefix);
                return 3;

            case 0x39:
                executeCmpRm16R16(prefix);
                return 3;

            case 0x3A:
                executeCmpR8Rm8(prefix);
                return 3;

            case 0x3B:
                executeCmpR16Rm16(prefix);
                return 3;

            case 0x84:
                executeTestRm8R8(prefix);
                return 3;

            case 0x85:
                executeTestRm16R16(prefix);
                return 3;

            case 0x86:
                executeXchgRm8R8(prefix);
                return 3;

            case 0x87:
                executeXchgRm16R16(prefix);
                return 3;

            case 0x24: { // AND AL, imm8
                int result = getReg8(0) & fetchU8();
                setReg8(0, result);
                setLogicFlags(result, 8);
                return 4;
            }

            case 0x25: { // AND AX/EAX, imm
                if(prefix.operand32) {
                    int result = getReg32(0) & fetchU32();
                    setReg32(0, result);
                    setLogicFlags(result, 32);
                }
                else {
                    int result = getReg16(0) & fetchU16();
                    setReg16(0, result);
                    setLogicFlags(result, 16);
                }
                return 4;
            }

            case 0x2C: { // SUB AL, imm8
                int src = fetchU8();
                int dest = getReg8(0);
                int result = (dest - src) & 0xFF;
                setReg8(0, result);
                setSubFlags(dest, src, result, 8);
                return 4;
            }

            case 0x2D: { // SUB AX/EAX, imm
                if(prefix.operand32) {
                    int src = fetchU32();
                    int dest = getReg32(0);
                    int result = dest - src;
                    setReg32(0, result);
                    setSubFlags(dest, src, result, 32);
                }
                else {
                    int src = fetchU16();
                    int dest = getReg16(0);
                    int result = (dest - src) & 0xFFFF;
                    setReg16(0, result);
                    setSubFlags(dest, src, result, 16);
                }
                return 4;
            }

            case 0x0C: { // OR AL, imm8
                int result = getReg8(0) | fetchU8();
                setReg8(0, result);
                setLogicFlags(result, 8);
                return 4;
            }

            case 0x0D: { // OR AX/EAX, imm
                if(prefix.operand32) {
                    int result = getReg32(0) | fetchU32();
                    setReg32(0, result);
                    setLogicFlags(result, 32);
                }
                else {
                    int result = getReg16(0) | fetchU16();
                    setReg16(0, result);
                    setLogicFlags(result, 16);
                }
                return 4;
            }

            case 0x34: { // XOR AL, imm8
                int result = getReg8(0) ^ fetchU8();
                setReg8(0, result);
                setLogicFlags(result, 8);
                return 4;
            }

            case 0x35: { // XOR AX/EAX, imm
                if(prefix.operand32) {
                    int result = getReg32(0) ^ fetchU32();
                    setReg32(0, result);
                    setLogicFlags(result, 32);
                }
                else {
                    int result = getReg16(0) ^ fetchU16();
                    setReg16(0, result);
                    setLogicFlags(result, 16);
                }
                return 4;
            }

            case 0x3C: {
                int imm = fetchU8();
                int dest = getReg8(0);
                int result = (dest - imm) & 0xFF;
                setSubFlags(dest, imm, result, 8);
                return 4;
            }

            case 0x3D: {
                if(prefix.operand32) {
                    int imm = fetchU32();
                    int dest = getReg32(0);
                    int result = dest - imm;
                    setSubFlags(dest, imm, result, 32);
                }
                else {
                    int imm = fetchU16();
                    int dest = getReg16(0);
                    int result = (dest - imm) & 0xFFFF;
                    setSubFlags(dest, imm, result, 16);
                }
                return 4;
            }

            case 0x70:
                executeShortConditionalJump(0x00);
                return 4;

            case 0x71:
                executeShortConditionalJump(0x01);
                return 4;

            case 0x73: // JAE/JNB
                {
                    int rel = fetchS8();
                    if(!FLAGS.isSet(X64Flags.CARRY))
                        setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 4;

            case 0x72: // JB/JC
                {
                    int rel = fetchS8();
                    if(FLAGS.isSet(X64Flags.CARRY))
                        setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 4;

            case 0x74: // JE/JZ
                {
                    int rel = fetchS8();
                    if(FLAGS.isSet(X64Flags.ZERO))
                        setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 4;

            case 0x75: // JNE/JNZ
                {
                    int rel = fetchS8();
                    if(!FLAGS.isSet(X64Flags.ZERO))
                        setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 4;

            case 0x76: // JBE
                {
                    int rel = fetchS8();
                    if(FLAGS.isSet(X64Flags.CARRY) || FLAGS.isSet(X64Flags.ZERO))
                        setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 4;

            case 0x77: // JA
                {
                    int rel = fetchS8();
                    if(!FLAGS.isSet(X64Flags.CARRY) && !FLAGS.isSet(X64Flags.ZERO))
                        setIP16((getIP16() + rel) & 0xFFFF);
                }
                return 4;

            case 0x78:
                executeShortConditionalJump(0x08);
                return 4;

            case 0x79:
                executeShortConditionalJump(0x09);
                return 4;

            case 0x7A:
                executeShortConditionalJump(0x0A);
                return 4;

            case 0x7B:
                executeShortConditionalJump(0x0B);
                return 4;

            case 0x7C:
                executeShortConditionalJump(0x0C);
                return 4;

            case 0x7D:
                executeShortConditionalJump(0x0D);
                return 4;

            case 0x7E:
                executeShortConditionalJump(0x0E);
                return 4;

            case 0x7F:
                executeShortConditionalJump(0x0F);
                return 4;

            case 0x68: // PUSH imm16/imm32
                if(prefix.operand32)
                    push32(fetchU32());
                else
                    push16(fetchU16());
                return 4;

            case 0x6A: { // PUSH imm8 (sign-extended)
                int imm = fetchS8();
                if(prefix.operand32)
                    push32(imm);
                else
                    push16(imm & 0xFFFF);
                return 4;
            }

            case 0x69:
                executeImulRmImm(prefix, prefix.operand32 ? 4 : 2);
                return 4;

            case 0x6B:
                executeImulRmImm(prefix, 1);
                return 4;

            case 0x80:
                executeGrp1Rm8Imm8(prefix);
                return 4;

            case 0x81:
                executeGrp1Rm16Imm(prefix, prefix.operand32 ? 4 : 2);
                return 4;

            case 0x83:
                executeGrp1Rm16Imm8(prefix);
                return 4;

            case 0xFE:
                executeGrp4Rm8(prefix);
                return 4;

            case 0xFF:
                executeGrp5(prefix);
                return 5;

            case 0x88:
                executeMovRm8R8(prefix);
                return 2;

            case 0x8A:
                executeMovR8Rm8(prefix);
                return 2;

            case 0x89:
                executeMovRm16R16(prefix);
                return 2;

            case 0x8B:
                executeMovR16Rm16(prefix);
                return 2;

            case 0x8C:
                executeMovRm16Sreg(prefix);
                return 2;

            case 0x8D:
                executeLea(prefix);
                return 2;

            case 0x8E:
                executeMovSregRm16(prefix);
                return 2;

            case 0xC0:
                executeGrp2Rm8Imm8(prefix);
                return 4;

            case 0xC1:
                executeGrp2Rm16Imm8(prefix);
                return 4;

            case 0xD0:
                executeGrp2Rm8Count(prefix, 1);
                return 4;

            case 0xD1:
                executeGrp2Rm16Count(prefix, 1);
                return 4;

            case 0xD2:
                executeGrp2Rm8Count(prefix, getReg8(1) & 0x1F);
                return 4;

            case 0xD3:
                executeGrp2Rm16Count(prefix, getReg8(1) & 0x1F);
                return 4;

            case 0xC6:
                executeMovRmImm(prefix, 1);
                return 4;

            case 0xC7:
                executeMovRmImm(prefix, prefix.operand32 ? 4 : 2);
                return 4;

            case 0x9C:
                push16((int)FLAGS.getValue());
                return 4;

            case 0x9D:
                FLAGS.setValue(pop16());
                return 4;

            case 0x98: // CBW/CWDE
                if(prefix.operand32)
                    setReg32(0, (short)getReg16(0));
                else
                    setReg16(0, (byte)getReg8(0));
                return 2;

            case 0x99: // CWD/CDQ
                if(prefix.operand32) {
                    int eax = getReg32(0);
                    setReg32(2, eax < 0 ? -1 : 0);
                }
                else {
                    int ax = getReg16(0);
                    setReg16(2, (ax & 0x8000) != 0 ? 0xFFFF : 0x0000);
                }
                return 2;

            case 0x06:
                push16(m_segES);
                return 3;

            case 0x0E:
                push16(m_segCS);
                return 3;

            case 0x16:
                push16(m_segSS);
                return 3;

            case 0x1E:
                push16(m_segDS);
                return 3;

            case 0x07:
                setSegmentRegister(SEG_ES, pop16());
                return 3;

            case 0x17:
                setSegmentRegister(SEG_SS, pop16());
                return 3;

            case 0x1F:
                setSegmentRegister(SEG_DS, pop16());
                return 3;

            case 0x60: // PUSHA/PUSHAD
                if(prefix.operand32) {
                    int originalEsp = getReg32(4);
                    push32(getReg32(0));
                    push32(getReg32(1));
                    push32(getReg32(2));
                    push32(getReg32(3));
                    push32(originalEsp);
                    push32(getReg32(5));
                    push32(getReg32(6));
                    push32(getReg32(7));
                }
                else {
                    int originalSp = getReg16(4);
                    push16(getReg16(0));
                    push16(getReg16(1));
                    push16(getReg16(2));
                    push16(getReg16(3));
                    push16(originalSp);
                    push16(getReg16(5));
                    push16(getReg16(6));
                    push16(getReg16(7));
                }
                return 8;

            case 0x61: // POPA/POPAD
                if(prefix.operand32) {
                    setReg32(7, pop32()); // EDI
                    setReg32(6, pop32()); // ESI
                    setReg32(5, pop32()); // EBP
                    pop32();              // skip ESP
                    setReg32(3, pop32()); // EBX
                    setReg32(2, pop32()); // EDX
                    setReg32(1, pop32()); // ECX
                    setReg32(0, pop32()); // EAX
                }
                else {
                    setReg16(7, pop16()); // DI
                    setReg16(6, pop16()); // SI
                    setReg16(5, pop16()); // BP
                    pop16();              // skip SP
                    setReg16(3, pop16()); // BX
                    setReg16(2, pop16()); // DX
                    setReg16(1, pop16()); // CX
                    setReg16(0, pop16()); // AX
                }
                return 8;

            default:
                if(opcode >= 0xB0 && opcode <= 0xB7) {
                    setReg8(opcode - 0xB0, fetchU8());
                    return 2;
                }

                if(opcode >= 0xB8 && opcode <= 0xBF) {
                    int reg = opcode - 0xB8;
                    if(prefix.operand32)
                        setReg32(reg, fetchU32());
                    else
                        setReg16(reg, fetchU16());
                    return 2;
                }

                if(opcode >= 0x50 && opcode <= 0x57) {
                    int reg = opcode - 0x50;
                    if(prefix.operand32)
                        push32(getReg32(reg));
                    else
                        push16(getReg16(reg));
                    return 3;
                }

                if(opcode >= 0x58 && opcode <= 0x5F) {
                    int reg = opcode - 0x58;
                    if(prefix.operand32)
                        setReg32(reg, pop32());
                    else
                        setReg16(reg, pop16());
                    return 3;
                }

                if(opcode >= 0x40 && opcode <= 0x47) {
                    int reg = opcode - 0x40;
                    int old = getReg16(reg);
                    int result = (old + 1) & 0xFFFF;
                    setReg16(reg, result);
                    setAddFlags(old, 1, result, 16);
                    return 2;
                }

                if(opcode >= 0x48 && opcode <= 0x4F) {
                    int reg = opcode - 0x48;
                    int old = getReg16(reg);
                    int result = (old - 1) & 0xFFFF;
                    setReg16(reg, result);
                    setSubFlags(old, 1, result, 16);
                    return 2;
                }

                throw unsupportedOpcode(opcode, 0, -1);
        }
    }

    private int executeLegacy0F(PrefixState prefix) {

        int opcode2 = fetchU8();
        if(opcode2 >= 0x80 && opcode2 <= 0x8F) {
            int rel = prefix.operand32 ? fetchS32() : fetchS16();
            if(isConditionTrue(opcode2 & 0x0F)) {
                if(prefix.operand32)
                    RIP.setValue(RIP.getValue() + rel);
                else
                    setIP16((getIP16() + rel) & 0xFFFF);
            }
            return 6;
        }

        switch(opcode2) {

            case 0x20:
                executeMovFromControl(0);
                return 4;

            case 0x22:
                executeMovToControl(0);
                return 4;

            case 0x30:
                executeWRMSR();
                return 16;

            case 0x32:
                executeRDMSR();
                return 16;

            case 0xA2:
                executeCPUID();
                return 12;

            default:
                throw unsupportedOpcode(0x0F, 0, opcode2);
        }
    }

    private void executeMovRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);
        writeOperand8(dst, getReg8(reg));
    }

    private void executeMovR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);
        setReg8(reg, readOperand8(src));
    }

    private void executeMovRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32)
            writeOperand32(dst, getReg32(reg));
        else
            writeOperand16(dst, getReg16(reg));
    }

    private void executeMovR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32)
            setReg32(reg, readOperand32(src));
        else
            setReg16(reg, readOperand16(src));
    }

    private void executeMovRm16Sreg(PrefixState prefix) {

        int modrm = fetchU8();
        int sreg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);
        writeOperand16(dst, getSegmentRegister(sreg));
    }

    private void executeMovSregRm16(PrefixState prefix) {

        int modrm = fetchU8();
        int sreg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);
        int value = readOperand16(src);
        setSegmentRegister(sreg, value);
    }

    private void executeLoadFarPointer(PrefixState prefix, int targetSegment) {

        int modrm = fetchU8();
        int mod = (modrm >>> 6) & 0x03;
        if(mod == 0x03)
            throw new UnsupportedOperationException("Far-pointer load requires a memory source operand");

        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);
        long ptrLinear = src.linearAddress;

        if(prefix.operand32) {
            int offset = readLinearU32(ptrLinear, false, false, false);
            int selector = readLinearU16(ptrLinear + 4, false, false, false);
            setReg32(reg, offset);
            setSegmentRegister(targetSegment, selector);
        }
        else {
            int offset = readLinearU16(ptrLinear, false, false, false);
            int selector = readLinearU16(ptrLinear + 2, false, false, false);
            setReg16(reg, offset);
            setSegmentRegister(targetSegment, selector);
        }
    }

    private void executeLea(PrefixState prefix) {

        int modrm = fetchU8();
        int mod = (modrm >>> 6) & 0x03;
        if(mod == 0x03)
            throw new UnsupportedOperationException("LEA requires a memory source operand");

        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32)
            setReg32(reg, src.offsetValue);
        else
            setReg16(reg, src.offsetValue & 0xFFFF);
    }

    private void executeMovRmImm(PrefixState prefix, int sizeBytes) {

        int modrm = fetchU8();
        int grp = (modrm >>> 3) & 0x07;
        if(grp != 0x00)
            throw new UnsupportedOperationException("MOV immediate group expects /0");

        OperandRef dst = decodeOperandRef(modrm, prefix);

        switch(sizeBytes) {

            case 1:
                writeOperand8(dst, fetchU8());
                break;

            case 2:
                writeOperand16(dst, fetchU16());
                break;

            case 4:
                writeOperand32(dst, fetchU32());
                break;

            default:
                throw new IllegalArgumentException("Unsupported move immediate size");
        }
    }

    private void executeXorRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int result = readOperand8(dst) ^ getReg8(reg);
        writeOperand8(dst, result);
        setLogicFlags(result, 8);
    }

    private void executeAddRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int dest = readOperand8(dst);
        int src = getReg8(reg);
        int result = (dest + src) & 0xFF;
        writeOperand8(dst, result);
        setAddFlags(dest, src, result, 8);
    }

    private void executeAddRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int dest = readOperand32(dst);
            int src = getReg32(reg);
            int result = dest + src;
            writeOperand32(dst, result);
            setAddFlags(dest, src, result, 32);
        }
        else {
            int dest = readOperand16(dst);
            int src = getReg16(reg);
            int result = (dest + src) & 0xFFFF;
            writeOperand16(dst, result);
            setAddFlags(dest, src, result, 16);
        }
    }

    private void executeAddR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int dest = getReg8(reg);
        int rhs = readOperand8(src);
        int result = (dest + rhs) & 0xFF;
        setReg8(reg, result);
        setAddFlags(dest, rhs, result, 8);
    }

    private void executeAddR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int dest = getReg32(reg);
            int rhs = readOperand32(src);
            int result = dest + rhs;
            setReg32(reg, result);
            setAddFlags(dest, rhs, result, 32);
        }
        else {
            int dest = getReg16(reg);
            int rhs = readOperand16(src);
            int result = (dest + rhs) & 0xFFFF;
            setReg16(reg, result);
            setAddFlags(dest, rhs, result, 16);
        }
    }

    private void executeSubRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int left = readOperand8(dst);
        int right = getReg8(reg);
        int result = (left - right) & 0xFF;
        writeOperand8(dst, result);
        setSubFlags(left, right, result, 8);
    }

    private void executeSubRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int left = readOperand32(dst);
            int right = getReg32(reg);
            int result = left - right;
            writeOperand32(dst, result);
            setSubFlags(left, right, result, 32);
        }
        else {
            int left = readOperand16(dst);
            int right = getReg16(reg);
            int result = (left - right) & 0xFFFF;
            writeOperand16(dst, result);
            setSubFlags(left, right, result, 16);
        }
    }

    private void executeSubR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int left = getReg8(reg);
        int right = readOperand8(src);
        int result = (left - right) & 0xFF;
        setReg8(reg, result);
        setSubFlags(left, right, result, 8);
    }

    private void executeSubR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int left = getReg32(reg);
            int right = readOperand32(src);
            int result = left - right;
            setReg32(reg, result);
            setSubFlags(left, right, result, 32);
        }
        else {
            int left = getReg16(reg);
            int right = readOperand16(src);
            int result = (left - right) & 0xFFFF;
            setReg16(reg, result);
            setSubFlags(left, right, result, 16);
        }
    }

    private void executeAndRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int result = readOperand8(dst) & getReg8(reg);
        writeOperand8(dst, result);
        setLogicFlags(result, 8);
    }

    private void executeAndRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = readOperand32(dst) & getReg32(reg);
            writeOperand32(dst, result);
            setLogicFlags(result, 32);
        }
        else {
            int result = readOperand16(dst) & getReg16(reg);
            writeOperand16(dst, result);
            setLogicFlags(result, 16);
        }
    }

    private void executeAndR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int result = getReg8(reg) & readOperand8(src);
        setReg8(reg, result);
        setLogicFlags(result, 8);
    }

    private void executeAndR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = getReg32(reg) & readOperand32(src);
            setReg32(reg, result);
            setLogicFlags(result, 32);
        }
        else {
            int result = getReg16(reg) & readOperand16(src);
            setReg16(reg, result);
            setLogicFlags(result, 16);
        }
    }

    private void executeOrRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int result = readOperand8(dst) | getReg8(reg);
        writeOperand8(dst, result);
        setLogicFlags(result, 8);
    }

    private void executeOrRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = readOperand32(dst) | getReg32(reg);
            writeOperand32(dst, result);
            setLogicFlags(result, 32);
        }
        else {
            int result = readOperand16(dst) | getReg16(reg);
            writeOperand16(dst, result);
            setLogicFlags(result, 16);
        }
    }

    private void executeOrR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int result = getReg8(reg) | readOperand8(src);
        setReg8(reg, result);
        setLogicFlags(result, 8);
    }

    private void executeOrR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = getReg32(reg) | readOperand32(src);
            setReg32(reg, result);
            setLogicFlags(result, 32);
        }
        else {
            int result = getReg16(reg) | readOperand16(src);
            setReg16(reg, result);
            setLogicFlags(result, 16);
        }
    }

    private void executeTestRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int result = readOperand8(src) & getReg8(reg);
        setLogicFlags(result, 8);
    }

    private void executeTestRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = readOperand32(src) & getReg32(reg);
            setLogicFlags(result, 32);
        }
        else {
            int result = readOperand16(src) & getReg16(reg);
            setLogicFlags(result, 16);
        }
    }

    private void executeXorRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = readOperand32(dst) ^ getReg32(reg);
            writeOperand32(dst, result);
            setLogicFlags(result, 32);
        }
        else {
            int result = readOperand16(dst) ^ getReg16(reg);
            writeOperand16(dst, result);
            setLogicFlags(result, 16);
        }
    }

    private void executeXorR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int result = getReg8(reg) ^ readOperand8(src);
        setReg8(reg, result);
        setLogicFlags(result, 8);
    }

    private void executeXorR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int result = getReg32(reg) ^ readOperand32(src);
            setReg32(reg, result);
            setLogicFlags(result, 32);
        }
        else {
            int result = getReg16(reg) ^ readOperand16(src);
            setReg16(reg, result);
            setLogicFlags(result, 16);
        }
    }

    private void executeXchgRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef operand = decodeOperandRef(modrm, prefix);

        int lhs = readOperand8(operand);
        int rhs = getReg8(reg);
        writeOperand8(operand, rhs);
        setReg8(reg, lhs);
    }

    private void executeXchgRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef operand = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int lhs = readOperand32(operand);
            int rhs = getReg32(reg);
            writeOperand32(operand, rhs);
            setReg32(reg, lhs);
        }
        else {
            int lhs = readOperand16(operand);
            int rhs = getReg16(reg);
            writeOperand16(operand, rhs);
            setReg16(reg, lhs);
        }
    }

    private void executeXchgAccWithReg(PrefixState prefix, int reg) {

        if(prefix.operand32) {
            int acc = getReg32(0);
            int rhs = getReg32(reg);
            setReg32(0, rhs);
            setReg32(reg, acc);
        }
        else {
            int acc = getReg16(0);
            int rhs = getReg16(reg);
            setReg16(0, rhs);
            setReg16(reg, acc);
        }
    }

    private void executeCmpRm8R8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int dest = readOperand8(dst);
        int src = getReg8(reg);
        int result = (dest - src) & 0xFF;
        setSubFlags(dest, src, result, 8);
    }

    private void executeCmpRm16R16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int dest = readOperand32(dst);
            int src = getReg32(reg);
            int result = dest - src;
            setSubFlags(dest, src, result, 32);
        }
        else {
            int dest = readOperand16(dst);
            int src = getReg16(reg);
            int result = (dest - src) & 0xFFFF;
            setSubFlags(dest, src, result, 16);
        }
    }

    private void executeCmpR8Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        int dest = getReg8(reg);
        int rhs = readOperand8(src);
        int result = (dest - rhs) & 0xFF;
        setSubFlags(dest, rhs, result, 8);
    }

    private void executeCmpR16Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int dest = getReg32(reg);
            int rhs = readOperand32(src);
            int result = dest - rhs;
            setSubFlags(dest, rhs, result, 32);
        }
        else {
            int dest = getReg16(reg);
            int rhs = readOperand16(src);
            int result = (dest - rhs) & 0xFFFF;
            setSubFlags(dest, rhs, result, 16);
        }
    }

    private void executeGrp1Rm8Imm8(PrefixState prefix) {

        int modrm = fetchU8();
        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);
        int imm = fetchU8();
        int oldVal = readOperand8(dst);

        switch(group) {

            case 0x00: { // ADD
                int result = (oldVal + imm) & 0xFF;
                writeOperand8(dst, result);
                setAddFlags(oldVal, imm, result, 8);
                break;
            }

            case 0x01: { // OR
                int result = (oldVal | imm) & 0xFF;
                writeOperand8(dst, result);
                setLogicFlags(result, 8);
                break;
            }

            case 0x04: { // AND
                int result = (oldVal & imm) & 0xFF;
                writeOperand8(dst, result);
                setLogicFlags(result, 8);
                break;
            }

            case 0x05: { // SUB
                int result = (oldVal - imm) & 0xFF;
                writeOperand8(dst, result);
                setSubFlags(oldVal, imm, result, 8);
                break;
            }

            case 0x06: { // XOR
                int result = (oldVal ^ imm) & 0xFF;
                writeOperand8(dst, result);
                setLogicFlags(result, 8);
                break;
            }

            case 0x07: { // CMP
                int result = (oldVal - imm) & 0xFF;
                setSubFlags(oldVal, imm, result, 8);
                break;
            }

            default:
                throw new UnsupportedOperationException("Unsupported group-1 byte operation: /" + group);
        }
    }

    private void executeGrp1Rm16Imm(PrefixState prefix, int immSizeBytes) {

        int modrm = fetchU8();
        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(immSizeBytes == 4 || prefix.operand32) {
            int imm = immSizeBytes == 4 ? fetchU32() : fetchU16();
            int oldVal = readOperand32(dst);

            switch(group) {

                case 0x00: {
                    int result = oldVal + imm;
                    writeOperand32(dst, result);
                    setAddFlags(oldVal, imm, result, 32);
                    break;
                }

                case 0x01: {
                    int result = oldVal | imm;
                    writeOperand32(dst, result);
                    setLogicFlags(result, 32);
                    break;
                }

                case 0x04: {
                    int result = oldVal & imm;
                    writeOperand32(dst, result);
                    setLogicFlags(result, 32);
                    break;
                }

                case 0x05: {
                    int result = oldVal - imm;
                    writeOperand32(dst, result);
                    setSubFlags(oldVal, imm, result, 32);
                    break;
                }

                case 0x06: {
                    int result = oldVal ^ imm;
                    writeOperand32(dst, result);
                    setLogicFlags(result, 32);
                    break;
                }

                case 0x07: {
                    int result = oldVal - imm;
                    setSubFlags(oldVal, imm, result, 32);
                    break;
                }

                default:
                    throw new UnsupportedOperationException("Unsupported group-1 dword operation: /" + group);
            }
            return;
        }

        int imm = fetchU16();
        int oldVal = readOperand16(dst);

        switch(group) {

            case 0x00: {
                int result = (oldVal + imm) & 0xFFFF;
                writeOperand16(dst, result);
                setAddFlags(oldVal, imm, result, 16);
                break;
            }

            case 0x01: {
                int result = (oldVal | imm) & 0xFFFF;
                writeOperand16(dst, result);
                setLogicFlags(result, 16);
                break;
            }

            case 0x04: {
                int result = (oldVal & imm) & 0xFFFF;
                writeOperand16(dst, result);
                setLogicFlags(result, 16);
                break;
            }

            case 0x05: {
                int result = (oldVal - imm) & 0xFFFF;
                writeOperand16(dst, result);
                setSubFlags(oldVal, imm, result, 16);
                break;
            }

            case 0x06: {
                int result = (oldVal ^ imm) & 0xFFFF;
                writeOperand16(dst, result);
                setLogicFlags(result, 16);
                break;
            }

            case 0x07: {
                int result = (oldVal - imm) & 0xFFFF;
                setSubFlags(oldVal, imm, result, 16);
                break;
            }

            default:
                throw new UnsupportedOperationException("Unsupported group-1 word operation: /" + group);
        }
    }

    private void executeGrp1Rm16Imm8(PrefixState prefix) {

        int modrm = fetchU8();
        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);
        int imm = fetchS8();

        if(prefix.operand32) {
            int oldVal = readOperand32(dst);
            int result;

            switch(group) {
                case 0x00:
                    result = oldVal + imm;
                    writeOperand32(dst, result);
                    setAddFlags(oldVal, imm, result, 32);
                    break;

                case 0x01:
                    result = oldVal | imm;
                    writeOperand32(dst, result);
                    setLogicFlags(result, 32);
                    break;

                case 0x04:
                    result = oldVal & imm;
                    writeOperand32(dst, result);
                    setLogicFlags(result, 32);
                    break;

                case 0x05:
                    result = oldVal - imm;
                    writeOperand32(dst, result);
                    setSubFlags(oldVal, imm, result, 32);
                    break;

                case 0x06:
                    result = oldVal ^ imm;
                    writeOperand32(dst, result);
                    setLogicFlags(result, 32);
                    break;

                case 0x07:
                    result = oldVal - imm;
                    setSubFlags(oldVal, imm, result, 32);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported group-1 dword imm8 operation: /" + group);
            }
            return;
        }

        int oldVal = readOperand16(dst);
        int result;

        switch(group) {
            case 0x00:
                result = (oldVal + imm) & 0xFFFF;
                writeOperand16(dst, result);
                setAddFlags(oldVal, imm, result, 16);
                break;

            case 0x01:
                result = (oldVal | imm) & 0xFFFF;
                writeOperand16(dst, result);
                setLogicFlags(result, 16);
                break;

            case 0x04:
                result = (oldVal & imm) & 0xFFFF;
                writeOperand16(dst, result);
                setLogicFlags(result, 16);
                break;

            case 0x05:
                result = (oldVal - imm) & 0xFFFF;
                writeOperand16(dst, result);
                setSubFlags(oldVal, imm, result, 16);
                break;

            case 0x06:
                result = (oldVal ^ imm) & 0xFFFF;
                writeOperand16(dst, result);
                setLogicFlags(result, 16);
                break;

            case 0x07:
                result = (oldVal - imm) & 0xFFFF;
                setSubFlags(oldVal, imm, result, 16);
                break;

            default:
                throw new UnsupportedOperationException("Unsupported group-1 word imm8 operation: /" + group);
        }
    }

    private void executeGrp2Rm16Imm8(PrefixState prefix) {

        int modrm = fetchU8();
        executeGrp2Rm16Count(prefix, modrm, fetchU8() & 0x1F);
    }

    private void executeGrp2Rm8Imm8(PrefixState prefix) {

        int modrm = fetchU8();
        executeGrp2Rm8Count(prefix, modrm, fetchU8() & 0x1F);
    }

    private void executeGrp2Rm16Count(PrefixState prefix, int count) {

        int modrm = fetchU8();
        executeGrp2Rm16Count(prefix, modrm, count);
    }

    private void executeGrp2Rm8Count(PrefixState prefix, int count) {

        int modrm = fetchU8();
        executeGrp2Rm8Count(prefix, modrm, count);
    }

    private void executeGrp2Rm16Count(PrefixState prefix, int modrm, int count) {

        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(count == 0)
            return;

        int bits = prefix.operand32 ? 32 : 16;
        long mask = bits == 32 ? 0xFFFFFFFFL : 0xFFFFL;
        long signBit = 1L << (bits - 1);
        long value = (prefix.operand32 ? readOperand32(dst) : readOperand16(dst)) & mask;
        long original = value;
        boolean carry = false;

        switch(group) {

            case 0x04: // SHL/SAL
                for(int i = 0; i < count; i++) {
                    carry = (value & signBit) != 0;
                    value = (value << 1) & mask;
                }
                break;

            case 0x05: // SHR
                for(int i = 0; i < count; i++) {
                    carry = (value & 0x01L) != 0;
                    value >>>= 1;
                }
                break;

            case 0x07: // SAR
                for(int i = 0; i < count; i++) {
                    carry = (value & 0x01L) != 0;
                    value = (value >>> 1) | (value & signBit);
                }
                break;

            default:
                throw new UnsupportedOperationException("Unsupported group-2 operation: /" + group);
        }

        int result = (int)(value & mask);
        if(prefix.operand32)
            writeOperand32(dst, result);
        else
            writeOperand16(dst, result);

        FLAGS.set(X64Flags.CARRY, carry);
        FLAGS.set(X64Flags.ZERO, (value & mask) == 0);
        FLAGS.set(X64Flags.SIGN, (value & signBit) != 0);
        FLAGS.set(X64Flags.PARITY, isEvenParity(result));

        if(count == 1) {
            switch(group) {
                case 0x04:
                    FLAGS.set(X64Flags.OVERFLOW, ((value & signBit) != 0) ^ carry);
                    break;
                case 0x05:
                    FLAGS.set(X64Flags.OVERFLOW, (original & signBit) != 0);
                    break;
                case 0x07:
                    FLAGS.set(X64Flags.OVERFLOW, false);
                    break;
                default:
                    break;
            }
        }
        else {
            FLAGS.set(X64Flags.OVERFLOW, false);
        }
    }

    private void executeGrp2Rm8Count(PrefixState prefix, int modrm, int count) {

        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        if(count == 0)
            return;

        int value = readOperand8(dst) & 0xFF;
        int original = value;
        boolean carry = false;

        switch(group) {

            case 0x04: // SHL/SAL
                for(int i = 0; i < count; i++) {
                    carry = (value & 0x80) != 0;
                    value = (value << 1) & 0xFF;
                }
                break;

            case 0x05: // SHR
                for(int i = 0; i < count; i++) {
                    carry = (value & 0x01) != 0;
                    value >>>= 1;
                }
                break;

            case 0x07: // SAR
                for(int i = 0; i < count; i++) {
                    carry = (value & 0x01) != 0;
                    value = (value >>> 1) | (value & 0x80);
                }
                break;

            default:
                throw new UnsupportedOperationException("Unsupported group-2 byte operation: /" + group);
        }

        value &= 0xFF;
        writeOperand8(dst, value);

        FLAGS.set(X64Flags.CARRY, carry);
        FLAGS.set(X64Flags.ZERO, value == 0);
        FLAGS.set(X64Flags.SIGN, (value & 0x80) != 0);
        FLAGS.set(X64Flags.PARITY, isEvenParity(value));

        if(count == 1) {
            switch(group) {
                case 0x04:
                    FLAGS.set(X64Flags.OVERFLOW, ((value & 0x80) != 0) ^ carry);
                    break;
                case 0x05:
                    FLAGS.set(X64Flags.OVERFLOW, (original & 0x80) != 0);
                    break;
                case 0x07:
                    FLAGS.set(X64Flags.OVERFLOW, false);
                    break;
                default:
                    break;
            }
        }
        else {
            FLAGS.set(X64Flags.OVERFLOW, false);
        }
    }

    private void executeGrp5(PrefixState prefix) {

        int modrm = fetchU8();
        int mod = (modrm >>> 6) & 0x03;
        int group = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        switch(group) {

            case 0x00: // INC r/m16/32
                if(prefix.operand32) {
                    int old = readOperand32(src);
                    int result = old + 1;
                    writeOperand32(src, result);
                    setAddFlags(old, 1, result, 32);
                }
                else {
                    int old = readOperand16(src);
                    int result = (old + 1) & 0xFFFF;
                    writeOperand16(src, result);
                    setAddFlags(old, 1, result, 16);
                }
                break;

            case 0x01: // DEC r/m16/32
                if(prefix.operand32) {
                    int old = readOperand32(src);
                    int result = old - 1;
                    writeOperand32(src, result);
                    setSubFlags(old, 1, result, 32);
                }
                else {
                    int old = readOperand16(src);
                    int result = (old - 1) & 0xFFFF;
                    writeOperand16(src, result);
                    setSubFlags(old, 1, result, 16);
                }
                break;

            case 0x02: // CALL near absolute r/m16/32
                if(prefix.operand32) {
                    int target = readOperand32(src);
                    push32((int)RIP.getValue());
                    RIP.setValue(Integer.toUnsignedLong(target));
                }
                else {
                    int target = readOperand16(src);
                    push16(getIP16());
                    setIP16(target);
                }
                break;

            case 0x03: // CALL far absolute m16:16/32
                if(mod == 0x03)
                    throw new UnsupportedOperationException("Far CALL requires a memory source operand");

                if(prefix.operand32) {
                    int targetOffset = readLinearU32(src.linearAddress, false, false, false);
                    int targetSegment = readLinearU16(src.linearAddress + 4, false, false, false);
                    push16(m_segCS);
                    push32((int)RIP.getValue());
                    setSegmentRegister(SEG_CS, targetSegment);
                    RIP.setValue(Integer.toUnsignedLong(targetOffset));
                }
                else {
                    int targetOffset = readLinearU16(src.linearAddress, false, false, false);
                    int targetSegment = readLinearU16(src.linearAddress + 2, false, false, false);
                    push16(m_segCS);
                    push16(getIP16());
                    setSegmentRegister(SEG_CS, targetSegment);
                    setIP16(targetOffset);
                }
                break;

            case 0x04: // JMP near absolute r/m16/32
                if(prefix.operand32)
                    RIP.setValue(Integer.toUnsignedLong(readOperand32(src)));
                else
                    setIP16(readOperand16(src));
                break;

            case 0x05: // JMP far absolute m16:16/32
                if(mod == 0x03)
                    throw new UnsupportedOperationException("Far JMP requires a memory source operand");

                if(prefix.operand32) {
                    int targetOffset = readLinearU32(src.linearAddress, false, false, false);
                    int targetSegment = readLinearU16(src.linearAddress + 4, false, false, false);
                    setSegmentRegister(SEG_CS, targetSegment);
                    RIP.setValue(Integer.toUnsignedLong(targetOffset));
                }
                else {
                    int targetOffset = readLinearU16(src.linearAddress, false, false, false);
                    int targetSegment = readLinearU16(src.linearAddress + 2, false, false, false);
                    setSegmentRegister(SEG_CS, targetSegment);
                    setIP16(targetOffset);
                }
                break;

            case 0x06: // PUSH r/m16/32
                if(prefix.operand32)
                    push32(readOperand32(src));
                else
                    push16(readOperand16(src));
                break;

            default:
                throw new UnsupportedOperationException("Unsupported group-5 operation: /" + group);
        }
    }

    private void executeGrp4Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        switch(group) {

            case 0x00: { // INC r/m8
                int old = readOperand8(dst);
                int result = (old + 1) & 0xFF;
                writeOperand8(dst, result);
                setAddFlags(old, 1, result, 8);
                break;
            }

            case 0x01: { // DEC r/m8
                int old = readOperand8(dst);
                int result = (old - 1) & 0xFF;
                writeOperand8(dst, result);
                setSubFlags(old, 1, result, 8);
                break;
            }

            default:
                throw new UnsupportedOperationException("Unsupported group-4 operation: /" + group);
        }
    }

    private void executeImulRmImm(PrefixState prefix, int immSizeBytes) {

        int modrm = fetchU8();
        int reg = (modrm >>> 3) & 0x07;
        OperandRef src = decodeOperandRef(modrm, prefix);

        if(prefix.operand32) {
            int lhs = readOperand32(src);
            int imm = immSizeBytes == 1 ? fetchS8() : fetchU32();
            long product = (long)lhs * (long)imm;
            int result = (int)product;
            setReg32(reg, result);

            boolean overflow = product != (long)result;
            FLAGS.set(X64Flags.CARRY, overflow);
            FLAGS.set(X64Flags.OVERFLOW, overflow);
        }
        else {
            int lhs = (short)readOperand16(src);
            int imm;
            if(immSizeBytes == 1)
                imm = fetchS8();
            else
                imm = (short)fetchU16();

            int product = lhs * imm;
            int result = product & 0xFFFF;
            setReg16(reg, result);

            boolean overflow = product < Short.MIN_VALUE || product > Short.MAX_VALUE;
            FLAGS.set(X64Flags.CARRY, overflow);
            FLAGS.set(X64Flags.OVERFLOW, overflow);
        }
    }

    private void executeGrp3Rm8(PrefixState prefix) {

        int modrm = fetchU8();
        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);
        int value = readOperand8(dst);

        switch(group) {

            case 0x00:
            case 0x01: { // TEST r/m8, imm8
                int imm = fetchU8();
                int result = value & imm;
                setLogicFlags(result, 8);
                break;
            }

            case 0x02: // NOT r/m8
                writeOperand8(dst, (~value) & 0xFF);
                break;

            case 0x03: { // NEG r/m8
                int result = (-value) & 0xFF;
                writeOperand8(dst, result);
                setSubFlags(0, value, result, 8);
                break;
            }

            case 0x04: { // MUL r/m8 (AX = AL * r/m8)
                int al = getReg8(0);
                int product = al * value;
                setReg16(0, product);
                boolean overflow = (product & 0xFF00) != 0;
                FLAGS.set(X64Flags.CARRY, overflow);
                FLAGS.set(X64Flags.OVERFLOW, overflow);
                break;
            }

            case 0x05: { // IMUL r/m8 (AX = AL * r/m8, signed)
                int al = (byte)getReg8(0);
                int src = (byte)value;
                int product = al * src;
                setReg16(0, product);
                boolean overflow = product < Byte.MIN_VALUE || product > Byte.MAX_VALUE;
                FLAGS.set(X64Flags.CARRY, overflow);
                FLAGS.set(X64Flags.OVERFLOW, overflow);
                break;
            }

            case 0x06: { // DIV r/m8 (AX / r/m8)
                int divisor = value;
                if(divisor == 0)
                    throw new UnsupportedOperationException("Divide error: DIV r/m8 by zero");

                int dividend = getReg16(0);
                int quotient = dividend / divisor;
                int remainder = dividend % divisor;
                if((quotient & ~0xFF) != 0)
                    throw new UnsupportedOperationException("Divide error: DIV r/m8 quotient overflow");

                setReg8(0, quotient);
                setReg8(4, remainder);
                break;
            }

            case 0x07: { // IDIV r/m8 (AX / r/m8, signed)
                int divisor = (byte)value;
                if(divisor == 0)
                    throw new UnsupportedOperationException("Divide error: IDIV r/m8 by zero");

                int dividend = (short)getReg16(0);
                int quotient = dividend / divisor;
                int remainder = dividend % divisor;
                if(quotient < Byte.MIN_VALUE || quotient > Byte.MAX_VALUE)
                    throw new UnsupportedOperationException("Divide error: IDIV r/m8 quotient overflow");

                setReg8(0, quotient);
                setReg8(4, remainder);
                break;
            }

            default:
                throw new UnsupportedOperationException("Unsupported group-3 byte operation: /" + group);
        }
    }

    private void executeGrp3Rm16(PrefixState prefix) {

        int modrm = fetchU8();
        int group = (modrm >>> 3) & 0x07;
        OperandRef dst = decodeOperandRef(modrm, prefix);

        int bits = prefix.operand32 ? 32 : 16;
        int value = prefix.operand32 ? readOperand32(dst) : readOperand16(dst);

        switch(group) {

            case 0x00:
            case 0x01: { // TEST r/m16/32, imm
                int imm = prefix.operand32 ? fetchU32() : fetchU16();
                int result = value & imm;
                setLogicFlags(result, bits);
                break;
            }

            case 0x02: // NOT r/m16/32
                if(prefix.operand32)
                    writeOperand32(dst, ~value);
                else
                    writeOperand16(dst, (~value) & 0xFFFF);
                break;

            case 0x03: { // NEG r/m16/32
                if(prefix.operand32) {
                    int result = -value;
                    writeOperand32(dst, result);
                    setSubFlags(0, value, result, 32);
                }
                else {
                    int result = (-value) & 0xFFFF;
                    writeOperand16(dst, result);
                    setSubFlags(0, value, result, 16);
                }
                break;
            }

            case 0x04: { // MUL r/m16/32 (unsigned)
                if(prefix.operand32) {
                    long eax = Integer.toUnsignedLong(getReg32(0));
                    long src = Integer.toUnsignedLong(value);
                    long product = eax * src;
                    setReg32(0, (int)product);
                    setReg32(2, (int)(product >>> 32));
                    boolean overflow = (product >>> 32) != 0;
                    FLAGS.set(X64Flags.CARRY, overflow);
                    FLAGS.set(X64Flags.OVERFLOW, overflow);
                }
                else {
                    int ax = getReg16(0);
                    int src = value & 0xFFFF;
                    int product = ax * src;
                    setReg16(0, product);
                    setReg16(2, product >>> 16);
                    boolean overflow = (product & 0xFFFF0000) != 0;
                    FLAGS.set(X64Flags.CARRY, overflow);
                    FLAGS.set(X64Flags.OVERFLOW, overflow);
                }
                break;
            }

            case 0x05: { // IMUL r/m16/32 (signed)
                if(prefix.operand32) {
                    long eax = getReg32(0);
                    long src = value;
                    long product = eax * src;
                    setReg32(0, (int)product);
                    setReg32(2, (int)(product >>> 32));
                    boolean overflow = product != (long)(int)product;
                    FLAGS.set(X64Flags.CARRY, overflow);
                    FLAGS.set(X64Flags.OVERFLOW, overflow);
                }
                else {
                    int ax = (short)getReg16(0);
                    int src = (short)(value & 0xFFFF);
                    int product = ax * src;
                    setReg16(0, product);
                    setReg16(2, product >>> 16);
                    boolean overflow = product < Short.MIN_VALUE || product > Short.MAX_VALUE;
                    FLAGS.set(X64Flags.CARRY, overflow);
                    FLAGS.set(X64Flags.OVERFLOW, overflow);
                }
                break;
            }

            case 0x06: { // DIV r/m16/32 (unsigned)
                if(prefix.operand32) {
                    long divisor = Integer.toUnsignedLong(value);
                    if(divisor == 0)
                        throw new UnsupportedOperationException("Divide error: DIV r/m32 by zero");

                    long dividend = (Integer.toUnsignedLong(getReg32(2)) << 32) | Integer.toUnsignedLong(getReg32(0));
                    long quotient = Long.divideUnsigned(dividend, divisor);
                    long remainder = Long.remainderUnsigned(dividend, divisor);
                    if(Long.compareUnsigned(quotient, 0xFFFFFFFFL) > 0)
                        throw new UnsupportedOperationException("Divide error: DIV r/m32 quotient overflow");

                    setReg32(0, (int)quotient);
                    setReg32(2, (int)remainder);
                }
                else {
                    int divisor = value & 0xFFFF;
                    if(divisor == 0)
                        throw new UnsupportedOperationException("Divide error: DIV r/m16 by zero");

                    int dividend = ((getReg16(2) & 0xFFFF) << 16) | (getReg16(0) & 0xFFFF);
                    int quotient = Integer.divideUnsigned(dividend, divisor);
                    int remainder = Integer.remainderUnsigned(dividend, divisor);
                    if((quotient & ~0xFFFF) != 0)
                        throw new UnsupportedOperationException("Divide error: DIV r/m16 quotient overflow");

                    setReg16(0, quotient);
                    setReg16(2, remainder);
                }
                break;
            }

            case 0x07: { // IDIV r/m16/32 (signed)
                if(prefix.operand32) {
                    int divisor = value;
                    if(divisor == 0)
                        throw new UnsupportedOperationException("Divide error: IDIV r/m32 by zero");

                    long dividend = (((long)getReg32(2)) << 32) | Integer.toUnsignedLong(getReg32(0));
                    long quotient = dividend / divisor;
                    long remainder = dividend % divisor;
                    if(quotient < Integer.MIN_VALUE || quotient > Integer.MAX_VALUE)
                        throw new UnsupportedOperationException("Divide error: IDIV r/m32 quotient overflow");

                    setReg32(0, (int)quotient);
                    setReg32(2, (int)remainder);
                }
                else {
                    int divisor = (short)(value & 0xFFFF);
                    if(divisor == 0)
                        throw new UnsupportedOperationException("Divide error: IDIV r/m16 by zero");

                    int dividend = ((short)getReg16(2) << 16) | (getReg16(0) & 0xFFFF);
                    int quotient = dividend / divisor;
                    int remainder = dividend % divisor;
                    if(quotient < Short.MIN_VALUE || quotient > Short.MAX_VALUE)
                        throw new UnsupportedOperationException("Divide error: IDIV r/m16 quotient overflow");

                    setReg16(0, quotient);
                    setReg16(2, remainder);
                }
                break;
            }

            default:
                throw new UnsupportedOperationException("Unsupported group-3 word/dword operation: /" + group);
        }
    }

    private void executeStos(PrefixState prefix, int sizeBytes) {

        int count = prefix.rep ? (prefix.address32 ? getReg32(1) : getReg16(1)) : 1;
        if(count < 0)
            count = 0;

        while(count-- > 0) {
            long linear = toLinearAddress(prefix.address32 ? getReg32(7) : getReg16(7), SEG_ES);

            switch(sizeBytes) {

                case 1:
                    writeLinearU8(linear, getReg8(0), false, false);
                    break;

                case 2:
                    writeLinearU16(linear, getReg16(0), false, false);
                    break;

                case 4:
                    writeLinearU32(linear, getReg32(0), false, false);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported STOS width");
            }

            int delta = FLAGS.isSet(X64Flags.DIRECTION) ? -sizeBytes : sizeBytes;
            if(prefix.address32)
                setReg32(7, getReg32(7) + delta);
            else
                setReg16(7, (getReg16(7) + delta) & 0xFFFF);
        }

        if(prefix.rep) {
            if(prefix.address32)
                setReg32(1, 0);
            else
                setReg16(1, 0);
        }
    }

    private void executeMovs(PrefixState prefix, int sizeBytes) {

        int count = (prefix.rep || prefix.repnz) ? (prefix.address32 ? getReg32(1) : getReg16(1)) : 1;
        if(count < 0)
            count = 0;

        int delta = FLAGS.isSet(X64Flags.DIRECTION) ? -sizeBytes : sizeBytes;
        int srcSegment = segmentForData(prefix);

        while(count-- > 0) {
            long srcLinear = toLinearAddress(prefix.address32 ? getReg32(6) : getReg16(6), srcSegment);
            long dstLinear = toLinearAddress(prefix.address32 ? getReg32(7) : getReg16(7), SEG_ES);

            switch(sizeBytes) {

                case 1:
                    writeLinearU8(dstLinear, readLinearU8(srcLinear, false, false, false), false, false);
                    break;

                case 2:
                    writeLinearU16(dstLinear, readLinearU16(srcLinear, false, false, false), false, false);
                    break;

                case 4:
                    writeLinearU32(dstLinear, readLinearU32(srcLinear, false, false, false), false, false);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported MOVS width");
            }

            if(prefix.address32) {
                setReg32(6, getReg32(6) + delta);
                setReg32(7, getReg32(7) + delta);
            }
            else {
                setReg16(6, (getReg16(6) + delta) & 0xFFFF);
                setReg16(7, (getReg16(7) + delta) & 0xFFFF);
            }
        }

        if(prefix.rep || prefix.repnz) {
            if(prefix.address32)
                setReg32(1, 0);
            else
                setReg16(1, 0);
        }
    }

    private void executeLods(PrefixState prefix, int sizeBytes) {

        int count = (prefix.rep || prefix.repnz) ? (prefix.address32 ? getReg32(1) : getReg16(1)) : 1;
        if(count < 0)
            count = 0;

        int delta = FLAGS.isSet(X64Flags.DIRECTION) ? -sizeBytes : sizeBytes;
        int srcSegment = segmentForData(prefix);

        while(count-- > 0) {
            long srcLinear = toLinearAddress(prefix.address32 ? getReg32(6) : getReg16(6), srcSegment);

            switch(sizeBytes) {

                case 1:
                    setReg8(0, readLinearU8(srcLinear, false, false, false));
                    break;

                case 2:
                    setReg16(0, readLinearU16(srcLinear, false, false, false));
                    break;

                case 4:
                    setReg32(0, readLinearU32(srcLinear, false, false, false));
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported LODS width");
            }

            if(prefix.address32)
                setReg32(6, getReg32(6) + delta);
            else
                setReg16(6, (getReg16(6) + delta) & 0xFFFF);
        }

        if(prefix.rep || prefix.repnz) {
            if(prefix.address32)
                setReg32(1, 0);
            else
                setReg16(1, 0);
        }
    }

    private void executeScas(PrefixState prefix, int sizeBytes) {

        int step = FLAGS.isSet(X64Flags.DIRECTION) ? -sizeBytes : sizeBytes;

        if(!prefix.rep && !prefix.repnz) {
            long linear = toLinearAddress(prefix.address32 ? getReg32(7) : getReg16(7), SEG_ES);
            int mem = sizeBytes == 1
                ? readLinearU8(linear, false, false, false)
                : (sizeBytes == 2
                    ? readLinearU16(linear, false, false, false)
                    : readLinearU32(linear, false, false, false));

            if(sizeBytes == 1) {
                int acc = getReg8(0);
                int result = (acc - mem) & 0xFF;
                setSubFlags(acc, mem, result, 8);
            }
            else if(sizeBytes == 2) {
                int acc = getReg16(0);
                int result = (acc - mem) & 0xFFFF;
                setSubFlags(acc, mem, result, 16);
            }
            else {
                int acc = getReg32(0);
                int result = acc - mem;
                setSubFlags(acc, mem, result, 32);
            }

            if(prefix.address32)
                setReg32(7, getReg32(7) + step);
            else
                setReg16(7, (getReg16(7) + step) & 0xFFFF);
            return;
        }

        int counter = prefix.address32 ? getReg32(1) : getReg16(1);
        while(counter != 0) {
            long linear = toLinearAddress(prefix.address32 ? getReg32(7) : getReg16(7), SEG_ES);
            int mem = sizeBytes == 1
                ? readLinearU8(linear, false, false, false)
                : (sizeBytes == 2
                    ? readLinearU16(linear, false, false, false)
                    : readLinearU32(linear, false, false, false));

            if(sizeBytes == 1) {
                int acc = getReg8(0);
                int result = (acc - mem) & 0xFF;
                setSubFlags(acc, mem, result, 8);
            }
            else if(sizeBytes == 2) {
                int acc = getReg16(0);
                int result = (acc - mem) & 0xFFFF;
                setSubFlags(acc, mem, result, 16);
            }
            else {
                int acc = getReg32(0);
                int result = acc - mem;
                setSubFlags(acc, mem, result, 32);
            }

            if(prefix.address32)
                setReg32(7, getReg32(7) + step);
            else
                setReg16(7, (getReg16(7) + step) & 0xFFFF);

            counter--;
            if(prefix.rep && !FLAGS.isSet(X64Flags.ZERO))
                break;
            if(prefix.repnz && FLAGS.isSet(X64Flags.ZERO))
                break;
        }

        if(prefix.address32)
            setReg32(1, counter);
        else
            setReg16(1, counter & 0xFFFF);
    }

    private void executeShortConditionalJump(int conditionCode) {

        int rel = fetchS8();
        if(isConditionTrue(conditionCode))
            setIP16((getIP16() + rel) & 0xFFFF);
    }

    private void executeSoftwareInterrupt(PrefixState prefix, int vector) {

        int interruptVector = vector & 0xFF;
        int tableOffset = interruptVector << 2;
        int targetOffset = readLinearU16(tableOffset, false, false, true);
        int targetSegment = readLinearU16(tableOffset + 2, false, false, true);

        if(prefix.operand32) {
            push32((int)FLAGS.getValue());
            push32(m_segCS);
            push32((int)RIP.getValue());
            FLAGS.set(X64Flags.INTERRUPT, false);
            FLAGS.set(X64Flags.TRAP, false);
            setSegmentRegister(SEG_CS, targetSegment);
            RIP.setValue(Integer.toUnsignedLong(targetOffset));
        }
        else {
            push16((int)FLAGS.getValue());
            push16(m_segCS);
            push16(getIP16());
            FLAGS.set(X64Flags.INTERRUPT, false);
            FLAGS.set(X64Flags.TRAP, false);
            setSegmentRegister(SEG_CS, targetSegment);
            setIP16(targetOffset);
        }
    }

    private void executeInterruptReturn(PrefixState prefix) {

        if(prefix.operand32) {
            int ip = pop32();
            int cs = pop32();
            int flags = pop32();
            setSegmentRegister(SEG_CS, cs);
            RIP.setValue(Integer.toUnsignedLong(ip));
            FLAGS.setValue(Integer.toUnsignedLong(flags));
        }
        else {
            int ip = pop16();
            int cs = pop16();
            int flags = pop16();
            setSegmentRegister(SEG_CS, cs);
            setIP16(ip);
            FLAGS.setValue(flags);
        }
    }

    private OperandRef decodeOperandRef(int modrm, PrefixState prefix) {

        int mod = (modrm >>> 6) & 0x03;
        int rm = modrm & 0x07;

        OperandRef ref = new OperandRef();
        if(mod == 0x03) {
            ref.register = true;
            ref.registerIndex = rm;
            return ref;
        }

        if(prefix.address32)
            throw new UnsupportedOperationException("Address-size override (0x67) not implemented yet");

        int displacement = 0;
        boolean usesBp = false;

        switch(mod) {
            case 0x00:
                if(rm == 0x06) {
                    displacement = fetchU16();
                }
                else {
                    displacement = getBaseOffset16(rm);
                    usesBp = rm == 0x02 || rm == 0x03;
                }
                break;

            case 0x01:
                displacement = (getBaseOffset16(rm) + fetchS8()) & 0xFFFF;
                usesBp = rm == 0x02 || rm == 0x03 || rm == 0x06;
                break;

            case 0x02:
                displacement = (getBaseOffset16(rm) + fetchS16()) & 0xFFFF;
                usesBp = rm == 0x02 || rm == 0x03 || rm == 0x06;
                break;

            default:
                throw new IllegalStateException("Invalid MOD value");
        }

        int defaultSegment = usesBp ? SEG_SS : SEG_DS;
        int segment = prefix.segmentOverride >= 0 ? prefix.segmentOverride : defaultSegment;
        ref.register = false;
        ref.offsetValue = displacement & (prefix.address32 ? 0xFFFFFFFF : 0xFFFF);
        ref.linearAddress = toLinearAddress(displacement, segment);
        return ref;
    }

    private int getBaseOffset16(int rm) {

        switch(rm) {

            case 0x00:
                return (getReg16(3) + getReg16(6)) & 0xFFFF; // BX + SI

            case 0x01:
                return (getReg16(3) + getReg16(7)) & 0xFFFF; // BX + DI

            case 0x02:
                return (getReg16(5) + getReg16(6)) & 0xFFFF; // BP + SI

            case 0x03:
                return (getReg16(5) + getReg16(7)) & 0xFFFF; // BP + DI

            case 0x04:
                return getReg16(6); // SI

            case 0x05:
                return getReg16(7); // DI

            case 0x06:
                return getReg16(5); // BP

            case 0x07:
                return getReg16(3); // BX

            default:
                throw new IllegalArgumentException("Invalid rm value");
        }
    }

    private int readOperand8(OperandRef ref) {

        if(ref.register)
            return getReg8(ref.registerIndex);

        return readLinearU8(ref.linearAddress, false, false, false);
    }

    private int readOperand16(OperandRef ref) {

        if(ref.register)
            return getReg16(ref.registerIndex);

        return readLinearU16(ref.linearAddress, false, false, false);
    }

    private int readOperand32(OperandRef ref) {

        if(ref.register)
            return getReg32(ref.registerIndex);

        return readLinearU32(ref.linearAddress, false, false, false);
    }

    private void writeOperand8(OperandRef ref, int value) {

        if(ref.register)
            setReg8(ref.registerIndex, value);
        else
            writeLinearU8(ref.linearAddress, value, false, false);
    }

    private void writeOperand16(OperandRef ref, int value) {

        if(ref.register)
            setReg16(ref.registerIndex, value);
        else
            writeLinearU16(ref.linearAddress, value, false, false);
    }

    private void writeOperand32(OperandRef ref, int value) {

        if(ref.register)
            setReg32(ref.registerIndex, value);
        else
            writeLinearU32(ref.linearAddress, value, false, false);
    }

    private int segmentForData(PrefixState prefix) {

        return prefix.segmentOverride >= 0 ? prefix.segmentOverride : SEG_DS;
    }

    private int fetchOffset(boolean address32) {

        return address32 ? fetchU32() : fetchU16();
    }

    private long toLinearAddress(int offset, int segmentIndex) {

        long base = getSegmentBase(segmentIndex);
        long off = Integer.toUnsignedLong(offset);
        return (base + off) & 0xFFFFFFFFL;
    }

    private long getSegmentBase(int segmentIndex) {

        if(CR.isLongModeActive()) {
            switch(segmentIndex) {
                case SEG_FS:
                    return m_fsBase;
                case SEG_GS:
                    return m_gsBase;
                default:
                    return 0L;
            }
        }

        int selector = getSegmentRegister(segmentIndex);
        return Integer.toUnsignedLong((selector & 0xFFFF) << 4);
    }

    private int getSegmentRegister(int segmentIndex) {

        switch(segmentIndex) {
            case SEG_ES:
                return m_segES;
            case SEG_CS:
                return m_segCS;
            case SEG_SS:
                return m_segSS;
            case SEG_DS:
                return m_segDS;
            case SEG_FS:
                return m_segFS;
            case SEG_GS:
                return m_segGS;
            default:
                throw new IllegalArgumentException("Invalid segment index");
        }
    }

    private void setSegmentRegister(int segmentIndex, int selector) {

        int val = selector & 0xFFFF;
        switch(segmentIndex) {
            case SEG_ES:
                m_segES = val;
                break;
            case SEG_CS:
                m_segCS = val;
                break;
            case SEG_SS:
                m_segSS = val;
                break;
            case SEG_DS:
                m_segDS = val;
                break;
            case SEG_FS:
                m_segFS = val;
                break;
            case SEG_GS:
                m_segGS = val;
                break;
            default:
                throw new IllegalArgumentException("Invalid segment index");
        }
    }

    private int getIP16() {

        return (int)(RIP.getValue() & 0xFFFFL);
    }

    private void setIP16(int value) {

        RIP.setValue(value & 0xFFFFL);
    }

    private void push16(int value) {

        int sp = (getReg16(4) - 2) & 0xFFFF;
        setReg16(4, sp);
        long linear = toLinearAddress(sp, SEG_SS);
        writeLinearU16(linear, value, false, false);
    }

    private int pop16() {

        int sp = getReg16(4);
        long linear = toLinearAddress(sp, SEG_SS);
        int value = readLinearU16(linear, false, false, false);
        setReg16(4, (sp + 2) & 0xFFFF);
        return value;
    }

    private void push32(int value) {

        int esp = getReg32(4) - 4;
        setReg32(4, esp);
        long linear = toLinearAddress(esp, SEG_SS);
        writeLinearU32(linear, value, false, false);
    }

    private int pop32() {

        int esp = getReg32(4);
        long linear = toLinearAddress(esp, SEG_SS);
        int value = readLinearU32(linear, false, false, false);
        setReg32(4, esp + 4);
        return value;
    }

    private int getReg16(int index) {

        return (int)(getGpr(index).getValue() & 0xFFFFL);
    }

    private void setReg16(int index, int value) {

        Register64 reg = getGpr(index);
        long old = reg.getValue();
        reg.setValue((old & ~0xFFFFL) | (value & 0xFFFFL));
    }

    private int getReg32(int index) {

        return getGpr(index).getValue32();
    }

    private void setReg32(int index, int value) {

        getGpr(index).setValue32(value);
    }

    private int getReg8(int index) {

        switch(index & 0x07) {
            case 0:
                return (int)(RAX.getValue() & 0xFF);
            case 1:
                return (int)(RCX.getValue() & 0xFF);
            case 2:
                return (int)(RDX.getValue() & 0xFF);
            case 3:
                return (int)(RBX.getValue() & 0xFF);
            case 4:
                return (int)((RAX.getValue() >>> 8) & 0xFF);
            case 5:
                return (int)((RCX.getValue() >>> 8) & 0xFF);
            case 6:
                return (int)((RDX.getValue() >>> 8) & 0xFF);
            case 7:
                return (int)((RBX.getValue() >>> 8) & 0xFF);
            default:
                throw new IllegalArgumentException("Invalid 8-bit register index");
        }
    }

    private void setReg8(int index, int value) {

        int val = value & 0xFF;
        switch(index & 0x07) {
            case 0:
                RAX.setValue((RAX.getValue() & ~0xFFL) | val);
                break;
            case 1:
                RCX.setValue((RCX.getValue() & ~0xFFL) | val);
                break;
            case 2:
                RDX.setValue((RDX.getValue() & ~0xFFL) | val);
                break;
            case 3:
                RBX.setValue((RBX.getValue() & ~0xFFL) | val);
                break;
            case 4:
                RAX.setValue((RAX.getValue() & ~0xFF00L) | ((long)val << 8));
                break;
            case 5:
                RCX.setValue((RCX.getValue() & ~0xFF00L) | ((long)val << 8));
                break;
            case 6:
                RDX.setValue((RDX.getValue() & ~0xFF00L) | ((long)val << 8));
                break;
            case 7:
                RBX.setValue((RBX.getValue() & ~0xFF00L) | ((long)val << 8));
                break;
            default:
                throw new IllegalArgumentException("Invalid 8-bit register index");
        }
    }

    private void setLogicFlags(int result, int bits) {

        long mask = bits == 32 ? 0xFFFFFFFFL : ((1L << bits) - 1L);
        long res = result & mask;
        long signBit = 1L << (bits - 1);

        FLAGS.set(X64Flags.CARRY, false);
        FLAGS.set(X64Flags.OVERFLOW, false);
        FLAGS.set(X64Flags.AUX, false);
        FLAGS.set(X64Flags.ZERO, res == 0);
        FLAGS.set(X64Flags.SIGN, (res & signBit) != 0);
        FLAGS.set(X64Flags.PARITY, isEvenParity((int)res));
    }

    private void setAddFlags(int dest, int src, int result, int bits) {

        long mask = bits == 32 ? 0xFFFFFFFFL : ((1L << bits) - 1L);
        long signBit = 1L << (bits - 1);
        long d = dest & mask;
        long s = src & mask;
        long r = result & mask;

        FLAGS.set(X64Flags.CARRY, r < d);
        FLAGS.set(X64Flags.ZERO, r == 0);
        FLAGS.set(X64Flags.SIGN, (r & signBit) != 0);
        FLAGS.set(X64Flags.PARITY, isEvenParity((int)r));
        FLAGS.set(X64Flags.AUX, ((d ^ s ^ r) & 0x10) != 0);
        FLAGS.set(X64Flags.OVERFLOW, ((~(d ^ s) & (d ^ r)) & signBit) != 0);
    }

    private void setSubFlags(int dest, int src, int result, int bits) {

        long mask = bits == 32 ? 0xFFFFFFFFL : ((1L << bits) - 1L);
        long signBit = 1L << (bits - 1);
        long d = dest & mask;
        long s = src & mask;
        long r = result & mask;

        FLAGS.set(X64Flags.CARRY, d < s);
        FLAGS.set(X64Flags.ZERO, r == 0);
        FLAGS.set(X64Flags.SIGN, (r & signBit) != 0);
        FLAGS.set(X64Flags.PARITY, isEvenParity((int)r));
        FLAGS.set(X64Flags.AUX, ((d ^ s ^ r) & 0x10) != 0);
        FLAGS.set(X64Flags.OVERFLOW, (((d ^ s) & (d ^ r)) & signBit) != 0);
    }

    private boolean isEvenParity(int value) {

        return (Integer.bitCount(value & 0xFF) & 1) == 0;
    }

    private boolean isConditionTrue(int conditionCode) {

        boolean cf = FLAGS.isSet(X64Flags.CARRY);
        boolean zf = FLAGS.isSet(X64Flags.ZERO);
        boolean sf = FLAGS.isSet(X64Flags.SIGN);
        boolean of = FLAGS.isSet(X64Flags.OVERFLOW);
        boolean pf = FLAGS.isSet(X64Flags.PARITY);

        switch(conditionCode & 0x0F) {

            case 0x00: // O
                return of;

            case 0x01: // NO
                return !of;

            case 0x02: // B/NAE/C
                return cf;

            case 0x03: // NB/AE/NC
                return !cf;

            case 0x04: // Z/E
                return zf;

            case 0x05: // NZ/NE
                return !zf;

            case 0x06: // BE/NA
                return cf || zf;

            case 0x07: // A/NBE
                return !cf && !zf;

            case 0x08: // S
                return sf;

            case 0x09: // NS
                return !sf;

            case 0x0A: // P/PE
                return pf;

            case 0x0B: // NP/PO
                return !pf;

            case 0x0C: // L/NGE
                return sf != of;

            case 0x0D: // GE/NL
                return sf == of;

            case 0x0E: // LE/NG
                return zf || (sf != of);

            case 0x0F: // G/NLE
                return !zf && (sf == of);

            default:
                throw new IllegalStateException("Invalid condition code");
        }
    }

    private int execute0F(int rex) {

        int opcode = fetchU8();

        switch(opcode) {

            case 0x20:
                executeMovFromControl(rex);
                return 4;

            case 0x22:
                executeMovToControl(rex);
                return 4;

            case 0x30:
                executeWRMSR();
                return 16;

            case 0x32:
                executeRDMSR();
                return 16;

            case 0xA2:
                executeCPUID();
                return 12;

            default:
                throw unsupportedOpcode(0x0F, rex, opcode);
        }
    }

    private void executeMovFromControl(int rex) {

        int modrm = fetchU8();
        int mod = (modrm >>> 6) & 0x03;
        if(mod != 0x03)
            throw new UnsupportedOperationException("MOV from CR currently supports register destination only");

        int crIndex = ((modrm >>> 3) & 0x07) | (((rex >>> 2) & 0x01) << 3);
        int gprIndex = (modrm & 0x07) | ((rex & 0x01) << 3);

        writeGpr(gprIndex, readControlRegister(crIndex));
    }

    private void executeMovToControl(int rex) {

        int modrm = fetchU8();
        int mod = (modrm >>> 6) & 0x03;
        if(mod != 0x03)
            throw new UnsupportedOperationException("MOV to CR currently supports register source only");

        int crIndex = ((modrm >>> 3) & 0x07) | (((rex >>> 2) & 0x01) << 3);
        int gprIndex = (modrm & 0x07) | ((rex & 0x01) << 3);

        writeControlRegister(crIndex, readGpr(gprIndex));
    }

    private void executeRDMSR() {

        int index = RCX.getValue32();
        long value = readMSR(index);

        RAX.setValue32((int)value);
        RDX.setValue32((int)(value >>> 32));
    }

    private void executeWRMSR() {

        int index = RCX.getValue32();
        long value = Integer.toUnsignedLong(EAX()) |
                     (Integer.toUnsignedLong(EDX()) << 32);

        writeMSR(index, value);
    }

    private void executeCPUID() {

        int leaf = EAX();

        switch(leaf) {

            case 0x00000000:
                RAX.setValue32(0x00000001);
                RBX.setValue32(0x756E6547); // Genu
                RDX.setValue32(0x49656E69); // ineI
                RCX.setValue32(0x6C65746E); // ntel
                break;

            case 0x00000001:
                RAX.setValue32(0x00000663); // Generic family/model placeholder
                RBX.setValue32(0x00000000);
                // FPU, TSC, MSR, PAE, CMOV, FXSR
                RDX.setValue32((1 << 0) | (1 << 4) | (1 << 5) | (1 << 6) | (1 << 15) | (1 << 23) | (1 << 24));
                RCX.setValue32(0);
                break;

            case 0x80000000:
                RAX.setValue32(0x80000001);
                break;

            case 0x80000001:
                RAX.setValue32(0);
                RBX.setValue32(0);
                RCX.setValue32(0);
                // NX + Long mode support
                RDX.setValue32((1 << 20) | (1 << 29));
                break;

            default:
                RAX.setValue32(0);
                RBX.setValue32(0);
                RCX.setValue32(0);
                RDX.setValue32(0);
                break;
        }
    }

    private int fetchU8() {

        if(!CR.isProtectedModeEnabled()) {
            int ip = getIP16();
            long linear = toLinearAddress(ip, SEG_CS);
            int value = readLinearU8(linear, false, false, true);
            setIP16((ip + 1) & 0xFFFF);
            return value;
        }

        long rip = RIP.getValue();
        int value = readLinearU8(rip, false, false, true);
        RIP.setValue(rip + 1L);
        return value;
    }

    private int fetchU16() {

        return fetchU8() | (fetchU8() << 8);
    }

    private int fetchU32() {

        return fetchU16() | (fetchU16() << 16);
    }

    private int fetchS8() {

        return (byte)fetchU8();
    }

    private int fetchS16() {

        return (short)fetchU16();
    }

    private int fetchS32() {

        return fetchU32();
    }

    private int readLinearU8(long linearAddress,
                             boolean writeAccess,
                             boolean userAccess,
                             boolean executeAccess) {

        long physicalAddress = translateLinearAddress(
            linearAddress,
            writeAccess,
            userAccess,
            executeAccess
        );

        return readPhysicalU8(physicalAddress);
    }

    private int readLinearU16(long linearAddress,
                              boolean writeAccess,
                              boolean userAccess,
                              boolean executeAccess) {

        int b0 = readLinearU8(linearAddress, writeAccess, userAccess, executeAccess);
        int b1 = readLinearU8(linearAddress + 1, writeAccess, userAccess, executeAccess);
        return b0 | (b1 << 8);
    }

    private int readLinearU32(long linearAddress,
                              boolean writeAccess,
                              boolean userAccess,
                              boolean executeAccess) {

        int w0 = readLinearU16(linearAddress, writeAccess, userAccess, executeAccess);
        int w1 = readLinearU16(linearAddress + 2, writeAccess, userAccess, executeAccess);
        return w0 | (w1 << 16);
    }

    private void writeLinearU8(long linearAddress,
                               int data,
                               boolean userAccess,
                               boolean executeAccess) {

        long physicalAddress = translateLinearAddress(
            linearAddress,
            true,
            userAccess,
            executeAccess
        );

        writePhysicalU8(physicalAddress, data);
    }

    private void writeLinearU16(long linearAddress,
                                int data,
                                boolean userAccess,
                                boolean executeAccess) {

        writeLinearU8(linearAddress, data & 0xFF, userAccess, executeAccess);
        writeLinearU8(linearAddress + 1, (data >>> 8) & 0xFF, userAccess, executeAccess);
    }

    private void writeLinearU32(long linearAddress,
                                int data,
                                boolean userAccess,
                                boolean executeAccess) {

        writeLinearU16(linearAddress, data & 0xFFFF, userAccess, executeAccess);
        writeLinearU16(linearAddress + 2, (data >>> 16) & 0xFFFF, userAccess, executeAccess);
    }

    private long translateLinearAddress(long linearAddress,
                                        boolean writeAccess,
                                        boolean userAccess,
                                        boolean executeAccess) {

        if(CR.isLongModeActive()) {
            if(!isCanonicalAddress(linearAddress))
                throw new IllegalStateException(String.format("Non-canonical address %016X", linearAddress));

            if(CR.isPagingEnabled()) {
                X64PageTableWalker.TranslationResult result = m_pageWalker.translate(
                    CR.getCR3(),
                    linearAddress,
                    writeAccess,
                    userAccess,
                    executeAccess,
                    (CR.getEFER() & X64Control.EFER_NXE) != 0
                );
                return result.physicalAddress;
            }
        }

        return linearAddress;
    }

    private int readPhysicalU8(long physicalAddress) {

        int addr = toAddress32(physicalAddress);
        return m_memoryMap.readMEM8(addr);
    }

    private void writePhysicalU8(long physicalAddress, int data) {

        int addr = toAddress32(physicalAddress);
        m_memoryMap.writeMEM8(addr, data & 0xFF);
    }

    private long readPhysicalU64(long physicalAddress) {

        int addr = toAddress32(physicalAddress);
        long lo = Integer.toUnsignedLong(m_memoryMap.readMEM32(addr));
        long hi = Integer.toUnsignedLong(m_memoryMap.readMEM32(addr + 4));
        return lo | (hi << 32);
    }

    private int toAddress32(long physicalAddress) {

        long masked = physicalAddress & m_a20GateMask;
        if((masked & ~0xFFFFFFFFL) != 0L) {
            throw new UnsupportedOperationException(
                String.format("Physical address outside 32-bit memory map: %016X", masked)
            );
        }

        return (int)masked;
    }

    private static boolean isCanonicalAddress(long linearAddress) {

        long signExtension = linearAddress >> 48;
        return signExtension == 0L || signExtension == -1L;
    }

    private long readGpr(int index) {

        return getGpr(index).getValue();
    }

    private void writeGpr(int index, long value) {

        getGpr(index).setValue(value);
    }

    private Register64 getGpr(int index) {

        switch(index) {

            case 0:
                return RAX;
            case 1:
                return RCX;
            case 2:
                return RDX;
            case 3:
                return RBX;
            case 4:
                return RSP;
            case 5:
                return RBP;
            case 6:
                return RSI;
            case 7:
                return RDI;
            case 8:
                return R8;
            case 9:
                return R9;
            case 10:
                return R10;
            case 11:
                return R11;
            case 12:
                return R12;
            case 13:
                return R13;
            case 14:
                return R14;
            case 15:
                return R15;
            default:
                throw new IllegalArgumentException("Invalid register index: " + index);
        }
    }

    private int EAX() {

        return RAX.getValue32();
    }

    private int EDX() {

        return RDX.getValue32();
    }

    private UnsupportedOperationException unsupportedOpcode(int opcode, int rex, int opcode2) {

        if(opcode2 >= 0) {
            return new UnsupportedOperationException(String.format(
                "RIP=%016X OPCODE=%02X %02X REX=%02X LM=%d",
                RIP.getValue() - 2,
                opcode,
                opcode2,
                rex,
                CR.isLongModeActive() ? 1 : 0
            ));
        }

        return new UnsupportedOperationException(String.format(
            "RIP=%016X OPCODE=%02X REX=%02X LM=%d",
            RIP.getValue() - 1,
            opcode,
            rex,
            CR.isLongModeActive() ? 1 : 0
        ));
    }



    @Override
    public String toString() {

        return String.format(
            "%s %s %s %s %s %s",
            RAX,
            RBX,
            RCX,
            RDX,
            RIP,
            CR
        );
    }
}
