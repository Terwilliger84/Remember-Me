package de.kontaktchronik.app.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
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
    version = 3,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kontakt_chronik.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Durchsucht die Android-Telefonkontakte (ContactsContract) nach Geburtstagen,
 * Telefonnummern und E-Mail-Adressen und synchronisiert sie in die lokale Datenbank.
 */
class ContactsSyncRepository(private val dao: KontaktChronikDao) {

    suspend fun syncDeviceContactsWithBirthdays(contentResolver: ContentResolver): Int {
        val existing = dao.getAllPersonsOnce().associateBy { it.importedFromDeviceContactId }
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
            val current = existing[cid]
            if (current == null) {
                dao.upsertPerson(
                    PersonEntity(
                        name = name,
                        birthdayIso = bday,
                        hasNoOwnPhone = phone.isBlank(),
                        phone = phone,
                        email = email,
                        timerEnabled = !bday.isNullOrBlank(),
                        importedFromDeviceContactId = cid
                    )
                )
                importedCount++
            } else {
                dao.updatePerson(
                    current.copy(
                        birthdayIso = if (!bday.isNullOrBlank()) bday else current.birthdayIso,
                        phone = if (current.phone.isBlank()) phone else current.phone,
                        email = if (current.email.isBlank()) email else current.email
                    )
                )
            }
        }
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

            if (rawContactId != null) {
                // Bestehende Datenzeilen dieses RawContacts aktualisieren (löschen & neu eintragen)
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?)",
                            arrayOf(
                                rawContactId.toString(),
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
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
