package testcases.borroq.misc;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

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
        X(@Mutable BoxA a, @Mutable BoxB b) {
            this.a = a;
            this.b = b;
        }

        @Mutable
        BoxA a;
        @Mutable
        BoxB b;

        public @Mutable BoxA getA(@Mutable @Scope("a") X this) {
            return a;
        }

        public @Mutable BoxB getB(@Mutable @Scope("b") X this) {
            return b;
        }
    }

    default void use(@Immutable Object o) {
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
