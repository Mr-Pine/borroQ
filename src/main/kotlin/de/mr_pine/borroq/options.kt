package de.mr_pine.borroq

enum class Strictness {
    STRICT, WARN_UNKNOWN, ALLOW_UNKNOWN;

    companion object {
        const val KEY = "strictness"
        val DEFAULT = STRICT.key
        fun fromString(string: String) = valueOf(string.uppercase().replace("-", "_"))
    }

    val key: String
        get() = name.lowercase().replace("_", "-")
}