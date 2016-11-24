/*
 *  Copyright (C) 2012-2016 Skylable Ltd. <info-copyright@skylable.com>
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  Special exception for linking this software with OpenSSL:
 *
 *  In addition, as a special exception, Skylable Ltd. gives permission to
 *  link the code of this program with the OpenSSL library and distribute
 *  linked combinations including the two. You must obey the GNU General
 *  Public License in all respects for all of the code used other than
 *  OpenSSL. You may extend this exception to your version of the program,
 *  but you are not obligated to do so. If you do not wish to do so, delete
 *  this exception statement from your version.
 */
package com.skylable.sx.sxdrive2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

public class NavigatorAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private LayoutInflater mInfalter;
    private NavigatorItemClickListener mNavigatorItemClickListener;

    public enum NavigatorOption {
        OPTION_FILES,
        OPTION_UPLOAD,
        OPTION_FAVOURITES,
        OPTION_SETTINGS,
        OPTION_ABOUT
    }

    final static NavigatorMenuItem[] sMenuList = {
            new NavigatorMenuItem(R.drawable.files, SxApp.getStringResource(R.string.navigator_files), NavigatorOption.OPTION_FILES),
            new NavigatorMenuItem(R.drawable.upload, SxApp.getStringResource(R.string.navigator_transfers), NavigatorOption.OPTION_UPLOAD),
            new NavigatorMenuItem(R.drawable.favorite, SxApp.getStringResource(R.string.navigator_starred), NavigatorOption.OPTION_FAVOURITES),
            new NavigatorMenuItem(R.drawable.settings, SxApp.getStringResource(R.string.navigator_settings), NavigatorOption.OPTION_SETTINGS),
            new NavigatorMenuItem(R.drawable.about, SxApp.getStringResource(R.string.navigator_about), NavigatorOption.OPTION_ABOUT)
    };

    public NavigatorAdapter(Context context, NavigatorItemClickListener navigatorItemClickListener)
    {
        mContext = context;
        mInfalter = LayoutInflater.from(context);
        mNavigatorItemClickListener = navigatorItemClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInfalter.inflate(R.layout.item_navigation_menu, parent, false);
        final MenuItemHolder itemHolder = new MenuItemHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int index = itemHolder.position;
                if (mNavigatorItemClickListener != null && index >= 0) {

                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mNavigatorItemClickListener.onClick(sMenuList[index].option);
                        }
                    },200);
                }
            }
        });
        return itemHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        MenuItemHolder itemHolder = (MenuItemHolder)holder;
        itemHolder.icon.setImageResource(sMenuList[position].imageId);
        itemHolder.title.setText(sMenuList[position].title);
        itemHolder.position = position;

        if (NavigationDrawer.sSelectedItem == sMenuList[position].option)
        {
            itemHolder.icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                    mContext.getResources().getColor(R.color.secondarySelectedIconColor)));
            itemHolder.title.setTextColor(mContext.getResources().getColor(R.color.secondarySelectedTextColor));
        }
        else
        {
            itemHolder.icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                    mContext.getResources().getColor(R.color.secondaryIconColor)));
            itemHolder.title.setTextColor(mContext.getResources().getColor(R.color.secondaryTextColor));
        }
    }

    @Override
    public int getItemCount() {
        return sMenuList.length;
    }

    public interface NavigatorItemClickListener
    {
        void onClick(NavigatorAdapter.NavigatorOption option);
    }

}

class MenuItemHolder extends RecyclerView.ViewHolder
{
    ImageView icon;
    TextView title;
    int position = -1;

    public MenuItemHolder(View itemView) {
        super(itemView);
        icon = (ImageView)itemView.findViewById(R.id.imageView);
        title = (TextView)itemView.findViewById(R.id.textView);
    }
}

class NavigatorMenuItem
{
    int imageId;
    String title;
    NavigatorAdapter.NavigatorOption option;
    public NavigatorMenuItem(int imageId, String title, NavigatorAdapter.NavigatorOption option)
    {
        this.imageId = imageId;
        this.title = title;
        this.option = option;
    }
}

