package ba.sum.fpmoz.events

import java.io.Serializable

data class Event(
    val id: String = "", // Default vrijednost
    val name: String = "",
    val date: String = "",
    val typeId: String = "",
    val typeName: String = "",
    val locationId: String = "",
    val locationName: String = "",
    val description: String = "",
    var interestedCount: Int = 0,
    @get:JvmName("getIsCurrentUserInterested")
    @set:JvmName("setIsCurrentUserInterested")
    var isCurrentUserInterested: Boolean = false
) : Serializable