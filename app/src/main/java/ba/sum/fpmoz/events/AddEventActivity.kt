package ba.sum.fpmoz.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
//import androidx.activity.enableEdgeToEdge // Uklonjeno ako nije potrebno ili uzrokuje probleme
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import java.util.Calendar

class AddEventActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var eventName: TextInputEditText
    private lateinit var eventDate: TextInputEditText
    private lateinit var eventTime: TextInputEditText
    private lateinit var locationSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var eventDescription: TextInputEditText
    private lateinit var addEventButton: Button
    // private lateinit var addEventTypeButton: Button // Uklonjeno
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private var eventTypes: List<Pair<String, String>> = emptyList() // (id, name)
    private var locations: List<Pair<String, String>> = emptyList() // (id, name)
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() // Uklonjeno ili zakomentirano ako nije nužno
        setContentView(R.layout.activity_add_event)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        eventName = findViewById(R.id.event_name)
        eventDate = findViewById(R.id.event_date)
        eventTime = findViewById(R.id.event_time)
        locationSpinner = findViewById(R.id.locationSpinner)
        typeSpinner = findViewById(R.id.typeSpinner)
        eventDescription = findViewById(R.id.eventDescription)
        addEventButton = findViewById(R.id.add_event_button)
        // addEventTypeButton = findViewById(R.id.addEventTypeButton) // Uklonjeno
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        recyclerView = findViewById(R.id.event_list)
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)

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
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, WelcomeActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_user_list -> {
                    val intent = Intent(this, UserList::class.java)
                    startActivity(intent)
                    // finish() // Ovisno da li želite zatvoriti ovu aktivnost
                }
                R.id.nav_add_event -> {
                    // Trenutna aktivnost
                }
                R.id.nav_manage_event_types -> {
                    val intent = Intent(this, ManageEventTypesActivity::class.java)
                    startActivity(intent)
                    // finish() // Ovisno da li želite zatvoriti ovu aktivnost
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

            if (name.isEmpty() || date.isEmpty() || time.isEmpty() || typePosition == -1 || locationPosition == -1 || typePosition >= eventTypes.size || locationPosition >= locations.size) {
                Toast.makeText(this, "Required fields are missing or invalid selection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (eventTypes.isEmpty()) {
                Toast.makeText(this, "No event types available. Please add event types first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (locations.isEmpty()) {
                Toast.makeText(this, "No locations available. Please add locations if necessary (or check Firestore).", Toast.LENGTH_LONG).show()
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
                    eventName.text?.clear()
                    eventDate.text?.clear()
                    eventTime.text?.clear()
                    if (typeSpinner.adapter.count > 0) typeSpinner.setSelection(0)
                    if (locationSpinner.adapter.count > 0) locationSpinner.setSelection(0)
                    eventDescription.text?.clear()
                    loadEvents()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error adding event: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Uklonjen listener za addEventTypeButton
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
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
                        navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = true
                        // addEventTypeButton.isVisible = true // Uklonjeno
                        loadEvents()
                    } else {
                        navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                        navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                        navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = false
                        // addEventTypeButton.isVisible = false // Uklonjeno
                        Toast.makeText(this, "Access denied: Admin rights required to add events.", Toast.LENGTH_SHORT).show()
                        // Preusmjeri na WelcomeActivity ako korisnik nije admin
                        val intent = Intent(this, WelcomeActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                    navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                    navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = false
                    // addEventTypeButton.isVisible = false // Uklonjeno
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = false
                // addEventTypeButton.isVisible = false // Uklonjeno
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
                    // Toast.makeText(this, "No event types available", Toast.LENGTH_SHORT).show()
                    // Nema potrebe za toastom ovdje, jer će se provjera vršiti prije dodavanja eventa
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
                    // Toast.makeText(this, "No locations available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Uklonjene metode showAddEventTypeDialog() i addEventType(typeName: String)

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
                        description = document.getString("description") ?: ""
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