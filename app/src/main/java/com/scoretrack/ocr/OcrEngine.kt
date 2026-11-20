package com.scoretrack.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google ML Kit 中文 OCR 封装：输出带坐标与置信度的行/词，供表格结构恢复使用。
 */
class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(bitmap: Bitmap, pageIndex: Int, sourceLabel: String): OcrPage {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = suspendCancellableCoroutine<Text> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
        return toPage(text, bitmap.width, bitmap.height, pageIndex, sourceLabel)
    }

    private fun toPage(text: Text, width: Int, height: Int, index: Int, label: String): OcrPage {
        val lines = ArrayList<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val tokens = line.elements.map { element ->
                    TextToken(
                        text = element.text,
                        box = element.boundingBox.toBox(),
                        confidence = element.confidence.takeIf { it > 0f },
                    )
                }
                val lineConfidences = tokens.mapNotNull { it.confidence }
                lines.add(
                    OcrLine(
                        text = line.text,
                        box = line.boundingBox.toBox(),
                        tokens = tokens,
                        confidence = if (lineConfidences.isEmpty()) null else lineConfidences.average().toFloat(),
                    )
                )
            }
        }
        val sorted = lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
        val confidences = sorted.mapNotNull { it.confidence }
        return OcrPage(
            pageIndex = index,
            imageWidth = width,
            imageHeight = height,
            lines = sorted,
            rawText = text.text,
            averageConfidence = if (confidences.isEmpty()) null else confidences.average().toFloat(),
            sourceLabel = label,
        )
    }

    private fun Rect?.toBox(): Box = if (this == null) Box.EMPTY else Box(
        left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
    )

    fun close() {
        runCatching { recognizer.close() }
    }
}
