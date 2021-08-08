package xyz.hyperreal.shell

import scala.scalanative.unsafe.{extern, Ptr}

@extern
object waitlib {
  def waitpid(pid: Int, status: Ptr[Int], options: Int): Int = extern
}
