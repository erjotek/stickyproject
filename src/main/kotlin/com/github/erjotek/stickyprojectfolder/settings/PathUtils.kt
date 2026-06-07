package com.github.erjotek.stickyprojectfolder.settings

internal object PathUtils {
    fun calculateNestedPaths(paths: List<String>): Set<String> {
        val result = HashSet<String>()
        val normalizedSet = paths.map { it.trimEnd('/') }.filter { it.isNotEmpty() }.toSet()

        for (path in paths) {
            var current = path.trimEnd('/')
            if (current.isEmpty()) continue

            while (true) {
                val lastSlashIndex = current.lastIndexOf('/')
                if (lastSlashIndex == -1) break
                current = current.substring(0, lastSlashIndex)
                if (current.isEmpty()) break

                if (normalizedSet.contains(current)) {
                    result.add(path)
                    break
                }
            }
        }
        return result
    }
}
