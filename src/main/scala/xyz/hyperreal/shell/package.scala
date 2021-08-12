package xyz.hyperreal

import scala.util.parsing.input.Position

package object shell {

  def problem(pos: Position, msg: String): Nothing = {
    printError(pos, msg)
    sys.error("error executing query")
  }

  def printError(pos: Position, msg: String): Unit = {
    if (pos eq null)
      Console.err.println(msg)
    else if (pos.line == 1)
      Console.err.println(s"$msg\n${pos.longString}")
    else
      Console.err.println(s"${pos.line}: $msg\n${pos.longString}")
  }
}
