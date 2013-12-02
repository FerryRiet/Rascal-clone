@bootstrapParser
module experiments::Compiler::Rascal2muRascal::RascalStatement

import Prelude;
import lang::rascal::\syntax::Rascal;

import experiments::Compiler::Rascal2muRascal::TmpAndLabel;
import experiments::Compiler::Rascal2muRascal::RascalModule;
import experiments::Compiler::Rascal2muRascal::RascalExpression;
import experiments::Compiler::Rascal2muRascal::RascalPattern;
import experiments::Compiler::Rascal2muRascal::RascalType;

import experiments::Compiler::muRascal::AST;
import experiments::Compiler::Rascal2muRascal::TypeUtils;

MuExp translateStats(Statement* statements) = muBlock([ translate(stat) | stat <- statements ]);

/********************************************************************/
/*                  Statement                                       */
/********************************************************************/
	
MuExp translate(s: (Statement) `assert <Expression expression> ;`) = muCallPrim("assertreport", [translate(expression), muCon(""), muCon(s@\loc)]);

MuExp translate(s: (Statement) `assert <Expression expression> : <Expression message>;`) = muCallPrim("assertreport", [translate(expression), translate(message), muCon(s@\loc)]);

MuExp translate(s: (Statement) `<Expression expression> ;`) = translate(expression);

MuExp translate(s: (Statement) `<Label label> <Visit visitItself>`) = translateVisit(label, visitItself);

MuExp translate(s: (Statement) `<Label label> while ( <{Expression ","}+ conditions> ) <Statement body>`) {
    whilename = getLabel(label);
    tmp = asTmp(whilename);
    enterLoop(whilename);
    enterBacktrackingScope(whilename);
    code = [ muAssignTmp(tmp, muCallPrim("listwriter_open", [])), 
             muWhile(whilename, muOne([translate(c) | c <-conditions]), [translate(body)]),
             muCallPrim("listwriter_close", [muTmp(tmp)])
           ];
    leaveBacktrackingScope();
    leaveLoop();
    return muBlock(code);
}

MuExp translateTemplate((StringTemplate) `while ( <Expression condition> ) { <Statement* preStats> <StringMiddle body> <Statement* postStats> }`, str pre){
    whilename = nextLabel();
    result = asTmp(whilename);
    enterLoop(whilename);
    enterBacktrackingScope(whilename);
    code = [ muAssignTmp(result, muCallPrim("template_open", [muTmp(pre)])), 
             muWhile(whilename, muOne([translate(condition)]), 
                     [ translateStats(preStats),  
                        muAssignTmp(result, muCallPrim("template_add", [muTmp(result), translateMiddle(body)])), 
                       translateStats(postStats)
                     ]),
             muCallPrim("template_close", [muTmp(result)])
           ];
    leaveBacktrackingScope();
    leaveLoop();
    return muBlock(code);
}

MuExp translate(s: (Statement) `<Label label> do <Statement body> while ( <Expression condition> ) ;`) {  
    doname = getLabel(label);
    tmp = asTmp(doname);
    enterLoop(doname);
    enterBacktrackingScope(doname);
    code = [ muAssignTmp(tmp, muCallPrim("listwriter_open", [])), 
             muDo(doname,  [translate(body)], muOne([translate(condition)])),
             muCallPrim("listwriter_close", [muTmp(tmp)])
           ];
    leaveBacktrackingScope();
    leaveLoop();
    return muBlock(code);
}

MuExp translateTemplate(s: (StringTemplate) `do { < Statement* preStats> <StringMiddle body> <Statement* postStats> } while ( <Expression condition> )`, str pre) {  
    doname = nextLabel();
    result = asTmp(doname);
    enterLoop(doname);
    enterBacktrackingScope(doname);
    code = [ muAssignTmp(result, muCallPrim("template_open", [muTmp(pre)])),
             muDo(doname,  [ translateStats(preStats),
                             muAssignTmp(result, muCallPrim("template_add", [muTmp(result), translateMiddle(body)])),
                             translateStats(postStats)], 
                  muOne([translate(condition)])
                 ),
             muCallPrim("template_close", [muTmp(result)])
           ];
    leaveBacktrackingScope();
    leaveLoop();
    return muBlock(code);
}

MuExp translate(s: (Statement) `<Label label> for ( <{Expression ","}+ generators> ) <Statement body>`) {
    forname = getLabel(label);
    tmp = asTmp(forname);
    enterLoop(forname);
    enterBacktrackingScope(forname);
    code = [ muAssignTmp(tmp, muCallPrim("listwriter_open", [])), 
             muWhile(forname, makeMuAll([translate(c) | c <-generators]), [ translate(body) ]),
             muCallPrim("listwriter_close", [muTmp(tmp)])
           ];
    leaveBacktrackingScope();
    leaveLoop();
    return muBlock(code);
}

MuExp translateTemplate((StringTemplate) `for ( <{Expression ","}+ generators> ) { <Statement* preStats> <StringMiddle body> <Statement* postStats> }`, str pre){
    forname = nextLabel();
    result = asTmp(forname);
    enterLoop(forname);
    enterBacktrackingScope(forname);
    code = [ muAssignTmp(result, muCallPrim("template_open", [muTmp(pre)])),
             muWhile(forname, makeMuAll([translate(c) | c <-generators]), 
                     [ translateStats(preStats),  
                       muAssignTmp(result, muCallPrim("template_add", [muTmp(result), translateMiddle(body)])),
                       translateStats(postStats)
                     ]),
             muCallPrim("template_close", [muTmp(result)])
           ];
    leaveBacktrackingScope();
    leaveLoop();
    return muBlock(code);
} 

MuExp translate(s: (Statement) `<Label label> if ( <{Expression ","}+ conditions> ) <Statement thenStatement>`) {
	ifname = getLabel(label);
	enterBacktrackingScope(ifname);
	code = muIfelse(ifname, muAll([translate(c) | c <-conditions]), [translate(thenStatement)], []);
    leaveBacktrackingScope();
    return code;
}
    
MuExp translateTemplate((StringTemplate) `if (<{Expression ","}+ conditions> ) { <Statement* preStats> <StringMiddle body> <Statement* postStats> }`, str pre){
    ifname = nextLabel();
    result = asTmp(ifname);
    enterBacktrackingScope(ifname);
    code = [ muAssignTmp(result, muCallPrim("template_open", [muTmp(pre)])),
             muIfelse(ifname, muAll([translate(c) | c <-conditions]), 
                      [ translateStats(preStats),
                        muAssignTmp(result, muCallPrim("template_add", [muTmp(result), translateMiddle(body)])),
                        translateStats(postStats)],
                      []),
               muCallPrim("template_close", [muTmp(result)])
           ];
    leaveBacktrackingScope();
    return muBlock(code);
}    

MuExp translate(s: (Statement) `<Label label> if ( <{Expression ","}+ conditions> ) <Statement thenStatement> else <Statement elseStatement>`) {
	ifname = getLabel(label);
	enterBacktrackingScope(ifname);
    code = muIfelse(ifname, muAll([translate(c) | c <-conditions]), [translate(thenStatement)], [translate(elseStatement)]);
    leaveBacktrackingScope();
    return code;
}
    
MuExp translateTemplate((StringTemplate) `if ( <{Expression ","}+ conditions> ) { <Statement* preStatsThen> <StringMiddle thenString> <Statement* postStatsThen> }  else { <Statement* preStatsElse> <StringMiddle elseString> <Statement* postStatsElse> }`, str pre){                    
    ifname = nextLabel();
    result = asTmp(ifname);
    enterBacktrackingScope(ifname);
    code = [ muAssignTmp(result, muCallPrim("template_open", [muTmp(pre)])),
             muIfelse(ifname, muAll([translate(c) | c <-conditions]), 
                      [ translateStats(preStatsThen), 
                        muAssignTmp(result, muCallPrim("template_add", [muTmp(result), translateMiddle(thenString)])),
                        translateStats(postStatsThen)
                      ],
                      [ translateStats(preStatsElse), 
                        muAssignTmp(result, muCallPrim("template_add", [muTmp(result), translateMiddle(elseString)])),
                        translateStats(postStatsElse)
                      ]),
              muCallPrim("template_close", [muTmp(result)])
           ];
    leaveBacktrackingScope();
    return muBlock(code);                                             
} 

MuExp translate(s: (Statement) `<Label label> switch ( <Expression expression> ) { <Case+ cases> }`) = translateSwitch(s);

MuExp translate(s: (Statement) `fail <Target target> ;`) = 
     inBacktrackingScope() ? muFail(target is empty ? currentBacktrackingScope() : "<target.name>")
                           : muFailReturn();

MuExp translate(s: (Statement) `break <Target target> ;`) = muBreak(target is empty ? currentLoop() : "<target.name>");

MuExp translate(s: (Statement) `continue <Target target> ;`) = muContinue(target is empty ? currentLoop() : "<target.name>");

MuExp translate(s: (Statement) `filter ;`) = muFilterReturn();

MuExp translate(s: (Statement) `solve ( <{QualifiedName ","}+ variables> <Bound bound> ) <Statement body>`) = translateSolve(s);

MuExp translate(s: (Statement) `try <Statement body> <Catch+ handlers>`) {
    list[Catch] defaultCases = [ handler | Catch handler <- handlers, handler is \default ];
    list[Catch] otherCases   = [ handler | Catch handler <- handlers, !(handler is \default) ];
    patterns = [ handler.pattern | Catch handler <- otherCases ];
    
    // If there is no default catch, compute lub of pattern types,
    // this gives optimization of the handler search based on types
    lubOfPatterns = !isEmpty(defaultCases) ? Symbol::\value() : Symbol::\void();
    if(isEmpty(defaultCases)) {
    	lubOfPatterns = ( lubOfPatterns | lub(it, getType(p@\loc)) | Pattern p <- patterns );
    }
    
    // Introduce a temporary variable that is bound within a catch block to a thrown value
    varname = asTmp(nextLabel());
    bigCatch = muCatch(varname, lubOfPatterns, translateCatches(varname, [ handler | handler <- handlers ], !isEmpty(defaultCases)));
    exp = muTry(translate(body), bigCatch, muBlock([]));
    
	return exp;
}

MuExp translate(s: (Statement) `try <Statement body> <Catch+ handlers> finally <Statement finallyBody>`) {
	// The stack of try-catch-finally block is managed to check whether there is a finally block 
	// that must be executed before 'return', if any; 
	// in this case, the return expression has to be first evaluated, stored in a temporary variable 
	// and returned after the 'finally' block has been executed
	enterTryCatchFinally();
	MuExp tryCatch = translate((Statement) `try <Statement body> <Catch+ handlers>`);
	leaveTryCatchFinally();
	MuExp finallyExp = translate(finallyBody);
	// Introduce a temporary variable that is bound within a catch block to a thrown value
	str varname = asTmp(nextLabel());
	return muTry(muTry(tryCatch.exp, tryCatch.\catch, muBlock([])), 
				 muCatch(varname, Symbol::\value(), muBlock([finallyExp, muThrow(muTmp(varname))])), 
				 finallyExp); 
}

MuExp translateCatches(str varname, list[Catch] catches, bool hasDefault) {
  // Translate a list of catch blocks into one catch block
  if(size(catches) == 0) {
  	  // In case there is no default catch provided, re-throw the value from the catch block
      return muThrow(muTmp(varname));
  }
  
  c = head(catches);
  
  if(c is binding) {
      ifname = nextLabel();
      enterBacktrackingScope(ifname);
      list[MuExp] conds = [];
      list[MuExp] then = [];
      if(c.pattern is literal) {
          conds = [ muCallMuPrim("equal", [ muTmp(asUnwrapedThrown(varname)), translate(c.pattern.literal) ]) ];
          then = [ translate(c.body) ];
      } else if(c.pattern is typedVariable) {
          conds = [ muCallMuPrim("check_arg_type", [ muTmp(asUnwrapedThrown(varname)), muTypeCon(translateType(c.pattern.\type)) ]) ];
          <fuid,pos> = getVariableScope("<c.pattern.name>", c.pattern.name@\loc);
          then = [ muAssign("<c.pattern.name>", fuid, pos, muTmp(asUnwrapedThrown(varname))), translate(c.body) ];
      } else {
          conds = [ muMulti(muCreate(mkCallToLibFun("Library","MATCH",2), [translatePat(c.pattern), muTmp(asUnwrapedThrown(varname))])) ];
          then = [ translate(c.body) ];
      }
      exp = muIfelse(ifname, muAll(conds), then, [translateCatches(varname, tail(catches), hasDefault)]);
      leaveBacktrackingScope();
      return exp;
  }
  
  // The default case will handle any thrown value
  exp = translate(c.body);
  
  return exp;
}

MuExp translate(s: (Statement) `<Label label> { <Statement+ statements> }`) =
    muBlock([translate(stat) | stat <- statements]);

MuExp translate(s: (Statement) `<Assignable assignable> <Assignment operator> <Statement statement>`) = translateAssignment(s); 

MuExp translate(s: (Statement) `;`) = muBlock([]);

MuExp translate(s: (Statement) `global <Type \type> <{QualifiedName ","}+ names> ;`) { throw("globalDirective"); }

MuExp translate(s: (Statement) `return <Statement statement>`) {
	// If the 'return' is used in the scope of a try-catch-finally block,
	// the respective 'finally' block must be executed before the function returns
	if(hasFinally()) {
		str varname = asTmp(nextLabel());
		return muBlock([ muAssignTmp(varname, translate(statement)), muReturn(muTmp(varname)) ]);
	} 
	return muReturn(translate(statement));
}

MuExp translate(s: (Statement) `throw <Statement statement>`) = muThrow(translate(statement));

MuExp translate(s: (Statement) `insert <DataTarget dataTarget> <Statement statement>`) // TODO: handle dataTarget
	= { fillCaseType(getType(statement@\loc)); 
		muBlock([ muAssignLocDeref("hasInsert",2,muBool(true)), 
				  muReturn(translate(statement)) ]); };

MuExp translate(s: (Statement) `append <DataTarget dataTarget> <Statement statement>`) =
   muCallPrim("listwriter_add", [muTmp(asTmp(currentLoop(dataTarget))), translate(statement)]);

MuExp translate(s: (Statement) `<FunctionDeclaration functionDeclaration>`) { translate(functionDeclaration); return muBlock([]); }

MuExp translate(s: (Statement) `<LocalVariableDeclaration declaration> ;`) { 
    tp = declaration.declarator.\type;
    {Variable ","}+ variables = declaration.declarator.variables;
    code = for(var <- variables){
    			if(var is initialized)
    				append mkAssign("<var.name>", var.name@\loc, translate(var.initial));
             }
    return muBlock(code);
}

default MuExp translate(Statement s){
   throw "MISSING CASE FOR STATEMENT: <s>";
}

/*********************************************************************/
/*                  End of Statements                                */
/*********************************************************************/

// Switch statement

MuExp translateSwitch(s: (Statement) `<Label label> switch ( <Expression expression> ) { <Case+ cases> }`) {
    switchname = getLabel(label);
    switchval = asTmp(switchname);
    return muBlock([ muAssignTmp(switchval, translate(expression)), translateSwitchCases(switchval, [c | c <- cases]) ]);
}

MuExp translateSwitchCases(str switchval, list[Case] cases) {
  if(size(cases) == 0)
      return muBlock([]);
  c = head(cases);
  
  if(c is patternWithAction){
     pwa = c.patternWithAction;
     if(pwa is arbitrary){
     	ifname = nextLabel();
     	enterBacktrackingScope(ifname);
        cond = muMulti(muCreate(mkCallToLibFun("Library","MATCH",2), [translatePat(pwa.pattern), muTmp(switchval)]));
        exp = muIfelse(ifname, muAll([cond]), [translate(pwa.statement)], [translateSwitchCases(switchval, tail(cases))]);
        leaveBacktrackingScope();
        return exp; 
     } else {
        throw "Replacement not allowed in switch statement";
     }
  } else {
        return translate(c.statement);
  }
}

// Solve statement

MuExp translateSolve(s: (Statement) `solve ( <{QualifiedName ","}+ variables> <Bound bound> ) <Statement body>`) {
   iterations = nextTmp();  // count number of iterations
   change = nextTmp();		// keep track of any changed value
   result = nextTmp();		// result of body computation
 
   varCode = [ translate(var) | var <- variables ];
   tmps = [ nextTmp() | var <- variables ];
// TODO: check that given bound is positive
   return muBlock([ muAssignTmp(iterations,  (bound is empty) ? muCon(1000000) : translate(bound.expression)),
                    muAssignTmp(change, muCon(true)),
                    *[ muAssignTmp(tmps[i], varCode[i]) | i <- index(varCode) ],
                    muWhile(nextLabel(),
                            muCallMuPrim("and_mbool_mbool", [muTmp(change), muCallPrim("int_greater_int", [muTmp(iterations), muCon(0)]) ]), 
                            [ muAssignTmp(change, muCon(false)),
                              muAssignTmp(result, translate(body)),
                              *[ muIfelse(nextLabel(), muCallPrim("notequal", [muTmp(tmps[i]), varCode[i]]), [muAssignTmp(change, muCon(true))], []) 
                 			   | i <- index(varCode)
                 			   ],
                              muAssignTmp(iterations, muCallPrim("int_subtract_int", [muTmp(iterations), muCon(1)])) 
                            ]),
                    muTmp(result)
           ]);
}


  
// Assignment statement

MuExp translateAssignment(s: (Statement) `<Assignable assignable> <Assignment operator> <Statement statement>`) =
    assignTo(assignable, applyAssignmentOperator("<operator>", assignable, statement));

// apply assignment operator 
    
MuExp applyAssignmentOperator(str operator, assignable, statement) {
    if(operator == "=")
    	return translate(statement);
    if(operator == "?="){
        oldval = getValues(assignable);
        assert size(oldval) == 1;	
        return generateIfDefinedOtherwise(oldval[0], translate(statement));
    }
    op1 = ("+=" : "add", "-=" : "subtract", "*=" : "product", "/=" : "divide", "&=" : "intersect")[operator]; 
    //op2 = "<getOuterType(assignable)>_<op1>_<getOuterType(statement)>";
    op2 = typedBinaryOp(getOuterType(assignable), op1, getOuterType(statement));;
    
    oldval = getValues(assignable);
    assert size(oldval) == 1;
    return muCallPrim("<op2>", [*oldval, translate(statement)]); 	
}
    
// assignTo: assign the rhs of the assignment (possibly modified by an assign operator) to the assignable
    
MuExp assignTo(a: (Assignable) `<QualifiedName qualifiedName>`, MuExp rhs) {
    return mkAssign("<qualifiedName>", qualifiedName@\loc, rhs);
}

MuExp assignTo(a: (Assignable) `<Assignable receiver> [ <Expression subscript> ]`, MuExp rhs) =
     assignTo(receiver, muCallPrim("<getOuterType(receiver)>_update", [*getValues(receiver), translate(subscript), rhs]));
    
MuExp assignTo(a: (Assignable) `<Assignable receiver> [ <OptionalExpression optFirst> .. <OptionalExpression optLast> ]`, MuExp rhs) =
    assignTo(receiver, muCallPrim("<getOuterType(receiver)>_replace", [*getValues(receiver), translateOpt(optFirst), muCon(false), translateOpt(optLast), rhs]) );

MuExp assignTo(a: (Assignable) `<Assignable receiver> [ <OptionalExpression optFirst> , <Expression second> .. <OptionalExpression optLast> ]`, MuExp rhs) =
     assignTo(receiver, muCallPrim("<getOuterType(receiver)>_replace", [*getValues(receiver), translateOpt(optFirst), translate(second), translateOpt(optLast), rhs]));

MuExp assignTo(a: (Assignable) `<Assignable receiver> . <Name field>`, MuExp rhs) =
     getOuterType(receiver) == "tuple" 
     ? assignTo(receiver, muCallPrim("<getOuterType(receiver)>_update", [*getValues(receiver), muCon(getTupleFieldIndex(getType(receiver@\loc), "<field>")), rhs]) )
     : assignTo(receiver, muCallPrim("<getOuterType(receiver)>_field_update", [*getValues(receiver), muCon("<field>"), rhs]) );
     
     //MuExp assignTo(a: (Assignable) `<Assignable receiver> . <Name field>`, MuExp rhs) =
     //assignTo(receiver, muCallPrim("<getOuterType(receiver)>_field_update", [*getValues(receiver), muCon("<field>"), rhs]) );

MuExp assignTo(a: (Assignable) `<Assignable receiver> ? <Expression defaultExpression>`, MuExp rhs) = 
	assignTo(receiver,  rhs);

MuExp assignTo(a: (Assignable) `\<  <{Assignable ","}+ elements> \>`, MuExp rhs) {
	nelems = size_assignables(elements);
    name = nextTmp();
    elems = [ e | e <- elements];	// hack since elements[i] yields a value result;
    return muBlock(
              muAssignTmp(name, rhs) + 
              [ assignTo(elems[i], muCallPrim("tuple_subscript_int", [muTmp(name), muCon(i)]) )
              | i <- [0 .. nelems]
              ]);
}

MuExp assignTo(a: (Assignable) `<Name name> ( <{Assignable ","}+ arguments> )`, MuExp rhs) { 
    nelems = size_assignables(elements);
    name = nextTmp();
    elems = [ e | e <- elements];	// hack since elements[i] yields a value result;
    return muBlock(
              muAssignTmp(name, rhs) + 
              [ assignTo(elems[i], muCalla("adt_subscript_int", [muTmp(name), muCon(i)]) )
              | i <- [0 .. nelems]
              ]);
}

MuExp assignTo(a: (Assignable) `<Assignable receiver> @ <Name annotation>`,  MuExp rhs) =
     assignTo(receiver, muCallPrim("annotation_set", [*getValues(receiver), muCon("<annotation>"), rhs]));

// getValues: get the current value(s) of an assignable

list[MuExp] getValues(a: (Assignable) `<QualifiedName qualifiedName>`) = 
    [ mkVar("<qualifiedName>", qualifiedName@\loc) ];
    
list[MuExp] getValues(a: (Assignable) `<Assignable receiver> [ <Expression subscript> ]`) {
    otr = getOuterType(receiver);
    subscript_op = "<otr>_subscript";
    if(otr notin {"map"}){
       subscript_op += "_<getOuterType(subscript)>";
    }
    return [ muCallPrim(subscript_op, [*getValues(receiver), translate(subscript)]) ];
}
    
list[MuExp] getValues(a: (Assignable) `<Assignable receiver> [ <OptionalExpression optFirst> .. <OptionalExpression optLast> ]`) = 
    translateSlice(getValues(receiver), translateOpt(optFirst), muCon(false),  translateOpt(optLast));
    
list[MuExp] getValues(a: (Assignable) `<Assignable receiver> [ <OptionalExpression optFirst>, <Expression second> .. <OptionalExpression optLast> ]`) = 
    translateSlice(getValues(receiver), translateOpt(optFirst), translate(second),  translateOpt(optLast));

list[MuExp] getValues(a:(Assignable) `<Assignable receiver> . <Name field>`) = 
    [ muCallPrim("<getOuterType(receiver)>_field_access", [ *getValues(receiver), muCon("<field>")]) ];

list[MuExp] getValues(a: (Assignable) `<Assignable receiver> ? <Expression defaultExpression>`) = 
     [ generateIfDefinedOtherwise(getValues(receiver)[0], translate(defaultExpression)) ];

list[MuExp] getValues(a:(Assignable) `\<  <{Assignable ","}+ elements > \>` ) = [ *getValues(elm) | elm <- elements ];

list[MuExp] getValues(a:(Assignable) `<Name name> ( <{Assignable ","}+ arguments> )` ) = [ *getValues(arg) | arg <- arguments ];

list[MuExp] getValues(a: (Assignable) `<Assignable receiver> @ <Name annotation>`) = 
    [ muCallPrim("annotation_get", [ *getValues(receiver), muCon("<annotation>")]) ];

// getReceiver: get the final receiver of an assignable

Assignable getReceiver(a: (Assignable) `<QualifiedName qualifiedName>`) = a;
Assignable getReceiver(a: (Assignable) `<Assignable receiver> [ <Expression subscript> ]`) = getReceiver(receiver);
Assignable getReceiver(a: (Assignable) `<Assignable receiver> [ <OptionalExpression optFirst> .. <OptionalExpression optLast> ]`) = getReceiver(receiver);
Assignable getReceiver(a: (Assignable) `<Assignable receiver> [ <OptionalExpression optFirst>, <Expression second> .. <OptionalExpression optLast> ]`) = getReceivers(receiver);  
Assignable getReceiver(a: (Assignable) `<Assignable receiver> . <Name field>`) = getReceiver(receiver); 
Assignable getReceiver(a: (Assignable) `<Assignable receiver> ? <Expression defaultExpression>`) = getReceiver(receiver); 
Assignable getReceiver(a: (Assignable) `<Name name> ( <{Assignable ","}+ arguments> )`) = a;
Assignable getReceiver(a: (Assignable) `\< <{Assignable ","}+ elements> \>`) =  a;
Assignable getReceiver(a: (Assignable) `<Assignable receiver> @ <Name annotation>`) = getReceiver(receiver); 
