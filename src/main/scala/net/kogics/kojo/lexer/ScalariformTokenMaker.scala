/*
 * Copyright (C) 2012 Lalit Pant <pant.lalit@gmail.com>
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 */

package net.kogics.kojo.lexer

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMap
import org.fife.ui.rsyntaxtextarea.TokenTypes
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Segment
import scalariform.lexer.ScalaLexer
import scalariform.lexer.{ Token => SfToken }
import scalariform.lexer.TokenType
import scalariform.lexer.Tokens
import scala.collection.mutable.ListBuffer
import net.kogics.kojo.util.Utils

class ScalariformTokenMaker extends AbstractTokenMaker {

  var docTokens = ScalaLexer.rawTokeniseV("", true)
  var debugOn = false
  var timingOn = false

  def debug(msg: => String) {
    if (debugOn) {
      println(msg)
    }
  }

  def showTiming(t0: Long, mod: String) {
    if (timingOn) {
      println("Doc lexing [%s] took: %2.4f secs" format (mod, (System.currentTimeMillis - t0) / 1000.0))
    }
  }

  val docListener = new DocumentListener {

    def insertUpdate(e: DocumentEvent) {
      def changeInToken(offset: Int, len: Int, t: SfToken) = scalariform.utils.Range(offset, len).intersects(t.range)

      val (offset, len) = (e.getOffset, e.getLength)
      val doc = e.getDocument

      val insertPrefix = doc.getText(offset, 2)
      if (insertPrefix == "//" || insertPrefix == "/*") {
        lexDoc(doc.getText(0, doc.getLength))
      }
      else {
        try {
          updateDiagnosticFlags()
          val t0 = System.currentTimeMillis()

          //          println("\n***\nDoc Insert: Offset: %d, Length: %d" format (offset, len))
          val preInactive = docTokens.takeWhile(!changeInToken(offset, len, _))
          val active = docTokens.slice(preInactive.length, docTokens.length).takeWhile(changeInToken(offset, len, _))
          val postInactive = docTokens.slice(preInactive.length + active.length, docTokens.length)

          //          println("Pre Inactive: " + preInactive)
          //          println("Active: " + active)
          //          println("Post Inactive: " + postInactive)

          val newPostInactive = postInactive.map { t => t.copy(offset = t.offset + len) }

          def needMore(t: SfToken) = t.tokenType match {
            case Tokens.XML_START_OPEN             => true
            case Tokens.XML_EMPTY_CLOSE            => true
            case Tokens.XML_TAG_CLOSE              => true
            case Tokens.XML_END_OPEN               => true
            case Tokens.XML_WHITESPACE             => true
            case Tokens.XML_ATTR_EQ                => true
            case Tokens.XML_ATTR_VALUE             => true
            case Tokens.XML_NAME                   => true
            case Tokens.XML_PCDATA                 => true
            case Tokens.XML_COMMENT                => true
            case Tokens.XML_CDATA                  => true
            case Tokens.XML_UNPARSED               => true
            case Tokens.XML_PROCESSING_INSTRUCTION => true
            case _                                 => false
          }

          def fragStart(ts: Seq[SfToken], dropped: Int): (Int, Int) = ts match {
            case Seq() => (0, dropped)
            case Seq(x, xs @ _*) =>
              if (dropped == 1) {
                fragStart(xs, dropped + 1)
              }
              else {
                if (needMore(x)) fragStart(xs, dropped + 1) else (x.offset, dropped)
              }
          }

          def fragEnd = newPostInactive match {
            case Seq()           => doc.getLength
            case Seq(x, xs @ _*) => x.offset
          }

          val (lower, dropped) = fragStart(preInactive.reverse, 1)
          val flen = fragEnd - lower
          //          println("Doc fragment - lower: %d, len: %d" format (lower, flen))
          val docFragment = doc.getText(lower, flen)
          //          println("Doc fragment: " + docFragment)
          val newActive = ScalaLexer.rawTokeniseV(docFragment, true).map { t => t.copy(offset = t.offset + lower) }
          //          println("New Active: " + newActive)
          docTokens = preInactive.slice(0, preInactive.size - dropped) ++ newActive.slice(0, newActive.size - 1) ++ newPostInactive
          showTiming(t0, "Incr")
        }
        catch {
          case t: Throwable => println(Utils.stackTraceAsString(t))
        }
      }
    }

    def removeUpdate(e: DocumentEvent) {
      val doc = e.getDocument().getText(0, e.getDocument().getLength())
      lexDoc(doc)
    }

    def changedUpdate(e: DocumentEvent) {
      val doc = e.getDocument().getText(0, e.getDocument().getLength())
      lexDoc(doc)
    }
  }

  override def onInsert(e: DocumentEvent) = docListener.insertUpdate(e)
  override def onRemove(e: DocumentEvent) = docListener.removeUpdate(e)

  override def getCurlyBracesDenoteCodeBlocks = true

  override def getMarkOccurrencesOfTokenType(tpe: Int) =
    tpe == TokenTypes.IDENTIFIER || tpe == TokenTypes.FUNCTION

  override def getShouldIndentNextLineAfter(t: Token) = {
    if (t != null && t.textCount == 1) {
      val ch = t.text(t.textOffset)
      ch == '{' || ch == '('
    }
    else {
      false
    }
  }

  override def getLineCommentStartAndEnd() = Array("//", null)

  override def getLastTokenTypeOnLine(text: Segment, initialTokenType: Int) = {
    TokenTypes.NULL
  }

  def updateDiagnosticFlags() {
    debugOn = if (System.getProperty("kojo.lexer.debug") == "true") true else false
    timingOn = if (System.getProperty("kojo.lexer.timing") == "true") true else false
  }

  def lexDoc(doc: String) {
    updateDiagnosticFlags()
    val t0 = System.currentTimeMillis()
    // The current implementation seems to work fast enough for all practical purposes
    // But here are some additional ideas for optimization:
    // (1) Make ScalaLexer work with a Reader, and provide a reader for Documents that does not copy string/array
    // data around.
    // (2) Retokensize only 'damaged' portions of the document
    docTokens = ScalaLexer.rawTokeniseV(doc, true)
    docTokens = docTokens.slice(0, docTokens.size - 1)
    showTiming(t0, "Full")
  }

  def tokensForLine(segment: Segment, segmentOffset: Int) = {
    def isLastMultiline(ts: Seq[SfToken]) = ts match {
      case Seq() => false
      case ts2 =>
        val t = ts2.last
        if (t.rawText.contains("\n")) true else false
    }

    def splitLastInactive(t: SfToken): Option[SfToken] = {
      val delta = segmentOffset - t.offset
      val upper = if (t.length > delta + segment.count) delta + segment.count else t.length
      val text = t.rawText.slice(delta, upper)
      if (text.size > 0)
        Some(SfToken(t.tokenType, text, t.offset + delta, text))
      else
        None
    }

    def splitLastActive(t: SfToken): Option[SfToken] = {
      val trim = segmentOffset + segment.count - t.offset
      val text = t.rawText.slice(0, trim)
      if (text.size > 0)
        Some(SfToken(t.tokenType, text, t.offset, text))
      else
        None
    }

    val (preInactive, rest) = docTokens.span { t => t.offset < segmentOffset }
    val (active, postInactive) = rest.span { t => t.offset <= segmentOffset + segment.length }

    val lineTokens = new ListBuffer[SfToken]

    if (isLastMultiline(preInactive)) {
      splitLastInactive(preInactive.last).foreach { lineTokens += _ }
    }

    if (isLastMultiline(active)) {
      val (active3, active4) = active.splitAt(active.size - 1)
      active3.foreach { lineTokens += _ }
      splitLastActive(active4(0)).foreach { lineTokens += _ }
    }
    else {
      active.foreach { lineTokens += _ }
    }

    if (lineTokens.size == 0) {
      lineTokens += SfToken(Tokens.EOF, "", segmentOffset, "")
    }

    lineTokens.toList
  }

  override def getTokenList(segment: Segment, initialTokenType: Int, segmentOffset: Int): Token = {
    def addRstaToken(t: SfToken) {
      debug(" %s |" format (t))
      val tRSTATokenStart = t.offset + segment.offset - segmentOffset
      val tRSTATokenEnd = tRSTATokenStart + t.length - 1
      val tRSTATokenOffset = t.offset
      addToken(segment.array, tRSTATokenStart, tRSTATokenEnd, convertTokenType(t.tokenType), tRSTATokenOffset)
    }

    debug("\n--- Getting tokens. Segment Offset: %d, Segment length: %d" format (segmentOffset, segment.length))
    debug("Input Text:" + segment.toString())

    resetTokenList()
    tokensForLine(segment, segmentOffset).foreach { addRstaToken }

    firstToken
  }

  override def getWordsToHighlight: TokenMap = wordsToHighlight

  def convertTokenType(sfType: TokenType): Int = {
    if (Tokens.KEYWORDS.contains(sfType)) {
      TokenTypes.RESERVED_WORD
    }
    else if (Tokens.COMMENTS.contains(sfType)) {
      TokenTypes.COMMENT_MULTILINE
    }
    else {
      sfType match {
        case Tokens.WS                         => TokenTypes.WHITESPACE
        case Tokens.CHARACTER_LITERAL          => TokenTypes.LITERAL_CHAR
        case Tokens.INTEGER_LITERAL            => TokenTypes.LITERAL_NUMBER_DECIMAL_INT
        case Tokens.FLOATING_POINT_LITERAL     => TokenTypes.LITERAL_NUMBER_FLOAT
        case Tokens.STRING_LITERAL             => TokenTypes.LITERAL_STRING_DOUBLE_QUOTE
        case Tokens.STRING_PART                => TokenTypes.LITERAL_CHAR
        case Tokens.SYMBOL_LITERAL             => TokenTypes.LITERAL_STRING_DOUBLE_QUOTE
        case Tokens.TRUE                       => TokenTypes.LITERAL_BOOLEAN
        case Tokens.FALSE                      => TokenTypes.LITERAL_BOOLEAN
        case Tokens.NULL                       => TokenTypes.LITERAL_CHAR
        case Tokens.EOF                        => TokenTypes.WHITESPACE
        case Tokens.LBRACE                     => TokenTypes.SEPARATOR
        case Tokens.RBRACE                     => TokenTypes.SEPARATOR
        case Tokens.LBRACKET                   => TokenTypes.SEPARATOR
        case Tokens.RBRACKET                   => TokenTypes.SEPARATOR
        case Tokens.LPAREN                     => TokenTypes.SEPARATOR
        case Tokens.RPAREN                     => TokenTypes.SEPARATOR

        case Tokens.XML_START_OPEN             => TokenTypes.MARKUP_TAG_DELIMITER
        case Tokens.XML_EMPTY_CLOSE            => TokenTypes.MARKUP_TAG_DELIMITER
        case Tokens.XML_TAG_CLOSE              => TokenTypes.MARKUP_TAG_DELIMITER
        case Tokens.XML_END_OPEN               => TokenTypes.MARKUP_TAG_DELIMITER
        case Tokens.XML_WHITESPACE             => TokenTypes.WHITESPACE
        case Tokens.XML_ATTR_EQ                => TokenTypes.MARKUP_TAG_ATTRIBUTE
        case Tokens.XML_ATTR_VALUE             => TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE
        case Tokens.XML_NAME                   => TokenTypes.MARKUP_TAG_NAME
        case Tokens.XML_PCDATA                 => TokenTypes.IDENTIFIER
        case Tokens.XML_COMMENT                => TokenTypes.COMMENT_MARKUP
        case Tokens.XML_CDATA                  => TokenTypes.MARKUP_CDATA
        case Tokens.XML_UNPARSED               => TokenTypes.MARKUP_CDATA
        case Tokens.XML_PROCESSING_INSTRUCTION => TokenTypes.MARKUP_PROCESSING_INSTRUCTION

        case _                                 => TokenTypes.IDENTIFIER
      }
    }
  }
}