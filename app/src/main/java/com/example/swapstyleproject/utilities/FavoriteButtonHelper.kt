package com.example.swapstyleproject.utilities

import android.view.View
import com.example.swapstyleproject.R
import com.google.android.material.button.MaterialButton

object FavoriteButtonHelper {
    fun updateFavoriteButton(isFavorite: Boolean, button: MaterialButton) {
        button.isChecked = isFavorite
        if (isFavorite) {
            button.setIconResource(R.drawable.ic_heart_filled)
            button.setIconTintResource(R.color.heart_yellow)
        } else {
            button.setIconResource(R.drawable.ic_heart_outline)
            button.setIconTintResource(R.color.black)
        }
    }

    fun setupFavoriteButton(
        button: MaterialButton,
        itemId: String,
        isOwnItem: Boolean,
        initialFavoriteState: Boolean,
        onFavoriteClick: (String, Boolean) -> Unit
    ) {
        if (isOwnItem) {
            button.visibility = View.GONE
            return
        }

        button.visibility = View.VISIBLE
        updateFavoriteButton(initialFavoriteState, button)

        button.setOnClickListener {
            onFavoriteClick(itemId, !initialFavoriteState)
        }
    }
}
