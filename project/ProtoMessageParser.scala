import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FileDescriptor.Syntax
import com.google.protobuf.Descriptors.{FieldDescriptor, FileDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}
import ProtoGetter._

import scala.collection.JavaConverters._

object ProtoMessageParser {
  val nullMessage = "Null"

  def generateFile(
      fileDesc: FileDescriptor,
      implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    val protocolName = getFileNameWithoutExtension(fileDesc)
    b.setName(s"$protocolName.scala")

    val messages = {
      val topMessages = fileDesc.getMessageTypes.asScala.toList
      getAllMessages(topMessages) ++: topMessages
    }

    val imports = {
      val hasCoproducts = !messages.flatMap(_.getOneofs.asScala.map(_.getFieldCount)).forall(_ < 3)

      List("import mu.rpc.protocol._") ++
        (if (hasCoproducts) List("import shapeless.{:+:, CNil}") else List.empty)
    }

    val enums =
      parseEnum(protocolName, getAllEnums(messages) ++: fileDesc.getEnumTypes.asScala.toList)

    val protocolTrait =
      if (enums.nonEmpty) s"sealed trait $protocolName"
      else s"sealed trait $protocolName extends Product with Serializable"

    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.getPackage}")
      .newline
      .add(getDependencies(fileDesc): _*)
      .add(imports: _*)
      .newline
      .add(protocolTrait)
      .add(enums: _*)
      .print(messages) {
        case (messagePrinter, message) =>
          val mFields = {
            val fields =
              parseFields(message.getFields.asScala.toList, fileDesc.getSyntax == Syntax.PROTO2)

            val oneOfFields = message.getOneofs.asScala.toList

            if (oneOfFields.nonEmpty)
              oneOfFields.flatMap { oneOf =>
                val fieldIndex = oneOf.getFields

                fields.patch(fieldIndex.get(0).getIndex, List(parseOneOf(oneOf)), fieldIndex.size())
              }.distinct
            else fields
          }

          if (mFields.nonEmpty)
            messagePrinter
              .add(s"@message final case class ${message.getName}(")
              .indent
              .add(mFields.mkString(", "))
              .outdent
              .add(s") extends $protocolName")
              .newline
          else
            messagePrinter
              .add(s"@message case object ${message.getName} extends $protocolName")
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

  def parseOneOf(oneOfDesc: Descriptors.OneofDescriptor): String = {

    val fields       = oneOfDesc.getFields.asScala.map(getTypeName)
    val optionalType = fields.filterNot(_ contains nullMessage).headOption.getOrElse("String")

    s"${oneOfDesc.getName}: " + (oneOfDesc.getFieldCount match {
      case x if x == 2 =>
        if (fields.exists(_ contains nullMessage))
          s"Option[$optionalType]"
        else s"Either[${fields.last}, ${fields.head}]"
      case _ => (fields :+ "CNil").mkString(" :+: ")
    })
  }

  def parseFields(fields: List[FieldDescriptor], findOptional: Boolean): List[String] =
    fields.map(field =>
      s"${toCamelCase(field.getName)}: " + (getTypeName(field) match {
        case opt if field.isOptional && findOptional => s"Option[$opt]"
        case list if field.isRepeated                => s"List[$list]"
        case single                                  => single
      }))

}
