package com.spotitfly.app.data.local.entity

import org.json.JSONArray
import org.json.JSONObject

internal object ChatLocalJson {

    fun mapLongToJson(map: Map<String, Long>?): String? {
        if (map.isNullOrEmpty()) return null
        val obj = JSONObject()
        map.forEach { (key, value) ->
            obj.put(key, value)
        }
        return obj.toString()
    }

    fun jsonToLongMap(json: String?): Map<String, Long>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optLong(key)
                result[key] = value
            }
            result.toMap()
        } catch (_: Exception) {
            null
        }
    }

    fun mapBooleanToJson(map: Map<String, Boolean>?): String? {
        if (map.isNullOrEmpty()) return null
        val obj = JSONObject()
        map.forEach { (key, value) ->
            obj.put(key, value)
        }
        return obj.toString()
    }

    fun jsonToBooleanMap(json: String?): Map<String, Boolean>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, Boolean>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optBoolean(key)
                result[key] = value
            }
            result.toMap()
        } catch (_: Exception) {
            null
        }
    }

    fun listToJson(list: List<String>?): String? {
        if (list.isNullOrEmpty()) return null
        val array = JSONArray()
        list.forEach { value ->
            array.put(value)
        }
        return array.toString()
    }

    fun jsonToStringList(json: String?): List<String>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (!value.isNullOrEmpty()) {
                    result.add(value)
                }
            }
            if (result.isEmpty()) null else result.toList()
        } catch (_: Exception) {
            null
        }
    }
}
