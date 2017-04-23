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
    dram_CLK  : out std_logic;   -- Clock
    dram_CKE  : out std_logic;   -- Clock Enable
    dram_RAS  : out std_logic;   -- Row Address Strobe
    dram_CAS  : out std_logic;   -- Column Address Strobe
    dram_WE   : out std_logic;   -- Write Enable
    dram_CS   : out std_logic;   -- Chip Select
    dram_BA   : out std_logic_vector(1 downto 0);   -- Bank Address
    dram_ADDR : out std_logic_vector(12 downto 0); -- SDRAM Address
    dram_DQM  : out std_logic_vector(3 downto 0); -- SDRAM byte Data Mask

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

      -- SDRAM OUTs
      io_sdramControllerPins_ramOut_clk   : out std_logic;
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
      io_sdramControllerPins_ramIn_dq     : in std_logic_vector(31 downto 0)
    );
  end component;

  -- DE2-70: 50 MHz clock => 80 MHz
  -- BeMicro: 16 MHz clock => 25.6 MHz
  constant pll_infreq : real    := 50.0;
  constant pll_mult   : natural := 8;
  constant pll_div    : natural := 5;

  signal clk_int : std_logic;

  -- for generation of internal reset
  signal int_res            : std_logic;
  signal res_reg1, res_reg2 : std_logic;
  signal res_cnt            : unsigned(2 downto 0) := "000"; -- for the simulation

  -- sdram signals for tristate inout
  signal sdram_out_dout_ena : std_logic;
  signal sdram_out_dout : std_logic_vector(31 downto 0);

  attribute altera_attribute : string;
  attribute altera_attribute of res_cnt : signal is "POWER_UP_LEVEL=LOW";

begin
  dram_CLK <= clk_int;
  pll_inst : entity work.pll generic map(
      input_freq  => pll_infreq,
      multiply_by => pll_mult,
      divide_by   => pll_div
    )
    port map(
      inclk0 => clk,
      c0     => clk_int
    );
  -- we use a PLL
  -- clk_int <= clk;

  --
  --  internal reset generation
  --  should include the PLL lock signal
  --
  process(clk_int)
  begin
    if rising_edge(clk_int) then
      if (res_cnt /= "111") then
        res_cnt <= res_cnt + 1;
      end if;
      res_reg1 <= not res_cnt(0) or not res_cnt(1) or not res_cnt(2);
      res_reg2 <= res_reg1;
      int_res  <= res_reg2;
    end if;
  end process;

  process(sdram_out_dout_ena, sdram_out_dout)
  begin
    if sdram_out_dout_ena='1' then
      dram_DQ <= sdram_out_dout;
    else
      dram_DQ <= (others => 'Z');
    end if;
  end process;

    patmos_inst : Patmos 
    port map (
      clk => clk_int, 
      reset => int_res,

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

      io_sdramControllerPins_ramOut_clk   => open, 
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
      io_sdramControllerPins_ramIn_dq     => dram_DQ
    );

end architecture rtl;
