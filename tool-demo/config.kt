package com.example.config

object AppConfig {
    const val DEBUG_MODE = false
    const val LOG_LEVEL = "INFO"

    object Database {
        const val HOST = "localhost"
        const val PORT = 5432
        const val USER = "admin"
    }

    object Cache {
        const val ENABLED = false
        const val TTL = 3600
    }

    object Api {
        const val TIMEOUT = 30
        const val RETRY_COUNT = 3
    }
}
