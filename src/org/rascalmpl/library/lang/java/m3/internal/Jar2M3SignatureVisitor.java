package org.rascalmpl.library.lang.java.m3.internal;

import java.net.URISyntaxException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Stack;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

class Jar2M3SignatureVisitor extends SignatureVisitor
{
	//IS_*	= What location in the signature (ISs are Mutually Exlusive)
	//A_*	= TYPEARG specifics (As are Mutually Exlusive)
	//T_*	= Type (Ts are Mutually Exlusive)
	//B_*	= Bound to x indicator (Bs are Mutually Exlusive)
	static class ParamType
	{
		public final static int ARRAY = 1;
		public final static int IS_PARAM = 2;
		public final static int IS_RETURN = 4;
		public final static int IS_TYPEPARAM = 8;
		public final static int IS_SUPER = 16;
		
		public final static int TYPEARG = 32;
		public final static int A_LOWERBOUND = 64;
		public final static int A_UPPERBOUND = 128;
		public final static int A_INSTANCE = 256;
		
		public final static int T_TYPEVAR = 512;
		public final static int T_CLASS = 1024;
		
		public final static int B_CLASS = 2048;
		public final static int B_INTERFACE = 4096;
	};
	
	private final JarConverter jc;
	private final String jarFileName;
	private final String classFileName;
	private final String classNamePath;
	private final String elemPath;
	private final int ignoreType;
	
	private LinkedHashMap<String, IConstructor> globalTypeParams;
	private LinkedHashMap<String, IConstructor> typeParams;
	private ArrayList<IConstructor> params;
	private IConstructor returnValue;
	
	//Reset these 3 after each item
	private int pType;
	private String typeParamName; //Name of the Type Param if (pType & ParamType.T_TYPEPARAM) != 0
	private int arrayDim; //Total dimensions of the array if (pType & ParamType.ARRAY) != 0
	
	private int paramInd = -1; //Index of the param if (pType & ParamType.T_PARAM) != 0
	
	private Stack<SimpleEntry<String, Integer>> itemStack;
	
	public Jar2M3SignatureVisitor(JarConverter jc, String jarFileName, String classFileName, String classNamePath)
	{
		this(jc, null, jarFileName, classFileName, classNamePath, null, 0);
	}
	
	public Jar2M3SignatureVisitor(JarConverter jc, LinkedHashMap<String, IConstructor> globalTypeParams,
		String jarFileName, String classFileName, String classNamePath, String elemPath, int ignoreType)
	{
		super(Opcodes.ASM4);
		
		this.jc = jc;
		this.globalTypeParams = globalTypeParams;
		this.jarFileName = jarFileName;
		this.classFileName = classFileName;
		this.classNamePath = classNamePath;
		this.elemPath = elemPath;
		this.ignoreType = ignoreType;
		
		typeParams = new LinkedHashMap<String, IConstructor>();
		params = new ArrayList<IConstructor>();
		itemStack = new Stack<SimpleEntry<String, Integer>>();
	}
	
	public LinkedHashMap<String, IConstructor> getTypeParameters()
	{
		return typeParams;
	}
	
	public ArrayList<IConstructor> getParameters()
	{
		return params;
	}
	
	public IValue getReturnValue()
	{
		return returnValue;
	}
	
	private void reset() //NOTE: Should be called by all terminating functions
	{
		pType = 0;
		typeParamName = null;
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
	
	private void createEntries(IConstructor cons, LinkedHashMap<String, String> dependencies)
	{
		if(cons == null) return;
		
		String path = elemPath;
		
		try
		{
			if((pType & ParamType.IS_TYPEPARAM) == 0)
			{
				String scheme;
				
				if((pType & ParamType.IS_RETURN) == 0)
				{
					if((pType & ParamType.IS_PARAM) != 0)
					{
						scheme = "java+parameter";
						path += "/param" + paramInd;
						params.add(cons);
					}
					else if((pType & ParamType.IS_SUPER) != 0)
					{
						scheme = "java+field";
					}
					else
					{
						throw new RuntimeException("SigVisitor encountered an unknown type while parsing the signature.");
					}
					
					//Types of item is standard type.
					jc.insert(jc.types, JavaToRascalConverter.values.sourceLocation(scheme, "", classNamePath + "/" + path), cons);
				}
				else
				{
					scheme = "java+method";
					returnValue = cons;
				}
				
				for(Entry<String, String> entry : dependencies.entrySet())
				{
					//Item depends on type x
					jc.insert(jc.typeDependency,
						JavaToRascalConverter.values.sourceLocation(scheme, "", classNamePath + "/" + path),
						JavaToRascalConverter.values.sourceLocation(entry.getValue(), "", entry.getKey()));
				}
			}
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
	}
	
	private IConstructor instantiateBaseType(String type)
	{
		if((pType & ignoreType) != 0) return null;
		
		return mapArray(jc.constructTypeSymbolNode(type));
	}
	
	private IConstructor instantiateClassType(String type, IConstructor typeParam, boolean isInterface)
	{
		if((pType & ignoreType) != 0) return null;
		
		try
		{
			IList typeParamsList = typeParam != null ? JavaToRascalConverter.values.list(typeParam)
				: JavaToRascalConverter.values.list(JavaToRascalConverter.TF.voidType());
			
			String tsNodeName;
			String scheme;
			
			if(isInterface)
			{
				tsNodeName = "interface";
				scheme = "java+interface";
			}
			else
			{
				tsNodeName = "class";
				scheme = "java+class";
			}
			
			//TODO Check for completeness (this may possibly make some interfaces java+class entries)
			return mapArray(jc.constructTypeSymbolNode(tsNodeName,
				JavaToRascalConverter.values.sourceLocation(scheme, "", "/" + type),
				typeParamsList));
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
		
		return null;
	}
	
	private IConstructor instantiateTypeVar(String type)
	{
		if((pType & ignoreType) != 0) return null;
		
		return mapArray(typeParams.containsKey(type) ? typeParams.get(type) : globalTypeParams.get(type));
	}
	
	@Override
	public void visitBaseType(char descriptor) //NOTE: visitBaseType is a termination function
	{
		String type = Signature.toString(String.valueOf(descriptor));
		
		System.out.println(String.format("BASE TYPE: %s, %s", descriptor, type));
		
		LinkedHashMap<String, String> dep = new LinkedHashMap<String, String>();
		dep.put(type, "java+primitiveType");
		createEntries(instantiateBaseType(type), dep);
		
		reset();
		
		super.visitBaseType(descriptor);
	}
	
	@Override
	public void visitTypeVariable(String var) //NOTE: visitTypeVariable is a termination function if the itemQueue is empty
	{
		System.out.println("TYPE VAR: " + var);
		
		if(itemStack.isEmpty())
		{
			LinkedHashMap<String, String> dep = new LinkedHashMap<String, String>();
			dep.put(var, "java+typeVariable");
			createEntries(instantiateTypeVar(var), dep);
		}
		else
		{
			pType |= ParamType.T_TYPEVAR;
			itemStack.push(new SimpleEntry<String, Integer>(var, pType));
		}
		
		reset();
		
		super.visitTypeVariable(var);
	}
	
	@Override
	public void visitClassType(String type)
	{
		System.out.println("CLASS TYPE: " + type);
		
		if(type.equalsIgnoreCase("java/lang/Object")) //Special case
		{
			if((pType & ParamType.IS_TYPEPARAM) != 0)
			{
				try
				{
					typeParams.put(typeParamName, jc.constructTypeSymbolNode("typeParameter",
						JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", classNamePath + "/" + typeParamName),
						jc.constructBoundNode("unbounded")));
				}
				catch (URISyntaxException e)
				{
					e.printStackTrace();
				}
			}
			
			LinkedHashMap<String, String> dep = new LinkedHashMap<String, String>();
			dep.put("object", "java+primitiveType");
			createEntries(instantiateBaseType("object"), dep);
		}
		else
		{
			if((pType & ParamType.IS_TYPEPARAM) != 0)
			{
				try
				{
					typeParams.put(typeParamName, jc.constructTypeSymbolNode("typeParameter",
						JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", classNamePath + "/" + typeParamName),
						jc.constructBoundNode("extends", instantiateClassType(type, null, (pType & ParamType.B_INTERFACE) != 0))));
				}
				catch (URISyntaxException e)
				{
					e.printStackTrace();
				}
			}
			
			pType |= ParamType.T_CLASS;
			itemStack.push(new SimpleEntry<String, Integer>(type, pType));
		}
		
		reset();
		
		super.visitClassType(type);
	}
	
	@Override
	public void visitFormalTypeParameter(String name)
	{
		System.out.println(String.format("TYPE PARAM: %s", name));
		
		pType |= ParamType.IS_TYPEPARAM;
		typeParamName = name;
		
		try
		{
			//TypeVar declared in classFile
			jc.insert(jc.declarations,
				JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", classNamePath + "/" + name),
				JavaToRascalConverter.values.sourceLocation(jarFileName + "!" + classFileName));
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
		
		super.visitFormalTypeParameter(name);
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
		
		return super.visitTypeArgument(arg);
	}
	
	@Override
	public void visitTypeArgument()
	{
		System.out.println("IS TYPE ARG");
		
		super.visitTypeArgument();
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
		
		pType |= ParamType.IS_PARAM;
		paramInd++;
		
		return super.visitParameterType();
	}
	
	@Override
	public SignatureVisitor visitReturnType()
	{
		System.out.println("IS RETURN");
		
		pType |= ParamType.IS_RETURN;
		
		return super.visitReturnType();
	}
	
	@Override
	public SignatureVisitor visitExceptionType()
	{
		System.out.println("IS EXCEPTION");
		
		//TODO pType?
		
		return super.visitExceptionType();
	}
	
	@Override
	public void visitInnerClassType(String type)
	{
		System.out.println("INNER CLASS TYPE: " + type);
		
		//TODO pType?
		
		super.visitInnerClassType(type);
	}
	
	@Override
	public SignatureVisitor visitSuperclass()
	{
		System.out.println("IS SUPER");

		pType |= ParamType.IS_SUPER;
		
		return super.visitSuperclass();
	}
	
	@Override
	public SignatureVisitor visitInterface()
	{
		System.out.println("IS INTERFACE");
		
		//TODO pType?
		
		return super.visitInterface();
	}
	
	@Override
	public SignatureVisitor visitClassBound()
	{
		System.out.println("CLASS BOUND");
		
		pType |= ParamType.B_CLASS;
		
		return super.visitClassBound();
	}
	
	@Override
	public SignatureVisitor visitInterfaceBound()
	{
		System.out.println("INTERFACE BOUND");
		
		pType |= ParamType.B_INTERFACE;
		
		return super.visitInterfaceBound();
	}
	
	@Override
	public void visitEnd() //NOTE: visitEnd is the termination function if; !visitBaseType && !(visitTypeVariable && itemStack.isEmpty)
	{
		System.out.println(">>SIG END<<");
		
		IConstructor stackResult = null;
		LinkedHashMap<String, String> dep = new LinkedHashMap<String, String>();
		
		while(!itemStack.isEmpty())
		{
			SimpleEntry<String, Integer> entry = itemStack.pop();
			String type = entry.getKey();
			pType = entry.getValue();
			String depScheme;
			
			IConstructor typeCons;
			if((pType & ParamType.T_TYPEVAR) != 0)
			{
				typeCons = instantiateTypeVar(type);
				depScheme = "java+typeParameter";
			}
			else if((pType & ParamType.T_CLASS) != 0)
			{
				boolean isInterface = (pType & ParamType.B_INTERFACE) != 0;
				typeCons = instantiateClassType(type, stackResult, isInterface);
				depScheme = isInterface ? "java+interface" : "java+class";
			}
			else
			{
				throw new RuntimeException("SigVisitor encountered an unknown type while parsing the signature.");
			}
			
			dep.put(type, depScheme);
			
			if((pType & ParamType.A_LOWERBOUND) != 0)
			{
				stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("super", typeCons));
			}
			else if((pType & ParamType.A_UPPERBOUND) != 0)
			{
				stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("extends", typeCons));
			}
			else if((pType & ParamType.A_INSTANCE) != 0)
			{
				stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("instanceof", typeCons));
			}
			else
			{
				stackResult = typeCons;
			}
		}
		
		createEntries(stackResult, dep);
		
		reset();
		
		super.visitEnd();
	}
}
