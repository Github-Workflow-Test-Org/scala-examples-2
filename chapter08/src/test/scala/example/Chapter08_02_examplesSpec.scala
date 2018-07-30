package example

import cats.kernel.Monoid
import cats.{Applicative, Contravariant, Functor, Monad}
import WuZip._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.monoid._
import org.scalatest.{FlatSpec, Matchers}
import io.chymyst.ch._

class Chapter08_02_examplesSpec extends FlatSpec with Matchers {

  behavior of "examples"

  it should "define mapN for Either" in {
    type Op[A] = Either[String, A]

    def map2[A, B, Z](a: Op[A], b: Op[B])(f: (A, B) ⇒ Z): Op[Z] = (a, b) match {
      case (Left(s1), Left(s2)) ⇒ Left(s1 + s2) // Concatenate the two error messages.
      case (Left(s), Right(_)) ⇒ Left(s)
      case (Right(_), Left(s)) ⇒ Left(s)
      case (Right(x1), Right(x2)) ⇒ Right(f(x1, x2))
    }

    // Generalize to mapN in two ways:
    // 1. Use a list of arguments.
    // 2. Use curried arguments in some clever way.

    // 1. Recursively deconstruct a list of arguments.
    def mapN1[A](xs: List[Op[A]]): Op[List[A]] = xs match {
      case Nil ⇒ Right(Nil)
      case head :: tl ⇒ map2(head, mapN1(tl)) { (x, t) ⇒ x :: t }
    }

    // 2. Define a curried version of `map2`, called `fmap2`:
    /*
        def fmap2[A, B, Z](f: A ⇒ B ⇒ Z): Op[A] ⇒ Op[B] ⇒ Op[Z] = {
          opa ⇒
            opb ⇒
              map2(opa, opb) { (x, y) ⇒ f(x)(y) }
        }
  
    Why can't the usual `fmap` work this way? Here's the definition of `fmap` for `Op`:
    */
    def fmap[A, B](f: A ⇒ B): Op[A] ⇒ Op[B] = _.map(f) // Just use .map() on Either.

    // For convenience, define an infix syntax for `fmap`, 
    // so that we can write `f <@> fa` instead of `fa.map(f)`.
    // This implements the short notation `f ↑ fa`.
    implicit class FmapSyntax[A, B](val f: A ⇒ B) {
      def <@>[F[_] : Functor](fa: F[A]): F[B] = fa.map(f)
    }

    import cats.instances.either._ // Enable the functor instance for Either.

    /* 
  Now, if we apply `fmap` to `f: A ⇒ (B ⇒ Z)` then we get
    `fmap(f): Op[A] ⇒ Op[B ⇒ Z].
  But we want Op[A] ⇒ (Op[B] ⇒ Op[Z]) instead.
  This can be obtained if we could somehow transform Op[B ⇒ Z] into Op[B] ⇒ Op[Z].
  This function is usually called `ap`: 
    */
    def ap[A, B]: Op[A ⇒ B] ⇒ Op[A] ⇒ Op[B] = {
      opab ⇒
        opa ⇒
          (opab, opa) match {
            case (Left(s1), Left(s2)) ⇒ Left(s1 + s2) // Concatenate the two error messages.
            case (Left(s), Right(_)) ⇒ Left(s)
            case (Right(_), Left(s)) ⇒ Left(s)
            case (Right(x1), Right(x2)) ⇒ Right(x1(x2))
          }
    }

    // For convenience, define an infix syntax for `ap`:
    // (so that we can write `fab <*> fa` instead of `ap(fab)(fa)`)
    implicit class ApSyntax[A, B](val opab: Op[A ⇒ B]) {
      def <*>(opa: Op[A]): Op[B] = ap(opab)(opa)
    }

    // Define fmap2 through ap and fmap:
    def fmap2[A, B, Z](f: A ⇒ B ⇒ Z): Op[A] ⇒ Op[B] ⇒ Op[Z] = {
      opa ⇒
        opb ⇒
          // Here we need to return an Op[Z].
          //    val x: Op[B ⇒ Z] = fmap(f)(opa)
          // Use `ap` on that x and obtain Op[B] ⇒ Op[Z].
          // So then `ap(x)(opb)` will be of type Op[Z] as required.
          // Use the infix syntax  to write `f <@> opa` instead of `fmap(f)(opa)`
          // and `x <*> opb` instead of `ap(x)(opb)`.
          //
          // Note that <@> and <*> associate to the left, so we can simply write this:
          f <@> opa <*> opb // This syntax now looks similar to `f (opa) (opb)` with the special separators <@> and <*>.
    }

    def fmap3[A, B, C, Z](f: A ⇒ B ⇒ C ⇒ Z): Op[A] ⇒ Op[B] ⇒ Op[C] ⇒ Op[Z] = {
      opa ⇒ opb ⇒ opc ⇒ f <@> opa <*> opb <*> opc
    }

    def fmap4[A, B, C, D, Z](f: A ⇒ B ⇒ C ⇒ D ⇒ Z): Op[A] ⇒ Op[B] ⇒ Op[C] ⇒ Op[D] ⇒ Op[Z] = {
      opa ⇒ opb ⇒ opc ⇒ opd ⇒ f <@> opa <*> opb <*> opc <*> opd
    }

    // Let's test this new syntax.
    // Instead of calling `mapN`, we could just use <@> and <*> directly:
    def safeDivide(x: Double, y: Double): Op[Double] = if (y == 0.0)
      Left(s"Error: dividing $x by 0\n")
    else Right(x / y)

    val f: Double ⇒ Double ⇒ Double = { x ⇒ y ⇒ x + y }

    val res: Op[Double] = f <@> safeDivide(2, 1) <*> safeDivide(4, 2)

    res shouldEqual Right(4.0)

    // Create a validated case class.
    case class C2(x: Double, y: Double)

    val result: Op[C2] = (C2.apply _).curried <@> safeDivide(2, 1) <*> safeDivide(4, 2)
    result shouldEqual Right(C2(2.0, 2.0))
  }

  it should "define construction 1 for applicative functors" in {
    // (a) Constant functor F[A] = 1.
    /*
    All methods return `()`. All laws are trivially satisfied.
     */

    // (b) Identity functor F[A] = A.
    implicit def functorB: Functor[Lambda[A ⇒ A]] = new Functor[Lambda[A ⇒ A]] {
      override def map[A, B](fa: A)(f: A ⇒ B): B = f(fa)
    }

    implicit def construction1b: WuZip[Lambda[A ⇒ A]] = new WuZip[Lambda[A ⇒ A]] {
      override def wu: Unit = ()

      override def zip[A, B](fa: A, fb: B): (A, B) = (fa, fb)
    }
    /* Check the laws: They are satisfied trivially, by definition of `≅`.
      Associativity: (fa, (fb, fc)) ≅ ((fa, fb), fc).
      Identity: ( (), fa ) ≅ fa ;  ( fa, () ) ≅ fa.
     */
  }

  it should "define construction 2 for applicative functors" in {
    // If G and H are applicative then G × H is also applicative.
    // Define pure as G.pure ⊗ H.pure. Similarly, define zip and ap.
    // E.g. zip:  G[A] × H[A] × G[B] × H[B] ⇒ G[A × B] × H[A × B].

    implicit def functorProduct[G[_] : Functor, H[_] : Functor]: Functor[Lambda[A ⇒ (G[A], H[A])]] = new Functor[Lambda[A ⇒ (G[A], H[A])]] {
      override def map[A, B](fa: (G[A], H[A]))(f: A ⇒ B): (G[B], H[B]) = (Functor[G].map(fa._1)(f), Functor[H].map(fa._2)(f))
    }

    implicit def construction2[G[_] : WuZip, H[_] : WuZip]: WuZip[Lambda[A ⇒ (G[A], H[A])]] =
      new WuZip[Lambda[A ⇒ (G[A], H[A])]] {
        override def wu: (G[Unit], H[Unit]) = (WuZip[G].wu, WuZip[H].wu)

        override def zip[A, B](fa: (G[A], H[A]), fb: (G[B], H[B])): (G[(A, B)], H[(A, B)]) =
          (WuZip[G].zip(fa._1, fb._1), WuZip[H].zip(fa._2, fb._2))
      }

    /* The laws hold separately in each part of the pair because, by assumption, they hold for G and H.
    Therefore, the laws hold for G × H:

    Associativity:
    ( (ga, ha) zip (gb, hb) ) zip (gc, hc) = (ga zip gb, ha zip hb) zip (gc, hc) ≅ (ga zip gb zip gc, ha zip hb zip hc)
    We can use associativity for G and H, e.g. (ga zip gb) zip gc = ga zip (gb zip gc), and establish that the above equals
    (ga, ha) zip ( (gb, hb) zip (gc, hc) ) = (ga, ha) zip (gb zip gc, hb zip hc) ≅ (ga zip gb zip gc, ha zip hb zip hc)

    Left identity:
    (gwu, hwu) zip (ga, ha) = (gwu zip ga, hwu zip ha) ≅ (ga, ha) since the identity laws hold for G and H.
    Right identity is shown similarly.
    */
  }

  it should "define construction 3 for applicative functors" in {
    // Functor F[A] = A + G[A].
    implicit def functorAG[G[_] : Functor]: Functor[Lambda[A ⇒ Either[A, G[A]]]] = new Functor[Lambda[A ⇒ Either[A, G[A]]]] {
      override def map[A, B](fa: Either[A, G[A]])(f: A ⇒ B): Either[B, G[B]] = fa match {
        case Left(a) ⇒ Left(f(a))
        case Right(ga) ⇒ Right(Functor[G].map(ga)(f))
      }
    }

    implicit def construction3[G[_] : WuZip]: WuZip[Lambda[A ⇒ Either[A, G[A]]]] = new WuZip[Lambda[A ⇒ Either[A, G[A]]]] {
      override def wu: Either[Unit, G[Unit]] = Left(()) // It turns out that `Right(WuZip[G].wu)` does not obey identity laws!

      override def zip[A, B](fa: Either[A, G[A]], fb: Either[B, G[B]]): Either[(A, B), G[(A, B)]] = (fa, fb) match {
        case (Left(a), Left(b)) ⇒ Left((a, b))
        // If we have an `a: A` and a `gb: G[B]`, we can't return Left[(A, B)], so we need to return Right[G[(A, B)]].
        // Lift `A` to `G[A]` using G's `pure`:
        case (Left(a), Right(gb)) ⇒ Right(WuZip[G].zip(WuZip[G].pure(a), gb))
        case (Right(ga), Left(b)) ⇒ Right(WuZip[G].zip(ga, WuZip[G].pure(b)))
        case (Right(ga), Right(gb)) ⇒ Right(WuZip[G].zip(ga, gb))
      }
    }

    /* Check the laws:
    
    Associativity: To verify that, consider (A + G[A]) zip (B + G[B]) zip (C + G[C]).
    If all 3 `Either`s are `Left`, we obtain the triple (A, B, C). This is associative.
    Otherwise, the values are lifted into the functor `G` using G's `pure` and then zipped.
    We know that G's `zip` is associative. Therefore, `zip` is associative for A + G[A].
    
    Identity: The wrapped unit is `1 + 0`. 
    Consider the right identity law: (A + G[A]) zip (1 + 0).
    If we have Left(a), the result will be Left( (a, 1) ). This is equivalent to Left(a).
    If we have Right(G[A]), we will lift 1 into G using `pure`. The result is, by definition, G's `wu`.
    Hence we will have 
    zip(Right(ga), Left(())) = Right ( WuZip[G].zip(ga, WuZip[G].wu) ).
    Since the identity law holds for G, zipping ga with wu is equivalent to ga. Hence the identity laws hold. 
    
    On the other hand, if we defined the wrapped unit as `0 + wu[G]`, and we consider
    zip( Left(a), Right(WuZip[G].wu) ) = Right( ... )
    Whatever the result, it's a `Right(...)`, which cannot be equivalent to `Left(a)`.
    So the `wu` must be defined as a `Left(())` as we did.
     */
  }

  it should "define construction 6 for applicative functors" in {
    // Construction 6. Constant functor F[A] = Z where Z is a monoid.

    implicit def functorA[Z]: Functor[Lambda[A ⇒ Z]] = new Functor[Lambda[A ⇒ Z]] {
      override def map[A, B](fa: Z)(f: A ⇒ B): Z = fa
    }

    implicit def construction1a[Z: Monoid]: WuZip[Lambda[A ⇒ Z]] = new WuZip[Lambda[A ⇒ Z]] {
      override def wu: Z = Monoid[Z].empty

      override def zip[A, B](fa: Z, fb: Z): Z = fa |+| fb

      // Note: We could have defined this to be `fb |+| fa`, it would still work.
    }

    // The laws hold because the monoid laws hold for Z.

    // Note that this is not a monad because monadic identity laws fail:
    implicit def badMonad[Z: Monoid]: CatsMonad[Lambda[A ⇒ Z]] = new CatsMonad[Lambda[A ⇒ Z]] {
      // No choice here: can't use `x` to compute a `Z`.
      override def pure[A](x: A): Z = Monoid[Z].empty

      // We don't have an `A`, so can't use `f` at all (we are losing information!).
      // The choice here is between returning `fa` and returning `empty`.
      override def flatMap[A, B](fa: Z)(f: A ⇒ Z): Z = fa
    }
    // The left identity law: `pure andThen flatMap(f) = f`.
    // This law cannot hold because flatMap does not use its argument `f`, i.e. it loses information.
  }

  it should "define construction 7 for applicative functors" in {
    // Functor F[A] = Z + G[A], where Z is a monoid and G is applicative.
    implicit def functorAG[G[_] : Functor, Z]: Functor[Lambda[A ⇒ Either[Z, G[A]]]] = new Functor[Lambda[A ⇒ Either[Z, G[A]]]] {
      override def map[A, B](fa: Either[Z, G[A]])(f: A ⇒ B): Either[Z, G[B]] = fa match {
        case Left(z) ⇒ Left(z)
        case Right(ga) ⇒ Right(Functor[G].map(ga)(f))
      }
    }

    implicit def construction7[G[_] : WuZip, Z: Monoid]: WuZip[Lambda[A ⇒ Either[Z, G[A]]]] = new WuZip[Lambda[A ⇒ Either[Z, G[A]]]] {
      override def wu: Either[Z, G[Unit]] = Right(WuZip[G].wu) // It turns out that `Left(Monoid[Z].empty)` does not obey identity laws!

      override def zip[A, B](fa: Either[Z, G[A]], fb: Either[Z, G[B]]): Either[Z, G[(A, B)]] = (fa, fb) match {
        case (Left(x), Left(y)) ⇒ Left(x |+| y)
        // If we have an `a: Z` and a `gb: G[B]`, we can't return Right[G[(A, B)]], so we need to return Left[Z].
        case (Left(z), Right(_)) ⇒ Left(z)
        case (Right(_), Left(z)) ⇒ Left(z)
        case (Right(ga), Right(gb)) ⇒ Right(WuZip[G].zip(ga, gb))
      }
    }

    /* Check the laws:
    
    Associativity: To verify that, consider (Z + G[A]) zip (Z + G[B]) zip (Z + G[C]).
    If at least some of the 3 `Either`s are `Left`, we obtain the result `z1 |+| z2 |+| z3` or perhaps with fewer `z`s.
    In any case, this is associative since Z is a monoid.
    
    Otherwise, we have `ga zip gb zip gc`, and we know that G's `zip` is associative. So this is associative as well.
    
    Therefore, `zip` is associative for Z + G[A].
    
    Identity: The wrapped unit is `0 + wuG`. 
    Consider the right identity law: (Z + G[A]) zip (0 + wuG).
    If we have Left(z), the result is Left(z).
    If we have Right(ga), we will have ga zip wuG = ga since the identity law holds for G.  
    Hence in both cases the identity law holds for Z + G[A]. 
    
    On the other hand, if we defined the wrapped unit as `Left(Monoid[Z].empty)`, and we consider
    zip( Left(Monoid[Z].empty), Right(ga) ) = Left(Monoid[Z].empty)
    This cannot be equivalent to Right(ga), which breaks the identity law.
     */
  }

  it should "define construction 8 for applicative functors" in {
    // Functor F[A] = G[H[A]].

    implicit def functorGH[G[_] : Functor, H[_] : Functor]: Functor[Lambda[A ⇒ G[H[A]]]] = new Functor[Lambda[A ⇒ G[H[A]]]] {
      override def map[A, B](fa: G[H[A]])(f: A ⇒ B): G[H[B]] = fa.map(_.map(f))
    }

    // Define zip as G[H[A]] × G[H[B]] ⇒ G[H[A × B]] using zip for G and zip for H.

    implicit def wuzipGH[G[_] : WuZip, H[_] : WuZip]: WuZip[Lambda[A ⇒ G[H[A]]]] = new WuZip[Lambda[A ⇒ G[H[A]]]] {
      override def wu: G[H[Unit]] = WuZip[G].pure(WuZip[H].wu)

      override def zip[A, B](fa: G[H[A]], fb: G[H[B]]): G[H[(A, B)]] =
        WuZip[G].zip(fa, fb).map { case (ha, hb) ⇒ WuZip[H].zip(ha, hb) }
    }

    /* Check the laws:
    
    Associativity: It is convenient to work in terms of G's `map2` because we will have many expressions of the kind `zip().map()`.
    
    (gha zip ghb) = zipG(gha, ghb).map(zipH) = map2(gha, ghb)(zipH)
    
    (gha zip ghb) zip ghc = map2 ( map2(gha, ghb)(zipH[A, B]), ghc)(zipH[(A, B), C])
    
    By the map2 naturality law, we can pull zipH[A, B] out:
    
    (gha zip ghb) zip ghc = map2 ( map2(gha, ghb)((_, _)), ghc) { case ((a,b),c) ⇒ zipH(zipH(a, b), c) }
    
    Similarly we get
    gha zip (ghb zip ghc) = map2( gha, map2(ghb, ghc)((_, _))) { case (a, (b, c)) ⇒ zipH(a, zipH(b, c)) }
    
    Now, since G's `map2` satisfies associativity, and `zipH` also does, we see that these two expressions are equivalent in the sense of `≅`.
    
    Identity:
    
    wu zip gha = map2(pureG(wuH), gha)(zipH)  // Now use the left identity law for G's `map2`:
     = gha.map { ha ⇒ zipH(wuH, ha) } = gha.map { ha ⇒ ha } = ga // We assume identity laws for G and H.  
    
    Similarly the right identity law holds.    
    */
  }

  it should "fail to define zippable for some functors" in {
    type F[A, P] = (A ⇒ P) ⇒ Option[A] // Not applicative.
    type G[A, P, Q] = Either[A ⇒ P, A ⇒ Q] // This is an applicative contrafunctor.
    type H[A, P, Q] = Either[P ⇒ A, Q ⇒ A] // Not applicative.
    type K[A, P, Q] = (A ⇒ P) ⇒ Q // Not applicative.

    def zipsF[A, B, P] = allOfType[(F[A, P], F[B, P]) ⇒ F[(A, B), P]]

    def zipsG[A, B, P, Q] = allOfType[(G[A, P, Q], G[B, P, Q]) ⇒ G[(A, B), P, Q]]

    def zipsH[A, B, P, Q] = allOfType[(H[A, P, Q], H[B, P, Q]) ⇒ H[(A, B), P, Q]]

    def zipsK[A, B, P, Q] = allOfType[(K[A, P, Q], K[B, P, Q]) ⇒ K[(A, B), P, Q]]

    zipsF.length shouldEqual 1
    // This function always returns `None`, and so fails the identity laws.
    zipsF.head.lambdaTerm.prettyPrint shouldEqual "a ⇒ b ⇒ (None() + 0)"
    zipsG.length shouldEqual 2 // This is OK.
    zipsH.length shouldEqual 0 // No implementations.
    zipsK.length shouldEqual 0 // No implementations.
  }
}
