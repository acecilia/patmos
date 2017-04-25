package io.test
import Chisel._
import Node._
import ocp._
import io._
import patmos.Constants._

//     val sdramControllerPins = new Bundle {
//       val ramOut = new Bundle {
//         val dq   = Bits(OUTPUT, width = sdramDataWidth)
//         val dqm  = Bits(OUTPUT, width = 4)
//         val addr = Bits(OUTPUT, width = sdramDataWidth)
//         val ba   = Bits(OUTPUT, width = 2)
//         val clk  = Bits(OUTPUT, width = 1)
//         val cke  = Bits(OUTPUT, width = 1)
//         val ras  = Bits(OUTPUT, width = 1)
//         val cas  = Bits(OUTPUT, width = 1)
//         val we   = Bits(OUTPUT, width = 1)
//         val cs   = Bits(OUTPUT, width = 1)
//         val dqEn = Bits(OUTPUT, width = 1)
//       }
//       val ramIn = new Bundle {
//         val dq    = Bits(INPUT, width = sdramAddrWidth)
//       }
//     }

// It is possible to avoid the default prints of poke, peek, execute and step by extending from Tester(dut, false) instead of Tester(dut)
class SdramControllerTester (dut: SdramController ) extends Tester(dut) {
    // Syntactic sugar
    private val ramOut = dut.io.sdramControllerPins.ramOut
    private val ocpMasterPort = dut.io.ocp.M

    println("\n0 idle\n1 write\n2 read\n3 refresh\n4 initStart\n5 initPrecharge\n6 initRefresh\n7 initRegister\n\n")
    println("-- Testing started --")
    Test.initialization.execute()

    // Object storing the tests
    private object Test {
        /* Testing Initialization of DRAM
        *
        *   The controller should send the correct init procedure
        *   to the DRAM, and ignore any input from the ocp until
        *   it is finished. We except the controller to take the 
        *   command from patmos as soon as the controller is ready
        */
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

            //     step (20000)
            //     peek(dut.io.sdramControllerPins.ramOut.cs)
            //     peek(dut.io.sdramControllerPins.ramOut.ras)
            //     peek(dut.io.sdramControllerPins.ramOut.we)
            //     peek(dut.refreshCounter)
            //     peek(dut.state)
            //         step (30000)
            //     peek(dut.io.sdramControllerPins.ramOut.cs)
            //     peek(dut.io.sdramControllerPins.ramOut.ras)
            //     peek(dut.io.sdramControllerPins.ramOut.we)
            //     peek(dut.refreshCounter)
            //     peek(dut.state)
            //         step (40000)
            //     peek(dut.io.sdramControllerPins.ramOut.cs)
            //     peek(dut.io.sdramControllerPins.ramOut.ras)
            //     peek(dut.io.sdramControllerPins.ramOut.we)
            //     peek(dut.refreshCounter)
            //     peek(dut.state)
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
