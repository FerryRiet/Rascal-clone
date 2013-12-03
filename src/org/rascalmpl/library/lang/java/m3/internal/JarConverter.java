package org.rascalmpl.library.lang.java.m3.internal;

import java.io.IOException;
import java.net.URISyntaxException;

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

	private enum EOpcodeType { CLASS, METHOD, FIELD };
	
	class Jar2M3ClassVisitor extends ClassVisitor
	{
		private final String jarFileName;
		private final String classFileName;
		private String className;
		private String classScheme;

		public Jar2M3ClassVisitor(ISourceLocation jarLoc)
		{
			super(Opcodes.ASM4, null);
			this.jarFileName = extractJarName(jarLoc);
			this.classFileName = extractClassName(jarLoc);
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
		
		private void processAccess(int access, String scheme, String authority, String path, JarConverter.EOpcodeType opcodeType)
			throws URISyntaxException
		{
			for(int i = 0; i < 15; i ++)
			{
				if((access & (0x0001 << i)) != 0)
				{
					IConstructor cons = mapFieldAccessCode(0x0001 << i, opcodeType);
					if(cons != null)
					{
						JarConverter.this.insert(JarConverter.this.modifiers,
							values.sourceLocation(scheme, authority, path), cons);
					}
				}
			}
		}
		
		private IConstructor mapFieldAccessCode(int code, JarConverter.EOpcodeType opcodeType)
		{
			switch (code)
			{
				case Opcodes.ACC_PUBLIC:
					return constructModifierNode("public");
				case Opcodes.ACC_PRIVATE:
					return constructModifierNode("private");
				case Opcodes.ACC_PROTECTED:
					return constructModifierNode("protected");
				case Opcodes.ACC_STATIC:
					return constructModifierNode("static");
				case Opcodes.ACC_FINAL:
					return constructModifierNode("final");
				case Opcodes.ACC_SYNCHRONIZED:
					if(opcodeType == JarConverter.EOpcodeType.CLASS) return null;
					return constructModifierNode("synchronized");
				case Opcodes.ACC_ABSTRACT:
					return constructModifierNode("abstract");
				case Opcodes.ACC_VOLATILE:
					return constructModifierNode("volatile");
				case Opcodes.ACC_TRANSIENT:
					return constructModifierNode("transient");
				case Opcodes.ACC_NATIVE:
					return constructModifierNode("native");
				default:
					return null;
			}
		}

		@Override
		public void visit(int version, int access, String name,
			String signature, String superName, String[] interfaces)
		{
			className = name.replace("$", "/");
			
			try
			{
				classScheme = "java+class";
				if((access & Opcodes.ACC_INTERFACE) != 0) classScheme = "java+interface";

				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation(classScheme, "/" + className, ""),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				if(superName != null && !superName.equalsIgnoreCase("java/lang/Object"))
				{
					JarConverter.this.insert(JarConverter.this.extendsRelations,
						values.sourceLocation(classScheme, "/" + className, ""),
						values.sourceLocation(classScheme, "/" + superName, ""));
				}
				
				processAccess(access, classScheme, "/" + className, "", JarConverter.EOpcodeType.CLASS);
				
				if((access & Opcodes.ACC_DEPRECATED) != 0)
                {
                	JarConverter.this.insert(JarConverter.this.annotations,
            			values.sourceLocation(classScheme, "/" + className, ""),
            			values.sourceLocation("java+interface", "/java/lang/Deprecated", ""));
                }
				
				for (String iFace : interfaces)
				{
					JarConverter.this.insert(JarConverter.this.implementsRelations,
						values.sourceLocation(classScheme, "/" + className, ""),
						values.sourceLocation("java+interface", "/" + iFace, ""));
				}
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
			System.out.println("OUTER: " + owner + " " + name + " " + desc);
		}
		
		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access)
		{
			System.out.println("INNER: " + name + " " + outerName + "(" + outerName == className + ")"
				+ " " + innerName + " " + access);
			
			try
			{
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "/" + className, ""), //outerName
					values.sourceLocation("java+class", "/" + name.replace("$", "/"), ""));
			}
			catch (URISyntaxException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
		public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value)
		{
			try
			{
				System.out.println("FIELD: " + name + desc + signature);
				
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation("java+field", "/" + className, "/" + name),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "/" + className, ""),
					values.sourceLocation("java+field", "/" + className, "/" + name));
				
				processAccess(access, "java+field", "/" + className, "/" + name, JarConverter.EOpcodeType.FIELD);
				
				// <|java+method:///Main/Main/FindMe(java.lang.String)|,|java+interface:///java/lang/Deprecated|>
				if((access & Opcodes.ACC_DEPRECATED) != 0)
                {
                	JarConverter.this.insert(JarConverter.this.annotations,
            			values.sourceLocation("java+field", "/" + className, "/" + name),
            			values.sourceLocation("java+interface", "/java/lang/Deprecated", ""));
                }
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
			String methodType = "java+method";
			if(name.equalsIgnoreCase("<init>"))
			{
				methodType = "java+constructor";
				name = className;
			}

			String sig = signature == null ? desc : signature;
			sig = sig.replaceAll("/", ".");
			sig = sig.substring(0, sig.lastIndexOf(")") + 1);
			
			System.out.println("METHOD: " + name + " " + desc + " " + signature);
			
			try
			{
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation(methodType, "/" + className, "/" + name + sig),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "/" + className, ""),
					values.sourceLocation(methodType, "/" + className, "/" + name + sig));
				
				processAccess(access, methodType, "/" + className, "/" + name + sig, JarConverter.EOpcodeType.METHOD);
				
				// Deprecated method emit type dependency Deprecated.
				// <|java+method:///Main/Main/FindMe(java.lang.String)|,|java+interface:///java/lang/Deprecated|>,
                if((access & Opcodes.ACC_DEPRECATED) != 0)
                {
                	JarConverter.this.insert(JarConverter.this.annotations,
            			values.sourceLocation(classScheme, "/" + className, "/" + name + sig),
            			values.sourceLocation("java+interface", "/java/lang/Deprecated", ""));
                }
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
			
		}
	}
}
