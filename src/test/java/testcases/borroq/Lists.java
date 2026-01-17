package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

import java.util.List;

public interface Lists {
    static void ensureMutable(@Mutable @Release Object x) {
    }

    static void ensureReadable(@Immutable @Release Object x) {
    }

    static void getElement(@Mutable @Release List<@Immutable Object> list, int i) {
        @Immutable Object elem = list.get(i);
        // :: error: permission.insufficient.shallow
        // :: error: permission.insufficient.deep
        @Immutable Object elem2 = list.get(i + 1);

        ensureReadable(elem);
        ensureReadable(elem2);
    }

    static void innerMutOuterImmut(@Mutable @Release List<@Mutable Object> list, int i) {
        @Mutable Object elem = list.get(i);
        ensureMutable(elem);
    }

    static void innerImmutOuterMut(@Mutable @Release List<@Immutable Object> list, int i) {
        // :: error: permission.insufficient.shallow.assignment.expression
        @Mutable Object elem = list.get(i);
        ensureMutable(elem);
    }
}
