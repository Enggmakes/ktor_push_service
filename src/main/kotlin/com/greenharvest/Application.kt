package com.greenharvest

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import java.io.InputStream
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.slf4j.event.Level

private val logger: Logger = LoggerFactory.getLogger("Application")

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // 1. Configure JSON Parsing (Jackson)
    install(ContentNegotiation) {
        jackson()
    }

    // 2. Configure Logging
    install(CallLogging) {
        level = Level.INFO
    }

    // 3. Initialize Firebase Admin SDK
    try {
        val serviceAccountStream: InputStream? = this::class.java.classLoader.getResourceAsStream("firebase-adminsdk.json") 
            ?: System.getenv("FIREBASE_CREDENTIALS_PATH")?.let { java.io.File(it).inputStream() }

        if (serviceAccountStream != null) {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build()
            
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK initialized successfully.")
            }
        } else {
            logger.warn("WARNING: firebase-adminsdk.json not found! Push notifications will fail.")
        }
    } catch (e: Exception) {
        logger.error("Failed to initialize Firebase Admin", e)
    }

    // 4. Set up HTTP Routing
    routing {
        get("/") {
            call.respondText("I am awake!")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        post("/api/v1/notify") {
            try {
                val request = call.receive<NotificationRequest>()
                
                val message = Message.builder()
                    .setTopic(request.targetId)
                    .setNotification(
                        Notification.builder()
                            .setTitle(request.title)
                            .setBody(request.body)
                            .build()
                    )
                    .putData("type", request.type ?: "default")
                    .putData("timestamp", request.timestamp ?: "")
                    .build()

                val response = FirebaseMessaging.getInstance().send(message)
                logger.info("Successfully sent message: $response")

                call.respond(HttpStatusCode.OK, mapOf("success" to true, "messageId" to response))
            } catch (e: Exception) {
                logger.error("Failed to send notification via FCM", e)
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("success" to false, "error" to e.localizedMessage)
                )
            }
        }
    }

    // 5. Start Self-Wake Loop (Keep-Alive for Render.com)
    startKeepAlive()
}

/**
 * Periodically pings the application's own URL to prevent Render.com from sleeping.
 * Expects an environment variable 'APP_URL' (e.g., https://your-app.onrender.com).
 */
fun Application.startKeepAlive() {
    val url = System.getenv("APP_URL")
    if (url.isNullOrBlank()) {
        logger.warn("WARNING: APP_URL environment variable not set. Self-wake loop disabled.")
        return
    }

    val client = HttpClient(CIO)
    
    // Launch background coroutine
    launch {
        logger.info("Starting self-wake loop for $url...")
        while (isActive) {
            try {
                // Wait 12 minutes (Render sleeps after 15m of inactivity)
                delay(12 * 60 * 1000L) 
                
                logger.info("Self-wake pinging $url...")
                val response = client.get(url)
                logger.info("Self-wake response status: ${response.status}")
            } catch (e: CancellationException) {
                logger.info("Self-wake loop cancelled.")
                break
            } catch (e: Exception) {
                logger.error("Self-wake ping failed: ${e.message}")
            }
        }
        client.close()
    }
}

// Data class mapping exactly to the payload sent from the Flutter App
data class NotificationRequest(
    val targetId: String,
    val title: String,
    val body: String,
    val type: String?,
    val timestamp: String?
)
