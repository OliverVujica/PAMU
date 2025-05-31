package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class InterestedEventsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private lateinit var emptyListTextView: TextView

    private var interestedEventsList: MutableList<Event> = mutableListOf()
    private var interestedEventIdsListener: ListenerRegistration? = null
    private var eventDetailsListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()
    private var currentUserInterestedEventIds: MutableSet<String> = mutableSetOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interested_events)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        drawerLayout = findViewById(R.id.drawerLayoutInterestedEvents)
        navigationView = findViewById(R.id.navigationViewInterestedEvents)
        toolbar = findViewById(R.id.toolbarInterestedEvents)
        recyclerView = findViewById(R.id.interested_events_recycler_view)
        emptyListTextView = findViewById(R.id.empty_interested_list_text)

        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavigationDrawer()

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = EventAdapter(
            interestedEventsList,
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

        if (auth.currentUser == null) {
            Toast.makeText(this, "Please log in to see your interested events.", Toast.LENGTH_LONG).show()
            finish() // Ili preusmjeri na LoginActivity
            return
        }

        // Prvo dohvati set ID-eva, pa onda slušaj promjene na tim ID-evima
        fetchAndListenToInterestedEventIds()
    }

    private fun fetchAndListenToInterestedEventIds() {
        val userId = auth.currentUser?.uid ?: return

        // Listener za promjene u listi zainteresiranih ID-eva
        interestedEventIdsListener = db.collection("users").document(userId)
            .collection("interested_events")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error fetching interested event IDs: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val newInterestedIds = snapshots?.documents?.map { it.id }?.toSet() ?: emptySet()
                currentUserInterestedEventIds.clear()
                currentUserInterestedEventIds.addAll(newInterestedIds)

                // Ukloni listenere za događaje koji više nisu u listi
                val idsToRemove = eventDetailsListeners.keys.filterNot { it in newInterestedIds }
                idsToRemove.forEach { eventId ->
                    eventDetailsListeners.remove(eventId)?.remove()
                    interestedEventsList.removeAll { it.id == eventId }
                }

                // Dodaj listenere za nove događaje u listi
                newInterestedIds.filterNot { it in eventDetailsListeners.keys }.forEach { eventId ->
                    listenToEventDetails(eventId)
                }
                updateRecyclerViewVisibility()
                eventAdapter.notifyDataSetChanged() // Ažuriraj adapter jer se lista mogla promijeniti
            }
    }


    private fun listenToEventDetails(eventId: String) {
        val eventListener = db.collection("events").document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Možda je događaj obrisan, ukloni ga iz liste ako postoji
                    val index = interestedEventsList.indexOfFirst { it.id == eventId }
                    if (index != -1) {
                        interestedEventsList.removeAt(index)
                        eventAdapter.notifyItemRemoved(index)
                    }
                    eventDetailsListeners.remove(eventId)?.remove() // Ukloni listener
                    updateRecyclerViewVisibility()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val event = snapshot.toObject(Event::class.java)?.copy(
                        id = snapshot.id, // Osiguraj da je ID postavljen
                        isCurrentUserInterested = true // Svi događaji u ovoj listi su "interested"
                    )
                    if (event != null) {
                        val existingIndex = interestedEventsList.indexOfFirst { it.id == event.id }
                        if (existingIndex != -1) {
                            interestedEventsList[existingIndex] = event
                            eventAdapter.notifyItemChanged(existingIndex)
                        } else {
                            interestedEventsList.add(event)
                            // Sortiraj listu (npr. po datumu)
                            interestedEventsList.sortBy { it.date }
                            eventAdapter.notifyDataSetChanged() // Koristi notifyDataSetChanged jer se redoslijed može promijeniti
                        }
                    }
                } else {
                    // Događaj ne postoji (možda je obrisan), ukloni ga iz liste
                    val index = interestedEventsList.indexOfFirst { it.id == eventId }
                    if (index != -1) {
                        interestedEventsList.removeAt(index)
                        eventAdapter.notifyItemRemoved(index)
                    }
                    eventDetailsListeners.remove(eventId)?.remove()
                }
                updateRecyclerViewVisibility()
            }
        eventDetailsListeners[eventId] = eventListener
    }


    private fun updateRecyclerViewVisibility() {
        if (interestedEventsList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyListTextView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyListTextView.visibility = View.GONE
        }
    }

    private fun toggleInterestStatus(event: Event, position: Int) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "You need to be logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val eventRef = db.collection("events").document(event.id)
        val userInterestedEventRef = db.collection("users").document(userId)
            .collection("interested_events").document(event.id)

        // S obzirom da smo na listi zainteresiranih, klik uvijek znači "unmark"
        db.runTransaction { transaction ->
            val currentEventSnapshot = transaction.get(eventRef)
            var currentInterestedCount = currentEventSnapshot.getLong("interestedCount")?.toInt() ?: 0

            transaction.delete(userInterestedEventRef) // Ukloni iz zainteresiranih korisnika
            currentInterestedCount = (currentInterestedCount - 1).coerceAtLeast(0)
            transaction.update(eventRef, "interestedCount", currentInterestedCount) // Dekrementiraj brojač na eventu

            currentInterestedCount // Vrati samo novi count, status je uvijek false nakon ovoga
        }.addOnSuccessListener { newCount ->
            // Uklanjanje iz lokalne liste i adaptera će se dogoditi automatski zbog Firestore listenera
            // na user's interested_events.
            // Samo prikaži poruku.
            Toast.makeText(this, "Removed from your interested list", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update interest: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupNavigationDrawer() {
        val menu = navigationView.menu
        val currentUser = auth.currentUser

        menu.findItem(R.id.nav_my_list).isVisible = currentUser != null
        menu.findItem(R.id.nav_logout).isVisible = currentUser != null

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get().addOnSuccessListener { doc ->
                val isAdmin = doc?.getString("role") == "admin"
                menu.findItem(R.id.nav_add_event).isVisible = isAdmin
                menu.findItem(R.id.nav_manage_event_types).isVisible = isAdmin
                menu.findItem(R.id.nav_user_list).isVisible = isAdmin
            }.addOnFailureListener {
                // Greška pri dohvaćanju uloge, sakrij admin opcije
                menu.findItem(R.id.nav_add_event).isVisible = false
                menu.findItem(R.id.nav_manage_event_types).isVisible = false
                menu.findItem(R.id.nav_user_list).isVisible = false
            }
        } else {
            // Gost
            menu.findItem(R.id.nav_add_event).isVisible = false
            menu.findItem(R.id.nav_manage_event_types).isVisible = false
            menu.findItem(R.id.nav_user_list).isVisible = false
        }


        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
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
                    startActivity(Intent(this, ManageEventTypesActivity::class.java))
                    finish()
                }
                R.id.nav_my_list -> { /* Trenutna aktivnost */ }
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
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interestedEventIdsListener?.remove()
        eventDetailsListeners.values.forEach { it.remove() }
        eventDetailsListeners.clear()
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
}