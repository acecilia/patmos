package io.test
import Chisel._
import Node._
import ocp._
import io._

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

class SdramControllerTester (dut: SdramController ) extends Tester(dut) {
    
    /* Testing Initialization of DRAM
    *
    *   The controller should send the correct init procedure
    *   to the DRAM, and ignore any input from the ocp until
    *   it is finished. We except the controller to take the 
    *   command from patmos as soon as the controller is ready
    */
    println("Testing Initialization: ")
    
    poke(dut.io.ocp.M.Cmd, 2)
    poke(dut.io.ocp.M.Addr, 1 )
    poke(dut.io.ocp.M.Data, 42 )
    step (1)
    val cs = peek(dut.io.sdramControllerPins.ramOut.cs)
    val ras = peek(dut.io.sdramControllerPins.ramOut.ras)
    val we = peek(dut.io.sdramControllerPins.ramOut.we)
    println("\n")
    println ("cs: \texpected: 0\t got: " + cs)
    println ("ras:\texpected: 1\t got:  " + cs)
    println ("we:\texpected: 1\t got:  " + cs)

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
