package com.shankar.imagepicker

import android.R.id
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.shankar.imagepicker.databinding.ActivityMainBinding
import com.shankar.library.*
import com.shankar.ucrop.UCropActivity
import com.shankar.ucrop.cache.LruCache
import com.shankar.ucrop.callback.BitmapCropCallback
import com.shankar.ucrop.task.BitmapCropTask
import com.shankar.ucrop.util.ScreenUtils
import com.shankar.ucrop.view.GestureCropImageView
import com.shankar.ucrop.view.OverlayView
import com.shankar.ucrop.view.TransformImageView
import com.shankar.ucrop.view.UCropView
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import android.R.id.button1





class MainActivity : AppCompatActivity() , InstagramImageGridAdapter.OnPhotoSelectChangedListener{
    val imagesList = ArrayList<LocalMedia>()

    private lateinit var binding: ActivityMainBinding
    private lateinit var mAdapter: InstagramImageGridAdapter
    private var mCropGridShowing = false
    private var mHandler: Handler? = null
    private var isAspectRatio = false
    private var mAspectRadio = 0f
    private var multiAspectRatio = 1.0f
    private lateinit var uCropView : UCropView
    private lateinit var mGestureCropImageView: GestureCropImageView
    private lateinit var mOverlayView: OverlayView
    private var mShowGridRunnable: ShowGridRunnable? = null
    private var mPreviewPosition = -1
    private var config = PictureSelectionConfig.getCleanInstance()
    private var isMulti: Boolean = false
    private var mLruCache: LruCache<LocalMedia, AsyncTask<*, *, *>>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        getImages()
        binding.ratioView.setOnClickListener {
            isAspectRatio = !isAspectRatio
            resetAspectRatio()
        }
        
        binding.btnImgMultiSelect.setOnClickListener {
            isMulti = !isMulti
            setMode(isMulti)
        }

        binding.btnSelectImgNext.setOnClickListener {

            if (config.selectionMode == PictureConfig.SINGLE || mAdapter.selectedImages.size == 1) {
                cropAndSaveImage()

            } else {
                savePreviousPositionCropInfo(mAdapter.images[mPreviewPosition])
                startMultiCrop()
            }
        }
    }

    private fun getImages() {
        ImagePicker(this).loadLocalImages(object : ImagePicker.ImageInterface{

            override fun onImagesLoaded(data: MutableList<LocalMedia>) {
                super.onImagesLoaded(data)
                imagesList.addAll(data)
                startPreview(imagesList[0])
                mAdapter.bindImagesData(imagesList)
            }
        })


    }

    private fun setPreviewPosition(position: Int) {
        if (mPreviewPosition != position && position < mAdapter.itemCount) {
            val previousPosition: Int = mPreviewPosition
            mPreviewPosition = position
            mAdapter.setPreviewPosition(position)
            mAdapter.notifyItemChanged(previousPosition)
            mAdapter.notifyItemChanged(position)
        }
    }


    private fun initViews() {
        setupCropImageView()
        initRecyclerViews()
    }

    private fun initRecyclerViews() {

        mAdapter = InstagramImageGridAdapter(this, config)
        mAdapter.setOnPhotoSelectChangedListener(this)

        binding.galleryRecycler.apply {
            setHasFixedSize(true)
            addItemDecoration(
                SpacingItemDecoration(
                    4,
                    ScreenUtils.dip2px(this@MainActivity, 2f), false
                )
            )
            layoutManager = GridLayoutManager(context, 4)
            (this.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            adapter = mAdapter
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCropImageView() {
        mHandler = Handler(this.mainLooper)
        uCropView = binding.uCropView
        mGestureCropImageView = uCropView.cropImageView
        mOverlayView = uCropView.overlayView
        mGestureCropImageView.apply {
            setPadding(0, 0, 0, 0)
            targetAspectRatio = 1.0f
            setTransformImageListener(mImageListener)
            isRotateEnabled = false
            setMaxScaleMultiplier(15.0f)
            scaleType = ImageView.ScaleType.MATRIX
        }

        mOverlayView.apply {
            setPadding(0, 0, 0, 0)
            setShowCropGrid(false)
            setShowCropFrame(false)

            setDimmedColor(Color.parseColor("#363636"))
            setCropGridColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.white
                )
            )

        }

        mGestureCropImageView.setOnTouchListener { v: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!mCropGridShowing) {
                    mOverlayView.setShowCropGrid(true)
                    mOverlayView.invalidate()
                    mCropGridShowing = true
                } else if (mShowGridRunnable != null) {
                    mHandler!!.removeCallbacks(mShowGridRunnable!!)
                }
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (mShowGridRunnable == null) {
                    mShowGridRunnable = ShowGridRunnable()
                }
                mHandler!!.postDelayed(mShowGridRunnable!!, 800)

            }
            false
        }

    }


    inner class ShowGridRunnable : Runnable {
        override fun run() {
            if (mCropGridShowing) {
                mOverlayView.setShowCropGrid(false)
                mOverlayView    .invalidate()
                mCropGridShowing = false
            }
        }


    }

    private val mImageListener: TransformImageView.TransformImageListener =
        object : TransformImageView.TransformImageListener {
            override fun onRotate(currentAngle: Float) {}
            override fun onScale(currentScale: Float) {

            }
            override fun onLoadComplete() {
            }
            override fun onLoadFailure(e: java.lang.Exception) {}
            override fun onBitmapLoadComplete(bitmap: Bitmap) {

                resetAspectRatio()

            }

        }


    private fun resetAspectRatio() {
        mAspectRadio = 0f

        if (isAspectRatio) {
            val drawable = mGestureCropImageView.drawable
            if (drawable != null) {
                mAspectRadio =
                    getInstagramAspectRatio(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight
                    )
            }
        }

        if(isMulti) {
            mGestureCropImageView.targetAspectRatio = multiAspectRatio
        }
        else {
            multiAspectRatio = if (isAspectRatio) mAspectRadio else 1.0f
            mGestureCropImageView.targetAspectRatio = if (isAspectRatio) mAspectRadio else 1.0f
        }
        mGestureCropImageView.onImageLaidOut()

    }

    private fun getInstagramAspectRatio(width: Int, height: Int): Float {
        var aspectRatio = 0f
        if (height > width * 1.266f) {
            aspectRatio = width / (width * 1.266f)
        } else if (width > height * 1.9f) {
            aspectRatio = height * 1.9f / height
        }
        return aspectRatio
    }

    override fun onItemChecked(position: Int, image: LocalMedia?, isCheck: Boolean) {

    }

    override fun onTakePhoto() {
    }

    override fun onChange(selectImages: MutableList<LocalMedia>?) {

    }

    override fun onPictureClick(media: LocalMedia?, position: Int) {

        Log.i("TAG_1", "$position : ${mGestureCropImageView.scrollX} - ${mGestureCropImageView.scrollY}")
        startPreview(media)
        setPreviewPosition(position)

    }

    private fun containsMedia(selectedImages: List<LocalMedia>, media: LocalMedia) : Boolean {
        for (selectedImage in selectedImages) {
            if (selectedImage.path.equals(media.path) || selectedImage.id == media.id) {
                return true
            }
        }
        return false
    }
    
    private fun setMode(isMulti: Boolean) {
        if (isMulti) {
            config.selectionMode = PictureConfig.MULTIPLE
            config.isSingleDirectReturn = false
            mAdapter.clearSelectedImages()
            if (mLruCache == null) {
                mLruCache = LruCache(20)
            }
            bindPreviewPosition()
        } else {
            config.selectionMode = PictureConfig.SINGLE
            config.isSingleDirectReturn = true
        }
        mAdapter.notifyDataSetChanged()
    }

    private fun bindPreviewPosition() {
        val selectedImages = mAdapter.selectedImages
        val size = selectedImages.size
        val mimeType = if (size > 0) selectedImages[0].mimeType else ""
        val previewMedia = mAdapter.images[mPreviewPosition]
        if (selectedImages.contains(previewMedia) || containsMedia(
                selectedImages,
                previewMedia
            )
        ) {
            return
        }
        if (!TextUtils.isEmpty(mimeType)) {
            val mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, previewMedia.mimeType)
            if (!mimeTypeSame) {
                ToastUtils.s(this, getString(R.string.picture_rule))
                return
            }
        }
        if (size >= config.maxSelectNum) {
            ToastUtils.s(
                this,
                StringUtils.getMsg(this, mimeType, config.maxSelectNum)
            )
            return
        }
        selectedImages.add(previewMedia)
        mAdapter.bindSelectImages(selectedImages)
    }


    private fun cropAndSaveImage() {
        mGestureCropImageView.cropAndSaveImage(
            UCropActivity.DEFAULT_COMPRESS_FORMAT,
            UCropActivity.DEFAULT_COMPRESS_QUALITY,
            object : BitmapCropCallback {
                override fun onBitmapCropped(
                    resultUri: Uri,
                    offsetX: Int,
                    offsetY: Int,
                    imageWidth: Int,
                    imageHeight: Int
                ) {

                }

                override fun onCropFailure(t: Throwable) {
//                    setResultError(activity, t)
                }
            })
    }

    private fun savePreviousPositionCropInfo(previousMedia: LocalMedia?) {
        if (previousMedia == null || mLruCache == null  || config.selectionMode == PictureConfig.SINGLE || !PictureMimeType.isHasImage(
                previousMedia.mimeType
            )
        ) {
            return
        }
        val selectedImages = mAdapter.selectedImages
        if (selectedImages.contains(previousMedia)) {
            mLruCache!!.put(
                previousMedia,
                createCropAndSaveImageTask(BitmapCropCallbackImpl(previousMedia))
            )
        } else {
            for (selectedImage in selectedImages) {
                if (selectedImage.path.equals(previousMedia.path) || selectedImage.id == previousMedia.id) {
                    mLruCache!!.put(selectedImage, createCropAndSaveImageTask(BitmapCropCallbackImpl(selectedImage)))
                    break
                }
            }
        }
    }


    private fun createCropAndSaveImageTask(cropCallback: BitmapCropCallback?): AsyncTask<*, *, *>? {
        return mGestureCropImageView.createCropAndSaveImageTask(
            UCropActivity.DEFAULT_COMPRESS_FORMAT,
            UCropActivity.DEFAULT_COMPRESS_QUALITY,
            cropCallback
        )
    }


    private fun startMultiCrop() {
        if (mLruCache == null) {
            return
        }

        for ((_, value) in mLruCache!!.entrySet()) {
            (value as BitmapCropTask).execute()
        }
        FinishMultiCropTask().execute()
    }

    @SuppressLint("StaticFieldLeak")
    inner class FinishMultiCropTask : AsyncTask<Void?, Void?, Void?>() {

        override fun doInBackground(vararg p0: Void?): Void? {
            return null
        }

        override fun onPostExecute(aVoid: Void?) {
            val result: MutableList<LocalMedia> = java.util.ArrayList()
            result.addAll(mAdapter.selectedImages)
        }

    }

    fun startPreview(localMedia: LocalMedia?) {

        var path = ""
        path = if(localMedia!!.cutPath == null) {
            localMedia.path
        } else {
            localMedia.cutPath
        }

        if (mPreviewPosition >= 0) {
            savePreviousPositionCropInfo(localMedia)
        }
        val isHttp: Boolean = PictureMimeType.isHasHttp(path)
        val isAndroidQ: Boolean = SdkVersionUtils.checkedAndroid_Q()
        val inputUri = if (isHttp || isAndroidQ) Uri.parse(path) else Uri.fromFile(File(path))
        val suffix = ".jpeg"
        val file = File(PictureFileUtils.getDiskCacheDir(this),  DateUtils.getCreateFileName("IMG_").toString() + suffix )


        val outputUri = Uri.fromFile(file)
        if (outputUri != null) {
            try {
//                val isOnTouch: Boolean = isOnTouch(inputUri)
                mGestureCropImageView.isScaleEnabled = true
                mGestureCropImageView.setImageUri(inputUri, outputUri)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class BitmapCropCallbackImpl() : BitmapCropCallback {
        private var mLocalMedia: LocalMedia? = null

        constructor(mLocalMedia: LocalMedia): this() {
            this.mLocalMedia = mLocalMedia
        }

        override fun onBitmapCropped(
            resultUri: Uri,
            offsetX: Int,
            offsetY: Int,
            imageWidth: Int,
            imageHeight: Int
        ) {
            if (mLocalMedia != null) {
                mLocalMedia!!.isCut = true
                mLocalMedia!!.cutPath = resultUri.path
                mLocalMedia!!.width = imageWidth
                mLocalMedia!!.height = imageHeight
                mLocalMedia!!.size = File(resultUri.path!!).length()
                mLocalMedia!!.androidQToPath = if (SdkVersionUtils.checkedAndroid_Q()) resultUri.path else mLocalMedia!!.androidQToPath

                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(File(mLocalMedia!!.cutPath)))
                mAdapter.clearSelectedImages()
            }
        }

        override fun onCropFailure(t: Throwable) {
            t.printStackTrace()
        }
    }


}

