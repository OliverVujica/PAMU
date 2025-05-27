package ba.sum.fpmoz.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.Calendar

class AddEventActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var eventName: EditText
    private lateinit var eventDate: EditText
    private lateinit var eventTime: EditText
    private lateinit var locationSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var eventDescription: EditText
    private lateinit var addEventButton: Button
    private lateinit var addEventTypeButton: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private var eventTypes: List<Pair<String, String>> = emptyList() // (id, name)
    private var locations: List<Pair<String, String>> = emptyList() // (id, name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_event)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        eventName = findViewById(R.id.event_name)
        eventDate = findViewById(R.id.event_date)
        eventTime = findViewById(R.id.event_time)
        locationSpinner = findViewById(R.id.locationSpinner)
        typeSpinner = findViewById(R.id.typeSpinner)
        eventDescription = findViewById(R.id.event_description)
        addEventButton = findViewById(R.id.add_event_button)
        addEventTypeButton = findViewById(R.id.addEventTypeButton)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        recyclerView = findViewById(R.id.event_list)

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = EventAdapter(emptyList(), R.layout.item_event, onDeleteClick = { eventId ->
            deleteEvent(eventId)
        })
        recyclerView.adapter = eventAdapter

        eventDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                    eventDate.setText(formattedDate)
                },
                year,
                month,
                day
            )
            datePickerDialog.show()
        }

        eventTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val timePickerDialog = TimePickerDialog(
                this,
                { _, selectedHour, selectedMinute ->
                    val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    eventTime.setText(formattedTime)
                },
                hour,
                minute,
                true
            )
            timePickerDialog.show()
        }

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, WelcomeActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_user_list -> {
                    val intent = Intent(this, UserList::class.java)
                    startActivity(intent)
                }
                R.id.nav_add_event -> {
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        checkUserRole()
        loadEventTypes()
        loadLocations()

        addEventButton.setOnClickListener {
            val name = eventName.text.toString().trim()
            val date = eventDate.text.toString().trim()
            val time = eventTime.text.toString().trim()
            val typePosition = typeSpinner.selectedItemPosition
            val locationPosition = locationSpinner.selectedItemPosition
            val description = eventDescription.text.toString().trim()

            if (name.isEmpty() || date.isEmpty() || time.isEmpty() || typePosition == -1 || locationPosition == -1 || eventTypes.isEmpty() || locations.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val typePair = eventTypes[typePosition]
            val locationPair = locations[locationPosition]

            val event = hashMapOf(
                "name" to name,
                "date" to "$date $time",
                "typeId" to typePair.first,
                "typeName" to typePair.second,
                "locationId" to locationPair.first,
                "locationName" to locationPair.second,
                "description" to description
            )

            db.collection("events").add(event)
                .addOnSuccessListener {
                    Toast.makeText(this, "Event added successfully", Toast.LENGTH_SHORT).show()
                    eventName.text.clear()
                    eventDate.text.clear()
                    eventTime.text.clear()
                    typeSpinner.setSelection(0)
                    locationSpinner.setSelection(0)
                    eventDescription.text.clear()
                    loadEvents()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error adding event: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        addEventTypeButton.setOnClickListener {
            showAddEventTypeDialog()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        return if (toggle.onOptionsItemSelected(item)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun checkUserRole() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    if (role == "admin") {
                        navigationView.menu.findItem(R.id.nav_user_list).isVisible = true
                        navigationView.menu.findItem(R.id.nav_add_event).isVisible = true
                        addEventTypeButton.isVisible = true
                        loadEvents()
                    } else {
                        navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                        navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                        addEventTypeButton.isVisible = false
                        Toast.makeText(this, "Access denied: Admin rights required", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                    navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                    addEventTypeButton.isVisible = false
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                addEventTypeButton.isVisible = false
                Toast.makeText(this, "Failed to check user role", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadEventTypes() {
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
                if (eventTypes.isEmpty()) {
                    Toast.makeText(this, "No event types available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadLocations() {
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
                if (locations.isEmpty()) {
                    Toast.makeText(this, "No locations available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddEventTypeDialog() {
        val editText = android.widget.EditText(this)
        editText.hint = "Enter new event type"

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 30)
        layout.addView(editText)

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Add New Event Type")
            .setView(layout)
            .setPositiveButton("Add") { dialog, _ ->
                val newType = editText.text.toString().trim()
                if (newType.isEmpty()) {
                    Toast.makeText(this, "Event type cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    addEventType(newType)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.show()
    }

    private fun addEventType(typeName: String) {
        val eventType = hashMapOf("name" to typeName)
        db.collection("event_types").add(eventType)
            .addOnSuccessListener {
                Toast.makeText(this, "Event type added successfully", Toast.LENGTH_SHORT).show()
                loadEventTypes()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error adding event type: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadEvents() {
        db.collection("events").get()
            .addOnSuccessListener { result: QuerySnapshot ->
                val events = result.map { document ->
                    Event(
                        id = document.id,
                        name = document.getString("name") ?: "",
                        date = document.getString("date") ?: "",
                        typeId = document.getString("typeId") ?: "",
                        typeName = document.getString("typeName") ?: "",
                        locationId = document.getString("locationId") ?: "",
                        locationName = document.getString("locationName") ?: "",
                    )
                }
                eventAdapter.updateEvents(events.sortedBy { it.date })
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading events: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteEvent(eventId: String) {
        db.collection("events").document(eventId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Event deleted successfully", Toast.LENGTH_SHORT).show()
                loadEvents()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error deleting event: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}