package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Mutable;

public interface Recombination {
    static void use(@Mutable Object x) {
    }

    static void recombine() {
        @Mutable Object a = new Object();
        @Mutable Object b = a;

        use(b);
        use(a);
    }
}
