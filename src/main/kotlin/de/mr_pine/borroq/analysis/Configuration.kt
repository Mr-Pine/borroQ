package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import de.mr_pine.borroq.Messages
import io.github.oshai.kotlinlogging.KotlinLogging
import org.checkerframework.dataflow.cfg.node.Node
import org.checkerframework.framework.source.SourceChecker

private val logger = KotlinLogging.logger { }

data class Configuration(
    val unknownSyntaxStrictness: UnknownSyntaxStrictness,
    val borroQExtensions: BorroQExtensions
) {
    interface ConfigOption<T : ConfigOption.ConfigValue> {
        interface ConfigValue {
            val key: String
        }

        val optionKey: String
        val default: T
        fun fromString(string: String): T

        fun getValue(checker: SourceChecker): T = checker.getOption(optionKey, default.key).let(::fromString)
    }

    enum class UnknownSyntaxStrictness : ConfigOption.ConfigValue {
        STRICT, WARN_UNKNOWN, ALLOW_UNKNOWN;

        companion object : ConfigOption<UnknownSyntaxStrictness> {
            const val KEY = "unknown_strictness"
            override val optionKey = KEY
            override val default = STRICT
            override fun fromString(string: String) = valueOf(string.uppercase().replace("-", "_"))
        }

        override val key: String
            get() = name.lowercase().replace("_", "-")

        fun reportUnknownSyntaxStrictness(checker: SourceChecker, node: Node) = node.tree?.let { source ->
            when (this) {
                STRICT -> checker.reportError(source, Messages.UNKNOWN_TREE_ENCOUNTERED, source.kind)
                WARN_UNKNOWN -> checker.reportWarning(source, Messages.UNKNOWN_TREE_ENCOUNTERED, source.kind)
                ALLOW_UNKNOWN -> {}
            }
        } ?: logger.warn { "Tree of unknown node $node is null" }
    }

    enum class BorroQExtensions : ConfigOption.ConfigValue {
        ENABLED, DISABLED;

        enum class Extension(val description: String) {
            ARRAYS("Array handling"),
            ALL_PRIMITIVES("Treat all primitive types the same as bool"),
            ANY_PARAMETER_COUNT("Allow parameter counts != 2"),
            NESTED_FIELD_ACCESS("Accessing nested fields in code and in scopes"),
            VOID_RETURN("Treat void return types identically as returning bool"),
            NO_OUT_OF_SCOPE_ASSIGNMENT("Disallow assigning fields that are out of scope for a method"),
            CONSTRUCTORS("Enable constructors"),
            NON_VARIABLE_ARGUMENTS("Allow arbitrary nested field accesses only local variables as arguments, return values, lhs of assignments, rhs of assignments"),
            ;
        }

        companion object : ConfigOption<BorroQExtensions> {
            const val KEY = "borroq_extensions"
            override val optionKey = KEY
            override val default = ENABLED
            override fun fromString(string: String) = valueOf(string.uppercase().replace("-", "_"))
        }

        @Suppress("unused")
        fun isActive(extension: Extension) = this == ENABLED

        fun requireExtension(extension: Extension, source: Tree, checker: SourceChecker) {
            if (!isActive(extension)) {
                checker.reportError(source, Messages.EXTENSION_USED, extension.toString(), extension.description)
            }
        }

        fun requireExtension(extension: Extension, sourceNode: Node, checker: SourceChecker) {
            if (!isActive(extension)) {
                sourceNode.tree?.let { source ->
                    requireExtension(extension, source, checker)
                } ?: logger.error { "Cannot report extension on null source tree for $sourceNode" }
            }
        }

        override val key: String
            get() = name.lowercase().replace("_", "-")
    }

    companion object {
        fun SourceChecker.getConfig(): Configuration {
            val unknownStrictness = UnknownSyntaxStrictness.getValue(this)
            val borroQExtensions = BorroQExtensions.getValue(this)
            return Configuration(unknownStrictness, borroQExtensions)
        }

    }
}
