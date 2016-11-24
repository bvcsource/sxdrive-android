package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/**
 * Created by tangarr on 24.09.15.
 */
public class TaskViewFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public void setProgress(long size, long progress)
    {

    }



    public class TaskHandler extends Handler
    {

        WeakReference<TaskViewFragment> weakReference;
        TaskHandler(TaskViewFragment view)
        {
            weakReference = new WeakReference<TaskViewFragment>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            String msgType = msg.getData().getString("type");
            if (msgType == null)
                return;
            switch (msgType)
            {
                case "setProgress":
                    setProgress(msg.getData());
            }
        }

        private void setProgress(Bundle bundle)
        {
            long progress = bundle.getLong("progress");
            long size = bundle.getLong("size");
            TaskViewFragment view = weakReference.get();
            if (view != null)
                view.setProgress(size, progress);
        }
    }
}
