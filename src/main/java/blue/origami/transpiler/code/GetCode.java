package blue.origami.transpiler.code;

import blue.origami.nez.ast.Tree;
import blue.origami.transpiler.DataTy;
import blue.origami.transpiler.NameHint;
import blue.origami.transpiler.TCodeSection;
import blue.origami.transpiler.TEnv;
import blue.origami.transpiler.TFmt;
import blue.origami.transpiler.Ty;
import blue.origami.transpiler.VarTy;

public class GetCode extends Code1 {
	final String name;

	public GetCode(Code recv, Tree<?> nameTree) {
		super(recv);
		this.name = nameTree.getString();
		this.setSource(nameTree);
	}

	public String getName() {
		return this.name;
	}

	@Override
	public Code asType(TEnv env, Ty t) {
		if (this.isUntyped()) {
			Ty recvTy = this.asTypeAt(env, 0, Ty.tUntyped());
			if (recvTy instanceof VarTy) {
				Ty infer = Ty.tData().asParameter();
				recvTy.acceptTy(bSUB, infer, bUPDATE);
				recvTy = infer;
			}
			if (recvTy instanceof DataTy) {
				NameHint hint = env.findGlobalNameHint(env, this.name);
				if (hint == null) {
					throw new ErrorCode(this.getSource(), TFmt.undefined_name__YY0, this.name);
				}
				DataTy dt = (DataTy) recvTy;
				dt.checkGetField(this.getSource(), this.name);
				this.setType(Ty.selfTy(hint.getType(), dt));
				// Code code = this.recv.applyMethodCode(env, "getf", new
				// IntCode(DSymbol.id(this.name)),
				// hint.getDefaultValue());
				// return code.asType(env, hint.getType()).asType(env, t);
				return this.castType(env, t);
			}
			throw new ErrorCode(this.getSource(), TFmt.unsupported_operator);
		}
		return this.castType(env, t);
	}

	@Override
	public void emitCode(TEnv env, TCodeSection sec) {
		sec.pushGet(env, this);
	}

	@Override
	public void strOut(StringBuilder sb) {

	}

}
