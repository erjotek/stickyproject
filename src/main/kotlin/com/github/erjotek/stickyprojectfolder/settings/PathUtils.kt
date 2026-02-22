package com.github.erjotek.stickyprojectfolder.settings

internal object PathUtils {
    fun calculateNestedPaths(paths: List<String>): Set<String> {
        val result = HashSet<String>()
        // Given N is typically small (e.g. < 100), O(N^2) complexity is acceptable here.
        // This is much better than O(N^2) during every cell render.
        for (i in paths.indices) {
            val path = paths[i]
            val normalizedPath = path.trimEnd('/')
            if (normalizedPath.isEmpty()) continue

            for (j in paths.indices) {
                if (i == j) continue
                val other = paths[j]
                val normalizedOther = other.trimEnd('/')
                if (normalizedOther.isEmpty() || normalizedOther == normalizedPath) continue

                // Check if path is nested under other
                // e.g. path="a/b", other="a". normalizedPath="a/b", normalizedOther="a"
                // "a/b".startsWith("a/") -> true.
                if (normalizedPath.startsWith("$normalizedOther/")) {
                    result.add(path)
                    break
                }
            }
        }
        return result
    }
}
