#import "@preview/basic-report:0.5.0": *

#show: it => basic-report(
  doc-category: "Artefact Evaluation Instructions",
  doc-title: "Fractional Permissions with Partial Borrows for
Object-Oriented Programs",
  author: "David Kiefer, Florian Lanzinger",
  affiliation: "Karlsruhe Institute of Technology",
  logo: none,
  // <a href="https://www.flaticon.com/free-icons/aerospace" title="aerospace icons">Aerospace icons created by gravisio - Flaticon</a>
  language: "en",
  compact-mode: true,
  it,
)

= Badge Claims

#let claim-marker(base, n) = {
  $sans(#base)_#n$
}
#let claim-label(prefix, n) = {
  label("claim_" + prefix + "_" + str(n))
}

#let claim(base, prefix, n, name, content) = [
  #claim-marker(base, n) #claim-label(prefix, n) _#name;_: #content
]
#let claim-ref(base, prefix, n) = {
  link(claim-label(prefix, n), claim-marker(base, n))
}

== Functional Outcomes

#let functional-claim = claim.with("F", "fun")
#let func-ref = claim-ref.with("F", "fun")

#let func-claims = (
  ([Immutability], [Fields on an immutable parameter cannot be reassigned.]),
  ([Mutability], [Fields on a mutable parameter can be reassigned.]),
  ([Immutable Return], [On the return value of a method with an immutable return type, fields cannot be reassigned]),
  ([Aliasing Detection], [On an aliased object, fields cannot be reassigned]),
  ([Deep field access], [A local variable assigned from a field on an immutable object behaves immutably]),
  ([Mutable Arguments], [An immutable parameter cannot be used as a mutable argument]),
  ([Recombination], [We can recombine fractional permissions once the alias is not used anymore]),
  ([Flexible Argument Extension], [We can use return values directly as arguments]),
  ([Array Extension], [We can use arrays]),
  ([Control-Flow Extension], [We can use while loops]),
  ([Extension Disabling], [Disabling the type system extensions forbids the use of non-formalized language features])
)

#for (i, (name, body)) in func-claims.enumerate() {
  functional-claim(i + 1, name, body)
  linebreak()
}


== Reusability Claims

#let reusable-claim = claim.with("R", "reuse")
#let reuse-ref = claim-ref.with("R", "reuse")

#let reuse-claims = (
  ([Using the typechecker], [Our typechecker can be used as a annotation process]),
)

#for (i, (name, body)) in reuse-claims.enumerate() {
  reusable-claim(i + 1, name, body)
  linebreak()
}

= Quick Start

== Setup Validation

To confirm the artefact and environment are set up correctly, we recommend running the unit tests.

As this project uses gradle, this can be done with `./gradlew test`. We also provide a docker image that contains a working gradle installation and the source code. To use it, replace `./gradlew` in all commands with `docker run --rm -it <docker image identifier>` #text(red)[Tag/hash/idk?].

If the environment is operational, all tests should succeed.

== Directory Structure

#text(red)[TODO: Figure]

The directory structure is standard for a gradle project. `src/main/kotlin/de/mr_pine/borroq` contains the source code of the checker, `src/main/java/de/mr_pine/borroq` contains vendored and fixed code from the checker framework.

`src/test` contains the unit tests, with `src/test/kotlin` having the test runners and `src/test/java` containing the source files that the checker is run on.




= Functional Evaluation


