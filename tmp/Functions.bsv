export alu ;
function Int#(32) alu ( Int#(32) arg1, Int#(32) arg2, UInt#(3) op, Bool flip );
    UInt#(5) _tmp_21 = unpack( pack( arg2 ) [ 4 : 0 ] );
    UInt#(5) shamt = _tmp_21;
    if ( ( op == 3'd0 ))
    begin
        if ( ( ! flip ))
        begin
            return ( arg1 + arg2 );
        end
        else
        begin
            return ( arg1 - arg2 );
        end
    end
    else
    begin
        if ( ( op == 3'd1 ))
        begin
            return ( arg1 << shamt );
        end
        else
        begin
            if ( ( op == 3'd2 ))
            begin
                return ( ( arg1 < arg2 ) ? 32'd1 : 32'd0 );
            end
            else
            begin
                if ( ( op == 3'd3 ))
                begin
                    UInt#(32) _tmp_22 = unpack( pack( arg1 ) );
                    UInt#(32) un1 = _tmp_22;
                    UInt#(32) _tmp_23 = unpack( pack( arg2 ) );
                    UInt#(32) un2 = _tmp_23;
                    return ( ( un1 < un2 ) ? 32'd1 : 32'd0 );
                end
                else
                begin
                    if ( ( op == 3'd4 ))
                    begin
                        return ( arg1 ^ arg2 );
                    end
                    else
                    begin
                        if ( ( op == 3'd5 ))
                        begin
                            if ( ( ! flip ))
                            begin
                                UInt#(32) _tmp_24 = unpack( pack( arg1 ) );
                                Int#(32) _tmp_25 = unpack( pack( ( _tmp_24 >> shamt ) ) );
                                return _tmp_25;
                            end
                            else
                            begin
                                return ( arg1 >> shamt );
                            end
                        end
                        else
                        begin
                            if ( ( op == 3'd6 ))
                            begin
                                return ( arg1 | arg2 );
                            end
                            else
                            begin
                                return ( arg1 & arg2 );
                            end
                        end
                    end
                end
            end
        end
    end
endfunction
export maskLoad ;
function Int#(32) maskLoad ( Int#(32) data, UInt#(3) op, UInt#(2) start );
    UInt#(5) boff = unpack( { pack( start ), pack( 3'd0 ) } );
    Int#(32) tmp = ( data >> boff );
    UInt#(8) _tmp_28 = unpack( pack( truncate( tmp ) ) );
    UInt#(8) bdata = _tmp_28;
    UInt#(16) _tmp_29 = unpack( pack( truncate( tmp ) ) );
    UInt#(16) hdata = _tmp_29;
    if ( ( op == 3'd0 ))
    begin
        Int#(32) _tmp_30 = unpack( pack( signExtend( bdata ) ) );
        return _tmp_30;
    end
    else
    begin
        if ( ( op == 3'd1 ))
        begin
            Int#(32) _tmp_31 = unpack( pack( signExtend( hdata ) ) );
            return _tmp_31;
        end
        else
        begin
            if ( ( op == 3'd2 ))
            begin
                return data;
            end
            else
            begin
                if ( ( op == 3'd4 ))
                begin
                    UInt#(32) _tmp_32 = zeroExtend( bdata );
                    UInt#(32) zext = _tmp_32;
                    Int#(32) _tmp_33 = unpack( pack( zext ) );
                    return _tmp_33;
                end
                else
                begin
                    if ( ( op == 3'd5 ))
                    begin
                        UInt#(32) _tmp_34 = zeroExtend( hdata );
                        UInt#(32) zext = _tmp_34;
                        Int#(32) _tmp_35 = unpack( pack( zext ) );
                        return _tmp_35;
                    end
                    else
                    begin
                        return 32'd0;
                    end
                end
            end
        end
    end
endfunction
export br ;
function Int#(16) br ( Int#(16) pc, Int#(16) off, UInt#(3) op, Int#(32) arg1, Int#(32) arg2 );
    Int#(16) offpc = ( pc + ( off >> 2'd2 ) );
    Int#(16) npc = ( pc + 16'd1 );
    if ( ( op == 3'd0 ))
    begin
        if ( ( arg1 == arg2 ))
        begin
            return offpc;
        end
        else
        begin
            return npc;
        end
    end
    else
    begin
        if ( ( op == 3'd1 ))
        begin
            if ( ( arg1 != arg2 ))
            begin
                return offpc;
            end
            else
            begin
                return npc;
            end
        end
        else
        begin
            if ( ( op == 3'd4 ))
            begin
                if ( ( arg1 < arg2 ))
                begin
                    return offpc;
                end
                else
                begin
                    return npc;
                end
            end
            else
            begin
                if ( ( op == 3'd5 ))
                begin
                    if ( ( arg1 >= arg2 ))
                    begin
                        return offpc;
                    end
                    else
                    begin
                        return npc;
                    end
                end
                else
                begin
                    if ( ( op == 3'd6 ))
                    begin
                        UInt#(32) _tmp_25 = unpack( pack( arg1 ) );
                        UInt#(32) un1 = _tmp_25;
                        UInt#(32) _tmp_26 = unpack( pack( arg2 ) );
                        UInt#(32) un2 = _tmp_26;
                        if ( ( un1 < un2 ))
                        begin
                            return offpc;
                        end
                        else
                        begin
                            return npc;
                        end
                    end
                    else
                    begin
                        if ( ( op == 3'd7 ))
                        begin
                            UInt#(32) _tmp_27 = unpack( pack( arg1 ) );
                            UInt#(32) un1 = _tmp_27;
                            UInt#(32) _tmp_28 = unpack( pack( arg2 ) );
                            UInt#(32) un2 = _tmp_28;
                            if ( ( un1 >= un2 ))
                            begin
                                return offpc;
                            end
                            else
                            begin
                                return npc;
                            end
                        end
                        else
                        begin
                            return npc;
                        end
                    end
                end
            end
        end
    end
endfunction
export mul ;
function Int#(32) mul ( Int#(32) arg1, Int#(32) arg2, UInt#(3) op );
    UInt#(32) _tmp_19 = unpack( pack( abs(arg1) ) );
    UInt#(32) mag1 = _tmp_19;
    UInt#(32) _tmp_20 = unpack( pack( abs(arg2) ) );
    UInt#(32) mag2 = _tmp_20;
    Int#(32) s1 = ( ( op == 3'd3 ) ? 32'd1 : signum(arg1) );
    Int#(32) s2 = ( ( op >= 3'd2 ) ? 32'd1 : signum(arg2) );
    Int#(64) _tmp_21 = unpack( pack( unsignedMul( mag1 , mag2 ) ) );
    Int#(64) magRes = _tmp_21;
    Int#(64) m = ( ( s1 == s2 ) ? magRes : ( - magRes ) );
    if ( ( op == 3'd0 ))
    begin
        return unpack( pack( m ) [ 31 : 0 ] );
    end
    else
    begin
        return unpack( pack( m ) [ 63 : 32 ] );
    end
endfunction
export storeMask ;
function UInt#(4) storeMask ( UInt#(2) off, UInt#(3) op );
    if ( ( op == 3'd0 ))
    begin
        return ( 4'b1 << off );
    end
    else
    begin
        if ( ( op == 3'd1 ))
        begin
            UInt#(2) shamt = unpack( { pack( off ) [ 1 : 1 ], pack( 1'd0 ) } );
            return ( 4'b11 << shamt );
        end
        else
        begin
            return 4'b1111;
        end
    end
endfunction