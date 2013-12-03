@license{
  Copyright (c) 2009-2013 CWI
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
module util::FileSystem

import Exception;
import IO;

data FileSystem 
  = directory(loc l, set[FileSystem] children)
  | file(loc l)
  ;
  
FileSystem crawl(loc l) = isDirectory(l) ? directory(l, {crawl(e) | e <- l.ls}) : file(l);

set[loc] find(loc f, bool (loc) filt) 
  = isDirectory(f) 
      ? {*find(c, filt) | c <- f.ls} + (filt(f) ? {f} : { }) 
      : (filt(f) ? {f} : { })
      ;

set[loc] find(loc f, str ext) = find(f, bool (loc l) { return l.extension == ext; });

