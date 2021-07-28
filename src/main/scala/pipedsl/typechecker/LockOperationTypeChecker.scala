package pipedsl.typechecker;

import pipedsl.common.Errors.{IllegalMemoryAccessOperation, MalformedLockTypes, UnexpectedCase}
import pipedsl.common.Locks.{General, LockGranularity, Specific}
import pipedsl.common.Syntax._

/**
 * A class to check whether a program's lock types are correct, and to check the memory accesses are correct
 * given those lock types, i.e. Read and Write. Memory reads require read locks, and memory writes
 * require write locks
 *
 * This typechecker will also annotate memory accesses and lock operations with LockType info that are not
 * already annotated
 */
class LockOperationTypeChecker(val memGranularityMap:Map[Id, Map[Id, LockGranularity]]) {

  private var memLockOpAnnotationMap: Map[Id, Map[LockArg, LockType]] = Map().withDefaultValue(Map())
  private var currentMod: Id = Id("-invalid-")

  private def getLockAnnotationMap: Map[LockArg, LockType] = {
    memLockOpAnnotationMap(currentMod)
  }

  private def updateLockAnnotationMap(lid: LockArg, lt: LockType): Unit = {
    memLockOpAnnotationMap = memLockOpAnnotationMap.updated(currentMod, getLockAnnotationMap + (lid -> lt))
  }

  private def getLockGranularityMap: Map[Id, LockGranularity] = {
    memGranularityMap(currentMod)
  }

  /**
   * Checks if the program has correct lock types
   * as defined in the comment at the top of this class.
   * @param p the program to be checked
   * @throws MalformedLockTypes error if lock types are not well formed
   * @throws IllegalMemoryAccessOperation error if access memory without correct READ or WRITE capability
   */
  def check(p:Prog) : Unit = {
    val Prog(_, moddefs, _) = p
    moddefs.foreach(m => checkModule(m))
  }

  private def checkModule(moduleDef: ModuleDef): Unit = {
    currentMod = moduleDef.name
    checkCommand(moduleDef.body)
  }

  private def checkCommand(command: Command): Unit = command match {
    case CSeq(c1, c2) => checkCommand(c1); checkCommand(c2)
    case CTBar(c1, c2) => checkCommand(c1); checkCommand(c2)
    case CIf(_, cons, alt) => checkCommand(cons); checkCommand(alt)
    case CSplit(cases, default) =>
      cases.foreach(c => checkCommand(c.body))
      checkCommand(default)
    case c@CLockOp(mem, _, lockType) =>
      if (c.granularity == General && lockType.isDefined)
        throw MalformedLockTypes("Can only specify lock type for address specific locks")
      if (lockType.isDefined) {
        if (getLockAnnotationMap.contains(mem) && getLockAnnotationMap(mem) != lockType.get) {
          throw MalformedLockTypes("Only one lock type per an address specific lock is allowed")
        }
        c.memOpType = lockType
        mem.memOpType = lockType
        updateLockAnnotationMap(mem, lockType.get)
      } else if (c.granularity == Specific) {
        if (!getLockAnnotationMap.contains(mem)) {
          throw MalformedLockTypes("Address specific locks must have an associated lock type")
        }
        c.lockType = getLockAnnotationMap.get(mem)
        c.memOpType = getLockAnnotationMap.get(mem)
        mem.memOpType = c.memOpType
      }
      //general locks stay as none
    case c@CRecv(lhs, rhs, _) => (lhs, rhs) match {
      case (e@EMemAccess(mem, index, _), _) =>
        //check if it exists and is lock read, otherwise is ok. If it is None, it means it is general
        getLockAnnotationMap.get(LockArg(mem, Some(index.asInstanceOf[EVar]))) match {
          case Some(LockRead) => throw IllegalMemoryAccessOperation(c.pos)
          case _ =>
            e.granularity = getLockGranularityMap(mem)
            e.memOpType = Some(LockWrite)
        }
      case (_, e@EMemAccess(mem, index, _)) =>
        getLockAnnotationMap.get(LockArg(mem, Some(index.asInstanceOf[EVar]))) match {
          case Some(LockWrite) => throw IllegalMemoryAccessOperation(c.pos)
          case _ =>
            e.memOpType = Some(LockRead)
            e.granularity = getLockGranularityMap(mem)
        }
      case (_, ECall(_, _)) =>
      case _ => throw UnexpectedCase(c.pos)
    }
    case CAssign(_, rhs, _) => checkExpr(rhs)
    case _ =>
  }

  private def checkExpr(e: Expr): Unit = e match {
    case EUop(_, ex) => checkExpr(ex)
    case EBinop(_, e1, e2) =>
      checkExpr(e1)
      checkExpr(e2)
    case e@EMemAccess(mem, index, _) =>
      //Only want to cast  to variable if it is specific, otherwise previous
      //typechecking pass would have errored out
      if (getLockGranularityMap(mem) == General) {
        e.memOpType = Some(LockRead)
        e.granularity = General
      } else {
        getLockAnnotationMap.get(LockArg(mem, Some(index.asInstanceOf[EVar]))) match {
          case Some(LockWrite) => throw IllegalMemoryAccessOperation(e.pos)
          case _ =>
            e.memOpType = Some(LockRead)
            e.granularity = Specific
        }
      }
    case ETernary(cond, tval, fval) =>
      checkExpr(cond)
      checkExpr(tval)
      checkExpr(fval)
    case EApp(_, args) => args.foreach(a => checkExpr(a))
    case ECall(_, args) => args.foreach(a => checkExpr(a))
    case ECast(_, exp) => checkExpr(exp)
    case _ =>
  }
}

