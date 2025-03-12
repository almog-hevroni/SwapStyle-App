package com.example.swapstyleproject

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.swapstyleproject.adapters.NotificationAdapter
import com.example.swapstyleproject.data.repository.base.FirebaseRepository
import com.example.swapstyleproject.databinding.ActivityNotificationsBinding
import kotlinx.coroutines.launch

class NotificationsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var notificationAdapter: NotificationAdapter

    private val repository = FirebaseRepository.getInstance()
    private val notificationRepository = repository.notificationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        loadNotifications()
    }

    private fun setupViews() {
        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupMarkAllAsRead()
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun setupRecyclerView() {
        // Notifications will only be marked as read via the "Mark all as read" button
        notificationAdapter = NotificationAdapter(
            onNotificationClick = { /* No action on individual notification click */ }
        )

        binding.notificationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            adapter = notificationAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadNotifications()
        }
    }

    private fun setupMarkAllAsRead() {
        binding.markAllAsReadButton.setOnClickListener {
            markAllNotificationsAsRead()
        }
    }

    private fun loadNotifications() {
        binding.swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                notificationRepository.getUserNotifications().collect { notifications ->
                    binding.swipeRefreshLayout.isRefreshing = false

                    if (notifications.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        notificationAdapter.submitList(notifications)
                    }
                }
            } catch (e: Exception) {
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(
                    this@NotificationsActivity,
                    "Error loading notifications: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun markAllNotificationsAsRead() {
        lifecycleScope.launch {
            try {
                notificationRepository.markAllNotificationsAsRead()
                    .onSuccess {
                        loadNotifications()
                        setResult(Activity.RESULT_OK)

                        Toast.makeText(
                            this@NotificationsActivity,
                            "Notifications cleared successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { exception ->
                        Toast.makeText(
                            this@NotificationsActivity,
                            "Error: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(
                    this@NotificationsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showEmptyState() {
        binding.notificationsRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.notificationsRecyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
    }
}