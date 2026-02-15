package testcases.borroq;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface Fields {
    class A {
        @Mutable
        B x;
        @Immutable
        B y;

        @Mutable
        B getXMutable(@Mutable @Scope(".x")A this) {
            return this.x;
        }

        static @Mutable B getXMutable2(@Mutable @Scope(".x") A a) {
            return a.x;
        }

        @Immutable
        B getX(@Immutable @Scope(".x") A this) {
            return this.x;
        }

        @Immutable
        B getY(@Immutable @Scope(".y") A this) {
            return this.y;
        }

        @Mutable
        A() {
            @Mutable B x = new B(0);
            this.x = x;
            @Mutable B y = new B(0);
            this.y = y;
        }

        void mainDirect(@Mutable A this) {
            @Mutable B b1 = this.x;
            @Immutable B b2 = new B(1);
            b1.mutable();
        }

        void mainDirect2(@Mutable A this) {
            @Immutable A a2 = this;
            @Immutable B b3 = a2.x;
            b3.immutable();
        }

        void mainDirect3(@Mutable A this) {
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

        void mutable(@Mutable B this) {
            this.value = this.value + 1;
        }

        void immutable(@Immutable B this) {
        }
    }
}
