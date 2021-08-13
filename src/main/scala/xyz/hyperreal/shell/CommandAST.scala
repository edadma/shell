package xyz.hyperreal.shell

import scala.util.parsing.input.Position

case class PipelineAST(cmds: List[CommandAST])
case class RedirectionAST(pos: Position, dir: String, file: ArgumentAST)
case class CommandAST(pos: Position, cmd: String, args: List[ArgumentAST], redirs: List[RedirectionAST])
case class ArgumentAST(pos: Position, arg: String)
