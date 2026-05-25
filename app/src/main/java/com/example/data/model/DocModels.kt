package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class DocType {
    @Json(name = "LEGAL") LEGAL,
    @Json(name = "HEALTHCARE") HEALTHCARE,
    @Json(name = "ACADEMIC") ACADEMIC,
    @Json(name = "FINANCIAL") FINANCIAL,
    @Json(name = "GENERAL") GENERAL
}

@JsonClass(generateAdapter = true)
data class GraphNode(
    val id: String,
    val label: String,
    val type: String, // e.g. "Person", "Org", "Date", "Law", "Risk", "Metrics"
    var x: Float = 0f, // For visual positioning in Compose Custom Canvas
    var y: Float = 0f  // For visual positioning in Compose Custom Canvas
)

@JsonClass(generateAdapter = true)
data class GraphEdge(
    val from: String,
    val to: String,
    val relation: String // e.g. "CONTRADICTS", "REPORTS", "OWNED_BY", "VERIFIES", "SIGNATORY_OF"
)

@JsonClass(generateAdapter = true)
data class RiskItem(
    val severity: String, // "CRITICAL", "WARNING", "INFO"
    val title: String,
    val clauseText: String,
    val reasoning: String,
    val suggestion: String
)

@JsonClass(generateAdapter = true)
data class TimelineItem(
    val date: String,
    val title: String,
    val significance: String,
    val level: String // "HIGH", "MEDIUM", "LOW"
)

@JsonClass(generateAdapter = true)
data class AgentStep(
    val agentId: String,
    val agentName: String,
    val status: String, // "PENDING", "RUNNING", "COMPLETED", "FAILED"
    val durationMs: Long,
    val logs: String
)

@JsonClass(generateAdapter = true)
data class DocAnalysis(
    val id: Long = 0,
    val title: String,
    val docType: DocType,
    val uploadTime: Long,
    val confidenceScore: Float,
    val summary: String,
    val fullOcrText: String,
    val explanation: String,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val risks: List<RiskItem> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val agentSteps: List<AgentStep> = emptyList(),
    val customImageUriStr: String? = null
)
