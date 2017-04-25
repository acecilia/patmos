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

class SdramControllerTester (dut: SdramController ) extends Tester(dut) {
    
    /* Testing Initialization of DRAM
    *
    *   The controller should send the correct init procedure
    *   to the DRAM, and ignore any input from the ocp until
    *   it is finished. We except the controller to take the 
    *   command from patmos as soon as the controller is ready
    */
    println("\n0 idle\n1 write\n2 read\n3 initStart\n4 refresh\n5 initPrecharge\n6 initRefresh\n7 initRegister\n\n")
    println("Testing Initialization: ")
        poke(dut.io.ocp.M.Cmd, 0)
        poke(dut.io.ocp.M.Addr, 1 )
        poke(dut.io.ocp.M.Data, 42 )
    step (dut.initCycles-1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)
    step (1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)
    step (1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)
    step (1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)
    step (1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)
    step (1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)
    step (1)
        peek(dut.io.sdramControllerPins.ramOut.cs)
        peek(dut.io.sdramControllerPins.ramOut.ras)
        peek(dut.io.sdramControllerPins.ramOut.we)
        peek(dut.io.sdramControllerPins.ramOut.cas)
        peek(dut.io.sdramControllerPins.ramOut.ba)
        peek(dut.io.sdramControllerPins.ramOut.addr)
        peek(dut.io.sdramControllerPins.ramOut.cke)
        peek(dut.refreshCounter)
        peek(dut.state)

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
