package com.tballhelper.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GameConfig(
    val name: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val templateId: String
)

data class TemplateInfo(
    val id: String,
    val name: String,
    val file: File
)

class TemplateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("tball_prefs", Context.MODE_PRIVATE)
    private val templateDir = File(context.filesDir, "templates").apply { mkdirs() }

    fun loadGames(): List<GameConfig> {
        val json = prefs.getString("games", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                GameConfig(
                    obj.getString("name"),
                    obj.getInt("screenWidth"),
                    obj.getInt("screenHeight"),
                    obj.getString("templateId")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveGames(games: List<GameConfig>) {
        val array = JSONArray()
        games.forEach { game ->
            val obj = JSONObject().apply {
                put("name", game.name)
                put("screenWidth", game.screenWidth)
                put("screenHeight", game.screenHeight)
                put("templateId", game.templateId)
            }
            array.put(obj)
        }
        prefs.edit().putString("games", array.toString()).apply()
    }

    fun getTemplateFile(templateId: String): File {
        return File(templateDir, "$templateId.png")
    }

    fun saveTemplate(templateId: String, bitmap: android.graphics.Bitmap) {
        val file = getTemplateFile(templateId)
        file.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun listTemplates(): List<TemplateInfo> {
        return templateDir.listFiles()
            ?.filter { it.extension == "png" }
            ?.map {
                TemplateInfo(it.nameWithoutExtension, it.nameWithoutExtension, it)
            } ?: emptyList()
    }

    fun deleteTemplate(templateId: String) {
        getTemplateFile(templateId).delete()
    }

    fun getCurrentGame(): GameConfig? {
        val games = loadGames()
        return if (games.isNotEmpty()) games[0] else null
    }
}
