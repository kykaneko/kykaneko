package jp.kykaneko.sleep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.kykaneko.sleep.data.DailyDebtData
import kotlin.math.max

@Composable
fun SleepDebtChart(
    data: List<DailyDebtData>,
    modifier: Modifier = Modifier,
) {
    val barColor = Color(0xFFC62828)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxDebt = max(data.maxOfOrNull { it.debtMinutes } ?: 0, 60)

    Column(modifier = modifier) {
        Text(
            text = "過去7日間の睡眠負債",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "最大 ${maxDebt} 分",
            style = MaterialTheme.typography.labelSmall,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(top = 12.dp),
        ) {
            val chartBottom = size.height
            val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(198, 40, 40)
                textSize = 11.sp.toPx()
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val labelGap = 4.dp.toPx()
            val chartTop = 20.dp.toPx()
            val chartHeight = chartBottom - chartTop
            val slotWidth = size.width / data.size.coerceAtLeast(1)
            val barWidth = slotWidth * 0.48f

            for (line in 0..4) {
                val y = chartTop + chartHeight * line / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            data.forEachIndexed { index, day ->
                if (day.debtMinutes > 0) {
                    val barHeight = chartHeight * day.debtMinutes / maxDebt
                    val left = slotWidth * index + (slotWidth - barWidth) / 2f
                    drawRect(
                        color = barColor,
                        topLeft = Offset(left, chartBottom - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    )
                    drawIntoCanvas { canvas ->
                        val labelY = (chartBottom - barHeight - labelGap)
                            .coerceAtLeast(labelPaint.textSize)
                        canvas.nativeCanvas.drawText(
                            day.debtMinutes.toString(),
                            left + barWidth / 2f,
                            labelY,
                            labelPaint,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEach { day ->
                Text(
                    text = day.date,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
