package org.theinfinitys.utils.translation

import com.google.gson.JsonElement
import com.google.gson.JsonObject

<<<<<<< HEAD
/* Translation layer made by noobyetpro */
=======
>>>>>>> 126e12847dadb5ae723ac63ab7de2db9aff7d2ee
fun JsonObject.deepGet(keyPath: String): JsonElement? {
    val parts = keyPath.split(".")
    var element: JsonElement? = this

    for (part in parts) {
        element = if (element is JsonObject) element.get(part) else null
    }
    return element
}
