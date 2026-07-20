package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class ImmutableReturn {
    class A {
        String xy;
    }

    void foo() {
        // :: error: permission.insufficient.shallow.assignment.expression
        @Mutable A a = getA();
        a.xy = "Hello, World!";
    }

    void bar() {
        @Immutable A a = getA();
        // :: error: permission.insufficient.shallow
        a.xy = "Hello, World!";
    }

    @Immutable A getA() {
        return new A();
    }
}
