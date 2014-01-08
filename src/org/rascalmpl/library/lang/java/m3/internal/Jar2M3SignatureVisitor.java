package org.rascalmpl.library.lang.java.m3.internal;

import java.net.URISyntaxException;
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
import org.rascalmpl.library.lang.java.m3.internal.Jar2M3SigEntryData.EBoundType;
import org.rascalmpl.library.lang.java.m3.internal.Jar2M3SigEntryData.ESigLocation;
import org.rascalmpl.library.lang.java.m3.internal.Jar2M3SigEntryData.EType;

class Jar2M3SignatureVisitor extends SignatureVisitor
{
	private final JarConverter jc;
	private final String jarFileName;
	private final String classFileName;
	private final String classNamePath;
	private final String elemPath;
	private final ESigLocation ignoreSigLoc;
	
	private LinkedHashMap<String, IConstructor> globalTypeParams;
	private LinkedHashMap<String, IConstructor> typeParams;
	private ArrayList<IConstructor> params;
	private IConstructor returnValue;
	
	//These are use to build the final entries
	private Stack<Jar2M3SigEntryData> itemStack;
	private ArrayList<Entry<String, String>> stackDependencies;
	private IConstructor stackResult;
	
	private int paramInd = -1; //Index of the param if (pType & ParamType.T_PARAM) != 0
	
	//Reset for each item
	private Jar2M3SigEntryData entryData;
	
	public Jar2M3SignatureVisitor(JarConverter jc, String jarFileName, String classFileName, String classNamePath)
	{
		this(jc, null, jarFileName, classFileName, classNamePath, null, ESigLocation.UNKNOWN);
	}
	
	public Jar2M3SignatureVisitor(JarConverter jc, LinkedHashMap<String, IConstructor> globalTypeParams,
		String jarFileName, String classFileName, String classNamePath, String elemPath, ESigLocation ignoreSigLoc)
	{
		super(Opcodes.ASM4);
		
		this.jc = jc;
		this.globalTypeParams = globalTypeParams;
		this.jarFileName = jarFileName;
		this.classFileName = classFileName;
		this.classNamePath = classNamePath;
		this.elemPath = elemPath;
		this.ignoreSigLoc = ignoreSigLoc;
		
		typeParams = new LinkedHashMap<>();
		params = new ArrayList<>();
		
		itemStack = new Stack<>();
		stackDependencies = new ArrayList<>();
		
		entryData = new Jar2M3SigEntryData();
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
		entryData = new Jar2M3SigEntryData();
	}
	
	private void buildStackResult() //NOTE: Should be called by all terminating functions
	{
		if(itemStack.isEmpty()) return;
		
		entryData = itemStack.pop();
		
		switch(entryData.type)
		{
			case BASE:
				stackResult = instantiateBaseType(entryData.typeName);
				break;
			case TYPEVAR:
				stackResult = instantiateTypeVar(entryData.typeName);
				break;
			case CLASS:
				stackResult = instantiateClassType(entryData.typeName, stackResult, entryData.isInterface);
				break;
			default:
				throw new RuntimeException("SigVisitor encountered an unknown type while parsing the signature.");
		}
		
		switch (entryData.boundType)
		{
			case LOWERBOUND: //Generic<? super X>
				stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("super", stackResult));
				break;
			case UPPERBOUND: //Generic<? extends X>
				stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("extends", stackResult));
				break;
			case INSTANCEOF: //Generic<X>
				//TODO Should this be wrapped in a wildcard? It's not really written as Generic<? instanceof X> in Java...
				//In that case we'll need another wrapping TypeSymbol or move 'instanceof' from Bound to TypeSymbol.
				stackResult = jc.constructTypeSymbolNode("wildcard", jc.constructBoundNode("instanceof", stackResult));
				break;
			default:
				break;
		}
		
		if(entryData.getDependsOn() != null)
		{
			stackDependencies.add(entryData.getDependsOn());
		}
		
		//Output after everything has been processed
		if(itemStack.isEmpty()) outputEntries();
	}
	
	private void outputEntries()
	{
		//Type parameters don't have @types or @typeDependency entries
		if(stackResult == null || entryData.sigLoc == ESigLocation.TYPEPARAM) return;
		
		String path = elemPath;
		
		try
		{
			String scheme;
			
			if(entryData.sigLoc != ESigLocation.RETURN)
			{
				switch (entryData.sigLoc)
				{
					case PARAM:
						scheme = "java+parameter";
						path += "/param" + paramInd;
						params.add(stackResult);
						break;
					case SUPER:
						scheme = "java+field";
						break;
					default:
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
			
			for(Entry<String, String> entry : stackDependencies)
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
		if(entryData.isArray)
		{
			for(int dim = 1; dim <= entryData.arrayDim; dim++)
			{
				baseValue = jc.constructTypeSymbolNode("array", baseValue, JavaToRascalConverter.values.integer(dim));
			}
		}
		
		return baseValue;
	}
	
	private IConstructor instantiateBaseType(String type)
	{
		if(entryData.sigLoc == ignoreSigLoc) return null;
		
		return mapArray(jc.constructTypeSymbolNode(type));
	}
	
	private IConstructor instantiateClassType(String type, IConstructor typeParam, boolean isInterface)
	{
		if(entryData.sigLoc == ignoreSigLoc) return null;
		
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
		if(entryData.sigLoc == ignoreSigLoc) return null;
		
		return mapArray(typeParams.containsKey(type) ? typeParams.get(type) : globalTypeParams.get(type));
	}
	
	@Override
	public void visitBaseType(char descriptor) //NOTE: visitBaseType is a termination function
	{
		String type = Signature.toString(String.valueOf(descriptor));
		
		System.out.println(String.format("BASE TYPE: %s, %s", descriptor, type));
		
		entryData.type = EType.BASE;
		entryData.typeName = type;
		entryData.setDependsOn("java+primitiveType");
		
		itemStack.push(entryData);
		
		buildStackResult();
		reset();
		
		super.visitBaseType(descriptor);
	}
	
	@Override
	public void visitTypeVariable(String type) //NOTE: visitTypeVariable is a termination function
	{
		System.out.println("TYPE VAR: " + type);
		
		entryData.type = EType.TYPEVAR;
		entryData.typeName = type;
		entryData.setDependsOn("java+typeVariable");
		
		itemStack.push(entryData);
		
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
			if(entryData.sigLoc == ESigLocation.TYPEPARAM)
			{
				try
				{
					typeParams.put(entryData.typeParamName, jc.constructTypeSymbolNode("typeParameter",
						JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", classNamePath + "/" + entryData.typeParamName),
						jc.constructBoundNode("unbounded")));
				}
				catch (URISyntaxException e)
				{
					e.printStackTrace();
				}
			}
			
			entryData.type = EType.BASE;
			entryData.typeName = "object";
			
			//stackDep.put("object", "java+primitiveType");
			itemStack.push(entryData);
			
			buildStackResult(); //Since object is a baseType, we became a termination function
		}
		else
		{
			if(entryData.sigLoc == ESigLocation.TYPEPARAM)
			{
				try
				{
					typeParams.put(entryData.typeParamName, jc.constructTypeSymbolNode("typeParameter",
						JavaToRascalConverter.values.sourceLocation("java+typeVariable", "", classNamePath + "/" + entryData.typeParamName),
						jc.constructBoundNode("extends", instantiateClassType(type, null, entryData.isInterface))));
				}
				catch (URISyntaxException e)
				{
					e.printStackTrace();
				}
			}
			
			entryData.type = EType.CLASS;
			entryData.typeName = type;
			entryData.setDependsOn(entryData.isInterface ? "java+interface" : "java+class");
			
			itemStack.push(entryData);
		}

		reset();
		
		super.visitClassType(type);
	}
	
	@Override
	public void visitFormalTypeParameter(String name)
	{
		System.out.println(String.format("TYPE PARAM: %s", name));
		
		entryData.sigLoc = ESigLocation.TYPEPARAM;
		entryData.typeParamName = name;
		
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
		
		entryData.sigLoc = ESigLocation.TYPEARG;
		
		switch(arg)
		{
			case SUPER:
				entryData.boundType = EBoundType.LOWERBOUND;
				break;
			case EXTENDS:
				entryData.boundType = EBoundType.UPPERBOUND;
				break;
			case INSTANCEOF:
				entryData.boundType = EBoundType.INSTANCEOF;
				break;
		}
		
		return super.visitTypeArgument(arg);
	}
	
	@Override
	public void visitTypeArgument()
	{
		System.out.println("TYPE ARG (UNBOUNDED)");
		
		entryData.sigLoc = ESigLocation.TYPEARG;
		
		//Supposedly called for unbounded type args. Did not see it getting called at all.
		//Cause is most likely because of being a JAR; everything not bound becomes bound to java/lang/Object.
		
		super.visitTypeArgument();
	}
	
	@Override
	public SignatureVisitor visitArrayType()
	{
		System.out.println("IS ARRAY");
		
		entryData.isArray = true;
		entryData.arrayDim++;
		
		return super.visitArrayType();
	}
	
	@Override
	public SignatureVisitor visitParameterType()
	{
		System.out.println("IS PARAM");
		
		entryData.sigLoc = ESigLocation.PARAM;
		paramInd++;
		
		return super.visitParameterType();
	}
	
	@Override
	public SignatureVisitor visitReturnType()
	{
		System.out.println("IS RETURN");
		
		entryData.sigLoc = ESigLocation.RETURN;
		
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

		entryData.sigLoc = ESigLocation.SUPER;
		
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
		
		entryData.isInterface = false;
		
		return super.visitClassBound();
	}
	
	@Override
	public SignatureVisitor visitInterfaceBound()
	{
		System.out.println("INTERFACE BOUND");
		
		entryData.isInterface = true;
		
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
