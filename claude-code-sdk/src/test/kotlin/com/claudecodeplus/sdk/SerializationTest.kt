package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.InterruptRequest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SerializationTest {
    
    @Test
    fun `test InterruptRequest serialization`() {
        val json = Json { 
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val request = InterruptRequest()
        
        val jsonElement = json.encodeToJsonElement(InterruptRequest.serializer(), request)
        println("Serialized InterruptRequest: $jsonElement")
        
        val jsonObject = jsonElement.jsonObject
        assertEquals("interrupt", jsonObject["subtype"]?.jsonPrimitive?.content)
    }
}