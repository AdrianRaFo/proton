//import mu.rpc.protocol._
//import shapeless.{:+:, CNil}
//
//sealed trait People extends Product with Serializable
//
//@message final case class Person(name: String, age: Int) extends People
//
//@message final case class NotFoundError(message: String) extends People
//
//@message final case class DuplicatedPersonError(message: String) extends People
//
//@message final case class PeopleRequest(name: String) extends People
//
//@message final case class PeopleResponse(
//    result: Person :+: NotFoundError :+: DuplicatedPersonError :+: CNil)
//    extends People
//
//object Gender extends Enumeration with People {
//  type Gender = Value
//  val Male, Female = Value
//}