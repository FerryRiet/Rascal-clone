package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.Instructions;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.CodeBlock;

public class StoreVar extends Instruction {

	final int pos;
	final String fuid;
	
	public StoreVar(CodeBlock ins, String fuid, int pos){
		super(ins, Opcode.STOREVAR);
		this.fuid = fuid;
		this.pos = pos;
	}
	
	public String toString() { return "STOREVAR " + fuid + ", " + pos; }
	
	public void generate(){
		codeblock.addCode2(opcode.getOpcode(), (pos == -1) ? codeblock.getConstantIndex(codeblock.vf.string(fuid))
                                      					   : codeblock.getFunctionIndex(fuid),
                                      		   pos);
	}
}
