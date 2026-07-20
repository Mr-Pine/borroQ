package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class MutableArguments {
    class A {
        String xy;
    }

    void main(@Immutable A a) {
        // :: error: permission.insufficient.shallow
        foo(a);
    }

    void foo(@Mutable A a) {
        a.xy = "Hello, World!";
    }
}
