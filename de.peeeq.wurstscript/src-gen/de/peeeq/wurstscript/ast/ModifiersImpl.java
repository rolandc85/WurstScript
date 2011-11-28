//generated by parseq
package de.peeeq.wurstscript.ast;

class ModifiersImpl extends Modifiers implements AstElementIntern {
 	private AstElement parent;
	public AstElement getParent() { return parent; }
	public void setParent(AstElement parent) {
		if (parent != null && this.parent != null) { 			throw new Error("Parent of " + this + " already set: " + this.parent + "\ntried to change to " + parent); 		}
		this.parent = parent;
	}

	protected void other_setParentToThis(Modifier t) {
		((AstElementIntern) t).setParent(this);
	}
	protected void other_clearParent(Modifier t) {
		((AstElementIntern) t).setParent(null);
	}
	@Override public void accept(CompilationUnit.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(Modifiers.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(FunctionDefinition.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(TypeDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(FuncDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ClassSlots.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(AstElementWithModifier.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ConstructorDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(PackageOrGlobal.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(VarDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassGlobalBlock.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(WPackage.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ClassOrModule.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(WEntity.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ClassSlot.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ClassMember.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(NativeType.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(NativeFunc.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(TopLevelDeclaration.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(NameDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassToplevelDeclaration.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(WScope.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(GlobalVarDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(WEntities.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ModuleDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(ClassDef.Visitor v) {
		for (Modifier i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	private boolean attr_attrNearestPackage_isCached = false;
	private PackageOrGlobal attr_attrNearestPackage_cache;
	public PackageOrGlobal attrNearestPackage() {
		if (!attr_attrNearestPackage_isCached) {
			attr_attrNearestPackage_cache = de.peeeq.wurstscript.attributes.AttrNearestPackage.calculate(this);
			attr_attrNearestPackage_isCached = true;
		}
		return attr_attrNearestPackage_cache;
	}
	private boolean attr_attrNearestFuncDef_isCached = false;
	private FuncDef attr_attrNearestFuncDef_cache;
	public FuncDef attrNearestFuncDef() {
		if (!attr_attrNearestFuncDef_isCached) {
			attr_attrNearestFuncDef_cache = de.peeeq.wurstscript.attributes.AttrNearestFuncDef.calculate(this);
			attr_attrNearestFuncDef_isCached = true;
		}
		return attr_attrNearestFuncDef_cache;
	}
	private boolean attr_attrNearestClassDef_isCached = false;
	private ClassDef attr_attrNearestClassDef_cache;
	public ClassDef attrNearestClassDef() {
		if (!attr_attrNearestClassDef_isCached) {
			attr_attrNearestClassDef_cache = de.peeeq.wurstscript.attributes.AttrNearestClassDef.calculate(this);
			attr_attrNearestClassDef_isCached = true;
		}
		return attr_attrNearestClassDef_cache;
	}
	private boolean attr_attrNearestClassOrModule_isCached = false;
	private ClassOrModule attr_attrNearestClassOrModule_cache;
	public ClassOrModule attrNearestClassOrModule() {
		if (!attr_attrNearestClassOrModule_isCached) {
			attr_attrNearestClassOrModule_cache = de.peeeq.wurstscript.attributes.AttrNearestClassDef.nearestClassOrModule(this);
			attr_attrNearestClassOrModule_isCached = true;
		}
		return attr_attrNearestClassOrModule_cache;
	}
	@Override public String toString() {
		String result =  "Modifiers(";
		boolean first = true;
		for (Modifier i : this ) {
			if (!first) { result +=", "; }
			if (result.length() > 1000) { result +="..."; break; }
			result += i;
			first = false;
		}
		result +=  ")";
		return result;
	}
}
