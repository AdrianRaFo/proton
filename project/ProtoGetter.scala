import com.google.protobuf.Descriptors._

import scala.collection.JavaConverters._

object ProtoGetter {
  def getFileNameWithoutExtension(fileDesc: FileDescriptor) = fileDesc.getName.split('.').head

  def toCamelCase(str: String): String =
    "_([a-z])".r.replaceAllIn(str, m => m.group(1).toUpperCase())

  def getTypeName(field: FieldDescriptor) =
    field.getType match {
      case msg if msg.toString.contains("MESSAGE") => field.getMessageType.getName
      case tp                                      => tp.getJavaType.toString.toLowerCase.capitalize
    }

  def getAllEnums(allMessages: List[Descriptor]): List[EnumDescriptor] =
    allMessages.flatMap(_.getEnumTypes.asScala)

  def getAllMessages(topMessages: List[Descriptor]): List[Descriptor] = {

    val nestedMessages =
      if (topMessages.nonEmpty) topMessages.head.getNestedTypes.asScala.toList else List.empty

    if (nestedMessages.nonEmpty)
      getAllMessages(nestedMessages) ++: nestedMessages ++: getAllMessages(topMessages.tail)
    else nestedMessages ++: getAllMessages(topMessages.tail)
  }
}
