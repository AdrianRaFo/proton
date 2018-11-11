import ProtoGetter.getDependencies
import com.google.protobuf.Descriptors.{FieldDescriptor, FileDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}

import scala.collection.JavaConverters._

object ProtoAlgebraParser {

  //TODO Create Tagless Algebra and parse endpoints
  //TODO Detect if has Streaming
  def generateFile(
      fileDesc: FileDescriptor,
      implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    val b            = CodeGeneratorResponse.File.newBuilder()
    
    val protocolName = ProtoGetter.getFileNameWithoutExtension(fileDesc)
    b.setName(s"$protocolName.scala")
    
    val fp = FunctionalPrinter()
        .add(s"package ${fileDesc.getPackage}")
        .newline
        .add(getDependencies(fileDesc): _*)
        .newline
        .add("import mu.rpc.protocol._")
        .newline
    
    b.setContent(fp.result)
    b.build
  }

}
