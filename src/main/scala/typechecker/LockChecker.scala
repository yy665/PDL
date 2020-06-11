package pipedsl.typechecker

import Environments._
import TypeChecker.TypeChecks
import pipedsl.common.Locks.LockState._
import pipedsl.common.Syntax._

object LockChecker extends TypeChecks[LockState] {

  override def emptyEnv(): Environment[LockState] = Environments.EmptyLockEnv

  //Functions can't interact with locks or memories right now.
  //Could add that to the function types explicitly to be able to check applications
  override def checkFunc(f: FuncDef, env: Environment[LockState]): Environment[LockState] = env

  override def checkModule(m: ModuleDef, env: Environment[LockState]): Environment[LockState] = {
    val nenv = m.modules.foldLeft[Environment[LockState]](env)( (e, m) => m.typ match {
      case TMemType(_, _) => e.add(m.name, Free)
      case TModType(_, _) => e.add(m.name, Free)
    })
    checkCommand(m.body, nenv)
  }

  //This assumes that memory access only occur in recv statements
  //And that they've been pulled out so that all receives are in the
  //form: v <- mem[idx] or mem[idx] <- v
  def checkCommand(c: Command, env: Environment[LockState]): Environment[LockState] = c match {
    case CSeq(c1, c2) => {
      val l1 = checkCommand(c1, env)
      checkCommand(c2, l1)
    }
    case CTBar(c1, c2) =>{
      val l1 = checkCommand(c1, env)
      checkCommand(c2, l1)
    }
    case CIf(_, cons, alt) => {
      val lt = checkCommand(cons, env)
      val lf = checkCommand(alt, env)
      lt.intersect(lf)
    }
    case CRecv(lhs, rhs) => (lhs, rhs) match {
        case (EMemAccess(mem,_), _) => env(mem).matchOrError(lhs.pos, Acquired) { case Acquired => env }
        case (_, EMemAccess(mem,_)) => env(mem).matchOrError(rhs.pos, Acquired) { case Acquired => env }
      }
    case CLockOp(mem, op) => env.add(mem, op)
    case _ => env
  }
  override def checkCircuit(c: Circuit, env: Environment[LockState]): Environment[LockState] = env
}
