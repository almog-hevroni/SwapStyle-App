package com.example.swapstyleproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherActivity : AppCompatActivity() {
    private val repository = FirebaseRepository.getInstance()
    private val authRepository = repository.authRepository
    private val itemRepository = repository.itemRepository

    private lateinit var loadingAnimation: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        // Lottie animation startup
        loadingAnimation = findViewById(R.id.loadingAnimation)

        // Checks that the animation ran
        loadingAnimation.playAnimation()

        lifecycleScope.launch {
            delay(500)
            withContext(Dispatchers.IO) {
                try {
                    if (authRepository.isUserLoggedIn()) {
                        // Check for expired items
                        itemRepository.checkAndUpdateExpiredItems()
                        navigateToMainActivity()
                    } else {
                        // If not logged in, go to the welcome screen
                        navigateToWelcomeActivity()
                    }
                } catch (e: Exception) {
                    if (authRepository.isUserLoggedIn()) {
                        navigateToMainActivity()
                    } else {
                        navigateToWelcomeActivity()
                    }
                }
            }
        }
    }

    //ensures that the user reaches the main screen as if they had reopened the app,
    // without being able to return to the splash screen
    private fun navigateToMainActivity() {
        val intent = Intent(this@LauncherActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    //ensures that the user reaches the welcome screen without being able to return to previous screens
    private fun navigateToWelcomeActivity() {
        val intent = Intent(this@LauncherActivity, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}