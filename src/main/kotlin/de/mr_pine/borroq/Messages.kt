package de.mr_pine.borroq

object Messages {
    const val UNKNOWN_TREE_ENCOUNTERED = "tree.unknown"
    const val INSUFFICIENT_SHALLOW_PERMISSION = "permission.insufficient.shallow"
    const val INSUFFICIENT_SHALLOW_PERMISSION_ASSIGNMENT_TARGET_RECEIVER = "permission.insufficient.shallow.assignment.target.receiver"
    const val INSUFFICIENT_SHALLOW_PERMISSION_ASSIGNMENT_EXPRESSION = "permission.insufficient.shallow.assignment.expression"
    const val INSUFFICIENT_SHALLOW_PERMISSION_BORROWED = "permission.insufficient.shallow.borrowed"
    const val INSUFFICIENT_DEEP_PERMISSION = "permission.insufficient.deep"
    const val TOP_PERMISSION_ENCOUNTERED = "permission.top"
    const val INCOMPATIBLE_SUPER_CONSTRUCTOR_MUTABILITY = "permission.super.incompatible"
    const val INCOMPATIBLE_RETURN_PERMISSION = "permission.return.incompatible"
    const val RELEASE_PERMISSION_MISSING = "permission.release.missing"
    const val RELEASE_BORROW_CONFLICT = "permission.release.borrow.conflict"
    const val IMMUTABLE_FIELD_IN_MUTABLE_ANNOTATION = "annotation.mutable.immutable.field"
    const val HIDDEN_FIELD_ASSIGNED = "field.hidden.assigned"
    const val CONFLICTING_PATH_RESTRICTIONS = "annotation.restricted.conflicting"
}