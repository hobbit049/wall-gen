package model

import org.mindrot.jbcrypt.BCrypt

interface UserRepository {
    suspend fun getUsers(): List<User>
    suspend fun addUser(user: User)
    suspend fun isUser(user: User): Boolean

    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }
    fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return BCrypt.checkpw(password, hashedPassword)
    }
}