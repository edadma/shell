package xyz.hyperreal.shell

import scala.scalanative.unsafe.{extern, CInt, CString, Ptr}

@extern
object unistd {
  def execvpe(path: CString, argv: Ptr[CString], envp: Ptr[CString]): CInt = extern
}
