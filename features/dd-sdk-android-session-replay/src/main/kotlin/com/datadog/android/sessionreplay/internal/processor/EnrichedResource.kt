/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.google.gson.JsonObject

internal data class EnrichedResource(
    internal val resource: ByteArray,
    internal val filename: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EnrichedResource

        if (!resource.contentEquals(other.resource)) return false
        return filename == other.filename
    }

    override fun hashCode(): Int {
        var result = resource.contentHashCode()
        result = 31 * result + filename.hashCode()
        return result
    }

    internal companion object {
        internal const val APPLICATION_ID_KEY = "applicationId"
        internal const val APPLICATION_KEY = "application"
        internal const val ID_KEY = "id"
        internal const val FILENAME_KEY = "filename"
    }
}

internal fun EnrichedResource.asBinaryMetadata(rumApplicationId: String): ByteArray {
    val filename = this.filename
    val jsonObject = JsonObject()
    jsonObject.addProperty(EnrichedResource.APPLICATION_ID_KEY, rumApplicationId)
    jsonObject.addProperty(EnrichedResource.FILENAME_KEY, filename)
    return jsonObject.toString().toByteArray(Charsets.UTF_8)
}
