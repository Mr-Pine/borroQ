package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class DeepField {
    class A {
        @Immutable B b;
    }

    class B {
        String xy;
    }

    void main(@Mutable A a) {
        // :: error: permission.insufficient.shallow.assignment.expression
        @Mutable B b = a.b;
        b.xy = "Hello, World!";
    }
}
