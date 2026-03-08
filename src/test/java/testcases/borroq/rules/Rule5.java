package testcases.borroq.rules;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

import static testcases.borroq.rules.RuleUtil.*;

public interface Rule5 {
    static void moveMutAAway(@Mutable A a) {
    }
    static void moveImmutAAway(@Immutable A a) {
    }

    static void a(@Mutable A a) {
        ensureMutable(a);
    }

    static void b(@Immutable A a) {
        // :: error: permission.insufficient.shallow
        ensureMutable(a);
    }

    static void c1(@Mutable A a) {
        @Mutable A b = a;
        // :: error: permission.insufficient.shallow
        ensureReadable(a);
        ensureMutable(b);
    }

    static void c2(@Mutable A a) {
        @Mutable A c = a.rec;
        // :: error: permission.insufficient.deep
        ensureRecReadable(a);
        ensureMutable(c);
    }

    static void c3(@Mutable A a) {
        @Immutable A d = a.rec;
        // :: error: permission.insufficient.deep
        ensureRecMutable(a);
        ensureReadable(d);
    }

    static void d(@Mutable A a) {
        moveMutAAway(a);
        // :: error: permission.insufficient.shallow
        ensureReadable(a);
    }

    static void e(@Mutable A a) {
        moveImmutAAway(a);
        ensureReadable(a);
        // :: error: permission.insufficient.shallow
        ensureMutable(a);
    }
}
