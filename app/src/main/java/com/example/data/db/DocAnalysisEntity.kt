package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.*

@Entity(tableName = "doc_analyses")
data class DocAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val docType: DocType,
    val uploadTime: Long,
    val confidenceScore: Float,
    val summary: String,
    val fullOcrText: String,
    val explanation: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val risks: List<RiskItem>,
    val timeline: List<TimelineItem>,
    val agentSteps: List<AgentStep>,
    val customImageUriStr: String?
) {
    fun toDomain(): DocAnalysis = DocAnalysis(
        id = id,
        title = title,
        docType = docType,
        uploadTime = uploadTime,
        confidenceScore = confidenceScore,
        summary = summary,
        fullOcrText = fullOcrText,
        explanation = explanation,
        nodes = nodes,
        edges = edges,
        risks = risks,
        timeline = timeline,
        agentSteps = agentSteps,
        customImageUriStr = customImageUriStr
    )

    companion object {
        fun fromDomain(doc: DocAnalysis): DocAnalysisEntity = DocAnalysisEntity(
            id = doc.id,
            title = doc.title,
            docType = doc.docType,
            uploadTime = doc.uploadTime,
            confidenceScore = doc.confidenceScore,
            summary = doc.summary,
            fullOcrText = doc.fullOcrText,
            explanation = doc.explanation,
            nodes = doc.nodes,
            edges = doc.edges,
            risks = doc.risks,
            timeline = doc.timeline,
            agentSteps = doc.agentSteps,
            customImageUriStr = doc.customImageUriStr
        )
    }
}
