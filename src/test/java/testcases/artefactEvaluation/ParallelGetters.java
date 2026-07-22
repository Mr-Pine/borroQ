package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.*;

public class ParallelGetters {
    class A {
        @Mutable B x;
        @Mutable B y;

        @Mutable B getX(@Mutable @Scope("x") A this) {
            return x;
        }
        @Mutable B getY(@Mutable @Scope("y") A this) {
            return y;
        }
    }

    class B {
        String xy;
    }

    void main(@Mutable A a) {
        @Mutable B x = a.getX();
        @Mutable B y = a.getY();
        x.xy = "Hello";
        y.xy = "World";
    }
}
