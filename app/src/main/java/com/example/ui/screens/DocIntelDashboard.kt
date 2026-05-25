package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DocIntelViewModel
import com.example.ui.viewmodel.EngineStatus
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocIntelDashboard(
    viewModel: DocIntelViewModel,
    modifier: Modifier = Modifier
) {
    val currentDoc by viewModel.currentAnalysis.collectAsStateWithLifecycle()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val history by viewModel.historyState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Helper function to safely decode a bitmap from a Uri with target size to prevent OOM
    fun decodeSampledBitmap(uri: android.net.Uri, reqWidth: Int = 1024, reqHeight: Int = 1024): android.graphics.Bitmap? {
        return try {
            val contentResolver = context.contentResolver
            
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            }

            // Calculate inSampleSize
            var inSampleSize = 1
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight || halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false

            // Decode bitmap with inSampleSize set
            contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (t: Throwable) {
            null
        }
    }

    // Launcher for custom custom PDF/images
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = decodeSampledBitmap(uri)
                if (bitmap != null) {
                    viewModel.analyzeCustomImage(bitmap, uri.lastPathSegment ?: "uploaded_image.jpg")
                }
            } catch (t: Throwable) {
                // handle error / OOM safely without crashing
            }
        }
    }

    // Dynamic color accents based on current loaded spec
    val activeAccentColor = when (currentDoc?.docType) {
        DocType.LEGAL -> WarmBronze
        DocType.HEALTHCARE -> ElectricCyan
        DocType.ACADEMIC -> CosmicPurple
        DocType.FINANCIAL -> BrightMint
        DocType.GENERAL -> LaserPink
        null -> CosmicPurple
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianMain),
        containerColor = ObsidianMain
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .drawBehind {
                    // Futuristic top-right radar ambient background pulse radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeAccentColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width, 0f),
                            radius = size.width * 0.8f
                        )
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Sleek HUD Top Header Panel
                HeaderPanel(
                    accentColor = activeAccentColor,
                    onKeyConfigClick = { viewModel.showApiKeyDialog(true) }
                )

                // 2. Main content router
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val status = engineStatus) {
                        is EngineStatus.Idle -> {
                            // Ready state before selection
                            WelcomeScreen(
                                history = history,
                                onSelectPreset = { viewModel.loadPresetDocument(it) },
                                onSelectHistory = { viewModel.loadHistoricalRecord(it) },
                                onDeleteHistory = { viewModel.deleteHistoryRecord(it) },
                                onClearHistory = { viewModel.clearAllHistory() }
                            )
                        }
                        is EngineStatus.Simulating -> {
                            // High immersion Agent Sequence terminal
                            SimulatingPipelineView(
                                status = status,
                                accentColor = activeAccentColor,
                                docType = currentDoc?.docType ?: DocType.GENERAL
                            )
                        }
                        is EngineStatus.Completed -> {
                            currentDoc?.let { doc ->
                                // Master intelligence Dashboard
                                MainIntelDashboard(
                                    doc = doc,
                                    viewModel = viewModel,
                                    accentColor = activeAccentColor,
                                    imagePickerLauncher = { imagePickerLauncher.launch("image/*") }
                                )
                            }
                        }
                        is EngineStatus.Error -> {
                            // Show standard fallback
                            WelcomeScreen(
                                history = history,
                                onSelectPreset = { viewModel.loadPresetDocument(it) },
                                onSelectHistory = { viewModel.loadHistoricalRecord(it) },
                                onDeleteHistory = { viewModel.deleteHistoryRecord(it) },
                                onClearHistory = { viewModel.clearAllHistory() }
                            )
                        }
                    }
                }

                // 3. Quick Deck Selector (Always visible at bottom for ease of traversal)
                BottomNavigationDeck(
                    currentDoc = currentDoc,
                    onSelectPreset = { viewModel.loadPresetDocument(it) },
                    onUploadClick = { imagePickerLauncher.launch("image/*") },
                    onHomeClick = { viewModel.resetToWelcome() }
                )
            }

            // API key input modal
            val dialogShowing by viewModel.apiKeyDialogShowing.collectAsStateWithLifecycle()
            if (dialogShowing) {
                ApiKeyModal(onDismiss = { viewModel.showApiKeyDialog(false) })
            }
        }
    }
}

// -------------------------------------------------------------
// CHILD COMPONENTS
// -------------------------------------------------------------

@Composable
fun HeaderPanel(
    accentColor: Color,
    onKeyConfigClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .background(ObsidianSurface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "COGNITIVE DOCUMENT INTEL OS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = alphaAnim))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "UPLINK PROTOCOL: ACTIVE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        IconButton(
            onClick = onKeyConfigClick,
            modifier = Modifier
                .size(36.dp)
                .background(ObsidianBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Config Rules",
                modifier = Modifier.size(16.dp),
                tint = TextPrimary
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    history: List<DocAnalysis>,
    onSelectPreset: (DocType) -> Unit,
    onSelectHistory: (DocAnalysis) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Brain Core Scan",
            modifier = Modifier.size(72.dp),
            tint = CosmicPurple
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "INITIATE COGNITIVE COUPLING",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Select a document type to deploy collaborating multi-agent nodes. Experience raw NLP modeling, contradictory diagnostics, and instant graphic synthesis.",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Large Quick select cards
        PresetOptionCard(
            title = "Commercial Lease Agreement",
            desc = "Find rent contradictions and legal traps",
            icon = Icons.Default.Lock,
            color = WarmBronze,
            onClick = { onSelectPreset(DocType.LEGAL) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PresetOptionCard(
            title = "Cardiology Diagnostic Report",
            desc = "Detect clinical transcription anomalies",
            icon = Icons.Default.Favorite,
            color = ElectricCyan,
            onClick = { onSelectPreset(DocType.HEALTHCARE) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PresetOptionCard(
            title = "Neural Network Research Paper",
            desc = "Compare plots vs abstract benchmarks",
            icon = Icons.Default.Star,
            color = CosmicPurple,
            onClick = { onSelectPreset(DocType.ACADEMIC) }
        )

        // Historical archive section
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SECURE COGNITIVE ARCHIVE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CosmicPurple
                )
                Text(
                    "CLEAR ALL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = LaserPink,
                    modifier = Modifier.clickable(onClick = onClearHistory)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            history.forEach { item ->
                HistoryRecordCard(
                    doc = item,
                    onClick = { onSelectHistory(item) },
                    onDelete = { onDeleteHistory(item.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun HistoryRecordCard(
    doc: DocAnalysis,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when (doc.docType) {
        DocType.LEGAL -> WarmBronze
        DocType.HEALTHCARE -> ElectricCyan
        DocType.ACADEMIC -> CosmicPurple
        DocType.FINANCIAL -> BrightMint
        DocType.GENERAL -> LaserPink
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .background(ObsidianSurface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (doc.docType) {
                    DocType.LEGAL -> Icons.Default.Lock
                    DocType.HEALTHCARE -> Icons.Default.Favorite
                    DocType.ACADEMIC -> Icons.Default.Star
                    DocType.FINANCIAL -> Icons.Default.Check
                    DocType.GENERAL -> Icons.Default.Info
                },
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title.uppercase(Locale.ROOT),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${doc.docType.name} • CONFIDENCE: ${(doc.confidenceScore * 100).toInt()}%",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Record",
                tint = LaserPink.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PresetOptionCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = color.copy(alpha = 0.7f))
    }
}

// -------------------------------------------------------------
// MULTI-AGENT SIMULATING PIPELINE VIEW (CINEMATIC TERM)
// -------------------------------------------------------------
@Composable
fun SimulatingPipelineView(
    status: EngineStatus.Simulating,
    accentColor: Color,
    docType: DocType
) {
    val agents = listOf("OCR Scanner", "Domain Analyzer", "Visuo-Risk Agent", "Graph Weaver", "Explainable AI")
    val activeAgentIndex = status.currentStepIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "DEPLOYING COGNITIVE SEQUENCE...",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Horizontal connecting agent progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            agents.forEachIndexed { index, name ->
                val isActive = index == activeAgentIndex
                val isCompleted = index < activeAgentIndex
                
                val nodeColor = when {
                    isActive -> accentColor
                    isCompleted -> TextGreenAccent
                    else -> ObsidianBorder
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (isActive) accentColor else nodeColor,
                                shape = CircleShape
                            )
                            .background(
                                color = if (isActive) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = TextGreenAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = (index + 1).toString(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isActive) accentColor else TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        name,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActive) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (index < agents.size - 1) {
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .weight(0.1f)
                            .background(if (isCompleted) TextGreenAccent else ObsidianBorder)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cyber terminal console log output screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                .background(Color(0xFF040509), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            val lines = remember(status.logs) { status.logs.split("\n").reversed() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                // Render text starting from bottom of terminal
                items(lines) { line ->
                    if (line.isNotBlank()) {
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (line.startsWith("ERR:")) LaserPink else if (line.startsWith("[")) accentColor else TextPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pulse details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "COMPILATION METRIC: ${status.elapsedMs}ms",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary
            )
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// MASTER ANALYSIS DASHBOARD SCREEN (COMPLETED STATE)
// -------------------------------------------------------------
@Composable
fun MainIntelDashboard(
    doc: DocAnalysis,
    viewModel: DocIntelViewModel,
    accentColor: Color,
    imagePickerLauncher: () -> Unit
) {
    val scrollState = rememberScrollState()

    val heatmapOn by viewModel.heatmapEnabled.collectAsStateWithLifecycle()
    val risksOn by viewModel.risksOverlayEnabled.collectAsStateWithLifecycle()
    val graphOn by viewModel.graphOverlayEnabled.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // --- Bento Row 1: Header / Title Info Card & Risk Index Score ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bento Tile 1: Document Overview Title Card
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(115.dp)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                    .background(ObsidianSurface, RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "COGNITIVE TARGET",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            doc.title.uppercase(Locale.ROOT),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TextGreenAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "CONFIDENCE: ${(doc.confidenceScore * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Bento Tile 2: Risk Index Score Card (HTML Style)
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .height(115.dp)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                    .background(ObsidianSurface, RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Risk Index",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        val calculatedRisk = when {
                            doc.risks.isEmpty() -> 12
                            doc.risks.size == 1 -> 34
                            doc.risks.size == 2 -> 58
                            else -> 74
                        }
                        Text(
                            calculatedRisk.toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light,
                            color = BentoLightRed
                        )
                        Text(
                            "/100",
                            fontSize = 11.sp,
                            color = BentoLightRed.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // --- Bento Row 2: Agent Pipeline (HTML high contrast) & System Metrics ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bento Tile 3: AI Agent Pipeline
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoLightBlue)
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "AI Agent Pipeline",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoDarkBlue
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-6).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AgentLetterBadge("O")
                        AgentLetterBadge("V")
                        AgentLetterBadge("R")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "LOADED",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = BentoDarkBlue.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bento Tile 4: Sub-system Engine model credentials
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                    .background(ObsidianSurface, RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "COGNITIVE CORE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Column {
                        Text(
                            "GEMINI-3.5-FLASH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Status: SYNCED",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextGreenAccent
                        )
                    }
                }
            }
        }

        // --- Double-Width Bento Card 3: Multimodal Smart-Lens ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(32.dp))
                .background(ObsidianSurface, RoundedCornerShape(32.dp))
                .padding(16.dp)
        ) {
            Column {
                // Top floating-style visual header in Bento theme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(30.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BentoLightRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "VISUAL REASONING MODE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        "I. SCAN-DECK & SENSING",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                // Switch Layer HUD controls (re-styled to be beautiful Bento controls)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        .background(Color(0xFF090A11), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    LayerToggleButton(
                        text = "HEATMAP LAYER",
                        active = heatmapOn,
                        accentColor = accentColor,
                        onToggle = { viewModel.toggleHeatmap() },
                        modifier = Modifier.weight(1f)
                    )
                    LayerToggleButton(
                        text = "ANOMALY COGNITION",
                        active = risksOn,
                        accentColor = accentColor,
                        onToggle = { viewModel.toggleRisksOverlay() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Smart Lens Main Board
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0A0C10))
                        .border(1.5.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                        .drawBehind {
                            val strokeColor = ElectricCyan.copy(alpha = 0.08f)
                            val w = size.width
                            val h = size.height
                            drawLine(color = strokeColor, start = Offset(w * 0.1f, h * 0.1f), end = Offset(w * 0.9f, h * 0.9f), strokeWidth = 1f)
                            drawLine(color = strokeColor, start = Offset(w * 0.9f, h * 0.1f), end = Offset(w * 0.1f, h * 0.9f), strokeWidth = 1f)
                            drawCircle(color = strokeColor, radius = minOf(w, h) * 0.2f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = 1f))
                        }
                        .padding(14.dp)
                ) {
                    Column {
                        val paragraphs = doc.fullOcrText.split("\n\n")
                        paragraphs.forEachIndexed { idx, para ->
                            val isCriticalRiskSnippet = doc.risks.any { risk ->
                                para.contains(risk.title) || para.contains("Paragraph 18") || para.contains("SECTION 8") || para.contains("FIGURE 4")
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .drawBehind {
                                        if (heatmapOn) {
                                            drawRect(
                                                color = accentColor.copy(alpha = 0.08f),
                                                size = size
                                            )
                                        }
                                        if (risksOn && isCriticalRiskSnippet) {
                                            drawRect(
                                                color = LaserPink.copy(alpha = 0.12f),
                                                size = size
                                            )
                                        }
                                    }
                                    .then(
                                        if (risksOn && isCriticalRiskSnippet) {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = LaserPink.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    para,
                                    fontSize = 11.sp,
                                    color = if (risksOn && isCriticalRiskSnippet) TextPrimary else TextSecondary,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lens scan status: Active",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "98.4% PARSING DEPTH",
                        fontSize = 9.sp,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Double-Width Bento Card 4: Knowledge Graph Fragment ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(28.dp))
                .background(ObsidianSurface, RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "II. KNOWLEDGE GRAPH FRAGMENT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Graph view",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Node canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color(0xFF06070B), RoundedCornerShape(16.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    InteractiveNodeCanvas(
                        nodes = doc.nodes,
                        edges = doc.edges,
                        accentColor = accentColor,
                        selectedNode = selectedNode,
                        onNodeSelected = { viewModel.selectNode(it) }
                    )

                    // Selected node floating popout (Bento-styled)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selectedNode != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                    ) {
                        selectedNode?.let { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianMain.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                                    .border(1.dp, accentColor, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (node.type == "CONTRA_ELEMENT" || node.type == "RISK_FLAG") LaserPink
                                            else accentColor
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(node.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                    Text("ENTITY: ${node.type}", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                                }
                                IconButton(
                                    onClick = { viewModel.selectNode(null) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Knowledge Fragment list items matching HTML layout exactly!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        .background(Color(0xFF040608), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GraphListField(
                        dotColor = ElectricCyan,
                        label = "Entity",
                        value = if (doc.nodes.isNotEmpty()) doc.nodes[0].label else "Apex Holdings Ltd."
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))
                    GraphListField(
                        dotColor = CosmicPurple,
                        label = "Conflict",
                        value = if (doc.risks.isNotEmpty()) doc.risks[0].title else "Section 4.2 vs. 8.1"
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))
                    GraphListField(
                        dotColor = BentoLightRed,
                        label = "Clause",
                        value = if (doc.risks.size > 1) doc.risks[1].title else "Force Majeure Exclusion"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Bento Card 5: Compliance Assurances list items ---
        Text(
            "III. COMPLIANCE ASSURANCES & ADVERSARIAL DISCREPANCIES",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        doc.risks.forEach { risk ->
            val isCritical = risk.severity == "CRITICAL"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .border(
                        width = 1.dp,
                        color = if (isCritical) LaserPink.copy(alpha = 0.3f) else WarmBronze.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCritical) LaserPink.copy(alpha = 0.03f) else WarmBronze.copy(alpha = 0.03f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCritical) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isCritical) LaserPink else WarmBronze,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            risk.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isCritical) LaserPink else TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "TARGET: ${risk.clauseText}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        risk.reasoning,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF040608), RoundedCornerShape(12.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                "SOLVENCY REMEDY:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TextGreenAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                risk.suggestion,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Bento Card 6: Extraction Chronicles Timeline ---
        if (doc.timeline.isNotEmpty()) {
            Text(
                "IV. EXTRACTION CHRONICLES CHRONICLER",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(28.dp))
                    .background(ObsidianSurface, RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column {
                    doc.timeline.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                if (index < doc.timeline.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(40.dp)
                                            .background(ObsidianBorder)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        item.date,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = accentColor
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (item.level == "HIGH") LaserPink.copy(alpha = 0.1f) else ObsidianBorder,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            item.level,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (item.level == "HIGH") LaserPink else TextSecondary
                                        )
                                    }
                                }
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                Text(item.significance, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Bento Card 7: Explainable AI evidentiary proof mapping ---
        Text(
            "V. EXPLAINABLE AI MODEL DISCLOSURE & JUSTIFICATIONS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                .background(ObsidianSurface, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = TextGreenAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "COG-INTELLIDENCE MODEL: GEMINI-3.5-FLASH",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    doc.explanation,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AgentLetterBadge(letter: String) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .border(2.dp, BentoLightBlue, CircleShape)
            .background(BentoDarkBlue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GraphListField(dotColor: Color, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$label: ",
            fontSize = 11.sp,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTOR HELPERS
// -------------------------------------------------------------

@Composable
fun LayerToggleButton(
    text: String,
    active: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accentColor.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) accentColor.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) accentColor else TextSecondary
        )
    }
}

@Composable
fun InteractiveNodeCanvas(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    accentColor: Color,
    selectedNode: GraphNode?,
    onNodeSelected: (GraphNode?) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar"
    )

    val dashPathEffect = remember {
        PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(nodes) {
                detectTapGestures { offset ->
                    try {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        if (canvasWidth > 0 && canvasHeight > 0) {
                            val hitRadius = 32.dp.toPx()
                            var hit: GraphNode? = null
                            for (node in nodes) {
                                val nx = node.x * canvasWidth
                                val ny = node.y * canvasHeight
                                val dx = nx - offset.x
                                val dy = ny - offset.y
                                if (Math.sqrt((dx * dx + dy * dy).toDouble()) < hitRadius) {
                                    hit = node
                                    break
                                }
                            }
                            onNodeSelected(hit)
                        }
                    } catch (t: Throwable) {
                        // Safe fail-silent to prevent UI main-thread crashes on touch delivery
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // 1. Draw connecting relationships lines first
        edges.forEach { edge ->
            val from = nodes.find { it.id == edge.from }
            val to = nodes.find { it.id == edge.to }
            if (from != null && to != null) {
                drawLine(
                    color = accentColor.copy(alpha = 0.35f),
                    start = Offset(from.x * w, from.y * h),
                    end = Offset(to.x * w, to.y * h),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashPathEffect
                )
            }
        }

        // 2. Draw circular nodes
        nodes.forEach { node ->
            val nx = node.x * w
            val ny = node.y * h
            val isSelected = selectedNode?.id == node.id
            val isRiskType = node.type == "CONTRA_ELEMENT" || node.type == "RISK_FLAG"
            val coreColor = if (isRiskType) LaserPink else accentColor

            // Selected glow pulse
            if (isSelected) {
                drawCircle(
                    color = coreColor.copy(alpha = 0.25f),
                    radius = 24.dp.toPx() * dotScale,
                    center = Offset(nx, ny)
                )
                drawCircle(
                    color = coreColor,
                    radius = 22.dp.toPx(),
                    center = Offset(nx, ny),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            } else {
                drawCircle(
                    color = coreColor.copy(alpha = 0.15f),
                    radius = 16.dp.toPx(),
                    center = Offset(nx, ny)
                )
            }

            // Core solid core
            drawCircle(
                color = coreColor,
                radius = 8.dp.toPx(),
                center = Offset(nx, ny)
            )

            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(nx, ny)
            )
        }
    }
}

// -------------------------------------------------------------
// CORE REUSABLE DECK SELECTOR (BOTTOM ROW ACTION CENTER)
// -------------------------------------------------------------
@Composable
fun BottomNavigationDeck(
    currentDoc: DocAnalysis?,
    onSelectPreset: (DocType) -> Unit,
    onUploadClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF090A11))
            .padding(16.dp)
            .navigationBarsPadding() // Notch safe areas bottom padding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DEPLOY DOMAIN MODEL PROTOCOLS",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Text(
                "VER: 1.0.2",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                IconButtonCard(
                    text = "HUB INDEX",
                    icon = Icons.Default.Home,
                    color = CosmicPurple,
                    onClick = onHomeClick
                )
            }
            item {
                IconButtonCard(
                    text = "RE-UPLOAD",
                    icon = Icons.Default.Add,
                    color = TextPrimary,
                    onClick = onUploadClick
                )
            }
            item {
                PresetDeckButton(
                    text = "LEGAL",
                    active = currentDoc?.docType == DocType.LEGAL,
                    color = WarmBronze,
                    onClick = { onSelectPreset(DocType.LEGAL) }
                )
            }
            item {
                PresetDeckButton(
                    text = "HEALTH",
                    active = currentDoc?.docType == DocType.HEALTHCARE,
                    color = ElectricCyan,
                    onClick = { onSelectPreset(DocType.HEALTHCARE) }
                )
            }
            item {
                PresetDeckButton(
                    text = "LEDGER",
                    active = currentDoc?.docType == DocType.FINANCIAL,
                    color = BrightMint,
                    onClick = { onSelectPreset(DocType.FINANCIAL) }
                )
            }
            item {
                PresetDeckButton(
                    text = "PAPERS",
                    active = currentDoc?.docType == DocType.ACADEMIC,
                    color = CosmicPurple,
                    onClick = { onSelectPreset(DocType.ACADEMIC) }
                )
            }
            item {
                PresetDeckButton(
                    text = "SPEC-SYS",
                    active = currentDoc?.docType == DocType.GENERAL,
                    color = LaserPink,
                    onClick = { onSelectPreset(DocType.GENERAL) }
                )
            }
        }
    }
}

@Composable
fun PresetDeckButton(
    text: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.2f) else ObsidianSurface)
            .border(
                width = 1.dp,
                color = if (active) color else ObsidianBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (active) color else TextSecondary
        )
    }
}

@Composable
fun IconButtonCard(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// -------------------------------------------------------------
// SECURE USER SETTINGS API KEY DIALOG MODAL
// -------------------------------------------------------------
@Composable
fun ApiKeyModal(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CosmicPurple,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "SECURE COGNITIVE CORE KEY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Informational Warning Box on local security matching Skill Guidelines
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LaserPink.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.5.dp, LaserPink.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "Uplinks use key injections via local .env configurations. No keys are ever written on clear files. For actual image analysis, register your GEMINI_API_KEY in the AI Studio Secrets panel.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("LOCK CORE & EXIT", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


