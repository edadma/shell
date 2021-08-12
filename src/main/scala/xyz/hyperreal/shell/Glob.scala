package xyz.hyperreal.shell

import scala.scalanative.unsafe.{extern, CInt, CString, Ptr}

@extern
object glob {
  def execvpe(path: CString, argv: Ptr[CString], envp: Ptr[CString]): CInt = extern
}
