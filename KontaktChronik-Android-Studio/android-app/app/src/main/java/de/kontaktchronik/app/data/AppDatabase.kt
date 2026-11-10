package de.kontaktchronik.app.data

import android.content.ContentResolver
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): KontaktChronikDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kontakt_chronik.db"
                )
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
        val birthdaysByContactId = mutableMapOf<String, Pair<String, String>>()
        val phonesByContactId = mutableMapOf<String, String>()
        val emailsByContactId = mutableMapOf<String, String>()

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
                birthdaysByContactId[cid] = name to rawDate
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
        for ((cid, nameAndDate) in birthdaysByContactId) {
            val (name, bday) = nameAndDate
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
                        importedFromDeviceContactId = cid
                    )
                )
                importedCount++
            } else {
                dao.updatePerson(
                    current.copy(
                        birthdayIso = bday,
                        phone = if (current.phone.isBlank()) phone else current.phone,
                        email = if (current.email.isBlank()) email else current.email
                    )
                )
            }
        }
        return importedCount
    }
}
