package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Mutable;

public interface ArrayDisjointBorrows {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    class Test {
        @SuppressWarnings("UnnecessaryLocalVariable")
        Test(int a, int b) {
            Box aVal = new Box(a);
            this.a = aVal;
            Box bVal = new Box(b);
            this.b = bVal;
        }

        @Mutable
        Box a;
        @Mutable
        Box b;
    }

    default void one() {
        Test test = new Test(0, 0);
        Test[] inputs = new Test[]{test};
        @Mutable Box a = inputs[0].a;
        @Mutable Box b = inputs[0].b;

        a.value = 0;
        a.value = 1;
    }
}
