package com.motondon.driveapidemoapp3.activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.fragments.DriveFolderMonitorFragment

class DriveFolderMonitorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val actionBar = supportActionBar
        actionBar?.title = "Drive Folder Monitor"

        var fragment: Fragment? = supportFragmentManager.findFragmentByTag(DriveFolderMonitorFragment.TAG)
        if (fragment == null) {
            try {
                fragment = DriveFolderMonitorFragment()
                fragment?.arguments = intent.extras

            } catch (e: Exception) {
                Log.e(TAG, "onCreate() - Fail when trying to create ${DriveFolderMonitorFragment::class.java.simpleName} fragment. Error: ${e.message}")
            }
        }
        supportFragmentManager.beginTransaction().replace(R.id.flContent, fragment, DriveFolderMonitorFragment.TAG).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        val TAG = DriveFolderMonitorActivity::class.java.simpleName
    }
}
