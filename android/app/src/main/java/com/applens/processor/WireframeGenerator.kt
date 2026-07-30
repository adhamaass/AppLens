package com.applens.processor

/**
 * Generate a wireframe SVG from a parsed component tree.
 * Draws rectangles per node, labeled with class shortname, sized proportionally.
 * No screenshots — purely deterministic from bounds data.
 */
object WireframeGenerator {

    private const val COLORS_BG = "#f5f5f5"
    private const val COLORS_BORDER = "#333333"
    private const val COLORS_FILL = "#ffffff"
    private const val COLORS_TEXT = "#333333"
    private const val COLORS_CLICKABLE = "#4a90d9"
    private const val COLORS_SCROLLABLE = "#e67e22"
    private const val COLORS_TEXT_INPUT = "#2ecc71"
    private const val COLORS_BUTTON = "#3498db"
    private const val COLORS_IMAGE = "#9b59b6"
    private const val COLORS_LIST = "#f39c12"
    private const val COLORS_TEXT_VIEW = "#34495e"
    private const val COLORS_HEADER = "#2c3e50"
    private const val COLORS_CONTAINER = "#ecf0f1"

    fun generateSVG(tree: ScreenTree): String {
        if (tree.root == null) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1080\" height=\"2400\">" +
                   "<text x=\"20\" y=\"30\" fill=\"#999\">No data for ${tree.screenId}</text></svg>"
        }

        val rootBounds = tree.root.boundsParsed
        val screenW = rootBounds?.width ?: 1080
        val screenH = rootBounds?.height ?: 2400

        val maxW = 540
        val scale = maxW.toFloat() / screenW.toFloat()
        val svgW = (screenW * scale).toInt()
        val svgH = (screenH * scale).toInt()

        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$svgW\" height=\"$svgH\" viewBox=\"0 0 $svgW $svgH\">")
        sb.append("<rect width=\"$svgW\" height=\"$svgH\" fill=\"$COLORS_BG\" stroke=\"$COLORS_BORDER\" stroke-width=\"1\"/>")

        drawNode(tree.root, scale, sb)

        sb.append("<text x=\"10\" y=\"${svgH - 10}\" font-family=\"monospace\" font-size=\"10\" fill=\"#999\">")
        sb.append("${tree.screenId} | ${tree.nodeCount} nodes | ${svgW}x${svgH}</text>")
        sb.append("</svg>")
        return sb.toString()
    }

    private fun drawNode(node: ComponentNode, scale: Float, sb: StringBuilder) {
        val b = node.boundsParsed ?: return
        val x = (b.x * scale).toInt()
        val y = (b.y * scale).toInt()
        val w = (b.width * scale).toInt()
        val h = (b.height * scale).toInt()

        if (w < 2 || h < 2) return

        val shortClass = XmlProcessor.getClassShortName(node.className)
        val fill = getNodeColor(node)
        val stroke = if (node.clickable) COLORS_CLICKABLE else COLORS_BORDER
        val strokeWidth = if (node.clickable) 2 else 1

        sb.append("<rect x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\" fill=\"$fill\" fill-opacity=\"0.3\" stroke=\"$stroke\" stroke-width=\"$strokeWidth\" rx=\"2\"/>")

        if (w > 40 && h > 16) {
            val fontSize = minOf(12, maxOf(8, h / 3))
            val labelWidth = minOf(w - 4, (shortClass.length * fontSize * 0.6 + 8).toInt())

            sb.append("<rect x=\"${x + 2}\" y=\"${y + 2}\" width=\"$labelWidth\" height=\"${fontSize + 4}\" fill=\"#ffffff\" fill-opacity=\"0.85\" rx=\"1\"/>")
            sb.append("<text x=\"${x + 6}\" y=\"${y + fontSize + 3}\" font-family=\"monospace\" font-size=\"$fontSize\" fill=\"$COLORS_TEXT\">")
            sb.append(escapeXml(shortClass))
            sb.append("</text>")

            if (node.text.isNotEmpty() && w > 60 && h > 20) {
                val maxChars = (w / (fontSize * 0.55)).toInt()
                val displayText = if (node.text.length <= maxChars) node.text else node.text.substring(0, minOf(maxChars - 3, node.text.length)) + "..."
                sb.append("<text x=\"${x + 6}\" y=\"${y + h / 2 + 4}\" font-family=\"sans-serif\" font-size=\"$fontSize\" fill=\"$COLORS_TEXT\">")
                sb.append(escapeXml(displayText))
                sb.append("</text>")
            }
        }

        for (child in node.children) drawNode(child, scale, sb)
    }

    private fun getNodeColor(node: ComponentNode): String {
        val cls = node.className.lowercase()
        return when {
            cls.contains("button") -> COLORS_BUTTON
            cls.contains("edittext") || cls.contains("textinput") -> COLORS_TEXT_INPUT
            cls.contains("imageview") || cls.contains("image") -> COLORS_IMAGE
            cls.contains("recyclerview") || cls.contains("listview") || cls.contains("scrollview") -> COLORS_LIST
            cls.contains("textview") || cls.contains("text") -> COLORS_TEXT_VIEW
            cls.contains("toolbar") || cls.contains("appbar") || cls.contains("actionbar") -> COLORS_HEADER
            cls.contains("layout") || cls.contains("container") || cls.contains("group") -> COLORS_CONTAINER
            node.clickable -> COLORS_CLICKABLE
            node.scrollable -> COLORS_SCROLLABLE
            else -> COLORS_FILL
        }
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
