package pipedsl.typechecker

import pipedsl.common.Errors.{IllegalLockAcquisition, IllegalLockRelease, InvalidLockState, UnexpectedCase}
import pipedsl.common.Locks._
import pipedsl.common.Syntax
import pipedsl.common.Syntax._
import pipedsl.typechecker.Environments._
import pipedsl.typechecker.TypeChecker.TypeChecks

/**
 * This checks that all reads and writes to memories
 * only happen when appropriate.
 * - Checks: Whenever a memory is read or written, the lock for that memory has been acquired
 * - Checks: That all locks are released (or never acquired) by the end of the program
 */
object LockChecker extends TypeChecks[Id, LockState] {

  override def emptyEnv(): Environment[Id, LockState] = Environments.EmptyLockEnv

  //Functions can't interact with locks or memories right now.
  //Could add that to the function types explicitly to be able to check applications
  override def checkFunc(f: FuncDef, env: Environment[Id, LockState]): Environment[Id, LockState] = env

  override def checkModule(m: ModuleDef, env: Environment[Id, LockState]): Environment[Id, LockState] = {
    val nenv = m.modules.foldLeft[Environment[Id, LockState]](env)( (e, m) => m.typ match {
      case TMemType(_, _, _, _) => e.add(m.name, Free)
      case TModType(_, _, _, _) => e.add(m.name, Free)
      case _ => throw UnexpectedCase(m.pos)
    })
    checkLockRegions(m.body, nenv)
    val finalenv = checkCommand(m.body, nenv)
    //At end of execution all acquired or reserved locks must be released
    finalenv.getMappedKeys().foreach(id => {
      finalenv(id).matchOrError(m.pos, id.v, Released) { case Free | Released => () }
    })
    env //no change to lock map after checking module
  }

  def checkLockRegions(c: Command, env: Environment[Id, LockState]): Environment[Id, LockState] = c match {
    case CSeq(c1, c2) =>
      val e1 = checkLockRegions(c1, env)
      checkLockRegions(c2, e1)
    case CTBar(c1, c2) => val e1 = checkLockRegions(c1, env)
      checkLockRegions(c2, e1)
    case CIf(_, cons, alt) =>
      val lt = checkLockRegions(cons, env)
      val lf = checkLockRegions(alt, env)
      val envfree = env.filter(Free)
      val ltfree = lt.filter(Free)
      //All locks that were Free before T branch but aren't anymore
      val ltacq = envfree -- ltfree.getMappedKeys()
      val lffree = lf.filter(Free)
      //All locks that were Free before F branch but aren't anymore
      val lfacq = envfree -- lffree.getMappedKeys()
      //If any locks were newly acquired/reserved in both branches, error
      if (ltacq.getMappedKeys().intersect(lfacq.getMappedKeys()).nonEmpty) {
        throw IllegalLockAcquisition(c.pos)
      }
      //Merge matching states, merge Free|Released states to Released, error others
      lt.intersect(lf) //real merge logic lives inside Envrionments.LockState
    case CLockStart(mod) => env.add(mod, Acquired)
    case CLockEnd(mod) => env.add(mod, Released)
      //can only reserve locks insisde of the relevant lock region
      //other lock ops can be outside of this pass
    case CLockOp(mem, op) if op == Reserved =>
      if (env(mem.id) != Acquired) {
        throw InvalidLockState(c.pos, mem.id.v, env(mem.id), Acquired)
      }
      env
    case Syntax.CEmpty => env
    case _ => env
  }

  def checkCommand(c: Command, env: Environment[Id, LockState]): Environment[Id, LockState] = c match {
    case CSeq(c1, c2) => {
      val l1 = checkCommand(c1, env)
      checkCommand(c2, l1)
    }
    case CTBar(c1, c2) => {
      val l1 = checkCommand(c1, env)
      checkCommand(c2, l1)
    }
    case CSplit(cases, default) => {
      val df = checkCommand(default, env)
      cases.foldLeft(df)((fenv, cs) => {
        fenv.intersect(checkCommand(cs.body, env))
      })
    }
    case CIf(_, cons, alt) => {
      val lt = checkCommand(cons, env)
      val lf = checkCommand(alt, env)
      val envfree = env.filter(Free)
      val ltfree = lt.filter(Free)
      //All locks that were Free before T branch but aren't anymore
      val ltacq = envfree -- ltfree.getMappedKeys()
      val lffree = lf.filter(Free)
      //All locks that were Free before F branch but aren't anymore
      val lfacq = envfree -- lffree.getMappedKeys()
      //If any locks were newly acquired/reserved in both branches, error
      if (ltacq.getMappedKeys().intersect(lfacq.getMappedKeys()).nonEmpty) {
        throw IllegalLockAcquisition(c.pos)
      }
      //Merge matching states, merge Free|Released states to Released, error others
      lt.intersect(lf) //real merge logic lives inside Envrionments.LockState
    }
    case CSpeculate(predVar, predVal, verify, body) => {
      //TODO same thing as CIF todo - this one's probably wrong
      val lverif = checkCommand(verify, env)
      val lvrel = lverif.filter(Released)
      checkCommand(body, env) //speculative body must check in the original env
      val lfinal = checkCommand(body, lverif) //but final is as if executing in serial
      val lfinalrel = lfinal.filter(Released)

      if (lvrel.getMappedKeys() != lfinalrel.getMappedKeys()) {
        throw IllegalLockRelease(body.pos);
      }
      lfinal
    }
    case CAssign(lhs, rhs) => (lhs, rhs) match {
      case (_, EMemAccess(mem,_)) => env(mem).matchOrError(rhs.pos, mem.v, Acquired) { case Acquired => env }
      case _ => env
    }
    case CRecv(lhs, rhs) => (lhs, rhs) match {
        case (EMemAccess(mem,_), _) => env(mem).matchOrError(lhs.pos, mem.v, Acquired) { case Acquired => env }
        case (_, EMemAccess(mem,_)) => env(mem).matchOrError(rhs.pos, mem.v, Acquired) { case Acquired => env }
        case (_, ECall(mod,_)) => env(mod).matchOrError(rhs.pos, mod.v, Acquired) { case Acquired => env }
        case _ => throw UnexpectedCase(c.pos)
      }
      //TODO don't just use id
    case CLockOp(mem, op) => env.add(mem.id, op) //logic inside the lock environment class
    case _ => env
  }
  override def checkCircuit(c: Circuit, env: Environment[Id, LockState]): Environment[Id, LockState] = env
}
