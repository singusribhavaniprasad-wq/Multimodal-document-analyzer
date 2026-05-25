package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DocConverters {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @TypeConverter
    fun fromDocType(value: DocType): String = value.name

    @TypeConverter
    fun toDocType(value: String): DocType = DocType.valueOf(value)

    @TypeConverter
    fun fromGraphNodeList(list: List<GraphNode>): String {
        val type = Types.newParameterizedType(List::class.java, GraphNode::class.java)
        return moshi.adapter<List<GraphNode>>(type).toJson(list)
    }

    @TypeConverter
    fun toGraphNodeList(json: String): List<GraphNode> {
        val type = Types.newParameterizedType(List::class.java, GraphNode::class.java)
        return moshi.adapter<List<GraphNode>>(type).fromJson(json) ?: emptyList()
    }

    @TypeConverter
    fun fromGraphEdgeList(list: List<GraphEdge>): String {
        val type = Types.newParameterizedType(List::class.java, GraphEdge::class.java)
        return moshi.adapter<List<GraphEdge>>(type).toJson(list)
    }

    @TypeConverter
    fun toGraphEdgeList(json: String): List<GraphEdge> {
        val type = Types.newParameterizedType(List::class.java, GraphEdge::class.java)
        return moshi.adapter<List<GraphEdge>>(type).fromJson(json) ?: emptyList()
    }

    @TypeConverter
    fun fromRiskItemList(list: List<RiskItem>): String {
        val type = Types.newParameterizedType(List::class.java, RiskItem::class.java)
        return moshi.adapter<List<RiskItem>>(type).toJson(list)
    }

    @TypeConverter
    fun toRiskItemList(json: String): List<RiskItem> {
        val type = Types.newParameterizedType(List::class.java, RiskItem::class.java)
        return moshi.adapter<List<RiskItem>>(type).fromJson(json) ?: emptyList()
    }

    @TypeConverter
    fun fromTimelineItemList(list: List<TimelineItem>): String {
        val type = Types.newParameterizedType(List::class.java, TimelineItem::class.java)
        return moshi.adapter<List<TimelineItem>>(type).toJson(list)
    }

    @TypeConverter
    fun toTimelineItemList(json: String): List<TimelineItem> {
        val type = Types.newParameterizedType(List::class.java, TimelineItem::class.java)
        return moshi.adapter<List<TimelineItem>>(type).fromJson(json) ?: emptyList()
    }

    @TypeConverter
    fun fromAgentStepList(list: List<AgentStep>): String {
        val type = Types.newParameterizedType(List::class.java, AgentStep::class.java)
        return moshi.adapter<List<AgentStep>>(type).toJson(list)
    }

    @TypeConverter
    fun toAgentStepList(json: String): List<AgentStep> {
        val type = Types.newParameterizedType(List::class.java, AgentStep::class.java)
        return moshi.adapter<List<AgentStep>>(type).fromJson(json) ?: emptyList()
    }
}
