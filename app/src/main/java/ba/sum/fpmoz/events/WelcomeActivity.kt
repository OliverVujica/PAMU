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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ProgressBar // Import ProgressBar

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
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var emptyViewText: TextView
    private var allEvents: MutableList<Event> = mutableListOf()
    private var currentUserInterestedEventIds: MutableSet<String> = mutableSetOf()
    private var locations: List<Pair<String, String>> = emptyList()
    private var eventTypes: List<Pair<String, String>> = emptyList()
    private var userPreferredEventTypeIds: List<String> = emptyList()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

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
        loadingProgressBar = findViewById(R.id.loading_progress_bar)
        emptyViewText = findViewById(R.id.empty_view_text)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setSupportActionBar(toolbar)
        recyclerView.layoutManager = LinearLayoutManager(this)

        recyclerView.visibility = View.GONE
        emptyViewText.visibility = View.GONE

        eventAdapter = EventAdapter(
            emptyList(),
            R.layout.item_event_welcome,
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
            },
            onInterestClick = { event, position ->
                toggleInterestStatus(event, position)
            }
        )
        recyclerView.adapter = eventAdapter

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavigationDrawer()

        setupFilterSpinners()
        clearFiltersButton.setOnClickListener { clearFilters() }
        filterButton.setOnClickListener { toggleFilterOptionsVisibility() }
        searchButton.setOnClickListener { toggleEventSearchVisibility() }
        eventSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        requestLocationPermission()

        loadingProgressBar.visibility = View.VISIBLE

        val currentUser = auth.currentUser
        if (currentUser != null) {
            welcomeTextView.text = "Welcome, ${currentUser.email}!" //
            fetchCurrentUserInterestedEventIds { // Prvo dohvati zainteresirane, pa onda provjeri ulogu i učitaj sve
                checkUserRole()
            }
        } else {
            welcomeTextView.text = "Welcome to our app!" //
            configureNavDrawerForGuest()
            userPreferredEventTypeIds = emptyList() // Gost nema preferencija
            loadFilterSpinnersDataAndThenEvents() // Gosti direktno učitavaju podatke za filtere i događaje
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home -> { /* Current activity */ } //
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                }
                R.id.nav_add_event -> startActivity(Intent(this, ManageEventsActivity::class.java)) //
                R.id.nav_manage_event_types -> startActivity(Intent(this, ManageEventTypesActivity::class.java)) //
                R.id.nav_user_list -> startActivity(Intent(this, UserList::class.java)) //
                R.id.nav_my_list -> startActivity(Intent(this, InterestedEventsActivity::class.java)) // Nova stavka
                R.id.nav_logout -> {
                    auth.signOut()
                    currentUserInterestedEventIds.clear() // Ocisti listu zainteresiranih kod odjave
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            true
        }
    }

    private fun configureNavDrawerForGuest() {
        val menu = navigationView.menu
        menu.findItem(R.id.nav_profile).isVisible = false
        menu.findItem(R.id.nav_user_list).isVisible = false //
        menu.findItem(R.id.nav_add_event).isVisible = false //
        menu.findItem(R.id.nav_manage_event_types).isVisible = false //
        menu.findItem(R.id.nav_my_list).isVisible = false // Ni gosti nemaju "Moju listu"
        menu.findItem(R.id.nav_logout).isVisible = false // Gosti se ne mogu odjaviti
    }

    private fun fetchCurrentUserInterestedEventIds(onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            currentUserInterestedEventIds.clear()
            onComplete()
            return
        }
        db.collection("users").document(userId).collection("interested_events")
            .get()
            .addOnSuccessListener { documents ->
                currentUserInterestedEventIds.clear()
                for (document in documents) {
                    currentUserInterestedEventIds.add(document.id)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch interested events: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                onComplete()
            }
    }

    private fun toggleInterestStatus(event: Event, position: Int) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "You need to be logged in to mark interest.", Toast.LENGTH_SHORT).show()
            return
        }

        val eventRef = db.collection("events").document(event.id)
        val userInterestedEventRef = db.collection("users").document(userId)
            .collection("interested_events").document(event.id)

        db.runTransaction { transaction ->
            val currentEventSnapshot = transaction.get(eventRef)
            var currentInterestedCount = currentEventSnapshot.getLong("interestedCount")?.toInt() ?: 0

            if (event.isCurrentUserInterested) { // Korisnik želi ukloniti interes
                transaction.delete(userInterestedEventRef)
                currentInterestedCount = (currentInterestedCount - 1).coerceAtLeast(0)
                transaction.update(eventRef, "interestedCount", currentInterestedCount)
            } else { // Korisnik želi dodati interes
                transaction.set(userInterestedEventRef, mapOf("timestamp" to FieldValue.serverTimestamp()))
                currentInterestedCount += 1
                transaction.update(eventRef, "interestedCount", currentInterestedCount)
            }
            Pair(!event.isCurrentUserInterested, currentInterestedCount)
        }.addOnSuccessListener { (newInterestStatus, newCount) ->
            event.isCurrentUserInterested = newInterestStatus
            event.interestedCount = newCount
            if(newInterestStatus) currentUserInterestedEventIds.add(event.id) else currentUserInterestedEventIds.remove(event.id)

            val indexInAllEvents = allEvents.indexOfFirst { it.id == event.id }
            if (indexInAllEvents != -1) {
                allEvents[indexInAllEvents].isCurrentUserInterested = newInterestStatus
                allEvents[indexInAllEvents].interestedCount = newCount
            }
            applyFilters()
            val message = if (newInterestStatus) "Added to your interested list" else "Removed from your interested list"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update interest: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
        filterOptionsContainer.visibility = if (filterOptionsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun toggleEventSearchVisibility() {
        eventSearchEditText.visibility = if (eventSearchEditText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun checkUserRole() {
        val currentUser = auth.currentUser
        val navMenu = navigationView.menu
        navMenu.findItem(R.id.nav_profile).isVisible = currentUser != null
        navMenu.findItem(R.id.nav_my_list).isVisible = currentUser != null // "Moja lista" vidljiva ako je korisnik prijavljen
        navMenu.findItem(R.id.nav_logout).isVisible = currentUser != null //

        if (currentUser == null) {
            configureNavDrawerForGuest()
            userPreferredEventTypeIds = emptyList()
            loadFilterSpinnersDataAndThenEvents()
            return
        }

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val isAdmin = document?.getString("role") == "admin"
                navMenu.findItem(R.id.nav_user_list).isVisible = isAdmin //
                navMenu.findItem(R.id.nav_add_event).isVisible = isAdmin //
                navMenu.findItem(R.id.nav_manage_event_types).isVisible = isAdmin //

                @Suppress("UNCHECKED_CAST")
                val preferredIdsFromDb = document?.get("preferredEventTypeIds") as? List<String>
                userPreferredEventTypeIds = preferredIdsFromDb ?: emptyList()

                loadFilterSpinnersDataAndThenEvents()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check user role or preferences: ${it.message}", Toast.LENGTH_SHORT).show()
                userPreferredEventTypeIds = emptyList()
                navMenu.findItem(R.id.nav_user_list).isVisible = false //
                navMenu.findItem(R.id.nav_add_event).isVisible = false //
                navMenu.findItem(R.id.nav_manage_event_types).isVisible = false //
                loadFilterSpinnersDataAndThenEvents()
            }
    }

    private fun loadFilterSpinnersDataAndThenEvents() {
        loadingProgressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyViewText.visibility = View.GONE

        var locationsLoaded = false
        var typesLoaded = false

        val onSpinnersDataLoaded = {
            if (locationsLoaded && typesLoaded) {
                if (userPreferredEventTypeIds.size == 1 && eventTypes.isNotEmpty()) {
                    val preferredId = userPreferredEventTypeIds.first()
                    val typeIndex = eventTypes.indexOfFirst { it.first == preferredId }
                    if (typeIndex != -1 && typeFilterSpinner.adapter != null && typeIndex < typeFilterSpinner.adapter.count) {
                        val currentListener = typeFilterSpinner.onItemSelectedListener
                        typeFilterSpinner.onItemSelectedListener = null
                        typeFilterSpinner.setSelection(typeIndex, false)
                        typeFilterSpinner.onItemSelectedListener = currentListener
                    }
                }
                loadEventsData()
            }
        }

        db.collection("locations").get()
            .addOnSuccessListener { result ->
                val fetchedLocations = mutableListOf(Pair("", "All Locations")) //
                fetchedLocations.addAll(result.map { document ->
                    Pair(document.id, document.getString("name") ?: "")
                })
                locations = fetchedLocations
                val locationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locations.map { it.second })
                locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                locationFilterSpinner.adapter = locationAdapter //
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading locations: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                locationsLoaded = true
                onSpinnersDataLoaded()
            }

        db.collection("event_types").get()
            .addOnSuccessListener { result ->
                val fetchedTypes = mutableListOf(Pair("", "All Types")) //
                fetchedTypes.addAll(result.map { document ->
                    Pair(document.id, document.getString("name") ?: "")
                })
                eventTypes = fetchedTypes
                val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, eventTypes.map { it.second })
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeFilterSpinner.adapter = typeAdapter //
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                typesLoaded = true
                onSpinnersDataLoaded()
            }
    }

    private fun loadEventsData() {
        db.collection("events").get()
            .addOnSuccessListener { result: QuerySnapshot ->
                allEvents.clear()
                allEvents.addAll(result.map { document ->
                    Event(
                        id = document.id,
                        name = document.getString("name") ?: "",
                        date = document.getString("date") ?: "", //
                        typeId = document.getString("typeId") ?: "", //
                        typeName = document.getString("typeName") ?: "", //
                        locationId = document.getString("locationId") ?: "", //
                        locationName = document.getString("locationName") ?: "", //
                        description = document.getString("description") ?: "", //
                        interestedCount = document.getLong("interestedCount")?.toInt() ?: 0, //
                        isCurrentUserInterested = currentUserInterestedEventIds.contains(document.id) //
                    )
                })
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading events: ${it.message}", Toast.LENGTH_SHORT).show() //
                allEvents.clear()
            }
            .addOnCompleteListener {
                applyFilters()
            }
    }

    private fun setupFilterSpinners() {
        val filterListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        locationFilterSpinner.onItemSelectedListener = filterListener //
        typeFilterSpinner.onItemSelectedListener = filterListener //
    }

    private fun applyFilters() {
        loadingProgressBar.visibility = View.GONE

        var filteredEvents = allEvents.toMutableList() // Radimo s kopijom

        val currentDate = Calendar.getInstance() //
        currentDate.set(Calendar.HOUR_OF_DAY, 0) //
        currentDate.set(Calendar.MINUTE, 0) //
        currentDate.set(Calendar.SECOND, 0) //
        currentDate.set(Calendar.MILLISECOND, 0) //

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) //

        filteredEvents.retainAll { event ->
            try {
                val eventDateTimeStr = event.date //
                val eventDate: Date? = dateFormat.parse(eventDateTimeStr) //

                if(eventDate != null) {
                    val eventCalendar = Calendar.getInstance() //
                    eventCalendar.time = eventDate //
                    eventCalendar.set(Calendar.HOUR_OF_DAY, 0) //
                    eventCalendar.set(Calendar.MINUTE, 0) //
                    eventCalendar.set(Calendar.SECOND, 0) //
                    eventCalendar.set(Calendar.MILLISECOND, 0) //

                    !eventCalendar.before(currentDate) // Pokaži ako je danas ili u budućnosti
                } else {
                    true
                }
            } catch (e: Exception) {
                true
            }
        }

        val selectedLocationId = locations.getOrNull(locationFilterSpinner.selectedItemPosition)?.first //
        if (!selectedLocationId.isNullOrEmpty()) {
            filteredEvents.retainAll { it.locationId == selectedLocationId } //
        }

        val typeIdFromSpinner = eventTypes.getOrNull(typeFilterSpinner.selectedItemPosition)?.first

        if (userPreferredEventTypeIds.isNotEmpty()) {
            filteredEvents.retainAll { event -> userPreferredEventTypeIds.contains(event.typeId) }
            if (!typeIdFromSpinner.isNullOrEmpty()) {
                filteredEvents.retainAll { event -> event.typeId == typeIdFromSpinner }
            }
        } else {
            if (!typeIdFromSpinner.isNullOrEmpty()) {
                filteredEvents.retainAll { it.typeId == typeIdFromSpinner } //
            }
        }

        val searchQuery = eventSearchEditText.text.toString().trim().lowercase(Locale.getDefault()) //
        if (searchQuery.isNotEmpty()) {
            filteredEvents.retainAll { event ->
                event.name.lowercase(Locale.getDefault()).contains(searchQuery) || //
                        event.description.lowercase(Locale.getDefault()).contains(searchQuery) //
            }
        }

        try {
            filteredEvents.sortWith(compareBy { event -> //
                dateFormat.parse(event.date) //
            })
        } catch (e: Exception) {
            // Log error or handle if date parsing fails during sort
        }

        eventAdapter.updateEvents(filteredEvents) //

        if (filteredEvents.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyViewText.text = if (allEvents.isEmpty() && !searchQuery.isNotEmpty() && selectedLocationId.isNullOrEmpty() && typeIdFromSpinner.isNullOrEmpty() && userPreferredEventTypeIds.isEmpty()) {
                "No events available at the moment."
            } else {
                "No events match your criteria."
            }
            emptyViewText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyViewText.visibility = View.GONE
        }
    }

    private fun clearFilters() {
        locationFilterSpinner.setSelection(0) //
        typeFilterSpinner.setSelection(0) //
        eventSearchEditText.text.clear() //
        // applyFilters() će biti automatski pozvan zbog listenera
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> { //
                getLastLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> { //
                Toast.makeText(this, "Location permission is needed to auto-filter by city.", Toast.LENGTH_LONG).show() //
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) //
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) //
            }
        }
    }

    private fun getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { //
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        getCityFromLocation(location.latitude, location.longitude) //
                    }
                }
        }
    }

    private fun getCityFromLocation(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault()) //
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1) //
            if (!addresses.isNullOrEmpty()) {
                val city = addresses[0].locality //
                if (city != null) {
                    val cityIndex = locations.indexOfFirst { it.second.equals(city, ignoreCase = true) }
                    if (cityIndex != -1 && cityIndex < locationFilterSpinner.adapter.count) {
                        // Provjeri da li je korisnik već ručno odabrao lokaciju
                        if (locationFilterSpinner.selectedItemPosition == 0) { // Ako je "All Locations"
                            locationFilterSpinner.setSelection(cityIndex)
                            Toast.makeText(this, "Auto-filtered by $city based on your location.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Greška pri dohvaćanju grada, ne prikazuj Toast korisniku
        }
    }
}