package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.MemberTypeAnalysis

/*context(memberTypeAnalysis: MemberTypeAnalysis)
fun IdPath.fieldPermission(): Permission? {
    val mutability = memberTypeAnalysis.getFieldMutability(tail.fields.lastOrNull() ?: return null) ?: return null
    return when (mutability) { // TODO: Sanity check
        is IMutability.Mutable -> Permission(Rational.ONE)
        is IMutability.Immutable -> Permission(BorroQTransfer.ImmutableFraction)
    }
}*/

context(memberTypeAnalysis: MemberTypeAnalysis)
fun Path.fieldPermission(): Permission? = TODO()//IdPath(Id(""), tail).fieldPermission()

context(store: BorroQStore)
fun Path.rootPermission() = when (root) {
    is PathRoot.ThisPathRoot -> store.queryThisPermission() as Permission
    is PathRoot.StaticPathRoot -> throw IllegalStateException("Static path root cannot be converted to permission")
    //is PathRoot.LocalVariableRoot -> store.queryPermission(root.variable) as Permission
    else -> TODO()
}

context(store: BorroQStore, memberTypeAnalysis: MemberTypeAnalysis)
fun Path.borrowedFieldPermission(): Permission? {
    val baseFieldPermission = fieldPermission()?.fraction ?: return null
    val idPath = TODO() //this.asIdPath()
    val borrowedAway =
        store.getBorrows().filter { it.source == idPath }.fold(Rational.ZERO) { acc, borrow -> acc + borrow.fraction }
    return Permission(baseFieldPermission - borrowedAway)
}

context(memberTypeAnalysis: MemberTypeAnalysis, store: BorroQStore)
fun Path.permission(): Permission? {
    if (tail.fields.isEmpty()) {
        return rootPermission()
    }
    val prefixPermission =
        copy(tail = PathTail(tail.fields.dropLast(1))).permission()!! // "Inner" fields aren't primitive
    val field = borrowedFieldPermission() ?: return null
    return Permission(prefixPermission.fraction * field.fraction)
}