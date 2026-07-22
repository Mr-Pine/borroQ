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

#show raw.where(block: false): r => box(fill: gray.transparentize(75%), outset: 2pt, radius: 3pt, r)

= Badge Claims

We claim two badges:
+ Artefacts Available
+ Artefacts (Functional and) Reusable
The reasons why our tool fulfils the requirements set out by the EAPLS scheme for are outlined below.

#let claim-marker(base, n) = {
  $sans(#base)_#n$
}
#let claim-label(prefix, n) = {
  label("claim_" + prefix + "_" + str(n))
}

#let claim(base, prefix, v) = [
  #let (key, (i, (name, reference, body))) = v
  #let reference = if reference != none [ (#reference)] else []
  #claim-marker(base, i) #claim-label(prefix, i) _#name#reference:_ #body
]
#let claim-ref(base, prefix, n) = {
  link(claim-label(prefix, n), claim-marker(base, n))
}

== Functional Outcomes

#let functional-claim = claim.with("F", "fun")
#let func-ref = claim-ref.with("F", "fun")

#let func-claims = (
  immutability: ([Immutability], [Section 3.1.1], [Fields on an immutable parameter cannot be reassigned.]),
  mutability: ([Mutability], [Section 3.1.1], [Fields on a mutable parameter can be reassigned.]),
  immut-return: (
    [Immutable Return],
    [Sections 2, 3.1.1],
    [On the return value of a method with an immutable return type, fields cannot be reassigned],
  ),
  alia-detection: ([Aliasing Detection], [Section 2], [On an aliased object, fields cannot be reassigned]),
  field-alias-detection: ([Field-Aliasing Detection], [Section 3.1], [If a field of an object is aliased, no nested fields on the field can be reassigned]),
  deep-field: (
    [Deep field access],
    [Sections 2, 3.1.1],
    [A local variable assigned from a field on an immutable object behaves immutably],
  ),
  scoping: (
    [Scoping],
    [Section 3.2],
    [While the return value of a scoped getter is still live, other fields are still mutable.],
  ),
  parallel-getters: (
    [Parallel Getters],
    [Section 3.2],
    [With scopes, return values from two different getters on an object can be live simultaneously.],
  ),
  recomb: (
    [Recombination],
    [Section 2.1],
    [We can recombine fractional permissions once the alias is not used anymore],
  ),
  flex-arg: ([Flexible Argument Extension], [Section 4.1], [We can use return values directly as arguments]),
  arr-ext: (
    [Array Extension],
    [Section 4.2.1],
    [Type-safe use of arrays is possible in the implementation through a extension of the formalization],
  ),
  cf-ext: ([Control-Flow Extension], [Section 4.2.2], [We can use while loops]),
  ext-switch: (
    [Extension Disabling],
    [Section 4.2],
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
  annot-proc: (
    [Using the typechecker],
    none,
    [Our typechecker can easily be utilized for external projects with the help of the checker framework gradle plugin],
  ),
)
#let reuse-claims = reuse-claims.pairs().enumerate().map(x => (x.at(1).at(0), (x.at(0) + 1, x.at(1).at(1)))).to-dict()

#for x in reuse-claims.pairs() {
  reusable-claim(x)
  linebreak()
}

= Quick Start

== Setup

To complete the artefact evaluation, you need to have either a JDK or Docker installed.

If you want to use the provided docker image, download the `borroq.image` file and run `docker load < borroq.image`.

== Setup Validation

To confirm the artefact and environment are set up correctly, we recommend running the unit tests.

As this project uses gradle, this can be done with `./gradlew test`. To use docker instead, replace `./gradlew` in all commands with `docker run --rm -it borroq:latest`. If you are evaluating on windows, replace `./gradlew` with `gradlew.bat`.

If the environment is operational, all tests should succeed.

== Directory Structure

#figure(caption: [Directory structure of BorroQ])[
  #dir-listing(
    pad: 6pt,
    line-y: 60%,
    dir(
      `borroq/`,
      dir(
        `src/`,
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

To verify the claims, we provide unit tests for the claims.
These all run the type checker on a Java file that exhibits the feature described in the claim and assert whether it raises an error or not.
Errors are asserted with `// :: error: <error key>` comments.
The Java source files for the tests can be found in `src/test/java/testcases/artefactEvaluation`.

The test cases can be run individually using `./gradlew test --tests <Test name>` (or with the equivalent docker command) but are also all covered by `./gradlew test`.

#let immutable = raw(lang: "java", "@Immutable")
#let mutable = raw(lang: "java", "@Mutable")
#let test-case-mapping = (
  immutability: (
    "Immutability",
    [Since the parameter `a` is declared as #immutable, we are not allowed to assign any fields on it.],
  ),
  mutability: ("Mutability", [On the #mutable parameter `a`, we are allowed to reassign fields.]),
  immut-return: (
    "ImmutableReturn",
    [The return value of a method whose return type is annotated with #mutable, we can neither assign it to a local variable declared as #mutable (`foo`), nor can we reassing a field if we assign it to an #immutable variable.],
  ),
  alia-detection: (
    "AliasDetection",
    [Since `a` is aliased by `b` (and `b` is kept live by the print), we are not allowed to reassing a field on `a`.],
  ),
  field-alias-detection: (
    "FieldAliasDetection",
    [Since `a.b` is aliased by `b` (and `b` is kept live by the print), we are not allowed to reassing the field `xy` on `a.b`.],
  ),
  deep-field: ("DeepField", [Since the field `b` on the class `A` is declared as #immutable, we cannot reassing any fields on it.]),
  scoping: ("Scope", [Since `getB` is scoped to the field `b`, we can still reassing `xy` on the other field `a`.]),
  parallel-getters: ("ParallelGetters", [Both `x` and `y` which hold the return values of two getters on `a` can be live concurrently because the getters are annotated with scopes.]),
  recomb: ("Recombination", [After the alias `b` is not live anymore, we can recombine the permissions, allowing us to reassing `xy` on `a`.]),
  flex-arg: ("FlexibleArgumentExtension", [The extension allows us to use the return value of `getValue()` in the argument of `consumeValue`.]),
  arr-ext: ("ArrayExtension", [The array extension detects that we try to store an #immutable value in an array of #mutable values and raises an error.]),
  cf-ext: ("ControlFlowExtension", [The control flow extension allows us to use while loops while avoiding the precision loss of widening.]),
  ext-switch: ("ExtensionDisabling", [With the command line argument disabling all extensions (see `src/test/kotlin/ArtefactEvaluation.kt`), all uses of syntax that we did not formalize and prove soundness for raises an `extension.used` error.]),
)

#for (key, (test-file, comment)) in test-case-mapping.pairs() {
  let (claim-number, claim-data) = func-claims.at(key)
  let test-name = raw(test-file + "Test")

  block(breakable: false, {
    [#func-ref(claim-number) _(#claim-data.at(0)):_ \
      *Test File*: #raw(test-file + ".java") \
      *Test Name*: #test-name \
    ]
    if comment != none [
      #comment \
    ]
  })
}

= Reusable Evaluation

We show that our type checker is reusable on an example project in `src/test/resources/example-project`. Its `build.gradle.kts` showcases how to setup the checkerframework gradle plugin and borroQ dependencies (the project is not published on Maven Central, so we need the local `includeBuild` in `settings.gradle.kts`).

To build the project, execute `./gradlew buildExampleProject` which runs `./gradlew build` in the example directory. This produces a build error from BorroQ, since there is an aliasing violation in the project's `Main.java`.
