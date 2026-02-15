package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface ReturnPermission {
    static @Mutable String returnPermission() {
        @Immutable String msg = "Hello World!";
        // :: error: permission.insufficient.shallow
        return msg;
    }
}
