package com.scoretrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scoretrack.core.CountdownPhase
import com.scoretrack.data.countdown.CountdownExam
import java.time.ZonedDateTime

/**
 * 主页「考试倒计时」卡片（固定渲染在大学目标卡之上）。
 *
 * - 没有可展示的考试时显示紧凑空状态 + 添加入口；
 * - 有考试时显示名称、日期、具体时间、剩余天数 / 剩余时分、备注；
 * - 点击整卡进入管理页。
 */
@Composable
fun CountdownSection(
    exams: List<CountdownExam>,
    now: ZonedDateTime,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = com.scoretrack.data.countdown.pickHomeCountdown(exams, now)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("考试倒计时", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpen) {
                Text(if (primary == null) "添加考试" else "管理")
            }
        }
        if (primary == null) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "还没有添加考试",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "添加考试，为重要的日子做好准备。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            CountdownCard(exam = primary, now = now, onOpen = onOpen)
        }
    }
}

@Composable
fun CountdownCard(
    exam: CountdownExam,
    now: ZonedDateTime,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countdown = exam.countdown(now)
    val accent = MaterialTheme.colorScheme.primary
    val today = countdown.isToday

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (today) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Event,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    exam.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (exam.primary) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            "主显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        countdown.statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        when (countdown.phase) {
                            CountdownPhase.UPCOMING -> {
                                Text(
                                    countdown.days.toString(),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("天", style = MaterialTheme.typography.titleMedium, color = accent)
                            }

                            CountdownPhase.TODAY_UPCOMING -> {
                                Text(
                                    countdown.remainingTimeText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                )
                            }

                            CountdownPhase.TODAY_NO_TIME,
                            CountdownPhase.TODAY_ONGOING,
                            -> Text(
                                "今天",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                            )

                            CountdownPhase.FINISHED -> Text(
                                "已结束",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (exam.timeMinutes != null) "${exam.dateText} ${exam.timeOrEmpty}" else exam.dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (exam.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    exam.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
