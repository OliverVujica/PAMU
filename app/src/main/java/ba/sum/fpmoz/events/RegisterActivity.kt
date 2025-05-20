package ba.sum.fpmoz.events

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var registerNameTxt: EditText
    private lateinit var registerLastnameTxt: EditText
    private lateinit var registerEmailTxt: EditText
    private lateinit var registerPasswordTxt: EditText
    private lateinit var registerCnfPasswordTxt: EditText
    private lateinit var registerSubmitBtn: Button
    private lateinit var googleSignInBtn: Button
    private lateinit var open_goToLogin: TextView

    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        registerNameTxt = findViewById(R.id.registerNameTxt)
        registerLastnameTxt = findViewById(R.id.registerLastnameTxt)
        registerEmailTxt = findViewById(R.id.registerEmailTxt)
        registerPasswordTxt = findViewById(R.id.registerPasswordTxt)
        registerCnfPasswordTxt = findViewById(R.id.registerCnfPasswordTxt)
        registerSubmitBtn = findViewById(R.id.registerSubmitBtn)
        googleSignInBtn = findViewById(R.id.googleSignInBtn)
        open_goToLogin = findViewById(R.id.goToLogin)

        registerSubmitBtn.setOnClickListener {
            val firstName = registerNameTxt.text.toString()
            val lastName = registerLastnameTxt.text.toString()
            val email = registerEmailTxt.text.toString()
            val password = registerPasswordTxt.text.toString()
            val cnfPassword = registerCnfPasswordTxt.text.toString()

            if (firstName.isNotEmpty() && lastName.isNotEmpty() && email.isNotEmpty()
                && password.isNotEmpty() && cnfPassword.isNotEmpty()) {
                if (password == cnfPassword) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val userId = auth.currentUser?.uid
                                val user = hashMapOf(
                                    "firstName" to firstName,
                                    "lastName" to lastName,
                                    "email" to email,
                                    "role" to "user"
                                )

                                userId?.let {
                                    db.collection("users").document(it)
                                        .set(user)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                                            val intent = Intent(this, LoginActivity::class.java)
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this, "Failed to store user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            } else {
                                val errorMessage = task.exception?.message
                                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Passwords don't match!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        googleSignInBtn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, RC_SIGN_IN)
            }
        }

        open_goToLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        val userData = hashMapOf(
                            "firstName" to (user.displayName?.split(" ")?.getOrNull(0) ?: ""),
                            "lastName" to (user.displayName?.split(" ")?.getOrNull(1) ?: ""),
                            "email" to user.email,
                            "role" to "user"
                        )

                        db.collection("users").document(it.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Google Registration successful", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to store user data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Google Sign-In failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}