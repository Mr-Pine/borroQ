package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

class ArrayExtension {
    static void ensureMutable(@Mutable Object x) {}

    void a() {
        @Immutable Object x = new Object();
        // :: error: type.parameter.mutability.incompatible
        @Mutable Object[] arr = new @Immutable Object[] {x};

        @Mutable Object y = arr[0];
        ensureMutable(y);
    }
}
