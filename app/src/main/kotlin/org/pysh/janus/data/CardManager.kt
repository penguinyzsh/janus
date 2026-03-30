package org.pysh.janus.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.pysh.janus.util.JanusPaths
import org.pysh.janus.util.RootUtils
import java.io.File
import java.util.zip.ZipFile

class CardManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "janus_config"
        private const val KEY_CARDS = "cards_config"
        private const val KEY_MASTER_ENABLED = "cards_master_enabled"
        private const val KEY_MUSIC_LYRIC_PATCH = "music_lyric_patch"
        private const val MAX_SLOTS = 20
        private const val CARDS_DIR = "cards"

        val CARDS_CONFIG_FLAG_PATH = JanusPaths.CARDS_CONFIG
        private val CARDS_DEPLOY_DIR = JanusPaths.CARDS_DIR
        private val DEPLOY_BASE = JanusPaths.TEMPLATES_DIR
        private val MUSIC_LYRIC_PATCH_FLAG = "${JanusPaths.CONFIG_DIR}/music_lyric_patch"
    }

    private val prefs: SharedPreferences = try {
        @Suppress("DEPRECATION")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (_: SecurityException) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val cardsDir: File
        get() = File(context.filesDir, CARDS_DIR).also { dir ->
            dir.mkdirs()
            // Make cards directory traversable for Hook-side reading
            // (subscreencenter process needs to read card ZIPs)
            dir.setReadable(true, false)
            dir.setExecutable(true, false)
            context.filesDir.setReadable(true, false)
            context.filesDir.setExecutable(true, false)
        }

    // ── Card CRUD ───────────────────────────────────────────────

    fun getCards(): List<CardInfo> {
        val json = prefs.getString(KEY_CARDS, "") ?: ""
        return CardInfo.listFromJson(json)
    }

    fun addCard(uri: Uri): CardInfo? {
        val slot = getNextAvailableSlot() ?: return null
        val tmpFile = File(context.cacheDir, "import_tmp.zip")
        try {
            // Copy URI to temp file
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            // Validate ZIP contains manifest.xml
            val name = try {
                ZipFile(tmpFile).use { zip ->
                    val entry = zip.getEntry("manifest.xml") ?: return null
                    parseCardName(zip.getInputStream(entry).bufferedReader().readText())
                }
            } catch (_: Exception) {
                return null
            }

            // Move to permanent location
            val destFile = File(cardsDir, "$slot.zip")
            tmpFile.copyTo(destFile, overwrite = true)
            destFile.setReadable(true, false) // World-readable for Hook-side access
            tmpFile.delete()

            // Extract original filename from URI
            val fileName = getFileNameFromUri(uri) ?: "card_$slot.zip"

            val card = CardInfo(
                slot = slot,
                name = name ?: fileName.removeSuffix(".zip").removeSuffix(".mrc"),
                fileName = fileName,
                enabled = true,
                sortOrder = getCards().maxOfOrNull { it.sortOrder + 1 } ?: 1,
            )

            val cards = getCards().toMutableList()
            cards.add(card)
            saveCards(cards)
            return card
        } catch (_: Exception) {
            tmpFile.delete()
            return null
        }
    }

    fun removeCard(slot: Int) {
        val cards = getCards().filter { it.slot != slot }
        saveCards(cards)
        File(cardsDir, "$slot.zip").delete()
        undeployCard(slot)
        syncConfig()
    }

    fun updateCard(card: CardInfo) {
        val cards = getCards().map { if (it.slot == card.slot) card else it }
        saveCards(cards)
    }

    fun reorderCards(reordered: List<CardInfo>) {
        // Redistribute existing priorities: top position = highest priority
        val priorities = reordered.map { it.priority }.sortedDescending().toMutableList()
        for (i in 1 until priorities.size) {
            if (priorities[i] >= priorities[i - 1]) {
                priorities[i] = (priorities[i - 1] - 1).coerceAtLeast(1)
            }
        }
        val updated = reordered.mapIndexed { index, card ->
            card.copy(sortOrder = index, priority = priorities[index])
        }
        saveCards(updated)
    }

    // ── Master Toggle ───────────────────────────────────────────

    fun isMasterEnabled(): Boolean = prefs.getBoolean(KEY_MASTER_ENABLED, false)

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).commit()
        makePrefsWorldReadable()
    }

    // ── Sync & Deploy ───────────────────────────────────────────

    /** Write the full card config as a JSON flag file for the Hook side. */
    fun syncConfig() {
        JanusPaths.ensureAllDirs()
        val config = JSONObject().apply {
            put("master_enabled", isMasterEnabled())
            val arr = JSONArray()
            getCards().filter { it.enabled }.forEach { card ->
                arr.put(JSONObject().apply {
                    put("slot", card.slot)
                    put("business", card.businessName)
                    put("refresh", card.refreshInterval)
                    put("priority", card.priority)
                })
            }
            put("cards", arr)
        }

        val tmp = File(context.cacheDir, "cards_cfg_tmp.json")
        tmp.writeText(config.toString())
        RootUtils.exec(
            "cp ${tmp.absolutePath} $CARDS_CONFIG_FLAG_PATH && chmod 644 $CARDS_CONFIG_FLAG_PATH && chcon u:object_r:theme_data_file:s0 $CARDS_CONFIG_FLAG_PATH"
        )
        tmp.delete()
    }

    /** Deploy a card's ZIP template to the system smart_assistant path.
     *  The template path in p2.a.d points directly to the ZIP file,
     *  NOT a directory containing it. */
    fun deployCard(card: CardInfo) {
        val zipFile = File(cardsDir, "${card.slot}.zip")
        if (!zipFile.exists()) {
            android.util.Log.e("Janus-CardMgr", "deployCard: ${zipFile.absolutePath} not found")
            return
        }
        JanusPaths.ensureAllDirs()
        val tmp = File(context.cacheDir, "card_deploy_${card.slot}.zip")
        zipFile.copyTo(tmp, overwrite = true)
        android.util.Log.d("Janus-CardMgr", "deployCard: copied ${zipFile.absolutePath} (${zipFile.length()}) to ${tmp.absolutePath} (${tmp.length()})")
        val dest = "$DEPLOY_BASE/${card.businessName}"
        val cmd = "rm -rf $dest && cp ${tmp.absolutePath} $dest && chmod 644 $dest && chcon u:object_r:theme_data_file:s0 $dest"
        val result = RootUtils.exec(cmd)
        android.util.Log.d("Janus-CardMgr", "deployCard: exec result=$result dest=$dest")
        tmp.delete()
    }

    /** Remove a card's deployed template from the system path. */
    fun undeployCard(slot: Int) {
        val business = if (slot == 0) "weather" else "janus_card_$slot"
        RootUtils.exec("rm -f $DEPLOY_BASE/$business")
    }

    /** Deploy card ZIPs to theme_magic config/cards/ so Hook can read them.
     *  subscreencenter can't read Janus app-private dir (SELinux MCS). */
    fun prepareCardsForHook() {
        JanusPaths.ensureAllDirs()
        getCards().filter { it.enabled }.forEach { card ->
            val src = File(cardsDir, "${card.slot}.zip")
            if (!src.exists()) return@forEach
            val tmp = File(context.cacheDir, "card_hook_${card.slot}.zip")
            src.copyTo(tmp, overwrite = true)
            val dest = "$CARDS_DEPLOY_DIR/${card.slot}.zip"
            RootUtils.exec(
                "cp ${tmp.absolutePath} $dest && chmod 644 $dest && chcon u:object_r:theme_data_file:s0 $dest"
            )
            tmp.delete()
        }
    }

    // ── System Card Override (generic) ────────────────────────────

    fun importSystemCardOverride(card: SystemCard, uri: Uri): String? {
        val tmpFile = File(context.cacheDir, "${card.business}_import_tmp.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val name = try {
                ZipFile(tmpFile).use { zip ->
                    val entry = zip.getEntry("manifest.xml") ?: return null
                    parseCardName(zip.getInputStream(entry).bufferedReader().readText())
                }
            } catch (_: Exception) { return null }

            val localFile = File(cardsDir, card.customFileName)
            tmpFile.copyTo(localFile, overwrite = true)
            localFile.setReadable(true, false)
            tmpFile.delete()

            JanusPaths.ensureAllDirs()
            val deployDest = "${JanusPaths.CARDS_DIR}/${card.customFileName}"
            val tmp = File(context.cacheDir, "${card.business}_deploy_tmp.zip")
            localFile.copyTo(tmp, overwrite = true)
            RootUtils.exec(
                "cp ${tmp.absolutePath} $deployDest && chmod 644 $deployDest && chcon u:object_r:theme_data_file:s0 $deployDest"
            )
            tmp.delete()

            val displayName = name ?: getFileNameFromUri(uri)?.removeSuffix(".zip") ?: "Custom"
            prefs.edit().putString(card.overridePrefsKey, displayName).commit()
            makePrefsWorldReadable()
            return displayName
        } catch (_: Exception) {
            tmpFile.delete()
            return null
        }
    }

    fun removeSystemCardOverride(card: SystemCard) {
        File(cardsDir, card.customFileName).delete()
        RootUtils.exec("rm -f '${JanusPaths.CARDS_DIR}/${card.customFileName}'")
        RootUtils.exec("rm -f '${JanusPaths.TEMPLATES_DIR}/${card.customTemplateName}'")
        prefs.edit().remove(card.overridePrefsKey).commit()
        makePrefsWorldReadable()
    }

    fun getSystemCardOverrideName(card: SystemCard): String? =
        prefs.getString(card.overridePrefsKey, null)

    /** Deploy all system card override ZIPs to theme_magic cards/ so Hook can read them. */
    fun prepareSystemCardOverridesForHook() {
        JanusPaths.ensureAllDirs()
        for (card in SystemCard.entries) {
            val src = File(cardsDir, card.customFileName)
            if (!src.exists()) continue
            val tmp = File(context.cacheDir, "${card.business}_hook_tmp.zip")
            src.copyTo(tmp, overwrite = true)
            val dest = "${JanusPaths.CARDS_DIR}/${card.customFileName}"
            RootUtils.exec(
                "cp ${tmp.absolutePath} $dest && chmod 644 $dest && chcon u:object_r:theme_data_file:s0 $dest"
            )
            tmp.delete()
        }
    }

    // ── Music Override (wrappers + lyric-specific) ──────────────────

    fun importMusicOverride(uri: Uri): String? = importSystemCardOverride(SystemCard.MUSIC, uri)
    fun removeMusicOverride() = removeSystemCardOverride(SystemCard.MUSIC)
    fun getMusicOverrideName(): String? = getSystemCardOverrideName(SystemCard.MUSIC)

    fun isMusicLyricPatch(): Boolean = prefs.getBoolean(KEY_MUSIC_LYRIC_PATCH, true)

    fun setMusicLyricPatch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC_LYRIC_PATCH, enabled).commit()
        makePrefsWorldReadable()
        JanusPaths.ensureAllDirs()
        if (enabled) {
            RootUtils.exec("touch '$MUSIC_LYRIC_PATCH_FLAG' && chmod 644 '$MUSIC_LYRIC_PATCH_FLAG' && chcon u:object_r:theme_data_file:s0 '$MUSIC_LYRIC_PATCH_FLAG'")
        } else {
            RootUtils.exec("rm -f '$MUSIC_LYRIC_PATCH_FLAG'")
        }
    }

    // ── Slot Management ─────────────────────────────────────────

    fun getNextAvailableSlot(): Int? {
        val usedSlots = getCards().map { it.slot }.toSet()
        return (0 until MAX_SLOTS).firstOrNull { it !in usedSlots }
    }

    // ── Observe ──────────────────────────────────────────────────

    /** Watch for card config changes. Returns an unregister function. */
    fun observeCards(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CARDS) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // ── Internal ────────────────────────────────────────────────

    private fun saveCards(cards: List<CardInfo>) {
        prefs.edit().putString(KEY_CARDS, CardInfo.listToJson(cards)).commit()
        makePrefsWorldReadable()
    }

    private fun parseCardName(manifestXml: String): String? {
        // Simple regex to extract name from <Widget name="..."> or <Wallpaper name="...">
        val nameRegex = Regex("""<(?:Widget|Wallpaper)\s[^>]*?name\s*=\s*"([^"]+)"""")
        return nameRegex.find(manifestXml)?.groupValues?.get(1)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex("_display_name")
                if (nameIndex >= 0) return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun makePrefsWorldReadable() {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(prefsDir, "$PREFS_NAME.xml")
        if (prefsFile.exists()) {
            prefsFile.setReadable(true, false)
        }
        prefsDir.setExecutable(true, false)
    }
}
