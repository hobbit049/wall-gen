import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
    Database.connect(
        "jdbc:postgresql://db.ljoldrmvgkpquyyctqth.supabase.co:5432/postgres",
        user = "postgres",
        password = "genart59332712"
    )
}