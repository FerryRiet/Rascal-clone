package org.rascalmpl.library.lang.java.m3.internal;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.rascalmpl.interpreter.IEvaluatorContext;

//TODO Adapt debugging Sysouts to only run when actually debugging
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
			cr.accept(new Jar2M3ClassVisitor(jarLoc), ClassReader.SKIP_DEBUG);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private enum EOpcodeType { CLASS, METHOD, FIELD };
	
	class Jar2M3ClassVisitor extends ClassVisitor
	{
		private final String jarFileName;
		private final String classFileName;
		private String classNamePath;
		private String classScheme;
		private boolean classIsEnum;
		private LinkedHashMap<String, IConstructor> globalTypeParams;
		
		public String getJarFileName()
		{
			return jarFileName;
		}
		
		public String getClassFileName()
		{
			return classFileName;
		}
		
		public String getClassNamePath()
		{
			return classNamePath;
		}

		public Jar2M3ClassVisitor(ISourceLocation jarLoc)
		{
			super(Opcodes.ASM4, null);
			
			this.jarFileName = extractJarName(jarLoc);
			this.classFileName = extractClassName(jarLoc);
		}

		private String extractJarName(ISourceLocation jarLoc)
		{
			String jarPath = jarLoc.getPath();
			jarPath = jarPath.substring(0, jarPath.indexOf('!'));
			return jarPath.substring(jarPath.lastIndexOf('/') + 1);
		}
		
		private String extractClassName(ISourceLocation jarLoc)
		{
			String jarPath = jarLoc.getPath();
			return jarPath.substring(jarPath.indexOf('!') + 1);
		}
		
		private void processAccess(int access, String scheme, String path, JarConverter.EOpcodeType opcodeType)
			throws URISyntaxException
		{
			for(int code = 0x0001; code < 0x8000; code = code << 1)
			{
				if((access & code) != 0)
				{
					IConstructor cons = mapFieldAccessCode(code, opcodeType);
					if(cons != null)
					{
						JarConverter.this.insert(JarConverter.this.modifiers,
							values.sourceLocation(scheme, "", path), cons);
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
		
		private String eliminateOutterClass(String desc)
		{
			//Find the end of the first class argument
			int semi = desc.indexOf(';');
			
			//If the first argument is contained in the class path, remove it
			if(semi > 0 && classFileName.contains(desc.substring(desc.indexOf('(') + 2, semi) + "$"))
			{
				return "(" + desc.substring(semi + 1);
			}
			
			return desc;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
		{
			System.out.println(String.format("CLASS: %s, %s, %s, %s, %s", version, access, name, signature, superName));
			
			classNamePath = "/" + name.replace('$', '/');
			classScheme = "java+class";
			classIsEnum = (access & Opcodes.ACC_ENUM) != 0;
			if(classIsEnum) classScheme = "java+enum";
			else if((access & Opcodes.ACC_INTERFACE) != 0) classScheme = "java+interface";
			
			try
			{
				if(signature != null)
				{
					SigVisitor sv = new SigVisitor(JarConverter.this, this, null, null, false);
					new SignatureReader(signature).accept(sv);
					
					//M3@types
					if(classIsEnum)
					{
						JarConverter.this.insert(JarConverter.this.types,
							values.sourceLocation(classScheme, "", classNamePath),
							constructTypeSymbolNode("enum", values.sourceLocation(classScheme, "", classNamePath)));
					}
					else
					{
						globalTypeParams = sv.getTypeParameters();
						
						JarConverter.this.insert(JarConverter.this.types,
							values.sourceLocation(classScheme, "", classNamePath),
							constructTypeSymbolNode((access & Opcodes.ACC_INTERFACE) != 0 ? "interface" : "class",
								values.sourceLocation(classScheme, "", classNamePath),
								values.list(globalTypeParams.values().toArray(new IValue[globalTypeParams.size()]))));
					}
				}
				
				//item declared in jar!item.class
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation(classScheme, "", classNamePath),
					values.sourceLocation(jarFileName + "!" + classFileName));

				if(superName != null && !(superName.equalsIgnoreCase("java/lang/Object")
					|| superName.equalsIgnoreCase("java/lang/Enum")))
				{
					//x extends y
					JarConverter.this.insert(JarConverter.this.extendsRelations,
						values.sourceLocation(classScheme, "", classNamePath),
						values.sourceLocation(classScheme, "", "/" + superName));
				}
				
				//Item contains jar? TODO
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "",  classNamePath),
					values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName));
				//Package contains jar? TODO
                JarConverter.this.insert(JarConverter.this.containment,
            		values.sourceLocation("java+package", "", "/" + classFileName.substring(0, classFileName.lastIndexOf('/'))),
            		values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName));
                //CompUnit contains item
                JarConverter.this.insert(JarConverter.this.containment,
            		values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName),
            		values.sourceLocation(classScheme, "", classNamePath));

				processAccess(access, classScheme, classNamePath, JarConverter.EOpcodeType.CLASS);
				
				if((access & Opcodes.ACC_DEPRECATED) != 0)
                {
					//Deprecated annotation present
                	JarConverter.this.insert(JarConverter.this.annotations,
            			values.sourceLocation(classScheme, "", classNamePath),
            			values.sourceLocation("java+interface", "", "/java/lang/Deprecated"));
                }
				
				for (String iFace : interfaces)
				{
					//x implements y
					JarConverter.this.insert(JarConverter.this.implementsRelations,
						values.sourceLocation(classScheme, "", classNamePath),
						values.sourceLocation("java+interface", "", "/" + iFace));
				}
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
		}

		@Override
		public void visitSource(String source, String debug)
		{
			System.out.println(String.format("SRC: %s, %s", source, debug));
		}

		@Override
		public void visitOuterClass(String owner, String name, String desc)
		{
			System.out.println(String.format("OUTER: %s, %s, %s", owner, name, desc));
		}
		
		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access)
		{
			System.out.println(String.format("INNER: %s, %s, %s, %s", name, outerName, innerName, access));
			
			try
			{
				//x contains inner class y
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "", classNamePath),
					values.sourceLocation("java+class", "", "/" + name.replace('$', '/')));
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible)
		{
			System.out.println(String.format("ANNOTATION: %s, %b", desc, visible));
			return null;
		}

		@Override
		public void visitAttribute(Attribute attr)
		{
			System.out.println(String.format("ATTRIBUTE: %s", attr));
		}

		@Override
		public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
		{
			if((access & Opcodes.ACC_SYNTHETIC) != 0) return null; //Compiler generated field. Ignore it.
			
			System.out.println(String.format("FIELD: %s, %s, %s, %s, %s", access, name, desc, signature, value));
			
			boolean isEnumConstant = (access & Opcodes.ACC_ENUM) != 0;
			String fieldScheme = isEnumConstant ? "java+enumConstant" : "java+field";
			
			try
			{
				//Field declared in classFile
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation(fieldScheme, "", classNamePath + "/" + name),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				//Class contains field
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "", classNamePath),
					values.sourceLocation(fieldScheme, "", classNamePath + "/" + name));
				
				if(!isEnumConstant) //No access modifiers or signatures for enum constants
				{
					processAccess(access, fieldScheme, classNamePath + "/" + name, JarConverter.EOpcodeType.FIELD);
					
					SigVisitor sv = new SigVisitor(JarConverter.this, this, globalTypeParams, name, false);
					new SignatureReader(signature != null ? signature : desc).accept(sv);
				}
				else
				{
					//Enum constants depend on the enum
					JarConverter.this.insert(JarConverter.this.typeDependency,
            			values.sourceLocation(fieldScheme, "", classNamePath + "/" + name),
            			values.sourceLocation(classScheme, "", classNamePath));
				}
				
				//Deprecated annotation present
				if((access & Opcodes.ACC_DEPRECATED) != 0)
                {
                	JarConverter.this.insert(JarConverter.this.annotations,
            			values.sourceLocation(fieldScheme, "", classNamePath + "/" + name),
            			values.sourceLocation("java+interface", "", "/java/lang/Deprecated"));
                }
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
		{
			System.out.println(String.format("METHOD: %s, %s, %s, %s, %s", access, name, desc, signature, exceptions));
			
			//Ignore enum functions
			if(classIsEnum && (name.equalsIgnoreCase("values")
				|| name.equalsIgnoreCase("valueOf")))
			{
				return null;
			}
			
			//Properly set constructor scheme and name
			String methodScheme = "java+method";
			if(name.startsWith("<"))
			{
				methodScheme = "java+constructor";
				name = classNamePath.substring(classNamePath.lastIndexOf('/'));
				desc = eliminateOutterClass(desc);
			}

			String sig = Signature.toString(signature == null ? desc : signature);
			sig = sig.substring(sig.indexOf('('), sig.indexOf(')') + 1);
			sig = sig.replaceAll("\\s+", "");
			sig = sig.replace('/', '.');
			
			try
			{
				//Method declared in classFile
				JarConverter.this.insert(JarConverter.this.declarations,
					values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
					values.sourceLocation(jarFileName + "!" + classFileName));
				
				//Class contains method
				JarConverter.this.insert(JarConverter.this.containment,
					values.sourceLocation(classScheme, "", classNamePath),
					values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig));
				
				processAccess(access, methodScheme, classNamePath + "/" + name + sig, JarConverter.EOpcodeType.METHOD);
				
				//Deprecated annotation present
                if((access & Opcodes.ACC_DEPRECATED) != 0)
                {
                	JarConverter.this.insert(JarConverter.this.annotations,
            			values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
            			values.sourceLocation("java+interface", "", "/java/lang/Deprecated"));
                }
                
                
            	String sigToVisit = (signature != null ? signature : desc);
            	boolean ignoreReturnType = false;
            	if(methodScheme == "java+constructor")
                {
            		//Constructor depends on the class
    				JarConverter.this.insert(JarConverter.this.typeDependency,
    					values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
    					values.sourceLocation(classScheme, "", classNamePath));
            		
    				//Don't include the return type; it's a constructor
    				ignoreReturnType = true;
            		//sigToVisit = sigToVisit.substring(sigToVisit.indexOf('('), sigToVisit.indexOf(')') + 1);
                }
            	
            	SigVisitor sv = new SigVisitor(JarConverter.this, this, globalTypeParams, name + sig, ignoreReturnType);
            	new SignatureReader(sigToVisit).accept(sv);
            	
				//M3@types
            	ArrayList<IValue> paramValues = sv.getParameters();
            	IValue paramList = values.list(paramValues.toArray(new IValue[paramValues.size()]));
            	
				IConstructor methodType;
				if(methodScheme == "java+constructor")
				{
					methodType = constructTypeSymbolNode("constructor", values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig), paramList);
				}
				else
				{
					LinkedHashMap<String, IConstructor> typeParams = sv.getTypeParameters();
	            	IValue typeParamsList = values.list(typeParams.values().toArray(new IValue[typeParams.size()]));
	            	
					methodType = constructTypeSymbolNode("method", values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
						typeParamsList, sv.getReturnValue(), paramList);
				}
				
				JarConverter.this.insert(JarConverter.this.types,
					values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
					methodType);
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void visitEnd()
		{
			
		}
	}
}
