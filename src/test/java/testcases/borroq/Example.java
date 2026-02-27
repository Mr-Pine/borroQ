package testcases.borroq;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public class Example {
    @Mutable Object x;
    @Immutable Object y;

    @Mutable Object getX(@Mutable @Scope("x") Example this) {
        return x;
    }

    @Immutable Object getY(@Immutable @Scope("y") Example this) {
        return y;
    }

    public static void main(String[] args) {
        @Mutable Example e = new Example();
        Object x = e.getX();
        Object y = e.y;
        Object y2 = e.getY();

        System.out.println(x);
        System.out.println(y);
        System.out.println(y2);
    }
}
