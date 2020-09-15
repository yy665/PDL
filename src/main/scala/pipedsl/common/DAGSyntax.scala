package pipedsl.common

import pipedsl.common.Syntax._
import scala.reflect.api.Position
import scala.util.parsing.input.Positional

//TODO better name
/*
 * This file contains syntax for the intermediate language which
 * explicitly represents pipeline stages and connections between those stages.
 * This corresponds to the language with concurrent execution semantics.
 */
object DAGSyntax {

  private def channelName(from:Id, to:Id): Id = {
    Id(s"${from.v}_to_${to.v}")
  }

  case class Channel(s: Id, r: Id) {
    val name: Id = channelName(s, r)
    val sender: Id = s
    val receiver: Id = r
  }


  sealed abstract class Process(n: Id) {
    val name: Id = n
  }

  sealed trait StageCommand extends Positional

  case class SAssign(lhs: Expr, rhs: Expr) extends StageCommand
  case class SIf(cond: Expr, cons: List[StageCommand], alt: List[StageCommand]) extends StageCommand
  case class SCall(id: Id, args: List[Expr]) extends StageCommand
  case class SReceive(g: Option[Expr], into: EVar, s: Channel) extends StageCommand
  case class SSend(g: Option[Expr], from: EVar, d: Channel) extends StageCommand
  case class STermStage(g: Option[Expr], vals: List[EVar], dest: List[PStage]) extends StageCommand
  case class SOutput(exp: Expr) extends StageCommand
  case class SReturn(exp: Expr) extends StageCommand
  case class SExpr(exp: Expr) extends StageCommand
  case object SEmpty extends StageCommand

  case class PipelineEdge(condSend: Option[Expr], condRecv: Option[Expr],
    from: PStage, to:PStage, values: Set[Id] = Set())

  def addValues(vals: Set[Id], e: PipelineEdge): PipelineEdge = {
    PipelineEdge(e.condSend, e.condRecv, e.from, e.to, e.values ++ vals)
  }

  /**
   *
   * @param n
   */
  class PStage(n:Id) extends Process(n) {

    //Any outgoing communication edge, including normal pipeline flow, calls and communication with memories
    private var edges: Set[PipelineEdge] = Set()

    def allEdges: Set[PipelineEdge] = edges

    def outEdges: Set[PipelineEdge] = {
      edges.filter(e => e.from == this)
    }
    def inEdges: Set[PipelineEdge] = {
      edges.filter(e => e.to == this)
    }

    //Set of combinational commands
    var cmds: List[Command] = List()
    //Successors for dataflow based computation. This encodes all dataflow dependencies.
    def succs: Set[PStage] = {
      edges.filter(e => e.from == this).map(e => e.to)
    }
    //Successors for dataflow based computation. This encodes all dataflow dependencies.
    def preds: Set[PStage] = {
      edges.filter(e => e.to == this).map(e => e.from)
    }

    /**
     *
     * @param other
     * @param condSend
     */
    def addEdgeTo(other: PStage, condSend: Option[Expr] = None, condRecv: Option[Expr] = None,
        vals: Set[Id] = Set()): Unit = {
      val edge = PipelineEdge(condSend, condRecv, this, other, vals)
      addEdge(edge)
    }

    /**
     *
     * @param edge
     */
    def addEdge(edge: PipelineEdge): Unit = {
      val other = if (edge.to == this) { edge.from } else { edge.to }
      this.edges = this.edges + edge
      other.edges = other.edges + edge
    }

    def setEdges(edges: Set[PipelineEdge]): Unit = {
      val toRemove = this.edges
      toRemove.foreach(e => this.removeEdge(e))
      edges.foreach(e => this.addEdge(e))
    }

    private def removeEdge(edge: PipelineEdge): Unit = {
      val other = if (edge.to == this) { edge.from } else { edge.to }
      this.edges = this.edges - edge
      other.edges = other.edges - edge
    }

    /**
     *
     * @param other
     * @return
     */
    def removeEdgesTo(other: PStage): Set[PipelineEdge] = {
      other.edges = other.edges.filter(e => e.from != this)
      val (otherEdges, notOther) = this.edges.partition(e => e.to == other)
      this.edges = notOther
      otherEdges
    }

    /**
     *
     * @param cmd
     */
    def addCmd(cmd: Command): Unit = {
      this.cmds = this.cmds :+ cmd
    }
  }


  class SpecStage(n: Id, val specVar: EVar, val specVal: Expr,
    val verifyStages: List[PStage],
    val specStages: List[PStage],
    val joinStage: PStage) extends PStage(n) {

    this.addEdgeTo(verifyStages.head)
    this.addEdgeTo(specStages.head)
    specStages.last.addEdgeTo(joinStage)
    verifyStages.last.addEdgeTo(joinStage)
    //used only for computing dataflow merge function
    //does not get synthesized
    this.addEdgeTo(joinStage)

    def predId = Id("__pred__" + specVar.id.v)
    def specId = Id("__spec__" + specVar.id.v)

    val predVar = EVar(predId)
    predVar.typ = specVar.typ
    //extract prediction to variable
    this.addCmd(CAssign(predVar, specVal))
    //set pred(specId) = prediction
    this.addCmd(ISpeculate(specId, specVar, predVar))
    //At end of verification update the predction success
    verifyStages.last.addCmd(IUpdate(specId, specVar, predVar))
    //At end of speculation side check whether or not pred(specId) == specVar
    specStages.last.addCmd(ICheck(specId, specVar))
  }

  /**
   *
   * @param n
   * @param cond
   * @param trueStages
   * @param falseStages
   * @param joinStage
   */
  class IfStage(n: Id, val cond: Expr, var trueStages: List[PStage],
    var falseStages: List[PStage], val joinStage: PStage) extends PStage(n) {

    val condVar = EVar(Id("__cond" + n.v))
    condVar.typ = Some(TBool())
    condVar.id.typ = condVar.typ
    val notCond = EUop(NotOp(), condVar)
    this.addCmd(CAssign(condVar, cond))
    this.addEdgeTo(trueStages.head, condSend = Some(condVar))
    this.addEdgeTo(falseStages.head, condSend = Some(notCond))
    trueStages.last.addEdgeTo(joinStage, condRecv =  Some(condVar))
    falseStages.last.addEdgeTo(joinStage, condRecv = Some(notCond))
  }

  class PMemory(n: Id, t: TMemType) extends Process(n) {
    val mtyp: TMemType = t
  }
  class PBlackBox(n: Id, t: TModType) extends Process(n) {
    val mtyp: TModType = t
  }

  sealed trait Message
  case class MRead(src: EVar) extends Message
  case class MWrite(dest: EVar, value: EVar) extends Message
}
