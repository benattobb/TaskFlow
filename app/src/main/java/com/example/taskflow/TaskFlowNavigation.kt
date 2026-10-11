package com.example.taskflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TaskFlowNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Home", "Tasks", "Focus")
    val icons = listOf(R.drawable.ic_nav_home, R.drawable.ic_nav_tasks, R.drawable.ic_nav_focus)
    Box(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(30.dp), color = Color.White, shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAE5D9)),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Row(Modifier.padding(7.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index, label ->
                    val active = selected == index
                    val transition = updateTransition(targetState = active, label = "${label}TabSelection")
                    val iconScale = transition.animateFloat(
                        transitionSpec = { androidx.compose.animation.core.spring(dampingRatio = 0.48f, stiffness = 520f) },
                        label = "${label}IconScale"
                    ) { selectedNow -> if (selectedNow) 1.12f else 1f }.value
                    val iconTilt = transition.animateFloat(
                        transitionSpec = { androidx.compose.animation.core.spring(dampingRatio = 0.52f, stiffness = 560f) },
                        label = "${label}IconTilt"
                    ) { selectedNow -> if (selectedNow) -4f else 0f }.value
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                            .background(if (active) Color(0xFFEAF0E7) else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(painterResource(icons[index]), contentDescription = null,
                            tint = if (active) Color(0xFF476544) else Color(0xFF77766F),
                            modifier = Modifier.size(22.dp).graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                rotationZ = iconTilt
                            })
                        Text(label, fontSize = 11.sp, color = if (active) Color(0xFF476544) else Color(0xFF77766F))
                    }
                }
            }
        }
    }
}
