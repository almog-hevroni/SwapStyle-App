package com.example.swapstyleproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.swapstyleproject.R

//Loading a List of Image URLs
//Using the Glide Library to Load Images
//Creating a Flexible Display of Images in a Scrolling List
//Adding an Optional Click Event to Images

class ClothingImageAdapter(private val imageUrls: List<String>,  private val onImageClick: (() -> Unit)? = null) :
    RecyclerView.Adapter<ClothingImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.itemImageView)

        fun bind(imageUrl: String,onImageClick: (() -> Unit)?) {
            println("Loading image URL: $imageUrl")
            Glide.with(itemView.context)
                .load(imageUrl)
                .centerCrop()
                .into(imageView)

            imageView.setOnClickListener { onImageClick?.invoke() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clothing_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageUrls[position], onImageClick)
    }

    override fun getItemCount() = imageUrls.size
}