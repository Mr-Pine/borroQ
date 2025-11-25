package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

public class Simple {
    @Mutable Simple() {

    }

    @Immutable String use(@Release @Immutable Simple this, @Release @Immutable Object o) {
        return "Hi";
    }

    void valid(@Release @Immutable Simple this) {
        @Mutable Object obj = new Object();
        @Mutable Object obj2 = new Object();
        @Immutable Object objCopy = obj;

        use(obj);
        use(obj2);
        use(objCopy);
    }

    void invalid(@Release @Immutable Simple this) {
        @Mutable Object obj = new Object();
        @Mutable Object obj2 = new Object();
        @Mutable Object objCopy = obj;

        // :: error: permission.insufficient
        use(obj);
        use(obj2);
        use(objCopy);
    }
}
