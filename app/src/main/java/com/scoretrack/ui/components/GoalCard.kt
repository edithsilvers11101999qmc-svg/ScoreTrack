package com.scoretrack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scoretrack.core.ColorHex
import com.scoretrack.core.formatScore
import com.scoretrack.data.goal.UniversityGoal
import com.scoretrack.domain.goal.barFraction
import com.scoretrack.ui.theme.ThemePresets
import java.io.File

/**
 * 主页大学目标卡：展示目标完成度（不封顶）、剩余分数、进度条。
 * 关闭按钮只隐藏卡片（isVisible=false），不删除任何数据。
 */
@Composable
fun GoalCardView(
    goal: UniversityGoal,
    latestTotal: Double?,
    onClose: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = goal.progress(latestTotal)
    val accent = goal.accentColorHex
        ?.let { ThemePresets.hexToColor(it) }
        ?: MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = goal.cardOpacity.coerceIn(0.15f, 1f))

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Box {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalLogoBadge(path = goal.logoPath, accent = accent)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            goal.universityName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (goal.majorName.isNotBlank()) {
                            Text(
                                goal.majorName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (progress.achieved) {
                        Text(
                            "目标已达成",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        progress.percentText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (progress.hasData) {
                            "最近 ${formatScore(progress.latestTotal ?: 0.0)} / 目标 ${formatScore(goal.targetScore)}"
                        } else {
                            "等待录入首次考试成绩"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                LinearProgressIndicator(
                    progress = { progress.barFraction() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                )

                if (goal.showAuxInfo) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            progress.remainingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress.achieved) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "目标 ${formatScore(goal.targetScore)} 分",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭这张目标卡（不删除数据）",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 大学校徽：位于卡片左侧。有图片时按圆角方形裁切显示，没有图片时回退到学校图标。
 * 图片来自应用私有目录（选择时已压缩到 512px），不直接引用相册原图。
 */
@Composable
fun GoalLogoBadge(
    path: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    val hasLogo = !path.isNullOrBlank() && File(path).isFile
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), shape)
            .border(1.dp, accent.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasLogo) {
            AsyncImage(
                model = File(path!!),
                contentDescription = "校徽",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.School,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** 主题色小圆点，用于预设色展示。 */
@Composable
fun ColorDot(hex: String, selected: Boolean, modifier: Modifier = Modifier) {
    val color = ThemePresets.hexToColor(hex)
    Box(
        modifier
            .size(if (selected) 34.dp else 28.dp)
            .background(color, RoundedCornerShape(50))
            .then(if (selected) Modifier.background(Color.Transparent) else Modifier),
    )
}
