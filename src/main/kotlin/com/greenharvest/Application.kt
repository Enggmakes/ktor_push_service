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
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import java.io.InputStream
import org.slf4j.event.Level

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
        // We expect the user to provide a firebase-adminsdk.json file in the deployment environment.
        // Render.com allows mounting secret files, or it can be packaged in src/main/resources (not recommended for public repos).
        // For testing, look for it in the classpath or as a system environment path.
        val serviceAccountStream: InputStream? = this::class.java.classLoader.getResourceAsStream("firebase-adminsdk.json") 
            ?: System.getenv("FIREBASE_CREDENTIALS_PATH")?.let { java.io.File(it).inputStream() }

        if (serviceAccountStream != null) {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build()
            
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                log.info("Firebase Admin SDK initialized successfully.")
            }
        } else {
            log.warn("WARNING: firebase-adminsdk.json not found! Push notifications will fail.")
        }
    } catch (e: Exception) {
        log.error("Failed to initialize Firebase Admin", e)
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
                // Parse incoming JSON body
                val request = call.receive<NotificationRequest>()
                
                // Construct the Firebase Message
                // This targets a specific Topic (e.g. user ID topic) or Token.
                // Assuming we subscribe vendors to a topic matching their sellerId.
                val message = Message.builder()
                    .setTopic(request.targetId) // e.g., "seller_abc123"
                    .setNotification(
                        Notification.builder()
                            .setTitle(request.title)
                            .setBody(request.body)
                            .build()
                    )
                    .putData("type", request.type ?: "default")
                    .putData("timestamp", request.timestamp ?: "")
                    .build()

                // Send via FCM
                val response = FirebaseMessaging.getInstance().send(message)
                log.info("Successfully sent message: $response")

                call.respond(HttpStatusCode.OK, mapOf("success" to true, "messageId" to response))
            } catch (e: Exception) {
                log.error("Failed to send notification via FCM", e)
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
        log.warn("WARNING: APP_URL environment variable not set. Self-wake loop disabled.")
        return
    }

    val client = HttpClient(CIO)
    
    // Launch background coroutine
    launch {
        log.info("Starting self-wake loop for $url...")
        while (isActive) {
            try {
                // Wait 12 minutes (Render sleeps after 15m of inactivity)
                delay(12 * 60 * 1000L) 
                
                log.info("Self-wake pinging $url...")
                val response = client.get(url)
                log.info("Self-wake response status: ${response.status}")
            } catch (e: CancellationException) {
                log.info("Self-wake loop cancelled.")
                break
            } catch (e: Exception) {
                log.error("Self-wake ping failed: ${e.message}")
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
