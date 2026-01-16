package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Release;

public interface MutCantAlias {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    default void use(@Mutable @Release Object o) {
    }

    default void main() {
        @Mutable Box b = new Box(0);
        @Mutable Box b1 = b;
        // :: error: permission.insufficient.shallow
        @Mutable Box b2 = b;

        use(b1);
    }
}
