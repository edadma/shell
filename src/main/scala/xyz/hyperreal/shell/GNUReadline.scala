package xyz.hyperreal.shell

import scala.scalanative.unsafe._

@link("readline")
@extern
object GNUReadline {
  def readline(prompt: CString): CString = extern

  def read_history(filename: CString): CInt                    = extern
  def write_history(filename: CString): CInt                   = extern
  def append_history(nelements: CInt, filename: CString): CInt = extern
  def add_history(line: CString): Unit                         = extern
}
