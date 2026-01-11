package com.example

import configureAuthentication
import configureDatabases
import configureRouting
import configureSerialization
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import model.PostgresProjectRepository
import model.PostgresUserRepository

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val userRepo = PostgresUserRepository()
    val projectRepo = PostgresProjectRepository()

    configureDatabases()
    configureSerialization()
    configureAuthentication()
    configureRouting(userRepo, projectRepo)
}
