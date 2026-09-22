package com.nahrahviing.lecteurnovel.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChapterUpdatesCard(
    isAutoUpdateEnabled: Boolean,
    onToggleAutoUpdate: (Boolean) -> Unit,
    lastCheckTimestamp: Long,
    updateCheckResult: String?,
    isCheckingUpdates: Boolean,
    onCheckForUpdatesNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Mises à jour automatiques",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Vérification quotidienne",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Recherche chaque jour si de nouveaux chapitres sont parus pour vos romans et vous avertit par notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isAutoUpdateEnabled,
                    onCheckedChange = onToggleAutoUpdate
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            val lastCheckStr = if (lastCheckTimestamp > 0) {
                val sdf = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.getDefault())
                sdf.format(Date(lastCheckTimestamp))
            } else {
                "Jamais"
            }

            Text(
                "Dernière vérification : $lastCheckStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            updateCheckResult?.let { result ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCheckForUpdatesNow,
                enabled = !isCheckingUpdates,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCheckingUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vérification en cours...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vérifier les nouveautés maintenant")
                }
            }
        }
    }
}
