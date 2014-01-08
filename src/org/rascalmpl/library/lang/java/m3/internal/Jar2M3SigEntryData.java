package org.rascalmpl.library.lang.java.m3.internal;

import java.util.AbstractMap.SimpleEntry;

public class Jar2M3SigEntryData
{
	public enum ESigLocation { UNKNOWN, PARAM, RETURN, TYPEPARAM, SUPER, TYPEARG }
	public enum EBoundType { UNKNOWN, LOWERBOUND, UPPERBOUND, INSTANCEOF }
	public enum EType { UNKNOWN, BASE, TYPEVAR, CLASS }
	
	private SimpleEntry<String, String> dependsOn;
	
	public boolean isArray = false;
	public boolean isInterface = false; //Currently only set true/false if sigLoc == TYPEPARAM
	public ESigLocation sigLoc = ESigLocation.UNKNOWN;
	public EBoundType boundType = EBoundType.UNKNOWN;
	public EType type = EType.UNKNOWN;
	
	public String typeParamName = null; //If sigLoc == TYPEPARAM
	public int arrayDim = 0; //if isArray
	public String typeName; //The name of the java type (ie. BASE = void, CLASS = java/lang/List, TYPEVAR = T). Used by the stack.

	public SimpleEntry<String, String> getDependsOn()
	{
		return dependsOn;
	}
	
	public void setDependsOn(String typeScheme)
	{
		dependsOn = new SimpleEntry<>(typeName, typeScheme);
	}
}
