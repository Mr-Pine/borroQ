package de.mr_pine.borroq.types

import de.mr_pine.borroq.qual.release.Borrow
import de.mr_pine.borroq.qual.release.Move
import de.mr_pine.borroq.qual.release.Release
import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror

/**
 * @param returnMutability The mutability of the return value. `null` if the return type is primitive/null
 * @param receiverType The type of the receiver. `null` if the method is static (or a constructor)
 * @param arguments The types of the arguments. `null` if the argument is a primitive
 */
data class SignatureType(
    val returnMutability: Mutability?, val receiverType: ArgumentType?, val arguments: List<ArgumentType?>
) {
    data class ArgumentType(val mutability: Mutability, val releaseMode: ReleaseMode) {
        companion object {
            enum class ReleaseMode {
                RELEASE, BORROW, MOVE;

                companion object {
                    fun fromAnnotations(annotations: Collection<AnnotationMirror>): ReleaseMode? {
                        val annotations = listOf(
                            Release::class, Borrow::class, Move::class
                        ).map { AnnotationUtils.getAnnotationByClass(annotations, it.java) }
                        val (releaseAnnotation, borrowAnnotation, moveAnnotation) = annotations

                        if (annotations.filterNotNull().size > 1) {
                            throw IllegalStateException("Conflicting release modes: $annotations")
                        }

                        return releaseAnnotation?.let { return RELEASE } ?: borrowAnnotation?.let { return BORROW }
                        ?: moveAnnotation?.let { return MOVE }
                    }
                }
            }
        }
    }
}