package com.example.swapstyleproject

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.swapstyleproject.databinding.ActivityMainBinding
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupKeyboardVisibilityListener()
        setupNavigation()
        setupDrawerMenu()
        setupBottomNavVisibility()
    }

    private fun setupKeyboardVisibilityListener() {
        val rootLayout = binding.root
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootLayout.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootLayout.height

            val keyboardHeight = screenHeight - rect.bottom

            if (keyboardHeight > 150 * resources.displayMetrics.density) {
                binding.bottomNav.visibility = View.GONE
            } else {
                if (navController.currentDestination?.id != R.id.navigation_add) {
                    binding.bottomNav.visibility = View.VISIBLE
                }
            }
        }
    }

    //Defines the navigation system in the application
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController  //Manages the actual navigation - switching between screens, saving history, and handling the Back button.

        binding.bottomNav.setupWithNavController(navController)
        binding.bottomNav.setItemActiveIndicatorColor(ContextCompat.getColorStateList(this, R.color.transparent))
        binding.navView.setupWithNavController(navController)
    }

    //Defines the behavior of the side menu
    private fun setupDrawerMenu() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> { //Clicking the logout button
                    showLogoutConfirmationDialog()
                    true
                }
                else -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    false
                }
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.show()

        //logout on left side
        val titleView = dialog.window?.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
        titleView?.apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        // the color of the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, R.color.tool_bar_color))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.tool_bar_color))
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                val repository = FirebaseRepository.getInstance()
                repository.authRepository.signOut()
                    .onSuccess {
                        repository.itemRepository.clearProcessedSwaps()
                        startActivity(Intent(this@MainActivity, WelcomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@MainActivity, "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.END)
    }

    // Hides the bottom menu
    private fun setupBottomNavVisibility() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_add ) {
                binding.bottomNav.visibility = View.GONE
            } else {
                binding.bottomNav.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val repository = FirebaseRepository.getInstance()
            repository.itemRepository.clearProcessedSwaps()
        }
    }
}