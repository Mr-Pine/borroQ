package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Move;

public interface Array {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    default void use(@Immutable @Move Box value) {
    }

    default void main() {
        Box elem0 = new Box(0);
        Box elem1 = new Box(1);
        Box elem2 = new Box(2);
        Box[] values = new Box []{elem0, elem1, elem2};
        Box x = values[0];
        Box y = values[1];

        use(x);
        use(y);
    }
}
