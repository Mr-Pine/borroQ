package de.mr_pine.borroq.types.specifiers

import de.mr_pine.borroq.types.PathTail

data class Scope(val includesBase: Boolean, val entries: List<PathTail>)
