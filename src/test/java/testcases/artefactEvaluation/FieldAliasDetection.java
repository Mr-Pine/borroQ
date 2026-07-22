package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class FieldAliasDetection {
    class A {
        @Mutable B b;
    }

    class B {
        String xy;
    }

    void main(@Mutable A a) {
        B b = a.b;
        // :: error: permission.insufficient.deep
        a.b.xy = "Hello, World!";
        System.out.println(b.xy);
    }
}
