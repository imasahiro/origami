package blue.origami.transpiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import blue.origami.ffi.OCast;
import blue.origami.nez.ast.SourcePosition;
import blue.origami.nez.ast.Tree;
import blue.origami.rule.OFmt;
import blue.origami.transpiler.code.TCode;
import blue.origami.transpiler.code.TErrorCode;
import blue.origami.transpiler.code.TParamCode;
import blue.origami.transpiler.rule.TTypeRule;
import blue.origami.util.Handled;

public class TEnv implements TEnvTraits, TEnvApi {
	private TEnv parent;
	private HashMap<String, TEnvEntry> definedMap = null;

	public TEnv(TEnv parent) {
		this.parent = parent;
	}

	@Override
	public TEnv getParent() {
		return this.parent;
	}

	@Override
	public TEnvEntry getEntry(String name) {
		if (this.definedMap != null) {
			return this.definedMap.get(name);
		}
		return null;
	}

	@Override
	public void addEntry(String name, TEnvEntry defined) {
		if (this.definedMap == null) {
			this.definedMap = new HashMap<>();
		}
		TEnvEntry prev = this.definedMap.get(name);
		defined.push(prev);
		this.definedMap.put(name, defined);
		// System.out.printf("adding symbol %s %s on %s at env %s\n", name,
		// defined.getHandled(), defined.pop(),
		// this.getClass().getSimpleName());
		// this.hookEntry(name, defined);
	}

	@Override
	public TEnv env() {
		return this;
	}

}

class TEnvEntry implements Handled<Object> {
	private Object value;
	private TEnvEntry onstack = null;

	TEnvEntry(SourcePosition s, Object value) {
		this.value = value;
	}

	public TEnvEntry push(TEnvEntry onstack) {
		this.onstack = onstack;
		return this;
	}

	public TEnvEntry pop() {
		return this.onstack;
	}

	@Override
	public Object getHandled() {
		return this.value;
	}

	public Object setHandled(Object value) {
		Object v = this.value;
		this.value = value;
		return v;
	}
}

interface TEnvTraits {

	void addEntry(String name, TEnvEntry d);

	TEnvEntry getEntry(String name);

	TEnvTraits getParent();

	public default TEnv newEnv() {
		return new TEnv((TEnv) this);
	}

	public default void add(SourcePosition s, String name, Object value) {
		addEntry(name, new TEnvEntry(s, value));
	}

	public default void add(String name, Object value) {
		add(SourcePosition.UnknownPosition, name, value);
	}

	public default void add(Class<?> cname, Object value) {
		add(key(cname), value);
	}

	default String key(Class<?> c) {
		return c.getName();
	}

	public default <X> void set(String name, Class<X> c, X value) {
		for (TEnvEntry d = this.getEntry(name); d != null; d = d.pop()) {
			X x = d.getHandled(c);
			if (x != null) {
				d.setHandled(value);
				return;
			}
		}
		add(name, value);
	}

	public default <X, Y> Y getLocal(String name, Class<X> c, TEnvMatcher<X, Y> f) {
		for (TEnvEntry d = this.getEntry(name); d != null; d = d.pop()) {
			X x = d.getHandled(c);
			if (x != null) {
				return f.match(x, c);
			}
		}
		return null;
	}

	public default <X> X getLocal(String name, Class<X> c) {
		return this.getLocal(name, c, (d, c2) -> d);
	}

	public default <X, Y> Y get(String name, Class<X> c, TEnvMatcher<X, Y> f) {
		for (TEnvTraits env = this; env != null; env = env.getParent()) {
			Y y = env.getLocal(name, c, f);
			if (y != null) {
				return y;
			}
		}
		return null;
	}

	public default <X> X get(String name, Class<X> c) {
		return this.get(name, c, (d, c2) -> d);
	}

	public default <X> X get(Class<X> c) {
		return this.get(c.getName(), c);
	}

	public interface TEnvChoicer<X> {
		public X choice(X x, X y);
	}

	public default <X, Y> Y find(String name, Class<X> c, TEnvMatcher<X, Y> f, TEnvChoicer<Y> g, Y start) {
		Y y = start;
		for (TEnvTraits env = this; env != null; env = env.getParent()) {
			for (TEnvEntry d = env.getEntry(name); d != null; d = d.pop()) {
				X x = d.getHandled(c);
				if (x != null) {
					Y updated = f.match(x, c);
					y = g.choice(y, updated);
				}
			}
		}
		return y;
	}

	public interface TEnvBreaker<X> {
		public boolean isEnd(X x);
	}

	public default <X, Y> Y find(String name, Class<X> c, TEnvMatcher<X, Y> f, TEnvChoicer<Y> g, Y start,
			TEnvBreaker<Y> h) {
		Y y = start;
		for (TEnvTraits env = this; env != null; env = env.getParent()) {
			for (TEnvEntry d = env.getEntry(name); d != null; d = d.pop()) {
				X x = d.getHandled(c);
				if (x != null) {
					y = g.choice(y, f.match(x, c));
					if (h.isEnd(y)) {
						return y;
					}
				}
			}
		}
		return y;
	}

	public interface OListMatcher<X> {
		public boolean isMatched(X x);
	}

	public default <X> void findList(String name, Class<X> c, List<X> l, OListMatcher<X> f) {
		for (TEnvTraits env = this; env != null; env = env.getParent()) {
			for (TEnvEntry d = env.getEntry(name); d != null; d = d.pop()) {
				X x = d.getHandled(c);
				if (x != null && f.isMatched(x)) {
					l.add(x);
				}
			}
		}
	}

	public default <X> void findList(Class<?> cname, Class<X> c, List<X> l, OListMatcher<X> f) {
		findList(key(cname), c, l, f);
	}

}

interface TEnvApi {
	TEnv env();

	// protected void defineSymbol(String key, String symbol) {
	// if (!this.isDefined(key)) {
	// if (symbol != null) {
	// int s = symbol.indexOf("$|");
	// while (s >= 0) {
	// int e = symbol.indexOf('|', s + 2);
	// String skey = symbol.substring(s + 2, e);
	// // if (this.symbolMap.get(skey) != null) {
	// symbol = symbol.replace("$|" + skey + "|", this.s(skey));
	// // }
	// e = s;
	// s = symbol.indexOf("$|");
	// if (e == s) {
	// break; // avoid infinite looping
	// }
	// // System.out.printf("'%s': %s\n", key, symbol);
	// }
	// }
	// this.symbolMap.put(key, symbol);
	// }
	// }

	public default void defineSymbol(String key, String value) {
		int loc = key.indexOf(':');
		if (loc == -1) {
			String name = key;
			env().add(name, new TCodeTemplate(name, TType.tUntyped, TConsts.emptyTypes, value));
		} else {
			String name = key.substring(0, loc);
			String[] tsigs = key.substring(loc + 1).split(":");
			TType ret = this.getType(tsigs[tsigs.length - 1]);
			if (tsigs.length > 1) {
				TType[] p = new TType[tsigs.length - 1];
				for (int i = 0; i < p.length; i++) {
					p[i] = this.getType(tsigs[i]);
				}
				env().add(name, new TCodeTemplate(name, ret, p, value));
			} else {
				TTemplate t = new TCodeTemplate(name, ret, TConsts.emptyTypes, value);
				env().add(name, t);
				env().add(key, t);
			}
		}
	}

	public default TType getType(String tsig) {
		return env().get(tsig, TType.class);
	}

	public default TCode typeTree(TEnv env, Tree<?> t) {
		String name = t.getTag().getSymbol();
		TCode node = null;
		try {
			node = env.get(name, TTypeRule.class, (d, c) -> d.apply(env, t));
		} catch (TErrorCode e) {
			e.setSourcePosition(t);
			throw e;
		}
		if (node == null) {
			try {
				Class<?> c = Class.forName("blue.origami.transpiler.rule." + name);
				TTypeRule rule = (TTypeRule) c.newInstance();
				env.add(name, rule);
				return env.typeTree(env, t);
			} catch (TErrorCode e) {
				throw e;
			} catch (Exception e) {
				System.out.println("DEBUG: " + e);
				throw new TErrorCode(t, OFmt.undefined_syntax__YY0, name);
			}
		}
		node.setSourcePosition(t);
		return node;
	}

	public default TCode typeExpr(TEnv env, Tree<?> t) {
		// if (t == null) {
		// return new EmptyCode(env);
		// }
		return typeTree(env, t);
	}

	public default TCode findParamCode(TEnv env, String name, TCode... params) {
		// for (TCode p : params) {
		// if (p.isUntyped()) {
		// return new TUntypedParamCode(name, params);
		// }
		// }
		List<TTemplate> l = new ArrayList<>(8);
		env.findList(name, TTemplate.class, l, (tt) -> tt.getParamSize() == params.length);
		// ODebug.trace("l = %s", l);
		if (l.size() == 0) {
			throw new TErrorCode("undefined %s %s", name, params);
		}
		TParamCode start = new TParamCode(l.get(0), params);
		for (int i = 1; i < l.size(); i++) {
			if (start.getMatchCost() <= 0) {
				return start;
			}
			TParamCode next = new TParamCode(l.get(i), params);
			start = (next.getMatchCost() < start.getMatchCost()) ? next : start;
		}
		if (start.getMatchCost() >= OCast.STUPID) {
			// ODebug.trace("miss cost=%d %s", start.getMatchCost(), start);
			throw new TErrorCode("mismatched %s(%s)", name, params);
		}
		return start;
	}

}