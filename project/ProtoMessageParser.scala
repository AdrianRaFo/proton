import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FileDescriptor.Syntax
import com.google.protobuf.Descriptors.{FieldDescriptor, FileDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}

import scala.collection.JavaConverters._

object ProtoMessageParser {

  def generateFile(
      fileDesc: FileDescriptor,
      implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    val protocolName = getNameWithoutExtension(fileDesc)
    b.setName(s"$protocolName.scala")

    val deps =
      (fileDesc.getDependencies.asScala ++ fileDesc.getPublicDependencies.asScala).map(dep =>
        s"import ${dep.getPackage}._")

    //TODO recursive
    val nestedMessages = fileDesc.getMessageTypes.asScala.flatMap(_.getNestedTypes.asScala)
    val messages       = nestedMessages ++: fileDesc.getMessageTypes.asScala

    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.getPackage}")
      .newline
      .add(deps: _*)
      .newline
      .add("import mu.rpc.protocol._")
      .newline
      .add(s"sealed trait $protocolName extends Product with Serializable")
      .newline
      .print(messages) {
        case (p, m) =>
          val messagePrinter = p.add(s"@message final case class ${m.getName}(").indent

          //TODO parse enums

          val fields = parseFields(m.getFields.asScala.toList)

          val oneOfFields = m.getOneofs.asScala.toList

          val parsedFields =
            if (oneOfFields.nonEmpty)
              oneOfFields.flatMap { oneOf =>
                val fieldIndex = oneOf.getFields

                fields.patch(
                  fieldIndex.get(0).getIndex,
                  List(parseOneOf(oneOf, fileDesc.getSyntax == Syntax.PROTO3)),
                  fieldIndex.size())
              }.distinct
            else fields

          messagePrinter
            .add(parsedFields.mkString(" "))
            .outdent
            .add(s") extends $protocolName")
            .newline
      }
    b.setContent(fp.result)
    b.build
  }

  def getNameWithoutExtension(fileDesc: FileDescriptor) = fileDesc.getName.split('.').head

  //TODO parse oneof either or coproduct
  // TODO ask for Null(proto3) as option
  def parseOneOf(oneOfDesc: Descriptors.OneofDescriptor, findOptional: Boolean): String = ""

  //TODO ask for optional(proto2)
  //TODO ask for repeated as list
  def parseFields(fields: List[FieldDescriptor]): List[String] = {

    def parse(field: FieldDescriptor) = s"${field.getName}: ${getTypeName(field)}"
    if (fields.nonEmpty && fields.size > 1)
      (parse(fields.head) + ",") :: parseFields(fields.tail)
    else if (fields.nonEmpty) List(parse(fields.head))
    else List.empty
  }

  def getTypeName(field: FieldDescriptor) =
    field.getType match {
      case msg if msg.toString.contains("MESSAGE") => field.getMessageType.getName
      case tp                                      => tp.getJavaType.toString.toLowerCase.capitalize
    }
}
