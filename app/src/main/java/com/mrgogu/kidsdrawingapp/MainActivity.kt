package com.mrgogu.kidsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if (result.resultCode == RESULT_OK && result.data!= null){
                val  imageBackGround: ImageView = findViewById(R.id.iv_background)
                imageBackGround.setImageURI(result.data?.data)
            }
        }

    //  This is a class-level variable
    private lateinit var requestPermission: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        //  Initialize permission launcher here
        requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission granted for reading files.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // This Time Intent is used fetch data from gallery as we granted the files permission

                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGalleryLauncher.launch(pickIntent)

                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission denied for: $permissionName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        drawingView = findViewById(R.id.drawing_View)
        drawingView?.setSizeForBrush(10f)

        val linearLayoutPaintColours = findViewById<LinearLayout>(R.id.ll_paint_colours)
        mImageButtonCurrentPaint = linearLayoutPaintColours[0] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val ib_brush: ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibGallery: ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener { requestStoragePermission() }

        val ibUndo: ImageButton= findViewById(R.id.ibUndo)
        ibUndo.setOnClickListener { drawingView?.undo() }

        val ibRedo: ImageButton = findViewById(R.id.ibRedo)
        ibRedo.setOnClickListener { drawingView?.redo() }

        val ibSave: ImageButton = findViewById(R.id.ibsave)
        ibRedo.setOnClickListener { drawingView?.redo() }


    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")

        val sizes = mapOf(
            R.id.ib_small_brush to 5f,
            R.id.ib_medium_brush to 10f,
            R.id.ib_large_brush to 15f,
            R.id.ib_extraLarge_brush to 20f,
            R.id.ib_pencil to 2f
        )

        sizes.forEach { (id, size) ->
            brushDialog.findViewById<ImageButton>(id).setOnClickListener {
                drawingView?.setSizeForBrush(size)
                brushDialog.dismiss()
            }
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView!!.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }

    // ✅ Handles Android version + rationale + proper re-request
    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        // If user previously denied permission
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
            showRationaleDialog(
                "Kids Drawing App",
                "This app needs to access your media to import and save drawings."
            )
        } else {
            // Directly request permission
            requestPermission.launch(permissions)
        }
    }

    private fun showRationaleDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestStoragePermission()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun getBitmapFromView(view: View) : Bitmap{
        val returnBitmap = createBitmap(view.width, view.height)
        // its basically Bitmap.createBitmap(view.width,view.height , Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)

        return returnBitmap
    }

    private suspend fun saveBitmapFile(mbitmap: Bitmap?): String{
        var result = ""
        withContext(Dispatchers.IO) {
            if (mbitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mbitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString()
                    + File.separator + "KidsDrawingApp_" + System.currentTimeMillis()/1000 + ".png")

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        if (result.isNotEmpty()){
                            Toast.makeText(
                                this@MainActivity,
                                "File Saved Successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }else{
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
}