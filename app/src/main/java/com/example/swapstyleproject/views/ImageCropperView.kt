package com.example.swapstyleproject.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

    class ImageCropperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var imageMatrix = Matrix()
    private var inverseMatrix = Matrix()

        // Cropping frame
    private val cropRect = RectF()
    private var activeHandle: Handle? = null
    private val handleRadius = 30f
    private val minSize = 100f
    private val touchAreaSize = 50f

    // Mapping the crop frame handles
    private enum class Handle {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP, RIGHT, BOTTOM, LEFT,
        CENTER
    }

    // Paints and brushes
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 2f
    }

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        setupInitialCropArea()
        invalidate()
    }

    private fun setupInitialCropArea() {
        imageBitmap?.let { bitmap ->
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            // Calculate the magnification ratio to fit the image to the display
            val scale = min(
                viewWidth / imageWidth,
                viewHeight / imageHeight
            )

            // Calculate the adjusted image size
            val scaledWidth = imageWidth * scale
            val scaledHeight = imageHeight * scale

            // Position the image in the center
            val left = (viewWidth - scaledWidth) / 2
            val top = (viewHeight - scaledHeight) / 2

            // Update the matrix
            imageMatrix.reset()
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(left, top)
            imageMatrix.invert(inverseMatrix)

            // Set the initial cropping area (80% of the image size)
            val cropMargin = min(scaledWidth, scaledHeight) * 0.1f
            cropRect.set(
                left + cropMargin,
                top + cropMargin,
                left + scaledWidth - cropMargin,
                top + scaledHeight - cropMargin
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            setupInitialCropArea()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Drawing the image
        imageBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, imageMatrix, null)

            // Draw the blackened area outside the cropping frame
            val path = Path()
            path.addRect(cropRect, Path.Direction.CW)
            path.addRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), Path.Direction.CCW)
            canvas.drawPath(path, overlayPaint)

            // Draw the cropping frame
            canvas.drawRect(cropRect, paint)

            // Drawing the handles
            drawHandle(canvas, cropRect.left, cropRect.top) // TOP_LEFT
            drawHandle(canvas, cropRect.right, cropRect.top) // TOP_RIGHT
            drawHandle(canvas, cropRect.left, cropRect.bottom) // BOTTOM_LEFT
            drawHandle(canvas, cropRect.right, cropRect.bottom) // BOTTOM_RIGHT

            // Draw middle handles
            drawHandle(canvas, cropRect.left + cropRect.width() / 2, cropRect.top) // TOP
            drawHandle(canvas, cropRect.right, cropRect.top + cropRect.height() / 2) // RIGHT
            drawHandle(canvas, cropRect.left + cropRect.width() / 2, cropRect.bottom) // BOTTOM
            drawHandle(canvas, cropRect.left, cropRect.top + cropRect.height() / 2) // LEFT
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, paint)
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activeHandle = getHandle(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != null) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    moveHandle(activeHandle!!, dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getHandle(x: Float, y: Float): Handle? {
        // Check corner handles
        if (isNearPoint(x, y, cropRect.left, cropRect.top)) return Handle.TOP_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.top)) return Handle.TOP_RIGHT
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom)) return Handle.BOTTOM_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom)) return Handle.BOTTOM_RIGHT

        // Check middle handles
        if (isNearPoint(x, y, cropRect.left + cropRect.width() / 2, cropRect.top)) return Handle.TOP
        if (isNearPoint(x, y, cropRect.right, cropRect.top + cropRect.height() / 2)) return Handle.RIGHT
        if (isNearPoint(x, y, cropRect.left + cropRect.width() / 2, cropRect.bottom)) return Handle.BOTTOM
        if (isNearPoint(x, y, cropRect.left, cropRect.top + cropRect.height() / 2)) return Handle.LEFT

        // Check if the point is inside the cropping frame
        if (cropRect.contains(x, y)) return Handle.CENTER

        return null
    }

    private fun isNearPoint(x: Float, y: Float, pointX: Float, pointY: Float): Boolean {
        return abs(x - pointX) < touchAreaSize && abs(y - pointY) < touchAreaSize
    }

    private fun moveHandle(handle: Handle, dx: Float, dy: Float) {
        val newLeft = cropRect.left
        val newTop = cropRect.top
        val newRight = cropRect.right
        val newBottom = cropRect.bottom

        when (handle) {
            Handle.TOP_LEFT -> {
                cropRect.left = (newLeft + dx).coerceIn(0f, newRight - minSize)
                cropRect.top = (newTop + dy).coerceIn(0f, newBottom - minSize)
            }
            Handle.TOP_RIGHT -> {
                cropRect.right = (newRight + dx).coerceIn(newLeft + minSize, width.toFloat())
                cropRect.top = (newTop + dy).coerceIn(0f, newBottom - minSize)
            }
            Handle.BOTTOM_LEFT -> {
                cropRect.left = (newLeft + dx).coerceIn(0f, newRight - minSize)
                cropRect.bottom = (newBottom + dy).coerceIn(newTop + minSize, height.toFloat())
            }
            Handle.BOTTOM_RIGHT -> {
                cropRect.right = (newRight + dx).coerceIn(newLeft + minSize, width.toFloat())
                cropRect.bottom = (newBottom + dy).coerceIn(newTop + minSize, height.toFloat())
            }
            Handle.TOP -> {
                cropRect.top = (newTop + dy).coerceIn(0f, newBottom - minSize)
            }
            Handle.RIGHT -> {
                cropRect.right = (newRight + dx).coerceIn(newLeft + minSize, width.toFloat())
            }
            Handle.BOTTOM -> {
                cropRect.bottom = (newBottom + dy).coerceIn(newTop + minSize, height.toFloat())
            }
            Handle.LEFT -> {
                cropRect.left = (newLeft + dx).coerceIn(0f, newRight - minSize)
            }
            Handle.CENTER -> {
                // Moving the entire frame
                val width = cropRect.width()
                val height = cropRect.height()
                cropRect.left = (newLeft + dx).coerceIn(0f, width.toFloat() - width)
                cropRect.top = (newTop + dy).coerceIn(0f, height.toFloat() - height)
                cropRect.right = cropRect.left + width
                cropRect.bottom = cropRect.top + height
            }
        }
    }

    fun getCroppedBitmap(): Bitmap? {
        return imageBitmap?.let { bitmap ->
            try {
                // המרת קואורדינטות מסך לקואורדינטות תמונה
                val points = floatArrayOf(
                    cropRect.left, cropRect.top,
                    cropRect.right, cropRect.bottom
                )
                inverseMatrix.mapPoints(points)

                // חיתוך בגבולות הבטוחים
                val x = points[0].coerceIn(0f, bitmap.width.toFloat()).toInt()
                val y = points[1].coerceIn(0f, bitmap.height.toFloat()).toInt()
                val width = (points[2] - points[0]).coerceIn(0f, bitmap.width.toFloat() - x).toInt()
                val height = (points[3] - points[1]).coerceIn(0f, bitmap.height.toFloat() - y).toInt()

                if (width <= 0 || height <= 0) return null

                Bitmap.createBitmap(
                    bitmap,
                    x,
                    y,
                    width,
                    height
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}