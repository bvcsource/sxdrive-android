package com.skylable.sx.sxdrive2;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;

public class NavigationDrawer extends Fragment {

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mToggle;
    private RecyclerView mRecycleView;

    public static NavigatorAdapter.NavigatorOption sSelectedItem = NavigatorAdapter.NavigatorOption.OPTION_FILES;

    public void selectOption(NavigatorAdapter.NavigatorOption option)
    {
        sSelectedItem = option;
    }
    public void closeDrawer()
    {
        mDrawerLayout.closeDrawers();
        mRecycleView.getAdapter().notifyDataSetChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_navigation_drawer, container, false);

        mRecycleView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecycleView.addItemDecoration(new SxMenuItemDecoration(getActivity()));
        mRecycleView.setHasFixedSize(true);
        mRecycleView.setLayoutManager(new LinearLayoutManager(getActivity()));

        return view;
    }

    public void setup(DrawerLayout drawerLayout, Toolbar toolbar, NavigatorAdapter.NavigatorItemClickListener navigatorItemClickListener) {
        mDrawerLayout = drawerLayout;
        mRecycleView.setAdapter(new NavigatorAdapter(getActivity(), navigatorItemClickListener));
        mToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout, toolbar, R.string.action_open, R.string.action_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getActivity().supportInvalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                getActivity().supportInvalidateOptionsMenu();
            }
        };
        mDrawerLayout.setDrawerListener(mToggle);
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mToggle.syncState();
            }
        });
    }


    interface iMenuItemSingleTapListener
    {
        void onSingleTap(View view, int position);
    }
}

