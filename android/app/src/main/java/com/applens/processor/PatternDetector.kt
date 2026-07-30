package com.applens.processor

/**
 * Detect component patterns in a parsed screen tree.
 * Tags screens based on the UI components present.
 */
object PatternDetector {

    data class DetectionResult(
        val patterns: List<String>,
        val componentCounts: Map<String, Int>,
        val summary: String,
        val totalNodes: Int
    )

    fun detect(tree: ScreenTree): DetectionResult {
        if (tree.root == null) return DetectionResult(emptyList(), emptyMap(), "Empty screen", 0)

        val patterns = mutableSetOf<String>()
        val componentCounts = mutableMapOf<String, Int>()

        fun walk(node: ComponentNode) {
            val cls = node.className.lowercase()
            val shortName = XmlProcessor.getClassShortName(node.className)
            componentCounts[shortName] = (componentCounts[shortName] ?: 0) + 1

            when {
                cls.contains("recyclerview") || cls.contains("listview") -> patterns.add("List")
                cls.contains("edittext") || cls.contains("textinputedittext") || cls.contains("textinput") -> patterns.add("Input Form")
                cls.contains("bottomnavigationview") || cls.contains("bottomnavigation") -> patterns.add("Bottom Nav")
                cls.contains("navigationview") || cls.contains("drawerlayout") -> patterns.add("Navigation Drawer")
                cls.contains("webview") -> patterns.add("WebView")
                cls.contains("viewpager") || cls.contains("viewpager2") -> patterns.add("ViewPager")
                cls.contains("tablayout") || cls.contains("tabhost") -> patterns.add("Tab Layout")
                cls.contains("toolbar") || cls.contains("appbar") || cls.contains("actionbar") -> patterns.add("Toolbar/AppBar")
                cls.contains("floatingactionbutton") || cls.contains("fab") -> patterns.add("FAB")
                cls.contains("imageview") || cls.contains("image") -> patterns.add("Images")
                cls.contains("checkbox") -> patterns.add("Checkbox")
                cls.contains("radiobutton") -> patterns.add("Radio Button")
                cls.contains("switch") || cls.contains("togglebutton") -> patterns.add("Switch/Toggle")
                cls.contains("spinner") || cls.contains("dropdown") -> patterns.add("Dropdown/Spinner")
                cls.contains("searchview") || cls.contains("searchbar") -> patterns.add("Search Bar")
                cls.contains("datepicker") || cls.contains("timepicker") || cls.contains("calendar") -> patterns.add("Date/Time Picker")
                cls.contains("video") || cls.contains("exoplayer") || cls.contains("mediaplayer") -> patterns.add("Media Player")
                cls.contains("dialog") || cls.contains("alertdialog") || cls.contains("modal") -> patterns.add("Dialog")
                cls.contains("progressbar") || cls.contains("loading") -> patterns.add("Progress/Loading")
            }

            for (child in node.children) walk(child)
        }

        walk(tree.root)

        val topComponents = componentCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key}(${it.value})" }

        return DetectionResult(
            patterns = patterns.toList(),
            componentCounts = componentCounts,
            summary = topComponents.ifEmpty { "No components" },
            totalNodes = tree.nodeCount
        )
    }
}
