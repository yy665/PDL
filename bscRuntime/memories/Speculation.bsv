package Speculation;

import Vector :: *;
typedef UInt#(TLog#(n)) SpecId#(numeric type n);

interface SpecTable#(type sid);
    method ActionValue#(sid) alloc();
    method Maybe#(Bool) check(sid s);
    method Action free(sid s);
    method Action validate(sid s);
    method Action invalidate(sid s);
endinterface

module mkSpecTable(SpecTable#(SpecId#(entries)));

    Vector#(entries, Reg#(Bool)) inUse <- replicateM(mkReg(False));
    Vector#(entries, Reg#(Maybe#(Bool))) specStatus <- replicateM(mkReg(tagged Invalid));

    Reg#(SpecId#(entries)) head <- mkReg(0);
    Bool full = inUse[head];

   //return true if entry a is a valid newer entry than b
   function Bool isNewer(SpecId#(entries) a, SpecId#(entries) b);
      let nohmid = a > b && !(b < head && a >= head);
      let hmid = a < head && b >= head;
      return nohmid || hmid;
   endfunction

   /*
   rule debug;
      $display("Head: %d", head);
      for (Integer i = 0; i < valueOf(entries); i = i + 1)
	 begin
	    $display("Idx %d, InUse: %b", i, inUse[fromInteger(i)]);   
	    $display("Idx %d, Status: %b", i, specStatus[fromInteger(i)]);
	 end
   endrule
    */
   
    //allocate a new entry in the table to track speculation	       
   method ActionValue#(SpecId#(entries)) alloc() if (!full);
        head <= head + 1;
        inUse[head] <= True;
        specStatus[head] <= tagged Invalid;
        return head;
    endmethod

    //lookup a given entry
    method Maybe#(Bool) check(SpecId#(entries) s);
        return tagged Invalid;
    endmethod

    method Action free(SpecId#(entries) s);
        inUse[s] <= False;
    endmethod

    //mark s as valid (correctly speculated)
    method Action validate(SpecId#(entries) s);
        specStatus[s] <= tagged Valid True;
    endmethod

    //mark s and all newer entries as invalid (misspeculated)
    method Action invalidate(SpecId#(entries) s);
       for (Integer i = 0; i < valueOf(entries); i = i + 1) begin
	  SpecId#(entries) lv = fromInteger(i);
	  if ((s == lv || isNewer(lv, s)) && inUse[lv]) specStatus[lv] <= tagged Valid False;
       end
    endmethod

endmodule





endpackage
