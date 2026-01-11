package model

import db.*
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update

class PostgresProjectRepository: ProjectRepository {
    override suspend fun addProject(project: Project): Unit = suspendTransaction{
        ProjectDAO.new {
            uuid = project.uuid
            username = project.username
            name = project.name
            description = project.description
            version = project.version
            lastUpdated = project.lastUpdated
        }
    }

    override suspend fun getProjects(): List<Project> = suspendTransaction{
        ProjectDAO.all().map(::daoToModel)
    }

    override suspend fun getProjectsByUser(username: String): List<Project> = suspendTransaction{
        ProjectDAO.find { (ProjectTable.username eq username) }.map(::daoToModel)
    }

    override suspend fun getProjectsSinceTimestamp(timestamp: Long): List<Project> = suspendTransaction{
        ProjectDAO.find { (ProjectTable.lastUpdated greaterEq timestamp) }.map(::daoToModel)
    }

    override suspend fun getProjectById(uuid: String): Project? = suspendTransaction{
        ProjectDAO.find { (ProjectTable.uuid eq uuid) }.limit(1).map(::daoToModel).firstOrNull()
    }

    override suspend fun deleteProject(uuid: String): Unit = suspendTransaction{
        ProjectTable.deleteWhere {
            ProjectTable.uuid eq uuid
        }
    }

    override suspend fun updateProjectData(uuid: String, updateProjectData: ProjectData): Unit = suspendTransaction{
        ProjectTable.update ({ ProjectTable.uuid eq uuid }) {
            it[name] = updateProjectData.name
            it[description] = updateProjectData.description
        }
    }

    override suspend fun updateProjectVersionAndTimestamp(uuid: String): Unit = suspendTransaction{
        val project = ProjectDAO.find { (ProjectTable.uuid eq uuid) }.limit(1).map(::daoToModel).firstOrNull()
        project?.let{
            ProjectTable.update ({ ProjectTable.uuid eq uuid }) {
                it[version] = project.version + 1
                it[lastUpdated] = java.time.Instant.now().toEpochMilli()
            }
        }
    }
}