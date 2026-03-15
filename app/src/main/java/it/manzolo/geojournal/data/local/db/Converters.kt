package it.manzolo.geojournal.data.local.db

import androidx.room.TypeConverter
import java.util.Date

class Converters {

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split("|").filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        return list?.joinToString("|") ?: ""
    }

    @TypeConverter
    fun fromDate(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun toDate(date: Date?): Long? = date?.time
}
