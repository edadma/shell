package xyz.hyperreal.shell

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}
import scala.util.parsing.input.{CharSequenceReader, Position, Positional}

object CommandParser extends RegexParsers with PackratParsers {
  lazy val pos: PackratParser[Position] = positioned(success(new Positional {})) ^^ (_.pos)

  lazy val pipeline: PackratParser[PipelineAST] =
    rep1sep(command, "|") ~ rep(redirection) ^^ {
      case cs ~ rs => PipelineAST(cs, rs)
    }

  lazy val redirection: PackratParser[RedirectionAST] =
    pos ~ ("<" | ">") ~ argument ^^ {
      case p ~ d ~ a => RedirectionAST(p, d, a)
    }

  lazy val command: PackratParser[CommandAST] =
    pos ~ word ~ rep(argument) ^^ {
      case p ~ c ~ as => CommandAST(p, c, as)
    }

  lazy val argument: PackratParser[ArgumentAST] =
    pos ~ (word | string) ^^ {
      case p ~ a => ArgumentAST(p, a)
    }

  lazy val singleQuoteString: PackratParser[String] =
    """'(?:[^'\x00-\x1F\x7F\\]|\\[\\'"bfnrt]|\\u[a-fA-F0-9]{4})*'""".r ^^ (s => s.substring(1, s.length - 1))

  lazy val doubleQuoteString: PackratParser[String] =
    """"(?:[^"\x00-\x1F\x7F\\]|\\[\\'"bfnrt]|\\u[a-fA-F0-9]{4})*"""".r ^^ (s => s.substring(1, s.length - 1))

  lazy val string: PackratParser[String] = singleQuoteString | doubleQuoteString

  lazy val word: PackratParser[String] = """[^'"\s|<>]+""".r ^^ identity

  def parsePipeline(input: String): PipelineAST =
    parseAll(phrase(pipeline), new PackratReader(new CharSequenceReader(input))) match {
      case Success(result, _)     => result
      case NoSuccess(error, rest) => problem(rest.pos, error)
    }
}
