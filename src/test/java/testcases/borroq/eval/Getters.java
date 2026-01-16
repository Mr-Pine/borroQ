package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;
import de.mr_pine.borroq.qual.release.Release;

public interface Getters {
    class BoxA {
        BoxA(int value) {
            this.value = value;
        }

        int value;
    }

    class BoxB {
        BoxB(int value) {
            this.value = value;
        }

        int value;
    }

    class X {
        X(@Mutable @Borrow BoxA a, @Mutable @Borrow BoxB b) {
            this.a = a;
            this.b = b;
        }

        @Mutable
        BoxA a;
        @Mutable
        BoxB b;

        public @Mutable BoxA getA(@Mutable("a") @Borrow("a")X this) {
            return a;
        }

        public @Mutable BoxB getB(@Mutable("b") @Borrow("b")X this) {
            return b;
        }
    }

    default void use(@Immutable @Release Object o) {
    }

    default void main() {
        @Mutable X x = new X(new BoxA(1), new BoxB(2));

        BoxA a = x.a;
        BoxB b = x.b;

        use(a);
        use(b);
    }
}
