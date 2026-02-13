package testcases.borroq.rules;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface RuleUtil {
    static void ensureMutable(@Mutable Object x) {
    }

    static void ensureRecMutable(@Mutable @Scope(".rec") A x) {
    }

    static void ensureReadable(@Immutable Object x) {
    }

    static void ensureRecReadable(@Immutable @Scope(".rec") A x) {
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
