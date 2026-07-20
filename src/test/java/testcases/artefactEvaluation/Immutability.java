package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class Immutability {
    class A {
        String xy;
    }

    void main(@Immutable A a) {
        // :: error: permission.insufficient.shallow
        a.xy = "Hello, World!";
    }
}
