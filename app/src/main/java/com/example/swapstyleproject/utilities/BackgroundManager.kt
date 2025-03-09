package com.example.swapstyleproject.utilities

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.storage.FirebaseStorage

object BackgroundManager {
    private val storage = FirebaseStorage.getInstance()
    private val backgroundsRef = storage.reference.child("app_backgrounds")

    private val cachedUrls = mutableMapOf<String, String>() //a map (dictionary) to store URLs of images that have already been loaded, to avoid unnecessary reloading

    fun loadBackground(imageName: String, imageView: ImageView, onError: (() -> Unit)? = null) {
        //Checks if the image is already cached
        cachedUrls[imageName]?.let { url ->
            loadWithGlide(url, imageView, onError)
            return
        }

        //If not cached, fetches from Firebase
        backgroundsRef.child(imageName).downloadUrl
            .addOnSuccessListener { uri ->
                val url = uri.toString()
                cachedUrls[imageName] = url
                loadWithGlide(url, imageView, onError)
            }
            .addOnFailureListener {
                onError?.invoke()
            }
    }

    private fun loadWithGlide(url: String, imageView: ImageView, onError: (() -> Unit)? = null) {
        Glide.with(imageView.context)
            .load(url)
            .centerCrop()
            .error(android.R.color.transparent)
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    onError?.invoke()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .into(imageView)
    }
}