package com.github.erjotek.stickyprojectfolder.editor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class CppStickyBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        val ids = listOf("C++", "ObjectiveC", "C")
        return ids.mapNotNull { Language.findLanguageByID(it) }.toTypedArray()
    }

    override fun acceptElement(element: PsiElement): Boolean {
        return classifyCpp(element) != null
    }

    @Suppress("UnstableApiUsage")
    override fun acceptStickyElement(element: PsiElement): Boolean {
        val kind = classifyCpp(element) ?: return false
        return isKindEnabledForSticky(kind)
    }

    override fun getElementInfo(element: PsiElement): String {
        val kind = classifyCpp(element) ?: return ""
        return labelFor(element, kind)
    }
}

private val CPP_SIMPLE_NAME_KIND: Map<String, StickyKind> = mapOf(
    "OCFunction" to StickyKind.CLASS_OR_FUNCTION,
    "OCFunctionDeclaration" to StickyKind.CLASS_OR_FUNCTION,
    "OCFunctionDefinition" to StickyKind.CLASS_OR_FUNCTION,
    "OCMethod" to StickyKind.CLASS_OR_FUNCTION,
    "OCMethodDeclaration" to StickyKind.CLASS_OR_FUNCTION,
    "OCMethodDefinition" to StickyKind.CLASS_OR_FUNCTION,
    "OCClass" to StickyKind.CLASS_OR_FUNCTION,
    "OCClassDeclaration" to StickyKind.CLASS_OR_FUNCTION,
    "OCStruct" to StickyKind.CLASS_OR_FUNCTION,
    "OCStructDeclaration" to StickyKind.CLASS_OR_FUNCTION,
    "OCStructSpecifier" to StickyKind.CLASS_OR_FUNCTION,
    "OCUnion" to StickyKind.CLASS_OR_FUNCTION,
    "OCUnionSpecifier" to StickyKind.CLASS_OR_FUNCTION,
    "OCEnum" to StickyKind.CLASS_OR_FUNCTION,
    "OCEnumSpecifier" to StickyKind.CLASS_OR_FUNCTION,
    "OCNamespace" to StickyKind.NAMESPACE,
    "OCNamespaceDefinition" to StickyKind.NAMESPACE,
    "OCIfStatement" to StickyKind.IF,
    "OCForStatement" to StickyKind.FOR,
    "OCForRangeStatement" to StickyKind.FOREACH,
    "OCForeachStatement" to StickyKind.FOREACH,
    "OCWhileStatement" to StickyKind.WHILE,
    "OCDoStatement" to StickyKind.DO,
    "OCDoWhileStatement" to StickyKind.DO,
    "OCSwitchStatement" to StickyKind.SWITCH,
    "OCTryStatement" to StickyKind.TRY,
    "OCTryBlockStatement" to StickyKind.TRY,
    "OCCatchStatement" to StickyKind.CATCH,
    "OCCatchBlock" to StickyKind.CATCH
)

private fun classifyCpp(element: PsiElement): StickyKind? =
    refineCppKind(element, classifyCppBase(element))

private fun classifyCppBase(element: PsiElement): StickyKind? =
    classifyBySimpleName(element, CPP_SIMPLE_NAME_KIND)
        ?: classifyCppBySimpleNameHeuristics(element)
        ?: classifyCppByElementType(element)
        ?: classifyCppByText(element)

private fun classifyCppBySimpleNameHeuristics(element: PsiElement): StickyKind? {
    val simple = cppSimpleName(element)
    val lower = simple.lowercase()
    if (!simple.startsWith("OC") && !simple.startsWith("Cpp") && !simple.startsWith("Cxx")) return null

    return when {
        lower.contains("ifstatement") -> StickyKind.IF
        lower.contains("foreach") || lower.contains("forrange") || lower.contains("forin") -> StickyKind.FOREACH
        lower.contains("forstatement") -> StickyKind.FOR
        lower.contains("dowhile") || lower.contains("dostatement") -> StickyKind.DO
        lower.contains("whilestatement") -> StickyKind.WHILE
        lower.contains("switchstatement") -> StickyKind.SWITCH
        lower.contains("trystatement") || lower.contains("tryblock") -> StickyKind.TRY
        lower.contains("catchstatement") || lower.contains("catchsection") || lower.contains("catchblock") -> StickyKind.CATCH
        lower.contains("finally") -> StickyKind.FINALLY
        lower.contains("namespace") && (lower.contains("definition") || lower.endsWith("namespace")) -> StickyKind.NAMESPACE
        lower.contains("function") && !lower.contains("call") -> StickyKind.CLASS_OR_FUNCTION
        lower.contains("method") || lower.contains("constructor") || lower.contains("destructor") -> StickyKind.CLASS_OR_FUNCTION
        lower.endsWith("class") || lower.contains("classdeclaration") || lower.contains("classspecifier") -> StickyKind.CLASS_OR_FUNCTION
        lower.endsWith("struct") || lower.contains("structdeclaration") || lower.contains("structspecifier") -> StickyKind.CLASS_OR_FUNCTION
        lower.endsWith("union") || lower.contains("unionspecifier") -> StickyKind.CLASS_OR_FUNCTION
        lower.endsWith("enum") || lower.contains("enumspecifier") -> StickyKind.CLASS_OR_FUNCTION
        else -> null
    }
}

private fun classifyCppByElementType(element: PsiElement): StickyKind? {
    val type = cppElementTypeName(element) ?: return null
    return when {
        "IF_STATEMENT" in type -> StickyKind.IF
        "ELSE_IF" in type -> StickyKind.ELSEIF
        "ELSE" in type && "BRANCH" in type -> StickyKind.ELSE
        "FOREACH" in type || "FOR_EACH" in type || "FOR_RANGE" in type || "FOR_IN" in type -> StickyKind.FOREACH
        "FOR_STATEMENT" in type -> StickyKind.FOR
        "DO_WHILE" in type || "DO_STATEMENT" in type -> StickyKind.DO
        "WHILE_STATEMENT" in type -> StickyKind.WHILE
        "SWITCH_STATEMENT" in type -> StickyKind.SWITCH
        "TRY_STATEMENT" in type || "TRY_BLOCK" in type -> StickyKind.TRY
        "CATCH_STATEMENT" in type || "CATCH_BLOCK" in type || "CATCH_SECTION" in type -> StickyKind.CATCH
        "FINALLY" in type -> StickyKind.FINALLY
        "NAMESPACE_DEFINITION" in type || "NAMESPACE_DECLARATION" in type -> StickyKind.NAMESPACE
        "FUNCTION_DEFINITION" in type || "FUNCTION_DECLARATION" in type -> StickyKind.CLASS_OR_FUNCTION
        "CLASS_SPECIFIER" in type || "CLASS_DECLARATION" in type ||
            "STRUCT_SPECIFIER" in type || "STRUCT_DECLARATION" in type ||
            "UNION_SPECIFIER" in type || "ENUM_SPECIFIER" in type -> StickyKind.CLASS_OR_FUNCTION
        else -> null
    }
}

private fun classifyCppByText(element: PsiElement): StickyKind? {
    if (!canClassifyCppByText(element)) return null
    val firstLine = cppFirstLine(element)?.trimStart('}', ';')?.trimStart() ?: return null
    val lower = firstLine.lowercase()

    return when {
        CPP_ELSE_IF_REGEX.containsMatchIn(lower) -> StickyKind.ELSEIF
        CPP_ELSE_REGEX.containsMatchIn(lower) -> StickyKind.ELSE
        CPP_IF_REGEX.containsMatchIn(lower) -> StickyKind.IF
        CPP_FOR_REGEX.containsMatchIn(lower) -> if (CPP_RANGE_FOR_REGEX.containsMatchIn(lower)) StickyKind.FOREACH else StickyKind.FOR
        CPP_WHILE_REGEX.containsMatchIn(lower) -> StickyKind.WHILE
        CPP_DO_REGEX.containsMatchIn(lower) -> StickyKind.DO
        CPP_SWITCH_REGEX.containsMatchIn(lower) -> StickyKind.SWITCH
        CPP_TRY_REGEX.containsMatchIn(lower) -> StickyKind.TRY
        CPP_CATCH_REGEX.containsMatchIn(lower) -> StickyKind.CATCH
        CPP_NAMESPACE_REGEX.containsMatchIn(lower) -> StickyKind.NAMESPACE
        CPP_CLASS_LIKE_REGEX.containsMatchIn(lower) -> StickyKind.CLASS_OR_FUNCTION
        else -> null
    }
}

private fun refineCppKind(element: PsiElement, baseKind: StickyKind?): StickyKind? {
    val firstLine = cppFirstLine(element)?.trimStart('}', ';')?.trimStart()?.lowercase()
    if (baseKind == StickyKind.IF && firstLine?.let { CPP_ELSE_IF_REGEX.containsMatchIn(it) } == true) return StickyKind.ELSEIF
    if (baseKind == StickyKind.IF && firstLine?.let { CPP_ELSE_REGEX.containsMatchIn(it) } == true) return StickyKind.ELSE

    val parent = element.parent ?: return baseKind
    val parentKind = classifyCppBase(parent)
    if (parentKind == StickyKind.IF) {
        val elseBranch = invokeNoArg(parent, "getElseBranch")
            ?: invokeNoArg(parent, "getElse")
            ?: invokeNoArg(parent, "getElseStatement")
        if (elseBranch === element) {
            return if (baseKind == StickyKind.IF || firstLine?.let { CPP_IF_REGEX.containsMatchIn(it) } == true) StickyKind.ELSEIF else StickyKind.ELSE
        }
        if (firstLine?.let { CPP_ELSE_IF_REGEX.containsMatchIn(it) } == true) return StickyKind.ELSEIF
        if (firstLine?.let { CPP_ELSE_REGEX.containsMatchIn(it) } == true) return StickyKind.ELSE
    }

    return baseKind
}

private fun canClassifyCppByText(element: PsiElement): Boolean {
    if (element.firstChild == null) return false
    val type = cppElementTypeName(element) ?: return false
    return "STATEMENT" in type || "DECLARATION" in type || "DEFINITION" in type || "BLOCK" in type
}

private fun cppSimpleName(element: PsiElement): String =
    simpleNameWithoutImpl(element)

private fun cppElementTypeName(element: PsiElement): String? =
    element.node?.elementType?.toString()?.uppercase()

private fun cppFirstLine(element: PsiElement): String? =
    element.text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }

private val CPP_ELSE_IF_REGEX = Regex("""^else\s+if\b""")
private val CPP_ELSE_REGEX = Regex("""^else\b""")
private val CPP_IF_REGEX = Regex("""^if\b""")
private val CPP_FOR_REGEX = Regex("""^for\b""")
private val CPP_RANGE_FOR_REGEX = Regex("""^for\b\s*\([^;)]*:[^)]*\)""")
private val CPP_WHILE_REGEX = Regex("""^while\b""")
private val CPP_DO_REGEX = Regex("""^do\b""")
private val CPP_SWITCH_REGEX = Regex("""^switch\b""")
private val CPP_TRY_REGEX = Regex("""^try\b""")
private val CPP_CATCH_REGEX = Regex("""^catch\b""")
private val CPP_NAMESPACE_REGEX = Regex("""^namespace\b""")
private val CPP_CLASS_LIKE_REGEX = Regex("""^(class|struct|union)\b""")
