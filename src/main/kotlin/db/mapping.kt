package db

import kotlinx.coroutines.Dispatchers
import model.Project
import model.User
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object UserTable : IntIdTable("users") {
    val username = varchar("username", 60)
    val passhash = varchar("passhash", 60)
}

class UserDAO(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<UserDAO>(UserTable)

    var username by UserTable.username
    var passhash by UserTable.passhash
}

object ProjectTable: IntIdTable("projects") {
    val uuid = varchar("uuid", 60)
    val username = varchar("username", 60)
    val name = varchar("name", 60)
    val description = varchar("description", 500)
    val version = integer("version")
    val lastUpdated = long("last_updated")
}

class ProjectDAO(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<ProjectDAO>(ProjectTable)

    var uuid by ProjectTable.uuid
    var username by ProjectTable.username
    var name by ProjectTable.name
    var description by ProjectTable.description
    var version by ProjectTable.version
    var lastUpdated by ProjectTable.lastUpdated
}

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)

fun daoToModel(dao: UserDAO) = User(
    dao.username,
    dao.passhash
)

fun daoToModel(dao: ProjectDAO) = Project(
    dao.uuid,
    dao.username,
    dao.name,
    dao.description,
    dao.version,
    dao.lastUpdated
)

