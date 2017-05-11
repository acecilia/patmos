package io.test
import Chisel._
import Node._
import ocp._
import io._
import patmos.Constants._

// It is possible to avoid the default prints of poke, peek, execute and step by extending from Tester(dut, false) instead of Tester(dut)
class SdramControllerTester(dut: SdramController) extends Tester(dut) {
    
    // Syntactic sugar
    private val ramOut = dut.io.sdramControllerPins.ramOut
    private val ramIn = dut.io.sdramControllerPins.ramIn
    private val ocpMasterPort = dut.io.ocp.M
    private val ocpSlavePort = dut.io.ocp.S

    println("\n0 waitPll\n1 idle\n2 write\n3 read\n4 refresh\n5 activate\n6 initStart\n7 initPrecharge\n8 initRefresh\n9 initRegister\n\n")

    writeTest()
    
    def initTest():Unit = {
        println("Testing Initialization: ")
            poke(ocpMasterPort.Cmd, 0)
            poke(ocpMasterPort.Addr, 1)
            poke(ocpMasterPort.Data, 42)
        step(dut.initCycles-1)
            println("\nStarting up")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.we, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.ba, 0x0)
            expect(ramOut.addr, 0x0)
            expect(ramOut.cke, 0x1)
            expect(dut.state, 0x4)
        step(1)
            println("\nprecharge all banks")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x0)
            expect(ramOut.addr, 0x400)
            expect(dut.state, 0x5)
        step(1)
            println("\nRefresh nr. 1")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x6)
        step(1)
            println("\nRefresh nr. 2")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x6)
        step(1)
            println("\nMode Register")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x0)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x0)
            expect(ramOut.addr, 0x22)
            expect(dut.state, 0x7)
        step(1)
            println("\nIdle and ready for use")
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x0)
    }

    def readTest():Unit = {
        println("Testing read:")
        step(dut.initCycles+100)
        println("\nMaking sure we are in idle")
            poke(ocpMasterPort.Cmd, 0)
            poke(ocpMasterPort.Addr, 0x1002001)
            poke(ocpMasterPort.Data, 42)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(dut.state, 0x0)
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
            expect(dut.state, 0x0)
        step(1)
            println("\nStarting read")
            poke(ocpMasterPort.Cmd, 0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x1)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x01)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nWaiting for data")
            expect(ocpSlavePort.Resp, 0x0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nGet 1st data")
            poke(ramIn.dq, 0x1)
            expect(ocpSlavePort.Data, 0x1)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nGet 2nd data")
            poke(ramIn.dq, 0x2)
            expect(ocpSlavePort.Data, 0x2)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nGet 3rd data")
            poke(ramIn.dq, 0x3)
            expect(ocpSlavePort.Data, 0x3)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nGet 4th data")
            poke(ramIn.dq, 0x4)
            expect(ocpSlavePort.Data, 0x4)
            expect(ocpSlavePort.Resp, 0x1)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nBack and ready in Idle")
            expect(ocpSlavePort.Resp, 0x0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x0)
    }

    def writeTest():Unit = {
        println("Testing write:")
        poke(ramIn.pllReady, 0x1)
        step(dut.initCycles+100)
        println("\nMaking sure we are in idle")
            expect(dut.state, 0x1)
            expect(dut.memoryCmd, 0x01)
        step(1)
            println("\nRow activation")
            poke(ocpMasterPort.Cmd, 1)
            poke(ocpMasterPort.Addr, 0x1555554) // 1 * 0101010101010 * 101010101 * 00
            poke(ocpMasterPort.Data, 0x25)
            poke(ocpMasterPort.DataByteEn, 0xf)
            poke(ocpMasterPort.DataValid, 1)
            expect(dut.counter, 0x00)
            expect(dut.memoryCmd, 0x01)
        step(1)
            expect(ramOut.ba, 0x01) // 1 (bank)
            expect(ramOut.addr, 0xAAA) // 0101010101010 (row, top part of the address)
            expect(dut.state, 0x05)
            expect(dut.counter, 0x01)
            expect(dut.memoryCmd, 0x07)
        step(1)
            expect(dut.state, 0x05)
            expect(dut.counter, 0x00)
            expect(dut.memoryCmd, 0x01)
        step(1)
            println("\nWrite burst")
            expect(ramOut.ba, 0x01) // 01 (bank)
            expect(ramOut.addr, 0x555) // 10 (autoprecharge) * 101010101 (column, bottom part of the address)
            expect(ocpSlavePort.CmdAccept, 0x1)
            expect(ocpSlavePort.DataAccept, 0x1)
            expect(ramOut.dq, 0x25)
            expect(dut.state, 0x02)
            expect(dut.counter, 0x04)
            expect(dut.memoryCmd, 0x06)
        step(1)
            poke(ocpMasterPort.Data, 0x26)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(ocpSlavePort.DataAccept, 0x1)
            expect(ramOut.dq, 0x26)
            expect(dut.state, 0x02)
            expect(dut.counter, 0x03)
            expect(dut.memoryCmd, 0x06)
        step(1)
            poke(ocpMasterPort.Data, 0x27)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(ocpSlavePort.DataAccept, 0x1)
            expect(ramOut.dq, 0x27)
            expect(dut.state, 0x02)
            expect(dut.counter, 0x02)
            expect(dut.memoryCmd, 0x06)
        step(1)
            poke(ocpMasterPort.Data, 0x28)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(ocpSlavePort.DataAccept, 0x1)
            expect(ramOut.dq, 0x28)
            expect(dut.state, 0x02)
            expect(dut.counter, 0x01)
            expect(dut.memoryCmd, 0x06)
        step(1)
            poke(ocpMasterPort.DataValid, 0)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(ocpSlavePort.DataAccept, 0x0)
            expect(ocpSlavePort.Resp, 0x1)
            expect(dut.state, 0x02)
            expect(dut.counter, 0x00)
            expect(dut.memoryCmd, 0x01)
        step(1)
            expect(dut.state, 0x01)
            expect(dut.memoryCmd, 0x01)
            /*
            println("\nwrite 1st data")
            poke(ocpMasterPort.Cmd, 0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x0)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x01)
            expect(ramOut.dq, 0x2a)
            expect(ocpMasterPort.Data, 0x2a)
            expect(ocpMasterPort.DataByteEn, 0xf)
            expect(ocpMasterPort.DataValid, 0x1)
			expect(ocpSlavePort.DataAccept, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 2nd data")
            poke(ocpMasterPort.Data, 43)
            poke(ocpMasterPort.DataByteEn, 0xA)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x0)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x1)
            expect(ramOut.dq, 0x2b)
			expect(ocpMasterPort.DataByteEn, 0xA)
            expect(ocpMasterPort.DataValid, 0x1)
			expect(ocpSlavePort.DataAccept, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 3rd data")
            poke(ocpMasterPort.Data, 44)
            poke(ocpMasterPort.DataByteEn, 0xf)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x0)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x1)
            expect(ramOut.dq, 0x2c)
			expect(ocpMasterPort.DataByteEn, 0xf)
            expect(ocpMasterPort.DataValid, 0x1)
			expect(ocpSlavePort.DataAccept, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 4th data")
            poke(ocpMasterPort.Data, 45)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x0)
            expect(ramOut.we, 0x0)
            expect(ramOut.ba, 0x2)
            expect(ramOut.addr, 0x1)
            expect(ramOut.dq, 0x2d)
			expect(ocpMasterPort.DataByteEn, 0xf)
            expect(ocpMasterPort.DataValid, 0x1)
			expect(ocpSlavePort.DataAccept, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nsend resp")
            poke(ocpMasterPort.DataValid, 0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.Resp, 0x1)
			expect(ocpSlavePort.DataAccept, 0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nBack and ready in Idle")
            expect(ocpSlavePort.Resp, 0x0)
            expect(ramOut.cs, 0x0)
            expect(ramOut.ras, 0x1)
            expect(ramOut.cas, 0x1)
            expect(ramOut.we, 0x1)
            expect(ocpSlavePort.CmdAccept, 0x0)
            expect(ocpSlavePort.Resp, 0x0)
            expect(dut.state, 0x0)
            */
    }

    def expectStateOrAdvance(state:Int, waitSteps:Int = 10000):Unit = {
        val initialState = peek(dut.state)
        for(a <- 0 until waitSteps){
            val tmpState = peek(dut.state)
            if(initialState != tmpState || tmpState == state) {
                expect(dut.state, state)
                return
            }
            step(1)
        }
        expect(dut.state, state)
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
