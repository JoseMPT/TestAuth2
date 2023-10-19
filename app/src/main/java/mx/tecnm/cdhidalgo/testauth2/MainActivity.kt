package mx.tecnm.cdhidalgo.testauth2

import android.content.Intent
import android.content.IntentSender
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var authStateText: TextView
    private lateinit var btnGoogle: SignInButton
    private lateinit var btnSignOut: Button

    //Variables para el uso de OneTap de Google
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    private lateinit var auth: FirebaseAuth

    //Code para relacionar el intent y su resultado
    private val codeOneTap = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authStateText = findViewById(R.id.text_auth_state)
        btnGoogle = findViewById(R.id.sign_in_button)
        btnSignOut = findViewById(R.id.btn_sign_out)

        //Se inicializan las variables con la configuración de inicio de sesión
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.web_client_id))
                    //Esto... estrictamente false desde un inicio...
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(false)
            .build()

        auth = Firebase.auth
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            authStateText.text = auth.currentUser?.email
        }

        btnGoogle.setOnClickListener {
            //Código básico que lanza la UI de One Tap o controla sus excepciones
            oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this) { result ->
                    try {
                        startIntentSenderForResult(
                            result.pendingIntent.intentSender, codeOneTap,
                            null, 0, 0, 0, null)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("Error UI", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    }
                }
                .addOnFailureListener(this) { e ->
                    // No saved credentials found. Launch the One Tap sign-up flow, or
                    // do nothing and continue presenting the signed-out UI.
                    e.localizedMessage?.let { it1 -> Log.d("No credentials", it1) }
                }
        }



        btnSignOut.setOnClickListener {
            auth.signOut()
            authStateText.text = getString(R.string.text_auth_state)
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(
        "super.onActivityResult(requestCode, resultCode, data)",
        "androidx.appcompat.app.AppCompatActivity")
    )
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //Código básico para realizar las acciones correspondientes en caso de iniciar sesión
        //exitosamemente o al haber un error. (Puede optimizarse).
        when (requestCode) {
            codeOneTap -> {
                try {
                    val credential = oneTapClient.getSignInCredentialFromIntent(data)
                    val idToken = credential.googleIdToken
                    val username = credential.id
                    when {
                        idToken != null -> {
                            // Got an ID token from Google. Use it to authenticate
                            // with your backend.
                            Log.d("Google Token", "Got ID token.")
                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                            auth.signInWithCredential(firebaseCredential)
                                .addOnCompleteListener(this) { task ->
                                    if (task.isSuccessful) {
                                        // Sign in success, update UI with the signed-in user's information
                                        // Aquí van las acciones correspondientes, si es que la autenticación salió bien
                                        Log.d("Login", "signInWithCredential:success")
                                        val user = auth.currentUser
                                        Toast.makeText(
                                            baseContext,
                                            "User logged: ${user?.email}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        authStateText.text = username
                                    } else {
                                        // If sign in fails, display a message to the user.
                                        // Acciones a realizar si falló la autenticación
                                        Log.w("Error Login", "signInWithCredential:failure", task.exception)
                                    }
                                }
                        } else -> {
                            // Shouldn't happen.
                            Log.d("No account credentials", "No ID token or password!")
                        }
                    }
                } catch (e: ApiException) {
                    // Otros errores a controlar
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> {
                            Log.d("One Tap UI", "One-tap dialog was closed.")
                            // Don't re-prompt the user.
                        }
                        CommonStatusCodes.NETWORK_ERROR -> {
                            Log.d("Network error", "One-tap encountered a network error.")
                            // Try again or just ignore.
                        } else -> {
                            Log.d("Credential error", "Couldn't get credential from result." +
                                    " (${e.localizedMessage})")
                        }
                    }
                }
            }
        }
    }
}