package com.skylable.sx.ui.fragments;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

/**
 * Created by tangarr on 30.09.15.
 */
public class FloatingActionFragment extends Fragment implements View.OnClickListener {

    private static final int ANIMATION_DURATION = 250;

    private View mView = null;
    private View mFloatingAction = null;
    private View mActionButton = null;
    private View mActionBar = null;

    private View mAddFile = null;
    private View mAddDir = null;
    private ValueAnimator mAnimator;
    private ActionListener mActionListener = null;

    private boolean isButton;
    private int mMinWidth = SxApp.convertDp2Px(56);
    private int mMaxWidth = -1;

    public void setActionListener(ActionListener actionListener)
    {
        mActionListener = actionListener;
    }

    private void setup()
    {
        mFloatingAction.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Log.e("onFocus", ""+hasFocus);
            }
        });


        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isButton) {
                    if (Build.VERSION.SDK_INT >= 11)
                        mAnimator.cancel();
                    isButton = true;
                    updateButton();
                }
                return false;
            }
        });

        if (Build.VERSION.SDK_INT >= 11) {
            mAnimator = new ValueAnimator();
            mAnimator.setDuration(ANIMATION_DURATION);
            mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (Build.VERSION.SDK_INT >= 11) {
                        resizeButton((Integer)animation.getAnimatedValue());
                    }
                }
            });
        }
        else
            mAnimator = null;

        mFloatingAction.setOnClickListener(this);
        mAddFile.setOnClickListener(this);
        mAddDir.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    void resizeButton(int width)
    {
        if (mFloatingAction.getWidth() == width)
            return;

        ViewGroup.LayoutParams params = mFloatingAction.getLayoutParams();

        if (width == mMinWidth)
        {
            mActionButton.setVisibility(View.VISIBLE);
            mActionBar.setVisibility(View.GONE);
            params.height = mMinWidth;
        }
        else if (width == mMaxWidth)
        {
            mActionButton.setVisibility(View.GONE);
            mActionBar.setVisibility(View.VISIBLE);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        else
        {
            mActionButton.setVisibility(View.GONE);
            mActionBar.setVisibility(View.GONE);
        }

        params.width = width;
        mFloatingAction.setLayoutParams(params);
    }


    void updateButton()
    {
        ViewGroup.LayoutParams params = mFloatingAction.getLayoutParams();
        if (isButton)
        {
            params.width = mMinWidth;
            params.height = mMinWidth;
            mActionButton.setVisibility(View.VISIBLE);
            mActionBar.setVisibility(View.GONE);
        }
        else
        {
            params.width = CardView.LayoutParams.MATCH_PARENT;
            params.height = CardView.LayoutParams.WRAP_CONTENT;
            mActionButton.setVisibility(View.GONE);
            mActionBar.setVisibility(View.VISIBLE);
        }
        mFloatingAction.setLayoutParams(params);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 21)
            mView = inflater.inflate(R.layout.floating_action_layout_v21, container, false);
        else
            mView = inflater.inflate(R.layout.floating_action_layout, container, false);

        mFloatingAction = mView.findViewById(R.id.floatingActionButton);
        mActionBar = mView.findViewById(R.id.bar);
        mActionButton = mView.findViewById(R.id.button);
        mAddFile = mView.findViewById(R.id.add_file);
        mAddDir = mView.findViewById(R.id.mkdir);

        isButton = true;
        updateButton();
        setup();
        return mView;
    }

    public boolean onBackPressed()
    {
        if (!isButton)
        {
            isButton = true;
            updateButton();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        if (v == mAddDir)
        {
            if (mActionListener != null)
                mActionListener.onAddDir();
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isButton = true;
                    updateButton();
                }
            }, 200);
        }
        else if (v == mAddFile)
        {
            if (mActionListener != null)
                mActionListener.onAddFile();

            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isButton = true;
                    updateButton();
                }
            }, 200);
        }
        else if (v == mFloatingAction && isButton) {
            isButton = false;
            if (Build.VERSION.SDK_INT >= 11 ) {
                if (mMaxWidth == -1) {
                    int x = (int) mFloatingAction.getX();
                    int parent = mView.getWidth();
                    int margin = parent - x - mMinWidth;
                    mMaxWidth = parent - 2 * margin;
                }
                mAnimator.setIntValues(mMinWidth, mMaxWidth);
                mAnimator.start();
            }
            else {
                updateButton();
            }
        }
    }

    public interface ActionListener
    {
        void onAddFile();
        void onAddDir();
    }
}
