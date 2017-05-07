/*
   Copyright 2013 Technical University of Denmark, DTU Compute.
   All rights reserved.

   This file is part of the time-predictable VLIW processor Patmos.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are met:

      1. Redistributions of source code must retain the above copyright notice,
         this list of conditions and the following disclaimer.

      2. Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER ``AS IS'' AND ANY EXPRESS
   OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
   OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
   NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
   DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
   (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
   ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

   The views and conclusions contained in the software and documentation are
   those of the authors and should not be interpreted as representing official
   policies, either expressed or implied, of the copyright holder.
 */

/*
 * SDRAM memory controller written in Chisel for the ALTDE2-115 board
 *  
 * Authors: Andres Cecilia Luque  (a.cecilia.luque@gmail.com)
 *          Roman Birca           (roman.birca@gmail.com)
 *
 */

package io
import scala.math._
import Chisel._
import Node._
import ocp._
import patmos.Constants._

object SdramController extends DeviceObject {
  private var sdramAddrWidth = 13
  private var sdramDataWidth = 32
  private var ocpAddrWidth   = 25

  def init(params: Map[String, String]) = {
    sdramAddrWidth  = getPosIntParam(params, "sdramAddrWidth")
    sdramDataWidth  = getPosIntParam(params, "sdramDataWidth")
    ocpAddrWidth    = getPosIntParam(params, "ocpAddrWidth")
  }

  def create(params: Map[String, String]): SdramController = {
    Module(new SdramController(sdramAddrWidth, sdramDataWidth, ocpAddrWidth, ocpBurstLen=BURST_LENGTH))
  }

  trait Pins {
    val sdramControllerPins = new Bundle {
      val ramOut = new Bundle {
        // The total 128MB SDRAM is implemented using two 64MB SDRAM devices (page 64 of the DE2-115 manual. "dq" and "dqm" will need to be splitted at pin assignment)
        val dq  = Bits(OUTPUT, sdramDataWidth) 
        val dqm = Bits(OUTPUT, 4)

        // Common signals for the two 64MB SDRAM devices
        val addr = Bits(OUTPUT, sdramDataWidth)
        val ba   = Bits(OUTPUT, 2)
        val clk  = Bits(OUTPUT, 1)
        val cke  = Bits(OUTPUT, 1)
        val ras  = Bits(OUTPUT, 1)
        val cas  = Bits(OUTPUT, 1)
        val we   = Bits(OUTPUT, 1)
        val cs   = Bits(OUTPUT, 1)
        val dqEn = Bits(OUTPUT, 1)
      }
      val ramIn = new Bundle {
        val dq   = Bits(INPUT, sdramDataWidth)
      }
    }
  }
}

class SdramController(sdramAddrWidth: Int, sdramDataWidth: Int, 
  ocpAddrWidth: Int, ocpBurstLen : Int) extends BurstDevice(ocpAddrWidth) {
  override val io  = new BurstDeviceIO(ocpAddrWidth) with SdramController.Pins
  
  // Syntactic sugar
  val clockFreq = 80000000 // util.Config.getConfig.frequency
  val ocpCmd  = io.ocp.M.Cmd
  val ocpSlavePort = io.ocp.S
  val ocpMasterPort = io.ocp.M
  // val ramOut = pipe
  // val ramIn = io.sdramControllerPins.ramIn
  val high = Bits("b1")
  val low  = Bits("b0")

  val state          = Reg(init = ControllerState.initStart) // Controller state
  val memoryCmd      = Reg(init = Bits(0))
  val address        = Reg(init = Bits(0))
  val initCycles     = (0.0001*clockFreq).toInt // Calculate number of cycles for init from processor clock freq
  val refreshRate    = (0.064*clockFreq).toInt
  val thisManyTimes  = 8192
  val initCounter    = Reg(init = Bits(initCycles))
  val refreshCounter = Reg(init = Bits(refreshRate))
  
  // counter used for burst
  val counter = Reg(init = Bits(0))
  
  val ramOut = new Bundle {
        val dq  = Reg(init = Bits(0))
        val dqm = Reg(init = Bits(0))
        val addr = Reg(init = Bits(0))
        val ba   = Reg(init = Bits(0))
        val clk  = Reg(init = Bits(0))
        val cke  = Reg(init = Bits(0))
        val ras  = Reg(init = Bits(0))
        val cas  = Reg(init = Bits(0))
        val we   = Reg(init = Bits(0))
        val cs   = Reg(init = Bits(0))
        val dqEn = Reg(init = Bits(0))
      }
      
  val ramIndq = Reg(init = Bits(0))

  // Default value for signals
  memoryCmd := MemCmd.noOperation

  // Default assignemts to OCP slave signals
  ocpSlavePort.Resp       := OcpResp.NULL
  ocpSlavePort.CmdAccept  := low 
  ocpSlavePort.DataAccept := low
  ocpSlavePort.Data       := low

  // Default assignemts to SdramController output signals
  io.sdramControllerPins.ramOut.dqEn := ramOut.dqEn
  io.sdramControllerPins.ramOut.dq   := ramOut.dq
  io.sdramControllerPins.ramOut.dqm  := ramOut.dqm
  io.sdramControllerPins.ramOut.addr := ramOut.addr
  io.sdramControllerPins.ramOut.ba   := ramOut.ba
  io.sdramControllerPins.ramOut.clk  := ramOut.clk
  io.sdramControllerPins.ramOut.cke  := ramOut.cke
  io.sdramControllerPins.ramOut.ras  := ramOut.ras
  io.sdramControllerPins.ramOut.cas  := ramOut.cas
  io.sdramControllerPins.ramOut.we   := ramOut.we
  io.sdramControllerPins.ramOut.cs   := ramOut.cs
  ramIndq                            := io.sdramControllerPins.ramIn.dq

  refreshCounter := refreshCounter - Bits(1)

  // state machine for the ocp signal
  when(state === ControllerState.idle) {
    when (refreshCounter < Bits(3+ocpBurstLen)) { // 3+ocpBurstLen in order to make sure there is room for read/write
        memoryCmd := MemCmd.cbrAutoRefresh
        ramOut.cs := low
        ramOut.ras := low
        ramOut.cas := low
        ramOut.we := high
        refreshCounter := Bits(refreshRate)
        state := ControllerState.refresh
        
    } .elsewhen (ocpCmd === OcpCmd.RD) {

        // Save address to later use
        address := ocpMasterPort.Addr
        
        // Send ACT signal to mem where addr = OCP addr 22-13, ba1 = OCP addr 24, ba2 = OCP addr 23
        memoryCmd := MemCmd.bankActivate        
        ramOut.addr(12,0) := ocpMasterPort.Addr(12,0)
        ramOut.ba := ocpMasterPort.Addr(24,23)
        
        // send accept to ocp
        ocpSlavePort.CmdAccept := high
        
        // reset burst counter
        counter := Bits(ocpBurstLen+3)
        
        // Set next state to read
        state := ControllerState.read

    }
    
    .elsewhen (ocpCmd === OcpCmd.WR) {

        // Save address to later use
        address := ocpMasterPort.Addr
        
        // Send ACT signal to mem where addr = OCP addr 22-13, ba1 = OCP addr 24, ba2 = OCP addr 23
        memoryCmd := MemCmd.bankActivate        
        ramOut.addr(12,0) := ocpMasterPort.Addr(12,0)
        ramOut.ba := ocpMasterPort.Addr(24,23)
        
        // send accept to ocp
        ocpSlavePort.CmdAccept := high
        
        // reset burst counter
        counter := Bits(ocpBurstLen)
        
        // Set next state to write
        state := ControllerState.write

    }
    
    .elsewhen (ocpCmd === OcpCmd.IDLE) {

        // Send Nop
        // Set next state to idle

    }
    
    .otherwise {
    
        // Manage all the other OCP commands that at the moment of writing this are not implemented
        
    }
  } 
  
  .elsewhen (state === ControllerState.write) {
  
    // Send write signal to memCmd with address and AUTO PRECHARGE enabled
    ramOut.addr(12,0) := address(12,0)
    ramOut.ba := ocpMasterPort.Addr(24,23)
    // set data and byte enable for read
    ramOut.dqEn := high
    ramOut.dq  := ocpMasterPort.Data
    ramOut.dqm := ocpMasterPort.DataByteEn
    
    // Either continue or stop burst
    when(counter >= Bits(1)) {
        memoryCmd := MemCmd.write
        counter := counter - Bits(1)
        io.ocp.S.DataAccept := high
        state := ControllerState.write
    } .otherwise {
        memoryCmd := MemCmd.noOperation
        ocpSlavePort.Resp := OcpResp.DVA
        state := ControllerState.idle
    }
  } 
  
  .elsewhen (state === ControllerState.read) {
    ramOut.dqEn := low
    // Send read signal to memCmd with address and AUTO PRECHARGE enabled - Only on first iteration
    when (counter === Bits(3+ocpBurstLen)) {
        memoryCmd := MemCmd.read
        ramOut.addr(9,0) := address(22,13)
        ramOut.ba := address(24,23)
    }
    
    when (counter < Bits(ocpBurstLen)) {
        ocpSlavePort.Data := ramIndq
        ocpSlavePort.Resp := OcpResp.DVA
    }
    
    // go to next address for duration of burst
    when (counter > Bits(0)) {
        counter := counter - Bits(1)
        state := ControllerState.read
    } .otherwise { 
        state := ControllerState.idle
    }
  }
  
  // The following is all part of the initialization phase
  .elsewhen (state === ControllerState.initStart) {
    /* The 512Mb SDRAM is initialized after the power is applied
    *  to Vdd and Vddq (simultaneously) and the clock is stable
    *  with DQM High and CKE High. */
    ramOut.cke := high
    // All bits of dqm set to high
    ramOut.dqm := Bits("b1111")
    
    /* A 100μs delay is required prior to issuing any command
    *  other than a COMMAND INHIBIT or a NOP. The COMMAND
    *  INHIBIT or NOP may be applied during the 100us period and
    *  should continue at least through the end of the period. */
    memoryCmd := MemCmd.noOperation
    when (initCounter > Bits(1)) {   
        initCounter := initCounter - Bits(1);
        state := ControllerState.initStart 
    } .otherwise {
        state := ControllerState.initPrecharge 
        }
    
  } .elsewhen (state === ControllerState.initPrecharge) {
    /* With at least one COMMAND INHIBIT or NOP command
    *  having been applied, a PRECHARGE command should
    *  be applied once the 100μs delay has been satisfied. All
    *  banks must be precharged. */
    memoryCmd := MemCmd.prechargeAllBanks
    state := ControllerState.initRefresh
    counter := high
    
  } .elsewhen (state === ControllerState.initRefresh) {
    /* at least two AUTO REFRESH cycles
    *  must be performed. */
    memoryCmd := MemCmd.cbrAutoRefresh
    when (counter===high) {
        counter := counter - Bits(1)
        state := ControllerState.initRefresh
    } .otherwise {
        state := ControllerState.initRegister
    }
  
  } .elsewhen (state === ControllerState.initRegister) {
    /* The mode register should be loaded prior to applying
    * any operational command because it will power up in an
    * unknown state. */
    
    /* Write Burst Mode
    *  0    Programmed Burst Length
    *  1    Single Location Access   */
    ramOut.addr(9)       := low
    
    /* Operating mode 
    *  00   Standard Operation 
    *  --   Reserved            */
    ramOut.addr(8,7)     := low
    
    /* Latency mode 
    *  010  2 cycles
    *  011  3 cycles
    *  ---  Reserved            */
    ramOut.addr(6,4)     := Bits (2)
    
    /* Burst Type
    *  0    Sequential
    *  1    Interleaved         */
    ramOut.addr(3)     := low
    
    /* Burst Length
    *  000  1
    *  001  2
    *  010  4
    *  011  8
    *  111  Full Page (for sequential type only)
    *  ---  Reserved            */
    ramOut.addr(2,0)     := Bits(2) // Burst Length TODO: make this dynamic
    
    memoryCmd := MemCmd.modeRegisterSet
    state := ControllerState.idle
  }
  
  .elsewhen (state === ControllerState.refresh) {
        memoryCmd := MemCmd.cbrAutoRefresh
        ramOut.cs := low
        ramOut.ras := low
        ramOut.cas := low
        ramOut.we := high
        
        when( refreshCounter > UInt(0) ) { // do it this many times
          refreshCounter := refreshCounter - UInt(1)
          state := ControllerState.refresh
        } .otherwise { 
          state := ControllerState.idle
        }
  }
  
  // No need to add the otherwise option if we have when implementation for all cases
  /*
  .otherwise { 
    // Used for standard register value update
    address := address
    io.ocp.S.CmdAccept := low
    io.ocp.S.DataAccept := low
    io.sdramControllerPins.ramOut.dq := low
    io.sdramControllerPins.ramOut.dqm := low
    io.sdramControllerPins.ramOut.ba := low
    io.sdramControllerPins.ramOut.cke := low
    io.ocp.S.Resp := OcpResp.NULL
    io.ocp.S.Data := low
    io.ocp.S.DataAccept := low
    memoryCmd := MemCmd.noOperation
  }
  */

  MemCmd.setToPins(memoryCmd, io)
}

// Memory controller internal states
object ControllerState {
    val idle :: write :: read :: refresh :: initStart :: initPrecharge :: initRefresh :: initRegister :: Nil = Enum(UInt(), 8)
}

object MemCmd {
  // States obtained from the IS42/45R86400D/16320D/32160D datasheet, from the table "COMMAND TRUTH TABLE"
  val deviceDeselect :: noOperation :: burstStop :: read :: readWithAutoPrecharge :: write :: writeWithAutoPrecharge :: bankActivate :: prechargeSelectBank :: prechargeAllBanks :: cbrAutoRefresh :: selfRefresh :: modeRegisterSet :: Nil = Enum(UInt(), 13)
  
  // Syntactic sugar
  private val high = Bits("b1")
  private val low  = Bits("b0")

  // Public API for encoding
  def setToPins(memCmd:UInt, io: BurstDeviceIO with SdramController.Pins) = {
    setToPinsImplementation(
      memCmd = memCmd,
      cke    = io.sdramControllerPins.ramOut.cke,
      cs     = io.sdramControllerPins.ramOut.cs,
      ras    = io.sdramControllerPins.ramOut.ras,
      cas    = io.sdramControllerPins.ramOut.cas,
      we     = io.sdramControllerPins.ramOut.we,
      ba     = io.sdramControllerPins.ramOut.ba,
      a10    = io.sdramControllerPins.ramOut.addr(10)
    ) 
  }

  /* 
    Private implementation of a decoding
    According to the datasheet there are other signals not considered in this function. This is why:
      - cke(n-1): in all cases of the "COMMAND TRUTH TABLE" is high
      - A12, A11, A9 to A0: data is allways going to be high or low, allways valid values
      - Valid states are not considered: they are allways going to be valid (it is not possible to have a signal with a value between low and high)
  */
  private def setToPinsImplementation(memCmd: UInt, cke: Bits, cs:Bits, ras:Bits, cas:Bits, we:Bits, ba:Bits, a10:Bits) = {
    when(memCmd === deviceDeselect) {
      cs := high
    }.elsewhen(memCmd === noOperation) {
      cs := low; ras := high; cas := high; we := high;
    }.elsewhen(memCmd === burstStop) {
      cs := low; ras := high; cas := high; we := low;
    }.elsewhen(memCmd === read) {
      cs := low; ras := high; cas := low; we := high; a10 := low;
    }.elsewhen(memCmd === writeWithAutoPrecharge) {
      cs := low; ras := high; cas := low; we := high; a10 := high;
    }.elsewhen(memCmd === write) {
      cs := low; ras := high; cas := low; we := low; a10 := low
    }.elsewhen(memCmd === writeWithAutoPrecharge) {
      cs := low; ras := high; cas := low; we := low; a10 := high
    }.elsewhen(memCmd === bankActivate) {
      cs := low; ras := low; cas := high; we := high
    }.elsewhen(memCmd === prechargeSelectBank) {
      cs := low; ras := low; cas := high; we := low; a10 := low
    }.elsewhen(memCmd === prechargeAllBanks) {
      cs := low; ras := low; cas := high; we := low; a10 := high
    }.elsewhen(memCmd === cbrAutoRefresh) {
      cke := high; cs := low; ras := low; cas := low; we := high
    }.elsewhen(memCmd === selfRefresh) {
      cke := low; cs := low; ras := low; cas := low; we := high
    }.elsewhen(memCmd === modeRegisterSet) {
      cs := low; ras := low; cas := low; we := low; ba(0) := low; ba(1) := low; a10 := low
    }
  }
}
