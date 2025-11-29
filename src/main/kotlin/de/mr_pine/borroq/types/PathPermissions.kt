package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.MemberTypeAnalysis

context(memberTypeAnalysis: MemberTypeAnalysis)
fun Path.fieldPermission(): Permission? {
    val mutability = memberTypeAnalysis.getFieldMutability(tail.fields.last()) ?: return null
    return when (mutability) {
        Mutability.MUTABLE -> Permission(Rational.ONE)
        Mutability.IMMUTABLE -> Permission(Rational.HALF)
    }
}

context(borrows: List<Borrow>, memberTypeAnalysis: MemberTypeAnalysis)
fun Path.borrowedFieldPermission(): Permission? {
    val baseFieldPermission = fieldPermission()?.fraction ?: return null
    val borrowedAway = borrows.filter { it.path == this }.fold(Rational.ZERO) { acc, borrow -> acc + borrow.fraction }
    return Permission(baseFieldPermission - borrowedAway)
}

context(memberTypeAnalysis: MemberTypeAnalysis, store: BorroQStore)
fun Path.permission(): Permission? {
    if (tail.fields.isEmpty()) {
        return when (root) {
            is PathRoot.ThisPathRoot -> store.queryThisPermission() as Permission
            is PathRoot.LocalVariableRoot -> store.queryPermission(root.variable) as Permission
        }
    }
    val prefixPermission = copy(tail = tail.copy(fields = tail.fields.dropLast(1))).permission()!! // "Inner" fields aren't primitive
    val field = context(store.getBorrows()) { borrowedFieldPermission() }  ?: return null
    return Permission(prefixPermission.fraction * field.fraction)
}