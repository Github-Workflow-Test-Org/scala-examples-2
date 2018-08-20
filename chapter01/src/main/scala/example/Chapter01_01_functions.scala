package example

object Chapter01_01_functions {

  def factorial(n: Int): Int = (1 to n).product

  def is_prime(n: Int): Boolean = {
    (2 until n).forall(k ⇒ n % k != 0)
  }

  def count_even(s: Set[Int]): Int = {
    def is_even(k: Int): Int = if (k % 2 == 0) 1 else 0

    s.toSeq.map(k ⇒ is_even(k)).sum
  }

  def count_even_using_val(s: Set[Int]): Int = {
    val is_even = (k: Int) ⇒ if (k % 2 == 0) 1 else 0

    // Need to convert a `Set[Int]` to a sequence (`Seq[Int]`), or else `map` does not work correctly!
    s.toSeq.map(k ⇒ is_even(k)).sum
  }
}
