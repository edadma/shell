package xyz.hyperreal.shell

import scala.scalanative.unsafe._
import scala.scalanative.posix.unistd._
import scala.scalanative.libc.stdio._
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.errno._
import scala.scalanative.libc.string._
import scala.scalanative.unsigned._

import unistd._
import waitlib._
import io.github.edadma.readline.facade._
import xyz.hyperreal.snutils.Globbing

import scala.annotation.tailrec
import scala.collection.mutable

object Main extends App {
  val homeDir = System.getProperty("user.home")

  val HISTORY_FILE = s"$homeDir/.shell_history"
  val BUFSIZE      = 256.toUInt

  var historyExists = read_history(HISTORY_FILE)

  @tailrec
  def repl(): Unit = {
    import Console._

    val cwd  = stackalloc[Byte](BUFSIZE)
    val scwd = if (getcwd(cwd, BUFSIZE) == null) "" else fromCString(cwd)
    val pcwd =
      if (scwd startsWith homeDir) '~' +: (scwd drop homeDir.length)
      else scwd
    val prompt = s"$CYAN$pcwd$RESET> "
    val line   = readline(prompt)

    if (line != null) {
      val s = line.trim

      if (s nonEmpty) {
        val prev = history_get(history_base + history_length - 1)

        if (prev == null || prev != s) {
          add_history(s)

          if (historyExists == 0)
            append_history(1, HISTORY_FILE)
          else {
            historyExists = 0
            write_history(HISTORY_FILE)
          }
        }

        try {
          val PipelineAST(commands) = CommandParser.parsePipeline(s)

          commands.head match {
            case CommandAST(_, "cd", List(ArgumentAST(_, dir)), Nil) => Zone(implicit z => chdir(toCString(tilde(dir))))
            case CommandAST(_, "cd", Nil, Nil)                       => Zone(implicit z => chdir(toCString(homeDir)))
            case CommandAST(_, "cd", _, _)                           => println("invalid 'cd' command")
            case CommandAST(_, "exit", List(ArgumentAST(_, st)), Nil) =>
              Zone(implicit z => sys.exit(toCString(st).toInt))
            case CommandAST(_, "exit", Nil, Nil) => sys.exit()
            case CommandAST(_, "exit", _, _)     => println("invalid 'exit' command")
            case _ =>
              commands.init foreach {
                _.redirs foreach {
                  case RedirectionAST(pos, ">" | ">>", _) =>
                    problem(pos, "output redirection not allowed here")
                  case _ =>
                }
              }

              commands.tail foreach {
                _.redirs foreach {
                  case RedirectionAST(pos, "<", _) =>
                    problem(pos, "input redirection not allowed here")
                  case _ =>
                }
              }

              val cs = commands map {
                case CommandAST(pos, cmd, args, redirs) =>
                  tilde(cmd) +: (args flatMap { case ArgumentAST(pos, arg) => Globbing.expand(tilde(arg)) })
              }

              pipeMany(STDIN_FILENO, STDOUT_FILENO, cs)
          }
        } catch {
          case e: RuntimeException if e.getClass.getName == "java.lang.RuntimeException" =>
          case e: Throwable                                                              => e.printStackTrace()
        }
      }

      repl()
    }
  }

  repl()

  def tilde(s: String): String =
    if (s startsWith "~") homeDir ++ (s drop 1)
    else s

  def pipeMany(input: Int, output: Int, procs: Seq[Seq[String]]): Int = {
    val pipe_array = stackalloc[Int]((2 * (procs.size - 1)).toUInt)
    var input_fds  = mutable.ArrayBuffer[Int](input)
    var output_fds = mutable.ArrayBuffer[Int]()

    // create our array of pipes
    for (i <- 0 until (procs.size - 1)) {
      val array_offset = i * 2
      val pipe_ret     = pipe(pipe_array + array_offset)

      output_fds += pipe_array(array_offset + 1)
      input_fds += pipe_array(array_offset)
    }

    output_fds += output

    val procsWithFds = (procs, input_fds, output_fds).zipped
    val pids = for ((proc, input_fd, output_fd) <- procsWithFds) yield {
      doFork { () =>
        // close all pipes that this process won't be using.
        for (p <- 0 until (2 * (procs.size - 1)))
          if (pipe_array(p) != input_fd && pipe_array(p) != output_fd)
            close(pipe_array(p))

        // reassign STDIN if we aren't at the front of the pipeline
        if (input_fd != input) {
          close(STDIN_FILENO)
          dup2(input_fd, STDIN_FILENO)
        }

        // reassign STDOUT if we aren't at the end of the pipeline
        if (output_fd != output) {
          close(STDOUT_FILENO)
          dup2(output_fd, STDOUT_FILENO)
        }

        runCommand(proc)
      }
    }

    for (i <- 0 until (2 * (procs.size - 1)))
      close(pipe_array(i))

    if (input != STDIN_FILENO)
      close(input)

    if (output != STDOUT_FILENO)
      close(output)

    var waiting_for      = pids.toSet
    var wait_result: Int = 0

    while (waiting_for.nonEmpty) {
      wait_result = waitpid(-1, null, 0)
      waiting_for = waiting_for - wait_result
    }

    wait_result
  }

  def pipeTwo(input: Int, output: Int, proc1: Seq[String], proc2: Seq[String]): Int = {
    val pipe_array = stackalloc[Int](2)
    val pipe_ret   = pipe(pipe_array)

    println(s"pipe() returned $pipe_ret")

    val output_pipe = pipe_array(1)
    val input_pipe  = pipe_array(0)
    val proc1_pid =
      doFork { () =>
        if (input != 0) {
          println(s"proc ${getpid()}: about to dup $input to stdin")
          dup2(input, STDIN_FILENO)
        }

        println(s"proc 1 about to dup $output_pipe to stdout")
        dup2(output_pipe, STDOUT_FILENO)
        println(s"process ${getpid()} about to runCommand")
        runCommand(proc1)
      }
    val proc2_pid =
      doFork { () =>
        println(s"proc ${getpid()}: about to dup")
        dup2(input_pipe, STDIN_FILENO)

        if (output != 1) {
          dup2(output, STDOUT_FILENO)
        }

        close(output_pipe)
        println(s"process ${getpid()} about to runCommand")
        runCommand(proc2)
      }

    close(input)
    close(output_pipe)
    close(input_pipe)

    val waiting_for = Seq(proc1_pid, proc2_pid)

    println(s"waiting for procs: $waiting_for")

    val r1 = waitpid(-1, null, 0)

    println(s"proc $r1 returned")

    val r2 = waitpid(-1, null, 0)

    println(s"proc $r2 returned")
    r2
  }

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
      val r           = execvpe(fname, arg_array, env_array)

      if (r != 0) {
        val err = errno

        println(s"error: $err ${fromCString(strerror(err))}")
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

//  val status =
//    doAndAwait { () =>
//      println(s"in child, about to exec command: ${args.toSeq}")
//      runCommand(args)
//    }
//
//  println(s"wait status $status")
