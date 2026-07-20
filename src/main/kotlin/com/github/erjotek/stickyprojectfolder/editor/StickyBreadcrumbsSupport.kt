package com.github.erjotek.stickyprojectfolder.editor

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal enum class StickyKind {
    CLASS_OR_FUNCTION,
    NAMESPACE,
    IF, ELSEIF, ELSE,
    FOR, FOREACH, WHILE, DO,
    SWITCH, TRY, CATCH, FINALLY,
    ARRAY
}

internal fun isKindEnabledForSticky(kind: StickyKind): Boolean {
    val state = StickyProjectSettings.instance.state
    return when (kind) {
        StickyKind.CLASS_OR_FUNCTION -> true
        StickyKind.NAMESPACE -> false
        StickyKind.IF, StickyKind.ELSEIF, StickyKind.ELSE,
        StickyKind.FOR, StickyKind.FOREACH, StickyKind.WHILE, StickyKind.DO,
        StickyKind.SWITCH, StickyKind.TRY, StickyKind.CATCH, StickyKind.FINALLY -> state.stickyControlBlocks
        StickyKind.ARRAY -> state.stickyArrayScopes
    }
}

internal fun classifyBySimpleName(element: PsiElement, table: Map<String, StickyKind>): StickyKind? {
    val cls = element.javaClass
    return SIMPLE_NAME_KIND_CACHE.get(cls).computeIfAbsent(table) {
        CachedStickyKind(classifyClassBySimpleName(cls, it))
    }.kind
}

internal fun classifyByClassNameOrSimpleName(
    element: PsiElement,
    classNameTable: Map<String, StickyKind>,
    simpleNameTable: Map<String, StickyKind>
): StickyKind? {
    val cls = element.javaClass
    val key = ClassNameOrSimpleNameTables(classNameTable, simpleNameTable)
    return CLASS_NAME_OR_SIMPLE_NAME_KIND_CACHE.get(cls).computeIfAbsent(key) {
        CachedStickyKind(classifyClassByClassNameOrSimpleName(cls, it.classNameTable, it.simpleNameTable))
    }.kind
}

internal fun parentSimpleName(element: PsiElement): String? {
    val parent = element.parent ?: return null
    return simpleNameWithoutImpl(parent.javaClass)
}

internal fun simpleNameWithoutImpl(element: PsiElement): String =
    simpleNameWithoutImpl(element.javaClass)

internal fun invokeNoArg(target: Any, methodName: String): Any? = runCatching {
    val method = findNoArgMethod(target.javaClass, methodName) ?: return null
    method.invoke(target)
}.getOrNull()

internal fun labelFor(element: PsiElement, kind: StickyKind): String {
    if (kind == StickyKind.ELSE) return "else"
    if (kind == StickyKind.NAMESPACE) {
        runCatching {
            val fqnMethod = findNoArgMethod(element.javaClass, "getFQN")
            (fqnMethod?.invoke(element) as? String)?.removePrefix("\\")?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    if (kind == StickyKind.CLASS_OR_FUNCTION || kind == StickyKind.NAMESPACE) {
        (element as? PsiNamedElement)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        runCatching {
            val nameMethod = findNoArgMethod(element.javaClass, "getName")
            (nameMethod?.invoke(element) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    val firstLine = element.text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return kind.name.lowercase()
    if (kind == StickyKind.ELSEIF && !firstLine.startsWith("elseif") && !firstLine.startsWith("else if")) {
        return ("else " + firstLine).take(120)
    }
    return firstLine.take(120)
}

private data class CachedStickyKind(val kind: StickyKind?)

private data class CachedMethod(val method: Method?)

private data class ClassNameOrSimpleNameTables(
    val classNameTable: Map<String, StickyKind>,
    val simpleNameTable: Map<String, StickyKind>
)

private val SIMPLE_NAME_KIND_CACHE = object : ClassValue<ConcurrentHashMap<Map<String, StickyKind>, CachedStickyKind>>() {
    override fun computeValue(type: Class<*>): ConcurrentHashMap<Map<String, StickyKind>, CachedStickyKind> =
        ConcurrentHashMap()
}

private val CLASS_NAME_OR_SIMPLE_NAME_KIND_CACHE = object : ClassValue<ConcurrentHashMap<ClassNameOrSimpleNameTables, CachedStickyKind>>() {
    override fun computeValue(type: Class<*>): ConcurrentHashMap<ClassNameOrSimpleNameTables, CachedStickyKind> =
        ConcurrentHashMap()
}

private val RAW_SIMPLE_NAME_CACHE = object : ClassValue<String>() {
    override fun computeValue(type: Class<*>): String = type.simpleName
}

private val SIMPLE_NAME_WITHOUT_IMPL_CACHE = object : ClassValue<String>() {
    override fun computeValue(type: Class<*>): String = rawSimpleName(type).removeSuffix("Impl")
}

private val NO_ARG_METHOD_CACHE = object : ClassValue<ConcurrentHashMap<String, CachedMethod>>() {
    override fun computeValue(type: Class<*>): ConcurrentHashMap<String, CachedMethod> =
        ConcurrentHashMap()
}

private fun classifyClassBySimpleName(cls: Class<*>, table: Map<String, StickyKind>): StickyKind? {
    val simple = simpleNameWithoutImpl(cls)
    table[simple]?.let { return it }

    for (iface in cls.interfaces) {
        table[rawSimpleName(iface)]?.let { return it }
    }
    var c: Class<*>? = cls.superclass
    while (c != null && c != Any::class.java) {
        val s = simpleNameWithoutImpl(c)
        table[s]?.let { return it }
        for (iface in c.interfaces) {
            table[rawSimpleName(iface)]?.let { return it }
        }
        c = c.superclass
    }
    return null
}

private fun classifyClassByClassNameOrSimpleName(
    cls: Class<*>,
    classNameTable: Map<String, StickyKind>,
    simpleNameTable: Map<String, StickyKind>
): StickyKind? {
    var c: Class<*>? = cls
    while (c != null && c != Any::class.java) {
        classNameTable[c.name]?.let { return it }
        simpleNameTable[simpleNameWithoutImpl(c)]?.let { return it }
        c = c.superclass
    }
    return null
}

private fun rawSimpleName(cls: Class<*>): String =
    RAW_SIMPLE_NAME_CACHE.get(cls)

private fun simpleNameWithoutImpl(cls: Class<*>): String =
    SIMPLE_NAME_WITHOUT_IMPL_CACHE.get(cls)

private fun findNoArgMethod(cls: Class<*>, methodName: String): Method? =
    NO_ARG_METHOD_CACHE.get(cls).computeIfAbsent(methodName) {
        CachedMethod(runCatching {
            cls.methods.firstOrNull { method -> method.name == methodName && method.parameterCount == 0 }
        }.getOrNull())
    }.method
