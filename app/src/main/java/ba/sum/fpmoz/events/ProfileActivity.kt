package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null

    private lateinit var toolbar: Toolbar
    private lateinit var nameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var changePasswordButton: Button
    private lateinit var eventTypesRecyclerView: RecyclerView
    private lateinit var noEventTypesTextView: TextView
    private lateinit var savePreferencesButton: Button
    private lateinit var loadingProgressBar: ProgressBar

    private lateinit var eventTypePreferenceAdapter: EventTypePreferenceAdapter
    private var allEventTypes: MutableList<ba.sum.fpmoz.events.EventType> = mutableListOf()
    private var userPreferredEventTypeIds: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "You need to be logged in to view your profile.", Toast.LENGTH_LONG).show()
            // Preusmjeri na LoginActivity ili zatvori aktivnost
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        toolbar = findViewById(R.id.toolbar_profile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        nameTextView = findViewById(R.id.profile_name_textview)
        emailTextView = findViewById(R.id.profile_email_textview)
        changePasswordButton = findViewById(R.id.profile_change_password_button)
        eventTypesRecyclerView = findViewById(R.id.profile_event_types_recyclerview)
        noEventTypesTextView = findViewById(R.id.profile_no_event_types_textview)
        savePreferencesButton = findViewById(R.id.profile_save_preferences_button)
        loadingProgressBar = findViewById(R.id.profile_loading_progressbar)

        setupRecyclerView()
        loadInitialData()

        changePasswordButton.setOnClickListener {
            sendPasswordReset()
        }

        savePreferencesButton.setOnClickListener {
            saveUserPreferences()
        }
    }

    private fun setupRecyclerView() {
        eventTypePreferenceAdapter = EventTypePreferenceAdapter(mutableListOf())
        eventTypesRecyclerView.layoutManager = LinearLayoutManager(this)
        eventTypesRecyclerView.adapter = eventTypePreferenceAdapter
    }

    private fun loadInitialData() {
        loadingProgressBar.visibility = View.VISIBLE
        savePreferencesButton.isEnabled = false // Onemogući dok se podaci učitavaju

        loadUserProfile() // Ovo će također pokrenuti učitavanje tipova događaja nakon što se dohvate preferencije
    }

    private fun loadUserProfile() {
        currentUser?.uid?.let { userId ->
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: "N/A"
                        nameTextView.text = firstName
                        emailTextView.text = currentUser?.email ?: "N/A"

                        @Suppress("UNCHECKED_CAST")
                        val preferredIdsFromDb = document.get("preferredEventTypeIds") as? List<String>
                        userPreferredEventTypeIds.clear()
                        preferredIdsFromDb?.let { userPreferredEventTypeIds.addAll(it) }

                    } else {
                        nameTextView.text = "User"
                        emailTextView.text = currentUser?.email ?: "N/A"
                        Toast.makeText(this, "User profile data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    nameTextView.text = "Error"
                    emailTextView.text = currentUser?.email ?: "N/A"
                }
                .addOnCompleteListener {
                    // Nakon što su podaci o korisniku i preferencijama učitani, učitaj sve tipove događaja
                    loadAllEventTypes()
                }
        }
    }

    private fun loadAllEventTypes() {
        db.collection("event_types").orderBy("name").get()
            .addOnSuccessListener { result ->
                allEventTypes.clear()
                result.forEach { document ->
                    allEventTypes.add(EventType(document.id, document.getString("name") ?: "Unknown Type"))
                }

                if (allEventTypes.isEmpty()) {
                    eventTypesRecyclerView.visibility = View.GONE
                    noEventTypesTextView.visibility = View.VISIBLE
                } else {
                    eventTypesRecyclerView.visibility = View.VISIBLE
                    noEventTypesTextView.visibility = View.GONE
                    eventTypePreferenceAdapter.updateData(allEventTypes, userPreferredEventTypeIds)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load event types: ${e.message}", Toast.LENGTH_SHORT).show()
                eventTypesRecyclerView.visibility = View.GONE
                noEventTypesTextView.visibility = View.VISIBLE
                noEventTypesTextView.text = "Error loading event types."
            }
            .addOnCompleteListener {
                loadingProgressBar.visibility = View.GONE
                savePreferencesButton.isEnabled = true
            }
    }

    private fun sendPasswordReset() {
        currentUser?.email?.let { email ->
            if (email.isNotEmpty()) {
                loadingProgressBar.visibility = View.VISIBLE // Pokazi progress dok se šalje email
                changePasswordButton.isEnabled = false

                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        loadingProgressBar.visibility = View.GONE
                        changePasswordButton.isEnabled = true
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Email address not available.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserPreferences() {
        currentUser?.uid?.let { userId ->
            val selectedIds = eventTypePreferenceAdapter.getSelectedTypeIds()

            loadingProgressBar.visibility = View.VISIBLE
            savePreferencesButton.isEnabled = false

            val preferencesUpdate = hashMapOf("preferredEventTypeIds" to selectedIds)

            db.collection("users").document(userId)
                .set(preferencesUpdate, SetOptions.merge()) // Koristi merge da ne prebrišeš ostale podatke korisnika
                .addOnSuccessListener {
                    Toast.makeText(this, "Preferences saved successfully!", Toast.LENGTH_SHORT).show()
                    userPreferredEventTypeIds.clear()
                    userPreferredEventTypeIds.addAll(selectedIds) // Ažuriraj lokalno stanje
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save preferences: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    loadingProgressBar.visibility = View.GONE
                    savePreferencesButton.isEnabled = true
                }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}