package org.rascalmpl.library.lang.java.m3.internal;

import java.io.IOException;

import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.objectweb.asm.ClassReader;
import org.rascalmpl.interpreter.IEvaluatorContext;

public class JarConverter extends M3Converter
{
	public JarConverter(TypeStore typeStore)
	{
		super(typeStore);
	}

	public void convert(ISourceLocation jarLoc, IEvaluatorContext ctx)
	{
		try
		{
			ClassReader cr = new ClassReader(ctx.getResolverRegistry().getInputStream(jarLoc.getURI()));
			cr.accept(new Jar2M3ClassVisitor(this, jarLoc), ClassReader.SKIP_DEBUG);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
