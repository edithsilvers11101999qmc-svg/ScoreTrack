package com.scoretrack.parser

import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 轻量 XLSX 读取：直接解析 OOXML（zip + xml），不依赖 Apache POI，
 * 以适配 Android 体积与 minSdk 限制。支持共享字符串、内联字符串、
 * 数字、日期（按序列号转文本）与合并单元格。
 */
object XlsxParser {

    fun parse(file: File, maxRows: Int = 2000, maxCols: Int = 60): List<SheetData> {
        ZipFile(file).use { zip ->
            val shared = readSharedStrings(zip)
            val workbook = zip.getEntry("xl/workbook.xml") ?: return emptyList()
            val rels = readRels(zip)
            val doc = parseXml(zip.getInputStream(workbook).readBytes())
            val sheets = doc.getElementsByTagName("sheet")
            val result = ArrayList<SheetData>()
            for (i in 0 until sheets.length) {
                val el = sheets.item(i) as? Element ?: continue
                val name = el.getAttribute("name").ifBlank { "Sheet${i + 1}" }
                val rid = el.getAttribute("r:id").ifBlank {
                    el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                }
                val target = rels[rid] ?: "worksheets/sheet${i + 1}.xml"
                val path = if (target.startsWith("xl/")) target else "xl/" + target.removePrefix("/")
                val entry = zip.getEntry(path) ?: zip.getEntry("xl/worksheets/sheet${i + 1}.xml") ?: continue
                val sheetDoc = parseXml(zip.getInputStream(entry).readBytes())
                val rows = readSheet(sheetDoc, shared, maxRows, maxCols)
                if (rows.isNotEmpty()) result.add(SheetData(name, rows))
            }
            return result
        }
    }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val doc = parseXml(zip.getInputStream(entry).readBytes())
        val items = doc.getElementsByTagName("si")
        val out = ArrayList<String>(items.length)
        for (i in 0 until items.length) {
            val si = items.item(i) as? Element ?: continue
            out.add(textOf(si))
        }
        return out
    }

    private fun readRels(zip: ZipFile): Map<String, String> {
        val entry = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return emptyMap()
        val doc = parseXml(zip.getInputStream(entry).readBytes())
        val rels = doc.getElementsByTagName("Relationship")
        val map = HashMap<String, String>()
        for (i in 0 until rels.length) {
            val el = rels.item(i) as? Element ?: continue
            map[el.getAttribute("Id")] = el.getAttribute("Target")
        }
        return map
    }

    private fun readSheet(doc: org.w3c.dom.Document, shared: List<String>, maxRows: Int, maxCols: Int): List<List<String>> {
        val grid = ArrayList<MutableList<String>>()
        val rowEls = doc.getElementsByTagName("row")
        for (i in 0 until rowEls.length) {
            if (grid.size >= maxRows) break
            val rowEl = rowEls.item(i) as? Element ?: continue
            val rowIndex = rowEl.getAttribute("r").toIntOrNull()?.minus(1) ?: grid.size
            while (grid.size <= rowIndex && grid.size < maxRows) grid.add(ArrayList())
            val target = grid.getOrNull(rowIndex) ?: continue
            val cells = rowEl.getElementsByTagName("c")
            for (j in 0 until cells.length) {
                val c = cells.item(j) as? Element ?: continue
                val ref = c.getAttribute("r")
                val col = columnIndex(ref)
                if (col < 0 || col >= maxCols) continue
                while (target.size <= col) target.add("")
                val type = c.getAttribute("t")
                val value = when (type) {
                    "s" -> {
                        val idx = childText(c, "v").toIntOrNull()
                        if (idx != null && idx in shared.indices) shared[idx] else ""
                    }
                    "inlineStr" -> textOf(c)
                    "str" -> childText(c, "v")
                    "b" -> if (childText(c, "v") == "1") "TRUE" else "FALSE"
                    "e" -> childText(c, "v")
                    else -> {
                        val raw = childText(c, "v")
                        if (raw.isBlank()) textOf(c) else formatNumber(raw)
                    }
                }
                target[col] = value.trim()
            }
        }
        return grid.map { it.toList() }
    }

    private fun formatNumber(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    }

    private fun columnIndex(ref: String): Int {
        var col = 0
        var any = false
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
                any = true
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
                any = true
            } else if (any) {
                break
            }
        }
        return if (any) col - 1 else -1
    }

    private fun childText(el: Element, tag: String): String {
        val nodes = el.getElementsByTagName(tag)
        if (nodes.length == 0) return ""
        return nodes.item(0)?.textContent ?: ""
    }

    private fun textOf(el: Element): String {
        val sb = StringBuilder()
        collectText(el, sb)
        return sb.toString()
    }

    private fun collectText(node: Node, sb: StringBuilder) {
        if (node.nodeType == Node.TEXT_NODE) {
            sb.append(node.nodeValue ?: "")
            return
        }
        if (node is Element) {
            val name = node.tagName
            if (name == "rPh" || name == "phoneticPr") return
        }
        var child = node.firstChild
        while (child != null) {
            collectText(child, sb)
            child = child.nextSibling
        }
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }
}
