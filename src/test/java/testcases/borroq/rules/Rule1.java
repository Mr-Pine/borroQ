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

    // :: warning: annotation.mutable.immutable.field
    static void c(@Mutable @Scope(".i1") A x1) {
    }

    // :: error: annotation.restricted.conflicting
    static void d(@Mutable @Scope({".i1", ".i1"}) A x1) {
    }

    static void e(@Immutable @Scope(".m2") A x1) {
        @Mutable Object m2 = x1.m2; // TODO: Report mutability exception here
        ensureReadable(m2);
        // :: error: permission.insufficient.shallow
        ensureMutable(m2);
    }

    static void f(@Immutable @Scope(".m2") A x1) {
        // :: error: permission.insufficient.shallow.borrowed
        @Immutable Object i2 = x1.i2; // TODO: Report mutability exception here
        // :: error: permission.insufficient.shallow
        ensureReadable(i2);
    }
}
