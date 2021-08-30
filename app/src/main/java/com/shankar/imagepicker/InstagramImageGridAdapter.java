package com.shankar.imagepicker;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.transition.Hold;
import com.shankar.library.DateUtils;
import com.shankar.library.LocalMedia;
import com.shankar.library.PictureConfig;
import com.shankar.library.PictureFileUtils;
import com.shankar.library.PictureMimeType;
import com.shankar.library.SdkVersionUtils;
import com.shankar.library.StringUtils;
import com.shankar.library.ToastUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class InstagramImageGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private OnPhotoSelectChangedListener imageSelectChangedListener;
    private List<LocalMedia> images = new ArrayList<>();
    private List<LocalMedia> selectImages = new ArrayList<>();
    private final PictureSelectionConfig config;
    private int mPreviewPosition;
    private long lastClickTime;

    public InstagramImageGridAdapter(Context context, PictureSelectionConfig config) {
        this.context = context;
        this.config = config;
    }


    public void bindImagesData(List<LocalMedia> images) {
        this.images = images == null ? new ArrayList<>() : images;
        notifyDataSetChanged();
    }

    public void bindSelectImages(List<LocalMedia> images) {
        List<LocalMedia> selection = new ArrayList<>();
        int size = images.size();
        for (int i = 0; i < size; i++) {
            LocalMedia media = images.get(i);
            selection.add(media);
        }
        this.selectImages = selection;
        subSelectPosition();
        if (imageSelectChangedListener != null) {
            imageSelectChangedListener.onChange(selectImages);
        }
    }

    public List<LocalMedia> getSelectedImages() {
        return selectImages == null ? new ArrayList<>() : selectImages;
    }
    public void clearSelectedImages() {
        selectImages.clear();
    }

    public List<LocalMedia> getImages() {
        return images == null ? new ArrayList<>() : images;
    }

    public boolean isDataEmpty() {
        return images == null || images.size() == 0;
    }

    public int getSize() {
        return images == null ? 0 : images.size();
    }

    @Override
    public int getItemViewType(int position) {

        return PictureConfig.TYPE_PICTURE;

    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context).inflate(R.layout.instagram_image_grid_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {

        final ViewHolder contentHolder = (ViewHolder) holder;
        final LocalMedia image = images.get(position);
        image.position = contentHolder.getAdapterPosition();
        final String path = image.getPath();
        final String mimeType = image.getMimeType();

        if (config.checkNumMode) {
            notifyCheckChanged(contentHolder, image);
        }

        if (!config.isSingleDirectReturn) {
            selectImage(contentHolder, isSelected(image));
        } else {
            contentHolder.ivPicture.setColorFilter(ContextCompat.getColor
                    (context, R.color.picture_color_20), PorterDuff.Mode.SRC_ATOP);
        }

        Object tag = contentHolder.ivPicture.getTag();
        boolean isLoadingAnim = false;
        if (tag instanceof Boolean) {
            isLoadingAnim = (boolean) tag;
        }
        if (!config.isSingleDirectReturn) {
            if (!isLoadingAnim) {
                if (isSelected(image)) {
                    if (contentHolder.ivPicture.getScaleX() != 1.12f) {
                        contentHolder.ivPicture.setScaleX(1.12f);
                        contentHolder.ivPicture.setScaleY(1.12f);
                    }
                } else {
                    if (contentHolder.ivPicture.getScaleX() != 1) {
                        contentHolder.ivPicture.setScaleX(1);
                        contentHolder.ivPicture.setScaleY(1);
                    }
                }
            }
        } else {
            if (contentHolder.ivPicture.getScaleX() != 1) {
                contentHolder.ivPicture.setScaleX(1);
                contentHolder.ivPicture.setScaleY(1);
            }
        }

        contentHolder.tvCheck.setVisibility(config.isSingleDirectReturn ? View.GONE : View.VISIBLE);
        contentHolder.btnCheck.setVisibility(config.isSingleDirectReturn ? View.GONE : View.VISIBLE);

        Glide.with(context).load(path).override(200, 200).into(contentHolder.ivPicture);


        if (mPreviewPosition == position) {
            contentHolder.maskView.setVisibility(View.VISIBLE);
        } else {
            contentHolder.maskView.setVisibility(View.GONE);
        }

        contentHolder.contentView.setOnClickListener(v -> {

            String newPath = SdkVersionUtils.checkedAndroid_Q()
                    ? PictureFileUtils.getPath(context, Uri.parse(path)) : path;
            if (!TextUtils.isEmpty(newPath)) {
                assert newPath != null;
                if (!new File(newPath).exists()) {
                    ToastUtils.s(context, PictureMimeType.s(context, mimeType));
                    return;
                }
            }
            if (position == -1) {
                return;
            }
            if (SdkVersionUtils.checkedAndroid_Q()) {
                image.setRealPath(newPath);
            }
            if (config.selectionMode == PictureConfig.SINGLE) {
                image.getMimeType();

                if (imageSelectChangedListener != null) {
                    imageSelectChangedListener.onPictureClick(image, position);
                }
            } else {
                if(contentHolder.rlTick.getVisibility() == View.VISIBLE && mPreviewPosition != position) {
                    imageSelectChangedListener.onPictureClick(image, position);
                }
                else
                {
                    changeCheckboxState(contentHolder, image, position);
                    imageSelectChangedListener.onPictureClick(image, position);
                }
            }
        });
    }

    public boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        if (time - lastClickTime < 800) {
            return true;
        }
        lastClickTime = time;
        return false;
    }


    @Override
    public int getItemCount() {
        return images.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPicture;
        TextView tvCheck;
        RelativeLayout rlTick;
        View contentView;
        View btnCheck;
        View maskView;

        public ViewHolder(View itemView) {
            super(itemView);
            contentView = itemView;
            ivPicture = itemView.findViewById(R.id.ivPicture);
            tvCheck = itemView.findViewById(R.id.tvCheck);
            rlTick = itemView.findViewById(R.id.rl_tick);
            btnCheck = itemView.findViewById(R.id.btnCheck);
            maskView = itemView.findViewById(R.id.iv_mask);

        }
    }

    public boolean isSelected(LocalMedia image) {
        int size = selectImages.size();
        for (int i = 0; i < size; i++) {
            LocalMedia media = selectImages.get(i);
            if (media == null || TextUtils.isEmpty(media.getPath())) {
                continue;
            }
            if (media.getPath()
                    .equals(image.getPath())
                    || media.getId() == image.getId()) {
                return true;
            }
        }
        return false;
    }

    private void notifyCheckChanged(ViewHolder viewHolder, LocalMedia imageBean) {
        viewHolder.tvCheck.setText("");
        int size = selectImages.size();
        for (int i = 0; i < size; i++) {
            LocalMedia media = selectImages.get(i);
            if (media.getPath().equals(imageBean.getPath())
                    || media.getId() == imageBean.getId()) {
                imageBean.setNum(media.getNum());
                media.setPosition(imageBean.getPosition());
                viewHolder.tvCheck.setText(String.valueOf(imageBean.getNum()));
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    private void changeCheckboxState(ViewHolder contentHolder, LocalMedia image, int position) {
        boolean isChecked = contentHolder.tvCheck.isSelected();
        int size = selectImages.size();
        String mimeType = size > 0 ? selectImages.get(0).getMimeType() : "";

        if (config.isWithVideoImage) {
            int videoSize = 0;
            int imageSize = 0;
            for (int i = 0; i < size; i++) {
                LocalMedia media = selectImages.get(i);
                if (PictureMimeType.isHasVideo(media.getMimeType())) {
                    videoSize++;
                } else {
                    imageSize++;
                }
            }
            if (PictureMimeType.isHasVideo(image.getMimeType())) {
                if (config.maxVideoSelectNum > 0
                        && videoSize >= config.maxVideoSelectNum && !isChecked) {
                    // 如果选择的是视频
                    ToastUtils.s(context, StringUtils.getMsg(context, image.getMimeType(), config.maxVideoSelectNum));
                    return;
                }

            }
            if (PictureMimeType.isHasImage(image.getMimeType()) && imageSize >= config.maxSelectNum && !isChecked) {
                ToastUtils.s(context, StringUtils.getMsg(context, image.getMimeType(), config.maxSelectNum));
                return;
            }


        } else {
            if (!TextUtils.isEmpty(mimeType)) {
                boolean mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, image.getMimeType());
                if (!mimeTypeSame) {
                    ToastUtils.s(context, context.getString(R.string.picture_rule));
                    return;
                }
            }
            if (size >= config.maxSelectNum && !isChecked) {
                ToastUtils.s(context, StringUtils.getMsg(context, mimeType, config.maxSelectNum));
                return;
            }
            image.getMimeType();
        }

        if (isChecked) {
            for (int i = 0; i < size; i++) {
                LocalMedia media = selectImages.get(i);
                if (media == null || TextUtils.isEmpty(media.getPath())) {
                    continue;
                }
                if (media.getPath().equals(image.getPath())
                        || media.getId() == image.getId()) {
                    selectImages.remove(media);
                    subSelectPosition();
                    if (contentHolder.ivPicture.getScaleX() == 1.12f) {
                        disZoom(contentHolder.ivPicture, config.zoomAnim);
                    }
                    break;
                }
            }
        } else {
            if (config.selectionMode == PictureConfig.SINGLE) {
                singleRadioMediaImage();
            }
            selectImages.add(image);
            image.setNum(selectImages.size());
            if (contentHolder.ivPicture.getScaleX() == 1f) {
                zoom(contentHolder.ivPicture, config.zoomAnim);
            }
            contentHolder.tvCheck.startAnimation(AnimationUtils.loadAnimation(context, R.anim.picture_anim_modal_in));
        }
        notifyItemChanged(contentHolder.getAdapterPosition());
        selectImage(contentHolder, !isChecked);
        if (imageSelectChangedListener != null) {
            imageSelectChangedListener.onItemChecked(position, image, !isChecked);
            imageSelectChangedListener.onChange(selectImages);
        }
    }

    private void singleRadioMediaImage() {
        if (selectImages != null
                && selectImages.size() > 0) {
            boolean isGo = true;
            LocalMedia media = selectImages.get(0);
            notifyItemChanged(config.isCamera ? media.position :
                    media.position);
            selectImages.clear();
        }
    }

    private void subSelectPosition() {
        if (config.checkNumMode) {
            int size = selectImages.size();
            for (int index = 0; index < size; index++) {
                LocalMedia media = selectImages.get(index);
                media.setNum(index + 1);
                notifyItemChanged(media.position);
            }
        }
    }

    public void setPreviewPosition(int previewPosition) {
        if (previewPosition < 0 || previewPosition >= getItemCount()) {
            return;
        }
        mPreviewPosition = previewPosition;
    }

    public void selectImage(ViewHolder holder, boolean isChecked) {
        holder.tvCheck.setSelected(isChecked);
        if (isChecked) {
            holder.ivPicture.setColorFilter(ContextCompat.getColor
                    (context, R.color.picture_color_80), PorterDuff.Mode.SRC_ATOP);
            holder.rlTick.setVisibility(View.VISIBLE);
        } else {
            holder.ivPicture.setColorFilter(ContextCompat.getColor
                    (context, R.color.picture_color_20), PorterDuff.Mode.SRC_ATOP);
            holder.rlTick.setVisibility(View.GONE);
        }


    }

    public interface OnPhotoSelectChangedListener {
        void onItemChecked(int position, LocalMedia image, boolean isCheck);
        void onTakePhoto();

        void onChange(List<LocalMedia> selectImages);

        void onPictureClick(LocalMedia media, int position);
    }

    public void setOnPhotoSelectChangedListener(OnPhotoSelectChangedListener
                                                        imageSelectChangedListener) {
        this.imageSelectChangedListener = imageSelectChangedListener;
    }

    private void zoom(View view, boolean isZoomAnim) {
        if (isZoomAnim) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.12f),
                    ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.12f)
            );
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    view.setTag(true);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setTag(false);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    view.setTag(false);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.setDuration(400);
            set.start();
        }
    }

    private void disZoom(View view, boolean isZoomAnim) {
        if (isZoomAnim) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(view, "scaleX", 1.12f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleY", 1.12f, 1f)
            );
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    view.setTag(true);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setTag(false);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    view.setTag(false);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.setDuration(400);
            set.start();
        }
    }
}
