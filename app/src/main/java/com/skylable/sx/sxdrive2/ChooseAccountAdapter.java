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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

public class ChooseAccountAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private LayoutInflater mInfalter;
    private Account[] mAccounts;
    private ChooseAccountClickListener mClickListener;

    public ChooseAccountAdapter(Context context, ChooseAccountClickListener clickListener)
    {
        mContext = context;
        mInfalter = LayoutInflater.from(context);
        AccountManager am = AccountManager.get(context);
        mAccounts =  am.getAccountsByType(context.getResources().getString(R.string.account_type));
        mClickListener = clickListener;
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInfalter.inflate(R.layout.item_choose_account, parent, false);
        final ChooseAccountItemHolder itemHolder = new ChooseAccountItemHolder(view);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickListener != null) {
                    String account = itemHolder.account;
                    mClickListener.onItemClicked(account, itemHolder.position >= mAccounts.length);
                }
            }
        });

        return itemHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ChooseAccountItemHolder itemHolder = (ChooseAccountItemHolder)holder;
        itemHolder.position = position;

        String account;

        if (position >= mAccounts.length)
        {
            account = "";
            itemHolder.account = null;
            itemHolder.icon.setImageResource(R.drawable.ic_add);
            itemHolder.user.setText(mContext.getText(R.string.action_add_account));
            itemHolder.cluster.setVisibility(View.GONE);
        }
        else
        {
            account = mAccounts[position].name;
            String tmp_account;

            if (account.startsWith("*") && account.endsWith("*")) {
                tmp_account = account.substring(1, account.length()-2);
                itemHolder.icon.setImageResource(R.drawable.ic_account_enterprise);
            }
            else {
                tmp_account = account;
                itemHolder.icon.setImageResource(R.drawable.ic_account);
            }
            int index = tmp_account.lastIndexOf("@");

            String user = tmp_account.substring(0, index);
            String cluster = tmp_account.substring(index+1);

            itemHolder.user.setText(user);
            itemHolder.cluster.setText(cluster);
            itemHolder.cluster.setVisibility(View.VISIBLE);
        }
        itemHolder.account = account;

        String currentAccount = SxApp.currentAccountName();

        if (TextUtils.equals(account, currentAccount))
        {
            itemHolder.icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                    mContext.getResources().getColor(R.color.secondarySelectedIconColor)));
            itemHolder.user.setTextColor(mContext.getResources().getColor(R.color.secondarySelectedTextColor));
            itemHolder.cluster.setTextColor(mContext.getResources().getColor(R.color.secondarySelectedTextColor));
        }
        else
        {
            itemHolder.icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                    mContext.getResources().getColor(R.color.secondaryIconColor)));
            itemHolder.user.setTextColor(mContext.getResources().getColor(R.color.secondaryTextColor));
            itemHolder.cluster.setTextColor(mContext.getResources().getColor(R.color.secondaryTextColor));
        }
    }

    @Override
    public int getItemCount() {
        return mAccounts.length+1;
    }

    public interface ChooseAccountClickListener
    {
        public abstract void onItemClicked(String title, boolean lastItem);
    }
}

class ChooseAccountItemHolder extends RecyclerView.ViewHolder
{
    ImageView icon;
    TextView user;
    TextView cluster;
    int position;
    String account = null;

    public ChooseAccountItemHolder(View itemView) {
        super(itemView);
        icon = (ImageView)itemView.findViewById(R.id.imageView);
        user = (TextView)itemView.findViewById(R.id.textUser);
        cluster = (TextView)itemView.findViewById(R.id.textCluster);
        position = -1;
    }
}