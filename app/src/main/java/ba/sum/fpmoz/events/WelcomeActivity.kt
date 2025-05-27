package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
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

class WelcomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var welcomeTextView: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter

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

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = EventAdapter(emptyList(), R.layout.item_event_welcome) { /* No delete action in WelcomeActivity */ }
        recyclerView.adapter = eventAdapter

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                }
                R.id.nav_user_list -> {
                    val intent = Intent(this, UserList::class.java)
                    startActivity(intent)
                }
                R.id.nav_add_event -> {
                    val intent = Intent(this, AddEventActivity::class.java)
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
            loadEvents()
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
        currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val role = document.getString("role")
                        if (role == "admin") {
                            navigationView.menu.findItem(R.id.nav_user_list).isVisible = true
                            navigationView.menu.findItem(R.id.nav_add_event).isVisible = true
                        } else {
                            navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                            navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                        }
                    } else {
                        navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                        navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                    }
                    loadEvents()
                }
                .addOnFailureListener {
                    navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                    navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                    Toast.makeText(this, "Failed to check user role", Toast.LENGTH_SHORT).show()
                    loadEvents()
                }
        } ?: run {
            navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
            navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
            loadEvents()
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
                        locationName = document.getString("locationName") ?: ""
                    )
                }
                eventAdapter.updateEvents(events.sortedBy { it.date })
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading events: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}