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
import java.util.Calendar

class CreateEventActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var eventNameEditText: TextInputEditText
    private lateinit var eventDateEditText: TextInputEditText
    private lateinit var eventTimeEditText: TextInputEditText
    private lateinit var locationSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var eventDescriptionEditText: TextInputEditText
    private lateinit var createEventButton: Button
    private lateinit var toolbar: Toolbar

    private var eventTypes: List<Pair<String, String>> = emptyList() // (id, name)
    private var locations: List<Pair<String, String>> = emptyList() // (id, name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)

        db = FirebaseFirestore.getInstance()

        toolbar = findViewById(R.id.toolbar_create_event)
        eventNameEditText = findViewById(R.id.create_event_name)
        eventDateEditText = findViewById(R.id.create_event_date)
        eventTimeEditText = findViewById(R.id.create_event_time)
        locationSpinner = findViewById(R.id.create_location_spinner)
        typeSpinner = findViewById(R.id.create_type_spinner)
        eventDescriptionEditText = findViewById(R.id.create_event_description)
        createEventButton = findViewById(R.id.create_event_button_actual)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create New Event"

        setupDateAndTimePickers()
        loadSpinnersData()

        createEventButton.setOnClickListener {
            saveEventToFirestore()
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
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                eventTimeEditText.setText(formattedTime)
            }, hour, minute, true).show()
        }
    }

    private fun loadSpinnersData() {
        // Load Event Types
        db.collection("event_types").get()
            .addOnSuccessListener { result ->
                eventTypes = result.map { document ->
                    Pair(document.id, document.getString("name") ?: "")
                }
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    eventTypes.map { it.second }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeSpinner.adapter = adapter
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show()
            }

        // Load Locations
        db.collection("locations").get()
            .addOnSuccessListener { result ->
                locations = result.map { document ->
                    Pair(document.id, document.getString("name") ?: "")
                }
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    locations.map { it.second }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                locationSpinner.adapter = adapter
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveEventToFirestore() {
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

        val event = hashMapOf(
            "name" to name,
            "date" to "$date $time",
            "typeId" to selectedType.first,
            "typeName" to selectedType.second,
            "locationId" to selectedLocation.first,
            "locationName" to selectedLocation.second,
            "description" to description,
            "interestedCount" to 0
        )

        db.collection("events").add(event)
            .addOnSuccessListener {
                Toast.makeText(this, "Event added successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding event: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}