package com.example.swapstyleproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityRegisterBinding
import com.example.swapstyleproject.utilities.BackgroundManager
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val repository = FirebaseRepository.getInstance()
    private val authRepository = repository.authRepository
    private val userRepository = repository.userRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
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

        // Setup click listeners
        binding.backButton.setOnClickListener { finish() }
        binding.registerBTN.setOnClickListener { handleRegistration() }
        // Hide keyboard when clicking outside of the EditText fields
        binding.root.setOnClickListener { hideKeyboard() }

        // Setup text change listeners for validation
        setupInputValidation()

    }

    private fun setupInputValidation() {
        // Username validation
        binding.usernameEDT.doAfterTextChanged { text ->
            if (text.isNullOrBlank()) {
                binding.usernameLayout.error = "Username is required"
            } else if (text.length < 3) {
                binding.usernameLayout.error = "Username must be at least 3 characters"
            } else {
                binding.usernameLayout.error = null
            }
        }

        // Email validation
        binding.emailEDT.doAfterTextChanged { text ->
            if (text.isNullOrBlank()) {
                binding.emailLayout.error = "Email is required"
            } else if (!Patterns.EMAIL_ADDRESS.matcher(text).matches()) {
                binding.emailLayout.error = "Please enter a valid email"
            } else {
                binding.emailLayout.error = null
            }
        }

        // Password validation
        binding.passwordEDT.doAfterTextChanged { text ->
            validatePassword(text?.toString() ?: "")
        }

        // Confirm password validation
        binding.confirmPasswordEDT.doAfterTextChanged { text ->
            validateConfirmPassword(
                password = binding.passwordEDT.text?.toString() ?: "",
                confirmPassword = text?.toString() ?: ""
            )
        }
    }

    private fun validatePassword(password: String) {
        when {
            password.isEmpty() -> {
                binding.passwordLayout.error = "Password is required"
            }
            password.length < 6 -> {
                binding.passwordLayout.error = "Password must be at least 6 characters"
            }
            !password.any { it.isDigit() } -> {
                binding.passwordLayout.error = "Password must contain at least one number"
            }
            !password.any { it.isLetter() } -> {
                binding.passwordLayout.error = "Password must contain at least one letter"
            }
            else -> {
                binding.passwordLayout.error = null
            }
        }
    }

    private fun validateConfirmPassword(password: String, confirmPassword: String) {
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordLayout.error = "Please confirm your password"
        } else if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = "Passwords do not match"
        } else {
            binding.confirmPasswordLayout.error = null
        }
    }

    private fun handleRegistration() {
        val username = binding.usernameEDT.text?.toString()?.trim() ?: ""
        val email = binding.emailEDT.text?.toString()?.trim() ?: ""
        val password = binding.passwordEDT.text?.toString() ?: ""
        val confirmPassword = binding.confirmPasswordEDT.text?.toString() ?: ""

        if (!validateRegistrationInput(username, email, password, confirmPassword)) {
            return
        }
        checkUsernameAndRegister(username, email, password)
    }

    private fun validateRegistrationInput(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // Clear previous errors
        binding.usernameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.confirmPasswordLayout.error = null

        // Username validation
        if (username.length < 3) {
            binding.usernameLayout.error = "Username must be at least 3 characters"
            isValid = false
        }

        // Email validation
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Please enter a valid email"
            isValid = false
        }

        // Password validation
        if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        // Confirm password validation
        if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = "Passwords do not match"
            isValid = false
        }

        return isValid
    }

    private fun checkUsernameAndRegister(username: String, email: String, password: String) {
        lifecycleScope.launch {
            userRepository.checkIfUsernameExists(username)
                .onSuccess { exists ->
                    if (exists) {
                        binding.usernameLayout.error = "Username is already taken"
                    } else {
                        checkEmailAndRegister(username, email, password)
                    }
                }
                .onFailure { exception ->
                    Toast.makeText(
                        this@RegisterActivity,
                        "Error checking username: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun checkEmailAndRegister(username: String, email: String, password: String) {
        lifecycleScope.launch {
            userRepository.checkIfEmailExists(email)
                .onSuccess { exists ->
                    if (exists) {
                        binding.emailLayout.error = "Email is already registered"
                    } else {
                        performRegistration(username, email, password)
                    }
                }
                .onFailure { exception ->
                    Toast.makeText(
                        this@RegisterActivity,
                        "Error checking email: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun performRegistration(username: String, email: String, password: String) {
        lifecycleScope.launch {
            authRepository.createUserWithEmailAndPassword(email, password, username)
                .onSuccess {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Registration successful!",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(this@RegisterActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                .onFailure { exception ->
                    Toast.makeText(
                        this@RegisterActivity,
                        "Registration failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
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