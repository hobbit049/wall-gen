package model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectData(val name: String, val description: String)
