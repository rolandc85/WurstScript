package de.peeeq.wurstscript.translation.imtojass;

import static de.peeeq.wurstscript.jassAst.JassAst.JassFunction;
import static de.peeeq.wurstscript.jassAst.JassAst.JassFunctions;
import static de.peeeq.wurstscript.jassAst.JassAst.JassNatives;
import static de.peeeq.wurstscript.jassAst.JassAst.JassProg;
import static de.peeeq.wurstscript.jassAst.JassAst.JassSimpleVars;
import static de.peeeq.wurstscript.jassAst.JassAst.JassStatements;
import static de.peeeq.wurstscript.jassAst.JassAst.JassTypeDefs;
import static de.peeeq.wurstscript.jassAst.JassAst.JassVars;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.WPos;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.jassAst.JassAst;
import de.peeeq.wurstscript.jassAst.JassFunction;
import de.peeeq.wurstscript.jassAst.JassFunctions;
import de.peeeq.wurstscript.jassAst.JassProg;
import de.peeeq.wurstscript.jassAst.JassSimpleVar;
import de.peeeq.wurstscript.jassAst.JassVar;
import de.peeeq.wurstscript.jassAst.JassVars;
import de.peeeq.wurstscript.jassIm.ImArrayType;
import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.jassIm.ImProg;
import de.peeeq.wurstscript.jassIm.ImTupleArrayType;
import de.peeeq.wurstscript.jassIm.ImTupleType;
import de.peeeq.wurstscript.jassIm.ImVar;
import de.peeeq.wurstscript.jassIm.JassImElement;
import de.peeeq.wurstscript.jassIm.JassImElementWithNames;
import de.peeeq.wurstscript.jassIm.JassImElementWithTrace;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.utils.Utils;

public class ImToJassTranslator {

	private ImProg imProg;
	private Multimap<ImFunction, ImFunction> calledFunctions;
	private ImFunction mainFunc;
	private ImFunction confFunction;
	private JassProg prog;
	private Stack<ImFunction> translatingFunctions = new Stack<ImFunction>();
	private Set<ImFunction> translatedFunctions = Sets.newHashSet();
	private Set<String> usedNames = Sets.newHashSet();
	private Multimap<ImFunction, String> usedLocalNames = HashMultimap.create();

	public ImToJassTranslator(ImProg imProg, Multimap<ImFunction, ImFunction> calledFunctions, 
			ImFunction mainFunc, ImFunction confFunction) {
		this.imProg = imProg;
		this.calledFunctions = calledFunctions;
		this.mainFunc = mainFunc;
		this.confFunction = confFunction;
	}
	
	public JassProg translate() {
		JassVars globals = JassVars();
		JassFunctions functions = JassFunctions();
		prog = JassProg(JassTypeDefs(), globals, JassNatives(), functions);
		
		collectGlobalVars();
		
		translateFunctionTransitive(mainFunc);
		translateFunctionTransitive(confFunction);
		
		return prog;
	}

	private void collectGlobalVars() {
		for (ImVar v : imProg.getGlobals()) {
			globalImVars.add(v);
			getJassVarFor(v);
		}
	}

	private void translateFunctionTransitive(ImFunction imFunc) {
		if (translatedFunctions.contains(imFunc)) {
			// already translated
			return;
		}
		if (translatingFunctions.contains(imFunc)) {
			if (imFunc != translatingFunctions.peek()) {
				String msg = "cyclic dependency between functions: " ;
				boolean start = false;
				for (ImFunction f : translatingFunctions) {
					if (imFunc == f) {
						start = true;
					}
					if (start) {
						msg += "\n - " + Utils.printElement(getTrace(f)) + "  ( " + f.attrTrace().attrSource().getFile() + " line  " +  f.attrTrace().attrSource().getLine() + ")";
					}
				}
				WPos src = getTrace(imFunc).attrSource();
				throw new CompileError(src, msg);
			}
			// already translating, recursive function
			return;
		}
		translatingFunctions.push(imFunc);
		for (ImFunction f : sorted(calledFunctions.get(imFunc))) {
			translateFunctionTransitive(f);
		}
		
		translateFunction(imFunc);
		
		// translation finished
		if (translatingFunctions.pop() != imFunc) {
			throw new Error("something went wrong...");
		}
		translatedFunctions.add(imFunc);
	}

	private List<ImFunction> sorted(Collection<ImFunction> collection) {
		List<ImFunction> r = Lists.newArrayList(collection);
		Collections.sort(r, new Comparator<ImFunction>() {

			@Override
			public int compare(ImFunction f, ImFunction g) {
				return f.getName().compareTo(g.getName());
			}
		});
		return r;
	}

	public static AstElement getTrace(JassImElement elem) {
		while (elem != null) {
			if (elem instanceof JassImElementWithTrace) {
				JassImElementWithTrace jassImElementWithTrace = (JassImElementWithTrace) elem;
				AstElement t = jassImElementWithTrace.getTrace();
				if (t != null) {
					return t;
				}
			}
			elem = elem.getParent();
		}
		throw new Error("Could not get trace to original program.");
	}

	private void translateFunction(ImFunction imFunc) {
		JassFunction f = getJassFuncFor(imFunc);
		
		f.setReturnType(imFunc.getReturnType().translateType());
		// translate parameters
		for (ImVar v : imFunc.getParameters()) {
			f.getParams().add((JassSimpleVar) getJassVarFor(v));
		}
		// translate locals
		for (ImVar v : imFunc.getLocals()) {
			f.getLocals().add(getJassVarFor(v));
		}
		imFunc.getBody().translate(f.getBody(), f, this);
		
	}



	private List<JassSimpleVar> simpleVars(List<JassVar> l) {
		List<JassSimpleVar> result = Lists.newArrayListWithCapacity(l.size());
		for (JassVar i : l) {
			result.add((JassSimpleVar) i);
		}
		return result;
	}

	private String getUniqueGlobalName(String name) { // TODO find local names
		if (!usedNames.contains(name)) {
			usedNames.add(name);
			return name;
		}
		String name2;
		int i = 1;
		do {
			i++;
			name2 = name + "_" + i;
		} while (usedNames.contains(name2));
		usedNames.add(name2);
		return name2;
	}
	
	private String getUniqueLocalName(ImFunction imFunction, String name) {
		if (!usedNames.contains(name) && !usedLocalNames.containsEntry(imFunction, name)) {
			usedLocalNames.put(imFunction, name);
			return name;
		}
		String name2;
		int i = 1;
		do {
			i++;
			name2 = name + "_" + i;
		} while (usedNames.contains(name2) || usedLocalNames.containsEntry(imFunction, name2));
		usedLocalNames.put(imFunction, name2);
		return name2;
	}

	Map<Pair<String, Integer>, JassVar> tempReturnVars = Maps.newHashMap();
	
	public JassVar getTempReturnVar(String type, int nr) {
		Pair<String, Integer> key = Pair.create(type, nr);
		JassVar v = tempReturnVars.get(key);
		if (v == null) {
			v = JassAst.JassSimpleVar(type, getUniqueGlobalName("temp_return_"+type+"_"+nr));
			prog.getGlobals().add(v);
			tempReturnVars.put(key, v);
		}
		return v;
	}

	Map<ImVar, JassVar> jassVars = Maps.newHashMap();
	private Set<ImVar> globalImVars = Sets.newHashSet();
	
	public JassVar getJassVarFor(ImVar v) {
		JassVar result = jassVars.get(v);
		if (result == null) {
			boolean isArray = v.getType() instanceof ImArrayType || v.getType() instanceof ImTupleArrayType;
			int i = 0;
			String type = v.getType().translateType();
			String name = v.getName();
			if (v.getNearestFunc() != null) {
				name = getUniqueLocalName(v.getNearestFunc(), name);
			} else {
				name = getUniqueGlobalName(name);
			}
			if (isArray) {
				result = JassAst.JassArrayVar(type, name);
			} else {
				result = JassAst.JassSimpleVar(type, name);
			}
			if (isGlobal(v) && !v.getIsBJ()) {
				prog.getGlobals().add(result);
			}
			jassVars.put(v, result);
		}
		return result ;
	}

	private boolean isGlobal(ImVar v) {
		return globalImVars.contains(v);
	}

	public JassVar newTempVar(JassFunction f, String type, String name) {
		JassSimpleVar v = JassAst.JassSimpleVar(type, getUniqueGlobalName(name));
		f.getLocals().add(v);
		return v;
	}

	Map<ImFunction, JassFunction> jassFuncs = Maps.newHashMap();
	
	public JassFunction getJassFuncFor(ImFunction func) {
		JassFunction f = jassFuncs.get(func);
		if (f == null) {
			f = JassFunction(getUniqueGlobalName(func.getName()), JassSimpleVars(), "nothing", JassVars(), JassStatements());
			if (!func.isNative() && !func.isBj()) {
				prog.getFunctions().add(f);
			}
			jassFuncs.put(func, f);
		}
		return f;
	}

	
}