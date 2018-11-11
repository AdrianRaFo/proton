import com.google.protobuf.Descriptors._
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import scalapb.compiler.DescriptorImplicits
import scalapb.options.compiler.Scalapb

import scala.collection.JavaConverters._

object RPCProtocolGenerator extends protocbridge.ProtocCodeGenerator {
  val params = scalapb.compiler.GeneratorParams()

  def run(input: Array[Byte]): Array[Byte] = {
    val registry = ExtensionRegistry.newInstance()
    Scalapb.registerAllExtensions(registry)
    val request = CodeGeneratorRequest.parseFrom(input)
    val b       = CodeGeneratorResponse.newBuilder

    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc)
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
      }

    val implicits = new DescriptorImplicits(params, fileDescByName.values.toVector)

    //TODO order by deps properly
    //TODO allow include messages on the same file as the services
    request.getFileToGenerateList.asScala
      .map(fileDescByName)
      .sortBy(_.getDependencies.size())
      .foreach { fileDesc =>
        val responseFile =
          if (fileDesc.getServices.isEmpty) ProtoMessageParser.generateFile(fileDesc, implicits)
          else ProtoAlgebraParser.generateFile(fileDesc, implicits)
        b.addFile(responseFile)
      }
    b.build.toByteArray
  }

}
