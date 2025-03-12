package com.example.swapstyleproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.swapstyleproject.R
import com.example.swapstyleproject.model.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onNotificationClick: (Notification) -> Unit,
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view, onNotificationClick)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(
        itemView: View,
        private val onNotificationClick: (Notification) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val notificationTitle: TextView = itemView.findViewById(R.id.notificationTitle)
        private val notificationMessage: TextView = itemView.findViewById(R.id.notificationMessage)
        private val notificationTime: TextView = itemView.findViewById(R.id.notificationTime)
        private val unreadIndicator: View = itemView.findViewById(R.id.unreadIndicator)
        private val cardView: CardView = itemView as CardView

        fun bind(notification: Notification) {
            notificationTitle.text = notification.title
            notificationMessage.text = notification.message
            notificationTime.text = formatTimestamp(notification.timestamp)

            if (notification.isRead) {
                unreadIndicator.visibility = View.GONE

                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                unreadIndicator.visibility = View.VISIBLE

                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.gray))
            }

            itemView.setOnClickListener {
                onNotificationClick(notification)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val date = Date(timestamp)
            val currentTime = System.currentTimeMillis()
            val diff = currentTime - timestamp

            return when {
                diff < 60 * 60 * 1000 -> { // Less than 1 hour
                    val minutes = (diff / (60 * 1000)).toInt()
                    if (minutes < 1) "Just now" else "$minutes min ago"
                }
                diff < 24 * 60 * 60 * 1000 -> { // Less than 24 hours
                    val hours = (diff / (60 * 60 * 1000)).toInt()
                    "$hours hours ago"
                }
                diff < 48 * 60 * 60 * 1000 -> { // Less than 48 hours
                    "Yesterday"
                }
                else -> {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
                }
            }
        }
    }

    private class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}