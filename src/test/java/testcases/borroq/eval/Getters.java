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
        BoxA box1 = new BoxA(1);
        BoxB box2 = new BoxB(2);
        @Mutable X x = new X(box1, box2);

        BoxA a = x.getA();
        BoxB b = x.getB();

        use(a);
        use(b);
    }
}
