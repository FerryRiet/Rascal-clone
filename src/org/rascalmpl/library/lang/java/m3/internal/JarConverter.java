package org.rascalmpl.library.lang.java.m3.internal;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.rascalmpl.interpreter.IEvaluatorContext;

public class JarConverter extends M3Converter
{
	private Map<String, Set<String>> methodOccurences = new HashMap<String, Set<String>>();
	private Map<String, String> extendedStuff = new HashMap<String, String>();
	private String currentClassName;
	
	public JarConverter(TypeStore typeStore)
	{
		super(typeStore);
	}

	public void convert(ISourceLocation jarLoc, IEvaluatorContext ctx)
	{
		try
		{
			ClassReader cr = new ClassReader(ctx.getResolverRegistry()
				.getInputStream(jarLoc.getURI()));
			
			cr.accept(new Jar2M3ClassVisitor(jarLoc), ClassReader.SKIP_DEBUG);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	class Jar2M3ClassVisitor extends ClassVisitor
	{
		private final String jarFile;
		private final String classFile;

		public Jar2M3ClassVisitor(ISourceLocation jarLoc)
		{
			super(Opcodes.ASM4, null);
			this.jarFile = extractJarName(jarLoc);
			this.classFile = extractClassName(jarLoc);
		}

		private String extractJarName(ISourceLocation jarLoc)
		{
			String tmp = jarLoc.getPath().substring(0,
				jarLoc.getPath().indexOf("!"));
			return tmp.substring(tmp.lastIndexOf("/") + 1);
		}
		
		private String extractClassName(ISourceLocation jarLoc)
		{
			return jarLoc.getPath().substring(jarLoc.getPath().indexOf("!") + 1);
		}
		
		private IConstructor mapFieldAccessCode(int code)
		{
			switch (code)
			{
				case Opcodes.ACC_PUBLIC:
					return constructModifierNode("public");
				case Opcodes.ACC_PRIVATE:
					return constructModifierNode("private");
				case Opcodes.ACC_PROTECTED:
					return constructModifierNode("protected");
				default:
					return constructModifierNode("public");
			}
		}

		@Override
		public void visit(int version, int access, String name,
			String signature, String superName, String[] interfaces)
		{
			try
			{
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation("java+class", classFile, "/" + name),
					values.sourceLocation(jarFile));
				
				JarConverter.this.insert(JarConverter.this.extendsRelations,
					values.sourceLocation("java+class", classFile, "/" + name),
					values.sourceLocation("java+class", "", "/" + superName));
				
				for (String iFace : interfaces)
				{
					JarConverter.this.insert(JarConverter.this.implementsRelations,
						values.sourceLocation("java+class", classFile, "/" + name),
						values.sourceLocation("java+interface", jarFile, "/" + iFace));
					
					extendedStuff.put(name, iFace);
				}
				
				extendedStuff.put(name, superName);
				currentClassName = name;
			}
			catch (URISyntaxException e)
			{
				// TODO Auto-generated catch block
				throw new RuntimeException("Should not happen", e);
			}
		}

		@Override
		public void visitSource(String source, String debug)
		{
			// TODO Auto-generated method stub
		}

		@Override
		public void visitOuterClass(String owner, String name, String desc)
		{
			// TODO Auto-generated method stub
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void visitAttribute(Attribute attr)
		{
			// TODO Auto-generated method stub
		}

		@Override
		public void visitInnerClass(String name, String outerName,
			String innerName, int access)
		{
			// TODO Auto-generated method stub
		}

		@Override
		public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value)
		{
			try
			{
				System.out.println("Field Signature: " + name + desc + signature);
				
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation("java+field", "", jarFile + "/" + name),
					values.sourceLocation(jarFile));
				
				JarConverter.this.insert(JarConverter.this.modifiers,
					values.sourceLocation("java+field", "", jarFile + "/" + name),
					mapFieldAccessCode(access));
			}
			catch (URISyntaxException e)
			{
				throw new RuntimeException("Should not happen", e);
			}
			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions)
		{
			try
			{
				System.out.println("Method Signature: " + name + desc + signature);
				
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation("java+method", "", classFile + "/" + name + desc),
					values.sourceLocation(classFile));
				
				JarConverter.this.insert(JarConverter.this.modifiers,
					values.sourceLocation("java+method", "", classFile + "/" + name),
					mapFieldAccessCode(access));
				
				if(!methodOccurences.containsKey(name))
					methodOccurences.put(name, new HashSet<String>());
				methodOccurences.get(name).add(currentClassName);
			}
			catch (URISyntaxException e)
			{
				// TODO Auto-generated catch block
				throw new RuntimeException("Should not happen", e);
			}
			return null;
		}

		@Override
		public void visitEnd()
		{
			for(Map.Entry<String, Set<String>> method : methodOccurences.entrySet())
			{
				System.out.println("END: " + method);
				
				for(String containingClass : method.getValue())
				{
					if(extendedStuff.containsKey(containingClass)
						&& methodOccurences.containsKey(extendedStuff.get(containingClass)))
					{
						try
						{
							JarConverter.this.insert(JarConverter.this.methodOverrides,
								values.sourceLocation("java+method", "", containingClass + "/" + method.getKey()),
								values.sourceLocation("java+class", "", extendedStuff.get(containingClass)));
						}
						catch (URISyntaxException e)
						{
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
}
