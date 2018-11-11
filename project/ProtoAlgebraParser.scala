import ProtoGetter.getDependencies
import com.google.protobuf.Descriptors.{FileDescriptor, MethodDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}
import ProtoGetter._
import com.google.protobuf.Descriptors

import scala.collection.JavaConverters._

object ProtoAlgebraParser {

  object Streaming extends Enumeration {
    type Streaming = Value
    val none, input, output, bidirectional = Value
  }

//TODO Create Tagless Algebra and parse endpoints
  def generateFile(
      fileDesc: FileDescriptor,
      implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    val protocolName = ProtoGetter.getFileNameWithoutExtension(fileDesc)
    b.setName(s"$protocolName.scala")

    val services = fileDesc.getServices.asScala

    val imports = {
      val hasStreaming = !services
        .flatMap(_.getMethods.asScala.toList)
        .map(getStreamingOption)
        .forall(_ == Streaming.none)

      List("import mu.rpc.protocol._") ++
        (if (hasStreaming) List("import fs2._") 
         else List.empty)
    }

    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.getPackage}")
      .newline
      .add(getDependencies(fileDesc): _*)
      .add(imports: _*)
      .newline
      .print(services) {
        case (servicePrinter, service) =>
          servicePrinter
            .add(s"@service(Protobuf) trait ${service.getName}[F[_]] {")
            .newline
            .indent
            .add(service.getMethods.asScala.map(parseEndpoint):_*)
            .outdent
            .newline
            .add("}")
      }

    b.setContent(fp.result)
    b.build
  }

  def parseEndpoint(methodDesc: MethodDescriptor): String = {
    val inputType = methodDesc.getInputType.getFullName
    val outputType = methodDesc.getOutputType.getFullName
    
    s"def ${toCamelCase(descapitalize(methodDesc.getName))}(request: " + (getStreamingOption(methodDesc) match {
      case Streaming.none =>
        s"$inputType): F[$outputType]"
      case Streaming.output =>
        s"$inputType): Stream[F, $outputType]"
      case Streaming.input =>
        s"Stream[F, $inputType]): [$outputType]"
      case Streaming.bidirectional =>
        s"Stream[F, $inputType]): Stream[F, $outputType]"
    })
  }

}
