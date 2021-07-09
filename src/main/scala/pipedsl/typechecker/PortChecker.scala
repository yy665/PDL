package pipedsl.typechecker

import pipedsl.common.{Locks, Syntax}
import pipedsl.common.Syntax._
import pipedsl.typechecker.TypeChecker.TypeChecks
import pipedsl.typechecker.Environments._

import scala.collection.mutable

/**
 * This annotates ports onto reads, writes, reserves, and releases.
 * It also checks in a fairly rudimentary way that the same pipe isn't called
 * multiple times in the same cycle (though this can potentially be updated
 * for superscalar execution). This bit is NOT done with the SMT solver, so
 * other than knowing abut if-else it isn't very smart. Ports are simply
 * assigned in order as operations are encountered in a cycle, with if-else
 * being done "in parallel" and then having a max taken at the end. For
 * example,
 * if(cond)
 * { x = rf[rs1]; y = rf[rs2] }
 * else
 * { z = rf[rs3]; }
 * w = rf[rs4]
 * will assign x to read port 1, y to read port 2, and z to read port 1. z
 * will then be assigned to read port 3
 */
class PortChecker(prnt :Boolean) extends TypeChecks[Id, (Int, Int)]
{

  private val optimalPorts = mutable.HashMap.empty[Id, (Int, Int)]
  private val modLims = mutable.HashMap.empty[Id, (Int, Int)]
  private val report_optimal = prnt
  
  override def
  emptyEnv(): Environment[Id, (Int, Int)] =
    EmptyIntEnv

  override def
  /*functions are combinational and cannot do anything with mem/locks*/
  checkFunc(f: FuncDef, env: Environment[Id, (Int, Int)])
  : Environment[Id, (Int, Int)] = env

  override def
  checkCircuit(c: Circuit, env: Environment[Id, (Int, Int)])
  : Environment[Id, (Int, Int)] = env

  override def
  checkModule(m: ModuleDef, env: Environment[Id, (Int, Int)])
  : Environment[Id, (Int, Int)] =
    {
      modLims.clear()
      optimalPorts.clear()
      m.modules.foreach(mod => mod.typ match {
        case TMemType(_, _, _, _, r, w) => modLims.addOne((mod.name, (r, w)))
        case TLockedMemType(TMemType(_, _, _, _, r, w), _, _) =>
          modLims.addOne((mod.name, (r, w)))
        case _ =>
      })
      val port_map = checkPipe(m.body, emptyEnv())
      if(prnt && false)
        port_map.getMappedKeys().foreach(mem =>
          {
            /*sadly we are reusing the int pair to mean sth dif on mem/locks*/
            println(mem + " r/res: " + port_map(mem)._1)
            println(mem + " w/rel: " + port_map(mem)._2)
          })

      m.modules.foreach(mod =>
        {
          val (r, w) = port_map.get(mod.name).getOrElse((0, 0))
          val (ro, wo) = optimalPorts.getOrElse(mod.name, (0, 0))
          val ass_ports = (rp :Int, wp :Int) =>
            {
              if(r > rp)
                throw new RuntimeException(s"Not enough read ports on " +
                  s"${mod.name}. Requires at least $r but found $rp")
              else if (r > ro && report_optimal)
                throw new RuntimeException("Better throughput could be " +
                  s"obtained by having $ro read ports on ${mod.name} instead " +
                  s"of $r")
              if(w > wp)
                throw new RuntimeException(s"Not enough write ports on ${mod
                  .name}. Requires at least $w but found $wp")
              else if (w > wo && report_optimal)
                throw new RuntimeException("Better throughput could be " +
                  s"obtained by having $wo write ports on ${mod.name} instead" +
                  s" of $w")
            }
          mod.typ match
          {
            case TMemType(_, _, _, _, rp, wp) =>
              ass_ports(rp, wp)
            case TLockedMemType(TMemType(_, _, _, _, rp, wp), _, _) =>
              ass_ports(rp, wp)
            case _ =>
          }
        }
      )
      port_map
    }

  def
  /*this function lets us use recursion to split across the time barriers*/
  checkPipe(c: Command,
            strt_env :Environment[Id, (Int, Int)])
  :Environment[Id, (Int, Int)] =
    {
      val start_env = strt_env//callees.foldLeft(strt_env)((nv, id) => nv.remove(id))
       c match
      {
        case CTBar(c1@CTBar(_, _), c2@CTBar(_, _)) => checkPipe(c2, checkPipe(c1, start_env))

        case CTBar(c1@CTBar(_, _), c2) =>
          val env1 = checkPipe(c1, start_env)
          checkCommand(c2, emptyEnv(), env1)
        case CTBar(c1, c2@CTBar(_, _)) =>
          checkPipe(c2, checkCommand(c1, emptyEnv(), start_env))
        case CTBar(c1, c2) =>
          val env1 = checkCommand(c1, emptyEnv(), start_env)
          checkCommand(c2, emptyEnv(), env1)
        case _ => checkCommand(c, emptyEnv(), start_env)
      }
    }


  /**
   * checks a command within a time barrier
   * @param c the command to check
   * @param env the environment to check in
   * @return a new environment including the results of checking c
   */
  def
  checkCommand(c: Command,
               env: Environment[Id, (Int, Int)],
               start_env: Environment[Id, (Int, Int)])
  : Environment[Id, (Int, Int)] = c match
  {
    case CSeq(c1, c2) =>
      checkCommand(c2, checkCommand(c1, env, start_env), start_env)
    case CTBar(_, _) => checkPipe(c, start_env)
    case CIf(_, cons, alt) =>
      checkCommand(cons, env, start_env).union(checkCommand(alt, env, start_env))
    case CAssign(_, data) =>
      checkExpr(data, env, start_env)
    case CRecv(EVar(_), EMemAccess(mem, EVar(_), _)) =>
      /*asynch read*/
//      println("asynch read")
      val ret = env.add(mem, (1, 0))
      var port = (ret(mem)._1  + start_env(mem)._1) % modLims(mem)._1
      if (port == 0) port = modLims(mem)._1
      c.portNum = Some(port)
      if (ret(mem)._1 > modLims(mem)._1)
        throw new RuntimeException(s"$mem does not have enough read ports!")
      val cur_opt = optimalPorts.getOrElse(mem, (0, 0))
      optimalPorts.update(mem, (cur_opt._1 + 1, cur_opt._2))
      ret
    case CRecv(EMemAccess(mem, _, _), _) =>
      /*any write, asynch or sequential*/
//      println("any write")
      val ret = env.add(mem, (0, 1))
      var port = (ret(mem)._2 + start_env(mem)._2) % modLims(mem)._2
      if (port == 0) port = modLims(mem)._2
      c.portNum = Some(port)
//      println(s"write port: $port")
//      println(s"limit: ${modLims(mem)._2}")
      if (ret(mem)._2 > modLims(mem)._2)
        throw new RuntimeException(s"$mem does not have enough write ports!")
      val cur_opt = optimalPorts.getOrElse(mem, (0, 0))
      optimalPorts.update(mem, (cur_opt._1, cur_opt._2 + 1))
      ret
    case CRecv(EVar(_), data) =>
      checkExpr(data, env, start_env)
    case CSpecCall(_, pipe, args) =>
      //callees += pipe
      val nenv = args.foldLeft(env)((acc,e) => checkExpr(e, acc, start_env))
      nenv.get(pipe) match
      {
        case Some((1, 0)) =>
          throw new RuntimeException(s"tried to call $pipe multiple " +
            s"times in the same cycle")
        case _ =>
          val ret = nenv.add(pipe, (1, 0))
          c.portNum = Some(ret(pipe)._1)
          ret
      }
    case CVerify(_, args, _) =>
      args.foldLeft(env)((acc, e) => checkExpr(e, acc, start_env))
     case COutput(exp) => checkExpr(exp, env, start_env)
    case CReturn(exp) => checkExpr(exp, env, start_env)
    case CExpr(exp) => checkExpr(exp, env, start_env)
     case CLockOp(mem, op, lockType) =>
      val mangled = lockType match
      {
        case Some(Syntax.LockRead)  => Id(mem.id + "?r")
        case Some(Syntax.LockWrite) => Id(mem.id + "?w")
        case None => Id(mem.id + "?g")
      }
      op match
      {
        case Locks.Reserved =>
          val start_res = start_env(mangled)._1
          val ret = env.add(mangled, (1, 0))
          val cur_opt = optimalPorts.getOrElse(mangled, (0, 0))
          optimalPorts.update(mangled, (cur_opt._1 + 1, cur_opt._2))
          c.portNum = Some(ret(mangled)._1)
//          if(ret(mangled)._1 == start_res)
//            throw new RuntimeException(s"${mem.id} does not support enough " +
//              s"reserves per cycle!")
//          println(c.portNum.get)
          ret
        case Locks.Released =>
          val start_rel = start_env(mangled)._2
          val ret = env.add(mangled, (0, 1))
          val cur_opt = optimalPorts.getOrElse(mangled, (0, 0))
          optimalPorts.update(mangled, (cur_opt._1, cur_opt._2 + 1))
          c.portNum = Some(ret(mangled)._2)
//          if(ret(mangled)._2 == start_rel)
//            throw new RuntimeException(s"${mem.id} does not support enough " +
//              s"releases per cycle!")
//          println(c.portNum.get)
          ret
        case _ => env
      }
    case CSplit(cases, default) =>
      cases.foldLeft(checkCommand(default, env, start_env))((enviro, comm) =>
        checkCommand(comm.body, env, start_env).union(enviro))
   case _: InternalCommand => env
    case _ => env
  }

  /**
   * recursively annotates ports on a single expression
   * @param e the expression to annotate
   * @param env the environment to check for existing mem/lock ops
   * @return the new environment with the results of e taken into account
   */
  def
  checkExpr(e: Expr,
            env: Environment[Id, (Int, Int)],
            start_env: Environment[Id, (Int, Int)])
  : Environment[Id, (Int, Int)] = e match
  {
    case EIsValid(ex) => checkExpr(ex, env, start_env)
    case EFromMaybe(ex) => checkExpr(ex, env, start_env)
    case EToMaybe(ex) => checkExpr(ex, env, start_env)
    case EUop(_, ex) => checkExpr(ex, env, start_env)
    case EBinop(_, e1, e2) =>
      checkExpr(e2, checkExpr(e1, env, start_env), start_env)
    case ERecAccess(rec, _) => checkExpr(rec, env, start_env)
    case ERecLiteral(fields) =>
      fields.foldLeft(env)((en, p) => checkExpr(p._2, en, start_env))
    case EMemAccess(mem, _, _) =>
      /*combinational read*/
//      println("combinational read")
      val ret = env.add(mem, (1, 0))
      var port = (ret(mem)._1  + start_env(mem)._1) % modLims(mem)._1
      if (port == 0) port = modLims(mem)._1
      e.portNum = Some(port)
      if (ret(mem)._1 > modLims(mem)._1)
        throw new RuntimeException(s"$mem does not have enough read ports!")
      val cur_opt = optimalPorts.getOrElse(mem, (0, 0))
      optimalPorts.update(mem, (cur_opt._1 + 1, cur_opt._2))
      ret
    case EBitExtract(num, _, _) => checkExpr(num, env, start_env)
    case ETernary(cond, tval, fval) =>
      val cond_env = checkExpr(cond, env, start_env)
      checkExpr(tval, cond_env, start_env)
        .union(checkExpr(fval, cond_env, start_env))
    case EApp(_, args) =>
      args.foldLeft(env)((acc, e) => checkExpr(e, acc, start_env))
    case ECall(mod, args) =>
      /*perhaps we should have some restrictions on calling the same module*/
      /*twice in a single cycle. I think I said that should be a nono*/
      //callees += mod
      val nenv = args.foldLeft(env)((acc, e) => checkExpr(e, acc, start_env))
      nenv.get(mod) match
    {
      case Some((1, 0)) =>
        throw new RuntimeException(s"${mod.v} is already called in this cycle")
      case _ =>
        /*we can annotate this so that if potentially we support superscalar*/
        /*pipes at some point this would be relevant*/
        val ret = nenv.add(mod, (1, 0))
        e.portNum = Some(ret(mod)._1)
        ret
    }
    case EVar(_) => env
    case ECast(_, exp) => checkExpr(exp, env, start_env)
    case _: CirExpr => env /*not sure about this part?*/
    case _ => env
  }
}