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

    suspend fun syncDeviceContactsWithBirthdays(contentResolver: ContentResolver): Int {
        val allPersonsInDb = dao.getAllPersonsOnce()
        val existingByCid = allPersonsInDb
            .filter { !it.importedFromDeviceContactId.isNullOrBlank() }
            .associateBy { it.importedFromDeviceContactId }
        val allNamesByContactId = mutableMapOf<String, String>()
        val birthdaysByContactId = mutableMapOf<String, String>()
        val phonesByContactId = mutableMapOf<String, String>()
        val emailsByContactId = mutableMapOf<String, String>()

        // 1. Alle Kontakte aus dem Telefonbuch lesen (auch ohne Geburtstag/Datum)
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val cid = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                if (name.isNotBlank()) {
                    allNamesByContactId[cid] = name
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
                val name = cursor.getString(nameIdx) ?: "Unbekannt"
                val rawDate = cursor.getString(dateIdx) ?: continue
                if (!allNamesByContactId.containsKey(cid)) {
                    allNamesByContactId[cid] = name
                }
                birthdaysByContactId[cid] = rawDate
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
                val num = cursor.getString(numIdx) ?: continue
                if (!phonesByContactId.containsKey(cid)) {
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
                val mail = cursor.getString(mailIdx) ?: continue
                if (!emailsByContactId.containsKey(cid)) {
                    emailsByContactId[cid] = mail
                }
            }
        }

        var importedCount = 0
        for ((cid, name) in allNamesByContactId) {
            val bday = birthdaysByContactId[cid]
            val phone = phonesByContactId[cid].orEmpty()
            val email = emailsByContactId[cid].orEmpty()
            val devicePhoto = readContactPhotoAsBase64(contentResolver, cid)
            val cleanDevicePhone = phone.replace(Regex("[^0-9]"), "")

            val current = existingByCid[cid] ?: allPersonsInDb.find { p ->
                p.name.equals(name, ignoreCase = true) ||
                    p.name.substringBefore(" (").trim().equals(name, ignoreCase = true) ||
                    (cleanDevicePhone.length >= 6 && p.phone.replace(Regex("[^0-9]"), "").endsWith(cleanDevicePhone.takeLast(7)))
            }
            if (current == null) {
                val finalPhoto = devicePhoto.ifBlank { generateDefaultPortraitBase64(name) }
                dao.upsertPerson(
                    PersonEntity(
                        name = name,
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
                val finalPhoto = when {
                    devicePhoto.isNotBlank() -> devicePhoto
                    current.photoBase64.isNotBlank() -> current.photoBase64
                    else -> generateDefaultPortraitBase64(current.name)
                }
                dao.updatePerson(
                    current.copy(
                        importedFromDeviceContactId = current.importedFromDeviceContactId ?: cid,
                        birthdayIso = if (!bday.isNullOrBlank()) bday else current.birthdayIso,
                        phone = if (current.phone.isBlank()) phone else current.phone,
                        email = if (current.email.isBlank()) email else current.email,
                        photoBase64 = finalPhoto
                    )
                )
            }
        }
        ensureDefaultPhotosForAllPersons()
        return importedCount
    }

    /**
     * Legt einen Kontakt direkt in den Android-Handykontakten an oder aktualisiert
     * einen bereits verknüpften Handykontakt (Name, Telefon, E-Mail, Geburtstag, Notiz, Zusatznummern).
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

            val ops = ArrayList<ContentProviderOperation>()
            val photoBytes = decodePhotoBase64ToBytes(person.photoBase64)

            if (rawContactId != null) {
                // Bestehende Datenzeilen dieses RawContacts aktualisieren (löschen & neu eintragen)
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                rawContactId.toString(),
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                            )
                        )
                        .build()
                )

                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, person.name)
                        .build()
                )
                if (photoBytes != null && photoBytes.isNotEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                            .build()
                    )
                }
                if (person.phone.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, person.phone)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                            .build()
                    )
                }
                extraInfos.forEach { (label, value) ->
                    if (value.isNotBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, value)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
                                .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, label.ifBlank { "Info" })
                                .build()
                        )
                    }
                }
                if (person.email.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, person.email)
                            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
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
                if (person.notes.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, person.notes)
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
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, person.name)
                        .build()
                )
                if (photoBytes != null && photoBytes.isNotEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                            .build()
                    )
                }
                if (person.phone.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, person.phone)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                            .build()
                    )
                }
                extraInfos.forEach { (label, value) ->
                    if (value.isNotBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, value)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
                                .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, label.ifBlank { "Info" })
                                .build()
                        )
                    }
                }
                if (person.email.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, person.email)
                            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
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
                if (person.notes.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, person.notes)
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
