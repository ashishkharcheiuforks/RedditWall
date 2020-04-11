package com.mehul.redditwall.savedsub

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mehul.redditwall.history.HistoryItem

@Entity(tableName = "sub_table")
class SubSaved(@field:PrimaryKey(autoGenerate = true) var id: Int,
               @field:ColumnInfo(name = "sub_name") var subName: String,
               @field:ColumnInfo(name = "sub_date") var subDate: String) {
    @field:ColumnInfo(name = "sub_int_date")
    var internalDate: String = subDate

    init {
        convertDate()
    }

    private fun convertDate() {
        if (!subDate.contains("at")) {
            return
        }

        val tempDate = subDate.trim().split(" at ")
        val date = tempDate[0].trim().split("-")
        val time = tempDate[1].trim().split(":")
        val month = HistoryItem.months[Integer.parseInt(date[0])]
        var hours = Integer.parseInt(time[0])
        val pmam = if (hours > 12) {
            hours -= 12
            "p.m"
        } else {
            "a.m"
        }
        subDate = "$month ${HistoryItem.getOrdinal(Integer.parseInt(date[1]))}, ${date[2]} | ${hours}:${time[1]} $pmam"

    }
}