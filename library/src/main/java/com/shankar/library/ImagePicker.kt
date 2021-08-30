package com.shankar.library

import android.content.Context

class ImagePicker(val context: Context) {


    fun loadLocalImages(imageInterface: ImageInterface) {

        PictureThreadUtils.executeByCached(object : PictureThreadUtils.SimpleTask<List<LocalMediaFolder>>() {
            override fun doInBackground(): List<LocalMediaFolder> {
                return LocalMediaLoader(context).loadAllMedia()
            }

            override fun onSuccess(folders: List<LocalMediaFolder>?) {
                PictureThreadUtils.cancel(PictureThreadUtils.getCachedPool())

                if (folders != null) {
                    if (folders.isNotEmpty()) {
                        val folder: LocalMediaFolder = folders[0]
                        folder.isChecked = true
                        imageInterface.onImagesLoaded(folder.data)
                    }
                }

            }
        })
    }

    interface ImageInterface {
        fun onImagesLoaded(data: MutableList<LocalMedia>) {}
    }

}