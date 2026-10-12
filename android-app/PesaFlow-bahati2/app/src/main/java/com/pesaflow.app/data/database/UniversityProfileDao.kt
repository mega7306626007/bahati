package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface UniversityProfileDao {


    @Query("SELECT * FROM university_profiles WHERE id = 'SINGLETON_USER_PROFILE' LIMIT 1")
    fun getUniversityProfile(): Flow<UniversityProfile?>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUniversityProfile(profile: UniversityProfile)


    @Query("UPDATE university_profiles SET universityName = :name, campus = :campus, currentSemester = :semester, academicYear = :year, semesterStartTimestamp = :start, semesterEndTimestamp = :end, startingFunding = :funding WHERE id = 'SINGLETON_USER_PROFILE'")
    suspend fun updateUniversityProfile(
        name: String,
        campus: String,
        semester: Int,
        year: String,
        start: Long,
        end: Long,
        funding: Double
    )


    @Query("DELETE FROM university_profiles WHERE id = 'SINGLETON_USER_PROFILE'")
    suspend fun deleteUniversityProfile()


    @Query("UPDATE university_profiles SET startingFunding = startingFunding + :amount WHERE id = 'SINGLETON_USER_PROFILE'")
    suspend fun addFunding(amount: Double)


    @Query("SELECT * FROM university_profiles")
    fun getAllProfiles(): Flow<List<UniversityProfile>>
}