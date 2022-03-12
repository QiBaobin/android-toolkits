import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.File
import java.io.OutputStream

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

private const val INTERNAL_VAR = "InternalFeatures"

class IntancesCollectorProcessor(
    private val env: SymbolProcessorEnvironment
) : SymbolProcessor {
    private var pass = 1

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (pass > 1) return emptyList()
        pass += 1

        val packageName = env.options["package-name"] ?: return emptyList()
        // we need the super type name detect sub classes
        val superTypeName = env.options["super-type"] ?: return emptyList()

        val internalVar = env.options["project-id"]?.let { projectId ->
            collectInternalClasses(
                resolver,
                packageName,
                "${
                    projectId.split('-', ':').joinToString(separator = "") {
                        it.lowercase().replaceFirstChar { it.uppercase() }
                    }
                }$INTERNAL_VAR".replaceFirstChar { it.lowercase() }, superTypeName
            )
        }

        val externalVar = (env.options["collect-external-instances-to"]
            ?: env.options["collect-all-instances-to"]?.plus("ExternalInstances"))?.let { variableName ->
            collectExternalClasses(
                resolver,
                packageName,
                variableName,
                superTypeName,
                env.options["collect-external-instances-to"] != null
            )
        }

        env.options["collect-all-instances-to"]?.let { variableName ->
            env.codeGenerator.createNewFile(Dependencies(false), packageName, variableName)
                .appendText(
                    """
                    package $packageName
                    
                    val $variableName = ${
                        if (externalVar != null) {
                            externalVar + if (internalVar != null) " + $internalVar" else ""
                        } else internalVar ?: "emptySetOf<$superTypeName>()"
                    }
                """.trimIndent()
                )
            variableName
        }
        return emptyList()
    }

    @OptIn(KspExperimental::class)
    private fun collectExternalClasses(
        resolver: Resolver,
        packageName: String,
        variableName: String,
        superType: String,
        createEvenEmpty: Boolean,
    ): String? {
        val variables = resolver.getDeclarationsFromPackage(packageName).mapNotNull {
            if (it.containingFile == null) it.simpleName.asString().takeIf {
                it.endsWith(INTERNAL_VAR)
            } else null
        }.joinToString(" + ")

        return if (createEvenEmpty || variables.isNotEmpty()) {
            env.codeGenerator.createNewFile(Dependencies(false), packageName, variableName)
                .appendText(
                    """
                    package $packageName
                    
                    val $variableName = ${variables.ifBlank { "emptySetOf<$superType>()" }}
                """.trimIndent()
                )
            variableName
        } else null
    }

    private fun collectInternalClasses(
        resolver: Resolver,
        packageName: String,
        variableName: String,
        superTypeName: String,
    ): String? {
        val superType = superTypeName.let { name ->
            resolver.run {
                getClassDeclarationByName(getKSNameFromString(name))?.asType(emptyList())
            }
        } ?: return null

        val gen = env.codeGenerator
        val internals = mutableSetOf<KSClassDeclaration>()
        val visitor = GeneratorVisitor(superType) {
            internals.add(it)
        }
        val files = resolver.getAllFiles()
        for (file in files) {
            file.accept(visitor, file)
        }
        val featureFile = gen.createNewFile(Dependencies(false), packageName, variableName)
        val cacheFile = cacheFile(env.codeGenerator.generatedFile.first())
        for (name in cacheFile.readLines()) {
            val d = resolver.getClassDeclarationByName(resolver.getKSNameFromString(name))
            if (d != null) internals.add(d)
        }

        val instances = internals.mapNotNull {
            it.qualifiedName?.asString()?.let { name ->
                name to if (it.classKind == ClassKind.CLASS) "()" else ""
            }
        }
        if (instances.isNotEmpty()) {
            featureFile.appendText(
                """
package $packageName

val $variableName = setOf<$superTypeName>(
${instances.joinToString(",\n") { "    ${it.first}${it.second}" }}
)
            """
            )
            gen.associate(internals.mapNotNull { it.containingFile }, packageName, variableName)
        }

        cacheFile.writeText(instances.joinToString("\n") { it.first })
        return variableName.takeIf { instances.isNotEmpty() }
    }

    private fun cacheFile(generatedFile: File): File =
        File(generatedFile.parent.run {
            val s = File.separator
            // need put outside of ksp generated directory so that it won't move/remove it
            substring(0, indexOf("${s}build${s}generated${s}")) + "${s}build${s}cache.txt"
        }).also { if (!it.exists()) it.createNewFile() }
}


class GeneratorVisitor(
    private val superType: KSType,
    private val action: (KSClassDeclaration) -> Unit,
) : KSVisitor<KSFile, Unit> {
    private val visited = HashSet<Any>()

    private fun checkVisited(symbol: Any) = visited.contains(symbol).also {
        if (!it) visited.add(symbol)
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: KSFile) {
        if (classDeclaration.isPublic() && !classDeclaration.isAbstract() && (classDeclaration.classKind == ClassKind.CLASS || classDeclaration.classKind == ClassKind.OBJECT) && superType.isAssignableFrom(
                classDeclaration.asType(emptyList())
            )
        ) {
            action(classDeclaration)
        }
    }

    override fun visitFile(file: KSFile, data: KSFile) {
        if (checkVisited(file)) return
        for (declaration in file.declarations) {
            declaration.accept(this, file)
        }
    }

    override fun visitAnnotated(annotated: KSAnnotated, data: KSFile) {
    }

    override fun visitAnnotation(annotation: KSAnnotation, data: KSFile) {
    }

    override fun visitCallableReference(reference: KSCallableReference, data: KSFile) {
    }

    override fun visitClassifierReference(reference: KSClassifierReference, data: KSFile) {
    }

    override fun visitDeclaration(declaration: KSDeclaration, data: KSFile) {
    }

    override fun visitDeclarationContainer(
        declarationContainer: KSDeclarationContainer,
        data: KSFile
    ) {
    }

    override fun visitDynamicReference(reference: KSDynamicReference, data: KSFile) {
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: KSFile) {
    }

    override fun visitModifierListOwner(modifierListOwner: KSModifierListOwner, data: KSFile) {
    }

    override fun visitNode(node: KSNode, data: KSFile) {
    }

    override fun visitParenthesizedReference(
        reference: KSParenthesizedReference,
        data: KSFile
    ) {
    }

    override fun visitPropertyAccessor(accessor: KSPropertyAccessor, data: KSFile) {
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: KSFile) {
    }

    override fun visitPropertyGetter(getter: KSPropertyGetter, data: KSFile) {
    }

    override fun visitPropertySetter(setter: KSPropertySetter, data: KSFile) {
    }

    override fun visitReferenceElement(element: KSReferenceElement, data: KSFile) {
    }

    override fun visitTypeAlias(typeAlias: KSTypeAlias, data: KSFile) {
    }

    override fun visitTypeArgument(typeArgument: KSTypeArgument, data: KSFile) {
    }

    override fun visitTypeParameter(typeParameter: KSTypeParameter, data: KSFile) {
    }

    override fun visitTypeReference(typeReference: KSTypeReference, data: KSFile) {
    }

    override fun visitValueArgument(valueArgument: KSValueArgument, data: KSFile) {
    }

    override fun visitValueParameter(valueParameter: KSValueParameter, data: KSFile) {
    }
}

class IntancesCollectorProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return IntancesCollectorProcessor(environment)
    }
}
