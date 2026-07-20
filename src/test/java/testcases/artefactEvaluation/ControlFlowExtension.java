package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.Immutable;

public class ControlFlowExtension {
    void drop_immut(@Immutable Object o) {

    }

    void main() {
        Object l = new Object();
        while (true) {
            drop_immut(l);
        }
    }
}
