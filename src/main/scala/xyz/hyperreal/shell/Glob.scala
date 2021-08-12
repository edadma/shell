package xyz.hyperreal.shell

import scala.scalanative.unsafe.{extern, CInt, CSize, CString, Ptr}

@extern
object Glob {
  type GlobT = Ptr[Byte]

  def globHelper(pattern: CString, globbuf: Ptr[GlobT], pathc: Ptr[CSize], pathv: Ptr[Ptr[CString]]): CInt = extern

  def globfree(pglob: GlobT): Unit = extern
}
