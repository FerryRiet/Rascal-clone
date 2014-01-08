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
	//L_*	= What location in the signature (Ls are Mutually Exlusive)
	//A_*	= TYPEARG specifics (As are Mutually Exlusive)
	//T_*	= Type (Ts are Mutually Exlusive)
	//B_*	= Bound to x indicator (Bs are Mutually Exlusive)
	static class ParamType //TODO Maybe make pType a class with enum fields instead. Marked fields with what it would become
	{
		//Boolean (isArray)
		public final static int ARRAY = 1;
		//Enum (ESigLocation)
		public final static int L_PARAM = 2;
		public final static int L_RETURN = 4;
		public final static int L_TYPEPARAM = 8;
		public final static int L_SUPER = 16;
		public final static int L_TYPEARG = 32;
		//Enum (EBoundType)
		public final static int A_LOWERBOUND = 64;
		public final static int A_UPPERBOUND = 128;
		public final static int A_INSTANCE = 256;
		//Enum (EType)
		public final static int T_BASE = 512;
		public final static int T_TYPEVAR = 1024;
		public final static int T_CLASS = 2048;
		//Boolean (isInterface)
		public final static int B_CLASS = 4096;
		public final static int B_INTERFACE = 8192;
	};
	
	private final JarConverter jc;
	private final String jarFileName;
	private final String classFileName;
	private final String classNamePath;
	private final String elemPath;
	private final int ignoreSigLoc;
	
	private LinkedHashMap<String, IConstructor> globalTypeParams;
	private LinkedHashMap<String, IConstructor> typeParams;
	private ArrayList<IConstructor> params;
	private IConstructor returnValue;
	
	//Reset these 3 after each item
	private int pType;
	private String typeParamName; //Name of the Type Param if (pType & ParamType.T_TYPEPARAM) != 0
	private int arrayDim; //Total dimensions of the array if (pType & ParamType.ARRAY) != 0
	
	private int paramInd = -1; //Index of the param if (pType & ParamType.T_PARAM) != 0
	
	//These are use to build the final entries
	private Stack<SimpleEntry<String, Integer>> itemStack;
	private LinkedHashMap<String, String> stackDep;
	private IConstructor stackResult;
	
	public Jar2M3SignatureVisitor(JarConverter jc, String jarFileName, String classFileName, String classNamePath)
	{
		this(jc, null, jarFileName, classFileName, classNamePath, null, 0);
	}
	
	public Jar2M3SignatureVisitor(JarConverter jc, LinkedHashMap<String, IConstructor> globalTypeParams,
		String jarFileName, String classFileName, String classNamePath, String elemPath, int ignoreSigLoc)
	{
		super(Opcodes.ASM4);
		
		this.jc = jc;
		this.globalTypeParams = globalTypeParams;
		this.jarFileName = jarFileName;
		this.classFileName = classFileName;
		this.classNamePath = classNamePath;
		this.elemPath = elemPath;
		this.ignoreSigLoc = ignoreSigLoc;
		
		typeParams = new LinkedHashMap<String, IConstructor>();
		params = new ArrayList<IConstructor>();
		
		itemStack = new Stack<SimpleEntry<String, Integer>>();
		stackDep = new LinkedHashMap<String, String>();
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
	
	private void reset() //NOTE: Should be called by all type functions (base, class, type param)
	{
		pType = 0;
		typeParamName = null;
		arrayDim = 0;
	}
	
	private void buildStackResult() //NOTE: Should be called by all terminating functions
	{
		if(itemStack.isEmpty()) return;
		
		SimpleEntry<String, Integer> entry = itemStack.pop();
		String type = entry.getKey();
		int pType = entry.getValue();
		
		if((pType & ParamType.T_BASE) != 0)
		{
			stackResult = instantiateBaseType(type);
		}
		else if((pType & ParamType.T_TYPEVAR) != 0)
		{
			stackResult = instantiateTypeVar(type);
		}
		else if((pType & ParamType.T_CLASS) != 0)
		{
			stackResult = instantiateClassType(type, stackResult, (pType & ParamType.B_INTERFACE) != 0);
		}
		else
		{
			throw new RuntimeException("SigVisitor encountered an unknown type while parsing the signature.");
		}
		
		if((pType & ParamType.A_LOWERBOUND) != 0) //Generic<? super X>
		{
			stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("super", stackResult));
		}
		else if((pType & ParamType.A_UPPERBOUND) != 0) //Generic<? extends X>
		{
			stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("extends", stackResult));
		}
		else if((pType & ParamType.A_INSTANCE) != 0) //Generic<X>
		{
			//TODO Should this be wrapped in a wildcard? It's not really written as Generic<? instanceof X> in Java...
			//In that case we'll need another wrapping TypeSymbol or move 'instanceof' from Bound to TypeSymbol.
			stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("instanceof", stackResult));
		}
		
		//Output after everything has been processed
		if(itemStack.isEmpty()) outputEntries(pType);
	}
	
	private void outputEntries(int pType)
	{
		//Type parameters don't have @types or @typeDependency entries
		if(stackResult == null || (pType & ParamType.L_TYPEPARAM) != 0) return;
		
		String path = elemPath;
		
		try
		{
			String scheme;
			
			if((pType & ParamType.L_RETURN) == 0)
			{
				if((pType & ParamType.L_PARAM) != 0)
				{
					scheme = "java+parameter";
					path += "/param" + paramInd;
					params.add(stackResult);
				}
				else if((pType & ParamType.L_SUPER) != 0)
				{
					scheme = "java+field";
				}
				else
				{
					throw new RuntimeException("SigVisitor encountered an unknown SigLoc while parsing the signature.");
				}
				
				//Types entry of item.
				jc.insert(jc.types, JavaToRascalConverter.values.sourceLocation(scheme, "", classNamePath + "/" + path), stackResult);
			}
			else
			{
				scheme = "java+method";
				returnValue = stackResult;
			}
			
			for(Entry<String, String> entry : stackDep.entrySet())
			{
				//Item depends on type x
				jc.insert(jc.typeDependency,
					JavaToRascalConverter.values.sourceLocation(scheme, "", classNamePath + "/" + path),
					JavaToRascalConverter.values.sourceLocation(entry.getValue(), "", entry.getKey()));
			}
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
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
	
	private IConstructor instantiateBaseType(String type)
	{
		if((pType & ignoreSigLoc) != 0) return null;
		
		return mapArray(jc.constructTypeSymbolNode(type));
	}
	
	private IConstructor instantiateClassType(String type, IConstructor typeParam, boolean isInterface)
	{
		if((pType & ignoreSigLoc) != 0) return null;
		
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
			
		try
		{
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
		if((pType & ignoreSigLoc) != 0) return null;
		
		return mapArray(typeParams.containsKey(type) ? typeParams.get(type) : globalTypeParams.get(type));
	}
	
	@Override
	public void visitBaseType(char descriptor) //NOTE: visitBaseType is a termination function
	{
		String type = Signature.toString(String.valueOf(descriptor));
		
		System.out.println(String.format("BASE TYPE: %s, %s", descriptor, type));
		
		pType |= ParamType.T_BASE;
		stackDep.put(type, "java+primitiveType");
		itemStack.push(new SimpleEntry<String, Integer>(type, pType));
		
		buildStackResult();
		reset();
		
		super.visitBaseType(descriptor);
	}
	
	@Override
	public void visitTypeVariable(String type) //NOTE: visitTypeVariable is a termination function
	{
		System.out.println("TYPE VAR: " + type);
		
		pType |= ParamType.T_TYPEVAR;
		stackDep.put(type, "java+typeVariable");
		itemStack.push(new SimpleEntry<String, Integer>(type, pType));
		
		buildStackResult();
		reset();
		
		super.visitTypeVariable(type);
	}
	
	@Override
	public void visitClassType(String type)
	{
		System.out.println("CLASS TYPE: " + type);
		
		if(type.equalsIgnoreCase("java/lang/Object")) //Special case; Object is a baseType
		{
			if((pType & ParamType.L_TYPEPARAM) != 0)
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
			
			pType |= ParamType.T_BASE;
			stackDep.put("object", "java+primitiveType");
			itemStack.push(new SimpleEntry<String, Integer>("object", pType));
			
			buildStackResult(); //Since object is a baseType, we became a termination function
		}
		else
		{
			boolean isInterface = (pType & ParamType.B_INTERFACE) != 0;
			
			if((pType & ParamType.L_TYPEPARAM) != 0)
			{
				try
				{
					typeParams.put(typeParamName, jc.constructTypeSymbolNode("typeParameter",
						JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", classNamePath + "/" + typeParamName),
						jc.constructBoundNode("extends", instantiateClassType(type, null, isInterface))));
				}
				catch (URISyntaxException e)
				{
					e.printStackTrace();
				}
			}
			
			pType |= ParamType.T_CLASS;
			stackDep.put(type, isInterface ? "java+interface" : "java+class");
			itemStack.push(new SimpleEntry<String, Integer>(type, pType));
		}

		reset();
		
		super.visitClassType(type);
	}
	
	@Override
	public void visitFormalTypeParameter(String name)
	{
		System.out.println(String.format("TYPE PARAM: %s", name));
		
		pType |= ParamType.L_TYPEPARAM;
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
		
		pType |= ParamType.L_TYPEARG;
		
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
		System.out.println("TYPE ARG (UNBOUNDED)");
		
		pType |= ParamType.L_TYPEARG;
		
		//Supposedly called for unbounded type args. Did not see it getting called at all.
		//Cause is most likely because of being a JAR; everything not bound becomes bound to java/lang/Object.
		
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
		
		pType |= ParamType.L_PARAM;
		paramInd++;
		
		return super.visitParameterType();
	}
	
	@Override
	public SignatureVisitor visitReturnType()
	{
		System.out.println("IS RETURN");
		
		pType |= ParamType.L_RETURN;
		
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

		pType |= ParamType.L_SUPER;
		
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
	public void visitEnd() //NOTE: visitEnd is the termination function if; !visitBaseType && !visitTypeVariable && visitClassType
	{
		System.out.println(">>SIG END<<");
		
		buildStackResult();
		reset();
		
		super.visitEnd();
	}
}
