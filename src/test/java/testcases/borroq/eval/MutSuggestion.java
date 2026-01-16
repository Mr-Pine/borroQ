package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;
import de.mr_pine.borroq.qual.release.Release;

public interface MutSuggestion {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    class S {
        void mutate(@Mutable S this) {}
    }

    default void func(@Immutable @Release S s) {
        // :: error: permission.insufficient.shallow
        s.mutate();
    }

    default void main() {
        @Immutable S local = new S();
        // :: error: permission.insufficient.shallow
        local.mutate();
    }
}
