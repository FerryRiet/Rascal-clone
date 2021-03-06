@license{
  Copyright (c) 2009-2013 CWI
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
@contributor{Paul Klint - Paul.Klint@cwi.nl - CWI}
//START
module demo::basic::Factorial

public int fac(int N) = N <= 0 ? 1 : N * fac(N - 1); /*1*/

public int fac2(0) = 1; /*2*/
public default int fac2(int N) = N * fac2(N - 1); /*3*/

public int fac3(int N)  { /*4*/
  if (N == 0) 
    return 1;
  return N * fac3(N - 1);
}
