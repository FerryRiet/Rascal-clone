package org.rascalmpl.library.lang.java.m3.internal;

public class Jar2M3SigEntryData
{
	public enum ESigLocation { PARAM, RETURN, TYPEPARAM, SUPER, TYPEARG }
	public enum EBoundType { LOWERBOUND, UPPERBOUND, INSTANCEOF }
	public enum EType { BASE, TYPEVAR, CLASS }
	
	public boolean isArray;
	public boolean isInterface; //If type == CLASS
	public ESigLocation sigLoc;
	public EBoundType boundType;
	public EType type;
	
	public String typeParamName; //If sigLoc == TYPEPARAM
	public int arrayDim; //if isArray
}
