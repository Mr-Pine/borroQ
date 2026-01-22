package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface ArrayComponentMutability {
    default void ensureMutable(@Mutable Object x) {}

    default void a() {
        @Immutable Object x = new Object();
        // :: error: type.parameter.mutability.incompatible
        @Mutable Object[] arr = new @Immutable Object[] {x};

        @Mutable Object y = arr[0];
        ensureMutable(y);
    }
}
