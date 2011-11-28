//generated by parseq
package de.peeeq.wurstscript.jassAst;

class JassStatementsImpl extends JassStatements implements JassAstElementIntern {
 	private JassAstElement parent;
	public JassAstElement getParent() { return parent; }
	public void setParent(JassAstElement parent) {
		if (parent != null && this.parent != null) { 			throw new Error("Parent of " + this + " already set: " + this.parent + "\ntried to change to " + parent); 		}
		this.parent = parent;
	}

	protected void other_setParentToThis(JassStatement t) {
		((JassAstElementIntern) t).setParent(this);
	}
	protected void other_clearParent(JassStatement t) {
		((JassAstElementIntern) t).setParent(null);
	}
	@Override public void accept(JassStmtIf.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassStmtLoop.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassStatements.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassFunctions.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassFunction.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassProg.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public void accept(JassStatement.Visitor v) {
		for (JassStatement i : this ) {
			i.accept(v);
		}
		v.visit(this);
	}
	@Override public String toString() {
		String result =  "JassStatements(";
		boolean first = true;
		for (JassStatement i : this ) {
			if (!first) { result +=", "; }
			if (result.length() > 1000) { result +="..."; break; }
			result += i;
			first = false;
		}
		result +=  ")";
		return result;
	}
}
