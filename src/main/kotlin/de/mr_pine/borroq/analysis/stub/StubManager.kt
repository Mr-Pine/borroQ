package de.mr_pine.borroq.analysis.stub

import de.mr_pine.borroq.analysis.MemberTypeAnalysis
import org.checkerframework.com.github.javaparser.ast.StubUnit
import org.checkerframework.com.github.javaparser.ast.body.ConstructorDeclaration
import org.checkerframework.com.github.javaparser.ast.body.MethodDeclaration
import org.checkerframework.com.github.javaparser.ast.visitor.GenericVisitorAdapter
import org.checkerframework.framework.qual.StubFiles
import org.checkerframework.framework.source.SourceChecker
import org.checkerframework.framework.util.JavaParserUtil
import org.checkerframework.javacutil.BugInCF
import org.checkerframework.javacutil.SystemUtil
import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import java.util.jar.JarEntry
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import kotlin.streams.asSequence

class StubManager(
    private val signatureAnalysis: MemberTypeAnalysis,
    private val processingEnvironment: ProcessingEnvironment,
    private val elements: Elements,
    private val options: StubOptions
) {
    data class StubOptions(
        val parseAnnotatedJdk: Boolean,
        val ignoreJdkAstub: Boolean,
        val permitMissingJdk: Boolean,
        val debug: Boolean,
        val additionalStubs: List<String>
    ) {
        val shouldParseJdkAstub: Boolean
            get() = !ignoreJdkAstub

        companion object {
            val SourceChecker.stubOptions: StubOptions
                get() {
                    val argumentStubs =
                        getOption("stubs")?.split(File.pathSeparator)?.filterNot(String::isNullOrBlank).orEmpty()
                    val checkerAnnotationStubs =
                        StubOptions::class.annotations.filterIsInstance<StubFiles>().flatMap { it.value.asList() }
                    val checkerMethodStubs = extraStubFiles
                    val additionalStubs = argumentStubs + checkerAnnotationStubs + checkerMethodStubs

                    return StubOptions(
                        hasOption("parseAnnotatedJdk"),
                        hasOption("ignorejdkastub"),
                        hasOption("permitMissingJdk"),
                        hasOption("stubDebug"),
                        additionalStubs
                    )
                }
        }
    }

    fun parseStubFiles() {
        if (options.parseAnnotatedJdk) {
            val resourceURL = signatureAnalysis.javaClass.getResource("/annotated-jdk")
            if (resourceURL != null) {
                require(resourceURL.protocol == "jar") { "JDK stubs must be in a jar file" }

                (resourceURL.openConnection() as JarURLConnection).apply {
                    defaultUseCaches = false
                    useCaches = false
                    connect()
                }.jarFile.use { jarFile ->
                    for (entry in jarFile.stream().asSequence().sortedBy(Any::toString).filterNot(JarEntry::isDirectory)
                        .filter { it.name.startsWith("annotated-jdk") || it.name.endsWith(".java") }) {
                        val fqn =
                            entry.name.substringAfter("/share/classes/").substringBeforeLast(".java").replace("/", ".")
                        jarFile.getInputStream(entry).use {
                            parseStubFile(it)
                        }
                    }
                }

            } else if (!options.permitMissingJdk) {
                throw BugInCF(
                    "JDK not found for type factory ${signatureAnalysis.javaClass.getSimpleName()}"
                )
            }
        }
        if (options.shouldParseJdkAstub) {
            val jdkVersion = SystemUtil.getReleaseValue(processingEnvironment) ?: SystemUtil.jreVersion.toString()
            for (astubFile in listOf("", jdkVersion).map { "jdk$it.astub" }) {
                signatureAnalysis.javaClass.getResourceAsStream(astubFile)?.use {
                    parseStubFile(
                        it
                    )
                }
            }
        }

        for (stubFile in options.additionalStubs) {
            signatureAnalysis.javaClass.getResourceAsStream(stubFile)?.use {
                parseStubFile(
                    it
                )
            }
        }
    }

    typealias ImportMap = Map<String, TypeElement>

    inner class StubSignatureVisitor : GenericVisitorAdapter<Nothing?, ImportMap?>() {
        override fun visit(
            n: StubUnit, arg: ImportMap?
        ): Nothing? {
            for (compilationUnit in n.compilationUnits) {
                val imports = buildMap {
                    for (import in compilationUnit.imports) {
                        if (!import.isStatic) {
                            val annotations = if (import.isAsterisk) {
                                val packageName = import.name.asString()
                                val element = elements.getPackageElement(packageName)
                                element?.enclosedElements?.filterIsInstance<TypeElement>()
                                    ?.filter { it.kind == ElementKind.ANNOTATION_TYPE }.orEmpty()
                            } else {
                                val annotationName = import.name.asString()
                                val annotation = elements.getTypeElement(annotationName)
                                listOfNotNull(annotation)
                            }
                            for (annotation in annotations) {
                                put(annotation.simpleName.toString(), annotation)
                            }
                        }
                    }
                }

                compilationUnit.accept(this, imports)
            }
            return null
        }

        override fun visit(
            constructor: ConstructorDeclaration, arg: ImportMap?
        ): Nothing? {
            signatureAnalysis.getType(constructor, arg!!)
            return null
        }

        override fun visit(
            method: MethodDeclaration, arg: ImportMap?
        ): Nothing? {
            signatureAnalysis.getType(method, arg!!)
            return null
        }
    }

    fun parseStubFile(inputStream: InputStream) {
        val stubUnit = JavaParserUtil.parseStubUnit(inputStream)
        val visitor = StubSignatureVisitor()
        stubUnit.accept(visitor, null)
    }
}