package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.MemberTypeAnalysis
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.Mutability

context(memberTypeAnalysis: MemberTypeAnalysis)
fun IdPath.fieldPermission(): Permission? {
    val mutability = memberTypeAnalysis.getFieldMutability(tail.fields.last()) ?: return null
    return when (mutability) { // TODO: Sanity check
        is Mutability.Mutable -> Permission(Rational.ONE)
        is Mutability.Immutable -> Permission(Rational.HALF)
    }
}

context(memberTypeAnalysis: MemberTypeAnalysis)
fun Path.fieldPermission(): Permission? = IdPath(Id(""), tail).fieldPermission()

context(store: BorroQStore)
fun Path.rootPermission() = when (root) {
    is PathRoot.ThisPathRoot -> store.queryThisPermission() as Permission
    is PathRoot.LocalVariableRoot -> store.queryPermission(root.variable) as Permission
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
        return rootPermission()
    }
    val prefixPermission =
        copy(tail = tail.copy(fields = tail.fields.dropLast(1))).permission()!! // "Inner" fields aren't primitive
    val field = context(store.getBorrows()) { borrowedFieldPermission() } ?: return null
    return Permission(prefixPermission.fraction * field.fraction)
}

context(store: BorroQStore, memberTypeAnalysis: MemberTypeAnalysis)
fun Path.hasDeepMutability() =
    permission()?.withId(Id(""))?.hasShallowMutability ?: false && context(store.getBorrows()) { asIdPath().allowsDeepMutability() }

context(store: BorroQStore, memberTypeAnalysis: MemberTypeAnalysis)
fun Path.hasDeepReadability() =
    permission()?.withId(Id(""))?.hasShallowReadability ?: false && context(store.getBorrows()) { asIdPath().allowsDeepReadability() }

context(borrows: List<Borrow>)
fun IdPath.allowsDeepMutability() = borrows.none { this.isPrefixOf(it.path) }

context(borrows: List<Borrow>, memberTypeAnalysis: MemberTypeAnalysis)
fun IdPath.allowsDeepReadability() = borrows.filter { this.isPrefixOf(it.path) }
    .all { borrow -> borrow.path.fieldPermission()?.fraction?.let { borrow.fraction < it } ?: true }