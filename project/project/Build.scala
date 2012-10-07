import sbt._
object PluginDef extends Build {
  override def projects = Seq(root)
  lazy val root = Project("plugins", file(".")) dependsOn(ghpages,ssa)
  lazy val ghpages = uri("git://github.com/jsuereth/xsbt-ghpages-plugin.git")
  lazy val ssa = uri("git://github.com/sbt-android-mill/sbt-source-align.git#0.2")
  //lazy val pamflet = uri("git://github.com/n8han/pamflet-plugin.git#0.4.1")
}
