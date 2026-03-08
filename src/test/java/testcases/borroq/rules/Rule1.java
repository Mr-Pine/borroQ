package testcases.borroq.rules;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

import static testcases.borroq.rules.RuleUtil.*;

public interface Rule1 {
    static void a(@Mutable Object x1) {
        ensureMutable(x1);
    }

    static void b(@Immutable Object x1) {
        ensureReadable(x1);
        // :: error: permission.insufficient.shallow
        ensureMutable(x1);
    }

    static void e(@Immutable @Scope(".m2") A x1) {
        // :: error: permission.insufficient.shallow
        @Mutable Object m2 = x1.m2;
        ensureMutable(m2);
    }

    static void f(@Immutable @Scope(".m2") A x1) {
        // :: error: permission.insufficient.deep
        @Immutable Object i2 = x1.i2;
        ensureReadable(i2);
    }
}
