package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

interface ConstructorVariance {
    class A {
        @Immutable A() {

        }
    }

    class B extends A {
        // :: error: permission.super.incompatible
        @Mutable B() {

        }
    }
}
