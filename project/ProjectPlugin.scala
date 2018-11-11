import sbt.{AutoPlugin, PluginTrigger, _}

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {

    lazy val V = new {
      val fs2        = "1.0.0"
      val muRPC      = "0.16.0"
      val pureconfig = "0.9.1"
    }
  }

}
