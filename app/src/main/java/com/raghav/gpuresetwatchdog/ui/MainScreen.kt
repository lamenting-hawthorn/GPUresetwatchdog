package com.raghav.gpuresetwatchdog.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raghav.gpuresetwatchdog.BuildConfig
import com.raghav.gpuresetwatchdog.GPUStressView
import com.raghav.gpuresetwatchdog.R
import com.raghav.gpuresetwatchdog.ui.theme.GPUResetWatchdogTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun MainScreen(
    isProcessing: Boolean,
    statusText: String,
    progress: Float,
    onStartReset: () -> Unit,
    onGpuStressViewCreated: (GPUStressView) -> Unit
) {
    GPUResetWatchdogTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isProcessing) {
                LoadingState(progress, onGpuStressViewCreated)
            } else {
                InitialState(statusText, onStartReset)
            }
            BannerAd(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun InitialState(
    statusText: String,
    onStartReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_gpu_reset),
            contentDescription = stringResource(id = R.string.icon_description),
            modifier = Modifier.size(128.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(id = R.string.app_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.app_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStartReset,
            modifier = Modifier
                .width(200.dp)
                .height(60.dp)
                .semantics {
                    contentDescription = "Tap to start GPU reset sequence"
                }
        ) {
            Text(
                text = stringResource(id = R.string.reset_button),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(id = R.string.duration_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun LoadingState(progress: Float, onGpuStressViewCreated: (GPUStressView) -> Unit) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "Progress Animation")

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                GPUStressView(context).apply {
                    onGpuStressViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(id = R.string.status_processing),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.width(280.dp)
            )
        }
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                // Use AdMob test ad unit ID in debug builds to avoid policy violations
                adUnitId = if (BuildConfig.DEBUG) {
                    "ca-app-pub-3940256099942544/6300978111"
                } else {
                    context.getString(R.string.banner_ad_unit_id)
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { adView ->
            try {
                adView.destroy()
            } catch (e: Exception) {
                // AdView.destroy() can occasionally throw; safe to ignore.
            }
        }
    )
}
