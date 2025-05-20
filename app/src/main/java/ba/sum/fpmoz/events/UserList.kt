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
import com.google.firebase.firestore.FirebaseFirestore

class UserList : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var userListLayout: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_user_list)

        db = FirebaseFirestore.getInstance()
        userListLayout = findViewById(R.id.userListLayout)

        loadUsers()
    }

    private fun loadUsers(){
        db.collection("users").get()
            .addOnSuccessListener { result ->
                userListLayout.removeAllViews()

                for (document in result) {
                    val email = document.getString("email") ?: continue
                    val name = document.getString("name") ?: "Nema imena"
                    val userId = document.id

                    val userView = layoutInflater.inflate(R.layout.item_user, null)
                    val emailText = userView.findViewById<TextView>(R.id.textEmail)
                    val deleteButton = userView.findViewById<Button>(R.id.btnDelete)

                    emailText.text = "$name\n$email"

                    deleteButton.setOnClickListener{
                        deleteUser(userId)
                    }

                    userListLayout.addView(userView)
                }
            }

            .addOnFailureListener {
                Toast.makeText(this, "Greska pri ucitavanju korisnika", Toast.LENGTH_SHORT).show()

            }
    }

    private fun deleteUser(userId: String) {
        db.collection("users").document(userId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "KOrisnik obrisan", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .addOnFailureListener{
                Toast.makeText(this, "Greska: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}