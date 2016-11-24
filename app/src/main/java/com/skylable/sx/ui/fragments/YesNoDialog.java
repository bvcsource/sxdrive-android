package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.ui.activities.ActivityMain;

/**
 * Created by tangarr on 21.10.15.
 */
public class YesNoDialog extends InputDialogFragment {

    View mView;
    String mTitle;
    String mText;
    String mYesButton;
    String mNoButton;
    AcceptListener mListener = null;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        setTextInputEnabled(false);

        if (savedInstanceState == null)
            updateViews();
        else {
            mTitle = savedInstanceState.getString("title");
            mText = savedInstanceState.getString("message");
            mYesButton = savedInstanceState.getString("yes");
            mNoButton = savedInstanceState.getString("no");
            updateViews();
        }
        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("title", mTitle);
        outState.putString("message", mText);
        outState.putString("yes", mYesButton);
        outState.putString("no",mNoButton);
        super.onSaveInstanceState(outState);
    }

    void setup(AcceptListener listener)
    {
        mListener = listener;
    }
    public void setup(AcceptListener listener, String title, String text, String yesButton, String noButton)
    {
        mListener = listener;
        mTitle = title;
        mText = text;
        mYesButton = yesButton;
        mNoButton = noButton;
        if (mView != null)
            updateViews();
    }
    public void setupAcceptListener(AcceptListener listener) {
        mListener = listener;
    }

    void updateViews()
    {
        setTitle(mTitle);
        setSecondaryTitle(mText);
        setOkButtonText(mYesButton);
        setCancelButtonText(mNoButton);
    }

    @Override
    public boolean onBackPressed() {
        close();
        return false;
    }

    @Override
    void onAcceptClicked() {
        if (mListener != null)
            mListener.onAcceptClicked(this);
    }

    public interface AcceptListener
    {
        void onAcceptClicked(YesNoDialog dialog);
    }
}
