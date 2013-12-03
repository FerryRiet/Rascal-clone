/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Tijs van der Storm - Tijs.van.der.Storm@cwi.nl
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Davy Landman - Davy.Landman@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.interpreter.result;

import static org.rascalmpl.interpreter.result.ResultFactory.bool;
import static org.rascalmpl.interpreter.result.ResultFactory.makeResult;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eclipse.imp.pdb.facts.IBool;
import org.eclipse.imp.pdb.facts.IInteger;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IListWriter;
import org.eclipse.imp.pdb.facts.IMap;
import org.eclipse.imp.pdb.facts.IMapWriter;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IString;
import org.eclipse.imp.pdb.facts.ITuple;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.IValueFactory;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.type.TypeFactory;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.asserts.ImplementationError;
import org.rascalmpl.interpreter.staticErrors.SyntaxError;
import org.rascalmpl.interpreter.staticErrors.UndeclaredField;
import org.rascalmpl.interpreter.staticErrors.UnexpectedType;
import org.rascalmpl.interpreter.staticErrors.UnsupportedOperation;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.uri.URIUtil;

public class SourceLocationResult extends ElementResult<ISourceLocation> {
	private final Type intTuple;

	public SourceLocationResult(Type type, ISourceLocation loc, IEvaluatorContext ctx) {
		super(type, loc, ctx);
		intTuple = getTypeFactory().tupleType(getTypeFactory().integerType(), "line", getTypeFactory().integerType(), "column");
	}

	
	@Override
	public Result<IValue> call(Type[] argTypes, IValue[] actuals, Map<String, IValue> keyArgValues) {
		if (actuals.length >= 2) {
			if (!argTypes[0].isSubtypeOf(getTypeFactory().integerType())) {
				throw new UnexpectedType(getTypeFactory().integerType(), argTypes[0], ctx.getCurrentAST());
			}
			if (!argTypes[1].isSubtypeOf(getTypeFactory().integerType())) {
				throw new UnexpectedType(getTypeFactory().integerType(), argTypes[1], ctx.getCurrentAST());
			}
			
			if (actuals.length == 4) {
				if (!argTypes[2].isSubtypeOf(intTuple)) {
					throw new UnexpectedType(intTuple, argTypes[2], ctx.getCurrentAST());
				}
				if (!argTypes[3].isSubtypeOf(intTuple)) {
					throw new UnexpectedType(intTuple, argTypes[3], ctx.getCurrentAST());
				}
			}
			else if (actuals.length != 2) {
				throw new SyntaxError("location constructor", ctx.getCurrentAST().getLocation());
			}
		}
		else {
			throw new SyntaxError("location constructor", ctx.getCurrentAST().getLocation());
		}
		

		int iLength = Integer.parseInt(actuals[1].toString());
		int iOffset = Integer.parseInt(actuals[0].toString());
		
		if (iLength < 0) {
			throw RuntimeExceptionFactory.illegalArgument(actuals[1], ctx.getCurrentAST(), ctx.getStackTrace());
		}
		
		if (iOffset < 0) {
			throw RuntimeExceptionFactory.illegalArgument(actuals[0], ctx.getCurrentAST(), ctx.getStackTrace());
		}
			
		if (actuals.length == 4) {
			int iBeginLine = Integer.parseInt(((ITuple) actuals[2]).get(0).toString());
			int iBeginColumn = Integer.parseInt(((ITuple) actuals[2]).get(1).toString());
			int iEndLine = Integer.parseInt(((ITuple) actuals[3]).get(0).toString());
			int iEndColumn = Integer.parseInt(((ITuple) actuals[3]).get(1).toString());
			
			if (iBeginLine < 0) {
				throw RuntimeExceptionFactory.illegalArgument(((ITuple) actuals[2]).get(0), ctx.getCurrentAST(), ctx.getStackTrace());
			}
			if (iBeginColumn < 0) {
				throw RuntimeExceptionFactory.illegalArgument(((ITuple) actuals[2]).get(1), ctx.getCurrentAST(), ctx.getStackTrace());
			}
			if (iEndLine < 0) {
				throw RuntimeExceptionFactory.illegalArgument(((ITuple) actuals[3]).get(0), ctx.getCurrentAST(), ctx.getStackTrace());
			}
			if (iEndColumn < 0) {
				throw RuntimeExceptionFactory.illegalArgument(((ITuple) actuals[3]).get(1), ctx.getCurrentAST(), ctx.getStackTrace());
			}

			return makeResult(getTypeFactory().sourceLocationType(), getValueFactory().sourceLocation(getValue(), iOffset, iLength, iBeginLine, iEndLine, iBeginColumn, iEndColumn), ctx);
		}
		else {
			return makeResult(getTypeFactory().sourceLocationType(), getValueFactory().sourceLocation(getValue(), iOffset, iLength), ctx);
		}
	}
	
	@Override
	public <U extends IValue> Result<U> fieldAccess(String name, TypeStore store) {
		IValueFactory vf = getValueFactory();
		TypeFactory tf = getTypeFactory();

		ISourceLocation value = getValue();
		String stringResult = null;
		Integer intResult = null;
		Integer tupleA = null;
		Integer tupleB = null;
		switch (name) {
		case "scheme": 
			stringResult = value.getScheme();
			break;

		case "authority": 
			stringResult = value.hasAuthority() ? value.getAuthority() : "";
			break;

		case "host": 
		case "user": 
		case "port": 
			URI uri = value.getURI();
			if (!ctx.getResolverRegistry().supportsHost(uri)) {
				throw new UndeclaredField(name, "The scheme " + uri.getScheme() + " does not support the " + name + " field, use authority instead.", tf.sourceLocationType(), ctx.getCurrentAST());
			}
			if (name.equals("host")) {
				stringResult = uri.getHost();
			}
			else if (name.equals("user")) {
				stringResult = uri.getUserInfo();
			}
			else {
				intResult = uri.getPort();
			}
			if (stringResult == null && intResult == null) {
				stringResult = "";
			}
			break;

		case "path":
			stringResult = value.hasPath() ? value.getPath() : "";
			break;

		case "query":
			stringResult = value.hasQuery() ? value.getQuery() : "";
			break;

		case "fragment":
			stringResult = value.hasFragment() ? value.getFragment() : "";
			break;

		case "length":
			if (value.hasOffsetLength()) {
				intResult = value.getLength();
				break;
			}
			throw RuntimeExceptionFactory.unavailableInformation(ctx.getCurrentAST(), ctx.getStackTrace());

		case "offset":
			if (value.hasOffsetLength()) {
				intResult = value.getOffset();
				break;
			}
			throw RuntimeExceptionFactory.unavailableInformation(ctx.getCurrentAST(), ctx.getStackTrace());

		case "begin":
			if (value.hasLineColumn()) {
				tupleA = value.getBeginLine();
				tupleB = value.getBeginColumn();
				break;
			}
			throw RuntimeExceptionFactory.unavailableInformation(ctx.getCurrentAST(), ctx.getStackTrace());

		case "end":
			if (value.hasLineColumn()) {
				tupleA = value.getEndLine();
				tupleB = value.getEndColumn();
				break;
			}
			throw RuntimeExceptionFactory.unavailableInformation(ctx.getCurrentAST(), ctx.getStackTrace());
		
		case "uri":
			stringResult = value.getURI().toString();
			break;

		case "top":
			return makeResult(tf.sourceLocationType(), vf.sourceLocation(value.getURI()), ctx);

		// now the calculated fields
		case "parent": {
			String path = value.hasPath() ? value.getPath() : "";
			if (path.equals("") || path.equals("/")) {
				throw RuntimeExceptionFactory.noParent(getValue(), ctx.getCurrentAST(), ctx.getStackTrace());
			}
			// remove one or more /'s at the end
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() -1);
			}
			int i = path.lastIndexOf((int)'/');
			if (i != -1) {
				path = path.substring(0, i);
				if (value.getScheme().equalsIgnoreCase("file")) {
					// there is a special case for file references to windows paths.
					// the root path should end with a / (c:/ not c:)
					if (path.lastIndexOf((int)'/') == 0 && path.endsWith(":")) {
						path += "/";
					}
				}
				return fieldUpdate("path", makeResult(tf.stringType(), vf.string(path), ctx), store);
			}
			throw RuntimeExceptionFactory.noParent(getValue(), ctx.getCurrentAST(), ctx.getStackTrace());
		}

		case "file": {
			String path = value.hasPath() ? value.getPath() : "";
			
			if (path.equals("")) {
				throw RuntimeExceptionFactory.noParent(getValue(), ctx.getCurrentAST(), ctx.getStackTrace());
			}
			int i = path.lastIndexOf((int)'/');
			
			if (i != -1) {
				stringResult = path.substring(i+1);
			}
			else {
				stringResult = path;
			}
			break;
		}

		case "ls": {
			try {
				ISourceLocation resolved = ctx.getHeap().resolveSourceLocation(value);
				if (!ctx.getResolverRegistry().isDirectory(resolved.getURI())) {
					throw RuntimeExceptionFactory.io(vf.string("You can only access ls on a directory, or a container."), ctx.getCurrentAST(), ctx.getStackTrace());
				}
				Result<IValue> resRes = makeResult(getType(), resolved, ctx);

				IListWriter w = ctx.getValueFactory().listWriter();
				Type stringType = tf.stringType();

				for (String elem : ctx.getResolverRegistry().listEntries(resolved.getURI())) {
					w.append(resRes.add(makeResult(stringType, vf.string(elem), ctx)).getValue());
				}

				IList result = w.done();
				// a list of loc's
				return makeResult(result.getType(), result, ctx);
				
			} catch (IOException e) {
				throw RuntimeExceptionFactory.io(vf.string(e.getMessage()), ctx.getCurrentAST(), ctx.getStackTrace());
			}
			
		}

		case "extension" : {
			String path = value.hasPath() ? value.getPath() : "";
			int i = path.lastIndexOf((int)'.');
			if (i != -1) {
				stringResult = path.substring(i + 1);
			}
			else {
				stringResult = "";
			}
			break;
		}
		
		case "params" : {
			String query = value.hasQuery() ? value.getQuery() : "";
			IMapWriter res = vf.mapWriter(tf.stringType(), tf.stringType());
			
			if (query != null && query.length() > 0) {
				String[] params = query.split("&");
				for (String param : params) {
					String[] keyValue = param.split("=");
					res.put(vf.string(keyValue[0]), vf.string(keyValue[1]));
				}
			}
			
			IMap map = res.done();
			return makeResult(map.getType(), map, ctx);
		}

		default: 
			throw new UndeclaredField(name, getTypeFactory().sourceLocationType(), ctx.getCurrentAST());
		}

		if (stringResult != null) {
			return makeResult(tf.stringType(), vf.string(stringResult), ctx);
		}
		if (intResult != null) {
			return makeResult(tf.integerType(), vf.integer(intResult), ctx);
		}
		if (tupleA != null && tupleB != null) {
			return makeResult(intTuple, vf.tuple(vf.integer(tupleA), vf.integer(tupleB)), ctx);
		}
		throw new RuntimeException("A case not handled? " + name);
	}

	@Override
	public <U extends IValue, V extends IValue> Result<U> fieldUpdate(String name, Result<V> repl, TypeStore store) {
		ISourceLocation loc = getValue();
		int iLength = loc.hasOffsetLength() ? loc.getLength() : -1;
		int iOffset = loc.hasOffsetLength() ? loc.getOffset() : -1;
		int iBeginLine = loc.hasLineColumn() ? loc.getBeginLine() : -1;
		int iBeginColumn = loc.hasLineColumn() ? loc.getBeginColumn() : -1;
		int iEndLine = loc.hasLineColumn() ? loc.getEndLine() : -1;
		int iEndColumn = loc.hasLineColumn() ? loc.getEndColumn() : -1;
		//URI uri = loc.getURI();
		boolean uriPartChanged = false;
		String scheme = loc.getScheme();
		String authority = loc.hasAuthority() ? loc.getAuthority() : null;
		String path = loc.hasPath() ? loc.getPath() : null;
		String query = loc.hasQuery() ? loc.getQuery() : null;
		String fragment = loc.hasFragment() ? loc.getFragment() : null;
		

		Type replType = repl.getType();
		IValue replValue = repl.getValue();

		try {
			String newStringValue = null;
			if (replType.isString()) {
				newStringValue = ((IString)replValue).getValue();
			}
			if (name.equals("uri")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				URI uri = URIUtil.createFromEncoded(newStringValue);
				// now destruct it again
				scheme = uri.getScheme();
				authority = uri.getAuthority();
				path = uri.getPath();
				query = uri.getQuery();
				fragment = uri.getFragment();
				uriPartChanged = true;
			} 
			else if (name.equals("scheme")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				scheme = newStringValue;
				uriPartChanged = true;
			}
			else if (name.equals("authority")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				authority = newStringValue;
				uriPartChanged = true;
			}
			else if (name.equals("host")) {
				URI uri = value.getURI();
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				if (!ctx.getResolverRegistry().supportsHost(uri)) {
					throw new UndeclaredField(name, "The scheme " + uri.getScheme() + " does not support the host field, use authority instead.", getTypeFactory().sourceLocationType(), ctx.getCurrentAST());
				}
				uri = URIUtil.changeHost(uri, newStringValue);
				authority = uri.getAuthority();
				uriPartChanged = true;
			}
			else if (name.equals("path")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				path = newStringValue;
				uriPartChanged = true;
			}
			else if (name.equals("file")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				int i = path.lastIndexOf("/");
				
				if (i != -1) {
					path = path.substring(0, i) + "/" + newStringValue;
				}
				else {
					path = path + "/" + newStringValue;	
				}
				uriPartChanged = true;
			}
			else if (name.equals("parent")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				
				int i = path.lastIndexOf("/");
				String parent = newStringValue;
				
				if (!parent.startsWith("/")) {
					parent = "/" + parent;
				}
				if (i != -1) {
					path =parent + path.substring(i);
				}
				else {
					path = parent;
				}
				uriPartChanged = true;
			}
			else if (name.equals("ls")) {
				throw new UnsupportedOperation("can not update the children of a location", ctx.getCurrentAST());
			}
			else if (name.equals("extension")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				String ext = newStringValue;
				
				if (path.length() > 1) {
					int index = path.lastIndexOf('.');

					if (index == -1 && !ext.isEmpty()) {
						path = path + (!ext.startsWith(".") ? "." : "") + ext;
					}
					else if (!ext.isEmpty()) {
						path = path.substring(0, index) + (!ext.startsWith(".") ? "." : "") + ext;
					}
					else {
						path = path.substring(0, index);
					}
				}
				uriPartChanged = true;
			}
			else if (name.equals("top")) {
				if (replType.isString()) {
					URI uri = URIUtil.assumeCorrect(newStringValue);
					scheme = uri.getScheme();
					authority = uri.getAuthority();
					path = uri.getPath();
					query = uri.getQuery();
					fragment = uri.getFragment();
				}
				else if (replType.isSourceLocation()) {
					ISourceLocation rep = ((ISourceLocation) repl.getValue());
					scheme = rep.getScheme();
					authority = rep.hasAuthority() ? rep.getAuthority() : null;
					path = rep.hasPath() ? rep.getPath() : null;
					query = rep.hasQuery() ? rep.getQuery() : null;
					fragment = rep.hasFragment() ? rep.getFragment() : null;
				}
				else {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				uriPartChanged = true;
			}
			else if (name.equals("fragment")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				fragment = newStringValue;
				uriPartChanged = true;
			}
			else if (name.equals("query")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				query= newStringValue;
				uriPartChanged = true;
			}
			else if (name.equals("user")) {
				if (!replType.isString()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				URI uri = loc.getURI();
				if (!ctx.getResolverRegistry().supportsHost(uri)) {
					throw new UndeclaredField(name, "The scheme " + uri.getScheme() + " does not support the user field, use authority instead.", getTypeFactory().sourceLocationType(), ctx.getCurrentAST());
				}
				if (uri.getHost() != null) {
					uri = URIUtil.changeUserInformation(uri, newStringValue);
				}
				authority = uri.getAuthority();
				uriPartChanged = true;
			}
			else if (name.equals("port")) {
				if (!replType.isInteger()) {
					throw new UnexpectedType(getTypeFactory().stringType(), replType, ctx.getCurrentAST());
				}
				
				URI uri = loc.getURI();
				if (!ctx.getResolverRegistry().supportsHost(uri)) {
					throw new UndeclaredField(name, "The scheme " + uri.getScheme() + " does not support the port field, use authority instead.", getTypeFactory().sourceLocationType(), ctx.getCurrentAST());
				}
				if (uri.getHost() != null) {
					int port = Integer.parseInt(((IInteger) repl.getValue()).getStringRepresentation());
					uri = URIUtil.changePort(uri, port);
				}
				authority = uri.getAuthority();
				uriPartChanged = true;
			}
			else if (name.equals("length")){
				if (!replType.isInteger()) {
					throw new UnexpectedType(getTypeFactory().integerType(), replType, ctx.getCurrentAST());
				}
				iLength = ((IInteger) replValue).intValue();
				
				if (iLength < 0) {
					throw RuntimeExceptionFactory.illegalArgument(replValue, ctx.getCurrentAST(), ctx.getStackTrace());
				}
			} 
			else if (name.equals("offset")){
				if (!replType.isInteger()) {
					throw new UnexpectedType(getTypeFactory().integerType(), replType, ctx.getCurrentAST());
				}
				iOffset = ((IInteger) replValue).intValue();
				
				if (iOffset < 0) {
					RuntimeExceptionFactory.illegalArgument(replValue, ctx.getCurrentAST(), ctx.getStackTrace());
				}
			} 
			else if (name.equals("begin")) {
				if (!replType.isSubtypeOf(intTuple)) {
					throw new UnexpectedType(intTuple, replType, ctx.getCurrentAST());
				}
				iBeginLine = ((IInteger) ((ITuple) replValue).get(0)).intValue();
				iBeginColumn = ((IInteger) ((ITuple) replValue).get(1)).intValue();
				
				if (iBeginColumn < 0 || iBeginLine < 0) {
					throw RuntimeExceptionFactory.illegalArgument(replValue, ctx.getCurrentAST(), ctx.getStackTrace());
				}
			}
			else if (name.equals("end")) {
				if (!replType.isSubtypeOf(intTuple)) {
					throw new UnexpectedType(intTuple, replType, ctx.getCurrentAST());
				}
				iEndLine = ((IInteger) ((ITuple) replValue).get(0)).intValue();
				iEndColumn = ((IInteger) ((ITuple) replValue).get(1)).intValue();
				
				if (iEndLine < 0 || iEndColumn < 0) {
					throw RuntimeExceptionFactory.illegalArgument(replValue, ctx.getCurrentAST(), ctx.getStackTrace());
				}
			}
			else {
				// TODO: is this the right exception? How so "undeclared"?
				throw new UndeclaredField(name, getTypeFactory().sourceLocationType(), ctx.getCurrentAST());
			}
			
			ISourceLocation newLoc = loc;
			if (uriPartChanged) {
				newLoc = getValueFactory().sourceLocation(scheme, authority, path, query, fragment);
			}
			if (loc.hasLineColumn()) {
				// was a complete loc, and thus will be now
				return makeResult(getType(), getValueFactory().sourceLocation(newLoc, iOffset, iLength, iBeginLine, iEndLine, iBeginColumn, iEndColumn), ctx);
			}
			
			if (loc.hasOffsetLength()) {
				// was a partial loc
				
				if (iBeginLine != -1 || iBeginColumn != -1) {
					//will be complete now.
					iEndLine = iBeginLine;
					iEndColumn = iBeginColumn;
					return makeResult(getType(), getValueFactory().sourceLocation(newLoc, iOffset, iLength, iBeginLine, iEndLine, iBeginColumn, iEndColumn), ctx);
				}
				else if (iEndLine != -1 || iEndColumn != -1) {
					// will be complete now.
					iBeginLine = iEndLine;
					iBeginColumn = iEndColumn;
					return makeResult(getType(), getValueFactory().sourceLocation(newLoc, iOffset, iLength, iBeginLine, iEndLine, iBeginColumn, iEndColumn), ctx);
				}
				else {
					// remains a partial loc
					return makeResult(getType(), getValueFactory().sourceLocation(newLoc, iOffset, iLength), ctx);
				}
			}

			// used to have no offset/length or line/column info, if we are here
			
			if (iBeginColumn != -1 || iEndColumn != -1 || iBeginLine != -1 || iBeginColumn != -1) {
				// trying to add line/column info to a uri that has no offset length
				throw RuntimeExceptionFactory.invalidUseOfLocation("Can not add line/column information without offset/length", ctx.getCurrentAST(), ctx.getStackTrace());
			}
			
			// trying to set offset that was not there before, adding length automatically
			if (iOffset != -1 ) {
				if (iLength == -1) {
					iLength = 0;
				}
			}
			
			// trying to set length that was not there before, adding offset automatically
			if (iLength != -1) {
				if (iOffset == -1) {
					iOffset = 0;
				}
			}
			
			if (iOffset != -1 || iLength != -1) {
				// used not to no offset/length, but do now
				return makeResult(getType(), getValueFactory().sourceLocation(newLoc, iOffset, iLength), ctx);
			}
			
			// no updates to offset/length or line/column, and did not used to have any either:
			return makeResult(getType(), newLoc, ctx);
		} 
		catch (IllegalArgumentException e) {
			throw RuntimeExceptionFactory.illegalArgument(ctx.getCurrentAST(), null);
		} catch (URISyntaxException e) {
			throw RuntimeExceptionFactory.parseError(ctx.getCurrentAST().getLocation(), ctx.getCurrentAST(), ctx.getStackTrace());
		}
	}
	
	@Override
	public <V extends IValue> Result<IBool> equals(Result<V> that) {
		return that.equalToSourceLocation(this);
	}

	@Override
	public <V extends IValue> Result<IBool> nonEquals(Result<V> that) {
		return that.nonEqualToSourceLocation(this);
	}
	
	@Override
	public <V extends IValue> Result<IBool> lessThan(Result<V> that) {
		return that.lessThanSourceLocation(this);
	}
	
	@Override
	public <V extends IValue> LessThanOrEqualResult lessThanOrEqual(Result<V> that) {
		return that.lessThanOrEqualSourceLocation(this);
	}
	
	@Override
	protected Result<IBool> lessThanSourceLocation(SourceLocationResult that) {
	  LessThanOrEqualResult loe = lessThanOrEqualSourceLocation(that);
	  return bool(loe.isLess().getValue().getValue() && !loe.isEqual().isTrue(), ctx);
	}
	
	@Override
	protected Result<IBool> greaterThanSourceLocation(SourceLocationResult that) {
	  return that.lessThanSourceLocation(this);
	}

	@Override
	protected Result<IBool> greaterThanOrEqualSourceLocation(SourceLocationResult that) {
	  return that.lessThanSourceLocation(this);
	}
	
	@Override
	protected LessThanOrEqualResult lessThanOrEqualSourceLocation(SourceLocationResult that) {
    ISourceLocation left = that.getValue();
    ISourceLocation right = this.getValue();
    
    int compare = left.getURI().toString().compareTo(right.getURI().toString());
    if (compare < 0) {
      return new LessThanOrEqualResult(true, false, ctx);
    }
    else if (compare > 0) {
      return new LessThanOrEqualResult(false, false, ctx);
    }

    // but the uri's are the same
    // note that line/column information is superfluous and does not matter for ordering
    
    if (left.hasOffsetLength()) {
      if (!right.hasOffsetLength()) {
        return new LessThanOrEqualResult(false, false, ctx);
      }

      int roffset = right.getOffset();
      int rlen = right.getLength();
      int loffset = left.getOffset();
      int llen = left.getLength();

      if (loffset == roffset) {
        return new LessThanOrEqualResult(llen < rlen, llen == rlen, ctx);
      }

      return new LessThanOrEqualResult(roffset < loffset && roffset + rlen >= loffset + llen, false, ctx);
    }
    else if (compare == 0) {
      return new LessThanOrEqualResult(false, true, ctx);
    }

    if (!right.hasOffsetLength()) {
      throw new ImplementationError("assertion failed");
    }

    return new LessThanOrEqualResult(false, false, ctx);
	}
	
	/////
	
	@Override
	protected Result<IBool> equalToSourceLocation(SourceLocationResult that) {
		return that.equalityBoolean(this);
	}

	@Override
	protected Result<IBool> nonEqualToSourceLocation(SourceLocationResult that) {
		return that.nonEqualityBoolean(this);
	}
	
	protected int compareSourceLocationInt(SourceLocationResult that) {
		// Note args have already been reversed.
		
		ISourceLocation left = this.getValue();
		ISourceLocation right = that.getValue();
		if (left.isEqual(right)) {
			return 0;
		}
		
		// they are not the same
		int compare = left.getURI().toString().compareTo(right.getURI().toString());
		if (compare != 0) {
			return compare;
		}
		
		// but the uri's are the same
		// note that line/column information is superfluous and does not matter for ordering
		
		if (left.hasOffsetLength()) {
			if (!right.hasOffsetLength()) {
				return 1;
			}
			int roffset = right.getOffset();
			int rlen = right.getLength();
			int loffset = left.getOffset();
			int llen = left.getLength();
			if(loffset == roffset){
				return (llen < rlen) ? -1 : ((llen == rlen) ? 0 : 1);
			}
			if(roffset < loffset && roffset + rlen >= loffset + llen)
				return -1;
			else
				return 1;
		}
		
		if (!right.hasOffsetLength()) {
			throw new ImplementationError("assertion failed");
		}
		
		return -1;
	}
	
	@Override
	public <U extends IValue, V extends IValue> Result<U> add(Result<V> that) {
		return that.addSourceLocation(this);
	}
	
	@Override
	protected <U extends IValue> Result<U> addListRelation(ListRelationResult that) {
		return that.addSourceLocation(this);
	}
	
	@Override
	protected <U extends IValue> Result<U> addRelation(RelationResult that) {
		return that.addSourceLocation(this);
	}
}
