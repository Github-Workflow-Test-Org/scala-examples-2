let Prelude = https://prelude.dhall-lang.org/v20.2.0/package.dhall

let Arith = ./naturals.dhall

let div: Natural -> Natural -> Natural = \(x: Natural) -> \(y: Natural) -> Prelude.Optional.default Natural 0 (Arith.div x y)

let Unit: Type = < Unit >

let unit = Unit.Unit : Unit

-- There can be no values of type Void.
let Void = forall (x: Type) -> x

-- Having a value of type Void is enough to produce a value of any type.
let absurd: forall (t: Type) -> Void -> t = \(t: Type) -> \(v: Void) -> v t

let Nonzero: Natural -> Type = \(y: Natural) -> if Natural/isZero y then Void else Unit

let test = unit : Nonzero 1

-- division where we require the divisor to be nonzero
let divide: Natural -> forall (y: Natural) -> Nonzero y -> Natural =
  \(x: Natural) -> \(y: Natural) -> \(nonzero: Nonzero y) ->
   div x y

let test = assert : divide 40 20 unit === 2
-- let test = assert : divide 40 0 unit === 2   -- This fails to typecheck!


in {divide}
