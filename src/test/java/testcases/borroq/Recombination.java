package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

public interface Recombination {
    static void use(@Mutable @Release Object x) {
    }

    static void recombine() {
        @Mutable Object a = new Object();
        @Mutable Object b = a;

        use(b);
        use(a);
    }
}
