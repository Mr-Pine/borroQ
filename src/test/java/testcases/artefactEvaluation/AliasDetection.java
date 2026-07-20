package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class AliasDetection {
    class A {
        String xy;
    }

    void main(@Mutable A a) {
        A b = a;
        // :: error: permission.insufficient.shallow
        a.xy = "Hello, World!";
        System.out.println(b.xy);
    }
}
