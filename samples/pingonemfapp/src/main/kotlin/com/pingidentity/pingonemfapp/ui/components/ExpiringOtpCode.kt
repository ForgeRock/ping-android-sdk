package com.pingidentity.pingonemfapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfapp.ui.theme.PingLightBlue
import com.pingidentity.pingonemfapp.ui.theme.PingRed

@Composable
fun ExpiringOtpCode(
    code: String,
    totalDurationMs: Long,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    // Progress goes from 1.0 -> 0.0 as time runs out
    val progress = (remainingSeconds.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    val interpolatedColor = lerp(
        start = PingLightBlue,
        stop = PingRed,
        fraction = 1f - progress
    )

    // Interpolate between Green -> Yellow -> Red
    val displayColor by animateColorAsState(
        targetValue = interpolatedColor,
        label = "otpColor"
    )

    // Slight fade-out near end (optional)
    val alpha by animateFloatAsState(
        targetValue = if (progress < 0.2f) 0.8f else 1f,
        label = "otpAlpha"
    )

    Row(
        modifier = modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        code.forEach { digit ->
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, displayColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = digit.toString(),
                    color = displayColor.copy(alpha = alpha),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

}