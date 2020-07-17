package pipedsl.passes

import pipedsl.common.DAGSyntax.{IfStage, PStage, PipelineEdge, SReceive, SSend}
import pipedsl.common.Syntax._
import pipedsl.common.Dataflow._
import pipedsl.common.Utilities._
import Passes.CommandPass
import pipedsl.common.{Dataflow, Syntax}
import pipedsl.common.Locks.LockState

object SplitStagesPass extends CommandPass[PStage] {

  var stgCounter: Int = 0

  def nextStageId(): Id = {
    val s = s"Stage__$stgCounter"
    stgCounter += 1
    Id(s)
  }

  override def run(c: Command): PStage = {
    val startStage = new PStage(Id("Start"))
    val termStage = splitToStages(c, startStage)
    val stages = getReachableStages(startStage)
    //Get variable lifetime information:
    //visit(startStage, (), addEdgeValues(vars_used_in, vars_used_out)(_,_))
    startStage
  }

  private def addEdgeValues(varsUsedIn: Dataflow.DFMap[Syntax.Id], varsUsedOut: Dataflow.DFMap[Syntax.Id])(stg: PStage, ignore: Unit) = {
    stg.outEdges = stg.outEdges.map(edge => {
      PipelineEdge(edge.cond, edge.to, varsUsedIn(edge.to.name))
    })
  }

  /**
   * If p may execute a 'call' command,
   * add an edge from p to the first stage in the pipeline
   * @param p - Stage to check for call statements
   * @param firstStage - First pipeline stage
   */
  private def addCallSuccs(p: PStage, firstStage: PStage): Unit = {
    p.cmds.foreach(c => callSuccsHelper(c, p, firstStage))
  }

  private def callSuccsHelper(c: Command, p:PStage, firstStage: PStage): Unit = c match {
    case CSeq(c1, c2) => callSuccsHelper(c1, p, firstStage); callSuccsHelper(c2, p, firstStage)
    case CIf(_, cons, alt) => callSuccsHelper(cons, p, firstStage); callSuccsHelper(alt, p, firstStage)
    case CCall(_, _) => {
      p.addEdgeTo(firstStage)
    }
    case CSpeculate(_, _, verify, body) => callSuccsHelper(verify, p, firstStage); callSuccsHelper(body, p, firstStage)
    case ICondCommand(_, c) => callSuccsHelper(c, p, firstStage);
    case _ => ()
  }

  /**
   *
   * @param c
   * @return
   */
  private def splitToStages(c: Command, curStage: PStage): PStage = c match {
    case CTBar(c1, c2) => {
      val lastLeftStage = splitToStages(c1, curStage)
      val nextStage = new PStage(nextStageId())
      val lastRightStage = splitToStages(c2, nextStage)
      //sequential pipeline, end of left sends to beginning of right
      lastLeftStage.addEdgeTo(nextStage)
      lastRightStage
    }
    case CSpeculate(predVar, predVal, verify, body) => {
      //For now throw this part in current stage, maybe can move it to speculative side
      curStage.addCmd(CAssign(predVar, predVal).setPos(c.pos))
      val specId = Id("__spec_" + predVar.id)
      //lock this speculative region and start speculation
      curStage.addCmd(CLockOp(specId, LockState.Acquired))
      curStage.addCmd(ISpeculate(specId, predVar).setPos(c.pos))
      //speculation must imply stage splitting on both sides
      val firstVerif = new PStage(nextStageId())
      val lastVerif = splitToStages(verify, firstVerif)
      //TODO need to check that this set of commands actually assigns to predVar
      val originalSpecVar = EVar(specId)
      originalSpecVar.typ = predVar.typ
      lastVerif.addCmd(IUpdate(specId, predVar, originalSpecVar).setPos(c.pos))
      val firstSpec = new PStage(nextStageId())
      val lastSpec = splitToStages(body, firstSpec)
      lastSpec.addCmd(CCheck(specId))
      //Need an edge to resend data on failure
      lastVerif.addEdgeTo(firstSpec, Some(EBinop(EqOp("=="), originalSpecVar, predVar)));
      val joinStage = new PStage(nextStageId())
      //release speculative lock once both branches arrive
      joinStage.addCmd(CLockOp(specId, LockState.Released))
      curStage.addEdgeTo(firstVerif)
      curStage.addEdgeTo(firstSpec)
      lastVerif.addEdgeTo(joinStage)
      lastSpec.addEdgeTo(joinStage)
      joinStage
    }
    case CIf(cond, cons, alt) => {
      val firstTrueStage = new PStage(nextStageId())
      val lastTrueStage = splitToStages(cons, firstTrueStage)
      val firstFalseStage = new PStage(nextStageId())
      val lastFalseStage = splitToStages(alt, firstFalseStage)
      val ifStage = new IfStage(nextStageId(), cond, firstTrueStage, lastTrueStage, firstFalseStage, lastFalseStage)
      curStage.addEdgeTo(ifStage)
      ifStage
    }
    case CSeq(c1, c2) => {
      val nstage = splitToStages(c1, curStage)
      splitToStages(c2, nstage)
    }
    case CEmpty => curStage
    case _ => curStage.addCmd(c); curStage
  }

  /**
   *
   * @param cond
   * @param stg
   */
  private def addConditionalExecution(cond: Expr, stg: PStage): Unit = {
    val oldCmds = stg.cmds
    val newCmds = oldCmds.foldLeft(List[Command]())((l,c)  => c match {
      case CEmpty => l
      case _ => l :+ ICondCommand(cond, c)
    })
    stg.cmds = newCmds
  }

  /**
   *
   * @param left
   * @param right
   */
  private def mergeStages(left: PStage, right: PStage): Unit = {
    left.cmds = left.cmds ++ right.cmds
    val succs = right.succs
    val preds = right.preds
    succs.foreach(s => {
      val removed = right.removeEdgesTo(s)
      removed.foreach(r => left.addEdge(r))
    })
    preds.foreach(s => {
      val removed = s.removeEdgesTo(right)
      removed.map(e => PipelineEdge(e.cond, left)).foreach(r => s.addEdge(r))
    })
  }

  /**
   * Extracts all of the statements which correspond to communication
   * with memory or external modules.
   * @param c The command to modify
   * @param nextStg The next stage in the pipeline
   * @return (the modified command, the set of send statements generated, the receive statements corresponding to those sends)
   */
  private def extractReceives(c: Command, nextStg: Id): (Command, List[SSend], List[SReceive]) = c match {
    case _ => (c, List(), List())
  }
  
}
