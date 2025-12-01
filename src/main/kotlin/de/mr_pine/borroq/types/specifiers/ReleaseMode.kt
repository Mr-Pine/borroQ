package de.mr_pine.borroq.types.specifiers

import de.mr_pine.borroq.qual.release.Borrow
import de.mr_pine.borroq.qual.release.Move
import de.mr_pine.borroq.qual.release.Release
import de.mr_pine.borroq.types.PathTail
import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.TypeElement

sealed interface ReleaseMode {
    fun pathsToSingleReleaseMode(): Map<PathTail, SingleReleaseMode>

    sealed interface SingleReleaseMode : ReleaseMode {
        val onPaths: List<PathTail>?

        override fun pathsToSingleReleaseMode() = onPaths?.associateWith { this } ?: emptyMap()

        data class Release(override val onPaths: List<PathTail>?) : SingleReleaseMode
        data class Borrow(override val onPaths: List<PathTail>?) : SingleReleaseMode
        data class Move(override val onPaths: List<PathTail>?) : SingleReleaseMode
    }

    data class Mixed(
        val release: SingleReleaseMode.Release,
        val borrow: SingleReleaseMode.Borrow,
        val move: SingleReleaseMode.Move
    ) : ReleaseMode {
        constructor(vararg releaseModes: SingleReleaseMode) : this(
            releaseModes.filterIsInstance<SingleReleaseMode.Release>().single(),
            releaseModes.filterIsInstance<SingleReleaseMode.Borrow>().single(),
            releaseModes.filterIsInstance<SingleReleaseMode.Move>().single()
        )

        init {
            // TODO: Check for conflicts
        }

        override fun pathsToSingleReleaseMode() =
            release.pathsToSingleReleaseMode() + borrow.pathsToSingleReleaseMode() + move.pathsToSingleReleaseMode()
    }

    companion object {
        fun fromAnnotationsOnType(annotations: Collection<AnnotationMirror>, type: TypeElement?): ReleaseMode? {
            val annotations = listOf(
                Release::class to SingleReleaseMode::Release,
                Borrow::class to SingleReleaseMode::Borrow,
                Move::class to SingleReleaseMode::Move
            ).mapNotNull { (annotationClass, releaseMode) ->
                AnnotationUtils.getAnnotationByClass(
                    annotations,
                    annotationClass.java
                )?.to(releaseMode)
            }

            if (annotations.isEmpty()) return null

            val releaseModes = annotations.map { (annotation, constructor) ->
                val paths = pathsFromAnnotationValueOnType(annotation, type)
                constructor(paths)
            }

            return (if (releaseModes.size == 1) releaseModes.single()
            else Mixed(*releaseModes.toTypedArray())) as ReleaseMode?
        }
    }
}