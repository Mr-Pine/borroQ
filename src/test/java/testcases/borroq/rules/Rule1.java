package testcases.borroq.rules;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

import static testcases.borroq.rules.RuleUtil.*;

public interface Rule1 {
    static void a(@Mutable @Release Object x1) {
        ensureMutable(x1);
    }

    static void b(@Immutable @Release Object x1) {
        ensureReadable(x1);
        // :: error: permission.insufficient.shallow
        ensureMutable(x1);
    }

    // :: warning: annotation.mutable.immutable.field
    static void c(@Mutable(".i1") @Release(".i1") A x1) {
    }

    // :: error: annotation.restricted.conflicting
    static void d(@Mutable({".i1", ".i1"}) @Release(".i1") A x1) {
    }

    static void e(@Immutable(".m2") @Release(".m2") A x1) {
        @Mutable Object m2 = x1.m2; // TODO: Report mutability exception here
        ensureReadable(m2);
        // :: error: permission.insufficient.shallow
        ensureMutable(m2);
    }

    static void f(@Immutable(".m2") @Release(".m2") A x1) {
        // :: error: permission.insufficient.shallow.borrowed
        @Immutable Object i2 = x1.i2; // TODO: Report mutability exception here
        // :: error: permission.insufficient.shallow
        ensureReadable(i2);
    }
}
