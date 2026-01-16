package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.analysis.exceptions.InsufficientShallowPermissionException
import de.mr_pine.borroq.analysis.exceptions.TopPermissionEncounteredException
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.Mutability
import org.checkerframework.dataflow.analysis.Store
import org.checkerframework.dataflow.cfg.node.Node
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.expression.*

/**
 * Stores the permissions of local variables and the this receiver as well as the borrow list.
 * @param variablePermissions A map of local variables to their permissions.
 * @param thisPermission The permission of the this receiver. Null if the method is static/a constructor.
 * @param borrowList A list of borrows.
 */
data class BorroQStore(
    private val variablePermissions: MutableMap<LocalVariable, VariablePermission>,
    private var thisPermission: VariablePermission?,
    private val borrowList: MutableList<Borrow>
) : Store<BorroQStore> {

    override fun copy() = BorroQStore(variablePermissions.toMutableMap(), thisPermission, borrowList.toMutableList())

    fun updatePermission(target: Node, permission: VariablePermission) {
        when (val expression = JavaExpression.fromNode(target)) {
            is LocalVariable -> variablePermissions[expression] = permission
            else -> TODO()
        }
    }

    fun updateThisPermission(permission: VariablePermission) {
        thisPermission = permission
    }

    @Throws(InsufficientShallowPermissionException::class, TopPermissionEncounteredException::class)
    fun chooseAndRemoveArgumentPermission(argument: Node, shallowMutability: Mutability): VariablePermission? {
        val availablePermission = when (val expression = JavaExpression.fromNode(argument)) {
            is LocalVariable -> variablePermissions[expression]!!
            is ValueLiteral -> return null
            is FieldAccess if Path.fromNode(argument).root == PathRoot.StaticPathRoot -> return Permission(Rational.HALF).withId(Id("<<static>>")) // TODO: This is not great
            else -> TODO("non local-var argument: $expression ${expression.javaClass}")
        }

        val (split, remaining) = when (availablePermission) {
            is VariablePermission.Top -> throw TopPermissionEncounteredException(argument.toString())
            is IdentifiedPermission -> availablePermission.split(shallowMutability)
        }

        updatePermission(argument, remaining)

        return split
    }

    fun chooseAndRemoveThisReceiverPermission(mutability: Mutability): VariablePermission {
        val availablePermission = thisPermission

        val (split, remaining) = when (availablePermission) {
            is VariablePermission.Top -> throw TopPermissionEncounteredException("this")
            is IdentifiedPermission -> availablePermission.split(mutability)

            null -> throw IllegalStateException("No this permission available")
        }

        updateThisPermission(remaining)

        return split
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
                        .filter { (key, _) -> key != expression }
                        .filter { (_, value) -> value.id == id }
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

    fun hasShallowMutability(id: Id): Boolean {
        return variablePermissions.values.filter { (it as? IdentifiedPermission)?.fraction?.isZero() != true }
            .all { it is IdentifiedPermission && it.id == id && it.hasShallowMutability }
    }

    fun localPermissionSum(id: Id) =
        (variablePermissions.values + thisPermission).filterIsInstance<IdentifiedPermission>().filter { it.id == id }
            .fold(
                Rational.ZERO
            ) { acc, v -> acc + v.fraction }

    fun getBorrows(): List<Borrow> = borrowList

    fun addBorrow(borrow: Borrow) {
        borrowList.add(borrow)
    }

    fun removeBorrow(borrow: Borrow) {
        borrowList.remove(borrow)
    }

    fun removeBorrowsWithId(id: Id): List<Borrow> {
        val toRemove = borrowList.filter { it.id == id }
        borrowList.removeAll(toRemove)
        return toRemove
    }

    fun removeBorrowsWithPathPrefix(path: IdPath): List<Borrow> {
        val toRemove = borrowList.filter { path.isPrefixOf(it.path) }
        borrowList.removeAll(toRemove)
        return toRemove
    }

    override fun leastUpperBound(other: BorroQStore?): BorroQStore {
        other!!
        val combinedLocalPermissions = variablePermissions.keys.union(other.variablePermissions.keys).associateWith {
            val permissionA = variablePermissions[it] ?: VariablePermission.Top // TODO: Can we just ignore them?
            val permissionB = variablePermissions[it] ?: VariablePermission.Top

            permissionA.combine(permissionB)
        }
        val combinedThisPermission = other.thisPermission?.let { otherThis -> thisPermission?.combine(otherThis) }

        val borrows =
            (borrowList + other.borrowList).groupBy { it.path to it.id }.values.map { it.maxBy { it.fraction } }

        return BorroQStore(combinedLocalPermissions.toMutableMap(), combinedThisPermission, borrows.toMutableList())
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