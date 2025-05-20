package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : AppCompatActivity() {

    private lateinit var welcomeTextView: TextView
    private lateinit var logoutBtn: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)

        auth = FirebaseAuth.getInstance()

        welcomeTextView = findViewById(R.id.welcomeTextView)
        logoutBtn = findViewById(R.id.logoutBtn)
        val btnUsers = findViewById<Button>(R.id.btnManageUsers)

        val currentUser = auth.currentUser

        if (currentUser != null) {
            welcomeTextView.text = "Welcome, ${currentUser.email}!"
        } else {
            welcomeTextView.text = "Welcome to our app!"
        }

        logoutBtn.setOnClickListener {
            auth.signOut()

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        btnUsers.setOnClickListener{
            val intent = Intent(this, UserList::class.java)
            startActivity(intent)
        }
    }
}