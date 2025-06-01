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

    private var allUsers: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList()
    private var currentPage = 0
    private val usersPerPage = 5

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

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "User List"

        recyclerView.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter(
            emptyList(),
            onDeleteClick = { userId -> deleteUser(userId) },
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

        checkAdminStatus()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkAdminStatus() {
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
                        loadAllUsers() // Load all users for admin
                    } else {
                        Toast.makeText(this, "Access denied: Admin rights required", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking permissions", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadAllUsers() {
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
                applySearchAndPagination()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading users: ${it.message}", Toast.LENGTH_SHORT).show()
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
        currentPage = 0
        displayUsersForCurrentPage()
    }

    private fun displayUsersForCurrentPage() {
        val startIndex = currentPage * usersPerPage
        val endIndex = (startIndex + usersPerPage).coerceAtMost(filteredUsers.size)
        val usersToDisplay = filteredUsers.subList(startIndex, endIndex)
        userAdapter.updateUsers(usersToDisplay)

        val totalPages = (filteredUsers.size + usersPerPage - 1) / usersPerPage
        pageInfoTextView.text = "Page ${currentPage + 1}/$totalPages"

        prevPageButton.isEnabled = currentPage > 0
        nextPageButton.isEnabled = (currentPage + 1) * usersPerPage < filteredUsers.size
    }

    private fun deleteUser(userId: String) {
        val currentUser = auth.currentUser
        if (userId == currentUser?.uid) {
            Toast.makeText(this, "You cannot delete your own account", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(userId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "User deleted successfully", Toast.LENGTH_SHORT).show()
                loadAllUsers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUpdateRoleDialog(userId: String, currentRole: String) {
        val currentUser = auth.currentUser
        if (userId == currentUser?.uid) {
            Toast.makeText(this, "You cannot change your own role", Toast.LENGTH_SHORT).show()
            return
        }

        val roles = arrayOf("user", "admin")
        var selectedRole = if (currentRole == "admin") 1 else 0

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Update User Role")
            .setSingleChoiceItems(roles, selectedRole) { _, which ->
                selectedRole = which
            }
            .setPositiveButton("Update") { dialog, _ ->
                val newRole = roles[selectedRole]
                updateUserRole(userId, newRole)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.show()
    }

    private fun updateUserRole(userId: String, newRole: String) {
        db.collection("users").document(userId)
            .update("role", newRole)
            .addOnSuccessListener {
                Toast.makeText(this, "User role updated successfully", Toast.LENGTH_SHORT).show()
                loadAllUsers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating role: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    data class User(
        val id: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val role: String
    )

    class UserAdapter(
        private var users: List<User>,
        private val onDeleteClick: (String) -> Unit,
        private val onUpdateRoleClick: (String, String) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val usernameText: TextView = itemView.findViewById(R.id.username)
            val deleteButton: Button = itemView.findViewById(R.id.delete_button)
            val updateRoleButton: Button = itemView.findViewById(R.id.update_role_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            holder.usernameText.text = "${user.firstName} ${user.lastName}\n${user.email}\nRole: ${user.role}"
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