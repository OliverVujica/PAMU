package ba.sum.fpmoz.events

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
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
    private lateinit var nameEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var typeEditText: EditText
    private lateinit var locationEditText: EditText
    private lateinit var addEventButton: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_event)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        nameEditText = findViewById(R.id.nameEditText)
        dateEditText = findViewById(R.id.dateEditText)
        typeEditText = findViewById(R.id.typeEditText)
        locationEditText = findViewById(R.id.locationEditText)
        addEventButton = findViewById(R.id.addEventButton)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        recyclerView = findViewById(R.id.eventRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = EventAdapter(emptyList(), R.layout.item_event, onDeleteClick = { eventId ->
            deleteEvent(eventId)
        })
        recyclerView.adapter = eventAdapter

        dateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                    dateEditText.setText(formattedDate)
                },
                year,
                month,
                day
            )
            datePickerDialog.show()
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
                R.id.nav_manage_users -> {
                    val intent = Intent(this, UserList::class.java)
                    startActivity(intent)
                }
                R.id.nav_manage_events -> {
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

        addEventButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val date = dateEditText.text.toString().trim()
            val type = typeEditText.text.toString().trim()
            val location = locationEditText.text.toString().trim()

            if (name.isEmpty() || date.isEmpty() || type.isEmpty() || location.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val event = hashMapOf(
                "name" to name,
                "date" to date,
                "type" to type,
                "location" to location
            )

            db.collection("events").add(event)
                .addOnSuccessListener {
                    Toast.makeText(this, "Event added successfully", Toast.LENGTH_SHORT).show()
                    nameEditText.text.clear()
                    dateEditText.text.clear()
                    typeEditText.text.clear()
                    locationEditText.text.clear()
                    loadEvents()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error adding event: ${it.message}", Toast.LENGTH_SHORT).show()
                }
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
                        navigationView.menu.findItem(R.id.nav_manage_users).isVisible = true
                        navigationView.menu.findItem(R.id.nav_manage_events).isVisible = true
                        loadEvents()
                    } else {
                        navigationView.menu.findItem(R.id.nav_manage_users).isVisible = false
                        navigationView.menu.findItem(R.id.nav_manage_events).isVisible = false
                        Toast.makeText(this, "Access denied: Admin rights required", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    navigationView.menu.findItem(R.id.nav_manage_users).isVisible = false
                    navigationView.menu.findItem(R.id.nav_manage_events).isVisible = false
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                navigationView.menu.findItem(R.id.nav_manage_users).isVisible = false
                navigationView.menu.findItem(R.id.nav_manage_events).isVisible = false
                Toast.makeText(this, "Failed to check user role", Toast.LENGTH_SHORT).show()
                finish()
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
                        type = document.getString("type") ?: "",
                        location = document.getString("location") ?: ""
                    )
                }
                eventAdapter.updateEvents(events)
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