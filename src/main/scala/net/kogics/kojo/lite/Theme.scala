package net.kogics.kojo.lite

import java.awt.Color
import java.awt.Font

import javax.swing.UIManager
import javax.swing.plaf.FontUIResource

import org.fife.ui.rsyntaxtextarea.{Theme => RTheme}

import com.bulenkov.darcula.DarculaLaf

import net.kogics.kojo.util.Utils

trait Theme {
  def outputPaneFg: Color
  def outputPaneBg: Color
  def canvasBg: Color
  def editorTheme: RTheme
  def loadLookAndFeel(): Unit
  def loadDefaultPerspective(kojoCtx: KojoCtx): Unit
  def toolbarBg: Option[Color]
  def defaultBg: Color
  def textBg: Color
  def textFg: Color
  def errorPaneFg: String
  def errorColor: Color
  def successColor: Color
  def neutralColor: Color
  def tracingBg: Color
  def tracingHighlightColor: Color
  def tracingCallColor: String
  def canvasAxesColor: Color
  def canvasGridColor: Color
  def canvasTickColor: Color
  def canvasTickLabelColor: Color
  def canvasTickIntegerLabelColor: Color
  def runPng: String
  def runwPng: String
  def runtPng: String
  def stopPng: String
}

class DarkTheme extends Theme {
  val canvasBg = new Color(0x424647)
  val editorTheme = RTheme.load(getClass.getResourceAsStream("dark-editor-theme.xml"))
  def loadLookAndFeel(): Unit = {
    val laf = new DarculaLaf
    UIManager.setLookAndFeel(laf)
    UIManager.getLookAndFeelDefaults.put("defaultFont", new FontUIResource("Arial", Font.PLAIN, 12))
  }
  def loadDefaultPerspective(kojoCtx: KojoCtx): Unit = {
    kojoCtx.switchToDefault2Perspective()
  }
  val toolbarBg = None
  val defaultBg = new Color(0x3c3f41)
  val textBg = defaultBg
  val textFg = new Color(0xbbbbbb)
  val outputPaneFg = textFg
  val outputPaneBg = new Color(0x2d2d2d)
  val errorPaneFg = "FF430F"
  val errorColor = new Color(0xFF430F)
  val successColor = new Color(0x10aa10)
  val neutralColor = defaultBg
  val tracingBg = defaultBg
  val tracingHighlightColor = new Color(173, 206, 238)
  val tracingCallColor = "rgb(0,200,50)"
  val canvasAxesColor = new Color(200, 200, 200)
  val canvasGridColor = new Color(100, 100, 100)
  val canvasTickColor = new Color(120, 120, 120)
  val canvasTickLabelColor = new Color(150, 150, 150)
  val canvasTickIntegerLabelColor = textFg
  val runPng = "run-d.png"
  val runwPng = "runw-d.png"
  val runtPng = "runt-d.png"
  val stopPng = "stop-d.png"
}

class LightTheme extends Theme {
  val canvasBg = Color.white
  val editorTheme = RTheme.load(getClass.getResourceAsStream("light-editor-theme.xml"))
  def loadLookAndFeel(): Unit = {
    if (Utils.isMac) {
      // use the system look and feel
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
      UIManager.getLookAndFeelDefaults.put("defaultFont", new FontUIResource("Arial", Font.PLAIN, 12))
    }
    else {
      UIManager.getInstalledLookAndFeels.find { _.getName == "Nimbus" }.foreach { nim =>
        UIManager.setLookAndFeel(nim.getClassName)
      }
    }

  }
  def loadDefaultPerspective(kojoCtx: KojoCtx): Unit = {
    kojoCtx.switchToDefaultPerspective()
  }

  val toolbarBg = Some(new Color(230, 230, 230))
  val defaultBg = Color.white
  val textBg = Color.white
  val textFg = new Color(32, 32, 32)
  val outputPaneFg = textFg
  val outputPaneBg = textBg
  val errorPaneFg = "rgb(240, 0, 0)"
  val errorColor = new Color(220, 0, 0)
  val successColor = new Color(0x33ff33)
  val neutralColor = new Color(0xf0f0f0)
  val tracingBg = Color.white
  val tracingHighlightColor = new Color(173, 206, 238)
  val tracingCallColor = "rgb(0,50,225)"
  val canvasAxesColor = new Color(100, 100, 100)
  val canvasGridColor = new Color(200, 200, 200)
  val canvasTickColor = new Color(150, 150, 150)
  val canvasTickLabelColor = new Color(50, 50, 50)
  val canvasTickIntegerLabelColor = Color.blue
  val runPng = "run.png"
  val runwPng = "runw.png"
  val runtPng = "runt.png"
  val stopPng = "stop.png"
}

object Theme {
  val currentTheme: Theme = {
    Utils.appProperty("theme") match {
      case Some("dark") => new DarkTheme
      case Some(_)      => new LightTheme
      case None         => new LightTheme
    }
  }
}
