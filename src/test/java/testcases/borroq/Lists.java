package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

import java.util.List;

public interface Lists {
    static void ensureMutable(@Mutable Object x) {
    }

    static void ensureReadable(@Immutable Object x) {
    }

    static void getElement(@Mutable List<@Immutable Object> list, int i) {
        @Immutable Object elem = list.get(i);
        int j = i + 1;
        // :: error: permission.insufficient.shallow
        @Immutable Object elem2 = list.get(j);

        ensureReadable(elem);
        ensureReadable(elem2);
    }

    static void innerMutOuterImmut(@Mutable List<@Mutable Object> list, int i) {
        @Mutable Object elem = list.get(i);
        ensureMutable(elem);
    }

    static void innerImmutOuterMut(@Mutable List<@Immutable Object> list, int i) {
        // :: error: permission.insufficient.shallow.assignment.expression
        @Mutable Object elem = list.get(i);
        ensureMutable(elem);
    }
}
