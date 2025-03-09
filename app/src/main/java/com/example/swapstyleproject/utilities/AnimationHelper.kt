package com.example.swapstyleproject.utilities

import android.animation.Animator
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView
import com.example.swapstyleproject.R

object AnimationHelper {
    private var successAnimationDialog: Dialog? = null

    fun showSuccessAnimation(
        context: Context,
        message: String = "Success!",
        onAnimationEnd: () -> Unit = {}
    ) {
        successAnimationDialog?.dismiss()

        // Create a new dialog
        successAnimationDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
            setContentView(R.layout.layout_success_animation)
            setCancelable(false)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Set a custom message
            findViewById<android.widget.TextView>(R.id.successMessage)?.text = message

            // Get the animation view
            val animationView = findViewById<LottieAnimationView>(R.id.successAnimation)

            // Add a listener to handle navigation after the animation ends
            animationView.addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        dismiss()
                        successAnimationDialog = null
                        onAnimationEnd()
                    }, 500)
                }
            })

            show()
        }
    }

    fun showLoadingAnimation(
        context: Context
    ) {
        successAnimationDialog?.dismiss()

        successAnimationDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
            setContentView(R.layout.layout_loading_animation)
            setCancelable(false)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            show()
        }
    }

    fun dismissDialog() {
        successAnimationDialog?.dismiss()
        successAnimationDialog = null
    }
}