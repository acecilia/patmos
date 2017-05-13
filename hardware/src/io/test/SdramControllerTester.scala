package io.test
import Chisel._
import Node._
import ocp._
import io._
import patmos.Constants._

// Memory controller internal states
object ControllerState {
    val waitPll = 0x00
    val idle = 0x01 
    val write = 0x02 
    val read = 0x03 
    val refresh = 0x04 
    val activate = 0x05 
    val initStart = 0x06
    val initPrecharge = 0x07
    val initRefresh = 0x08 
    val initRegister = 0x09
}

// Memory commands
object MemCmd {
    val deviceDeselect = 0x00
    val noOperation = 0x01
    val burstStop = 0x02
    val read = 0x03
    val readWithAutoPrecharge = 0x04
    val write = 0x05
    val writeWithAutoPrecharge = 0x06
    val bankActivate = 0x07
    val prechargeSelectBank = 0x08
    val prechargeAllBanks = 0x09
    val cbrAutoRefresh = 0x0A
    val selfRefresh = 0x0B
    val modeRegisterSet = 0x0C
}

// Memory controller internal states
object OcpCmd {
    val IDLE = 0x00
    val WR = 0x01 
    val RD = 0x02 
}

// It is possible to avoid the default prints of poke, peek, execute and step by extending from Tester(dut, false) instead of Tester(dut)
class SdramControllerTester(dut: SdramController) extends Tester(dut) {
    
    // Syntactic sugar
    private val ramOut = dut.io.sdramControllerPins.ramOut
    private val ramIn = dut.io.sdramControllerPins.ramIn
    private val ocpMasterPort = dut.io.ocp.M
    private val ocpSlavePort = dut.io.ocp.S

    poke(ramIn.pllReady, 1)

    refreshTest()
    activateTest()
    writeTest()

    def activateTest():Unit = {
        println("\nTesting activation:")
        println("\nWait until idle state:")
        stepUntil(dut.state, ControllerState.idle, 100000)

        println("\nSimulate a write, for the activation:")
            poke(ocpMasterPort.Cmd, OcpCmd.WR)
            poke(ocpMasterPort.Addr, 0x1555554) // 10*1010101010101*0101010100
            poke(ocpMasterPort.Data, 0x25)
            poke(ocpMasterPort.DataByteEn, 0xf)
            poke(ocpMasterPort.DataValid, 1)
            expect(dut.state, ControllerState.idle)
        step(1)
        println("\nStay in activation state during trcd:")
            expect(dut.memoryCmd, MemCmd.bankActivate)
            expect(ramOut.addr, 0x1555)            // Row: 1010101010101
            expect(ramOut.ba, 0x02)                // Bank: 10
            expect(dut.state, ControllerState.activate)
        step(1)
            expect(dut.memoryCmd, MemCmd.noOperation)
            expect(dut.state, ControllerState.activate)
        step(1)
        println("\nStart the writing (finishing test, not in the scope now):")
            expect(dut.state, ControllerState.write)
    }

    def refreshTest():Unit = {
        println("\nTesting refresh:")
        println("\nWait until idle state:")
        stepUntil(dut.state, ControllerState.idle, 100000)

        println("\nWait until refresh starts:")
        stepUntil(dut.state, ControllerState.refresh)

        // Execute this part of the test two times, to be sure it works
        for(a <- 0 until 2){
            println("\nStay in refresh state during trc:")
                expect(dut.memoryCmd, MemCmd.cbrAutoRefresh)
                expect(dut.state, ControllerState.refresh)
            step(1)
                expect(dut.memoryCmd, MemCmd.noOperation)
                expect(dut.state, ControllerState.refresh)
            step(1)
                expect(dut.memoryCmd, MemCmd.noOperation)
                expect(dut.state, ControllerState.refresh)
            step(1)
                expect(dut.memoryCmd, MemCmd.noOperation)
                expect(dut.state, ControllerState.refresh)
            step(1)
                expect(dut.memoryCmd, MemCmd.noOperation)
                expect(dut.state, ControllerState.refresh)
            step(1)

            println("\nGo back to idle and wait until next refresh:")
                expect(dut.memoryCmd, MemCmd.noOperation)
                expect(dut.state, ControllerState.idle)
            step(624)
                expect(dut.memoryCmd, MemCmd.noOperation)
                expect(dut.state, ControllerState.idle)
            step(1)
        }
    }

    def initTest():Unit = {
        println("Testing Initialization: ")
            poke(ramIn.pllReady, 0x1)
            poke(ocpMasterPort.Cmd, 0)
            poke(ocpMasterPort.Addr, 1)
            poke(ocpMasterPort.Data, 42)
        step(dut.initCycles)
            println("\nStarting up")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.we, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.ba, 0x0)
            expect(ramOut.addr, 0x0)
            expect(ramOut.cke, 0x1)
            expect(dut.state, 0x6)
        step(1)
            println("\nprecharge all banks")
            expect(ramOut.addr, 0x400)
            expect(dut.memoryCmd, 0x09)
            expect(dut.state, 0x7)
        step(1)
            println("\nRefresh nr. 1")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x8)
        step(8192)
            println("\nNo operation")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.we, 0x1)
            expect(ramOut.cas, 0x1)
            expect(dut.state, 0x8)
        step(1)
            println("\nRefresh nr. 2")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x8)
        step(8192)
            println("\nNo operation")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.we, 0x1)
            expect(ramOut.cas, 0x1)
            expect(dut.state, 0x8)
        step(1)
            println("\nMode Register")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x0)
            expect(ramOut.addr, 0x22)
            expect(dut.state, 0x9)
        step(1)
            println("\nIdle and ready for use")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x1)
    }

    def readTest():Unit = {
            println("Testing read:")
            poke(ramIn.pllReady, 0x1)
        step(dut.initCycles+100)
        println("\nMaking sure we are in idle")
            poke(ocpMasterPort.Cmd, 0)
            poke(ocpMasterPort.Addr, 0x1002001)
            poke(ocpMasterPort.Data, 42)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x1)
        step(1)
            println("\nData is valid")
            poke(ocpMasterPort.Cmd, 2)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x01)
            expect(ocpSlavePort.CmdAccept, 0x1)
            expect(dut.state, 0x1)
        step(1)
            println("\nWait for activate 1")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x5)
        step(1)
            println("\nWait for activate 2")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x5)
        step(1)
            println("\nStarting read (T0)")
            poke(ocpMasterPort.Cmd, 0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x1)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x401)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x3)
        step(1)
            println("\nWaiting for data (T1)")
            expect(ocpSlavePort.Resp, 0x0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x3)
        step(1)
            println("\nGet 1st data (T2)")
            poke(ramIn.dq, 0x1)
            expect(ocpSlavePort.Data, 0x1)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x3)
        step(1)
            println("\nGet 2nd data (T3)")
            poke(ramIn.dq, 0x2)
            expect(ocpSlavePort.Data, 0x2)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x3)
        step(1)
            println("\nGet 3rd data (T4)")
            poke(ramIn.dq, 0x3)
            expect(ocpSlavePort.Data, 0x3)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x3)
        step(1)
            println("\nGet 4th data (T5)")
            poke(ramIn.dq, 0x4)
            expect(ocpSlavePort.Data, 0x4)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x3)
        step(1)
            println("\nBack and ready in Idle (T6)")
            expect(ocpSlavePort.Resp, 0x0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x1)
    }

    def writeTest():Unit = {
        val data = Array(0x25, 0x56, 0xAA, 0x32)

        println("Testing write:")
        println("\nWait until idle state:")
        stepUntil(dut.state, ControllerState.idle, 100000)

            println("\nOrder one write")
            poke(ocpMasterPort.Cmd, OcpCmd.WR)
            poke(ocpMasterPort.Addr, 0x1555554) // 10*1010101010101*0101010100
            poke(ocpMasterPort.Data, data(0))
            poke(ocpMasterPort.DataValid, 1)
            poke(ocpMasterPort.DataByteEn, 0xf)

            expect(dut.state, ControllerState.idle)
            expect(dut.memoryCmd, MemCmd.noOperation)

        println("\nWait until write state (activation should happen, but we do not want to test it now):")
        stepUntil(dut.state, ControllerState.write)
            expect(ocpSlavePort.CmdAccept, 0x1)
            expect(ocpSlavePort.DataAccept, 0x1)
            expect(dut.state, ControllerState.write)
            expect(ramOut.ba, 0x02) // 10 bank
            expect(ramOut.addr, 0x554) // Column: 1(A10, auto-precharge)*0101010100
            expect(ramOut.dq, data(0))
            expect(dut.memoryCmd, MemCmd.writeWithAutoPrecharge)
        step(1)
        for(i <- 1 until data.length){
                poke(ocpMasterPort.Data, data(i))

                expect(ocpSlavePort.CmdAccept, 0x0)
                expect(ocpSlavePort.DataAccept, 0x1)
                expect(dut.state, ControllerState.write)
                expect(ramOut.dq, data(i))
                expect(dut.memoryCmd, MemCmd.noOperation)
            step(1)
        }
            println("\nThe burst finished, now we stop it and report to Patmos:")
            poke(ocpMasterPort.DataValid, 0)

            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(ocpSlavePort.DataAccept, 0x0)
            expect(ocpSlavePort.Resp, 0x1)
            expect(dut.state, ControllerState.write)
            expect(dut.memoryCmd, MemCmd.burstStop)
        step(1)
            println("\nWait for trp to finish:")
            expect(dut.state, ControllerState.write)
            expect(dut.memoryCmd, MemCmd.noOperation)
        step(1)
            println("\nGo back to idle:")
            expect(dut.state, ControllerState.idle)
    }

    def writeTest2():Unit = {
            println("Testing write:")
            poke(ramIn.pllReady, 0x1)
        step(dut.initCycles+100)
            println("\nMaking sure we are in idle")
            poke(dut.io.ocp.M.Cmd, 0)
            poke(dut.io.ocp.M.Addr, 0x1002001 )
            poke(dut.io.ocp.M.Data, 42 )
            poke(dut.io.ocp.M.DataValid, 1)
            poke(dut.io.ocp.M.DataByteEn, 0xf)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x1)
        step(1)
            println("\nData is valid (T0)")
            poke(dut.io.ocp.M.Cmd, 1)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x01)
            expect(dut.io.ocp.M.DataValid,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x1)
			expect(dut.io.ocp.S.DataAccept,0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nWait for activate 1 (T1)")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x5)
        step(1)
            println("\nWait for activate 2 (T2)")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x5)
        step(1)
            println("\nwrite 1st data (T3)")
            poke(dut.io.ocp.M.Cmd, 0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x401)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2a)
            expect(dut.io.ocp.M.Data,0x2a)
            expect(dut.io.ocp.M.DataByteEn,0xf)
            expect(dut.io.ocp.M.DataValid,0x1)
			expect(dut.io.ocp.S.DataAccept,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nwrite 2nd data (T4)")
            poke(dut.io.ocp.M.Data, 43)
            poke(dut.io.ocp.M.DataByteEn, 0xA)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2b)
			expect(dut.io.ocp.M.DataByteEn,0xA)
            expect(dut.io.ocp.M.DataValid,0x1)
			expect(dut.io.ocp.S.DataAccept,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nwrite 3rd data (T5)")
            poke(dut.io.ocp.M.Data, 44 )
            poke(dut.io.ocp.M.DataByteEn, 0xf)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2c)
			expect(dut.io.ocp.M.DataByteEn,0xf)
            expect(dut.io.ocp.M.DataValid,0x1)
			expect(dut.io.ocp.S.DataAccept,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nwrite 4th data (T6)")
            poke(dut.io.ocp.M.Data, 45 )
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2d)
			expect(dut.io.ocp.M.DataByteEn,0xf)
            expect(dut.io.ocp.M.DataValid,0x1)
			expect(dut.io.ocp.S.DataAccept,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nsend resp (T7)")
            poke(dut.io.ocp.M.DataValid, 0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.Resp,0x1)
			expect(dut.io.ocp.S.DataAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nBack and ready in Idle (T8)")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.state, 0x1)
    }
    
    def stepUntil(signal:Bits, value:BigInt, maxWait:Int = 10000):Unit = {
        for(a <- 0 until maxWait){
            val tmpSignal = peek(signal)
            if(tmpSignal == value) {
                return
            }
            step(1)
        }
    }
}

object SdramControllerTester {
    var sdramAddrWidth = 13
    var sdramDataWidth = 32
    var ocpAddrWidth   = 25
    var ocpBurstLen    = 4
    
    def main(args: Array [ String ]): Unit = {
        chiselMainTest(Array("--genHarness", "--test", "--backend", "c", "--compile", "--targetDir", "generated"), 
       () => Module(new SdramController(sdramAddrWidth, sdramDataWidth, ocpAddrWidth, ocpBurstLen))) { c => new SdramControllerTester(c) }
    }
}
