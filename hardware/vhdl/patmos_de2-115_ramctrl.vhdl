--
-- Copyright: 2013, Technical University of Denmark, DTU Compute
-- Author: Martin Schoeberl (martin@jopdesign.com)
--         Rasmus Bo Soerensen (rasmus@rbscloud.dk)
-- License: Simplified BSD License
--

-- VHDL top level for Patmos in Chisel on Altera de2-115 board
--
-- Includes some 'magic' VHDL code to generate a reset after FPGA configuration.
--

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity patmos_top is
  port(
    clk : in  std_logic;
    oLedsPins_led : out std_logic_vector(8 downto 0);
    iKeysPins_key : in std_logic_vector(3 downto 0);
    oUartPins_txd : out std_logic;
    iUartPins_rxd : in  std_logic;
    
    -- memory interface
    dram_CLK  : out std_logic;                      -- Clock
    dram_CKE  : out std_logic;                      -- Clock Enable
    dram_RAS  : out std_logic;                      -- Row Address Strobe
    dram_CAS  : out std_logic;                      -- Column Address Strobe
    dram_WE   : out std_logic;                      -- Write Enable
    dram_CS   : out std_logic;                      -- Chip Select
    dram_BA   : out std_logic_vector(1 downto 0);   -- Bank Address
    dram_ADDR : out std_logic_vector(12 downto 0);  -- SDRAM Address
    dram_DQM  : out std_logic_vector(3 downto 0);   -- SDRAM byte Data Mask
    dram_LED  : out std_logic_vector(7 downto 0);   -- LED, for testing purposes (to be removed)

    -- data bus to and from the chips
    dram_DQ   : inout std_logic_vector(31 downto 0)
  );
end entity patmos_top;

architecture rtl of patmos_top is
  component Patmos is
    port(
      clk             : in  std_logic;
      reset           : in  std_logic;

      io_comConf_M_Cmd        : out std_logic_vector(2 downto 0);
      io_comConf_M_Addr       : out std_logic_vector(31 downto 0);
      io_comConf_M_Data       : out std_logic_vector(31 downto 0);
      io_comConf_M_ByteEn     : out std_logic_vector(3 downto 0);
      io_comConf_M_RespAccept : out std_logic;
      io_comConf_S_Resp       : in std_logic_vector(1 downto 0);
      io_comConf_S_Data       : in std_logic_vector(31 downto 0);
      io_comConf_S_CmdAccept  : in std_logic;

      io_comSpm_M_Cmd         : out std_logic_vector(2 downto 0);
      io_comSpm_M_Addr        : out std_logic_vector(31 downto 0);
      io_comSpm_M_Data        : out std_logic_vector(31 downto 0);
      io_comSpm_M_ByteEn      : out std_logic_vector(3 downto 0);
      io_comSpm_S_Resp        : in std_logic_vector(1 downto 0);
      io_comSpm_S_Data        : in std_logic_vector(31 downto 0);

      io_cpuInfoPins_id   : in  std_logic_vector(31 downto 0);
      io_cpuInfoPins_cnt  : in  std_logic_vector(31 downto 0);
      io_ledsPins_led : out std_logic_vector(8 downto 0);
      io_keysPins_key : in  std_logic_vector(3 downto 0);
      io_uartPins_tx  : out std_logic;
      io_uartPins_rx  : in  std_logic;

      -- controller pll ready
      io_sdramControllerPins_ramIn_pllReady : in std_logic;
      
      -- controller data in
      io_sdramControllerPins_ramIn_dq       : in std_logic_vector(31 downto 0);
      
      -- SDRAM OUTs
      io_sdramControllerPins_ramOut_cke   : out std_logic;
      io_sdramControllerPins_ramOut_ras   : out std_logic;
      io_sdramControllerPins_ramOut_cas   : out std_logic;
      io_sdramControllerPins_ramOut_we    : out std_logic;
      io_sdramControllerPins_ramOut_cs    : out std_logic;
      io_sdramControllerPins_ramOut_ba    : out std_logic_vector(1 downto 0);         
      io_sdramControllerPins_ramOut_addr  : out std_logic_vector(12 downto 0);         
      io_sdramControllerPins_ramOut_dqm   : out std_logic_vector(3 downto 0);         
      io_sdramControllerPins_ramOut_dq    : out std_logic_vector(31 downto 0);
      io_sdramControllerPins_ramOut_dqEn  : out std_logic;      
      io_sdramControllerPins_ramOut_led   : out std_logic_vector(7 downto 0)
      );
  end component;

  component de2_115_sdram_pll is
    port(
      inclk0  : in std_logic := '0';
      c0      : out std_logic;
      c1      : out std_logic;
      c2      : out std_logic;
      locked  : out std_logic
    );
  end component;

  signal sys_clk  : std_logic;
  signal pllReady : std_logic;
  --signal dram_clk_skew  : std_logic;

  -- for generation of internal reset
  signal rst      : std_logic;
  signal rst_n    : std_logic;
  signal rst_cnt  : unsigned(2 downto 0) := "000"; -- for the simulation

  -- sdram signals for tristate inout
  signal sdram_out_dout_ena : std_logic;
  signal sdram_out_dout : std_logic_vector(31 downto 0);

  attribute altera_attribute : string;
  attribute altera_attribute of rst_cnt : signal is "POWER_UP_LEVEL=LOW";

begin
  -- tristate to accomodate for chisel lacking an inout port
  process(sdram_out_dout_ena, sdram_out_dout)
  begin
    if sdram_out_dout_ena='1' then
      dram_DQ <= sdram_out_dout;
    else
      dram_DQ <= (others => 'Z');
    end if;
  end process;

  -- reset process
  process(sys_clk, pllReady)
  begin
    if pllReady = '0' then
      rst_cnt <= "000";
      rst     <= '1';
    elsif rising_edge(sys_clk) then
      if (rst_cnt /= "111") then
        rst_cnt <= rst_cnt + 1;
      end if;
      rst <= not rst_cnt(0) or not rst_cnt(1) or not rst_cnt(2);
    end if;
  end process;

  pll : de2_115_sdram_pll
  port map(
    inclk0  => clk, --50 MHz clock from the board
    c0      => sys_clk,      
    c1      => open,
    c2      => dram_CLK,      
    locked  => pllReady
  );

  patmos_inst : Patmos 
  port map (
    clk => sys_clk, 
    reset => rst,

    io_comConf_M_Cmd => open,
    io_comConf_M_Addr => open,
    io_comConf_M_Data => open,
    io_comConf_M_ByteEn => open,
    io_comConf_M_RespAccept => open,
    io_comConf_S_Resp => (others => '0'),
    io_comConf_S_Data => (others => '0'),
    io_comConf_S_CmdAccept => '0',

    io_comSpm_M_Cmd => open,
    io_comSpm_M_Addr => open,
    io_comSpm_M_Data => open,
    io_comSpm_M_ByteEn => open,
    io_comSpm_S_Resp => (others => '0'),
    io_comSpm_S_Data => (others => '0'),

    io_cpuInfoPins_id => X"00000000",
    io_cpuInfoPins_cnt => X"00000001",
    io_ledsPins_led => oLedsPins_led,
    io_keysPins_key => iKeysPins_key,
    io_uartPins_tx => oUartPins_txd,
    io_uartPins_rx => iUartPins_rxd,

    io_sdramControllerPins_ramOut_cke   => dram_CKE, 
    io_sdramControllerPins_ramOut_ras   => dram_RAS, 
    io_sdramControllerPins_ramOut_cas   => dram_CAS, 
    io_sdramControllerPins_ramOut_we    => dram_WE,
    io_sdramControllerPins_ramOut_cs    => dram_CS,
    io_sdramControllerPins_ramOut_ba    => dram_BA,
    io_sdramControllerPins_ramOut_addr  => dram_ADDR,
    io_sdramControllerPins_ramOut_dqm   => dram_DQM,
    io_sdramControllerPins_ramOut_dq    => sdram_out_dout,
    io_sdramControllerPins_ramOut_dqEn  => sdram_out_dout_ena,      
    io_sdramControllerPins_ramOut_led   => dram_LED,
    io_sdramControllerPins_ramIn_dq     => dram_DQ,
    io_sdramControllerPins_ramIn_pllReady => pllReady
  );

end architecture rtl;
