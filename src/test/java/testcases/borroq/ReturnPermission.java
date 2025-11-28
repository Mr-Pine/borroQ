package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

public interface ReturnPermission {
    static @Mutable String returnPermission() {
        @Immutable String msg = "Hello World!";
        // :: error: permission.return.incompatible
        return msg;
    }
}
