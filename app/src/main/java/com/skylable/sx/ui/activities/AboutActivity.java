package com.skylable.sx.ui.activities;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class AboutActivity extends ActionBarActivity
{
    protected void onCreate(android.os.Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        String versionText = getString(R.string.version_text, SxApp.sPackageInfo.versionName);
        ((TextView) findViewById(R.id.version_info)).setText(versionText);

        findViewById(R.id.logo).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.skylable_web)));
                startActivity(browserIntent);
            }
        });
        findViewById(R.id.send_feedback).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("message/rfc822");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { getString(R.string.skylable_email) });
                startActivity(Intent.createChooser(emailIntent, getString(R.string.send_feedback)));
            }
        });
        if (getResources().getBoolean(R.bool.show_powered_by)) {
            TextView view = (TextView) findViewById(R.id.powered_by);
            view.setVisibility(View.VISIBLE);
            view.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
        {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
