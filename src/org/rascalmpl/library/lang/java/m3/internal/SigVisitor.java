package org.rascalmpl.library.lang.java.m3.internal;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

class SigVisitor extends SignatureVisitor
{
	//T_* = Types (Ts are Mutually Exlusive)
	//A_* = TYPEARG specifics (As are Mutually Exlusive)
	private static class ParamType
	{
		public final static int ARRAY = 0x0001;
		public final static int T_PARAM = 0x0002;
		public final static int T_RETURN = 0x0004;
		public final static int T_TYPEPARAM = 0x008;
		public final static int T_SUPER = 0x016;
		
		public final static int TYPEARG = 0x0032;
		public final static int A_LOWERBOUND = 0x0064;
		public final static int A_UPPERBOUND = 0x0128;
		public final static int A_INSTANCE = 0x0256;
	};
	
	private final JarConverter jc;
	private final JarConverter.Jar2M3ClassVisitor cv;
	private final String elemPath;
	private final boolean ignoreReturnType;
	
	private LinkedHashMap<String, IConstructor> globalTypeParams;
	private LinkedHashMap<String, IConstructor> typeParams;
	private ArrayList<IValue> params;
	private IValue returnValue;
	
	//Reset these after each item
	private int pType;
	private String typeParamName; //Name of the Type Param if (pType & ParamType.T_TYPEPARAM) != 0
	private ISourceLocation typeParamSrcLoc; //Src loc for the Type Param if (pType & ParamType.T_TYPEPARAM) != 0
	private int arrayDim; //Total dimensions of the array if (pType & ParamType.ARRAY) != 0
	
	private int paramInd; //Index of the param if (pType & ParamType.T_PARAM) != 0
	
	public SigVisitor(JarConverter jc, JarConverter.Jar2M3ClassVisitor cv, LinkedHashMap<String, IConstructor> globalTypeParams,
		String elemPath, boolean ignoreReturnType)
	{
		super(Opcodes.ASM4);
		
		this.jc = jc;
		this.cv = cv;
		this.globalTypeParams = globalTypeParams;
		this.elemPath = elemPath;
		this.ignoreReturnType = ignoreReturnType;
		
		typeParams = new LinkedHashMap<String, IConstructor>();
		params = new ArrayList<IValue>();
	}
	
	public LinkedHashMap<String, IConstructor> getTypeParameters()
	{
		return typeParams;
	}
	
	public ArrayList<IValue> getParameters()
	{
		return params;
	}
	
	public IValue getReturnValue()
	{
		return returnValue;
	}
	
	private void reset()
	{
		pType = 0;
		typeParamName = null;
		typeParamSrcLoc = null;
		arrayDim = 0;
	}
	
	private IConstructor mapArray(IConstructor baseValue)
	{
		if((pType & ParamType.ARRAY) != 0)
		{
			for(int dim = 1; dim <= arrayDim; dim++)
			{
				baseValue = jc.constructTypeSymbolNode("array", baseValue, JavaToRascalConverter.values.integer(dim));
			}
		}
		
		return baseValue;
	}
	
	private void createTypeDependencyEntry(String scheme, String path, String typeScheme, String type)
		throws URISyntaxException
	{
		jc.insert(jc.typeDependency,
			JavaToRascalConverter.values.sourceLocation(scheme, "", path),
			JavaToRascalConverter.values.sourceLocation(typeScheme, "", type));
	}
	
	private void createTypesEntry(String scheme, String path, IConstructor returnValue)
		throws URISyntaxException
	{
		jc.insert(jc.types, JavaToRascalConverter.values.sourceLocation(scheme, "", path), returnValue);
	}
	
	private void handleBaseType(String fullType)
	{
		String scheme;
		String path = elemPath;
		
		IConstructor cons = mapArray(jc.constructTypeSymbolNode(fullType));
		
		if((pType & ParamType.T_TYPEPARAM) == 0)
		{
			if((pType & ParamType.T_PARAM) != 0)
			{
				scheme = "java+parameter";
				path += "/" + fullType + paramInd;
				params.add(cons);
			}
			else if((pType & ParamType.T_RETURN) != 0)
			{
				scheme = "java+method";
				returnValue = cons;
				
				if(ignoreReturnType)
				{
					return;
				}
			}
			else if((pType & ParamType.T_SUPER) != 0)
			{
				scheme = "java+field";
			}
			else
			{
				reset();
				return;
			}
			
			try
			{
				//Item depends on type x
				createTypeDependencyEntry(scheme, cv.getClassNamePath() + "/" + path, "java+primitiveType", fullType);
				
				//Types of item is standard type.
				createTypesEntry(scheme, cv.getClassNamePath() + "/" + path, cons);
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public void visitBaseType(char descriptor)
	{
		System.out.println(String.format("BASE TYPE: %s, %s", descriptor, Signature.toString(String.valueOf(descriptor))));
		
		String fullType = Signature.toString(String.valueOf(descriptor));
		
		if((pType & ParamType.T_TYPEPARAM) != 0)
		{
			typeParams.put(typeParamName, jc.constructTypeSymbolNode("typeParameter", typeParamSrcLoc,
				jc.constructBoundNode("extends", jc.constructTypeSymbolNode(fullType))));
		}
		
		handleBaseType(fullType);
		
		reset();
	}
	
	@Override
	public void visitClassType(String type)
	{
		System.out.println("CLASS TYPE: " + type);
		
		boolean typeIsObj = type.equalsIgnoreCase("java/lang/Object");
		
		if(typeIsObj)
		{
			if((pType & ParamType.T_TYPEPARAM) != 0)
			{
				typeParams.put(typeParamName, jc.constructTypeSymbolNode("typeParameter", typeParamSrcLoc,
					jc.constructBoundNode("unbounded")));
			}
			handleBaseType("object");
		}
		else
		{
			String scheme;
			String path = elemPath;
			
			try
			{
				if((pType & ParamType.T_TYPEPARAM) != 0)
				{
					typeParams.put(typeParamName, jc.constructTypeSymbolNode("typeParameter", typeParamSrcLoc,
						jc.constructBoundNode("extends", jc.constructTypeSymbolNode("class",
							JavaToRascalConverter.values.sourceLocation("java+class", "", "/" + type),
							JavaToRascalConverter.values.list(JavaToRascalConverter.TF.sourceLocationType())))));
				}
				else
				{
					//TODO Check for completeness
					IConstructor cons = mapArray(jc.constructTypeSymbolNode("class",
						JavaToRascalConverter.values.sourceLocation("java+class", "", "/" + type),
						JavaToRascalConverter.values.list(JavaToRascalConverter.TF.sourceLocationType())));
					
					if((pType & ParamType.T_PARAM) != 0)
					{
						scheme = "java+parameter";
						path += "/" + type + paramInd;
						params.add(cons);
					}
					else if((pType & ParamType.T_RETURN) != 0)
					{
						scheme = "java+method";
						returnValue = cons;
						
						if(ignoreReturnType)
						{
							reset();
							return;
						}
					}
					else if((pType & ParamType.T_SUPER) != 0)
					{
						scheme = "java+field";
					}
					else
					{
						reset();
						return;
					}
					
					//Item depends on type x
					createTypeDependencyEntry(scheme, cv.getClassNamePath() + "/" + path, "java+class", type);
					
					//Type entry of item is class.
					createTypesEntry(scheme, cv.getClassNamePath() + "/" + path, cons);
				}
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
		}
		
		reset();
	}
	
	@Override
	public void visitTypeVariable(String var)
	{
		System.out.println("TYPE VAR: " + var);
		
		String scheme;
		String path = elemPath;
		IConstructor cons = typeParams.containsKey(var) ? mapArray(typeParams.get(var)) : mapArray(globalTypeParams.get(var));
		
		if((pType & ParamType.T_TYPEPARAM) == 0)
		{
			if((pType & ParamType.T_PARAM) != 0)
			{
				scheme = "java+parameter";
				path += "/" + var + paramInd;
				params.add(cons);
			}
			else if((pType & ParamType.T_RETURN) != 0)
			{
				scheme = "java+method";
				returnValue = cons;
				
				if(ignoreReturnType)
				{
					reset();
					return;
				}
			}
			else if((pType & ParamType.T_SUPER) != 0)
			{
				scheme = "java+field";
			}
			else
			{
				reset();
				return;
			}
			
			try
			{
				//Item depends on type x
				createTypeDependencyEntry(scheme, cv.getClassNamePath() + "/" + path, "java+typeVariable", var);
				
				//Type entry for item
				createTypesEntry(scheme, cv.getClassNamePath() + "/" + path, cons);
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
		}
		
		reset();
	}
	
	@Override
	public void visitFormalTypeParameter(String name)
	{
		System.out.println(String.format("TYPE PARAM: %s", name));
		
		pType |= ParamType.T_TYPEPARAM;
		typeParamName = name;
		
		try
		{
			//TypeVar declared in classFile
			typeParamSrcLoc = JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", cv.getClassNamePath() + "/" + name);
			jc.insert(jc.declarations, typeParamSrcLoc,
				JavaToRascalConverter.values.sourceLocation(cv.getJarFileName() + "!" + cv.getClassFileName()));
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public SignatureVisitor visitTypeArgument(char arg)
	{
		System.out.println("TYPE ARG: " + arg);
		
		pType |= ParamType.TYPEARG;
		
		switch(arg)
		{
			case SUPER:
				pType |= ParamType.A_LOWERBOUND;
				break;
			case EXTENDS:
				pType |= ParamType.A_UPPERBOUND;
				break;
			case INSTANCEOF:
				pType |= ParamType.A_INSTANCE;
				break;
		}
		
		//TODO process different bounds
		
		return super.visitTypeArgument(arg);
	}
	
	@Override
	public void visitTypeArgument()
	{
		System.out.println("IS TYPE ARG");
	}
	
	@Override
	public SignatureVisitor visitArrayType()
	{
		System.out.println("IS ARRAY");
		
		pType |= ParamType.ARRAY;
		arrayDim++;
		
		return super.visitArrayType();
	}
	
	@Override
	public SignatureVisitor visitParameterType()
	{
		System.out.println("IS PARAM");
		
		pType |= ParamType.T_PARAM;
		paramInd++;
		
		return super.visitParameterType();
	}
	
	@Override
	public SignatureVisitor visitReturnType()
	{
		System.out.println("IS RETURN");
		
		pType |= ParamType.T_RETURN;
		
		return super.visitReturnType();
	}
	
	@Override
	public SignatureVisitor visitExceptionType()
	{
		System.out.println("IS EXCEPTION");
		
		//TODO pType
		
		return super.visitExceptionType();
	}
	
	@Override
	public void visitInnerClassType(String type)
	{
		System.out.println("INNER CLASS TYPE: " + type);
		
		//TODO pType
	}
	
	@Override
	public SignatureVisitor visitSuperclass()
	{
		System.out.println("IS SUPER");

		pType |= ParamType.T_SUPER;
		
		return super.visitSuperclass();
	}
	
	@Override
	public SignatureVisitor visitInterface()
	{
		System.out.println("IS INTERFACE");
		
		//TODO pType
		
		return super.visitInterface();
	}
	
	@Override
	public SignatureVisitor visitClassBound()
	{
		System.out.println("CLASS BOUND");
		
		return super.visitClassBound();
	}
	
	@Override
	public SignatureVisitor visitInterfaceBound()
	{
		System.out.println("INTERFACE BOUND");
		
		return super.visitInterfaceBound();
	}
	
	@Override
	public void visitEnd()
	{
		
	}
}
