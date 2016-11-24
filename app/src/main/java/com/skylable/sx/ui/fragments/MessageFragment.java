package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

/**
 * Created by tangarr on 30.09.15.
 */
public class MessageFragment extends Fragment{

    String mMessage = null;
    View mView = null;
    TextView mTextMessage = null;
    MessageFragment mFragment = this;
    private static FragmentManager sFragmentManager = null;
    private static int sContainerId;
    private static String sMessageToShow = null;
    private static int sMessageToShowContainerId = -1;

    static public void setupFragmentManager(FragmentManager fm, int container)
    {
        sFragmentManager = fm;
        sContainerId = container;
        if (sMessageToShow != null && sContainerId == sMessageToShowContainerId) {
            String message = sMessageToShow;
            sMessageToShow = null;
            showMessage(message);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.message_fragment, container, false);

        mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFragmentManager().beginTransaction().remove(mFragment).commit();
            }
        });

        mTextMessage = (TextView) mView.findViewById(R.id.textView);
        if (savedInstanceState != null)
        {
            mMessage = savedInstanceState.getString("message");
        }
        if (mMessage != null)
        {
            mTextMessage.setText(mMessage);
        }
        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("message", mMessage);
        super.onSaveInstanceState(outState);
    }

    void setMessage(String message)
    {
        mMessage = message;
        if (mTextMessage != null)
            mTextMessage.setText(mMessage);
    }
    public static void showMessage(int stringResourceId)
    {
        showMessage(SxApp.getStringResource(stringResourceId));
    }
    public static void showMessage(String message) {
        if (sFragmentManager == null)
        {
            Log.e("MessageFragment", "unable to show message, FragmentManager not set");
            return;
        }

        MessageFragment fragment = new MessageFragment();
        fragment.setMessage(message);
        try {
            sFragmentManager.beginTransaction().add(sContainerId, fragment, "SX_MESSAGEBOX").commit();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            sMessageToShow = message;
            sMessageToShowContainerId = sContainerId;
        }
    }
}

