package ba.sum.fpmoz.events

import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class EventDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Event Details"

        val eventName: TextView = findViewById(R.id.event_detail_name)
        val eventDescription: TextView = findViewById(R.id.event_detail_description)
        val eventDate: TextView = findViewById(R.id.event_detail_date)
        val eventTime: TextView = findViewById(R.id.event_detail_time)
        val eventLocation: TextView = findViewById(R.id.event_detail_location)
        val eventType: TextView = findViewById(R.id.event_detail_type)
        val eventImage: ImageView = findViewById(R.id.event_detail_image)

        // Retrieve event data from Intent
        val eventId = intent.getStringExtra("event_id")
        val name = intent.getStringExtra("event_name")
        val date = intent.getStringExtra("event_date")
        val time = intent.getStringExtra("event_time")
        val description = intent.getStringExtra("event_description")
        val locationName = intent.getStringExtra("event_location_name")
        val typeName = intent.getStringExtra("event_type_name")

        // Populate views with event data
        eventName.text = name
        eventDescription.text = description
        eventDate.text = date?.split(" ")?.getOrNull(0) // Assuming date is "YYYY-MM-DD HH:MM"
        eventTime.text = date?.split(" ")?.getOrNull(1)
        eventLocation.text = locationName
        eventType.text = typeName

        // For now, using a placeholder image. In a real app, you'd load from URL.
        eventImage.setImageResource(R.drawable.ic_event_placeholder)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed() // Handle back button click
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}