package com.skylable.sx.ui.fragments;

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.ui.activities.ActivityMain;
import com.skylable.sx.ui.activities.ActivityUploadFile;

/**
 * Created by tangarr on 01.10.15.
 */
public abstract class InputDialogFragment extends Fragment implements View.OnClickListener {

    public static final String FRAGMENT_TAG = "SxInputDialogFragment";

    private View mView;
    private TextView mButtonOk;
    private TextView mButtonCancel;
    private EditText mEdit, mEdit2;
    private TextView mTitle;
    private TextView mSecondaryTitle;
    private CheckBox mCheckbox;

    private View mProgress;
    private boolean mLocked = false;
    static private android.support.v4.app.FragmentManager fm = null;

    private final int ENUM_ACTIVITY_NONE = 0;
    private final int ENUM_ACTIVITY_MAIN = 1;
    private final int ENUM_ACTIVITY_UPLOAD = 2;

    private int enum_activity = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.input_dialog_fragment, container, false);
        View card = mView.findViewById(R.id.card);
        mButtonOk = (TextView) mView.findViewById(R.id.button_ok);
        mButtonCancel = (TextView) mView.findViewById(R.id.button_cancel);
        mEdit = (EditText) mView.findViewById(R.id.textEdit);
        mEdit2 = (EditText) mView.findViewById(R.id.textEdit2);
        mProgress = mView.findViewById(R.id.progressBar);
        mTitle = (TextView) mView.findViewById(R.id.title);
        mSecondaryTitle = (TextView) mView.findViewById(R.id.textView);
        mCheckbox = (CheckBox) mView.findViewById(R.id.checkbox);

        mView.setOnClickListener(this);
        card.setOnClickListener(this);
        mButtonOk.setOnClickListener(this);
        mButtonCancel.setOnClickListener(this);

        if (savedInstanceState != null) {
            fm = getFragmentManager();
        }

        setupColoring();

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ActivityMain.getInstance() != null)
            enum_activity = ENUM_ACTIVITY_MAIN;
        else if (ActivityUploadFile.getInstance() != null)
            enum_activity = ENUM_ACTIVITY_UPLOAD;
        else
            enum_activity = ENUM_ACTIVITY_NONE;
    }

    private void setupColoring() {
        EditText[] list = { mEdit, mEdit2 };

        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (v instanceof EditText) {
                    EditText e = (EditText) v;
                    if (Build.VERSION.SDK_INT >= 16) {
                        LightingColorFilter filter = new LightingColorFilter(Color.BLACK,
                                hasFocus ?
                                        getResources().getColor(R.color.accentColor) :
                                        getResources().getColor(R.color.inactiveTextColor));
                        e.getBackground().setColorFilter(filter);

                    }
                }
            }
        };

        for (EditText e : list)
        {
            if (Build.VERSION.SDK_INT >= 16)
            {
                LightingColorFilter filter = new LightingColorFilter(Color.BLACK,
                        getResources().getColor(R.color.inactiveTextColor));
                e.getBackground().setColorFilter(filter);
            }
            e.setHintTextColor(getResources().getColor(R.color.inactiveTextColor));
            e.setOnFocusChangeListener(focusChangeListener);
        }
    }

    public void setTitle(String title)
    {
        if (title == null)
            mTitle.setVisibility(View.GONE);
        else
        {
            mTitle.setVisibility(View.VISIBLE);
            mTitle.setText(title);
        }
    }

    public void setSecondaryTitle(String title)
    {
        if (title == null)
            mSecondaryTitle.setVisibility(View.GONE);
        else
        {
            mSecondaryTitle.setVisibility(View.VISIBLE);
            mSecondaryTitle.setText(title);
        }
    }

    public void setTextInputEnabled(boolean enabled)
    {
        if (enabled) {
            mEdit.setVisibility(View.VISIBLE);
            mEdit.requestFocus();
        }
        else
            mEdit.setVisibility(View.GONE);
    }

    public void setPasswordInputEnabled(boolean enabled, boolean repeat)
    {
        if (enabled) {
            mEdit.setVisibility(View.VISIBLE);
            mEdit.setHint(R.string.enter_password);
            mEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mEdit.setText("");

            mEdit2.setVisibility(repeat ? View.VISIBLE : View.GONE);
            mEdit2.setHint(R.string.re_enter_password);
            mEdit2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mEdit2.setText("");
        }
        else
        {
            mEdit.setVisibility(View.GONE);
            mEdit2.setVisibility(View.GONE);
        }
    }

    public void setCheckboxText(String text) {
        mCheckbox.setText(text);
    }

    public void setCheckboxVisible(boolean visible) {
        mCheckbox.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setCheckboxValue(boolean checked)
    {
        mCheckbox.setChecked(checked);
    }
    public boolean getCheckboxValue()
    {
        return mCheckbox.isChecked();
    }

    public void setOkButtonText(String text)
    {
        mButtonOk.setText(text);
    }

    public void setCancelButtonText(String text)
    {
        mButtonCancel.setText(text);
    }

    public void close()
    {
        hideSoftKeyboard();
        if (enum_activity == ENUM_ACTIVITY_MAIN)
            ActivityMain.closeFragment(InputDialogFragment.FRAGMENT_TAG);
        else if (enum_activity == ENUM_ACTIVITY_UPLOAD)
            ActivityUploadFile.closeFragment(InputDialogFragment.FRAGMENT_TAG);
    }

    public void showProgressBar()
    {
        mEdit.setVisibility(View.GONE);
        mEdit2.setVisibility(View.GONE);
        mButtonOk.setVisibility(View.GONE);
        mButtonCancel.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        mCheckbox.setVisibility(View.INVISIBLE);
    }
    public void hideProgressBar()
    {
        mButtonOk.setVisibility(View.VISIBLE);
        mButtonCancel.setVisibility(View.VISIBLE);
        mProgress.setVisibility(View.GONE);
    }

    public void setLocket(boolean locked)
    {
        mLocked = locked;
    }

    public abstract boolean onBackPressed();
    abstract void onAcceptClicked();

    public String getInputText()
    {
        return mEdit.getText().toString();
    }
    public String getInputText2()
    {
        return mEdit2.getText().toString();
    }

    public void setInputError(String error)
    {
        mEdit.setError(error);
        mEdit.requestFocus();
    }

    @Override
    public void onClick(View v) {
        if (mLocked)
            return;
        if (v == mView)
        {
            onBackPressed();
        }
        else if (v == mButtonCancel)
        {
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    close();
                }
            }, 200);
        }
        else if (v == mButtonOk)
        {
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onAcceptClicked();
                }
            }, 200);
        }
    }

    protected void hideSoftKeyboard() {
        FragmentActivity x = getActivity();
        if (x != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mEdit.getWindowToken(), 0);
        }
    }
    protected void showSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mEdit, 0, null);
    }
}
