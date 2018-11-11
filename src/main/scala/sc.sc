val l1 = List("a", "b", "c", "d")
val l2 = List(3, 4)
val l2P = "cd"


l1.patch(l2.head-1, List(l2P), l2.size)