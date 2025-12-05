package testcases.borroq.rules;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Move;
import de.mr_pine.borroq.qual.release.Release;

import static testcases.borroq.rules.RuleUtil.*;

public interface Rule5 {
    static void moveMutAAway(@Mutable @Move A a) {
    }
    static void moveImmutAAway(@Immutable @Move A a) {
    }

    static void a(@Mutable @Release A a) {
        ensureMutable(a);
    }

    static void b(@Immutable @Release A a) {
        // :: error: permission.insufficient.shallow
        ensureMutable(a);
    }

    static void c(@Mutable @Release A a) {
        @Mutable A b = a;
        // :: error: permission.insufficient.shallow
        ensureReadable(a);
        ensureMutable(b);

        @Mutable A c = a.rec;
        // :: error: permission.insufficient.deep
        ensureRecReadable(a);
        ensureMutable(c);
        @Immutable A d = a.rec;
        // :: error: permission.insufficient.deep
        ensureRecMutable(a);
        ensureReadable(d);
    }

    static void d(@Mutable @Move A a) {
        moveMutAAway(a);
        // :: error: permission.insufficient.shallow
        ensureReadable(a);
    }

    static void e(@Mutable @Move A a) {
        moveImmutAAway(a);
        ensureReadable(a);
        // :: error: permission.insufficient.shallow
        ensureMutable(a);
    }

    static void f(@Mutable @Release A a) {
        // :: error: permission.release.borrow.conflict
        moveMutAAway(a);
        return;
    }
}
