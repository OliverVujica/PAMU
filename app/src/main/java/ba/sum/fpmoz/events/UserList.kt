package ba.sum.fpmoz.events

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserList : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userListLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_list)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userListLayout = findViewById(R.id.userListLayout)

        checkAdminStatus()
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
                        loadUsers()
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

    private fun loadUsers() {
        db.collection("users").get()
            .addOnSuccessListener { result ->
                userListLayout.removeAllViews()

                for (document in result) {
                    val email = document.getString("email") ?: continue
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val role = document.getString("role") ?: "user"
                    val userId = document.id

                    val userView = layoutInflater.inflate(R.layout.item_user, null)
                    val emailText = userView.findViewById<TextView>(R.id.textEmail)
                    val deleteButton = userView.findViewById<Button>(R.id.btnDelete)
                    val updateRoleButton = userView.findViewById<Button>(R.id.btnUpdateRole)

                    emailText.text = "$firstName $lastName\n$email\nRole: $role"

                    deleteButton.setOnClickListener {
                        deleteUser(userId)
                    }

                    updateRoleButton.setOnClickListener {
                        showUpdateRoleDialog(userId, role)
                    }

                    userListLayout.addView(userView)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading users: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteUser(userId: String) {
        val currentUser = auth.currentUser

        // Prevent admin from deleting themselves
        if (userId == currentUser?.uid) {
            Toast.makeText(this, "You cannot delete your own account", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(userId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "User deleted successfully", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUpdateRoleDialog(userId: String, currentRole: String) {
        val currentUser = auth.currentUser

        // Prevent admin from changing their own role
        if (userId == currentUser?.uid) {
            Toast.makeText(this, "You cannot change your own role", Toast.LENGTH_SHORT).show()
            return
        }

        val roles = arrayOf("user", "admin")
        var selectedRole = if (currentRole == "admin") 1 else 0

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
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
                loadUsers() // Refresh the list
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating role: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUpdatePasswordDialog(userId: String, userEmail: String) {
        val passwordEditText = android.widget.EditText(this)
        passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordEditText.hint = "New password"

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 30)
        layout.addView(passwordEditText)

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Update Password for $userEmail")
            .setView(layout)
            .setPositiveButton("Update") { dialog, _ ->
                val newPassword = passwordEditText.text.toString()
                if (newPassword.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                } else {
                    updateUserPassword(userId, userEmail, newPassword)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.show()
    }

    private fun updateUserPassword(userId: String, email: String, newPassword: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send reset email: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}