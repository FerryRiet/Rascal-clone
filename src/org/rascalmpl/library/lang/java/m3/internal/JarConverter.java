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
			
			System.out.println("END");
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	class Jar2M3ClassVisitor extends ClassVisitor
	{
		private final String jarFileName;
		private final String classFileName;
		private String className;

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
				case Opcodes.ACC_STATIC:
					return constructModifierNode("static");
				case Opcodes.ACC_FINAL:
					return constructModifierNode("final");
				default:
					return constructModifierNode("public");
			}
		}

		@Override
		public void visit(int version, int access, String name,
			String signature, String superName, String[] interfaces)
		{
			className = name;
			
			try
			{
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation("java+class", className, ""),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				JarConverter.this.insert(JarConverter.this.extendsRelations,
					values.sourceLocation("java+class", className, ""),
					values.sourceLocation("java+class", superName, ""));
				
				for (String iFace : interfaces)
				{
					JarConverter.this.insert(JarConverter.this.implementsRelations,
						values.sourceLocation("java+class", className, ""),
						values.sourceLocation("java+interface", iFace, ""));
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

<<<<<<< HEAD
	private void inserDeclMethod(String type, String signature, String desc, String name,int access) throws URISyntaxException{
		String sig ;
		if( signature != null) {
			sig = extractSignature(signature);
		}else {
			sig = extractSignature(desc);
		}	
		this.insert(this.declarations,values.sourceLocation(type, "", LogPath + "/" + name + "(" + sig + ")"),values.sourceLocation(jarFile + "!" + ClassFile));	
		for ( int fs = 0 ; fs < 15 ; fs++ ) { 
			if ( (access & (0x0001 << fs )) != 0 ) {
				this.insert(this.modifiers,values.sourceLocation(type, "" ,LogPath + "/" + name + "(" + sig + ")"),mapFieldAccesCode(0x0001<<fs) );				
			}
=======
		@Override
		public void visitOuterClass(String owner, String name, String desc)
		{
			// TODO Auto-generated method stub
>>>>>>> dc8600b7c3730791be9dad80c0e26f0407059dfe
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
					values.sourceLocation("java+field", className, "/" + name),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				JarConverter.this.insert(JarConverter.this.containment,
						values.sourceLocation("java+class", className, ""),
						values.sourceLocation("java+field", className, "/" + name));
				
				for(int i = 0; i < 15; i ++)
				{
					if((access & (0x0001 << i)) != 0)
					{
						JarConverter.this.insert(JarConverter.this.modifiers,
							values.sourceLocation("java+field", className, "/" + name),
							mapFieldAccessCode(access));
					}
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
			String methodType;
			if(name.equalsIgnoreCase("<init>"))
			{
				methodType = "java+constructor";
				name = className;
			}
			else
			{
				methodType = "java+method";
			}
			String sig = signature == null ? desc : signature;
			sig = sig.replaceAll("/", ".");
			sig = sig.substring(0, sig.length() - 1);
			
			System.out.println("Method Signature: " + name + " " + desc + " " + signature);
			
			try
			{
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation(methodType, className, "/" + name + sig),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation("java+class", className, ""),
					values.sourceLocation(methodType, className, "/" + name + sig));
				
				for(int i = 0; i < 15; i ++)
				{
					if((access & (0x0001 << i)) != 0)
					{
						JarConverter.this.insert(JarConverter.this.modifiers,
							values.sourceLocation(methodType, className, "/" + name + sig),
							mapFieldAccessCode(access));
					}
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
