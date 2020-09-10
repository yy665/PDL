package pipedsl.codegen

import pipedsl.common.BSVSyntax._
import pipedsl.common.DAGSyntax.{PStage, PipelineEdge}
import pipedsl.common.Errors.UnexpectedCommand
import pipedsl.common.Syntax._
import pipedsl.common.Utilities.log2

/**
 * Given a list of pipeline stages that describe a pipeline module
 * and the interface to that module, generate a
 * BSV Module that represents the entire pipeline.
 *
 * This comment and top-level function are still TODO,
 * we need to refactor a bit.
 * @param firstStage - The first stage in the pipeline that accepts inputs from
 *                   a single channel (unlike the other stages).
 * @param otherStages - The full remaining list of pipeline stages.
 * @return - The BSV Module that represents this pipeline.
 */
class BluespecGeneration(val mod: ModuleDef, val firstStage: PStage, val otherStages: List[PStage]) {

  private val lockType = "Lock"
  private val lockModuleName = "mkLock"
  private val lockLib = "Locks"
  private val memLib = "Memories"
  private val regType = "Reg"
  private val regModuleName = "mkReg"
  private val fifoType = "FIFOF"
  private val fifoLib = "FIFOF"
  private val fifoModuleName = "mkFIFOF"
  private val threadIdName = "_threadID"
  private val startMethodName = "start"

  type EdgeInfo = Map[PipelineEdge, BStructDef]
  type ModInfo = Map[Id, BVar]
  type LockInfo = Map[Id, BVar]
  type StageTypes = Map[PStage, BInterfaceDef]
  //Normal Map that special cases when checking the first Stage as part of the key
  //Since The First stage only has 1 incoming 'edge'
  class EdgeMap(firstStage: PStage, firstStageInput: BStruct, edges: EdgeInfo) {

    private val realMap: Map[PipelineEdge, BStruct] = edges map { case (k, v) => (k, v.typ) }

    def apply(e: PipelineEdge): BStruct = {
      realMap.get(e) match {
        case Some(b) => b
        case None if e.to == firstStage => firstStageInput
        case _ => throw new RuntimeException("Missing edge struct type")
      }
    }

  }

  private val threadIdVar = BVar(threadIdName, getThreadIdType)
  //Data types for passing between stages
  private val edgeStructInfo = getEdgeStructInfo(otherStages, addTId = true)
  //First stage should have exactly one input edge by definition
  private val firstStageStruct = getEdgeStructInfo(List(firstStage), addTId = false).values.head
  //This map returns the correct struct type based on the destination stage
  private val edgeMap = new EdgeMap(firstStage, firstStageStruct.typ, edgeStructInfo)
  //Generate map from existing module parameter names to BSV variables
  private val modParams: ModInfo = mod.modules.foldLeft[ModInfo](Map())((vars, m) => {
    vars + (m.name -> BVar(m.name.v, toBSVType(m.typ)))
  })
  //mapping memory ids to their associated locks
  private val lockParams: LockInfo = mod.modules.foldLeft[LockInfo](Map())((locks, m) => {
    locks + (m.name -> BVar(genLockName(m.name), getLockType()))
  })
  //Generate a Submodule for each stage
  private val stgMap = (otherStages).foldLeft(Map[PStage, BModuleDef]())((m, s) => {
    m + (s -> getStageModule(s, isFirstStg = false))
  }) + (firstStage -> getStageModule(firstStage, isFirstStg = true))
  //Define an interface for interacting with this module
  private val startMethod = BMethodSig(startMethodName, Action, firstStageStruct.typ.fields)
  private val modInterfaceTyp = BInterface(mod.name.v.capitalize, List())
  private val modInterfaceDef = BInterfaceDef(modInterfaceTyp, List(startMethod))

  private val topModule = getTopModule
  /**
   * Uses the configured fifo interface type and the provided
   * BSV type to make a paramterized fifo type.
   * @param typ - The BSV type that describes the fifo's elements' types
   * @return - The new BSV type describing the parameterized fifo
   */
  private def getFifoType(typ: BSVType): BInterface = {
    BInterface(fifoType, List(BVar("elemtyp", typ)))
  }
  /**
   * Uses the configured register interface type and the provided
   * BSV type to make a paramterized register type.
   * @param typ - The BSV type that describes the register's element type
   * @return - The new BSV type describing the parameterized register
   */
  private def getRegType(typ: BSVType): BInterface = {
    BInterface(regType, List(BVar("elemtyp", typ)))
  }

  private def getThreadIdType: BSVType = {
    BSizedInt(unsigned = true, log2(otherStages.length + 1))
  }
  private def getLockType(): BInterface = {
    BInterface(lockType, List(BVar("idsize", getThreadIdType)))
  }

  /**
   * Returns the BSV program AST that represents the translation
   * of the provided module.
   * @return
   */
  def getBSV: BProgram = {
    BProgram(topModule = topModule, imports = List(BImport(fifoLib), BImport(lockLib), BImport(memLib)),
      exports = List(BExport(modInterfaceTyp.name, expFields = true), BExport(topModule.name, expFields = false)),
      structs = firstStageStruct +: edgeStructInfo.values.toList,
      interfaces = List(modInterfaceDef), modules = stgMap.values.toList)
  }

  /**
   * Converts the edge value information into BSV structs
   * that hold all of those values and puts it into a lookup structure.
   * @param stgs - The list of stages to process.
   * @param addTId - If true, then add a ThreadID field to the struct
   * @return - A map from edges to the BSV type definitions that represent their values.
   */
  private def getEdgeStructInfo(stgs: Iterable[PStage], addTId: Boolean = true): Map[PipelineEdge, BStructDef] = {
    stgs.foldLeft[Map[PipelineEdge, BStructDef]](Map())((m, s) => {
      s.inEdges.foldLeft[Map[PipelineEdge, BStructDef]](m)((ms, e) => {
        var sfields = e.values.foldLeft(List[BVar]())((l, id) => {
          l :+ BVar(id.v, toBSVType(id.typ.get))
        })
        if (addTId) sfields = sfields :+ threadIdVar
        val styp = BStruct(genEdgeName(e), sfields)
        val structdef =  BStructDef(styp, List("Bits", "Eq"))
        ms + (e -> structdef)
      })
    })
  }

  //Helper functions that cannonically generate names from pipeline structures.
  private def genEdgeName(e: PipelineEdge) = {
    "E_" + e.from.name.v + "_TO_" + e.to.name.v
  }
  private def genParamName(e: PipelineEdge): String = {
    "fifo_" + e.from.name.v + "_" + e.to.name.v
  }
  private def genParamName(s: PStage): String = {
    "s_" + s.name
  }
  private def genModuleName(s: PStage): String = {
    "mk" + s.name.v
  }
  private def genLockName(mem: Id): String = {
    mem.v + "_lock"
  }

  /**
   * Given a pipeline stage and the necessary edge info,
   * generate a BSV module definition.
   * @param stg - The pipeline stage to convert
   * @return - The generated BSV module definition
   */
  private def getStageModule(stg: PStage, isFirstStg: Boolean = false): BModuleDef = {
    //Define module parameters to communicate along pipeline edges
    val inMap = stg.inEdges.foldLeft[Map[PipelineEdge, BVar]](Map())((m, e) => {
      val edgeStructType = edgeMap(e)
      m + (e -> BVar(genParamName(e.from), getFifoType(edgeStructType)))
    })
    val inOutMap = inMap ++ stg.outEdges.foldLeft[Map[PipelineEdge, BVar]](Map())((m, e) => {
      val edgeStructType = edgeMap(e)
      m + (e -> BVar(genParamName(e.to), getFifoType(edgeStructType)))
    })
    //TODO only pass the module parameters that are actually needed instead of all
    val params = inOutMap.values.toList ++ modParams.values ++ lockParams.values
    //Generate set of definitions needed by rule conditions
    //(declaring variables read from unconditional inputs)
    val sBody = getStageBody(stg, inOutMap, isFirstStg)
    //Generate the set of execution rules for reading args and writing outputs
    val execRule = getStageRule(stg, inOutMap, isFirstStg)
    BModuleDef(name = genModuleName(stg), typ = None,
      params = params, body = sBody, rules = List(execRule), methods = List())
  }

  /**
   * Given a stage and the relevant edge information, generate the
   * rule which represents executing this stage, both reading its inputs
   * and writing its outputs
   * @param stg - The stage to process
   * @param paramMap - Edge to Parameter name Mapping
   * @return A single BSV Rule
   */
  private def getStageRule(stg: PStage, paramMap: Map[PipelineEdge, BVar], isFirstStg: Boolean = false): BRuleDef = {
    var writeCmdStmts = getEffectCmds(stg.cmds)
    val queueStmts = getEdgeQueueStmts(stg, stg.allEdges, paramMap)
    val blockingConds = getBlockingConds(stg.cmds)
    //add a statement to increment the thread ID
    if (isFirstStg) {
      writeCmdStmts = writeCmdStmts :+
        BModAssign(threadIdVar, BBOp("+", threadIdVar, BOne))
    }
    BRuleDef("execute", blockingConds, writeCmdStmts ++ queueStmts)
  }

  /**
   * If any commands could cause blocking conditions that prevent
   * the rule from running, place those here (e.g. checking if locks can be acquired)
   * @param cmds The list of commands to translate
   * @return The list of translated blocking commands
   */
  private def getBlockingConds(cmds: Iterable[Command]): List[BExpr] = {
    cmds.foldLeft(List[BExpr]())((l, c) => c match {
      case ICheckLock(mem) => {
        l :+ BMethodInvoke(lockParams(mem), "owns", List(threadIdVar))
      }
      case _ => l
    })
  }

  /**
   * Get the list of statements that represent dequeuing from
   * or enqueuing onto the input and output edges. Wrap them in
   * conditional statements if they are conditional edges.
   * @param s The stage we're compiling
   * @param es The subset of s's edges that we're translating
   * @param paramMap The edge variable names used by this stage
   * @return A list of statements that represent queue operations for the relevant edges
   */
  private def getEdgeQueueStmts(s: PStage, es: Iterable[PipelineEdge],
    paramMap: Map[PipelineEdge, BVar]): List[BStatement] = {
    es.foldLeft(List[BStatement]())((l, e) => {
      val stmt = if (e.to == s) {
        val deq = BExprStmt(BMethodInvoke(paramMap(e), "deq", List()))
        if (e.condRecv.isDefined) {
          BIf(toBSVExpr(e.condRecv.get), List(deq), List())
        } else {
          deq
        }
      } else {
        val op = getCanonicalStruct(edgeMap(e))
        val enq = BExprStmt(BMethodInvoke(paramMap(e), "enq", List(op)))
        if (e.condSend.isDefined) {
          BIf(toBSVExpr(e.condSend.get), List(enq), List())
        } else {
          enq
        }
      }
      l :+ stmt
    })
  }

  /**
   *
   * @param stg The stage to compile
   * @param edgeParams The edge parameter variables used by this stage
   * @return
   */
  private def getStageBody(stg: PStage, edgeParams: Map[PipelineEdge, BVar], isFirstStg: Boolean = false): List[BStatement] = {
    var body: List[BStatement] = List()
    //If the first stage, then instantiate the threadID register
    if (isFirstStg) {
      body = body :+ BModInst(BVar(threadIdName, getRegType(getThreadIdType)),
        BModule(regModuleName, List(BZero)))
    }
    //Define all of the variables read unconditionally
    val uncondIn = stg.inEdges.filter(e => e.condRecv.isEmpty)
    //unconditional reads are just variable declarations w/ values
    uncondIn.foreach(e => {
      //First element in read queue
      val paramExpr = BMethodInvoke(edgeParams(e), "first", List())
      e.values.foreach(v => {
        body = body :+ BDecl(BVar(v.v, toBSVType(v.typ.get)),
          BStructAccess(paramExpr, BVar(v.v, toBSVType(v.typ.get))))
      })
      //only read threadIDs from an unconditional edge
      if (!isFirstStg) {
        body = body :+ BDecl(threadIdVar,
          BStructAccess(paramExpr, threadIdVar))
      }
    })
    //generate a conditional assignment expression to choose
    //which conditional edge we're reading inputs from
    val condIn = stg.inEdges.filter(e => e.condRecv.isDefined)
    //each edge better be accepting the same values
    val variableList = condIn.foldLeft(Set[Id]())((s, e) => {
      s ++ e.values
    })
    variableList.foreach(v => {
      val condEdgeExpr = condIn.foldLeft[BExpr](BDontCare)((expr, edge) => {
        val paramExpr = BMethodInvoke(edgeParams(edge), "first", List())
        BTernaryExpr(toBSVExpr(edge.condRecv.get),
          BStructAccess(paramExpr, BVar(v.v, toBSVType(v.typ.get))),
          expr)
      })
      body = body :+ BDecl(BVar(v.v, toBSVType(v.typ.get)), condEdgeExpr)
    })
    //And now add all of the combinational connections
    body ++ getCombinationalCommands(stg.cmds)
  }
  //TODO add in the module parameters into the definition w/ appropriate types
  //TODO define the top level module interface based on the declaration
  private def getTopModule: BModuleDef = {
    //Body instantiates all of the params (fifos & memories) and then all of the stages
    //One fifo per edge in the graph
    val allEdges = (firstStage +: otherStages).foldLeft(Set[PipelineEdge]())((es, s) => {
      es ++ s.inEdges ++ s.outEdges
    })
    val edgeParams = allEdges.foldLeft(Map[PipelineEdge, BVar]())((m, e) => {
      m + (e -> BVar(genParamName(e), getFifoType(edgeMap(e))))
    })
    val edgeFifos = allEdges.foldLeft(Map[PipelineEdge, BModInst]())((m, e) => {
      m + (e -> BModInst(edgeParams(e), BModule(fifoModuleName, List())))
    })
    //Instantiate a lock for each memory:
    val memLocks = lockParams.keys.foldLeft(Map[Id, BModInst]())((m, id) => {
      m + (id -> BModInst(lockParams(id), BModule(lockModuleName, List())))
    })

    //Instantiate each stage module
    val mkStgs = (firstStage +: otherStages).map(s => {
      val moddef = stgMap(s)
      val modtyp = moddef.typ match {
        case Some(t) => t
        case None => BEmptyModule
      }
      val args: List[BVar] = s.allEdges.map(e => {
        edgeFifos(e).lhs
      }).toList ++ modParams.values.toList ++ lockParams.values.toList //TODO only pass mods that are necessary
      BModInst(BVar(genParamName(s), modtyp), BModule(moddef.name, args))
    })
    val stmts = edgeFifos.values.toList ++ memLocks.values.toList ++ mkStgs
    //expose a start method as part of the top level interface
    val startMethodDef = BMethodDef(startMethod, getEdgeQueueStmts(firstStage.inEdges.head.from, firstStage.inEdges, edgeParams))
    BModuleDef(name = "mk" + mod.name.v.capitalize, typ = Some(modInterfaceTyp),
      params = modParams.values.toList, body = stmts,
      rules = List(), methods = List(startMethodDef))
  }

  //Helper to accumulate getCombinationalCommand results into a single list
  private def getCombinationalCommands(cmds: Iterable[Command]): List[BStatement] = {
    cmds.foldLeft(List[BStatement]())((l, c) => {
      getCombinationalCommand(c) match {
        case Some(bs) => l :+ bs
        case None => l
      }
    })
  }
  /**
   * Translate all of the commands that go into the execution
   * rules that do not write any permanent state.
   * @param cmd The command to translate
   * @return Some(translation) if cmd is combinational, otherwise None
   */
  private def getCombinationalCommand(cmd: Command): Option[BStatement] = cmd match {
    case CAssign(lhs, rhs) =>
      Some(BDecl(BVar(lhs.id.v, toBSVType(lhs.typ.get)), toBSVExpr(rhs)))
    case ICondCommand(cond: Expr, c: Command) => getCombinationalCommand(c) match {
      case Some(bc) => Some(BIf(toBSVExpr(cond), List(bc), List()))
      case None => None
    }
    case CExpr(exp) => Some(BExprStmt(toBSVExpr(exp)))
    case IMemRecv(mem: Id, data: Option[EVar]) => data match {
      case Some(v) => {
        Some(BDecl(BVar(v.id.v, toBSVType(v.typ.get)), BMemPeek(modParams(mem))))
      }
      case None => None
    }
    case CCall(id, args) => None
    case CLockOp(mem, op) => None
    case CCheck(predVar) => None
    case CEmpty => None
    case v: ISpeculate => None
    case v: IUpdate => None
    case v: ICheck => None
    case v: IMemSend => None
    case v: ISend => None
    case v: IRecv => None
    case _: InternalCommand => None
    case CRecv(lhs, rhs) => throw UnexpectedCommand(cmd)
    case CIf(cond, cons, alt) => throw UnexpectedCommand(cmd)
    case CSeq(c1, c2) => throw UnexpectedCommand(cmd)
    case CTBar(c1, c2) => throw UnexpectedCommand(cmd)
    case COutput(exp) => throw UnexpectedCommand(cmd)
    case CReturn(exp) => throw UnexpectedCommand(cmd)
    case CSpeculate(predVar, predVal, verify, body) => throw UnexpectedCommand(cmd)
    case CSplit(cases, default) => throw UnexpectedCommand(cmd)
  }
  //Helper to accumulate getWriteCmd results into a single list
  private def getEffectCmds(cmds: Iterable[Command]): List[BStatement] = {
    cmds.foldLeft(List[BStatement]())((l, c) => {
      getEffectCmd(c) match {
        case Some(bs) => l :+ bs
        case None => l
      }
    })
  }
  /**
   * Returns the translation of any commands that write to any state:
   * this includes CALLs to the pipeline, lock acquisition, or any
   * memory requests/responses
   * @param cmd The command to translate
   * @return Some(translation) if cmd is effectful, else None
   */
  private def getEffectCmd(cmd: Command): Option[BStatement] = cmd match {
    case ICondCommand(cond: Expr, c: Command) => getEffectCmd(c) match {
      case Some(bc) => Some(BIf(toBSVExpr(cond), List(bc), List()))
      case None => None
    }
    //TODO implement lock arguments (a.k.a. thread IDs)
    case CLockOp(mem, op) => op match {
      case pipedsl.common.Locks.LockState.Free => None
      case pipedsl.common.Locks.LockState.Reserved => Some(BExprStmt(BMethodInvoke(lockParams(mem), "res", List(threadIdVar))))
      case pipedsl.common.Locks.LockState.Acquired => Some(BExprStmt(BMethodInvoke(lockParams(mem), "acq", List(threadIdVar))))
      case pipedsl.common.Locks.LockState.Released => Some(BExprStmt(BMethodInvoke(lockParams(mem), "rel", List(threadIdVar))))
    }
    case IMemSend(isWrite, mem: Id, data: Option[EVar], addr: EVar) => {
      if (isWrite) {
        Some(BMemWrite(modParams(mem), toBSVExpr(addr), toBSVExpr(data.get)))
      } else {
        Some(BMemReadReq(modParams(mem), toBSVExpr(addr)))
      }
    }
    //This is a write op b/c is modifies the mem queue its reading from
    case IMemRecv(mem: Id, data: Option[EVar]) => data match {
      case Some(v) => {
        Some(BMemReadResp(BVar(v.id.v, toBSVType(v.typ.get)), modParams(mem)))
      }
      case None => None
    }
    //TODO implement calls
    case CCall(id, args) => None
    case _:ICheckLock => None
    case CAssign(lhs, rhs) => None
    case CExpr(exp) => None
    case CEmpty => None
    case command: InternalCommand => throw UnexpectedCommand(cmd)
    case CCheck(predVar) => throw UnexpectedCommand(cmd)
    case CRecv(lhs, rhs) => throw UnexpectedCommand(cmd)
    case CSeq(c1, c2) => throw UnexpectedCommand(cmd)
    case CTBar(c1, c2) => throw UnexpectedCommand(cmd)
    case CIf(cond, cons, alt) => throw UnexpectedCommand(cmd)
    case COutput(exp) => throw UnexpectedCommand(cmd)
    case CReturn(exp) => throw UnexpectedCommand(cmd)
    case CSpeculate(predVar, predVal, verify, body) => throw UnexpectedCommand(cmd)
    case CSplit(cases, default) => throw UnexpectedCommand(cmd)
  }
  
}
