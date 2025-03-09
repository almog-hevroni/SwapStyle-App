package com.example.swapstyleproject.utilities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.swapstyleproject.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

//Handles the selection and upload of new images to the system
//Allows the user to select images in two ways
//Handles the required permissions (access to the camera and gallery)
//Allows editing and cropping of the selected image
//Uploads the image to Firebase Storage

class ImagePickerHelper(
    private val fragment: Fragment,
    private val onImageUploaded: (Uri, ImageUploadType) -> Unit,
    var uploadType: ImageUploadType = ImageUploadType.PROFILE
) {

    private var tempImageUri: Uri? = null
    private var hasGalleryPermission = false
    private var hasCameraPermission = false

    //Handles a permission request from the user
    private val requestPermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkAndRequestPermissions()
        } else {
            Toast.makeText(
                fragment.requireContext(),
                "Permissions to access photos and camera must be approved.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    //Select a photo from the gallery
    private val getContent = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> startImageCrop(uri) }
        }
    }

    //Taking a picture
    private val takePicture =
        fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                tempImageUri?.let { uri -> startImageCrop(uri) }
            }
        }

    private val cropResult = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            UCrop.getOutput(result.data!!)?.let { uri ->
                val context = fragment.requireContext()

                AnimationHelper.showLoadingAnimation(context)

                uploadImageToFirebase(uri)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(result.data!!)
            Toast.makeText(
                fragment.requireContext(),
                "שגיאה בחיתוך התמונה: ${error?.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startImageCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(
            File(fragment.requireContext().cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        )

        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f) // 1:1 ratio (square)
            .withOptions(UCrop.Options().apply {
                setCompressionQuality(90) // Image quality
                setHideBottomControls(false)
                setFreeStyleCropEnabled(true) // Allows free cutting
                setToolbarTitle("Adjust image")
                setToolbarColor(
                    ContextCompat.getColor(
                        fragment.requireContext(),
                        R.color.tool_bar_color
                    )
                )
                setStatusBarColor(
                    ContextCompat.getColor(
                        fragment.requireContext(),
                        R.color.tool_bar_color
                    )
                )
                setToolbarWidgetColor(
                    ContextCompat.getColor(
                        fragment.requireContext(),
                        R.color.white
                    )
                )
            })

        try {
            cropResult.launch(uCrop.getIntent(fragment.requireContext()))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                fragment.requireContext(),
                "Error opening image editor",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //Permissions check
    private fun checkAndRequestPermissions() {
        val context = fragment.requireContext()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                hasGalleryPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            }

            else -> {
                hasGalleryPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        // Request permissions if needed
        when {
            !hasGalleryPermission -> requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            !hasCameraPermission -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            else -> showImageSourceDialog(context) // If everything is correct, the dialog is displayed.
        }
    }

    //Serves as the department's main interface
    fun requestPermissionsAndShowDialog(context: Context) {
        checkAndRequestPermissions()
    }

    //Display image selection dialog
    private fun showImageSourceDialog(context: Context) {
        val options = arrayOf("Take a picture", "Choose from the gallery")
        AlertDialog.Builder(context)
            .setTitle("Select image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePictureFromCamera(context)
                    1 -> openGallery()
                }
            }
            .show()
    }

    //Taking a picture using the camera
    private fun takePictureFromCamera(context: Context) {
        //Create a unique name for the image
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"

        //Setting the properties of the image file
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        //Create a temporary URI to save the image
        tempImageUri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        //Turning on the camera
        tempImageUri?.let { uri ->
            takePicture.launch(uri)
        } ?: run {
            Toast.makeText(context, "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }

    //Opening the gallery to select a photo
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        getContent.launch(intent)
    }

    private fun uploadImageToFirebase(uri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null && uploadType == ImageUploadType.ITEM) {
            Toast.makeText(fragment.requireContext(), "Error: User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        if (uri == null) {
            Toast.makeText(fragment.requireContext(), "Error: Image URI is null", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a reference to the Firebase Storage location
        val storageRef = when (uploadType) {
            ImageUploadType.PROFILE -> FirebaseStorage.getInstance().reference
                .child("profile_images/$userId/${System.currentTimeMillis()}.jpg")
            ImageUploadType.ITEM -> FirebaseStorage.getInstance().reference
                .child("items/$userId/${System.currentTimeMillis()}.jpg")
        }

        storageRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                onImageUploaded(downloadUri, uploadType)
                AnimationHelper.dismissDialog()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(fragment.requireContext(), "Upload failed: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    // Enum for upload types
    enum class ImageUploadType {
        PROFILE,
        ITEM
    }
}