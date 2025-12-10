package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.types.*

fun PathTail.display() = fields.joinToString(".") { it.simpleName }

fun IdPath.display(): String {
    val subfields = tail.display()
    return if (subfields.isEmpty()) {
        id.display()
    } else {
        "${id.display()}.$subfields"
    }
}
fun PathRoot.display() = when (this) {
    is PathRoot.LocalVariableRoot -> variable.toString()
    PathRoot.ThisPathRoot -> "this"
    PathRoot.StaticPathRoot -> "<static class reference>"
}

fun Path.display(): String {
    val subfields = tail.display()
    return if (subfields.isEmpty()) {
        root.display()
    } else {
        "${root.display()}.$subfields"
    }
}

fun Borrow.Identifier.display() = when (this) {
    is Borrow.Identifier.Dummy -> "dummy"
    is Id -> name
}