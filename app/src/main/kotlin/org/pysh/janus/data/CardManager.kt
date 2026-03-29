package org.pysh.janus.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.pysh.janus.util.RootUtils
import java.io.File
import java.util.zip.ZipFile

class CardManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "janus_config"
        private const val KEY_CARDS = "cards_config"
        private const val KEY_MASTER_ENABLED = "cards_master_enabled"
        private const val MAX_SLOTS = 20
        private const val CARDS_DIR = "cards"

        private const val CONFIG_DIR =
            "/data/system/theme_magic/users/0/subscreencenter/config"
        const val CARDS_CONFIG_FLAG_PATH = "$CONFIG_DIR/janus_cards_config"

        private const val CARDS_DEPLOY_DIR = "$CONFIG_DIR/cards"
        private const val DEPLOY_BASE =
            "/data/system/theme_magic/users/0/subscreencenter/smart_assistant"
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
        val updated = reordered.mapIndexed { index, card ->
            card.copy(sortOrder = index)
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
        RootUtils.exec("mkdir -p $CARDS_DEPLOY_DIR")
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

    // ── Slot Management ─────────────────────────────────────────

    fun getNextAvailableSlot(): Int? {
        val usedSlots = getCards().map { it.slot }.toSet()
        return (0 until MAX_SLOTS).firstOrNull { it !in usedSlots }
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
