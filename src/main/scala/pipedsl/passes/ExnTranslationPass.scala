/* ExnTranslationPass.scala */
package pipedsl.passes

import pipedsl.common.Syntax._
import pipedsl.passes.Passes.{ModulePass, ProgPass}
import pipedsl.typechecker.BaseTypeChecker.replaceNamedType

class ExnTranslationPass extends ModulePass[ModuleDef] with ProgPass[Prog]{
  private var exnArgCallMap = Map[Int, EVar]()
  private var exnArgApplyMap = Map[Id, EVar]()

  private val localExnFlag = EVar(Id("_localExnFlag"))

  override def run(m: ModuleDef): ModuleDef =
  {
    if(is_excepting(m)){
      val new_m = addExnVars(m)
      new_m.name.typ = m.name.typ
      val modified_exnblk = m.except_blk.map(convertExnArgsId)
      createNewStg(new_m.copy(body = new_m.body, commit_blk = new_m.commit_blk, except_blk = modified_exnblk).copyMeta(m))
    } else {
      m
    }
  }

  override def run(p: Prog): Prog = p.copy(moddefs = p.moddefs.map(m => run(m).copyMeta(m)))

  def addExnVars(m: ModuleDef): ModuleDef =
  {
    localExnFlag.typ = Some(TBool())
    localExnFlag.id.typ = localExnFlag.typ
    val fixed_except = m.except_blk match {
      case ExceptFull(args, c) =>
        var arg_count = 0
        args.foreach(arg => {
          arg.typ match {
            case Some(t: Type) =>
              val newExnArgId = Id("_exnArg_"+arg_count.toString())
              newExnArgId.typ = Some(t)
              val newExnArgVar = EVar(newExnArgId)
              newExnArgVar.typ = Some(t)
              exnArgCallMap = exnArgCallMap + (arg_count -> newExnArgVar)
              exnArgApplyMap = exnArgApplyMap + (arg -> newExnArgVar)
              arg_count += 1
            case _ =>
              arg_count += 1
          }
        })
      case ExceptEmpty() => CEmpty()
    }

    m.copy(body = CSeq(ICheckExn(), convertBody(m.body)), commit_blk = m.commit_blk, except_blk = m.except_blk).copyMeta(m)
  }

  def convertBody(c: Command): Command = {
    c match {
      case CSeq(c1, c2) => CSeq(convertBody(c1), convertBody(c2)).copyMeta(c)
      case CIf(cond, cons, alt) => CIf(cond, convertBody(cons), convertBody(alt)).copyMeta(c)
      case CTBar(c1, c2) => CTBar(convertBody(c1), CSeq(ICheckExn(), convertBody(c2))).copyMeta(c)
      case CSplit(cases, default) =>
        val newCases = cases.map(c => CaseObj(c.cond, convertBody(c.body)))
        CSplit(newCases, convertBody(default)).copyMeta(c)
      case CExcept(args) =>
        val setLocalErrFlag = CAssign(localExnFlag, EBool(true)).copyMeta(c)
        var arg_count = 0
        val setArgs: Command = args.foldLeft[Command](CSeq(setLocalErrFlag, CEmpty()))((c, arg) => {
          arg.typ match {
            case Some(t: Type) =>
              val translatedVar = exnArgCallMap.getOrElse(arg_count, EVar(Id("_Undefined_")))
              val setCurrArg = CAssign(translatedVar, arg).copyMeta(c)
              arg_count += 1
              CSeq(c, setCurrArg).copyMeta(c)
            case _ => c
          }
      })
        setArgs
      case _ => c
    }
  }

  def convertExnArgsId(c: Command): Command = {
    c match {
      case CSeq(c1, c2) => CSeq(convertExnArgsId(c1), convertExnArgsId(c2)).copyMeta(c)
      case CIf(cond, cons, alt) => CIf(convertExnArgsId(cond), convertExnArgsId(cons), convertExnArgsId(alt)).copyMeta(c)
      case CTBar(c1, c2) => CTBar(convertExnArgsId(c1), convertExnArgsId(c2)).copyMeta(c);
      case CSplit(cases, default) =>
        val newCases = cases.map(c => CaseObj(c.cond, convertExnArgsId(c.body)))
        CSplit(newCases, convertExnArgsId(default)).copyMeta(c)
      case CAssign(v, exp) =>
        val newv = exnArgApplyMap.getOrElse(v.id, EVar(v.id)).setPos(c.pos)
        CAssign(newv, convertExnArgsId(exp)).copyMeta(c)
      case CExpr(exp) =>
        CExpr(convertExnArgsId(exp)).copyMeta(c)
      case CPrint(args) =>
        val newArgs = args.map(convertExnArgsId)
        CPrint(newArgs).copyMeta(c)
      case _ => c
    }
  }

  def convertExnArgsId(e: Expr): Expr = {
    e match {
      case EIsValid(ex) => EIsValid(convertExnArgsId(ex)).setPos(e.pos)
      case EFromMaybe(ex) => EFromMaybe(convertExnArgsId(ex)).setPos(e.pos)
      case EToMaybe(ex) => EToMaybe(convertExnArgsId(ex)).setPos(e.pos)
      case EUop(op, ex) => EUop(op, convertExnArgsId(ex)).setPos(e.pos)
      case EBinop(op, e1, e2) => EBinop(op, convertExnArgsId(e1), convertExnArgsId(e2)).setPos(e.pos)
      case EApp(func, args) => {
        val newArgs = args.foldLeft(List[Expr]())((l, arg) => {
          arg match {
            case EVar(id) =>
              val newv = exnArgApplyMap.getOrElse(id, arg).setPos(e.pos)
              l :+ newv
            case _ => l :+ arg
          }
        })
        EApp(func, newArgs).setPos(e.pos)
      }
      case ECall(mod, name, args, isAtomic) => {
        val newArgs = args.foldLeft(List[Expr]())((l, arg) => {
          arg match {
            case EVar(id) =>
              val newv = exnArgApplyMap.getOrElse(id, arg).setPos(e.pos)
              l :+ newv
            case _ => l :+ arg
          }
        })
        ECall(mod, name, newArgs, isAtomic).setPos(e.pos)
      }
      case ECast(ctyp, e) => ECast(ctyp, convertExnArgsId(e)).setPos(e.pos)
      case EVar(id) => exnArgApplyMap.getOrElse(id, e).setPos(e.pos)
      case _ => e
    }
  }

  def createNewStg(m: ModuleDef): ModuleDef = {
    val commit_stmts = m.commit_blk match {
      case Some(c) => c
      case _ => CEmpty()
    }
    val except_stmts = m.except_blk match {
      case ExceptFull(_, c) => c
      case ExceptEmpty() => CEmpty()
    }

    val abortStmts = m.modules.foldLeft(CSeq(CEmpty(), CEmpty()))((c, mod) =>
      mod.typ match {
        case TLockedMemType(mem, _, _) => CSeq(c, IAbort(mod.name))
        case TMemType(_, _, _, _, _, _) => CSeq(c, IAbort(mod.name))
        case _ => CSeq(c, CEmpty())
      })

    println(abortStmts)
    val initLocalErrFlag = CAssign(localExnFlag, EBool(false)).copyMeta(m.body)
    val setGlobalExnFlag = ISetGlobalExnFlag(true)
    val unsetGlobalExnFlag = ISetGlobalExnFlag(false)

    val clearSpecTable = if (m.maybeSpec) ISpecClear() else CEmpty()
    val clearFifos = IFifoClear()

    val exnRollbackStmts = CTBar(setGlobalExnFlag, CSeq(CSeq(abortStmts, clearSpecTable), clearFifos))
    val translatedExnBlock = CTBar(exnRollbackStmts, CSeq(except_stmts, unsetGlobalExnFlag))
    val finalBlocks = CIf(localExnFlag, translatedExnBlock, commit_stmts)
    val newBody = CSeq(initLocalErrFlag, CSeq(m.body, finalBlocks))

    //TODO require memory or module types
    m.copy(body = newBody, commit_blk = None, except_blk = m.except_blk).copyMeta(m)
  }
}