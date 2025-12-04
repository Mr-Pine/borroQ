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

        @Mutable int m1; // TODO: Report mutable annotation on int
        @Mutable Object m2;
        @Immutable int i1;
        @Immutable Object i2;
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
    static void R1_c(@Mutable(".i1") @Release A x1) {
    }

    // :: error: annotation.restricted.conflicting
    static void R1_d(@Mutable({".i1", ".i1"}) @Release A x1) {
    }

    static void R1_e(@Immutable(".m2") @Release A x1) {
        @Mutable Object m2 = x1.m2; // TODO: Report mutability exception here
        ensureReadable(m2);
        // :: error: permission.insufficient.shallow
        ensureMutable(m2);
    }

    static void R1_f(@Immutable(".m2") @Release A x1) {
        // :: error: permission.insufficient.shallow.borrowed
        @Immutable Object i2 = x1.i2; // TODO: Report mutability exception here
        // :: error: permission.insufficient.shallow
        ensureReadable(i2);
    }
}
