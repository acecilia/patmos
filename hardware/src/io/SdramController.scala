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
 *          Martin Obel Thomsen   (s134862@student.dtu.dk)
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
  private var ocpAddrWidth   = 27 // bank(2) + row(13) + column(10) + OcpMAddrDummyBits(2) = 27

  def init(params: Map[String, String]) = {
    sdramDataWidth  = getPosIntParam(params, "sdramDataWidth")
  }

  def create(params: Map[String, String]): SdramController = {
    Module(new SdramController(ocpBurstLen=BURST_LENGTH))
  }

  trait Pins {
    val sdramControllerPins = new Bundle {
      val ramOut = new Bundle {
        // The total 128MB SDRAM is implemented using two 64MB SDRAM devices (page 64 of the DE2-115 manual. "dq" and "dqm" will need to be splitted at pin assignment)
        val dq  = Bits(OUTPUT, sdramDataWidth) 
        val dqm = Bits(OUTPUT, 4)

        // Common signals for the two 64MB SDRAM devices
        val addr = Bits(OUTPUT, sdramAddrWidth)
        val ba   = Bits(OUTPUT, 2)
        val cke  = Bits(OUTPUT, 1)
        val ras  = Bits(OUTPUT, 1)
        val cas  = Bits(OUTPUT, 1)
        val we   = Bits(OUTPUT, 1)
        val cs   = Bits(OUTPUT, 1)
        val dqEn = Bits(OUTPUT, 1)
        val led  = Bits(OUTPUT, 8)

        // Need to add the memoryCmd in the sdramControllerPins for being able to test it
        val memoryCmd = UInt(OUTPUT)
      }
      val ramIn = new Bundle {
        val pllReady  = Bits(INPUT, 1)
        val dq        = Bits(INPUT, sdramDataWidth)
      }
    }
  }
}

class SdramController(ocpBurstLen : Int) extends BurstDevice(SdramController.ocpAddrWidth) {
  override val io  = new BurstDeviceIO(SdramController.ocpAddrWidth) with SdramController.Pins
  
  /*
  General information:
  -> KEY TIMING PARAMETERS for the SDRAM on the DE2-115 = -7
  */

  // Syntactic sugar
  val ocpCmd        = io.ocp.M.Cmd
  val ocpSlavePort  = io.ocp.S
  val ocpMasterPort = io.ocp.M
  val ramOut        = io.sdramControllerPins.ramOut
  val ramIn         = io.sdramControllerPins.ramIn
  val memoryCmd     = io.sdramControllerPins.ramOut.memoryCmd
  val high          = Bits("b1")
  val low           = Bits("b0")

  val state          = Reg(init = ControllerState.waitPll) // Controller state
  val tmpState       = Reg(init = ControllerState.idle)
  val initCycles     = 8000

  /*
  REFRESH
  From the datasheet: (trc) is required for a single refresh operation, and no other commands can be executed during this period. This command is executed at least 8192 times for every Tref period.
  -> trc(-7) = 60ns min
  -> Tref = 64ms max

  => Frequency to execute the refresh command => 8192/Tref = 8192/0.064 = 128000 times per second => 80MHz/128000 = execute every 625 cycles
  => After issuing the refresh command we have to wait trc => 80MHz*60ns = 4.8 clocks min ≈ 5 clocks 
  */
  val refreshRate    = 625
  val trc            = 5
  val refreshCounter = Reg(init = Bits(refreshRate))

  /*
  ACTIVATE
  From the datasheet: after opening a row (issuing an ACTIVE command), a READ or WRITE command may be issued to that row, subject to the trcd speci cation. Minimum trcd should be divided by the clock period and rounded up to the next whole number to determine the earliest clock edge after the ACTIVE command on which a READ or WRITE command can be entered
  -> trcd(-7) = 15ns min

  => After issuing the activate command we have to wait trcd => 80MHz*15ns = 1.2 clocks min ≈ 2 clocks 
  */
  val trcd = 2

  /*
  PRECHARGE
  From the datasheet: after executing this command, the next command for the selected bank(s) is executed after passage of the period tRP, which is the period required for bank precharging.
  -> trp(-7) = 15ns min

  => After issuing the precharge command we have to wait trp => 80MHz*15ns = 1.2 clocks min ≈ 2 clocks (also for auto-precharge)
  */
  val trp = 2

  /*
  TCAS
  From the datasheet: during READ bursts, the valid data-out element from the starting column address will be available following the CAS latency after the READ command. Each subsequent data-out element will be valid by the next positive clock edge
  */
  val tcas = 2

  /*
  READ TIME
  */
  val tread = ocpBurstLen + max(tcas, trp)

  /*
  ADDRESS MAPPING
  From the PATMOS manual: MAddr = Address, byte-based, lowest two bits always 0
  
  According to Luca's code:
  ocpMaster.MAddr              => M_Addr(26 downto 2)

  constant CS_WIDTH    : integer := 0; -- 1 rank
  constant COL_LOW_BIT : integer := 0;
  constant ROW_LOW_BIT : integer := COL_LOW_BIT + SDRAM.COL_WIDTH; -- 10
  constant BA_LOW_BIT  : integer := ROW_LOW_BIT + SDRAM.ROW_WIDTH; -- 10+13=23
  constant CS_LOW_BIT  : integer := BA_LOW_BIT + SDRAM.BA_WIDTH; -- 23+2=25
  */
  val column = Bits(width = 10)
  val row    = Bits(width = 13)
  val bank   = Bits(width = 2)
  column := ocpMasterPort.Addr(11,2)
  row    := ocpMasterPort.Addr(24,12)
  bank   := ocpMasterPort.Addr(26,25)


  val initCounter = Reg(init = Bits(initCycles))
  val counter     = Reg(init = Bits(0))

  // Default value for signals
  memoryCmd := MemCmd.noOperation

  // Default assignemts to OCP slave signals
  ocpSlavePort.Resp       := OcpResp.NULL
  ocpSlavePort.CmdAccept  := low 
  ocpSlavePort.DataAccept := low
  ocpSlavePort.Data       := low

  // Default assignemts to SdramController output signals
  ramOut.dqEn := low
  ramOut.dq   := low 
  ramOut.dqm  := low 
  ramOut.addr := low        
  ramOut.ba   := low         
  ramOut.cke  := low        
  ramOut.ras  := low        
  ramOut.cas  := low         
  ramOut.we   := low        
  ramOut.cs   := high

  // Code for using a led for real-time testing
  val refreshRateAux = 1000
  val ledReg         = Reg(init = Bits(0))
  ramOut.led := ledReg

  val counterAux = Reg(init = Bits(refreshRateAux))
  when(counterAux === Bits(1)) {
    ledReg(0) := ~ledReg(0)
    counterAux := Bits(refreshRateAux)
  }

  val counterAux1 = Reg(init = Bits(refreshRateAux))
  when(counterAux1 === Bits(1)) {
    ledReg(5) := ~ledReg(5)
    counterAux1 := Bits(refreshRateAux)
  }

  // state machine for the ocp signal
  when(state === ControllerState.waitPll) {
    when(ramIn.pllReady === high) {
      state := ControllerState.initStart
    }
  }
  .elsewhen(state === ControllerState.idle) {   
    memoryCmd := MemCmd.noOperation               // When idle, the memory is in noOperation state
    refreshCounter := refreshCounter - Bits(1)    // Wait until refresh is needed

    when(refreshCounter <= Bits(1)) {            // Time to refresh, we use <= to be sure, in case the counter is negative
        counter := Bits(trc)                      // We have to wait Trc until coming back from auto-refresh
        state := ControllerState.refresh
    } 

    .elsewhen(ocpCmd === OcpCmd.RD || ocpCmd === OcpCmd.WR) {
        ledReg(4) := high
        
        // Set state to jump to after activation
        when(ocpCmd === OcpCmd.RD) {
          tmpState := ControllerState.read 
        }.elsewhen(ocpCmd === OcpCmd.WR) {
          tmpState := ControllerState.write 
        }
        
        counter := Bits(trcd)                     // Prepare next state: activate
        state := ControllerState.activate         // Change to activate state
    }
    
    .elsewhen(ocpCmd === OcpCmd.WR) {  
        ledReg(2) := high
        // address := ocpMasterPort.Addr             // Save address for later use
        tmpState := ControllerState.write         // Set future state
        
        counter := Bits(trcd)                     // Prepare next state: activate
        state := ControllerState.activate         // Change to activate state
    }
    
    .elsewhen(ocpCmd === OcpCmd.IDLE) {
        ledReg(1) := high
        // Send Nop
        // Set next state to idle
    }
    
    .otherwise {
        // Manage all the other OCP commands that at the moment of writing this are not implemented
    }
  } 
  
  .elsewhen(state === ControllerState.write) {
    counter := counter - Bits(1)

    // Logic for interacting with OCP
    // Start and keep writting until burstStop
    when(counter > Bits(trp)) {
      // Accept the command
      when(counter === Bits(ocpBurstLen + trp)) {
        /*
        CmdAccept should be issued together with DataAccept.
        Form Patmos handbook: the signals SCmdAccept and SDataAccept can be generated by the same logic. For the acceptance of write commands they must be identical, otherwise at least one of the signals can have an undefined value.
        */
        ocpSlavePort.CmdAccept := high
      }

      ramOut.dq := ocpMasterPort.Data
      ramOut.dqEn := high                       // byte enable for read (for the tristate)
      /*
      We have to invert the level of DataByteEn when passing it to dqm.
      From the OCP specification document: there is one bit in MDataByteEn for each byte in the OCP word. Setting MDataByteEn[n] to 1 indicates that the byte associated with MData wires [(8n + 7):8n] should be transferred

      From the SDRAM datasheet: DQML and DQMH control the output buffer. When DQML or DQMH is LOW, the corresponding buffer byte is enabled, and when HIGH, disabled
      */
      ramOut.dqm := ~ocpMasterPort.DataByteEn    // Subword writing
      ocpSlavePort.DataAccept := high           // Accept data
    }
    // Confirm write by responding
    .elsewhen(counter === Bits(trp)) {
      ocpSlavePort.Resp := OcpResp.DVA
    }

    // Logic for interacting with the SDRAM
    // Start write
    when(counter === Bits(ocpBurstLen + trp)) {
      memoryCmd := MemCmd.writeWithAutoPrecharge
      ramOut.addr := column
      ramOut.ba := bank
    }
    // Wait until burst finishes
    .elsewhen(counter > Bits(trp)) {
      memoryCmd := MemCmd.noOperation
    }
    // Stop burst
    .elsewhen(counter === Bits(trp)) {
      memoryCmd := MemCmd.burstStop
    }
    //Wait for tcp
    .elsewhen(counter >= Bits(1)) {
      memoryCmd := MemCmd.noOperation

      when(counter === Bits(1)) {
        counterAux := counterAux - Bits(1)  // Temporal led test
        state := ControllerState.idle
      }
    }
  } 
  
  .elsewhen(state === ControllerState.read) {
    counter := counter - Bits(1)

    // Logic for interacting with OCP
    // Do nothing until tcas has passed
    when(counter >= Bits(tread - tcas + 1)) {
      // Accept the command 1 cycle previous sending the data
      when(counter === Bits(tread - tcas + 1)) {
        ocpSlavePort.CmdAccept := high
      }
    }
    // Send the data
    .elsewhen(counter > Bits(tread - tcas - ocpBurstLen)) {
      ocpSlavePort.Data := ramIn.dq
      ocpSlavePort.Resp := OcpResp.DVA
      ramOut.dqEn := low
    }

    // Logic for interacting with the SDRAM
    // Start read
    when(counter === Bits(tread)) {
      memoryCmd := MemCmd.readWithAutoPrecharge
      ramOut.addr(9,0) := column
      ramOut.ba := bank
      ramOut.dqm := low
    }
    // Wait until burst finishes
    .elsewhen(counter > Bits(tread - ocpBurstLen + 1)) {
      memoryCmd := MemCmd.noOperation
      ramOut.dqm := low
    }
    // Stop burst
    .elsewhen(counter === Bits(tread - ocpBurstLen + 1)) {
      memoryCmd := MemCmd.burstStop
      ramOut.dqm := low
    }
    // Wait for trp
    .elsewhen(counter > Bits(tread - ocpBurstLen - trp)) {
      memoryCmd := MemCmd.noOperation 

      when(counter === Bits(1)) {
        counterAux1 := counterAux1 - Bits(1)  // Temporal led test
        state := ControllerState.idle
      }
    }
  }
  
  // The following is all part of the initialization phase
  .elsewhen(state === ControllerState.initStart) {
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
    when(initCounter > Bits(1)) {   
        initCounter := initCounter - Bits(1);
        state := ControllerState.initStart 
    } .otherwise {
        state := ControllerState.initPrecharge 
    }
    
  } .elsewhen(state === ControllerState.initPrecharge) {
    /* With at least one COMMAND INHIBIT or NOP command
    *  having been applied, a PRECHARGE command should
    *  be applied once the 100μs delay has been satisfied. All
    *  banks must be precharged. */
    memoryCmd := MemCmd.prechargeAllBanks
    state := ControllerState.initRefresh
    counter := high
    
  } .elsewhen(state === ControllerState.initRefresh) {
    /* at least two AUTO REFRESH cycles
    *  must be performed. */
    memoryCmd := MemCmd.cbrAutoRefresh
    when(counter===high) {
        counter := counter - Bits(1)
        state := ControllerState.initRefresh
    } .otherwise {
        state := ControllerState.initRegister
    }
  
  } .elsewhen(state === ControllerState.initRegister) {
    /* The mode register should be loaded prior to applying
    * any operational command because it will power up in an
    * unknown state. */
    
    /* Write Burst Mode
    *  0    Programmed Burst Length
    *  1    Single Location Access   */
    ramOut.addr(9) := Bits(0)
    
    /* Operating mode 
    *  00   Standard Operation 
    *  --   Reserved            */
    ramOut.addr(8,7) := Bits(0)
    
    /* Latency mode 
    *  010  2 cycles
    *  011  3 cycles
    *  ---  Reserved            */
    ramOut.addr(6,4) := Bits(tcas)
    
    /* Burst Type
    *  0    Sequential
    *  1    Interleaved         */
    ramOut.addr(3) := Bits(0)
    
    /* Burst Length
    *  000  1
    *  001  2
    *  010  4
    *  011  8
    *  111  Full Page (for sequential type only)
    *  ---  Reserved            */
    ramOut.addr(2,0) := Bits(7) // Burst Length TODO: make this dynamic
    
    memoryCmd := MemCmd.modeRegisterSet
    state := ControllerState.idle
  }
  
  .elsewhen(state === ControllerState.refresh) {
      counter := counter - Bits(1)

      when(counter === Bits(trc)) {
        memoryCmd := MemCmd.cbrAutoRefresh  // Send the auto-refresh command for one cycle
      }
      .elsewhen(counter > Bits(1)) {
        memoryCmd := MemCmd.noOperation     // Wait trc
      }
      .otherwise {
        refreshCounter := Bits(refreshRate) // Restart the refresh counter
        state := ControllerState.idle       // Go back to idle
      }
  }

  .elsewhen(state === ControllerState.activate) {
    counter := counter - Bits(1)          // Decrement counter, waiting for activation

    when(counter === Bits(trcd)) {
      memoryCmd := MemCmd.bankActivate        
      ramOut.addr(12,0) := row                // Activate row
      ramOut.ba := bank                       // Activate bank
    }
    .elsewhen(counter > Bits(1)) {
      memoryCmd := MemCmd.noOperation     // Wait trcd
    }
    .otherwise {
      memoryCmd := MemCmd.noOperation

      when(tmpState === ControllerState.write) {
        counter := Bits(ocpBurstLen + trp)
      }.elsewhen(tmpState === ControllerState.read) {
        counter := Bits(tread)
      }
      state := tmpState
    }
  }

  MemCmd.setToPins(memoryCmd, io)
}

// Memory controller internal states
object ControllerState {
    val waitPll :: idle :: write :: read :: refresh :: activate :: initStart :: initPrecharge :: initRefresh :: initRegister :: Nil = Enum(UInt(), 10)
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
      cs := low; ras := high; cas := high; we := high
    }.elsewhen(memCmd === burstStop) {
      cs := low; ras := high; cas := high; we := low
    }.elsewhen(memCmd === read) {
      cs := low; ras := high; cas := low; we := high; a10 := low
    }.elsewhen(memCmd === readWithAutoPrecharge) {
      cs := low; ras := high; cas := low; we := high; a10 := high
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
