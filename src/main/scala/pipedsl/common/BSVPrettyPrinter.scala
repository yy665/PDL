package pipedsl.common

import java.io.{FileOutputStream, OutputStreamWriter, Writer}

import pipedsl.common.BSVSyntax._
import pipedsl.common.Errors.BaseError

object BSVPrettyPrinter {

  private def mkExprString(strs: String*): String = {
    strs.mkString(" ")
  }

  def toDeclString(v: BVar): String = {
    mkExprString(toBSVTypeStr(v.typ), v.name)
  }

  def toBSVTypeStr(t: BSVType): String = t match {
    case BStruct(name, _) => name
    case BEmptyModule => "Empty"
    case BInterface(name, tparams) =>
      if (tparams.nonEmpty) {
        val paramstring = tparams.map(v => toBSVTypeStr(v.typ)).mkString(", ")
        name + "#( " + paramstring + " )"
      } else {
        name
      }
    case BSizedInt(unsigned, size) =>
      (if (unsigned) {
        "U"
      } else {
        ""
      }) + "Int#(" + size + ")"
    case BBool => "Bool"
    case BMemType(elem, addrSize) => "TODO MEM TYPE"
  }

  private def toIntString(base: Int, value: Int): String = base match {
    case 16 => "h" + value.toHexString
    case 10 => "d" + value.toString
    case 8 => "o" + value.toOctalString
    case 2 => "b" + value.toBinaryString
    case default => throw BaseError(base)
  }

  private def toBSVExprStr(expr: BExpr): String = expr match {
    case BCaseExpr(cond, cases) =>
      val caseString = cases.map(c => {
        toBSVExprStr(c._1) + ": return " + toBSVExprStr(c._2)
      }).mkString(";")
      mkExprString("case (", toBSVExprStr(cond), ")", caseString,"endcase")
    case BBoolLit(v) => if (v) { "True" } else { "False" }
    case BIntLit(v, base, bits) => bits.toString + "'" + toIntString(base, v)
    case BStructLit(typ, fields) =>
      val fieldStr = fields.keys.map(k => {
        mkExprString(toBSVExprStr(k), ":", toBSVExprStr(fields(k)))
      }).mkString(",")
      mkExprString(typ.name, "{",  fieldStr, "}")
    case BStructAccess(rec, field) => toBSVExprStr(rec) + "." + toBSVExprStr(field)
    case BVar(name, typ) => name
    case BBOp(op, lhs, rhs) => mkExprString("(", toBSVExprStr(lhs), op, toBSVExprStr(rhs), ")")
    case BUOp(op, expr) => mkExprString("(", op, toBSVExprStr(expr), ")")
    case BBitExtract(expr, start, end) => mkExprString(
      "(", toBSVExprStr(expr), "[", end.toString, ":", start.toString, "]" ,")"
    )
    case BConcat(first, rest) =>
      val exprstr = rest.foldLeft[String](toBSVExprStr(first))((s, e) => {
        s + ", " + toBSVExprStr(e)
      })
      mkExprString( "{", exprstr, "}")
    case BModule(name, args) =>
      val argstring = args.map(a => toBSVExprStr(a)).mkString(", ")
      mkExprString(name, "(", argstring, ")")
    case BMethodInvoke(mod, method, args) =>
      val argstring = args.map(a => toBSVExprStr(a)).mkString(", ")
      toBSVExprStr(mod) + "." + method + "(" + argstring + ")"
  }

  def getFilePrinter(name: String): BSVPretyPrinterImpl = {
    new BSVPretyPrinterImpl(new OutputStreamWriter(new FileOutputStream(name)))
  }

  class BSVPretyPrinterImpl(w: Writer) {

    private val indentSize = 4
    private var curIndent = 0

    private def indent(): String = {
      " " * curIndent
    }
    private def incIndent(): Unit = {
      curIndent += indentSize
    }
    private def decIndent(): Unit = {
      curIndent -= indentSize
    }

    private def mkIndentedExpr(strs: String*): String = {
      indent() + strs.mkString(" ")
    }
    private def mkStatementString(strs: String*): String = {
      indent() + strs.mkString(" ") + ";\n"
    }

    def printImport(imp: BImport): Unit = {
      w.write(mkStatementString("import", imp.name, ":: *"))
    }

    def printStructDef(sdef: BStructDef): Unit = {
      val structstring = mkExprString("struct {",
        sdef.typ.fields.map(f => {
          toDeclString(f)
        }).mkString("; "),
        "; }", sdef.typ.name)
      w.write(
        mkStatementString("typedef", structstring,
          "deriving(", sdef.derives.mkString(","), ")"
        )
      )
    }

    def printBSVStatement(stmt: BStatement): Unit = stmt match {
      case BExprStmt(expr) => w.write(mkStatementString(toBSVExprStr(expr)))
      case BModInst(lhs, rhs) => w.write(mkStatementString(toDeclString(lhs), "<-", toBSVExprStr(rhs)))
      case BModAssign(lhs, rhs) => w.write(mkStatementString(toBSVExprStr(lhs), "<=", toBSVExprStr(rhs)))
      case BAssign(lhs, rhs) => w.write(mkStatementString(toBSVExprStr(lhs), "=", toBSVExprStr(rhs)))
      case BDecl(lhs, rhs) => w.write(mkStatementString(toDeclString(lhs), "=", toBSVExprStr(rhs)))
      case BIf(cond, trueBranch, falseBranch) =>
        w.write(mkStatementString("if", "(", toBSVExprStr(cond) + ")"))
        w.write(mkIndentedExpr("begin\n"))
        incIndent()
        trueBranch.foreach(s => printBSVStatement(s))
        decIndent()
        w.write(mkIndentedExpr("else\n"))
        incIndent()
        falseBranch.foreach(s => printBSVStatement(s))
        decIndent()
        w.write(mkIndentedExpr("end\n"))
    }

    def printBSVRule(rule: BRuleDef): Unit = {
      val condString = rule.conds.map(c => toBSVExprStr(c)).mkString(" && ")
      w.write(mkStatementString("rule", rule.name, "(", condString, ")"))
      incIndent()
      rule.body.foreach(b => printBSVStatement(b))
      decIndent()
      w.write(mkIndentedExpr("endrule\n"))
    }

    //TODO - we don't generate any methods atm
    def printBSVMethod(method: BMethodDef, w: Writer): Unit = {}

    def printModule(mod: BModuleDef, synthesize: Boolean = false): Unit = {
      //this just defines the interface this module implements,
      // the variable is necessary but unused
      val interfaceParam = if (mod.typ.isDefined) {
        BVar("_unused_", mod.typ.get)
      } else {
        BVar("_unused_", BEmptyModule)
      }
      val paramStr = (mod.params :+ interfaceParam).map(p => toDeclString(p)).mkString(", ")
      val paramString = mkExprString("(", paramStr, ")")
      if (synthesize) {
        w.write("(* synthesize *)\n")
      }
      w.write(mkStatementString("module", mod.name, paramString))
      incIndent()
      mod.body.foreach(s => printBSVStatement(s))
      mkStatementString("") //for readability only
      mod.rules.foreach(r => printBSVRule(r))
      mkStatementString("")
      mod.methods.foreach(m => printBSVMethod(m, w))
      mkStatementString("")
      decIndent()
      //Doesn't end in semi-colon
      w.write(mkIndentedExpr("endmodule\n"))
    }

    def printBSVProg(b: BProgram): Unit = {
      b.imports.foreach(i => printImport(i))
      w.write("\n")
      b.structs.foreach(s => printStructDef(s))
      w.write("\n")
      b.modules.foreach(m => printModule(m))
      w.write("\n")
      printModule(b.topModule, synthesize = true)
      w.flush()
    }
  }
}
