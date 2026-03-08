package testcases.borroq.rules;

import de.mr_pine.borroq.qual.mutability.Mutable;

import static testcases.borroq.rules.RuleUtil.*;

public interface Rule4 {
    static void a(@Mutable A a) {
        @Mutable Object x = a.m2;
        ensureMutable(x);
        // :: error: permission.insufficient.deep
        @Mutable Object y = a.m2;
        ensureMutable(y);

        @Mutable A b = a.rec.rec.rec.rec;
        @Mutable Object x1 = a.rec.m2;
        @Mutable Object x2 = a.rec.rec.m2;
        @Mutable Object x3 = a.rec.rec.rec.m2;
        // :: error: permission.insufficient.deep
        @Mutable Object x4 = a.rec.rec.rec.rec.m2;
        ensureMutable(x1);
        ensureMutable(x2);
        ensureMutable(x3);
        ensureMutable(x4);

        ensureReadable(b);
    }
}
