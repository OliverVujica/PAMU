package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var eventNameTextView: TextView
    private lateinit var eventDescriptionTextView: TextView
    private lateinit var eventDateTextView: TextView
    private lateinit var eventTimeTextView: TextView
    private lateinit var eventLocationTextView: TextView
    private lateinit var eventTypeTextView: TextView
    private lateinit var eventImage: ImageView
    private lateinit var addToCalendarButton: Button
    private lateinit var shareEventButton: Button

    private var eventId: String? = null
    private var name: String? = null
    private var fullDateStr: String? = null // String koji sadrži i datum i vrijeme
    private var description: String? = null
    private var locationName: String? = null
    private var typeName: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Event Details"

        eventNameTextView = findViewById(R.id.event_detail_name)
        eventDescriptionTextView = findViewById(R.id.event_detail_description)
        eventDateTextView = findViewById(R.id.event_detail_date)
        eventTimeTextView = findViewById(R.id.event_detail_time)
        eventLocationTextView = findViewById(R.id.event_detail_location)
        eventTypeTextView = findViewById(R.id.event_detail_type)
        eventImage = findViewById(R.id.event_detail_image)
        addToCalendarButton = findViewById(R.id.btn_add_to_calendar)
        shareEventButton = findViewById(R.id.btn_share_event)

        // Retrieve event data from Intent
        eventId = intent.getStringExtra("event_id")
        name = intent.getStringExtra("event_name")
        fullDateStr = intent.getStringExtra("event_date") // Ovo je "YYYY-MM-DD HH:MM"
        description = intent.getStringExtra("event_description")
        locationName = intent.getStringExtra("event_location_name")
        typeName = intent.getStringExtra("event_type_name")

        eventNameTextView.text = name
        eventDescriptionTextView.text = description

        val dateTimeParts = fullDateStr?.split(" ")
        val datePart = dateTimeParts?.getOrNull(0)
        val timePart = dateTimeParts?.getOrNull(1)

        eventDateTextView.text = datePart
        eventTimeTextView.text = timePart
        eventLocationTextView.text = locationName
        eventTypeTextView.text = typeName

        eventImage.setImageResource(R.drawable.ic_event_placeholder) //

        addToCalendarButton.setOnClickListener {
            addEventToCalendar()
        }

        shareEventButton.setOnClickListener {
            shareEvent()
        }
    }

    private fun addEventToCalendar() {
        if (name.isNullOrEmpty() || fullDateStr.isNullOrEmpty()) {
            Toast.makeText(this, "Event details are incomplete to add to calendar.", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        try {
            val eventDate = sdf.parse(fullDateStr!!)
            val beginTime = Calendar.getInstance()
            beginTime.time = eventDate

            val endTime = Calendar.getInstance()
            endTime.time = eventDate
            endTime.add(Calendar.HOUR_OF_DAY, 1)

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, name)
                putExtra(CalendarContract.Events.DESCRIPTION, description ?: "")
                putExtra(CalendarContract.Events.EVENT_LOCATION, locationName ?: "")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime.timeInMillis)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No calendar app found.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing event date/time.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun shareEvent() {
        if (name.isNullOrEmpty() || fullDateStr.isNullOrEmpty()) {
            Toast.makeText(this, "Event details are incomplete to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val dateTimeParts = fullDateStr?.split(" ")
        val datePart = dateTimeParts?.getOrNull(0) ?: "N/A"
        val timePart = dateTimeParts?.getOrNull(1) ?: "N/A"

        val shareText = """
            Check out this event:
            Title: $name
            Date: $datePart
            Time: $timePart
            Location: ${locationName ?: "N/A"}
            Description: ${description ?: "N/A"}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Event: $name")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Event Via"))
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}