package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.Instructions;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.CodeBlock;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.MuPrimitive;

public class CallMuPrim extends Instruction {

	MuPrimitive muprim;
	int arity;
	
	public CallMuPrim(CodeBlock ins, MuPrimitive muprim, int arity){
		super(ins, Opcode.CALLMUPRIM);
		this.muprim = muprim;
		this.arity = arity;
	}
	
	public String toString() { return "CALLMUPRIM " + muprim + ", " + arity; }
	
	public void generate(){
		codeblock.addCode2(opcode.getOpcode(), muprim.ordinal(), arity);
	}
}
