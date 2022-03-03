//
// Generated by Bluespec Compiler
//
//
// Ports:
// Name                         I/O  size props
// req                            O     1 reg
// RDY_req                        O     1
// RDY_resp                       O     1 const
// checkHandle                    O     1
// RDY_checkHandle                O     1 const
// peek                           O    32 reg
// RDY_peek                       O     1 const
// CLK                            I     1 clock
// RST_N                          I     1 reset
// req_num                        I    32
// req_denom                      I    32
// req_quot                       I    32
// req_acc                        I    32
// req_cnt                        I     5
// req_retQuot                    I     1
// checkHandle_handle             I     1
// EN_resp                        I     1
// EN_req                         I     1
//
// Combinational paths from inputs to outputs:
//   checkHandle_handle -> checkHandle
//
//

`ifdef BSV_ASSIGNMENT_DELAY
`else
  `define BSV_ASSIGNMENT_DELAY
`endif

`ifdef BSV_POSITIVE_RESET
  `define BSV_RESET_VALUE 1'b1
  `define BSV_RESET_EDGE posedge
`else
  `define BSV_RESET_VALUE 1'b0
  `define BSV_RESET_EDGE negedge
`endif

module mkMulti_stg_div(CLK,
		       RST_N,

		       req_num,
		       req_denom,
		       req_quot,
		       req_acc,
		       req_cnt,
		       req_retQuot,
		       EN_req,
		       req,
		       RDY_req,

		       EN_resp,
		       RDY_resp,

		       checkHandle_handle,
		       checkHandle,
		       RDY_checkHandle,

		       peek,
		       RDY_peek);
  input  CLK;
  input  RST_N;

  // actionvalue method req
  input  [31 : 0] req_num;
  input  [31 : 0] req_denom;
  input  [31 : 0] req_quot;
  input  [31 : 0] req_acc;
  input  [4 : 0] req_cnt;
  input  req_retQuot;
  input  EN_req;
  output req;
  output RDY_req;

  // action method resp
  input  EN_resp;
  output RDY_resp;

  // value method checkHandle
  input  checkHandle_handle;
  output checkHandle;
  output RDY_checkHandle;

  // value method peek
  output [31 : 0] peek;
  output RDY_peek;

  // signals for module outputs
  wire [31 : 0] peek;
  wire RDY_checkHandle, RDY_peek, RDY_req, RDY_resp, checkHandle, req;

  // inlined wires
  wire [134 : 0] fifo__input__TO_Start_enq_data$wget;
  wire fifo__input__TO_Start_enq_data$whas;

  // register _unnamed_
  reg _unnamed_;
  wire _unnamed_$D_IN, _unnamed_$EN;

  // register busyReg
  reg busyReg;
  wire busyReg$D_IN, busyReg$EN;

  // register outputQueue_nextTag_rl
  reg outputQueue_nextTag_rl;
  wire outputQueue_nextTag_rl$D_IN, outputQueue_nextTag_rl$EN;

  // register outputQueue_val
  reg [32 : 0] outputQueue_val;
  wire [32 : 0] outputQueue_val$D_IN;
  wire outputQueue_val$EN;

  // ports of submodule fifo__input__TO_Start_f
  wire [134 : 0] fifo__input__TO_Start_f$D_IN, fifo__input__TO_Start_f$D_OUT;
  wire fifo__input__TO_Start_f$CLR,
       fifo__input__TO_Start_f$DEQ,
       fifo__input__TO_Start_f$EMPTY_N,
       fifo__input__TO_Start_f$ENQ,
       fifo__input__TO_Start_f$FULL_N;

  // ports of submodule outputQueue_nextTag_dummy2_0
  wire outputQueue_nextTag_dummy2_0$D_IN,
       outputQueue_nextTag_dummy2_0$EN,
       outputQueue_nextTag_dummy2_0$Q_OUT;

  // ports of submodule outputQueue_nextTag_dummy2_1
  wire outputQueue_nextTag_dummy2_1$D_IN,
       outputQueue_nextTag_dummy2_1$EN,
       outputQueue_nextTag_dummy2_1$Q_OUT;

  // rule scheduling signals
  wire WILL_FIRE_RL_s_Start_execute;

  // inputs to muxes for submodule ports
  wire [134 : 0] MUX_fifo__input__TO_Start_enq_data$wset_1__VAL_1,
		 MUX_fifo__input__TO_Start_enq_data$wset_1__VAL_2;
  wire [32 : 0] MUX_outputQueue_val$write_1__VAL_1;
  wire MUX_busyReg$write_1__SEL_1,
       MUX_fifo__input__TO_Start_enq_data$wset_1__SEL_1;

  // remaining internal signals
  wire [31 : 0] IF_fifo__input__TO_Start_f_first__3_BITS_37_TO_ETC___d36,
		b__h1819;
  wire IF_outputQueue_nextTag_lat_0_whas_THEN_outputQ_ETC___d10,
       b__h1320,
       fifo__input__TO_Start_f_first__3_BITS_37_TO_7__ETC___d32;

  // actionvalue method req
  assign req = _unnamed_ ;
  assign RDY_req = !busyReg && fifo__input__TO_Start_f$FULL_N ;

  // action method resp
  assign RDY_resp = 1'd1 ;

  // value method checkHandle
  assign checkHandle =
	     (outputQueue_nextTag_dummy2_0$Q_OUT &&
	      outputQueue_nextTag_dummy2_1$Q_OUT &&
	      outputQueue_nextTag_rl) ==
	     checkHandle_handle &&
	     outputQueue_val[32] ;
  assign RDY_checkHandle = 1'd1 ;

  // value method peek
  assign peek = outputQueue_val[31:0] ;
  assign RDY_peek = 1'd1 ;

  // submodule fifo__input__TO_Start_f
  FIFO2 #(.width(32'd135),
	  .guarded(1'd1)) fifo__input__TO_Start_f(.RST(RST_N),
						  .CLK(CLK),
						  .D_IN(fifo__input__TO_Start_f$D_IN),
						  .ENQ(fifo__input__TO_Start_f$ENQ),
						  .DEQ(fifo__input__TO_Start_f$DEQ),
						  .CLR(fifo__input__TO_Start_f$CLR),
						  .D_OUT(fifo__input__TO_Start_f$D_OUT),
						  .FULL_N(fifo__input__TO_Start_f$FULL_N),
						  .EMPTY_N(fifo__input__TO_Start_f$EMPTY_N));

  // submodule outputQueue_nextTag_dummy2_0
  RevertReg #(.width(32'd1),
	      .init(1'd1)) outputQueue_nextTag_dummy2_0(.CLK(CLK),
							.D_IN(outputQueue_nextTag_dummy2_0$D_IN),
							.EN(outputQueue_nextTag_dummy2_0$EN),
							.Q_OUT(outputQueue_nextTag_dummy2_0$Q_OUT));

  // submodule outputQueue_nextTag_dummy2_1
  RevertReg #(.width(32'd1),
	      .init(1'd1)) outputQueue_nextTag_dummy2_1(.CLK(CLK),
							.D_IN(outputQueue_nextTag_dummy2_1$D_IN),
							.EN(outputQueue_nextTag_dummy2_1$EN),
							.Q_OUT(outputQueue_nextTag_dummy2_1$Q_OUT));

  // rule RL_s_Start_execute
  assign WILL_FIRE_RL_s_Start_execute =
	     fifo__input__TO_Start_f$EMPTY_N &&
	     (fifo__input__TO_Start_f$D_OUT[6:2] == 5'd31 ||
	      fifo__input__TO_Start_f$FULL_N) &&
	     (fifo__input__TO_Start_f$D_OUT[6:2] != 5'd31 ||
	      (outputQueue_nextTag_dummy2_1$Q_OUT &&
	       IF_outputQueue_nextTag_lat_0_whas_THEN_outputQ_ETC___d10) ==
	      fifo__input__TO_Start_f$D_OUT[0]) ;

  // inputs to muxes for submodule ports
  assign MUX_busyReg$write_1__SEL_1 =
	     WILL_FIRE_RL_s_Start_execute &&
	     fifo__input__TO_Start_f$D_OUT[6:2] == 5'd31 ;
  assign MUX_fifo__input__TO_Start_enq_data$wset_1__SEL_1 =
	     WILL_FIRE_RL_s_Start_execute &&
	     fifo__input__TO_Start_f$D_OUT[6:2] != 5'd31 ;
  assign MUX_fifo__input__TO_Start_enq_data$wset_1__VAL_1 =
	     { fifo__input__TO_Start_f$D_OUT[133:103],
	       1'd0,
	       fifo__input__TO_Start_f$D_OUT[102:71],
	       fifo__input__TO_Start_f$D_OUT[69:39],
	       !fifo__input__TO_Start_f_first__3_BITS_37_TO_7__ETC___d32,
	       IF_fifo__input__TO_Start_f_first__3_BITS_37_TO_ETC___d36,
	       fifo__input__TO_Start_f$D_OUT[6:2] + 5'd1,
	       fifo__input__TO_Start_f$D_OUT[1:0] } ;
  assign MUX_fifo__input__TO_Start_enq_data$wset_1__VAL_2 =
	     { req_num,
	       req_denom,
	       req_quot,
	       req_acc,
	       req_cnt,
	       req_retQuot,
	       _unnamed_ } ;
  assign MUX_outputQueue_val$write_1__VAL_1 =
	     { 1'd1,
	       fifo__input__TO_Start_f$D_OUT[1] ?
		 { fifo__input__TO_Start_f$D_OUT[69:39],
		   !fifo__input__TO_Start_f_first__3_BITS_37_TO_7__ETC___d32 } :
		 IF_fifo__input__TO_Start_f_first__3_BITS_37_TO_ETC___d36 } ;

  // inlined wires
  assign fifo__input__TO_Start_enq_data$wget =
	     MUX_fifo__input__TO_Start_enq_data$wset_1__SEL_1 ?
	       MUX_fifo__input__TO_Start_enq_data$wset_1__VAL_1 :
	       MUX_fifo__input__TO_Start_enq_data$wset_1__VAL_2 ;
  assign fifo__input__TO_Start_enq_data$whas =
	     WILL_FIRE_RL_s_Start_execute &&
	     fifo__input__TO_Start_f$D_OUT[6:2] != 5'd31 ||
	     EN_req ;

  // register _unnamed_
  assign _unnamed_$D_IN = _unnamed_ + 1'd1 ;
  assign _unnamed_$EN = EN_req ;

  // register busyReg
  assign busyReg$D_IN = !MUX_busyReg$write_1__SEL_1 ;
  assign busyReg$EN =
	     WILL_FIRE_RL_s_Start_execute &&
	     fifo__input__TO_Start_f$D_OUT[6:2] == 5'd31 ||
	     EN_req ;

  // register outputQueue_nextTag_rl
  assign outputQueue_nextTag_rl$D_IN =
	     IF_outputQueue_nextTag_lat_0_whas_THEN_outputQ_ETC___d10 ;
  assign outputQueue_nextTag_rl$EN = 1'd1 ;

  // register outputQueue_val
  assign outputQueue_val$D_IN =
	     MUX_busyReg$write_1__SEL_1 ?
	       MUX_outputQueue_val$write_1__VAL_1 :
	       33'h0AAAAAAAA ;
  assign outputQueue_val$EN =
	     WILL_FIRE_RL_s_Start_execute &&
	     fifo__input__TO_Start_f$D_OUT[6:2] == 5'd31 ||
	     EN_resp ;

  // submodule fifo__input__TO_Start_f
  assign fifo__input__TO_Start_f$D_IN = fifo__input__TO_Start_enq_data$wget ;
  assign fifo__input__TO_Start_f$ENQ =
	     fifo__input__TO_Start_f$FULL_N &&
	     fifo__input__TO_Start_enq_data$whas ;
  assign fifo__input__TO_Start_f$DEQ = WILL_FIRE_RL_s_Start_execute ;
  assign fifo__input__TO_Start_f$CLR = 1'b0 ;

  // submodule outputQueue_nextTag_dummy2_0
  assign outputQueue_nextTag_dummy2_0$D_IN = 1'd1 ;
  assign outputQueue_nextTag_dummy2_0$EN = EN_resp ;

  // submodule outputQueue_nextTag_dummy2_1
  assign outputQueue_nextTag_dummy2_1$D_IN = 1'b0 ;
  assign outputQueue_nextTag_dummy2_1$EN = 1'b0 ;

  // remaining internal signals
  assign IF_fifo__input__TO_Start_f_first__3_BITS_37_TO_ETC___d36 =
	     fifo__input__TO_Start_f_first__3_BITS_37_TO_7__ETC___d32 ?
	       b__h1819 :
	       b__h1819 - fifo__input__TO_Start_f$D_OUT[102:71] ;
  assign IF_outputQueue_nextTag_lat_0_whas_THEN_outputQ_ETC___d10 =
	     EN_resp ? b__h1320 : outputQueue_nextTag_rl ;
  assign b__h1320 =
	     (outputQueue_nextTag_dummy2_0$Q_OUT &&
	      outputQueue_nextTag_dummy2_1$Q_OUT &&
	      outputQueue_nextTag_rl) +
	     1'd1 ;
  assign b__h1819 =
	     { fifo__input__TO_Start_f$D_OUT[37:7],
	       fifo__input__TO_Start_f$D_OUT[134] } ;
  assign fifo__input__TO_Start_f_first__3_BITS_37_TO_7__ETC___d32 =
	     b__h1819 < fifo__input__TO_Start_f$D_OUT[102:71] ;

  // handling of inlined registers

  always@(posedge CLK)
  begin
    if (RST_N == `BSV_RESET_VALUE)
      begin
        _unnamed_ <= `BSV_ASSIGNMENT_DELAY 1'd0;
	busyReg <= `BSV_ASSIGNMENT_DELAY 1'd0;
	outputQueue_nextTag_rl <= `BSV_ASSIGNMENT_DELAY 1'd0;
	outputQueue_val <= `BSV_ASSIGNMENT_DELAY 33'h0AAAAAAAA;
      end
    else
      begin
        if (_unnamed_$EN) _unnamed_ <= `BSV_ASSIGNMENT_DELAY _unnamed_$D_IN;
	if (busyReg$EN) busyReg <= `BSV_ASSIGNMENT_DELAY busyReg$D_IN;
	if (outputQueue_nextTag_rl$EN)
	  outputQueue_nextTag_rl <= `BSV_ASSIGNMENT_DELAY
	      outputQueue_nextTag_rl$D_IN;
	if (outputQueue_val$EN)
	  outputQueue_val <= `BSV_ASSIGNMENT_DELAY outputQueue_val$D_IN;
      end
  end

  // synopsys translate_off
  `ifdef BSV_NO_INITIAL_BLOCKS
  `else // not BSV_NO_INITIAL_BLOCKS
  initial
  begin
    _unnamed_ = 1'h0;
    busyReg = 1'h0;
    outputQueue_nextTag_rl = 1'h0;
    outputQueue_val = 33'h0AAAAAAAA;
  end
  `endif // BSV_NO_INITIAL_BLOCKS
  // synopsys translate_on
endmodule  // mkMulti_stg_div

