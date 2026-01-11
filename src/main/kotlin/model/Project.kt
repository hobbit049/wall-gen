package model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    var uuid: String,
    var username: String,
    val name: String,
    val description: String,
    val version: Int,
    val lastUpdated: Long
)

@Serializable
data class ProjectList(val projects: List<Project>)