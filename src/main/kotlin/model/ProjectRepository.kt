package model

interface ProjectRepository {
    suspend fun addProject(project: Project)
    suspend fun getProjects(): List<Project>
    suspend fun getProjectsByUser(username: String): List<Project>
    suspend fun getProjectsSinceTimestamp(timestamp: Long): List<Project>
    suspend fun getProjectById(uuid: String): Project?
    suspend fun deleteProject(uuid: String)
    suspend fun updateProjectData(uuid: String, updateProjectData: ProjectData)
    suspend fun updateProjectVersionAndTimestamp(uuid: String)
}