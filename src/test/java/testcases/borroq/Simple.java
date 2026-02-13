package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.*;

public class Simple {
    @Mutable Simple() {

    }

    @Immutable String use(@Immutable Simple this, @Immutable Object o) {
        return "Hi";
    }

    void valid(@Immutable Simple this) {
        @Mutable Object obj = new Object();
        @Mutable Object obj2 = new Object();
        @Immutable Object objCopy = obj;

        use(obj);
        use(obj2);
        use(objCopy);
    }

    void invalid(@Immutable Simple this) {
        @Mutable Object obj = new Object();
        @Mutable Object obj2 = new Object();
        @Mutable Object objCopy = obj;

        // :: error: permission.insufficient.shallow
        use(obj);
        use(obj2);
        use(objCopy);
    }
}
