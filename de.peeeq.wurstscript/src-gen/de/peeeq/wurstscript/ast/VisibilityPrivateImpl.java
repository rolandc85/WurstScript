//generated by parseq
package de.peeeq.wurstscript.ast;

class VisibilityPrivateImpl implements VisibilityPrivate, AstElementIntern {
	VisibilityPrivateImpl(WPos source) {
		if (source == null) throw new IllegalArgumentException();
		((AstElementIntern)source).setParent(this);
		this.source = source;
	}

	private AstElement parent;
	public AstElement getParent() { return parent; }
	public void setParent(AstElement parent) {
		if (parent != null && this.parent != null) { 			throw new Error("Parent of " + this + " already set: " + this.parent + "\ntried to change to " + parent); 		}
		this.parent = parent;
	}

	private WPos source;
	public void setSource(WPos source) {
		if (source == null) throw new IllegalArgumentException();
		((AstElementIntern)this.source).setParent(null);
		((AstElementIntern)source).setParent(this);
		this.source = source;
	} 
	public WPos getSource() { return source; }

	public AstElement get(int i) {
		switch (i) {
			case 0: return source;
			default: throw new IllegalArgumentException("Index out of range: " + i);
		}
	}
	public int size() {
		return 1;
	}
	public VisibilityPrivate copy() {
		return new VisibilityPrivateImpl(source.copy());
	}
	@Override public void accept(CompilationUnit.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(Modifiers.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(FunctionDefinition.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(TypeDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(FuncDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ClassSlots.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(AstElementWithModifier.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ConstructorDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(PackageOrGlobal.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(VarDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(JassGlobalBlock.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(WPackage.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ClassOrModule.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(Modifier.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(WEntity.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ClassSlot.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ClassMember.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(NativeType.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(NativeFunc.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(TopLevelDeclaration.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(VisibilityPrivate.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(NameDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(JassToplevelDeclaration.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(WScope.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(GlobalVarDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(WEntities.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ModuleDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(ClassDef.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public void accept(VisibilityModifier.Visitor v) {
		source.accept(v);
		v.visit(this);
	}
	@Override public <T> T match(Modifier.Matcher<T> matcher) {
		return matcher.case_VisibilityPrivate(this);
	}
	@Override public void match(Modifier.MatcherVoid matcher) {
		matcher.case_VisibilityPrivate(this);
	}

	@Override public <T> T match(VisibilityModifier.Matcher<T> matcher) {
		return matcher.case_VisibilityPrivate(this);
	}
	@Override public void match(VisibilityModifier.MatcherVoid matcher) {
		matcher.case_VisibilityPrivate(this);
	}

	@Override public String toString() {
		return "VisibilityPrivate(" + source+")";
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
}
