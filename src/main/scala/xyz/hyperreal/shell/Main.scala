package xyz.hyperreal.shell

import scala.scalanative.unsafe._
import scala.scalanative.posix.unistd._
import scala.scalanative.libc.stdio._
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.errno._
import scala.scalanative.libc.string._
import scala.scalanative.unsigned._

import waitlib._
import linenoiselib._

@extern
object linenoiselib {
  def linenoise(prompt: CString): CString           = extern
  def linenoiseHistoryAdd(line: CString): CInt      = extern
  def linenoiseHistorySetMaxLen(len: CInt): CInt    = extern
  def linenoiseHistorySave(filename: CString): CInt = extern
  def linenoiseHistoryLoad(filename: CString): CInt = extern
  def linenoiseSetMultiLine(ml: CInt): Unit         = extern
}

object Main extends App {
  val HISTORY_FILE  = c"history.txt"
  var line: CString = _

  linenoiseHistorySetMaxLen(100)
  linenoiseHistoryLoad(HISTORY_FILE)

  while ({ line = linenoise(c"hello> "); line != null }) {
    val s = fromCString(line).trim

    free(line)

    if (s nonEmpty)
      Zone { implicit z =>
        println(s)
        linenoiseHistoryAdd(toCString(s))
        linenoiseHistorySave(HISTORY_FILE)
      }
  }

//  if (args.isEmpty) {
//    println("bye")
//    exit(1)
//  }
//
//  println("about to fork")
//
//  val status =
//    doAndAwait { () =>
//      println(s"in child, about to exec command: ${args.toSeq}")
//      runCommand(args)
//    }
//
//  println(s"wait status ${status}")

  def runOneAtATime(commands: Seq[Seq[String]]): Unit =
    for (command <- commands)
      doAndAwait(() => runCommand(command))

  def runSimultaneously(commands: Seq[Seq[String]]): Unit =
    commands map (c => doFork(() => runCommand(c))) foreach await

  def doFork(task: () => Int): Int = {
    val pid = fork()

    if (pid > 0) {
      pid
    } else {
      val res = task.apply()

      exit(res)
      res
    }
  }

  def runOneAtATime(commands: Seq[Seq[String]]): Unit = {
    for (command <- commands) {
      doAndAwait { () =>
        runCommand(command)
      }
    }
  }

  def await(pid: Int): Int = {
    val status = stackalloc[Int]

    waitpid(pid, status, 0)

    val statusCode = !status

    if (statusCode != 0) throw new Exception(s"Child process returned error ${statusCode >> 8}")

    statusCode
  }

  def doAndAwait(task: () => Int): Int = {
    val pid = doFork(task)

    await(pid)
  }

  def runCommand(args: Seq[String], env: Map[String, String] = Map.empty): Int = {
    if (args.isEmpty)
      throw new Exception("bad arguments of length 0")

    Zone { implicit z =>
      val fname       = toCString(args.head)
      val arg_array   = makeStringArray(args)
      val env_strings = env map { case (k, v) => s"$k=$v" } toSeq
      val env_array   = makeStringArray(env_strings)
      val r           = execve(fname, arg_array, env_array)

      if (r != 0) {
        val err = errno

        printf(s"error: $err ${fromCString(strerror(err))}\n")
        throw new Exception(s"bad execve: returned $r")
      } else 0
    }
  }

  def makeStringArray(args: Seq[String]): Ptr[CString] = {
    val size       = sizeof[Ptr[CString]] * (args.size + 1).toUInt
    val dest_array = malloc(size).asInstanceOf[Ptr[CString]]
    val count      = args.size

    for ((arg, i) <- args.zipWithIndex) {
      val string_len = arg.length
      val dest_str   = malloc((string_len + 1).toUInt)

      copyString(arg, dest_str)
      dest_array(i) = dest_str
    }

    dest_array(count) = null
    dest_array
  }

  def copyString(src: String, dst: CString): Unit = {
    for ((c, i) <- src.zipWithIndex)
      dst(i) = c.asInstanceOf[CChar]

    dst(src.length) = '\u0000'
  }
}

@extern
object waitlib {
  def waitpid(pid: Int, status: Ptr[Int], options: Int): Int = extern
}
