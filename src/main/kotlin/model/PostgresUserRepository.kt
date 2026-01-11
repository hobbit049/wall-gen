package model

import db.UserDAO
import db.UserTable
import db.daoToModel
import db.suspendTransaction
import org.jetbrains.exposed.sql.and

class PostgresUserRepository: UserRepository {
    override suspend fun getUsers(): List<User> = suspendTransaction {
        UserDAO.all().map(::daoToModel)
    }

    override suspend fun addUser(user: User): Unit = suspendTransaction {
        UserDAO.new {
            username = user.username
            passhash = hashPassword(user.password)
        }
    }

    override suspend fun isUser(user: User): Boolean = suspendTransaction {
        //get the user with matching username
        val matchingUser = UserDAO
            .find { (UserTable.username eq user.username) }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()

        //compare password hashes
        matchingUser?.let {
            return@suspendTransaction verifyPassword(user.password, matchingUser.password)
        }
        return@suspendTransaction false
    }
}