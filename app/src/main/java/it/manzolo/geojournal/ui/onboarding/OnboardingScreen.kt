package it.manzolo.geojournal.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import it.manzolo.geojournal.R

@Composable
fun OnboardingScreen(
    onClose: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Text(
                text = "🗺️",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Card Ospite
            OnboardingCard(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.onboarding_guest_title),
                body = stringResource(R.string.onboarding_guest_body),
                badge = stringResource(R.string.onboarding_guest_badge),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                badgeColor = MaterialTheme.colorScheme.secondary,
                iconTint = MaterialTheme.colorScheme.secondary
            )

            // Card Account
            OnboardingCard(
                icon = Icons.Filled.Cloud,
                title = stringResource(R.string.onboarding_cloud_title),
                body = stringResource(R.string.onboarding_cloud_body),
                badge = stringResource(R.string.onboarding_cloud_badge),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                badgeColor = MaterialTheme.colorScheme.primary,
                iconTint = MaterialTheme.colorScheme.primary
            )

            // Open source note
            Text(
                text = stringResource(R.string.onboarding_open_source_note),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Crashlytics note
            Text(
                text = stringResource(R.string.onboarding_crashlytics_note),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // CTA button
            Button(
                onClick = {
                    viewModel.accept()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.onboarding_cta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    icon: ImageVector,
    title: String,
    body: String,
    badge: String,
    containerColor: Color,
    onContainerColor: Color,
    badgeColor: Color,
    iconTint: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, shape = RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = badgeColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
            // Badge highlight
            Box(
                modifier = Modifier
                    .background(
                        badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            }
        }
    }
}
