package pipedsl.typechecker

import org.apache.commons.io.filefilter.FalseFileFilter
import org.graalvm.compiler.nodes.virtual.LockState
import pipedsl.common.Syntax._
import pipedsl.typechecker.Environments._
import pipedsl.common.Errors._
import pipedsl.common.Locks.{General, Released}
import pipedsl.common.Syntax
import pipedsl.typechecker.TypeChecker.TypeChecks

object FinalblocksConstraintChecker {

  def check(p :Prog) :Unit =
    {
      p.moddefs.foreach(checkModule)
    }

  private def checkModule(m :ModuleDef) :Unit =
  {
    m.except_blk match {
      case ExceptEmpty() => checkNormBody(m.body)
      case ExceptFull(args, c) =>
        checkExBody(m.body)
        checkExceptingBlock(c)
    }
  }

  private sealed trait ExnStatus
  private case object NonExn extends ExnStatus
  private case object PreCall extends ExnStatus
  private case object PostCall extends ExnStatus


  private def lub(e1 :ExnStatus, e2 :ExnStatus) :ExnStatus = (e1, e2) match {
    case (PreCall, PostCall) | (PostCall, PreCall) => PostCall
    case (x, y) if x == y => x
    case _ => throw new RuntimeException("Bad lub of exnstati")
  }

  private def after_call(e :ExnStatus) = e match {
    case NonExn => NonExn
    case _ => PostCall
  }


  private def checkExBody(c :Command) :Unit = c match {
    case CSeq(c1, c2) => checkExBody(c1); checkExBody(c2)
    case CTBar(c1, c2) => checkExBody(c1); checkExBody(c2)
    case CIf(_, cons, alt) => checkExBody(cons); checkExBody(alt)
    case CRecv(EMemAccess(mem, _, _, _, _, isAtomic), _) if isAtomic || !isLockedMemory(mem)=> throw NoCommittingWriteInBody(c.pos)
    case c@CLockOp(mem, Released, _, _, _) if c.memOpType.contains(LockWrite) || c.granularity == General => throw NoWriteReleaseInBody(c.pos)
    case CSplit(cases, default) => checkExBody(default); cases.foreach(co => checkExBody(co.body))
    case _ => ()
  }

  private def checkExceptingBlock(c :Command) :Unit = checkNoThrow(c, PreCall)

  private def checkNormBody(c: Command) :Unit = checkNoThrow(c, NonExn)


  private def checkNoThrow(c :Command, estat :ExnStatus) :ExnStatus = c match {
    case CSeq(c1, c2) => checkNoThrow(c2, checkNoThrow(c1, estat))
    case CTBar(c1, c2) => checkNoThrow(c2, checkNoThrow(c1, estat))
    case CIf(cond, cons, alt) => lub(checkNoThrow(cons, estat), checkNoThrow(alt, estat))
    case CAssign(lhs, rhs) => checkNoThrowExpr(rhs, estat)
    case CRecv(lhs, rhs) => checkNoThrowExpr(rhs, estat)
    case CSpecCall(handle, pipe, args) => after_call(estat)
    case CUpdate(newHandle, handle, args, preds, checkHandles) => args.foldLeft(estat)((es, ex) => lub(es, checkNoThrowExpr(ex, estat)))
    case CPrint(args) => args.foldLeft(estat)((es, ex) => lub(estat, checkNoThrowExpr(ex, estat)))
    case COutput(exp) => checkNoThrowExpr(exp, estat)
    case CReturn(exp) => checkNoThrowExpr(exp, estat)
    case CExpr(exp) => checkNoThrowExpr(exp, estat)
    case CLockEnd(mod) if estat == PostCall => throw MustEndBeforeCall(c.pos)
    case CLockOp(mem, op, lockType, args, ret) => args.foldLeft(estat)((es, ex) => lub(es, checkNoThrowExpr(ex, estat)))
    case CSplit(cases, default) => cases.foldLeft(checkNoThrow(default, estat))((es, co) => lub(checkNoThrow(co.body, estat), es))
    case CExcept(_) => throw IllegalThrowPlacement(c.pos)
    case _ => estat
  }

  private def checkNoThrowExpr(e :Expr, estat :ExnStatus) :ExnStatus =
    {
      def reccall(e :Expr) :ExnStatus = checkNoThrowExpr(e, estat)
      e match {
        case EIsValid(ex) => reccall(ex)
        case EFromMaybe(ex) => reccall(ex)
        case EToMaybe(ex) => reccall(ex)
        case EUop(_, ex) => reccall(ex)
        case EBinop(_, e1, e2) => lub(reccall(e1), reccall(e2))
        case ERecAccess(rec, _) => reccall(rec)
        case ERecLiteral(fields) => fields.foldLeft(estat)((es, idex) => lub(reccall(idex._2), es))
        case EMemAccess(_, index, _, _, _, _) => reccall(index)
        case EBitExtract(num, _, _) => reccall(num)
        case ETernary(_, tval, fval) => lub(reccall(tval), reccall(fval))
        case EApp(_, args) => args.foldLeft(estat)((es, ex) => lub(reccall(ex), es))
        case _ :ECall => estat match {
          case NonExn => NonExn
          case _ => PostCall
        }
        case ECast(_, exp) => reccall(exp)
        case _ => estat
      }
    }




//  private var currentMod: Id = Id("-invalid-")
//  private var ifPostCall: Boolean = false
//  private var hasCommitBlk: Boolean = false
//
//  def check(p:Prog) : Unit = {
//    val Prog(_, _, moddefs, _) = p
//    moddefs.foreach(m => checkModule(m))
//  }
//
//  private def checkModule(moduleDef: ModuleDef): Unit = {
//    currentMod = moduleDef.name
//    ifPostCall = false
//    hasCommitBlk = false
//
//    moduleDef.commit_blk match {
//      case Some(c) =>
//        hasCommitBlk = true
//        checkCommand(c)
//      case _ => ()
//    }
//
//    moduleDef.except_blk match {
//      case ExceptEmpty() => ()
//      case ExceptFull(args, c) if (hasCommitBlk == true) => checkCommand(c)
//      case ExceptFull(args, c) if (hasCommitBlk == false) =>
//        throw MalformedExceptBlock(c.pos)
//    }
//  }
//
//  private def checkCommand(command: Command): Unit = command match {
//    case CSeq(c1, c2) => val s1 = checkCommand(c1); checkCommand(c2)
//    case CTBar(c1, c2) => val s1 = checkCommand(c1); checkCommand(c2)
//    case CIf(_, cons, alt) => val s1 = checkCommand(cons); checkCommand(alt)
//    case CSplit(cases, default) =>
//      checkCommand(default)
//    case CLockOp(mem, _, _, _, _) =>
//      if (ifPostCall == true) throw MalformedLockTypes("Cannot Reserve any locks after a call")
//    case CAssign(lhs, rhs) => checkExpr(lhs); checkExpr(rhs)
//    case CRecv(lhs, rhs) => checkExpr(lhs); checkExpr(rhs)
//    case CExcept(args) => throw IllegalThrowPlacement(command.pos)
//    case _ => ()
//  }
//
//  private def checkExpr(e: Expr): Unit = e match {
//    case EIsValid(ex) => checkExpr(ex)
//    case EFromMaybe(ex) => checkExpr(ex)
//    case EToMaybe(ex) => checkExpr(ex)
//    case EUop(_, ex) => checkExpr(ex)
//    case EBinop(_, e1, e2) => checkExpr(e1); checkExpr(e2)
//    case ETernary(cond, tval, fval) => checkExpr(cond); checkExpr(tval); checkExpr(fval)
//    case EApp(_, args) => ifPostCall = true
//    case ECall(_, _, _, _) => ifPostCall = true
//  }

}