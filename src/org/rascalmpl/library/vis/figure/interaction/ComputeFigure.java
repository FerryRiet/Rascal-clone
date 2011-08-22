/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.library.vis.figure.interaction;


import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IValue;
import org.rascalmpl.library.vis.figure.FigureFactory;
import org.rascalmpl.library.vis.figure.combine.LayoutProxy;
import org.rascalmpl.library.vis.properties.PropertyManager;
import org.rascalmpl.library.vis.swt.ICallbackEnv;
import org.rascalmpl.library.vis.swt.IFigureConstructionEnv;
import org.rascalmpl.library.vis.util.Mutable;

public class ComputeFigure extends LayoutProxy {
	
	final private IValue callback;
	private IConstructor prevValue; // TODO: remove this when nullary closures are memoed
	
	private IList childProps;

	public ComputeFigure(IFigureConstructionEnv env, PropertyManager properties,  IValue fun, IList childProps) {
		super(null,properties);
		this.childProps = childProps;
		env.getCallBackEnv().checkIfIsCallBack(fun);
		this.callback = fun;
	}

	public void init(IFigureConstructionEnv env, MouseOver mparent, Mutable<Boolean> swtSeen){
		IConstructor figureCons =
			(IConstructor) env.getCallBackEnv().executeRascalFigureCallBack(callback, noTypes, noArgs);
		if(prevValue == null || !figureCons.isEqual(prevValue)){
			if(innerFig != null){
				innerFig.destroy(env);
			}
			setInnerFig( FigureFactory.make(env, figureCons, prop, childProps));
			prevValue = figureCons;
		}
	}
}