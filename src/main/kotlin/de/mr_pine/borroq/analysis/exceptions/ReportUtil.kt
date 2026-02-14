package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.types.*

fun PathTail.display() = fields.joinToString(".") { it.simpleName }

fun Path.display(): String {
    val subfields = tail.display()
    return if (subfields.isEmpty()) {
        root.display()
    } else {
        "${root.display()}.$subfields"
    }
}

fun PathRoot.display() = when (this) {
    is PathRoot.IdPathRoot -> id.toString()
    PathRoot.ThisPathRoot -> "this"
    is PathRoot.StaticPathRoot -> className.toString()
}

fun Borrow.Identifier.display() = when (this) {
    is Borrow.Identifier.Dummy -> "dummy"
    is Id -> name
}