# Object type system for PDL

## General
 - every type would have an "interface" of methods
 - each method would have argument types, a return type, as well as a latency
	 * adding a latency to each method type would help with typechecking
       different lock/extern types when they are swapped out.
	 * latency would be kind of annoying
		 - could be that combinational <: sequential <: asynchronous
		 - this would be something of an issue since certain combinational
           things might HAVE to be done combinationally? 
		 - this would mean that the base lock interface would have to have
           everything asynchronous, which is kinda just stupid.
		 - there could also be no base lock interface, or at least the latency
           wouldn't be a part of it. This might imply that latency is a
           different part of the type system that has a special relation with
           inheritance.

## Locks
 - read
 - write
 - reserve
 - block
 - release
 - acquire - this would only be different from reserve + block on some
   implementations, and would just be sugar on others
 - address based and general locks are just two different things
 - could have different latency based on different operations (r/w)
 
 ^^ on the PDL level
   
### FAQueue&lt;regfile&gt;
 - `reserve<comb>(addr, r/w)`
 - `block<comb>(addr, r/w)>`
 - `release<comb>(addr)`
 - `read<comd>(addr)`
 - `write<seq>(addr, data)`
 
## Extern
These would just be objects w/ methods as defined above 
   
 
