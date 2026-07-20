package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class Recombination {
    class A {
        String xy;
    }

    void main(@Mutable A a) {
        @Mutable A b = a;
        b.xy = "Hello there";
        a.xy = "Hello, World!";
    }
}
