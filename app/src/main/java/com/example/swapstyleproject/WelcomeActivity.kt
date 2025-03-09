package com.example.swapstyleproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.swapstyleproject.databinding.WelcomeScreenActivityBinding
import com.example.swapstyleproject.utilities.BackgroundManager
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: WelcomeScreenActivityBinding
    private lateinit var loginButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WelcomeScreenActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        findViews()
        initViews()
    }

    private fun findViews() {
        loginButton = binding.loginBTN
    }

    private fun initViews() {
        // Loading the background image from Firebase Storage
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

        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}