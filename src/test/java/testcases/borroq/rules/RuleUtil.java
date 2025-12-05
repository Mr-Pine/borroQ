package testcases.borroq.rules;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

public interface RuleUtil {
    static void ensureMutable(@Mutable @Release Object x) {
    }

    static void ensureRecMutable(@Mutable(".rec") @Release(".rec") A x) {
    }

    static void ensureReadable(@Immutable @Release Object x) {
    }

    static void ensureRecReadable(@Immutable(".rec") @Release(".rec") A x) {
    }

    class A {
        @Mutable
        A() {

        }

        @Mutable
        int m1; // TODO: Report mutable annotation on int
        @Mutable
        Object m2;
        @Immutable
        int i1;
        @Immutable
        Object i2;

        @Mutable A rec;
    }
}
