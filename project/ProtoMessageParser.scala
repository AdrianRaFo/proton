import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FileDescriptor.Syntax
import com.google.protobuf.Descriptors.{FieldDescriptor, FileDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}
import ProtoGetter._

import scala.collection.JavaConverters._

object ProtoMessageParser {

  def generateFile(
      fileDesc: FileDescriptor,
      implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    val protocolName = getFileNameWithoutExtension(fileDesc)
    b.setName(s"$protocolName.scala")

    val protoDeps =
      (fileDesc.getDependencies.asScala ++ fileDesc.getPublicDependencies.asScala).map(dep =>
        s"import ${dep.getPackage}._")

    val topMessages = fileDesc.getMessageTypes.asScala.toList

    val messages = getAllMessages(topMessages) ++: topMessages

    val hasCoproducts = !messages.flatMap(_.getOneofs.asScala.map(_.getFieldCount)).forall(_ < 3)

    val deps = List("import mu.rpc.protocol._") ++
      (if (hasCoproducts) List("import shapeless.{:+:, CNil}") else List.empty)

    val enums =
      parseEnum(protocolName, getAllEnums(messages) ++: fileDesc.getEnumTypes.asScala.toList)

    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.getPackage}")
      .newline
      .add(protoDeps: _*)
      .newline
      .add(deps: _*)
      .newline
      .add(s"sealed trait $protocolName extends Product with Serializable")
      .newline
      .add(enums: _*)
      .newline
      .print(messages) {
        case (p, m) =>
          val messagePrinter = p.add(s"@message final case class ${m.getName}(").indent

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

  def parseEnum(protocolName: String, enums: List[Descriptors.EnumDescriptor]): List[String] =
    enums.map { enumDesc => s"""
       |object ${enumDesc.getName} extends Enumeration with $protocolName {
       |  type ${enumDesc.getName} = Value
       |  val ${enumDesc.getValues.asScala.map(_.getName).mkString(", ")} = Value
       |}
     """.stripMargin
    }

  def parseOneOf(oneOfDesc: Descriptors.OneofDescriptor, findOptional: Boolean): String = {
    val nullMessage = "Null"

    val fields = oneOfDesc.getFields.asScala.map(getTypeName)
    val optionalType = fields.filterNot(_ == nullMessage).headOption.getOrElse("String")

    s"${oneOfDesc.getName}: " + (oneOfDesc.getFieldCount match {
      case x if x == 2 =>
        if (fields.contains(nullMessage))
          s"Option[$optionalType]"
        else s"Either[${fields.last}, ${fields.head}]"
      case _ => (fields :+ "CNil").mkString(":+:")
    })
  }

  def parseFields(fields: List[FieldDescriptor]): List[String] = {

    def parse(field: FieldDescriptor) =
      s"${toCamelCase(field.getName)}: " + getTypeName(field) match {
        case opt if field.isOptional  => s"Option[$opt]"
        case list if field.isRepeated => s"List[$list]"
        case single                   => single
      }

    if (fields.nonEmpty && fields.size > 1)
      (parse(fields.head) + ",") :: parseFields(fields.tail)
    else if (fields.nonEmpty) List(parse(fields.head))
    else List.empty
  }

}
