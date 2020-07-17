package pipedsl

import com.typesafe.scalalogging.Logger
import common.PrettyPrinter
import pprint.pprintln
import common.Dataflow._
import java.io.File
import java.nio.file.Files

import passes.{CanonicalizePass, SimplifyRecvPass, SplitStagesPass}
import typechecker.{BaseTypeChecker, LockChecker, SpeculationChecker, TimingTypeChecker}

object Main {
  val logger: Logger = Logger("main")

  def main(args: Array[String]): Unit = {
    logger.debug("Hello")
    val p: Parser = new Parser();
    val i: Interpreter = new Interpreter();
    if (args.length < 1) {
      throw new RuntimeException(s"Need to pass a file path as an argument")
    }
    val inputFile = new File(args(0)).toPath
    if (!Files.exists(inputFile)) {
      throw new RuntimeException(s"File $inputFile does not exist")
    }
    val r = p.parseAll(p.prog, new String(Files.readAllBytes(inputFile)));
    val prog = CanonicalizePass.run(r.get)
    //logger.info(prog.toString)
    val basetypes = BaseTypeChecker.check(prog, None)
    TimingTypeChecker.check(prog, Some(basetypes))
    val prog_recv = SimplifyRecvPass.run(prog)
    LockChecker.check(prog_recv, None)
    SpeculationChecker.check(prog_recv, Some(basetypes))
    val firstStage = SplitStagesPass.run(prog_recv.moddefs.head.body)
    PrettyPrinter.printStages(firstStage)
    PrettyPrinter.printStageGraph(prog_recv.moddefs.head.name.v, firstStage)
    val (df_ins, df_outs) = worklist(firstStage, UsedInLaterStages)
   // PrettyPrinter.printProgram(prog_recv)
  }

}