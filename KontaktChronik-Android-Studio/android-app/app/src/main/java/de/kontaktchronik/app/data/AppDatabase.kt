package de.kontaktchronik.app.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.provider.ContactsContract
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import kotlin.math.abs
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.kontaktchronik.app.model.DirectMessageEntity
import de.kontaktchronik.app.model.PersonEntity
import de.kontaktchronik.app.model.RelationEntity
import de.kontaktchronik.app.model.SpecialEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KontaktChronikDao {
    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun observeAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY name ASC")
    suspend fun getAllPersonsOnce(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("SELECT * FROM relations")
    fun observeAllRelations(): Flow<List<RelationEntity>>

    @Query("SELECT * FROM relations")
    suspend fun getAllRelationsOnce(): List<RelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: RelationEntity): Long

    @Delete
    suspend fun deleteRelation(relation: RelationEntity)

    @Query("SELECT * FROM special_events ORDER BY dateIso ASC")
    fun observeAllSpecialEvents(): Flow<List<SpecialEventEntity>>

    @Query("SELECT * FROM special_events ORDER BY dateIso ASC")
    suspend fun getAllSpecialEventsOnce(): List<SpecialEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecialEvent(event: SpecialEventEntity): Long

    @Delete
    suspend fun deleteSpecialEvent(event: SpecialEventEntity)

    // App-zu-App Direktnachrichten & geteilte Sondertermine
    @Query("SELECT * FROM direct_messages ORDER BY id ASC")
    fun observeAllDirectMessages(): Flow<List<DirectMessageEntity>>

    @Query("SELECT * FROM direct_messages WHERE personId = :personId ORDER BY id ASC")
    fun observeMessagesForPerson(personId: Long): Flow<List<DirectMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectMessage(msg: DirectMessageEntity): Long

    @Query("UPDATE direct_messages SET isRead = 1 WHERE personId = :personId")
    suspend fun markMessagesReadForPerson(personId: Long)

    @Query("UPDATE direct_messages SET personId = :targetPersonId WHERE personId = :oldPersonId")
    suspend fun reassignMessagesForPerson(oldPersonId: Long, targetPersonId: Long)
}

@Database(
    entities = [
        PersonEntity::class,
        RelationEntity::class,
        SpecialEventEntity::class,
        DirectMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): KontaktChronikDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN photoBase64 TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kontakt_chronik.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Durchsucht die Android-Telefonkontakte (ContactsContract) nach Geburtstagen,
 * Telefonnummern, E-Mail-Adressen und Kontaktfotos und synchronisiert sie in die lokale Datenbank.
 */
class ContactsSyncRepository(private val dao: KontaktChronikDao) {

    private fun bitmapToBase64Jpeg(bmp: Bitmap, maxDim: Int = 256): String {
        val w = bmp.width.coerceAtLeast(1)
        val h = bmp.height.coerceAtLeast(1)
        val scale = (maxDim.toFloat() / maxOf(w, h).toFloat()).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bmp
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun readContactPhotoAsBase64(contentResolver: ContentResolver, contactId: String): String {
        return try {
            val idLong = contactId.toLongOrNull() ?: return ""
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, idLong)
            ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, contactUri, true)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    return bitmapToBase64Jpeg(bmp)
                }
            }
            // Fallback: Direkt aus CommonDataKinds.Photo.PHOTO lesen
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val blob = c.getBlob(0)
                    if (blob != null && blob.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                        if (bmp != null) {
                            return bitmapToBase64Jpeg(bmp)
                        }
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun decodePhotoBase64ToBytes(photoBase64: String): ByteArray? {
        if (photoBase64.isBlank()) return null
        return try {
            val clean = photoBase64.substringAfter("base64,", photoBase64).trim()
            val rawBytes = Base64.decode(clean, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bmp != null) {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            } else {
                rawBytes
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateDefaultPortraitBase64(name: String): String {
        return try {
            val n = name.lowercase()
            var bg1 = 0xFF4A90E2.toInt()
            var bg2 = 0xFF2B5B84.toInt()
            var skin = 0xFFF6D0B8.toInt()
            var hair = 0xFF4A5568.toInt()
            var shirt = 0xFF2E7D5B.toInt()
            var style = "woman"
            var glasses = false
            var beard = false

            when {
                n.contains("helga") || n.contains("oma") -> {
                    bg1 = 0xFF5B9BD5.toInt(); bg2 = 0xFF2E5B88.toInt()
                    skin = 0xFFF7D5BE.toInt(); hair = 0xFFD9D9E0.toInt(); shirt = 0xFF4A6FA5.toInt()
                    style = "oma"; glasses = true
                }
                n.contains("anna") -> {
                    bg1 = 0xFF38BDF8.toInt(); bg2 = 0xFF0284C7.toInt()
                    skin = 0xFFF8D6C0.toInt(); hair = 0xFF4A5568.toInt(); shirt = 0xFF2E7D5B.toInt()
                    style = "woman"
                }
                n.contains("sabine") -> {
                    bg1 = 0xFF6366F1.toInt(); bg2 = 0xFF3730A3.toInt()
                    skin = 0xFFF7D4BC.toInt(); hair = 0xFF64748B.toInt(); shirt = 0xFF3B82F6.toInt()
                    style = "woman"
                }
                n.contains("thomas") -> {
                    bg1 = 0xFF2E7D5B.toInt(); bg2 = 0xFF164A33.toInt()
                    skin = 0xFFF0C5A8.toInt(); hair = 0xFF334155.toInt(); shirt = 0xFF2B4C7E.toInt()
                    style = "man"
                }
                n.contains("markus") -> {
                    bg1 = 0xFF3B82F6.toInt(); bg2 = 0xFF1E3A8A.toInt()
                    skin = 0xFFF2C8AD.toInt(); hair = 0xFF475569.toInt(); shirt = 0xFF0284C7.toInt()
                    style = "man"
                }
                n.contains("max") -> {
                    bg1 = 0xFF0EA5E9.toInt(); bg2 = 0xFF0C4A6E.toInt()
                    skin = 0xFFEFC2A5.toInt(); hair = 0xFF1E293B.toInt(); shirt = 0xFF374151.toInt()
                    style = "man"; beard = true
                }
                n.contains("leo") || n.contains("baby") -> {
                    bg1 = 0xFF60A5FA.toInt(); bg2 = 0xFF2563EB.toInt()
                    skin = 0xFFFBE0CD.toInt(); hair = 0xFF94A3B8.toInt(); shirt = 0xFF38BDF8.toInt()
                    style = "baby"
                }
                n.contains("praxis") || n.contains("dr.") -> {
                    bg1 = 0xFF14B8A6.toInt(); bg2 = 0xFF0F766E.toInt()
                    skin = 0xFFF3CBB2.toInt(); hair = 0xFF9CA3AF.toInt(); shirt = 0xFFFFFFFF.toInt()
                    style = "man"; glasses = true
                }
                else -> {
                    val h = abs(n.fold(0) { acc, ch -> ch.code + ((acc shl 5) - acc) })
                    val bgPairs = listOf(
                        0xFF4A90E2.toInt() to 0xFF2563EB.toInt(),
                        0xFF38BDF8.toInt() to 0xFF0284C7.toInt(),
                        0xFF6366F1.toInt() to 0xFF4338CA.toInt(),
                        0xFF14B8A6.toInt() to 0xFF0D9488.toInt()
                    )
                    val pair = bgPairs[h % bgPairs.size]
                    bg1 = pair.first; bg2 = pair.second
                    val hairs = listOf(0xFF334155.toInt(), 0xFF475569.toInt(), 0xFF1E293B.toInt(), 0xFF64748B.toInt())
                    hair = hairs[h % hairs.size]
                    val shirts = listOf(0xFF2B4C7E.toInt(), 0xFF0284C7.toInt(), 0xFF2E7D5B.toInt(), 0xFF4F46E5.toInt())
                    shirt = shirts[(h shr 2) % shirts.size]
                    style = if (h % 2 == 0) "woman" else "man"
                }
            }

            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Hintergrund-Verlauf
            paint.shader = LinearGradient(0f, 0f, 128f, 128f, bg1, bg2, Shader.TileMode.CLAMP)
            canvas.drawOval(RectF(0f, 0f, 128f, 128f), paint)
            paint.shader = null

            // Haare hinten (Damen / Oma-Dutt)
            paint.color = hair
            paint.style = Paint.Style.FILL
            if (style == "woman") {
                canvas.drawRoundRect(RectF(30f, 44f, 98f, 112f), 26f, 26f, paint)
            } else if (style == "oma") {
                canvas.drawCircle(64f, 26f, 14f, paint)
            }

            // Schultern / Oberteil
            paint.color = shirt
            canvas.drawOval(RectF(20f, 92f, 108f, 148f), paint)

            // Hals & Kopf
            paint.color = skin
            canvas.drawRoundRect(RectF(54f, 76f, 74f, 96f), 8f, 8f, paint)
            val headRx = if (style == "baby") 24f else 26f
            val headRy = if (style == "baby") 24f else 28f
            canvas.drawOval(RectF(64f - headRx, 60f - headRy, 64f + headRx, 60f + headRy), paint)

            // Haare oben
            paint.color = hair
            if (style == "baby") {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4.5f
                paint.strokeCap = Paint.Cap.ROUND
                val curl = Path().apply {
                    moveTo(58f, 30f)
                    quadTo(66f, 18f, 70f, 28f)
                }
                canvas.drawPath(curl, paint)
                paint.style = Paint.Style.FILL
            } else {
                val topHair = Path().apply {
                    moveTo(36f, 54f)
                    cubicTo(36f, 26f, 92f, 26f, 92f, 54f)
                    cubicTo(80f, 40f, 48f, 40f, 36f, 54f)
                    close()
                }
                canvas.drawPath(topHair, paint)
            }

            // Augen
            paint.color = 0xFF2C201B.toInt()
            canvas.drawCircle(52f, 60f, 3f, paint)
            canvas.drawCircle(76f, 60f, 3f, paint)

            // Optional: Brille
            if (glasses) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.4f
                paint.color = 0xFF374151.toInt()
                canvas.drawCircle(52f, 60f, 8f, paint)
                canvas.drawCircle(76f, 60f, 8f, paint)
                canvas.drawLine(60f, 60f, 68f, 60f, paint)
                paint.style = Paint.Style.FILL
            }

            // Optional: Bart
            if (beard) {
                paint.color = hair
                paint.alpha = 215
                val beardPath = Path().apply {
                    moveTo(40f, 66f)
                    cubicTo(42f, 88f, 86f, 88f, 88f, 66f)
                    cubicTo(80f, 76f, 48f, 76f, 40f, 66f)
                    close()
                }
                canvas.drawPath(beardPath, paint)
                paint.alpha = 255
            }

            // Lächeln
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.8f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = 0xFFA84A3A.toInt()
            val smile = Path().apply {
                moveTo(55f, 72f)
                quadTo(64f, 79f, 73f, 72f)
            }
            canvas.drawPath(smile, paint)

            bitmapToBase64Jpeg(bmp, 128)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun ensureDefaultPhotosForAllPersons() {
        val all = dao.getAllPersonsOnce()
        for (p in all) {
            if (p.photoBase64.isBlank()) {
                val generated = generateDefaultPortraitBase64(p.name)
                if (generated.isNotBlank()) {
                    dao.updatePerson(p.copy(photoBase64 = generated))
                }
            }
        }
    }

    suspend fun syncDeviceContactsWithBirthdays(
        contentResolver: ContentResolver,
        forcePhotoRefresh: Boolean = false,
        ignoredDeviceContactIds: Set<String> = emptySet()
    ): Int {
        val allPersonsInDb = dao.getAllPersonsOnce()
        val existingByCid = allPersonsInDb
            .filter { !it.importedFromDeviceContactId.isNullOrBlank() }
            .associateBy { it.importedFromDeviceContactId }
        val claimedManualIds = mutableSetOf<Long>()

        val allContactIds = linkedSetOf<String>()
        val rawDisplayByCid = mutableMapOf<String, String>()
        val structuredFormattedByCid = mutableMapOf<String, String>()
        val companyByCid = mutableMapOf<String, String>()
        val birthdaysByContactId = mutableMapOf<String, String>()
        val phonesByContactId = mutableMapOf<String, String>()
        val emailsByContactId = mutableMapOf<String, String>()

        // 1. Alle Kontakte aus dem Telefonbuch lesen (auch ohne Geburtstag/Datum oder ohne Anzeigename)
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val primIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val dispIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                allContactIds.add(cid)
                val name = cursor.getString(primIdx)?.trim().orEmpty()
                    .ifBlank { cursor.getString(dispIdx)?.trim().orEmpty() }
                if (name.isNotBlank()) {
                    rawDisplayByCid[cid] = name
                }
            }
        }

        // 1b. Echte Vor- und Nachnamen aus StructuredName lesen (für exakte Nachnamen-Sortierung)
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val dispIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
            val givenIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
            val midIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME)
            val famIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                allContactIds.add(cid)
                val disp = cursor.getString(dispIdx)?.trim().orEmpty()
                val given = cursor.getString(givenIdx)?.trim().orEmpty()
                val mid = cursor.getString(midIdx)?.trim().orEmpty()
                val fam = cursor.getString(famIdx)?.trim().orEmpty()
                if (disp.isNotBlank() && !rawDisplayByCid.containsKey(cid)) {
                    rawDisplayByCid[cid] = disp
                }
                if (!structuredFormattedByCid.containsKey(cid)) {
                    val firstCombined = listOf(given, mid).filter { it.isNotBlank() }.joinToString(" ").trim()
                    if (fam.isNotBlank() && firstCombined.isNotBlank() && !fam.equals(firstCombined, ignoreCase = true)) {
                        structuredFormattedByCid[cid] = "$fam, $firstCombined"
                    } else if (fam.isNotBlank() && firstCombined.isBlank()) {
                        structuredFormattedByCid[cid] = fam
                    }
                }
            }
        }

        // 1c. Firmennamen als Fallback lesen
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Organization.COMPANY
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val compIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                val comp = cursor.getString(compIdx)?.trim().orEmpty()
                if (comp.isNotBlank() && !companyByCid.containsKey(cid)) {
                    allContactIds.add(cid)
                    companyByCid[cid] = comp
                }
            }
        }

        // 2. Geburtstage zuordnen (falls vorhanden)
        val bdayProjection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.START_DATE
        )
        val bdaySelection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
        val bdayArgs = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
        )

        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            bdayProjection,
            bdaySelection,
            bdayArgs,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME)
            val dateIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val rawDate = cursor.getString(dateIdx)?.trim().orEmpty()
                if (rawDate.isNotBlank()) {
                    allContactIds.add(cid)
                    if (name.isNotBlank() && !rawDisplayByCid.containsKey(cid)) {
                        rawDisplayByCid[cid] = name
                    }
                    birthdaysByContactId[cid] = rawDate
                }
            }
        }

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                val num = cursor.getString(numIdx)?.trim().orEmpty()
                if (num.isNotBlank() && !phonesByContactId.containsKey(cid)) {
                    allContactIds.add(cid)
                    phonesByContactId[cid] = num
                }
            }
        }

        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val mailIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                val mail = cursor.getString(mailIdx)?.trim().orEmpty()
                if (mail.isNotBlank() && !emailsByContactId.containsKey(cid)) {
                    allContactIds.add(cid)
                    emailsByContactId[cid] = mail
                }
            }
        }

        var importedCount = 0
        for (cid in allContactIds) {
            if (ignoredDeviceContactIds.contains(cid) && !existingByCid.containsKey(cid)) {
                continue
            }
            val bday = birthdaysByContactId[cid]
            val phone = phonesByContactId[cid].orEmpty()
            val email = emailsByContactId[cid].orEmpty()
            val company = companyByCid[cid].orEmpty()
            val rawDisplay = rawDisplayByCid[cid].orEmpty()
            val structuredName = structuredFormattedByCid[cid].orEmpty()

            val resolvedName = when {
                structuredName.isNotBlank() -> structuredName
                rawDisplay.isNotBlank() -> rawDisplay
                company.isNotBlank() -> company
                email.isNotBlank() -> email
                phone.isNotBlank() -> phone
                else -> ""
            }
            if (resolvedName.isBlank()) continue

            // Nur nach exakter Kontakt-ID oder (einmalig) nach manuell in der App angelegtem Kontakt ohne Kontakt-ID matchen!
            // Niemals unterschiedliche Kontakte wegen gleicher Festnetz-/Firmennummer oder Namensgleichheit zusammenwerfen!
            val current = existingByCid[cid] ?: allPersonsInDb.find { p ->
                p.importedFromDeviceContactId.isNullOrBlank() &&
                    !claimedManualIds.contains(p.id) &&
                    (p.name.equals(resolvedName, ignoreCase = true) ||
                        (rawDisplay.isNotBlank() && p.name.equals(rawDisplay, ignoreCase = true)))
            }?.also { claimedManualIds.add(it.id) }

            if (current == null) {
                val devicePhoto = readContactPhotoAsBase64(contentResolver, cid)
                val finalPhoto = devicePhoto.ifBlank { generateDefaultPortraitBase64(resolvedName) }
                dao.upsertPerson(
                    PersonEntity(
                        name = resolvedName,
                        birthdayIso = bday,
                        hasNoOwnPhone = phone.isBlank(),
                        phone = phone,
                        email = email,
                        timerEnabled = !bday.isNullOrBlank(),
                        importedFromDeviceContactId = cid,
                        photoBase64 = finalPhoto
                    )
                )
                importedCount++
            } else {
                val needPhotoLoad = forcePhotoRefresh || current.photoBase64.isBlank()
                val devicePhoto = if (needPhotoLoad) readContactPhotoAsBase64(contentResolver, cid) else ""
                val finalPhoto = when {
                    devicePhoto.isNotBlank() -> devicePhoto
                    current.photoBase64.isNotBlank() -> current.photoBase64
                    else -> generateDefaultPortraitBase64(current.name)
                }
                // Aktualisiere Name auf "Nachname, Vorname" aus StructuredName, sofern noch im Roh-Anzeigeformat
                val updatedName = if (
                    structuredName.isNotBlank() &&
                    !current.name.contains(",") &&
                    (current.name.equals(rawDisplay, ignoreCase = true) || current.name.isBlank())
                ) {
                    structuredName
                } else {
                    current.name
                }
                val updatedCopy = current.copy(
                    name = updatedName,
                    importedFromDeviceContactId = current.importedFromDeviceContactId ?: cid,
                    birthdayIso = if (!bday.isNullOrBlank()) bday else current.birthdayIso,
                    phone = if (current.phone.isBlank()) phone else current.phone,
                    email = if (current.email.isBlank()) email else current.email,
                    photoBase64 = finalPhoto
                )
                if (updatedCopy != current) {
                    dao.updatePerson(updatedCopy)
                }
            }
        }
        ensureDefaultPhotosForAllPersons()
        return importedCount
    }

    /**
     * Liest alle Telefonnummern, E-Mail-Adressen, Postadressen, Notizen und das echte Gerätekontaktfoto
     * eines Android-Kontakts aus, damit beim Zusammenführen keinerlei Zweitnummern oder Adressen verloren gehen.
     */
    fun readDeviceContactFullDetails(
        contentResolver: ContentResolver,
        contactId: String
    ): DeviceContactFullDetails {
        if (contactId.isBlank()) return DeviceContactFullDetails()
        val phones = mutableListOf<Pair<String, String>>()
        val emails = mutableListOf<Pair<String, String>>()
        val addresses = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<String>()

        runCatching {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val lblIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                while (cursor.moveToNext()) {
                    val num = cursor.getString(numIdx)?.trim().orEmpty()
                    if (num.isNotBlank()) {
                        val t = cursor.getInt(typeIdx)
                        val customLbl = cursor.getString(lblIdx)?.trim().orEmpty()
                        val label = when {
                            customLbl.isNotBlank() -> customLbl
                            t == ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Festnetz"
                            t == ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Telefon Arbeit"
                            else -> "Telefon"
                        }
                        phones.add(label to num)
                    }
                }
            }
        }

        runCatching {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val lblIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.LABEL)
                while (cursor.moveToNext()) {
                    val mail = cursor.getString(addrIdx)?.trim().orEmpty()
                    if (mail.isNotBlank()) {
                        val customLbl = cursor.getString(lblIdx)?.trim().orEmpty()
                        emails.add(customLbl.ifBlank { "E-Mail" } to mail)
                    }
                }
            }
        }

        runCatching {
            contentResolver.query(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.LABEL
                ),
                "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val fmtIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.TYPE)
                val lblIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.LABEL)
                while (cursor.moveToNext()) {
                    val addr = cursor.getString(fmtIdx)?.trim().orEmpty()
                    if (addr.isNotBlank()) {
                        val t = cursor.getInt(typeIdx)
                        val customLbl = cursor.getString(lblIdx)?.trim().orEmpty()
                        val label = when {
                            customLbl.isNotBlank() -> customLbl
                            t == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "Adresse Arbeit"
                            else -> "Adresse"
                        }
                        addresses.add(label to addr)
                    }
                }
            }
        }

        runCatching {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Note.NOTE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                val noteIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Note.NOTE)
                while (cursor.moveToNext()) {
                    val n = cursor.getString(noteIdx)?.trim().orEmpty()
                    if (n.isNotBlank()) {
                        notes.add(n)
                    }
                }
            }
        }

        val realPhoto = readContactPhotoAsBase64(contentResolver, contactId)
        return DeviceContactFullDetails(
            phones = phones,
            emails = emails,
            addresses = addresses,
            notes = notes,
            realPhotoBase64 = realPhoto
        )
    }

    /**
     * Entfernt einen zusammengeführten Duplikat-Kontakt aus den Android-Handykontakten.
     */
    fun deleteDeviceContactById(
        contentResolver: ContentResolver,
        contactId: String
    ): Boolean {
        if (contactId.isBlank()) return false
        return try {
            val deleted = contentResolver.delete(
                ContactsContract.RawContacts.CONTENT_URI,
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId)
            )
            deleted > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Legt einen Kontakt direkt in den Android-Handykontakten an oder aktualisiert
     * einen bereits verknüpften Handykontakt (Name, Telefon, E-Mail, Adresse, Geburtstag, Notiz, Zusatznummern)
     * – strikt dedupliziert, sodass keine Nummer, E-Mail oder Adresse doppelt eingetragen wird.
     * Gibt die Android CONTACT_ID zurück.
     */
    fun saveOrUpdateDeviceContact(
        contentResolver: ContentResolver,
        person: PersonEntity,
        extraInfos: List<Pair<String, String>> = emptyList()
    ): String? {
        return try {
            var rawContactId: Long? = null
            val existingContactId = person.importedFromDeviceContactId?.takeIf { it.isNotBlank() }

            if (existingContactId != null) {
                contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts._ID),
                    "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
                    arrayOf(existingContactId),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        rawContactId = c.getLong(0)
                    }
                }
            }

            // Alle Nummern, E-Mails und Adressen kanonisch deduplizieren
            val seenPhoneKeys = mutableSetOf<String>()
            val uniquePhones = mutableListOf<Pair<String?, String>>() // label (null = primary mobile), number
            if (person.phone.isNotBlank()) {
                val pk = ContactDeduplicator.canonicalPhoneKey(person.phone)
                if (pk.isNotBlank() && seenPhoneKeys.add(pk)) {
                    uniquePhones.add(null to ContactDeduplicator.cleanWhitespace(person.phone))
                }
            }

            val seenEmailKeys = mutableSetOf<String>()
            val uniqueEmails = mutableListOf<Pair<String?, String>>()
            if (person.email.isNotBlank()) {
                val ek = ContactDeduplicator.canonicalEmailKey(person.email)
                if (ek.isNotBlank() && seenEmailKeys.add(ek)) {
                    uniqueEmails.add(null to person.email.trim())
                }
            }

            val seenAddressKeys = mutableSetOf<String>()
            val uniqueAddresses = mutableListOf<Pair<String, String>>()
            val extraNoteLines = mutableListOf<String>()

            extraInfos.forEach { (rawLabel, rawValue) ->
                val label = rawLabel.trim()
                val value = rawValue.trim()
                if (value.isBlank()) return@forEach
                when {
                    ContactDeduplicator.isEmailLike(value) -> {
                        val ek = ContactDeduplicator.canonicalEmailKey(value)
                        if (ek.isNotBlank() && seenEmailKeys.add(ek)) {
                            uniqueEmails.add(label.ifBlank { "E-Mail" } to value)
                        }
                    }
                    ContactDeduplicator.isPhoneLike(value) -> {
                        val pk = ContactDeduplicator.canonicalPhoneKey(value)
                        if (pk.isNotBlank() && seenPhoneKeys.add(pk)) {
                            uniquePhones.add(label.ifBlank { "2. Telefon" } to ContactDeduplicator.cleanWhitespace(value))
                        }
                    }
                    ContactDeduplicator.isAddressLabel(label) -> {
                        val ak = ContactDeduplicator.canonicalTextKey(value)
                        if (ak.isNotBlank() && seenAddressKeys.add(ak)) {
                            uniqueAddresses.add(label.ifBlank { "Adresse" } to value)
                        }
                    }
                    else -> {
                        val line = if (label.isNotBlank() && !label.equals("Info", ignoreCase = true)) {
                            "$label: $value"
                        } else {
                            value
                        }
                        extraNoteLines.add(line)
                    }
                }
            }

            val combinedNotes = ContactDeduplicator.deduplicateNotes(
                rawNotes = listOf(person.notes) + extraNoteLines,
                seenPhoneKeys = seenPhoneKeys,
                seenEmailKeys = seenEmailKeys,
                seenAddressKeys = seenAddressKeys
            )

            val ops = ArrayList<ContentProviderOperation>()
            val photoBytes = decodePhotoBase64ToBytes(person.photoBase64)
            val hasComma = person.name.contains(",")
            val parsedFamily = if (hasComma) person.name.substringBefore(",").trim() else ""
            val parsedGiven = if (hasComma) person.name.substringAfter(",").trim() else ""
            val androidDisplayName = if (hasComma && parsedGiven.isNotBlank() && parsedFamily.isNotBlank()) {
                "$parsedGiven $parsedFamily"
            } else {
                person.name.trim()
            }

            if (rawContactId != null) {
                val deleteMimes = mutableListOf(
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                )
                if (uniqueAddresses.isNotEmpty()) {
                    deleteMimes.add(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                }
                val placeholders = deleteMimes.joinToString(", ") { "?" }
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN ($placeholders)",
                            (listOf(rawContactId.toString()) + deleteMimes).toTypedArray()
                        )
                        .build()
                )

                val nameOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, androidDisplayName)
                if (hasComma && parsedFamily.isNotBlank()) {
                    nameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, parsedFamily)
                }
                if (hasComma && parsedGiven.isNotBlank()) {
                    nameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, parsedGiven)
                }
                ops.add(nameOp.build())

                if (photoBytes != null && photoBytes.isNotEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                            .build()
                    )
                }
                uniquePhones.forEach { (lbl, number) ->
                    val pOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    if (lbl == null) {
                        pOp.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    } else {
                        pOp.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
                            .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, lbl)
                    }
                    ops.add(pOp.build())
                }
                uniqueEmails.forEach { (lbl, mail) ->
                    val eOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, mail)
                    if (lbl == null) {
                        eOp.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    } else {
                        eOp.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM)
                            .withValue(ContactsContract.CommonDataKinds.Email.LABEL, lbl)
                    }
                    ops.add(eOp.build())
                }
                uniqueAddresses.forEach { (lbl, addr) ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, addr)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, lbl)
                            .build()
                    )
                }
                if (!person.birthdayIso.isNullOrBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, person.birthdayIso)
                            .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                            .build()
                    )
                }
                if (combinedNotes.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, combinedNotes)
                            .build()
                    )
                }
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                return existingContactId
            } else {
                // Neuen Kontakt in Android Handykontakten anlegen
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )
                val newNameOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, androidDisplayName)
                if (hasComma && parsedFamily.isNotBlank()) {
                    newNameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, parsedFamily)
                }
                if (hasComma && parsedGiven.isNotBlank()) {
                    newNameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, parsedGiven)
                }
                ops.add(newNameOp.build())
                if (photoBytes != null && photoBytes.isNotEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                            .build()
                    )
                }
                uniquePhones.forEach { (lbl, number) ->
                    val pOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    if (lbl == null) {
                        pOp.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    } else {
                        pOp.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
                            .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, lbl)
                    }
                    ops.add(pOp.build())
                }
                uniqueEmails.forEach { (lbl, mail) ->
                    val eOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, mail)
                    if (lbl == null) {
                        eOp.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    } else {
                        eOp.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM)
                            .withValue(ContactsContract.CommonDataKinds.Email.LABEL, lbl)
                    }
                    ops.add(eOp.build())
                }
                uniqueAddresses.forEach { (lbl, addr) ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, addr)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, lbl)
                            .build()
                    )
                }
                if (!person.birthdayIso.isNullOrBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, person.birthdayIso)
                            .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                            .build()
                    )
                }
                if (combinedNotes.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, combinedNotes)
                            .build()
                    )
                }

                val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                val rawUri = results.firstOrNull()?.uri ?: return null
                val newRawId = ContentUris.parseId(rawUri)
                var createdContactId: String? = null
                contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(newRawId.toString()),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        createdContactId = c.getLong(0).toString()
                    }
                }
                createdContactId
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class DeviceContactFullDetails(
    val phones: List<Pair<String, String>> = emptyList(),
    val emails: List<Pair<String, String>> = emptyList(),
    val addresses: List<Pair<String, String>> = emptyList(),
    val notes: List<String> = emptyList(),
    val realPhotoBase64: String = ""
)

object ContactDeduplicator {
    fun cleanWhitespace(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    /**
     * Kanonisiert Telefonnummern unabhängig von Leerzeichen, Bindestrichen, Schrägstrichen,
     * Klammern sowie +49 / 0049 / führender 0, damit jede Nummer garantiert nur ein einziges Mal gespeichert wird.
     */
    fun canonicalPhoneKey(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val cleaned = buildString {
            trimmed.forEachIndexed { idx, ch ->
                if (ch in '0'..'9') {
                    append(ch)
                } else if (ch == '+' && isEmpty() && idx == 0) {
                    append('+')
                }
            }
        }
        val digitsOnly = cleaned.filter { it.isDigit() }
        if (digitsOnly.length < 3) return ""

        val intlNormalized = if (cleaned.startsWith("00") && digitsOnly.length >= 5) {
            "+" + cleaned.drop(2)
        } else {
            cleaned
        }

        return when {
            intlNormalized.startsWith("+49") -> {
                val subscriber = intlNormalized.removePrefix("+49").trimStart('0')
                if (subscriber.isNotBlank()) "DE:$subscriber" else "DE:0"
            }
            intlNormalized.startsWith("+43") -> {
                val subscriber = intlNormalized.removePrefix("+43").trimStart('0')
                "AT:$subscriber"
            }
            intlNormalized.startsWith("+41") -> {
                val subscriber = intlNormalized.removePrefix("+41").trimStart('0')
                "CH:$subscriber"
            }
            intlNormalized.startsWith("+") -> intlNormalized
            intlNormalized.startsWith("0") -> {
                val subscriber = intlNormalized.trimStart('0')
                if (subscriber.isNotBlank()) "DE:$subscriber" else "RAW:$digitsOnly"
            }
            else -> "RAW:$digitsOnly"
        }
    }

    fun isPhoneLike(raw: String): Boolean {
        val t = raw.trim()
        if (t.isBlank() || t.contains('@')) return false
        if (t.any { it.isLetter() }) return false
        if (Regex("^\\d{1,2}\\.\\d{1,2}\\.(\\d{2,4})?$").matches(t)) return false
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(t)) return false
        val digitCount = t.count { it.isDigit() }
        return digitCount >= 4
    }

    fun canonicalEmailKey(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("mailto:")
            .trim()
            .removeSurrounding("<", ">")
            .replace(Regex("\\s+"), "")
            .lowercase()
        return if (cleaned.contains('@') && cleaned.substringAfter('@').contains('.')) cleaned else ""
    }

    fun isEmailLike(raw: String): Boolean =
        canonicalEmailKey(raw).isNotBlank()

    fun isAddressLabel(label: String): Boolean {
        val l = label.trim().lowercase()
        return l.contains("adresse") ||
            l.contains("anschrift") ||
            l.contains("wohnort") ||
            l.contains("straße") ||
            l.contains("strasse") ||
            l.contains("post")
    }

    fun canonicalTextKey(raw: String): String {
        val pre = raw.trim().lowercase()
            .replace("straße", "str ")
            .replace("strasse", "str ")
            .replace("str.", "str ")
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("ł", "l")
            .replace("đ", "d")
            .replace("ø", "o")
        val noMarks = Normalizer.normalize(pre, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noMarks
            .replace(Regex("[\\s,.;:/\\-–_•|]+"), " ")
            .trim()
    }

    fun deduplicateNotes(
        rawNotes: List<String>,
        seenPhoneKeys: Set<String> = emptySet(),
        seenEmailKeys: Set<String> = emptySet(),
        seenAddressKeys: Set<String> = emptySet()
    ): String {
        val seenNoteKeys = mutableSetOf<String>()
        seenNoteKeys.addAll(seenAddressKeys)
        val resultLines = mutableListOf<String>()
        rawNotes.forEach { block ->
            block.lines().forEach { rawLine ->
                val line = cleanWhitespace(rawLine)
                if (line.isBlank()) return@forEach
                if (isEmailLike(line) && seenEmailKeys.contains(canonicalEmailKey(line))) return@forEach
                if (isPhoneLike(line) && seenPhoneKeys.contains(canonicalPhoneKey(line))) return@forEach
                val tk = canonicalTextKey(line)
                if (tk.isNotBlank() && seenNoteKeys.add(tk)) {
                    resultLines.add(line)
                }
            }
        }
        return resultLines.joinToString("\n")
    }
}
