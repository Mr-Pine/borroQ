package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

public interface Rules {
    static void ensureMutable(@Mutable @Release Object x) {}
    static void ensureReadable(@Immutable @Release Object x) {}

    class A {
        @Mutable A() {

        }

        @Mutable int x;
        @Immutable int y;
    }

    static void R1_a(@Mutable @Release Object x1) {
        ensureMutable(x1);
    }
    static void R1_b(@Immutable @Release Object x1) {
        ensureReadable(x1);
        // :: error: permission.insufficient.shallow
        ensureMutable(x1);
    }

    // :: warning: annotation.mutable.immutable.field
    static void R1_c(@Mutable(".y") @Release A x1) {
    }

    // :: error: annotation.restricted.conflicting
    static void R1_d(@Mutable({".y", ".y"}) @Release A x1) {
    }
}
