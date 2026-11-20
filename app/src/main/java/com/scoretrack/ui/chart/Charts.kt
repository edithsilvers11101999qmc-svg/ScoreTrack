package com.scoretrack.ui.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

data class ChartPoint(
    val label: String,
    val value: Double,
    val secondary: String = "",
)

/**
 * 成绩折线图（MPAndroidChart）。支持缩放、拖动、点击节点查看详情，
 * 横轴为考试名称，纵轴按满分或得分率自动定标。
 */
@Composable
fun ScoreLineChart(
    points: List<ChartPoint>,
    maxValue: Double,
    modifier: Modifier = Modifier,
    showValues: Boolean = true,
    valueSuffix: String = "",
    selectedIndex: Int = -1,
    onPointSelected: (Int) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.primary.toArgb()
    val textColor = colors.onSurfaceVariant.toArgb()
    val fillArgb = colors.primary.copy(alpha = 0.16f).toArgb()
    val surfaceColor = colors.surface.toArgb()

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    setBackgroundColor(surfaceColor)
                    axisRight.isEnabled = false
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.granularity = 1f
                    xAxis.textColor = textColor
                    xAxis.textSize = 10f
                    xAxis.labelRotationAngle = -20f
                    axisLeft.textColor = textColor
                    axisLeft.textSize = 10f
                    axisLeft.setDrawGridLines(true)
                    axisLeft.gridColor = colors.outline.copy(alpha = 0.25f).toArgb()
                    axisLeft.gridLineWidth = 0.6f
                    setNoDataText("暂无数据")
                    setNoDataTextColor(textColor)
                    extraBottomOffset = 6f
                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            val index = e?.x?.toInt() ?: return
                            onPointSelected(index)
                        }

                        override fun onNothingSelected() = Unit
                    })
                }
            },
            update = { chart ->
                val entries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.value.toFloat()) }
                if (entries.isEmpty()) {
                    chart.clear()
                    chart.invalidate()
                    return@AndroidView
                }
                val dataSet = LineDataSet(entries, "成绩").apply {
                    color = lineColor
                    setCircleColor(lineColor)
                    lineWidth = 2.4f
                    circleRadius = 4f
                    setDrawCircleHole(false)
                    setDrawValues(showValues)
                    valueTextSize = 9f
                    valueTextColor = lineColor
                    setDrawFilled(true)
                    mode = LineDataSet.Mode.LINEAR
                    highLightColor = colors.tertiary.toArgb()
                    highlightLineWidth = 1f
                }
                dataSet.fillAlpha = 60
                dataSet.fillColor = fillArgb
                dataSet.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = trim(value.toDouble())
                }
                chart.data = LineData(dataSet)
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(points.map { it.label })
                chart.xAxis.axisMinimum = -0.4f
                chart.xAxis.axisMaximum = (points.size - 0.6f).coerceAtLeast(0f)
                chart.axisLeft.axisMinimum = 0f
                chart.axisLeft.axisMaximum = (maxValue * 1.08f).toFloat()
                chart.setVisibleXRangeMaximum(8f)
                chart.data.notifyDataChanged()
                chart.notifyDataSetChanged()
                if (selectedIndex in points.indices) {
                    chart.highlightValue(selectedIndex.toFloat(), 0, false)
                } else {
                    chart.highlightValue(null)
                }
                chart.invalidate()
            },
        )
        if (selectedIndex in points.indices) {
            val point = points[selectedIndex]
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 3.dp,
                color = colors.surfaceVariant,
            ) {
                Text(
                    text = "${point.label} · ${trim(point.value)}$valueSuffix" +
                        if (point.secondary.isNotBlank()) " · ${point.secondary}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 首页 / 卡片内的小型趋势曲线，无坐标轴。 */
@Composable
fun MiniSparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val lineColor = color.toArgb()
    Box(modifier = modifier.height(64.dp).fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(false)
                    axisRight.isEnabled = false
                    axisLeft.isEnabled = false
                    xAxis.isEnabled = false
                    setDrawGridBackground(false)
                    setNoDataText("")
                }
            },
            update = { chart ->
                if (values.size < 2) {
                    chart.clear()
                    chart.invalidate()
                    return@AndroidView
                }
                val entries = values.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
                val set = LineDataSet(entries, "").apply {
                    this.color = lineColor
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    setDrawFilled(true)
                    fillAlpha = 40
                    this.fillColor = lineColor
                }
                chart.data = LineData(set)
                chart.axisLeft.axisMinimum = (values.min() * 0.95f).toFloat()
                chart.axisLeft.axisMaximum = (values.max() * 1.05f).toFloat()
                chart.xAxis.axisMinimum = 0f
                val max = (values.size - 1).coerceAtLeast(1)
                chart.xAxis.axisMaximum = max.toFloat()
                chart.setVisibleXRangeMaximum(max.toFloat())
                chart.invalidate()
            },
        )
    }
}

private fun trim(value: Double): String =
    if (value == Math.floor(value)) value.toInt().toString() else String.format("%.1f", value)
