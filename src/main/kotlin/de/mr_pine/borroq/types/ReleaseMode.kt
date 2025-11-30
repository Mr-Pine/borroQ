package de.mr_pine.borroq.types

import de.mr_pine.borroq.qual.release.Borrow
import de.mr_pine.borroq.qual.release.Move
import de.mr_pine.borroq.qual.release.Release
import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror

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