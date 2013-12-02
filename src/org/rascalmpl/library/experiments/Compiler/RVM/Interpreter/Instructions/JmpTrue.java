package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.Instructions;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.CodeBlock;

public class JmpTrue extends Instruction {

	String label;
	
	public JmpTrue(CodeBlock ins, String label){
		super(ins, Opcode.JMPTRUE);
		this.label = label;
	}
	
	public String toString() { return "JMPTRUE " + label + " [" + codeblock.getLabelPC(label) + "]"; }
	
	public void generate(){
		codeblock.addCode1(opcode.getOpcode(), codeblock.getLabelPC(label));
	}
}
