package ba.sum.fpmoz.events

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class UserList : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var userSearchEditText: EditText
    private lateinit var prevPageButton: Button
    private lateinit var nextPageButton: Button
    private lateinit var pageInfoTextView: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var emptyViewText: TextView


    private var allUsers: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList()
    private var currentPage = 0
    private val usersPerPage = 5 // Ili neki drugi broj po želji

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_list)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        recyclerView = findViewById(R.id.user_list)
        toolbar = findViewById(R.id.toolbar)
        userSearchEditText = findViewById(R.id.user_search_edit_text)
        prevPageButton = findViewById(R.id.prev_page_button)
        nextPageButton = findViewById(R.id.next_page_button)
        pageInfoTextView = findViewById(R.id.page_info_text_view)

        // Inicijalizacija loading UI elemenata
        loadingProgressBar = findViewById(R.id.user_loading_progress_bar)
        emptyViewText = findViewById(R.id.user_empty_view_text)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "User List"

        recyclerView.layoutManager = LinearLayoutManager(this)
        // Inicijalno sakrij RecyclerView i emptyViewText
        recyclerView.visibility = View.GONE
        emptyViewText.visibility = View.GONE

        userAdapter = UserAdapter(
            emptyList(),
            onDeleteClick = { userId -> confirmDeleteUser(userId) },
            onUpdateRoleClick = { userId, role -> showUpdateRoleDialog(userId, role) }
        )
        recyclerView.adapter = userAdapter

        userSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchAndPagination()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        prevPageButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                displayUsersForCurrentPage()
            }
        }

        nextPageButton.setOnClickListener {
            if ((currentPage + 1) * usersPerPage < filteredUsers.size) {
                currentPage++
                displayUsersForCurrentPage()
            }
        }
        // Pokaži ProgressBar prije dohvaćanja podataka
        loadingProgressBar.visibility = View.VISIBLE
        checkAdminStatus()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed() //
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkAdminStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            loadingProgressBar.visibility = View.GONE // Sakrij ako korisnik nije prijavljen
            finish()
            return
        }

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    if (role == "admin") {
                        loadAllUsers() // Load all users for admin
                    } else {
                        Toast.makeText(this, "Access denied: Admin rights required", Toast.LENGTH_SHORT).show()
                        loadingProgressBar.visibility = View.GONE
                        finish()
                    }
                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    loadingProgressBar.visibility = View.GONE
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking permissions: ${it.message}", Toast.LENGTH_SHORT).show()
                loadingProgressBar.visibility = View.GONE
                finish()
            }
    }

    private fun loadAllUsers() {
        loadingProgressBar.visibility = View.VISIBLE // Pokaži ProgressBar
        recyclerView.visibility = View.GONE
        emptyViewText.visibility = View.GONE

        db.collection("users").get()
            .addOnSuccessListener { result ->
                allUsers = result.map { document ->
                    User(
                        id = document.id,
                        email = document.getString("email") ?: "",
                        firstName = document.getString("firstName") ?: "",
                        lastName = document.getString("lastName") ?: "",
                        role = document.getString("role") ?: "user"
                    )
                }
                // applySearchAndPagination će sakriti ProgressBar i prikazati listu/prazno stanje
                applySearchAndPagination()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading users: ${it.message}", Toast.LENGTH_SHORT).show()
                allUsers = emptyList() // Osiguraj praznu listu u slučaju greške
                applySearchAndPagination() // Pokaži poruku o grešci/praznom stanju
            }
    }

    private fun applySearchAndPagination() {
        val query = userSearchEditText.text.toString().trim().lowercase(Locale.getDefault())
        filteredUsers = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.firstName.lowercase(Locale.getDefault()).contains(query) ||
                        user.lastName.lowercase(Locale.getDefault()).contains(query) ||
                        user.email.lowercase(Locale.getDefault()).contains(query)
            }
        }
        currentPage = 0 // Resetiraj na prvu stranicu nakon pretrage/filtriranja
        displayUsersForCurrentPage()
    }

    private fun displayUsersForCurrentPage() {
        loadingProgressBar.visibility = View.GONE // Sakrij ProgressBar

        if (filteredUsers.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyViewText.text = if (userSearchEditText.text.toString().trim().isNotEmpty()) {
                "No users match your search."
            } else {
                "No users found."
            }
            emptyViewText.visibility = View.VISIBLE
            // Sakrij kontrole za paginaciju ako nema korisnika
            findViewById<View>(R.id.pagination_controls_layout).visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyViewText.visibility = View.GONE
            findViewById<View>(R.id.pagination_controls_layout).visibility = View.VISIBLE

            val startIndex = currentPage * usersPerPage
            val endIndex = (startIndex + usersPerPage).coerceAtMost(filteredUsers.size)
            val usersToDisplay = filteredUsers.subList(startIndex, endIndex)
            userAdapter.updateUsers(usersToDisplay)

            val totalPages = (filteredUsers.size + usersPerPage - 1) / usersPerPage
            if (totalPages <=0 ) { // Ako nema korisnika, stranica je 0/0
                pageInfoTextView.text = "Page 0/0"
            } else {
                pageInfoTextView.text = "Page ${currentPage + 1}/$totalPages"
            }


            prevPageButton.isEnabled = currentPage > 0
            nextPageButton.isEnabled = (currentPage + 1) * usersPerPage < filteredUsers.size
        }
    }


    private fun confirmDeleteUser(userId: String) {
        val currentUser = auth.currentUser
        if (userId == currentUser?.uid) {
            Toast.makeText(this, "You cannot delete your own account", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete this user? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteUser(userId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun deleteUser(userId: String) {
        // Ovdje možete prikazati ProgressDialog ili onemogućiti gumbe u adapteru
        // Za jednostavnost, koristit ćemo Toast
        Toast.makeText(this, "Deleting user...", Toast.LENGTH_SHORT).show()

        db.collection("users").document(userId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "User deleted successfully", Toast.LENGTH_SHORT).show()
                loadAllUsers() // Ponovno učitaj korisnike da se lista osvježi
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error deleting user: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUpdateRoleDialog(userId: String, currentRole: String) {
        val currentUser = auth.currentUser
        if (userId == currentUser?.uid) {
            Toast.makeText(this, "You cannot change your own role", Toast.LENGTH_SHORT).show()
            return
        }

        val roles = arrayOf("user", "admin")
        var selectedRoleIndex = if (currentRole.equals("admin", ignoreCase = true)) 1 else 0

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Update User Role")
            .setSingleChoiceItems(roles, selectedRoleIndex) { _, which ->
                selectedRoleIndex = which
            }
            .setPositiveButton("Update") { dialog, _ ->
                val newRole = roles[selectedRoleIndex]
                updateUserRole(userId, newRole)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.show()
    }

    private fun updateUserRole(userId: String, newRole: String) {
        Toast.makeText(this, "Updating role...", Toast.LENGTH_SHORT).show()
        db.collection("users").document(userId)
            .update("role", newRole)
            .addOnSuccessListener {
                Toast.makeText(this, "User role updated successfully", Toast.LENGTH_SHORT).show()
                loadAllUsers() // Ponovno učitaj korisnike
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating role: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // User data class (mora biti ista kao što UserAdapter očekuje)
    data class User(
        val id: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val role: String
    )

    // Unutar UserList klase, modificirajte UserAdapter:
    class UserAdapter(
        private var users: List<User>, // Koristi User data klasu definiranu iznad
        private val onDeleteClick: (String) -> Unit,
        private val onUpdateRoleClick: (String, String) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // Novi TextView-ovi prema item_user.xml
            val fullNameText: TextView = itemView.findViewById(R.id.user_full_name_text)
            val emailText: TextView = itemView.findViewById(R.id.user_email_text)
            val roleText: TextView = itemView.findViewById(R.id.user_role_text)

            val deleteButton: Button = itemView.findViewById(R.id.delete_button)
            val updateRoleButton: Button = itemView.findViewById(R.id.update_role_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user, parent, false) // Koristi se novi item_user.xml
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]

            // Postavljanje teksta na nove TextView-ove
            val fullName = "${user.firstName} ${user.lastName}".trim()
            holder.fullNameText.text = if (fullName.isNotEmpty()) fullName else "N/A" // Prikaži N/A ako je ime prazno
            holder.emailText.text = user.email
            holder.roleText.text = "Role: ${user.role}"

            holder.deleteButton.setOnClickListener {
                onDeleteClick(user.id)
            }
            holder.updateRoleButton.setOnClickListener {
                onUpdateRoleClick(user.id, user.role)
            }
        }

        override fun getItemCount(): Int = users.size

        fun updateUsers(newUsers: List<User>) {
            users = newUsers
            notifyDataSetChanged()
        }
    }
}