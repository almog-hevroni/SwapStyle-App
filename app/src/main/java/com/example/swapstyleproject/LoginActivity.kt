package com.example.swapstyleproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityLoginBinding
import com.example.swapstyleproject.utilities.BackgroundManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val repository = FirebaseRepository.getInstance()
    private val authRepository = repository.authRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
    }

    private fun initViews() {
        //Loading the background image from Firebase Storage
        BackgroundManager.loadBackground(
            "welcome_background.png",
            binding.backgroundImage
        ) {
            Toast.makeText(
                this,
                "Failed to load background image",
                Toast.LENGTH_SHORT
            ).show()
        }

        //Back to Welcome screen
        binding.backButton.setOnClickListener {
            finish()
        }

        //Go to the user creation screen
        binding.registerPrompt.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Handle login
        binding.loginBTN.setOnClickListener {
            val email = binding.logEmailEDT.text.toString().trim()
            val password = binding.passwordEDT.text.toString()

            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        // Handle forgot password
        binding.forgotPassword.setOnClickListener {
            showResetPasswordDialog()
        }

        // Hide keyboard when clicking
        binding.root.setOnClickListener {
            hideKeyboard()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.logemailLayout.error = "Please enter a valid email"
            isValid = false
        } else {
            binding.logemailLayout.error = null
        }

        if (password.isEmpty() || password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }

        return isValid
    }

    // Perform login with email and password
    private fun performLogin(email: String, password: String) {
        binding.loginBTN.isEnabled = false
        lifecycleScope.launch {
            authRepository.signInWithEmailAndPassword(email, password)
                .onSuccess {
                    startActivity(
                        Intent(this@LoginActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                .onFailure { exception ->
                    Toast.makeText(
                        this@LoginActivity,
                        exception.message ?: "Login failed",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.loginBTN.isEnabled = true
                }
        }
    }

    private fun showResetPasswordDialog() {
        // Creating a custom layout for a dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_password, null)
        val emailInput = dialogView.findViewById<EditText>(R.id.emailInput)
        val sendButton = dialogView.findViewById<MaterialButton>(R.id.sendButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelButton)

        // Creating the dialogue
        val dialogBuilder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        dialogBuilder.setView(dialogView)

        // Creating a dialogue
        val dialog = dialogBuilder.create()
        dialog.show()

        // Action for Cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss() // Closing the dialogue
        }

        // Action for Send button
        sendButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                lifecycleScope.launch {
                    authRepository.sendPasswordResetEmail(email)
                        .onSuccess {
                            Toast.makeText(
                                this@LoginActivity,
                                "If the email exists, a reset link has been sent.",
                                Toast.LENGTH_LONG
                            ).show()
                            dialog.dismiss()
                        }
                        .onFailure { exception ->
                            Toast.makeText(
                                this@LoginActivity,
                                "Error: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            } else {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

}