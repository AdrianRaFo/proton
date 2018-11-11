import ProtoAlgebraParser._
import com.google.protobuf.DescriptorProtos.MethodOptions
import com.google.protobuf.Descriptors._

import scala.collection.JavaConverters._

object ProtoGetter {
  val StreamingOptionIndex = 1047

  def getFileNameWithoutExtension(fileDesc: FileDescriptor) = fileDesc.getName.split('.').head

  def toCamelCase(str: String): String =
    "_([a-z])".r.replaceAllIn(str, m => m.group(1).toUpperCase())

  def descapitalize(str: String): String =
    "[A-Z]".r.replaceFirstIn(str, str.head.toString.toLowerCase)

  def getTypeName(field: FieldDescriptor) =
    field.getType match {
      case msg if msg.toString.contains("MESSAGE") =>
        val fullName = field.getMessageType.getFullName
        if (fullName.contains(field.getContainingType.getName)) field.getMessageType.getName
        else fullName
      case tp => tp.getJavaType.toString.toLowerCase.capitalize
    }

  def getDependencies(fileDesc: FileDescriptor): List[String] =
    fileDesc.getPublicDependencies.asScala.map(dep => s"import ${dep.getPackage}._").toList

  def getStreamingOption(methodDesc: MethodDescriptor): Streaming.Value = {
    def mapOptions(options: MethodOptions): Map[Int, String] =
      if (options.toString.nonEmpty)
        options.toString
          .split("\n")
          .flatMap(_.split(':'))
          .grouped(2)
          .toList
          .groupBy(_.head.toInt)
          .mapValues(_.flatten.last.trim)
      else Map.empty

    mapOptions(methodDesc.getOptions)
      .get(StreamingOptionIndex)
      .map(value => Streaming(value.toInt))
      .getOrElse(Streaming.none)
  }

  def getAllEnums(allMessages: List[Descriptor]): List[EnumDescriptor] =
    allMessages.flatMap(_.getEnumTypes.asScala)

  def getAllMessages(topMessages: List[Descriptor]): List[Descriptor] = {

    val nestedMessages =
      if (topMessages.nonEmpty) topMessages.head.getNestedTypes.asScala.toList else List.empty

    if (nestedMessages.nonEmpty)
      getAllMessages(nestedMessages) ++: nestedMessages ++: getAllMessages(topMessages.tail)
    else if (topMessages.nonEmpty) nestedMessages ++: getAllMessages(topMessages.tail)
    else nestedMessages
  }
}
