package testcases.borroq;

import de.mr_pine.borroq.qual.Immutable;
import de.mr_pine.borroq.qual.Mutable;

public class Simple {
    @Immutable String use(@Immutable Object o) {
        return "Hi";
    }

    void valid() {
        @Mutable Object obj = new Object();
        @Mutable Object obj2 = new Object();
        @Immutable Object objCopy = obj;

        use(obj);
        use(obj2);
        use(objCopy);
    }

    void invalid() {
        @Mutable Object obj = new Object();
        @Mutable Object obj2 = new Object();
        // :: error: (unsure)
        @Mutable Object objCopy = obj;

        use(obj);
        use(obj2);
        use(objCopy);
    }
}
