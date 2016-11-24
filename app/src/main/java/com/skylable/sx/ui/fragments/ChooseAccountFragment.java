package com.skylable.sx.ui.fragments;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.ChooseAccountAdapter;
import com.skylable.sx.ui.activities.ActivityAccount;

public class ChooseAccountFragment extends Fragment{

    Fragment mFragment;

    private ChooseAccountListener mChooseAccountListener = null;
    public void setChooseAccountListener(ChooseAccountListener listener)
    {
        mChooseAccountListener = listener;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        mFragment = this;
        final View view = inflater.inflate(R.layout.fragment_chose_account, container, false);
        View outside = view.findViewById(R.id.outside_card);
        final View inside = view.findViewById(R.id.card);
        RecyclerView accountList = (RecyclerView) inside.findViewById(R.id.account_list);
        accountList.setAdapter(new ChooseAccountAdapter(getActivity(), new ChooseAccountAdapter.ChooseAccountClickListener() {
            @Override
            public void onItemClicked(String title, boolean lastItem) {
                if (lastItem)
                {
                    Intent intent = new Intent(getActivity(), ActivityAccount.class);
                    startActivity(intent);
                    if (mChooseAccountListener != null)
                        mChooseAccountListener.onClose();
                }
                else
                {
                    if (mChooseAccountListener != null) {
                        mChooseAccountListener.onChooseAccount(title);
                        mChooseAccountListener.onClose();
                    }
                }
            }
        }));
        accountList.setHasFixedSize(true);
        accountList.setLayoutManager(new LinearLayoutManager(getActivity()));

        int count = accountList.getAdapter().getItemCount();
        if (count > 5)
        {
            count = 5;
        }
        int height = count*52+4;
        accountList.getLayoutParams().height = SxApp.convertDp2Px(height);


        if (Build.VERSION.SDK_INT >= 21)
        {
            inside.setElevation(20);
        }

        setupClickListeners(inside, outside, container, view);
        return view;
    }
    public void setupClickListeners(View insideView, View outsideView, final ViewGroup container, final View parent)
    {
        insideView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        outsideView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mChooseAccountListener != null) {
                    mChooseAccountListener.onClose();
                }
            }
        });
    }

    public interface ChooseAccountListener
    {
        void onChooseAccount(String account);
        void onClose();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("ActivityMain", "account dialog - pause");
    }
}
