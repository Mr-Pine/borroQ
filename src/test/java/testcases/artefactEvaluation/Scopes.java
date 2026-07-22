package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.*;

public class Scopes {
    class A {
        @Mutable B a;
        @Mutable B b;

        @Mutable B getB(@Mutable @Scope("b") A this) {
            return b;
        }
    }

    class B {
        String xy;
    }

    void main(@Mutable A a) {
        @Mutable B b = a.getB();
        a.a.xy = "Hello";
        b.xy = "World";
    }
}
