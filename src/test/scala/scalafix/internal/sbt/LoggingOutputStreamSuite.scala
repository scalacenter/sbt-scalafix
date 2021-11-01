package scalafix.internal.sbt

import java.io.PrintStream

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import sbt.{Level, Logger}

import scala.collection.mutable

class LoggingOutputStreamSuite extends AnyFunSuite with Matchers {

  val sep = System.lineSeparator
  val CRLF = "\r\n"

  def withStubLogger(
      level: Level.Value,
      separator: String = sep
  )(
      testFun: (
          LoggingOutputStream,
          mutable.Seq[(Level.Value, String)]
      ) => Unit
  ): Unit = {
    val logs = mutable.ListBuffer[(Level.Value, String)]()

    val logger = new Logger {
      override def log(level: Level.Value, message: => String): Unit =
        logs += ((level, message))

      override def success(message: => String): Unit = ???
      override def trace(t: => Throwable): Unit = ???
    }

    testFun(new LoggingOutputStream(logger, level, separator), logs)
  }

  test("capture objects printed through PrintStream.println") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val data = 1234
      new PrintStream(outputStream).println(data)
      logs must be(mutable.Seq((Level.Warn, String.valueOf(data))))
    }
  }

  test("capture messages of increasing length") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val word = "foo"
      val printStream = new PrintStream(outputStream)
      printStream.println(word)
      printStream.println(word * 2)
      printStream.println(word * 3)
      logs.map(_._2) must be(mutable.Seq(word, word * 2, word * 3))
    }
  }

  test("capture messages of decreasing length") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val word = "foo"
      val printStream = new PrintStream(outputStream)
      printStream.println(word * 3)
      printStream.println(word * 2)
      printStream.println(word)
      logs.map(_._2) must be(mutable.Seq(word * 3, word * 2, word))
    }
  }

  test("capture messages of non-monotonic length") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val word = "foo"
      val printStream = new PrintStream(outputStream)
      printStream.println(word * 3)
      printStream.println(word)
      printStream.println(word * 2)
      logs.map(_._2) must be(mutable.Seq(word * 3, word, word * 2))
    }
  }

  test("capture heading empty message") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val message = "hello world!"
      outputStream.write(s"$sep$message$sep".getBytes)
      logs.map(_._2) must be(mutable.Seq("", message))
    }
  }

  test("capture in-between empty message") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val message1 = "hello world!"
      val message2 = "here we are"
      outputStream.write(s"$message1$sep$sep$message2$sep".stripMargin.getBytes)
      logs.map(_._2) must be(mutable.Seq(message1, "", message2))
    }
  }

  test("capture trailing empty message") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val message = "hello world!"
      outputStream.write(s"$message$sep$sep".getBytes)
      logs.map(_._2) must be(mutable.Seq(message, ""))
    }
  }

  test("capture multi-byte characters") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val messageWithNonAsciiChar = "il était un petit navire"
      messageWithNonAsciiChar.getBytes.length must be > messageWithNonAsciiChar.length // assert test is correct
      outputStream.write(s"$messageWithNonAsciiChar$sep".getBytes)
      logs.map(_._2) must be(mutable.Seq(messageWithNonAsciiChar))
    }
  }

  test("handle multi-character separator") {
    withStubLogger(Level.Warn, CRLF) { (outputStream, logs) =>
      val message1 = "hello world!"
      val message2 = "here we are"
      outputStream.write(s"$message1$CRLF$CRLF$message2$CRLF".getBytes)
      logs.map(_._2) must be(mutable.Seq(message1, "", message2))
    }
  }

  test("capture very long messages") {
    withStubLogger(Level.Warn) { (outputStream, logs) =>
      val veryLongMessage =
        "a" * 1000000 // this would exhaust memory on quadratic implementations
      outputStream.write(s"$veryLongMessage$sep".getBytes)
      logs.map(_._2) must be(mutable.Seq(veryLongMessage))
    }
  }

  test(
    "capture very long messages containing a subset of the line separator"
  ) {
    withStubLogger(Level.Warn, CRLF) { (outputStream, logs) =>
      val veryLongMessage = CRLF.head.toString * 1000000
      outputStream.write(s"$veryLongMessage$CRLF".getBytes)
      logs.map(_._2) must be(mutable.Seq(veryLongMessage))
    }
  }

  test("fail verbosely for invalid separator") {
    an[IllegalArgumentException] should be thrownBy new LoggingOutputStream(
      Logger.Null,
      Level.Warn,
      separator = ""
    )
  }
}
