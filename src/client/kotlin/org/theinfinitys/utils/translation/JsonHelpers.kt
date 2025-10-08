package org.theinfinitys.utils.translation

import com.google.gson.JsonElement
import com.google.gson.JsonObject

fun JsonObject.deepGet(keyPath: String): JsonElement? {
    val parts = keyPath.split(".")
    var element: JsonElement? = this

    for (part in parts) {
        element = if (element is JsonObject) element.get(part) else null
    }
    return element
}
