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

  private def channelName(from:Process, to:Process): Id = {
    Id(s"${from.name}_to_${to.name}")
  }

  case class Channel(s: Process, r: Process) {
    val name: Id = channelName(s, r)
    val sender: Process = s
    val receiver: Process = r
  }

  sealed abstract class Process(n: Id) {
    val name: Id = n
  }

  sealed trait StageCommand extends Positional

  case class SAssign(lhs: Expr, rhs: Expr) extends StageCommand
  case class SCall(id: Id, args: List[Expr]) extends StageCommand
  case class SReceive(g: Option[EVar], into: EVar, s: Channel) extends StageCommand
  case class SSend(g: Option[EVar], from: EVar, d: Channel) extends StageCommand
  case class SOutput(exp: Expr) extends StageCommand
  case class SReturn(exp: Expr) extends StageCommand
  case class SExpr(exp: Expr) extends StageCommand
  case object SEmpty extends StageCommand

  /**
   * Convert the commands, which have no
   * control or timing structure into our IR.
   * These are all essentially No-Ops
   * @param c The Command to convert
   * @return The new StageCommand representation.
   */
  def toStageCmd(c: Command): StageCommand = c match {
    case CAssign(lhs, rhs) => SAssign(lhs, rhs)
    case CCall(id, args) => SCall(id, args)
    case COutput(exp) => SOutput(exp)
    case CReturn(exp) => SReturn(exp)
    case CExpr(exp) => SExpr(exp)
    case Syntax.CEmpty => SEmpty
  }

  class PStage(n:Id, var preds:List[SReceive], var body: List[StageCommand], var succs: List[SSend]) extends Process(n)

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
