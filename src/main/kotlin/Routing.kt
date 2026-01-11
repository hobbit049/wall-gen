import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.forms.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import model.*
import java.io.File
import java.util.*

fun Application.configureRouting(userRepository: UserRepository, projectRepository: ProjectRepository) {
    val jwtConfig = environment.config.config("ktor.jwt")
    val secret = jwtConfig.property("secret").getString()
    val issuer = jwtConfig.property("issuer").getString()
    val audience = jwtConfig.property("audience").getString()

    routing {
        get("/") {
            call.respondText("Welcome to the Generative Art server!")
        }

        post("/signup") {
            val user = call.receive<User>()

            val result = validateNewUser(user, userRepository)
            if (result.success) {
                userRepository.addUser(user)
                call.respond(HttpStatusCode.OK)
                return@post
            } else {
                call.respond(HttpStatusCode.BadRequest, result.message)
                return@post
            }
        }

        post("/login") {
            val user = call.receive<User>()
            // Check username and password
            if (!userRepository.isUser(user)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            //build and return token
            val token = JWT.create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("username", user.username)
                .withExpiresAt(Date(System.currentTimeMillis() + 500000))
                .sign(Algorithm.HMAC256(secret))
            call.respond(hashMapOf("token" to token))
        }

        //read op for all projects
        get("/projects") {
            call.respond(projectRepository.getProjects())
            return@get
        }

        //read op for projects created/updated past a certain timestamp
        get("/projects/since/{timestamp}") {
            val timestamp = call.parameters["timestamp"]?.toLong()

            if (timestamp == null) {
                call.respond(HttpStatusCode.BadRequest, "No timestamp provided")
                return@get
            }

            //get the projects since timestamp
            call.respond(projectRepository.getProjectsSinceTimestamp(timestamp))
            return@get
        }

        //read op for projects by a particular user
        get("/projects/user/{username}") {
            val username = call.parameters["username"]

            if (username == null) {
                call.respond(HttpStatusCode.BadRequest, "No username provided")
                return@get
            }

            call.respond(projectRepository.getProjectsByUser(username))
            return@get
        }

        //read op for particular projects
        get("/project/{uuid}") {
            val uuid = call.parameters["uuid"]

            if (uuid == null) {
                call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                return@get
            }

            val project = projectRepository.getProjectById(uuid)

            if (project == null) {
                call.respond(HttpStatusCode.BadRequest, "No project with that ID exists")
                return@get
            }

            call.respond(project)
            return@get
        }

        //read op for the image of a particular project
        get("/project/image/{uuid}") {
            val uuid = call.parameters["uuid"]

            if (uuid == null) {
                call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                return@get
            }

            val project = projectRepository.getProjectById(uuid)

            if (project == null) {
                call.respond(HttpStatusCode.BadRequest, "No project with that ID exists")
                return@get
            }

            call.respondFile(File("data/$uuid.jpg"))
            return@get
        }

        //run op for particular projects
        get("/project/run/{uuid}") {
            val uuid = call.parameters["uuid"]
            if (uuid == null) {
                call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                return@get
            }

            val project = projectRepository.getProjectById(uuid)
            if (project == null) {
                call.respond(HttpStatusCode.BadRequest, "No project with that ID exists")
                return@get
            }

            val width: Int?
            val height: Int?
            try {
                width = call.request.queryParameters["width"]?.toInt()
                height = call.request.queryParameters["height"]?.toInt()
            } catch (e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Width or height is not a valid integer")
                return@get
            }
            if (width == null || height == null) {
                call.respond(HttpStatusCode.BadRequest, "Width or height of image to be generated was not provided")
                return@get
            }

            //run it!
            val process = ProcessBuilder("java", "-jar", "data/$uuid.jar", width.toString(), height.toString(), "output.jpg")
                .redirectOutput(File("output.txt"))
                .redirectError(File("error.txt"))
                .start()
            val exitCode = process.waitFor()

            call.respondFile(File("output.jpg"))
            return@get
        }

        authenticate("auth-jwt") {
            get("/myprojects") {
                val username = getUsernameFromAuthedCall(call)

                call.respond(projectRepository.getProjectsByUser(username))
                return@get
            }

            post("/project/create") {
                val multipart = call.receiveMultipart()

                //read the multipart form data
                var newProject: ProjectData? = null
                var jarPart: PartData.FileItem? = null
                var imagePart: PartData.FileItem? = null
                multipart.forEachPart {part ->
                    when (part) {
                        is PartData.FormItem -> {
                            try { newProject = Json.decodeFromString(part.value) } catch (_: Exception) {}
                        }
                        is PartData.FileItem -> {
                            when (part.name) {
                                "jar" -> { jarPart = part }
                                "image" -> { imagePart = part }
                            }
                        }
                        else -> {}
                    }
                }

                //throw errors if executable jar file, thumbnail image, or NewProject json are not present
                if (newProject == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or un-parseable new project json")
                    return@post
                } else if (jarPart == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing jar file")
                    return@post
                } else if (imagePart == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing image file")
                    return@post
                }

                //throw errors if project name is invalid (too long or conflicting with existing project)
                val username = getUsernameFromAuthedCall(call)
                val result = validateNewProject(newProject!!, username, projectRepository)
                if (!result.success) {
                    call.respond(HttpStatusCode.BadRequest, result.message)
                    return@post
                }

                //create project object and add to the database
                val uuid = UUID.randomUUID().toString()
                val project = Project(
                    uuid,
                    username,
                    newProject!!.name,
                    newProject!!.description,
                    1,
                    java.time.Instant.now().toEpochMilli()
                )
                projectRepository.addProject(project)

                //save jar file in local filesystem
                val jarFile = File("data/$uuid.jar")
                jarPart!!.streamProvider().use { input ->
                    jarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                //save image file in local filesystem
                val imageFile = File("data/$uuid.jpg")
                imagePart!!.streamProvider().use { input ->
                    imageFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                //dispose of the parts to free resources
                multipart.forEachPart {
                    it.dispose()
                }

                call.respond(HttpStatusCode.OK, project)
                return@post
            }

            post("project/update/project/{uuid}") {
                val uuid = call.parameters["uuid"]

                if (uuid == null) {
                    call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                    return@post
                }

                val username = getUsernameFromAuthedCall(call)
                if (!projectBelongsToUser(uuid, username, projectRepository)) {
                    call.respond(HttpStatusCode.BadRequest, "This project does not exist or does not belong to you")
                    return@post
                }

                val updateProjectData = call.receive<ProjectData>()
                projectRepository.updateProjectData(uuid, updateProjectData)
                projectRepository.updateProjectVersionAndTimestamp(uuid)

                call.respond(HttpStatusCode.OK)
                return@post
            }

            post("project/update/jar/{uuid}") {
                val uuid = call.parameters["uuid"]

                if (uuid == null) {
                    call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                    return@post
                }

                val username = getUsernameFromAuthedCall(call)
                if (!projectBelongsToUser(uuid, username, projectRepository)) {
                    call.respond(HttpStatusCode.BadRequest, "This project does not exist or does not belong to you")
                    return@post
                }

                val multipart = call.receiveMultipart()
                var jarPart: PartData.FileItem? = null
                multipart.forEachPart {part ->
                    when (part) {
                        is PartData.FileItem -> {
                            if (part.name == "jar") {
                                jarPart = part
                            }
                        }
                        else -> {}
                    }
                }

                if (jarPart == null) {
                    call.respond(HttpStatusCode.BadRequest, "Jar file not included")
                    return@post
                }

                val jarFile = File("data/$uuid.jar")
                jarFile.delete()
                jarPart!!.streamProvider().use { input ->
                    jarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                projectRepository.updateProjectVersionAndTimestamp(uuid)

                multipart.forEachPart {
                    it.dispose()
                }

                call.respond(HttpStatusCode.OK)
                return@post
            }

            post("project/update/image/{uuid}") {
                val uuid = call.parameters["uuid"]

                if (uuid == null) {
                    call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                    return@post
                }

                val username = getUsernameFromAuthedCall(call)
                if (!projectBelongsToUser(uuid, username, projectRepository)) {
                    call.respond(HttpStatusCode.BadRequest, "This project does not exist or does not belong to you")
                    return@post
                }

                val multipart = call.receiveMultipart()
                var imagePart: PartData.FileItem? = null
                multipart.forEachPart {part ->
                    when (part) {
                        is PartData.FileItem -> {
                            if (part.name == "image") {
                                imagePart = part
                            }
                        }
                        else -> {}
                    }
                }

                if (imagePart == null) {
                    call.respond(HttpStatusCode.BadRequest, "Image file not included")
                    return@post
                }

                val imageFile = File("data/$uuid.jpg")
                imageFile.delete()
                imagePart!!.streamProvider().use { input ->
                    imageFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                projectRepository.updateProjectVersionAndTimestamp(uuid)

                multipart.forEachPart {
                    it.dispose()
                }

                call.respond(HttpStatusCode.OK)
                return@post
            }

            delete("project/delete/{uuid}") {
                val uuid = call.parameters["uuid"]

                if (uuid == null) {
                    call.respond(HttpStatusCode.BadRequest, "No project ID provided")
                    return@delete
                }

                val username = getUsernameFromAuthedCall(call)
                if (!projectBelongsToUser(uuid, username, projectRepository)) {
                    call.respond(HttpStatusCode.BadRequest, "This project does not exist or does not belong to you")
                    return@delete
                }

                //delete in database
                projectRepository.deleteProject(uuid)
                //delete in filesystem
                val jarFile = File("data/$uuid.jar")
                jarFile.delete()
                val imageFile = File("data/$uuid.jpg")
                imageFile.delete()

                call.respond(HttpStatusCode.OK)
                return@delete
            }
        }
    }

}

suspend fun validateNewUser(user: User, userRepository: UserRepository): Result {
    if (user.username.length < 8 || user.username.length > 50) {
        return Result(false, "Username must be 8-50 characters long")
    }
    if (user.password.length < 8 || user.password.length > 50) {
        return Result(false, "Password must be 8-50 characters long")
    }

    val users = userRepository.getUsers()
    val uniqueUsername = !users.any { it.username == user.username }
    return Result(uniqueUsername, "Username is already taken")
}

suspend fun validateNewProject(newProject: ProjectData, username: String, projectRepository: ProjectRepository): Result {
    //check name size
    if (newProject.name.length >= 60) {
        return Result(false, "Name must be less than 60 characters long")
    }

    //check name conflict(for projects by the same user)
    val projects = projectRepository.getProjects()
    val uniqueName = !projects.any {it.username == username && it.name == newProject.name}
    return Result(uniqueName, "Name is already taken by one of your existing projects")
}

fun getUsernameFromAuthedCall(call: ApplicationCall): String {
    val principal = call.principal<JWTPrincipal>()
    return principal!!.payload.getClaim("username").asString()
}

suspend fun projectBelongsToUser(projectId: String, username: String, projectRepository: ProjectRepository): Boolean {
    val project = projectRepository.getProjectById(projectId)
    return project?.username == username
}

data class Result(val success: Boolean, val message: String)