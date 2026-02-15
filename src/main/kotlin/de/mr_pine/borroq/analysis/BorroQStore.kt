package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.Configuration.BorroQExtensions.Extension
import de.mr_pine.borroq.analysis.exceptions.InsufficientDeepPermissionException
import de.mr_pine.borroq.analysis.exceptions.InsufficientShallowPermissionException
import de.mr_pine.borroq.analysis.transfer.BorroQTransfer.Pseudoarg.BorrowTarget
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.BorroQValue.PseudocallResult.FreeBorrow
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.ArgPermission
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.Scope
import io.github.oshai.kotlinlogging.KotlinLogging
import org.checkerframework.dataflow.analysis.Store
import org.checkerframework.dataflow.cfg.node.LocalVariableNode
import org.checkerframework.dataflow.cfg.node.Node
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.expression.FieldAccess
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable
import org.checkerframework.dataflow.expression.ThisReference
import javax.lang.model.util.Elements

private val logger = KotlinLogging.logger { }

/**
 * Stores the permissions of local variables and the this receiver as well as the borrow list.
 * @param variablePermissions A map of local variables to their permissions.
 * @param thisPermission The permission of the this receiver. Null if the method is static/a constructor.
 * @param borrowList A list of borrows.
 */
data class BorroQStore(
    private val checker: BorroQChecker,
    private val configuration: Configuration,
    private val variablePermissions: MutableMap<LocalVariable, VariablePermission>,
    private var thisPermission: VariablePermission?,
    private val borrowList: MutableList<Borrow>
) : Store<BorroQStore> {

    override fun copy() = BorroQStore(
        checker, configuration, variablePermissions.toMutableMap(), thisPermission, borrowList.toMutableList()
    )

    fun createFreshId(variableNode: LocalVariableNode): Id {
        val name = variableNode.name.toString()
        val nonce = variableNode.hashCode()

        return Id(name, nonce)
    }

    fun updatePermission(target: Node, permission: VariablePermission) {
        when (val expression = JavaExpression.fromNode(target)) {
            is LocalVariable -> updatePermission(expression, permission)
            else -> TODO()
        }
    }

    fun updatePermission(target: LocalVariable, permission: VariablePermission) {
        variablePermissions[target] = permission
    }

    fun updateThisPermission(permission: VariablePermission) {
        thisPermission = permission
    }

    fun recombine(receiver: LocalVariable, permission: IdentifiedPermission) {
        val existingPermission = variablePermissions[receiver]!!
        require(existingPermission is IdentifiedPermission) { "Cannot recombine with non-identified permission" }
        require(existingPermission.id == permission.id) { "Cannot recombine permissions with different ids" }
        variablePermissions[receiver] = existingPermission.recombineFractional(permission)
    }

    fun recombineNodeOrThis(receiver: Node?, permission: VariablePermission) =
        if (receiver != null) recombine(receiver, permission) else recombineThis(permission)

    fun recombine(receiver: Node, permission: VariablePermission) {
        require(permission is IdentifiedPermission) { "Cannot recombine non-identified permission" }
        when (val expression = JavaExpression.fromNode(receiver)) {
            is LocalVariable -> recombine(expression, permission)
            is FieldAccess if Path.fromNode(receiver).isStatic -> {}
            else -> TODO("Recombine receiver $expression of type ${expression.javaClass}")
        }
    }

    fun recombineAny(permission: IdentifiedPermission) {
        val receiver =
            variablePermissions.filterValues { it is IdentifiedPermission && it.id == permission.id }.keys.firstOrNull()
                ?: return
        recombine(receiver, permission)
    }

    fun recombineThis(permission: VariablePermission) {
        require(thisPermission is IdentifiedPermission) { "Cannot recombine with non-identified permission" }
        require(permission is IdentifiedPermission) { "Cannot recombine non-identified permission" }
        require((thisPermission as IdentifiedPermission).id == permission.id) {
            "Cannot recombine permissions with different ids: $thisPermission, $permission"
        }
        thisPermission = (thisPermission as IdentifiedPermission).recombineFractional(permission)
    }

    fun killVariable(liveVariable: Node) {
        when (val expression = JavaExpression.fromNode(liveVariable)) {
            is LocalVariable -> {
                if (expression.type.kind.isPrimitive) return

                val permission = variablePermissions[expression]!!
                if (permission !is IdentifiedPermission) {
                    return
                }
                val id = permission.id
                val otherVariables =
                    variablePermissions.entries.filterIsInstance<Map.Entry<LocalVariable, IdentifiedPermission>>()
                        .filter { (key, _) -> key != expression }.filter { (_, value) -> value.id == id }
                        .map(Map.Entry<LocalVariable, IdentifiedPermission>::key)
                val otherVariable = otherVariables.firstOrNull() ?: return
                variablePermissions[expression] = Permission(Rational.ZERO).withId(id)
                recombine(otherVariable, permission)
            }

            is FieldAccess -> {}

            else -> TODO()
        }
    }

    fun queryPermission(target: LocalVariable): VariablePermission? = variablePermissions[target]

    fun queryPermission(target: Node): VariablePermission? {
        return when (val expression = JavaExpression.fromNode(target)) {
            is LocalVariable -> queryPermission(expression)
            is ThisReference -> thisPermission
            else -> TODO("Querying permission for $expression of type ${expression.javaClass} not yet supported")
        }
    }

    fun queryThisPermission(): VariablePermission? = thisPermission

    fun localPermissionSum(id: Id) =
        (variablePermissions.values + thisPermission).filterIsInstance<IdentifiedPermission>().filter { it.id == id }
            .fold(
                Rational.ZERO
            ) { acc, v -> acc + v.fraction }

    fun getBorrows(): List<Borrow> = borrowList

    fun addBorrow(borrow: Borrow) {
        borrowList.add(borrow)
    }

    fun deleteInactiveBorrows(liveIds: Set<Id>) {
        val deadIds = borrowList.map { it.target }.filterIsInstance<Id>().filter { it !in liveIds }
        val activityGuards = deadIds.associateWith { id ->
            borrowList.filter { it.source.root == PathRoot.IdPathRoot(id) }.toMutableList()
        }
        val inactiveIds = activityGuards.filterValues { it.isEmpty() }.keys.toMutableList()
        while (inactiveIds.isNotEmpty()) {
            val id = inactiveIds.removeAt(0)
            val borrowsToRemove = borrowList.filter { it.target == id }
            borrowList.removeAll(borrowsToRemove)
            val modifiedIds =
                borrowsToRemove.map { it.source.root }.filterIsInstance<PathRoot.IdPathRoot>().map { it.id }
            for (modifiedId in modifiedIds) {
                activityGuards[modifiedId]?.removeIf { it.target == id }
                if (activityGuards[modifiedId]?.isEmpty() == true) inactiveIds.add(modifiedId)
            }
        }
    }

    private fun ensureShallowPermission(
        permission: ArgPermission, node: Node, value: BorroQValue, freeBorrows: List<FreeBorrow>
    ) {
        val fraction = when (value) {
            is IdentifiedPermission -> value.fraction
            is BorroQValue.PseudocallResult -> value.permission.fraction
            else -> throw InsufficientShallowPermissionException(node, permission, value as VariablePermission)
        }

        val fractionEnough = when (permission) {
            ArgPermission.READABLE -> fraction > Rational.ZERO
            ArgPermission.MUTABLE -> fraction == Rational.ONE
        }
        if (!fractionEnough) throw InsufficientShallowPermissionException(node, permission, value as VariablePermission)

        if (value is IdentifiedPermission) {
            val idFraction =
                (variablePermissions.values + listOfNotNull(thisPermission)).filterIsInstance<IdentifiedPermission>()
                    .filter { it.id == value.id }.map(IdentifiedPermission::fraction)
                    .fold(Rational.ZERO, Rational::plus)
            val borrowedFraction =
                borrowList.asSequence().map { FreeBorrow(it.source, it.fraction, BorrowTarget.PERSISTENT) }
                    .plus(freeBorrows)
                    .filter { it.source.root == PathRoot.IdPathRoot(value.id) && it.source.tail.fields.isEmpty() }
                    .map { it.fraction }.fold(Rational.ZERO, Rational::plus)

            val borrowsOk = when (permission) {
                ArgPermission.READABLE -> borrowedFraction < idFraction
                ArgPermission.MUTABLE -> borrowedFraction.isZero()
            }
            if (!borrowsOk) throw InsufficientShallowPermissionException(node, permission, value as VariablePermission)
        }
    }

    fun ensureDeepPermission(
        permission: ArgPermission,
        value: BorroQValue,
        node: Node,
        elements: Elements,
        memberTypeAnalysis: MemberTypeAnalysis,
        freeBorrows: List<FreeBorrow>
    ) {
        val fullScope = Scope.full(node.type, elements)
        ensurePermissionOn(permission, value, node, fullScope, memberTypeAnalysis, freeBorrows)
    }

    fun ensurePermissionOn(
        permission: ArgPermission,
        value: BorroQValue,
        node: Node,
        scope: Scope,
        memberTypeAnalysis: MemberTypeAnalysis,
        freeBorrows: List<FreeBorrow>
    ) {
        ensureShallowPermission(permission, node, value, freeBorrows)

        if (value !is IdentifiedPermission) {
            configuration.borroQExtensions.requireExtension(Extension.NESTED_FIELD_ACCESS, node.tree!!, checker)
            logger.warn { "Non-identified permission given to ensureIsReadableOn: $value. Treating as deep readable. Ensure that it should be deep $permission." }
            return
        }

        val id = value.id
        val check = when (permission) {
            ArgPermission.READABLE -> ::isReadable
            ArgPermission.MUTABLE -> ::isMutable
        }
        for (scopeTail in scope.entries) {
            if (!check(id, scopeTail, memberTypeAnalysis, freeBorrows)) {
                throw InsufficientDeepPermissionException(scopeTail, permission)
            }
        }
    }

    fun borrowedFraction(root: PathRoot, pathTail: PathTail, freeBorrows: List<FreeBorrow>): Rational {
        val borrowSource = Path(root, pathTail)
        return borrowList.asSequence().map { FreeBorrow(it.source, it.fraction, BorrowTarget.PERSISTENT) }
            .plus(freeBorrows).filter { it.source == borrowSource }.map { it.fraction }
            .fold(Rational.ZERO, Rational::plus)
    }

    private fun isReadable(
        id: Id, pathTail: PathTail, memberTypeAnalysis: MemberTypeAnalysis, freeBorrows: List<FreeBorrow>
    ): Boolean {
        val pathTailPrefixes = pathTail.fields.let { fields -> fields.indices.map { fields.take(it + 1) } }
        return pathTailPrefixes.all { fields ->
            val borrowedFraction = borrowedFraction(PathRoot.IdPathRoot(id), PathTail(fields), freeBorrows)
            val maximumFraction = (memberTypeAnalysis.getFieldMutability(fields.last())
                ?: DefaultInference.inferFieldMutability()).fraction
            borrowedFraction < maximumFraction
        }
    }

    private fun isMutable(
        id: Id, pathTail: PathTail, memberTypeAnalysis: MemberTypeAnalysis, freeBorrows: List<FreeBorrow>
    ): Boolean {
        val pathTailPrefixes = pathTail.fields.let { fields -> fields.indices.map { fields.take(it + 1) } }
        return pathTailPrefixes.all { fields ->
            val borrowedFraction = borrowedFraction(PathRoot.IdPathRoot(id), PathTail(fields), freeBorrows)

            val fieldMutability =
                memberTypeAnalysis.getFieldMutability(fields.last()) ?: DefaultInference.inferFieldMutability()

            if (fieldMutability == Mutability.MUTABLE) {
                borrowedFraction == Rational.ZERO
            } else {
                borrowedFraction < fieldMutability.fraction
            }
        }
    }

    override fun leastUpperBound(other: BorroQStore): BorroQStore {
        val combinedLocalPermissions = variablePermissions.keys.union(other.variablePermissions.keys).associateWith {
            val permissionA = variablePermissions[it] ?: VariablePermission.Top // TODO: Can we just ignore them?
            val permissionB = variablePermissions[it] ?: VariablePermission.Top

            permissionA.combine(permissionB)
        }
        val combinedThisPermission = other.thisPermission?.let { otherThis -> thisPermission?.combine(otherThis) }

        val borrows =
            (borrowList + other.borrowList).groupBy { it.source to it.target }.values.map { it.maxBy { it.fraction } }

        return BorroQStore(
            checker,
            configuration,
            combinedLocalPermissions.toMutableMap(),
            combinedThisPermission,
            borrows.toMutableList()
        )
    }

    override fun widenedUpperBound(previous: BorroQStore?): BorroQStore {
        TODO("Not yet implemented")
    }

    override fun canAlias(
        a: JavaExpression?, b: JavaExpression?
    ): Boolean {
        return true
    }

    override fun visualize(viz: CFGVisualizer<*, BorroQStore, *>?): String {
        viz!! as CFGVisualizer<BorroQValue, BorroQStore, *>
        val values = buildList {
            variablePermissions.entries.sortedBy { it.key.toString() }
                .forEach { (variable, permission) -> add(viz.visualizeStoreLocalVar(variable, permission)) }
            if (thisPermission != null) {
                add(viz.visualizeStoreThisVal(thisPermission))
            }
        }.joinToString(viz.separator)

        val borrows = borrowList.joinToString(viz.separator)

        val content = "$values${viz.separator}$borrows"

        return if (content.trim().isEmpty()) {
            "${this::class.simpleName}()"
        } else {
            "${this::class.simpleName}(${viz.separator}$content${viz.separator})"
        }
    }
}