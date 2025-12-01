package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.types.*


fun IdPath.display(): String {
    val subfields = tail.fields.joinToString(".") { it.simpleName }
    return if (subfields.isEmpty()) {
        id.display()
    } else {
        "${id.display()}.$subfields"
    }
}
fun PathRoot.display() = when (this) {
    is PathRoot.LocalVariableRoot -> variable.toString()
    PathRoot.ThisPathRoot -> "this"
}

fun Path.display(): String {
    val subfields = tail.fields.joinToString(".") { it.simpleName }
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