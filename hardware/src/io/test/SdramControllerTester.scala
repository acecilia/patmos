package io.test
import Chisel._
import Node._
import ocp._
import io._
import patmos.Constants._

// It is possible to avoid the default prints of poke, peek, execute and step by extending from Tester(dut, false) instead of Tester(dut)
class SdramControllerTester (dut: SdramController ) extends Tester(dut) {
    
    println("\n0 idle\n1 write\n2 read\n3 initStart\n4 refresh\n5 initPrecharge\n6 initRefresh\n7 initRegister\n\n")
    
    initTest()
    readTest()
    writeTest()
    
    def initTest():Unit = {
        println("Testing Initialization: ")
            poke(dut.io.ocp.M.Cmd, 0)
            poke(dut.io.ocp.M.Addr, 1 )
            poke(dut.io.ocp.M.Data, 42 )
        step (dut.initCycles)
            println("\nStarting up")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x0)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cke,0x1)
            expect(dut.state, 0x5)
        step (1)
            println("\nprecharge all banks")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x400)
            expect(dut.state, 0x6)
        step (1)
            println("\nRefresh nr. 1")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x6)
        step (1)
            println("\nRefresh nr. 2")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x7)
        step (1)
            println("\nMode Register")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x22)
            expect(dut.state, 0x0)
        step (1)
            println("\nIdle and ready for use")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x0)
    }

    def readTest():Unit = {
        println("Testing read:")
        step(dut.initCycles+100)
        println("\nMaking sure we are in idle")
            poke(dut.io.ocp.M.Cmd, 0)
            poke(dut.io.ocp.M.Addr, 0x1002001 )
            poke(dut.io.ocp.M.Data, 42 )
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.state, 0x0)
        step(1)
            poke(dut.io.ocp.M.Cmd, 2)
        step(1)
            expect(dut.io.ocp.S.CmdAccept,0x1)
        step(1)
            println("\nData is valid")
            poke(dut.io.ocp.M.Cmd, 0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x01)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nStarting read")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x01)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nWaiting for data")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nData arives")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
            poke(dut.io.sdramControllerPins.ramIn.dq,0x1)
        step(1)
            println("\nGet 1st data")
            poke(dut.io.sdramControllerPins.ramIn.dq,0x2)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nGet 2nd data")
            poke(dut.io.sdramControllerPins.ramIn.dq,0x3)
            expect(dut.io.ocp.S.Data,0x1)
            expect(dut.io.ocp.S.Resp,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
        step(1)
            println("\nGet 3rd data")
            poke(dut.io.sdramControllerPins.ramIn.dq,0x4)
            expect(dut.io.ocp.S.Data,0x2)
            expect(dut.io.ocp.S.Resp,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
            peek(dut.counter)
        step(1)
            println("\nGet 4th data")
            expect(dut.io.ocp.S.Data,0x3)
            expect(dut.io.ocp.S.Resp,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x2)
            peek(dut.counter)
        step(1)
            println("\nBack and ready in Idle")
            expect(dut.io.ocp.S.Data,0x4)
            expect(dut.io.ocp.S.Resp,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x0)
    }

    def writeTest():Unit = {
        println("Testing write:")
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
            expect(dut.state, 0x0)
        step(1)
            poke(dut.io.ocp.M.Cmd, 1)
        step(1)
            expect(dut.io.ocp.S.CmdAccept,0x1)
            expect(dut.io.ocp.S.DataAccept, 0x1)
            
        step(1)
            println("\nData is valid")
            poke(dut.io.ocp.M.Cmd, 0)
            poke(dut.io.ocp.M.Data, 43 )
            expect(dut.io.ocp.S.DataAccept, 0x1)
            
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x01)
			expect(dut.io.ocp.S.DataAccept,0x1)
			expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 1st data")
            expect(dut.io.ocp.S.DataAccept, 0x1)
            poke(dut.io.ocp.M.Data, 44 )
            poke(dut.io.ocp.M.DataByteEn, 0xf)
            
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x01)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2a)
			expect(dut.io.ocp.S.DataAccept,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 2nd data")
            poke(dut.io.ocp.M.Data, 45 )
            expect(dut.io.ocp.S.DataAccept, 0x1)
            
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x1)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2b)
			expect(dut.io.ocp.S.DataAccept,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 3rd data")
            
            expect(dut.io.ocp.S.DataAccept, 0x0)
            
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x1)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2c)
			expect(dut.io.ocp.S.DataAccept,0x0)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nwrite 4th data and send resp")
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x0)
            expect(dut.io.sdramControllerPins.ramOut.we,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ba,0x2)
            expect(dut.io.sdramControllerPins.ramOut.addr,0x1)
            expect(dut.io.sdramControllerPins.ramOut.dq,0x2d)
            expect(dut.io.ocp.S.Resp,0x1)
			expect(dut.io.ocp.S.DataAccept,0x0)
            expect(dut.state, 0x1)
        step(1)
            println("\nBack and ready in Idle")
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.io.sdramControllerPins.ramOut.cs,0x0)
            expect(dut.io.sdramControllerPins.ramOut.ras,0x1)
            expect(dut.io.sdramControllerPins.ramOut.cas,0x1)
            expect(dut.io.sdramControllerPins.ramOut.we,0x1)
            expect(dut.io.ocp.S.CmdAccept,0x0)
            expect(dut.io.ocp.S.Resp,0x0)
            expect(dut.state, 0x0)

    }
    
    // Syntactic sugar
    private val ramOut = dut.io.sdramControllerPins.ramOut
    private val ocpMasterPort = dut.io.ocp.M

    //println("-- Testing started --")
    //Test.initialization.execute()

    // Object storing the tests
    private object Test {
        val initialization = Test("Initialize", () => {
            poke(ocpMasterPort.Cmd, 0)
            poke(ocpMasterPort.Addr, 1)
            poke(ocpMasterPort.Data, 42)

            expectStateOrAdvance(4) //initStart
            expectStateOrAdvance(5) //initPrecharge
            expectStateOrAdvance(6) //initRefresh
            expectStateOrAdvance(7) //initRegister
            expectStateOrAdvance(0) //idle

            peek(ramOut.cs)
            peek(ramOut.ras)
            peek(ramOut.we)
            peek(dut.refreshCounter)
            peek(dut.state)
            
        })

        val read = Test("Read", () => {
        })

        val write = Test("Write", () => {
        })

        def expectStateOrAdvance(state:Int, waitSteps:Int = 10000):Unit = {
            val initialState = peek(dut.state)
            for( a <- 0 until waitSteps){
                val tmpState = peek(dut.state)
                if (initialState != tmpState || tmpState == state) {
                    expect(dut.state, state)
                    return
                }
                step(1)
            }
            expect(dut.state, state)
        }
    }
    // Class to store the test information
    private case class Test(name: String, execution: () => Unit) {
        def execute() = {
            println("--->" + name + ":")
            reset()
            execution()
        }
    }
}

object SdramControllerTester {
    var sdramAddrWidth = 13
    var sdramDataWidth = 32
    var ocpAddrWidth   = 25
    var ocpBurstLen    = 4
    
    def main(args: Array [ String ]): Unit = {
        chiselMainTest ( Array ("--genHarness","--test","--backend","c", "--compile","--targetDir","generated"),
        () => Module (new SdramController (sdramAddrWidth, sdramDataWidth, ocpAddrWidth, ocpBurstLen))) { c => new SdramControllerTester (c) }
    }
}
