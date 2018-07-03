package com.motondon.driveapidemoapp3.activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.fragments.ActionsWithDriveFilePickerFragment
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

class ActionsWithDriveFilePickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val actionBar = supportActionBar
        actionBar?.title = "Actions w/Drive File Picker"

        var fragment: Fragment? = supportFragmentManager.findFragmentByTag(ActionsWithDriveFilePickerFragment.TAG)
        if (fragment == null) {
            try {
                fragment = ActionsWithDriveFilePickerFragment()
                fragment?.arguments = intent.extras

            } catch (e: Exception) {
                Log.e(TAG, "onCreate() - Fail when trying to create ${ActionsWithDriveFilePickerFragment::class.java.simpleName} fragment. Error: ${e.message}")
            }
        }
        supportFragmentManager.beginTransaction().replace(R.id.flContent, fragment, ActionsWithDriveFilePickerFragment.TAG).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        val TAG = ActionsWithDriveFilePickerActivity::class.java.simpleName
    }
}
