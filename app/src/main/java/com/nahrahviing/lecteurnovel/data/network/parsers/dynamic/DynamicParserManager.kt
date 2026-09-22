package com.nahrahviing.lecteurnovel.data.network.parsers.dynamic

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object DynamicParserManager {

    private val _parsersConfig = MutableStateFlow<List<DynamicParserConfig>>(emptyList())
    val parsersConfig: StateFlow<List<DynamicParserConfig>> = _parsersConfig.asStateFlow()

    private var cachedParsers = listOf<DynamicNovelParser>()

    fun init(context: Context) {
        val configs = mutableListOf<DynamicParserConfig>()

        // 1. Charger les parseurs intégrés de base depuis les assets
        try {
            val assetFiles = context.assets.list("parsers") ?: emptyArray()
            for (fileName in assetFiles) {
                if (fileName.endsWith(".json")) {
                    try {
                        val jsonStr = context.assets.open("parsers/$fileName").bufferedReader().use { it.readText() }
                        val config = DynamicParserConfig.fromJson(jsonStr, isCustom = false)
                        configs.add(config)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Charger les parseurs personnalisés ou modifiés sauvegardés localement
        try {
            val customDir = File(context.filesDir, "custom_parsers")
            if (customDir.exists()) {
                val files = customDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
                for (file in files) {
                    try {
                        val jsonStr = file.readText()
                        val customConfig = DynamicParserConfig.fromJson(jsonStr, isCustom = true)
                        // Remplacer si même ID, sinon ajouter
                        val existingIdx = configs.indexOfFirst { it.id == customConfig.id }
                        if (existingIdx >= 0) {
                            configs[existingIdx] = customConfig
                        } else {
                            configs.add(customConfig)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }

        updateParsersList(configs)
    }

    private fun updateParsersList(configs: List<DynamicParserConfig>) {
        _parsersConfig.value = configs
        cachedParsers = configs.map { DynamicNovelParser(it) }
    }

    fun getParsers(): List<DynamicNovelParser> = cachedParsers

    fun getParserForUrl(url: String): DynamicNovelParser? {
        return cachedParsers.firstOrNull { it.isNovelUrl(url) }
            ?: cachedParsers.firstOrNull { it.canParse(url) }
    }

    fun importParser(context: Context, jsonStr: String): Result<DynamicParserConfig> {
        return try {
            val config = DynamicParserConfig.fromJson(jsonStr, isCustom = true)
            if (config.baseUrl.isBlank() || config.name.isBlank()) {
                return Result.failure(IllegalArgumentException("Le parseur doit au moins définir un 'name' et une 'baseUrl'."))
            }

            val customDir = File(context.filesDir, "custom_parsers")
            if (!customDir.exists()) {
                customDir.mkdirs()
            }

            val file = File(customDir, "${config.id}.json")
            file.writeText(config.toJson())

            val current = _parsersConfig.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.id == config.id }
            if (existingIdx >= 0) {
                current[existingIdx] = config
            } else {
                current.add(config)
            }
            updateParsersList(current)

            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteCustomParser(context: Context, id: String): Boolean {
        val customDir = File(context.filesDir, "custom_parsers")
        val file = File(customDir, "$id.json")
        if (file.exists()) {
            file.delete()
        }

        // Recharger depuis les assets de base
        init(context)
        return true
    }

    fun resetToDefaults(context: Context) {
        val customDir = File(context.filesDir, "custom_parsers")
        if (customDir.exists()) {
            customDir.deleteRecursively()
        }
        init(context)
    }
}
