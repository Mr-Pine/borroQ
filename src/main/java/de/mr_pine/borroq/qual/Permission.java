package de.mr_pine.borroq.qual;

import de.mr_pine.borroq.Rational;

public @interface Permission {
    Rational value();
    String id();
}
