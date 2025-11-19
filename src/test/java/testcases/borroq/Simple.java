package testcases.borroq;

import de.mr_pine.borroq.Rational;
import de.mr_pine.borroq.qual.Permission;

public class Simple {
    void use(Object o) {}

    void valid() {
        @Permission(value = @Rational(numerator = 1, denominator = 1), id = "obj_id") Object obj = new Object();
        @Permission(value = @Rational(numerator = 1, denominator = 1), id = "obj_id_2") Object obj2 = new Object();
        @Permission(value = @Rational(numerator = 1, denominator = 2), id = "obj_id") Object objCopy = obj;

        use(obj);
        use(obj2);
        use(objCopy);
    }
    void invalid() {
        @Permission(value = @Rational(numerator = 1, denominator = 1), id = "obj_id") Object obj = new Object();
        @Permission(value = @Rational(numerator = 1, denominator = 1), id = "obj_id_2") Object obj2 = new Object();
        // :: error: (unsure)
        @Permission(value = @Rational(numerator = 1, denominator = 1), id = "obj_id") Object objCopy = obj;

        use(obj);
        use(obj2);
        use(objCopy);
    }
}
