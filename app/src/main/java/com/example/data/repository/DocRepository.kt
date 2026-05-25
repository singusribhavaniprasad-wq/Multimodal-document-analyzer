package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import com.example.data.db.AppDatabase
import com.example.data.db.DocAnalysisDao
import com.example.data.db.DocAnalysisEntity
import com.example.data.model.*
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.InlineData
import com.example.data.network.Part
import com.example.data.network.ResponseSchema
import com.example.data.network.RetrofitClient
import com.example.data.network.SchemaProperty
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random

class DocRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.docAnalysisDao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Query historical scans mapped to domain
    val allAnalyses: Flow<List<DocAnalysis>> = dao.getAllAnalyses().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getAnalysisById(id: Long): DocAnalysis? = withContext(Dispatchers.IO) {
        dao.getAnalysisById(id)?.toDomain()
    }

    suspend fun deleteAnalysis(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteAnalysisById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    // Direct Image Base64 Converter
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // Call actual Gemini API to perform Multi-Agent reasoning & OCR on uploaded image
    suspend fun analyzeUploadedImage(bitmap: Bitmap, title: String): DocAnalysis = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API Key is not set in AI Studio Secrets.")
        }

        // 1. Convert bitmap to Base64
        val base64Image = bitmap.toBase64()

        // 2. Define Schema for JSON structured output
        val graphNodeSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "id" to SchemaProperty("STRING", "Unique ID for the node, e.g. entity_name"),
                "label" to SchemaProperty("STRING", "Display label, e.g. John Doe, $5000/mo"),
                "type" to SchemaProperty(
                    "STRING", 
                    "Shorter capitalized category: PERSON, ORGANIZATION, DATE, METRIC, CONTRA_ELEMENT, RISK_FLAG"
                ),
                "x" to SchemaProperty("NUMBER", "Visual canvas grid position X between 0.1 and 0.9"),
                "y" to SchemaProperty("NUMBER", "Visual canvas grid position Y between 0.1 and 0.9")
            ),
            required = listOf("id", "label", "type", "x", "y")
        )

        val graphEdgeSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "from" to SchemaProperty("STRING", "Source node ID"),
                "to" to SchemaProperty("STRING", "Target node ID"),
                "relation" to SchemaProperty("STRING", "Relationship label, e.g. SIGNED, CONTRADICTS, DECREASED")
            ),
            required = listOf("from", "to", "relation")
        )

        val riskSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "severity" to SchemaProperty("STRING", "Severity of issue: CRITICAL, WARNING, INFO"),
                "title" to SchemaProperty("STRING", "Risk Title"),
                "clauseText" to SchemaProperty("STRING", "Offending text snippet or parameter name from document"),
                "reasoning" to SchemaProperty("STRING", "Cognitive reasoning of why this is a risk or contradiction"),
                "suggestion" to SchemaProperty("STRING", "Tactical recommendation to fix or review")
            )
        )

        val timelineSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "date" to SchemaProperty("STRING", "Extracted date or time segment, e.g., June 2026"),
                "title" to SchemaProperty("STRING", "Milestone / Event Name"),
                "significance" to SchemaProperty("STRING", "Explanation of its chronological impact"),
                "level" to SchemaProperty("STRING", "Level: HIGH, MEDIUM, LOW")
            )
        )

        val agentSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "agentId" to SchemaProperty("STRING", "Agent name: OCRAgent, ContextAgent, RiskAgent, GraphAgent"),
                "agentName" to SchemaProperty("STRING", "Complete display name"),
                "status" to SchemaProperty("STRING", "Always COMPLETED"),
                "durationMs" to SchemaProperty("INTEGER", "Fictional elapsed duration"),
                "logs" to SchemaProperty("STRING", "Step-by-step cognitive internal execution records of this agent")
            )
        )

        val rootProperties = mapOf(
            "title" to SchemaProperty("STRING", "Summarized concise title representing the document"),
            "docType" to SchemaProperty("STRING", "Specialized category mode: LEGAL, HEALTHCARE, ACADEMIC, FINANCIAL, GENERAL"),
            "confidenceScore" to SchemaProperty("NUMBER", "AI cognitive validation accuracy between 0.0 and 1.0"),
            "summary" to SchemaProperty("STRING", "Futuristic high-level summary paragraph describing the core findings"),
            "fullOcrText" to SchemaProperty("STRING", "Full extracted logical textual text from the document"),
            "explanation" to SchemaProperty("STRING", "Explainable AI explanation detail mapping evidence to findings"),
            "nodes" to SchemaProperty("ARRAY", "Entities extracted and plotted", items = graphNodeSchema),
            "edges" to SchemaProperty("ARRAY", "Relations mapped", items = graphEdgeSchema),
            "risks" to SchemaProperty("ARRAY", "Anomalies, conflicts, and risks identified", items = riskSchema),
            "timeline" to SchemaProperty("ARRAY", "Extracted timeline chronological items", items = timelineSchema),
            "agentSteps" to SchemaProperty("ARRAY", "Individual collaborative AI agent execution records", items = agentSchema)
        )

        val finalSchema = ResponseSchema(
            type = "OBJECT",
            properties = rootProperties,
            required = listOf("title", "docType", "confidenceScore", "summary", "fullOcrText", "explanation", "nodes", "edges", "risks")
        )

        // 3. Assemble Prompt & Direct REST request
        val prompt = """
            You are the 'Cognitive Document Intelligence System' - a next-generation collaborative AI document OS.
            Analyze the attached image. Play the roles of collaborating agents executing in sequence:
            1. OCR Agent: Extract all layout text, titles, numbers.
            2. Context Analyzer: Determine domain from content: Legal contract, medical report, academic paper, invoice/bill, or general diagram. Determine 'docType'.
            3. Visual Reasoning & Risk Agent: Spot data points, charts, or paragraph contradictions (e.g. math errors in totals, contradictory clauses, mismatched numbers).
            4. Knowledge Graph Agent: Locate key entities and build linked relationships with designated x and y grid positions between 0.1 and 0.9.
            5. Explainability Agent: Provide deep confidence logs, justifying evidence paths, and explanations.

            Be highly rigorous, specific, and realistic based strictly on the image. Return a structured JSON conforming exactly to the responseSchema.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = finalSchema,
                temperature = 0.15f
            )
        )

        // 4. Exec Retrofit service
        val response = RetrofitClient.service.generateContent(apiKey, request)
        val responseJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty reply received from Gemini Vision API.")

        // 5. Parse using Moshi
        val adapter = moshi.adapter(DocAnalysis::class.java)
        val analyzedDoc = adapter.fromJson(responseJson)
            ?: throw IllegalStateException("Failed to parse structured response from AI.")

        // 6. Save results to local Room database to remember it
        val dbEntity = DocAnalysisEntity.fromDomain(analyzedDoc.copy(uploadTime = System.currentTimeMillis()))
        val insertedId = dao.insertAnalysis(dbEntity)
        
        analyzedDoc.copy(id = insertedId, uploadTime = dbEntity.uploadTime)
    }

    // High fidelity presets that showcase the system in intense detail instantly!
    fun getPresetDocument(type: DocType): DocAnalysis {
        return when (type) {
            DocType.LEGAL -> getLegalPreset()
            DocType.HEALTHCARE -> getMedicalPreset()
            DocType.ACADEMIC -> getAcademicPreset()
            DocType.FINANCIAL -> getFinancialPreset()
            DocType.GENERAL -> getFlowchartPreset()
        }
    }

    suspend fun saveAnalysis(doc: DocAnalysis): Long = withContext(Dispatchers.IO) {
        val entity = DocAnalysisEntity.fromDomain(doc.copy(uploadTime = System.currentTimeMillis()))
        dao.insertAnalysis(entity)
    }

    // -------------------------------------------------------------
    // HIGH-FIDELITY COGNITIVE MOCKS
    // -------------------------------------------------------------

    private fun getLegalPreset(): DocAnalysis {
        val ocrText = """
            COMMERCIAL LEASE AGREEMENT
            This Lease is entered into this 15th day of June, 2026, by and between APEX REAL ESTATE HOLDINGS ("Landlord") and NEOSOFT TECHNOLOGIES CORP ("Tenant").
            
            Paragraph 4. BASE RENT: Tenant agrees to pay Landlord Base Rent in the amount of Five Lease Units ($5,000.00) per calendar month, fully fixed and immutable for the duration of the entire two (2) year tenancy. No increases shall take effect prior to month 24.
            
            Paragraph 12. NOTICES AND AGREEMENTS: This writing constitutes the full agreement between parties.
            
            Paragraph 18. RENT ADJUSTMENTS & TAXES: Notwithstanding anything contrary herein, Landlord hereby reserves the unilateral right to adjust the Base Rent upward by up to fifteen percent (15%) after the completion of six (6) initial lease months, providing ten (10) consecutive calendar days written notice via registered mail.
        """.trimIndent()

        val summary = "A highly risky commercial lease agreement showing a severe, legally invalid contradiction regarding Base Rent stability between early rental covenants and hidden adjustment riders."

        val explanation = "The OCR and Risk agents successfully cross-referenced Paragraph 4 and Paragraph 18. Paragraph 4 asserts rent is 'fully fixed and immutable for the entire duration' ($5,000/mo) with no increases before month 24, whereas Paragraph 18 gives the Landlord the 'unilateral right' to raise rent 15% after just 6 months. This represents a binary logical direct contradiction (MUTUALLY EXCLUSIVE COVENANTS) and is flagged as a Critical Risk."

        val nodes = listOf(
            GraphNode("landlord", "Apex Holdings", "ORGANIZATION", 0.3f, 0.25f),
            GraphNode("tenant", "NeoSoft Technologies", "ORGANIZATION", 0.7f, 0.25f),
            GraphNode("p4_rent", "Rent: $5,000 (Fixed 2yrs)", "METRIC", 0.2f, 0.6f),
            GraphNode("p18_rent", "Rent Raise +15% (6mos)", "CONTRA_ELEMENT", 0.5f, 0.8f),
            GraphNode("contradiction", "Mismatched Lease Baseline", "RISK_FLAG", 0.5f, 0.5f),
            GraphNode("notice", "10-Day Notice Period", "RISK_FLAG", 0.8f, 0.6f)
        )

        val edges = listOf(
            GraphEdge("landlord", "contradiction", "REPRESENTS"),
            GraphEdge("tenant", "contradiction", "REPRESENTS"),
            GraphEdge("p4_rent", "contradiction", "ESTABLISHES"),
            GraphEdge("p18_rent", "contradiction", "AMENDS_ILLEGALLY"),
            GraphEdge("p18_rent", "notice", "TRIGGERS"),
            GraphEdge("p4_rent", "p18_rent", "CONTRADICTS")
        )

        val risks = listOf(
            RiskItem(
                "CRITICAL",
                "Rent Stabilisation Direct Contradiction",
                "Paragraph 4 vs Paragraph 18",
                "Paragraph 4 promises completely fixed rents of $5,000/mo for 24 months, while Paragraph 18 claims the Landlord can unilaterally raise it by 15% at 6 months. Executing this exposes Tenant to major unbudgeted overhead adjustments.",
                "Amend Paragraph 18 to align with Paragraph 4, or delete Paragraph 18 entirely to preserve the fixed-rate lease covenant."
            ),
            RiskItem(
                "WARNING",
                "Unreasonably Short Adjustment Notice",
                "Paragraph 18 ('10 consecutive calendar days notice')",
                "A rent increase notice of 10 days is administratively hostile, giving virtually no lead time to audit or dispute.",
                "Renegotiate notice period from 10 days to a commercially standard 45-60 business days."
            ),
            RiskItem(
                "INFO",
                "Full Integration Clause Detected",
                "Paragraph 12 ('This writing constitutes the full agreement')",
                "Standard integration limits verbal side-deals. Prevents external evidence from modifying lease interpretation.",
                "Ensure all agreed verbal concessions are fully documented in the written addendums."
            )
        )

        val timeline = listOf(
            TimelineItem("June 15, 2026", "Lease Commencement", "The contract goes into effect; tenant takes initial office possession.", "HIGH"),
            TimelineItem("Dec 15, 2026", "6-Month Adjustment Window", "The date at which Paragraph 18's rate adjustments becomes active.", "HIGH"),
            TimelineItem("May 15, 2028", "Contract Expiration", "Standard contract termination date under the 24-month fixed term.", "MEDIUM")
        )

        val steps = listOf(
            AgentStep("OCRAgent", "OCR Layout Specialist", "COMPLETED", 320, "Scanned PDF. Mapped coordinates for Paragraph 4 (Y: 0.32) and Paragraph 18 (Y: 0.84)... Found 4 paragraphs and signature zones."),
            AgentStep("ContextAgent", "Cognitive Classifier", "COMPLETED", 150, "Document format matches 'Commercial Lease Contract'. Applying Legal AI advisory expert-mode schema."),
            AgentStep("RiskAgent", "Adversarial Risk Auditor", "COMPLETED", 680, "Running cross-clause validation... Match found between keyword 'fixed rent' and 'adjust Base Rent'. Logic verification: mutual exclusion found. Triggered CRITICAL conflict flag on Paragraph 4 vs Paragraph 18."),
            AgentStep("GraphAgent", "Knowledge Graph Weaver", "COMPLETED", 430, "Extracted 6 structural nodes. Calculated spring positions to visually segregate the landlord-tenant agreement lines and isolate the collision nodes of the contradiction."),
            AgentStep("ExplainAgent", "Explainable AI Engine", "COMPLETED", 200, "Evidence mappings established. Confidence score calibrated at 97% based on explicit textual overlap.")
        )

        return DocAnalysis(
            id = 101,
            title = "Apex Lease Contradiction Scan",
            docType = DocType.LEGAL,
            uploadTime = System.currentTimeMillis() - 3600_000,
            confidenceScore = 0.97f,
            summary = summary,
            fullOcrText = ocrText,
            explanation = explanation,
            nodes = nodes,
            edges = edges,
            risks = risks,
            timeline = timeline,
            agentSteps = steps
        )
    }

    private fun getMedicalPreset(): DocAnalysis {
        val ocrText = """
            SIRIUS HEART & VASCULAR CLINICAL CLINIC
            CARDIOLOGY LABORATORY WORKUP RECORD
            Date: May 12, 2026 | Patient ID: PT-9042
            Physician: Dr. Elena Vance, MD | Specialty: Interventional Cardiology
            
            SECTION 2. SUMMARY OF CLINICAL FINDINGS & DIAGNOSED METRICS:
            The patient presented with mild sinus bradycardia. Standard 12-lead Electrocardiogram (ECG) reveals normal sinus rhythm at 55 bpm, healthy axis. Transthoracic Echocardiography (TTE) indicates healthy left ventricular chamber dimensions and healthy global systolic performance, with a Normal Calculated Ejection Fraction of 62% [62 percent]. Ventricular walls show standard thickness. No valvular stenosis.
            
            SECTION 8. ECHOCARDIOGRAPHY LOG DETAIL:
            Quantitative ultrasound telemetry reports: Diastolic Vol: 110mL, Systolic Vol: 71.5mL.
            Calculated Ejection Fraction (EF) telemetric log is severely depressed at 35% [35 percent] representing extensive hypokinesia of the mid-anteroseptal walls. Severe left ventricular systolic global dysfunction. Imminent heart failure risk indicators.
        """.trimIndent()

        val summary = "Critical life-safety medical anomaly scan. A direct diagnostic contradiction between Normal Clinical Ejection Fraction metrics and Depressed Echocardiography logs."

        val explanation = "The Diagnostic AI agent cross-referenced Cardiology telemetry in SECTION 2 (which lists EF of 62%, 'Normal/Healthy') with the raw ultrasound telemetry in SECTION 8 (which lists EF as 35% with 'severe left ventricular global dysfunction'). A 62% EF and 35% EF are clinically contradictory; a 35% EF represent severe dysfunction. A transcription error during physician report formatting is suspected and must be urgently verified."

        val nodes = listOf(
            GraphNode("physician", "Dr. Elena Vance", "PERSON", 0.5f, 0.2f),
            GraphNode("pt_sinus", "Normal Rhythm (55 bpm)", "METRIC", 0.2f, 0.4f),
            GraphNode("sec2_ef", "Listed EF: 62% (Normal)", "METRIC", 0.3f, 0.7f),
            GraphNode("sec8_ef", "Calculated EF: 35% (Severe)", "CONTRA_ELEMENT", 0.7f, 0.7f),
            GraphNode("heart_fail", "Imminent Heart Fail", "RISK_FLAG", 0.8f, 0.4f),
            GraphNode("conflict_node", "EKG Telemetry Conflict", "RISK_FLAG", 0.5f, 0.5f)
        )

        val edges = listOf(
            GraphEdge("physician", "conflict_node", "REVIEWS"),
            GraphEdge("pt_sinus", "sec2_ef", "COEXISTS"),
            GraphEdge("sec2_ef", "conflict_node", "DECLARES_ERRONEOUSLY"),
            GraphEdge("sec8_ef", "conflict_node", "MEASURES_TELEMETRY"),
            GraphEdge("sec8_ef", "heart_fail", "INDICATES"),
            GraphEdge("sec2_ef", "sec8_ef", "CONTRADICTS")
        )

        val risks = listOf(
            RiskItem(
                "CRITICAL",
                "Ejection Fraction Transcription Discrepancy",
                "SECTION 2 (62%) vs SECTION 8 (35%)",
                "SECTION 2 states ejection fraction is normal at 62%, while SECTION 8 details an objectively depressed telemetry of 35% (heart failure indicator). This represents a direct life-safety documentation threat.",
                "Recheck raw echocardiography images immediately. Contact Dr. Elena Vance to resolve transcription variance before prescribing therapy."
            ),
            RiskItem(
                "WARNING",
                "Ventricular Wall Hypokinesia",
                "SECTION 8 ('hypokinesia of mid-anteroseptal walls')",
                "Hypokinesia indicates damaged cardiac muscle from ischemia. It directly contradicts SECTION 2's 'healthy ventricular walls' label.",
                "Order an immediate Myocardial Perfusion Imaging (MPI) or Cardiac MRI to rule out active coronary artery occlusion."
            )
        )

        val timeline = listOf(
            TimelineItem("May 12, 2026", "Patient Intake ECG Scan", "Initial ECG recorded at clinic demonstrating rhythm of 55 bpm.", "MEDIUM"),
            TimelineItem("May 12, 2026", "TTE Ultrasound Logged", "Ultrasonic sensor records raw ventricular cavity dimensions.", "HIGH"),
            TimelineItem("May 13, 2026", "Prescription Review", "Date at which medical records are finalized for medication plans.", "HIGH")
        )

        val steps = listOf(
            AgentStep("OCRAgent", "OCR Layout Specialist", "COMPLETED", 410, "Scanned Cardiology workflow PDF. Extracted patient ID 'PT-9042'. Decoded numeric data '62%' and '35%' alongside cardiac terminology."),
            AgentStep("ContextAgent", "Cognitive Classifier", "COMPLETED", 120, "Identified medical chart/ECG report layout. Loaded Healthcare Clinical Intelligence Prompt Engine."),
            AgentStep("RiskAgent", "Cardiology Validator", "COMPLETED", 720, "Running therapeutic rule engine... EF discrepancy flagged. Normal limits are >= 50%. A value of 35% represents major ventricular hypokinesia. The 27% difference between values is a statistical medical impossibility."),
            AgentStep("GraphAgent", "Knowledge Graph Weaver", "COMPLETED", 310, "Assembled heart nodes. Centered the conflict node between ECG records and Ultrasound logs to highlight transcription source mismatch."),
            AgentStep("ExplainAgent", "Clinician Explainer", "COMPLETED", 180, "Computed 99% probability of clerical transposition error. Plotted evidence links directly to raw diastolic volume figures.")
        )

        return DocAnalysis(
            id = 102,
            title = "Sirius Cardiology Diagnostic Scan",
            docType = DocType.HEALTHCARE,
            uploadTime = System.currentTimeMillis() - 7200_000,
            confidenceScore = 0.99f,
            summary = summary,
            fullOcrText = ocrText,
            explanation = explanation,
            nodes = nodes,
            edges = edges,
            risks = risks,
            timeline = timeline,
            agentSteps = steps
        )
    }

    private fun getFinancialPreset(): DocAnalysis {
        val ocrText = """
            NEXUS CLOUD SOLS - PROCUREMENT INVOICE
            Invoice No: INV-2026-883 | Issue Date: May 10, 2026
            Billing Entity: NEXUS CLOUD SYSTEMS LLC, DE
            Purchaser: ALPHA LABS INC, SAN FRANCISCO, CA
            
            LINE ITEMS DETAILED:
            1. Core Cluster Server Rack (M-Class Gen-3) [Qty: 2] @ $4,500.00 each .............. $9,000.00
            2. Liquid Cooling Sub-Rack modules [Qty: 4] @ $250.00 each ........................ $1,000.00
            3. Systems Integration Architecture Consulting hours [Qty: 10] @ $125.00/hr ...... $1,250.00
            
            INVOICE SUMMARY & TOTALS:
            TOTAL ITEMIZED SUM OF CHARGES: $11,250.00
            TAX APPLICABLE (0.00% - DE Exempt): $0.00
            =========================================
            TOTAL DUE & PAYABLE AMOUNTS: $12,450.00
            =========================================
            Payment Net 14 terms. Late payments accrue 10% penalty per calendar week.
        """.trimIndent()

        val summary = "An invoice containing an undocumented discrepancy of $1,200 between individual line balances ($11,250) and the total payable amount ($12,450)."

        val explanation = "The Financial Audit and Ledger agents analyzed individual columns. The individual line items total $11,250.00. However, the listed Total Due reflects $12,450.00. There is a delta of exactly $1,200.00. Since Delaware tax is listed as 0.00%, this represents a math mismatch or a hidden, undisclosed service charge. An automated procurement reject is recommended."

        val nodes = listOf(
            GraphNode("nexus", "Nexus Cloud", "ORGANIZATION", 0.2f, 0.25f),
            GraphNode("alpha", "Alpha Labs", "ORGANIZATION", 0.8f, 0.25f),
            GraphNode("item_sum", "Itemized: $11,250", "METRIC", 0.35f, 0.65f),
            GraphNode("total_due", "Total Due: $12,450", "CONTRA_ELEMENT", 0.65f, 0.65f),
            GraphNode("delta", "Hidden Delta: $1,200", "RISK_FLAG", 0.5f, 0.45f),
            GraphNode("penalty", "+10%/W Late fee", "RISK_FLAG", 0.75f, 0.85f)
        )

        val edges = listOf(
            GraphEdge("nexus", "delta", "INVOICES"),
            GraphEdge("alpha", "delta", "OWES"),
            GraphEdge("item_sum", "delta", "SUM_PRODUCT"),
            GraphEdge("total_due", "delta", "COLLIDES_WITH"),
            GraphEdge("total_due", "penalty", "ACC_ON_LAPSE"),
            GraphEdge("item_sum", "total_due", "MUTUALLY_EXCLUSIVE_TOTALS")
        )

        val risks = listOf(
            RiskItem(
                "CRITICAL",
                "Ledger Sum Arithmetic Mismatch",
                "Line Items ($11,250) vs Total Due ($12,450)",
                "The listed individual purchase blocks do not sum up to the total payable. An undisclosed amount of $1,200 has been added to the final due sum without description.",
                "Dispute invoice instantly. Require Nexus Cloud Systems to issue a corrected invoice itemizing the $1,200 surplus, or reducing total due to $11,250."
            ),
            RiskItem(
                "WARNING",
                "Aggressive Late Payment Surcharge",
                "Net 14 Terms ('10% penalty per calendar week')",
                "A weekly penalty of 10% is highly usurious, representing an annualized rate of over 520%. This is likely legally unenforceable under CA and DE corporate statutes.",
                "Amend surcharge clause to standard 1.5% per month or state statutory prime rate."
            )
        )

        val timeline = listOf(
            TimelineItem("May 10, 2026", "Invoice Issued", "Nexus Cloud generates invoice INV-2026-883.", "MEDIUM"),
            TimelineItem("May 24, 2026", "Net 14 Due Date", "Payment contract deadline before penalties.", "HIGH"),
            TimelineItem("May 31, 2026", "Surcharge Trigger", "First 10% weekly fee compounding window.", "HIGH")
        )

        val steps = listOf(
            AgentStep("OCRAgent", "Invoice OCR Reader", "COMPLETED", 190, "Scanned procurement receipt. Structured grid layout coordinates. Mapped columns: Quantity, Unit Price, Line Total."),
            AgentStep("ContextAgent", "Cognitive Classifier", "COMPLETED", 80, "Document categorised as Corporate Procurement Invoice. Triggering accounting compliance agent pipelines."),
            AgentStep("RiskAgent", "Ledger Auditor", "COMPLETED", 510, "Triggering math validation sub-routine: Qty[2]*4500 + Qty[4]*250 + Qty[10]*125 = 9000 + 1000 + 1250 = 11250. Total Due asserts 12450. System delta error: $1,200. Verification failure: Balance does not close."),
            AgentStep("GraphAgent", "Knowledge Graph Weaver", "COMPLETED", 240, "Formulating transaction flow. Connects billing nodes with the missing delta node placed dynamically as a bridge tension element.")
        )

        return DocAnalysis(
            id = 103,
            title = "Nexus Cloud Procurement Scan",
            docType = DocType.FINANCIAL,
            uploadTime = System.currentTimeMillis() - 10800_000,
            confidenceScore = 0.98f,
            summary = summary,
            fullOcrText = ocrText,
            explanation = explanation,
            nodes = nodes,
            edges = edges,
            risks = risks,
            timeline = timeline,
            agentSteps = steps
        )
    }

    private fun getAcademicPreset(): DocAnalysis {
        val ocrText = """
            PREPRINT ARCHIVE OF NEURAL SCI - ARCHISTRY6
            "Attention is All You Deserve: Linearized Spatial Projections on Neural Memory Units"
            Authors: Prof. K. Reinhardt, Dr. Linus Chen | AI Labs, Zürich.
            
            ABSTRACT:
            We present a linearized alternative to multi-head spatial self-attention, reducing complexity from O(N^2) quadratic space allocations to O(N) linear spatial dimensions. By projecting memory units spatial queries into a localized 3D affine canvas, we achieve robust representation grids. 
            
            SECTION 3.2. BENCHMARKING AND EXPERIMENTAL OUTCOME:
            We tested our Spatial Linear Projection model (SLP-Net) on the ImageNet-1K computer vision dataset. Under standard parameters, SLP-Net achieves a peak top-1 validation classification accuracy of 94.2% [Ninety-four point two percent], which outperforms basic Vision Transformers (ViT-Base) by 1.8%.
            
            FIGURE 4. IMAGE-NET 1K PERFORMANCE GRAPH LOGS:
            Curves show training learning dynamics. Plot line (A) represents ViT-Hub baseline peaking at 85.3% accuracy. Plot line (B) represents our novel Spatial Linear Projection (SLP-Net) algorithm, which peaks at exactly 82.5% [eighty-two point five percent] accuracy, experiencing early gradient decay.
        """.trimIndent()

        val summary = "An academic paper preprint containing a direct, major academic reporting anomaly between benchmarks listed in text (94.2%) and actual plotted curves in the figures (82.5%)."

        val explanation = "The Visual reasoning agent scanned Figure 4 graph and matched visual accuracy curves of 82.5% with SECTION 3.2 text asserting 94.2% top-1 accuracy. An error of 11.7% suggests either a critical graph scaling error, or academic data inflation. This requires strict verification before peer-review filing."

        val nodes = listOf(
            GraphNode("authors", "Prof. Reinhardt & Chen", "PERSON", 0.5f, 0.2f),
            GraphNode("slp_net", "SLP-Net Algorithm", "ORGANIZATION", 0.25f, 0.45f),
            GraphNode("text_acc", "Text: 94.2% Accuracy", "METRIC", 0.3f, 0.8f),
            GraphNode("fig_acc", "Figure: 82.5% Accuracy", "CONTRA_ELEMENT", 0.7f, 0.8f),
            GraphNode("err_delta", "Delta Gap: 11.7%", "RISK_FLAG", 0.5f, 0.65f),
            GraphNode("complexity", "O(N) Complexity", "METRIC", 0.75f, 0.45f)
        )

        val edges = listOf(
            GraphEdge("authors", "slp_net", "DEVELOPED"),
            GraphEdge("slp_net", "complexity", "ACHIEVES"),
            GraphEdge("slp_net", "text_acc", "REPORTS_IN_TEXT"),
            GraphEdge("slp_net", "fig_acc", "PLOTS_IN_FIGURE"),
            GraphEdge("text_acc", "fig_acc", "CONTRADICTS"),
            GraphEdge("text_acc", "err_delta", "TRIGGERS"),
            GraphEdge("fig_acc", "err_delta", "TRIGGERS")
        )

        val risks = listOf(
            RiskItem(
                "CRITICAL",
                "Empirical Result Reporting Inconsistency",
                "SECTION 3.2 (94.2% top-1) vs Figure 4 Chart (82.5% top-1)",
                "Text reports state-of-the-art results beating the baseline, but the actual validation curves in Figure 4 show a modest 82.5% accuracy, which is lower than the baseline of 85.3%. This will trigger immediate peer rejection.",
                "Inspect research logs to confirm if the Figure 4 image was imported from an outdated run, or if the text claims are exaggerated."
            ),
            RiskItem(
                "WARNING",
                "Unaddressed Gradient Decay in Figure",
                "Figure 4 Caption ('experiencing early gradient decay')",
                "Gradient decay severely limits the scalability of spatial neural projections. The preprint abstract claims 'robust spatial representation grids' but glosses over active gradient limits.",
                "Discuss the training limitations and proposed optimization adjustments objectively in Section 4 (Discussion) to avoid reviewer criticisms."
            )
        )

        val timeline = listOf(
            TimelineItem("June 2017", "Attention Paradigm", "Introduction of quadratic O(N^2) Transformers.", "LOW"),
            TimelineItem("May 15, 2026", "Swiss Preprint Drafted", " Reinhardt drafts Switzerland Zurich labs paper.", "MEDIUM"),
            TimelineItem("June 01, 2026", "ArXiv Peer Review Submission", "Target date for open source paper disclosure.", "HIGH")
        )

        val steps = listOf(
            AgentStep("OCRAgent", "OCR Layout Specialist", "COMPLETED", 530, "Extracted abstract headers. Parsed complex superscript formulas and mapped values '94.2%' in Paragraph 3 and '82.5%' in figure caption."),
            AgentStep("ContextAgent", "Cognitive Classifier", "COMPLETED", 130, "Detected scholarly formatting, abstracts, and reference sections. Transferred execution to Academic reasoning mod."),
            AgentStep("RiskAgent", "Scientific Auditor", "COMPLETED", 690, "Cross-referenced textual assertion with figure logs. Plot line comparison indicates a direct reporting shortfall of 11.7%. Under peer-review guidelines, this represents a severe validation conflict."),
            AgentStep("GraphAgent", "Knowledge Graph Weaver", "COMPLETED", 360, "Configured neural topology grid. Map displays algorithm performance nodes with a tension weight of 11.7% indicating the accuracy variance.")
        )

        return DocAnalysis(
            id = 104,
            title = "SLP-Net Research Preprint Scan",
            docType = DocType.ACADEMIC,
            uploadTime = System.currentTimeMillis() - 14400_000,
            confidenceScore = 0.95f,
            summary = summary,
            fullOcrText = ocrText,
            explanation = explanation,
            nodes = nodes,
            edges = edges,
            risks = risks,
            timeline = timeline,
            agentSteps = steps
        )
    }

    private fun getFlowchartPreset(): DocAnalysis {
        val ocrText = """
            COSMOS INFRASTRUCTURE ARCHITECTURE CANVAS
            Specification Document: System Schema v2.6.
            
            Paragraph B. HIGH-AVAILABILITY CACHING RULES:
            To prevent database write lockups during transaction rushes, all incoming transaction API payloads MUST first pass through a highly distributed, zero-loss Redis In-Memory Cache Cluster, buffer queuing payloads, before getting written by the async Spanner Writer Agent.
            
            Paragraph K. PHYSICAL NETWORK DIAGRAM DIRECTORY:
            Canvas Diagram v2 (File: diagram.png) visualizes architecture paths:
            - Node [API Gateway IP: 10.0.1.1] shoots stream directly to Node [Spanner DB Cluster Host: 10.0.2.2] passing no firewalls or cache bridges. No Redis cache node is wired.
        """.trimIndent()

        val summary = "An architectural specification showing a critical pipeline contradiction between caching specifications (which mandate Redis buffers) and the physical system block diagram (which bypasses Redis entirely, connecting API Gateway directly to Spanner)."

        val explanation = "The Visual reasoning agent audited System Diagram Canvas v2 and discovered a direct write trail from API Gateway to Spanner Database. The OCR agent, meanwhile, analyzed Paragraph B which explicitly mandates a buffering stage through a Redis Cluster to prevent write lockups. Connecting directly to Spanner will lead to transaction contention under heavy spikes."

        val nodes = listOf(
            GraphNode("api_gw", "API Gateway (10.0.1.1)", "METRIC", 0.2f, 0.5f),
            GraphNode("spanner", "Spanner DB (10.0.2.2)", "METRIC", 0.8f, 0.5f),
            GraphNode("redis_spec", "Redis Mandates (Para B)", "ORGANIZATION", 0.5f, 0.2f),
            GraphNode("spec_mismatch", "Bypassed Cache Loop", "RISK_FLAG", 0.5f, 0.5f),
            GraphNode("write_lock", "Spanner Write Lockup", "RISK_FLAG", 0.8f, 0.8f)
        )

        val edges = listOf(
            GraphEdge("api_gw", "spanner", "CONNECTS_DIRECTLY_IN_DIAGRAM"),
            GraphEdge("redis_spec", "api_gw", "PRESCRIBES_CACHE_FOR"),
            GraphEdge("api_gw", "spec_mismatch", "BYPASSES_MANDATE"),
            GraphEdge("spanner", "write_lock", "EXPOSES_TO_LOAD"),
            GraphEdge("redis_spec", "spec_mismatch", "CONTRADICTS_DIAGRAM_PATH")
        )

        val risks = listOf(
            RiskItem(
                "CRITICAL",
                "Schema Pipeline Cache Bypass",
                "Paragraph B Caching Rules vs Diagram v2 Network Connections",
                "The text strictly mandates a Redis Cluster buffer to avoid write locks on Spanner during transaction rushes. However, the physical network diagram connects API Gateway directly to Spanner, bypassing the cache. This undercuts system resilience.",
                "Redraw diagram.png to route API Gateway traffic to Redis cluster nodes, with Redis syncing asynchronous block writes to Spanner."
            )
        )

        val timeline = listOf(
            TimelineItem("May 20, 2026", "Specification Drafted", "Initial draft of system schema v2.6 including caching specs.", "MEDIUM"),
            TimelineItem("May 22, 2026", "Diagram v2 Issued", "The architectural connection diagrams are published.", "HIGH")
        )

        val steps = listOf(
            AgentStep("OCRAgent", "Layout Analyst", "COMPLETED", 340, "Parsed blueprint schema text logs. Logged system IPs '10.0.1.1' and '10.0.2.2'."),
            AgentStep("ContextAgent", "Cognitive Classifier", "COMPLETED", 90, "Classified document as System Architecture Specification under General canvas reasoning template."),
            AgentStep("RiskAgent", "Diagram Auditor", "COMPLETED", 580, "Cross-referenced network path matrices. Spec says: API Gateway -> Redis Cluster -> Spanner DB. Diagram shows: API Gateway -> Spanner DB. Mismatch is a critical high-load failure vector."),
            AgentStep("GraphAgent", "Knowledge Graph Weaver", "COMPLETED", 200, "Assembled system network nodes. Visualised the omitted Redis cache bridge in red to denote missing pipeline link.")
        )

        return DocAnalysis(
            id = 105,
            title = "Cosmos HA System Specification",
            docType = DocType.GENERAL,
            uploadTime = System.currentTimeMillis() - 17200_000,
            confidenceScore = 0.96f,
            summary = summary,
            fullOcrText = ocrText,
            explanation = explanation,
            nodes = nodes,
            edges = edges,
            risks = risks,
            timeline = timeline,
            agentSteps = steps
        )
    }
}
