package org.rascalmpl.library.lang.java.m3.internal;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.rascalmpl.library.lang.java.m3.internal.Jar2M3SigEntryData.ESigLocation;

//TODO Adapt debugging Sysouts to only run when actually debugging
class Jar2M3ClassVisitor extends ClassVisitor
{
	private enum EOpcodeType { CLASS, METHOD, FIELD };
	
	private final JarConverter jc;
	private final String jarFileName;
	private final String classFileName;
	private String classNamePath;
	private String classScheme;
	private boolean classIsEnum;
	private LinkedHashMap<String, IConstructor> globalTypeParams;
	
	public Jar2M3ClassVisitor(JarConverter jc, ISourceLocation jarLoc)
	{
		super(Opcodes.ASM4, null);
		
		this.jc = jc;
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
	
	private void processAccess(int access, String scheme, String path, EOpcodeType opcodeType)
		throws URISyntaxException
	{
		for(int code = 0x0001; code < 0x8000; code = code << 1)
		{
			if((access & code) != 0)
			{
				IConstructor cons = mapFieldAccessCode(code, opcodeType);
				if(cons != null)
				{
					jc.insert(jc.modifiers,
						JavaToRascalConverter.values.sourceLocation(scheme, "", path), cons);
				}
			}
		}
	}
	
	private IConstructor mapFieldAccessCode(int code, EOpcodeType opcodeType)
	{
		switch (code)
		{
			case Opcodes.ACC_PUBLIC:
				return jc.constructModifierNode("public");
			case Opcodes.ACC_PRIVATE:
				return jc.constructModifierNode("private");
			case Opcodes.ACC_PROTECTED:
				return jc.constructModifierNode("protected");
			case Opcodes.ACC_STATIC:
				return jc.constructModifierNode("static");
			case Opcodes.ACC_FINAL:
				return jc.constructModifierNode("final");
			case Opcodes.ACC_SYNCHRONIZED:
				if(opcodeType == EOpcodeType.CLASS) return null;
				return jc.constructModifierNode("synchronized");
			case Opcodes.ACC_ABSTRACT:
				return jc.constructModifierNode("abstract");
			case Opcodes.ACC_VOLATILE:
				return jc.constructModifierNode("volatile");
			case Opcodes.ACC_TRANSIENT:
				return jc.constructModifierNode("transient");
			case Opcodes.ACC_NATIVE:
				return jc.constructModifierNode("native");
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
			processAccess(access, classScheme, classNamePath, EOpcodeType.CLASS);
			
			IList typeParamsList = JavaToRascalConverter.values.list(JavaToRascalConverter.TF.voidType());
			if(signature != null)
			{
				Jar2M3SignatureVisitor sv = new Jar2M3SignatureVisitor(jc, jarFileName, classFileName, classNamePath);
				new SignatureReader(signature).accept(sv);
				
				globalTypeParams = sv.getTypeParameters();
				typeParamsList = JavaToRascalConverter.values.list(globalTypeParams.values().toArray(new IValue[globalTypeParams.size()]));
			}
			
			//M3@types
			if(classIsEnum)
			{
				jc.insert(jc.types,
					JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
					jc.constructTypeSymbolNode("enum", JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath)));
			}
			else
			{
				jc.insert(jc.types,
					JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
					jc.constructTypeSymbolNode((access & Opcodes.ACC_INTERFACE) != 0 ? "interface" : "class",
						JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
						typeParamsList));
			}
			
			//item declared in jar!item.class
			jc.insert(jc.declarations,
				JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
				JavaToRascalConverter.values.sourceLocation(jarFileName + "!" + classFileName));
			
			//Jar contains package
			//TODO I feel like this entry is not correct/shouldn't be there. Can't find anything similar in M3s from src.
			jc.insert(jc.containment,
				JavaToRascalConverter.values.sourceLocation(classScheme, "",  classNamePath),
				JavaToRascalConverter.values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName));
//			jc.insert(jc.containment,
//				JavaToRascalConverter.values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName),
//				JavaToRascalConverter.values.sourceLocation("java+package", "", "/" + classFileName.substring(0, classFileName.lastIndexOf('/'))));
			
			//Package contains compilation unit
			//TODO Shouldn't the class file name be appended to the jarFileName?
            jc.insert(jc.containment,
        		JavaToRascalConverter.values.sourceLocation("java+package", "", "/" + classFileName.substring(0, classFileName.lastIndexOf('/'))),
        		JavaToRascalConverter.values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName));
            
            //Compilation unit contains item
			//TODO Shouldn't the class file name be appended to the jarFileName?
            jc.insert(jc.containment,
        		JavaToRascalConverter.values.sourceLocation("java+compilationUnit", "", "/jar:///" + jarFileName),
        		JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath));
			
			if(superName != null && !(superName.equalsIgnoreCase("java/lang/Object")
				|| superName.equalsIgnoreCase("java/lang/Enum")))
			{
				//x extends y
				jc.insert(jc.extendsRelations,
					JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
					JavaToRascalConverter.values.sourceLocation(classScheme, "", "/" + superName));
			}
			
			if((access & Opcodes.ACC_DEPRECATED) != 0)
            {
				//Deprecated annotation present
            	jc.insert(jc.annotations,
        			JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
        			JavaToRascalConverter.values.sourceLocation("java+interface", "", "/java/lang/Deprecated"));
            }
			
			for (String iFace : interfaces)
			{
				//x implements y
				jc.insert(jc.implementsRelations,
					JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
					JavaToRascalConverter.values.sourceLocation("java+interface", "", "/" + iFace));
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
			jc.insert(jc.containment,
				JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
				JavaToRascalConverter.values.sourceLocation("java+class", "", "/" + name.replace('$', '/')));
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
			if(!isEnumConstant) //No access modifiers or signatures for enum constants
			{
				processAccess(access, fieldScheme, classNamePath + "/" + name, EOpcodeType.FIELD);
				
				Jar2M3SignatureVisitor sv = new Jar2M3SignatureVisitor(jc, globalTypeParams, jarFileName, classFileName, classNamePath, name, ESigLocation.UNKNOWN);
				new SignatureReader(signature != null ? signature : desc).accept(sv);
			}
			else
			{
				//Enum constants depend on the enum
				jc.insert(jc.typeDependency,
        			JavaToRascalConverter.values.sourceLocation(fieldScheme, "", classNamePath + "/" + name),
        			JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath));
			}
			
			//Field declared in classFile
			jc.insert(jc.declarations,
				JavaToRascalConverter.values.sourceLocation(fieldScheme, "", classNamePath + "/" + name),
				JavaToRascalConverter.values.sourceLocation(jarFileName + "!" + classFileName));
			
			//Class contains field
			jc.insert(jc.containment,
				JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
				JavaToRascalConverter.values.sourceLocation(fieldScheme, "", classNamePath + "/" + name));
			
			//Deprecated annotation present
			if((access & Opcodes.ACC_DEPRECATED) != 0)
            {
            	jc.insert(jc.annotations,
        			JavaToRascalConverter.values.sourceLocation(fieldScheme, "", classNamePath + "/" + name),
        			JavaToRascalConverter.values.sourceLocation("java+interface", "", "/java/lang/Deprecated"));
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

		//String sig = Signature.toString(signature == null ? desc : signature);
		String sig = Signature.toString(desc); //NOTE: Comparing with a SRC M3, the above was invalid.
		sig = sig.substring(sig.indexOf('('), sig.indexOf(')') + 1);
		sig = sig.replaceAll("\\s+", "");
		sig = sig.replace('/', '.');
		
		try
		{
			processAccess(access, methodScheme, classNamePath + "/" + name + sig, EOpcodeType.METHOD);
			
			String sigToVisit = (signature != null ? signature : desc);
        	ESigLocation ignoreSigLoc = ESigLocation.UNKNOWN;
        	if(methodScheme == "java+constructor")
            {
        		//Constructor depends on the class
				jc.insert(jc.typeDependency,
					JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
					JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath));
        		
				//Don't include the return type; it's a constructor
				ignoreSigLoc = ESigLocation.RETURN;
            }
        	
        	//M3@types
        	Jar2M3SignatureVisitor sv = new Jar2M3SignatureVisitor(jc, globalTypeParams, jarFileName, classFileName, classNamePath, name + sig, ignoreSigLoc);
        	new SignatureReader(sigToVisit).accept(sv);
        	
        	ArrayList<IConstructor> paramValues = sv.getParameters();
        	IValue paramList = JavaToRascalConverter.values.list(paramValues.toArray(new IValue[paramValues.size()]));
        	
			IConstructor methodType;
			if(methodScheme == "java+constructor")
			{
				methodType = jc.constructTypeSymbolNode("constructor", JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig), paramList);
			}
			else
			{
				LinkedHashMap<String, IConstructor> typeParams = sv.getTypeParameters();
            	
				methodType = jc.constructTypeSymbolNode("method",
					JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
					JavaToRascalConverter.values.list(typeParams.values().toArray(new IValue[typeParams.size()])),
						sv.getReturnValue(), paramList);
			}
			
			jc.insert(jc.types,
				JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
				methodType);
			
			//Method declared in classFile
			jc.insert(jc.declarations,
				JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
				JavaToRascalConverter.values.sourceLocation(jarFileName + "!" + classFileName));
			
			//Class contains method
			jc.insert(jc.containment,
				JavaToRascalConverter.values.sourceLocation(classScheme, "", classNamePath),
				JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig));
			
			//Deprecated annotation present
            if((access & Opcodes.ACC_DEPRECATED) != 0)
            {
            	jc.insert(jc.annotations,
        			JavaToRascalConverter.values.sourceLocation(methodScheme, "", classNamePath + "/" + name + sig),
        			JavaToRascalConverter.values.sourceLocation("java+interface", "", "/java/lang/Deprecated"));
            }
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
