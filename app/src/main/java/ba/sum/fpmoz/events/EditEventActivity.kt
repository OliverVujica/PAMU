package ba.sum.fpmoz.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditEventActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var eventNameEditText: TextInputEditText
    private lateinit var eventDateEditText: TextInputEditText
    private lateinit var eventTimeEditText: TextInputEditText
    private lateinit var locationSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var eventDescriptionEditText: TextInputEditText
    private lateinit var saveEventButton: Button
    private lateinit var toolbar: Toolbar

    private var eventTypes: MutableList<Pair<String, String>> = mutableListOf()
    private var locations: MutableList<Pair<String, String>> = mutableListOf()

    private var currentEventId: String? = null
    private var currentEvent: Event? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_event)

        db = FirebaseFirestore.getInstance()

        toolbar = findViewById(R.id.toolbar_edit_event)
        eventNameEditText = findViewById(R.id.edit_event_name)
        eventDateEditText = findViewById(R.id.edit_event_date)
        eventTimeEditText = findViewById(R.id.edit_event_time)
        locationSpinner = findViewById(R.id.edit_location_spinner)
        typeSpinner = findViewById(R.id.edit_type_spinner)
        eventDescriptionEditText = findViewById(R.id.edit_event_description)
        saveEventButton = findViewById(R.id.edit_event_button_actual)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Event"
        saveEventButton.text = "Save Changes"

        currentEventId = intent.getStringExtra("EVENT_ID_KEY")
        if (currentEventId == null) {
            Toast.makeText(this, "Error: Event ID missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupDateAndTimePickers()
        loadSpinnersDataAndThenEvent()

        saveEventButton.setOnClickListener {
            updateEventInFirestore()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    private fun setupDateAndTimePickers() {
        eventDateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()

            currentEvent?.date?.split(" ")?.getOrNull(0)?.let { dateStr ->
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    calendar.time = sdf.parse(dateStr)!!
                } catch (e: Exception) { /* Use current date if parsing fails */ }
            }
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                eventDateEditText.setText(formattedDate)
            }, year, month, day).show()
        }

        eventTimeEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            currentEvent?.date?.split(" ")?.getOrNull(1)?.let { timeStr ->
                try {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    calendar.time = sdf.parse(timeStr)!!
                } catch (e: Exception) { /* Use current time if parsing fails */ }
            }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                eventTimeEditText.setText(formattedTime)
            }, hour, minute, true).show()
        }
    }

    private fun loadSpinnersDataAndThenEvent() {
        var typesLoaded = false
        var locationsLoaded = false

        val männlichLaden = {
            if (typesLoaded && locationsLoaded) {
                loadEventDetails()
            }
        }

        db.collection("event_types").get()
            .addOnSuccessListener { result ->
                eventTypes.clear()
                eventTypes.addAll(result.map { document -> Pair(document.id, document.getString("name") ?: "") })
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, eventTypes.map { it.second })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeSpinner.adapter = adapter
                typesLoaded = true
                männlichLaden()
            }
            .addOnFailureListener { Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show() }

        db.collection("locations").get()
            .addOnSuccessListener { result ->
                locations.clear()
                locations.addAll(result.map { document -> Pair(document.id, document.getString("name") ?: "") })
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locations.map { it.second })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                locationSpinner.adapter = adapter
                locationsLoaded = true
                männlichLaden()
            }
            .addOnFailureListener { Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun loadEventDetails() {
        currentEventId?.let { id ->
            db.collection("events").document(id).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        currentEvent = document.toObject(Event::class.java)?.copy(id = document.id)
                        currentEvent?.let { populateEventData(it) }
                    } else {
                        Toast.makeText(this, "Event not found.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error loading event details: ${it.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun populateEventData(event: Event) {
        eventNameEditText.setText(event.name)
        eventDescriptionEditText.setText(event.description)

        val dateTimeParts = event.date.split(" ")
        if (dateTimeParts.size == 2) {
            eventDateEditText.setText(dateTimeParts[0])
            eventTimeEditText.setText(dateTimeParts[1])
        } else {
            eventDateEditText.setText(event.date)
        }


        val typeIdx = eventTypes.indexOfFirst { it.first == event.typeId }
        if (typeIdx != -1) typeSpinner.setSelection(typeIdx)

        val locationIdx = locations.indexOfFirst { it.first == event.locationId }
        if (locationIdx != -1) locationSpinner.setSelection(locationIdx)
    }

    private fun updateEventInFirestore() {
        val name = eventNameEditText.text.toString().trim()
        val date = eventDateEditText.text.toString().trim()
        val time = eventTimeEditText.text.toString().trim()
        val typePosition = typeSpinner.selectedItemPosition
        val locationPosition = locationSpinner.selectedItemPosition
        val description = eventDescriptionEditText.text.toString().trim()

        if (name.isEmpty() || date.isEmpty() || time.isEmpty() || typePosition < 0 || locationPosition < 0) {
            Toast.makeText(this, "All fields are required and selections must be valid.", Toast.LENGTH_SHORT).show()
            return
        }
        if (typePosition >= eventTypes.size || locationPosition >= locations.size) {
            Toast.makeText(this, "Invalid selection for type or location.", Toast.LENGTH_SHORT).show()
            return
        }


        val selectedType = eventTypes[typePosition]
        val selectedLocation = locations[locationPosition]

        val eventUpdate = mapOf(
            "name" to name,
            "date" to "$date $time",
            "typeId" to selectedType.first,
            "typeName" to selectedType.second,
            "locationId" to selectedLocation.first,
            "locationName" to selectedLocation.second,
            "description" to description
        )

        currentEventId?.let {
            db.collection("events").document(it).update(eventUpdate)
                .addOnSuccessListener {
                    Toast.makeText(this, "Event updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating event: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}