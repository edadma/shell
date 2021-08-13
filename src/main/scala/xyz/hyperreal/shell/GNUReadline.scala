package xyz.hyperreal.shell

import scala.scalanative.unsafe._

@link("readline")
@extern
object GNUReadline {
  type _hist_entry = CString

  def readline(prompt: CString): CString = extern

  def read_history(filename: CString): CInt                    = extern
  def write_history(filename: CString): CInt                   = extern
  def append_history(nelements: CInt, filename: CString): CInt = extern
  def add_history(line: CString): Unit                         = extern

  def history_get(offset: CInt): Ptr[_hist_entry] = extern

  var history_base: CInt   = extern
  var history_length: CInt = extern
}
