package xyz.hyperreal.shell

import scala.scalanative.unsafe._

@extern
object linenoiselib {
  def linenoise(prompt: CString): CString           = extern
  def linenoiseHistoryAdd(line: CString): CInt      = extern
  def linenoiseHistorySetMaxLen(len: CInt): CInt    = extern
  def linenoiseHistorySave(filename: CString): CInt = extern
  def linenoiseHistoryLoad(filename: CString): CInt = extern
  def linenoiseSetMultiLine(ml: CInt): Unit         = extern
}
