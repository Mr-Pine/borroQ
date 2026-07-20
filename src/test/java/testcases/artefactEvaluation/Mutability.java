package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class Mutability {
    class A {
        String xy;
    }

    void main(@Mutable A a) {
        a.xy = "Hello, World!";
    }
}
