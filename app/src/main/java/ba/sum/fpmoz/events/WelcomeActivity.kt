package ba.sum.fpmoz.events

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WelcomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var welcomeTextView: TextView
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var locationFilterSpinner: Spinner
    private lateinit var typeFilterSpinner: Spinner
    private lateinit var clearFiltersButton: Button
    private lateinit var filterButton: ImageView
    private lateinit var filterOptionsContainer: LinearLayout
    private lateinit var searchButton: ImageView
    private lateinit var eventSearchEditText: EditText

    private var allEvents: List<Event> = emptyList()
    private var locations: List<Pair<String, String>> = emptyList() // (id, name)
    private var eventTypes: List<Pair<String, String>> = emptyList() // (id, name)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getLastLocation()
        } else {
            Toast.makeText(this, "Location permission denied. Cannot auto-filter by city.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        welcomeTextView = findViewById(R.id.welcomeTextView)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        recyclerView = findViewById(R.id.event_list)
        toolbar = findViewById(R.id.toolbar)
        locationFilterSpinner = findViewById(R.id.location_filter_spinner)
        typeFilterSpinner = findViewById(R.id.type_filter_spinner)
        clearFiltersButton = findViewById(R.id.clear_filters_button)
        filterButton = findViewById(R.id.filter_button)
        filterOptionsContainer = findViewById(R.id.filter_options_container)
        searchButton = findViewById(R.id.search_button)
        eventSearchEditText = findViewById(R.id.event_search_edit_text)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setSupportActionBar(toolbar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = EventAdapter(emptyList(), R.layout.item_event_welcome, onItemClick = { event ->
            val intent = Intent(this, EventDetailsActivity::class.java).apply {
                putExtra("event_id", event.id)
                putExtra("event_name", event.name)
                putExtra("event_date", event.date)
                putExtra("event_description", event.description)
                putExtra("event_location_name", event.locationName)
                putExtra("event_type_name", event.typeName)
            }
            startActivity(intent)
        })
        recyclerView.adapter = eventAdapter

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Current activity
                }
                R.id.nav_user_list -> {
                    val intent = Intent(this, UserList::class.java)
                    startActivity(intent)
                }
                R.id.nav_add_event -> {
                    val intent = Intent(this, AddEventActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_manage_event_types -> {
                    val intent = Intent(this, ManageEventTypesActivity::class.java)
                    startActivity(intent)
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

        val currentUser = auth.currentUser
        if (currentUser != null) {
            welcomeTextView.text = "Welcome, ${currentUser.email}!"
            checkUserRole()
        } else {
            welcomeTextView.text = "Welcome to our app!"
            navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
            navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
            loadFilterDataAndEvents()
        }

        setupFilterSpinners()
        clearFiltersButton.setOnClickListener {
            clearFilters()
        }

        filterButton.setOnClickListener {
            toggleFilterOptionsVisibility()
        }

        searchButton.setOnClickListener {
            toggleEventSearchVisibility()
        }

        eventSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        requestLocationPermission()
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

    private fun toggleFilterOptionsVisibility() {
        if (filterOptionsContainer.visibility == View.VISIBLE) {
            filterOptionsContainer.visibility = View.GONE
        } else {
            filterOptionsContainer.visibility = View.VISIBLE
        }
    }

    private fun toggleEventSearchVisibility() {
        if (eventSearchEditText.visibility == View.VISIBLE) {
            eventSearchEditText.visibility = View.GONE
        } else {
            eventSearchEditText.visibility = View.VISIBLE
        }
    }

    private fun checkUserRole() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val role = document.getString("role")
                        if (role == "admin") {
                            navigationView.menu.findItem(R.id.nav_user_list).isVisible = true
                            navigationView.menu.findItem(R.id.nav_add_event).isVisible = true
                            navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = true
                        } else {
                            navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                            navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                            navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = false
                        }
                    } else {
                        navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                        navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                        navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = false
                    }
                    loadFilterDataAndEvents()
                }
                .addOnFailureListener {
                    navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                    navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                    Toast.makeText(this, "Failed to check user role", Toast.LENGTH_SHORT).show()
                    loadFilterDataAndEvents()
                }
        } ?: run {
            navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
            navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
            loadFilterDataAndEvents()
        }
    }

    private fun loadFilterDataAndEvents() {
        // Load locations
        db.collection("locations").get()
            .addOnSuccessListener { result ->
                val fetchedLocations = mutableListOf<Pair<String, String>>()
                fetchedLocations.add(Pair("", "All Locations"))
                fetchedLocations.addAll(result.map { document ->
                    Pair(document.id, document.getString("name") ?: "")
                })
                locations = fetchedLocations
                val locationNames = locations.map { it.second }
                val locationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locationNames)
                locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                locationFilterSpinner.adapter = locationAdapter
                locationFilterSpinner.setSelection(0)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show()
            }

        // Load event types
        db.collection("event_types").get()
            .addOnSuccessListener { result ->
                val fetchedTypes = mutableListOf<Pair<String, String>>()
                fetchedTypes.add(Pair("", "All Types")) // Add "All" option
                fetchedTypes.addAll(result.map { document ->
                    Pair(document.id, document.getString("name") ?: "")
                })
                eventTypes = fetchedTypes
                val typeNames = eventTypes.map { it.second }
                val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeNames)
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeFilterSpinner.adapter = typeAdapter
                typeFilterSpinner.setSelection(0) // Default to "All Types"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show()
            }

        // Load all events initially
        db.collection("events").get()
            .addOnSuccessListener { result: QuerySnapshot ->
                allEvents = result.map { document ->
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
                }.sortedBy { it.date }
                applyFilters() // Apply filters after all events are loaded
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading events: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupFilterSpinners() {
        locationFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        typeFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun applyFilters() {
        var filteredEvents = allEvents

        val selectedLocationId = locations.getOrNull(locationFilterSpinner.selectedItemPosition)?.first
        val selectedTypeId = eventTypes.getOrNull(typeFilterSpinner.selectedItemPosition)?.first
        val searchQuery = eventSearchEditText.text.toString().trim().lowercase(Locale.getDefault())

        if (!selectedLocationId.isNullOrEmpty()) {
            filteredEvents = filteredEvents.filter { it.locationId == selectedLocationId }
        }

        if (!selectedTypeId.isNullOrEmpty()) {
            filteredEvents = filteredEvents.filter { it.typeId == selectedTypeId }
        }

        if (searchQuery.isNotEmpty()) {
            filteredEvents = filteredEvents.filter { event ->
                event.name.lowercase(Locale.getDefault()).contains(searchQuery) ||
                        event.description.lowercase(Locale.getDefault()).contains(searchQuery)
            }
        }

        eventAdapter.updateEvents(filteredEvents)
    }

    private fun clearFilters() {
        locationFilterSpinner.setSelection(0) // Select "All Locations"
        typeFilterSpinner.setSelection(0) // Select "All Types"
        eventSearchEditText.text.clear() // Clear search query
        applyFilters() // Reapply filters to show all events
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLastLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(this, "Location permission is needed to auto-filter by city.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun getLastLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        getCityFromLocation(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(this, "Could not get current location.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getCityFromLocation(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val city = addresses[0].locality
                if (city != null && city.equals("Mostar", ignoreCase = true)) {
                    val mostarIndex = locations.indexOfFirst { it.second.equals("Mostar", ignoreCase = true) }
                    if (mostarIndex != -1) {
                        locationFilterSpinner.setSelection(mostarIndex)
                        Toast.makeText(this, "Auto-filtered by Mostar based on your location.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error getting city from location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}