import com.google.protobuf.Descriptors.{FieldDescriptor, FileDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}

import scala.collection.JavaConverters._

object ProtoAlgebraParser {

  //TODO Create Tagless Algebra and parse endpoints
  def generateFile(
      fileDesc: FileDescriptor,
      implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    val b            = CodeGeneratorResponse.File.newBuilder()
    val protocolName = getNameWithoutExtension(fileDesc)
    b.setName(s"$protocolName.scala")
    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.getPackage}")
      .newline
      .add("import mu.rpc.protocol._")
      .newline
      .add(s"sealed trait $protocolName extends Product with Serializable")
      .newline
      .print(fileDesc.getMessageTypes.asScala) {
        case (p, m) =>
          val messagePrinter = p.add(s"@message final case class ${m.getName}(").indent
          val fields         = parseFields(m.getFields.asScala.toList)
          messagePrinter
            .add(fields: _*)
            .outdent
            .add(s") extends $protocolName")
      }
    b.setContent(fp.result)
    b.build
  }

  def getNameWithoutExtension(fileDesc: FileDescriptor) = fileDesc.getName.split('.').head

  def parseFields(fields: List[FieldDescriptor]): List[String] = {
    def parse(field: FieldDescriptor) = s"${field.getName}: ${parseProtoType(field.getType)}"
    if (fields.nonEmpty && fields.size > 1)
      (parse(fields.head) + ",") :: parseFields(fields.tail)
    else if (fields.nonEmpty) List(parse(fields.head))
    else List.empty
  }

  def parseProtoType(fieldType: FieldDescriptor.Type): String =
    fieldType.getJavaType.toString.toLowerCase.capitalize
}
