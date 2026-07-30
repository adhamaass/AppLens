package com.applens.processor

import android.util.Xml
import org.xmlpullparser.v1.XmlPullParser
import java.io.StringReader

/**
 * Parse a UIAutomator XML dump into a structured component tree.
 * Replaces the Node.js fast-xml-parser logic — runs entirely on-device.
 */
data class ComponentNode(
    val className: String = "",
    val resourceId: String = "",
    val text: String = "",
    val contentDesc: String = "",
    val bounds: String = "",
    val boundsParsed: Bounds? = null,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val focusable: Boolean = false,
    val enabled: Boolean = true,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val longClickable: Boolean = false,
    val pkg: String = "",
    val depth: Int = 0,
    val children: MutableList<ComponentNode> = mutableListOf()
)

data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int, val x2: Int, val y2: Int)

data class ScreenTree(
    val screenId: String,
    val root: ComponentNode?,
    val nodeCount: Int
)

object XmlProcessor {

    fun parseScreenXml(xml: String, screenId: String): ScreenTree {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var root: ComponentNode? = null
            val stack = ArrayDeque<ComponentNode>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "node") {
                            val node = parseNodeAttributes(parser, stack.size)
                            if (stack.isEmpty()) {
                                root = node
                            } else {
                                stack.last().children.add(node)
                            }
                            stack.addLast(node)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "node" && stack.isNotEmpty()) {
                            stack.removeLast()
                        }
                    }
                }
                event = parser.next()
            }

            val count = root?.let { countNodes(it) } ?: 0
            return ScreenTree(screenId, root, count)
        } catch (e: Exception) {
            return ScreenTree(screenId, null, 0)
        }
    }

    private fun parseNodeAttributes(parser: XmlPullParser, depth: Int): ComponentNode {
        val boundsStr = parser.getAttributeValue(null, "bounds") ?: ""
        return ComponentNode(
            className = parser.getAttributeValue(null, "class") ?: "",
            resourceId = parser.getAttributeValue(null, "resource-id") ?: "",
            text = parser.getAttributeValue(null, "text") ?: "",
            contentDesc = parser.getAttributeValue(null, "content-desc") ?: "",
            bounds = boundsStr,
            boundsParsed = parseBounds(boundsStr),
            clickable = parser.getAttributeValue(null, "clickable") == "true",
            scrollable = parser.getAttributeValue(null, "scrollable") == "true",
            focusable = parser.getAttributeValue(null, "focusable") == "true",
            enabled = parser.getAttributeValue(null, "enabled") != "false",
            checkable = parser.getAttributeValue(null, "checkable") == "true",
            checked = parser.getAttributeValue(null, "checked") == "true",
            longClickable = parser.getAttributeValue(null, "long-clickable") == "true",
            pkg = parser.getAttributeValue(null, "package") ?: "",
            depth = depth
        )
    }

    fun parseBounds(boundsStr: String): Bounds? {
        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = regex.find(boundsStr) ?: return null
        val x1 = match.groupValues[1].toInt()
        val y1 = match.groupValues[2].toInt()
        val x2 = match.groupValues[3].toInt()
        val y2 = match.groupValues[4].toInt()
        return Bounds(x1, y1, x2 - x1, y2 - y1, x2, y2)
    }

    fun countNodes(node: ComponentNode): Int {
        var count = 1
        for (child in node.children) count += countNodes(child)
        return count
    }

    fun getClassShortName(className: String): String {
        if (className.isEmpty()) return "View"
        val parts = className.split(".")
        return parts.lastOrNull() ?: className
    }
}
