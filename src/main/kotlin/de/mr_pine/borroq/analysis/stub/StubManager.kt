package de.mr_pine.borroq.analysis.stub

import de.mr_pine.borroq.analysis.SignatureTypeAnalysis
import org.checkerframework.com.github.javaparser.ast.ImportDeclaration
import org.checkerframework.com.github.javaparser.ast.StubUnit
import org.checkerframework.com.github.javaparser.ast.body.ConstructorDeclaration
import org.checkerframework.com.github.javaparser.ast.body.MethodDeclaration
import org.checkerframework.com.github.javaparser.ast.visitor.GenericVisitorAdapter
import org.checkerframework.framework.source.SourceChecker
import org.checkerframework.framework.util.JavaParserUtil
import org.checkerframework.javacutil.SystemUtil
import java.io.File
import java.io.InputStream
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements

class StubManager(
    private val signatureAnalysis: SignatureTypeAnalysis,
    private val processingEnvironment: ProcessingEnvironment,
    private val elements: Elements,
    private val options: StubOptions
) {
    data class StubOptions(
        val ignoreJdkAstub: Boolean,
        val parseAllJdkFiles: Boolean,
        val permitMissingJdk: Boolean,
        val debug: Boolean,
        val additionalStubs: List<String>
    ) {
        val shouldParseJdk: Boolean
            get() = !ignoreJdkAstub

        companion object {
            val SourceChecker.stubOptions: StubOptions
                get() = StubOptions(
                    hasOption("ignorejdkastub"),
                    hasOption("parseAllJdk"),
                    hasOption("permitMissingJdk"),
                    hasOption("stubDebug"),
                    getOption("stubs")?.split(File.pathSeparator)?.filterNot(String::isNullOrBlank).orEmpty()
                )
        }
    }

    fun parseStubFiles() {
        if (options.shouldParseJdk) {
            //region Annotated JDK
            /*val resourceURL = signatureAnalysis.javaClass.getResource("/annotated-jdk")
            if (resourceURL != null) {
                require(resourceURL.protocol == "jar") { "JDK stubs must be in a jar file" }

                (resourceURL.openConnection() as JarURLConnection).apply {
                    defaultUseCaches = false
                    useCaches = false
                    connect()
                }.jarFile.use { jarFile ->
                    for (entry in jarFile.stream().asSequence().sortedBy(Any::toString)
                        .filterNot(JarEntry::isDirectory)
                        .filter { it.name.startsWith("annotated-jdk") || it.name.endsWith(".java") }) {
                        val fqn = entry.name.substringAfter("/share/classes/").substringBeforeLast(".java")
                            .replace("/", ".")
                        jarFile.getInputStream(entry).use {
                            parseStubFile(AnnotationFileUtil.AnnotationFileType.JDK_STUB, it)
                        }
                    }
                }

            } else if (!options.permitMissingJdk) {
                throw BugInCF(
                    "JDK not found for type factory ${signatureAnalysis.javaClass.getSimpleName()}"
                )
            }*/
            //endregion

            val jdkVersion = SystemUtil.getReleaseValue(processingEnvironment) ?: SystemUtil.jreVersion.toString()
            for (astubFile in listOf("", jdkVersion).map { "jdk$it.astub" }) {
                signatureAnalysis.javaClass.getResourceAsStream(astubFile)?.use {
                    parseStubFile(
                        it
                    )
                }
            }
        }
    }

    @ConsistentCopyVisibility
    data class ImportedAnnotationScopes private constructor(val annotations: MutableList<Pair<String, TypeElement>>) {
        constructor() : this(mutableListOf())

        fun push(simpleName: String, annotation: TypeElement) {
            annotations.addLast(simpleName to annotation)
        }

        fun pop(): Pair<String, TypeElement> {
            return annotations.removeLast()
        }

        operator fun get(index: String): TypeElement {
            return annotations.last { it.first == index }.second
        }
    }

    inner class StubSignatureVisitor : GenericVisitorAdapter<Nothing?, ImportedAnnotationScopes>() {
        override fun visit(
            n: StubUnit,
            arg: ImportedAnnotationScopes
        ): Nothing? {
            for (compilationUnit in n.compilationUnits) {
                compilationUnit.accept(this, arg)
            }
            return null
        }

        override fun visit(
            import: ImportDeclaration,
            arg: ImportedAnnotationScopes
        ): Nothing? {
            require(!import.isStatic)
            val annotations = if (import.isAsterisk) {
                val packageName = import.name.asString()
                val element = elements.getPackageElement(packageName)!!
                element.enclosedElements.filterIsInstance<TypeElement>().filter { it.kind == ElementKind.ANNOTATION_TYPE }
            } else {
                TODO()
            }
            for (annotation in annotations) {
                arg.push(annotation.simpleName.toString(), annotation)
            }
            super.visit(import, arg)
            repeat(annotations.size) {
                arg.pop()
            }
            return null
        }

        override fun visit(
            constructor: ConstructorDeclaration,
            arg: ImportedAnnotationScopes
        ): Nothing? {
            signatureAnalysis.getType(constructor)
            return null
        }

        override fun visit(
            method: MethodDeclaration,
            arg: ImportedAnnotationScopes
        ): Nothing? {
            signatureAnalysis.getType(method)
            return null
        }
    }

    fun parseStubFile(inputStream: InputStream) {
        val stubUnit = JavaParserUtil.parseStubUnit(inputStream)
        val visitor = StubSignatureVisitor()
        stubUnit.accept(visitor, ImportedAnnotationScopes())
        TODO()
    }
}