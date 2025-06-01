package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.util.Locale

class ManageEventsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var addNewEventFab: FloatingActionButton

    // Filter and Search UI elements
    private lateinit var searchView: SearchView
    private lateinit var locationFilterSpinner: Spinner
    private lateinit var eventTypeFilterSpinner: Spinner
    private lateinit var clearFiltersButton: Button

    private var masterEventList: MutableList<Event> = mutableListOf() // Stores ALL events fetched initially
    private var eventTypesList: MutableList<Pair<String, String>> = mutableListOf() // (id, name)
    private var locationsList: MutableList<Pair<String, String>> = mutableListOf() // (id, name)

    private var selectedLocationId: String? = null
    private var selectedEventTypeId: String? = null
    private var currentSearchQuery: String = ""
    private var isAdminUser: Boolean = false // Flag to track admin status

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_events)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        recyclerView = findViewById(R.id.event_list_manage)
        toolbar = findViewById(R.id.toolbar)
        addNewEventFab = findViewById(R.id.add_new_event_fab)

        searchView = findViewById(R.id.event_search_view)
        locationFilterSpinner = findViewById(R.id.location_filter_spinner)
        eventTypeFilterSpinner = findViewById(R.id.event_type_filter_spinner)
        clearFiltersButton = findViewById(R.id.clear_filters_button)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Manage Events"

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = EventAdapter(
            emptyList(),
            R.layout.item_event,
            onDeleteClick = { eventId -> deleteEvent(eventId) },
            onEditClick = { event -> openEditEventScreen(event) },
            onItemClick = { event ->
                val intent = Intent(this, EventDetailsActivity::class.java).apply {
                    putExtra("event_id", event.id)
                    putExtra("event_name", event.name)
                    putExtra("event_date", event.date)
                    putExtra("event_description", event.description)
                    putExtra("event_location_name", event.locationName)
                    putExtra("event_type_name", event.typeName)
                }
                startActivity(intent)
            }
        )
        recyclerView.adapter = eventAdapter

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavigationDrawer()
        setupFilterSpinners() // Load data for spinners first
        setupSearchAndFilterListeners()

        addNewEventFab.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }
        checkUserRoleAndLoadInitialData()
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, WelcomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                }
                R.id.nav_user_list -> startActivity(Intent(this, UserList::class.java))
                R.id.nav_add_event -> { /* Current activity */ }
                R.id.nav_manage_event_types -> startActivity(Intent(this, ManageEventTypesActivity::class.java))
                R.id.nav_my_list -> {
                    startActivity(Intent(this, InterestedEventsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
            true
        }
    }
    override fun onResume() {
        super.onResume()
        if (isAdminUser) { // Only reload if user is confirmed admin
            loadMasterEventList() // Refresh data when returning to the activity
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        return if (toggle.onOptionsItemSelected(item)) true else super.onOptionsItemSelected(item)
    }

    private fun checkUserRoleAndLoadInitialData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        db.collection("users").document(currentUser.uid).get().addOnSuccessListener { doc ->
            if (doc != null && doc.exists()) {
                val role = doc.getString("role")
                isAdminUser = role == "admin"
                navigationView.menu.findItem(R.id.nav_user_list).isVisible = isAdminUser
                navigationView.menu.findItem(R.id.nav_add_event).isVisible = isAdminUser // "Manage Events"
                navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = isAdminUser
                navigationView.menu.findItem(R.id.nav_my_list).isVisible = true // All users can have this

                if (isAdminUser) {
                    loadMasterEventList() // Initial data load for admin
                } else {
                    Toast.makeText(this, "Access Denied: Admin rights required.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, WelcomeActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Role check failed: ${it.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupFilterSpinners() {
        // Load Locations
        db.collection("locations").orderBy("name").get().addOnSuccessListener { result ->
            locationsList.clear()
            locationsList.add(Pair("", "All Locations")) // Empty string ID for "All"
            locationsList.addAll(result.map { Pair(it.id, it.getString("name") ?: "") })
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locationsList.map { it.second })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            locationFilterSpinner.adapter = adapter
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show()
        }

        // Load Event Types
        db.collection("event_types").orderBy("name").get().addOnSuccessListener { result ->
            eventTypesList.clear()
            eventTypesList.add(Pair("", "All Event Types")) // Empty string ID for "All"
            eventTypesList.addAll(result.map { Pair(it.id, it.getString("name") ?: "") })
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, eventTypesList.map { it.second })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            eventTypeFilterSpinner.adapter = adapter
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchAndFilterListeners() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyFiltersAndDisplay()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyFiltersAndDisplay()
                return true
            }
        })

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (parent?.id) {
                    R.id.location_filter_spinner -> {
                        // Get ID, if position > 0 and it's not empty, otherwise null
                        selectedLocationId = locationsList.getOrNull(position)?.first?.takeIf { it.isNotEmpty() }
                    }
                    R.id.event_type_filter_spinner -> {
                        selectedEventTypeId = eventTypesList.getOrNull(position)?.first?.takeIf { it.isNotEmpty() }
                    }
                }
                applyFiltersAndDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        locationFilterSpinner.onItemSelectedListener = itemSelectedListener
        eventTypeFilterSpinner.onItemSelectedListener = itemSelectedListener

        clearFiltersButton.setOnClickListener {
            searchView.setQuery("", false)
            locationFilterSpinner.setSelection(0) // Triggers onItemSelected
            eventTypeFilterSpinner.setSelection(0) // Triggers onItemSelected
        }
    }

    private fun loadMasterEventList() {
        db.collection("events")
            .orderBy("date", Query.Direction.DESCENDING) // Fetch all, admin might want to see past events
            .get()
            .addOnSuccessListener { result: QuerySnapshot ->
                masterEventList.clear()
                masterEventList.addAll(result.documents.mapNotNull { document ->
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
                })
                applyFiltersAndDisplay() // Display with current (default) filters
            }.addOnFailureListener {
                Log.e("ManageEventsActivity", "Error loading master event list: ${it.message}", it)
                Toast.makeText(this, "Error loading events: ${it.message}", Toast.LENGTH_LONG).show()
                eventAdapter.updateEvents(emptyList())
            }
    }

    private fun applyFiltersAndDisplay() {
        var filteredEvents = masterEventList.toMutableList()

        // Filter by Location
        selectedLocationId?.let { locId ->
            if (locId.isNotEmpty()) { // Ensure "All" (empty ID) isn't used for filtering
                filteredEvents = filteredEvents.filter { it.locationId == locId }.toMutableList()
            }
        }

        // Filter by Event Type
        selectedEventTypeId?.let { typeId ->
            if (typeId.isNotEmpty()) { // Ensure "All" (empty ID) isn't used for filtering
                filteredEvents = filteredEvents.filter { it.typeId == typeId }.toMutableList()
            }
        }

        // Filter by Search Query
        if (currentSearchQuery.isNotEmpty()) {
            filteredEvents = filteredEvents.filter {
                it.name.lowercase(Locale.getDefault()).contains(currentSearchQuery) ||
                        it.description.lowercase(Locale.getDefault()).contains(currentSearchQuery)
            }.toMutableList()
        }

        eventAdapter.updateEvents(filteredEvents)

        if (filteredEvents.isEmpty() && (currentSearchQuery.isNotBlank() || selectedLocationId != null || selectedEventTypeId != null)) {
            // Toast.makeText(this, "No events match your criteria.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteEvent(eventId: String) {
        db.collection("events").document(eventId).delete().addOnSuccessListener {
            Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show()
            loadMasterEventList() // Refresh master list and re-apply filters
        }.addOnFailureListener {
            Toast.makeText(this, "Error deleting: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEditEventScreen(event: Event) {
        val intent = Intent(this, EditEventActivity::class.java).apply {
            putExtra("EVENT_ID_KEY", event.id)
        }
        startActivity(intent)
    }
}