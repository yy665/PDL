module cache (clk, reset, ready_in, valid_in, addr_in, op_in,
	      write_data_in, ready_out, valid_out, data_out);
   input clk, reset;
   input valid_in, ready_out, op_in;
   input [31:0] addr_in, write_data_in;
   output 	ready_in, valid_out;
   output [31:0] data_out;

   localparam CACHE_READ = 1'b0,
     CACHE_WRITE = 1'b1;
   reg [31:0] 	 req_addr, req_write_data;
   reg 		 req_op, req_valid;
   
   reg [31:0] 	 mem[0:1023];
   assign data_out = mem[req_addr];
   assign valid_out = req_valid;
   assign ready_in = !req_valid | ready_out;

   initial begin
      $readmemb("rom_image.mem", mem);
   end
     
   always@(posedge clk) begin
      if(reset) begin
	 req_valid <= 0;
	 req_addr <= 0;
	 req_write_data <= 0;
	 req_op <= 0;
      end else begin
	 if (valid_in & ready_in) begin
	    req_addr <= addr_in >> 2;
	    req_write_data <= write_data_in;
	    req_op <= op_in;
	    req_valid <= 1'b1;
	 end else if (valid_out & ready_out) begin
	    req_valid <= 0'b0;
	 end
	 if (req_valid & req_op == CACHE_WRITE) begin
	    mem[req_addr] <= req_write_data;
	 end
      end // else: !if(reset)
   end
endmodule
   
module regfile (clk, reset, write_en, write_addr, write_val,
		read_1_addr, read_1_en, read_1_out,
		read_2_addr, read_2_en, read_2_out);
   input clk, reset;
   input write_en, read_1_en, read_2_en;
   input [4:0] write_addr, read_1_addr, read_2_addr;
   input [31:0] write_val;
   output reg [31:0] read_1_out, read_2_out;
   integer 	     i;
   
   reg [31:0] 	 mem [0:31];
   always@(posedge clk) begin
      if (reset) begin
	 begin
	    for (i=0; i<32; i=i+1) mem[i] <= 32'b0;
	 end
      end else begin
	 if (write_en) begin
	    mem[write_addr] <= write_val;
	 end
	 if (read_1_en) begin
	    read_1_out <= mem[read_1_addr];
	 end
	 if (read_2_en) begin
	    read_2_out <= mem[read_2_addr];
	 end
      end // else: !if(reset)
   end
      
endmodule   

module alu (arg_1, arg_2, alu_op, result);
  
   input [31:0] arg_1, arg_2;
   input [6:0] 	alu_op;
   output [31:0] result;   
   reg [31:0] 	 tmp;
   assign result = tmp;
   
   localparam ADD_OP = 6'b000000,
     SUB_OP = 6'd32;
   
   always@(*) begin
      case(alu_op)
	ADD_OP : begin
	   tmp = arg_1 + arg_2;
	end
	SUB_OP : begin
	   tmp = arg_1 - arg_2;
	end
	default : begin
	   tmp = 0;
	end
      endcase // case (alu_op)
   end // always@ (*)


endmodule
