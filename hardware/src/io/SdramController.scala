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
 * Authors: Andres Cecilia Luque (a.cecilia.luque@gmail.com)
 *          Roman Birca (roman.birca@gmail.com)
 *
 */

package io
import scala.math._
import Chisel._
import Node._
import ocp._
import patmos.Constants._

object SdramController extends DeviceObject {
  var sdramAddrWidth = 13
  var sdramDataWidth = 32
  var ocpAddrWidth   = 25
  var ocpBurstLen    = 0

  def init(params: Map[String, String]) = {
    sdramAddrWidth  = getPosIntParam(params, "sdramAddrWidth")
    sdramDataWidth  = getPosIntParam(params, "sdramDataWidth")
    ocpAddrWidth    = getPosIntParam(params, "ocpAddrWidth")
    ocpBurstLen     = getPosIntParam(params, "ocpBurstLen")
  }

  def create(params: Map[String, String]): SdramController = {
    Module(new SdramController(sdramAddrWidth, sdramDataWidth, ocpAddrWidth, ocpBurstLen))
  }

  trait Pins {
    val sdramControllerPins = new Bundle {
      val ramOut = new Bundle {
        val dq   = Bits(OUTPUT, width = sdramDataWidth)
        val dqm  = Bits(OUTPUT, width = 4)
        val addr = Bits(OUTPUT, width = sdramDataWidth)
        val ba   = Bits(OUTPUT, width = 2)
        val clk  = Bits(OUTPUT, width = 1)
        val cke  = Bits(OUTPUT, width = 1)
        val ras  = Bits(OUTPUT, width = 1)
        val cas  = Bits(OUTPUT, width = 1)
        val we   = Bits(OUTPUT, width = 1)
        val cs   = Bits(OUTPUT, width = 1)
        val dqEn = Bits(OUTPUT, width = 1)
      }
      val ramIn = new Bundle {
        val dq    = Bits(INPUT, width = sdramAddrWidth)
      }
    }
  }
}

class SdramController(sdramAddrWidth: Int, sdramDataWidth: Int, 
  ocpAddrWidth: Int, ocpBurstLen : Int) extends BurstDevice(ocpAddrWidth) {

  override val io = new BurstDeviceIO(ocpAddrWidth) with SdramController.Pins
  val cmd         = io.ocp.M.Cmd
  
  private val high = Bits(1)
  private val low  = Bits(0)
  
  // Controller states
  val idle :: write :: read :: init_start :: refresh :: init_precharge :: init_refresh :: init_register :: Nil = Enum(UInt(), 8)
  val state = Reg(init = init_start);
  val memoryCmd = Reg(init = MemCmd.noOperation);
  val address = Reg(init = Bits(0))
  val initCycles = (0.0001*CLOCK_FREQ).toInt // Calculate number of cycles for init from processor clock freq
  val refreshRate = (0.064*CLOCK_FREQ).toInt
  val thisManyTimes = 8192
  val initCounter = Reg(init = Bits(initCycles))
  val refreshCounter = Reg(init = Bits(refreshRate))
  
  // counter used for burst
  val counter = Reg(init = Bits(0));

  // default assignments
  val slavePort = io.ocp.S
  val ramOut = io.sdramControllerPins.ramOut
  // Default assignemts to OCP slave signals
  slavePort.Resp       := OcpResp.NULL
  slavePort.CmdAccept  := Bits(1) 
  slavePort.DataAccept := Bits(1)
  slavePort.Data       := Bits(sdramDataWidth)
  // Default assignemts to SdramController OUTPUT signals
  ramOut.dqEn := Bits(1)
  ramOut.dq   := Bits(sdramDataWidth)         
  ramOut.dqm  := Bits(4)         
  ramOut.addr := Bits(sdramDataWidth)         
  ramOut.ba   := Bits(2)         
  ramOut.clk  := Bits(1)         
  ramOut.cke  := Bits(1)         
  ramOut.ras  := Bits(1)         
  ramOut.cas  := Bits(1)         
  ramOut.we   := Bits(1)         
  ramOut.cs   := Bits(1)

  // We need to provide a default value for the full word in order to be able to do subword assignation. This is to avoid the "Subword assignment requires a default value to have been assigned" chisel error
  io.sdramControllerPins.ramOut.addr := UInt(0)

  MemCmd.setToPins(memoryCmd,io)

  // Examples of use of the encoder/decoder of memCmd. We just need to send orders to the memory, so the decode function may be not used in the future
  // We need to provide a default value for the full word previous encoding in order to be able to do subword assignation. This is to avoid the "Subword assignment requires a default value to have been assigned" chisel error
  //val memCmd = MemCmd.getFromPins(io)
  //io.sdramControllerPins.ramOut.addr := UInt(34536)
  //MemCmd.setToPins(MemCmd.read, io) 
  
  refreshCounter := refreshCounter - Bits(1)
  
  // state machine for the ocp signal
  when(state === idle) {
    
    when (refreshCounter < Bits(3+ocpBurstLen)) { // 3+ocpBurstLen in order to make sure there is room for read/write
        memoryCmd := MemCmd.cbrAutoRefresh
        io.sdramControllerPins.ramOut.cs := low
        io.sdramControllerPins.ramOut.ras := low
        io.sdramControllerPins.ramOut.cas := low
        io.sdramControllerPins.ramOut.we := high
        refreshCounter := Bits(refreshRate)
        state := refresh
        
    } .elsewhen (cmd === OcpCmd.RD) {

        // Save address to later use
        address := io.ocp.M.Addr
        
        // Send ACT signal to mem where addr = OCP addr 22-13, ba1 = OCP addr 24, ba2 = OCP addr 23
        memoryCmd := MemCmd.bankActivate        
        io.sdramControllerPins.ramOut.addr(12,0) := address(22,13)
        io.sdramControllerPins.ramOut.ba := address(24,23)
        
        // send accept to ocp
        io.ocp.S.CmdAccept := high
        
        // reset burst counter
        counter := Bits(ocpBurstLen+2)
        
        // Set next state to write
        state := read

    }
    
    .elsewhen (cmd === OcpCmd.WR) {

        // Save address to later use
        address := io.ocp.M.Addr
        
        // Send ACT signal to mem where addr = OCP addr 22-13, ba1 = OCP addr 24, ba2 = OCP addr 23
        memoryCmd := MemCmd.bankActivate        
        io.sdramControllerPins.ramOut.addr(12,0) := address(22,13)
        io.sdramControllerPins.ramOut.ba := address(24,23)
        
        // send accept to ocp
        io.ocp.S.CmdAccept := high
        
        // reset burst counter
        counter := Bits(ocpBurstLen)
        
        // Set next state to write
        state := write

    }
    
    .elsewhen (cmd === OcpCmd.IDLE) {

        // Send Nop
        // Set next state to idle

    }
    
    .otherwise {
    
        // Manage all the other OCP commands that at the moment of writing this are not implemented
        
    }
  } 
  
  .elsewhen (state === write) {
  
    // Send write signal to memCmd with address and AUTO PRECHARGE enabled
    memoryCmd := MemCmd.write
    io.sdramControllerPins.ramOut.addr(9,0) := address(13,0)
    io.sdramControllerPins.ramOut.addr(10)  := high
    // set io.ocp.S.CmdAccept to HIGH only on first iteration
    io.ocp.S.CmdAccept := high & counter(2)  
    // set data and byte enable for read
    io.sdramControllerPins.ramOut.dq := io.ocp.M.Data
    io.sdramControllerPins.ramOut.dqm := io.ocp.M.DataByteEn
    
    // Either continue or stop burst
    when(counter > Bits(1)) {
        counter := counter - Bits(1)
        address := address + Bits(4)
        state := write
    } .otherwise {
        io.ocp.S.Resp := OcpResp.DVA
        state := idle
    }
    
  } 
  
  .elsewhen (state === read) {
  
    // Send read signal to memCmd with address and AUTO PRECHARGE enabled - Only on first iteration
    when (counter === Bits(2+ocpBurstLen)) {
        memoryCmd := MemCmd.read
        io.sdramControllerPins.ramOut.addr(9,0) := address(13,0)
        io.sdramControllerPins.ramOut.addr(10)  := high
    }
    
    when (counter < Bits(ocpBurstLen)) {
        io.ocp.S.Data := io.sdramControllerPins.ramIn.dq
        io.ocp.S.Resp := OcpResp.DVA
    }
    
    // go to next address for duration of burst
    when (counter > Bits(1)) {
        counter := counter - Bits(1)
        state := read
    } .otherwise { 
        state := idle
    }
  
  }
  
  // The following is all part of the initialization phase
  .elsewhen (state === init_start) {
    /* The 512Mb SDRAM is initialized after the power is applied
    *  to Vdd and Vddq (simultaneously) and the clock is stable
    *  with DQM High and CKE High. */
    io.sdramControllerPins.ramOut.cke := high
    io.sdramControllerPins.ramOut.dqm := Bits(15) //4 bit high
    
    /* A 100μs delay is required prior to issuing any command
    *  other than a COMMAND INHIBIT or a NOP. The COMMAND
    *  INHIBIT or NOP may be applied during the 100us period and
    *  should continue at least through the end of the period. */
    memoryCmd := MemCmd.noOperation
    when (initCounter>Bits(0)) {   
        initCounter := initCounter - Bits(1);
        state := init_start 
    } .otherwise {
        state := init_precharge 
        }
    
  } .elsewhen (state === init_precharge) {
    /* With at least one COMMAND INHIBIT or NOP command
    *  having been applied, a PRECHARGE command should
    *  be applied once the 100μs delay has been satisfied. All
    *  banks must be precharged. */
    memoryCmd := MemCmd.prechargeAllBanks
    state := init_refresh
    counter := high
    
  } .elsewhen (state === init_refresh) {
    /* at least two AUTO REFRESH cycles
    *  must be performed. */
    memoryCmd := MemCmd.cbrAutoRefresh
    when (counter===high) {
        counter := counter - Bits(1)
        state := init_refresh
    } .otherwise {
        state := init_register
    }
  
  } .elsewhen (state === init_register) {
    /* The mode register should be loaded prior to applying
    * any operational command because it will power up in an
    * unknown state. */
    
    /* Write Burst Mode
    *  0    Programmed Burst Length
    *  1    Single Location Access   */
    io.sdramControllerPins.ramOut.addr(9)       := low
    
    /* Operating mode 
    *  00   Standard Operation 
    *  --   Reserved            */
    io.sdramControllerPins.ramOut.addr(8,7)     := low
    
    /* Latency mode 
    *  010  2 cycles
    *  011  3 cycles
    *  ---  Reserved            */
    io.sdramControllerPins.ramOut.addr(8,7)     := Bits (2)
    
    /* Burst Type
    *  0    Sequential
    *  1    Interleaved         */
    io.sdramControllerPins.ramOut.addr(8,7)     := low
    
    /* Burst Length
    *  000  1
    *  001  2
    *  010  4
    *  011  8
    *  111  Full Page (for sequential type only)
    *  ---  Reserved            */
    io.sdramControllerPins.ramOut.addr(2,0)     := Bits(2) // Burst Length TODO: make this dynamic
  }
  
  .elsewhen (state === refresh) {
        memoryCmd := MemCmd.cbrAutoRefresh
        io.sdramControllerPins.ramOut.cs := low
        io.sdramControllerPins.ramOut.ras := low
        io.sdramControllerPins.ramOut.cas := low
        io.sdramControllerPins.ramOut.we := high
        
        when( refreshCounter > UInt(0) ) { // do it this many times
          refreshCounter := refreshCounter - UInt(1)
          state := refresh
        } .otherwise { 
          state := idle
        }
  }
  
  .otherwise { 
    // Used for standard register value update
    address := address;
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
}

// Memory controller internal states
private object ControllerState {
    val idle :: write :: read :: Nil = Enum(UInt(), 3)
}

private object MemCmd {
  // States obtained from the IS42/45R86400D/16320D/32160D datasheet, from the table "COMMAND TRUTH TABLE"
  val deviceDeselect :: noOperation :: burstStop :: read :: readWithAutoPrecharge :: write :: writeWithAutoPrecharge :: bankActivate :: prechargeSelectBank :: prechargeAllBanks :: cbrAutoRefresh :: selfRefresh :: modeRegisterSet :: Nil = Enum(UInt(), 13)
  
  // Syntactic sugar
  private val high = Bits(1)
  private val low  = Bits(0)

  // Public API for decoding
  def getFromPins(io: BurstDeviceIO with SdramController.Pins): UInt = {
    val cmd = getFromPinsImplementation(
                clk = io.sdramControllerPins.ramOut.clk,
                cs  = io.sdramControllerPins.ramOut.cs,
                ras = io.sdramControllerPins.ramOut.ras,
                cas = io.sdramControllerPins.ramOut.cas,
                we  = io.sdramControllerPins.ramOut.we,
                ba  = io.sdramControllerPins.ramOut.ba,
                a10 = io.sdramControllerPins.ramOut.addr(10)
              )
    return cmd
  }

  // Public API for encoding
  def setToPins(memCmd:UInt, io: BurstDeviceIO with SdramController.Pins) = {
    setToPinsImplementation(
      memCmd = memCmd,
      clk    = io.sdramControllerPins.ramOut.clk,
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
      - clk(n-1): in all cases of the "COMMAND TRUTH TABLE" is high
      - A12, A11, A9 to A0: data is allways going to be high or low, allways valid values
      - Valid states are not considered: they are allways going to be valid (it is not possible to have a signal with a value between low and high)
  */
  private def getFromPinsImplementation(clk: Bits, cs:Bits, ras:Bits, cas:Bits, we:Bits, ba:Bits, a10:Bits): UInt = {
    val reg  = Reg(UInt())

    when(cs === high) {
      reg := deviceDeselect
    }.elsewhen(cs === low && ras === high && cas === high && we === high) {
      reg := noOperation
    }.elsewhen(cs === low && ras === high && cas === high && we === low) {
      reg := burstStop
    }.elsewhen(cs === low && ras === high && cas === low && we === high && a10 === low) {
      reg := read
    }.elsewhen(cs === low && ras === high && cas === low && we === high && a10 === high) {
      reg := writeWithAutoPrecharge
    }.elsewhen(cs === low && ras === high && cas === low && we === low && a10 === low) {
      reg := write
    }.elsewhen(cs === low && ras === high && cas === low && we === low && a10 === high) {
      reg := writeWithAutoPrecharge
    }.elsewhen(cs === low && ras === low && cas === high && we === high) {
      reg := bankActivate
    }.elsewhen(cs === low && ras === low && cas === high && we === low && a10 === low) {
      reg := prechargeSelectBank
    }.elsewhen(cs === low && ras === low && cas === high && we === low && a10 === high) {
      reg := prechargeAllBanks
    }.elsewhen(clk === high && cs === low && ras === low && cas === low && we === high) {
      reg := cbrAutoRefresh
    }.elsewhen(clk === low && cs === low && ras === low && cas === low && we === high) {
      reg := selfRefresh
    }.elsewhen(cs === low && ras === low && cas === low && we === low && ba(0) === low && ba(1) === low && a10 === low) {
      reg := modeRegisterSet
    }.otherwise {
      // Entering here is an error
      reg := deviceDeselect
    }

    return reg
  }

  private def setToPinsImplementation(memCmd: UInt, clk: Bits, cs:Bits, ras:Bits, cas:Bits, we:Bits, ba:Bits, a10:Bits) = {
    when(memCmd === deviceDeselect) {
      clk := high; cs := high; ras := low; cas := low; we := low; a10 := low
    }.elsewhen(memCmd === burstStop) {
      clk := high; cs := low; ras := high; cas := high; we := low; a10 := low
    }.elsewhen(memCmd === read) {
      clk := high; cs := low; ras := high; cas := low; we := high; a10 := low
    }.elsewhen(memCmd === writeWithAutoPrecharge) {
      clk := high; cs := low; ras := high; cas := low; we := high; a10 := high
    }.elsewhen(memCmd === write) {
      clk := high; cs := low; ras := high; cas := low; we := low; a10 := low
    }.elsewhen(memCmd === writeWithAutoPrecharge) {
      clk := high; cs := low; ras := high; cas := low; we := low; a10 := high
    }.elsewhen(memCmd === bankActivate) {
      clk := high; cs := low; ras := low; cas := high; we := high; a10 := low
    }.elsewhen(memCmd === prechargeSelectBank) {
      clk := high; cs := low; ras := low; cas := high; we := low; a10 := low
    }.elsewhen(memCmd === prechargeAllBanks) {
      clk := high; cs := low; ras := low; cas := high; we := low; a10 := high
    }.elsewhen(memCmd === cbrAutoRefresh) {
      clk := high; cs := low; ras := low; cas := low; we := high; a10 := low
    }.elsewhen(memCmd === selfRefresh) {
      clk := low; cs := low; ras := low; cas := low; we := high; a10 := low
    }.elsewhen(memCmd === modeRegisterSet) {
      clk := high; cs := low; ras := low; cas := low; we := low; a10 := low
    }.otherwise { // assumes memCmd === noOperation
      clk := high; cs := low; ras := high; cas := high; we := high; a10 := low
    }
  }
}
