#import "@preview/basic-report:0.5.0": *
#import "directories.typ": dir, dir-listing, mydir, vdots

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

#let claim(base, prefix, v) = [
  #let (key, (i, (name, body))) = v
  #claim-marker(base, i) #claim-label(prefix, i) _#name;_: #body
]
#let claim-ref(base, prefix, n) = {
  link(claim-label(prefix, n), claim-marker(base, n))
}

== Functional Outcomes

#let functional-claim = claim.with("F", "fun")
#let func-ref = claim-ref.with("F", "fun")

#let func-claims = (
  immutability: ([Immutability], [Fields on an immutable parameter cannot be reassigned.]),
  mutability: ([Mutability], [Fields on a mutable parameter can be reassigned.]),
  immut-return: (
    [Immutable Return],
    [On the return value of a method with an immutable return type, fields cannot be reassigned],
  ),
  alia-detection: ([Aliasing Detection], [On an aliased object, fields cannot be reassigned]),
  deep-field: ([Deep field access], [A local variable assigned from a field on an immutable object behaves immutably]),
  mut-arg: ([Mutable Arguments], [An immutable parameter cannot be used as a mutable argument]),
  borrowed-arg: ([Borrowed Arguments], [An parameter with an aliased field cannot be used as a mutable argument]),
  recomb: ([Recombination], [We can recombine fractional permissions once the alias is not used anymore]),
  flex-arg: ([Flexible Argument Extension], [We can use return values directly as arguments]),
  arr-ext: ([Array Extension], [We can use arrays]),
  cf-ext: ([Control-Flow Extension], [We can use while loops]),
  ext-switch: (
    [Extension Disabling],
    [Disabling the type system extensions forbids the use of non-formalized language features],
  ),
)


#let func-claims = func-claims.pairs().enumerate().map(x => (x.at(1).at(0), (x.at(0) + 1, x.at(1).at(1)))).to-dict()

#for x in func-claims.pairs() {
  functional-claim(x)
  linebreak()
}

== Reusability Claims

#let reusable-claim = claim.with("R", "reuse")
#let reuse-ref = claim-ref.with("R", "reuse")

#let reuse-claims = (
  annot-proc: ([Using the typechecker], [Our typechecker can be used as a annotation processor]),
)
#let reuse-claims = reuse-claims.pairs().enumerate().map(x => (x.at(1).at(0), (x.at(0) + 1, x.at(1).at(1)))).to-dict()

#for x in reuse-claims.pairs() {
  reusable-claim(x)
  linebreak()
}

= Quick Start

== Setup Validation

To confirm the artefact and environment are set up correctly, we recommend running the unit tests.

As this project uses gradle, this can be done with `./gradlew test`. We also provide a docker image that contains a working gradle installation and the source code. To use it, replace `./gradlew` in all commands with `docker run --rm -it <docker image identifier>` #text(red)[Tag/hash/idk?].

If the environment is operational, all tests should succeed.

== Directory Structure

#figure(caption: [Directory structure of BorroQ])[
  #dir-listing(
    pad: 6pt,
    line-y: 60%,
    dir(
      `borroq/`,
      mydir(
        `src/`,
        none,
        mydir(
          `main/`,
          [Type checker source files],
          mydir(`java`, [Vendored files from checker framework]),
          mydir(`kotlin`, [Main source files]),
        ),
        mydir(
          `test/`,
          [Unit test files],
          mydir(`kotlin`, [Test runners]),
          mydir(`java`, [Files the checker runs on]),
        ),
      ),
      dir(`gradle/`, mydir(`libs.versions.toml`, [Dependency Configuration]), mydir(
        `wrapper/`,
        [Gradle Wrapper Files],
      )),
      mydir(`*.gradle.kts`, [Gradle Configuration Files]),
      mydir(vdots, [Other files and directories, ignore]),
    ),
  )
]


= Functional Evaluation

We show the functional claims using unit tests. For claims that detect a violation or disallow something, the tests verify that the type checker throws an error via `// :: error: <error key>` comments.

The test cases can be run individually using `./gradlew test --tests <Test name>` (or with the equivalent docker command) but are also all covered by `./gradlew test`.

#figure(caption: [Corresponding test cases for functional claims], {
  let test-case-mapping = (
    immutability: ("Immutability", none),
    mutability: ("Mutability", none),
    immut-return: ("ImmutableReturn", none),
    alia-detection: ("AliasDetection", none),
    deep-field: ("DeepField", none),
    mut-arg: ("MutableArguments", none),
    borrowed-arg: ("BorrowedArguments", none),
    recomb: ("Recombination", none),
    flex-arg: ("FlexibleArgumentExtension", none),
    arr-ext: ("ArrayExtension", none),
    cf-ext: ("ControlFlowExtension", none),
    ext-switch: ("ExtensionDisabling", [This test adds an additional config option to forbid all extensions]),
  )

  let cells = test-case-mapping
    .pairs()
    .map(x => {
      let (key, (test-name, comment)) = x
      let claim-number = func-claims.at(key).at(0)
      let test-name = raw(test-name + "Test")
      let comment = if comment == none [] else {comment}
      (table.cell(func-ref(claim-number)), table.cell(align: left, test-name), table.cell(comment))
    })
    .flatten()

  table(
    columns: 3,
    ..cells
  )
})
