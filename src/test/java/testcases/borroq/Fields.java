package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;
import de.mr_pine.borroq.qual.release.Release;

public interface Fields {
    class A {
        @Mutable
        B x;
        @Immutable
        B y;

        @Mutable
        B getXMutable(@Mutable(".x") @Borrow(".x")A this) {
            return this.x;
        }

        static @Mutable B getXMutable2(@Mutable(".x") @Borrow(".x") A a) {
            return a.x;
        }

        @Immutable
        B getX(@Immutable(".x") @Borrow(".x")A this) {
            return this.x;
        }

        @Immutable
        B getY(@Immutable(".y") @Borrow(".y")A this) {
            return this.y;
        }

        @Mutable
        A() {
            this.x = new B(0);
            this.y = new B(1);
        }


        void main(@Mutable @Release A this) {
            @Mutable B b1 = this.x;
            @Immutable B b2 = new B(1);
            b1.mutable();
            @Immutable A a2 = this;
            @Immutable B b3 = a2.x;
            b3.immutable();
            @Immutable B b4 = this.y;
            b4.immutable();
        }
    }

    class B {
        int value;

        @Mutable
        B(int value) {
            this.value = value;
        }

        void mutable(@Mutable @Release B this) {
            this.value = this.value + 1;
        }

        void immutable(@Immutable @Release B this) {
        }
    }
}
