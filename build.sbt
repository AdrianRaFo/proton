import ProjectPlugin.autoImport.V

name := "proton"

version := "0.1.0"

scalaVersion := "2.12.7"

resolvers += Resolver.bintrayRepo("beyondthelines", "maven")

scalacOptions := Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Ywarn-unused-import"
)

scalafmtCheck := true

scalafmtOnCompile := true

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

libraryDependencies ++= Seq(
  "io.higherkindness"     %% "mu-rpc-client-core" % V.muRPC,
  "com.github.pureconfig" %% "pureconfig"         % V.pureconfig,
  "com.thesamet.scalapb"  %% "compilerplugin"     % "0.8.2",
  "com.google.protobuf"   % "protobuf-java"       % "3.6.1" % "protobuf"
)

// ScalaPB

// Additional directories to search for imports:
//PB.includePaths in Compile +=

// Changing where to look for protos to compile (default src/main/protobuf):
PB.protoSources in Compile := Seq((Compile / resourceDirectory).value)

// Rarely needed: override where proto files from library dependencies are
// extracted to:
//PB.externalIncludePath := file("/tmp/foo")

// By default we generate into target/src_managed. To customize:
PB.targets in Compile := Seq(
  RPCProtocolGenerator -> (sourceManaged in Compile).value
)
