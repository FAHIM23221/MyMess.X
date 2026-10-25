package com.messerp.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
fun LogoMark(size: Dp = 64.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.5f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, note: String? = null, valueColor: Color = Color.Unspecified) {
    AppCard(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
        if (note != null) Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusChip(text: String, positive: Boolean = false) {
    val container = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(20.dp), color = container) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

class Tile(val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
fun ActionTile(tile: Tile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(c.surface)
            .border(BorderStroke(1.dp, c.outlineVariant), shape)
            .clickable(onClick = tile.onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            tile.label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = if (tile.enabled) c.onSurface else c.onSurfaceVariant
        )
        if (!tile.enabled) Text("শীঘ্রই আসছে", fontSize = 11.sp, color = c.onSurfaceVariant)
    }
}

@Composable
fun TileGrid(tiles: List<Tile>) {
    tiles.chunked(2).forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(pair[0], Modifier.weight(1f))
            val second = pair.getOrNull(1)
            if (second != null) ActionTile(second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun BottomBar(active: Int, onHome: () -> Unit, onProfile: () -> Unit, onSoon: () -> Unit) {
    val labels = listOf("হোম", "মিল", "বার্তা", "প্রোফাইল")
    val c = MaterialTheme.colorScheme
    Surface(color = c.surface, shadowElevation = 6.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            labels.forEachIndexed { i, label ->
                val action = when (i) {
                    0 -> onHome
                    3 -> onProfile
                    else -> onSoon
                }
                val isActive = i == active
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = action),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (isActive) c.primary else Color.Transparent)
                    )
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 14.dp),
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) c.primary else c.onSurfaceVariant
                    )
                }
            }
        }
    }
}
