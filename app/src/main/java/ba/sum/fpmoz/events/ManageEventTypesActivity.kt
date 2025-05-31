package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class ManageEventTypesActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var newEventTypeNameEditText: TextInputEditText
    private lateinit var addNewEventTypeButton: Button
    private lateinit var eventTypesRecyclerView: RecyclerView
    private lateinit var eventTypeAdapter: EventTypeAdapter
    private var currentEventTypes: MutableList<EventType> = mutableListOf()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_event_types)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        newEventTypeNameEditText = findViewById(R.id.new_event_type_name_edit_text)
        addNewEventTypeButton = findViewById(R.id.btnAddNewEventType)
        eventTypesRecyclerView = findViewById(R.id.event_types_recycler_view)

        drawerLayout = findViewById(R.id.drawerLayoutManageEventTypes)
        navigationView = findViewById(R.id.navigationViewManageEventTypes)
        toolbar = findViewById(R.id.toolbarManageEventTypes)

        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, WelcomeActivity::class.java))
                    finish()
                }
                R.id.nav_add_event -> {
                    startActivity(Intent(this, AddEventActivity::class.java))
                    finish()
                }
                R.id.nav_manage_event_types -> {
                    // Trenutna aktivnost
                }
                R.id.nav_user_list -> {
                    startActivity(Intent(this, UserList::class.java))
                    finish()
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


        eventTypeAdapter = EventTypeAdapter(currentEventTypes) { eventTypeId ->
            confirmDeleteEventType(eventTypeId)
        }
        eventTypesRecyclerView.layoutManager = LinearLayoutManager(this)
        eventTypesRecyclerView.adapter = eventTypeAdapter

        addNewEventTypeButton.setOnClickListener {
            val typeName = newEventTypeNameEditText.text.toString().trim()
            if (typeName.isNotEmpty()) {
                addEventTypeToFirestore(typeName)
            } else {
                Toast.makeText(this, "Event type name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        checkUserRoleAndLoadData()
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


    private fun checkUserRoleAndLoadData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val role = document?.getString("role")
                if (role == "admin") {
                    navigationView.menu.findItem(R.id.nav_user_list).isVisible = true
                    navigationView.menu.findItem(R.id.nav_add_event).isVisible = true
                    navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = true
                    loadEventTypesFromFirestore()
                } else {
                    Toast.makeText(this, "Access denied: Admin rights required", Toast.LENGTH_SHORT).show()
                    // Sakrij opcije koje nisu za običnog korisnika ili ga preusmjeri
                    navigationView.menu.findItem(R.id.nav_user_list).isVisible = false
                    navigationView.menu.findItem(R.id.nav_add_event).isVisible = false
                    navigationView.menu.findItem(R.id.nav_manage_event_types).isVisible = false
                    // Možda preusmjeriti na WelcomeActivity ako nije admin
                    startActivity(Intent(this, WelcomeActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check user role: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }


    private fun loadEventTypesFromFirestore() {
        db.collection("event_types").get()
            .addOnSuccessListener { result: QuerySnapshot ->
                currentEventTypes.clear()
                for (document in result) {
                    currentEventTypes.add(EventType(document.id, document.getString("name") ?: ""))
                }
                eventTypeAdapter.updateEventTypes(currentEventTypes.sortedBy { it.name })
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading event types: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addEventTypeToFirestore(typeName: String) {
        val eventType = hashMapOf("name" to typeName)
        db.collection("event_types").add(eventType)
            .addOnSuccessListener {
                Toast.makeText(this, "Event type added successfully", Toast.LENGTH_SHORT).show()
                newEventTypeNameEditText.text?.clear()
                loadEventTypesFromFirestore() // Osvježi listu
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error adding event type: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteEventType(eventTypeId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Event Type")
            .setMessage("Are you sure you want to delete this event type? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteEventTypeFromFirestore(eventTypeId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEventTypeFromFirestore(eventTypeId: String) {
        db.collection("event_types").document(eventTypeId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Event type deleted successfully", Toast.LENGTH_SHORT).show()
                loadEventTypesFromFirestore() // Osvježi listu
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error deleting event type: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}