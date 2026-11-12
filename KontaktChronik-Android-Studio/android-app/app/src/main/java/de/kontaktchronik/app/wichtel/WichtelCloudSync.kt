package de.kontaktchronik.app.wichtel

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object WichtelCloudSync {
    const val PORTAL_BASE_URL = "https://wichteln.kirchnerapp.workers.dev"
    private const val API_PROXY_BASE = "https://wichteln.kirchnerapp.workers.dev/v1"
    private const val FALLBACK_API_BASE = "https://fra.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "6981eb5a00304f3ed7a3"
    private const val DB_ID = "remember_me_db"
    private const val COL_ID = "wichteln"

    private val random = SecureRandom()

    data class ParticipantSlotSetup(
        val localParticipantId: Long,
        val cloudPid: Int,
        val name: String,
        val directToken: String,
        val targetCloudPid: Int,
        val targetName: String,
        val wishText: String,
        val noGos: String,
        val address: String,
        val wishlistUrl: String,
        val extraWishUrls: List<String>
    )

    data class CloudParticipantStatus(
        val cloudPid: Int,
        val isOpened: Boolean,
        val hasPin: Boolean,
        val hasPush: Boolean = false,
        val hasWishlist: Boolean,
        val wishText: String,
        val noGos: String,
        val address: String,
        val wishlistUrl: String,
        val extraWishUrls: List<String>
    )

    data class CloudRoundLiveStatus(
        val cloudRoundId: String,
        val participantStatuses: Map<Int, CloudParticipantStatus>,
        val totalAnonMessages: Int,
        val unreadAnonMessages: Int
    )

    data class SmsInviteItem(
        val name: String,
        val phone: String,
        val cloudPid: Int,
        val directToken: String
    )

    fun generateRandomCode(len: Int = 10): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        return buildString(len) {
            repeat(len) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    fun sha256Bytes(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    fun sha256Hex(str: String): String {
        val digest = sha256Bytes(str.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sealWichtelBox(plainJson: String, roundId: String, pid: Int): String {
        val plain = plainJson.toByteArray(Charsets.UTF_8)
        val cipher = ByteArray(plain.size)
        var blockIdx = 0
        var offset = 0
        while (offset < plain.size) {
            val keySeed = "RM_WICHTEL_V1:$roundId:$pid:${blockIdx++}".toByteArray(Charsets.UTF_8)
            val keyBlock = sha256Bytes(keySeed)
            val chunk = minOf(32, plain.size - offset)
            for (j in 0 until chunk) {
                cipher[offset + j] = (plain[offset + j].toInt() xor keyBlock[j].toInt()).toByte()
            }
            offset += 32
        }
        return Base64.encodeToString(cipher, Base64.NO_WRAP)
    }

    fun buildGroupPortalUrl(cloudRoundId: String): String {
        return "$PORTAL_BASE_URL/?r=${URLEncoder.encode(cloudRoundId, "UTF-8")}"
    }

    fun buildDirectMemberPortalUrl(cloudRoundId: String, cloudPid: Int, directToken: String): String {
        return "$PORTAL_BASE_URL/?r=${URLEncoder.encode(cloudRoundId, "UTF-8")}&p=$cloudPid&k=${URLEncoder.encode(directToken, "UTF-8")}"
    }

    private fun httpRequest(method: String, path: String, jsonBody: String? = null): Pair<Int, String> {
        val bases = listOf(API_PROXY_BASE, FALLBACK_API_BASE)
        var lastException: Exception? = null
        for (base in bases) {
            try {
                val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 9000
                    readTimeout = 12000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-appwrite-project", PROJECT_ID)
                    if (jsonBody != null) {
                        doOutput = true
                    }
                }
                if (jsonBody != null) {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(jsonBody)
                        writer.flush()
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()
                return code to respText
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw (lastException ?: RuntimeException("Appwrite nicht erreichbar"))
    }

    private fun qEqual(attr: String, value: String): String {
        val q = JSONObject().apply {
            put("method", "equal")
            put("attribute", attr)
            put("values", JSONArray().put(value))
        }.toString()
        return URLEncoder.encode(q, "UTF-8")
    }

    private fun qLimit(limit: Int): String {
        val q = JSONObject().apply {
            put("method", "limit")
            put("values", JSONArray().put(limit))
        }.toString()
        return URLEncoder.encode(q, "UTF-8")
    }

    private fun upsertDocument(docId: String, roundId: String, docType: String, slotKey: String, payloadObj: JSONObject) {
        val dataObj = JSONObject().apply {
            put("roundId", roundId)
            put("docType", docType)
            put("slotKey", slotKey)
            put("payload", payloadObj.toString())
        }
        val createBody = JSONObject().apply {
            put("documentId", docId)
            put("data", dataObj)
        }.toString()
        val (code, _) = httpRequest("POST", "/databases/$DB_ID/collections/$COL_ID/documents", createBody)
        if (code == 409) {
            val updateBody = JSONObject().apply {
                put("data", dataObj)
            }.toString()
            httpRequest("PATCH", "/databases/$DB_ID/collections/$COL_ID/documents/$docId", updateBody)
        }
    }

    suspend fun publishRoundToCloud(
        oldCloudRoundId: String,
        newCloudRoundId: String,
        groupName: String,
        adminName: String,
        motto: String,
        budget: String,
        revealDateIso: String,
        forceRevealed: Boolean,
        slots: List<ParticipantSlotSetup>
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (oldCloudRoundId.isNotBlank() && oldCloudRoundId != newCloudRoundId) {
                deleteRoundFromCloudInternal(oldCloudRoundId)
            }

            // Zufällige anonyme Thread-IDs pro Paarung erzeugen (keine Rückschlüsse auf Personen möglich!)
            val outThreadByPid = slots.associate { it.cloudPid to "th_${generateRandomCode(14)}" }
            val inThreadByPid = mutableMapOf<Int, String>()
            for (s in slots) {
                val edgeThread = outThreadByPid[s.cloudPid].orEmpty()
                inThreadByPid[s.targetCloudPid] = edgeThread
            }

            // 1. ROUND Metadaten-Dokument
            val rosterArr = JSONArray()
            slots.forEach { s ->
                rosterArr.put(JSONObject().apply {
                    put("pid", s.cloudPid)
                    put("name", s.name)
                })
            }
            val roundPayload = JSONObject().apply {
                put("cloudRoundId", newCloudRoundId)
                put("groupName", groupName)
                put("adminName", adminName)
                put("motto", motto)
                put("budget", budget)
                put("revealDateIso", revealDateIso)
                put("forceRevealed", forceRevealed)
                put("createdAt", Instant.now().toString())
                put("roster", rosterArr)
            }
            upsertDocument("r_$newCloudRoundId", newCloudRoundId, "ROUND", "META", roundPayload)

            // 2. Einzelne Teilnehmer-Slots mit verschlüsselter Los-Box
            for (s in slots) {
                val secretBoxJson = JSONObject().apply {
                    put("targetPid", s.targetCloudPid)
                    put("targetName", s.targetName)
                    put("outThreadId", outThreadByPid[s.cloudPid].orEmpty())
                    put("inThreadId", inThreadByPid[s.cloudPid].orEmpty())
                }.toString()

                val sealedBox = sealWichtelBox(secretBoxJson, newCloudRoundId, s.cloudPid)
                val extraArr = JSONArray()
                s.extraWishUrls.filter { it.isNotBlank() }.forEach { extraArr.put(it) }

                val partPayload = JSONObject().apply {
                    put("pid", s.cloudPid)
                    put("name", s.name)
                    put("tokenHash", sha256Hex(s.directToken))
                    put("pinHash", "")
                    put("openedAt", "")
                    put("wishUpdatedAt", "")
                    put("wishText", s.wishText)
                    put("noGos", s.noGos)
                    put("address", s.address)
                    put("wishlistUrl", s.wishlistUrl)
                    put("extraWishUrls", extraArr)
                    put("sealedBox", sealedBox)
                }
                upsertDocument("p_${newCloudRoundId}_${s.cloudPid}", newCloudRoundId, "PART", "P_${s.cloudPid}", partPayload)
            }
            true
        }.getOrDefault(false)
    }

    private fun postPushEvent(payloadObj: JSONObject) {
        runCatching {
            val conn = (URL("$PORTAL_BASE_URL/api/push-event").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 9000
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payloadObj.toString())
                writer.flush()
            }
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun fetchEnabledPushPids(cloudRoundId: String): Set<Int> {
        return runCatching {
            val conn = (URL("$PORTAL_BASE_URL/api/push-status?r=${URLEncoder.encode(cloudRoundId, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
            }
            val code = conn.responseCode
            val text = if (code in 200..299) conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty() else ""
            conn.disconnect()
            if (text.isBlank()) emptySet()
            else {
                val arr = JSONObject(text).optJSONArray("enabledPids") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i -> arr.optInt(i, 0).takeIf { it > 0 } }.toSet()
            }
        }.getOrDefault(emptySet())
    }

    suspend fun fetchRoundLiveStatus(cloudRoundId: String): CloudRoundLiveStatus? = withContext(Dispatchers.IO) {
        if (cloudRoundId.isBlank()) return@withContext null
        runCatching {
            val enabledPushPids = fetchEnabledPushPids(cloudRoundId)
            val path = "/databases/$DB_ID/collections/$COL_ID/documents?queries[]=${qEqual("roundId", cloudRoundId)}&queries[]=${qLimit(250)}"
            val (code, body) = httpRequest("GET", path)
            if (code !in 200..299) return@runCatching null
            val root = JSONObject(body)
            val docs = root.optJSONArray("documents") ?: JSONArray()
            val partMap = mutableMapOf<Int, CloudParticipantStatus>()
            var totalMsgs = 0
            var unreadMsgs = 0

            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val type = doc.optString("docType")
                val payloadStr = doc.optString("payload")
                if (payloadStr.isBlank()) continue
                val pObj = runCatching { JSONObject(payloadStr) }.getOrNull() ?: continue

                if (type == "PART") {
                    val pid = pObj.optInt("pid", 0)
                    if (pid > 0) {
                        val wish = pObj.optString("wishText", "")
                        val noGos = pObj.optString("noGos", "")
                        val addr = pObj.optString("address", "")
                        val url = pObj.optString("wishlistUrl", "")
                        val extraArr = pObj.optJSONArray("extraWishUrls") ?: JSONArray()
                        val extras = (0 until extraArr.length()).mapNotNull { k ->
                            extraArr.optString(k).takeIf { it.isNotBlank() }
                        }
                        partMap[pid] = CloudParticipantStatus(
                            cloudPid = pid,
                            isOpened = pObj.optString("openedAt", "").isNotBlank(),
                            hasPin = pObj.optString("pinHash", "").isNotBlank(),
                            hasPush = enabledPushPids.contains(pid),
                            hasWishlist = wish.isNotBlank() || url.isNotBlank() || extras.isNotEmpty(),
                            wishText = wish,
                            noGos = noGos,
                            address = addr,
                            wishlistUrl = url,
                            extraWishUrls = extras
                        )
                    }
                } else if (type == "MSG") {
                    totalMsgs++
                    val fromRole = pObj.optString("fromRole", "")
                    val unread = if (fromRole == "SANTA") !pObj.optBoolean("readByTarget", false)
                    else !pObj.optBoolean("readBySanta", false)
                    if (unread) unreadMsgs++
                }
            }

            CloudRoundLiveStatus(
                cloudRoundId = cloudRoundId,
                participantStatuses = partMap,
                totalAnonMessages = totalMsgs,
                unreadAnonMessages = unreadMsgs
            )
        }.getOrNull()
    }

    suspend fun resetParticipantPin(cloudRoundId: String, cloudPid: Int): Boolean = withContext(Dispatchers.IO) {
        if (cloudRoundId.isBlank() || cloudPid <= 0) return@withContext false
        runCatching {
            val docId = "p_${cloudRoundId}_$cloudPid"
            val (code, body) = httpRequest("GET", "/databases/$DB_ID/collections/$COL_ID/documents/$docId")
            if (code !in 200..299) return@runCatching false
            val doc = JSONObject(body)
            val payloadObj = JSONObject(doc.optString("payload", "{}"))
            payloadObj.put("pinHash", "")
            upsertDocument(docId, cloudRoundId, "PART", "P_$cloudPid", payloadObj)
            true
        }.getOrDefault(false)
    }

    suspend fun updateParticipantWishesInCloud(
        cloudRoundId: String,
        cloudPid: Int,
        name: String,
        wishText: String,
        noGos: String,
        address: String,
        wishlistUrl: String,
        extraWishUrls: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (cloudRoundId.isBlank() || cloudPid <= 0) return@withContext false
        runCatching {
            val docId = "p_${cloudRoundId}_$cloudPid"
            val (code, body) = httpRequest("GET", "/databases/$DB_ID/collections/$COL_ID/documents/$docId")
            if (code !in 200..299) return@runCatching false
            val doc = JSONObject(body)
            val payloadObj = JSONObject(doc.optString("payload", "{}"))
            val extraArr = JSONArray()
            extraWishUrls.filter { it.isNotBlank() }.forEach { extraArr.put(it) }
            payloadObj.put("name", name)
            payloadObj.put("wishText", wishText)
            payloadObj.put("noGos", noGos)
            payloadObj.put("address", address)
            payloadObj.put("wishlistUrl", wishlistUrl)
            payloadObj.put("extraWishUrls", extraArr)
            payloadObj.put("wishUpdatedAt", Instant.now().toString())
            upsertDocument(docId, cloudRoundId, "PART", "P_$cloudPid", payloadObj)
            postPushEvent(
                JSONObject().apply {
                    put("roundId", cloudRoundId)
                    put("eventType", "WISH_UPDATED")
                    put("fromPid", cloudPid)
                }
            )
            true
        }.getOrDefault(false)
    }

    suspend fun updateRoundMetaInCloud(
        cloudRoundId: String,
        groupName: String,
        adminName: String,
        motto: String,
        budget: String,
        revealDateIso: String,
        forceRevealed: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (cloudRoundId.isBlank()) return@withContext false
        runCatching {
            val docId = "r_$cloudRoundId"
            val (code, body) = httpRequest("GET", "/databases/$DB_ID/collections/$COL_ID/documents/$docId")
            if (code !in 200..299) return@runCatching false
            val doc = JSONObject(body)
            val payloadObj = JSONObject(doc.optString("payload", "{}"))
            payloadObj.put("groupName", groupName)
            payloadObj.put("adminName", adminName)
            payloadObj.put("motto", motto)
            payloadObj.put("budget", budget)
            payloadObj.put("revealDateIso", revealDateIso)
            payloadObj.put("forceRevealed", forceRevealed)
            upsertDocument(docId, cloudRoundId, "ROUND", "META", payloadObj)
            true
        }.getOrDefault(false)
    }

    suspend fun updateForceRevealInCloud(cloudRoundId: String, forceRevealed: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (cloudRoundId.isBlank()) return@withContext false
        runCatching {
            val docId = "r_$cloudRoundId"
            val (code, body) = httpRequest("GET", "/databases/$DB_ID/collections/$COL_ID/documents/$docId")
            if (code !in 200..299) return@runCatching false
            val doc = JSONObject(body)
            val payloadObj = JSONObject(doc.optString("payload", "{}"))
            payloadObj.put("forceRevealed", forceRevealed)
            upsertDocument(docId, cloudRoundId, "ROUND", "META", payloadObj)
            if (forceRevealed) {
                postPushEvent(
                    JSONObject().apply {
                        put("roundId", cloudRoundId)
                        put("eventType", "ROUND_REVEALED")
                    }
                )
            }
            true
        }.getOrDefault(false)
    }

    private fun deleteRoundFromCloudInternal(cloudRoundId: String) {
        for (rid in listOf(cloudRoundId, "push_$cloudRoundId")) {
            val path = "/databases/$DB_ID/collections/$COL_ID/documents?queries[]=${qEqual("roundId", rid)}&queries[]=${qLimit(250)}"
            val (code, body) = httpRequest("GET", path)
            if (code !in 200..299) continue
            val docs = JSONObject(body).optJSONArray("documents") ?: JSONArray()
            for (i in 0 until docs.length()) {
                val docId = docs.optJSONObject(i)?.optString("\$id").orEmpty()
                if (docId.isNotBlank()) {
                    runCatching { httpRequest("DELETE", "/databases/$DB_ID/collections/$COL_ID/documents/$docId") }
                }
            }
        }
    }

    suspend fun deleteRoundFromCloud(cloudRoundId: String) = withContext(Dispatchers.IO) {
        if (cloudRoundId.isNotBlank()) {
            runCatching { deleteRoundFromCloudInternal(cloudRoundId) }
        }
    }

    @Suppress("DEPRECATION")
    fun sendAutomaticBulkSmsInvites(
        context: Context,
        groupName: String,
        cloudRoundId: String,
        invites: List<SmsInviteItem>
    ): Pair<Int, Int> {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
        var sent = 0
        var skipped = 0
        for (item in invites) {
            val cleanedPhone = item.phone.trim().replace(Regex("[^0-9+]"), "")
            if (cleanedPhone.length < 6 || item.cloudPid <= 0 || item.directToken.isBlank()) {
                skipped++
                continue
            }
            val link = buildDirectMemberPortalUrl(cloudRoundId, item.cloudPid, item.directToken)
            val firstName = item.name.trim().substringBefore(" ")
            val text = "🎄 Hallo $firstName! Dein geheimer Wichtel-Zugang (ohne App, inkl. Wunschzettel & anonymen Fragen) fuer \"$groupName\":\n$link"
            runCatching {
                val parts = smsManager.divideMessage(text)
                smsManager.sendMultipartTextMessage(cleanedPhone, null, parts, null, null)
                sent++
            }.onFailure {
                skipped++
            }
        }
        return sent to skipped
    }
}
