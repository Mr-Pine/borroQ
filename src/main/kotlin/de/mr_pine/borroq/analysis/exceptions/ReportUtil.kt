package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.types.PathTail

fun PathTail.display() = fields.joinToString(".") { it.simpleName }