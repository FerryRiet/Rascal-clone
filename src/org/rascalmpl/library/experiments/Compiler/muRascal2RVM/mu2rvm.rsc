module experiments::Compiler::muRascal2RVM::mu2rvm

import Prelude;

import experiments::Compiler::RVM::AST;

import experiments::Compiler::muRascal::Syntax;
import experiments::Compiler::muRascal::AST;
import experiments::Compiler::muRascal::Implode;

import experiments::Compiler::Rascal2muRascal::RascalModule;
import experiments::Compiler::Rascal2muRascal::TypeUtils;
import experiments::Compiler::muRascal2RVM::ToplevelType;
import experiments::Compiler::muRascal2RVM::StackSize;
import experiments::Compiler::muRascal2RVM::PeepHole;

alias INS = list[Instruction];

// Unique label generator

int nlabel = -1;
str nextLabel() { nlabel += 1; return "L<nlabel>"; }

str functionScope = "";
int nlocal = 0;

int get_nlocals() = nlocal;

void set_nlocals(int n) {
	nlocal = n;
}

// Systematic label generation related to loops

str mkContinue(str loopname) = "CONTINUE_<loopname>";
str mkBreak(str loopname) = "BREAK_<loopname>";
str mkFail(str loopname) = "FAIL_<loopname>";
str mkElse(str branchname) = "ELSE_<branchname>";

// Exception handling: labels to mark the start and end of 'try', 'catch' and 'finally' blocks 
str mkTryFrom(str label) = "TRY_FROM_<label>";
str mkTryTo(str label) = "TRY_TO_<label>";
str mkCatchFrom(str label) = "CATCH_FROM_<label>";
str mkCatchTo(str label) = "CATCH_TO_<label>";
str mkFinallyFrom(str label) = "FINALLY_FROM_<label>";
str mkFinallyTo(str label) = "FINALLY_TO_<label>";

//int defaultStackSize = 25;

int newLocal() {
    n = nlocal;
    nlocal += 1;
    return n;
}

map[str,int] temporaries = ();
str asUnwrapedThrown(str name) = name + "_unwraped";

int getTmp(str name){
   if(temporaries[name]?)
   		return temporaries[name];
   n = newLocal();
   temporaries[name] = n;
   return n;		
}

// Does an expression produce a value? (needed for cleaning up the stack)

bool producesValue(muWhile(str label, MuExp cond, list[MuExp] body)) = false;
bool producesValue(muDo(str label, list[MuExp] body,  MuExp cond)) = false;
bool producesValue(muReturn()) = false;
bool producesValue(muNext(MuExp coro)) = false;
default bool producesValue(MuExp exp) = true;

// Management needed to compute exception tables

// An EEntry's handler is defined for ranges 
// this is needed to inline 'finally' blocks, which may be defined in different 'try' scopes, 
// into 'try', 'catch' and 'finally' blocks
alias EEntry = tuple[lrel[str,str] ranges, Symbol \type, str \catch, MuExp \finally];

// Stack of 'try' blocks (needed as 'try' blocks may be nested)
list[EEntry] tryBlocks = [];
list[EEntry] finallyBlocks = [];

// Functions to manage the stack of 'try' blocks
void enterTry(str from, str to, Symbol \type, str \catch, MuExp \finally) {
	tryBlocks = <[<from, to>], \type, \catch, \finally> + tryBlocks;
	finallyBlocks = <[<from, to>], \type, \catch, \finally> + finallyBlocks;
}
void leaveTry() {
	tryBlocks = tail(tryBlocks);
}
void leaveFinally() {
	finallyBlocks = tail(finallyBlocks);
}

// Get the label of a top 'try' block
EEntry topTry() = top(tryBlocks);

// 'Catch' blocks may also throw an exception, which must be handled by 'catch' blocks of surrounding 'try' block
list[EEntry] catchAsPartOfTryBlocks = [];

void enterCatchAsPartOfTryBlock(str from, str to, Symbol \type, str \catch, MuExp \finally) {
	catchAsPartOfTryBlocks = <[<from, to>], \type, \catch, \finally> + catchAsPartOfTryBlocks;
}
void leaveCatchAsPartOfTryBlocks() {
	catchAsPartOfTryBlocks = tail(catchAsPartOfTryBlocks);
}

EEntry topCatchAsPartOfTryBlocks() = top(catchAsPartOfTryBlocks);


// Instruction block of all the 'catch' blocks within a function body in the same order in which they appear in the code
list[INS] catchBlocks = [[]];
int currentCatchBlock = 0;

INS finallyBlock = [];

// As we use label names to mark try blocks (excluding 'catch' clauses)
list[EEntry] exceptionTable = [];

/*********************************************************************/
/*      Translate a muRascal module                                  */
/*********************************************************************/

// Translate a muRascal module

RVMProgram mu2rvm(muModule(str module_name, list[loc] imports, map[str,Symbol] types, 
                           list[MuFunction] functions, list[MuVariable] variables, list[MuExp] initializations,
                           map[str,int] resolver, lrel[str,list[str],list[str]] overloaded_functions, map[Symbol, Production] grammar), 
                  bool listing=false){
  funMap = ();
  nlabel = -1;
  temporaries = ();
  
  println("mu2rvm: Compiling module <module_name>");
 
  for(fun <- functions){
    functionScope = fun.qname;
    nlocal = fun.nlocals;
    temporaries = ();
    exceptionTable = [];
    catchBlocks = [[]];
    if(listing){
    	iprintln(fun);
    }
    // Append catch blocks to the end of the function body code
    //code = tr(fun.body) + [ *catchBlock | INS catchBlock <- catchBlocks ];
    code = peephole(tr(fun.body)) + [ *catchBlock | INS catchBlock <- catchBlocks ];
    
    // Debugging exception handling
    // println("FUNCTION BODY:");
    // for(ins <- code) {
    //	 println("	<ins>");
    // }
    // println("EXCEPTION TABLE:");
    // for(entry <- exceptionTable) {
    //	 println("	<entry>");
    // }
    
    required_frame_size = nlocal + estimate_stack_size(fun.body);
    lrel[str from, str to, Symbol \type, str target] exceptions = [ <range.from, range.to, entry.\type, entry.\catch> | tuple[lrel[str,str] ranges, Symbol \type, str \catch, MuExp _] entry <- exceptionTable, 
    																			  tuple[str from, str to] range <- entry.ranges ];
    funMap += (fun is muCoroutine) ? (fun.qname : COROUTINE(fun.qname, fun.scopeIn, fun.nformals, nlocal, fun.refs, required_frame_size, code))
    							   : (fun.qname : FUNCTION(fun.qname, fun.ftype, fun.scopeIn, fun.nformals, nlocal, fun.isVarArgs, required_frame_size, code, exceptions));
  }
  
  main_fun = getUID(module_name,[],"MAIN",1);
  module_init_fun = getUID(module_name,[],"#<module_name>_init",1);
  ftype = Symbol::func(Symbol::\value(),[Symbol::\list(Symbol::\value())]);
  if(!funMap[main_fun]?) {
  	 main_fun = getFUID(module_name,"main",ftype,0);
  	 module_init_fun = getFUID(module_name,"#<module_name>_init",ftype,0);
  }
  
  funMap += (module_init_fun : FUNCTION(module_init_fun, ftype, "" /*in the root*/, 1, size(variables) + 1, false, estimate_stack_size(initializations) + size(variables) + 1, 
  									[*trvoidblock(initializations), 
  									 LOADCON(true),
  									 RETURN1(1),
  									 HALT()
  									],
  									[]));
 
  main_testsuite = getUID(module_name,[],"TESTSUITE",1);
  module_init_testsuite = getUID(module_name,[],"#module_init_testsuite",1);
  if(!funMap[main_testsuite]?) { 						
  	 main_testsuite = getFUID(module_name,"testsuite",ftype,0);
  	 module_init_testsuite = getFUID(module_name,"#module_init_testsuite",ftype,0);
  }
  
  res = rvm(module_name, imports, types, funMap, [], resolver, overloaded_functions, grammar);
  if(listing){
    for(fname <- funMap)
  		iprintln(funMap[fname]);
  }
  return res;
}

/*********************************************************************/
/*      Translate lists of muRascal expressions                      */
/*********************************************************************/


INS  tr(list[MuExp] exps) = [ *tr(exp) | exp <- exps ];

INS tr_and_pop(muBlock([])) = [];

default INS tr_and_pop(MuExp exp) = producesValue(exp) ? [*tr(exp), POP()] : tr(exp);

INS trblock(list[MuExp] exps) {
  if(size(exps) == 0){
     return [LOADCON(666)]; // TODO: throw "Non void block cannot be empty";
  }
  ins = [*tr_and_pop(exp) | exp <- exps[0..-1]];
  return ins + tr(exps[-1]);
}

//default INS trblock(MuExp exp) = tr(exp);

INS trvoidblock(list[MuExp] exps){
  if(size(exps) == 0)
     return [];
  ins = [*tr_and_pop(exp) | exp <- exps];
  return ins;
}

INS tr(muBlock([MuExp exp])) = tr(exp);
default INS tr(muBlock(list[MuExp] exps)) = trblock(exps);


/*********************************************************************/
/*      Translate a single muRascal expression                       */
/*********************************************************************/

// Literals and type constants

INS tr(muBool(bool b)) = [LOADBOOL(b)];
INS tr(muCon("true")) = [LOADCON(true)];
INS tr(muCon("false")) = [LOADCON(false)];

INS tr(muInt(int n)) = [LOADINT(n)];
default INS tr(muCon(value c)) = [LOADCON(c)];

INS tr(muTypeCon(Symbol sym)) = [LOADTYPE(sym)];

// muRascal functions

INS tr(muFun(str fuid)) = [LOADFUN(fuid)];
INS tr(muFun(str fuid, str scopeIn)) = [LOAD_NESTED_FUN(fuid, scopeIn)];

// Rascal functions

INS tr(muOFun(str fuid)) = [ LOADOFUN(fuid) ];

INS tr(muConstr(str fuid)) = [LOADCONSTR(fuid)];

// Variables and assignment

INS tr(muVar(str id, str fuid, int pos)) = [fuid == functionScope ? LOADLOC(pos) : LOADVAR(fuid, pos)];
INS tr(muLoc(str id, int pos)) = [LOADLOC(pos)];
INS tr(muTmp(str id)) = [LOADLOC(getTmp(id))];

INS tr(muLocDeref(str name, int pos)) = [ LOADLOCDEREF(pos) ];
INS tr(muVarDeref(str name, str fuid, int pos)) = [ fuid == functionScope ? LOADLOCDEREF(pos) : LOADVARDEREF(fuid, pos) ];

INS tr(muLocRef(str name, int pos)) = [ LOADLOCREF(pos) ];
INS tr(muVarRef(str name, str fuid, int pos)) = [ fuid == functionScope ? LOADLOCREF(pos) : LOADVARREF(fuid, pos) ];
INS tr(muTmpRef(str name)) = [ LOADLOCREF(getTmp(name)) ];

INS tr(muAssignLocDeref(str id, int pos, MuExp exp)) = [ *tr(exp), STORELOCDEREF(pos) ];
INS tr(muAssignVarDeref(str id, str fuid, int pos, MuExp exp)) = [ *tr(exp), fuid == functionScope ? STORELOCDEREF(pos) : STOREVARDEREF(fuid, pos) ];

INS tr(muAssign(str id, str fuid, int pos, MuExp exp)) = [*tr(exp), fuid == functionScope ? STORELOC(pos) : STOREVAR(fuid, pos)];
INS tr(muAssignLoc(str id, int pos, MuExp exp)) = [*tr(exp), STORELOC(pos) ];
INS tr(muAssignTmp(str id, MuExp exp)) = [*tr(exp), STORELOC(getTmp(id)) ];

// Calls

// Constructor

INS tr(muCallConstr(str fuid, list[MuExp] args)) = [ *tr(args), CALLCONSTR(fuid, size(args)) ];

// muRascal functions

INS tr(muCall(muFun(str fuid), list[MuExp] args)) = [*tr(args), CALL(fuid, size(args))];
INS tr(muCall(muConstr(str fuid), list[MuExp] args)) = [*tr(args), CALLCONSTR(fuid, size(args))];
INS tr(muCall(MuExp fun, list[MuExp] args)) = [*tr(args), *tr(fun), CALLDYN(size(args))];

// Rascal functions

INS tr(muOCall(muOFun(str fuid), list[MuExp] args)) = [*tr(args), OCALL(fuid, size(args))];
INS tr(muOCall(MuExp fun, Symbol types, list[MuExp] args)) 
	= [ *tr(args), 
		*tr(fun), 
		OCALLDYN(types, size(args))];

// Calls to Rascal primitives

INS tr(muCallPrim("println", list[MuExp] args)) = [*tr(args), PRINTLN(size(args))];
INS tr(muCallPrim("subtype", list[MuExp] args)) = [*tr(args), SUBTYPE()];
INS tr(muCallPrim("typeOf", list[MuExp] args)) = [*tr(args), TYPEOF()];

default INS tr(muCallPrim(str name, list[MuExp] args)) = (name == "println") ? [*tr(args), PRINTLN(size(args))] : [*tr(args), CALLPRIM(name, size(args))];

// Calls to MuRascal primitives

INS tr(muCallMuPrim("println", list[MuExp] args)) = [*tr(args), PRINTLN(size(args))];
INS tr(muCallMuPrim("subscript_array_mint", list[MuExp] args)) = [*tr(args), SUBSCRIPTARRAY()];
INS tr(muCallMuPrim("subscript_list_mint", list[MuExp] args)) = [*tr(args), SUBSCRIPTLIST()];
INS tr(muCallMuPrim("less_mint_mint", list[MuExp] args)) = [*tr(args), LESSINT()];
INS tr(muCallMuPrim("greater_equal_mint_mint", list[MuExp] args)) = [*tr(args), GREATEREQUALINT()];
INS tr(muCallMuPrim("addition_mint_mint", list[MuExp] args)) = [*tr(args), ADDINT()];
INS tr(muCallMuPrim("subtraction_mint_mint", list[MuExp] args)) = [*tr(args), SUBTRACTINT()];
INS tr(muCallMuPrim("and_mbool_mbool", list[MuExp] args)) = [*tr(args), ANDBOOL()];
INS tr(muCallMuPrim("check_arg_type", list[MuExp] args)) = [*tr(args), CHECKARGTYPE()];

default INS tr(muCallMuPrim(str name, list[MuExp] args)) = [*tr(args), CALLMUPRIM(name, size(args))];

INS tr(muCallJava(str name, str class, Symbol types, int reflect, list[MuExp] args)) = [ *tr(args), CALLJAVA(name, class, types, reflect) ];

// Return

INS tr(muReturn()) = [RETURN0()];
INS tr(muReturn(MuExp exp)) {
	if(muTmp(str varname) := exp) {
		inlineMuFinally();
		return [*finallyBlock, *tr(exp), RETURN1(1)];
	}
	return [*tr(exp), RETURN1(1)];
}
INS tr(muReturn(MuExp exp, list[MuExp] exps))
	= [*tr(exp), *tr(exps), RETURN1(size(exps) + 1)];

INS tr(muFailReturn()) = [ FAILRETURN() ];

INS tr(muFilterReturn()) = [ FILTERRETURN() ];

// Coroutines

INS tr(muCreate(muFun(str fuid))) = [CREATE(fuid, 0)];
INS tr(muCreate(MuExp fun)) = [ *tr(fun), CREATEDYN(0) ];
INS tr(muCreate(muFun(str fuid), list[MuExp] args)) = [ *tr(args), CREATE(fuid, size(args)) ];
INS tr(muCreate(MuExp fun, list[MuExp] args)) = [ *tr(args), *tr(fun), CREATEDYN(size(args)) ];

INS tr(muInit(MuExp exp)) = [*tr(exp), INIT(0)];
INS tr(muInit(MuExp coro, list[MuExp] args)) = [*tr(args), *tr(coro),  INIT(size(args))];  // order!

// INS tr(muHasNext(MuExp coro)) = [*tr(coro), HASNEXT()];

INS tr(muNext(MuExp coro)) = [*tr(coro), NEXT0()];
INS tr(muNext(MuExp coro, list[MuExp] args)) = [*tr(args), *tr(coro),  NEXT1()]; // order!

INS tr(muYield()) = [YIELD0()];
INS tr(muYield(MuExp exp)) = [*tr(exp), YIELD1(1)];
INS tr(muYield(MuExp exp, list[MuExp] exps)) = [ *tr(exp), *tr(exps), YIELD1(size(exps) + 1) ];

INS tr(muExhaust()) = [ EXHAUST() ];

INS tr(muGuard(MuExp exp)) = [ *tr(exp), GUARD() ];

// Exceptions

INS tr(muThrow(MuExp exp)) = [ *tr(exp), THROW() ];

INS tr(muTry(MuExp exp, MuCatch \catch, MuExp \finally)) {
	// Mark the begin and end of the 'try' and 'catch' blocks
	str tryLab = nextLabel();
	str catchLab = nextLabel();
	str finallyLab = nextLabel();
	
	str try_from      = mkTryFrom(tryLab);
	str try_to        = mkTryTo(tryLab);
	str catch_from    = mkCatchFrom(catchLab); // used to jump
	str catch_to      = mkCatchTo(catchLab);   // used to mark the end of a 'catch' block and find a handler catch
	
	// Mark the begin of 'catch' blocks that have to be also translated as part of 'try' blocks 
	str catchAsPartOfTry_from = mkCatchFrom(nextLabel()); // used to find a handler catch
	
	// There might be no surrounding 'try' block for a 'catch' block
	if(!isEmpty(tryBlocks)) {
		// Get the outer 'try' block
		EEntry currentTry = topTry();
		// Enter the current 'catch' block as part of the outer 'try' block
		enterCatchAsPartOfTryBlock(catchAsPartOfTry_from, catch_to, currentTry.\type, currentTry.\catch, currentTry.\finally);
	}
	
	// Enter the current 'try' block; also including a 'finally' block
	enterTry(try_from, try_to, \catch.\type, catch_from, \finally); 
	
	// Translate the 'try' block; inlining 'finally' blocks where necessary
	code = [ LABEL(try_from), *tr(exp) ];
	
	oldFinallyBlocks = finallyBlocks;
	leaveFinally();
	
	// Fill in the 'try' block entry into the current exception table
	currentTry = topTry();
	exceptionTable += <currentTry.ranges, currentTry.\type, currentTry.\catch, currentTry.\finally>;
	
	leaveTry();
	
	// Translate the 'finally' block; inlining 'finally' blocks where necessary
	code = code + [ LABEL(try_to), *trMuFinally(\finally) ];
	
	// Translate the 'catch' block; inlining 'finally' blocks where necessary
	// 'Catch' block may also throw an exception, and if it is part of an outer 'try' block,
	// it has to be handled by the 'catch' blocks of the outer 'try' blocks
	
	oldTryBlocks = tryBlocks;
	tryBlocks = catchAsPartOfTryBlocks;
	finallyBlocks = oldFinallyBlocks;
	
	trMuCatch(\catch, catch_from, catchAsPartOfTry_from, catch_to, try_to);
		
	// Restore 'try' block environment
	catchAsPartOfTryBlocks = tryBlocks;
	tryBlocks = oldTryBlocks;
	finallyBlocks = tryBlocks;
	
	// Fill in the 'catch' block entry into the current exception table
	if(!isEmpty(tryBlocks)) {
		EEntry currentCatchAsPartOfTryBlock = topCatchAsPartOfTryBlocks();
		exceptionTable += <currentCatchAsPartOfTryBlock.ranges, currentCatchAsPartOfTryBlock.\type, currentCatchAsPartOfTryBlock.\catch, currentCatchAsPartOfTryBlock.\finally>;
		leaveCatchAsPartOfTryBlocks();
	}
	
	return code;
}

void trMuCatch(muCatch(str id, Symbol \type, MuExp exp), str from, str fromAsPartOfTryBlock, str to, str jmpto) {
	
	oldCatchBlocks = catchBlocks;
	oldCurrentCatchBlock = currentCatchBlock;
	currentCatchBlock = size(catchBlocks);
	catchBlocks = catchBlocks + [[]];
	catchBlock = [];
	
	str catchAsPartOfTryNewLab = nextLabel();
	str catchAsPartOfTryNew_from = mkCatchFrom(catchAsPartOfTryNewLab);
	str catchAsPartOfTryNew_to = mkCatchTo(catchAsPartOfTryNewLab);
	
	// Copy 'try' block environment of the 'catch' block; needed in case of nested 'catch' blocks
	catchAsPartOfTryBlocks = [ < [<catchAsPartOfTryNew_from, catchAsPartOfTryNew_to>],
								 entry.\type, entry.\catch, entry.\finally > | EEntry entry <- catchAsPartOfTryBlocks ];
	
	if(muBlock([]) := exp) {
		catchBlock = [ LABEL(from), POP(), LABEL(to), JMP(jmpto) ];
	} else {
		catchBlock = [ LABEL(from), 
					   // store a thrown value
					   STORELOC(getTmp(id)), POP(),
					   // load a thrown value, unwrap it and store the unwrapped one in a separate local variable
					   LOADLOC(getTmp(id)), UNWRAPTHROWN(getTmp(asUnwrapedThrown(id))),
					   *tr(exp), LABEL(to), JMP(jmpto) ];
	}
	
	if(!isEmpty(catchBlocks[currentCatchBlock])) {
		catchBlocks[currentCatchBlock] = [ LABEL(catchAsPartOfTryNew_from), *catchBlocks[currentCatchBlock], LABEL(catchAsPartOfTryNew_to) ];
		for(currentCatchAsPartOfTryBlock <- catchAsPartOfTryBlocks) {
			exceptionTable += <currentCatchAsPartOfTryBlock.ranges, currentCatchAsPartOfTryBlock.\type, currentCatchAsPartOfTryBlock.\catch, currentCatchAsPartOfTryBlock.\finally>;
		}
	} else {
		catchBlocks = oldCatchBlocks;
	}
	
	currentCatchBlock = oldCurrentCatchBlock;
	
	// 'catchBlock' is always non-empty 
	catchBlocks[currentCatchBlock] = [ LABEL(fromAsPartOfTryBlock), *catchBlocks[currentCatchBlock], *catchBlock ];
		
}

// TODO: Re-think the way empty 'finally' blocks are translated
INS trMuFinally(MuExp \finally) = (muBlock([]) := \finally) ? [ LOADCON(666), POP() ] : tr(\finally);

void inlineMuFinally() {
	
	finallyBlock = [];

	str finallyLab   = nextLabel();
	str finally_from = mkFinallyFrom(finallyLab);
	str finally_to   = mkFinallyTo(finallyLab);
	
	// Stack of 'finally' blocks to be inlined
	list[MuExp] finallyStack = [ entry.\finally | EEntry entry <- finallyBlocks ];
	
	// Make a space (hole) in the current (potentially nested) 'try' blocks to inline a 'finally' block
	if(isEmpty([ \finally | \finally <- finallyStack, !(muBlock([]) := \finally) ])) {
		return;
	}
	tryBlocks = [ <[ *head, <from,finally_from>, <finally_to + "_<size(finallyBlocks) - 1>",to>], 
				   tryBlock.\type, tryBlock.\catch, tryBlock.\finally> | EEntry tryBlock <- tryBlocks, 
				   														 [ *tuple[str,str] head, <from,to> ] := tryBlock.ranges ];
	
	oldTryBlocks = tryBlocks;
	oldCatchAsPartOfTryBlocks = catchAsPartOfTryBlocks;
	oldFinallyBlocks = finallyBlocks;
	oldCurrentCatchBlock = currentCatchBlock;
	oldCatchBlocks = catchBlocks;
	
	// Translate 'finally' blocks as 'try' blocks: mark them with labels
	tryBlocks = [];	
	for(int i <- [0..size(finallyStack)]) {
		// The last 'finally' does not have an outer 'try' block
		if(i < size(finallyStack) - 1) {
			EEntry outerTry = finallyBlocks[i + 1];
			tryBlocks = tryBlocks + [ <[<finally_from, finally_to + "_<i>">], outerTry.\type, outerTry.\catch, outerTry.\finally> ];
		}
	}
	finallyBlocks = tryBlocks;
	catchAsPartOfTryBlocks = [];
	currentCatchBlock = size(catchBlocks);
	catchBlocks = catchBlocks + [[]];
	
	finallyBlock = [ LABEL(finally_from) ];
	for(int i <- [0..size(finallyStack)]) {
		finallyBlock = [ *finallyBlock, *trMuFinally(finallyStack[i]), LABEL(finally_to + "_<i>") ];
		if(i < size(finallyStack) - 1) {
			EEntry currentTry = topTry();
			// Fill in the 'catch' block entry into the current exception table
			exceptionTable += <currentTry.ranges, currentTry.\type, currentTry.\catch, currentTry.\finally>;
			leaveTry();
			leaveFinally();
		}
	}
	
	tryBlocks = oldTryBlocks;
	catchAsPartOfTryBlocks = oldCatchAsPartOfTryBlocks;
	finallyBlocks = oldFinallyBlocks;
	if(isEmpty(catchBlocks[currentCatchBlock])) {
		catchBlocks = oldCatchBlocks;
	}
	currentCatchBlock = oldCurrentCatchBlock;
	
}

// Control flow

// If

INS tr(muIfelse(str label, MuExp cond, list[MuExp] thenPart, list[MuExp] elsePart)) {
    if(label == "") {
    	label = nextLabel();
    };
    elseLab = mkElse(label);
    continueLab = mkContinue(label);
    return [ *tr_cond(cond, { mkFail(label) }, elseLab), 
             *(isEmpty(thenPart) ? LOADCON(111) : trblock(thenPart)),
             JMP(continueLab), 
             LABEL(elseLab),
             *(isEmpty(elsePart) ? LOADCON(222) : trblock(elsePart)),
             LABEL(continueLab)
           ];
}

// While

INS tr(muWhile(str label, MuExp cond, list[MuExp] body)) {
    if(label == ""){
    	label = nextLabel();
    }	
    continueLab = mkContinue(label);
    failLab = mkFail(label);
    breakLab = mkBreak(label);
    return [ *tr_cond(cond, { continueLab, failLab }, breakLab), 	 					
    		 *trvoidblock(body),			
    		 JMP(continueLab),
    		 LABEL(breakLab)		
    		];
}
// Do

INS tr(muDo(str label, list[MuExp] body, MuExp cond)) {
    if(label == ""){
    	label = nextLabel();
    }
    continueLab = mkContinue(label);
    breakLab = mkBreak(label);
    return [ LABEL(continueLab),
     		 *trvoidblock(body),	
             *tr_cond_do(cond, { continueLab }, breakLab),	
    		 JMP(continueLab),
    		 LABEL(breakLab)		
           ];
}

INS tr(muBreak(str label)) = [ JMP(mkBreak(label)) ];
INS tr(muContinue(str label)) = [ JMP(mkContinue(label)) ];
INS tr(muFail(str label)) = [ JMP(mkFail(label)) ];


INS tr(muTypeSwitch(MuExp exp, list[MuTypeCase] cases, MuExp defaultExp)){
   defaultLab = nextLabel();
   continueLab = mkContinue(defaultLab);
   labels = [defaultLab | i <- index(toplevelTypes) ];
   caseCode =  [];
	for(cs <- cases){
		caseLab = defaultLab + "_" + cs.name;
		labels[getToplevelType(cs.name)] = caseLab;
		caseCode += [ LABEL(caseLab), *tr(cs.exp), JMP(continueLab) ];
	 };
   caseCode += [LABEL(defaultLab), *tr(defaultExp), JMP(continueLab) ];
   return [ *tr(exp), JMPSWITCH(labels), *caseCode, LABEL(continueLab) ];
}

// Multi/One/All outside conditional context
    
default INS tr(e: muMulti(MuExp exp)) = 
	 [ *tr(exp),
       INIT(0),
       NEXT0()
    ];
    //when bprintln("tr outer muMulti: <e>");
    
INS tr(e:muOne(list[MuExp] exps)) {
  //bprintln("tr outer muOne: <e>");
  dummyLab = nextLabel();
  failLab = nextLabel();
  afterLab = nextLabel();
  return
     [ *tr_cond(muAll(exps), { dummyLab }, failLab),
       LOADCON(true),
       JMP(afterLab),
       LABEL(failLab),
       LOADCON(false),
       LABEL(afterLab)
     ];
}

INS tr(e:muAll(list[MuExp] exps)) { 
    //println("tr outer muAll: <e>");
    
    startLab = nextLabel();
    //continueLab = nextLabel();
    failLab = nextLabel();
    currentFail = failLab;
    afterLab = nextLabel();
    
    lastMulti = -1;
    for(i <- index(exps)){
        if(muMulti(exp1) := exps[i]){
           lastMulti = i;
        }
    }
    
    code = [ JMP(startLab),
             LABEL(failLab),
             LOADCON(false),
             JMP(afterLab),
             LABEL(startLab)
           ];
    for(i <- index(exps)){
        exp = exps[i];
        if(muMulti(exp1) := exp){
           newFail = nextLabel();
           co = newLocal();
           code += [ *tr(exp1), 
          		     INIT(0), 
          		     STORELOC(co), 
          		     POP(),
          		     LABEL(newFail),
          		     LOADLOC(co), 
          		     NEXT0(), 
          		     JMPFALSE(currentFail)
          		   ];
          currentFail = newFail;
        } else {
          code += [ *tr(exp), 
          		    JMPFALSE(currentFail)
          		  ];
        } 
    }
    code += [ LOADCON(true),
              LABEL(afterLab)
    		 ];
    return code;   
}

// The above list of muExps is exhaustive, no other cases exist

default INS tr(MuExp e) { throw "Unknown node in the muRascal AST: <e>"; }

/*********************************************************************/
/*      End of muRascal expressions                                  */
/*********************************************************************/


/*********************************************************************/
/*      Translate conditions                                         */
/*********************************************************************/

/*
 * The contract of tr_cond is as follows:
 * - continueLab: continue searching for more solutions for this condition
 *   (is created by the caller, but inserted in the code generated by tr_cond)
 * - failLab: location ot jump to whe no more solutions exist.
 *   (is created by the caller and only jumped to by code generated by tr_cond.)
 *
 * The generated code falls through to subsequent instructions when the condition is true, and jumps to failLab otherwise.
 */

// muOne: explore one successfull evaluation

INS tr_cond(e: muOne(list[MuExp] exps), set[str] continueLabs, str failLab){
    code = [LABEL(continueLab) | str continueLab <- continueLabs];
    for(exp <- exps){
        if(muMulti(exp1) := exp){
          code += [*tr(exp1), 
          		   INIT(0), 
          		   NEXT0(), 
          		   JMPFALSE(failLab)
          		  ];
        } else {
          code += [*tr(exp), 
          		   JMPFALSE(failLab)
          		  ];
        } 
    } 
    return code;   
}

// Special case for do_while:
// - continueLab is inserted by caller.

INS tr_cond_do(muOne(list[MuExp] exps), set[str] continueLabs, str failLab){
    code = [];
    for(exp <- exps){
        if(muMulti(exp1) := exp){
          code += [*tr(exp1), 
          		   INIT(0), 
          		   NEXT0(), 
          		   JMPFALSE(failLab)
          		  ];
        } else {
          code += [*tr(exp), 
          		   JMPFALSE(failLab)
          		  ];
        } 
    } 
    return code;   
}

// muAll: explore all sucessfull evaluations

INS tr_cond(e: muAll(list[MuExp] exps), set[str] continueLabs, str failLab){
    code = [];
    lastMulti = -1;
    
    for(i <- index(exps)){
        if(muMulti(exp1) := exps[i]){
           lastMulti = i;
        }
    }
    startLab = nextLabel();
    currentFail = failLab;
    
    if(lastMulti == -1)
       code = [ JMP(startLab),
                *[ LABEL(continueLab) | str continueLab <- continueLabs ],
                JMP(failLab),
                LABEL(startLab)
              ];
 
    for(i <- index(exps)){
        exp = exps[i];
        if(muMulti(exp1) := exp){
          newFail = nextLabel();
          co = newLocal();
          code += [ *tr(exp1), 
          		    INIT(0), 
          		    STORELOC(co), 
          		    POP(),
           	        LABEL(newFail),
          			*( (i == lastMulti) ? [ LABEL(continueLab) | str continueLab <- continueLabs ] : [] ),
          		    LOADLOC(co), 
          		    NEXT0(), 
          		    JMPFALSE(currentFail)
          		  ];
          currentFail = newFail;
        } else {
          code += [*tr(exp), 
          		   JMPFALSE(currentFail)
          		  ];
        } 
    }
    return code;
}

INS tr_cond(e: muMulti(MuExp exp), set[str] continueLabs, str failLab) =
    [ *[ LABEL(continueLab) | str continueLab <- continueLabs ],
      *tr(exp),
      INIT(0),
      NEXT0(),
      JMPFALSE(failLab)
    ];

default INS tr_cond(MuExp exp, set[str] continueLabs, str failLab) 
	= [ * [ LABEL(continueLab) | str continueLab <- continueLabs ], *tr(exp), JMPFALSE(failLab) ];
    
